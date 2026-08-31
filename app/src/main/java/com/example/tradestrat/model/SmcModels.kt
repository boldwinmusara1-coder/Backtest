package com.example.tradestrat.model

enum class SmcSessionType(val displayName: String, val startUtcHour: Int, val endUtcHour: Int) {
    LONDON("London (07:00 - 16:00 UTC)", 7, 16),
    NEW_YORK("New York (12:00 - 21:00 UTC)", 12, 21),
    ASIA("Asia / Tokyo (00:00 - 09:00 UTC)", 0, 9),
    LONDON_NY_OVERLAP("London / NY Overlap (12:00 - 16:00 UTC)", 12, 16),
    ALL("All Sessions (24/7)", 0, 24)
}

enum class FvgMitigationType(val displayName: String) {
    TOUCH("Gap Edge Touch"),
    CONSEQUENT_ENCROACHMENT("50% Consequent Encroachment (CE)"),
    FULL_FILL("100% Full Gap Fill")
}

enum class SmcTradeDirection(val displayName: String) {
    BOTH("Long & Short"),
    LONG_ONLY("Long Only"),
    SHORT_ONLY("Short Only")
}

enum class StructureType(val label: String, val isBullish: Boolean) {
    BULLISH_BOS("Bullish BOS", true),
    BEARISH_BOS("Bearish BOS", false),
    BULLISH_CHOCH("Bullish CHOCH / MSS", true),
    BEARISH_CHOCH("Bearish CHOCH / MSS", false),
    BULLISH_LIQUIDITY_SWEEP("Bullish Liquidity Sweep", true),
    BEARISH_LIQUIDITY_SWEEP("Bearish Liquidity Sweep", false),
    BULLISH_FVG("Bullish Fair Value Gap (FVG)", true),
    BEARISH_FVG("Bearish Fair Value Gap (FVG)", false),
    BULLISH_ORDER_BLOCK("Bullish Order Block (OB)", true),
    BEARISH_ORDER_BLOCK("Bearish Order Block (OB)", false),
    BULLISH_BREAKER_BLOCK("Bullish Breaker Block", true),
    BEARISH_BREAKER_BLOCK("Bearish Breaker Block", false),
    DISPLACEMENT_BULLISH("Bullish Displacement", true),
    DISPLACEMENT_BEARISH("Bearish Displacement", false),
    EQUAL_HIGHS("Equal Highs (EQH)", false),
    EQUAL_LOWS("Equal Lows (EQL)", true),
    PREMIUM_ZONE("Premium Zone (>50%)", false),
    DISCOUNT_ZONE("Discount Zone (<50%)", true)
}

data class SmcConfig(
    val useBos: Boolean = true,
    val bosLookback: Int = 5,
    val bosCloseConfirmation: Boolean = true,
    
    val useChoch: Boolean = true,
    val chochLookback: Int = 5,
    
    val useLiquiditySweep: Boolean = false,
    val sweepLookback: Int = 10,
    val sweepWickMinPct: Double = 0.15,
    
    val useFvg: Boolean = false,
    val fvgMinGapAtrMultiple: Double = 0.4,
    val fvgMitigationType: FvgMitigationType = FvgMitigationType.TOUCH,
    
    val useOrderBlock: Boolean = false,
    val obLookback: Int = 15,
    val obMitigationRequired: Boolean = true,
    
    val useBreakerBlock: Boolean = false,
    val breakerLookback: Int = 15,
    
    val usePremiumDiscount: Boolean = false,
    val discountThresholdPct: Double = 50.0,
    
    val useDisplacement: Boolean = false,
    val displacementAtrMultiplier: Double = 1.8,
    
    val useEqualHighsLows: Boolean = false,
    val eqTolerancePct: Double = 0.15,
    
    val useSessionFilter: Boolean = false,
    val sessionType: SmcSessionType = SmcSessionType.ALL,
    
    val tradeDirection: SmcTradeDirection = SmcTradeDirection.BOTH,
    val requireConfluence: Boolean = false,
    val minConfluences: Int = 1
)

data class SmcZone(
    val id: String,
    val type: StructureType,
    val startBarIndex: Int,
    val startTimestamp: Long,
    val topPrice: Double,
    val bottomPrice: Double,
    val midPrice: Double = (topPrice + bottomPrice) / 2.0,
    var isMitigated: Boolean = false,
    var mitigatedBarIndex: Int? = null
)

data class SmcSignalEvent(
    val barIndex: Int,
    val timestamp: Long,
    val type: StructureType,
    val direction: TradeDirection,
    val price: Double,
    val description: String
)

data class SmcEvaluationResult(
    val longSignal: Boolean,
    val shortSignal: Boolean,
    val rawSignalsCount: Int,
    val confluenceCount: Int,
    val detectedEvents: List<SmcSignalEvent> = emptyList(),
    val activeZones: List<SmcZone> = emptyList(),
    val isFiltered: Boolean = false,
    val filterReason: String? = null
)

data class SmcMetrics(
    val rawSignalsCount: Int = 0,
    val filteredSignalsCount: Int = 0,
    val bosEventsCount: Int = 0,
    val chochEventsCount: Int = 0,
    val liquiditySweepsCount: Int = 0,
    val fvgCount: Int = 0,
    val orderBlocksCount: Int = 0,
    val breakerBlocksCount: Int = 0,
    val displacementCount: Int = 0,
    val eqhEqlCount: Int = 0
)
