package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.tradestrat.data.*
import com.example.tradestrat.engine.*
import com.example.tradestrat.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase8OrbFocusedOptimizationTest {

    private lateinit var context: Context
    private lateinit var btcAsset: MarketAsset
    private val baselineOrbStrategy = StrategyDefinition.PRESETS.first { it.id == "preset_orb_breakout" }

    private val standardRisk = RiskParameters(
        initialCapital = 10000.0,
        positionSizingMode = PositionSizingMode.PERCENT_EQUITY,
        positionSizeValue = 25.0,
        leverage = 1.0,
        stopLossType = StopLossType.PERCENTAGE,
        stopLossValue = 3.0,
        takeProfitType = TakeProfitType.RISK_REWARD_RATIO,
        takeProfitValue = 2.0,
        slippageBps = 5.0,
        commissionBps = 10.0,
        allowShorting = true,
        executionModel = ExecutionModel.REALISTIC,
        intrabarExecution = IntrabarExecutionAssumption.PESSIMISTIC_STOP_FIRST
    )

    private val baseStartTime = 1704067200000L // 2024-01-01 00:00:00 UTC
    private val total5mBars = 52416           // 182 days * 288 bars/day
    private val df = DecimalFormat("#,##0.00")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        btcAsset = MarketDataProvider.ASSETS.first { it.symbol == "BTC/USDT" }
    }

    private fun generate5mCandles(): List<Candle> {
        val candles5m = ArrayList<Candle>(total5mBars)
        var currentPrice = 42200.0
        var curTime = baseStartTime
        val stepMs = 5 * 60 * 1000L
        val random = Random(20240630L)

        for (i in 0 until total5mBars) {
            val progress = i.toDouble() / total5mBars.toDouble()
            val macroDrift = when {
                progress < 0.15 -> 0.00018
                progress < 0.38 -> 0.00032
                progress < 0.55 -> -0.00018
                progress < 0.72 -> 0.00010
                progress < 0.88 -> -0.00015
                else -> 0.00002
            }

            val cycleFast = kotlin.math.sin(i / 40.0) * 0.0018
            val cycleSlow = kotlin.math.cos(i / 180.0) * 0.0012
            val noise = (random.nextDouble() - 0.4985) * 0.0038
            val drift = macroDrift + cycleFast + cycleSlow + noise
            val volScale = if (progress in 0.20..0.45 || progress in 0.75..0.88) 0.0055 else 0.0032
            val volatility = currentPrice * volScale

            val open = currentPrice
            val close = max(1000.0, open * (1.0 + drift))
            val high = max(open, close) + random.nextDouble() * volatility
            val low = min(open, close) - random.nextDouble() * volatility
            val volume = 30.0 + random.nextDouble() * 350.0

            candles5m.add(
                Candle(
                    timestamp = curTime,
                    open = (open * 100.0).toLong() / 100.0,
                    high = (high * 100.0).toLong() / 100.0,
                    low = (low * 100.0).toLong() / 100.0,
                    close = (close * 100.0).toLong() / 100.0,
                    volume = (volume * 100.0).toLong() / 100.0
                )
            )

            currentPrice = close
            curTime += stepMs
        }

        val (clean5m, _) = MarketDataValidator.validateAndClean(candles5m, Timeframe.M5)
        return clean5m
    }

    private fun evaluateRun(name: String, strat: StrategyDefinition, risk: RiskParameters, candles: List<Candle>): BacktestResult {
        val res = BacktestEngine.runBacktest(candles, btcAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.M5, strat, risk)
        val m = res.metrics
        val trades = res.trades

        val wins = trades.filter { it.isWin }
        val losses = trades.filter { !it.isWin }
        val avgWin = if (wins.isNotEmpty()) wins.map { it.pnlDollars }.average() else 0.0
        val avgLoss = if (losses.isNotEmpty()) losses.map { abs(it.pnlDollars) }.average() else 0.0

        // Calculate monthly profitable count
        val barsPerMonth = candles.size / 6
        var profitableMonths = 0
        for (month in 0 until 6) {
            val mCandles = candles.subList(month * barsPerMonth, (month + 1) * barsPerMonth)
            val mRes = BacktestEngine.runBacktest(mCandles, btcAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.M5, strat, risk)
            if (mRes.metrics.netProfitDollars > 0) profitableMonths++
        }

        println("$name -> Trades: ${m.totalTrades}, WinRate: ${df.format(m.winRatePercent)}%, PF: ${df.format(m.profitFactor)}, Exp: $${df.format(m.expectancyDollars)}, NetPnL: $${df.format(m.netProfitDollars)}, ROI: ${df.format(m.netProfitPercent)}%, MaxDD: ${df.format(m.maxDrawdownPercent)}%, AvgWin: $${df.format(avgWin)}, AvgLoss: $${df.format(avgLoss)}, MaxConsLoss: ${m.maxConsecutiveLosses}, ProfMonths: $profitableMonths/6")
        return res
    }

    @Test
    fun testAllExperiments() {
        val candles = generate5mCandles()

        println("\n=== BASELINE AUDIT ===")
        evaluateRun("BASELINE", baselineOrbStrategy, standardRisk, candles)

        println("\n=== EXPERIMENT 1: STOP LOSS / TAKE PROFIT GRID ===")
        val sls = listOf(1.5, 2.0, 2.5, 3.0, 3.5)
        val tps = listOf(1.5, 1.75, 2.0, 2.25, 2.5, 3.0)
        for (sl in sls) {
            for (tp in tps) {
                val r = standardRisk.copy(stopLossValue = sl, takeProfitValue = tp)
                evaluateRun("SL ${sl}%, TP ${tp}R", baselineOrbStrategy, r, candles)
            }
        }

        println("\n=== EXPERIMENT 2: OPENING RANGE DURATION ===")
        val durations = listOf(15, 30, 45, 60)
        for (dur in durations) {
            val p = baselineOrbStrategy.indicatorConfig.orbParams.copy(openingRangeMinutes = dur)
            val s = baselineOrbStrategy.copy(indicatorConfig = baselineOrbStrategy.indicatorConfig.copy(orbParams = p))
            evaluateRun("OR Duration ${dur}m", s, standardRisk, candles)
        }

        println("\n=== EXPERIMENT 3: VOLUME FILTER ===")
        val vols = listOf(1.0, 1.1, 1.2, 1.3, 1.4, 1.5)
        for (v in vols) {
            val p = baselineOrbStrategy.indicatorConfig.orbParams.copy(volumeMultiplier = v)
            val s = baselineOrbStrategy.copy(indicatorConfig = baselineOrbStrategy.indicatorConfig.copy(orbParams = p))
            evaluateRun("Vol Multiplier ${v}x", s, standardRisk, candles)
        }

        println("\n=== EXPERIMENT 4: EMA FILTER ===")
        val noEmaP = baselineOrbStrategy.indicatorConfig.orbParams.copy(useEmaTrendFilter = false)
        val noEmaS = baselineOrbStrategy.copy(indicatorConfig = baselineOrbStrategy.indicatorConfig.copy(orbParams = noEmaP))
        evaluateRun("EMA OFF", noEmaS, standardRisk, candles)

        val emas = listOf(20, 30, 50, 75, 100)
        for (ema in emas) {
            val p = baselineOrbStrategy.indicatorConfig.orbParams.copy(useEmaTrendFilter = true, emaTrendPeriod = ema)
            val s = baselineOrbStrategy.copy(indicatorConfig = baselineOrbStrategy.indicatorConfig.copy(orbParams = p))
            evaluateRun("EMA $ema ON", s, standardRisk, candles)
        }

        println("\n=== EXPERIMENT 5: RSI FILTER ===")
        val noRsiP = baselineOrbStrategy.indicatorConfig.orbParams.copy(useRsiFilter = false)
        val noRsiS = baselineOrbStrategy.copy(indicatorConfig = baselineOrbStrategy.indicatorConfig.copy(orbParams = noRsiP))
        evaluateRun("RSI OFF", noRsiS, standardRisk, candles)

        val rsis = listOf(50.0, 52.0, 55.0)
        for (rThresh in rsis) {
            val p = baselineOrbStrategy.indicatorConfig.orbParams.copy(
                useRsiFilter = true,
                rsiLongThreshold = rThresh,
                rsiShortThreshold = (100.0 - rThresh)
            )
            val s = baselineOrbStrategy.copy(indicatorConfig = baselineOrbStrategy.indicatorConfig.copy(orbParams = p))
            evaluateRun("RSI Threshold $rThresh ON", s, standardRisk, candles)
        }

        println("\n=== EXPERIMENT 6: BREAKOUT STRENGTH BUFFER ===")
        val bufs = listOf(0.00, 0.05, 0.10, 0.15, 0.20, 0.30)
        for (b in bufs) {
            val p = baselineOrbStrategy.indicatorConfig.orbParams.copy(breakoutBufferPct = b)
            val s = baselineOrbStrategy.copy(indicatorConfig = baselineOrbStrategy.indicatorConfig.copy(orbParams = p))
            evaluateRun("Breakout Buffer ${b}%", s, standardRisk, candles)
        }

        println("\n=== EXPERIMENT 7: LONG VS SHORT ===")
        val rBoth = standardRisk.copy(allowShorting = true)
        val rLong = standardRisk.copy(allowShorting = false)
        evaluateRun("BOTH (Long + Short)", baselineOrbStrategy, rBoth, candles)
        evaluateRun("LONG ONLY", baselineOrbStrategy, rLong, candles)

        // Evaluate Candidate combinations
        println("\n=== COMBINATIONS & BEST CANDIDATES ===")
        // Optimized Candidate 1: SL 2.5%, TP 2.25R, 0.05% Buffer, Vol 1.2x, EMA 50, RSI 50
        val pOpt1 = baselineOrbStrategy.indicatorConfig.orbParams.copy(breakoutBufferPct = 0.05)
        val sOpt1 = baselineOrbStrategy.copy(indicatorConfig = baselineOrbStrategy.indicatorConfig.copy(orbParams = pOpt1))
        val rOpt1 = standardRisk.copy(stopLossValue = 2.5, takeProfitValue = 2.25)
        evaluateRun("OPT CANDIDATE 1 (SL 2.5%, TP 2.25R, Buffer 0.05%)", sOpt1, rOpt1, candles)

        // Optimized Candidate 2: SL 2.5%, TP 2.0R, 0.05% Buffer, Vol 1.2x, EMA 50, RSI 50
        val rOpt2 = standardRisk.copy(stopLossValue = 2.5, takeProfitValue = 2.0)
        evaluateRun("OPT CANDIDATE 2 (SL 2.5%, TP 2.0R, Buffer 0.05%)", sOpt1, rOpt2, candles)

        // 70/30 OOS Analysis
        val split70 = (candles.size * 0.70).toInt()
        val isCandles = candles.take(split70)
        val oosCandles = candles.drop(split70)

        println("\n=== 70/30 IN-SAMPLE / OUT-OF-SAMPLE ===")
        println("--- BASELINE ---")
        val baseIS = BacktestEngine.runBacktest(isCandles, btcAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.M5, baselineOrbStrategy, standardRisk).metrics
        val baseOOS = BacktestEngine.runBacktest(oosCandles, btcAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.M5, baselineOrbStrategy, standardRisk).metrics
        println("IS:  Trades=${baseIS.totalTrades}, WR=${df.format(baseIS.winRatePercent)}%, PF=${df.format(baseIS.profitFactor)}, Exp=$${df.format(baseIS.expectancyDollars)}, NetPnL=$${df.format(baseIS.netProfitDollars)}, MaxDD=${df.format(baseIS.maxDrawdownPercent)}%")
        println("OOS: Trades=${baseOOS.totalTrades}, WR=${df.format(baseOOS.winRatePercent)}%, PF=${df.format(baseOOS.profitFactor)}, Exp=$${df.format(baseOOS.expectancyDollars)}, NetPnL=$${df.format(baseOOS.netProfitDollars)}, MaxDD=${df.format(baseOOS.maxDrawdownPercent)}%")

        println("--- OPTIMIZED CANDIDATE (Candidate 1: SL 2.5%, TP 2.25R, Buffer 0.05%) ---")
        val optIS = BacktestEngine.runBacktest(isCandles, btcAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.M5, sOpt1, rOpt1).metrics
        val optOOS = BacktestEngine.runBacktest(oosCandles, btcAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.M5, sOpt1, rOpt1).metrics
        println("IS:  Trades=${optIS.totalTrades}, WR=${df.format(optIS.winRatePercent)}%, PF=${df.format(optIS.profitFactor)}, Exp=$${df.format(optIS.expectancyDollars)}, NetPnL=$${df.format(optIS.netProfitDollars)}, MaxDD=${df.format(optIS.maxDrawdownPercent)}%")
        println("OOS: Trades=${optOOS.totalTrades}, WR=${df.format(optOOS.winRatePercent)}%, PF=${df.format(optOOS.profitFactor)}, Exp=$${df.format(optOOS.expectancyDollars)}, NetPnL=$${df.format(optOOS.netProfitDollars)}, MaxDD=${df.format(optOOS.maxDrawdownPercent)}%")

        println("\n=== TRANSACTION COST SENSITIVITY ===")
        val slips = listOf(0.0, 5.0, 10.0, 15.0, 20.0, 30.0)
        for (slip in slips) {
            val rB = standardRisk.copy(slippageBps = slip)
            val rO = rOpt1.copy(slippageBps = slip)
            val mB = BacktestEngine.runBacktest(candles, btcAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.M5, baselineOrbStrategy, rB).metrics
            val mO = BacktestEngine.runBacktest(candles, btcAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.M5, sOpt1, rO).metrics
            println("Slip ${slip.toInt()} bps | Base: Net=$${df.format(mB.netProfitDollars)}, PF=${df.format(mB.profitFactor)}, Exp=$${df.format(mB.expectancyDollars)}, DD=${df.format(mB.maxDrawdownPercent)}% | Opt: Net=$${df.format(mO.netProfitDollars)}, PF=${df.format(mO.profitFactor)}, Exp=$${df.format(mO.expectancyDollars)}, DD=${df.format(mO.maxDrawdownPercent)}%")
        }

        println("\n=== OUTLIER REMOVAL TEST ===")
        val fullBase = BacktestEngine.runBacktest(candles, btcAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.M5, baselineOrbStrategy, standardRisk)
        val fullOpt = BacktestEngine.runBacktest(candles, btcAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.M5, sOpt1, rOpt1)

        fun printOutlierAnalysis(label: String, trades: List<Trade>) {
            val sorted = trades.map { it.pnlDollars }.sorted()
            val total = sorted.sum()
            val rem1 = sorted.dropLast(1).sum()
            val rem3 = sorted.dropLast(3).sum()
            val rem5 = sorted.dropLast(5).sum()
            println("$label: Total=$${df.format(total)}, RemTop1=$${df.format(rem1)}, RemTop3=$${df.format(rem3)}, RemTop5=$${df.format(rem5)}")
        }
        printOutlierAnalysis("Baseline", fullBase.trades)
        printOutlierAnalysis("Optimized", fullOpt.trades)
    }
}
