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
 * FINAL HOLDOUT VALIDATION TEST (Completely Unseen Post-December 2024 Data)
 * 
 * Strict Invariants:
 *  - Dataset: 2025-01-01 to 2026-08-18 (595 daily BTC/USDT candles, 100% unseen holdout)
 *  - Strategy: Frozen EMA 9 / SMA 21 Dynamic Trend
 *  - Risk & Sizing: Fixed $2,000 margin, 2x leverage ($4,000 position notional)
 *  - 5% Stop Loss, 10% Take Profit
 *  - 5 bps slippage, 10 bps commission
 *  - Next-bar-open execution (Realistic)
 *  - Pessimistic SL-first assumption
 *  - Zero parameter changes, zero optimization, zero regime filters
 */
class FinalHoldoutValidationTest {

    companion object {
        lateinit var holdoutCandles: List<Candle>
        val btcAsset = MarketAsset("BTC_USD", "BTC/USD", "Bitcoin", AssetCategory.CRYPTO, 65000.0, "Crypto")

        val frozenStrategy = StrategyDefinition(
            id = "frozen_ema_sma_trend",
            name = "EMA 9 / SMA 21 Dynamic Trend",
            description = "Frozen production strategy for final untouched holdout validation",
            strategyType = StrategyType.MA_CROSSOVER,
            indicatorConfig = IndicatorConfig(
                maParams = MovingAverageParams(fastPeriod = 9, slowPeriod = 21, useEma = true)
            )
        )

        val frozenRisk = RiskParameters(
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

        @JvmStatic
        @BeforeClass
        fun loadHoldoutDataset() {
            val resourceStream = FinalHoldoutValidationTest::class.java.classLoader?.getResourceAsStream("data/btc_daily_holdout_2025_2026.csv")
            val rawCsv = if (resourceStream != null) {
                InputStreamReader(resourceStream).readText()
            } else {
                val directFile = File("src/test/resources/data/btc_daily_holdout_2025_2026.csv")
                if (directFile.exists()) directFile.readText() else File("/app/src/test/resources/data/btc_daily_holdout_2025_2026.csv").readText()
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
            holdoutCandles = list
        }
    }

    @Test
    fun testFinalHoldoutValidation() {
        assertTrue("Holdout dataset must contain post-December 2024 candles", holdoutCandles.size >= 100)
        
        // Strict data sanitization & validation check
        val (cleanCandles, valReport) = MarketDataValidator.validateAndClean(
            rawCandles = holdoutCandles,
            timeframe = Timeframe.D1
        )
        assertTrue("Holdout data validation must be valid", valReport.isValid)

        // Execute Backtest on Holdout
        val result = BacktestEngine.runBacktest(
            candles = cleanCandles,
            asset = btcAsset,
            regime = MarketRegime.HISTORICAL_REALISTIC,
            timeframe = Timeframe.D1,
            strategy = frozenStrategy,
            risk = frozenRisk
        )

        val trades = result.trades
        val totalTrades = trades.size
        val winningTrades = trades.filter { it.pnlDollars > 0 }
        val losingTrades = trades.filter { it.pnlDollars < 0 }

        val winRate = if (totalTrades > 0) (winningTrades.size.toDouble() / totalTrades) * 100.0 else 0.0
        val grossProfit = winningTrades.sumOf { it.pnlDollars + it.feesPaid }
        val grossLoss = losingTrades.sumOf { abs(it.pnlDollars + it.feesPaid) }
        val totalFees = trades.sumOf { it.feesPaid }
        val netPnl = trades.sumOf { it.pnlDollars }
        val profitFactor = if (grossLoss > 0) grossProfit / grossLoss else if (grossProfit > 0) 99.9 else 0.0
        val expectancy = if (totalTrades > 0) netPnl / totalTrades else 0.0
        val avgWin = if (winningTrades.isNotEmpty()) winningTrades.sumOf { it.pnlDollars } / winningTrades.size else 0.0
        val avgLoss = if (losingTrades.isNotEmpty()) abs(losingTrades.sumOf { it.pnlDollars }) / losingTrades.size else 0.0
        val maxDrawdown = result.metrics.maxDrawdownPercent

        var maxConsecutiveLosses = 0
        var currentLosses = 0
        for (trade in trades) {
            if (trade.pnlDollars < 0) {
                currentLosses++
                maxConsecutiveLosses = max(maxConsecutiveLosses, currentLosses)
            } else if (trade.pnlDollars > 0) {
                currentLosses = 0
            }
        }

        val endingEquity = frozenRisk.initialCapital + netPnl
        val accountRoi = (netPnl / frozenRisk.initialCapital) * 100.0

        println("================ FINAL HOLDOUT TEST (POST-DEC 2024 UNSEEN DATA) ================")
        println(String.format("Holdout Period: %s to %s (%d daily candles)", 
            cleanCandles.first().formattedDate(1440), cleanCandles.last().formattedDate(1440), cleanCandles.size))
        println("Frozen Strategy: EMA 9 / SMA 21 | Sizing: Fixed $2,000 Margin (2x Leverage)")
        println("--------------------------------------------------------------------------------")
        println(String.format("Total Holdout Trades: %d", totalTrades))
        println(String.format("Win Rate: %.2f%% (%d wins / %d losses)", winRate, winningTrades.size, losingTrades.size))
        println(String.format("Gross Profit: $%.2f", grossProfit))
        println(String.format("Gross Loss: $%.2f", grossLoss))
        println(String.format("Total Fees / Slippage: $%.2f", totalFees))
        println(String.format("Net Realized P&L: $%.2f", netPnl))
        println(String.format("Profit Factor: %.3f", profitFactor))
        println(String.format("Expectancy per Trade: $%.2f", expectancy))
        println(String.format("Average Winning Trade: $%.2f", avgWin))
        println(String.format("Average Losing Trade: $%.2f", avgLoss))
        println(String.format("Maximum Drawdown: %.2f%%", maxDrawdown))
        println(String.format("Maximum Consecutive Losses: %d", maxConsecutiveLosses))
        println(String.format("Ending Equity: $%.2f", endingEquity))
        println(String.format("Account ROI: %.2f%%", accountRoi))
        println("--------------------------------------------------------------------------------")

        // Previous OOS Baseline (2021-2024 Walk-Forward):
        val baselineOosTrades = 52
        val baselineOosWinRate = 46.15
        val baselineOosNetPnl = 3346.46
        val baselineOosPf = 1.739
        val baselineOosExpectancy = 64.35
        val baselineOosMaxDd = 11.20

        // Comparison Calculations
        val winRateDiff = winRate - baselineOosWinRate
        val winRateDegradationPct = ((winRate - baselineOosWinRate) / baselineOosWinRate) * 100.0
        val pfDegradationPct = ((profitFactor - baselineOosPf) / baselineOosPf) * 100.0
        val expDegradationPct = ((expectancy - baselineOosExpectancy) / baselineOosExpectancy) * 100.0
        val ddDegradationPct = ((maxDrawdown - baselineOosMaxDd) / baselineOosMaxDd) * 100.0

        println("COMPARISON: FINAL HOLDOUT vs. PREVIOUS OOS BASELINE:")
        println(String.format("Win Rate      : Holdout %5.2f%% vs Baseline %5.2f%% -> Shift: %+6.2f%% (%+6.2f%% relative)", winRate, baselineOosWinRate, winRateDiff, winRateDegradationPct))
        println(String.format("Profit Factor : Holdout %5.3f  vs Baseline %5.3f  -> Shift: %+6.2f%% relative", profitFactor, baselineOosPf, pfDegradationPct))
        println(String.format("Expectancy    : Holdout $%5.2f vs Baseline $%5.2f -> Shift: %+6.2f%% relative", expectancy, baselineOosExpectancy, expDegradationPct))
        println(String.format("Max Drawdown  : Holdout %5.2f%% vs Baseline %5.2f%% -> Shift: %+6.2f%% relative", maxDrawdown, baselineOosMaxDd, ddDegradationPct))
        println("================================================================================")

        assertTrue("Holdout trades must execute", totalTrades > 0)
    }
}
