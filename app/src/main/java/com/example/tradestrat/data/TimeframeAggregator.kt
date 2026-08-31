package com.example.tradestrat.data

import com.example.tradestrat.model.Candle
import com.example.tradestrat.model.Timeframe

object TimeframeAggregator {

    /**
     * Aggregates lower-timeframe candles into higher-timeframe candles (e.g. 1H -> 4H).
     * Calculates:
     * - Open = first candle open
     * - High = maximum high
     * - Low = minimum low
     * - Close = final candle close
     * - Volume = sum volume
     * Aligns timestamps to standard epoch bucket boundaries.
     */
    fun aggregate(
        candles: List<Candle>,
        sourceTimeframe: Timeframe,
        targetTimeframe: Timeframe
    ): List<Candle> {
        if (candles.isEmpty()) return emptyList()
        if (sourceTimeframe == targetTimeframe) return candles
        if (sourceTimeframe.minutes >= targetTimeframe.minutes) return candles

        val targetDurationMs = targetTimeframe.minutes * 60 * 1000L
        val sorted = candles.sortedBy { it.timestamp }

        // Group by aligned timestamp bucket
        val buckets = sorted.groupBy { (it.timestamp / targetDurationMs) * targetDurationMs }

        return buckets.map { (bucketTimestamp, bucketCandles) ->
            val firstCandle = bucketCandles.first()
            val lastCandle = bucketCandles.last()
            val maxHigh = bucketCandles.maxOf { it.high }
            val minLow = bucketCandles.minOf { it.low }
            val totalVolume = bucketCandles.sumOf { it.volume }

            Candle(
                timestamp = bucketTimestamp,
                open = firstCandle.open,
                high = maxHigh,
                low = minLow,
                close = lastCandle.close,
                volume = totalVolume
            )
        }.sortedBy { it.timestamp }
    }
}
