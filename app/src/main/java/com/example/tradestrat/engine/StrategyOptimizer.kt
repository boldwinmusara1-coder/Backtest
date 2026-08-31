package com.example.tradestrat.engine

import com.example.tradestrat.data.MarketDataProvider
import com.example.tradestrat.model.*

data class OptimizationResult(
    val param1Label: String,
    val param1Value: Double,
    val param2Label: String,
    val param2Value: Double,
    val netProfitPercent: Double,
    val winRatePercent: Double,
    val totalTrades: Int,
    val profitFactor: Double,
    val maxDrawdownPercent: Double,
    val sharpeRatio: Double,
    val strategy: StrategyDefinition
)

data class RegimeComparisonResult(
    val regime: MarketRegime,
    val netProfitPercent: Double,
    val benchmarkPercent: Double,
    val winRatePercent: Double,
    val totalTrades: Int,
    val maxDrawdownPercent: Double,
    val profitFactor: Double
)

object StrategyOptimizer {

    enum class OptimizationMetric(val displayName: String) {
        NET_PROFIT("Net Profit %"),
        SHARPE_RATIO("Sharpe Ratio"),
        PROFIT_FACTOR("Profit Factor"),
        WIN_RATE("Win Rate %"),
        MIN_DRAWDOWN("Lowest Drawdown")
    }

    /**
     * Runs a 2D parameter sweep grid for the selected strategy.
     */
    fun runParameterSweep(
        baseStrategy: StrategyDefinition,
        asset: MarketAsset,
        regime: MarketRegime,
        timeframe: Timeframe,
        risk: RiskParameters,
        candles: List<Candle>
    ): List<OptimizationResult> {
        val results = mutableListOf<OptimizationResult>()

        when (baseStrategy.strategyType) {
            StrategyType.MA_CROSSOVER -> {
                val fastValues = listOf(5, 9, 14, 20)
                val slowValues = listOf(21, 34, 50, 100, 200)
                for (fast in fastValues) {
                    for (slow in slowValues) {
                        if (fast >= slow) continue
                        val strat = baseStrategy.copy(
                            id = "opt_ma_${fast}_$slow",
                            name = "EMA ($fast / $slow)",
                            indicatorConfig = baseStrategy.indicatorConfig.copy(
                                maParams = baseStrategy.indicatorConfig.maParams.copy(
                                    fastPeriod = fast,
                                    slowPeriod = slow
                                )
                            )
                        )
                        val res = BacktestEngine.runBacktest(candles, asset, regime, timeframe, strat, risk)
                        results.add(
                            OptimizationResult(
                                param1Label = "Fast Period",
                                param1Value = fast.toDouble(),
                                param2Label = "Slow Period",
                                param2Value = slow.toDouble(),
                                netProfitPercent = res.metrics.netProfitPercent,
                                winRatePercent = res.metrics.winRatePercent,
                                totalTrades = res.metrics.totalTrades,
                                profitFactor = res.metrics.profitFactor,
                                maxDrawdownPercent = res.metrics.maxDrawdownPercent,
                                sharpeRatio = res.metrics.sharpeRatio,
                                strategy = strat
                            )
                        )
                    }
                }
            }

            StrategyType.RSI_MEAN_REVERSION -> {
                val rsiPeriods = listOf(7, 10, 14, 21)
                val oversoldLevels = listOf(20.0, 25.0, 30.0, 35.0)
                for (period in rsiPeriods) {
                    for (os in oversoldLevels) {
                        val ob = 100.0 - os
                        val strat = baseStrategy.copy(
                            id = "opt_rsi_${period}_${os.toInt()}",
                            name = "RSI ($period, $os/$ob)",
                            indicatorConfig = baseStrategy.indicatorConfig.copy(
                                rsiParams = baseStrategy.indicatorConfig.rsiParams.copy(
                                    period = period,
                                    oversoldThreshold = os,
                                    overboughtThreshold = ob
                                )
                            )
                        )
                        val res = BacktestEngine.runBacktest(candles, asset, regime, timeframe, strat, risk)
                        results.add(
                            OptimizationResult(
                                param1Label = "RSI Period",
                                param1Value = period.toDouble(),
                                param2Label = "Oversold Level",
                                param2Value = os,
                                netProfitPercent = res.metrics.netProfitPercent,
                                winRatePercent = res.metrics.winRatePercent,
                                totalTrades = res.metrics.totalTrades,
                                profitFactor = res.metrics.profitFactor,
                                maxDrawdownPercent = res.metrics.maxDrawdownPercent,
                                sharpeRatio = res.metrics.sharpeRatio,
                                strategy = strat
                            )
                        )
                    }
                }
            }

            StrategyType.BOLLINGER_REVERSION, StrategyType.BOLLINGER_BREAKOUT -> {
                val bbPeriods = listOf(14, 20, 30)
                val multipliers = listOf(1.5, 2.0, 2.5, 3.0)
                for (period in bbPeriods) {
                    for (mult in multipliers) {
                        val strat = baseStrategy.copy(
                            id = "opt_bb_${period}_$mult",
                            name = "BB ($period, ${mult}σ)",
                            indicatorConfig = baseStrategy.indicatorConfig.copy(
                                bollingerParams = baseStrategy.indicatorConfig.bollingerParams.copy(
                                    period = period,
                                    stdDevMultiplier = mult
                                )
                            )
                        )
                        val res = BacktestEngine.runBacktest(candles, asset, regime, timeframe, strat, risk)
                        results.add(
                            OptimizationResult(
                                param1Label = "BB Period",
                                param1Value = period.toDouble(),
                                param2Label = "StdDev Mult",
                                param2Value = mult,
                                netProfitPercent = res.metrics.netProfitPercent,
                                winRatePercent = res.metrics.winRatePercent,
                                totalTrades = res.metrics.totalTrades,
                                profitFactor = res.metrics.profitFactor,
                                maxDrawdownPercent = res.metrics.maxDrawdownPercent,
                                sharpeRatio = res.metrics.sharpeRatio,
                                strategy = strat
                            )
                        )
                    }
                }
            }

            StrategyType.OPENING_RANGE_BREAKOUT -> {
                val rangeBarsList = listOf(5, 10, 15, 20, 30)
                val volMultipliers = listOf(1.0, 1.2, 1.5, 2.0)
                for (rb in rangeBarsList) {
                    for (vm in volMultipliers) {
                        val strat = baseStrategy.copy(
                            id = "opt_orb_${rb}_${vm}",
                            name = "ORB (${rb} bars, ${vm}x Vol)",
                            indicatorConfig = baseStrategy.indicatorConfig.copy(
                                orbParams = baseStrategy.indicatorConfig.orbParams.copy(
                                    rangeBars = rb,
                                    volumeMultiplier = vm
                                )
                            )
                        )
                        val res = BacktestEngine.runBacktest(candles, asset, regime, timeframe, strat, risk)
                        results.add(
                            OptimizationResult(
                                param1Label = "Range Bars",
                                param1Value = rb.toDouble(),
                                param2Label = "Vol Multiplier",
                                param2Value = vm,
                                netProfitPercent = res.metrics.netProfitPercent,
                                winRatePercent = res.metrics.winRatePercent,
                                totalTrades = res.metrics.totalTrades,
                                profitFactor = res.metrics.profitFactor,
                                maxDrawdownPercent = res.metrics.maxDrawdownPercent,
                                sharpeRatio = res.metrics.sharpeRatio,
                                strategy = strat
                            )
                        )
                    }
                }
            }

            StrategyType.TRENDLINE_BREAK, StrategyType.TRENDLINE_BOUNCE -> {
                val lookbacks = listOf(5, 10, 15, 20)
                val confirmations = listOf(0.1, 0.3, 0.5, 1.0)
                for (lb in lookbacks) {
                    for (conf in confirmations) {
                        val strat = baseStrategy.copy(
                            id = "opt_tl_${lb}_${conf}",
                            name = "Trendline ($lb bars, ${conf}%)",
                            indicatorConfig = baseStrategy.indicatorConfig.copy(
                                trendlineParams = baseStrategy.indicatorConfig.trendlineParams.copy(
                                    pivotLookback = lb,
                                    confirmationThresholdPct = conf
                                )
                            )
                        )
                        val res = BacktestEngine.runBacktest(candles, asset, regime, timeframe, strat, risk)
                        results.add(
                            OptimizationResult(
                                param1Label = "Pivot Lookback",
                                param1Value = lb.toDouble(),
                                param2Label = "Confirm %",
                                param2Value = conf,
                                netProfitPercent = res.metrics.netProfitPercent,
                                winRatePercent = res.metrics.winRatePercent,
                                totalTrades = res.metrics.totalTrades,
                                profitFactor = res.metrics.profitFactor,
                                maxDrawdownPercent = res.metrics.maxDrawdownPercent,
                                sharpeRatio = res.metrics.sharpeRatio,
                                strategy = strat
                            )
                        )
                    }
                }
            }

            StrategyType.SMC_CONCEPTS -> {
                val bosLookbacks = listOf(3, 5, 8, 12)
                val obLookbacks = listOf(5, 10, 15, 20)
                for (bosLb in bosLookbacks) {
                    for (obLb in obLookbacks) {
                        val strat = baseStrategy.copy(
                            id = "opt_smc_${bosLb}_${obLb}",
                            name = "SMC (BOS $bosLb, OB $obLb)",
                            indicatorConfig = baseStrategy.indicatorConfig.copy(
                                smcConfig = baseStrategy.indicatorConfig.smcConfig.copy(
                                    bosLookback = bosLb,
                                    chochLookback = bosLb,
                                    obLookback = obLb
                                )
                            )
                        )
                        val res = BacktestEngine.runBacktest(candles, asset, regime, timeframe, strat, risk)
                        results.add(
                            OptimizationResult(
                                param1Label = "BOS Lookback",
                                param1Value = bosLb.toDouble(),
                                param2Label = "OB Lookback",
                                param2Value = obLb.toDouble(),
                                netProfitPercent = res.metrics.netProfitPercent,
                                winRatePercent = res.metrics.winRatePercent,
                                totalTrades = res.metrics.totalTrades,
                                profitFactor = res.metrics.profitFactor,
                                maxDrawdownPercent = res.metrics.maxDrawdownPercent,
                                sharpeRatio = res.metrics.sharpeRatio,
                                strategy = strat
                            )
                        )
                    }
                }
            }

            StrategyType.ICT_CONCEPTS -> {
                val fvgMultipliers = listOf(0.2, 0.4, 0.6, 0.8)
                val sweepWicks = listOf(0.10, 0.15, 0.25, 0.35)
                for (fvgMult in fvgMultipliers) {
                    for (sweepWick in sweepWicks) {
                        val strat = baseStrategy.copy(
                            id = "opt_ict_${fvgMult}_${sweepWick}",
                            name = "ICT (FVG ${fvgMult}x, Sweep ${sweepWick}%)",
                            indicatorConfig = baseStrategy.indicatorConfig.copy(
                                smcConfig = baseStrategy.indicatorConfig.smcConfig.copy(
                                    fvgMinGapAtrMultiple = fvgMult,
                                    sweepWickMinPct = sweepWick
                                )
                            )
                        )
                        val res = BacktestEngine.runBacktest(candles, asset, regime, timeframe, strat, risk)
                        results.add(
                            OptimizationResult(
                                param1Label = "FVG Gap ATR",
                                param1Value = fvgMult,
                                param2Label = "Sweep Wick %",
                                param2Value = sweepWick,
                                netProfitPercent = res.metrics.netProfitPercent,
                                winRatePercent = res.metrics.winRatePercent,
                                totalTrades = res.metrics.totalTrades,
                                profitFactor = res.metrics.profitFactor,
                                maxDrawdownPercent = res.metrics.maxDrawdownPercent,
                                sharpeRatio = res.metrics.sharpeRatio,
                                strategy = strat
                            )
                        )
                    }
                }
            }

            StrategyType.SMC_ICT_CONCEPTS -> {
                val confluences = listOf(1, 2, 3)
                val obLookbacks = listOf(8, 15, 20)
                for (conf in confluences) {
                    for (obLb in obLookbacks) {
                        val strat = baseStrategy.copy(
                            id = "opt_smc_ict_${conf}_${obLb}",
                            name = "SMC/ICT (Min Conf $conf, OB $obLb)",
                            indicatorConfig = baseStrategy.indicatorConfig.copy(
                                smcConfig = baseStrategy.indicatorConfig.smcConfig.copy(
                                    minConfluences = conf,
                                    requireConfluence = conf > 1,
                                    obLookback = obLb
                                )
                            )
                        )
                        val res = BacktestEngine.runBacktest(candles, asset, regime, timeframe, strat, risk)
                        results.add(
                            OptimizationResult(
                                param1Label = "Min Confluence",
                                param1Value = conf.toDouble(),
                                param2Label = "OB Lookback",
                                param2Value = obLb.toDouble(),
                                netProfitPercent = res.metrics.netProfitPercent,
                                winRatePercent = res.metrics.winRatePercent,
                                totalTrades = res.metrics.totalTrades,
                                profitFactor = res.metrics.profitFactor,
                                maxDrawdownPercent = res.metrics.maxDrawdownPercent,
                                sharpeRatio = res.metrics.sharpeRatio,
                                strategy = strat
                            )
                        )
                    }
                }
            }

            else -> {
                // Default fallback: ATR period vs Multiplier
                val atrPeriods = listOf(7, 10, 14, 20)
                val mults = listOf(2.0, 2.5, 3.0, 4.0)
                for (atr in atrPeriods) {
                    for (mult in mults) {
                        val strat = baseStrategy.copy(
                            id = "opt_st_${atr}_$mult",
                            name = "Supertrend ($atr, ${mult}x)",
                            indicatorConfig = baseStrategy.indicatorConfig.copy(
                                supertrendParams = baseStrategy.indicatorConfig.supertrendParams.copy(
                                    atrPeriod = atr,
                                    multiplier = mult
                                )
                            )
                        )
                        val res = BacktestEngine.runBacktest(candles, asset, regime, timeframe, strat, risk)
                        results.add(
                            OptimizationResult(
                                param1Label = "ATR Period",
                                param1Value = atr.toDouble(),
                                param2Label = "Multiplier",
                                param2Value = mult,
                                netProfitPercent = res.metrics.netProfitPercent,
                                winRatePercent = res.metrics.winRatePercent,
                                totalTrades = res.metrics.totalTrades,
                                profitFactor = res.metrics.profitFactor,
                                maxDrawdownPercent = res.metrics.maxDrawdownPercent,
                                sharpeRatio = res.metrics.sharpeRatio,
                                strategy = strat
                            )
                        )
                    }
                }
            }
        }

        return results
    }

    /**
     * Evaluates the strategy across all market regimes to measure robustness using supplied real historical data.
     */
    fun evaluateAcrossRegimesWithData(
        strategy: StrategyDefinition,
        asset: MarketAsset,
        timeframe: Timeframe,
        risk: RiskParameters,
        realDataByRegime: Map<MarketRegime, List<Candle>>
    ): List<RegimeComparisonResult> {
        return MarketRegime.values().map { regime ->
            val candles = realDataByRegime[regime] ?: emptyList()
            val res = BacktestEngine.runBacktest(candles, asset, regime, timeframe, strategy, risk)
            RegimeComparisonResult(
                regime = regime,
                netProfitPercent = res.metrics.netProfitPercent,
                benchmarkPercent = res.metrics.benchmarkReturnPercent,
                winRatePercent = res.metrics.winRatePercent,
                totalTrades = res.metrics.totalTrades,
                maxDrawdownPercent = res.metrics.maxDrawdownPercent,
                profitFactor = res.metrics.profitFactor
            )
        }
    }

    /**
     * Evaluates the strategy across all market regimes.
     */
    fun evaluateAcrossRegimes(
        strategy: StrategyDefinition,
        asset: MarketAsset,
        timeframe: Timeframe,
        risk: RiskParameters,
        realDataByRegime: Map<MarketRegime, List<Candle>>? = null
    ): List<RegimeComparisonResult> {
        if (realDataByRegime != null) {
            return evaluateAcrossRegimesWithData(strategy, asset, timeframe, risk, realDataByRegime)
        }
        return MarketRegime.values().map { regime ->
            val candles = MarketDataProvider.generateHistoricalData(asset, regime, timeframe, 300)
            val res = BacktestEngine.runBacktest(candles, asset, regime, timeframe, strategy, risk)
            RegimeComparisonResult(
                regime = regime,
                netProfitPercent = res.metrics.netProfitPercent,
                benchmarkPercent = res.metrics.benchmarkReturnPercent,
                winRatePercent = res.metrics.winRatePercent,
                totalTrades = res.metrics.totalTrades,
                maxDrawdownPercent = res.metrics.maxDrawdownPercent,
                profitFactor = res.metrics.profitFactor
            )
        }
    }
}
