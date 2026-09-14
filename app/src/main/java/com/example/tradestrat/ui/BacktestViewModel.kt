package com.example.tradestrat.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tradestrat.data.MarketDataRepository
import com.example.tradestrat.data.MarketDataProvider
import com.example.tradestrat.data.db.AppDatabase
import com.example.tradestrat.data.db.BacktestRepository
import com.example.tradestrat.data.db.SavedBacktestEntity
import com.example.tradestrat.engine.*
import com.example.tradestrat.model.*
import com.example.tradestrat.ui.components.DateRangePreset
import com.example.tradestrat.ui.components.ProviderSelection
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class BacktestViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BacktestRepository(AppDatabase.getDatabase(application))
    private val marketDataRepo = MarketDataRepository()

    // UI Configuration States
    private val _selectedAsset = MutableStateFlow(MarketDataProvider.ASSETS.first())
    val selectedAsset = _selectedAsset.asStateFlow()

    private val _selectedRegime = MutableStateFlow(MarketRegime.HISTORICAL_REALISTIC)
    val selectedRegime = _selectedRegime.asStateFlow()

    private val _selectedTimeframe = MutableStateFlow(Timeframe.D1)
    val selectedTimeframe = _selectedTimeframe.asStateFlow()

    private val _selectedStrategy = MutableStateFlow(StrategyDefinition.PRESETS.first())
    val selectedStrategy = _selectedStrategy.asStateFlow()

    private val _riskParameters = MutableStateFlow(RiskParameters())
    val riskParameters = _riskParameters.asStateFlow()

    // Real Historical Data Feed States
    private val _selectedProvider = MutableStateFlow(ProviderSelection.AUTO)
    val selectedProvider = _selectedProvider.asStateFlow()

    private val _selectedDatePreset = MutableStateFlow(DateRangePreset.DAYS_180)
    val selectedDatePreset = _selectedDatePreset.asStateFlow()

    private val _customStartDate = MutableStateFlow<String?>(null)
    val customStartDate = _customStartDate.asStateFlow()

    private val _customEndDate = MutableStateFlow<String?>(null)
    val customEndDate = _customEndDate.asStateFlow()

    private val _dataFetchError = MutableStateFlow<String?>(null)
    val dataFetchError = _dataFetchError.asStateFlow()

    private val _dataSourceInfo = MutableStateFlow<DataSourceInfo?>(null)
    val dataSourceInfo = _dataSourceInfo.asStateFlow()

    private val _apiKey = MutableStateFlow<String?>(null)
    val apiKey = _apiKey.asStateFlow()

    private val _customCsvContent = MutableStateFlow<String?>(null)
    val customCsvContent = _customCsvContent.asStateFlow()

    // Custom & Experimental Strategies
    private val _customStrategies = MutableStateFlow<List<StrategyDefinition>>(emptyList())
    val customStrategies = _customStrategies.asStateFlow()

    // All available strategies (Presets + Custom + DB Saved)
    val allStrategies: StateFlow<List<StrategyDefinition>> = combine(
        _customStrategies,
        repository.savedStrategies
    ) { custom, saved ->
        val list = mutableListOf<StrategyDefinition>()
        list.addAll(StrategyDefinition.PRESETS)
        for (c in custom) {
            if (list.none { it.id == c.id }) list.add(c)
        }
        for (s in saved) {
            if (list.none { it.id == s.id }) list.add(s)
        }
        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StrategyDefinition.PRESETS)

    // Unified Backtest Configuration Model State
    private val _pendingConfiguration = MutableStateFlow(
        BacktestConfiguration(
            strategy = StrategyDefinition.PRESETS.first(),
            asset = MarketDataProvider.ASSETS.first(),
            marketRegime = MarketRegime.HISTORICAL_REALISTIC,
            timeframe = Timeframe.D1,
            provider = ProviderSelection.AUTO,
            datePreset = DateRangePreset.DAYS_180,
            initialCapital = 10000.0,
            riskPerTrade = 25.0,
            positionSizingMode = PositionSizingMode.PERCENT_EQUITY,
            stopLossType = StopLossType.PERCENTAGE,
            stopLossValue = 3.0,
            takeProfitType = TakeProfitType.RISK_REWARD_RATIO,
            takeProfitValue = 2.0,
            commissionBps = 10.0,
            slippageBps = 5.0,
            executionModel = ExecutionModel.REALISTIC,
            intrabarExecution = IntrabarExecutionAssumption.PESSIMISTIC_STOP_FIRST
        )
    )
    val pendingConfiguration = _pendingConfiguration.asStateFlow()

    fun setCustomCsvContent(csv: String?) {
        _customCsvContent.value = csv
        if (!csv.isNullOrBlank()) {
            _selectedProvider.value = ProviderSelection.CSV_IMPORT
        }
        syncPendingConfiguration()
    }

    // Execution Outputs
    private val _currentResult = MutableStateFlow<BacktestResult?>(null)
    val currentResult = _currentResult.asStateFlow()

    private val _isBacktesting = MutableStateFlow(false)
    val isBacktesting = _isBacktesting.asStateFlow()

    private val _backtestProgress = MutableStateFlow(BacktestProgress())
    val backtestProgress = _backtestProgress.asStateFlow()

    private var backtestJob: Job? = null

    // Optimization & Regime Stress Test
    private val _optimizationResults = MutableStateFlow<List<OptimizationResult>>(emptyList())
    val optimizationResults = _optimizationResults.asStateFlow()

    private val _isOptimizing = MutableStateFlow(false)
    val isOptimizing = _isOptimizing.asStateFlow()

    private var optimizationJob: Job? = null

    private val _regimeComparison = MutableStateFlow<List<RegimeComparisonResult>>(emptyList())
    val regimeComparison = _regimeComparison.asStateFlow()

    private val _isComparingRegimes = MutableStateFlow(false)
    val isComparingRegimes = _isComparingRegimes.asStateFlow()

    private val _healthScorecard = MutableStateFlow<StrategyHealthScorecard?>(null)
    val healthScorecard = _healthScorecard.asStateFlow()

    // Strategy Lab (Comparison on Identical Dataset)
    private val _strategyLabItems = MutableStateFlow<List<StrategyLabItem>>(emptyList())
    val strategyLabItems = _strategyLabItems.asStateFlow()

    private val _isStrategyLabRunning = MutableStateFlow(false)
    val isStrategyLabRunning = _isStrategyLabRunning.asStateFlow()

    // Multi-Strategy Comparison Dashboard States
    private val _comparisonSelectedStrategies = MutableStateFlow<Set<String>>(
        StrategyDefinition.PRESETS.take(4).map { it.id }.toSet()
    )
    val comparisonSelectedStrategies = _comparisonSelectedStrategies.asStateFlow()

    private val _multiStrategyComparisonResult = MutableStateFlow<MultiStrategyComparisonResult?>(null)
    val multiStrategyComparisonResult = _multiStrategyComparisonResult.asStateFlow()

    private val _isComparingMultiStrategies = MutableStateFlow(false)
    val isComparingMultiStrategies = _isComparingMultiStrategies.asStateFlow()

    // Dual Trendline Strategy Comparison (High-Win-Rate vs A+ V1.0)
    private val _dualStrategyComparisonData = MutableStateFlow<DualStrategyComparisonData?>(null)
    val dualStrategyComparisonData = _dualStrategyComparisonData.asStateFlow()

    private val _latestHwrResult = MutableStateFlow<BacktestResult?>(null)
    val latestHwrResult = _latestHwrResult.asStateFlow()

    private val _latestAplusResult = MutableStateFlow<BacktestResult?>(null)
    val latestAplusResult = _latestAplusResult.asStateFlow()

    private val _isDualComparing = MutableStateFlow(false)
    val isDualComparing = _isDualComparing.asStateFlow()

    private val _showDualComparisonDialog = MutableStateFlow(false)
    val showDualComparisonDialog = _showDualComparisonDialog.asStateFlow()

    private val _comparisonSortMetric = MutableStateFlow(ComparisonSortMetric.NET_PNL)
    val comparisonSortMetric = _comparisonSortMetric.asStateFlow()

    private val _rankWeights = MutableStateFlow(StrategyRankWeights())
    val rankWeights = _rankWeights.asStateFlow()

    private val _selectedComparisonDetailItem = MutableStateFlow<StrategyComparisonItem?>(null)
    val selectedComparisonDetailItem = _selectedComparisonDetailItem.asStateFlow()

    // Market Search & Favorites
    private val _favoriteSymbols = MutableStateFlow<Set<String>>(setOf("BTCUSDT", "EURUSD", "SPY"))
    val favoriteSymbols = _favoriteSymbols.asStateFlow()

    private val _recentSymbols = MutableStateFlow<List<String>>(listOf("BTCUSDT", "ETHUSDT", "EURUSD", "AAPL"))
    val recentSymbols = _recentSymbols.asStateFlow()

    // Trade Inspection & Detail View
    private val _selectedTradeForDetail = MutableStateFlow<Trade?>(null)
    val selectedTradeForDetail = _selectedTradeForDetail.asStateFlow()

    // Trade Journal
    private val _journalEntries = MutableStateFlow<List<JournalEntry>>(emptyList())
    val journalEntries = _journalEntries.asStateFlow()

    // Multi-Timeframe Workspace
    private val _isMtfEnabled = MutableStateFlow(false)
    val isMtfEnabled = _isMtfEnabled.asStateFlow()

    private val _mtfConfirmationTimeframe = MutableStateFlow(Timeframe.M15)
    val mtfConfirmationTimeframe = _mtfConfirmationTimeframe.asStateFlow()

    private val _mtfConfirmationCandles = MutableStateFlow<List<Candle>>(emptyList())
    val mtfConfirmationCandles = _mtfConfirmationCandles.asStateFlow()

    // Historical Replay & Manual Trading Engine
    private val _isReplayActive = MutableStateFlow(false)
    val isReplayActive = _isReplayActive.asStateFlow()

    private val _replayAllCandles = MutableStateFlow<List<Candle>>(emptyList())
    private val _replayCurrentIndex = MutableStateFlow(0)
    val replayCurrentIndex = _replayCurrentIndex.asStateFlow()

    private val _replaySpeed = MutableStateFlow(1.0f) // 0.25x, 0.5x, 1x, 2x, 5x
    val replaySpeed = _replaySpeed.asStateFlow()

    private val _isReplayPlaying = MutableStateFlow(false)
    val isReplayPlaying = _isReplayPlaying.asStateFlow()

    private val _activeManualPosition = MutableStateFlow<ManualReplayPosition?>(null)
    val activeManualPosition = _activeManualPosition.asStateFlow()

    private val _manualReplayTrades = MutableStateFlow<List<Trade>>(emptyList())
    val manualReplayTrades = _manualReplayTrades.asStateFlow()

    private var replayTimerJob: Job? = null

    // Database Flows
    val savedStrategies: StateFlow<List<StrategyDefinition>> = repository.savedStrategies
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedBacktests: StateFlow<List<SavedBacktestEntity>> = repository.savedBacktests
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Derived Analytics Flows
    val sessionAnalytics: StateFlow<List<SessionAnalytics>> = _currentResult.map { result ->
        if (result == null || result.trades.isEmpty()) emptyList()
        else computeSessionAnalytics(result.trades)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val maeMfeDistribution: StateFlow<MaeMfeDistribution?> = _currentResult.map { result ->
        if (result == null || result.trades.isEmpty()) null
        else computeMaeMfe(result.trades)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // A+ Strategy Specification Approval State
    private val _aplusApprovalState = MutableStateFlow(APlusApprovalState())
    val aplusApprovalState = _aplusApprovalState.asStateFlow()

    init {
        // Safe startup: do not execute backtest automatically on launch
    }

    fun syncPendingConfiguration() {
        _pendingConfiguration.value = buildCurrentBacktestConfiguration()
    }

    fun buildCurrentBacktestConfiguration(): BacktestConfiguration {
        val asset = _selectedAsset.value
        val regime = _selectedRegime.value
        val tf = _selectedTimeframe.value
        val strat = _selectedStrategy.value
        val risk = _riskParameters.value
        val preset = _selectedDatePreset.value
        val prov = _selectedProvider.value
        val now = System.currentTimeMillis()
        val startMs = now - (preset.days.toLong() * 24L * 60L * 60L * 1000L)

        return BacktestConfiguration(
            strategy = strat,
            asset = asset,
            marketRegime = regime,
            timeframe = tf,
            provider = prov,
            datePreset = preset,
            startDate = _customStartDate.value ?: BacktestConfiguration.calculateStartDate(preset),
            endDate = _customEndDate.value ?: BacktestConfiguration.calculateEndDate(),
            startTimestamp = startMs,
            endTimestamp = now,
            customCsvContent = _customCsvContent.value,
            initialCapital = risk.initialCapital,
            positionSizingMode = risk.positionSizingMode,
            riskPerTrade = risk.positionSizeValue,
            leverage = risk.leverage,
            allowShorting = risk.allowShorting,
            maxDrawdownCircuitBreakerPct = risk.maxDrawdownCircuitBreakerPct,
            stopLossType = risk.stopLossType,
            stopLossValue = risk.stopLossValue,
            takeProfitType = risk.takeProfitType,
            takeProfitValue = risk.takeProfitValue,
            commissionBps = risk.commissionBps,
            slippageBps = risk.slippageBps,
            executionModel = risk.executionModel,
            intrabarExecution = risk.intrabarExecution,
            strategyParams = BacktestConfiguration.extractStrategyParameters(strat)
        )
    }

    fun setAsset(asset: MarketAsset) {
        _selectedAsset.value = asset
        recordRecentSymbol(asset.symbol)
        _currentResult.value = null
        syncPendingConfiguration()
    }

    fun setRegime(regime: MarketRegime) {
        _selectedRegime.value = regime
        _currentResult.value = null
        syncPendingConfiguration()
    }

    fun setTimeframe(tf: Timeframe) {
        _selectedTimeframe.value = tf
        _currentResult.value = null
        syncPendingConfiguration()
    }

    fun setDateRange(startDate: String, endDate: String) {
        _customStartDate.value = startDate
        _customEndDate.value = endDate
        _currentResult.value = null
        syncPendingConfiguration()
    }

    fun setStrategy(strategy: StrategyDefinition) {
        _currentResult.value = null
        if (strategy.strategyType == StrategyType.A_PLUS_TRENDLINE && !strategy.isCustom) {
            val aplusState = _aplusApprovalState.value
            val alignedConfig = if (aplusState.isApproved) {
                aplusState.activeConfig
            } else {
                strategy.indicatorConfig.aPlusTrendlineConfig.copy(
                    profileName = APlusStrategySpecification.PROFILE_PENDING_APPROVAL,
                    isApproved = false
                )
            }
            _selectedStrategy.value = strategy.copy(
                indicatorConfig = strategy.indicatorConfig.copy(aPlusTrendlineConfig = alignedConfig)
            )
        } else {
            _selectedStrategy.value = strategy
        }
        syncPendingConfiguration()
    }

    fun duplicateStrategyAsExperiment(
        baseStrategy: StrategyDefinition = _selectedStrategy.value,
        customName: String? = null
    ): StrategyDefinition {
        val count = _customStrategies.value.size + 1
        val newId = "custom_${System.currentTimeMillis()}_${baseStrategy.id}"
        val name = customName?.takeIf { it.isNotBlank() } ?: "${baseStrategy.name} (Custom #$count)"

        val clonedConfig = when (baseStrategy.strategyType) {
            StrategyType.A_PLUS_TRENDLINE -> {
                baseStrategy.indicatorConfig.copy(
                    aPlusTrendlineConfig = baseStrategy.indicatorConfig.aPlusTrendlineConfig.copy(
                        profileName = "EXP_$name",
                        isExperimental = true,
                        isApproved = true
                    )
                )
            }
            else -> baseStrategy.indicatorConfig
        }

        val newStrategy = baseStrategy.copy(
            id = newId,
            name = name,
            description = "Experimental copy derived from ${baseStrategy.name}",
            isCustom = true,
            indicatorConfig = clonedConfig
        )

        _customStrategies.value = _customStrategies.value + newStrategy
        setStrategy(newStrategy)
        return newStrategy
    }

    fun updateStrategyParameter(paramKey: String, value: Any) {
        val currentStrat = _selectedStrategy.value
        val targetStrat = if (!currentStrat.isCustom && (currentStrat.id == STRATEGY_ID_A_PLUS_V1_0 || currentStrat.id == STRATEGY_ID_TRENDLINE_BREAK_HIGH_WIN_RATE)) {
            duplicateStrategyAsExperiment(currentStrat, "${currentStrat.name} (Custom)")
        } else {
            currentStrat
        }

        val ic = targetStrat.indicatorConfig
        val updatedIc = when (targetStrat.strategyType) {
            StrategyType.MA_CROSSOVER -> {
                when (paramKey) {
                    "fastPeriod" -> ic.copy(maParams = ic.maParams.copy(fastPeriod = (value as Number).toInt()))
                    "slowPeriod" -> ic.copy(maParams = ic.maParams.copy(slowPeriod = (value as Number).toInt()))
                    "useEma" -> ic.copy(maParams = ic.maParams.copy(useEma = value as Boolean))
                    else -> ic
                }
            }
            StrategyType.RSI_MEAN_REVERSION -> {
                when (paramKey) {
                    "period" -> ic.copy(rsiParams = ic.rsiParams.copy(period = (value as Number).toInt()))
                    "oversoldThreshold" -> ic.copy(rsiParams = ic.rsiParams.copy(oversoldThreshold = (value as Number).toDouble()))
                    "overboughtThreshold" -> ic.copy(rsiParams = ic.rsiParams.copy(overboughtThreshold = (value as Number).toDouble()))
                    else -> ic
                }
            }
            StrategyType.MACD_MOMENTUM -> {
                when (paramKey) {
                    "fastPeriod" -> ic.copy(macdParams = ic.macdParams.copy(fastPeriod = (value as Number).toInt()))
                    "slowPeriod" -> ic.copy(macdParams = ic.macdParams.copy(slowPeriod = (value as Number).toInt()))
                    "signalPeriod" -> ic.copy(macdParams = ic.macdParams.copy(signalPeriod = (value as Number).toInt()))
                    else -> ic
                }
            }
            StrategyType.BOLLINGER_BREAKOUT, StrategyType.BOLLINGER_REVERSION -> {
                when (paramKey) {
                    "period" -> ic.copy(bollingerParams = ic.bollingerParams.copy(period = (value as Number).toInt()))
                    "stdDevMultiplier" -> ic.copy(bollingerParams = ic.bollingerParams.copy(stdDevMultiplier = (value as Number).toDouble()))
                    else -> ic
                }
            }
            StrategyType.SUPERTREND_RUN -> {
                when (paramKey) {
                    "atrPeriod" -> ic.copy(supertrendParams = ic.supertrendParams.copy(atrPeriod = (value as Number).toInt()))
                    "multiplier" -> ic.copy(supertrendParams = ic.supertrendParams.copy(multiplier = (value as Number).toDouble()))
                    else -> ic
                }
            }
            StrategyType.TURTLE_BREAKOUT -> {
                when (paramKey) {
                    "period" -> ic.copy(donchianParams = ic.donchianParams.copy(period = (value as Number).toInt()))
                    else -> ic
                }
            }
            StrategyType.OPENING_RANGE_BREAKOUT -> {
                when (paramKey) {
                    "openingRangeMinutes" -> ic.copy(orbParams = ic.orbParams.copy(openingRangeMinutes = (value as Number).toInt()))
                    "volumeMultiplier" -> ic.copy(orbParams = ic.orbParams.copy(volumeMultiplier = (value as Number).toDouble()))
                    "breakoutBufferPct" -> ic.copy(orbParams = ic.orbParams.copy(breakoutBufferPct = (value as Number).toDouble()))
                    "useEmaTrendFilter" -> ic.copy(orbParams = ic.orbParams.copy(useEmaTrendFilter = value as Boolean))
                    "emaTrendPeriod" -> ic.copy(orbParams = ic.orbParams.copy(emaTrendPeriod = (value as Number).toInt()))
                    "useRsiFilter" -> ic.copy(orbParams = ic.orbParams.copy(useRsiFilter = value as Boolean))
                    "rsiThreshold" -> ic.copy(orbParams = ic.orbParams.copy(rsiThreshold = (value as Number).toDouble()))
                    else -> ic
                }
            }
            StrategyType.TRENDLINE_BREAK, StrategyType.TRENDLINE_BOUNCE -> {
                when (paramKey) {
                    "pivotLookback" -> ic.copy(trendlineParams = ic.trendlineParams.copy(pivotLookback = (value as Number).toInt()))
                    "pivotStrength" -> ic.copy(trendlineParams = ic.trendlineParams.copy(pivotStrength = (value as Number).toInt()))
                    "minTouches" -> ic.copy(trendlineParams = ic.trendlineParams.copy(minTouches = (value as Number).toInt()))
                    "maxLineAge" -> ic.copy(trendlineParams = ic.trendlineParams.copy(maxLineAge = (value as Number).toInt()))
                    "confirmationThresholdPct" -> ic.copy(trendlineParams = ic.trendlineParams.copy(confirmationThresholdPct = (value as Number).toDouble()))
                    "retestRequired" -> ic.copy(trendlineParams = ic.trendlineParams.copy(retestRequired = value as Boolean))
                    "useRsiFilter" -> ic.copy(trendlineParams = ic.trendlineParams.copy(useRsiFilter = value as Boolean))
                    "useMaTrendFilter" -> ic.copy(trendlineParams = ic.trendlineParams.copy(useMaTrendFilter = value as Boolean))
                    else -> ic
                }
            }
            StrategyType.SMC_CONCEPTS, StrategyType.ICT_CONCEPTS, StrategyType.SMC_ICT_CONCEPTS -> {
                val smc = ic.smcConfig
                val updatedSmc = when (paramKey) {
                    "useBos" -> smc.copy(useBos = value as Boolean)
                    "useChoch" -> smc.copy(useChoch = value as Boolean)
                    "useLiquiditySweep" -> smc.copy(useLiquiditySweep = value as Boolean)
                    "useFvg" -> smc.copy(useFvg = value as Boolean)
                    "useOrderBlock" -> smc.copy(useOrderBlock = value as Boolean)
                    "useBreakerBlock" -> smc.copy(useBreakerBlock = value as Boolean)
                    "usePremiumDiscount" -> smc.copy(usePremiumDiscount = value as Boolean)
                    "useDisplacement" -> smc.copy(useDisplacement = value as Boolean)
                    "obLookback" -> smc.copy(obLookback = (value as Number).toInt())
                    "breakerLookback" -> smc.copy(breakerLookback = (value as Number).toInt())
                    "sweepLookback" -> smc.copy(sweepLookback = (value as Number).toInt())
                    "sweepWickMinPct" -> smc.copy(sweepWickMinPct = (value as Number).toDouble())
                    "fvgMinGapAtrMultiple" -> smc.copy(fvgMinGapAtrMultiple = (value as Number).toDouble())
                    "displacementAtrMultiplier" -> smc.copy(displacementAtrMultiplier = (value as Number).toDouble())
                    "discountThresholdPct" -> smc.copy(discountThresholdPct = (value as Number).toDouble())
                    "requireConfluence" -> smc.copy(requireConfluence = value as Boolean)
                    "minConfluences" -> smc.copy(minConfluences = (value as Number).toInt())
                    else -> smc
                }
                ic.copy(smcConfig = updatedSmc)
            }
            StrategyType.A_PLUS_TRENDLINE -> {
                val aplus = ic.aPlusTrendlineConfig
                val updatedAplus = when (paramKey) {
                    "swingLookback" -> aplus.copy(swingLookback = (value as Number).toInt())
                    "confirmationBars" -> aplus.copy(confirmationBars = (value as Number).toInt())
                    "minSwingSeparationBars" -> aplus.copy(minSwingSeparationBars = (value as Number).toInt())
                    "minTouches" -> aplus.copy(minTouches = (value as Number).toInt())
                    "breakConfirmationATR" -> aplus.copy(breakConfirmationATR = (value as Number).toDouble())
                    "retestToleranceATR" -> aplus.copy(retestToleranceATR = (value as Number).toDouble())
                    "retestMaxBars" -> aplus.copy(retestMaxBars = (value as Number).toInt())
                    "stopBufferATR" -> aplus.copy(stopBufferATR = (value as Number).toDouble())
                    "breakEvenTriggerR" -> aplus.copy(breakEvenTriggerR = (value as Number).toDouble())
                    "requireStructuralBreakEven" -> aplus.copy(requireStructuralBreakEven = value as Boolean)
                    "enableStructuralTrailing" -> aplus.copy(enableStructuralTrailing = value as Boolean)
                    "exitOnCounterStructureBreak" -> aplus.copy(exitOnCounterStructureBreak = value as Boolean)
                    else -> aplus
                }
                ic.copy(aPlusTrendlineConfig = updatedAplus)
            }
            StrategyType.MULTI_CONFLUENCE -> {
                when (paramKey) {
                    "maFastPeriod" -> ic.copy(maParams = ic.maParams.copy(fastPeriod = (value as Number).toInt()))
                    "maSlowPeriod" -> ic.copy(maParams = ic.maParams.copy(slowPeriod = (value as Number).toInt()))
                    "rsiPeriod" -> ic.copy(rsiParams = ic.rsiParams.copy(period = (value as Number).toInt()))
                    "rsiOversold" -> ic.copy(rsiParams = ic.rsiParams.copy(oversoldThreshold = (value as Number).toDouble()))
                    "rsiOverbought" -> ic.copy(rsiParams = ic.rsiParams.copy(overboughtThreshold = (value as Number).toDouble()))
                    "macdFast" -> ic.copy(macdParams = ic.macdParams.copy(fastPeriod = (value as Number).toInt()))
                    "macdSlow" -> ic.copy(macdParams = ic.macdParams.copy(slowPeriod = (value as Number).toInt()))
                    "macdSignal" -> ic.copy(macdParams = ic.macdParams.copy(signalPeriod = (value as Number).toInt()))
                    else -> ic
                }
            }
        }

        val updatedStrat = targetStrat.copy(indicatorConfig = updatedIc)
        setStrategy(updatedStrat)
    }

    fun selectHighWinRateStrategy() {
        val hwr = StrategyDefinition.PRESETS.find { it.id == STRATEGY_ID_TRENDLINE_BREAK_HIGH_WIN_RATE }
            ?: STRATEGY_TRENDLINE_BREAK_HIGH_WIN_RATE
        setStrategy(hwr)
    }

    fun selectAPlusStrategy() {
        val aplus = StrategyDefinition.PRESETS.find { it.id == STRATEGY_ID_A_PLUS_V1_0 }
            ?: STRATEGY_A_PLUS_V1_0
        setStrategy(aplus)
    }

    // A+ Strategy Approval Management
    fun updateAplusParameter(paramId: String, newValue: Double) {
        val current = _aplusApprovalState.value
        val updatedParams = current.parameters.map { param ->
            if (param.id == paramId) {
                val clamped = newValue.coerceIn(param.minVal, param.maxVal)
                val valueStr = when (param.unit) {
                    "bars" -> "${clamped.toInt()} bars"
                    "%" -> String.format(Locale.US, "%.2f%%", clamped)
                    "R" -> String.format(Locale.US, "%.2f R", clamped)
                    else -> String.format(Locale.US, "%.2f", clamped)
                }
                param.copy(rawValue = clamped, currentValueStr = valueStr)
            } else param
        }
        val newConfig = buildAplusConfigFromParameters(updatedParams, current.activeConfig)
        _aplusApprovalState.value = current.copy(
            parameters = updatedParams,
            activeConfig = newConfig
        )
    }

    fun toggleParameterApproval(paramId: String) {
        val current = _aplusApprovalState.value
        val updatedParams = current.parameters.map { param ->
            if (param.id == paramId) {
                param.copy(isApproved = !param.isApproved)
            } else param
        }
        _aplusApprovalState.value = current.copy(parameters = updatedParams)
    }

    fun approveAllParameters() {
        val current = _aplusApprovalState.value
        val updatedParams = current.parameters.map { it.copy(isApproved = true) }
        _aplusApprovalState.value = current.copy(parameters = updatedParams)
    }

    fun resetParametersToEngineDefault() {
        val current = _aplusApprovalState.value
        val defaultParams = APlusStrategySpecification.DEFAULT_PARAMETERS
        val newConfig = buildAplusConfigFromParameters(defaultParams, APlusTrendlineConfig.A_PLUS_DEFAULT_CONFIG)
        _aplusApprovalState.value = current.copy(
            parameters = defaultParams,
            activeConfig = newConfig
        )
    }

    fun approveStrategySpec(approvedBy: String = "Human Trader") {
        val current = _aplusApprovalState.value
        if (!current.allParametersApproved) return

        val now = System.currentTimeMillis()
        val iso = APlusStrategySpecification.formatIsoTimestamp(now)
        val finalConfig = buildAplusConfigFromParameters(current.parameters, current.activeConfig).copy(
            profileName = APlusStrategySpecification.PROFILE_APPROVED_V1_0,
            isApproved = true,
            isExperimental = false
        )
        val hash = APlusStrategySpecification.computeSpecificationHash(
            config = finalConfig,
            profileName = APlusStrategySpecification.PROFILE_APPROVED_V1_0,
            version = APlusStrategySpecification.VERSION_APPROVED_V1_0
        )
        val approvedConfigWithHash = finalConfig.copy(specificationHash = hash)

        val v1Profile = ApprovedStrategyProfile(
            profileId = "aplus_v1_0_profile",
            profileName = APlusStrategySpecification.PROFILE_APPROVED_V1_0,
            strategyVersion = APlusStrategySpecification.VERSION_APPROVED_V1_0,
            isImmutable = true,
            isExperimental = false,
            approvalTimestamp = now,
            approvalTimestampIso = iso,
            approvedBy = approvedBy,
            ruleSpecificationHash = hash,
            config = approvedConfigWithHash,
            parameterSnapshot = approvedConfigWithHash.toParameterMap()
        )

        val updatedProfiles = listOf(v1Profile) + current.approvedProfiles.filter { it.profileId != v1Profile.profileId }

        _aplusApprovalState.value = current.copy(
            activeProfileName = APlusStrategySpecification.PROFILE_APPROVED_V1_0,
            strategyVersion = APlusStrategySpecification.VERSION_APPROVED_V1_0,
            isApproved = true,
            approvalTimestamp = now,
            approvalTimestampIso = iso,
            approvedBy = approvedBy,
            ruleSpecificationHash = hash,
            activeConfig = approvedConfigWithHash,
            approvedProfiles = updatedProfiles
        )

        if (_selectedStrategy.value.strategyType == StrategyType.A_PLUS_TRENDLINE) {
            _selectedStrategy.value = _selectedStrategy.value.copy(
                indicatorConfig = _selectedStrategy.value.indicatorConfig.copy(
                    aPlusTrendlineConfig = approvedConfigWithHash
                )
            )
        }
        syncPendingConfiguration()
    }

    fun createExperimentalCopy(experimentName: String) {
        val current = _aplusApprovalState.value
        if (!current.isApproved) return

        val expId = "exp_${System.currentTimeMillis()}"
        val expCount = current.approvedProfiles.count { it.isExperimental } + 1
        val expNameClean = if (experimentName.isBlank()) "A_PLUS_EXPERIMENT_${String.format(Locale.US, "%02d", expCount)}" else experimentName.trim()
        val now = System.currentTimeMillis()
        val iso = APlusStrategySpecification.formatIsoTimestamp(now)

        val expConfig = current.activeConfig.copy(
            profileName = expNameClean,
            isApproved = true,
            isExperimental = true
        )
        val expHash = APlusStrategySpecification.computeSpecificationHash(
            config = expConfig,
            profileName = expNameClean,
            version = "1.0.0-experimental"
        )
        val expConfigWithHash = expConfig.copy(specificationHash = expHash)

        val expProfile = ApprovedStrategyProfile(
            profileId = expId,
            profileName = expNameClean,
            strategyVersion = "1.0.0-experimental",
            isImmutable = false,
            isExperimental = true,
            parentProfileId = "aplus_v1_0_profile",
            approvalTimestamp = now,
            approvalTimestampIso = iso,
            approvedBy = "Human Trader (Experimental)",
            ruleSpecificationHash = expHash,
            config = expConfigWithHash,
            parameterSnapshot = expConfigWithHash.toParameterMap()
        )

        _aplusApprovalState.value = current.copy(
            approvedProfiles = current.approvedProfiles + expProfile
        )
    }

    fun selectAplusProfile(profileId: String) {
        val current = _aplusApprovalState.value
        val profile = current.approvedProfiles.firstOrNull { it.profileId == profileId } ?: return
        _aplusApprovalState.value = current.copy(
            activeProfileName = profile.profileName,
            strategyVersion = profile.strategyVersion,
            isApproved = true,
            ruleSpecificationHash = profile.ruleSpecificationHash,
            activeConfig = profile.config
        )

        if (_selectedStrategy.value.strategyType == StrategyType.A_PLUS_TRENDLINE) {
            _selectedStrategy.value = _selectedStrategy.value.copy(
                indicatorConfig = _selectedStrategy.value.indicatorConfig.copy(
                    aPlusTrendlineConfig = profile.config
                )
            )
        }
        syncPendingConfiguration()
    }

    private fun buildAplusConfigFromParameters(params: List<APlusParameterItem>, base: APlusTrendlineConfig): APlusTrendlineConfig {
        var swingLookback = base.swingLookback
        var confirmationBars = base.confirmationBars
        var minSwingSeparationBars = base.minSwingSeparationBars
        var breakConfirmationATR = base.breakConfirmationATR
        var retestToleranceATR = base.retestToleranceATR
        var retestMaxBars = base.retestMaxBars
        var stopBufferATR = base.stopBufferATR
        var breakEvenTriggerR = base.breakEvenTriggerR
        var requireStructuralBreakEven = base.requireStructuralBreakEven
        var enableStructuralTrailing = base.enableStructuralTrailing

        for (p in params) {
            when (p.id) {
                "swingLookback" -> swingLookback = p.rawValue.toInt()
                "confirmationBars" -> confirmationBars = p.rawValue.toInt()
                "minSwingSeparationBars" -> minSwingSeparationBars = p.rawValue.toInt()
                "breakConfirmationATR" -> breakConfirmationATR = p.rawValue
                "retestToleranceATR" -> retestToleranceATR = p.rawValue
                "retestMaxBars" -> retestMaxBars = p.rawValue.toInt()
                "stopBufferATR" -> stopBufferATR = p.rawValue
                "breakEvenTriggerR" -> breakEvenTriggerR = p.rawValue
                "requireStructuralBreakEven" -> requireStructuralBreakEven = p.rawValue > 0.5
                "enableStructuralTrailing" -> enableStructuralTrailing = p.rawValue > 0.5
            }
        }

        return base.copy(
            swingLookback = swingLookback,
            confirmationBars = confirmationBars,
            minSwingSeparationBars = minSwingSeparationBars,
            breakConfirmationATR = breakConfirmationATR,
            retestToleranceATR = retestToleranceATR,
            retestMaxBars = retestMaxBars,
            stopBufferATR = stopBufferATR,
            breakEvenTriggerR = breakEvenTriggerR,
            requireStructuralBreakEven = requireStructuralBreakEven,
            enableStructuralTrailing = enableStructuralTrailing
        )
    }

    fun updateRiskParameters(risk: RiskParameters) {
        _riskParameters.value = risk
        _currentResult.value = null
        syncPendingConfiguration()
    }

    fun updateInitialCapital(capital: Double) {
        _riskParameters.value = _riskParameters.value.copy(initialCapital = capital)
        _currentResult.value = null
        syncPendingConfiguration()
    }

    fun updatePositionSizing(mode: PositionSizingMode, value: Double) {
        _riskParameters.value = _riskParameters.value.copy(
            positionSizingMode = mode,
            positionSizeValue = value
        )
        _currentResult.value = null
        syncPendingConfiguration()
    }

    fun updateStopLoss(type: StopLossType, value: Double) {
        _riskParameters.value = _riskParameters.value.copy(stopLossType = type, stopLossValue = value)
        _currentResult.value = null
        syncPendingConfiguration()
    }

    fun updateTakeProfit(type: TakeProfitType, value: Double) {
        _riskParameters.value = _riskParameters.value.copy(takeProfitType = type, takeProfitValue = value)
        _currentResult.value = null
        syncPendingConfiguration()
    }

    fun updateCommissionBps(bps: Double) {
        _riskParameters.value = _riskParameters.value.copy(commissionBps = bps)
        _currentResult.value = null
        syncPendingConfiguration()
    }

    fun updateSlippageBps(bps: Double) {
        _riskParameters.value = _riskParameters.value.copy(slippageBps = bps)
        _currentResult.value = null
        syncPendingConfiguration()
    }

    fun updateExecutionModel(model: ExecutionModel) {
        _riskParameters.value = _riskParameters.value.copy(executionModel = model)
        _currentResult.value = null
        syncPendingConfiguration()
    }

    fun updateIntrabarExecution(assumption: IntrabarExecutionAssumption) {
        _riskParameters.value = _riskParameters.value.copy(intrabarExecution = assumption)
        _currentResult.value = null
        syncPendingConfiguration()
    }

    fun updateLeverage(leverage: Double) {
        _riskParameters.value = _riskParameters.value.copy(leverage = leverage)
        _currentResult.value = null
        syncPendingConfiguration()
    }

    fun updateAllowShorting(allow: Boolean) {
        _riskParameters.value = _riskParameters.value.copy(allowShorting = allow)
        _currentResult.value = null
        syncPendingConfiguration()
    }

    fun updateSmcConfig(smcConfig: SmcConfig) {
        val current = _selectedStrategy.value
        val updatedType = when (current.strategyType) {
            StrategyType.SMC_CONCEPTS -> StrategyType.SMC_CONCEPTS
            StrategyType.ICT_CONCEPTS -> StrategyType.ICT_CONCEPTS
            StrategyType.SMC_ICT_CONCEPTS -> StrategyType.SMC_ICT_CONCEPTS
            else -> StrategyType.SMC_CONCEPTS
        }
        val updated = current.copy(
            strategyType = updatedType,
            indicatorConfig = current.indicatorConfig.copy(smcConfig = smcConfig)
        )
        _selectedStrategy.value = updated
        _currentResult.value = null
        syncPendingConfiguration()
    }

    fun setProvider(provider: ProviderSelection) {
        _selectedProvider.value = provider
        _currentResult.value = null
        syncPendingConfiguration()
    }

    fun setDatePreset(preset: DateRangePreset) {
        _selectedDatePreset.value = preset
        _customStartDate.value = null
        _customEndDate.value = null
        _currentResult.value = null
        syncPendingConfiguration()
    }

    fun setApiKey(key: String) {
        _apiKey.value = if (key.isBlank()) null else key
    }

    fun toggleFavoriteSymbol(symbol: String) {
        val current = _favoriteSymbols.value.toMutableSet()
        if (current.contains(symbol)) {
            current.remove(symbol)
        } else {
            current.add(symbol)
        }
        _favoriteSymbols.value = current
    }

    private fun recordRecentSymbol(symbol: String) {
        val current = _recentSymbols.value.toMutableList()
        current.remove(symbol)
        current.add(0, symbol)
        _recentSymbols.value = current.take(8)
    }

    fun selectTradeForDetail(trade: Trade?) {
        _selectedTradeForDetail.value = trade
    }

    // Cancellation of running backtest
    fun cancelBacktest() {
        backtestJob?.cancel()
        _isBacktesting.value = false
        _backtestProgress.value = BacktestProgress(isRunning = false)
    }

    fun runBacktest(explicitConfig: BacktestConfiguration? = null) {
        backtestJob?.cancel()
        backtestJob = viewModelScope.launch(Dispatchers.Default) {
            _isBacktesting.value = true
            _dataFetchError.value = null

            val cfg = explicitConfig ?: buildCurrentBacktestConfiguration()
            val asset = cfg.asset
            val regime = cfg.marketRegime
            val tf = cfg.timeframe
            val strat = cfg.strategy
            val risk = cfg.toRiskParameters()
            val startMs = cfg.startTimestamp
            val endMs = cfg.endTimestamp
            val prov = cfg.provider

            // Performance Gate Enforcement for A+ Trendline Strategy
            if (strat.strategyType == StrategyType.A_PLUS_TRENDLINE && !strat.isCustom) {
                val aplusApproval = _aplusApprovalState.value
                if (!aplusApproval.isApproved && !strat.indicatorConfig.aPlusTrendlineConfig.isApproved) {
                    _dataFetchError.value = "PERFORMANCE BACKTEST BLOCKED: A+ Trendline Strategy is in 'A_PLUS_PENDING_APPROVAL' state. Specification must be approved in the Strategy Specification screen before running performance backtests."
                    _currentResult.value = null
                    _healthScorecard.value = null
                    _isBacktesting.value = false
                    _backtestProgress.value = BacktestProgress(isRunning = false)
                    return@launch
                }
            }

            _backtestProgress.value = BacktestProgress(
                isRunning = true,
                progressPct = 0.20f,
                stageMessage = "Preparing historical dataset...",
                processedCandles = 0,
                totalCandles = 0,
                currentDateStr = "Fetching ${asset.symbol} candles (${tf.label})...",
                tradesFound = 0,
                currentEquity = risk.initialCapital,
                strategyName = strat.name,
                symbol = asset.symbol,
                timeframe = tf.label
            )

            val fetchResult = marketDataRepo.getHistoricalCandles(
                asset = asset,
                timeframe = tf,
                startTimeMs = startMs,
                endTimeMs = endMs,
                apiKey = _apiKey.value,
                isDemoMode = false,
                provider = if (prov == ProviderSelection.AUTO) null else prov.id,
                customCsvContent = _customCsvContent.value
            )

            if (!isActive) return@launch

            if (fetchResult.isSuccess) {
                val fetchData = fetchResult.getOrThrow()
                val validatedCandles = fetchData.candles

                // Gate: Synthetic data is NOT permitted for official A+ Strategy performance verification
                if (strat.strategyType == StrategyType.A_PLUS_TRENDLINE && !fetchData.isRealHistorical && !strat.isCustom) {
                    _dataFetchError.value = "PERFORMANCE BACKTEST BLOCKED: Synthetic market data is not permitted for official A+ Strategy performance verification. Validated Real Market Data is required."
                    _currentResult.value = null
                    _healthScorecard.value = null
                    _isBacktesting.value = false
                    _backtestProgress.value = BacktestProgress(isRunning = false)
                    return@launch
                }
                _dataFetchError.value = null

                val dsInfo = cfg.toDataSourceInfo(
                    candleCount = validatedCandles.size,
                    isRealHistorical = fetchData.isRealHistorical,
                    validationStatus = if (fetchData.validationReport.isValid) "VERIFIED_VALID" else "WARNING"
                ).copy(
                    dataHash = fetchData.dataHash,
                    startDate = validatedCandles.firstOrNull()?.formattedDate(tf.minutes) ?: cfg.startDate,
                    endDate = validatedCandles.lastOrNull()?.formattedDate(tf.minutes) ?: cfg.endDate,
                    startTimestamp = validatedCandles.firstOrNull()?.timestamp ?: startMs,
                    endTimestamp = validatedCandles.lastOrNull()?.timestamp ?: endMs
                )
                _dataSourceInfo.value = dsInfo

                _backtestProgress.value = BacktestProgress(
                    isRunning = true,
                    progressPct = 0.50f,
                    stageMessage = "Simulating order execution...",
                    processedCandles = validatedCandles.size,
                    totalCandles = validatedCandles.size,
                    currentDateStr = dsInfo.endDate,
                    tradesFound = 0,
                    currentEquity = risk.initialCapital,
                    strategyName = strat.name,
                    symbol = asset.symbol,
                    timeframe = tf.label
                )

                val rawResult = BacktestEngine.runBacktest(
                    candles = validatedCandles,
                    asset = asset,
                    regime = regime,
                    timeframe = tf,
                    strategy = strat,
                    risk = risk,
                    dataSourceInfo = dsInfo
                )

                if (!isActive) return@launch

                val result = rawResult.copy(configuration = cfg)

                _backtestProgress.value = BacktestProgress(
                    isRunning = true,
                    progressPct = 0.85f,
                    stageMessage = "Calculating performance metrics...",
                    processedCandles = validatedCandles.size,
                    totalCandles = validatedCandles.size,
                    currentDateStr = dsInfo.endDate,
                    tradesFound = result.trades.size,
                    currentEquity = result.metrics.finalEquity,
                    strategyName = strat.name,
                    symbol = asset.symbol,
                    timeframe = tf.label
                )

                _currentResult.value = result
                _healthScorecard.value = StrategyAdvisor.generateHealthReport(result)

                if (strat.id == STRATEGY_ID_TRENDLINE_BREAK_HIGH_WIN_RATE || (strat.strategyType == StrategyType.TRENDLINE_BREAK && !strat.isCustom)) {
                    _latestHwrResult.value = result
                } else if (strat.id == STRATEGY_ID_A_PLUS_V1_0 || (strat.strategyType == StrategyType.A_PLUS_TRENDLINE && !strat.isCustom)) {
                    _latestAplusResult.value = result
                }

                _backtestProgress.value = BacktestProgress(
                    isRunning = false,
                    progressPct = 1.0f,
                    stageMessage = "Completed (${result.trades.size} trades)",
                    processedCandles = validatedCandles.size,
                    totalCandles = validatedCandles.size,
                    currentDateStr = dsInfo.endDate,
                    tradesFound = result.trades.size,
                    currentEquity = result.metrics.finalEquity,
                    strategyName = strat.name,
                    symbol = asset.symbol,
                    timeframe = tf.label
                )
            } else {
                val error = fetchResult.exceptionOrNull()
                val errMessage = error?.message ?: "Failed to fetch real market data."
                _dataFetchError.value = errMessage
                _currentResult.value = null
                _healthScorecard.value = null
                _backtestProgress.value = BacktestProgress(isRunning = false)
            }

            _isBacktesting.value = false
        }
    }

    // Strategy Lab (Compare multiple strategies on identical dataset)
    fun runStrategyLabComparison(
        strategiesToCompare: List<StrategyDefinition>? = null,
        explicitAsset: MarketAsset? = null,
        explicitTf: Timeframe? = null
    ) {
        val strats = strategiesToCompare ?: listOf(
            StrategyDefinition.PRESETS.firstOrNull { it.id == STRATEGY_ID_TRENDLINE_BREAK_HIGH_WIN_RATE } ?: StrategyDefinition.PRESETS[0],
            StrategyDefinition.PRESETS.firstOrNull { it.id == STRATEGY_ID_A_PLUS_V1_0 } ?: StrategyDefinition.PRESETS[1],
            StrategyDefinition.PRESETS.firstOrNull { it.strategyType == StrategyType.SMC_CONCEPTS } ?: StrategyDefinition.PRESETS[2],
            StrategyDefinition.PRESETS.firstOrNull { it.strategyType == StrategyType.ICT_CONCEPTS } ?: StrategyDefinition.PRESETS[3]
        )

        viewModelScope.launch(Dispatchers.Default) {
            _isStrategyLabRunning.value = true
            _strategyLabItems.value = strats.map { StrategyLabItem(strategy = it, isEvaluating = true) }

            val asset = explicitAsset ?: _selectedAsset.value
            val regime = _selectedRegime.value
            val tf = explicitTf ?: _selectedTimeframe.value
            val risk = _riskParameters.value
            val preset = _selectedDatePreset.value
            val prov = _selectedProvider.value

            val now = System.currentTimeMillis()
            val startMs = now - (preset.days.toLong() * 24L * 60L * 60L * 1000L)

            val fetchResult = marketDataRepo.getHistoricalCandles(
                asset = asset,
                timeframe = tf,
                startTimeMs = startMs,
                endTimeMs = now,
                apiKey = _apiKey.value,
                isDemoMode = false,
                provider = if (prov == ProviderSelection.AUTO) null else prov.id,
                customCsvContent = _customCsvContent.value
            )

            if (fetchResult.isSuccess) {
                val fetchObj = fetchResult.getOrThrow()
                val validatedCandles = fetchObj.candles
                val dsInfo = _dataSourceInfo.value ?: DataSourceInfo(
                    provider = fetchObj.providerName,
                    symbol = asset.symbol,
                    market = asset.category.label,
                    timeframe = tf.label,
                    startDate = validatedCandles.firstOrNull()?.formattedDate(tf.minutes) ?: "",
                    endDate = validatedCandles.lastOrNull()?.formattedDate(tf.minutes) ?: "",
                    startTimestamp = validatedCandles.firstOrNull()?.timestamp ?: 0L,
                    endTimestamp = validatedCandles.lastOrNull()?.timestamp ?: 0L,
                    candleCount = validatedCandles.size,
                    isRealHistorical = fetchObj.isRealHistorical,
                    dataHash = fetchObj.dataHash,
                    datasetId = "${asset.symbol}_${tf.name}_${validatedCandles.firstOrNull()?.timestamp}_${validatedCandles.lastOrNull()?.timestamp}"
                )

                val results = mutableListOf<StrategyLabItem>()
                for (strat in strats) {
                    try {
                        val res = BacktestEngine.runBacktest(
                            candles = validatedCandles,
                            asset = asset,
                            regime = regime,
                            timeframe = tf,
                            strategy = strat,
                            risk = risk,
                            dataSourceInfo = dsInfo
                        )
                        results.add(StrategyLabItem(strategy = strat, result = res, isEvaluating = false))
                    } catch (e: Exception) {
                        results.add(StrategyLabItem(strategy = strat, error = e.message, isEvaluating = false))
                    }
                }
                _strategyLabItems.value = results
            } else {
                _strategyLabItems.value = strats.map {
                    StrategyLabItem(strategy = it, error = "Failed to fetch market data", isEvaluating = false)
                }
            }

            _isStrategyLabRunning.value = false
        }
    }

    // Multi-Strategy Comparison Dashboard Functions
    fun toggleComparisonStrategy(strategyId: String) {
        val current = _comparisonSelectedStrategies.value.toMutableSet()
        if (current.contains(strategyId)) {
            current.remove(strategyId)
        } else {
            current.add(strategyId)
        }
        _comparisonSelectedStrategies.value = current
    }

    fun selectAllComparisonStrategies() {
        _comparisonSelectedStrategies.value = StrategyDefinition.PRESETS.map { it.id }.toSet()
    }

    fun clearAllComparisonStrategies() {
        _comparisonSelectedStrategies.value = emptySet()
    }

    fun setComparisonSortMetric(metric: ComparisonSortMetric) {
        _comparisonSortMetric.value = metric
    }

    fun setRankWeights(weights: StrategyRankWeights) {
        _rankWeights.value = weights
    }

    fun selectComparisonDetailItem(item: StrategyComparisonItem?) {
        _selectedComparisonDetailItem.value = item
    }

    fun runMultiStrategyComparison() {
        val selectedIds = _comparisonSelectedStrategies.value
        val selectedStrats = StrategyDefinition.PRESETS.filter { selectedIds.contains(it.id) }

        if (selectedStrats.isEmpty()) {
            _multiStrategyComparisonResult.value = MultiStrategyComparisonResult(
                validation = ComparisonValidationResult(
                    isValid = false,
                    validationErrors = listOf("Please select at least one strategy to compare.")
                ),
                items = emptyList(),
                monthlyMatrix = emptyList()
            )
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            _isComparingMultiStrategies.value = true

            val asset = _selectedAsset.value
            val regime = _selectedRegime.value
            val tf = _selectedTimeframe.value
            val risk = _riskParameters.value
            val preset = _selectedDatePreset.value
            val prov = _selectedProvider.value

            val now = System.currentTimeMillis()
            val startMs = now - (preset.days.toLong() * 24L * 60L * 60L * 1000L)

            val fetchResult = marketDataRepo.getHistoricalCandles(
                asset = asset,
                timeframe = tf,
                startTimeMs = startMs,
                endTimeMs = now,
                apiKey = _apiKey.value,
                isDemoMode = false,
                provider = if (prov == ProviderSelection.AUTO) null else prov.id,
                customCsvContent = _customCsvContent.value
            )

            if (fetchResult.isSuccess) {
                val fetchObj = fetchResult.getOrThrow()
                val validatedCandles = fetchObj.candles
                val dsInfo = _dataSourceInfo.value ?: DataSourceInfo(
                    provider = fetchObj.providerName,
                    symbol = asset.symbol,
                    market = asset.category.label,
                    timeframe = tf.label,
                    startDate = validatedCandles.firstOrNull()?.formattedDate(tf.minutes) ?: "",
                    endDate = validatedCandles.lastOrNull()?.formattedDate(tf.minutes) ?: "",
                    startTimestamp = validatedCandles.firstOrNull()?.timestamp ?: 0L,
                    endTimestamp = validatedCandles.lastOrNull()?.timestamp ?: 0L,
                    candleCount = validatedCandles.size,
                    isRealHistorical = fetchObj.isRealHistorical,
                    dataHash = fetchObj.dataHash,
                    datasetId = "${asset.symbol}_${tf.name}_${validatedCandles.firstOrNull()?.timestamp}_${validatedCandles.lastOrNull()?.timestamp}"
                )

                val compResult = StrategyComparisonEngine.runComparison(
                    strategies = selectedStrats,
                    candles = validatedCandles,
                    asset = asset,
                    regime = regime,
                    timeframe = tf,
                    risk = risk,
                    dataSourceInfo = dsInfo
                )
                _multiStrategyComparisonResult.value = compResult
            } else {
                _multiStrategyComparisonResult.value = MultiStrategyComparisonResult(
                    validation = ComparisonValidationResult(
                        isValid = false,
                        validationErrors = listOf("Failed to fetch market data: ${fetchResult.exceptionOrNull()?.message ?: "Unknown error"}")
                    ),
                    items = emptyList(),
                    monthlyMatrix = emptyList()
                )
            }

            _isComparingMultiStrategies.value = false
        }
    }

    // Dual Trendline Strategy Comparison (High-Win-Rate vs A+ V1.0)
    fun showDualComparison() {
        _showDualComparisonDialog.value = true
        if (_dualStrategyComparisonData.value == null) {
            runDualStrategyComparison()
        }
    }

    fun dismissDualComparison() {
        _showDualComparisonDialog.value = false
    }

    fun runDualStrategyComparison() {
        _showDualComparisonDialog.value = true
        viewModelScope.launch(Dispatchers.Default) {
            _isDualComparing.value = true
            val asset = _selectedAsset.value
            val regime = _selectedRegime.value
            val tf = _selectedTimeframe.value
            val risk = _riskParameters.value
            val preset = _selectedDatePreset.value
            val prov = _selectedProvider.value

            val now = System.currentTimeMillis()
            val startMs = now - (preset.days.toLong() * 24L * 60L * 60L * 1000L)

            val fetchResult = marketDataRepo.getHistoricalCandles(
                asset = asset,
                timeframe = tf,
                startTimeMs = startMs,
                endTimeMs = now,
                apiKey = _apiKey.value,
                isDemoMode = false,
                provider = if (prov == ProviderSelection.AUTO) null else prov.id,
                customCsvContent = _customCsvContent.value
            )

            if (fetchResult.isSuccess) {
                val fetchObj = fetchResult.getOrThrow()
                val validatedCandles = fetchObj.candles
                val dsInfo = _dataSourceInfo.value ?: DataSourceInfo(
                    provider = fetchObj.providerName,
                    symbol = asset.symbol,
                    market = asset.category.label,
                    timeframe = tf.label,
                    startDate = validatedCandles.firstOrNull()?.formattedDate(tf.minutes) ?: "",
                    endDate = validatedCandles.lastOrNull()?.formattedDate(tf.minutes) ?: "",
                    startTimestamp = validatedCandles.firstOrNull()?.timestamp ?: 0L,
                    endTimestamp = validatedCandles.lastOrNull()?.timestamp ?: 0L,
                    candleCount = validatedCandles.size,
                    isRealHistorical = fetchObj.isRealHistorical,
                    dataHash = fetchObj.dataHash,
                    datasetId = "${asset.symbol}_${tf.name}_${validatedCandles.firstOrNull()?.timestamp}_${validatedCandles.lastOrNull()?.timestamp}"
                )

                // High-Win-Rate Strategy execution (isolated instance & rules)
                val stratHighWinRate = STRATEGY_TRENDLINE_BREAK_HIGH_WIN_RATE
                // A+ V1.0 Strategy execution (frozen baseline rules)
                val stratAPlusV1 = STRATEGY_A_PLUS_V1_0

                val resHighWinRate = BacktestEngine.runBacktest(
                    candles = validatedCandles,
                    asset = asset,
                    regime = regime,
                    timeframe = tf,
                    strategy = stratHighWinRate,
                    risk = risk,
                    dataSourceInfo = dsInfo
                )

                val resAPlusV1 = BacktestEngine.runBacktest(
                    candles = validatedCandles,
                    asset = asset,
                    regime = regime,
                    timeframe = tf,
                    strategy = stratAPlusV1,
                    risk = risk,
                    dataSourceInfo = dsInfo
                )

                _latestHwrResult.value = resHighWinRate
                _latestAplusResult.value = resAPlusV1

                _dualStrategyComparisonData.value = DualStrategyComparisonBuilder.buildComparison(
                    highWinRateResult = resHighWinRate,
                    aPlusV1Result = resAPlusV1,
                    timeframe = tf,
                    asset = asset,
                    initialCapital = risk.initialCapital
                )
            }

            _isDualComparing.value = false
        }
    }

    // Multi-Timeframe Workspace
    fun toggleMtfMode() {
        _isMtfEnabled.value = !_isMtfEnabled.value
        if (_isMtfEnabled.value) {
            loadMtfConfirmationCandles()
        }
    }

    fun setMtfConfirmationTimeframe(tf: Timeframe) {
        _mtfConfirmationTimeframe.value = tf
        loadMtfConfirmationCandles()
    }

    private fun loadMtfConfirmationCandles() {
        viewModelScope.launch(Dispatchers.Default) {
            val asset = _selectedAsset.value
            val tf = _mtfConfirmationTimeframe.value
            val preset = _selectedDatePreset.value
            val prov = _selectedProvider.value
            val now = System.currentTimeMillis()
            val startMs = now - (preset.days.toLong() * 24L * 60L * 60L * 1000L)

            val fetchResult = marketDataRepo.getHistoricalCandles(
                asset = asset,
                timeframe = tf,
                startTimeMs = startMs,
                endTimeMs = now,
                apiKey = _apiKey.value,
                isDemoMode = false,
                provider = if (prov == ProviderSelection.AUTO) null else prov.id,
                customCsvContent = _customCsvContent.value
            )

            if (fetchResult.isSuccess) {
                _mtfConfirmationCandles.value = fetchResult.getOrThrow().candles
            }
        }
    }

    // Replay Engine & Manual Trading
    fun startHistoricalReplay(startBarIndex: Int = 30) {
        val result = _currentResult.value ?: return
        val allCandles = result.candles
        if (allCandles.size < 30) return

        _replayAllCandles.value = allCandles
        _replayCurrentIndex.value = startBarIndex.coerceIn(20, allCandles.size - 1)
        _isReplayActive.value = true
        _isReplayPlaying.value = false
        _activeManualPosition.value = null
    }

    fun exitReplay() {
        stopReplayTimer()
        _isReplayActive.value = false
        _isReplayPlaying.value = false
        _activeManualPosition.value = null
    }

    fun stepReplay(delta: Int) {
        val maxIdx = _replayAllCandles.value.size - 1
        if (maxIdx <= 0) return
        val nextIdx = (_replayCurrentIndex.value + delta).coerceIn(20, maxIdx)
        _replayCurrentIndex.value = nextIdx
        checkManualPositionTrigger(nextIdx)
    }

    fun resetReplayToStart() {
        stopReplayTimer()
        _replayCurrentIndex.value = 20
        _activeManualPosition.value = null
    }

    fun jumpReplayToEnd() {
        stopReplayTimer()
        _replayCurrentIndex.value = (_replayAllCandles.value.size - 1).coerceAtLeast(0)
    }

    fun toggleReplayPlay() {
        if (_isReplayPlaying.value) {
            stopReplayTimer()
        } else {
            startReplayTimer()
        }
    }

    fun setReplaySpeed(speed: Float) {
        _replaySpeed.value = speed
        if (_isReplayPlaying.value) {
            startReplayTimer()
        }
    }

    private fun startReplayTimer() {
        stopReplayTimer()
        _isReplayPlaying.value = true
        val delayMs = (1000L / _replaySpeed.value).toLong().coerceIn(100L, 4000L)

        replayTimerJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive && _isReplayPlaying.value) {
                delay(delayMs)
                val maxIdx = _replayAllCandles.value.size - 1
                if (_replayCurrentIndex.value >= maxIdx) {
                    _isReplayPlaying.value = false
                    break
                }
                val nextIdx = _replayCurrentIndex.value + 1
                _replayCurrentIndex.value = nextIdx
                checkManualPositionTrigger(nextIdx)
            }
        }
    }

    private fun stopReplayTimer() {
        _isReplayPlaying.value = false
        replayTimerJob?.cancel()
        replayTimerJob = null
    }

    fun placeManualReplayOrder(
        direction: TradeDirection,
        stopLoss: Double,
        takeProfit: Double,
        notes: String = ""
    ) {
        val candles = _replayAllCandles.value
        val curIdx = _replayCurrentIndex.value
        if (curIdx !in candles.indices) return

        val currentCandle = candles[curIdx]
        val entryPrice = currentCandle.close
        val capital = _riskParameters.value.initialCapital
        val riskPerTrade = capital * (_riskParameters.value.riskPerTradePercent / 100.0)
        val slDistance = kotlin.math.abs(entryPrice - stopLoss)
        val qty = if (slDistance > 0) riskPerTrade / slDistance else 1.0

        val pos = ManualReplayPosition(
            id = UUID.randomUUID().toString(),
            direction = direction,
            entryPrice = entryPrice,
            stopLoss = stopLoss,
            takeProfit = takeProfit,
            quantity = qty,
            entryTimestamp = currentCandle.timestamp,
            entryBarIndex = curIdx,
            notes = notes
        )
        _activeManualPosition.value = pos
    }

    fun closeManualReplayPositionManually() {
        val pos = _activeManualPosition.value ?: return
        val candles = _replayAllCandles.value
        val curIdx = _replayCurrentIndex.value
        if (curIdx !in candles.indices) return

        val candle = candles[curIdx]
        val exitPrice = candle.close
        recordManualReplayTrade(pos, exitPrice, candle.timestamp, curIdx, ExitReason.SIGNAL_REVERSAL)
        _activeManualPosition.value = null
    }

    private fun checkManualPositionTrigger(barIndex: Int) {
        val pos = _activeManualPosition.value ?: return
        val candles = _replayAllCandles.value
        if (barIndex !in candles.indices) return

        val candle = candles[barIndex]
        var exitHit: Pair<Double, ExitReason>? = null

        if (pos.direction == TradeDirection.LONG) {
            if (candle.low <= pos.stopLoss) {
                exitHit = Pair(pos.stopLoss, ExitReason.STOP_LOSS)
            } else if (candle.high >= pos.takeProfit) {
                exitHit = Pair(pos.takeProfit, ExitReason.TAKE_PROFIT)
            }
        } else {
            if (candle.high >= pos.stopLoss) {
                exitHit = Pair(pos.stopLoss, ExitReason.STOP_LOSS)
            } else if (candle.low <= pos.takeProfit) {
                exitHit = Pair(pos.takeProfit, ExitReason.TAKE_PROFIT)
            }
        }

        if (exitHit != null) {
            recordManualReplayTrade(pos, exitHit.first, candle.timestamp, barIndex, exitHit.second)
            _activeManualPosition.value = null
        }
    }

    private fun recordManualReplayTrade(
        pos: ManualReplayPosition,
        exitPrice: Double,
        exitTimestamp: Long,
        exitBarIndex: Int,
        exitReason: ExitReason
    ) {
        val slDist = kotlin.math.abs(pos.entryPrice - pos.stopLoss)
        val priceDiff = if (pos.direction == TradeDirection.LONG) exitPrice - pos.entryPrice else pos.entryPrice - exitPrice
        val pnlDollars = priceDiff * pos.quantity
        val pnlPct = if (pos.entryPrice > 0) (priceDiff / pos.entryPrice) * 100.0 else 0.0
        val rMultiple = if (slDist > 0) priceDiff / slDist else 0.0

        val trade = Trade(
            id = pos.id,
            barIndex = pos.entryBarIndex,
            exitBarIndex = exitBarIndex,
            entryTimestamp = pos.entryTimestamp,
            exitTimestamp = exitTimestamp,
            direction = pos.direction,
            entryPrice = pos.entryPrice,
            exitPrice = exitPrice,
            quantity = pos.quantity,
            positionValue = pos.entryPrice * pos.quantity,
            pnlDollars = pnlDollars,
            pnlPercent = pnlPct,
            exitReason = exitReason,
            feesPaid = 0.0,
            rMultiple = rMultiple,
            holdingBars = exitBarIndex - pos.entryBarIndex,
            maxRunUpPct = 0.0,
            maxDrawdownPct = 0.0,
            entryReason = "Manual Replay Execution"
        )

        _manualReplayTrades.value = listOf(trade) + _manualReplayTrades.value

        // Auto-add to journal
        val jEntry = JournalEntry(
            id = UUID.randomUUID().toString(),
            tradeId = trade.id,
            timestamp = System.currentTimeMillis(),
            symbol = _selectedAsset.value.symbol,
            strategyName = "Manual Replay",
            strategyType = _selectedStrategy.value.strategyType,
            direction = trade.direction,
            entryPrice = trade.entryPrice,
            exitPrice = trade.exitPrice,
            pnlDollars = trade.pnlDollars,
            pnlPercent = trade.pnlPercent,
            rMultiple = trade.rMultiple,
            thesis = pos.notes.ifBlank { "Replay manual trade entry" },
            isManualReplay = true,
            entryReason = "Manual Replay [${trade.direction.name}]"
        )
        _journalEntries.value = listOf(jEntry) + _journalEntries.value
    }

    // Journal Management
    fun addOrUpdateJournalEntry(entry: JournalEntry) {
        val current = _journalEntries.value.toMutableList()
        val idx = current.indexOfFirst { it.id == entry.id }
        if (idx >= 0) {
            current[idx] = entry
        } else {
            current.add(0, entry)
        }
        _journalEntries.value = current
    }

    fun deleteJournalEntry(id: String) {
        _journalEntries.value = _journalEntries.value.filter { it.id != id }
    }

    fun createJournalEntryFromTrade(trade: Trade, notes: String = "", tags: List<String> = emptyList()) {
        val entry = JournalEntry(
            id = UUID.randomUUID().toString(),
            tradeId = trade.id,
            timestamp = trade.exitTimestamp,
            symbol = _selectedAsset.value.symbol,
            strategyName = _selectedStrategy.value.name,
            strategyType = _selectedStrategy.value.strategyType,
            direction = trade.direction,
            entryPrice = trade.entryPrice,
            exitPrice = trade.exitPrice,
            pnlDollars = trade.pnlDollars,
            pnlPercent = trade.pnlPercent,
            rMultiple = trade.rMultiple,
            thesis = notes,
            tags = tags,
            entryReason = trade.entryReason ?: trade.exitReason.label,
            setupGrade = if (trade.isWin) JournalGrade.A else JournalGrade.B
        )
        addOrUpdateJournalEntry(entry)
    }

    // Optimizer Sweep
    fun cancelOptimization() {
        optimizationJob?.cancel()
        _isOptimizing.value = false
    }

    fun runOptimization() {
        if (_selectedStrategy.value.strategyType == StrategyType.A_PLUS_TRENDLINE) {
            _dataFetchError.value = "OPTIMIZATION DISABLED: A+ Trendline System parameters must be explicitly specified by the human trader to prevent curve-fitting and data-mining bias."
            return
        }

        optimizationJob?.cancel()
        optimizationJob = viewModelScope.launch(Dispatchers.Default) {
            _isOptimizing.value = true
            val asset = _selectedAsset.value
            val regime = _selectedRegime.value
            val tf = _selectedTimeframe.value
            val strat = _selectedStrategy.value
            val risk = _riskParameters.value
            val preset = _selectedDatePreset.value
            val prov = _selectedProvider.value

            val now = System.currentTimeMillis()
            val startMs = now - (preset.days.toLong() * 24L * 60L * 60L * 1000L)

            val fetchResult = marketDataRepo.getHistoricalCandles(
                asset = asset,
                timeframe = tf,
                startTimeMs = startMs,
                endTimeMs = now,
                apiKey = _apiKey.value,
                isDemoMode = false,
                provider = if (prov == ProviderSelection.AUTO) null else prov.id,
                customCsvContent = _customCsvContent.value
            )

            if (!isActive) return@launch

            if (fetchResult.isSuccess) {
                val cleanCandles = fetchResult.getOrThrow().candles
                val optResults = StrategyOptimizer.runParameterSweep(strat, asset, regime, tf, risk, cleanCandles)
                _optimizationResults.value = optResults
            } else {
                _dataFetchError.value = fetchResult.exceptionOrNull()?.message ?: "Optimization data fetch failed."
            }

            _isOptimizing.value = false
        }
    }

    fun runRegimeStressTest() {
        viewModelScope.launch(Dispatchers.Default) {
            _isComparingRegimes.value = true
            val asset = _selectedAsset.value
            val tf = _selectedTimeframe.value
            val strat = _selectedStrategy.value
            val risk = _riskParameters.value

            val comparisons = StrategyOptimizer.evaluateAcrossRegimes(strat, asset, tf, risk)
            _regimeComparison.value = comparisons
            _isComparingRegimes.value = false
        }
    }

    fun saveCurrentStrategy(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val custom = _selectedStrategy.value.copy(
                id = "strat_${System.currentTimeMillis()}",
                name = name,
                isCustom = true
            )
            repository.saveStrategy(custom)
            _selectedStrategy.value = custom
        }
    }

    fun saveCurrentBacktest() {
        viewModelScope.launch(Dispatchers.IO) {
            _currentResult.value?.let { res ->
                repository.saveBacktestResult(res)
            }
        }
    }

    fun deleteSavedStrategy(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteStrategy(id)
        }
    }

    fun deleteSavedBacktest(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteBacktest(id)
        }
    }

    // Analytics Computations
    private fun computeSessionAnalytics(trades: List<Trade>): List<SessionAnalytics> {
        val timeZone = TimeZone.getTimeZone("UTC")
        val asiaTrades = mutableListOf<Trade>()
        val londonTrades = mutableListOf<Trade>()
        val nyTrades = mutableListOf<Trade>()
        val overlapTrades = mutableListOf<Trade>()

        val cal = Calendar.getInstance(timeZone)
        for (trade in trades) {
            cal.timeInMillis = trade.entryTimestamp
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            when (hour) {
                in 0..7 -> asiaTrades.add(trade)
                in 8..11 -> londonTrades.add(trade)
                in 12..16 -> overlapTrades.add(trade)
                in 17..21 -> nyTrades.add(trade)
                else -> asiaTrades.add(trade)
            }
        }

        fun summarize(name: String, list: List<Trade>): SessionAnalytics {
            val count = list.size
            val wins = list.count { it.isWin }
            val winRate = if (count > 0) (wins.toDouble() / count) * 100.0 else 0.0
            val pnl = list.sumOf { it.pnlDollars }
            val avgR = if (count > 0) list.map { it.rMultiple }.average() else 0.0
            return SessionAnalytics(name, count, winRate, pnl, avgR)
        }

        return listOf(
            summarize("Asia (00:00 - 08:00 UTC)", asiaTrades),
            summarize("London (08:00 - 12:00 UTC)", londonTrades),
            summarize("London / NY Overlap (12:00 - 16:00 UTC)", overlapTrades),
            summarize("New York (16:00 - 22:00 UTC)", nyTrades)
        )
    }

    private fun computeMaeMfe(trades: List<Trade>): MaeMfeDistribution {
        val points = trades.map { trade ->
            val mae = trade.maxDrawdownPct
            val mfe = trade.maxRunUpPct
            MaeMfePoint(
                tradeId = trade.id,
                barIndex = trade.barIndex,
                direction = trade.direction,
                rMultiple = trade.rMultiple,
                maePct = mae,
                mfePct = mfe,
                isWin = trade.isWin,
                entryPrice = trade.entryPrice,
                exitPrice = trade.exitPrice
            )
        }
        val avgMae = if (points.isNotEmpty()) points.map { it.maePct }.average() else 0.0
        val avgMfe = if (points.isNotEmpty()) points.map { it.mfePct }.average() else 0.0
        val maxMae = if (points.isNotEmpty()) points.maxOf { it.maePct } else 0.0
        val maxMfe = if (points.isNotEmpty()) points.maxOf { it.mfePct } else 0.0

        return MaeMfeDistribution(
            avgMaePct = avgMae,
            avgMfePct = avgMfe,
            maxMaePct = maxMae,
            maxMfePct = maxMfe,
            points = points
        )
    }
}

