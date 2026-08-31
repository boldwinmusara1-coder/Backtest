package com.example.tradestrat.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Trade Distribution statistics for distinguishing high-frequency low-edge strategies
 * from lower-frequency high-expectancy strategies.
 */
data class StrategyTradeDistribution(
    val medianTradeDollars: Double,
    val averageTradeDollars: Double,
    val p25TradeDollars: Double,
    val p75TradeDollars: Double,
    val largestWinnerDollars: Double,
    val largestLoserDollars: Double,
    val averageWinnerDollars: Double,
    val averageLoserDollars: Double,
    val interquartileRangeDollars: Double
)

/**
 * Monthly performance metrics for a single strategy.
 */
data class StrategyMonthlyMetrics(
    val yearMonth: String, // e.g. "2024-07"
    val netPnlDollars: Double,
    val roiPercent: Double,
    val tradesCount: Int,
    val winRatePercent: Double,
    val profitFactor: Double
)

/**
 * Monthly matrix row across all compared strategies.
 */
data class MonthlyComparisonRow(
    val yearMonth: String, // e.g. "2024-07"
    val displayName: String, // e.g. "Jul 2024"
    val strategyPnl: Map<String, Double>, // strategyId -> PnL
    val strategyRoi: Map<String, Double> // strategyId -> ROI %
)

/**
 * Risk-adjusted metrics summary for comparing strategy quality.
 */
data class StrategyRiskAdjustedMetrics(
    val sharpeRatio: Double?,
    val sortinoRatio: Double?,
    val calmarRatio: Double?,
    val profitFactor: Double,
    val expectancyDollars: Double,
    val maxDrawdownPercent: Double
)

/**
 * Complete evaluation result for a single strategy in the comparison run.
 */
data class StrategyComparisonItem(
    val strategy: StrategyDefinition,
    val result: BacktestResult,
    val normalizedEquityCurve: List<NormalizedEquityPoint>, // normalized to $10,000 start
    val drawdownCurve: List<DrawdownPoint>,
    val distribution: StrategyTradeDistribution,
    val monthlyMetrics: List<StrategyMonthlyMetrics>,
    val riskAdjusted: StrategyRiskAdjustedMetrics,
    val profitableMonthsCount: Int,
    val losingMonthsCount: Int,
    val bestMonth: StrategyMonthlyMetrics?,
    val worstMonth: StrategyMonthlyMetrics?
)

data class NormalizedEquityPoint(
    val barIndex: Int,
    val timestamp: Long,
    val normalizedEquity: Double, // normalized to $10,000 base
    val originalEquity: Double
)

data class DrawdownPoint(
    val barIndex: Int,
    val timestamp: Long,
    val drawdownPct: Double
)

enum class ComparisonSortMetric(val label: String, val shortLabel: String) {
    NET_PNL("Net P&L ($)", "P&L"),
    PROFIT_FACTOR("Profit Factor", "PF"),
    EXPECTANCY("Expectancy ($)", "Exp"),
    ROI("ROI (%)", "ROI"),
    MAX_DRAWDOWN("Max Drawdown (%)", "Max DD"),
    WIN_RATE("Win Rate (%)", "WR"),
    TOTAL_TRADES("Total Trades", "Trades"),
    SHARPE_RATIO("Sharpe Ratio", "Sharpe"),
    CALMAR_RATIO("Calmar Ratio", "Calmar"),
    SORTINO_RATIO("Sortino Ratio", "Sortino")
}

/**
 * Validation result checking that all compared strategies ran on strictly identical conditions.
 */
data class ComparisonValidationResult(
    val isValid: Boolean,
    val commonAssetSymbol: String = "",
    val commonTimeframeLabel: String = "",
    val commonDateRange: String = "",
    val commonInitialCapital: Double = 0.0,
    val commonCommissionBps: Double = 0.0,
    val commonSlippageBps: Double = 0.0,
    val validationErrors: List<String> = emptyList()
)

/**
 * Multi-Strategy Comparison Run Result.
 */
data class MultiStrategyComparisonResult(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val validation: ComparisonValidationResult,
    val items: List<StrategyComparisonItem>,
    val monthlyMatrix: List<MonthlyComparisonRow>,
    val commonCandlesCount: Int = 0
)
