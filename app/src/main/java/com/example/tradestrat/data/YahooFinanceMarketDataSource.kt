package com.example.tradestrat.data

import com.example.tradestrat.model.AssetCategory
import com.example.tradestrat.model.Candle
import com.example.tradestrat.model.MarketAsset
import com.example.tradestrat.model.Timeframe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class YahooFinanceMarketDataSource(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) : MarketDataSource {

    override val providerName: String = "Yahoo Finance Real Market Feed"
    override val supportsApiKey: Boolean = false

    override fun supportsAsset(asset: MarketAsset): Boolean {
        return asset.category in listOf(
            AssetCategory.FOREX,
            AssetCategory.COMMODITIES,
            AssetCategory.STOCKS,
            AssetCategory.INDICES,
            AssetCategory.CRYPTO
        )
    }

    private fun mapSymbol(asset: MarketAsset): String {
        return when (asset.category) {
            AssetCategory.FOREX -> {
                when (asset.symbol) {
                    "EUR/USD" -> "EURUSD=X"
                    "GBP/USD" -> "GBPUSD=X"
                    "USD/JPY" -> "JPY=X"
                    "AUD/USD" -> "AUDUSD=X"
                    else -> asset.symbol.replace("/", "") + "=X"
                }
            }
            AssetCategory.COMMODITIES -> {
                when (asset.symbol) {
                    "XAU/USD" -> "GC=F" // Gold Futures
                    "WTI/USD" -> "CL=F" // Crude Oil Futures
                    "XAG/USD" -> "SI=F" // Silver Futures
                    else -> "GC=F"
                }
            }
            AssetCategory.STOCKS -> asset.symbol
            AssetCategory.INDICES -> {
                when (asset.symbol) {
                    "SPX" -> "^GSPC"
                    "NDX" -> "^IXIC"
                    else -> asset.symbol
                }
            }
            AssetCategory.CRYPTO -> {
                when (asset.symbol) {
                    "BTC/USD", "BTC/USDT" -> "BTC-USD"
                    "ETH/USD", "ETH/USDT" -> "ETH-USD"
                    "SOL/USD", "SOL/USDT" -> "SOL-USD"
                    "BNB/USD", "BNB/USDT" -> "BNB-USD"
                    "XRP/USD", "XRP/USDT" -> "XRP-USD"
                    else -> asset.symbol.replace("/", "-")
                }
            }
        }
    }

    private fun mapTimeframe(tf: Timeframe): String {
        return when (tf) {
            Timeframe.M5 -> "5m"
            Timeframe.M15 -> "15m"
            Timeframe.M30 -> "30m"
            Timeframe.H1 -> "1h"
            Timeframe.H4 -> "1h" // We can aggregate or request 1h
            Timeframe.D1 -> "1d"
        }
    }

    override suspend fun fetchHistoricalCandles(
        asset: MarketAsset,
        timeframe: Timeframe,
        startTimeMs: Long,
        endTimeMs: Long,
        apiKey: String?
    ): Result<List<Candle>> = withContext(Dispatchers.IO) {
        val ySymbol = mapSymbol(asset)
        val interval = mapTimeframe(timeframe)
        val p1 = startTimeMs / 1000L
        val p2 = endTimeMs / 1000L

        val url = "https://query1.finance.yahoo.com/v8/finance/chart/$ySymbol?symbol=$ySymbol&period1=$p1&period2=$p2&interval=$interval&includePrePost=false&events=div%7Csplit"

        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (TradeStrat-Backtester; Android)")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val code = response.code
                    val err = response.body?.string() ?: ""
                    return@withContext Result.failure(
                        IOException("Yahoo Finance HTTP $code for $ySymbol: $err")
                    )
                }

                val body = response.body?.string() ?: throw IOException("Empty payload from Yahoo Finance")
                val root = JSONObject(body)
                val chart = root.getJSONObject("chart")
                val results = chart.optJSONArray("result")

                if (results == null || results.length() == 0) {
                    val errorObj = chart.optJSONObject("error")
                    val errorMsg = errorObj?.optString("description") ?: "No chart result returned"
                    return@withContext Result.failure(IOException(errorMsg))
                }

                val item = results.getJSONObject(0)
                val timestamps = item.optJSONArray("timestamp")
                val indicators = item.getJSONObject("indicators")
                val quote = indicators.getJSONArray("quote").getJSONObject(0)

                val opens = quote.optJSONArray("open")
                val highs = quote.optJSONArray("high")
                val lows = quote.optJSONArray("low")
                val closes = quote.optJSONArray("close")
                val volumes = quote.optJSONArray("volume")

                if (timestamps == null || opens == null || highs == null || lows == null || closes == null) {
                    return@withContext Result.failure(IOException("Missing candlestick arrays in Yahoo response"))
                }

                val candles = mutableListOf<Candle>()
                for (i in 0 until timestamps.length()) {
                    if (opens.isNull(i) || highs.isNull(i) || lows.isNull(i) || closes.isNull(i)) {
                        continue
                    }
                    val ts = timestamps.getLong(i) * 1000L
                    val o = opens.getDouble(i)
                    val h = highs.getDouble(i)
                    val l = lows.getDouble(i)
                    val c = closes.getDouble(i)
                    val v = if (volumes != null && !volumes.isNull(i)) volumes.optDouble(i, 0.0) else 0.0

                    if (o > 0 && h > 0 && l > 0 && c > 0) {
                        candles.add(
                            Candle(
                                timestamp = ts,
                                open = o,
                                high = h,
                                low = l,
                                close = c,
                                volume = v
                            )
                        )
                    }
                }

                if (candles.isEmpty()) {
                    return@withContext Result.failure(
                        IOException("Zero valid candles parsed from Yahoo Finance for $ySymbol")
                    )
                }

                val finalCandles = if (timeframe == Timeframe.H4) {
                    TimeframeAggregator.aggregate(candles, Timeframe.H1, Timeframe.H4)
                } else {
                    candles
                }

                Result.success(finalCandles)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
