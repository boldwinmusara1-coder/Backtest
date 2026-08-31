package com.example.tradestrat.model

enum class ExecutionModel(val label: String, val shortLabel: String, val description: String) {
    REALISTIC(
        label = "Realistic (Next Bar Open)",
        shortLabel = "Next Open (Causal)",
        description = "Signal confirmed at Candle N Close; entry order executes at Candle N+1 Open with realistic market slippage and commission. Completely eliminates look-ahead bias."
    ),
    IDEALIZED(
        label = "Idealized (Same Bar Close)",
        shortLabel = "Same Close",
        description = "Signal evaluated and filled immediately at Candle N Close. Assumes zero execution latency."
    )
}

enum class PositionSizingMode(val displayName: String, val description: String) {
    PERCENT_EQUITY("Percent of Portfolio", "Allocates a fixed percentage of current account equity per trade"),
    FIXED_DOLLAR("Fixed Dollar Amount", "Allocates a constant USD amount per position"),
    RISK_BASED("Risk-Distance Sizing (ATR / Stop)", "Sizes position so maximum dollar loss equals X% of portfolio if Stop Loss is hit"),
    KELLY_CRITERION("Half-Kelly Fractional", "Dynamically scales size based on historical win rate and payoff ratio")
}

enum class StopLossType(val displayName: String) {
    NONE("No Stop Loss"),
    PERCENTAGE("Fixed Percentage (%)"),
    ATR_MULTIPLE("ATR Multiple (Volatility)"),
    TRAILING_PERCENTAGE("Trailing Stop (%)"),
    TRAILING_ATR("Trailing ATR")
}

enum class TakeProfitType(val displayName: String) {
    NONE("No Take Profit"),
    PERCENTAGE("Fixed Target (%)"),
    RISK_REWARD_RATIO("Risk-to-Reward Ratio (R:R)"),
    ATR_MULTIPLE("ATR Target Multiple")
}

enum class IntrabarExecutionAssumption(val label: String, val shortLabel: String, val description: String) {
    PESSIMISTIC_STOP_FIRST(
        label = "Conservative (Stop Loss First)",
        shortLabel = "Stop First",
        description = "If both Stop Loss & Take Profit are within the same candle's high/low range, assumes Stop Loss was hit first (pessimistic / safest approach)."
    ),
    BAR_DIRECTION(
        label = "Trend-Aligned (Bar Direction)",
        shortLabel = "Trend-Aligned",
        description = "Evaluates candle open vs close. For bullish bars (Close >= Open), assumes price tested Low before High. For bearish bars, assumes High before Low."
    ),
    OPTIMISTIC_TP_FIRST(
        label = "Optimistic (Take Profit First)",
        shortLabel = "TP First",
        description = "Assumes Take Profit was executed before Stop Loss inside the bar (aggressive approach)."
    )
}

data class RiskParameters(
    val initialCapital: Double = 10000.0,
    val positionSizingMode: PositionSizingMode = PositionSizingMode.PERCENT_EQUITY,
    val positionSizeValue: Double = 25.0, // e.g. 25% or $2500 or 1% risk
    val stopLossType: StopLossType = StopLossType.PERCENTAGE,
    val stopLossValue: Double = 3.0, // 3% or 1.5 ATR
    val takeProfitType: TakeProfitType = TakeProfitType.RISK_REWARD_RATIO,
    val takeProfitValue: Double = 2.0, // 2:1 R:R or 6% or 2 ATR
    val slippageBps: Double = 5.0, // 5 basis points = 0.05%
    val commissionBps: Double = 10.0, // 10 basis points = 0.10%
    val leverage: Double = 1.0, // 1.0x to 10.0x
    val allowShorting: Boolean = true,
    val maxDrawdownCircuitBreakerPct: Double = 30.0, // Pause trading if drawdown > 30%
    val executionModel: ExecutionModel = ExecutionModel.REALISTIC, // Default is REALISTIC (No look-ahead bias)
    val intrabarExecution: IntrabarExecutionAssumption = IntrabarExecutionAssumption.PESSIMISTIC_STOP_FIRST
) {
    val riskPerTradePercent: Double get() = positionSizeValue
    val slippagePercent: Double get() = slippageBps / 10000.0
    val commissionPercent: Double get() = commissionBps / 10000.0
    val stopLossAtrMultiplier: Double get() = stopLossValue
    val takeProfitRMultiple: Double get() = takeProfitValue
}
