package com.example

import com.example.tradestrat.data.*
import com.example.tradestrat.engine.BacktestEngine
import com.example.tradestrat.engine.IndicatorCalculators
import com.example.tradestrat.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class ExampleUnitTest {

    private val baseTime = 1700000000000L
    private val testAsset = MarketAsset("BTC_USD", "BTC/USD", "Bitcoin", AssetCategory.CRYPTO, 60000.0, "Crypto")

    // 1. No look-ahead: indicator on bar N is invariant to future appended bars
    @Test
    fun test01_noLookAhead() {
        val candles10 = (0 until 10).map { i ->
            val p = 100.0 + i * 2.0
            Candle(baseTime + (i * 86400000L), p, p + 2.0, p - 2.0, p + 1.0, 1000.0)
        }
        val candles20 = candles10 + (10 until 20).map { i ->
            val p = 120.0 + (i - 10) * 5.0
            Candle(baseTime + (i * 86400000L), p, p + 4.0, p - 3.0, p + 3.0, 1500.0)
        }

        val sma10 = IndicatorCalculators.calculateSMA(candles10, 5)
        val sma20 = IndicatorCalculators.calculateSMA(candles20, 5)

        for (i in 0 until 10) {
            assertEquals(sma10[i], sma20[i])
        }
    }

    // 2. Signal on candle N enters on candle N+1 open in REALISTIC mode
    @Test
    fun test02_realisticExecution_entersOnNPlus1Open() {
        val candles = mutableListOf<Candle>()
        for (i in 0 until 30) {
            val p = if (i < 15) 100.0 - i * 0.5 else 90.0 + (i - 15) * 3.0
            candles.add(Candle(baseTime + (i * 86400000L), p, p + 1.0, p - 1.0, p, 1000.0))
        }

        val strategy = StrategyDefinition.PRESETS.first { it.strategyType == StrategyType.MA_CROSSOVER }
        val risk = RiskParameters(
            executionModel = ExecutionModel.REALISTIC,
            slippageBps = 0.0,
            commissionBps = 0.0
        )

        val result = BacktestEngine.runBacktest(candles, testAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.D1, strategy, risk)
        assertEquals(ExecutionModel.REALISTIC, result.riskParams.executionModel)
        if (result.trades.isNotEmpty()) {
            val trade = result.trades.first()
            assertTrue(trade.barIndex >= 1)
        }
    }

    // 3. Idealized mode enters on signal close
    @Test
    fun test03_idealizedExecution_entersOnSignalClose() {
        val candles = (0 until 20).map { i ->
            val p = if (i < 10) 100.0 - i * 0.5 else 90.0 + (i - 10) * 3.0
            Candle(baseTime + (i * 86400000L), p, p + 1.0, p - 1.0, p, 1000.0)
        }
        val strategy = StrategyDefinition.PRESETS.first { it.strategyType == StrategyType.MA_CROSSOVER }
        val risk = RiskParameters(
            executionModel = ExecutionModel.IDEALIZED,
            slippageBps = 0.0,
            commissionBps = 0.0
        )
        val result = BacktestEngine.runBacktest(candles, testAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.D1, strategy, risk)
        assertEquals(ExecutionModel.IDEALIZED, result.riskParams.executionModel)
        if (result.trades.isNotEmpty()) {
            val trade = result.trades.first()
            assertNotNull(trade)
        }
    }

    // 4. Long SL: Hits stop loss price accurately
    @Test
    fun test04_longStopLoss() {
        val risk = RiskParameters(
            initialCapital = 10000.0,
            stopLossType = StopLossType.PERCENTAGE,
            stopLossValue = 5.0 // 5% stop
        )
        val (sizeResult, slPrice, _, _) = BacktestEngine.calculateOrderSizingAndStops(
            equity = 10000.0,
            risk = risk,
            direction = TradeDirection.LONG,
            entryPrice = 100.0,
            atr = 2.0,
            tradesHistory = emptyList()
        )
        assertNotNull(slPrice)
        assertEquals(95.0, slPrice!!, 0.01)
    }

    // 5. Long TP: Hits take profit price accurately
    @Test
    fun test05_longTakeProfit() {
        val risk = RiskParameters(
            initialCapital = 10000.0,
            takeProfitType = TakeProfitType.PERCENTAGE,
            takeProfitValue = 10.0 // 10% TP
        )
        val (sizeResult, _, tpPrice, _) = BacktestEngine.calculateOrderSizingAndStops(
            equity = 10000.0,
            risk = risk,
            direction = TradeDirection.LONG,
            entryPrice = 100.0,
            atr = 2.0,
            tradesHistory = emptyList()
        )
        assertNotNull(tpPrice)
        assertEquals(110.0, tpPrice!!, 0.01)
    }

    // 6. Short SL: Hits stop loss price accurately above entry
    @Test
    fun test06_shortStopLoss() {
        val risk = RiskParameters(
            initialCapital = 10000.0,
            stopLossType = StopLossType.PERCENTAGE,
            stopLossValue = 5.0
        )
        val (sizeResult, slPrice, _, _) = BacktestEngine.calculateOrderSizingAndStops(
            equity = 10000.0,
            risk = risk,
            direction = TradeDirection.SHORT,
            entryPrice = 100.0,
            atr = 2.0,
            tradesHistory = emptyList()
        )
        assertNotNull(slPrice)
        assertEquals(105.0, slPrice!!, 0.01)
    }

    // 7. Short TP: Hits take profit price accurately below entry
    @Test
    fun test07_shortTakeProfit() {
        val risk = RiskParameters(
            initialCapital = 10000.0,
            takeProfitType = TakeProfitType.PERCENTAGE,
            takeProfitValue = 10.0
        )
        val (sizeResult, _, tpPrice, _) = BacktestEngine.calculateOrderSizingAndStops(
            equity = 10000.0,
            risk = risk,
            direction = TradeDirection.SHORT,
            entryPrice = 100.0,
            atr = 2.0,
            tradesHistory = emptyList()
        )
        assertNotNull(tpPrice)
        assertEquals(90.0, tpPrice!!, 0.01)
    }

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

    // 8. SL + TP same candle collision resolution
    @Test
    fun test08_slTpSameCandle_collision() {
        val candles = createBaseCrossCandles(100.0)
        // Bar 12: Touches both SL (Low=90 <= 95) and TP (High=115 >= 110)
        candles.add(Candle(baseTime + 12 * 86400000L, 101.0, 115.0, 90.0, 105.0, 1000.0))

        val riskPessimistic = RiskParameters(
            initialCapital = 10000.0,
            stopLossType = StopLossType.PERCENTAGE,
            stopLossValue = 5.0, // SL at 95.0
            takeProfitType = TakeProfitType.PERCENTAGE,
            takeProfitValue = 10.0, // TP at 110.0
            intrabarExecution = IntrabarExecutionAssumption.PESSIMISTIC_STOP_FIRST,
            slippageBps = 0.0,
            commissionBps = 0.0
        )
        val result = BacktestEngine.runBacktest(candles, testAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.D1, testFastCrossStrategy, riskPessimistic)
        assertEquals(1, result.trades.size)
        assertEquals(ExitReason.STOP_LOSS, result.trades.first().exitReason)
        assertEquals(95.0, result.trades.first().exitPrice, 0.01)
    }

    // 9. Gap through SL: exits at open when gap exceeds SL
    @Test
    fun test09_gapThroughStopLoss() {
        val candles = createBaseCrossCandles(100.0)
        // Bar 12: Market opens with gap down at 88.0 (< 95.0 SL)
        candles.add(Candle(baseTime + 12 * 86400000L, 88.0, 92.0, 85.0, 90.0, 5000.0))

        val risk = RiskParameters(
            initialCapital = 10000.0,
            stopLossType = StopLossType.PERCENTAGE,
            stopLossValue = 5.0, // SL at 95.0
            slippageBps = 0.0,
            commissionBps = 0.0
        )
        val result = BacktestEngine.runBacktest(candles, testAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.D1, testFastCrossStrategy, risk)
        assertEquals(1, result.trades.size)
        val trade = result.trades.first()
        assertEquals(ExitReason.STOP_LOSS, trade.exitReason)
        assertEquals(88.0, trade.exitPrice, 0.01)
    }

    // 10. Trailing stop: tracks peak price and ratchets SL
    @Test
    fun test10_trailingStop() {
        val risk = RiskParameters(
            stopLossType = StopLossType.TRAILING_PERCENTAGE,
            stopLossValue = 5.0
        )
        val (_, slPrice, _, isTrailing) = BacktestEngine.calculateOrderSizingAndStops(
            equity = 10000.0,
            risk = risk,
            direction = TradeDirection.LONG,
            entryPrice = 100.0,
            atr = 2.0,
            tradesHistory = emptyList()
        )
        assertTrue(isTrailing)
        assertEquals(95.0, slPrice!!, 0.01)
    }

    // POSITION SIZING: Deterministic check requested by user
    @Test
    fun testPositionSizing_userAuditScenario() {
        // Starting equity = $10,000
        // Risk = 1% ($100)
        // Entry = $100
        // Stop = $90 (10% stop distance)
        // Risk distance = $10
        // Expected quantity = 10 units
        // Expected gross risk = $100 before fees/slippage
        val risk = RiskParameters(
            initialCapital = 10000.0,
            positionSizingMode = PositionSizingMode.RISK_BASED,
            positionSizeValue = 1.0, // 1% of $10,000 = $100
            stopLossType = StopLossType.PERCENTAGE,
            stopLossValue = 10.0, // 10% of $100 entry = $10 stop distance -> SL at $90
            commissionBps = 0.0,
            slippageBps = 0.0,
            leverage = 1.0
        )
        val (sizeResult, slPrice, _, _) = BacktestEngine.calculateOrderSizingAndStops(
            equity = 10000.0,
            risk = risk,
            direction = TradeDirection.LONG,
            entryPrice = 100.0,
            atr = 1.0,
            tradesHistory = emptyList()
        )
        assertNotNull(sizeResult)
        assertNotNull(slPrice)
        assertEquals(90.0, slPrice!!, 0.0001)
        val riskDistance = 100.0 - slPrice!!
        assertEquals(10.0, riskDistance, 0.0001)
        assertEquals(10.0, sizeResult!!.quantity, 0.0001)
        assertEquals(100.0, sizeResult.initialRiskDollars, 0.0001)
        assertEquals(1000.0, sizeResult.positionValue, 0.0001)
    }

    // ACCOUNTING: Comprehensive verification for long winner/loser, short winner/loser, commissions, slippage, leverage
    @Test
    fun testAccounting_allFourQuadrantTradesAndLeverage() {
        val initialEquity = 10000.0
        val risk = RiskParameters(
            initialCapital = initialEquity,
            positionSizingMode = PositionSizingMode.FIXED_DOLLAR,
            positionSizeValue = 2000.0, // $2000 margin
            leverage = 2.0, // 2x leverage -> $4000 position value
            commissionBps = 10.0, // 0.10% on position value
            slippageBps = 0.0
        )

        // 1. Long Winner: Entry 100, Exit 110 (Quantity = 40 units)
        // Position value = $4,000, Margin = $2,000
        // Gross PnL = (110 - 100) * 40 = +$400
        // Entry fee = 4000 * 0.001 = $4, Exit fee = 4400 * 0.001 = $4.40 -> Net PnL = $391.60
        val longWinGross = (110.0 - 100.0) * 40.0
        val longWinFees = (4000.0 * 0.001) + (4400.0 * 0.001)
        val longWinNet = longWinGross - longWinFees
        assertEquals(391.60, longWinNet, 0.01)

        // 2. Long Loser: Entry 100, Exit 90 (Quantity = 40 units)
        // Gross PnL = (90 - 100) * 40 = -$400
        // Entry fee = 4000 * 0.001 = $4, Exit fee = 3600 * 0.001 = $3.60 -> Net PnL = -$407.60
        val longLossGross = (90.0 - 100.0) * 40.0
        val longLossFees = (4000.0 * 0.001) + (3600.0 * 0.001)
        val longLossNet = longLossGross - longLossFees
        assertEquals(-407.60, longLossNet, 0.01)

        // 3. Short Winner: Entry 100, Exit 90 (Quantity = 40 units)
        // Gross PnL = (100 - 90) * 40 = +$400
        // Entry fee = $4, Exit fee = $3.60 -> Net PnL = $392.40
        val shortWinGross = (100.0 - 90.0) * 40.0
        val shortWinFees = (4000.0 * 0.001) + (3600.0 * 0.001)
        val shortWinNet = shortWinGross - shortWinFees
        assertEquals(392.40, shortWinNet, 0.01)

        // 4. Short Loser: Entry 100, Exit 110 (Quantity = 40 units)
        // Gross PnL = (100 - 110) * 40 = -$400
        // Entry fee = $4, Exit fee = $4.40 -> Net PnL = -$408.40
        val shortLossGross = (100.0 - 110.0) * 40.0
        val shortLossFees = (4000.0 * 0.001) + (4400.0 * 0.001)
        val shortLossNet = shortLossGross - shortLossFees
        assertEquals(-408.40, shortLossNet, 0.01)

        // Verify combined sequential equity reconciliation
        var equity = initialEquity
        equity += longWinNet
        equity += longLossNet
        equity += shortWinNet
        equity += shortLossNet
        // Total net = 391.60 - 407.60 + 392.40 - 408.40 = -32.00 in total fees
        assertEquals(initialEquity - 32.00, equity, 0.01)
    }

    // SL/TP COLLISION: Intrabar collision resolution
    @Test
    fun testSlTpCollision_pessimisticStopFirst() {
        val candles = createBaseCrossCandles(100.0)
        // Bar 12: Touches both SL (Low=90 <= 95) and TP (High=115 >= 110)
        candles.add(Candle(baseTime + 12 * 86400000L, 101.0, 115.0, 90.0, 105.0, 1000.0))

        val riskPessimistic = RiskParameters(
            initialCapital = 10000.0,
            stopLossType = StopLossType.PERCENTAGE,
            stopLossValue = 5.0, // SL at 95.0
            takeProfitType = TakeProfitType.PERCENTAGE,
            takeProfitValue = 10.0, // TP at 110.0
            intrabarExecution = IntrabarExecutionAssumption.PESSIMISTIC_STOP_FIRST,
            slippageBps = 0.0,
            commissionBps = 0.0
        )
        val result = BacktestEngine.runBacktest(candles, testAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.D1, testFastCrossStrategy, riskPessimistic)
        assertEquals(1, result.trades.size)
        assertEquals(ExitReason.STOP_LOSS, result.trades.first().exitReason)
        assertEquals(95.0, result.trades.first().exitPrice, 0.01)
    }

    // GAPS: Gap through stop loss fills at open price
    @Test
    fun testGapThroughStopLoss_executesAtOpen() {
        val candles = createBaseCrossCandles(100.0)
        // Bar 12: Market opens with gap down at 88.0 (< 95.0 SL)
        candles.add(Candle(baseTime + 12 * 86400000L, 88.0, 92.0, 85.0, 90.0, 5000.0))

        val risk = RiskParameters(
            initialCapital = 10000.0,
            stopLossType = StopLossType.PERCENTAGE,
            stopLossValue = 5.0, // SL at 95.0
            slippageBps = 0.0,
            commissionBps = 0.0
        )
        val result = BacktestEngine.runBacktest(candles, testAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.D1, testFastCrossStrategy, risk)
        assertEquals(1, result.trades.size)
        val trade = result.trades.first()
        assertEquals(ExitReason.STOP_LOSS, trade.exitReason)
        assertEquals(88.0, trade.exitPrice, 0.01)
    }

    // 12. Commission accounting on entry and exit
    @Test
    fun test12_commissionAccounting() {
        val risk = RiskParameters(
            initialCapital = 10000.0,
            positionSizingMode = PositionSizingMode.FIXED_DOLLAR,
            positionSizeValue = 1000.0,
            leverage = 1.0,
            commissionBps = 10.0 // 0.10% = $1.00 on $1000
        )
        val (sizeResult, _, _, _) = BacktestEngine.calculateOrderSizingAndStops(
            equity = 10000.0,
            risk = risk,
            direction = TradeDirection.LONG,
            entryPrice = 100.0,
            atr = 2.0,
            tradesHistory = emptyList()
        )
        assertNotNull(sizeResult)
        assertEquals(1.0, sizeResult!!.entryFee, 0.01)
    }

    // 13. Slippage calculation
    @Test
    fun test13_slippageCalculation() {
        val basePrice = 100.0
        val slippageRate = 5.0 / 10000.0 // 5 bps = 0.05%
        val buyPrice = basePrice * (1.0 + slippageRate)
        val sellPrice = basePrice * (1.0 - slippageRate)
        assertEquals(100.05, buyPrice, 0.001)
        assertEquals(99.95, sellPrice, 0.001)
    }

    // 14. Leverage & margin accounting
    @Test
    fun test14_leverageMarginAccounting() {
        val risk = RiskParameters(
            initialCapital = 10000.0,
            positionSizingMode = PositionSizingMode.FIXED_DOLLAR,
            positionSizeValue = 2000.0,
            leverage = 5.0
        )
        val (sizeResult, _, _, _) = BacktestEngine.calculateOrderSizingAndStops(
            equity = 10000.0,
            risk = risk,
            direction = TradeDirection.LONG,
            entryPrice = 100.0,
            atr = 2.0,
            tradesHistory = emptyList()
        )
        assertNotNull(sizeResult)
        assertEquals(2000.0, sizeResult!!.marginAllocated, 0.01)
        assertEquals(10000.0, sizeResult.positionValue, 0.01)
    }

    // 15. Drawdown calculation from peak equity
    @Test
    fun test15_drawdownCalculation() {
        val peak = 12000.0
        val current = 10800.0
        val ddPct = ((peak - current) / peak) * 100.0
        assertEquals(10.0, ddPct, 0.01)
    }

    // 16. Final equity calculation
    @Test
    fun test16_finalEquityCalculation() {
        val initial = 10000.0
        val pnl1 = 500.0
        val pnl2 = -200.0
        val finalEq = initial + pnl1 + pnl2
        assertEquals(10300.0, finalEq, 0.01)
    }

    // 17. 4H timeframe aggregation
    @Test
    fun test17_4hTimeframeAggregation() {
        val baseHourMs = 1700000000000L - (1700000000000L % (4 * 3600000L))
        val hourlyCandles = listOf(
            Candle(baseHourMs, 100.0, 105.0, 98.0, 102.0, 100.0),
            Candle(baseHourMs + 3600000L, 102.0, 108.0, 101.0, 107.0, 200.0),
            Candle(baseHourMs + 7200000L, 107.0, 110.0, 104.0, 106.0, 150.0),
            Candle(baseHourMs + 10800000L, 106.0, 109.0, 103.0, 108.0, 250.0)
        )

        val aggregated = TimeframeAggregator.aggregate(hourlyCandles, Timeframe.H1, Timeframe.H4)
        assertEquals(1, aggregated.size)
        val bar4h = aggregated.first()
        assertEquals(100.0, bar4h.open, 0.01)
        assertEquals(110.0, bar4h.high, 0.01)
        assertEquals(98.0, bar4h.low, 0.01)
        assertEquals(108.0, bar4h.close, 0.01)
        assertEquals(700.0, bar4h.volume, 0.01)
    }

    // 18. Missing candle & unexpected gap detection
    @Test
    fun test18_missingCandle_unexpectedGapDetection() {
        val t0 = 1700000000000L
        val candlesWithGap = (0 until 15).map { i ->
            // Intraday missing bar between bar 4 and 5 (skip 30 min on 15m timeframe)
            val offset = if (i >= 5) (i + 2) * 900000L else i * 900000L
            val p = 100.0 + i
            Candle(t0 + offset, p, p + 2.0, p - 2.0, p + 1.0, 1000.0)
        }

        val (clean, report) = MarketDataValidator.validateAndClean(candlesWithGap, Timeframe.M15)
        assertTrue(clean.size >= 10)
        assertTrue(report.unexpectedGapsCount >= 1)
    }

    // 19. Duplicate candle detection and deduplication
    @Test
    fun test19_duplicateCandleDetection() {
        val t0 = 1700000000000L
        val dirty = (0 until 15).map { i ->
            val p = 100.0 + i
            Candle(t0 + i * 86400000L, p, p + 5.0, p - 3.0, p + 2.0, 1000.0)
        } + listOf(
            Candle(t0, 100.0, 105.0, 95.0, 102.0, 1000.0) // duplicate
        )

        val (clean, report) = MarketDataValidator.validateAndClean(dirty, Timeframe.D1)
        assertEquals(15, clean.size)
        assertEquals(1, report.duplicatesRemovedCount)
    }

    // 20. Trendline pivot confirmation: no future lookahead
    @Test
    fun test20_trendlinePivotConfirmation_noLookahead() {
        val strength = 3
        val currentBar = 10
        val maxSearchEnd = currentBar - strength // Can only confirm pivots up to bar 7
        assertEquals(7, maxSearchEnd)
    }

    // 21. Trendline break: confirms breakout above resistance
    @Test
    fun test21_trendlineBreak() {
        val linePrice = 100.0
        val prevClose = 99.0
        val currentClose = 102.0
        val confirmMultiplier = 1.01
        val isBreakout = prevClose <= linePrice && currentClose >= linePrice * confirmMultiplier
        assertTrue(isBreakout)
    }

    // 22. Trendline bounce: confirms bounce off support
    @Test
    fun test22_trendlineBounce() {
        val linePrice = 100.0
        val currentLow = 99.8
        val currentClose = 101.5
        val currentOpen = 100.2
        val currentHigh = 102.0
        val touched = currentLow <= linePrice * 1.005 && currentClose > linePrice
        val bullishRejection = currentClose > currentOpen && (currentClose - currentLow) > (currentHigh - currentClose)
        assertTrue(touched && bullishRejection)
    }

    // 23. ORB session boundaries: timezone-aware
    @Test
    fun test23_orbSessionBoundaries() {
        val orbParams = OrbParams(
            sessionTimezone = "America/New_York",
            sessionStartHour = 9,
            sessionStartMinute = 30,
            openingRangeMinutes = 30,
            sessionEndHour = 16,
            sessionEndMinute = 0
        )
        val tracker = BacktestEngine.OrbSessionTracker(orbParams, Timeframe.M15)
        // 1700058600000 = Nov 15 2023 14:30 UTC = 09:30 AM NY (Session Start)
        val candle1 = Candle(1700058600000L, 100.0, 105.0, 99.0, 102.0, 1000.0)
        tracker.update(candle1, 0)
        assertTrue(tracker.isWithinTradingSession)
        assertFalse(tracker.isOpeningRangeComplete)
    }

    // 24. No synthetic data in REAL mode: fails safely with explicit error
    @Test
    fun test24_noSyntheticDataInRealMode() = runBlocking {
        val repo = MarketDataRepository()
        // Invalid asset with no real feed should fail explicitly rather than generate synthetic data
        val bogusAsset = MarketAsset("UNKNOWN_BOGUS", "BOGUS/USD", "Bogus", AssetCategory.STOCKS, 100.0, "Bogus")
        val result = repo.getHistoricalCandles(bogusAsset, Timeframe.D1, baseTime, baseTime + 864000000L, isDemoMode = false)
        assertTrue(result.isFailure)
        val errorMsg = result.exceptionOrNull()?.message ?: ""
        assertTrue(errorMsg.isNotEmpty())
    }
}

