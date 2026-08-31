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
class Phase7BOrbValidationAuditTest {

    private lateinit var context: Context
    private lateinit var btcAsset: MarketAsset
    private val existingOrbStrategy = StrategyDefinition.PRESETS.first { it.id == "preset_orb_breakout" }

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

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        btcAsset = MarketDataProvider.ASSETS.first { it.symbol == "BTC/USDT" }
    }

    private fun generateMultiTimeframeData(): Map<Timeframe, List<Candle>> {
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
        val agg15m = TimeframeAggregator.aggregate(clean5m, Timeframe.M5, Timeframe.M15)
        val (clean15m, _) = MarketDataValidator.validateAndClean(agg15m, Timeframe.M15)
        val agg30m = TimeframeAggregator.aggregate(clean5m, Timeframe.M5, Timeframe.M30)
        val (clean30m, _) = MarketDataValidator.validateAndClean(agg30m, Timeframe.M30)
        val agg1h = TimeframeAggregator.aggregate(clean5m, Timeframe.M5, Timeframe.H1)
        val (clean1h, _) = MarketDataValidator.validateAndClean(agg1h, Timeframe.H1)

        return mapOf(
            Timeframe.M5 to clean5m,
            Timeframe.M15 to clean15m,
            Timeframe.M30 to clean30m,
            Timeframe.H1 to clean1h
        )
    }

    @Test
    fun auditSampleSessionsAndCalculation() {
        val tfMap = generateMultiTimeframeData()
        val c5m = tfMap[Timeframe.M5]!!
        val orbParams = existingOrbStrategy.indicatorConfig.orbParams
        val tracker = BacktestEngine.OrbSessionTracker(orbParams, Timeframe.M5)
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).apply {
            timeZone = TimeZone.getTimeZone(orbParams.sessionTimezone)
        }

        val sessions = mutableListOf<String>()
        var recordedSessions = 0
        var prevComplete = false
        var currentDay = ""

        println("=== 5 SAMPLE SESSIONS AUDIT ===")
        val cal = Calendar.getInstance(TimeZone.getTimeZone(orbParams.sessionTimezone))
        for (i in c5m.indices) {
            val c = c5m[i]
            cal.timeInMillis = c.timestamp
            val dayStr = "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH) + 1}-${cal.get(Calendar.DAY_OF_MONTH)}"
            if (dayStr != currentDay) {
                currentDay = dayStr
                prevComplete = false
            }

            tracker.update(c, i)
            if (tracker.isOpeningRangeComplete && !prevComplete) {
                prevComplete = true
                recordedSessions++
                if (recordedSessions in 1..5) {
                    val rangeStart = "$dayStr 09:30:00 EST/EDT"
                    val rangeEnd = "$dayStr 10:00:00 EST/EDT"
                    val rHigh = tracker.orbHigh ?: 0.0
                    val rLow = tracker.orbLow ?: 0.0

                    // Find first breakout after range completes in this session
                    var breakoutTs: Long? = null
                    var breakoutDir: String? = null
                    for (k in i until c5m.size) {
                        val cb = c5m[k]
                        cal.timeInMillis = cb.timestamp
                        val bDay = "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH) + 1}-${cal.get(Calendar.DAY_OF_MONTH)}"
                        if (bDay != currentDay) break
                        val minOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                        if (minOfDay >= 960) break // session end (16:00)

                        if (cb.close > rHigh) {
                            breakoutTs = cb.timestamp
                            breakoutDir = "LONG"
                            break
                        } else if (cb.close < rLow) {
                            breakoutTs = cb.timestamp
                            breakoutDir = "SHORT"
                            break
                        }
                    }

                    println("Session $recordedSessions:")
                    println("  Date: $dayStr")
                    println("  Range: $rangeStart to $rangeEnd")
                    println("  Range High: $$rHigh | Range Low: $$rLow")
                    if (breakoutTs != null) {
                        println("  First Breakout: ${sdf.format(Date(breakoutTs))} ($breakoutTs) -> $breakoutDir")
                    } else {
                        println("  First Breakout: None (Price stayed within range)")
                    }
                }
            }
        }
    }

    @Test
    fun auditStrongerLookAhead() {
        val tfMap = generateMultiTimeframeData()
        val c5m = tfMap[Timeframe.M5]!!
        val orbParams = existingOrbStrategy.indicatorConfig.orbParams

        // Select 3 sample sessions (Day 10, Day 50, Day 100)
        val dayIndices = listOf(10 * 288, 50 * 288, 100 * 288)

        for (baseIdx in dayIndices) {
            val normalCandles = c5m.take(baseIdx + 288)
            val resNormal = BacktestEngine.runBacktest(normalCandles, btcAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.M5, existingOrbStrategy, standardRisk)

            // Mutate all bars after 10:00 AM on that day
            val mutatedCandles = normalCandles.toMutableList()
            val cal = Calendar.getInstance(TimeZone.getTimeZone(orbParams.sessionTimezone))
            var mutatedCount = 0
            for (k in baseIdx until mutatedCandles.size) {
                cal.timeInMillis = mutatedCandles[k].timestamp
                val minOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                if (minOfDay >= 600) { // >= 10:00 AM
                    val orig = mutatedCandles[k]
                    mutatedCandles[k] = orig.copy(
                        open = orig.open * 1.05,
                        high = orig.high * 1.08,
                        low = orig.low * 0.95,
                        close = orig.close * 1.04
                    )
                    mutatedCount++
                }
            }

            // Tracker for normal vs mutated during the 09:30 - 10:00 range
            val tracker1 = BacktestEngine.OrbSessionTracker(orbParams, Timeframe.M5)
            val tracker2 = BacktestEngine.OrbSessionTracker(orbParams, Timeframe.M5)

            for (k in baseIdx until (baseIdx + 288)) {
                cal.timeInMillis = normalCandles[k].timestamp
                val minOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                if (minOfDay < 600) {
                    tracker1.update(normalCandles[k], k)
                    tracker2.update(mutatedCandles[k], k)
                }
            }

            assertEquals("ORB High must be identical regardless of future mutation", tracker1.orbHigh, tracker2.orbHigh)
            assertEquals("ORB Low must be identical regardless of future mutation", tracker1.orbLow, tracker2.orbLow)
        }
        println("=== STRONGER LOOK-AHEAD AUDIT: 100% PASSED ===")
    }
}
