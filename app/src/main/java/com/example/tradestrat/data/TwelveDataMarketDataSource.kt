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
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class TwelveDataMarketDataSource(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) : MarketDataSource {

    override val providerName: String = "Twelve Data REST API"
    override val supportsApiKey: Boolean = true

    override fun supportsAsset(asset: MarketAsset): Boolean {
        return true
    }

    private fun mapTimeframe(tf: Timeframe): String {
        return when (tf) {
            Timeframe.M5 -> "5min"
            Timeframe.M15 -> "15min"
            Timeframe.M30 -> "30min"
            Timeframe.H1 -> "1h"
            Timeframe.H4 -> "4h"
            Timeframe.D1 -> "1day"
        }
    }

    override suspend fun fetchHistoricalCandles(
        asset: MarketAsset,
        timeframe: Timeframe,
        startTimeMs: Long,
        endTimeMs: Long,
        apiKey: String?
    ): Result<List<Candle>> = withContext(Dispatchers.IO) {
        val key = apiKey?.trim()
        if (key.isNullOrEmpty()) {
            return@withContext Result.failure(
                IllegalArgumentException("Twelve Data requires an API key. Please enter your MARKET_DATA_API_KEY in API Settings or use Binance / Public Market feeds.")
            )
        }

        val symbol = asset.symbol
        val interval = mapTimeframe(timeframe)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val startStr = dateFormat.format(Date(startTimeMs))
        val endStr = dateFormat.format(Date(endTimeMs))

        val url = "https://api.twelvedata.com/time_series?symbol=$symbol&interval=$interval&start_date=$startStr&end_date=$endStr&outputsize=5000&apikey=$key"

        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "TradeStrat-Backtester/1.0")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val code = response.code
                    return@withContext Result.failure(IOException("Twelve Data HTTP $code: ${response.body?.string()}"))
                }

                val body = response.body?.string() ?: throw IOException("Empty payload from Twelve Data")
                val root = JSONObject(body)

                if (root.optString("status") == "error") {
                    val message = root.optString("message", "API Error from Twelve Data")
                    return@withContext Result.failure(IOException(message))
                }

                val values = root.optJSONArray("values")
                if (values == null || values.length() == 0) {
                    return@withContext Result.failure(IOException("No candle values returned for $symbol"))
                }

                val candles = mutableListOf<Candle>()
                val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val dateOnlyParser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }

                for (i in 0 until values.length()) {
                    val item = values.getJSONObject(i)
                    val dtStr = item.getString("datetime")
                    val parsedDate = try {
                        if (dtStr.length == 10) dateOnlyParser.parse(dtStr) else parser.parse(dtStr)
                    } catch (e: Exception) {
                        null
                    }
                    val ts = parsedDate?.time ?: continue
                    val o = item.getString("open").toDoubleOrNull() ?: continue
                    val h = item.getString("high").toDoubleOrNull() ?: continue
                    val l = item.getString("low").toDoubleOrNull() ?: continue
                    val c = item.getString("close").toDoubleOrNull() ?: continue
                    val v = item.optString("volume", "0").toDoubleOrNull() ?: 0.0

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

                Result.success(candles.sortedBy { it.timestamp })
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
