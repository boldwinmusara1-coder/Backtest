package com.example.tradestrat.engine

import com.example.tradestrat.model.Candle
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object IndicatorCalculators {

    /**
     * Simple Moving Average (SMA)
     */
    fun calculateSMA(candles: List<Candle>, period: Int): List<Double?> {
        if (period <= 0 || candles.isEmpty()) return List(candles.size) { null }
        val result = ArrayList<Double?>(candles.size)
        var runningSum = 0.0

        for (i in candles.indices) {
            runningSum += candles[i].close
            if (i >= period) {
                runningSum -= candles[i - period].close
            }
            if (i >= period - 1) {
                result.add(runningSum / period)
            } else {
                result.add(null)
            }
        }
        return result
    }

    /**
     * Exponential Moving Average (EMA)
     */
    fun calculateEMA(candles: List<Candle>, period: Int): List<Double?> {
        if (period <= 0 || candles.isEmpty()) return List(candles.size) { null }
        val result = ArrayList<Double?>(candles.size)
        val multiplier = 2.0 / (period + 1.0)
        var prevEma: Double? = null

        for (i in candles.indices) {
            val close = candles[i].close
            if (i < period - 1) {
                result.add(null)
            } else if (i == period - 1) {
                // Seed with SMA
                var sum = 0.0
                for (j in 0 until period) {
                    sum += candles[j].close
                }
                val initialEma = sum / period
                prevEma = initialEma
                result.add(initialEma)
            } else {
                val currentEma = (close - (prevEma ?: close)) * multiplier + (prevEma ?: close)
                prevEma = currentEma
                result.add(currentEma)
            }
        }
        return result
    }

    /**
     * Relative Strength Index (RSI - Wilder's smoothing)
     */
    fun calculateRSI(candles: List<Candle>, period: Int = 14): List<Double?> {
        if (period <= 0 || candles.size <= period) return List(candles.size) { null }
        val result = ArrayList<Double?>(candles.size)

        var avgGain = 0.0
        var avgLoss = 0.0

        for (i in candles.indices) {
            if (i == 0) {
                result.add(null)
                continue
            }

            val change = candles[i].close - candles[i - 1].close
            val gain = if (change > 0) change else 0.0
            val loss = if (change < 0) -change else 0.0

            if (i < period) {
                avgGain += gain
                avgLoss += loss
                result.add(null)
            } else if (i == period) {
                avgGain = (avgGain + gain) / period
                avgLoss = (avgLoss + loss) / period
                val rs = if (avgLoss == 0.0) 100.0 else avgGain / avgLoss
                val rsi = 100.0 - (100.0 / (1.0 + rs))
                result.add(rsi)
            } else {
                avgGain = (avgGain * (period - 1) + gain) / period
                avgLoss = (avgLoss * (period - 1) + loss) / period
                val rs = if (avgLoss == 0.0) 100.0 else avgGain / avgLoss
                val rsi = 100.0 - (100.0 / (1.0 + rs))
                result.add(rsi)
            }
        }
        return result
    }

    /**
     * Moving Average Convergence Divergence (MACD)
     */
    data class MacdOutput(
        val macdLine: List<Double?>,
        val signalLine: List<Double?>,
        val histogram: List<Double?>
    )

    fun calculateMACD(
        candles: List<Candle>,
        fastPeriod: Int = 12,
        slowPeriod: Int = 26,
        signalPeriod: Int = 9
    ): MacdOutput {
        val fastEma = calculateEMA(candles, fastPeriod)
        val slowEma = calculateEMA(candles, slowPeriod)

        val macdLine = ArrayList<Double?>(candles.size)
        for (i in candles.indices) {
            val f = fastEma[i]
            val s = slowEma[i]
            if (f != null && s != null) {
                macdLine.add(f - s)
            } else {
                macdLine.add(null)
            }
        }

        // Calculate EMA of the valid macdLine values
        val signalLine = ArrayList<Double?>(candles.size)
        val histogram = ArrayList<Double?>(candles.size)

        val validMacdValues = mutableListOf<Pair<Int, Double>>()
        for (i in macdLine.indices) {
            val v = macdLine[i]
            if (v != null) {
                validMacdValues.add(Pair(i, v))
            }
        }

        if (validMacdValues.size < signalPeriod) {
            return MacdOutput(macdLine, List(candles.size) { null }, List(candles.size) { null })
        }

        // Fill initial nulls for signal
        for (i in candles.indices) {
            signalLine.add(null)
            histogram.add(null)
        }

        val multiplier = 2.0 / (signalPeriod + 1.0)
        var sum = 0.0
        for (k in 0 until signalPeriod) {
            sum += validMacdValues[k].second
        }
        var prevSignal = sum / signalPeriod
        val firstSignalIdx = validMacdValues[signalPeriod - 1].first
        signalLine[firstSignalIdx] = prevSignal
        histogram[firstSignalIdx] = (macdLine[firstSignalIdx] ?: 0.0) - prevSignal

        for (k in signalPeriod until validMacdValues.size) {
            val (idx, value) = validMacdValues[k]
            val currentSignal = (value - prevSignal) * multiplier + prevSignal
            prevSignal = currentSignal
            signalLine[idx] = currentSignal
            histogram[idx] = value - currentSignal
        }

        return MacdOutput(macdLine, signalLine, histogram)
    }

    /**
     * Bollinger Bands (SMA, Upper, Lower, Bandwidth)
     */
    data class BollingerBandsOutput(
        val upper: List<Double?>,
        val middle: List<Double?>,
        val lower: List<Double?>
    )

    fun calculateBollingerBands(
        candles: List<Candle>,
        period: Int = 20,
        stdDevMultiplier: Double = 2.0
    ): BollingerBandsOutput {
        val middle = calculateSMA(candles, period)
        val upper = ArrayList<Double?>(candles.size)
        val lower = ArrayList<Double?>(candles.size)

        for (i in candles.indices) {
            val mid = middle[i]
            if (mid == null || i < period - 1) {
                upper.add(null)
                lower.add(null)
            } else {
                var sumSq = 0.0
                for (j in 0 until period) {
                    val diff = candles[i - j].close - mid
                    sumSq += diff * diff
                }
                val stdDev = sqrt(sumSq / period)
                upper.add(mid + stdDevMultiplier * stdDev)
                lower.add(mid - stdDevMultiplier * stdDev)
            }
        }
        return BollingerBandsOutput(upper, middle, lower)
    }

    /**
     * Average True Range (ATR)
     */
    fun calculateATR(candles: List<Candle>, period: Int = 14): List<Double?> {
        if (candles.isEmpty() || period <= 0) return List(candles.size) { null }
        val result = ArrayList<Double?>(candles.size)
        val trList = ArrayList<Double>(candles.size)

        for (i in candles.indices) {
            val current = candles[i]
            val tr = if (i == 0) {
                current.high - current.low
            } else {
                val prevClose = candles[i - 1].close
                max(current.high - current.low, max(abs(current.high - prevClose), abs(current.low - prevClose)))
            }
            trList.add(tr)

            if (i < period - 1) {
                result.add(null)
            } else if (i == period - 1) {
                var sum = 0.0
                for (j in 0 until period) sum += trList[j]
                result.add(sum / period)
            } else {
                val prevAtr = result[i - 1] ?: (trList[i])
                val currentAtr = (prevAtr * (period - 1) + tr) / period
                result.add(currentAtr)
            }
        }
        return result
    }

    /**
     * Supertrend Indicator
     */
    fun calculateSupertrend(
        candles: List<Candle>,
        atrPeriod: Int = 10,
        multiplier: Double = 3.0
    ): List<Double?> {
        val atr = calculateATR(candles, atrPeriod)
        val result = ArrayList<Double?>(candles.size)

        var trend = 1 // 1 for UP, -1 for DOWN
        var upperBand = 0.0
        var lowerBand = 0.0

        for (i in candles.indices) {
            val currentAtr = atr[i]
            if (currentAtr == null) {
                result.add(null)
                continue
            }

            val hl2 = (candles[i].high + candles[i].low) / 2.0
            var currentUpper = hl2 + multiplier * currentAtr
            var currentLower = hl2 - multiplier * currentAtr

            if (i > 0 && atr[i - 1] != null) {
                val prevClose = candles[i - 1].close
                if (prevClose > lowerBand) {
                    currentLower = max(currentLower, lowerBand)
                }
                if (prevClose < upperBand) {
                    currentUpper = min(currentUpper, upperBand)
                }
            }

            upperBand = currentUpper
            lowerBand = currentLower

            val close = candles[i].close
            if (trend == 1) {
                if (close < lowerBand) {
                    trend = -1
                    result.add(upperBand)
                } else {
                    result.add(lowerBand)
                }
            } else {
                if (close > upperBand) {
                    trend = 1
                    result.add(lowerBand)
                } else {
                    result.add(upperBand)
                }
            }
        }
        return result
    }

    /**
     * Donchian Channels (Turtle Breakout)
     */
    data class DonchianOutput(
        val upper: List<Double?>,
        val lower: List<Double?>
    )

    fun calculateDonchian(candles: List<Candle>, period: Int = 20): DonchianOutput {
        val upper = ArrayList<Double?>(candles.size)
        val lower = ArrayList<Double?>(candles.size)

        for (i in candles.indices) {
            if (i < period) {
                upper.add(null)
                lower.add(null)
            } else {
                var maxHigh = Double.NEGATIVE_INFINITY
                var minLow = Double.POSITIVE_INFINITY
                for (j in 1..period) {
                    val c = candles[i - j]
                    if (c.high > maxHigh) maxHigh = c.high
                    if (c.low < minLow) minLow = c.low
                }
                upper.add(maxHigh)
                lower.add(minLow)
            }
        }
        return DonchianOutput(upper, lower)
    }
}
