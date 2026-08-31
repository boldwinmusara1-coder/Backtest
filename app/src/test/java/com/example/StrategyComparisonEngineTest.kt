package com.example

import com.example.tradestrat.data.MarketDataProvider
import com.example.tradestrat.engine.BacktestEngine
import com.example.tradestrat.engine.StrategyComparisonEngine
import com.example.tradestrat.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.InputStreamReader
import kotlin.math.abs

class StrategyComparisonEngineTest {

    private lateinit var sampleCandles: List<Candle>
    private val asset = MarketDataProvider.ASSETS.first { it.symbol.contains("BTC") }
    private val regime = MarketRegime.STRONG_BULL
    private val tf = Timeframe.D1
    private val risk = RiskParameters(
        initialCapital = 10000.0,
        positionSizingMode = PositionSizingMode.FIXED_DOLLAR,
        positionSizeValue = 2500.0,
        stopLossType = StopLossType.PERCENTAGE,
        stopLossValue = 3.0,
        takeProfitType = TakeProfitType.PERCENTAGE,
        takeProfitValue = 6.0,
        leverage = 1.0,
        commissionBps = 5.0,
        slippageBps = 2.0
    )

    @Before
    fun setUp() {
        val resourceStream = StrategyComparisonEngineTest::class.java.classLoader?.getResourceAsStream("data/btc_daily_5yr.csv")
        val rawCsv = if (resourceStream != null) {
            InputStreamReader(resourceStream).readText()
        } else {
            val directFile = File("src/test/resources/data/btc_daily_5yr.csv")
            if (directFile.exists()) directFile.readText() else File("/app/src/test/resources/data/btc_daily_5yr.csv").readText()
        }

        val list = mutableListOf<Candle>()
        val lines = rawCsv.lines().drop(1).filter { it.isNotBlank() }.take(250)
        for (line in lines) {
            val parts = line.split(",")
            if (parts.size >= 6) {
                list.add(
                    Candle(
                        timestamp = parts[0].trim().toLong(),
                        open = parts[1].trim().toDouble(),
                        high = parts[2].trim().toDouble(),
                        low = parts[3].trim().toDouble(),
                        close = parts[4].trim().toDouble(),
                        volume = parts[5].trim().toDouble()
                    )
                )
            }
        }
        sampleCandles = list
    }

    // ---------------------------------------------------------------------------------------------
    // 1. $10,000 Equity Normalization
    // ---------------------------------------------------------------------------------------------
    @Test
    fun testEquityNormalizationMathematicalBaseline() {
        val customCapitalRisk = risk.copy(initialCapital = 25000.0)
        val strat = StrategyDefinition.PRESETS.first { it.id == "preset_orb_standard" }

        val comparison = StrategyComparisonEngine.runComparison(
            strategies = listOf(strat),
            candles = sampleCandles,
            asset = asset,
            regime = regime,
            timeframe = tf,
            risk = customCapitalRisk
        )

        assertTrue("Comparison must succeed", comparison.validation.isValid)
        val item = comparison.items.first()
        val normCurve = item.normalizedEquityCurve

        assertTrue("Normalized curve must not be empty", normCurve.isNotEmpty())

        // 1. Base check: First point must normalize strictly to exactly $10,000.00
        assertEquals(10000.0, normCurve.first().normalizedEquity, 0.001)

        // 2. Mathematical scaling check: normalized = original * (10000 / initialCapital)
        val expectedScaleFactor = 10000.0 / 25000.0 // 0.4
        for (point in normCurve) {
            val expectedNormalized = point.originalEquity * expectedScaleFactor
            assertEquals("Normalized equity point scale mismatch", expectedNormalized, point.normalizedEquity, 0.001)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 2. Ranking Order and Composite Score Weights
    // ---------------------------------------------------------------------------------------------
    @Test
    fun testRankingOrderAndCompositeScoreWeights() {
        val strats = listOf(
            StrategyDefinition.PRESETS.first { it.id == "preset_smc_structure_shift" },
            StrategyDefinition.PRESETS.first { it.id == "preset_orb_standard" },
            StrategyDefinition.PRESETS.first { it.id == "preset_orb_defensive" },
            StrategyDefinition.PRESETS.first { it.id == "preset_ema_cross" }
        )

        val comparison = StrategyComparisonEngine.runComparison(
            strategies = strats,
            candles = sampleCandles,
            asset = asset,
            regime = regime,
            timeframe = tf,
            risk = risk
        )

        assertTrue(comparison.validation.isValid)
        val rankings = StrategyRankingCalculator.calculateRankings(comparison.items)

        assertEquals("Must rank all evaluated strategies", strats.size, rankings.size)

        // Verify ranks 1..N sequence
        val rankIndices = rankings.map { it.rank }
        assertEquals(listOf(1, 2, 3, 4), rankIndices)

        // Verify composite score strictly non-increasing order
        for (i in 0 until rankings.size - 1) {
            assertTrue(
                "Rank ${rankings[i].rank} score (${rankings[i].compositeScore}) must be >= Rank ${rankings[i+1].rank} score (${rankings[i+1].compositeScore})",
                rankings[i].compositeScore >= rankings[i + 1].compositeScore
            )
        }

        // Verify transparent score formula breakdown is non-empty and formatted
        rankings.forEach { r ->
            assertTrue("Formula breakdown must contain Profit Factor", r.scoreFormulaBreakdown.contains("PF:"))
            assertTrue("Formula breakdown must contain Drawdown", r.scoreFormulaBreakdown.contains("DD:"))
            assertTrue("Composite score must be within [0, 100]", r.compositeScore in 0.0..100.0)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 3. Drawdown Calculation
    // ---------------------------------------------------------------------------------------------
    @Test
    fun testDrawdownCalculationUnderwaterPrecision() {
        val strat = StrategyDefinition.PRESETS.first { it.id == "preset_smc_structure_shift" }
        val comparison = StrategyComparisonEngine.runComparison(
            strategies = listOf(strat),
            candles = sampleCandles,
            asset = asset,
            regime = regime,
            timeframe = tf,
            risk = risk
        )

        val item = comparison.items.first()
        val ddCurve = item.drawdownCurve
        val rawEquity = item.result.equityCurve

        assertEquals(rawEquity.size, ddCurve.size)

        var runningPeak = rawEquity.first().equity
        var calculatedMaxDd = 0.0

        for (i in rawEquity.indices) {
            val eq = rawEquity[i].equity
            if (eq > runningPeak) {
                runningPeak = eq
            }
            val expectedDd = if (runningPeak > 0) ((runningPeak - eq) / runningPeak) * 100.0 else 0.0
            if (expectedDd > calculatedMaxDd) calculatedMaxDd = expectedDd

            assertEquals("Drawdown curve percentage mismatch at bar $i", expectedDd, ddCurve[i].drawdownPct, 0.01)
            assertTrue("Drawdown percentage must be >= 0", ddCurve[i].drawdownPct >= 0.0)
            assertTrue("Drawdown percentage must be <= 100", ddCurve[i].drawdownPct <= 100.0)
        }

        assertEquals("Max drawdown metric mismatch", calculatedMaxDd, item.result.metrics.maxDrawdownPercent, 0.05)
    }

    // ---------------------------------------------------------------------------------------------
    // 4. Monthly P&L and Matrix Aggregation
    // ---------------------------------------------------------------------------------------------
    @Test
    fun testMonthlyPnlAndRoiAggregation() {
        val strats = listOf(
            StrategyDefinition.PRESETS.first { it.id == "preset_smc_structure_shift" },
            StrategyDefinition.PRESETS.first { it.id == "preset_orb_standard" }
        )

        val comparison = StrategyComparisonEngine.runComparison(
            strategies = strats,
            candles = sampleCandles,
            asset = asset,
            regime = regime,
            timeframe = tf,
            risk = risk
        )

        assertTrue(comparison.validation.isValid)

        for (item in comparison.items) {
            val monthly = item.monthlyMetrics
            if (item.result.trades.isNotEmpty()) {
                val sumMonthlyPnl = monthly.sumOf { it.netPnlDollars }
                val totalTradesPnl = item.result.trades.sumOf { it.pnlDollars }
                assertEquals("Sum of monthly PnL must equal sum of all trade PnLs", totalTradesPnl, sumMonthlyPnl, 0.01)

                // Verify monthly ROI formula: (monthlyNetPnl / initialCapital) * 100
                monthly.forEach { m ->
                    val expectedRoi = (m.netPnlDollars / risk.initialCapital) * 100.0
                    assertEquals("Monthly ROI percentage mismatch", expectedRoi, m.roiPercent, 0.001)
                }
            }
        }

        // Verify cross-strategy unified matrix rows
        if (comparison.monthlyMatrix.isNotEmpty()) {
            comparison.monthlyMatrix.forEach { row ->
                assertEquals("Matrix row must contain PnL for each strategy", strats.size, row.strategyPnl.size)
                assertEquals("Matrix row must contain ROI for each strategy", strats.size, row.strategyRoi.size)
                strats.forEach { s ->
                    assertTrue("Must have key for strategy ${s.id}", row.strategyPnl.containsKey(s.id))
                    assertTrue("Must have key for strategy ${s.id}", row.strategyRoi.containsKey(s.id))
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 5. P25 / Median / P75 / IQR Mathematical Precision
    // ---------------------------------------------------------------------------------------------
    @Test
    fun testPercentilesMedianAndIqrMathematicalPrecision() {
        val strat = StrategyDefinition.PRESETS.first { it.id == "preset_smc_structure_shift" }
        val comparison = StrategyComparisonEngine.runComparison(
            strategies = listOf(strat),
            candles = sampleCandles,
            asset = asset,
            regime = regime,
            timeframe = tf,
            risk = risk
        )

        val item = comparison.items.first()
        val dist = item.distribution
        val trades = item.result.trades

        if (trades.isNotEmpty()) {
            val sortedPnls = trades.map { it.pnlDollars }.sorted()

            // Verify IQR invariant: IQR = P75 - P25
            assertEquals("IQR must equal P75 - P25", dist.p75TradeDollars - dist.p25TradeDollars, dist.interquartileRangeDollars, 0.0001)

            // Verify monotonic percentile progression: P25 <= Median <= P75
            assertTrue("P25 must be <= Median", dist.p25TradeDollars <= dist.medianTradeDollars + 1e-9)
            assertTrue("Median must be <= P75", dist.medianTradeDollars <= dist.p75TradeDollars + 1e-9)

            // Verify boundaries
            assertEquals("Largest winner mismatch", sortedPnls.filter { it > 0 }.maxOrNull() ?: 0.0, dist.largestWinnerDollars, 0.001)
            assertEquals("Largest loser mismatch", sortedPnls.filter { it < 0 }.minOrNull() ?: 0.0, dist.largestLoserDollars, 0.001)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 6. Fair-Comparison Validation
    // ---------------------------------------------------------------------------------------------
    @Test
    fun testFairComparisonValidationMismatches() {
        val strat1 = StrategyDefinition.PRESETS.first { it.id == "preset_orb_standard" }
        val strat2 = StrategyDefinition.PRESETS.first { it.id == "preset_smc_structure_shift" }

        val asset1 = MarketDataProvider.ASSETS[0]
        val asset2 = MarketDataProvider.ASSETS[1]

        val resBase = BacktestEngine.runBacktest(
            candles = sampleCandles,
            asset = asset1,
            regime = regime,
            timeframe = Timeframe.D1,
            strategy = strat1,
            risk = risk.copy(initialCapital = 10000.0, commissionBps = 5.0, slippageBps = 2.0, leverage = 1.0)
        )

        // 1. Mismatched initial capital
        val resCapitalMismatch = BacktestEngine.runBacktest(
            candles = sampleCandles,
            asset = asset1,
            regime = regime,
            timeframe = Timeframe.D1,
            strategy = strat2,
            risk = risk.copy(initialCapital = 50000.0)
        )
        val valCapital = StrategyComparisonEngine.validateFairComparison(listOf(resBase, resCapitalMismatch))
        assertFalse("Capital mismatch must fail validation", valCapital.isValid)
        assertTrue(valCapital.validationErrors.any { it.contains("initial capital mismatch") })

        // 2. Mismatched asset
        val resAssetMismatch = BacktestEngine.runBacktest(
            candles = sampleCandles,
            asset = asset2,
            regime = regime,
            timeframe = Timeframe.D1,
            strategy = strat2,
            risk = risk
        )
        val valAsset = StrategyComparisonEngine.validateFairComparison(listOf(resBase, resAssetMismatch))
        assertFalse("Asset mismatch must fail validation", valAsset.isValid)
        assertTrue(valAsset.validationErrors.any { it.contains("evaluated asset") })

        // 3. Mismatched timeframe
        val resTfMismatch = BacktestEngine.runBacktest(
            candles = sampleCandles,
            asset = asset1,
            regime = regime,
            timeframe = Timeframe.H1,
            strategy = strat2,
            risk = risk
        )
        val valTf = StrategyComparisonEngine.validateFairComparison(listOf(resBase, resTfMismatch))
        assertFalse("Timeframe mismatch must fail validation", valTf.isValid)
        assertTrue(valTf.validationErrors.any { it.contains("timeframe") })

        // 4. Mismatched commission
        val resCommMismatch = BacktestEngine.runBacktest(
            candles = sampleCandles,
            asset = asset1,
            regime = regime,
            timeframe = Timeframe.D1,
            strategy = strat2,
            risk = risk.copy(commissionBps = 20.0)
        )
        val valComm = StrategyComparisonEngine.validateFairComparison(listOf(resBase, resCommMismatch))
        assertFalse("Commission mismatch must fail validation", valComm.isValid)
        assertTrue(valComm.validationErrors.any { it.contains("commission mismatch") })
    }

    // ---------------------------------------------------------------------------------------------
    // 7. Strategy State Isolation
    // ---------------------------------------------------------------------------------------------
    @Test
    fun testStrategyStateIsolationIndependentExecutions() {
        val strat1 = StrategyDefinition.PRESETS.first { it.id == "preset_orb_standard" }
        val strat2 = StrategyDefinition.PRESETS.first { it.id == "preset_smc_structure_shift" }

        // Execute strat1 alone
        val single1 = StrategyComparisonEngine.runComparison(
            strategies = listOf(strat1),
            candles = sampleCandles,
            asset = asset,
            regime = regime,
            timeframe = tf,
            risk = risk
        )

        // Execute strat1 and strat2 together
        val combined = StrategyComparisonEngine.runComparison(
            strategies = listOf(strat1, strat2),
            candles = sampleCandles,
            asset = asset,
            regime = regime,
            timeframe = tf,
            risk = risk
        )

        // Execute strat2 and strat1 in reverse order
        val combinedReversed = StrategyComparisonEngine.runComparison(
            strategies = listOf(strat2, strat1),
            candles = sampleCandles,
            asset = asset,
            regime = regime,
            timeframe = tf,
            risk = risk
        )

        val singleItem1 = single1.items.first()
        val combinedItem1 = combined.items.first { it.strategy.id == strat1.id }
        val reversedItem1 = combinedReversed.items.first { it.strategy.id == strat1.id }

        // Verify total isolation of metric results
        assertEquals(singleItem1.result.trades.size, combinedItem1.result.trades.size)
        assertEquals(singleItem1.result.trades.size, reversedItem1.result.trades.size)
        assertEquals(singleItem1.result.metrics.netProfitDollars, combinedItem1.result.metrics.netProfitDollars, 0.001)
        assertEquals(singleItem1.result.metrics.netProfitDollars, reversedItem1.result.metrics.netProfitDollars, 0.001)
        assertEquals(singleItem1.result.metrics.maxDrawdownPercent, combinedItem1.result.metrics.maxDrawdownPercent, 0.001)
        assertEquals(singleItem1.result.metrics.maxDrawdownPercent, reversedItem1.result.metrics.maxDrawdownPercent, 0.001)
    }

    // ---------------------------------------------------------------------------------------------
    // 8. Identical Candle-Data Enforcement
    // ---------------------------------------------------------------------------------------------
    @Test
    fun testIdenticalCandleDataEnforcementAcrossStrategies() {
        val strats = listOf(
            StrategyDefinition.PRESETS.first { it.id == "preset_smc_structure_shift" },
            StrategyDefinition.PRESETS.first { it.id == "preset_orb_standard" },
            StrategyDefinition.PRESETS.first { it.id == "preset_ema_cross" }
        )

        val comparison = StrategyComparisonEngine.runComparison(
            strategies = strats,
            candles = sampleCandles,
            asset = asset,
            regime = regime,
            timeframe = tf,
            risk = risk
        )

        assertTrue(comparison.validation.isValid)
        assertEquals("Common candle count must match input candle count", sampleCandles.size, comparison.commonCandlesCount)

        comparison.items.forEach { item ->
            assertEquals(sampleCandles.size, item.result.candles.size)
            // Verify timestamp alignment for every single bar
            for (i in sampleCandles.indices) {
                assertEquals(sampleCandles[i].timestamp, item.result.candles[i].timestamp)
                assertEquals(sampleCandles[i].close, item.result.candles[i].close, 0.0001)
            }
        }
    }
}
