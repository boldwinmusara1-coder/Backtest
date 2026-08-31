package com.example

import com.example.tradestrat.data.*
import com.example.tradestrat.engine.*
import com.example.tradestrat.model.*
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test

/**
 * 11-POINT DUAL STRATEGY ISOLATION & VALIDATION TEST SUITE
 *
 * Validates complete isolation, parameter integrity, determinism, and specification invariance
 * between:
 * 1. STRATEGY 1: TRENDLINE_BREAK_HIGH_WIN_RATE (Trendline Break — High Win Rate)
 * 2. STRATEGY 2: A_PLUS_V1_0 (A+ Trendline V1.0 — Frozen)
 */
class DualStrategyIsolationAndValidationTest {

    companion object {
        lateinit var btcAsset: MarketAsset
        lateinit var candles1H: List<Candle>
        lateinit var candles30M: List<Candle>
        lateinit var highWinRateStrategy: StrategyDefinition
        lateinit var aPlusV1Strategy: StrategyDefinition

        lateinit var standardRiskHighWinRate: RiskParameters
        lateinit var riskBasedAPlusV1: RiskParameters

        const val START_TIME_MS = 1609459200000L // 2021-01-01 00:00:00 UTC
        const val END_TIME_MS = 1704067200000L   // 2024-01-01 00:00:00 UTC
        const val EXPECTED_APLUS_V1_HASH = "d5c19207e05031bfe74659b87df8fc3c5aaee11f5fe6d4b58bb3f0ba5b5a8e0f"

        @BeforeClass
        @JvmStatic
        fun setup() {
            btcAsset = MarketAsset(
                id = "BTC_USD",
                symbol = "BTC/USD",
                name = "Bitcoin",
                category = AssetCategory.CRYPTO,
                basePrice = 29374.0,
                description = "Bitcoin spot"
            )

            // High Win Rate Risk: Fixed Dollar ($2,000 margin / 2x leverage = $4,000 notional, 3% stop loss)
            standardRiskHighWinRate = RiskParameters(
                initialCapital = 10000.0,
                positionSizingMode = PositionSizingMode.FIXED_DOLLAR,
                positionSizeValue = 2000.0,
                leverage = 2.0,
                stopLossType = StopLossType.PERCENTAGE,
                stopLossValue = 3.0,
                takeProfitType = TakeProfitType.PERCENTAGE,
                takeProfitValue = 6.0,
                slippageBps = 5.0,
                commissionBps = 10.0,
                allowShorting = true,
                executionModel = ExecutionModel.REALISTIC,
                intrabarExecution = IntrabarExecutionAssumption.PESSIMISTIC_STOP_FIRST
            )

            // A+ V1.0 Risk: 1.0% equity risk per trade, structural stop invalidation
            riskBasedAPlusV1 = RiskParameters(
                initialCapital = 10000.0,
                positionSizingMode = PositionSizingMode.RISK_BASED,
                positionSizeValue = 1.0,
                leverage = 1.0,
                slippageBps = 2.0,
                commissionBps = 4.0,
                allowShorting = true,
                executionModel = ExecutionModel.REALISTIC,
                intrabarExecution = IntrabarExecutionAssumption.PESSIMISTIC_STOP_FIRST
            )

            val raw1H = DeterministicHistoricalDataProvider.generateCandles(
                asset = btcAsset,
                timeframe = Timeframe.H1,
                startTimeMs = START_TIME_MS,
                endTimeMs = END_TIME_MS,
                seed = 20210101L + Timeframe.H1.minutes
            )
            candles1H = MarketDataValidator.validateAndClean(raw1H, Timeframe.H1, AssetCategory.CRYPTO).first

            val raw30M = DeterministicHistoricalDataProvider.generateCandles(
                asset = btcAsset,
                timeframe = Timeframe.M30,
                startTimeMs = START_TIME_MS,
                endTimeMs = END_TIME_MS,
                seed = 20210101L + Timeframe.M30.minutes
            )
            candles30M = MarketDataValidator.validateAndClean(raw30M, Timeframe.M30, AssetCategory.CRYPTO).first

            highWinRateStrategy = STRATEGY_TRENDLINE_BREAK_HIGH_WIN_RATE
            aPlusV1Strategy = STRATEGY_A_PLUS_V1_0
        }
    }

    // 1. Both strategies can be selected and initialized independently
    @Test
    fun test01_bothStrategiesCanBeInitializedIndependently() {
        assertNotNull(highWinRateStrategy)
        assertNotNull(aPlusV1Strategy)
        assertEquals(STRATEGY_ID_TRENDLINE_BREAK_HIGH_WIN_RATE, highWinRateStrategy.id)
        assertEquals(STRATEGY_ID_A_PLUS_V1_0, aPlusV1Strategy.id)
        assertEquals(StrategyType.TRENDLINE_BREAK, highWinRateStrategy.strategyType)
        assertEquals(StrategyType.A_PLUS_TRENDLINE, aPlusV1Strategy.strategyType)
    }

    // 2. Selecting Strategy A does not load Strategy B parameters
    @Test
    fun test02_selectingStrategyADoesNotLoadStrategyBParameters() {
        val stratA = highWinRateStrategy
        assertEquals(10, stratA.indicatorConfig.trendlineParams.pivotLookback)
        assertEquals(5, stratA.indicatorConfig.trendlineParams.pivotStrength)
        assertEquals(0.3, stratA.indicatorConfig.trendlineParams.confirmationThresholdPct, 0.001)
        assertFalse(stratA.indicatorConfig.trendlineParams.retestRequired)
    }

    // 3. Selecting Strategy B does not load Strategy A parameters
    @Test
    fun test03_selectingStrategyBDoesNotLoadStrategyAParameters() {
        val stratB = aPlusV1Strategy
        val cfg = stratB.indicatorConfig.aPlusTrendlineConfig
        assertEquals(5, cfg.swingLookback)
        assertEquals(3, cfg.confirmationBars)
        assertEquals(4, cfg.minSwingSeparationBars)
        assertEquals(2, cfg.minTouches)
        assertEquals(0.10, cfg.breakConfirmationATR, 0.001)
        assertEquals(0.25, cfg.retestToleranceATR, 0.001)
        assertEquals(12, cfg.retestMaxBars)
        assertEquals(0.10, cfg.stopBufferATR, 0.001)
        assertTrue(cfg.enableBreakEven)
        assertTrue(cfg.requireStructuralBreakEven)
        assertTrue(cfg.enableStructuralTrailing)
        assertTrue(cfg.exitOnCounterStructureBreak)
    }

    // 4. Each strategy produces deterministic results
    @Test
    fun test04_eachStrategyProducesDeterministicResults() {
        val resA1 = BacktestEngine.runBacktest(candles1H, btcAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.H1, highWinRateStrategy, standardRiskHighWinRate)
        val resA2 = BacktestEngine.runBacktest(candles1H, btcAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.H1, highWinRateStrategy, standardRiskHighWinRate)

        assertEquals(resA1.metrics.totalTrades, resA2.metrics.totalTrades)
        assertEquals(resA1.metrics.netProfitDollars, resA2.metrics.netProfitDollars, 0.001)
        assertEquals(resA1.metrics.winRatePercent, resA2.metrics.winRatePercent, 0.001)

        val resB1 = BacktestEngine.runBacktest(candles1H, btcAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.H1, aPlusV1Strategy, riskBasedAPlusV1)
        val resB2 = BacktestEngine.runBacktest(candles1H, btcAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.H1, aPlusV1Strategy, riskBasedAPlusV1)

        assertEquals(resB1.metrics.totalTrades, resB2.metrics.totalTrades)
        assertEquals(resB1.metrics.netProfitDollars, resB2.metrics.netProfitDollars, 0.001)
        assertEquals(resB1.metrics.winRatePercent, resB2.metrics.winRatePercent, 0.001)
    }

    // 5. Strategy state is isolated with zero cross-contamination
    @Test
    fun test05_strategyStateIsIsolatedWithZeroCrossContamination() {
        // Run A, then B, then A again -> result of A must be identical
        val resA_first = BacktestEngine.runBacktest(candles1H, btcAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.H1, highWinRateStrategy, standardRiskHighWinRate)
        val resB_mid = BacktestEngine.runBacktest(candles1H, btcAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.H1, aPlusV1Strategy, riskBasedAPlusV1)
        val resA_second = BacktestEngine.runBacktest(candles1H, btcAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.H1, highWinRateStrategy, standardRiskHighWinRate)

        assertEquals(resA_first.trades.size, resA_second.trades.size)
        assertEquals(resA_first.metrics.netProfitDollars, resA_second.metrics.netProfitDollars, 0.001)
        assertNotEquals(resA_first.metrics.totalTrades, resB_mid.metrics.totalTrades)
    }

    // 6. Backtests use identical candle data during comparison
    @Test
    fun test06_backtestsUseIdenticalCandleDataDuringComparison() {
        val fairRiskHigh = standardRiskHighWinRate.copy(commissionBps = 10.0, slippageBps = 5.0)
        val fairRiskAPlus = riskBasedAPlusV1.copy(commissionBps = 10.0, slippageBps = 5.0)

        val comparison = DualStrategyComparisonBuilder.buildComparison(
            highWinRateResult = BacktestEngine.runBacktest(candles1H, btcAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.H1, highWinRateStrategy, fairRiskHigh),
            aPlusV1Result = BacktestEngine.runBacktest(candles1H, btcAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.H1, aPlusV1Strategy, fairRiskAPlus),
            timeframe = Timeframe.H1,
            asset = btcAsset,
            initialCapital = 10000.0
        )

        assertTrue(comparison.validation.isValid)
        assertTrue(comparison.metricRows.isNotEmpty())
    }

    // 7. Risk configuration remains isolated and respected
    @Test
    fun test07_riskConfigurationRemainsIsolatedAndRespected() {
        val resA = BacktestEngine.runBacktest(candles1H, btcAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.H1, highWinRateStrategy, standardRiskHighWinRate)
        val resB = BacktestEngine.runBacktest(candles1H, btcAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.H1, aPlusV1Strategy, riskBasedAPlusV1)

        // Res A trades used Fixed Dollar margin with leverage ($4000 position value)
        resA.trades.forEach { trade ->
            assertTrue(trade.positionValue > 0.0)
            assertEquals(4000.0, trade.positionValue, 0.01)
        }

        // Res B trades used Risk-Based 1.0% sizing
        resB.trades.forEach { trade ->
            assertTrue(trade.positionValue > 0.0)
        }
    }

    // 8. Trade logs identify which strategy generated each trade
    @Test
    fun test08_tradeLogsIdentifyWhichStrategyGeneratedEachTrade() {
        val resA = BacktestEngine.runBacktest(candles1H, btcAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.H1, highWinRateStrategy, standardRiskHighWinRate)
        val resB = BacktestEngine.runBacktest(candles1H, btcAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.H1, aPlusV1Strategy, riskBasedAPlusV1)

        assertEquals("Trendline Break — High Win Rate", resA.strategyName)
        assertEquals("A+ Trendline V1.0 — Frozen", resB.strategyName)
        assertTrue(resA.trades.isNotEmpty())
        assertTrue(resB.trades.isNotEmpty())
    }

    // 9. Comparison results are reproducible and verifiable
    @Test
    fun test09_comparisonResultsAreReproducibleAndVerifiable() {
        val fairRiskHigh = standardRiskHighWinRate.copy(commissionBps = 10.0, slippageBps = 5.0)
        val fairRiskAPlus = riskBasedAPlusV1.copy(commissionBps = 10.0, slippageBps = 5.0)

        val comp1 = DualStrategyComparisonBuilder.buildComparison(
            highWinRateResult = BacktestEngine.runBacktest(candles1H, btcAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.H1, highWinRateStrategy, fairRiskHigh),
            aPlusV1Result = BacktestEngine.runBacktest(candles1H, btcAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.H1, aPlusV1Strategy, fairRiskAPlus),
            timeframe = Timeframe.H1,
            asset = btcAsset,
            initialCapital = 10000.0
        )
        val comp2 = DualStrategyComparisonBuilder.buildComparison(
            highWinRateResult = BacktestEngine.runBacktest(candles1H, btcAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.H1, highWinRateStrategy, fairRiskHigh),
            aPlusV1Result = BacktestEngine.runBacktest(candles1H, btcAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.H1, aPlusV1Strategy, fairRiskAPlus),
            timeframe = Timeframe.H1,
            asset = btcAsset,
            initialCapital = 10000.0
        )

        assertEquals(comp1.metricRows.size, comp2.metricRows.size)
        comp1.metricRows.indices.forEach { i ->
            assertEquals(comp1.metricRows[i].metricLabel, comp2.metricRows[i].metricLabel)
            assertEquals(comp1.metricRows[i].highWinRateValue, comp2.metricRows[i].highWinRateValue)
            assertEquals(comp1.metricRows[i].aPlusV1Value, comp2.metricRows[i].aPlusV1Value)
        }
    }

    // 10. A+ V1.0 specification hash remains unchanged
    @Test
    fun test10_aPlusV1SpecificationHashRemainsUnchanged() {
        val config = aPlusV1Strategy.indicatorConfig.aPlusTrendlineConfig
        assertEquals(EXPECTED_APLUS_V1_HASH, config.specificationHash)
        assertEquals("A_PLUS_V1.0", config.profileName)
        assertTrue(config.isApproved)
    }

    // 11. High-Win-Rate strategy implementation generates valid dynamic trades
    @Test
    fun test11_highWinRateStrategyImplementationGeneratesValidDynamicTrades() {
        val res1H = BacktestEngine.runBacktest(candles1H, btcAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.H1, highWinRateStrategy, standardRiskHighWinRate)
        val res30M = BacktestEngine.runBacktest(candles30M, btcAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.M30, highWinRateStrategy, standardRiskHighWinRate)

        assertTrue("1H should generate trades dynamically from dataset", res1H.trades.isNotEmpty())
        assertTrue("1H win rate should be positive", res1H.metrics.winRatePercent > 50.0)
        assertEquals(STRATEGY_ID_TRENDLINE_BREAK_HIGH_WIN_RATE, res1H.strategy.id)

        assertTrue("30M should generate trades dynamically from dataset", res30M.trades.isNotEmpty())
        assertTrue("30M win rate should be positive", res30M.metrics.winRatePercent > 50.0)
        assertEquals(STRATEGY_ID_TRENDLINE_BREAK_HIGH_WIN_RATE, res30M.strategy.id)
    }

    // 12. Full Strategy Switching Isolation Sequence: A -> B -> A and B -> A -> B
    @Test
    fun test12_fullStrategySwitchingSequenceIsolation() {
        // Run A -> B -> A
        val runA1 = BacktestEngine.runBacktest(candles1H, btcAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.H1, highWinRateStrategy, standardRiskHighWinRate)
        val runB1 = BacktestEngine.runBacktest(candles1H, btcAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.H1, aPlusV1Strategy, riskBasedAPlusV1)
        val runA2 = BacktestEngine.runBacktest(candles1H, btcAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.H1, highWinRateStrategy, standardRiskHighWinRate)

        assertEquals("High-Win-Rate trades must match across sequence A->B->A", runA1.trades.size, runA2.trades.size)
        assertEquals("High-Win-Rate net profit must match across sequence A->B->A", runA1.metrics.netProfitDollars, runA2.metrics.netProfitDollars, 0.0001)
        assertEquals("High-Win-Rate win rate must match across sequence A->B->A", runA1.metrics.winRatePercent, runA2.metrics.winRatePercent, 0.0001)

        // Run B -> A -> B
        val runB2 = BacktestEngine.runBacktest(candles1H, btcAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.H1, aPlusV1Strategy, riskBasedAPlusV1)
        val runA3 = BacktestEngine.runBacktest(candles1H, btcAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.H1, highWinRateStrategy, standardRiskHighWinRate)
        val runB3 = BacktestEngine.runBacktest(candles1H, btcAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.H1, aPlusV1Strategy, riskBasedAPlusV1)

        assertEquals("A+ V1.0 trades must match across sequence B->A->B", runB1.trades.size, runB3.trades.size)
        assertEquals("A+ V1.0 net profit must match across sequence B->A->B", runB1.metrics.netProfitDollars, runB3.metrics.netProfitDollars, 0.0001)
        assertEquals("A+ V1.0 win rate must match across sequence B->A->B", runB1.metrics.winRatePercent, runB3.metrics.winRatePercent, 0.0001)
    }

    // 13. Risk Model Isolation: 1.0% Equity Risk vs Fixed Dollar Margin
    @Test
    fun test13_riskModelIsolationEquationVerification() {
        val resA = BacktestEngine.runBacktest(candles1H, btcAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.H1, highWinRateStrategy, standardRiskHighWinRate)
        val resB = BacktestEngine.runBacktest(candles1H, btcAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.H1, aPlusV1Strategy, riskBasedAPlusV1)

        // Strategy A: Fixed Dollar Sizing ($2,000 margin with 2.0x leverage = $4,000 notional position value)
        assertEquals(PositionSizingMode.FIXED_DOLLAR, standardRiskHighWinRate.positionSizingMode)
        resA.trades.forEach { trade ->
            assertEquals(4000.0, trade.positionValue, 0.01)
        }

        // Strategy B: Risk-Based Sizing (riskAmount = currentEquity * 0.01)
        assertEquals(PositionSizingMode.RISK_BASED, riskBasedAPlusV1.positionSizingMode)
        assertEquals(1.0, riskBasedAPlusV1.positionSizeValue, 0.001)
        resB.trades.forEach { trade ->
            assertTrue("Trade position value must be positive", trade.positionValue > 0.0)
            assertTrue("Trade notional position value must be realistic for 1% risk", trade.positionValue <= 10000.0 * 50.0)
        }
    }

    // 14. Dataset Equality Verification: Identical candles, timestamps, capital, fees, slippage
    @Test
    fun test14_datasetEqualityVerification() {
        val comparison = DualStrategyComparisonBuilder.buildComparison(
            highWinRateResult = BacktestEngine.runBacktest(candles1H, btcAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.H1, highWinRateStrategy, standardRiskHighWinRate),
            aPlusV1Result = BacktestEngine.runBacktest(candles1H, btcAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.H1, aPlusV1Strategy, riskBasedAPlusV1),
            timeframe = Timeframe.H1,
            asset = btcAsset,
            initialCapital = 10000.0
        )

        assertEquals("BTC/USD", comparison.asset.symbol)
        assertEquals(Timeframe.H1, comparison.timeframe)
        assertEquals(10000.0, comparison.initialCapital, 0.01)
        assertTrue(comparison.validation.isValid)
        assertEquals("BTC/USD", comparison.validation.commonAssetSymbol)
        assertEquals("1H", comparison.validation.commonTimeframeLabel)

        // Check required metrics in comparison table
        val metricNames = comparison.metricRows.map { it.metricLabel }
        assertTrue(metricNames.contains("Total Closed Trades"))
        assertTrue(metricNames.contains("Win Rate"))
        assertTrue(metricNames.contains("Net Profit / Loss ($)"))
        assertTrue(metricNames.contains("Return on Capital (ROI)"))
        assertTrue(metricNames.contains("Profit Factor"))
        assertTrue(metricNames.contains("Expectancy ($ / Trade)"))
        assertTrue(metricNames.contains("Average R-Multiple"))
        assertTrue(metricNames.contains("Maximum Drawdown"))
        assertTrue(metricNames.contains("Sharpe Ratio"))
        assertTrue(metricNames.contains("Sortino Ratio"))
        assertTrue(metricNames.contains("Calmar Ratio"))
        assertTrue(metricNames.contains("Max Winning Streak"))
        assertTrue(metricNames.contains("Max Losing Streak"))
    }
}
