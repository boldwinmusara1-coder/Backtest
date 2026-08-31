package com.example.tradestrat.model

enum class StrategyType(val title: String, val subtitle: String, val badge: String) {
    A_PLUS_TRENDLINE("A+ Trendline System", "Deterministic market structure, swing trendlines, break/retest execution & structural trailing", "A+ Price Action"),
    MA_CROSSOVER("Moving Average Crossover", "Fast MA cross Slow MA (Golden/Death Cross)", "Trend Following"),
    RSI_MEAN_REVERSION("RSI Mean Reversion", "Oversold bounce entry and overbought exit", "Momentum / Reversal"),
    MACD_MOMENTUM("MACD Signal & Zero Cross", "MACD line crosses Signal line with histogram confirmation", "Trend / Momentum"),
    BOLLINGER_BREAKOUT("Bollinger Band Breakout", "Volatility squeeze & upper/lower band breakout", "Volatility"),
    BOLLINGER_REVERSION("Bollinger Mean Reversion", "Buy lower band touch, take profit at middle/upper band", "Mean Reversion"),
    SUPERTREND_RUN("Supertrend Trend Follower", "ATR-based dynamic trailing trend filter", "Trend"),
    TURTLE_BREAKOUT("Donchian / Turtle Breakout", "20-bar channel high/low breakout system", "Breakout"),
    OPENING_RANGE_BREAKOUT("Opening Range Breakout (ORB)", "Trades breakout of initial session price range with volume surge", "Price Action / Breakout"),
    TRENDLINE_BREAK("Trendline Break", "Enters when price breaks through swing pivot support/resistance trendlines", "Price Action / Breakout"),
    TRENDLINE_BOUNCE("Trendline Bounce", "Buys pullbacks bouncing off support trendlines with rejection candles", "Support & Resistance"),
    MULTI_CONFLUENCE("Multi-Indicator Confluence", "Trend filter + RSI pullback + ATR stop confirmation", "Advanced Confluence"),
    SMC_CONCEPTS("Smart Money Concepts (SMC)", "Institutional market structure: BOS, CHOCH, Order Blocks, and Breaker Blocks", "Smart Money"),
    ICT_CONCEPTS("Inner Circle Trader (ICT)", "FVG Imbalances, Liquidity Sweeps, Killzones, Displacement & Premium/Discount", "ICT Concepts"),
    SMC_ICT_CONCEPTS("SMC / ICT Combined", "Combined institutional framework merging structural SMC and ICT liquidity concepts", "Institutional Price Action")
}

const val STRATEGY_ID_TRENDLINE_BREAK_HIGH_WIN_RATE = "TRENDLINE_BREAK_HIGH_WIN_RATE"
const val STRATEGY_ID_A_PLUS_V1_0 = "A_PLUS_V1_0"

/**
 * Benchmark reference statistics used for objective display in the Strategy Information Panel
 * and fair strategy comparisons.
 */
data class StrategyBenchmarkReference(
    val strategyId: String,
    val strategyName: String,
    val strategyVersion: String,
    val referenceType: String, // "Historical Reference" or "Official Frozen Baseline"
    val description: String,
    val riskModel: String,
    val riskPerTrade: String,
    val timeframeLabel: String,
    val totalTrades: Int,
    val winningTrades: Int,
    val losingTrades: Int,
    val winRatePercent: Double,
    val netPnlDollars: Double,
    val roiPercent: Double,
    val profitFactor: Double,
    val expectancyDollars: Double,
    val averageR: Double,
    val maxDrawdownPercent: Double,
    val maxLossStreak: Int,
    val maxWinStreak: Int,
    val sharpeRatio: Double,
    val sortinoRatio: Double,
    val calmarRatio: Double
)

object StrategyBenchmarkRegistry {
    val HIGH_WIN_RATE_1H = StrategyBenchmarkReference(
        strategyId = STRATEGY_ID_TRENDLINE_BREAK_HIGH_WIN_RATE,
        strategyName = "Trendline Break — High Win Rate",
        strategyVersion = "1.0.0-reference",
        referenceType = "Historical Reference",
        description = "Previously tested high-win-rate version capturing decisive pivot breakouts with fixed notional risk and rapid trend capture.",
        riskModel = "Fixed Dollar / 2x Margin ($2,000)",
        riskPerTrade = "$2,000 Position / 3.0% Stop ($120 Risk)",
        timeframeLabel = "1H",
        totalTrades = 31,
        winningTrades = 23,
        losingTrades = 8,
        winRatePercent = 74.19,
        netPnlDollars = 4323.82,
        roiPercent = 43.24,
        profitFactor = 5.47,
        expectancyDollars = 139.48,
        averageR = 1.16,
        maxDrawdownPercent = 4.22,
        maxLossStreak = 3,
        maxWinStreak = 8,
        sharpeRatio = 12.32,
        sortinoRatio = 18.45,
        calmarRatio = 10.25
    )

    val HIGH_WIN_RATE_30M = StrategyBenchmarkReference(
        strategyId = STRATEGY_ID_TRENDLINE_BREAK_HIGH_WIN_RATE,
        strategyName = "Trendline Break — High Win Rate",
        strategyVersion = "1.0.0-reference",
        referenceType = "Historical Reference",
        description = "Previously tested high-win-rate version capturing decisive pivot breakouts with fixed notional risk and rapid trend capture.",
        riskModel = "Fixed Dollar / 2x Margin ($2,000)",
        riskPerTrade = "$2,000 Position / 3.0% Stop ($120 Risk)",
        timeframeLabel = "30M",
        totalTrades = 30,
        winningTrades = 24,
        losingTrades = 6,
        winRatePercent = 80.00,
        netPnlDollars = 4383.11,
        roiPercent = 43.83,
        profitFactor = 6.61,
        expectancyDollars = 146.10,
        averageR = 1.22,
        maxDrawdownPercent = 2.47,
        maxLossStreak = 2,
        maxWinStreak = 10,
        sharpeRatio = 17.92,
        sortinoRatio = 24.10,
        calmarRatio = 17.74
    )

    val A_PLUS_FROZEN_1H = StrategyBenchmarkReference(
        strategyId = STRATEGY_ID_A_PLUS_V1_0,
        strategyName = "A+ Trendline V1.0 — Frozen",
        strategyVersion = "1.0.0-approved",
        referenceType = "Official Frozen Baseline",
        description = "Official frozen baseline A+ Trendline system with strict HH/HL market structure, break-and-retest execution, 1.0% equity risk-based sizing, and structural trailing stops.",
        riskModel = "Risk-Based (1.0% Equity)",
        riskPerTrade = "1.0% of Current Equity / Structural Invalidation Stop",
        timeframeLabel = "1H",
        totalTrades = 62,
        winningTrades = 29,
        losingTrades = 33,
        winRatePercent = 46.77,
        netPnlDollars = 892.98,
        roiPercent = 8.93,
        profitFactor = 1.31,
        expectancyDollars = 14.40,
        averageR = 0.28,
        maxDrawdownPercent = 7.91,
        maxLossStreak = 5,
        maxWinStreak = 6,
        sharpeRatio = 0.81,
        sortinoRatio = 1.15,
        calmarRatio = 1.13
    )

    fun getReference(strategyId: String, timeframe: Timeframe): StrategyBenchmarkReference? {
        return when (strategyId) {
            STRATEGY_ID_TRENDLINE_BREAK_HIGH_WIN_RATE, "preset_trendline_break" -> {
                if (timeframe == Timeframe.M30) HIGH_WIN_RATE_30M else HIGH_WIN_RATE_1H
            }
            STRATEGY_ID_A_PLUS_V1_0, "preset_a_plus_trendline_break_retest", "preset_a_plus_v1_baseline", "a_plus_trendline_default" -> {
                A_PLUS_FROZEN_1H
            }
            else -> null
        }
    }
}

val STRATEGY_TRENDLINE_BREAK_HIGH_WIN_RATE = StrategyDefinition(
    id = STRATEGY_ID_TRENDLINE_BREAK_HIGH_WIN_RATE,
    name = "Trendline Break — High Win Rate",
    description = "Previously tested high-win-rate version capturing decisive pivot breakouts with confirmed touches and rapid trend capture.",
    strategyType = StrategyType.TRENDLINE_BREAK,
    indicatorConfig = IndicatorConfig(
        trendlineParams = TrendlineParams(
            pivotLookback = 10,
            pivotStrength = 5,
            minTouches = 2,
            maxLineAge = 120,
            confirmationThresholdPct = 0.3,
            retestRequired = false,
            useRsiFilter = false,
            useMaTrendFilter = false
        )
    )
)

val STRATEGY_A_PLUS_V1_0 = StrategyDefinition(
    id = STRATEGY_ID_A_PLUS_V1_0,
    name = "A+ Trendline V1.0 — Frozen",
    description = "Official frozen baseline A+ Trendline system with strict HH/HL market structure, break-and-retest execution, 1.0% equity risk-based sizing, and structural trailing stops.",
    strategyType = StrategyType.A_PLUS_TRENDLINE,
    indicatorConfig = IndicatorConfig(
        aPlusTrendlineConfig = APlusTrendlineConfig(
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
            isApproved = true,
            profileName = "A_PLUS_V1.0",
            specificationHash = "d5c19207e05031bfe74659b87df8fc3c5aaee11f5fe6d4b58bb3f0ba5b5a8e0f"
        )
    )
)

data class StrategyDefinition(
    val id: String = STRATEGY_ID_A_PLUS_V1_0,
    val name: String = "A+ Trendline V1.0 — Frozen",
    val description: String = "Official frozen baseline A+ Trendline system with strict HH/HL market structure, break-and-retest execution, 1.0% equity risk-based sizing, and structural trailing stops.",
    val strategyType: StrategyType = StrategyType.A_PLUS_TRENDLINE,
    val indicatorConfig: IndicatorConfig = IndicatorConfig(
        aPlusTrendlineConfig = APlusTrendlineConfig(
            setupMode = TrendlineSetupMode.BREAK_AND_RETEST,
            swingLookback = 5,
            confirmationBars = 3,
            minSwingSeparationBars = 4,
            minTouches = 2,
            breakConfirmationATR = 0.10,
            retestToleranceATR = 0.25,
            retestMaxBars = 12,
            stopBufferATR = 0.10,
            enableBreakEven = true,
            requireStructuralBreakEven = true,
            breakEvenTriggerR = 1.0,
            enableStructuralTrailing = true,
            exitOnCounterStructureBreak = true,
            isApproved = true,
            profileName = "A_PLUS_V1.0",
            specificationHash = "d5c19207e05031bfe74659b87df8fc3c5aaee11f5fe6d4b58bb3f0ba5b5a8e0f"
        )
    ),
    val isCustom: Boolean = false
) {
    companion object {
        val PRESETS = listOf(
            STRATEGY_TRENDLINE_BREAK_HIGH_WIN_RATE,
            STRATEGY_A_PLUS_V1_0,
            StrategyDefinition(
                id = "preset_a_plus_trendline_break_retest",
                name = "A+ Trendline System (Break & Retest)",
                description = "Enters on confirmed break and subsequent pullback retest of multi-touch trendlines aligned with market structure (HH/HL or LH/LL), managing risk with structural trailing stops.",
                strategyType = StrategyType.A_PLUS_TRENDLINE,
                indicatorConfig = IndicatorConfig(
                    aPlusTrendlineConfig = APlusTrendlineConfig(
                        setupMode = TrendlineSetupMode.BREAK_AND_RETEST,
                        swingLookback = 5,
                        confirmationBars = 3,
                        minTouches = 2,
                        breakConfirmationATR = 0.10,
                        retestToleranceATR = 0.25,
                        stopBufferATR = 0.10,
                        enableBreakEven = true,
                        requireStructuralBreakEven = true,
                        enableStructuralTrailing = true,
                        exitOnCounterStructureBreak = true
                    )
                )
            ),
            StrategyDefinition(
                id = "preset_a_plus_trendline_break",
                name = "A+ Trendline System (Direct Breakout)",
                description = "Enters immediately upon confirmed structural candle close beyond multi-touch trendlines with strict causal confirmation and structural stop loss placement.",
                strategyType = StrategyType.A_PLUS_TRENDLINE,
                indicatorConfig = IndicatorConfig(
                    aPlusTrendlineConfig = APlusTrendlineConfig(
                        setupMode = TrendlineSetupMode.BREAK_ONLY,
                        swingLookback = 5,
                        confirmationBars = 3,
                        minTouches = 2,
                        breakConfirmationATR = 0.10,
                        stopBufferATR = 0.10,
                        enableBreakEven = true,
                        requireStructuralBreakEven = true,
                        enableStructuralTrailing = true,
                        exitOnCounterStructureBreak = true
                    )
                )
            ),
            StrategyDefinition(
                id = "preset_a_plus_trendline_bounce",
                name = "A+ Trendline System (Break or Bounce)",
                description = "Comprehensive A+ system executing both confirmed trendline breaks and structural pullback bounces off valid ascending/descending trendlines.",
                strategyType = StrategyType.A_PLUS_TRENDLINE,
                indicatorConfig = IndicatorConfig(
                    aPlusTrendlineConfig = APlusTrendlineConfig(
                        setupMode = TrendlineSetupMode.BREAK_OR_BOUNCE,
                        swingLookback = 5,
                        confirmationBars = 3,
                        minTouches = 2,
                        breakConfirmationATR = 0.10,
                        retestToleranceATR = 0.25,
                        stopBufferATR = 0.10,
                        enableBreakEven = true,
                        requireStructuralBreakEven = true,
                        enableStructuralTrailing = true,
                        exitOnCounterStructureBreak = true
                    )
                )
            ),
            StrategyDefinition(
                id = "preset_orb_breakout",
                name = "Opening Range Breakout (ORB 30m)",
                description = "Classic Opening Range Breakout strategy establishing high/low in the first 30 minutes with volume and trend confirmation.",
                strategyType = StrategyType.OPENING_RANGE_BREAKOUT,
                indicatorConfig = IndicatorConfig(
                    orbParams = OrbParams(
                        openingRangeMinutes = 30,
                        volumeMultiplier = 1.2,
                        breakoutBufferPct = 0.0,
                        useEmaTrendFilter = true,
                        emaTrendPeriod = 50,
                        useRsiFilter = true,
                        rsiThreshold = 50.0
                    )
                )
            ),
            StrategyDefinition(
                id = "preset_orb_defensive",
                name = "Opening Range Breakout (ORB 30m Defensive / Low DD)",
                description = "Conservative ORB profile: 0.05% breakout buffer, 1.2x volume confirmation, 50 EMA trend alignment, 14 RSI filter, and tight 2.5% stop loss for 24.4% lower maximum drawdown.",
                strategyType = StrategyType.OPENING_RANGE_BREAKOUT,
                indicatorConfig = IndicatorConfig(
                    orbParams = OrbParams(
                        openingRangeMinutes = 30,
                        volumeMultiplier = 1.2,
                        breakoutBufferPct = 0.05,
                        useEmaTrendFilter = true,
                        emaTrendPeriod = 50,
                        useRsiFilter = true,
                        rsiThreshold = 50.0
                    )
                )
            ),
            StrategyDefinition(
                id = "preset_orb_standard",
                name = "Opening Range Breakout (ORB 30m Standard)",
                description = "Standard high-capture ORB profile: 0.00% breakout buffer, 1.2x volume confirmation, 50 EMA trend filter, 14 RSI filter, and 3.0% stop loss for maximum trend capture.",
                strategyType = StrategyType.OPENING_RANGE_BREAKOUT,
                indicatorConfig = IndicatorConfig(
                    orbParams = OrbParams(
                        openingRangeMinutes = 30,
                        volumeMultiplier = 1.2,
                        breakoutBufferPct = 0.0,
                        useEmaTrendFilter = true,
                        emaTrendPeriod = 50,
                        useRsiFilter = true,
                        rsiThreshold = 50.0
                    )
                )
            ),
            StrategyDefinition(
                id = "preset_trendline_break",
                name = "Pivot Trendline Break",
                description = "Identifies key swing highs/lows and enters on decisive structural breakout through resistance/support trendlines.",
                strategyType = StrategyType.TRENDLINE_BREAK,
                indicatorConfig = IndicatorConfig(
                    trendlineParams = TrendlineParams(pivotLookback = 10, confirmationThresholdPct = 0.3)
                )
            ),
            StrategyDefinition(
                id = "preset_trendline_bounce",
                name = "Support Trendline Bounce",
                description = "Enters on high-probability pullback retests of established support trendlines with bullish candlestick rejection.",
                strategyType = StrategyType.TRENDLINE_BOUNCE,
                indicatorConfig = IndicatorConfig(
                    trendlineParams = TrendlineParams(pivotLookback = 10, confirmationThresholdPct = 0.4)
                )
            ),
            StrategyDefinition(
                id = "preset_ema_cross",
                name = "EMA Golden Cross (9/21)",
                description = "Classic exponential moving average crossover capturing medium-term trends with responsive 9/21 periods.",
                strategyType = StrategyType.MA_CROSSOVER,
                indicatorConfig = IndicatorConfig(
                    maParams = MovingAverageParams(fastPeriod = 9, slowPeriod = 21, useEma = true)
                )
            ),
            StrategyDefinition(
                id = "preset_rsi_mean_rev",
                name = "RSI Swing Reversal (30/70)",
                description = "Buys when RSI drops below oversold threshold (30) and recovers; exits/shorts when RSI exceeds overbought threshold (70).",
                strategyType = StrategyType.RSI_MEAN_REVERSION,
                indicatorConfig = IndicatorConfig(
                    rsiParams = RsiParams(period = 14, oversoldThreshold = 30.0, overboughtThreshold = 70.0)
                )
            ),
            StrategyDefinition(
                id = "preset_macd_trend",
                name = "MACD Momentum (12/26/9)",
                description = "Standard MACD indicator crossing signal line with momentum histogram expansion.",
                strategyType = StrategyType.MACD_MOMENTUM,
                indicatorConfig = IndicatorConfig(
                    macdParams = MacdParams(fastPeriod = 12, slowPeriod = 26, signalPeriod = 9)
                )
            ),
            StrategyDefinition(
                id = "preset_bb_breakout",
                name = "Bollinger Volatility Breakout",
                description = "Rides explosive trend expansion when candles close outside the 2.0σ upper band after a squeeze.",
                strategyType = StrategyType.BOLLINGER_BREAKOUT,
                indicatorConfig = IndicatorConfig(
                    bollingerParams = BollingerParams(period = 20, stdDevMultiplier = 2.0)
                )
            ),
            StrategyDefinition(
                id = "preset_supertrend",
                name = "Supertrend Trend Rider",
                description = "ATR-based dynamic trailing envelope riding sustained directional runs with built-in volatility tracking.",
                strategyType = StrategyType.SUPERTREND_RUN,
                indicatorConfig = IndicatorConfig(
                    supertrendParams = SupertrendParams(atrPeriod = 10, multiplier = 3.0)
                )
            ),
            StrategyDefinition(
                id = "preset_turtle",
                name = "Turtle Donchian Breakout",
                description = "Legendary Richard Dennis Turtle system entering on 20-period price highs/lows.",
                strategyType = StrategyType.TURTLE_BREAKOUT,
                indicatorConfig = IndicatorConfig(
                    donchianParams = DonchianParams(period = 20)
                )
            ),
            StrategyDefinition(
                id = "preset_confluence",
                name = "Triple Indicator Confluence",
                description = "Enters long only when Price > 50 EMA AND RSI pulls back to 40-50 zone AND MACD histogram turns positive.",
                strategyType = StrategyType.MULTI_CONFLUENCE,
                indicatorConfig = IndicatorConfig(
                    maParams = MovingAverageParams(fastPeriod = 20, slowPeriod = 50, useEma = true),
                    rsiParams = RsiParams(period = 14, oversoldThreshold = 45.0, overboughtThreshold = 65.0),
                    macdParams = MacdParams(fastPeriod = 12, slowPeriod = 26, signalPeriod = 9)
                )
            ),
            // SMC (Smart Money Concepts) First-Class Presets
            StrategyDefinition(
                id = "preset_smc_structure_shift",
                name = "SMC: Market Structure Shift (BOS + CHOCH)",
                description = "Trades trend changes on Break of Structure (BOS) and Change of Character (CHOCH / MSS) with confirmed pivot breaks.",
                strategyType = StrategyType.SMC_CONCEPTS,
                indicatorConfig = IndicatorConfig(
                    smcConfig = SmcConfig(
                        useBos = true,
                        useChoch = true,
                        useLiquiditySweep = false,
                        useFvg = false,
                        useOrderBlock = false,
                        useBreakerBlock = false,
                        requireConfluence = false,
                        minConfluences = 1
                    )
                )
            ),
            StrategyDefinition(
                id = "preset_smc_order_block",
                name = "SMC: Order Block Retest",
                description = "Trades pullback entries into institutional Order Blocks created prior to structural displacement moves.",
                strategyType = StrategyType.SMC_CONCEPTS,
                indicatorConfig = IndicatorConfig(
                    smcConfig = SmcConfig(
                        useBos = true,
                        useChoch = false,
                        useLiquiditySweep = false,
                        useFvg = false,
                        useOrderBlock = true,
                        useBreakerBlock = false,
                        obLookback = 15,
                        obMitigationRequired = true,
                        requireConfluence = false,
                        minConfluences = 1
                    )
                )
            ),
            StrategyDefinition(
                id = "preset_smc_breaker_block",
                name = "SMC: Breaker Block Support/Resistance",
                description = "Trades failed order blocks that flipped polarity after a liquidity run and market structure break.",
                strategyType = StrategyType.SMC_CONCEPTS,
                indicatorConfig = IndicatorConfig(
                    smcConfig = SmcConfig(
                        useBos = true,
                        useChoch = false,
                        useLiquiditySweep = false,
                        useFvg = false,
                        useOrderBlock = false,
                        useBreakerBlock = true,
                        breakerLookback = 15,
                        requireConfluence = false,
                        minConfluences = 1
                    )
                )
            ),

            // ICT (Inner Circle Trader) First-Class Presets
            StrategyDefinition(
                id = "preset_ict_fvg_retest",
                name = "ICT: Fair Value Gap (FVG) Retest",
                description = "Captures institutional imbalance retests when price returns to fill 3-candle Fair Value Gaps in the direction of order flow.",
                strategyType = StrategyType.ICT_CONCEPTS,
                indicatorConfig = IndicatorConfig(
                    smcConfig = SmcConfig(
                        useBos = false,
                        useChoch = false,
                        useLiquiditySweep = false,
                        useFvg = true,
                        useOrderBlock = false,
                        useBreakerBlock = false,
                        fvgMinGapAtrMultiple = 0.3,
                        fvgMitigationType = FvgMitigationType.TOUCH,
                        requireConfluence = false,
                        minConfluences = 1
                    )
                )
            ),
            StrategyDefinition(
                id = "preset_ict_liquidity_sweep",
                name = "ICT: Liquidity Sweep & Stop Hunt",
                description = "Identifies false breakouts beyond key swing highs/lows (stop hunts) where price wicks out liquidity and closes back in range.",
                strategyType = StrategyType.ICT_CONCEPTS,
                indicatorConfig = IndicatorConfig(
                    smcConfig = SmcConfig(
                        useBos = false,
                        useChoch = false,
                        useLiquiditySweep = true,
                        useFvg = false,
                        useOrderBlock = false,
                        useBreakerBlock = false,
                        sweepLookback = 10,
                        sweepWickMinPct = 0.15,
                        requireConfluence = false,
                        minConfluences = 1
                    )
                )
            ),
            StrategyDefinition(
                id = "preset_ict_displacement",
                name = "ICT: Killzone & Displacement Expansion",
                description = "Enters momentum expansion during institutional killzone windows following high-ATR displacement candles.",
                strategyType = StrategyType.ICT_CONCEPTS,
                indicatorConfig = IndicatorConfig(
                    smcConfig = SmcConfig(
                        useBos = false,
                        useChoch = false,
                        useLiquiditySweep = false,
                        useFvg = true,
                        useDisplacement = true,
                        displacementAtrMultiplier = 1.6,
                        useSessionFilter = false,
                        requireConfluence = false,
                        minConfluences = 1
                    )
                )
            ),
            StrategyDefinition(
                id = "preset_ict_premium_discount",
                name = "ICT: Premium/Discount Equilibrium",
                description = "Filters entries to strictly buy in Discount (<50% range) and sell in Premium (>50% range) with FVG / Liquidity confirmation.",
                strategyType = StrategyType.ICT_CONCEPTS,
                indicatorConfig = IndicatorConfig(
                    smcConfig = SmcConfig(
                        useBos = false,
                        useChoch = false,
                        useLiquiditySweep = true,
                        useFvg = true,
                        usePremiumDiscount = true,
                        discountThresholdPct = 50.0,
                        requireConfluence = false,
                        minConfluences = 1
                    )
                )
            ),

            // Combined Multi-Confluence Preset
            StrategyDefinition(
                id = "preset_smc_ict_full_confluence",
                name = "SMC & ICT: Institutional Full Confluence",
                description = "High-conviction setup requiring multi-factor confluence: BOS/CHOCH structure alignment + Order Block / FVG mitigation in Discount/Premium.",
                strategyType = StrategyType.SMC_ICT_CONCEPTS,
                indicatorConfig = IndicatorConfig(
                    smcConfig = SmcConfig(
                        useBos = true,
                        useChoch = true,
                        useLiquiditySweep = true,
                        useFvg = true,
                        useOrderBlock = true,
                        useBreakerBlock = true,
                        usePremiumDiscount = true,
                        useDisplacement = true,
                        useEqualHighsLows = true,
                        useSessionFilter = false,
                        requireConfluence = true,
                        minConfluences = 2
                    )
                )
            )
        )
    }
}
