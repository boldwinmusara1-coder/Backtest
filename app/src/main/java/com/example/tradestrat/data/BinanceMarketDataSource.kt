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
import java.util.concurrent.TimeUnit

class BinanceMarketDataSource(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) : MarketDataSource {

    override val providerName: String = "Binance Public Spot REST API"
    override val supportsApiKey: Boolean = false

    private val baseUrls = listOf(
        "https://api.binance.com",
        "https://data-api.binance.vision"
    )

    override fun supportsAsset(asset: MarketAsset): Boolean {
        return asset.category == AssetCategory.CRYPTO
    }

    private fun mapTimeframe(tf: Timeframe): String {
        return when (tf) {
            Timeframe.M5 -> "5m"
            Timeframe.M15 -> "15m"
            Timeframe.M30 -> "30m"
            Timeframe.H1 -> "1h"
            Timeframe.H4 -> "4h"
            Timeframe.D1 -> "1d"
        }
    }

    private fun mapSymbol(asset: MarketAsset): String {
        // e.g. "BTC/USD" -> "BTCUSDT", "ETH/USD" -> "ETHUSDT", "SOL/USD" -> "SOLUSDT"
        val raw = asset.symbol.replace("/", "").replace("-", "").uppercase()
        return if (raw.endsWith("USD")) {
            raw + "T" // e.g. BTCUSDT
        } else if (raw.endsWith("USDT")) {
            raw
        } else {
            raw + "USDT"
        }
    }

    override suspend fun fetchHistoricalCandles(
        asset: MarketAsset,
        timeframe: Timeframe,
        startTimeMs: Long,
        endTimeMs: Long,
        apiKey: String?
    ): Result<List<Candle>> = withContext(Dispatchers.IO) {
        val symbol = mapSymbol(asset)
        val interval = mapTimeframe(timeframe)
        val allCandles = mutableListOf<Candle>()

        var currentStart = startTimeMs
        val limit = 1000 // Binance max per call
        var hasMore = true
        var attempts = 0
        val maxBatches = 150 // Extended pagination up to 150,000 candles for multi-year high frequency backtests

        while (hasMore && attempts < maxBatches && currentStart < endTimeMs) {
            attempts++
            var lastError: Exception? = null
            var batchLoaded = false

            for (baseUrl in baseUrls) {
                val url = "$baseUrl/api/v3/klines?symbol=$symbol&interval=$interval&startTime=$currentStart&endTime=$endTimeMs&limit=$limit"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "TradeStrat-Backtester/1.0")
                    .build()

                try {
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            val code = response.code
                            val errorBody = response.body?.string() ?: ""
                            lastError = IOException("Binance API HTTP $code: $errorBody")
                            return@use
                        }

                        val body = response.body?.string() ?: throw IOException("Empty response from Binance API")
                        val jsonArray = JSONArray(body)

                        if (jsonArray.length() == 0) {
                            hasMore = false
                            batchLoaded = true
                            return@use
                        }

                        var lastTimestamp = 0L
                        for (i in 0 until jsonArray.length()) {
                            val kline = jsonArray.getJSONArray(i)
                            val openTime = kline.getLong(0)
                            val open = kline.getString(1).toDoubleOrNull() ?: 0.0
                            val high = kline.getString(2).toDoubleOrNull() ?: 0.0
                            val low = kline.getString(3).toDoubleOrNull() ?: 0.0
                            val close = kline.getString(4).toDoubleOrNull() ?: 0.0
                            val volume = kline.getString(5).toDoubleOrNull() ?: 0.0

                            allCandles.add(
                                Candle(
                                    timestamp = openTime,
                                    open = open,
                                    high = high,
                                    low = low,
                                    close = close,
                                    volume = volume
                                )
                            )
                            lastTimestamp = openTime
                        }

                        if (jsonArray.length() < limit || lastTimestamp >= endTimeMs) {
                            hasMore = false
                        } else {
                            // Step forward by 1ms from the last candle
                            currentStart = lastTimestamp + 1
                        }
                        batchLoaded = true
                    }
                } catch (e: Exception) {
                    lastError = e
                }

                if (batchLoaded) break
            }

            if (!batchLoaded) {
                val err = lastError
                if (err != null && allCandles.isEmpty()) {
                    return@withContext Result.failure(err)
                } else if (!batchLoaded) {
                    break
                }
            }
        }

        if (allCandles.isEmpty()) {
            return@withContext Result.failure(
                IOException("No market data returned from Binance for $symbol ($interval) between timestamps $startTimeMs and $endTimeMs")
            )
        }

        Result.success(allCandles)
    }
}
