package com.example.tradestrat.model

data class EquityPoint(
    val barIndex: Int,
    val timestamp: Long,
    val equity: Double,
    val cash: Double,
    val drawdownPct: Double,
    val benchmarkEquity: Double,
    val price: Double
)

data class MonthlyReturn(
    val year: Int,
    val month: Int,
    val returnPct: Double,
    val tradesCount: Int
)

data class DataSourceInfo(
    val provider: String,
    val symbol: String,
    val market: String,
    val timeframe: String,
    val startDate: String,
    val endDate: String,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val candleCount: Int,
    val lastRefreshTime: Long = System.currentTimeMillis(),
    val isRealHistorical: Boolean = true,
    val validationStatus: String = "VALIDATED_INTEGRITY_PASSED",
    val unexpectedGapsCount: Int = 0,
    val intrabarExecutionRule: String = "Conservative (Stop Loss First)",
    val executionModel: String = "Realistic (Next Bar Open)",
    val dataHash: String = "",
    val datasetId: String = "",
    val timezone: String = "UTC"
)

data class BacktestMetrics(
    val initialCapital: Double,
    val finalEquity: Double,
    val netProfitDollars: Double,
    val netProfitPercent: Double,
    val grossProfitDollars: Double = 0.0,
    val grossLossDollars: Double = 0.0,
    val benchmarkReturnPercent: Double,
    val alphaPercent: Double,
    val cagrPercent: Double,
    val maxDrawdownDollars: Double = 0.0,
    val maxDrawdownPercent: Double,
    val maxDrawdownDurationBars: Int,
    val recoveryPeriodBars: Int = 0,
    val sharpeRatio: Double,
    val sortinoRatio: Double,
    val calmarRatio: Double,
    val totalTrades: Int,
    val winningTrades: Int,
    val losingTrades: Int,
    val winRatePercent: Double,
    val profitFactor: Double,
    val payoffRatio: Double, // Avg Win / Avg Loss
    val avgTradePercent: Double,
    val avgWinningTradePercent: Double,
    val avgLosingTradePercent: Double,
    val avgWinDollars: Double = 0.0,
    val avgLossDollars: Double = 0.0,
    val avgRMultiple: Double = 0.0,
    val largestWinningTradeDollars: Double,
    val largestLosingTradeDollars: Double,
    val maxConsecutiveWins: Int,
    val maxConsecutiveLosses: Int,
    val totalFeesPaid: Double,
    val avgHoldingBars: Double,
    val expectancyDollars: Double,
    val expectancyR: Double,
    val p25TradeDollars: Double = 0.0,
    val medianTradeDollars: Double = 0.0,
    val p75TradeDollars: Double = 0.0,
    val iqrTradeDollars: Double = 0.0
)

data class CalculatedIndicators(
    val fastMa: List<Double?> = emptyList(),
    val slowMa: List<Double?> = emptyList(),
    val rsi: List<Double?> = emptyList(),
    val macdLine: List<Double?> = emptyList(),
    val macdSignal: List<Double?> = emptyList(),
    val macdHist: List<Double?> = emptyList(),
    val bbUpper: List<Double?> = emptyList(),
    val bbMiddle: List<Double?> = emptyList(),
    val bbLower: List<Double?> = emptyList(),
    val atr: List<Double?> = emptyList(),
    val supertrend: List<Double?> = emptyList(),
    val donchianUpper: List<Double?> = emptyList(),
    val donchianLower: List<Double?> = emptyList()
)

data class SignalMarker(
    val barIndex: Int,
    val timestamp: Long,
    val price: Double,
    val direction: TradeDirection,
    val isEntry: Boolean,
    val exitReason: ExitReason? = null,
    val signalReason: String? = null
)

data class BacktestResult(
    val id: String,
    val timestamp: Long = System.currentTimeMillis(),
    val asset: MarketAsset,
    val regime: MarketRegime,
    val timeframe: Timeframe,
    val strategy: StrategyDefinition,
    val riskParams: RiskParameters,
    val candles: List<Candle>,
    val indicators: CalculatedIndicators,
    val trades: List<Trade>,
    val equityCurve: List<EquityPoint>,
    val signalMarkers: List<SignalMarker>,
    val metrics: BacktestMetrics,
    val smcMetrics: SmcMetrics? = null,
    val aPlusVisualData: APlusStrategyVisualData? = null,
    val aPlusForensicAuditLog: APlusForensicAuditLog? = null,
    val strategyName: String = strategy.name,
    val strategyVersion: String = if (strategy.strategyType == StrategyType.A_PLUS_TRENDLINE) {
        if (strategy.indicatorConfig.aPlusTrendlineConfig.isApproved) APlusStrategySpecification.VERSION_APPROVED_V1_0 else APlusStrategySpecification.VERSION_PENDING
    } else "1.0.0",
    val profileName: String = if (strategy.strategyType == StrategyType.A_PLUS_TRENDLINE) strategy.indicatorConfig.aPlusTrendlineConfig.profileName else strategy.id,
    val parameterSnapshot: Map<String, Any> = if (strategy.strategyType == StrategyType.A_PLUS_TRENDLINE) strategy.indicatorConfig.aPlusTrendlineConfig.toParameterMap() else emptyMap(),
    val ruleSpecificationHash: String? = if (strategy.strategyType == StrategyType.A_PLUS_TRENDLINE) {
        strategy.indicatorConfig.aPlusTrendlineConfig.specificationHash ?: APlusStrategySpecification.computeSpecificationHash(
            config = strategy.indicatorConfig.aPlusTrendlineConfig,
            profileName = strategy.indicatorConfig.aPlusTrendlineConfig.profileName,
            version = if (strategy.indicatorConfig.aPlusTrendlineConfig.isApproved) APlusStrategySpecification.VERSION_APPROVED_V1_0 else APlusStrategySpecification.VERSION_PENDING
        )
    } else null,
    val isGated: Boolean = false,
    val gateReason: String? = null,
    val dataSource: DataSourceInfo = DataSourceInfo(
        provider = "Real Historical API",
        symbol = asset.symbol,
        market = asset.category.label,
        timeframe = timeframe.label,
        startDate = if (candles.isNotEmpty()) candles.first().formattedDate(timeframe.minutes) else "",
        endDate = if (candles.isNotEmpty()) candles.last().formattedDate(timeframe.minutes) else "",
        startTimestamp = candles.firstOrNull()?.timestamp ?: 0L,
        endTimestamp = candles.lastOrNull()?.timestamp ?: 0L,
        candleCount = candles.size,
        isRealHistorical = true
    )
)
