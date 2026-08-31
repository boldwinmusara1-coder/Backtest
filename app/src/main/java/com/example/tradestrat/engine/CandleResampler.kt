package com.example.tradestrat.engine

import com.example.tradestrat.model.Candle
import com.example.tradestrat.model.Timeframe
import kotlin.math.max
import kotlin.math.min

/**
 * Multi-Timeframe Candle Resampler and Indicator Causal Aligner.
 * Ensures zero look-ahead bias when aggregating lower-timeframe bars into higher-timeframe bars,
 * and when projecting higher-timeframe indicator states onto lower-timeframe execution bars.
 */
object CandleResampler {

    /**
     * Resamples smaller timeframe candles into higher timeframe candles.
     * Buckets are calculated strictly by timestamp intervals.
     */
    fun resample(candles: List<Candle>, targetTimeframe: Timeframe): List<Candle> {
        if (candles.isEmpty()) return emptyList()

        val bucketDurationMs = targetTimeframe.minutes * 60 * 1000L
        val sorted = candles.sortedBy { it.timestamp }
        val buckets = LinkedHashMap<Long, MutableList<Candle>>()

        for (c in sorted) {
            val bucketStart = (c.timestamp / bucketDurationMs) * bucketDurationMs
            buckets.getOrPut(bucketStart) { mutableListOf() }.add(c)
        }

        val resampled = mutableListOf<Candle>()
        for ((bucketTimestamp, bucketCandles) in buckets) {
            if (bucketCandles.isEmpty()) continue
            val open = bucketCandles.first().open
            var high = Double.NEGATIVE_INFINITY
            var low = Double.POSITIVE_INFINITY
            val close = bucketCandles.last().close
            var volume = 0.0

            for (bc in bucketCandles) {
                if (bc.high > high) high = bc.high
                if (bc.low < low) low = bc.low
                volume += bc.volume
            }

            resampled.add(
                Candle(
                    timestamp = bucketTimestamp,
                    open = open,
                    high = high,
                    low = low,
                    close = close,
                    volume = volume
                )
            )
        }

        return resampled
    }

    /**
     * Causally aligns a higher-timeframe indicator series onto a lower-timeframe candle timeline.
     * For any lower-timeframe candle at timestamp `t`, this returns the indicator value of the
     * LAST FULLY CLOSED higher-timeframe candle (where `htfCandle.timestamp + htfDurationMs <= t`).
     * Prevents look-ahead bias where lower-timeframe bars access incomplete higher-timeframe bars.
     */
    fun alignHigherTimeframeIndicator(
        lowerCandles: List<Candle>,
        higherCandles: List<Candle>,
        higherTimeframe: Timeframe,
        higherIndicatorValues: List<Double?>
    ): List<Double?> {
        if (lowerCandles.isEmpty() || higherCandles.isEmpty() || higherIndicatorValues.isEmpty()) {
            return List(lowerCandles.size) { null }
        }

        val htfDurationMs = higherTimeframe.minutes * 60 * 1000L
        val result = ArrayList<Double?>(lowerCandles.size)

        var htfIndex = 0
        var latestClosedHtfIndicator: Double? = null

        for (ltf in lowerCandles) {
            // Advance higher timeframe pointer as higher candles close strictly before or at ltf.timestamp
            while (htfIndex < higherCandles.size) {
                val htfCandle = higherCandles[htfIndex]
                val htfCloseTime = htfCandle.timestamp + htfDurationMs

                if (htfCloseTime <= ltf.timestamp) {
                    if (htfIndex < higherIndicatorValues.size) {
                        latestClosedHtfIndicator = higherIndicatorValues[htfIndex]
                    }
                    htfIndex++
                } else {
                    break
                }
            }

            result.add(latestClosedHtfIndicator)
        }

        return result
    }
}
