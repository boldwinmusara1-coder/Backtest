package com.example.tradestrat.model

import com.example.tradestrat.engine.StrategyComparisonEngine

data class DualMetricRow(
    val metricLabel: String,
    val highWinRateValue: String,
    val aPlusV1Value: String,
    val note: String = ""
)

data class DualStrategyComparisonData(
    val timestamp: Long = System.currentTimeMillis(),
    val asset: MarketAsset,
    val timeframe: Timeframe,
    val initialCapital: Double,
    val riskSettingsSummary: String,
    val highWinRateResult: BacktestResult,
    val aPlusV1Result: BacktestResult,
    val highWinRateBenchmark: StrategyBenchmarkReference?,
    val aPlusV1Benchmark: StrategyBenchmarkReference?,
    val validation: ComparisonValidationResult,
    val metricRows: List<DualMetricRow>
)

object DualStrategyComparisonBuilder {

    fun buildComparison(
        highWinRateResult: BacktestResult,
        aPlusV1Result: BacktestResult,
        timeframe: Timeframe,
        asset: MarketAsset,
        initialCapital: Double
    ): DualStrategyComparisonData {
        val validation = StrategyComparisonEngine.validateFairComparison(listOf(highWinRateResult, aPlusV1Result), allowStrategySpecificRisk = true)
        val highBench = StrategyBenchmarkRegistry.getReference(STRATEGY_ID_TRENDLINE_BREAK_HIGH_WIN_RATE, timeframe)
        val aPlusBench = StrategyBenchmarkRegistry.getReference(STRATEGY_ID_A_PLUS_V1_0, timeframe)

        val m1 = highWinRateResult.metrics
        val m2 = aPlusV1Result.metrics

        val rows = listOf(
            DualMetricRow(
                metricLabel = "Strategy Type",
                highWinRateValue = "Pivot Breakout (High Win Rate)",
                aPlusV1Value = "A+ Trendline (Break & Retest)",
                note = "Independent Execution Logic"
            ),
            DualMetricRow(
                metricLabel = "Version Identity",
                highWinRateValue = highWinRateResult.strategyVersion,
                aPlusV1Value = aPlusV1Result.strategyVersion,
                note = "Immutable Strategy IDs"
            ),
            DualMetricRow(
                metricLabel = "Position Sizing Model",
                highWinRateValue = "Fixed Dollar ($2,000 / 2x Margin)",
                aPlusV1Value = "Risk-Based (1.0% Equity)",
                note = "Isolated Risk Configuration"
            ),
            DualMetricRow(
                metricLabel = "Stop Loss Method",
                highWinRateValue = "Fixed Percentage (3.0%)",
                aPlusV1Value = "Structural Invalidation (0.10 ATR)",
                note = "Dynamic vs Fixed Stop"
            ),
            DualMetricRow(
                metricLabel = "Total Trades",
                highWinRateValue = "${m1.totalTrades}",
                aPlusV1Value = "${m2.totalTrades}",
                note = "Simulated on Identical Dataset"
            ),
            DualMetricRow(
                metricLabel = "Win Rate",
                highWinRateValue = String.format(java.util.Locale.US, "%.2f%% (%d/%d)", m1.winRatePercent, m1.winningTrades, m1.totalTrades),
                aPlusV1Value = String.format(java.util.Locale.US, "%.2f%% (%d/%d)", m2.winRatePercent, m2.winningTrades, m2.totalTrades),
                note = "Win Rate is only one metric"
            ),
            DualMetricRow(
                metricLabel = "Net P&L ($)",
                highWinRateValue = String.format(java.util.Locale.US, "%s$%.2f", if (m1.netProfitDollars >= 0) "+" else "", m1.netProfitDollars),
                aPlusV1Value = String.format(java.util.Locale.US, "%s$%.2f", if (m2.netProfitDollars >= 0) "+" else "", m2.netProfitDollars),
                note = "Net Realized Profit"
            ),
            DualMetricRow(
                metricLabel = "Return on Investment (ROI)",
                highWinRateValue = String.format(java.util.Locale.US, "%s%.2f%%", if (m1.netProfitPercent >= 0) "+" else "", m1.netProfitPercent),
                aPlusV1Value = String.format(java.util.Locale.US, "%s%.2f%%", if (m2.netProfitPercent >= 0) "+" else "", m2.netProfitPercent),
                note = "Cumulative Capital Return"
            ),
            DualMetricRow(
                metricLabel = "Profit Factor",
                highWinRateValue = String.format(java.util.Locale.US, "%.2f", m1.profitFactor),
                aPlusV1Value = String.format(java.util.Locale.US, "%.2f", m2.profitFactor),
                note = "Gross Profit / Gross Loss"
            ),
            DualMetricRow(
                metricLabel = "Expectancy ($ / Trade)",
                highWinRateValue = String.format(java.util.Locale.US, "%s$%.2f", if (m1.expectancyDollars >= 0) "+" else "", m1.expectancyDollars),
                aPlusV1Value = String.format(java.util.Locale.US, "%s$%.2f", if (m2.expectancyDollars >= 0) "+" else "", m2.expectancyDollars),
                note = "Average Expected Value per Trade"
            ),
            DualMetricRow(
                metricLabel = "Average R-Multiple",
                highWinRateValue = String.format(java.util.Locale.US, "%s%.2f R", if (m1.avgRMultiple >= 0) "+" else "", m1.avgRMultiple),
                aPlusV1Value = String.format(java.util.Locale.US, "%s%.2f R", if (m2.avgRMultiple >= 0) "+" else "", m2.avgRMultiple),
                note = "Average Risk-Unit Return"
            ),
            DualMetricRow(
                metricLabel = "Maximum Drawdown",
                highWinRateValue = String.format(java.util.Locale.US, "%.2f%%", m1.maxDrawdownPercent),
                aPlusV1Value = String.format(java.util.Locale.US, "%.2f%%", m2.maxDrawdownPercent),
                note = "Peak-to-Trough Capital Decline"
            ),
            DualMetricRow(
                metricLabel = "Sharpe Ratio",
                highWinRateValue = String.format(java.util.Locale.US, "%.2f", m1.sharpeRatio),
                aPlusV1Value = String.format(java.util.Locale.US, "%.2f", m2.sharpeRatio),
                note = "Risk-Adjusted Return"
            ),
            DualMetricRow(
                metricLabel = "Sortino Ratio",
                highWinRateValue = String.format(java.util.Locale.US, "%.2f", m1.sortinoRatio),
                aPlusV1Value = String.format(java.util.Locale.US, "%.2f", m2.sortinoRatio),
                note = "Downside Risk-Adjusted Return"
            ),
            DualMetricRow(
                metricLabel = "Calmar Ratio",
                highWinRateValue = String.format(java.util.Locale.US, "%.2f", m1.calmarRatio),
                aPlusV1Value = String.format(java.util.Locale.US, "%.2f", m2.calmarRatio),
                note = "Annualized Return / Max Drawdown"
            ),
            DualMetricRow(
                metricLabel = "Max Winning Streak",
                highWinRateValue = "${m1.maxConsecutiveWins} wins",
                aPlusV1Value = "${m2.maxConsecutiveWins} wins",
                note = "Consecutive Win Cluster"
            ),
            DualMetricRow(
                metricLabel = "Max Losing Streak",
                highWinRateValue = "${m1.maxConsecutiveLosses} losses",
                aPlusV1Value = "${m2.maxConsecutiveLosses} losses",
                note = "Consecutive Loss Cluster"
            )
        )

        return DualStrategyComparisonData(
            asset = asset,
            timeframe = timeframe,
            initialCapital = initialCapital,
            riskSettingsSummary = "Identical Dataset & Testing Conditions (BTC/USD ${timeframe.label}, $${initialCapital.toInt()})",
            highWinRateResult = highWinRateResult,
            aPlusV1Result = aPlusV1Result,
            highWinRateBenchmark = highBench,
            aPlusV1Benchmark = aPlusBench,
            validation = validation,
            metricRows = rows
        )
    }
}
