package com.example.tradestrat.data

import com.example.tradestrat.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

data class MarketDataFetchResult(
    val candles: List<Candle>,
    val validationReport: DataValidationReport,
    val providerName: String,
    val isRealHistorical: Boolean,
    val fetchTimestamp: Long = System.currentTimeMillis(),
    val dataHash: String = validationReport.dataHash.ifEmpty { MarketDataValidator.computeDataHash(candles) }
)

class MarketDataRepository(
    private val binanceSource: BinanceMarketDataSource = BinanceMarketDataSource(),
    private val coinbaseSource: CoinbaseMarketDataSource = CoinbaseMarketDataSource(),
    private val yahooSource: YahooFinanceMarketDataSource = YahooFinanceMarketDataSource(),
    private val twelveDataSource: TwelveDataMarketDataSource = TwelveDataMarketDataSource()
) {

    /**
     * Retrieves genuine historical market candles from real market-data APIs.
     * Validates data integrity (chronology, OHLC bounds, duplicates) and fails safely with descriptive errors.
     * Does NOT silently fall back to random synthetic data when real mode is requested.
     */
    suspend fun getHistoricalCandles(
        asset: MarketAsset,
        timeframe: Timeframe,
        startTimeMs: Long,
        endTimeMs: Long,
        apiKey: String? = null,
        isDemoMode: Boolean = false,
        provider: String? = null,
        customCsvContent: String? = null
    ): Result<MarketDataFetchResult> = withContext(Dispatchers.IO) {
        if (!customCsvContent.isNullOrBlank()) {
            val (candles, report) = CsvMarketDataLoader.parseCsv(
                csvContent = customCsvContent,
                timeframe = timeframe,
                assetCategory = asset.category
            )
            if (report.isValid && candles.isNotEmpty()) {
                val filtered = candles.filter { it.timestamp in startTimeMs..endTimeMs }.ifEmpty { candles }
                return@withContext Result.success(
                    MarketDataFetchResult(
                        candles = filtered,
                        validationReport = report,
                        providerName = "Custom Imported CSV Dataset",
                        isRealHistorical = true
                    )
                )
            } else {
                return@withContext Result.failure(
                    IOException("CSV Validation Failed: ${report.violations.joinToString("; ")}")
                )
            }
        }

        if (provider?.lowercase() == "csv" || provider?.lowercase() == "archive") {
            val (candles, report) = CsvMarketDataLoader.loadBuiltInBtc5Year(timeframe = timeframe)
            if (report.isValid && candles.isNotEmpty()) {
                val filtered = candles.filter { it.timestamp in startTimeMs..endTimeMs }.ifEmpty { candles }
                return@withContext Result.success(
                    MarketDataFetchResult(
                        candles = filtered,
                        validationReport = report,
                        providerName = "Built-in 5-Year Historical Archive (BTC/USD)",
                        isRealHistorical = true
                    )
                )
            }
        }

        if (isDemoMode) {
            // Explicitly requested isolated demo data
            val demoCandles = DemoMarketDataProvider.generateDemoCandles(asset, timeframe, startTimeMs, endTimeMs)
            val (clean, report) = MarketDataValidator.validateAndClean(demoCandles, timeframe)
            return@withContext Result.success(
                MarketDataFetchResult(
                    candles = clean,
                    validationReport = report,
                    providerName = "Isolated Demo Engine (Synthetic Preview)",
                    isRealHistorical = false
                )
            )
        }

        // Determine real provider chain based on asset category, user choice, and available API keys
        val providers = mutableListOf<MarketDataSource>()

        when (provider?.lowercase()) {
            "binance" -> providers.add(binanceSource)
            "coinbase" -> providers.add(coinbaseSource)
            "yahoo" -> providers.add(yahooSource)
            "twelvedata" -> providers.add(twelveDataSource)
            else -> {
                if (!apiKey.isNullOrBlank()) {
                    providers.add(twelveDataSource)
                }
                when (asset.category) {
                    AssetCategory.CRYPTO -> {
                        providers.add(binanceSource)
                        providers.add(coinbaseSource)
                        providers.add(yahooSource)
                    }
                    AssetCategory.FOREX,
                    AssetCategory.COMMODITIES,
                    AssetCategory.STOCKS,
                    AssetCategory.INDICES -> {
                        providers.add(yahooSource)
                    }
                }
            }
        }

        val errors = mutableListOf<String>()

        for (provider in providers) {
            if (!provider.supportsAsset(asset)) continue

            val fetchResult = provider.fetchHistoricalCandles(
                asset = asset,
                timeframe = timeframe,
                startTimeMs = startTimeMs,
                endTimeMs = endTimeMs,
                apiKey = apiKey
            )

            if (fetchResult.isSuccess) {
                val rawCandles = fetchResult.getOrNull() ?: emptyList()
                if (rawCandles.isNotEmpty()) {
                    // Run strict Historical Data Integrity Validator
                    val (cleanCandles, report) = MarketDataValidator.validateAndClean(
                        rawCandles = rawCandles,
                        timeframe = timeframe,
                        assetCategory = asset.category,
                        expectedStartTimeMs = startTimeMs,
                        expectedEndTimeMs = endTimeMs
                    )

                    if (report.isValid && cleanCandles.isNotEmpty()) {
                        return@withContext Result.success(
                            MarketDataFetchResult(
                                candles = cleanCandles,
                                validationReport = report,
                                providerName = provider.providerName,
                                isRealHistorical = true
                            )
                        )
                    } else {
                        errors.add("${provider.providerName}: Integrity validation failed: ${report.violations.joinToString("; ")}")
                    }
                }
            } else {
                val ex = fetchResult.exceptionOrNull()
                errors.add("${provider.providerName}: ${ex?.message ?: "Unknown error"}")
            }
        }

        // Fails safely: Never invent data when real data fails
        val aggregatedErrorMessage = if (errors.isEmpty()) {
            "No compatible real historical data provider found for ${asset.symbol} (${asset.category.label})."
        } else {
            "Real Historical Data Fetch Failed:\n" + errors.joinToString("\n• ", prefix = "• ")
        }

        Result.failure(IOException(aggregatedErrorMessage))
    }
}
