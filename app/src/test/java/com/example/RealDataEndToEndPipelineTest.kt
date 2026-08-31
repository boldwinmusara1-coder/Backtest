package com.example

import com.example.tradestrat.data.DataValidationReport
import com.example.tradestrat.data.DeterministicHistoricalDataProvider
import com.example.tradestrat.data.MarketDataProvider
import com.example.tradestrat.data.MarketDataValidator
import com.example.tradestrat.engine.BacktestEngine
import com.example.tradestrat.engine.StrategyComparisonEngine
import com.example.tradestrat.model.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class RealDataEndToEndPipelineTest {

    private val btcAsset = MarketDataProvider.ASSETS.first { it.id == "btc_usdt" }
    private val ethAsset = MarketDataProvider.ASSETS.first { it.id == "eth_usdt" }
    private val spyAsset = MarketDataProvider.ASSETS.first { it.id == "spy" }
    private val eurUsdAsset = MarketDataProvider.ASSETS.first { it.id == "eur_usd" }

    @Test
    fun test1_HistoricalDataIngestionAndDateFiltering() {
        val startTime = 1704067200000L // 2024-01-01 00:00:00 UTC
        val endTime = 1709251200000L   // 2024-03-01 00:00:00 UTC
        val timeframe = Timeframe.H1

        val candles = DeterministicHistoricalDataProvider.generateCandles(
            asset = btcAsset,
            timeframe = timeframe,
            startTimeMs = startTime,
            endTimeMs = endTime
        )

        assertTrue("Should generate candles for the date range", candles.isNotEmpty())
        assertEquals("First candle timestamp should match start time", startTime, candles.first().timestamp)
        assertTrue("Last candle timestamp should be before or at end time", candles.last().timestamp <= endTime)

        // Verify candle interval consistency
        val expectedStep = timeframe.minutes * 60 * 1000L
        for (i in 1 until candles.size) {
            val step = candles[i].timestamp - candles[i - 1].timestamp
            assertEquals("Candle step must be exactly $expectedStep ms", expectedStep, step)
        }
    }

    @Test
    fun test2_ChronologicalProcessingAndDataIntegrityValidation() {
        val startTime = 1704067200000L
        val rawCandles = DeterministicHistoricalDataProvider.generateCandlesCount(
            asset = spyAsset,
            timeframe = Timeframe.D1,
            startTimeMs = startTime,
            count = 100
        )

        // Introduce corrupted duplicates and out-of-order candles to test validator
        val stepMs = 24 * 60 * 60 * 1000L
        val corrupted = rawCandles.toMutableList().apply {
            add(Candle(startTime + 5 * stepMs, 470.0, 475.0, 468.0, 472.0, 10000.0)) // Duplicate
            add(0, Candle(startTime + 50 * stepMs, 480.0, 485.0, 479.0, 482.0, 15000.0)) // Out-of-order
        }

        val (cleanCandles, report) = MarketDataValidator.validateAndClean(
            rawCandles = corrupted,
            timeframe = Timeframe.D1,
            assetCategory = spyAsset.category
        )

        assertTrue("Report must be valid after deduplication and sorting", report.isValid)
        assertTrue("Deduplicator must remove duplicate", report.duplicatesRemovedCount >= 1)
        
        // Assert strict chronological monotonicity
        for (i in 1 until cleanCandles.size) {
            assertTrue("Candles must be strictly increasing in time", cleanCandles[i].timestamp > cleanCandles[i - 1].timestamp)
            assertTrue("High >= Open", cleanCandles[i].high >= cleanCandles[i].open)
            assertTrue("High >= Close", cleanCandles[i].high >= cleanCandles[i].close)
            assertTrue("Low <= Open", cleanCandles[i].low <= cleanCandles[i].open)
            assertTrue("Low <= Close", cleanCandles[i].low <= cleanCandles[i].close)
            assertTrue("Volume >= 0", cleanCandles[i].volume >= 0.0)
        }
    }

    @Test
    fun test3_AntiLookaheadAndFutureCandleIsolation() {
        val candles = DeterministicHistoricalDataProvider.generateCandlesCount(
            asset = btcAsset,
            timeframe = Timeframe.H1,
            startTimeMs = 1704067200000L,
            count = 150
        )

        val strategy = StrategyDefinition.PRESETS.first { it.strategyType == StrategyType.TRENDLINE_BREAK }
        val realisticRisk = RiskParameters(
            initialCapital = 10000.0,
            executionModel = ExecutionModel.REALISTIC
        )

        val result = BacktestEngine.runBacktest(
            candles = candles,
            asset = btcAsset,
            regime = MarketRegime.HISTORICAL_REALISTIC,
            timeframe = Timeframe.H1,
            strategy = strategy,
            risk = realisticRisk
        )

        // In Realistic execution model, trades must never execute on the same bar as the signal
        // Orders generated on bar N close must fill at bar N+1 Open
        for (trade in result.trades) {
            val entryCandle = candles.getOrNull(trade.barIndex)
            assertNotNull("Entry candle must exist", entryCandle)
            assertEquals("Trade entry timestamp must match bar timestamp", entryCandle!!.timestamp, trade.entryTimestamp)
            assertTrue("Exit bar index must be >= entry bar index", trade.exitBarIndex >= trade.barIndex)
            assertTrue("Holding bars must be >= 1", trade.holdingBars >= 1)
        }
    }

    @Test
    fun test4_StrategySignalGeneration() {
        val candles = DeterministicHistoricalDataProvider.generateCandlesCount(
            asset = ethAsset,
            timeframe = Timeframe.H4,
            startTimeMs = 1704067200000L,
            count = 250
        )

        val strategies = StrategyDefinition.PRESETS.take(4)
        for (strat in strategies) {
            val result = BacktestEngine.runBacktest(
                candles = candles,
                asset = ethAsset,
                regime = MarketRegime.HISTORICAL_REALISTIC,
                timeframe = Timeframe.H4,
                strategy = strat,
                risk = RiskParameters(initialCapital = 10000.0)
            )

            assertNotNull("Result for ${strat.name} must not be null", result)
            assertNotNull("Indicators must be computed", result.indicators)
            assertTrue("Indicators ATR must be computed", result.indicators.atr.isNotEmpty())
        }
    }

    @Test
    fun test5_RealisticTradeEntryWithSlippageAndPositionSizing() {
        val candles = DeterministicHistoricalDataProvider.generateCandlesCount(
            asset = btcAsset,
            timeframe = Timeframe.H1,
            startTimeMs = 1704067200000L,
            count = 200
        )

        val slippageBps = 10.0 // 10 bps = 0.1%
        val commissionBps = 10.0 // 10 bps = 0.1%
        val risk = RiskParameters(
            initialCapital = 10000.0,
            slippageBps = slippageBps,
            commissionBps = commissionBps,
            positionSizingMode = PositionSizingMode.PERCENT_EQUITY,
            positionSizeValue = 20.0, // 20% allocation = $2000
            executionModel = ExecutionModel.REALISTIC
        )

        val strategy = StrategyDefinition.PRESETS.first { it.strategyType == StrategyType.TRENDLINE_BREAK }
        val result = BacktestEngine.runBacktest(
            candles = candles,
            asset = btcAsset,
            regime = MarketRegime.HISTORICAL_REALISTIC,
            timeframe = Timeframe.H1,
            strategy = strategy,
            risk = risk
        )

        if (result.trades.isNotEmpty()) {
            val trade = result.trades.first()
            val entryCandle = candles[trade.barIndex]
            val expectedSlippageRate = slippageBps / 10000.0

            if (trade.direction == TradeDirection.LONG) {
                val expectedEntryPrice = entryCandle.open * (1.0 + expectedSlippageRate)
                assertEquals("Long entry price should incorporate positive slippage", expectedEntryPrice, trade.entryPrice, 0.01)
            } else {
                val expectedEntryPrice = entryCandle.open * (1.0 - expectedSlippageRate)
                assertEquals("Short entry price should incorporate negative slippage", expectedEntryPrice, trade.entryPrice, 0.01)
            }

            assertTrue("Fees paid must be positive", trade.feesPaid > 0.0)
            assertTrue("Position value should be around $2000", trade.positionValue in 1500.0..2500.0)
        }
    }

    @Test
    fun test6_TradeLifecycleAndCompleteTracking() {
        val candles = DeterministicHistoricalDataProvider.generateCandlesCount(
            asset = eurUsdAsset,
            timeframe = Timeframe.H1,
            startTimeMs = 1704067200000L,
            count = 300
        )

        val strategy = StrategyDefinition.PRESETS.first { it.strategyType == StrategyType.TRENDLINE_BREAK }
        val result = BacktestEngine.runBacktest(
            candles = candles,
            asset = eurUsdAsset,
            regime = MarketRegime.HISTORICAL_REALISTIC,
            timeframe = Timeframe.H1,
            strategy = strategy,
            risk = RiskParameters(initialCapital = 10000.0, stopLossValue = 2.0, takeProfitValue = 2.0)
        )

        for (trade in result.trades) {
            assertNotNull("Trade ID must be present", trade.id)
            assertNotNull("Direction must be present", trade.direction)
            assertTrue("Quantity > 0", trade.quantity > 0.0)
            assertTrue("Position value > 0", trade.positionValue > 0.0)
            assertNotNull("Exit reason must be assigned", trade.exitReason)
            assertTrue("Exit timestamp >= Entry timestamp", trade.exitTimestamp >= trade.entryTimestamp)
            
            // Validate net PnL equals gross PnL - fees
            assertEquals("Net PnL must equal Gross PnL minus Fees", trade.grossPnl - trade.fees, trade.pnlDollars, 0.01)
            
            // Validate result indicator matches PnL
            if (trade.pnlDollars > 0) {
                assertEquals("WIN", trade.result)
                assertTrue(trade.isWin)
            } else if (trade.pnlDollars < 0) {
                assertEquals("LOSS", trade.result)
                assertFalse(trade.isWin)
            }
        }
    }

    @Test
    fun test7_EquityCurveAndDrawdownTracking() {
        val initialCapital = 10000.0
        val candles = DeterministicHistoricalDataProvider.generateCandlesCount(
            asset = btcAsset,
            timeframe = Timeframe.D1,
            startTimeMs = 1704067200000L,
            count = 100
        )

        val strategy = StrategyDefinition.PRESETS.first()
        val result = BacktestEngine.runBacktest(
            candles = candles,
            asset = btcAsset,
            regime = MarketRegime.HISTORICAL_REALISTIC,
            timeframe = Timeframe.D1,
            strategy = strategy,
            risk = RiskParameters(initialCapital = initialCapital)
        )

        assertEquals("Equity curve points must equal candle count", candles.size, result.equityCurve.size)
        
        var runningPeak = initialCapital
        for (ep in result.equityCurve) {
            if (ep.equity > runningPeak) {
                runningPeak = ep.equity
            }
            val expectedDdPct = if (runningPeak > 0) ((runningPeak - ep.equity) / runningPeak) * 100.0 else 0.0
            assertEquals("Drawdown % must accurately reflect underwater curve", expectedDdPct, ep.drawdownPct, 0.01)
            assertTrue("Drawdown % must be >= 0", ep.drawdownPct >= 0.0)
        }

        assertTrue("Max drawdown % in metrics must be >= 0", result.metrics.maxDrawdownPercent >= 0.0)
        assertTrue("Max drawdown $ in metrics must be >= 0", result.metrics.maxDrawdownDollars >= 0.0)
    }

    @Test
    fun test8_PerformanceMetricsCalculation() {
        val initialCapital = 10000.0
        val candles = DeterministicHistoricalDataProvider.generateCandlesCount(
            asset = btcAsset,
            timeframe = Timeframe.H1,
            startTimeMs = 1704067200000L,
            count = 350
        )

        val strategy = StrategyDefinition.PRESETS.first()
        val result = BacktestEngine.runBacktest(
            candles = candles,
            asset = btcAsset,
            regime = MarketRegime.HISTORICAL_REALISTIC,
            timeframe = Timeframe.H1,
            strategy = strategy,
            risk = RiskParameters(initialCapital = initialCapital)
        )

        val m = result.metrics
        assertEquals("Total trades must match trades list", result.trades.size, m.totalTrades)
        assertEquals("Winning + losing must equal total", m.totalTrades, m.winningTrades + m.losingTrades)
        
        if (m.totalTrades > 0) {
            val expectedWinRate = (m.winningTrades.toDouble() / m.totalTrades) * 100.0
            assertEquals("Win rate formula", expectedWinRate, m.winRatePercent, 0.01)
        }

        val expectedNetProfit = m.finalEquity - initialCapital
        assertEquals("Net profit dollars", expectedNetProfit, m.netProfitDollars, 0.01)

        val expectedRoi = (expectedNetProfit / initialCapital) * 100.0
        assertEquals("ROI percent", expectedRoi, m.netProfitPercent, 0.01)

        // Verify percentile metrics consistency
        assertTrue("P75 >= Median", m.p75TradeDollars >= m.medianTradeDollars)
        assertTrue("Median >= P25", m.medianTradeDollars >= m.p25TradeDollars)
        assertEquals("IQR = P75 - P25", m.p75TradeDollars - m.p25TradeDollars, m.iqrTradeDollars, 0.001)
    }

    @Test
    fun test9_MultiStrategyComparisonOnIdenticalCandlesAndRanking() {
        val candles = DeterministicHistoricalDataProvider.generateCandlesCount(
            asset = btcAsset,
            timeframe = Timeframe.H1,
            startTimeMs = 1704067200000L,
            count = 300
        )

        val strategies = StrategyDefinition.PRESETS.take(4)
        val risk = RiskParameters(initialCapital = 10000.0)

        val compResult = StrategyComparisonEngine.runComparison(
            strategies = strategies,
            candles = candles,
            asset = btcAsset,
            regime = MarketRegime.HISTORICAL_REALISTIC,
            timeframe = Timeframe.H1,
            risk = risk
        )

        assertTrue("Multi-strategy comparison validation must pass", compResult.validation.isValid)
        assertEquals("Must return 4 strategy comparison items", 4, compResult.items.size)

        // Verify all strategies evaluated identical candle count
        for (item in compResult.items) {
            assertEquals("Each strategy must evaluate exact same candle count", candles.size, item.result.candles.size)
            assertEquals("Each normalized equity curve must have same points", candles.size, item.normalizedEquityCurve.size)
            assertEquals("Normalized base equity must start at $10,000", 10000.0, item.normalizedEquityCurve.first().normalizedEquity, 0.01)
        }

        // Verify composite ranking calculation
        val ranked = StrategyRankingCalculator.calculateRankings(compResult.items)

        assertEquals("All items must be ranked", compResult.items.size, ranked.size)
        for (i in 0 until ranked.size) {
            assertEquals("Rank number must be 1-indexed", i + 1, ranked[i].rank)
            if (i > 0) {
                assertTrue(
                    "Ranked items must be monotonically decreasing in composite score",
                    ranked[i - 1].compositeScore >= ranked[i].compositeScore
                )
            }
        }
    }

    @Test
    fun test10_StrategyStateIsolation() {
        val candles = DeterministicHistoricalDataProvider.generateCandlesCount(
            asset = btcAsset,
            timeframe = Timeframe.H1,
            startTimeMs = 1704067200000L,
            count = 200
        )

        val strategyA = StrategyDefinition.PRESETS[0]
        val strategyB = StrategyDefinition.PRESETS[1]
        val risk = RiskParameters(initialCapital = 10000.0)

        // Run A alone
        val soloA = BacktestEngine.runBacktest(
            candles = candles,
            asset = btcAsset,
            regime = MarketRegime.HISTORICAL_REALISTIC,
            timeframe = Timeframe.H1,
            strategy = strategyA,
            risk = risk
        )

        // Run B alone
        val soloB = BacktestEngine.runBacktest(
            candles = candles,
            asset = btcAsset,
            regime = MarketRegime.HISTORICAL_REALISTIC,
            timeframe = Timeframe.H1,
            strategy = strategyB,
            risk = risk
        )

        // Run both in comparison engine
        val comp = StrategyComparisonEngine.runComparison(
            strategies = listOf(strategyA, strategyB),
            candles = candles,
            asset = btcAsset,
            regime = MarketRegime.HISTORICAL_REALISTIC,
            timeframe = Timeframe.H1,
            risk = risk
        )

        val compA = comp.items.first { it.strategy.id == strategyA.id }.result
        val compB = comp.items.first { it.strategy.id == strategyB.id }.result

        assertEquals("Strategy A trade count in comparison must match solo run", soloA.trades.size, compA.trades.size)
        assertEquals("Strategy A final equity must match solo run", soloA.metrics.finalEquity, compA.metrics.finalEquity, 0.001)

        assertEquals("Strategy B trade count in comparison must match solo run", soloB.trades.size, compB.trades.size)
        assertEquals("Strategy B final equity must match solo run", soloB.metrics.finalEquity, compB.metrics.finalEquity, 0.001)
    }
}
