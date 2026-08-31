package com.example.tradestrat.data

import com.example.tradestrat.model.Candle
import com.example.tradestrat.model.MarketAsset
import com.example.tradestrat.model.Timeframe
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Isolated deterministic generator strictly for development demo previews.
 * NEVER used as a fallback for real historical backtests.
 */
object DemoMarketDataProvider {

    fun generateDemoCandles(
        asset: MarketAsset,
        timeframe: Timeframe,
        startTimeMs: Long,
        endTimeMs: Long,
        count: Int = 300
    ): List<Candle> {
        val candles = mutableListOf<Candle>()
        val barDurationMs = timeframe.minutes * 60 * 1000L
        var currentPrice = asset.basePrice
        var currentTs = max(startTimeMs, endTimeMs - (count * barDurationMs))

        for (i in 0 until count) {
            val wave = sin(i * 0.05) * (currentPrice * 0.008)
            val open = currentPrice
            val close = open + wave + ((i % 5 - 2) * (currentPrice * 0.003))
            val high = max(open, close) + (currentPrice * 0.004)
            val low = min(open, close) - (currentPrice * 0.004)
            val volume = 1000.0 + (i % 20) * 150.0

            candles.add(
                Candle(
                    timestamp = currentTs,
                    open = max(0.01, open),
                    high = max(0.01, high),
                    low = max(0.01, low),
                    close = max(0.01, close),
                    volume = volume
                )
            )

            currentPrice = close
            currentTs += barDurationMs
        }

        return candles
    }
}
