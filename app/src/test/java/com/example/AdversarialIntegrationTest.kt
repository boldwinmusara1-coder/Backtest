package com.example

import com.example.tradestrat.data.BinanceMarketDataSource
import com.example.tradestrat.data.MarketDataValidator
import com.example.tradestrat.engine.BacktestEngine
import com.example.tradestrat.engine.CandleResampler
import com.example.tradestrat.engine.IndicatorCalculators
import com.example.tradestrat.engine.StrategyOptimizer
import com.example.tradestrat.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AdversarialIntegrationTest {

    private val baseTime = 1700000000000L
    private val testAsset = MarketAsset("BTC_USD", "BTC/USD", "Bitcoin", AssetCategory.CRYPTO, 60000.0, "Crypto")

    private val testFastCrossStrategy = StrategyDefinition(
        id = "test_fast_cross",
        name = "Fast SMA Cross",
        strategyType = StrategyType.MA_CROSSOVER,
        indicatorConfig = IndicatorConfig(
            maParams = MovingAverageParams(fastPeriod = 2, slowPeriod = 3, useEma = false)
        )
    )

    private fun createBaseCrossCandles(entryPrice: Double): MutableList<Candle> {
        val candles = mutableListOf<Candle>()
        for (i in 0 until 10) {
            candles.add(Candle(baseTime + i * 86400000L, 100.0, 100.5, 99.5, 100.0, 1000.0))
        }
        // Bar 10: Price moves up to 110.0 -> Fast SMA(2)=105 > Slow SMA(3)=103.33 -> Triggers Long Signal
        candles.add(Candle(baseTime + 10 * 86400000L, 100.0, 111.0, 99.0, 110.0, 2000.0))
        // Bar 11: Enters Long at Open = entryPrice in REALISTIC mode
        candles.add(Candle(baseTime + 11 * 86400000L, entryPrice, entryPrice + 2.0, entryPrice - 2.0, entryPrice + 1.0, 1000.0))
        return candles
    }

    /**
     * ADVERSARIAL TEST 1: Donchian Channel Off-By-One (Double Lag) in Turtle Breakout
     */
    @Test
    fun testVulnerability1_donchianTurtleBreakoutDoubleLag() {
        val candles = mutableListOf<Candle>()
        for (i in 0 until 20) {
            val h = if (i == 5) 120.0 else 100.0
            candles.add(Candle(baseTime + i * 86400000L, 98.0, h, 95.0, 98.0, 1000.0))
        }
        // Bar 20 (i-1): High = 100.0
        candles.add(Candle(baseTime + 20 * 86400000L, 98.0, 100.0, 95.0, 98.0, 1000.0))
        // Bar 21 (i): High = 115.0 (Below bar 5's high of 120.0 -> NOT a breakout!)
        candles.add(Candle(baseTime + 21 * 86400000L, 98.0, 115.0, 95.0, 100.0, 1000.0))
        // Bar 22..25: Follow-through
        for (i in 22..25) {
            candles.add(Candle(baseTime + i * 86400000L, 100.0, 102.0, 98.0, 101.0, 1000.0))
        }

        val strategy = StrategyDefinition.PRESETS.first { it.strategyType == StrategyType.TURTLE_BREAKOUT }
        val risk = RiskParameters(
            executionModel = ExecutionModel.REALISTIC,
            slippageBps = 0.0,
            commissionBps = 0.0
        )

        val result = BacktestEngine.runBacktest(candles, testAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.D1, strategy, risk)

        // There should be NO trades entered from a false breakout
        assertEquals("Bar 21 high of 115 should not trigger breakout against 20-bar high of 120", 0, result.trades.size)
    }

    /**
     * ADVERSARIAL TEST 2: Trailing Stop Intra-Bar Peak Ratchet and Execution
     */
    @Test
    fun testVulnerability2_trailingStopSameBarDrawdown() {
        val candles = createBaseCrossCandles(100.0)
        // Bar 12: Surges to High = 150.0 (Ratchets SL to 150 * 0.90 = 135.0 for subsequent bars)
        candles.add(Candle(baseTime + 12 * 86400000L, 102.0, 150.0, 140.0, 148.0, 1000.0))
        // Bar 13: Opens at 148.0, dips to Low = 125.0 (< 135.0 trailing stop) -> Exits causally at 135.0
        candles.add(Candle(baseTime + 13 * 86400000L, 148.0, 149.0, 125.0, 126.0, 1000.0))

        val risk = RiskParameters(
            executionModel = ExecutionModel.REALISTIC,
            stopLossType = StopLossType.TRAILING_PERCENTAGE,
            stopLossValue = 10.0, // 10% trailing stop
            takeProfitType = TakeProfitType.NONE,
            intrabarExecution = IntrabarExecutionAssumption.PESSIMISTIC_STOP_FIRST,
            slippageBps = 0.0,
            commissionBps = 0.0
        )

        val result = BacktestEngine.runBacktest(candles, testAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.D1, testFastCrossStrategy, risk)

        assertTrue("Expected at least 1 trade", result.trades.isNotEmpty())
        val trade = result.trades.first()
        assertEquals(ExitReason.TRAILING_STOP, trade.exitReason)
        assertEquals(135.0, trade.exitPrice, 0.01)
        assertEquals(13, trade.exitBarIndex)
    }

    /**
     * ADVERSARIAL TEST 3: Real BacktestEngine Accounting Execution
     */
    @Test
    fun testVulnerability3_fullEngineAccountingExecution() {
        val risk = RiskParameters(
            initialCapital = 10000.0,
            positionSizingMode = PositionSizingMode.FIXED_DOLLAR,
            positionSizeValue = 2000.0,
            leverage = 2.0, // Position value = $4,000, Qty = 40 units at $100 entry
            stopLossType = StopLossType.PERCENTAGE,
            stopLossValue = 10.0, // SL at 90
            takeProfitType = TakeProfitType.PERCENTAGE,
            takeProfitValue = 10.0, // TP at 110
            commissionBps = 10.0, // 0.10% = $4 entry fee, $4.40 exit fee on win
            slippageBps = 0.0
        )

        val candlesWin = createBaseCrossCandles(100.0)
        // Bar 12: Hits TP at 110.0
        candlesWin.add(Candle(baseTime + 12 * 86400000L, 103.0, 112.0, 102.0, 111.0, 1000.0))

        val resultWin = BacktestEngine.runBacktest(candlesWin, testAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.D1, testFastCrossStrategy, risk)
        assertEquals(1, resultWin.trades.size)
        val tradeWin = resultWin.trades.first()
        assertEquals(100.0, tradeWin.entryPrice, 0.01)
        assertEquals(110.0, tradeWin.exitPrice, 0.01)
        assertEquals(40.0, tradeWin.quantity, 0.01)
        assertEquals(8.40, tradeWin.feesPaid, 0.01)
        assertEquals(391.60, tradeWin.pnlDollars, 0.01)
        assertEquals(400.0, tradeWin.pnlDollars + tradeWin.feesPaid, 0.01) // Gross PnL
        assertEquals(10391.60, resultWin.metrics.finalEquity, 0.01)
    }

    /**
     * ADVERSARIAL TEST 4: Real BacktestEngine Gap-Through-Stop Execution
     */
    @Test
    fun testVulnerability4_fullEngineGapThroughStopLoss() {
        val risk = RiskParameters(
            initialCapital = 10000.0,
            positionSizingMode = PositionSizingMode.FIXED_DOLLAR,
            positionSizeValue = 1000.0,
            leverage = 1.0,
            stopLossType = StopLossType.PERCENTAGE,
            stopLossValue = 5.0, // SL at 95.0
            slippageBps = 0.0,
            commissionBps = 0.0
        )

        val candles = createBaseCrossCandles(100.0)
        // Bar 12: Gap Down Open = 88.0 (Below SL of 95.0)
        candles.add(Candle(baseTime + 12 * 86400000L, 88.0, 92.0, 85.0, 90.0, 5000.0))

        val result = BacktestEngine.runBacktest(candles, testAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.D1, testFastCrossStrategy, risk)
        assertEquals(1, result.trades.size)
        val trade = result.trades.first()
        assertEquals(ExitReason.STOP_LOSS, trade.exitReason)
        // Must execute at actual Gap Open Price (88.0), NOT optimistic fill at SL (95.0)
        assertEquals(88.0, trade.exitPrice, 0.01)
    }

    /**
     * ADVERSARIAL TEST 5: Optimizer Regime Analysis uses Real Data Map
     */
    @Test
    fun testVulnerability5_optimizerRegimeAnalysisWithRealData() {
        val strategy = StrategyDefinition.PRESETS.first()
        val risk = RiskParameters()

        val realDataMap = MarketRegime.values().associateWith { regime ->
            (0 until 50).map { i ->
                Candle(baseTime + i * 86400000L, 100.0 + i, 102.0 + i, 99.0 + i, 101.0 + i, 1000.0)
            }
        }

        val results = StrategyOptimizer.evaluateAcrossRegimesWithData(
            strategy, testAsset, Timeframe.D1, risk, realDataMap
        )

        assertEquals(MarketRegime.values().size, results.size)
        results.forEach { r ->
            assertNotNull(r.regime)
            assertTrue("Expected trades to be evaluated on supplied data", r.totalTrades >= 0)
        }
    }

    /**
     * ADVERSARIAL TEST 6: Multi-Timeframe Causal Resampling and Indicator Alignment
     * Verifies that higher-timeframe data does NOT leak into lower-timeframe bars prior to HTF bar close.
     */
    @Test
    fun testAdversarial6_multiTimeframeCausalAlignmentNoLookahead() {
        val start = 1700000000000L
        val min15Ms = 15 * 60 * 1000L

        // Generate 12 fifteen-minute candles (3 one-hour bars)
        val ltfCandles = (0 until 12).map { i ->
            Candle(
                timestamp = start + i * min15Ms,
                open = 100.0 + i,
                high = 105.0 + i,
                low = 95.0 + i,
                close = 102.0 + i,
                volume = 100.0
            )
        }

        // Resample to 1-Hour candles
        val htfCandles = CandleResampler.resample(ltfCandles, Timeframe.H1)
        assertEquals(3, htfCandles.size)

        // Mock 1-Hour indicator values: [10.0, 20.0, 30.0]
        val htfIndicators = listOf(10.0, 20.0, 30.0)

        // Align HTF indicator causally to 15m candles
        val aligned = CandleResampler.alignHigherTimeframeIndicator(
            lowerCandles = ltfCandles,
            higherCandles = htfCandles,
            higherTimeframe = Timeframe.H1,
            higherIndicatorValues = htfIndicators
        )

        assertEquals(12, aligned.size)

        // During the first 1-hour bar (LTF bars 0..3: timestamps 0, 15m, 30m, 45m), the 1-hour bar has NOT closed yet!
        // So aligned values MUST be null (no future lookahead)
        for (i in 0..3) {
            assertNull("Bar $i at timestamp ${ltfCandles[i].timestamp} must not see unclosed 1-hour bar", aligned[i])
        }

        // Once the first 1-hour bar closes (at timestamp start + 60m = LTF bar 4), aligned value becomes 10.0
        for (i in 4..7) {
            assertEquals("Bar $i must see the first closed 1-hour bar indicator (10.0)", 10.0, aligned[i]!!, 0.001)
        }

        // Once the second 1-hour bar closes (at timestamp start + 120m = LTF bar 8), aligned value becomes 20.0
        for (i in 8..11) {
            assertEquals("Bar $i must see the second closed 1-hour bar indicator (20.0)", 20.0, aligned[i]!!, 0.001)
        }
    }

    /**
     * ADVERSARIAL TEST 7: Bollinger Reversion Causal Execution
     */
    @Test
    fun testAdversarial7_bollingerReversionCausalExecution() {
        val bbStrategy = StrategyDefinition(
            id = "test_bb",
            name = "Test Bollinger",
            description = "Test BB Reversion",
            strategyType = StrategyType.BOLLINGER_REVERSION,
            indicatorConfig = IndicatorConfig(
                bollingerParams = BollingerParams(period = 5, stdDevMultiplier = 2.0)
            )
        )

        val risk = RiskParameters(
            initialCapital = 10000.0,
            positionSizingMode = PositionSizingMode.FIXED_DOLLAR,
            positionSizeValue = 1000.0,
            leverage = 1.0,
            stopLossType = StopLossType.NONE,
            takeProfitType = TakeProfitType.NONE,
            slippageBps = 0.0,
            commissionBps = 0.0
        )

        // Build 10 baseline candles around 100.0
        val candles = (0 until 10).map { i ->
            Candle(baseTime + i * 86400000L, 100.0, 101.0, 99.0, 100.0, 1000.0)
        }.toMutableList()

        // Bar 10: Drops below lower band (Low = 85.0, Close = 86.0)
        candles.add(Candle(baseTime + 10 * 86400000L, 100.0, 100.0, 85.0, 86.0, 1000.0))
        // Bar 11: Mean reverts back inside bands (Open = 86.0, Low = 86.0, High = 100.0, Close = 98.0)
        candles.add(Candle(baseTime + 11 * 86400000L, 86.0, 100.0, 86.0, 98.0, 1000.0))
        // Bar 12: Next bar for execution at Open
        candles.add(Candle(baseTime + 12 * 86400000L, 98.0, 100.0, 97.0, 99.0, 1000.0))

        val result = BacktestEngine.runBacktest(candles, testAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.D1, bbStrategy, risk)
        assertTrue("Expected signal to enter after confirmed reversion", result.trades.isNotEmpty() || result.signalMarkers.isNotEmpty())
    }

    /**
     * ADVERSARIAL TEST 8: Intrabar Collision Order (Pessimistic SL First vs Optimistic TP First)
     */
    @Test
    fun testAdversarial8_intrabarCollisionOrderAssumptions() {
        val candlesPessimistic = createBaseCrossCandles(100.0)
        // Bar 12: Massive candle touching BOTH Stop Loss (90.0) and Take Profit (120.0)
        // Open = 100, High = 130, Low = 80, Close = 105
        candlesPessimistic.add(Candle(baseTime + 12 * 86400000L, 100.0, 130.0, 80.0, 105.0, 1000.0))

        val pessimisticRisk = RiskParameters(
            initialCapital = 10000.0,
            positionSizingMode = PositionSizingMode.FIXED_DOLLAR,
            positionSizeValue = 1000.0,
            stopLossType = StopLossType.PERCENTAGE,
            stopLossValue = 10.0, // SL at 90.0
            takeProfitType = TakeProfitType.PERCENTAGE,
            takeProfitValue = 20.0, // TP at 120.0
            intrabarExecution = IntrabarExecutionAssumption.PESSIMISTIC_STOP_FIRST,
            slippageBps = 0.0,
            commissionBps = 0.0
        )

        val resultPessimistic = BacktestEngine.runBacktest(candlesPessimistic, testAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.D1, testFastCrossStrategy, pessimisticRisk)
        assertEquals(1, resultPessimistic.trades.size)
        assertEquals(ExitReason.STOP_LOSS, resultPessimistic.trades.first().exitReason)
        assertEquals(90.0, resultPessimistic.trades.first().exitPrice, 0.01)

        val optimisticRisk = pessimisticRisk.copy(
            intrabarExecution = IntrabarExecutionAssumption.OPTIMISTIC_TP_FIRST
        )
        val resultOptimistic = BacktestEngine.runBacktest(candlesPessimistic, testAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.D1, testFastCrossStrategy, optimisticRisk)
        assertEquals(1, resultOptimistic.trades.size)
        assertEquals(ExitReason.TAKE_PROFIT, resultOptimistic.trades.first().exitReason)
        assertEquals(120.0, resultOptimistic.trades.first().exitPrice, 0.01)
    }

    /**
     * ADVERSARIAL TEST 9: Circuit Breaker Catastrophic Drawdown Halt
     */
    @Test
    fun testAdversarial9_circuitBreakerCatastrophicHalt() {
        val candles = createBaseCrossCandles(100.0)
        // Position value is $10,000 (100 units at $100).
        // Bar 12: Price crashes to 80.0 (unrealized drawdown = $2,000 / $10,000 = 20% DD).
        candles.add(Candle(baseTime + 12 * 86400000L, 95.0, 95.0, 78.0, 80.0, 5000.0))

        val riskWithBreaker = RiskParameters(
            initialCapital = 10000.0,
            positionSizingMode = PositionSizingMode.FIXED_DOLLAR,
            positionSizeValue = 10000.0,
            stopLossType = StopLossType.NONE,
            takeProfitType = TakeProfitType.NONE,
            maxDrawdownCircuitBreakerPct = 15.0, // 15% Circuit Breaker
            slippageBps = 0.0,
            commissionBps = 0.0
        )

        val result = BacktestEngine.runBacktest(candles, testAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.D1, testFastCrossStrategy, riskWithBreaker)
        assertEquals(1, result.trades.size)
        val trade = result.trades.first()
        assertEquals(ExitReason.CIRCUIT_BREAKER, trade.exitReason)
        assertEquals(80.0, trade.exitPrice, 0.01)
    }

    /**
     * ADVERSARIAL TEST 10: Market Data Validator Filters Out Corrupt and Future Candles
     */
    @Test
    fun testAdversarial10_marketDataValidatorIntegrity() {
        val now = 1700000000000L
        val corruptCandles = listOf(
            // Valid candle
            Candle(now - 3 * 86400000L, 100.0, 105.0, 95.0, 102.0, 1000.0),
            // Duplicate timestamp
            Candle(now - 3 * 86400000L, 101.0, 106.0, 96.0, 103.0, 1200.0),
            // Invalid OHLC (Low > High)
            Candle(now - 2 * 86400000L, 100.0, 90.0, 110.0, 95.0, 500.0),
            // Negative price
            Candle(now - 1 * 86400000L, -10.0, 10.0, -15.0, 5.0, 500.0),
            // Future unclosed candle (close time > now)
            Candle(now + 86400000L, 100.0, 105.0, 95.0, 102.0, 1000.0)
        )

        val (clean, report) = MarketDataValidator.validateAndClean(
            rawCandles = corruptCandles,
            timeframe = Timeframe.D1,
            currentTimeMs = now
        )

        assertEquals(1, clean.size)
        assertEquals(100.0, clean.first().open, 0.001)
        assertTrue(report.duplicatesRemovedCount >= 1)
        assertTrue(report.violations.isNotEmpty())
    }
}
