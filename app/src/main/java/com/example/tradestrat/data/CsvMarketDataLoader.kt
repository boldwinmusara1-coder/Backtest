package com.example.tradestrat.data

import android.content.Context
import com.example.tradestrat.model.AssetCategory
import com.example.tradestrat.model.Candle
import com.example.tradestrat.model.Timeframe
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Universal High-Integrity CSV Market Data Importer.
 * Supports standard formats:
 * - timestamp,open,high,low,close,volume
 * - date,open,high,low,close,volume (with auto-parsing for ISO-8601, yyyy-MM-dd HH:mm:ss, yyyy-MM-dd)
 * - epoch milliseconds or epoch seconds
 */
object CsvMarketDataLoader {

    private val DATE_FORMATS = listOf(
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
        SimpleDateFormat("MM/dd/yyyy HH:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
        SimpleDateFormat("MM/dd/yyyy", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    )

    /**
     * Parses raw CSV string data into validated [Candle] objects with comprehensive strict validation.
     */
    fun parseCsv(
        csvContent: String,
        timeframe: Timeframe = Timeframe.D1,
        assetCategory: AssetCategory = AssetCategory.CRYPTO,
        symbol: String = "BTC/USD"
    ): Pair<List<Candle>, DataValidationReport> {
        val lines = csvContent.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) {
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
                    violations = listOf("Empty CSV content provided."),
                    dataHash = MarketDataValidator.computeDataHash(emptyList())
                )
            )
        }

        // Header detection
        val header = lines.first().lowercase()
        val hasHeader = header.contains("open") || header.contains("close") || header.contains("time") || header.contains("date")
        val dataLines = if (hasHeader) lines.drop(1) else lines

        var timeIdx = 0
        var openIdx = 1
        var highIdx = 2
        var lowIdx = 3
        var closeIdx = 4
        var volIdx = 5

        val violations = mutableListOf<String>()

        if (hasHeader) {
            val cols = header.split(",").map { it.trim().lowercase() }
            timeIdx = cols.indexOfFirst { it.contains("time") || it.contains("date") || it == "ts" }.takeIf { it >= 0 } ?: -1
            openIdx = cols.indexOfFirst { it == "open" || it.startsWith("op") }.takeIf { it >= 0 } ?: -1
            highIdx = cols.indexOfFirst { it == "high" || it.startsWith("hi") }.takeIf { it >= 0 } ?: -1
            lowIdx = cols.indexOfFirst { it == "low" || it.startsWith("lo") }.takeIf { it >= 0 } ?: -1
            closeIdx = cols.indexOfFirst { it == "close" || it.startsWith("cl") }.takeIf { it >= 0 } ?: -1
            volIdx = cols.indexOfFirst { it.contains("vol") || it == "v" }.takeIf { it >= 0 } ?: -1

            if (timeIdx == -1 || openIdx == -1 || highIdx == -1 || lowIdx == -1 || closeIdx == -1) {
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
                        violations = listOf("Missing required CSV columns in header. Expected: timestamp, open, high, low, close, [volume]. Found: $header"),
                        dataHash = MarketDataValidator.computeDataHash(emptyList())
                    )
                )
            }
        }

        val rawCandles = mutableListOf<Candle>()

        for ((lineIdx, line) in dataLines.withIndex()) {
            val parts = line.split(",").map { it.trim() }
            if (parts.size < 5) {
                violations.add("Line ${lineIdx + 2}: Insufficient columns (${parts.size} < 5)")
                continue
            }

            try {
                val timeStr = parts.getOrNull(timeIdx)
                val openStr = parts.getOrNull(openIdx)
                val highStr = parts.getOrNull(highIdx)
                val lowStr = parts.getOrNull(lowIdx)
                val closeStr = parts.getOrNull(closeIdx)
                val volStr = if (volIdx >= 0) parts.getOrNull(volIdx) else "0.0"

                if (timeStr == null || openStr == null || highStr == null || lowStr == null || closeStr == null) {
                    violations.add("Line ${lineIdx + 2}: Null field encountered.")
                    continue
                }

                val timestamp = parseTimestamp(timeStr)
                if (timestamp == null) {
                    violations.add("Line ${lineIdx + 2}: Unable to parse timestamp '$timeStr'")
                    continue
                }

                val open = openStr.toDoubleOrNull()
                val high = highStr.toDoubleOrNull()
                val low = lowStr.toDoubleOrNull()
                val close = closeStr.toDoubleOrNull()
                val volume = volStr?.toDoubleOrNull() ?: 0.0

                if (open == null || high == null || low == null || close == null) {
                    violations.add("Line ${lineIdx + 2}: Non-numeric OHLC values: O=$openStr, H=$highStr, L=$lowStr, C=$closeStr")
                    continue
                }

                rawCandles.add(
                    Candle(
                        timestamp = timestamp,
                        open = open,
                        high = high,
                        low = low,
                        close = close,
                        volume = volume
                    )
                )
            } catch (e: Exception) {
                violations.add("Line ${lineIdx + 2}: Error parsing line: ${e.message}")
            }
        }

        val (cleanedCandles, report) = MarketDataValidator.validateAndClean(
            rawCandles = rawCandles,
            timeframe = timeframe,
            assetCategory = assetCategory
        )

        val combinedViolations = (violations + report.violations).distinct()
        val finalReport = report.copy(
            violations = combinedViolations,
            isValid = report.isValid && rawCandles.isNotEmpty()
        )

        return Pair(cleanedCandles, finalReport)
    }

    /**
     * Performs strict validation and returns ComprehensiveValidationResult with dataHash.
     */
    fun validateCsvContent(
        csvContent: String,
        datasetName: String = "Imported Historical CSV",
        symbol: String = "BTC/USD",
        timeframe: Timeframe = Timeframe.D1,
        assetCategory: AssetCategory = AssetCategory.CRYPTO
    ): ComprehensiveValidationReport {
        val (candles, _) = parseCsv(csvContent, timeframe, assetCategory, symbol)
        return MarketDataValidator.validateDatasetStrict(
            candles = candles,
            datasetName = datasetName,
            symbol = symbol,
            timeframe = timeframe,
            assetCategory = assetCategory
        )
    }

    /**
     * Loads CSV from Android Asset inputStream.
     */
    fun loadFromStream(
        inputStream: InputStream,
        timeframe: Timeframe = Timeframe.D1,
        assetCategory: AssetCategory = AssetCategory.CRYPTO
    ): Pair<List<Candle>, DataValidationReport> {
        val content = BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
        return parseCsv(content, timeframe, assetCategory)
    }

    /**
     * Loads built-in 5-year verified real BTC historical dataset from assets.
     */
    fun loadBuiltInBtc5Year(
        context: Context? = null,
        timeframe: Timeframe = Timeframe.D1
    ): Pair<List<Candle>, DataValidationReport> {
        if (context != null) {
            try {
                context.assets.open("data/btc_daily_5yr.csv").use { stream ->
                    return loadFromStream(stream, timeframe, AssetCategory.CRYPTO)
                }
            } catch (_: Exception) {}
        }

        // Fallback for JVM test runner
        val classLoaderStream = CsvMarketDataLoader::class.java.classLoader?.getResourceAsStream("data/btc_daily_5yr.csv")
        if (classLoaderStream != null) {
            return loadFromStream(classLoaderStream, timeframe, AssetCategory.CRYPTO)
        }

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
                violations = listOf("Built-in BTC 5Y historical dataset not found.")
            )
        )
    }

    private fun parseTimestamp(raw: String): Long? {
        val clean = raw.trim()
        val num = clean.toLongOrNull()
        if (num != null) {
            // Check if in seconds vs milliseconds
            return if (num < 10000000000L) num * 1000L else num
        }

        for (format in DATE_FORMATS) {
            try {
                val parsed = format.parse(clean)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {}
        }
        return null
    }
}
