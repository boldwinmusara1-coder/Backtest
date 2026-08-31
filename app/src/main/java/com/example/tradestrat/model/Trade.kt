package com.example.tradestrat.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class TradeDirection(val label: String) {
    LONG("LONG"),
    SHORT("SHORT")
}

enum class ExitReason(val label: String, val badgeColor: Long) {
    TAKE_PROFIT("Take Profit", 0xFF10B981),
    STOP_LOSS("Stop Loss", 0xFFEF4444),
    TRAILING_STOP("Trailing Stop", 0xFFF59E0B),
    BREAK_EVEN("Break-Even", 0xFF6366F1),
    STRUCTURE_EXIT("Structure Exit", 0xFFEC4899),
    SIGNAL_REVERSAL("Signal Reversal", 0xFF38BDF8),
    CIRCUIT_BREAKER("Circuit Breaker", 0xFFA855F7),
    END_OF_DATA("End of Test", 0xFF94A3B8)
}

data class Trade(
    val id: String,
    val barIndex: Int,
    val exitBarIndex: Int,
    val entryTimestamp: Long,
    val exitTimestamp: Long,
    val direction: TradeDirection,
    val entryPrice: Double,
    val exitPrice: Double,
    val quantity: Double,
    val positionValue: Double,
    val pnlDollars: Double,
    val pnlPercent: Double,
    val exitReason: ExitReason,
    val feesPaid: Double,
    val rMultiple: Double,
    val holdingBars: Int,
    val maxRunUpPct: Double,
    val maxDrawdownPct: Double,
    val entryReason: String? = null,
    val stopLossPrice: Double? = null,
    val takeProfitPrice: Double? = null,
    val strategyName: String? = null,
    val strategyId: String? = null,
    val symbol: String? = null,
    val timeframe: String? = null,
    val grossPnlDollars: Double = 0.0,
    val slippagePaid: Double = 0.0,
    val result: String = if (pnlDollars > 0) "WIN" else if (pnlDollars < 0) "LOSS" else "BREAKEVEN",
    val explanation: TradeExplanation? = null
) {
    val isWin: Boolean get() = pnlDollars > 0
    val netPnlDollars: Double get() = pnlDollars
    val netPnlPercent: Double get() = pnlPercent
    val grossPnl: Double get() = grossPnlDollars
    val fees: Double get() = feesPaid
    val slippage: Double get() = slippagePaid
    val notionalValue: Double get() = positionValue
    val positionSize: Double get() = quantity
    val side: TradeDirection get() = direction
    val stopLoss: Double? get() = stopLossPrice
    val takeProfit: Double? get() = takeProfitPrice
    val commission: Double get() = feesPaid
    val netPnL: Double get() = pnlDollars
    val grossPnL: Double get() = grossPnlDollars

    fun formattedEntryDate(): String {
        return SimpleDateFormat("MMM dd, HH:mm", Locale.US).format(Date(entryTimestamp))
    }

    fun formattedExitDate(): String {
        return SimpleDateFormat("MMM dd, HH:mm", Locale.US).format(Date(exitTimestamp))
    }
}
