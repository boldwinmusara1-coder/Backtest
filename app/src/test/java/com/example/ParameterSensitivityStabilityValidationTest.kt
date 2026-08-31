package com.example

import com.example.tradestrat.engine.BacktestEngine
import com.example.tradestrat.model.*
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.io.InputStreamReader
import kotlin.math.abs

/**
 * PARAMETER SENSITIVITY & STABILITY TEST
 * 
 * Evaluates the neighborhood grid around EMA 9 / SMA 21:
 *  - Fast EMA: 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15 (11 values)
 *  - Slow SMA: 18, 20, 21, 22, 24, 26, 28, 30 (8 values)
 *  Total combinations: 88
 * 
 * Strict Invariants:
 *  - Same 5-year historical dataset (1,828 daily BTC/USDT candles)
 *  - Same realistic execution (next-bar open)
 *  - Same $2,000 fixed margin (2x leverage -> $4,000 position notional)
 *  - Same 5% SL, 10% TP, 5 bps adverse slippage, 10 bps commission
 *  - Same pessimistic SL-first assumption
 */
class ParameterSensitivityStabilityValidationTest {

    companion object {
        lateinit var full5YearCandles: List<Candle>
        val btcAsset = MarketAsset("BTC_USD", "BTC/USD", "Bitcoin", AssetCategory.CRYPTO, 65000.0, "Crypto")

        val standardRisk = RiskParameters(
            initialCapital = 10000.0,
            positionSizingMode = PositionSizingMode.FIXED_DOLLAR,
            positionSizeValue = 2000.0,
            leverage = 2.0,
            stopLossType = StopLossType.PERCENTAGE,
            stopLossValue = 5.0,
            takeProfitType = TakeProfitType.PERCENTAGE,
            takeProfitValue = 10.0,
            slippageBps = 5.0,
            commissionBps = 10.0,
            executionModel = ExecutionModel.REALISTIC,
            intrabarExecution = IntrabarExecutionAssumption.PESSIMISTIC_STOP_FIRST
        )

        val fastEmaList = listOf(5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15)
        val slowSmaList = listOf(18, 20, 21, 22, 24, 26, 28, 30)

        @JvmStatic
        @BeforeClass
        fun loadDataset() {
            val resourceStream = ParameterSensitivityStabilityValidationTest::class.java.classLoader?.getResourceAsStream("data/btc_daily_5yr.csv")
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

    data class CellResult(
        val fast: Int,
        val slow: Int,
        val trades: Int,
        val winRate: Double,
        val netPnl: Double,
        val profitFactor: Double,
        val expectancy: Double,
        val maxDrawdown: Double
    )

    @Test
    fun testParameterGridSensitivityAndStability() {
        val results = mutableMapOf<Pair<Int, Int>, CellResult>()

        for (fast in fastEmaList) {
            for (slow in slowSmaList) {
                val strategy = StrategyDefinition(
                    id = "grid_${fast}_${slow}",
                    name = "EMA $fast / SMA $slow",
                    description = "Sensitivity grid test",
                    strategyType = StrategyType.MA_CROSSOVER,
                    indicatorConfig = IndicatorConfig(
                        maParams = MovingAverageParams(fastPeriod = fast, slowPeriod = slow, useEma = true)
                    )
                )

                val backtest = BacktestEngine.runBacktest(
                    candles = full5YearCandles,
                    asset = btcAsset,
                    regime = MarketRegime.HISTORICAL_REALISTIC,
                    timeframe = Timeframe.D1,
                    strategy = strategy,
                    risk = standardRisk
                )

                val trades = backtest.trades
                val wins = trades.filter { it.pnlDollars > 0 }
                val losses = trades.filter { it.pnlDollars < 0 }
                val winRate = if (trades.isNotEmpty()) (wins.size.toDouble() / trades.size) * 100.0 else 0.0
                val grossProfit = wins.sumOf { it.pnlDollars + it.feesPaid }
                val grossLoss = losses.sumOf { abs(it.pnlDollars + it.feesPaid) }
                val netPnl = trades.sumOf { it.pnlDollars }
                val profitFactor = if (grossLoss > 0) grossProfit / grossLoss else if (grossProfit > 0) 99.9 else 0.0
                val expectancy = if (trades.isNotEmpty()) netPnl / trades.size else 0.0
                val maxDd = backtest.metrics.maxDrawdownPercent

                val cell = CellResult(
                    fast = fast,
                    slow = slow,
                    trades = trades.size,
                    winRate = winRate,
                    netPnl = netPnl,
                    profitFactor = profitFactor,
                    expectancy = expectancy,
                    maxDrawdown = maxDd
                )
                results[Pair(fast, slow)] = cell
            }
        }

        assertEquals("Total grid cell count must equal 88", 88, results.size)

        val profitableCells = results.values.count { it.netPnl > 0 }
        val profitablePercent = (profitableCells.toDouble() / results.size) * 100.0

        // 1. Heatmap / Table of Profit Factor
        println("=========================================================================================")
        println("PARAMETER SENSITIVITY GRID: PROFIT FACTOR (PF)")
        println("Fast EMA (Rows) vs. Slow SMA (Columns)")
        println("=========================================================================================")
        print("Fast \\ Slow |")
        for (slow in slowSmaList) {
            print(String.format("  SMA %2d |", slow))
        }
        println()
        println("------------+" + "---------+".repeat(slowSmaList.size))
        for (fast in fastEmaList) {
            print(String.format(" EMA %2d     |", fast))
            for (slow in slowSmaList) {
                val cell = results[Pair(fast, slow)]!!
                val isBaseline = (fast == 9 && slow == 21)
                val marker = if (isBaseline) "*" else " "
                print(String.format("%s%6.2f%s |", marker, cell.profitFactor, if (isBaseline) "*" else " "))
            }
            println()
        }
        println("(* = Baseline Production EMA 9 / SMA 21)")
        println()

        // 2. Heatmap / Table of Net Realized P&L ($)
        println("=========================================================================================")
        println("PARAMETER SENSITIVITY GRID: NET REALIZED P&L ($)")
        println("Fast EMA (Rows) vs. Slow SMA (Columns)")
        println("=========================================================================================")
        print("Fast \\ Slow |")
        for (slow in slowSmaList) {
            print(String.format("   SMA %2d  |", slow))
        }
        println()
        println("------------+" + "-----------+".repeat(slowSmaList.size))
        for (fast in fastEmaList) {
            print(String.format(" EMA %2d     |", fast))
            for (slow in slowSmaList) {
                val cell = results[Pair(fast, slow)]!!
                val isBaseline = (fast == 9 && slow == 21)
                val marker = if (isBaseline) "*" else " "
                print(String.format("%s$%7.1f%s|", marker, cell.netPnl, if (isBaseline) "*" else " "))
            }
            println()
        }
        println("(* = Baseline Production EMA 9 / SMA 21)")
        println()

        // Detailed Baseline Cell
        val baseline = results[Pair(9, 21)]!!
        println("=========================================================================================")
        println("BASELINE PRODUCTION (EMA 9 / SMA 21) SUMMARY:")
        println(String.format("Trades: %d | Win Rate: %.2f%% | Net P&L: $%.2f | PF: %.3f | Expectancy: $%.2f | MaxDD: %.2f%%",
            baseline.trades, baseline.winRate, baseline.netPnl, baseline.profitFactor, baseline.expectancy, baseline.maxDrawdown
        ))
        println("=========================================================================================")

        // Neighbor evaluation: Fast EMA in [7..11], Slow SMA in [20..24]
        val neighbors = results.values.filter { it.fast in 7..11 && it.slow in 20..24 }
        val profitableNeighbors = neighbors.count { it.netPnl > 0 }
        val avgNeighborPf = neighbors.map { it.profitFactor }.average()
        val avgNeighborPnl = neighbors.map { it.netPnl }.average()

        println(String.format("Immediate Neighborhood (Fast 7-11, Slow 20-24, %d cells):", neighbors.size))
        println(String.format("  Profitable Cells: %d / %d (%.1f%%)", profitableNeighbors, neighbors.size, (profitableNeighbors.toDouble() / neighbors.size) * 100.0))
        println(String.format("  Average Neighbor Profit Factor: %.3f", avgNeighborPf))
        println(String.format("  Average Neighbor Net P&L: $%.2f", avgNeighborPnl))
        println()
        println(String.format("Global Grid Summary (%d total combinations):", results.size))
        println(String.format("  Total Profitable Combinations: %d / %d (%.1f%%)", profitableCells, results.size, profitablePercent))
        println("=========================================================================================")

        assertTrue("Baseline 9/21 must be profitable", baseline.netPnl > 0)
        assertTrue("Majority of immediate neighborhood must be profitable", profitableNeighbors >= neighbors.size * 0.75)
    }
}
