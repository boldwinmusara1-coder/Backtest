package com.example.tradestrat.model

import java.text.DecimalFormat
import kotlin.math.abs

data class StrategyRankWeights(
    val profitFactorWeight: Float = 0.25f,
    val expectancyWeight: Float = 0.20f,
    val netPnlWeight: Float = 0.20f,
    val roiWeight: Float = 0.15f,
    val maxDrawdownWeight: Float = 0.10f, // penalty for higher DD
    val sharpeWeight: Float = 0.10f
)

data class StrategyRankDetail(
    val strategyId: String,
    val strategyName: String,
    val rank: Int,
    val compositeScore: Double,
    val metricRankings: Map<ComparisonSortMetric, Int>,
    val scoreFormulaBreakdown: String
)

object StrategyRankingCalculator {

    fun calculateRankings(
        items: List<StrategyComparisonItem>,
        weights: StrategyRankWeights = StrategyRankWeights()
    ): List<StrategyRankDetail> {
        if (items.isEmpty()) return emptyList()
        if (items.size == 1) {
            val item = items.first()
            return listOf(
                StrategyRankDetail(
                    strategyId = item.strategy.id,
                    strategyName = item.strategy.name,
                    rank = 1,
                    compositeScore = 100.0,
                    metricRankings = ComparisonSortMetric.values().associateWith { 1 },
                    scoreFormulaBreakdown = "Single strategy evaluated (100.0/100)"
                )
            )
        }

        // Rank by individual metrics
        val pfRanks = items.sortedByDescending { it.result.metrics.profitFactor }
            .mapIndexed { idx, it -> it.strategy.id to (idx + 1) }.toMap()

        val expRanks = items.sortedByDescending { it.result.metrics.expectancyDollars }
            .mapIndexed { idx, it -> it.strategy.id to (idx + 1) }.toMap()

        val pnlRanks = items.sortedByDescending { it.result.metrics.netProfitDollars }
            .mapIndexed { idx, it -> it.strategy.id to (idx + 1) }.toMap()

        val roiRanks = items.sortedByDescending { it.result.metrics.netProfitPercent }
            .mapIndexed { idx, it -> it.strategy.id to (idx + 1) }.toMap()

        val ddRanks = items.sortedBy { it.result.metrics.maxDrawdownPercent } // lower is better
            .mapIndexed { idx, it -> it.strategy.id to (idx + 1) }.toMap()

        val sharpeRanks = items.sortedByDescending { it.riskAdjusted.sharpeRatio ?: -999.0 }
            .mapIndexed { idx, it -> it.strategy.id to (idx + 1) }.toMap()

        val calmarRanks = items.sortedByDescending { it.riskAdjusted.calmarRatio ?: -999.0 }
            .mapIndexed { idx, it -> it.strategy.id to (idx + 1) }.toMap()

        val wrRanks = items.sortedByDescending { it.result.metrics.winRatePercent }
            .mapIndexed { idx, it -> it.strategy.id to (idx + 1) }.toMap()

        val tradesRanks = items.sortedByDescending { it.result.metrics.totalTrades }
            .mapIndexed { idx, it -> it.strategy.id to (idx + 1) }.toMap()

        val sortinoRanks = items.sortedByDescending { it.riskAdjusted.sortinoRatio ?: -999.0 }
            .mapIndexed { idx, it -> it.strategy.id to (idx + 1) }.toMap()

        // Min-Max normalization for transparent composite scoring
        fun minMaxNorm(values: List<Double>, value: Double, invert: Boolean = false): Double {
            val min = values.minOrNull() ?: 0.0
            val max = values.maxOrNull() ?: 1.0
            if (abs(max - min) < 1e-9) return 1.0
            val norm = (value - min) / (max - min)
            return if (invert) (1.0 - norm) else norm
        }

        val allPf = items.map { it.result.metrics.profitFactor }
        val allExp = items.map { it.result.metrics.expectancyDollars }
        val allPnl = items.map { it.result.metrics.netProfitDollars }
        val allRoi = items.map { it.result.metrics.netProfitPercent }
        val allDd = items.map { it.result.metrics.maxDrawdownPercent }
        val allSharpe = items.map { it.riskAdjusted.sharpeRatio ?: 0.0 }

        val df = DecimalFormat("#,##0.0")

        val scored = items.map { item ->
            val id = item.strategy.id
            val m = item.result.metrics
            val r = item.riskAdjusted

            val sPf = minMaxNorm(allPf, m.profitFactor) * 100.0
            val sExp = minMaxNorm(allExp, m.expectancyDollars) * 100.0
            val sPnl = minMaxNorm(allPnl, m.netProfitDollars) * 100.0
            val sRoi = minMaxNorm(allRoi, m.netProfitPercent) * 100.0
            val sDd = minMaxNorm(allDd, m.maxDrawdownPercent, invert = true) * 100.0
            val sSharpe = minMaxNorm(allSharpe, r.sharpeRatio ?: 0.0) * 100.0

            val composite = (sPf * weights.profitFactorWeight) +
                    (sExp * weights.expectancyWeight) +
                    (sPnl * weights.netPnlWeight) +
                    (sRoi * weights.roiWeight) +
                    (sDd * weights.maxDrawdownWeight) +
                    (sSharpe * weights.sharpeWeight)

            val metricMap = mapOf(
                ComparisonSortMetric.PROFIT_FACTOR to (pfRanks[id] ?: 1),
                ComparisonSortMetric.EXPECTANCY to (expRanks[id] ?: 1),
                ComparisonSortMetric.NET_PNL to (pnlRanks[id] ?: 1),
                ComparisonSortMetric.ROI to (roiRanks[id] ?: 1),
                ComparisonSortMetric.MAX_DRAWDOWN to (ddRanks[id] ?: 1),
                ComparisonSortMetric.SHARPE_RATIO to (sharpeRanks[id] ?: 1),
                ComparisonSortMetric.CALMAR_RATIO to (calmarRanks[id] ?: 1),
                ComparisonSortMetric.WIN_RATE to (wrRanks[id] ?: 1),
                ComparisonSortMetric.TOTAL_TRADES to (tradesRanks[id] ?: 1),
                ComparisonSortMetric.SORTINO_RATIO to (sortinoRanks[id] ?: 1)
            )

            val breakdown = "PF: ${df.format(sPf)}×${(weights.profitFactorWeight * 100).toInt()}% + " +
                    "Exp: ${df.format(sExp)}×${(weights.expectancyWeight * 100).toInt()}% + " +
                    "PnL: ${df.format(sPnl)}×${(weights.netPnlWeight * 100).toInt()}% + " +
                    "ROI: ${df.format(sRoi)}×${(weights.roiWeight * 100).toInt()}% + " +
                    "DD: ${df.format(sDd)}×${(weights.maxDrawdownWeight * 100).toInt()}% + " +
                    "Sharpe: ${df.format(sSharpe)}×${(weights.sharpeWeight * 100).toInt()}%"

            StrategyRankDetail(
                strategyId = id,
                strategyName = item.strategy.name,
                rank = 0, // will assign after sorting
                compositeScore = composite,
                metricRankings = metricMap,
                scoreFormulaBreakdown = breakdown
            )
        }.sortedByDescending { it.compositeScore }

        return scored.mapIndexed { idx, detail ->
            detail.copy(rank = idx + 1)
        }
    }
}
