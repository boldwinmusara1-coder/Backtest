package com.example.tradestrat.engine

import com.example.tradestrat.model.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

object StrategyComparisonEngine {

    /**
     * Executes backtests for all selected strategies on the exact same candle dataset and common risk/cost settings,
     * performs strict fair-comparison validation, normalizes equity curves to $10,000, and generates complete
     * distribution, monthly matrix, and risk-adjusted metrics.
     */
    fun runComparison(
        strategies: List<StrategyDefinition>,
        candles: List<Candle>,
        asset: MarketAsset,
        regime: MarketRegime,
        timeframe: Timeframe,
        risk: RiskParameters,
        dataSourceInfo: DataSourceInfo? = null
    ): MultiStrategyComparisonResult {
        if (strategies.isEmpty() || candles.isEmpty()) {
            return MultiStrategyComparisonResult(
                validation = ComparisonValidationResult(
                    isValid = false,
                    validationErrors = listOf(
                        if (strategies.isEmpty()) "No strategies selected for comparison" else "No market data candles provided"
                    )
                ),
                items = emptyList(),
                monthlyMatrix = emptyList(),
                commonCandlesCount = candles.size
            )
        }

        // Run each strategy through the single production BacktestEngine
        val rawResults = strategies.map { strat ->
            val result = BacktestEngine.runBacktest(
                candles = candles,
                asset = asset,
                regime = regime,
                timeframe = timeframe,
                strategy = strat,
                risk = risk,
                dataSourceInfo = dataSourceInfo
            )
            strat to result
        }

        // Validate fair comparison
        val validation = validateFairComparison(rawResults.map { it.second })

        if (!validation.isValid) {
            return MultiStrategyComparisonResult(
                validation = validation,
                items = emptyList(),
                monthlyMatrix = emptyList(),
                commonCandlesCount = candles.size
            )
        }

        // Build comparison items
        val comparisonItems = rawResults.map { (strat, result) ->
            buildStrategyComparisonItem(strat, result, risk.initialCapital)
        }

        // Build unified monthly comparison matrix
        val monthlyMatrix = buildMonthlyMatrix(comparisonItems)

        return MultiStrategyComparisonResult(
            validation = validation,
            items = comparisonItems,
            monthlyMatrix = monthlyMatrix,
            commonCandlesCount = candles.size
        )
    }

    /**
     * Fair-comparison validator ensuring identical testing conditions across all backtest runs.
     */
    fun validateFairComparison(results: List<BacktestResult>, allowStrategySpecificRisk: Boolean = false): ComparisonValidationResult {
        if (results.isEmpty()) {
            return ComparisonValidationResult(isValid = false, validationErrors = listOf("No results to validate"))
        }

        val first = results.first()
        val errors = mutableListOf<String>()

        val assetSymbol = first.asset.symbol
        val timeframeMinutes = first.timeframe.minutes
        val startTs = first.candles.firstOrNull()?.timestamp ?: 0L
        val endTs = first.candles.lastOrNull()?.timestamp ?: 0L
        val candleCount = first.candles.size
        val initialCapital = first.riskParams.initialCapital
        val commBps = first.riskParams.commissionBps
        val slipBps = first.riskParams.slippageBps
        val leverage = first.riskParams.leverage
        val sizingMode = first.riskParams.positionSizingMode
        val sizingValue = first.riskParams.positionSizeValue

        for ((idx, r) in results.withIndex()) {
            if (r.asset.symbol != assetSymbol) {
                errors.add("Strategy #${idx + 1} (${r.strategy.name}) evaluated asset ${r.asset.symbol} != $assetSymbol")
            }
            if (r.timeframe.minutes != timeframeMinutes) {
                errors.add("Strategy #${idx + 1} (${r.strategy.name}) timeframe ${r.timeframe.label} != ${first.timeframe.label}")
            }
            if (r.candles.size != candleCount) {
                errors.add("Strategy #${idx + 1} (${r.strategy.name}) candle count ${r.candles.size} != $candleCount")
            }
            val rStart = r.candles.firstOrNull()?.timestamp ?: 0L
            val rEnd = r.candles.lastOrNull()?.timestamp ?: 0L
            if (rStart != startTs || rEnd != endTs) {
                errors.add("Strategy #${idx + 1} (${r.strategy.name}) date range mismatch ($rStart..$rEnd != $startTs..$endTs)")
            }
            if (abs(r.riskParams.initialCapital - initialCapital) > 0.001) {
                errors.add("Strategy #${idx + 1} initial capital mismatch ($${r.riskParams.initialCapital} != $$initialCapital)")
            }
            if (abs(r.riskParams.commissionBps - commBps) > 0.001) {
                errors.add("Strategy #${idx + 1} commission mismatch (${r.riskParams.commissionBps} != $commBps bps)")
            }
            if (abs(r.riskParams.slippageBps - slipBps) > 0.001) {
                errors.add("Strategy #${idx + 1} slippage mismatch (${r.riskParams.slippageBps} != $slipBps bps)")
            }
            if (!allowStrategySpecificRisk) {
                if (abs(r.riskParams.leverage - leverage) > 0.001) {
                    errors.add("Strategy #${idx + 1} leverage mismatch (${r.riskParams.leverage} != $leverage)")
                }
                if (r.riskParams.positionSizingMode != sizingMode || abs(r.riskParams.positionSizeValue - sizingValue) > 0.001) {
                    errors.add("Strategy #${idx + 1} sizing mismatch")
                }
            }
        }

        val dateRangeStr = if (first.candles.isNotEmpty()) {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            "${sdf.format(Date(startTs))} to ${sdf.format(Date(endTs))}"
        } else "N/A"

        return ComparisonValidationResult(
            isValid = errors.isEmpty(),
            commonAssetSymbol = assetSymbol,
            commonTimeframeLabel = first.timeframe.label,
            commonDateRange = dateRangeStr,
            commonInitialCapital = initialCapital,
            commonCommissionBps = commBps,
            commonSlippageBps = slipBps,
            validationErrors = errors
        )
    }

    private fun buildStrategyComparisonItem(
        strat: StrategyDefinition,
        result: BacktestResult,
        initialCapital: Double
    ): StrategyComparisonItem {
        val baseNormCapital = 10000.0
        val scaleFactor = if (initialCapital > 0) baseNormCapital / initialCapital else 1.0

        // 1. Normalized Equity Curve ($10,000 base)
        val normalizedEquity = result.equityCurve.map { ep ->
            NormalizedEquityPoint(
                barIndex = ep.barIndex,
                timestamp = ep.timestamp,
                normalizedEquity = ep.equity * scaleFactor,
                originalEquity = ep.equity
            )
        }

        // 2. Drawdown Curve
        val drawdownCurve = result.equityCurve.map { ep ->
            DrawdownPoint(
                barIndex = ep.barIndex,
                timestamp = ep.timestamp,
                drawdownPct = ep.drawdownPct
            )
        }

        // 3. Trade Distribution (Percentiles and statistics)
        val distribution = calculateTradeDistribution(result.trades)

        // 4. Monthly Performance
        val monthlyMetrics = calculateMonthlyMetrics(result.trades, initialCapital)
        val profitableMonths = monthlyMetrics.count { it.netPnlDollars > 0 }
        val losingMonths = monthlyMetrics.count { it.netPnlDollars < 0 }
        val bestMonth = monthlyMetrics.maxByOrNull { it.netPnlDollars }
        val worstMonth = monthlyMetrics.minByOrNull { it.netPnlDollars }

        // 5. Risk-Adjusted Metrics
        val riskAdjusted = StrategyRiskAdjustedMetrics(
            sharpeRatio = if (result.metrics.sharpeRatio.isFinite() && !result.metrics.sharpeRatio.isNaN()) result.metrics.sharpeRatio else null,
            sortinoRatio = if (result.metrics.sortinoRatio.isFinite() && !result.metrics.sortinoRatio.isNaN()) result.metrics.sortinoRatio else null,
            calmarRatio = if (result.metrics.calmarRatio.isFinite() && !result.metrics.calmarRatio.isNaN()) result.metrics.calmarRatio else null,
            profitFactor = result.metrics.profitFactor,
            expectancyDollars = result.metrics.expectancyDollars,
            maxDrawdownPercent = result.metrics.maxDrawdownPercent
        )

        return StrategyComparisonItem(
            strategy = strat,
            result = result,
            normalizedEquityCurve = normalizedEquity,
            drawdownCurve = drawdownCurve,
            distribution = distribution,
            monthlyMetrics = monthlyMetrics,
            riskAdjusted = riskAdjusted,
            profitableMonthsCount = profitableMonths,
            losingMonthsCount = losingMonths,
            bestMonth = bestMonth,
            worstMonth = worstMonth
        )
    }

    private fun calculateTradeDistribution(trades: List<Trade>): StrategyTradeDistribution {
        if (trades.isEmpty()) {
            return StrategyTradeDistribution(
                medianTradeDollars = 0.0,
                averageTradeDollars = 0.0,
                p25TradeDollars = 0.0,
                p75TradeDollars = 0.0,
                largestWinnerDollars = 0.0,
                largestLoserDollars = 0.0,
                averageWinnerDollars = 0.0,
                averageLoserDollars = 0.0,
                interquartileRangeDollars = 0.0
            )
        }

        val pnls = trades.map { it.pnlDollars }.sorted()
        val n = pnls.size

        val median = percentile(pnls, 50.0)
        val p25 = percentile(pnls, 25.0)
        val p75 = percentile(pnls, 75.0)
        val avg = pnls.average()

        val winners = pnls.filter { it > 0 }
        val losers = pnls.filter { it < 0 }

        val largestWinner = if (winners.isNotEmpty()) winners.maxOrNull() ?: 0.0 else 0.0
        val largestLoser = if (losers.isNotEmpty()) losers.minOrNull() ?: 0.0 else 0.0
        val avgWinner = if (winners.isNotEmpty()) winners.average() else 0.0
        val avgLoser = if (losers.isNotEmpty()) losers.average() else 0.0

        return StrategyTradeDistribution(
            medianTradeDollars = median,
            averageTradeDollars = avg,
            p25TradeDollars = p25,
            p75TradeDollars = p75,
            largestWinnerDollars = largestWinner,
            largestLoserDollars = largestLoser,
            averageWinnerDollars = avgWinner,
            averageLoserDollars = avgLoser,
            interquartileRangeDollars = p75 - p25
        )
    }

    private fun percentile(sortedList: List<Double>, p: Double): Double {
        if (sortedList.isEmpty()) return 0.0
        if (sortedList.size == 1) return sortedList.first()
        val index = (p / 100.0) * (sortedList.size - 1)
        val lower = index.toInt()
        val upper = ceil(index).toInt()
        val weight = index - lower
        return sortedList[lower] * (1.0 - weight) + sortedList[upper] * weight
    }

    private fun calculateMonthlyMetrics(trades: List<Trade>, initialCapital: Double): List<StrategyMonthlyMetrics> {
        val sdfKey = SimpleDateFormat("yyyy-MM", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val grouped = trades.groupBy { sdfKey.format(Date(it.entryTimestamp)) }

        return grouped.map { (ym, mTrades) ->
            val pnl = mTrades.sumOf { it.pnlDollars }
            val wins = mTrades.count { it.isWin }
            val winRate = (wins.toDouble() / mTrades.size) * 100.0
            val grossWin = mTrades.filter { it.pnlDollars > 0 }.sumOf { it.pnlDollars }
            val grossLoss = mTrades.filter { it.pnlDollars < 0 }.sumOf { -it.pnlDollars }
            val pf = if (grossLoss > 0) grossWin / grossLoss else if (grossWin > 0) 99.9 else 0.0
            val roi = if (initialCapital > 0) (pnl / initialCapital) * 100.0 else 0.0

            StrategyMonthlyMetrics(
                yearMonth = ym,
                netPnlDollars = pnl,
                roiPercent = roi,
                tradesCount = mTrades.size,
                winRatePercent = winRate,
                profitFactor = pf
            )
        }.sortedBy { it.yearMonth }
    }

    private fun buildMonthlyMatrix(items: List<StrategyComparisonItem>): List<MonthlyComparisonRow> {
        val allYearMonths = items.flatMap { item -> item.monthlyMetrics.map { it.yearMonth } }.toSet().sorted()
        val sdfInput = SimpleDateFormat("yyyy-MM", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val sdfDisplay = SimpleDateFormat("MMM yyyy", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

        return allYearMonths.map { ym ->
            val display = try {
                val d = sdfInput.parse(ym)
                if (d != null) sdfDisplay.format(d) else ym
            } catch (e: Exception) {
                ym
            }

            val pnlMap = mutableMapOf<String, Double>()
            val roiMap = mutableMapOf<String, Double>()

            for (item in items) {
                val m = item.monthlyMetrics.firstOrNull { it.yearMonth == ym }
                pnlMap[item.strategy.id] = m?.netPnlDollars ?: 0.0
                roiMap[item.strategy.id] = m?.roiPercent ?: 0.0
            }

            MonthlyComparisonRow(
                yearMonth = ym,
                displayName = display,
                strategyPnl = pnlMap,
                strategyRoi = roiMap
            )
        }
    }
}
