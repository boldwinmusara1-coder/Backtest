package com.example.tradestrat.model

import com.example.tradestrat.ui.components.DateRangePreset
import com.example.tradestrat.ui.components.ProviderSelection
import java.text.SimpleDateFormat
import java.util.*

/**
 * Immutable Backtest Configuration Model.
 * Represents a completely specified, reproducible backtest experiment.
 * 
 * Separates Strategy Definition and Experiment Parameters from Execution and Results.
 */
data class BacktestConfiguration(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Backtest Experiment",
    val timestamp: Long = System.currentTimeMillis(),
    
    // 1. Strategy Definition
    val strategy: StrategyDefinition = StrategyDefinition.PRESETS.first(),
    
    // 2. Instrument & Market Data
    val asset: MarketAsset = MarketAsset(
        id = "btc_usdt",
        symbol = "BTC/USD",
        name = "Bitcoin",
        category = AssetCategory.CRYPTO,
        basePrice = 64250.0,
        description = "Global benchmark crypto asset"
    ),
    val marketRegime: MarketRegime = MarketRegime.HISTORICAL_REALISTIC,
    val timeframe: Timeframe = Timeframe.H1,
    val provider: ProviderSelection = ProviderSelection.AUTO,
    val datePreset: DateRangePreset = DateRangePreset.DAYS_180,
    val startDate: String = calculateStartDate(datePreset),
    val endDate: String = calculateEndDate(),
    val startTimestamp: Long = calculateStartTimestamp(datePreset),
    val endTimestamp: Long = System.currentTimeMillis(),
    val customCsvContent: String? = null,
    
    // 3. Risk & Capital Management
    val initialCapital: Double = 10000.0,
    val positionSizingMode: PositionSizingMode = PositionSizingMode.PERCENT_EQUITY,
    val riskPerTrade: Double = 25.0, // % of equity or $ or ATR risk
    val leverage: Double = 1.0,
    val allowShorting: Boolean = true,
    val maxDrawdownCircuitBreakerPct: Double = 30.0,
    
    // 4. Stop Loss & Take Profit
    val stopLossType: StopLossType = StopLossType.PERCENTAGE,
    val stopLossValue: Double = 3.0,
    val takeProfitType: TakeProfitType = TakeProfitType.RISK_REWARD_RATIO,
    val takeProfitValue: Double = 2.0,
    
    // 5. Execution Assumptions & Friction
    val commissionBps: Double = 10.0, // 10 bps = 0.10%
    val slippageBps: Double = 5.0,     // 5 bps = 0.05%
    val executionModel: ExecutionModel = ExecutionModel.REALISTIC,
    val intrabarExecution: IntrabarExecutionAssumption = IntrabarExecutionAssumption.PESSIMISTIC_STOP_FIRST,
    
    // 6. Strategy Parameter Snapshot (Key-Value map for reproducibility)
    val strategyParams: Map<String, Any> = extractStrategyParameters(strategy)
) {
    /**
     * Converts configuration to RiskParameters for BacktestEngine execution.
     */
    fun toRiskParameters(): RiskParameters {
        return RiskParameters(
            initialCapital = initialCapital,
            positionSizingMode = positionSizingMode,
            positionSizeValue = riskPerTrade,
            stopLossType = stopLossType,
            stopLossValue = stopLossValue,
            takeProfitType = takeProfitType,
            takeProfitValue = takeProfitValue,
            slippageBps = slippageBps,
            commissionBps = commissionBps,
            leverage = leverage,
            allowShorting = allowShorting,
            maxDrawdownCircuitBreakerPct = maxDrawdownCircuitBreakerPct,
            executionModel = executionModel,
            intrabarExecution = intrabarExecution
        )
    }

    /**
     * Builds DataSourceInfo tracking data provenance.
     */
    fun toDataSourceInfo(candleCount: Int = 0, isRealHistorical: Boolean = true, validationStatus: String = "VALIDATED_INTEGRITY_PASSED"): DataSourceInfo {
        return DataSourceInfo(
            provider = provider.label,
            symbol = asset.symbol,
            market = asset.category.label,
            timeframe = timeframe.label,
            startDate = startDate,
            endDate = endDate,
            startTimestamp = startTimestamp,
            endTimestamp = endTimestamp,
            candleCount = candleCount,
            isRealHistorical = isRealHistorical,
            validationStatus = validationStatus,
            intrabarExecutionRule = intrabarExecution.label,
            executionModel = executionModel.label,
            datasetId = "${asset.symbol}_${timeframe.label}_${startDate}_${endDate}"
        )
    }

    companion object {
        fun calculateStartDate(preset: DateRangePreset): String {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            cal.add(Calendar.DAY_OF_YEAR, -preset.days)
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            return sdf.format(cal.time)
        }

        fun calculateEndDate(): String {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            return sdf.format(cal.time)
        }

        fun calculateStartTimestamp(preset: DateRangePreset): Long {
            return System.currentTimeMillis() - (preset.days.toLong() * 24L * 60L * 60L * 1000L)
        }

        fun parseDateToTimestamp(dateStr: String, defaultTimestamp: Long): Long {
            return try {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                sdf.parse(dateStr)?.time ?: defaultTimestamp
            } catch (e: Exception) {
                defaultTimestamp
            }
        }

        fun extractStrategyParameters(strategy: StrategyDefinition): Map<String, Any> {
            val map = mutableMapOf<String, Any>()
            map["strategyType"] = strategy.strategyType.name
            map["strategyId"] = strategy.id
            map["strategyName"] = strategy.name
            
            val ic = strategy.indicatorConfig
            when (strategy.strategyType) {
                StrategyType.MA_CROSSOVER -> {
                    map["fastPeriod"] = ic.maParams.fastPeriod
                    map["slowPeriod"] = ic.maParams.slowPeriod
                    map["useEma"] = ic.maParams.useEma
                }
                StrategyType.RSI_MEAN_REVERSION -> {
                    map["period"] = ic.rsiParams.period
                    map["oversoldThreshold"] = ic.rsiParams.oversoldThreshold
                    map["overboughtThreshold"] = ic.rsiParams.overboughtThreshold
                }
                StrategyType.MACD_MOMENTUM -> {
                    map["fastPeriod"] = ic.macdParams.fastPeriod
                    map["slowPeriod"] = ic.macdParams.slowPeriod
                    map["signalPeriod"] = ic.macdParams.signalPeriod
                }
                StrategyType.BOLLINGER_BREAKOUT, StrategyType.BOLLINGER_REVERSION -> {
                    map["period"] = ic.bollingerParams.period
                    map["stdDevMultiplier"] = ic.bollingerParams.stdDevMultiplier
                }
                StrategyType.SUPERTREND_RUN -> {
                    map["atrPeriod"] = ic.supertrendParams.atrPeriod
                    map["multiplier"] = ic.supertrendParams.multiplier
                }
                StrategyType.TURTLE_BREAKOUT -> {
                    map["period"] = ic.donchianParams.period
                }
                StrategyType.OPENING_RANGE_BREAKOUT -> {
                    map["openingRangeMinutes"] = ic.orbParams.openingRangeMinutes
                    map["volumeMultiplier"] = ic.orbParams.volumeMultiplier
                    map["breakoutBufferPct"] = ic.orbParams.breakoutBufferPct
                    map["useEmaTrendFilter"] = ic.orbParams.useEmaTrendFilter
                    map["emaTrendPeriod"] = ic.orbParams.emaTrendPeriod
                    map["useRsiFilter"] = ic.orbParams.useRsiFilter
                    map["rsiThreshold"] = ic.orbParams.rsiThreshold
                }
                StrategyType.TRENDLINE_BREAK, StrategyType.TRENDLINE_BOUNCE -> {
                    map["pivotLookback"] = ic.trendlineParams.pivotLookback
                    map["pivotStrength"] = ic.trendlineParams.pivotStrength
                    map["minTouches"] = ic.trendlineParams.minTouches
                    map["maxLineAge"] = ic.trendlineParams.maxLineAge
                    map["confirmationThresholdPct"] = ic.trendlineParams.confirmationThresholdPct
                    map["retestRequired"] = ic.trendlineParams.retestRequired
                    map["useRsiFilter"] = ic.trendlineParams.useRsiFilter
                    map["useMaTrendFilter"] = ic.trendlineParams.useMaTrendFilter
                }
                StrategyType.SMC_CONCEPTS, StrategyType.ICT_CONCEPTS, StrategyType.SMC_ICT_CONCEPTS -> {
                    val smc = ic.smcConfig
                    map["useBos"] = smc.useBos
                    map["useChoch"] = smc.useChoch
                    map["useLiquiditySweep"] = smc.useLiquiditySweep
                    map["useFvg"] = smc.useFvg
                    map["useOrderBlock"] = smc.useOrderBlock
                    map["useBreakerBlock"] = smc.useBreakerBlock
                    map["usePremiumDiscount"] = smc.usePremiumDiscount
                    map["useDisplacement"] = smc.useDisplacement
                    map["obLookback"] = smc.obLookback
                    map["breakerLookback"] = smc.breakerLookback
                    map["sweepLookback"] = smc.sweepLookback
                    map["sweepWickMinPct"] = smc.sweepWickMinPct
                    map["fvgMinGapAtrMultiple"] = smc.fvgMinGapAtrMultiple
                    map["displacementAtrMultiplier"] = smc.displacementAtrMultiplier
                    map["discountThresholdPct"] = smc.discountThresholdPct
                    map["requireConfluence"] = smc.requireConfluence
                    map["minConfluences"] = smc.minConfluences
                }
                StrategyType.A_PLUS_TRENDLINE -> {
                    val aplus = ic.aPlusTrendlineConfig
                    map["setupMode"] = aplus.setupMode.name
                    map["swingLookback"] = aplus.swingLookback
                    map["confirmationBars"] = aplus.confirmationBars
                    map["minSwingSeparationBars"] = aplus.minSwingSeparationBars
                    map["minTouches"] = aplus.minTouches
                    map["breakConfirmationATR"] = aplus.breakConfirmationATR
                    map["retestToleranceATR"] = aplus.retestToleranceATR
                    map["retestMaxBars"] = aplus.retestMaxBars
                    map["stopBufferATR"] = aplus.stopBufferATR
                    map["breakEvenTriggerR"] = aplus.breakEvenTriggerR
                    map["requireStructuralBreakEven"] = aplus.requireStructuralBreakEven
                    map["enableStructuralTrailing"] = aplus.enableStructuralTrailing
                    map["exitOnCounterStructureBreak"] = aplus.exitOnCounterStructureBreak
                    map["isApproved"] = aplus.isApproved
                    map["specificationHash"] = aplus.specificationHash ?: ""
                }
                StrategyType.MULTI_CONFLUENCE -> {
                    map["maFastPeriod"] = ic.maParams.fastPeriod
                    map["maSlowPeriod"] = ic.maParams.slowPeriod
                    map["rsiPeriod"] = ic.rsiParams.period
                    map["rsiOversold"] = ic.rsiParams.oversoldThreshold
                    map["rsiOverbought"] = ic.rsiParams.overboughtThreshold
                    map["macdFast"] = ic.macdParams.fastPeriod
                    map["macdSlow"] = ic.macdParams.slowPeriod
                    map["macdSignal"] = ic.macdParams.signalPeriod
                }
            }
            return map
        }
    }
}
