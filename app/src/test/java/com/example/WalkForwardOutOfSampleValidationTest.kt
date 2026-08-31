package com.example

import com.example.tradestrat.data.MarketDataValidator
import com.example.tradestrat.engine.BacktestEngine
import com.example.tradestrat.model.*
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.io.InputStreamReader
import kotlin.math.abs
import kotlin.math.max

/**
 * ROBUSTNESS / OUT-OF-SAMPLE WALK-FORWARD VALIDATION TEST
 * 
 * Validates the EMA 9 / SMA 21 Dynamic Trend strategy across chronological
 * Out-of-Sample (OOS) windows without modifying parameters or curve-fitting.
 * 
 * Walk-Forward Windows:
 *  - Window 1: Train 2020 -> Test 2021 (OOS 1: Historic Bull Peak)
 *  - Window 2: Train 2020-2021 -> Test 2022 (OOS 2: Severe Bear Market)
 *  - Window 3: Train 2020-2022 -> Test 2023 (OOS 3: Sideways Chop & Recovery)
 *  - Window 4: Train 2020-2023 -> Test 2024 (OOS 4: Spot ETF Expansion Rally)
 */
class WalkForwardOutOfSampleValidationTest {

    companion object {
        lateinit var full5YearCandles: List<Candle>
        val btcAsset = MarketAsset("BTC_USD", "BTC/USD", "Bitcoin", AssetCategory.CRYPTO, 65000.0, "Crypto")

        // Exact Unmodified Production Strategy: EMA 9 / SMA 21
        val productionStrategy = StrategyDefinition(
            id = "prod_ema_sma_trend",
            name = "EMA 9 / SMA 21 Dynamic Trend",
            description = "Unmodified trend following system for out-of-sample validation",
            strategyType = StrategyType.MA_CROSSOVER,
            indicatorConfig = IndicatorConfig(
                maParams = MovingAverageParams(fastPeriod = 9, slowPeriod = 21, useEma = true)
            )
        )

        // Exact Unmodified Risk Model
        val standardRisk = RiskParameters(
            initialCapital = 10000.0,
            positionSizingMode = PositionSizingMode.FIXED_DOLLAR,
            positionSizeValue = 2000.0, // Fixed $2,000 margin
            leverage = 2.0,             // $4,000 position notional
            stopLossType = StopLossType.PERCENTAGE,
            stopLossValue = 5.0,        // 5% Stop Loss
            takeProfitType = TakeProfitType.PERCENTAGE,
            takeProfitValue = 10.0,     // 10% Take Profit
            slippageBps = 5.0,          // 5 bps adverse slippage
            commissionBps = 10.0,       // 10 bps exchange commission
            executionModel = ExecutionModel.REALISTIC,
            intrabarExecution = IntrabarExecutionAssumption.PESSIMISTIC_STOP_FIRST
        )

        @JvmStatic
        @BeforeClass
        fun loadDataset() {
            val resourceStream = WalkForwardOutOfSampleValidationTest::class.java.classLoader?.getResourceAsStream("data/btc_daily_5yr.csv")
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

    data class WindowPerformance(
        val windowName: String,
        val totalTrades: Int,
        val winRate: Double,
        val netPnl: Double,
        val profitFactor: Double,
        val expectancy: Double,
        val maxDrawdown: Double,
        val avgTradePnl: Double,
        val grossProfit: Double,
        val grossLoss: Double,
        val trades: List<Trade>
    )

    private fun runWindowTest(name: String, candles: List<Candle>): WindowPerformance {
        val result = BacktestEngine.runBacktest(
            candles = candles,
            asset = btcAsset,
            regime = MarketRegime.HISTORICAL_REALISTIC,
            timeframe = Timeframe.D1,
            strategy = productionStrategy,
            risk = standardRisk
        )

        val trades = result.trades
        val winningTrades = trades.filter { it.pnlDollars > 0 }
        val losingTrades = trades.filter { it.pnlDollars < 0 }

        val totalTrades = trades.size
        val winRate = if (totalTrades > 0) (winningTrades.size.toDouble() / totalTrades) * 100.0 else 0.0
        val grossProfit = winningTrades.sumOf { it.pnlDollars + it.feesPaid }
        val grossLoss = losingTrades.sumOf { abs(it.pnlDollars + it.feesPaid) }
        val netPnl = trades.sumOf { it.pnlDollars }
        val profitFactor = if (grossLoss > 0) grossProfit / grossLoss else if (grossProfit > 0) 99.9 else 0.0
        val expectancy = if (totalTrades > 0) netPnl / totalTrades else 0.0
        val maxDrawdown = result.metrics.maxDrawdownPercent
        val avgTrade = if (totalTrades > 0) netPnl / totalTrades else 0.0

        return WindowPerformance(
            windowName = name,
            totalTrades = totalTrades,
            winRate = winRate,
            netPnl = netPnl,
            profitFactor = profitFactor,
            expectancy = expectancy,
            maxDrawdown = maxDrawdown,
            avgTradePnl = avgTrade,
            grossProfit = grossProfit,
            grossLoss = grossLoss,
            trades = trades
        )
    }

    /**
     * TEST 1: Chronological Walk-Forward Windows (Anchored In-Sample vs Sequential Out-of-Sample)
     */
    @Test
    fun testChronologicalWalkForwardWindows() {
        // Dataset Timestamps
        // 2020: 1577836800000L to 1609459199000L
        // 2021: 1609459200000L to 1640995199000L
        // 2022: 1640995200000L to 1672531199000L
        // 2023: 1672531200000L to 1704067199000L
        // 2024: 1704067200000L to 1735689600000L

        val candles2020 = full5YearCandles.filter { it.timestamp in 1577836800000L..1609459199000L }
        val candles2021 = full5YearCandles.filter { it.timestamp in 1609459200000L..1640995199000L }
        val candles2022 = full5YearCandles.filter { it.timestamp in 1640995200000L..1672531199000L }
        val candles2023 = full5YearCandles.filter { it.timestamp in 1672531200000L..1704067199000L }
        val candles2024 = full5YearCandles.filter { it.timestamp in 1704067200000L..1735689600000L }

        // Window 1: Train 2020 -> Test 2021 (OOS 1)
        val oos1 = runWindowTest("OOS Period 1 (2021 - Bull Market Peak)", candles2021)
        
        // Window 2: Train 2020-2021 -> Test 2022 (OOS 2)
        val oos2 = runWindowTest("OOS Period 2 (2022 - Severe Bear Market)", candles2022)
        
        // Window 3: Train 2020-2022 -> Test 2023 (OOS 3)
        val oos3 = runWindowTest("OOS Period 3 (2023 - Range / Recovery)", candles2023)
        
        // Window 4: Train 2020-2023 -> Test 2024 (OOS 4)
        val oos4 = runWindowTest("OOS Period 4 (2024 - ETF Expansion Rally)", candles2024)

        val oosWindows = listOf(oos1, oos2, oos3, oos4)

        // Combined Out-of-Sample Metrics (All OOS trades 2021-2024)
        val allOosTrades = oosWindows.flatMap { it.trades }
        val totalOosTrades = allOosTrades.size
        val oosWins = allOosTrades.filter { it.pnlDollars > 0 }
        val oosLosses = allOosTrades.filter { it.pnlDollars < 0 }
        val oosWinRate = (oosWins.size.toDouble() / totalOosTrades) * 100.0
        val oosGrossProfit = oosWins.sumOf { it.pnlDollars + it.feesPaid }
        val oosGrossLoss = oosLosses.sumOf { abs(it.pnlDollars + it.feesPaid) }
        val oosNetPnl = allOosTrades.sumOf { it.pnlDollars }
        val oosProfitFactor = if (oosGrossLoss > 0) oosGrossProfit / oosGrossLoss else 0.0
        val oosExpectancy = oosNetPnl / totalOosTrades
        val oosAvgTrade = oosNetPnl / totalOosTrades
        val maxSingleOosDd = oosWindows.maxOf { it.maxDrawdown }

        println("================ WALK-FORWARD OUT-OF-SAMPLE (OOS) VALIDATION REPORT ================")
        println("Strategy: EMA 9 / SMA 21 Dynamic Trend | Sizing: Fixed $2,000 Margin (2x Leverage)")
        println("Zero Parameter Tuning on Test Data (True Out-Of-Sample Execution)")
        println("------------------------------------------------------------------------------------")
        for (w in oosWindows) {
            println(String.format("%-42s | Trades: %2d | WinRate: %5.1f%% | Net P&L: $%8.2f | PF: %5.2f | Expectancy: $%6.2f | MaxDD: %5.2f%% | AvgTrade: $%6.2f",
                w.windowName, w.totalTrades, w.winRate, w.netPnl, w.profitFactor, w.expectancy, w.maxDrawdown, w.avgTradePnl
            ))
        }
        println("------------------------------------------------------------------------------------")
        println("COMBINED OUT-OF-SAMPLE (2021-2024):")
        println(String.format("Total OOS Trades: %d", totalOosTrades))
        println(String.format("Combined OOS Win Rate: %.2f%% (%d wins / %d losses)", oosWinRate, oosWins.size, oosLosses.size))
        println(String.format("Combined OOS Gross Profit: $%.2f", oosGrossProfit))
        println(String.format("Combined OOS Gross Loss: $%.2f", oosGrossLoss))
        println(String.format("Combined OOS Net P&L: $%.2f", oosNetPnl))
        println(String.format("Combined OOS Profit Factor: %.3f", oosProfitFactor))
        println(String.format("Combined OOS Expectancy per Trade: $%.2f", oosExpectancy))
        println(String.format("Max Window Drawdown: %.2f%%", maxSingleOosDd))
        println("====================================================================================")

        assertTrue("Each OOS window must execute trades", oosWindows.all { it.totalTrades > 0 })
        assertTrue("Combined OOS trades must exceed 40 trades", totalOosTrades >= 40)
    }

    /**
     * TEST 2: 50/50 In-Sample vs Out-of-Sample Split Comparison
     */
    @Test
    fun testInSampleVsOutOfSampleSplitComparison() {
        // Split 50/50 by date:
        // In-Sample (IS): 2020-01-01 to 2022-06-30 (first 2.5 years, 912 bars)
        // Out-Of-Sample (OOS): 2022-07-01 to 2024-12-31 (second 2.5 years, 916 bars)
        val midpointTimestamp = 1656633600000L // 2022-07-01 00:00:00 UTC

        val inSampleCandles = full5YearCandles.filter { it.timestamp < midpointTimestamp }
        val outOfSampleCandles = full5YearCandles.filter { it.timestamp >= midpointTimestamp }

        val isPerf = runWindowTest("In-Sample (Train: 2020 - mid 2022)", inSampleCandles)
        val oosPerf = runWindowTest("Out-of-Sample (Test: mid 2022 - 2024)", outOfSampleCandles)

        println("================ IN-SAMPLE VS OUT-OF-SAMPLE COMPARISON ================")
        println(String.format("In-Sample (2020 - mid 2022)   : Trades: %2d | WinRate: %5.1f%% | Net P&L: $%8.2f | PF: %5.2f | MaxDD: %5.2f%% | Expectancy: $%6.2f",
            isPerf.totalTrades, isPerf.winRate, isPerf.netPnl, isPerf.profitFactor, isPerf.maxDrawdown, isPerf.expectancy
        ))
        println(String.format("Out-of-Sample (mid 2022 - 2024): Trades: %2d | WinRate: %5.1f%% | Net P&L: $%8.2f | PF: %5.2f | MaxDD: %5.2f%% | Expectancy: $%6.2f",
            oosPerf.totalTrades, oosPerf.winRate, oosPerf.netPnl, oosPerf.profitFactor, oosPerf.maxDrawdown, oosPerf.expectancy
        ))
        println("=======================================================================")

        assertTrue("In-sample candle count valid", inSampleCandles.size >= 800)
        assertTrue("Out-of-sample candle count valid", outOfSampleCandles.size >= 800)
    }
}
