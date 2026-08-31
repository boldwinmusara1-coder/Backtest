package com.example.tradestrat.data

import com.example.tradestrat.model.AssetCategory
import com.example.tradestrat.model.Candle
import com.example.tradestrat.model.MarketAsset
import com.example.tradestrat.model.Timeframe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class CoinbaseMarketDataSource(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) : MarketDataSource {

    override val providerName: String = "Coinbase Exchange Public API"
    override val supportsApiKey: Boolean = false

    override fun supportsAsset(asset: MarketAsset): Boolean {
        return asset.category == AssetCategory.CRYPTO
    }

    private fun mapGranularity(tf: Timeframe): Int {
        return when (tf) {
            Timeframe.M5 -> 300
            Timeframe.M15 -> 900
            Timeframe.M30 -> 1800
            Timeframe.H1 -> 3600
            Timeframe.H4 -> 3600 // Request 1h and aggregate into true 4h
            Timeframe.D1 -> 86400
        }
    }

    private fun mapProductId(asset: MarketAsset): String {
        return when (asset.symbol) {
            "BTC/USD", "BTC/USDT" -> "BTC-USD"
            "ETH/USD", "ETH/USDT" -> "ETH-USD"
            "SOL/USD", "SOL/USDT" -> "SOL-USD"
            "BNB/USD", "BNB/USDT" -> "BNB-USDT"
            "XRP/USD", "XRP/USDT" -> "XRP-USD"
            else -> asset.symbol.replace("/", "-")
        }
    }

    override suspend fun fetchHistoricalCandles(
        asset: MarketAsset,
        timeframe: Timeframe,
        startTimeMs: Long,
        endTimeMs: Long,
        apiKey: String?
    ): Result<List<Candle>> = withContext(Dispatchers.IO) {
        val productId = mapProductId(asset)
        val granularity = mapGranularity(timeframe)
        val stepMs = 300L * granularity * 1000L // 300 candles per batch
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val allCandles = mutableListOf<Candle>()
        var currentStart = startTimeMs

        try {
            while (currentStart < endTimeMs) {
                val currentEnd = kotlin.math.min(currentStart + stepMs, endTimeMs)
                val startIso = isoFormat.format(Date(currentStart))
                val endIso = isoFormat.format(Date(currentEnd))
                val url = "https://api.exchange.coinbase.com/products/$productId/candles?granularity=$granularity&start=$startIso&end=$endIso"

                val request = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "TradeStrat-Backtester/1.0")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val code = response.code
                        val errorBody = response.body?.string() ?: ""
                        if (allCandles.isEmpty()) {
                            return@withContext Result.failure(
                                IOException("Coinbase API HTTP $code: $errorBody")
                            )
                        } else {
                            currentStart = endTimeMs // Stop pagination gracefully on partial success
                            return@use
                        }
                    }

                    val body = response.body?.string() ?: throw IOException("Empty response from Coinbase API")
                    val jsonArray = JSONArray(body)

                    if (jsonArray.length() == 0) {
                        currentStart = endTimeMs
                        return@use
                    }

                    for (i in 0 until jsonArray.length()) {
                        val item = jsonArray.getJSONArray(i)
                        val epochSeconds = item.getLong(0)
                        val low = item.getDouble(1)
                        val high = item.getDouble(2)
                        val open = item.getDouble(3)
                        val close = item.getDouble(4)
                        val volume = item.getDouble(5)

                        allCandles.add(
                            Candle(
                                timestamp = epochSeconds * 1000L,
                                open = open,
                                high = high,
                                low = low,
                                close = close,
                                volume = volume
                            )
                        )
                    }
                }

                currentStart = currentEnd + 1000L
            }

            val deduplicated = allCandles.distinctBy { it.timestamp }.sortedBy { it.timestamp }
            val finalCandles = if (timeframe == Timeframe.H4) {
                TimeframeAggregator.aggregate(deduplicated, Timeframe.H1, Timeframe.H4)
            } else {
                deduplicated
            }

            Result.success(finalCandles)
        } catch (e: Exception) {
            if (allCandles.isNotEmpty()) {
                val deduplicated = allCandles.distinctBy { it.timestamp }.sortedBy { it.timestamp }
                val finalCandles = if (timeframe == Timeframe.H4) {
                    TimeframeAggregator.aggregate(deduplicated, Timeframe.H1, Timeframe.H4)
                } else {
                    deduplicated
                }
                Result.success(finalCandles)
            } else {
                Result.failure(e)
            }
        }
    }
}
