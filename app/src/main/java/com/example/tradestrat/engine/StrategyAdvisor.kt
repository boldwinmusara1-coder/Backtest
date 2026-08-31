package com.example.tradestrat.engine

import com.example.tradestrat.model.BacktestMetrics
import com.example.tradestrat.model.BacktestResult

data class StrategyHealthScorecard(
    val overallScore: Int, // 0 to 100
    val riskRewardScore: Int,
    val drawdownScore: Int,
    val consistencyScore: Int,
    val statisticalSignificanceScore: Int,
    val summaryVerdict: String,
    val recommendations: List<StrategyRecommendation>
)

data class StrategyRecommendation(
    val title: String,
    val category: String, // "Risk Management", "Indicator Tuning", "Execution", "Overfitting"
    val description: String,
    val isPositive: Boolean
)

object StrategyAdvisor {

    fun generateHealthReport(result: BacktestResult): StrategyHealthScorecard {
        val m = result.metrics
        val recs = mutableListOf<StrategyRecommendation>()

        // 1. Statistical Significance (Sample size)
        val sampleScore = when {
            m.totalTrades >= 40 -> 95
            m.totalTrades >= 20 -> 80
            m.totalTrades >= 10 -> 60
            m.totalTrades >= 5 -> 40
            else -> 20
        }
        if (m.totalTrades < 10) {
            recs.add(
                StrategyRecommendation(
                    title = "Low Trade Sample Size",
                    category = "Statistical Significance",
                    description = "Backtest executed only ${m.totalTrades} trades. Increase the historical data length or decrease timeframe to verify statistical validity.",
                    isPositive = false
                )
            )
        } else {
            recs.add(
                StrategyRecommendation(
                    title = "Adequate Sample Distribution",
                    category = "Statistical Significance",
                    description = "Dataset captured ${m.totalTrades} unique trade execution cycles for statistical significance.",
                    isPositive = true
                )
            )
        }

        // 2. Drawdown Health
        val ddScore = when {
            m.maxDrawdownPercent < 10.0 -> 95
            m.maxDrawdownPercent < 18.0 -> 80
            m.maxDrawdownPercent < 28.0 -> 65
            m.maxDrawdownPercent < 40.0 -> 45
            else -> 20
        }
        if (m.maxDrawdownPercent > 25.0) {
            recs.add(
                StrategyRecommendation(
                    title = "Excessive Maximum Drawdown (${String.format("%.1f", m.maxDrawdownPercent)}%)",
                    category = "Risk Management",
                    description = "Peak-to-trough drawdown of ${String.format("%.1f", m.maxDrawdownPercent)}% is high. Consider tightening the Stop Loss or activating the Max Drawdown Circuit Breaker.",
                    isPositive = false
                )
            )
        } else {
            recs.add(
                StrategyRecommendation(
                    title = "Controlled Drawdown Exposure",
                    category = "Risk Management",
                    description = "Maximum drawdown remained at a manageable ${String.format("%.1f", m.maxDrawdownPercent)}% throughout the market cycle.",
                    isPositive = true
                )
            )
        }

        // 3. Risk-to-Reward & Expectancy
        val rrScore = when {
            m.profitFactor >= 2.0 && m.payoffRatio >= 1.5 -> 95
            m.profitFactor >= 1.5 -> 80
            m.profitFactor >= 1.1 -> 65
            m.profitFactor >= 0.9 -> 45
            else -> 20
        }
        if (m.profitFactor < 1.1) {
            recs.add(
                StrategyRecommendation(
                    title = "Unfavorable Profit Factor (${String.format("%.2f", m.profitFactor)})",
                    category = "Indicator Tuning",
                    description = "Gross gains do not sufficiently outweigh gross losses. Test adjusting entry filter criteria or targeting a higher Risk-to-Reward ratio (e.g. 2.0+ R:R).",
                    isPositive = false
                )
            )
        } else if (m.profitFactor >= 1.8) {
            recs.add(
                StrategyRecommendation(
                    title = "High Profit Factor (${String.format("%.2f", m.profitFactor)})",
                    category = "Execution",
                    description = "Strategy generates $${String.format("%.2f", m.profitFactor)} for every $1 of gross losses.",
                    isPositive = true
                )
            )
        }

        // 4. Alpha vs Benchmark
        val alphaScore = when {
            m.alphaPercent > 30.0 -> 95
            m.alphaPercent > 10.0 -> 80
            m.alphaPercent > 0.0 -> 70
            m.alphaPercent > -15.0 -> 50
            else -> 25
        }
        if (m.alphaPercent > 0) {
            recs.add(
                StrategyRecommendation(
                    title = "Outperformed Buy & Hold (+${String.format("%.1f", m.alphaPercent)}% Alpha)",
                    category = "Performance",
                    description = "Strategy generated ${String.format("%.1f", m.netProfitPercent)}% vs benchmark ${String.format("%.1f", m.benchmarkReturnPercent)}%.",
                    isPositive = true
                )
            )
        } else {
            recs.add(
                StrategyRecommendation(
                    title = "Underperformed Passive Holding (${String.format("%.1f", m.alphaPercent)}% Alpha)",
                    category = "Performance",
                    description = "Passive Buy & Hold returned ${String.format("%.1f", m.benchmarkReturnPercent)}% while active strategy achieved ${String.format("%.1f", m.netProfitPercent)}%.",
                    isPositive = false
                )
            )
        }

        // 5. Sharpe & Sortino Quality
        if (m.sharpeRatio >= 1.5) {
            recs.add(
                StrategyRecommendation(
                    title = "Institutional-Grade Sharpe Ratio (${String.format("%.2f", m.sharpeRatio)})",
                    category = "Risk-Adjusted Return",
                    description = "Risk-adjusted return profile indicates strong excess returns relative to portfolio volatility.",
                    isPositive = true
                )
            )
        } else if (m.sharpeRatio < 0.8) {
            recs.add(
                StrategyRecommendation(
                    title = "Sub-Optimal Sharpe Ratio (${String.format("%.2f", m.sharpeRatio)})",
                    category = "Risk-Adjusted Return",
                    description = "Returns are volatile relative to downside exposure. Consider testing dynamic volatility sizing (e.g. ATR-based position sizing).",
                    isPositive = false
                )
            )
        }

        val overall = ((sampleScore * 0.2) + (ddScore * 0.3) + (rrScore * 0.3) + (alphaScore * 0.2)).toInt()

        val verdict = when {
            overall >= 80 -> "Robust Institutional Alpha Profile"
            overall >= 65 -> "Promising Strategy with Manageable Risk"
            overall >= 50 -> "Moderate Viability — Parameter Tuning Recommended"
            else -> "High Risk Profile — Redesign Strategy Rules"
        }

        return StrategyHealthScorecard(
            overallScore = overall,
            riskRewardScore = rrScore,
            drawdownScore = ddScore,
            consistencyScore = alphaScore,
            statisticalSignificanceScore = sampleScore,
            summaryVerdict = verdict,
            recommendations = recs
        )
    }
}
