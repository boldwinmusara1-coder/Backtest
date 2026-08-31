package com.example.tradestrat.model

enum class TrendlineSetupMode(val label: String, val description: String) {
    BREAK_AND_RETEST("Break & Retest (A+ Default)", "Prioritizes Break -> Retest -> Confirmation -> Entry sequence"),
    BREAK_ONLY("Break Only", "Enters on confirmed breakout/breakdown beyond trendline"),
    BOUNCE_ONLY("Bounce Only", "Enters on confirmed structural rejection bounce from trendline"),
    BREAK_OR_BOUNCE("Break or Bounce", "Enters on confirmed breaks as well as clean structural bounces")
}

enum class TrendlineMarketStructure(val label: String, val badgeColor: Long) {
    BULLISH_TREND("Bullish Trend (HH/HL)", 0xFF10B981),
    BEARISH_TREND("Bearish Trend (LH/LL)", 0xFFEF4444),
    CONSOLIDATION("Consolidation / Ambiguous", 0xFF94A3B8)
}

enum class TrendlineType(val label: String) {
    ASCENDING_SUPPORT("Ascending Support"),
    DESCENDING_RESISTANCE("Descending Resistance")
}

data class SwingPoint(
    val barIndex: Int,
    val timestamp: Long,
    val price: Double,
    val isHigh: Boolean,
    val confirmedAtBar: Int
)

data class APlusTrendline(
    val id: String,
    val type: TrendlineType,
    val firstSwing: SwingPoint,
    val secondSwing: SwingPoint,
    val slope: Double, // Price delta per bar index
    val intercept: Double, // Price at bar index 0: y = intercept + slope * x
    val touches: List<Int>, // Bar indices of swing points & touches
    val confirmedAtBar: Int,
    val brokenAtBar: Int? = null,
    val brokenPrice: Double? = null
) {
    fun priceAt(barIndex: Int): Double = intercept + slope * barIndex

    val touchCount: Int get() = touches.size
}

data class TradeExplanation(
    val setupType: String, // "BREAK", "BREAK_RETEST", "BOUNCE"
    val direction: String, // "LONG", "SHORT"
    val trend: String, // "BULLISH", "BEARISH", "CONSOLIDATION"
    val trendlineDescription: String,
    val touchCount: Int,
    val breakCandleTimestamp: Long? = null,
    val breakCandleBarIndex: Int? = null,
    val retestStatus: String = "N/A",
    val entryReason: String,
    val stopReason: String,
    val exitReason: String? = null
)

data class APlusTrendlineConfig(
    val setupMode: TrendlineSetupMode = TrendlineSetupMode.BREAK_AND_RETEST,
    val swingLookback: Int = 5, // Bars to the left required for a swing peak/trough
    val confirmationBars: Int = 3, // Right bars required to close before confirming swing
    val minSwingSeparationBars: Int = 4, // Minimum bars between swing points
    val minTouches: Int = 2, // Minimum verified touches required (Core A+ Rule)
    val breakConfirmationATR: Double = 0.10, // Required close beyond line in ATR multiples (0.10 ATR)
    val retestToleranceATR: Double = 0.25, // Retest zone tolerance in ATR multiples (0.25 ATR)
    val retestMaxBars: Int = 12, // Maximum bars allowed to complete retest sequence
    val requireStructureConfirmation: Boolean = true, // Must have non-conflicting market structure (rejects consolidation)
    val useStructuralStop: Boolean = true, // Structural swing point SL
    val stopBufferATR: Double = 0.10, // Buffer below swing low / above swing high in ATR multiples (0.10 ATR)
    val enableBreakEven: Boolean = true, // Structure-based break-even
    val requireStructuralBreakEven: Boolean = true, // Break-even only upon new confirmed structural swing
    val breakEvenTriggerR: Double = 1.0, // Excursion threshold before structural BE evaluation
    val enableStructuralTrailing: Boolean = true, // Trail stop behind confirmed swing HLs/LHs
    val exitOnCounterStructureBreak: Boolean = true, // Close trade if counter-trend structure is confirmed
    val fixedTakeProfitR: Double? = null, // Optional fixed target; default is null (let runners develop)
    val profileName: String = APlusStrategySpecification.PROFILE_APPROVED_V1_0,
    val isApproved: Boolean = true,
    val specificationHash: String? = null,
    val isExperimental: Boolean = false
) {
    companion object {
        const val STRATEGY_NAME = "A+ Trendline System"
        const val STRATEGY_VERSION = "1.0.0-frozen"
        const val PROFILE_NAME = "A_PLUS_V1.0"

        /**
         * The frozen approved configuration for the A+ Trendline Strategy V1.0.
         */
        val A_PLUS_DEFAULT_CONFIG = APlusTrendlineConfig(
            setupMode = TrendlineSetupMode.BREAK_AND_RETEST,
            swingLookback = 5,
            confirmationBars = 3,
            minSwingSeparationBars = 4,
            minTouches = 2,
            breakConfirmationATR = 0.10,
            retestToleranceATR = 0.25,
            retestMaxBars = 12,
            requireStructureConfirmation = true,
            useStructuralStop = true,
            stopBufferATR = 0.10,
            enableBreakEven = true,
            requireStructuralBreakEven = true,
            breakEvenTriggerR = 1.0,
            enableStructuralTrailing = true,
            exitOnCounterStructureBreak = true,
            fixedTakeProfitR = null,
            profileName = APlusStrategySpecification.PROFILE_APPROVED_V1_0,
            isApproved = true
        )
    }

    fun toParameterMap(): Map<String, Any> = mapOf(
        "strategyName" to STRATEGY_NAME,
        "strategyVersion" to STRATEGY_VERSION,
        "profileName" to profileName,
        "isApproved" to isApproved,
        "specificationHash" to (specificationHash ?: "UNLOCKED_PENDING"),
        "setupMode" to setupMode.name,
        "swingLookback" to swingLookback,
        "confirmationBars" to confirmationBars,
        "minSwingSeparationBars" to minSwingSeparationBars,
        "minTouches" to minTouches,
        "breakConfirmationATR" to breakConfirmationATR,
        "retestToleranceATR" to retestToleranceATR,
        "retestMaxBars" to retestMaxBars,
        "requireStructureConfirmation" to requireStructureConfirmation,
        "useStructuralStop" to useStructuralStop,
        "stopBufferATR" to stopBufferATR,
        "enableBreakEven" to enableBreakEven,
        "requireStructuralBreakEven" to requireStructuralBreakEven,
        "breakEvenTriggerR" to breakEvenTriggerR,
        "enableStructuralTrailing" to enableStructuralTrailing,
        "exitOnCounterStructureBreak" to exitOnCounterStructureBreak,
        "fixedTakeProfitR" to (fixedTakeProfitR ?: "UNBOUNDED_RUNNER")
    )
}

val A_PLUS_DEFAULT_CONFIG = APlusTrendlineConfig.A_PLUS_DEFAULT_CONFIG

data class SwingConfirmationAuditRecord(
    val candidateBarIndex: Int,
    val candidateTimestamp: Long,
    val candidatePrice: Double,
    val isHigh: Boolean,
    val confirmationBarIndex: Int,
    val confirmationTimestamp: Long,
    val delayBars: Int
)

data class TrendlineAuditRecord(
    val trendlineId: String,
    val anchor1: SwingPoint,
    val anchor2: SwingPoint,
    val touchCount: Int,
    val slope: Double,
    val tolerancePct: Double,
    val creationBarIndex: Int,
    val creationTimestamp: Long,
    val firstUsableBarIndex: Int,
    val firstUsableTimestamp: Long
)

data class BreakAuditRecord(
    val trendlineId: String,
    val breakCandleIndex: Int,
    val breakCandleTimestamp: Long,
    val breakPrice: Double,
    val candleClose: Double,
    val trendlinePrice: Double,
    val breakDistancePct: Double,
    val confirmationTimestamp: Long
)

data class RetestAuditRecord(
    val breakCandleIndex: Int,
    val retestCandleIndex: Int,
    val trendlinePrice: Double,
    val retestPrice: Double,
    val distancePct: Double,
    val rejectionConfirmed: Boolean,
    val entryCandleIndex: Int
)

data class StructuralStopAuditRecord(
    val tradeId: String,
    val structuralReferenceSwing: SwingPoint,
    val swingTimestamp: Long,
    val swingPrice: Double,
    val bufferPct: Double,
    val finalStopPrice: Double
)

enum class TradeManagementEventType {
    BREAK_EVEN_ADJUSTMENT,
    STRUCTURAL_TRAILING_STOP,
    COUNTER_STRUCTURE_EXIT,
    STANDARD_EXIT
}

data class TradeManagementAuditRecord(
    val tradeId: String,
    val eventType: TradeManagementEventType,
    val barIndex: Int,
    val timestamp: Long,
    val previousStop: Double?,
    val newStop: Double?,
    val triggeringStructure: String,
    val rAtTrigger: Double,
    val exitReason: ExitReason? = null
)

data class APlusForensicAuditLog(
    val strategyName: String = APlusTrendlineConfig.STRATEGY_NAME,
    val strategyVersion: String = APlusTrendlineConfig.STRATEGY_VERSION,
    val profileName: String = APlusTrendlineConfig.PROFILE_NAME,
    val parameters: Map<String, Any> = emptyMap(),
    val swingAudits: List<SwingConfirmationAuditRecord> = emptyList(),
    val trendlineAudits: List<TrendlineAuditRecord> = emptyList(),
    val breakAudits: List<BreakAuditRecord> = emptyList(),
    val retestAudits: List<RetestAuditRecord> = emptyList(),
    val structuralStopAudits: List<StructuralStopAuditRecord> = emptyList(),
    val managementAudits: List<TradeManagementAuditRecord> = emptyList()
)

data class APlusStrategyVisualData(
    val swingPoints: List<SwingPoint> = emptyList(),
    val trendlines: List<APlusTrendline> = emptyList(),
    val structureStates: Map<Int, TrendlineMarketStructure> = emptyMap(),
    val breakBars: List<Int> = emptyList(),
    val retestBars: List<Int> = emptyList(),
    val entryBars: List<Int> = emptyList(),
    val stopLossLevels: Map<Int, Double> = emptyMap(),
    val trailingStopLevels: Map<Int, Double> = emptyMap(),
    val breakEvenBars: List<Int> = emptyList()
)
