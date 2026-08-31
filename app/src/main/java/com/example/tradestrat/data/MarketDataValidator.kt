package com.example.tradestrat.data

import com.example.tradestrat.model.AssetCategory
import com.example.tradestrat.model.Candle
import com.example.tradestrat.model.Timeframe

import java.security.MessageDigest

data class ComprehensiveValidationReport(
    val datasetName: String,
    val symbol: String,
    val timeframe: String,
    val firstTimestamp: Long,
    val lastTimestamp: Long,
    val totalCandles: Int,
    val timezone: String,
    val missingCandlesCount: Int,
    val duplicateTimestampsCount: Int,
    val invalidOhlcCandlesCount: Int,
    val isChronological: Boolean,
    val hasNanOrInfinite: Boolean,
    val isTimeframeIntervalConsistent: Boolean,
    val isValid: Boolean,
    val dataHash: String,
    val violations: List<String>
)

data class DataValidationReport(
    val isValid: Boolean,
    val totalCandles: Int,
    val firstCandleTimestamp: Long,
    val lastCandleTimestamp: Long,
    val duplicatesRemovedCount: Int,
    val unexpectedGapsCount: Int = 0,
    val expectedGapsCount: Int = 0,
    val lastClosedCandleTimestamp: Long = 0L,
    val violations: List<String>,
    val dataHash: String = ""
)

object MarketDataValidator {

    /**
     * Computes a deterministic SHA-256 hash representing the exact OHLCV candle sequence.
     * Proves data identity across backtests.
     */
    fun computeDataHash(candles: List<Candle>): String {
        if (candles.isEmpty()) return "0".repeat(64)
        val md = MessageDigest.getInstance("SHA-256")
        for (c in candles) {
            val record = "${c.timestamp},${c.open},${c.high},${c.low},${c.close},${c.volume}\n"
            md.update(record.toByteArray(Charsets.UTF_8))
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Strict dataset validator for real historical data pipelines.
     * Enforces that:
     * - Timestamps are strictly chronological and unique
     * - OHLC values are finite, numeric, and non-negative
     * - High >= max(Open, Close, Low) and Low <= min(Open, Close, High)
     * - Volume >= 0
     * - Candle intervals match declared timeframe without impossible intervals
     * Returns a ComprehensiveValidationReport with pass/fail status and dataHash.
     */
    fun validateDatasetStrict(
        candles: List<Candle>,
        datasetName: String = "Market Dataset",
        symbol: String = "BTC/USD",
        timeframe: Timeframe = Timeframe.D1,
        assetCategory: AssetCategory = AssetCategory.CRYPTO,
        timezone: String = "UTC"
    ): ComprehensiveValidationReport {
        val violations = mutableListOf<String>()
        if (candles.isEmpty()) {
            return ComprehensiveValidationReport(
                datasetName = datasetName,
                symbol = symbol,
                timeframe = timeframe.label,
                firstTimestamp = 0L,
                lastTimestamp = 0L,
                totalCandles = 0,
                timezone = timezone,
                missingCandlesCount = 0,
                duplicateTimestampsCount = 0,
                invalidOhlcCandlesCount = 0,
                isChronological = false,
                hasNanOrInfinite = false,
                isTimeframeIntervalConsistent = false,
                isValid = false,
                dataHash = computeDataHash(emptyList()),
                violations = listOf("Dataset contains zero candles.")
            )
        }

        var isChronological = true
        var duplicateCount = 0
        var invalidOhlcCount = 0
        var hasNanOrInf = false
        val seenTimestamps = mutableSetOf<Long>()
        val expectedIntervalMs = timeframe.minutes * 60 * 1000L
        var inconsistentIntervalsCount = 0
        var unexpectedGaps = 0

        for (i in candles.indices) {
            val c = candles[i]

            // NaN / Infinite checks
            if (c.open.isNaN() || c.high.isNaN() || c.low.isNaN() || c.close.isNaN() || c.volume.isNaN() ||
                c.open.isInfinite() || c.high.isInfinite() || c.low.isInfinite() || c.close.isInfinite() || c.volume.isInfinite()
            ) {
                hasNanOrInf = true
                invalidOhlcCount++
                violations.add("Bar $i contains NaN or Infinite value.")
                continue
            }

            // Positive / Non-zero price check
            if (c.open <= 0.0 || c.high <= 0.0 || c.low <= 0.0 || c.close <= 0.0 || c.volume < 0.0) {
                invalidOhlcCount++
                violations.add("Bar $i contains non-positive price or negative volume: O=${c.open}, H=${c.high}, L=${c.low}, C=${c.close}, V=${c.volume}")
            }

            // OHLC bound validity
            val validHigh = c.high >= c.open && c.high >= c.close && c.high >= c.low
            val validLow = c.low <= c.open && c.low <= c.close && c.low <= c.high
            if (!validHigh || !validLow) {
                invalidOhlcCount++
                violations.add("Bar $i violates OHLC bounding: High (${c.high}) must be >= Open (${c.open}), Close (${c.close}), Low (${c.low}). Low (${c.low}) must be <= Open, Close, High.")
            }

            // Chronology & Uniqueness
            if (seenTimestamps.contains(c.timestamp)) {
                duplicateCount++
                violations.add("Duplicate timestamp detected at index $i: ${c.timestamp}")
            }
            seenTimestamps.add(c.timestamp)

            if (i > 0) {
                val prevTs = candles[i - 1].timestamp
                if (c.timestamp < prevTs) {
                    isChronological = false
                    violations.add("Non-chronological timestamp order at index $i: current ${c.timestamp} < previous $prevTs")
                } else if (c.timestamp == prevTs) {
                    isChronological = false
                } else {
                    val delta = c.timestamp - prevTs
                    if (delta < expectedIntervalMs / 2) {
                        inconsistentIntervalsCount++
                        violations.add("Inconsistent timeframe interval at index $i: bar spacing ${delta / 1000}s is significantly smaller than declared timeframe ${timeframe.label} (${expectedIntervalMs / 1000}s).")
                    } else if (delta > expectedIntervalMs + 5000L) {
                        unexpectedGaps++
                    }
                }
            }
        }

        val minCandlesMet = candles.size >= 10
        if (!minCandlesMet) {
            violations.add("Insufficient candle count: ${candles.size} bars (minimum 10 required).")
        }

        val isValid = isChronological &&
                duplicateCount == 0 &&
                invalidOhlcCount == 0 &&
                !hasNanOrInf &&
                inconsistentIntervalsCount == 0 &&
                minCandlesMet

        val dataHash = computeDataHash(candles)

        return ComprehensiveValidationReport(
            datasetName = datasetName,
            symbol = symbol,
            timeframe = timeframe.label,
            firstTimestamp = candles.first().timestamp,
            lastTimestamp = candles.last().timestamp,
            totalCandles = candles.size,
            timezone = timezone,
            missingCandlesCount = unexpectedGaps,
            duplicateTimestampsCount = duplicateCount,
            invalidOhlcCandlesCount = invalidOhlcCount,
            isChronological = isChronological,
            hasNanOrInfinite = hasNanOrInf,
            isTimeframeIntervalConsistent = inconsistentIntervalsCount == 0,
            isValid = isValid,
            dataHash = dataHash,
            violations = violations
        )
    }

    /**
     * Validates and cleanses raw market candle data.
     * Ensures strict chronological sorting, removes duplicate timestamps,
     * filters out incomplete/future candles, and verifies that all OHLC relationships hold true.
     * Classifies gaps into EXPECTED GAP (weekends/market closures for non-24/7 markets) and UNEXPECTED DATA GAP.
     * For 24/7 markets (e.g. Crypto), weekend gaps are correctly identified as UNEXPECTED DATA GAP.
     */
    fun validateAndClean(
        rawCandles: List<Candle>,
        timeframe: Timeframe,
        assetCategory: AssetCategory? = null,
        expectedStartTimeMs: Long? = null,
        expectedEndTimeMs: Long? = null,
        currentTimeMs: Long = System.currentTimeMillis()
    ): Pair<List<Candle>, DataValidationReport> {
        val violations = mutableListOf<String>()

        if (rawCandles.isEmpty()) {
            return Pair(
                emptyList(),
                DataValidationReport(
                    isValid = false,
                    totalCandles = 0,
                    firstCandleTimestamp = 0L,
                    lastCandleTimestamp = 0L,
                    duplicatesRemovedCount = 0,
                    unexpectedGapsCount = 0,
                    expectedGapsCount = 0,
                    lastClosedCandleTimestamp = 0L,
                    violations = listOf("Candle dataset is empty. No historical data received from API.")
                )
            )
        }

        // 1. Filter out incomplete / future candles (close time must be <= currentTimeMs)
        val timeframeDurationMs = timeframe.minutes * 60 * 1000L
        val closedCandles = rawCandles.filter { c ->
            val candleCloseTime = c.timestamp + timeframeDurationMs
            candleCloseTime <= currentTimeMs + 60000L // 1-minute clock skew tolerance
        }

        val incompleteFiltered = rawCandles.size - closedCandles.size
        if (incompleteFiltered > 0) {
            violations.add("Filtered out $incompleteFiltered incomplete / unclosed current bar(s).")
        }

        // 2. Remove duplicate timestamps while keeping the latest entry
        val initialCount = closedCandles.size
        val deduplicated = closedCandles.distinctBy { it.timestamp }
        val duplicatesRemoved = initialCount - deduplicated.size
        if (duplicatesRemoved > 0) {
            violations.add("Detected and purged $duplicatesRemoved duplicate candle timestamp(s).")
        }

        // 3. Sort chronologically
        val sorted = deduplicated.sortedBy { it.timestamp }

        // 4. Validate OHLC price integrity and positive volume
        val cleanCandles = mutableListOf<Candle>()
        var ohlcViolationCount = 0

        for (i in sorted.indices) {
            val c = sorted[i]

            // Check non-negative / non-zero prices
            if (c.open <= 0 || c.high <= 0 || c.low <= 0 || c.close <= 0) {
                ohlcViolationCount++
                continue
            }

            // High must be >= max(open, close, low)
            // Low must be <= min(open, close, high)
            val isValidHigh = c.high >= c.open && c.high >= c.close && c.high >= c.low
            val isValidLow = c.low <= c.open && c.low <= c.close && c.low <= c.high
            val isValidVolume = c.volume >= 0.0

            if (!isValidHigh || !isValidLow || !isValidVolume) {
                ohlcViolationCount++
                continue
            }

            cleanCandles.add(c)
        }

        if (ohlcViolationCount > 0) {
            violations.add("Filtered out $ohlcViolationCount invalid candle(s) violating OHLC bounds (High >= Open/Close/Low, Low <= Open/Close/High).")
        }

        // 5. Gap Classification: EXPECTED GAP vs UNEXPECTED DATA GAP
        var unexpectedGaps = 0
        var expectedGaps = 0
        val is247Market = assetCategory == AssetCategory.CRYPTO

        if (cleanCandles.size >= 2) {
            for (i in 1 until cleanCandles.size) {
                val prevTs = cleanCandles[i - 1].timestamp
                val currTs = cleanCandles[i].timestamp
                val deltaMs = currTs - prevTs

                if (deltaMs > timeframeDurationMs + 5000L) {
                    if (is247Market) {
                        // 24/7 Crypto markets never close on weekends or overnight
                        unexpectedGaps++
                        val gapHours = deltaMs / (1000.0 * 3600.0)
                        violations.add("UNEXPECTED DATA GAP: Detected %.1fh gap in 24/7 Crypto market between %d and %d.".format(
                            gapHours, prevTs, currTs
                        ))
                    } else {
                        // Traditional financial markets (FX, Equities, Indices, Commodities)
                        val isWeekendClosure = deltaMs in (36 * 3600 * 1000L)..(72 * 3600 * 1000L)
                        val isOvernightClosure = (assetCategory == AssetCategory.STOCKS || assetCategory == AssetCategory.INDICES) &&
                                deltaMs in (10 * 3600 * 1000L)..(20 * 3600 * 1000L)

                        if (isWeekendClosure || isOvernightClosure) {
                            expectedGaps++
                            val gapType = if (isWeekendClosure) "Weekend closure" else "Overnight closure"
                            violations.add("EXPECTED GAP: %s (%.1fh) between %d and %d.".format(
                                gapType, deltaMs / (1000.0 * 3600.0), prevTs, currTs
                            ))
                        } else {
                            unexpectedGaps++
                            violations.add("UNEXPECTED DATA GAP: Detected %.1fh gap during regular trading hours between %d and %d.".format(
                                deltaMs / (1000.0 * 3600.0), prevTs, currTs
                            ))
                        }
                    }
                }
            }
        }

        // 6. Verify minimum historical candle depth
        if (cleanCandles.size < 10) {
            violations.add("Insufficient historical depth: got ${cleanCandles.size} clean candles, minimum 10 required for indicator calculation.")
        }

        val isValid = cleanCandles.size >= 10

        val lastClosedTimestamp = cleanCandles.lastOrNull()?.let { it.timestamp + timeframeDurationMs } ?: 0L

        val report = DataValidationReport(
            isValid = isValid,
            totalCandles = cleanCandles.size,
            firstCandleTimestamp = cleanCandles.firstOrNull()?.timestamp ?: 0L,
            lastCandleTimestamp = cleanCandles.lastOrNull()?.timestamp ?: 0L,
            duplicatesRemovedCount = duplicatesRemoved,
            unexpectedGapsCount = unexpectedGaps,
            expectedGapsCount = expectedGaps,
            lastClosedCandleTimestamp = lastClosedTimestamp,
            violations = violations,
            dataHash = computeDataHash(cleanCandles)
        )

        return Pair(cleanCandles, report)
    }
}
