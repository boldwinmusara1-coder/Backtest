package com.example.tradestrat.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class JournalGrade(val label: String) {
    A_PLUS("A+"),
    A("A"),
    B("B"),
    C("C"),
    D("D")
}

enum class ExecutionQuality(val label: String) {
    PERFECT("Perfect Execution"),
    ACCEPTABLE("Acceptable"),
    EARLY_ENTRY("Early Entry"),
    LATE_ENTRY("Late Entry"),
    EARLY_EXIT("Early Exit"),
    SLIPPAGE_ISSUE("Slippage / Chase"),
    MISTAKE("Rule Violation / Mistake")
}

enum class TraderEmotion(val label: String) {
    CALM_DISCIPLINED("Calm & Disciplined"),
    CONFIDENT("Confident"),
    FOMO("FOMO / Anxious"),
    GREEDY("Greedy / Hesitant"),
    REVENGE("Revenge Trading"),
    NEUTRAL("Neutral")
}

data class JournalEntry(
    val id: String,
    val tradeId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val symbol: String,
    val strategyName: String,
    val strategyType: StrategyType,
    val direction: TradeDirection,
    val entryPrice: Double,
    val exitPrice: Double,
    val pnlDollars: Double,
    val pnlPercent: Double,
    val rMultiple: Double,
    val thesis: String = "",
    val mistakes: String = "",
    val emotion: TraderEmotion = TraderEmotion.CALM_DISCIPLINED,
    val executionQuality: ExecutionQuality = ExecutionQuality.PERFECT,
    val setupGrade: JournalGrade = JournalGrade.A_PLUS,
    val tags: List<String> = emptyList(),
    val isManualReplay: Boolean = false,
    val entryReason: String? = null
) {
    val isWin: Boolean get() = pnlDollars > 0

    fun formattedDate(): String {
        return SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US).format(Date(timestamp))
    }
}

data class ManualReplayPosition(
    val id: String,
    val direction: TradeDirection,
    val entryPrice: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val quantity: Double,
    val entryTimestamp: Long,
    val entryBarIndex: Int,
    val notes: String = ""
)

data class BacktestProgress(
    val isRunning: Boolean = false,
    val progressPct: Float = 0f,
    val stageMessage: String = "",
    val processedCandles: Int = 0,
    val totalCandles: Int = 0,
    val currentDateStr: String = "",
    val tradesFound: Int = 0,
    val currentEquity: Double = 0.0,
    val strategyName: String = "",
    val symbol: String = "",
    val timeframe: String = ""
)

data class StrategyLabItem(
    val strategy: StrategyDefinition,
    val result: BacktestResult? = null,
    val isEvaluating: Boolean = false,
    val error: String? = null
)

data class SessionAnalytics(
    val sessionName: String,
    val tradeCount: Int,
    val winRate: Double,
    val netPnl: Double,
    val avgR: Double
)

data class MaeMfeDistribution(
    val avgMaePct: Double,
    val avgMfePct: Double,
    val maxMaePct: Double,
    val maxMfePct: Double,
    val points: List<MaeMfePoint>
)

data class MaeMfePoint(
    val tradeId: String,
    val barIndex: Int,
    val direction: TradeDirection,
    val rMultiple: Double,
    val maePct: Double,
    val mfePct: Double,
    val isWin: Boolean,
    val entryPrice: Double,
    val exitPrice: Double
)
