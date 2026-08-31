package com.example.tradestrat.data

import com.example.tradestrat.model.*
import java.util.Random
import kotlin.math.*

/**
 * Clean historical OHLCV market-data provider and deterministic sample dataset generator.
 * Provides consistent, reproducible, high-integrity candle datasets across multiple assets,
 * timeframes, and arbitrary start/end dates for end-to-end backtesting pipeline execution and validation.
 */
object DeterministicHistoricalDataProvider {

    /**
     * Generates a deterministic historical OHLCV dataset for any asset and timeframe across an arbitrary date range.
     */
    fun generateCandles(
        asset: MarketAsset,
        timeframe: Timeframe,
        startTimeMs: Long,
        endTimeMs: Long,
        seed: Long = 42L + asset.symbol.hashCode() + timeframe.minutes
    ): List<Candle> {
        val barDurationMs = timeframe.minutes * 60 * 1000L
        if (endTimeMs <= startTimeMs || barDurationMs <= 0) return emptyList()

        val calculatedCount = ((endTimeMs - startTimeMs) / barDurationMs).toInt().coerceIn(20, 150000)
        return generateCandlesCount(
            asset = asset,
            timeframe = timeframe,
            startTimeMs = startTimeMs,
            count = calculatedCount,
            seed = seed
        )
    }

    /**
     * Generates a fixed count of deterministic candles starting from [startTimeMs].
     */
    fun generateCandlesCount(
        asset: MarketAsset,
        timeframe: Timeframe,
        startTimeMs: Long,
        count: Int = 300,
        seed: Long = 1001L
    ): List<Candle> {
        val candles = mutableListOf<Candle>()
        val barDurationMs = timeframe.minutes * 60 * 1000L
        val rng = Random(seed)

        var currentPrice = asset.basePrice
        var currentTs = startTimeMs

        // Asset-specific volatility characteristics
        val volatilityPct = when (asset.category) {
            AssetCategory.CRYPTO -> 0.015
            AssetCategory.STOCKS -> 0.010
            AssetCategory.INDICES -> 0.006
            AssetCategory.FOREX -> 0.003
            AssetCategory.COMMODITIES -> 0.008
        }

        var regimeTrend = 0.0
        var regimeCounter = 0

        for (i in 0 until count) {
            if (regimeCounter <= 0) {
                regimeCounter = 25 + rng.nextInt(35)
                regimeTrend = when (rng.nextInt(3)) {
                    0 -> 0.002  // Bullish run
                    1 -> -0.002 // Bearish run
                    else -> 0.0 // Ranging
                }
            }
            regimeCounter--

            // Periodic macro cycle oscillation
            val macroWave = sin(i * 0.04) * (currentPrice * volatilityPct * 0.4)
            val microNoise = (rng.nextDouble() * 2.0 - 1.0) * (currentPrice * volatilityPct)
            val trendDrift = regimeTrend * currentPrice

            val open = currentPrice
            var close = open + trendDrift + macroWave + microNoise
            if (close <= 0.01) close = 0.01

            val upperWick = rng.nextDouble() * (currentPrice * volatilityPct * 0.8)
            val lowerWick = rng.nextDouble() * (currentPrice * volatilityPct * 0.8)

            val high = max(open, close) + upperWick
            val low = max(0.001, min(open, close) - lowerWick)

            val volumeBase = when (asset.category) {
                AssetCategory.CRYPTO -> 5000.0
                AssetCategory.STOCKS -> 50000.0
                AssetCategory.INDICES -> 100000.0
                AssetCategory.FOREX -> 20000.0
                AssetCategory.COMMODITIES -> 15000.0
            }
            val volumeMultiplier = 0.6 + rng.nextDouble() * 0.8 + (abs(close - open) / (currentPrice * volatilityPct)).coerceAtMost(2.5)
            val volume = volumeBase * volumeMultiplier

            candles.add(
                Candle(
                    timestamp = currentTs,
                    open = roundToDecimals(open, asset.defaultDecimals),
                    high = roundToDecimals(high, asset.defaultDecimals),
                    low = roundToDecimals(low, asset.defaultDecimals),
                    close = roundToDecimals(close, asset.defaultDecimals),
                    volume = roundToDecimals(volume, 2)
                )
            )

            currentPrice = close
            currentTs += barDurationMs
        }

        return candles
    }

    private fun roundToDecimals(value: Double, decimals: Int): Double {
        val factor = 10.0.pow(decimals.toDouble())
        return kotlin.math.round(value * factor) / factor
    }
}
