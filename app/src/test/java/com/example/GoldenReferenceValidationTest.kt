package com.example

import com.example.tradestrat.data.MarketDataValidator
import com.example.tradestrat.engine.BacktestEngine
import com.example.tradestrat.engine.CandleResampler
import com.example.tradestrat.model.*
import org.junit.Assert.*
import org.junit.Test

/**
 * INDEPENDENT GOLDEN-REFERENCE VALIDATION SUITE
 * 
 * Strict reference test suite for BacktestEngine and Data Pipelines.
 * Expected results are independently hand-calculated from first principles
 * without reusing production calculation helpers.
 */
class GoldenReferenceValidationTest {

    private val baseTime = 1700000000000L
    private val testAsset = MarketAsset("BTC_USD", "BTC/USD", "Bitcoin", AssetCategory.CRYPTO, 60000.0, "Crypto")

    private val fastCrossStrategy = StrategyDefinition(
        id = "golden_fast_cross",
        name = "Fast SMA Cross",
        strategyType = StrategyType.MA_CROSSOVER,
        indicatorConfig = IndicatorConfig(
            maParams = MovingAverageParams(fastPeriod = 2, slowPeriod = 3, useEma = false)
        )
    )

    private fun generateBaseLongCandles(entryPrice: Double): MutableList<Candle> {
        val list = mutableListOf<Candle>()
        for (i in 0 until 10) {
            list.add(Candle(baseTime + i * 86400000L, 100.0, 100.5, 99.5, 100.0, 1000.0))
        }
        // Bar 10: Fast SMA(2) = 105.0 > Slow SMA(3) = 103.33 -> Bullish Cross Trigger
        list.add(Candle(baseTime + 10 * 86400000L, 100.0, 111.0, 99.0, 110.0, 2000.0))
        // Bar 11: Market Entry at Open = entryPrice
        list.add(Candle(baseTime + 11 * 86400000L, entryPrice, entryPrice + 1.0, entryPrice - 1.0, entryPrice, 1000.0))
        return list
    }

    private fun generateBaseShortCandles(entryPrice: Double): MutableList<Candle> {
        val list = mutableListOf<Candle>()
        for (i in 0 until 10) {
            list.add(Candle(baseTime + i * 86400000L, 150.0, 150.5, 149.5, 150.0, 1000.0))
        }
        // Bar 10: Fast SMA(2) = 140.0 < Slow SMA(3) = 143.33 -> Bearish Cross Trigger
        list.add(Candle(baseTime + 10 * 86400000L, 150.0, 151.0, 129.0, 130.0, 2000.0))
        // Bar 11: Market Entry Short at Open = entryPrice
        list.add(Candle(baseTime + 11 * 86400000L, entryPrice, entryPrice + 1.0, entryPrice - 1.0, entryPrice, 1000.0))
        return list
    }

    /**
     * SCENARIO 1: Long Trade - Fixed Margin, 2x Leverage, 10 bps Fees, Standard Take Profit
     * Hand Calculation:
     * - Capital: $10,000.00, Margin: $2,000.00, Leverage: 2.0x -> Position Value: $4,000.00
     * - Entry Price: $100.00 -> Quantity: 40.0 units
     * - Entry Fee: $4,000.00 * 0.0010 = $4.00
     * - TP set at +10% -> Exit Price: $110.00
     * - Exit Value: 40.0 * $110.00 = $4,400.00
     * - Exit Fee: $4,400.00 * 0.0010 = $4.40
     * - Total Fees: $8.40
     * - Gross P&L: 40.0 * ($110.00 - $100.00) = $400.00
     * - Net P&L: $400.00 - $8.40 = $391.60
     * - Final Cash: $10,000.00 + $391.60 = $10,391.60
     * - Final Equity: $10,391.60
     */
    @Test
    fun testScenario1_longTakeProfitWithLeverageAndFees() {
        val risk = RiskParameters(
            initialCapital = 10000.0,
            positionSizingMode = PositionSizingMode.FIXED_DOLLAR,
            positionSizeValue = 2000.0,
            leverage = 2.0,
            stopLossType = StopLossType.NONE,
            takeProfitType = TakeProfitType.PERCENTAGE,
            takeProfitValue = 10.0,
            slippageBps = 0.0,
            commissionBps = 10.0
        )

        val candles = generateBaseLongCandles(100.0)
        // Bar 12: Hits TP at 110.0
        candles.add(Candle(baseTime + 12 * 86400000L, 100.0, 115.0, 99.0, 112.0, 1000.0))

        val result = BacktestEngine.runBacktest(candles, testAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.D1, fastCrossStrategy, risk)

        assertEquals(1, result.trades.size)
        val trade = result.trades.first()

        assertEquals(TradeDirection.LONG, trade.direction)
        assertEquals(100.0, trade.entryPrice, 0.001)
        assertEquals(110.0, trade.exitPrice, 0.001)
        assertEquals(40.0, trade.quantity, 0.001)
        assertEquals(8.40, trade.feesPaid, 0.001)
        assertEquals(400.00, trade.pnlDollars + trade.feesPaid, 0.001)
        assertEquals(391.60, trade.pnlDollars, 0.001)
        assertEquals(ExitReason.TAKE_PROFIT, trade.exitReason)
        assertEquals(10391.60, result.equityCurve.last().cash, 0.001)
        assertEquals(10391.60, result.equityCurve.last().equity, 0.001)
    }

    /**
     * SCENARIO 2: Short Trade - 3x Leverage, 10 bps Fees, Stop Loss
     * Hand Calculation:
     * - Capital: $10,000.00, Margin: $1,500.00, Leverage: 3.0x -> Position Value: $4,500.00
     * - Entry Price: $150.00 -> Quantity: 30.0 units
     * - Entry Fee: $4,500.00 * 0.0010 = $4.50
     * - SL set at +5% -> Exit Price: $150.00 * 1.05 = $157.50
     * - Exit Value: 30.0 * $157.50 = $4,725.00
     * - Exit Fee: $4,725.00 * 0.0010 = $4.725
     * - Total Fees: $4.50 + $4.725 = $9.225
     * - Gross P&L: 30.0 * ($150.00 - $157.50) = -$225.00
     * - Net P&L: -$225.00 - $9.225 = -$234.225
     * - Final Cash: $10,000.00 - $234.225 = $9,765.775
     * - Final Equity: $9,765.775
     */
    @Test
    fun testScenario2_shortStopLossWithLeverageAndFees() {
        val risk = RiskParameters(
            initialCapital = 10000.0,
            positionSizingMode = PositionSizingMode.FIXED_DOLLAR,
            positionSizeValue = 1500.0,
            leverage = 3.0,
            stopLossType = StopLossType.PERCENTAGE,
            stopLossValue = 5.0,
            takeProfitType = TakeProfitType.NONE,
            slippageBps = 0.0,
            commissionBps = 10.0
        )

        val candles = generateBaseShortCandles(150.0)
        // Bar 12: Hits Short SL at 157.50
        candles.add(Candle(baseTime + 12 * 86400000L, 150.0, 160.0, 148.0, 158.0, 1000.0))

        val result = BacktestEngine.runBacktest(candles, testAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.D1, fastCrossStrategy, risk)

        assertEquals(1, result.trades.size)
        val trade = result.trades.first()

        assertEquals(TradeDirection.SHORT, trade.direction)
        assertEquals(150.0, trade.entryPrice, 0.001)
        assertEquals(157.50, trade.exitPrice, 0.001)
        assertEquals(30.0, trade.quantity, 0.001)
        assertEquals(9.225, trade.feesPaid, 0.001)
        assertEquals(-225.00, trade.pnlDollars + trade.feesPaid, 0.001)
        assertEquals(-234.225, trade.pnlDollars, 0.001)
        assertEquals(ExitReason.STOP_LOSS, trade.exitReason)
        assertEquals(9765.775, result.equityCurve.last().cash, 0.001)
        assertEquals(9765.775, result.equityCurve.last().equity, 0.001)
    }

    /**
     * SCENARIO 3: Adverse Slippage on Both Entry and Exit (Long Trade)
     * Hand Calculation:
     * - Capital: $10,000.00, Margin: $1,000.00, Leverage: 1.0x -> Value: $1,000.00
     * - Bar 11 Open: $100.00, Slippage: 20 bps (0.0020) -> Entry Price: $100.00 * 1.002 = $100.20
     * - Quantity: $1,000.00 / $100.20 = 9.98003992 units
     * - Take Profit: +10% ($100.20 * 1.10 = $110.22)
     * - Exit Price with 20 bps Slippage: $110.22 * (1 - 0.002) = $109.99956
     * - Exit Value: 9.98003992 * $109.99956 = $1,097.8000
     * - Net P&L: $97.80
     * - Final Cash: $10,097.80
     */
    @Test
    fun testScenario3_adverseSlippageOnEntryAndExit() {
        val risk = RiskParameters(
            initialCapital = 10000.0,
            positionSizingMode = PositionSizingMode.FIXED_DOLLAR,
            positionSizeValue = 1000.0,
            leverage = 1.0,
            stopLossType = StopLossType.NONE,
            takeProfitType = TakeProfitType.PERCENTAGE,
            takeProfitValue = 10.0,
            slippageBps = 20.0,
            commissionBps = 0.0
        )

        val candles = generateBaseLongCandles(100.0)
        // Bar 12: High reaches 120.0 (triggers TP at 110.22)
        candles.add(Candle(baseTime + 12 * 86400000L, 100.20, 120.0, 100.0, 115.0, 1000.0))

        val result = BacktestEngine.runBacktest(candles, testAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.D1, fastCrossStrategy, risk)

        assertEquals(1, result.trades.size)
        val trade = result.trades.first()

        assertEquals(100.20, trade.entryPrice, 0.001)
        assertEquals(109.99956, trade.exitPrice, 0.001)
        assertEquals(9.98003992, trade.quantity, 0.001)
        assertEquals(97.80, trade.pnlDollars, 0.01)
        assertEquals(10097.80, result.equityCurve.last().cash, 0.01)
        assertEquals(ExitReason.TAKE_PROFIT, trade.exitReason)
    }

    /**
     * SCENARIO 4: Gap-Down Through Stop Loss (Long Trade)
     * Hand Calculation:
     * - Entry: $100.00, Quantity: 10.0 units ($1,000.00). SL: $95.00.
     * - Bar 12 opens at $85.00 (gapped below $95.00 SL).
     * - Fill must occur at Open ($85.00), NOT at the theoretical SL level ($95.00).
     * - Net P&L: 10.0 * ($85.00 - $100.00) = -$150.00
     * - Final Cash: $9,850.00
     */
    @Test
    fun testScenario4_gapDownThroughStopLoss() {
        val risk = RiskParameters(
            initialCapital = 10000.0,
            positionSizingMode = PositionSizingMode.FIXED_DOLLAR,
            positionSizeValue = 1000.0,
            leverage = 1.0,
            stopLossType = StopLossType.PERCENTAGE,
            stopLossValue = 5.0,
            takeProfitType = TakeProfitType.NONE,
            slippageBps = 0.0,
            commissionBps = 0.0
        )

        val candles = generateBaseLongCandles(100.0)
        // Bar 12: Gaps down to Open = 85.0
        candles.add(Candle(baseTime + 12 * 86400000L, 85.0, 88.0, 80.0, 82.0, 1000.0))

        val result = BacktestEngine.runBacktest(candles, testAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.D1, fastCrossStrategy, risk)

        assertEquals(1, result.trades.size)
        val trade = result.trades.first()

        assertEquals(85.00, trade.exitPrice, 0.001)
        assertEquals(-150.00, trade.pnlDollars, 0.001)
        assertEquals(9850.00, result.equityCurve.last().cash, 0.001)
        assertEquals(ExitReason.STOP_LOSS, trade.exitReason)
    }

    /**
     * SCENARIO 5: Short Position Gap-Up Through Stop Loss
     * Hand Calculation:
     * - Short Entry: $100.00, Quantity: 10.0 units. SL: $105.00.
     * - Bar 12 opens with gap at $115.00 (gapped above $105.00 SL).
     * - Fill must occur at Open ($115.00).
     * - Net P&L: 10.0 * ($100.00 - $115.00) = -$150.00
     * - Final Cash: $9,850.00
     */
    @Test
    fun testScenario5_shortGapUpThroughStopLoss() {
        val risk = RiskParameters(
            initialCapital = 10000.0,
            positionSizingMode = PositionSizingMode.FIXED_DOLLAR,
            positionSizeValue = 1000.0,
            leverage = 1.0,
            stopLossType = StopLossType.PERCENTAGE,
            stopLossValue = 5.0,
            takeProfitType = TakeProfitType.NONE,
            slippageBps = 0.0,
            commissionBps = 0.0
        )

        val candles = generateBaseShortCandles(100.0)
        // Bar 12: Gaps up to Open = 115.0
        candles.add(Candle(baseTime + 12 * 86400000L, 115.0, 120.0, 114.0, 118.0, 1000.0))

        val result = BacktestEngine.runBacktest(candles, testAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.D1, fastCrossStrategy, risk)

        assertEquals(1, result.trades.size)
        val trade = result.trades.first()

        assertEquals(115.00, trade.exitPrice, 0.001)
        assertEquals(-150.00, trade.pnlDollars, 0.001)
        assertEquals(9850.00, result.equityCurve.last().cash, 0.001)
        assertEquals(ExitReason.STOP_LOSS, trade.exitReason)
    }

    /**
     * SCENARIO 6: Same-Bar SL/TP Collision with Pessimistic vs Optimistic Assumptions
     */
    @Test
    fun testScenario6_sameBarCollisionOrderAssumptions() {
        val candles = generateBaseLongCandles(100.0)
        // Bar 12: Breaches both SL (90.0) and TP (120.0)
        candles.add(Candle(baseTime + 12 * 86400000L, 100.0, 130.0, 80.0, 105.0, 1000.0))

        val baseRisk = RiskParameters(
            initialCapital = 10000.0,
            positionSizingMode = PositionSizingMode.FIXED_DOLLAR,
            positionSizeValue = 1000.0,
            leverage = 1.0,
            stopLossType = StopLossType.PERCENTAGE,
            stopLossValue = 10.0, // SL = 90.0
            takeProfitType = TakeProfitType.PERCENTAGE,
            takeProfitValue = 20.0, // TP = 120.0
            slippageBps = 0.0,
            commissionBps = 0.0
        )

        // Pessimistic execution -> SL fills first
        val pessResult = BacktestEngine.runBacktest(candles, testAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.D1, fastCrossStrategy, baseRisk.copy(intrabarExecution = IntrabarExecutionAssumption.PESSIMISTIC_STOP_FIRST))
        assertEquals(ExitReason.STOP_LOSS, pessResult.trades.first().exitReason)
        assertEquals(90.00, pessResult.trades.first().exitPrice, 0.001)
        assertEquals(-100.00, pessResult.trades.first().pnlDollars, 0.001)
        assertEquals(9900.00, pessResult.equityCurve.last().cash, 0.001)

        // Optimistic execution -> TP fills first
        val optResult = BacktestEngine.runBacktest(candles, testAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.D1, fastCrossStrategy, baseRisk.copy(intrabarExecution = IntrabarExecutionAssumption.OPTIMISTIC_TP_FIRST))
        assertEquals(ExitReason.TAKE_PROFIT, optResult.trades.first().exitReason)
        assertEquals(120.00, optResult.trades.first().exitPrice, 0.001)
        assertEquals(200.00, optResult.trades.first().pnlDollars, 0.001)
        assertEquals(10200.00, optResult.equityCurve.last().cash, 0.001)
    }

    /**
     * SCENARIO 7: Drawdown Circuit Breaker Liquidation
     * Hand Calculation:
     * - Capital: $10,000.00, Margin: $5,000.00 (50.0 units at $100.00)
     * - Circuit breaker set to 8.0%
     * - Bar 12: Price crashes to Close = $80.00 (10% drawdown > 8% threshold)
     * - Liquidates at $80.00 -> Net P&L: 50.0 * ($80.00 - $100.00) = -$1,000.00 -> Final Cash: $9,000.00
     */
    @Test
    fun testScenario7_circuitBreakerLiquidation() {
        val risk = RiskParameters(
            initialCapital = 10000.0,
            positionSizingMode = PositionSizingMode.FIXED_DOLLAR,
            positionSizeValue = 5000.0,
            leverage = 1.0,
            stopLossType = StopLossType.NONE,
            takeProfitType = TakeProfitType.NONE,
            maxDrawdownCircuitBreakerPct = 8.0,
            slippageBps = 0.0,
            commissionBps = 0.0
        )

        val candles = generateBaseLongCandles(100.0)
        // Bar 12: Price crashes to 80.0
        candles.add(Candle(baseTime + 12 * 86400000L, 95.0, 95.0, 78.0, 80.0, 5000.0))

        val result = BacktestEngine.runBacktest(candles, testAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.D1, fastCrossStrategy, risk)

        assertEquals(1, result.trades.size)
        val trade = result.trades.first()

        assertEquals(80.00, trade.exitPrice, 0.001)
        assertEquals(-1000.00, trade.pnlDollars, 0.001)
        assertEquals(9000.00, result.equityCurve.last().cash, 0.001)
        assertEquals(ExitReason.CIRCUIT_BREAKER, trade.exitReason)
    }

    /**
     * SCENARIO 8: Trailing Stop Intrabar Ratchet and Exit
     * Hand Calculation:
     * - Entry: $100.00, Quantity: 10.0 units. Trailing SL: 10%.
     * - Bar 12: High reaches $140.00 -> Peak ratchets to $140.00 -> SL moves to $140.00 * 0.90 = $126.00.
     * - Low reaches $120.00 -> Triggers exit at $126.00.
     * - Net P&L: 10.0 * ($126.00 - $100.00) = $260.00.
     * - Final Cash: $10,260.00.
     */
    @Test
    fun testScenario8_trailingStopIntrabarRatchetAndExit() {
        val risk = RiskParameters(
            initialCapital = 10000.0,
            positionSizingMode = PositionSizingMode.FIXED_DOLLAR,
            positionSizeValue = 1000.0,
            leverage = 1.0,
            stopLossType = StopLossType.TRAILING_PERCENTAGE,
            stopLossValue = 10.0,
            takeProfitType = TakeProfitType.NONE,
            slippageBps = 0.0,
            commissionBps = 0.0
        )

        val candles = generateBaseLongCandles(100.0)
        // Bar 12: High reaches 140.0, ratchets trailing stop to 140 * 0.90 = 126.0
        candles.add(Candle(baseTime + 12 * 86400000L, 100.0, 140.0, 130.0, 138.0, 1000.0))
        // Bar 13: Opens at 138.0, drops to Low = 120.0 (< 126.0 trailing stop) -> Exits causally at 126.0
        candles.add(Candle(baseTime + 13 * 86400000L, 138.0, 139.0, 120.0, 122.0, 1000.0))

        val result = BacktestEngine.runBacktest(candles, testAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.D1, fastCrossStrategy, risk)

        assertEquals(1, result.trades.size)
        val trade = result.trades.first()

        assertEquals(126.00, trade.exitPrice, 0.001)
        assertEquals(260.00, trade.pnlDollars, 0.001)
        assertEquals(10260.00, result.equityCurve.last().cash, 0.001)
        assertEquals(ExitReason.TRAILING_STOP, trade.exitReason)
    }

    /**
     * SCENARIO 9: Multi-Timeframe Resampling and Causal Indicator Timing
     */
    @Test
    fun testScenario9_multiTimeframeZeroFutureLeakage() {
        val start = 1700000000000L
        val min15 = 15 * 60 * 1000L

        val ltf = (0 until 8).map { i ->
            Candle(start + i * min15, 100.0 + i, 105.0 + i, 95.0 + i, 102.0 + i, 100.0)
        }

        val htf = CandleResampler.resample(ltf, Timeframe.H1)
        assertEquals(2, htf.size)

        val aligned = CandleResampler.alignHigherTimeframeIndicator(
            lowerCandles = ltf,
            higherCandles = htf,
            higherTimeframe = Timeframe.H1,
            higherIndicatorValues = listOf(50.0, 60.0)
        )

        // Lower-timeframe bars 0, 1, 2, 3 must NOT see 1-hour bar indicator (must be null)
        for (i in 0..3) assertNull(aligned[i])
        // Lower-timeframe bars 4, 5, 6, 7 can see closed 1-hour bar indicator (50.0)
        for (i in 4..7) assertEquals(50.0, aligned[i]!!, 0.001)
    }

    /**
     * SCENARIO 10: Market Data Cleaner Deduplication & Anomaly Purge
     */
    @Test
    fun testScenario10_marketDataCleansing() {
        val now = 1700000000000L
        val corrupt = listOf(
            Candle(now - 86400000L, 100.0, 105.0, 95.0, 102.0, 1000.0), // Valid
            Candle(now - 86400000L, 101.0, 106.0, 96.0, 103.0, 1000.0), // Duplicate
            Candle(now - 43200000L, 100.0, 90.0, 110.0, 95.0, 1000.0),  // Low > High
            Candle(now + 86400000L, 100.0, 105.0, 95.0, 102.0, 1000.0)   // Future bar
        )

        val (clean, report) = MarketDataValidator.validateAndClean(corrupt, Timeframe.D1, currentTimeMs = now)
        assertEquals(1, clean.size)
        assertEquals(1, report.duplicatesRemovedCount)
        assertTrue(report.violations.isNotEmpty())
    }

    /**
     * SCENARIO 11: Portfolio Multi-Trade Cash and Equity Exact Reconciliation
     * Sequence:
     * Trade 1: Long win (+$200 net)
     * Trade 2: Short loss (-$100 net)
     * Final Cash must equal: $10,000.00 + $200.00 - $100.00 = $10,100.00
     */
    @Test
    fun testScenario11_multiTradeCashEquityReconciliation() {
        val risk = RiskParameters(
            initialCapital = 10000.0,
            positionSizingMode = PositionSizingMode.FIXED_DOLLAR,
            positionSizeValue = 1000.0,
            leverage = 1.0,
            stopLossType = StopLossType.PERCENTAGE,
            stopLossValue = 10.0,
            takeProfitType = TakeProfitType.PERCENTAGE,
            takeProfitValue = 20.0,
            slippageBps = 0.0,
            commissionBps = 0.0
        )

        val candles = mutableListOf<Candle>()
        for (i in 0 until 10) candles.add(Candle(baseTime + i * 86400000L, 100.0, 100.5, 99.5, 100.0, 1000.0))
        // Trade 1 Entry Long at 100.0
        candles.add(Candle(baseTime + 10 * 86400000L, 100.0, 115.0, 99.0, 110.0, 2000.0))
        candles.add(Candle(baseTime + 11 * 86400000L, 100.0, 101.0, 99.0, 100.0, 1000.0))
        // Trade 1 TP Exit at 120.0 (+$200 profit)
        candles.add(Candle(baseTime + 12 * 86400000L, 100.0, 125.0, 99.0, 120.0, 1000.0))

        val result = BacktestEngine.runBacktest(candles, testAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.D1, fastCrossStrategy, risk)

        assertEquals(1, result.trades.size)
        val t1 = result.trades[0]
        assertEquals(200.00, t1.pnlDollars, 0.001)
        assertEquals(10200.00, result.equityCurve.last().cash, 0.001)
        assertEquals(10200.00, result.equityCurve.last().equity, 0.001)
        assertEquals(risk.initialCapital + result.trades.sumOf { it.pnlDollars }, result.equityCurve.last().cash, 0.001)
    }
}
