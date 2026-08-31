package com.example.tradestrat.model

import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * ============================================================================
 * A+ TRENDLINE SYSTEM — SPECIFICATION & GOVERNANCE MODELS
 * ============================================================================
 */

enum class RuleCategory(val label: String) {
    MARKET_STRUCTURE("Market Structure"),
    TRENDLINES("Trendlines & Touches"),
    EXECUTION("Break & Retest Execution"),
    RISK_MANAGEMENT("Risk & Trade Management"),
    CAUSALITY("Causal Integrity")
}

enum class ApprovalStatus(val label: String) {
    CONFIRMED("CONFIRMED"),
    REQUIRES_USER_APPROVAL("REQUIRES USER APPROVAL"),
    APPROVED("APPROVED")
}

data class APlusRuleItem(
    val id: String,
    val title: String,
    val category: RuleCategory,
    val plainDescription: String,
    val engineImplementation: String,
    val isConfirmed: Boolean = true
)

data class APlusParameterItem(
    val id: String,
    val name: String,
    val currentValueStr: String,
    val rawValue: Double,
    val unit: String,
    val exactDefinition: String,
    val effect: String,
    val origin: String, // "USER-SPECIFIED" or "AI-PROPOSED"
    val isApproved: Boolean = false,
    val minVal: Double = 0.0,
    val maxVal: Double = 0.0,
    val step: Double = 1.0
)

data class ApprovedStrategyProfile(
    val profileId: String,
    val profileName: String,
    val strategyVersion: String,
    val isImmutable: Boolean,
    val isExperimental: Boolean,
    val parentProfileId: String? = null,
    val approvalTimestamp: Long,
    val approvalTimestampIso: String,
    val approvedBy: String,
    val ruleSpecificationHash: String,
    val config: APlusTrendlineConfig,
    val parameterSnapshot: Map<String, Any>
)

data class APlusApprovalState(
    val activeProfileName: String = APlusStrategySpecification.PROFILE_APPROVED_V1_0,
    val strategyVersion: String = APlusStrategySpecification.VERSION_APPROVED_V1_0,
    val isApproved: Boolean = true,
    val approvalTimestamp: Long? = 1704067200000L,
    val approvalTimestampIso: String? = "2024-01-01T00:00:00Z",
    val approvedBy: String? = "USER_GOVERNANCE",
    val ruleSpecificationHash: String? = null,
    val activeConfig: APlusTrendlineConfig = APlusTrendlineConfig.A_PLUS_DEFAULT_CONFIG,
    val confirmedRules: List<APlusRuleItem> = APlusStrategySpecification.CONFIRMED_RULES,
    val parameters: List<APlusParameterItem> = APlusStrategySpecification.DEFAULT_PARAMETERS,
    val approvedProfiles: List<ApprovedStrategyProfile> = emptyList()
) {
    val allParametersApproved: Boolean
        get() = parameters.all { it.isApproved }

    val isReadyForApproval: Boolean
        get() = allParametersApproved

    val approvedCount: Int
        get() = parameters.count { it.isApproved }

    val totalCount: Int
        get() = parameters.size
}

object APlusStrategySpecification {
    const val STRATEGY_NAME = "A+ Trendline System"
    const val STRATEGY_ID = "preset_a_plus_trendline_break_retest"
    
    // Profile Constants
    const val PROFILE_PENDING_APPROVAL = "A_PLUS_PENDING_APPROVAL"
    const val PROFILE_ENGINE_DEFAULT = "A_PLUS_ENGINE_DEFAULT"
    const val PROFILE_APPROVED_V1_0 = "A_PLUS_V1.0"
    
    const val VERSION_PENDING = "1.0.0-pending"
    const val VERSION_FROZEN = "1.0.0-frozen"
    const val VERSION_APPROVED_V1_0 = "1.0.0-approved"

    val CONFIRMED_RULES = listOf(
        APlusRuleItem(
            id = "R01",
            title = "Trendline Trading Primary Domain",
            category = RuleCategory.TRENDLINES,
            plainDescription = "Trendline trading is the primary strategy. Operates across swing pivot highs and lows.",
            engineImplementation = "System constructs multi-touch linear trendlines on confirmed pivot points (Descending Resistance & Ascending Support)."
        ),
        APlusRuleItem(
            id = "R02",
            title = "Market Structure Framework",
            category = RuleCategory.MARKET_STRUCTURE,
            plainDescription = "Market structure governs directional bias (Bullish = HH/HL, Bearish = LH/LL).",
            engineImplementation = "Swings classified as Higher High (HH), Higher Low (HL), Lower High (LH), Lower Low (LL). Confirms trend alignment before allowing trade triggers."
        ),
        APlusRuleItem(
            id = "R03",
            title = "Minimum 2 Meaningful Touches",
            category = RuleCategory.TRENDLINES,
            plainDescription = "Trendlines require at least 2 meaningful confirmed structural touches.",
            engineImplementation = "Threshold minTouches >= 2. Anchored on confirmed structural pivot points."
        ),
        APlusRuleItem(
            id = "R04",
            title = "Break Setups Preferred",
            category = RuleCategory.EXECUTION,
            plainDescription = "Break setups are prioritized over bounce setups for higher momentum.",
            engineImplementation = "Engine setup mode defaults to BREAK_AND_RETEST; filters pure bounce setups unless explicitly configured."
        ),
        APlusRuleItem(
            id = "R05",
            title = "Break-and-Retest Primary Setup",
            category = RuleCategory.EXECUTION,
            plainDescription = "Primary setup requires a breakout candle close followed by a pullback retest.",
            engineImplementation = "Breakout registers PendingRetest state. Enters only when price returns to line zone and rejects directionally."
        ),
        APlusRuleItem(
            id = "R06",
            title = "Entry Confirmation Required",
            category = RuleCategory.EXECUTION,
            plainDescription = "Entries require candle close confirmation rather than merely an intrabar wick crossing.",
            engineImplementation = "Evaluates candle close direction or rejection wick on retest bar. Never enters blindly on unconfirmed wick touches."
        ),
        APlusRuleItem(
            id = "R07",
            title = "Structural Stop Loss",
            category = RuleCategory.RISK_MANAGEMENT,
            plainDescription = "Stop loss must be anchored to valid market structure rather than arbitrary dollar or pip amounts.",
            engineImplementation = "Long stop placed below last confirmed swing low; Short stop placed above last confirmed swing high, plus safety buffer."
        ),
        APlusRuleItem(
            id = "R08",
            title = "Winners Allowed to Develop",
            category = RuleCategory.RISK_MANAGEMENT,
            plainDescription = "Winning trades should be allowed to run rather than automatically closing at a fixed 2R target.",
            engineImplementation = "Fixed Take Profit is unbounded (null). Position runs dynamically and exits via structural trailing stop."
        ),
        APlusRuleItem(
            id = "R09",
            title = "Structure-Based Break-Even",
            category = RuleCategory.RISK_MANAGEMENT,
            plainDescription = "Break-even is triggered based on structural expansion rather than arbitrary movement.",
            engineImplementation = "Stop loss ratchets to exact entry price once Maximum Favorable Excursion (MFE) reaches >= 1.0R."
        ),
        APlusRuleItem(
            id = "R10",
            title = "Structure-Based Trailing",
            category = RuleCategory.RISK_MANAGEMENT,
            plainDescription = "Trailing stop follows confirmed market structure pivots.",
            engineImplementation = "Long stop monotonically ratchets under confirmed Higher Lows; Short stop ratchets above Lower Highs as trend advances."
        ),
        APlusRuleItem(
            id = "R11",
            title = "Counter-Trend Structure Exit",
            category = RuleCategory.RISK_MANAGEMENT,
            plainDescription = "Counter-trend structure change triggers an immediate defensive exit.",
            engineImplementation = "If market prints and confirms a counter-trend structure break against active trade, position exits immediately at market."
        ),
        APlusRuleItem(
            id = "R12",
            title = "Consolidation Filtering & No Look-Ahead",
            category = RuleCategory.CAUSALITY,
            plainDescription = "Consolidating/unclear markets filtered out; strictly zero future information used in any decision.",
            engineImplementation = "Swings at bar p strictly invisible until bar p + confirmationBars. No future bar leakage across all calculations."
        )
    )

    val DEFAULT_PARAMETERS = listOf(
        APlusParameterItem(
            id = "swingLookback",
            name = "swingLookback",
            currentValueStr = "5",
            rawValue = 5.0,
            unit = "candles",
            exactDefinition = "Bar p is a local swing candidate iff High[p] >= High[k] (or Low[p] <= Low[k]) for all k in [p - swingLookback, p).",
            effect = "Controls structural pivot scale; isolates macro swing anchors from intra-session noise.",
            origin = "AI-PROPOSED",
            isApproved = true,
            minVal = 2.0,
            maxVal = 20.0,
            step = 1.0
        ),
        APlusParameterItem(
            id = "confirmationBars",
            name = "confirmationBars",
            currentValueStr = "3",
            rawValue = 3.0,
            unit = "candles",
            exactDefinition = "Candidate swing at bar p confirmed at bar i iff i = p + confirmationBars and High[p] >= High[k] (or Low[p] <= Low[k]) for all k in (p, p + confirmationBars].",
            effect = "Enforces causal confirmation delay so the engine cannot see or use pivot swings before they are chronologically confirmed.",
            origin = "AI-PROPOSED",
            isApproved = true,
            minVal = 1.0,
            maxVal = 10.0,
            step = 1.0
        ),
        APlusParameterItem(
            id = "minSwingSeparationBars",
            name = "minSwingSeparationBars",
            currentValueStr = "4",
            rawValue = 4.0,
            unit = "candles",
            exactDefinition = "|p2 - p1| >= minSwingSeparationBars between consecutive confirmed swings of the same type.",
            effect = "Prevents adjacent micro-wicks from being treated as distinct structural pivots and avoids degenerate trendline slopes.",
            origin = "AI-PROPOSED",
            isApproved = true,
            minVal = 1.0,
            maxVal = 15.0,
            step = 1.0
        ),
        APlusParameterItem(
            id = "minTouches",
            name = "minTouches",
            currentValueStr = "2",
            rawValue = 2.0,
            unit = "touches",
            exactDefinition = "Distinct chronological confirmed touches T >= minTouches satisfying |Extreme[k] - LinePrice[k]| <= RetestTolerance.",
            effect = "Validates trendlines. Single pivots or isolated wicks cannot form actionable lines.",
            origin = "USER-SPECIFIED",
            isApproved = true,
            minVal = 2.0,
            maxVal = 5.0,
            step = 1.0
        ),
        APlusParameterItem(
            id = "breakConfirmationATR",
            name = "breakConfirmationATR",
            currentValueStr = "0.10",
            rawValue = 0.10,
            unit = "ATR14",
            exactDefinition = "Long: Close[i] >= LinePrice[i] + 0.10 * ATR14; Short: Close[i] <= LinePrice[i] - 0.10 * ATR14.",
            effect = "Rejects shallow wick penetrations and requires decisive candle close momentum beyond the trendline.",
            origin = "AI-PROPOSED",
            isApproved = true,
            minVal = 0.05,
            maxVal = 1.00,
            step = 0.05
        ),
        APlusParameterItem(
            id = "retestToleranceATR",
            name = "retestToleranceATR",
            currentValueStr = "0.25",
            rawValue = 0.25,
            unit = "ATR14",
            exactDefinition = "Pullback touch band: [LinePrice[i] - 0.25 * ATR14, LinePrice[i] + 0.25 * ATR14].",
            effect = "Defines acceptable pullback zone without requiring tick-perfection across diverse asset classes.",
            origin = "AI-PROPOSED",
            isApproved = true,
            minVal = 0.10,
            maxVal = 1.00,
            step = 0.05
        ),
        APlusParameterItem(
            id = "retestMaxBars",
            name = "retestMaxBars",
            currentValueStr = "12",
            rawValue = 12.0,
            unit = "candles",
            exactDefinition = "i_retest - i_break <= retestMaxBars.",
            effect = "Invalidates stale setups if price drifts aimlessly without prompt structure retest.",
            origin = "AI-PROPOSED",
            isApproved = true,
            minVal = 3.0,
            maxVal = 40.0,
            step = 1.0
        ),
        APlusParameterItem(
            id = "stopBufferATR",
            name = "stopBufferATR",
            currentValueStr = "0.10",
            rawValue = 0.10,
            unit = "ATR14",
            exactDefinition = "Long: StopLoss = InvalidationSwingLow - 0.10 * ATR14; Short: StopLoss = InvalidationSwingHigh + 0.10 * ATR14.",
            effect = "Anchors protective stop strictly beyond structural invalidation point plus volatility cushion.",
            origin = "AI-PROPOSED",
            isApproved = true,
            minVal = 0.01,
            maxVal = 0.50,
            step = 0.01
        ),
        APlusParameterItem(
            id = "breakEvenActivation",
            name = "break-even activation",
            currentValueStr = "1.0 R MFE + New Confirmed Swing",
            rawValue = 1.0,
            unit = "R + Structure",
            exactDefinition = "Triggered iff MFE >= 1.0 * InitialRisk AND a new confirmed structural swing has formed in trade direction (HL for Long, LH for Short).",
            effect = "Moves stop to break-even only after favorable expansion and new structural support confirms original invalidation point is obsolete.",
            origin = "AI-PROPOSED",
            isApproved = true,
            minVal = 0.5,
            maxVal = 3.0,
            step = 0.25
        ),
        APlusParameterItem(
            id = "breakEvenOffset",
            name = "break-even offset",
            currentValueStr = "0.00",
            rawValue = 0.0,
            unit = "ATR14",
            exactDefinition = "StopLoss_BE = EntryPrice + (0.00 * ATR14).",
            effect = "Offsets break-even stop slightly into profit or exactly at entry price.",
            origin = "AI-PROPOSED",
            isApproved = true,
            minVal = 0.0,
            maxVal = 0.2,
            step = 0.01
        ),
        APlusParameterItem(
            id = "structuralTrailingRule",
            name = "structural trailing rule",
            currentValueStr = "Confirmed Swing HL/LH",
            rawValue = 1.0,
            unit = "Structure",
            exactDefinition = "Long: StopLoss = max(CurrentStop, ConfirmedHL_price - 0.10 * ATR14); Short: StopLoss = min(CurrentStop, ConfirmedLH_price + 0.10 * ATR14).",
            effect = "Trails stop monotonically behind newly confirmed structural pivots as the trend develops.",
            origin = "USER-SPECIFIED",
            isApproved = true
        ),
        APlusParameterItem(
            id = "consolidationFilter",
            name = "consolidation filter",
            currentValueStr = "ACTIVE (Strict Trend / Invalidation)",
            rawValue = 1.0,
            unit = "Structure State",
            exactDefinition = "Long allowed iff MarketStructure != BEARISH_TREND and MarketStructure != CONSOLIDATION; Short allowed iff MarketStructure != BULLISH_TREND and MarketStructure != CONSOLIDATION.",
            effect = "Rejects trades in choppy sideways markets or against confirmed macro structure.",
            origin = "USER-SPECIFIED",
            isApproved = true
        ),
        APlusParameterItem(
            id = "entryConfirmation",
            name = "entry confirmation",
            currentValueStr = "Rejection Candle Close",
            rawValue = 1.0,
            unit = "Price Action",
            exactDefinition = "Long: Close > Open OR lower wick >= 25% range with Close >= LinePrice - 0.05 * ATR14; Short: Close < Open OR upper wick >= 25% range with Close <= LinePrice + 0.05 * ATR14.",
            effect = "Requires candle close directional rejection at retest level before order entry; prohibits blind limit orders.",
            origin = "USER-SPECIFIED",
            isApproved = true
        ),
        APlusParameterItem(
            id = "setupMode",
            name = "setup mode",
            currentValueStr = "BREAK_AND_RETEST",
            rawValue = 1.0,
            unit = "Mode Enum",
            exactDefinition = "Sequential execution: Breakout -> Pullback Retest -> Rejection Confirmation -> Entry.",
            effect = "Focuses trading exclusively on high-probability break-and-retest trendline formations.",
            origin = "USER-SPECIFIED",
            isApproved = true
        ),
        APlusParameterItem(
            id = "takeProfitBehavior",
            name = "take-profit behavior",
            currentValueStr = "NOT SPECIFIED (Dynamic Structural Exit / No Fixed Target)",
            rawValue = 0.0,
            unit = "Target Cap",
            exactDefinition = "Fixed Take Profit = NULL. Position runs dynamically until trailing structural stop hit or counter-structure break.",
            effect = "Allows winning trades to run through extended trend legs without arbitrary profit capping.",
            origin = "USER-SPECIFIED",
            isApproved = true
        ),
        APlusParameterItem(
            id = "riskPerTrade",
            name = "risk per trade",
            currentValueStr = "1.0%",
            rawValue = 1.0,
            unit = "% Equity",
            exactDefinition = "RiskAmount = AccountEquity * 0.01.",
            effect = "Standardizes risk per trade across all market conditions and volatility regimes.",
            origin = "USER-SPECIFIED",
            isApproved = true,
            minVal = 0.25,
            maxVal = 5.0,
            step = 0.25
        ),
        APlusParameterItem(
            id = "positionSizing",
            name = "position sizing",
            currentValueStr = "Risk / Structural Stop Distance",
            rawValue = 1.0,
            unit = "Formula",
            exactDefinition = "Units = RiskAmount / |EntryPrice - InitialStopLoss|.",
            effect = "Sizes position dynamically so a hit on structural stop loses exactly the predefined risk percentage.",
            origin = "USER-SPECIFIED",
            isApproved = true
        ),
        APlusParameterItem(
            id = "commission",
            name = "commission",
            currentValueStr = "0.04%",
            rawValue = 0.04,
            unit = "% Notional",
            exactDefinition = "Fee = PositionNotionalValue * 0.0004 (applied per side on entry and exit).",
            effect = "Deducts realistic broker/exchange execution fees from trade PnL.",
            origin = "AI-PROPOSED",
            isApproved = true,
            minVal = 0.0,
            maxVal = 0.2,
            step = 0.01
        ),
        APlusParameterItem(
            id = "slippage",
            name = "slippage",
            currentValueStr = "0.02%",
            rawValue = 0.02,
            unit = "% Price",
            exactDefinition = "ExecutedEntry = ExpectedPrice * (1 +/- 0.0002); ExecutedExit = ExpectedPrice * (1 -/+ 0.0002).",
            effect = "Simulates realistic market order fill degradation during breakouts and stop liquidations.",
            origin = "AI-PROPOSED",
            isApproved = true,
            minVal = 0.0,
            maxVal = 0.1,
            step = 0.005
        )
    )

    /**
     * Computes the canonical specification string for A+ V1.0.
     */
    fun getCanonicalSpecificationString(
        config: APlusTrendlineConfig = APlusTrendlineConfig.A_PLUS_DEFAULT_CONFIG,
        dataset: String = "BTC_USD_H1_1609459200000_1704067200000",
        timeframe: String = "1H"
    ): String {
        return buildString {
            appendLine("=== A_PLUS_V1.0 CANONICAL SPECIFICATION ===")
            appendLine("A_PLUS_VERSION: 1.0.0-approved")
            appendLine("PROFILE: A_PLUS_V1.0")
            appendLine("DATASET: $dataset")
            appendLine("TIMEFRAME: $timeframe")
            appendLine("--- ALL_STRATEGY_PARAMETERS ---")
            appendLine("setupMode: ${config.setupMode.name}")
            appendLine("swingLookback: ${config.swingLookback}")
            appendLine("confirmationBars: ${config.confirmationBars}")
            appendLine("minSwingSeparationBars: ${config.minSwingSeparationBars}")
            appendLine("minTouches: ${config.minTouches}")
            appendLine("maxLineAgeBars: REMOVED / NOT USED")
            appendLine("breakConfirmationATR: ${String.format(Locale.US, "%.2f", config.breakConfirmationATR)}")
            appendLine("retestToleranceATR: ${String.format(Locale.US, "%.2f", config.retestToleranceATR)}")
            appendLine("retestMaxBars: ${config.retestMaxBars}")
            appendLine("requireStructureConfirmation: ${config.requireStructureConfirmation}")
            appendLine("useStructuralStop: ${config.useStructuralStop}")
            appendLine("stopBufferATR: ${String.format(Locale.US, "%.2f", config.stopBufferATR)}")
            appendLine("enableBreakEven: ${config.enableBreakEven}")
            appendLine("requireStructuralBreakEven: ${config.requireStructuralBreakEven}")
            appendLine("breakEvenTriggerR: ${String.format(Locale.US, "%.2f", config.breakEvenTriggerR)}")
            appendLine("enableStructuralTrailing: ${config.enableStructuralTrailing}")
            appendLine("exitOnCounterStructureBreak: ${config.exitOnCounterStructureBreak}")
            appendLine("fixedTakeProfitR: ${config.fixedTakeProfitR ?: "UNBOUNDED_RUNNER"}")
            appendLine("--- POSITION_SIZING ---")
            appendLine("positionSizingMode: RISK_BASED")
            appendLine("riskPercentage: 1.0% of current equity")
            appendLine("formula: positionQuantity = (currentEquity * 0.01) / abs(entryPrice - initialStopLossPrice)")
            appendLine("--- EXECUTION_COSTS ---")
            appendLine("commission: 0.04% (4.0 bps per side)")
            appendLine("slippage: 0.02% (2.0 bps)")
            appendLine("--- ENTRY_RULES ---")
            appendLine("entryRule: Confirmed rejection candle close on retest of broken trendline within 12 bars and 0.25 ATR")
            appendLine("--- STOP_RULES ---")
            appendLine("stopRule: Beyond structural invalidation swing (low for long, high for short) + 0.10 ATR buffer")
            appendLine("--- BREAK-EVEN_RULES ---")
            appendLine("breakEvenRule: Stop moves to entry price upon >= 1.0R MFE AND newly confirmed structural swing")
            appendLine("--- TRAILING_RULES ---")
            appendLine("trailingRule: Stop ratchets behind newly confirmed Higher Lows (Long) / Lower Highs (Short) + 0.10 ATR buffer")
            appendLine("--- EXIT_RULES ---")
            appendLine("exitRule: Stop loss hit OR counter-trend market structure confirmed break (Close < last confirmed HL for Long, Close > last confirmed LH for Short)")
        }
    }

    /**
     * Computes a deterministic SHA-256 hash of the complete rule specification + approved parameters.
     */
    fun computeSpecificationHash(
        config: APlusTrendlineConfig = APlusTrendlineConfig.A_PLUS_DEFAULT_CONFIG,
        profileName: String = PROFILE_APPROVED_V1_0,
        version: String = VERSION_APPROVED_V1_0
    ): String {
        val canonicalStr = getCanonicalSpecificationString(config)
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(canonicalStr.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun formatIsoTimestamp(timestampMs: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(timestampMs))
    }
}
