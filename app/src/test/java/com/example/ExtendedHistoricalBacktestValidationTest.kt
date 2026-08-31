package com.example

import com.example.tradestrat.data.MarketDataValidator
import com.example.tradestrat.engine.BacktestEngine
import com.example.tradestrat.engine.CandleResampler
import com.example.tradestrat.engine.IndicatorCalculators
import com.example.tradestrat.model.*
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.io.InputStreamReader
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * EXTENDED HISTORICAL BACKTEST VALIDATION SUITE (5-YEAR DATASET)
 * 
 * Validates BacktestEngine, Risk Accounting, and Indicator Pipelines
 * over 5 full years (2020-01-01 to 2025-01-01, 1,828 daily candles) of real BTC/USDT market data
 * spanning multiple distinct market regimes:
 *  - 2020 High Volatility & Covid Recovery
 *  - 2020-2021 Historic Bull Run
 *  - 2022 Severe Crypto Winter / Bear Market
 *  - 2023 Sideways Chop & Range Consolidation
 *  - 2024 Spot ETF Inflows & Bull Market Expansion
 */
class ExtendedHistoricalBacktestValidationTest {

    companion object {
        lateinit var full5YearCandles: List<Candle>
        val btcAsset = MarketAsset("BTC_USD", "BTC/USD", "Bitcoin", AssetCategory.CRYPTO, 65000.0, "Crypto")

        // Production Strategy: EMA 9 / SMA 21 Trend Crossover
        val productionStrategy = StrategyDefinition(
            id = "prod_ema_sma_trend",
            name = "EMA 9 / SMA 21 Dynamic Trend",
            description = "Standard dynamic trend following system without curve-fitting or optimization",
            strategyType = StrategyType.MA_CROSSOVER,
            indicatorConfig = IndicatorConfig(
                maParams = MovingAverageParams(fastPeriod = 9, slowPeriod = 21, useEma = true)
            )
        )

        // Realistic Production Risk Model
        val standardRisk = RiskParameters(
            initialCapital = 10000.0,
            positionSizingMode = PositionSizingMode.FIXED_DOLLAR,
            positionSizeValue = 2000.0, // Fixed $2,000 margin allocation
            leverage = 2.0,             // $4,000 position value per trade
            stopLossType = StopLossType.PERCENTAGE,
            stopLossValue = 5.0,        // 5% Stop Loss
            takeProfitType = TakeProfitType.PERCENTAGE,
            takeProfitValue = 10.0,     // 10% Take Profit
            slippageBps = 5.0,          // 5 bps adverse slippage on entries and exits
            commissionBps = 10.0,       // 10 bps exchange commission on entry and exit
            executionModel = ExecutionModel.REALISTIC,
            intrabarExecution = IntrabarExecutionAssumption.PESSIMISTIC_STOP_FIRST
        )

        @JvmStatic
        @BeforeClass
        fun loadDataset() {
            val resourceStream = ExtendedHistoricalBacktestValidationTest::class.java.classLoader?.getResourceAsStream("data/btc_daily_5yr.csv")
            val rawCsv = if (resourceStream != null) {
                InputStreamReader(resourceStream).readText()
            } else {
                val directFile = File("src/test/resources/data/btc_daily_5yr.csv")
                if (directFile.exists()) directFile.readText() else File("/app/src/test/resources/data/btc_daily_5yr.csv").readText()
            }

            val list = mutableListOf<Candle>()
            val lines = rawCsv.lines().drop(1).filter { it.isNotBlank() }
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
            full5YearCandles = list
        }
    }

    /**
     * TEST 1: Full 5-Year Dataset Ingestion and Strict Data Sanitization Validation
     */
    @Test
    fun testFiveYearDatasetSanitizationAndInvariantCheck() {
        assertTrue("Dataset must contain at least 1,800 daily candles (5 full years)", full5YearCandles.size >= 1800)

        val startTime = full5YearCandles.first().timestamp
        val endTime = full5YearCandles.last().timestamp

        val (cleanCandles, report) = MarketDataValidator.validateAndClean(
            rawCandles = full5YearCandles,
            timeframe = Timeframe.D1,
            expectedStartTimeMs = startTime,
            expectedEndTimeMs = endTime
        )

        assertTrue("Validation must pass with valid status", report.isValid)
        assertEquals("Zero duplicate timestamps across 5 years", 0, report.duplicatesRemovedCount)
        assertEquals("Zero unexpected gaps in continuous daily data", 0, report.unexpectedGapsCount)
        assertEquals(full5YearCandles.size, cleanCandles.size)

        // Validate every single candle for physical market invariants
        for (i in cleanCandles.indices) {
            val c = cleanCandles[i]
            assertTrue("Timestamp must be positive at bar $i", c.timestamp > 0)
            assertTrue("Open must be positive at bar $i", c.open > 0)
            assertTrue("High must be positive at bar $i", c.high > 0)
            assertTrue("Low must be positive at bar $i", c.low > 0)
            assertTrue("Close must be positive at bar $i", c.close > 0)
            assertTrue("High must be >= Low at bar $i (${c.high} vs ${c.low})", c.high >= c.low)
            assertTrue("High must be >= Open at bar $i (${c.high} vs ${c.open})", c.high >= c.open)
            assertTrue("High must be >= Close at bar $i (${c.high} vs ${c.close})", c.high >= c.close)
            assertTrue("Low must be <= Open at bar $i (${c.low} vs ${c.open})", c.low <= c.open)
            assertTrue("Low must be <= Close at bar $i (${c.low} vs ${c.close})", c.low <= c.close)
            if (i > 0) {
                assertTrue("Timestamps must be strictly ascending at bar $i", c.timestamp > cleanCandles[i - 1].timestamp)
            }
        }
    }

    /**
     * TEST 2: Complete 5-Year Historical Replay with Comprehensive Metric Calculations
     */
    @Test
    fun testFiveYearHistoricalReplayAndPerformanceMetrics() {
        val result = BacktestEngine.runBacktest(
            candles = full5YearCandles,
            asset = btcAsset,
            regime = MarketRegime.HISTORICAL_REALISTIC,
            timeframe = Timeframe.D1,
            strategy = productionStrategy,
            risk = standardRisk
        )

        assertEquals("Equity curve must record every single bar across 5 years", full5YearCandles.size, result.equityCurve.size)
        assertTrue("Multi-year backtest must produce realistic trade frequency", result.trades.size >= 25)

        // Calculate all required metrics independently
        val totalTrades = result.trades.size
        val winningTrades = result.trades.filter { it.pnlDollars > 0 }
        val losingTrades = result.trades.filter { it.pnlDollars < 0 }

        val winRate = (winningTrades.size.toDouble() / totalTrades) * 100.0
        val grossProfit = winningTrades.sumOf { it.pnlDollars + it.feesPaid }
        val grossLoss = losingTrades.sumOf { abs(it.pnlDollars + it.feesPaid) }
        val totalFees = result.trades.sumOf { it.feesPaid }
        val netPnl = result.trades.sumOf { it.pnlDollars }
        val profitFactor = if (grossLoss > 0) grossProfit / grossLoss else Double.POSITIVE_INFINITY
        val expectancy = netPnl / totalTrades
        val avgWin = if (winningTrades.isNotEmpty()) winningTrades.sumOf { it.pnlDollars } / winningTrades.size else 0.0
        val avgLoss = if (losingTrades.isNotEmpty()) abs(losingTrades.sumOf { it.pnlDollars }) / losingTrades.size else 0.0
        val largestWin = winningTrades.maxOfOrNull { it.pnlDollars } ?: 0.0
        val largestLoss = losingTrades.minOfOrNull { it.pnlDollars } ?: 0.0
        val avgDurationBars = result.trades.map { it.holdingBars }.average()

        // Consecutive wins / losses calculation
        var maxConsecutiveWins = 0
        var maxConsecutiveLosses = 0
        var currentWins = 0
        var currentLosses = 0
        for (trade in result.trades) {
            if (trade.pnlDollars > 0) {
                currentWins++
                currentLosses = 0
                maxConsecutiveWins = max(maxConsecutiveWins, currentWins)
            } else if (trade.pnlDollars < 0) {
                currentLosses++
                currentWins = 0
                maxConsecutiveLosses = max(maxConsecutiveLosses, currentLosses)
            }
        }

        val maxDd = result.metrics.maxDrawdownPercent

        println("================ 5-YEAR HISTORICAL BACKTEST PERFORMANCE REPORT ================")
        println("Dataset Period: 2020-01-01 to 2025-01-01 (1,828 Daily Candles)")
        println("Asset: BTC/USDT | Strategy: EMA 9 / SMA 21 Dynamic Trend | Sizing: Fixed $2,000 Margin (2x)")
        println("--------------------------------------------------------------------------------")
        println(String.format("Total Trades: %d", totalTrades))
        println(String.format("Win Rate: %.2f%% (%d wins / %d losses)", winRate, winningTrades.size, losingTrades.size))
        println(String.format("Gross Profit: $%.2f", grossProfit))
        println(String.format("Gross Loss: $%.2f", grossLoss))
        println(String.format("Total Commission & Slippage Fees: $%.2f", totalFees))
        println(String.format("Net P&L: $%.2f (ROI: %.2f%%)", netPnl, (netPnl / standardRisk.initialCapital) * 100.0))
        println(String.format("Profit Factor: %.3f", profitFactor))
        println(String.format("Expectancy per Trade: $%.2f", expectancy))
        println(String.format("Maximum Drawdown: %.2f%%", maxDd))
        println(String.format("Average Win: $%.2f", avgWin))
        println(String.format("Average Loss: $%.2f", avgLoss))
        println(String.format("Largest Win: $%.2f", largestWin))
        println(String.format("Largest Loss: $%.2f", largestLoss))
        println(String.format("Average Trade Duration: %.1f bars (days)", avgDurationBars))
        println(String.format("Max Consecutive Wins: %d | Max Consecutive Losses: %d", maxConsecutiveWins, maxConsecutiveLosses))
        println("================================================================================")
        assertTrue("Max drawdown must be >= 0%", maxDd >= 0.0)
        assertTrue("Average duration must be >= 1 bar", avgDurationBars >= 1.0)
        assertTrue("Max consecutive wins >= 0", maxConsecutiveWins >= 0)
        assertTrue("Max consecutive losses >= 0", maxConsecutiveLosses >= 0)

        // Bar-by-bar Mark-to-Market accounting verification
        for (i in result.equityCurve.indices) {
            val point = result.equityCurve[i]
            assertTrue("Equity must be positive at bar $i", point.equity > 0.0)
            assertTrue("Cash must be positive at bar $i", point.cash >= 0.0)
            assertTrue("Drawdown must be in [0%, 100%]", point.drawdownPct in 0.0..100.0)
        }

        // Verify that trade PnL reconciles with gross PnL - fees
        for (trade in result.trades) {
            val expectedGross = if (trade.direction == TradeDirection.LONG) {
                (trade.exitPrice - trade.entryPrice) * trade.quantity
            } else {
                (trade.entryPrice - trade.exitPrice) * trade.quantity
            }
            assertEquals("Net PnL = Gross PnL - Fees", trade.pnlDollars, expectedGross - trade.feesPaid, 0.01)
        }
    }

    /**
     * TEST 3: Multi-Year Deterministic Invariance (Bit-for-Bit Identity across 5 Years)
     */
    @Test
    fun testFiveYearDeterministicReplayInvariance() {
        val run1 = BacktestEngine.runBacktest(
            candles = full5YearCandles,
            asset = btcAsset,
            regime = MarketRegime.HISTORICAL_REALISTIC,
            timeframe = Timeframe.D1,
            strategy = productionStrategy,
            risk = standardRisk
        )

        val run2 = BacktestEngine.runBacktest(
            candles = full5YearCandles,
            asset = btcAsset,
            regime = MarketRegime.HISTORICAL_REALISTIC,
            timeframe = Timeframe.D1,
            strategy = productionStrategy,
            risk = standardRisk
        )

        assertEquals("Trade count must be strictly identical", run1.trades.size, run2.trades.size)
        assertEquals("Final equity must be strictly identical", run1.equityCurve.last().equity, run2.equityCurve.last().equity, 0.000001)
        assertEquals("Final cash must be strictly identical", run1.equityCurve.last().cash, run2.equityCurve.last().cash, 0.000001)
        assertEquals("Max drawdown must be strictly identical", run1.metrics.maxDrawdownPercent, run2.metrics.maxDrawdownPercent, 0.000001)
        assertEquals("Win rate must be strictly identical", run1.metrics.winRatePercent, run2.metrics.winRatePercent, 0.000001)

        for (i in run1.trades.indices) {
            val t1 = run1.trades[i]
            val t2 = run2.trades[i]
            assertEquals("Trade $i entry price identical", t1.entryPrice, t2.entryPrice, 0.000001)
            assertEquals("Trade $i exit price identical", t1.exitPrice, t2.exitPrice, 0.000001)
            assertEquals("Trade $i quantity identical", t1.quantity, t2.quantity, 0.000001)
            assertEquals("Trade $i pnlDollars identical", t1.pnlDollars, t2.pnlDollars, 0.000001)
            assertEquals("Trade $i feesPaid identical", t1.feesPaid, t2.feesPaid, 0.000001)
            assertEquals("Trade $i exitReason identical", t1.exitReason, t2.exitReason)
        }
    }

    /**
     * TEST 4: Chunked vs Full Replay Causal Equivalence Across 5-Year Timeline
     */
    @Test
    fun testFiveYearChunkedVsFullCausalEquivalence() {
        val fullEma = IndicatorCalculators.calculateEMA(full5YearCandles, 9)
        val fullSma = IndicatorCalculators.calculateSMA(full5YearCandles, 21)

        // Sample 20 progressive checkpoints across the 5-year timeline
        val step = full5YearCandles.size / 20
        for (k in step until full5YearCandles.size step step) {
            val prefix = full5YearCandles.take(k)
            val prefixEma = IndicatorCalculators.calculateEMA(prefix, 9)
            val prefixSma = IndicatorCalculators.calculateSMA(prefix, 21)

            val lastIdx = k - 1
            val fullEmaVal = fullEma[lastIdx]
            val prefixEmaVal = prefixEma.last()

            val fullSmaVal = fullSma[lastIdx]
            val prefixSmaVal = prefixSma.last()

            if (fullEmaVal != null && prefixEmaVal != null) {
                assertEquals("EMA at checkpoint bar $lastIdx must match", fullEmaVal, prefixEmaVal, 0.001)
            }
            if (fullSmaVal != null && prefixSmaVal != null) {
                assertEquals("SMA at checkpoint bar $lastIdx must match", fullSmaVal, prefixSmaVal, 0.001)
            }
        }
    }

    /**
     * TEST 5: Regime-Partitioned Multi-Year Performance Breakdown
     */
    @Test
    fun testRegimePartitionedReplay() {
        // Regime 1: 2020 Covid Crash & Recovery (2020-01-01 to 2020-09-30)
        val regime1 = full5YearCandles.filter { it.timestamp in 1577836800000L..1601424000000L }
        val res1 = BacktestEngine.runBacktest(regime1, btcAsset, MarketRegime.VOLATILE_CRASH_RECOVERY, Timeframe.D1, productionStrategy, standardRisk)

        // Regime 2: 2020-2021 Historic Bull Run (2020-10-01 to 2021-11-30)
        val regime2 = full5YearCandles.filter { it.timestamp in 1601510400000L..1638230400000L }
        val res2 = BacktestEngine.runBacktest(regime2, btcAsset, MarketRegime.STRONG_BULL, Timeframe.D1, productionStrategy, standardRisk)

        // Regime 3: 2022 Severe Bear Market (2021-12-01 to 2022-12-31)
        val regime3 = full5YearCandles.filter { it.timestamp in 1638316800000L..1672444800000L }
        val res3 = BacktestEngine.runBacktest(regime3, btcAsset, MarketRegime.BEAR_MARKET, Timeframe.D1, productionStrategy, standardRisk)

        // Regime 4: 2023 Sideways Chop (2023-01-01 to 2023-09-30)
        val regime4 = full5YearCandles.filter { it.timestamp in 1672531200000L..1696032000000L }
        val res4 = BacktestEngine.runBacktest(regime4, btcAsset, MarketRegime.CHOPPY_RANGE, Timeframe.D1, productionStrategy, standardRisk)

        // Regime 5: 2023-2024 ETF Expansion Rally (2023-10-01 to 2024-12-31)
        val regime5 = full5YearCandles.filter { it.timestamp in 1696118400000L..1735689600000L }
        val res5 = BacktestEngine.runBacktest(regime5, btcAsset, MarketRegime.STRONG_BULL, Timeframe.D1, productionStrategy, standardRisk)

        assertTrue("Regime 1 must have candles", res1.equityCurve.isNotEmpty())
        assertTrue("Regime 2 must have candles", res2.equityCurve.isNotEmpty())
        assertTrue("Regime 3 must have candles", res3.equityCurve.isNotEmpty())
        assertTrue("Regime 4 must have candles", res4.equityCurve.isNotEmpty())
        assertTrue("Regime 5 must have candles", res5.equityCurve.isNotEmpty())

        val regimes = listOf(
            "Regime 1: 2020 Covid Crash & Recovery" to res1,
            "Regime 2: 2020-2021 Historic Bull Run" to res2,
            "Regime 3: 2022 Severe Crypto Winter / Bear" to res3,
            "Regime 4: 2023 Sideways Chop & Range" to res4,
            "Regime 5: 2024 ETF Expansion Rally" to res5
        )

        println("================ REGIME-PARTITIONED PERFORMANCE BREAKDOWN ================")
        for ((name, res) in regimes) {
            val wins = res.trades.count { it.pnlDollars > 0 }
            val net = res.trades.sumOf { it.pnlDollars }
            val wr = if (res.trades.isNotEmpty()) (wins.toDouble() / res.trades.size) * 100.0 else 0.0
            println(String.format("%-42s | Trades: %2d | WinRate: %5.1f%% | Net P&L: $%8.2f | MaxDD: %5.2f%%",
                name, res.trades.size, wr, net, res.metrics.maxDrawdownPercent
            ))
        }
        println("==========================================================================")

        // Verify mark-to-market trade PnL reconciliation for each regime
        listOf(res1, res2, res3, res4, res5).forEach { res ->
            for (trade in res.trades) {
                val expectedGross = if (trade.direction == TradeDirection.LONG) {
                    (trade.exitPrice - trade.entryPrice) * trade.quantity
                } else {
                    (trade.entryPrice - trade.exitPrice) * trade.quantity
                }
                assertEquals("Trade net PnL matches gross PnL - fees", trade.pnlDollars, expectedGross - trade.feesPaid, 0.01)
            }
        }
    }
}
