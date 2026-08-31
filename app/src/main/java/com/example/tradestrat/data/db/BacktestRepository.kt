package com.example.tradestrat.data.db

import com.example.tradestrat.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BacktestRepository(private val database: AppDatabase) {

    val savedStrategies: Flow<List<StrategyDefinition>> = database.strategyDao().getAllStrategies().map { list ->
        list.map { entity ->
            StrategyDefinition(
                id = entity.id,
                name = entity.name,
                description = entity.description,
                strategyType = StrategyType.valueOf(entity.strategyType),
                indicatorConfig = IndicatorConfig(
                    maParams = MovingAverageParams(
                        fastPeriod = entity.fastMaPeriod,
                        slowPeriod = entity.slowMaPeriod,
                        useEma = entity.useEma
                    ),
                    rsiParams = RsiParams(
                        period = entity.rsiPeriod,
                        oversoldThreshold = entity.rsiOversold,
                        overboughtThreshold = entity.rsiOverbought
                    ),
                    macdParams = MacdParams(
                        fastPeriod = entity.macdFast,
                        slowPeriod = entity.macdSlow,
                        signalPeriod = entity.macdSignal
                    ),
                    bollingerParams = BollingerParams(
                        period = entity.bbPeriod,
                        stdDevMultiplier = entity.bbStdDev
                    ),
                    supertrendParams = SupertrendParams(
                        atrPeriod = entity.stAtrPeriod,
                        multiplier = entity.stMultiplier
                    ),
                    donchianParams = DonchianParams(
                        period = entity.donchianPeriod
                    )
                ),
                isCustom = true
            )
        }
    }

    val savedBacktests: Flow<List<SavedBacktestEntity>> = database.savedBacktestDao().getAllBacktests()

    suspend fun saveStrategy(strategy: StrategyDefinition) {
        val entity = StrategyEntity(
            id = strategy.id,
            name = strategy.name,
            description = strategy.description,
            strategyType = strategy.strategyType.name,
            fastMaPeriod = strategy.indicatorConfig.maParams.fastPeriod,
            slowMaPeriod = strategy.indicatorConfig.maParams.slowPeriod,
            useEma = strategy.indicatorConfig.maParams.useEma,
            rsiPeriod = strategy.indicatorConfig.rsiParams.period,
            rsiOversold = strategy.indicatorConfig.rsiParams.oversoldThreshold,
            rsiOverbought = strategy.indicatorConfig.rsiParams.overboughtThreshold,
            macdFast = strategy.indicatorConfig.macdParams.fastPeriod,
            macdSlow = strategy.indicatorConfig.macdParams.slowPeriod,
            macdSignal = strategy.indicatorConfig.macdParams.signalPeriod,
            bbPeriod = strategy.indicatorConfig.bollingerParams.period,
            bbStdDev = strategy.indicatorConfig.bollingerParams.stdDevMultiplier,
            stAtrPeriod = strategy.indicatorConfig.supertrendParams.atrPeriod,
            stMultiplier = strategy.indicatorConfig.supertrendParams.multiplier,
            donchianPeriod = strategy.indicatorConfig.donchianParams.period
        )
        database.strategyDao().insertStrategy(entity)
    }

    suspend fun deleteStrategy(id: String) {
        database.strategyDao().deleteStrategyById(id)
    }

    suspend fun saveBacktestResult(result: BacktestResult) {
        val entity = SavedBacktestEntity(
            id = result.id,
            strategyName = result.strategy.name,
            strategyType = result.strategy.strategyType.name,
            assetSymbol = result.asset.symbol,
            regimeName = result.regime.title,
            timeframe = result.timeframe.label,
            initialCapital = result.metrics.initialCapital,
            finalEquity = result.metrics.finalEquity,
            netProfitPercent = result.metrics.netProfitPercent,
            benchmarkReturnPercent = result.metrics.benchmarkReturnPercent,
            winRatePercent = result.metrics.winRatePercent,
            totalTrades = result.metrics.totalTrades,
            profitFactor = result.metrics.profitFactor,
            maxDrawdownPercent = result.metrics.maxDrawdownPercent,
            sharpeRatio = result.metrics.sharpeRatio
        )
        database.savedBacktestDao().insertBacktest(entity)
    }

    suspend fun deleteBacktest(id: String) {
        database.savedBacktestDao().deleteBacktestById(id)
    }
}
