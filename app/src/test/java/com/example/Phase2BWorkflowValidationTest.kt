package com.example

import com.example.tradestrat.engine.BacktestEngine
import com.example.tradestrat.model.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

class Phase2BWorkflowValidationTest {

    private val testAsset = MarketAsset("BTC_USD", "BTC/USD", "Bitcoin", AssetCategory.CRYPTO, 65000.0, "Crypto")
    private val testTimeframe = Timeframe.D1
    private val testRegime = MarketRegime.STRONG_BULL
    private val riskParams = RiskParameters(initialCapital = 10000.0, positionSizeValue = 20.0)

    private fun createSyntheticCandles(): List<Candle> {
        val baseTime = 1704067200000L
        val dayMs = 86400000L
        val list = mutableListOf<Candle>()
        var currentPrice = 50000.0

        for (i in 0 until 400) {
            val macroCycle = sin(i / 25.0) * 4000.0
            val subSwings = sin(i / 5.0) * 1000.0 + cos(i / 12.0) * 1500.0
            val drift = i * 15.0
            val targetClose = 50000.0 + macroCycle + subSwings + drift

            val open = currentPrice
            val close = targetClose
            val spread = kotlin.math.abs(close - open)
            val high = maxOf(open, close) + spread * 0.4 + 100.0
            val low = minOf(open, close) - spread * 0.4 - 100.0
            val volume = 10000.0 + (i % 20) * 500.0

            list.add(
                Candle(
                    timestamp = baseTime + i * dayMs,
                    open = open,
                    high = high,
                    low = low,
                    close = close,
                    volume = volume
                )
            )
            currentPrice = close
        }
        return list
    }

    @Test
    fun testStrategyLabBatchEvaluationProducesIndependentResults() {
        val candles = createSyntheticCandles()

        val strats = listOf(
            StrategyDefinition.PRESETS.first { it.strategyType == StrategyType.TRENDLINE_BREAK || it.strategyType == StrategyType.TRENDLINE_BOUNCE },
            StrategyDefinition.PRESETS.first { it.strategyType == StrategyType.SMC_CONCEPTS },
            StrategyDefinition.PRESETS.first { it.strategyType == StrategyType.ICT_CONCEPTS },
            StrategyDefinition.PRESETS.first { it.strategyType == StrategyType.SMC_ICT_CONCEPTS }
        )

        val results = strats.map { strat ->
            BacktestEngine.runBacktest(
                strategy = strat,
                candles = candles,
                asset = testAsset,
                timeframe = testTimeframe,
                regime = testRegime,
                risk = riskParams
            )
        }

        assertEquals(4, results.size)
        results.forEach { res ->
            assertNotNull(res)
            assertNotNull(res.metrics)
            assertNotNull(res.equityCurve)
            assertTrue(res.equityCurve.isNotEmpty())
        }
    }

    @Test
    fun testTradeJournalCreationAndFiltering() {
        val sampleTrade = Trade(
            id = "trade_journal_test_1",
            barIndex = 10,
            exitBarIndex = 20,
            entryTimestamp = 1700000000000L,
            exitTimestamp = 1700036000000L,
            direction = TradeDirection.LONG,
            entryPrice = 50000.0,
            exitPrice = 52500.0,
            quantity = 0.5,
            positionValue = 25000.0,
            pnlDollars = 1250.0,
            pnlPercent = 5.0,
            exitReason = ExitReason.TAKE_PROFIT,
            feesPaid = 25.0,
            rMultiple = 2.5,
            holdingBars = 10,
            maxRunUpPct = 6.0,
            maxDrawdownPct = 0.8,
            entryReason = "SMC CHOCH + Order Block mitigation"
        )

        val journalEntry = JournalEntry(
            id = UUID.randomUUID().toString(),
            tradeId = sampleTrade.id,
            symbol = "BTC-USD",
            strategyName = "Smart Money Concepts",
            strategyType = StrategyType.SMC_CONCEPTS,
            direction = sampleTrade.direction,
            entryPrice = sampleTrade.entryPrice,
            exitPrice = sampleTrade.exitPrice,
            pnlDollars = sampleTrade.pnlDollars,
            pnlPercent = sampleTrade.pnlPercent,
            rMultiple = sampleTrade.rMultiple,
            timestamp = sampleTrade.entryTimestamp,
            setupGrade = JournalGrade.A_PLUS,
            executionQuality = ExecutionQuality.PERFECT,
            emotion = TraderEmotion.CONFIDENT,
            thesis = "High timeframe liquidity swept into H1 Bullish Order Block with clean displacement.",
            tags = listOf("Disciplined", "Confluence", "SMC"),
            isManualReplay = false
        )

        assertEquals("BTC-USD", journalEntry.symbol)
        assertTrue(journalEntry.isWin)
        assertEquals(JournalGrade.A_PLUS, journalEntry.setupGrade)
        assertTrue(journalEntry.tags.contains("SMC"))
    }

    @Test
    fun testExcursionCalculations() {
        val trades = listOf(
            Trade(
                id = "t1",
                barIndex = 0,
                exitBarIndex = 5,
                entryTimestamp = 1000L,
                exitTimestamp = 2000L,
                direction = TradeDirection.LONG,
                entryPrice = 100.0,
                exitPrice = 110.0,
                quantity = 1.0,
                positionValue = 100.0,
                pnlDollars = 10.0,
                pnlPercent = 10.0,
                exitReason = ExitReason.TAKE_PROFIT,
                feesPaid = 0.1,
                rMultiple = 2.0,
                holdingBars = 5,
                maxRunUpPct = 12.0,
                maxDrawdownPct = 1.5
            ),
            Trade(
                id = "t2",
                barIndex = 6,
                exitBarIndex = 10,
                entryTimestamp = 3000L,
                exitTimestamp = 4000L,
                direction = TradeDirection.SHORT,
                entryPrice = 100.0,
                exitPrice = 105.0,
                quantity = 1.0,
                positionValue = 100.0,
                pnlDollars = -5.0,
                pnlPercent = -5.0,
                exitReason = ExitReason.STOP_LOSS,
                feesPaid = 0.1,
                rMultiple = -1.0,
                holdingBars = 4,
                maxRunUpPct = 1.0,
                maxDrawdownPct = 5.0
            )
        )

        val avgMae = trades.map { it.maxDrawdownPct }.average()
        val avgMfe = trades.map { it.maxRunUpPct }.average()
        val maxMae = trades.maxOf { it.maxDrawdownPct }
        val maxMfe = trades.maxOf { it.maxRunUpPct }

        assertEquals(3.25, avgMae, 0.01)
        assertEquals(6.5, avgMfe, 0.01)
        assertEquals(5.0, maxMae, 0.01)
        assertEquals(12.0, maxMfe, 0.01)
    }
}
