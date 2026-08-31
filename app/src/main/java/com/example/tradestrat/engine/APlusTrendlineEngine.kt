package com.example.tradestrat.engine

import com.example.tradestrat.model.APlusForensicAuditLog
import com.example.tradestrat.model.APlusStrategyVisualData
import com.example.tradestrat.model.APlusTrendline
import com.example.tradestrat.model.APlusTrendlineConfig
import com.example.tradestrat.model.BreakAuditRecord
import com.example.tradestrat.model.Candle
import com.example.tradestrat.model.ExitReason
import com.example.tradestrat.model.RetestAuditRecord
import com.example.tradestrat.model.StructuralStopAuditRecord
import com.example.tradestrat.model.SwingConfirmationAuditRecord
import com.example.tradestrat.model.SwingPoint
import com.example.tradestrat.model.TradeDirection
import com.example.tradestrat.model.TradeExplanation
import com.example.tradestrat.model.TradeManagementAuditRecord
import com.example.tradestrat.model.TradeManagementEventType
import com.example.tradestrat.model.TrendlineAuditRecord
import com.example.tradestrat.model.TrendlineMarketStructure
import com.example.tradestrat.model.TrendlineSetupMode
import com.example.tradestrat.model.TrendlineType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class APlusTrendlineEngine(
    val config: APlusTrendlineConfig = APlusTrendlineConfig()
) {

    data class SignalResult(
        val longSignal: Boolean = false,
        val shortSignal: Boolean = false,
        val setupType: String? = null, // "BREAK", "BREAK_RETEST", "BOUNCE"
        val explanation: TradeExplanation? = null,
        val structuralStopLoss: Double? = null,
        val targetTrendline: APlusTrendline? = null
    )

    data class PendingRetest(
        val trendline: APlusTrendline,
        val direction: TradeDirection,
        val breakBarIndex: Int,
        val breakTimestamp: Long,
        val breakPrice: Double,
        val swingInvalidationPrice: Double,
        val marketStructureAtBreak: TrendlineMarketStructure
    )

    private var pendingLongRetest: PendingRetest? = null
    private var pendingShortRetest: PendingRetest? = null

    // Cache of detected swing points across the series
    private val allSwingHighs = mutableListOf<SwingPoint>()
    private val allSwingLows = mutableListOf<SwingPoint>()

    // Visual debugging accumulators
    private val visualBreakBars = mutableListOf<Int>()
    private val visualRetestBars = mutableListOf<Int>()
    private val visualEntryBars = mutableListOf<Int>()
    private val visualBreakEvenBars = mutableListOf<Int>()
    private val visualStructureMap = mutableMapOf<Int, TrendlineMarketStructure>()
    private val visualActiveTrendlines = mutableListOf<APlusTrendline>()

    // Forensic Audit accumulators
    private val auditSwings = mutableListOf<SwingConfirmationAuditRecord>()
    private val auditTrendlines = mutableListOf<TrendlineAuditRecord>()
    private val auditBreaks = mutableListOf<BreakAuditRecord>()
    private val auditRetests = mutableListOf<RetestAuditRecord>()
    private val auditStructuralStops = mutableListOf<StructuralStopAuditRecord>()
    private val auditManagement = mutableListOf<TradeManagementAuditRecord>()

    fun reset() {
        pendingLongRetest = null
        pendingShortRetest = null
        allSwingHighs.clear()
        allSwingLows.clear()
        visualBreakBars.clear()
        visualRetestBars.clear()
        visualEntryBars.clear()
        visualBreakEvenBars.clear()
        visualStructureMap.clear()
        visualActiveTrendlines.clear()
        auditSwings.clear()
        auditTrendlines.clear()
        auditBreaks.clear()
        auditRetests.clear()
        auditStructuralStops.clear()
        auditManagement.clear()
    }

    /**
     * Calculates 14-period ATR causal up to bar index `i`.
     */
    fun getAtrAt(candles: List<Candle>, i: Int, period: Int = 14): Double {
        if (candles.isEmpty()) return 1.0
        if (i < 1) return max(0.0001, candles[i].high - candles[i].low)
        val start = max(1, i - period + 1)
        var trSum = 0.0
        var count = 0
        for (k in start..i) {
            val h = candles[k].high
            val l = candles[k].low
            val prevC = candles[k - 1].close
            val tr = max(h - l, max(abs(h - prevC), abs(l - prevC)))
            trSum += tr
            count++
        }
        val atr = if (count > 0) trSum / count else (candles[i].high - candles[i].low)
        return max(0.00001, atr)
    }

    /**
     * Strictly Causal Swing Detection:
     * A candle at index `p` is confirmed as a swing high/low at index `i` iff
     * `p + confirmationBars <= i` and `p - swingLookback >= 0`.
     */
    fun detectConfirmedSwingsUpTo(
        candles: List<Candle>,
        currentBarIndex: Int
    ): Pair<List<SwingPoint>, List<SwingPoint>> {
        val confirmedHighs = mutableListOf<SwingPoint>()
        val confirmedLows = mutableListOf<SwingPoint>()

        val maxSearchEnd = currentBarIndex - config.confirmationBars
        val lookback = config.swingLookback
        val minSep = config.minSwingSeparationBars

        for (p in lookback..maxSearchEnd) {
            val highCandidate = candles[p].high
            var isHigh = true
            for (k in (p - lookback)..(p + config.confirmationBars)) {
                if (k != p && candles[k].high > highCandidate) {
                    isHigh = false
                    break
                }
            }

            if (isHigh) {
                // Ensure separation from previous confirmed swing high
                val prevHigh = confirmedHighs.lastOrNull()
                if (prevHigh == null || (p - prevHigh.barIndex) >= minSep) {
                    val swing = SwingPoint(
                        barIndex = p,
                        timestamp = candles[p].timestamp,
                        price = highCandidate,
                        isHigh = true,
                        confirmedAtBar = p + config.confirmationBars
                    )
                    confirmedHighs.add(swing)
                    if (!auditSwings.any { it.candidateBarIndex == p && it.isHigh }) {
                        auditSwings.add(
                            SwingConfirmationAuditRecord(
                                candidateBarIndex = p,
                                candidateTimestamp = candles[p].timestamp,
                                candidatePrice = highCandidate,
                                isHigh = true,
                                confirmationBarIndex = p + config.confirmationBars,
                                confirmationTimestamp = candles[p + config.confirmationBars].timestamp,
                                delayBars = config.confirmationBars
                            )
                        )
                    }
                } else if (highCandidate > prevHigh.price) {
                    // Replace if higher within separation window
                    val swing = SwingPoint(
                        barIndex = p,
                        timestamp = candles[p].timestamp,
                        price = highCandidate,
                        isHigh = true,
                        confirmedAtBar = p + config.confirmationBars
                    )
                    confirmedHighs[confirmedHighs.size - 1] = swing
                    val idx = auditSwings.indexOfFirst { it.candidateBarIndex == prevHigh.barIndex && it.isHigh }
                    val rec = SwingConfirmationAuditRecord(
                        candidateBarIndex = p,
                        candidateTimestamp = candles[p].timestamp,
                        candidatePrice = highCandidate,
                        isHigh = true,
                        confirmationBarIndex = p + config.confirmationBars,
                        confirmationTimestamp = candles[p + config.confirmationBars].timestamp,
                        delayBars = config.confirmationBars
                    )
                    if (idx >= 0) auditSwings[idx] = rec else auditSwings.add(rec)
                }
            }

            val lowCandidate = candles[p].low
            var isLow = true
            for (k in (p - lookback)..(p + config.confirmationBars)) {
                if (k != p && candles[k].low < lowCandidate) {
                    isLow = false
                    break
                }
            }

            if (isLow) {
                // Ensure separation from previous confirmed swing low
                val prevLow = confirmedLows.lastOrNull()
                if (prevLow == null || (p - prevLow.barIndex) >= minSep) {
                    val swing = SwingPoint(
                        barIndex = p,
                        timestamp = candles[p].timestamp,
                        price = lowCandidate,
                        isHigh = false,
                        confirmedAtBar = p + config.confirmationBars
                    )
                    confirmedLows.add(swing)
                    if (!auditSwings.any { it.candidateBarIndex == p && !it.isHigh }) {
                        auditSwings.add(
                            SwingConfirmationAuditRecord(
                                candidateBarIndex = p,
                                candidateTimestamp = candles[p].timestamp,
                                candidatePrice = lowCandidate,
                                isHigh = false,
                                confirmationBarIndex = p + config.confirmationBars,
                                confirmationTimestamp = candles[p + config.confirmationBars].timestamp,
                                delayBars = config.confirmationBars
                            )
                        )
                    }
                } else if (lowCandidate < prevLow.price) {
                    // Replace if lower within separation window
                    val swing = SwingPoint(
                        barIndex = p,
                        timestamp = candles[p].timestamp,
                        price = lowCandidate,
                        isHigh = false,
                        confirmedAtBar = p + config.confirmationBars
                    )
                    confirmedLows[confirmedLows.size - 1] = swing
                    val idx = auditSwings.indexOfFirst { it.candidateBarIndex == prevLow.barIndex && !it.isHigh }
                    val rec = SwingConfirmationAuditRecord(
                        candidateBarIndex = p,
                        candidateTimestamp = candles[p].timestamp,
                        candidatePrice = lowCandidate,
                        isHigh = false,
                        confirmationBarIndex = p + config.confirmationBars,
                        confirmationTimestamp = candles[p + config.confirmationBars].timestamp,
                        delayBars = config.confirmationBars
                    )
                    if (idx >= 0) auditSwings[idx] = rec else auditSwings.add(rec)
                }
            }
        }

        return Pair(confirmedHighs, confirmedLows)
    }

    /**
     * Evaluates Market Structure:
     * BULLISH_TREND: Higher Highs (HH) AND Higher Lows (HL)
     * BEARISH_TREND: Lower Highs (LH) AND Lower Lows (LL)
     * CONSOLIDATION: Any mixed or ambiguous structural state
     */
    fun evaluateMarketStructure(
        confirmedHighs: List<SwingPoint>,
        confirmedLows: List<SwingPoint>
    ): TrendlineMarketStructure {
        if (confirmedHighs.size < 2 || confirmedLows.size < 2) {
            return TrendlineMarketStructure.CONSOLIDATION
        }

        val hLast = confirmedHighs.last()
        val hPrev = confirmedHighs[confirmedHighs.size - 2]
        val lLast = confirmedLows.last()
        val lPrev = confirmedLows[confirmedLows.size - 2]

        val isHigherHigh = hLast.price > hPrev.price
        val isHigherLow = lLast.price > lPrev.price
        val isLowerHigh = hLast.price < hPrev.price
        val isLowerLow = lLast.price < lPrev.price

        return when {
            isHigherHigh && isHigherLow -> TrendlineMarketStructure.BULLISH_TREND
            isLowerHigh && isLowerLow -> TrendlineMarketStructure.BEARISH_TREND
            else -> TrendlineMarketStructure.CONSOLIDATION
        }
    }

    /**
     * Constructs valid multi-touch trendlines up to the current bar.
     * Ascending Support: Connects confirmed swing lows (slope >= 0).
     * Descending Resistance: Connects confirmed swing highs (slope <= 0).
     */
    fun findActiveTrendlines(
        candles: List<Candle>,
        currentBarIndex: Int,
        confirmedHighs: List<SwingPoint>,
        confirmedLows: List<SwingPoint>
    ): Pair<APlusTrendline?, APlusTrendline?> {
        val atr = getAtrAt(candles, currentBarIndex)
        val currentClose = candles.getOrNull(currentBarIndex)?.close ?: 1.0
        val tolerance = (config.retestToleranceATR * atr) / currentClose

        // Find best descending resistance trendline
        var bestResistance: APlusTrendline? = null
        if (confirmedHighs.size >= 2) {
            for (i in (confirmedHighs.size - 1) downTo 1) {
                val h2 = confirmedHighs[i]
                for (j in (i - 1) downTo max(0, i - 4)) {
                    val h1 = confirmedHighs[j]
                    if (h2.barIndex - h1.barIndex < config.minSwingSeparationBars) continue

                    val slope = (h2.price - h1.price) / (h2.barIndex - h1.barIndex)
                    // Descending resistance must have downward or flat slope
                    if (slope > (0.0005 * h1.price)) continue

                    val intercept = h1.price - slope * h1.barIndex

                    // Verify causal integrity: no bar close prior to current break bar can penetrate significantly above line
                    var valid = true
                    val touches = mutableListOf<Int>()
                    touches.add(h1.barIndex)
                    touches.add(h2.barIndex)

                    val evalEnd = currentBarIndex - 1
                    for (k in h1.barIndex..evalEnd) {
                        val linePrice = intercept + slope * k
                        if (candles[k].close > linePrice * (1.0 + tolerance * 1.5)) {
                            // Violated prior to now
                            valid = false
                            break
                        }
                        if (k != h1.barIndex && k != h2.barIndex) {
                            if (abs(candles[k].high - linePrice) / linePrice <= tolerance * 1.5) {
                                touches.add(k)
                            }
                        }
                    }

                    if (valid && touches.size >= config.minTouches) {
                        val line = APlusTrendline(
                            id = "res_${h1.barIndex}_${h2.barIndex}",
                            type = TrendlineType.DESCENDING_RESISTANCE,
                            firstSwing = h1,
                            secondSwing = h2,
                            slope = slope,
                            intercept = intercept,
                            touches = touches.distinct().sorted(),
                            confirmedAtBar = h2.confirmedAtBar
                        )
                        bestResistance = line
                        if (!auditTrendlines.any { it.trendlineId == line.id }) {
                            auditTrendlines.add(
                                TrendlineAuditRecord(
                                    trendlineId = line.id,
                                    anchor1 = h1,
                                    anchor2 = h2,
                                    touchCount = touches.size,
                                    slope = slope,
                                    tolerancePct = config.retestToleranceATR * 100.0,
                                    creationBarIndex = h2.confirmedAtBar,
                                    creationTimestamp = candles[h2.confirmedAtBar].timestamp,
                                    firstUsableBarIndex = h2.confirmedAtBar,
                                    firstUsableTimestamp = candles[h2.confirmedAtBar].timestamp
                                )
                            )
                        }
                        break
                    }
                }
                if (bestResistance != null) break
            }
        }

        // Find best ascending support trendline
        var bestSupport: APlusTrendline? = null
        if (confirmedLows.size >= 2) {
            for (i in (confirmedLows.size - 1) downTo 1) {
                val l2 = confirmedLows[i]
                for (j in (i - 1) downTo max(0, i - 4)) {
                    val l1 = confirmedLows[j]
                    if (l2.barIndex - l1.barIndex < config.minSwingSeparationBars) continue

                    val slope = (l2.price - l1.price) / (l2.barIndex - l1.barIndex)
                    // Ascending support must have upward or flat slope
                    if (slope < (-0.0005 * l1.price)) continue

                    val intercept = l1.price - slope * l1.barIndex

                    var valid = true
                    val touches = mutableListOf<Int>()
                    touches.add(l1.barIndex)
                    touches.add(l2.barIndex)

                    val evalEnd = currentBarIndex - 1
                    for (k in l1.barIndex..evalEnd) {
                        val linePrice = intercept + slope * k
                        if (candles[k].close < linePrice * (1.0 - tolerance * 1.5)) {
                            valid = false
                            break
                        }
                        if (k != l1.barIndex && k != l2.barIndex) {
                            if (abs(candles[k].low - linePrice) / linePrice <= tolerance * 1.5) {
                                touches.add(k)
                            }
                        }
                    }

                    if (valid && touches.size >= config.minTouches) {
                        val line = APlusTrendline(
                            id = "sup_${l1.barIndex}_${l2.barIndex}",
                            type = TrendlineType.ASCENDING_SUPPORT,
                            firstSwing = l1,
                            secondSwing = l2,
                            slope = slope,
                            intercept = intercept,
                            touches = touches.distinct().sorted(),
                            confirmedAtBar = l2.confirmedAtBar
                        )
                        bestSupport = line
                        if (!auditTrendlines.any { it.trendlineId == line.id }) {
                            auditTrendlines.add(
                                TrendlineAuditRecord(
                                    trendlineId = line.id,
                                    anchor1 = l1,
                                    anchor2 = l2,
                                    touchCount = touches.size,
                                    slope = slope,
                                    tolerancePct = config.retestToleranceATR * 100.0,
                                    creationBarIndex = l2.confirmedAtBar,
                                    creationTimestamp = candles[l2.confirmedAtBar].timestamp,
                                    firstUsableBarIndex = l2.confirmedAtBar,
                                    firstUsableTimestamp = candles[l2.confirmedAtBar].timestamp
                                )
                            )
                        }
                        break
                    }
                }
                if (bestSupport != null) break
            }
        }

        return Pair(bestSupport, bestResistance)
    }

    /**
     * Evaluates candle bar `i` for entry signals according to the A+ Trendline Rules.
     */
    fun evaluateBar(
        candles: List<Candle>,
        i: Int
    ): SignalResult {
        if (i < config.swingLookback + config.confirmationBars + 5) {
            return SignalResult()
        }

        val (confirmedHighs, confirmedLows) = detectConfirmedSwingsUpTo(candles, i)
        val structure = evaluateMarketStructure(confirmedHighs, confirmedLows)
        visualStructureMap[i] = structure

        val (supportLine, resistanceLine) = findActiveTrendlines(candles, i, confirmedHighs, confirmedLows)
        if (supportLine != null && !visualActiveTrendlines.any { it.id == supportLine.id }) {
            visualActiveTrendlines.add(supportLine)
        }
        if (resistanceLine != null && !visualActiveTrendlines.any { it.id == resistanceLine.id }) {
            visualActiveTrendlines.add(resistanceLine)
        }

        val current = candles[i]
        val prev = candles[i - 1]
        val atr = getAtrAt(candles, i)
        val breakDistanceAtr = config.breakConfirmationATR * atr
        val retestToleranceAtr = config.retestToleranceATR * atr
        val stopBufferAtr = config.stopBufferATR * atr

        // =========================================================================
        // 1. Check for NEW Break Events (Descending Resistance breakout or Ascending Support breakdown)
        // =========================================================================
        if (config.setupMode != TrendlineSetupMode.BOUNCE_ONLY) {
            if (resistanceLine != null) {
                val resPriceCurrent = resistanceLine.priceAt(i)
                val resPricePrev = resistanceLine.priceAt(i - 1)
                val isBullishBreak = prev.close <= resPricePrev && current.close >= (resPriceCurrent + breakDistanceAtr)

                if (isBullishBreak) {
                    visualBreakBars.add(i)
                    auditBreaks.add(
                        BreakAuditRecord(
                            trendlineId = resistanceLine.id,
                            breakCandleIndex = i,
                            breakCandleTimestamp = current.timestamp,
                            breakPrice = current.close,
                            candleClose = current.close,
                            trendlinePrice = resPriceCurrent,
                            breakDistancePct = abs(current.close - resPriceCurrent) / atr * 100.0,
                            confirmationTimestamp = current.timestamp
                        )
                    )

                    val relevantSwingLow = confirmedLows.lastOrNull()?.price ?: current.low
                    val invalidationPrice = relevantSwingLow - stopBufferAtr

                    if (config.setupMode == TrendlineSetupMode.BREAK_ONLY) {
                        val structOk = !config.requireStructureConfirmation || (structure != TrendlineMarketStructure.BEARISH_TREND && structure != TrendlineMarketStructure.CONSOLIDATION)
                        if (structOk) {
                            visualEntryBars.add(i)
                            val refSwing = confirmedLows.lastOrNull() ?: SwingPoint(i, current.timestamp, current.low, false, i)
                            auditStructuralStops.add(
                                StructuralStopAuditRecord(
                                    tradeId = "trade_long_$i",
                                    structuralReferenceSwing = refSwing,
                                    swingTimestamp = refSwing.timestamp,
                                    swingPrice = refSwing.price,
                                    bufferPct = config.stopBufferATR * 100.0,
                                    finalStopPrice = invalidationPrice
                                )
                            )
                            return SignalResult(
                                longSignal = true,
                                setupType = "BREAK",
                                explanation = TradeExplanation(
                                    setupType = "BREAK",
                                    direction = "LONG",
                                    trend = structure.name,
                                    trendlineDescription = "Descending Resistance Breakout (${resistanceLine.touchCount} touches, slope: ${String.format("%.4f", resistanceLine.slope)})",
                                    touchCount = resistanceLine.touchCount,
                                    breakCandleTimestamp = current.timestamp,
                                    breakCandleBarIndex = i,
                                    retestStatus = "N/A (Direct Breakout)",
                                    entryReason = "Bullish candle close above descending resistance trendline by >= 0.10 ATR with ${resistanceLine.touchCount} confirmed touches",
                                    stopReason = "Below confirmed structural swing low at ${String.format("%.2f", invalidationPrice)}"
                                ),
                                structuralStopLoss = invalidationPrice,
                                targetTrendline = resistanceLine
                            )
                        }
                    } else {
                        // Setup mode is BREAK_AND_RETEST or BREAK_OR_BOUNCE -> Register pending retest
                        pendingLongRetest = PendingRetest(
                            trendline = resistanceLine,
                            direction = TradeDirection.LONG,
                            breakBarIndex = i,
                            breakTimestamp = current.timestamp,
                            breakPrice = current.close,
                            swingInvalidationPrice = invalidationPrice,
                            marketStructureAtBreak = structure
                        )
                    }
                }
            }

            if (supportLine != null) {
                val supPriceCurrent = supportLine.priceAt(i)
                val supPricePrev = supportLine.priceAt(i - 1)
                val isBearishBreak = prev.close >= supPricePrev && current.close <= (supPriceCurrent - breakDistanceAtr)

                if (isBearishBreak) {
                    visualBreakBars.add(i)
                    auditBreaks.add(
                        BreakAuditRecord(
                            trendlineId = supportLine.id,
                            breakCandleIndex = i,
                            breakCandleTimestamp = current.timestamp,
                            breakPrice = current.close,
                            candleClose = current.close,
                            trendlinePrice = supPriceCurrent,
                            breakDistancePct = abs(current.close - supPriceCurrent) / atr * 100.0,
                            confirmationTimestamp = current.timestamp
                        )
                    )

                    val relevantSwingHigh = confirmedHighs.lastOrNull()?.price ?: current.high
                    val invalidationPrice = relevantSwingHigh + stopBufferAtr

                    if (config.setupMode == TrendlineSetupMode.BREAK_ONLY) {
                        val structOk = !config.requireStructureConfirmation || (structure != TrendlineMarketStructure.BULLISH_TREND && structure != TrendlineMarketStructure.CONSOLIDATION)
                        if (structOk) {
                            visualEntryBars.add(i)
                            val refSwing = confirmedHighs.lastOrNull() ?: SwingPoint(i, current.timestamp, current.high, true, i)
                            auditStructuralStops.add(
                                StructuralStopAuditRecord(
                                    tradeId = "trade_short_$i",
                                    structuralReferenceSwing = refSwing,
                                    swingTimestamp = refSwing.timestamp,
                                    swingPrice = refSwing.price,
                                    bufferPct = config.stopBufferATR * 100.0,
                                    finalStopPrice = invalidationPrice
                                )
                            )
                            return SignalResult(
                                shortSignal = true,
                                setupType = "BREAK",
                                explanation = TradeExplanation(
                                    setupType = "BREAK",
                                    direction = "SHORT",
                                    trend = structure.name,
                                    trendlineDescription = "Ascending Support Breakdown (${supportLine.touchCount} touches, slope: ${String.format("%.4f", supportLine.slope)})",
                                    touchCount = supportLine.touchCount,
                                    breakCandleTimestamp = current.timestamp,
                                    breakCandleBarIndex = i,
                                    retestStatus = "N/A (Direct Breakdown)",
                                    entryReason = "Bearish candle close below ascending support trendline by >= 0.10 ATR with ${supportLine.touchCount} confirmed touches",
                                    stopReason = "Above confirmed structural swing high at ${String.format("%.2f", invalidationPrice)}"
                                ),
                                structuralStopLoss = invalidationPrice,
                                targetTrendline = supportLine
                            )
                        }
                    } else {
                        pendingShortRetest = PendingRetest(
                            trendline = supportLine,
                            direction = TradeDirection.SHORT,
                            breakBarIndex = i,
                            breakTimestamp = current.timestamp,
                            breakPrice = current.close,
                            swingInvalidationPrice = invalidationPrice,
                            marketStructureAtBreak = structure
                        )
                    }
                }
            }
        }

        // =========================================================================
        // 2. Evaluate Pending Break-and-Retest
        // =========================================================================
        if (pendingLongRetest != null) {
            val retest = pendingLongRetest!!
            val barsSinceBreak = i - retest.breakBarIndex

            if (barsSinceBreak > config.retestMaxBars) {
                // Retest window expired
                pendingLongRetest = null
            } else if (barsSinceBreak > 0) {
                val linePrice = retest.trendline.priceAt(i)
                // Invalidation: Price closes deeply below the line or below swing invalidation
                if (current.close < retest.swingInvalidationPrice || current.close < (linePrice - retestToleranceAtr * 2.0)) {
                    pendingLongRetest = null
                } else {
                    // Check if candle low tapped into ATR retest zone
                    val touchedRetestZone = current.low <= (linePrice + retestToleranceAtr) && current.low >= (linePrice - retestToleranceAtr * 2.0)
                    // Check bullish rejection: Green candle or lower wick rejection closing above retest level
                    val isBullishRejection = (current.close > current.open && current.close >= (linePrice - 0.05 * atr)) ||
                            ((min(current.open, current.close) - current.low) >= (current.high - current.low) * 0.25 && current.close >= linePrice)

                    if (touchedRetestZone && isBullishRejection) {
                        val structOk = !config.requireStructureConfirmation || (structure != TrendlineMarketStructure.BEARISH_TREND && structure != TrendlineMarketStructure.CONSOLIDATION)
                        if (structOk) {
                            visualRetestBars.add(i)
                            visualEntryBars.add(i)
                            val stopPrice = retest.swingInvalidationPrice
                            auditRetests.add(
                                RetestAuditRecord(
                                    breakCandleIndex = retest.breakBarIndex,
                                    retestCandleIndex = i,
                                    trendlinePrice = linePrice,
                                    retestPrice = current.close,
                                    distancePct = abs(current.close - linePrice) / atr * 100.0,
                                    rejectionConfirmed = true,
                                    entryCandleIndex = i
                                )
                            )
                            val refSwing = confirmedLows.lastOrNull() ?: SwingPoint(i, current.timestamp, current.low, false, i)
                            auditStructuralStops.add(
                                StructuralStopAuditRecord(
                                    tradeId = "trade_long_retest_$i",
                                    structuralReferenceSwing = refSwing,
                                    swingTimestamp = refSwing.timestamp,
                                    swingPrice = refSwing.price,
                                    bufferPct = config.stopBufferATR * 100.0,
                                    finalStopPrice = stopPrice
                                )
                            )
                            pendingLongRetest = null // Retest executed
                            return SignalResult(
                                longSignal = true,
                                setupType = "BREAK_RETEST",
                                explanation = TradeExplanation(
                                    setupType = "BREAK_RETEST",
                                    direction = "LONG",
                                    trend = structure.name,
                                    trendlineDescription = "Break & Retest of Descending Resistance (${retest.trendline.touchCount} touches)",
                                    touchCount = retest.trendline.touchCount,
                                    breakCandleTimestamp = retest.breakTimestamp,
                                    breakCandleBarIndex = retest.breakBarIndex,
                                    retestStatus = "Confirmed at bar $i",
                                    entryReason = "Bullish rejection candle confirming retest within 0.25 ATR of broken descending resistance line",
                                    stopReason = "Below confirmed structural swing low at ${String.format("%.2f", stopPrice)}"
                                ),
                                structuralStopLoss = stopPrice,
                                targetTrendline = retest.trendline
                            )
                        }
                    }
                }
            }
        }

        if (pendingShortRetest != null) {
            val retest = pendingShortRetest!!
            val barsSinceBreak = i - retest.breakBarIndex

            if (barsSinceBreak > config.retestMaxBars) {
                pendingShortRetest = null
            } else if (barsSinceBreak > 0) {
                val linePrice = retest.trendline.priceAt(i)
                if (current.close > retest.swingInvalidationPrice || current.close > (linePrice + retestToleranceAtr * 2.0)) {
                    pendingShortRetest = null
                } else {
                    val touchedRetestZone = current.high >= (linePrice - retestToleranceAtr) && current.high <= (linePrice + retestToleranceAtr * 2.0)
                    val isBearishRejection = (current.close < current.open && current.close <= (linePrice + 0.05 * atr)) ||
                            ((current.high - max(current.open, current.close)) >= (current.high - current.low) * 0.25 && current.close <= linePrice)

                    if (touchedRetestZone && isBearishRejection) {
                        val structOk = !config.requireStructureConfirmation || (structure != TrendlineMarketStructure.BULLISH_TREND && structure != TrendlineMarketStructure.CONSOLIDATION)
                        if (structOk) {
                            visualRetestBars.add(i)
                            visualEntryBars.add(i)
                            val stopPrice = retest.swingInvalidationPrice
                            auditRetests.add(
                                RetestAuditRecord(
                                    breakCandleIndex = retest.breakBarIndex,
                                    retestCandleIndex = i,
                                    trendlinePrice = linePrice,
                                    retestPrice = current.close,
                                    distancePct = abs(current.close - linePrice) / atr * 100.0,
                                    rejectionConfirmed = true,
                                    entryCandleIndex = i
                                )
                            )
                            val refSwing = confirmedHighs.lastOrNull() ?: SwingPoint(i, current.timestamp, current.high, true, i)
                            auditStructuralStops.add(
                                StructuralStopAuditRecord(
                                    tradeId = "trade_short_retest_$i",
                                    structuralReferenceSwing = refSwing,
                                    swingTimestamp = refSwing.timestamp,
                                    swingPrice = refSwing.price,
                                    bufferPct = config.stopBufferATR * 100.0,
                                    finalStopPrice = stopPrice
                                )
                            )
                            pendingShortRetest = null
                            return SignalResult(
                                shortSignal = true,
                                setupType = "BREAK_RETEST",
                                explanation = TradeExplanation(
                                    setupType = "BREAK_RETEST",
                                    direction = "SHORT",
                                    trend = structure.name,
                                    trendlineDescription = "Break & Retest of Ascending Support (${retest.trendline.touchCount} touches)",
                                    touchCount = retest.trendline.touchCount,
                                    breakCandleTimestamp = retest.breakTimestamp,
                                    breakCandleBarIndex = retest.breakBarIndex,
                                    retestStatus = "Confirmed at bar $i",
                                    entryReason = "Bearish rejection candle confirming retest within 0.25 ATR of broken ascending support line",
                                    stopReason = "Above confirmed structural swing high at ${String.format("%.2f", stopPrice)}"
                                ),
                                structuralStopLoss = stopPrice,
                                targetTrendline = retest.trendline
                            )
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 3. Evaluate Bounce Setups (if BOUNCE_ONLY or BREAK_OR_BOUNCE mode is active)
        // =========================================================================
        if (config.setupMode == TrendlineSetupMode.BOUNCE_ONLY || config.setupMode == TrendlineSetupMode.BREAK_OR_BOUNCE) {
            // Support Bounce -> Long
            if (supportLine != null && structure == TrendlineMarketStructure.BULLISH_TREND) {
                val supPrice = supportLine.priceAt(i)
                val touchedSupport = current.low <= (supPrice + retestToleranceAtr) && current.close >= (supPrice - retestToleranceAtr * 0.5)
                val isBullishRejection = current.close > current.open && (current.close - current.low) >= (current.high - current.low) * 0.4

                if (touchedSupport && isBullishRejection) {
                    val relevantLow = confirmedLows.lastOrNull()?.price ?: current.low
                    val stopPrice = relevantLow - stopBufferAtr
                    visualEntryBars.add(i)
                    val refSwing = confirmedLows.lastOrNull() ?: SwingPoint(i, current.timestamp, current.low, false, i)
                    auditStructuralStops.add(
                        StructuralStopAuditRecord(
                            tradeId = "trade_bounce_long_$i",
                            structuralReferenceSwing = refSwing,
                            swingTimestamp = refSwing.timestamp,
                            swingPrice = refSwing.price,
                            bufferPct = config.stopBufferATR * 100.0,
                            finalStopPrice = stopPrice
                        )
                    )
                    return SignalResult(
                        longSignal = true,
                        setupType = "BOUNCE",
                        explanation = TradeExplanation(
                            setupType = "BOUNCE",
                            direction = "LONG",
                            trend = structure.name,
                            trendlineDescription = "Ascending Support Trendline (${supportLine.touchCount} touches)",
                            touchCount = supportLine.touchCount,
                            retestStatus = "N/A (Clean Bounce)",
                            entryReason = "Bullish rejection bounce from confirmed ascending support trendline in Bullish Market Structure",
                            stopReason = "Below confirmed structural swing low at ${String.format("%.2f", stopPrice)}"
                        ),
                        structuralStopLoss = stopPrice,
                        targetTrendline = supportLine
                    )
                }
            }

            // Resistance Bounce -> Short
            if (resistanceLine != null && structure == TrendlineMarketStructure.BEARISH_TREND) {
                val resPrice = resistanceLine.priceAt(i)
                val touchedResistance = current.high >= (resPrice - retestToleranceAtr) && current.close <= (resPrice + retestToleranceAtr * 0.5)
                val isBearishRejection = current.close < current.open && (current.high - current.close) >= (current.high - current.low) * 0.4

                if (touchedResistance && isBearishRejection) {
                    val relevantHigh = confirmedHighs.lastOrNull()?.price ?: current.high
                    val stopPrice = relevantHigh + stopBufferAtr
                    visualEntryBars.add(i)
                    val refSwing = confirmedHighs.lastOrNull() ?: SwingPoint(i, current.timestamp, current.high, true, i)
                    auditStructuralStops.add(
                        StructuralStopAuditRecord(
                            tradeId = "trade_bounce_short_$i",
                            structuralReferenceSwing = refSwing,
                            swingTimestamp = refSwing.timestamp,
                            swingPrice = refSwing.price,
                            bufferPct = config.stopBufferATR * 100.0,
                            finalStopPrice = stopPrice
                        )
                    )
                    return SignalResult(
                        shortSignal = true,
                        setupType = "BOUNCE",
                        explanation = TradeExplanation(
                            setupType = "BOUNCE",
                            direction = "SHORT",
                            trend = structure.name,
                            trendlineDescription = "Descending Resistance Trendline (${resistanceLine.touchCount} touches)",
                            touchCount = resistanceLine.touchCount,
                            retestStatus = "N/A (Clean Bounce)",
                            entryReason = "Bearish rejection bounce from confirmed descending resistance trendline in Bearish Market Structure",
                            stopReason = "Above confirmed structural swing high at ${String.format("%.2f", stopPrice)}"
                        ),
                        structuralStopLoss = stopPrice,
                        targetTrendline = resistanceLine
                    )
                }
            }
        }

        return SignalResult()
    }

    /**
     * Checks if structural trailing stop or break-even stop should ratchet at bar `i`.
     */
    fun evaluateStructuralManagement(
        candles: List<Candle>,
        i: Int,
        direction: TradeDirection,
        entryPrice: Double,
        currentStopLoss: Double?,
        initialRiskDollars: Double,
        highestPriceSeen: Double,
        lowestPriceSeen: Double
    ): Pair<Double?, ExitReason?> {
        if (currentStopLoss == null) return Pair(null, null)

        val (confirmedHighs, confirmedLows) = detectConfirmedSwingsUpTo(candles, i)
        val structure = evaluateMarketStructure(confirmedHighs, confirmedLows)

        var newStop = currentStopLoss
        var structureExitReason: ExitReason? = null

        val current = candles[i]
        val atr = getAtrAt(candles, i)
        val stopBuffer = config.stopBufferATR * atr

        if (direction == TradeDirection.LONG) {
            val initialRDistance = abs(entryPrice - currentStopLoss)
            val currentMfeR = if (initialRDistance > 0) (highestPriceSeen - entryPrice) / initialRDistance else 0.0

            // 1. Structure-Based Break-Even Check:
            // Eligible only after favorable excursion AND a new confirmed structural swing has formed in trade direction
            if (config.enableBreakEven) {
                val hasStructuralConfirmation = if (config.requireStructuralBreakEven) {
                    confirmedLows.any { it.barIndex > 0 && it.price > currentStopLoss }
                } else true

                if (currentMfeR >= config.breakEvenTriggerR && hasStructuralConfirmation) {
                    if (newStop < entryPrice) {
                        auditManagement.add(
                            TradeManagementAuditRecord(
                                tradeId = "trade_mgmt_long_$i",
                                eventType = TradeManagementEventType.BREAK_EVEN_ADJUSTMENT,
                                barIndex = i,
                                timestamp = current.timestamp,
                                previousStop = newStop,
                                newStop = entryPrice,
                                triggeringStructure = "NEW_STRUCTURAL_SWING_FORMED_MFE_${String.format("%.2f", currentMfeR)}R",
                                rAtTrigger = currentMfeR
                            )
                        )
                        newStop = entryPrice
                        visualBreakEvenBars.add(i)
                    }
                }
            }

            // 2. Structural Trailing: Ratchet stop under newly confirmed swing higher lows
            if (config.enableStructuralTrailing && confirmedLows.isNotEmpty()) {
                val lastConfirmedLow = confirmedLows.last()
                val potentialStructuralStop = lastConfirmedLow.price - stopBuffer
                // Only ratchet UP
                if (potentialStructuralStop > newStop && lastConfirmedLow.barIndex > 0) {
                    auditManagement.add(
                        TradeManagementAuditRecord(
                            tradeId = "trade_mgmt_long_$i",
                            eventType = TradeManagementEventType.STRUCTURAL_TRAILING_STOP,
                            barIndex = i,
                            timestamp = current.timestamp,
                            previousStop = newStop,
                            newStop = potentialStructuralStop,
                            triggeringStructure = "CONFIRMED_HIGHER_LOW_BAR_${lastConfirmedLow.barIndex}",
                            rAtTrigger = currentMfeR
                        )
                    )
                    newStop = potentialStructuralStop
                }
            }

            // 3. Counter-trend structure break check
            if (config.exitOnCounterStructureBreak && structure == TrendlineMarketStructure.BEARISH_TREND) {
                val lastLow = confirmedLows.lastOrNull()?.price
                if (lastLow != null && current.close < lastLow) {
                    structureExitReason = ExitReason.STRUCTURE_EXIT
                    auditManagement.add(
                        TradeManagementAuditRecord(
                            tradeId = "trade_mgmt_long_$i",
                            eventType = TradeManagementEventType.COUNTER_STRUCTURE_EXIT,
                            barIndex = i,
                            timestamp = current.timestamp,
                            previousStop = newStop,
                            newStop = null,
                            triggeringStructure = structure.name,
                            rAtTrigger = currentMfeR,
                            exitReason = ExitReason.STRUCTURE_EXIT
                        )
                    )
                }
            }
        } else {
            // SHORT Direction
            val initialRDistance = abs(entryPrice - currentStopLoss)
            val currentMfeR = if (initialRDistance > 0) (entryPrice - lowestPriceSeen) / initialRDistance else 0.0

            // 1. Structure-Based Break-Even Check:
            if (config.enableBreakEven) {
                val hasStructuralConfirmation = if (config.requireStructuralBreakEven) {
                    confirmedHighs.any { it.barIndex > 0 && it.price < currentStopLoss }
                } else true

                if (currentMfeR >= config.breakEvenTriggerR && hasStructuralConfirmation) {
                    if (newStop > entryPrice) {
                        auditManagement.add(
                            TradeManagementAuditRecord(
                                tradeId = "trade_mgmt_short_$i",
                                eventType = TradeManagementEventType.BREAK_EVEN_ADJUSTMENT,
                                barIndex = i,
                                timestamp = current.timestamp,
                                previousStop = newStop,
                                newStop = entryPrice,
                                triggeringStructure = "NEW_STRUCTURAL_SWING_FORMED_MFE_${String.format("%.2f", currentMfeR)}R",
                                rAtTrigger = currentMfeR
                            )
                        )
                        newStop = entryPrice
                        visualBreakEvenBars.add(i)
                    }
                }
            }

            // 2. Structural Trailing: Ratchet stop above newly confirmed swing lower highs
            if (config.enableStructuralTrailing && confirmedHighs.isNotEmpty()) {
                val lastConfirmedHigh = confirmedHighs.last()
                val potentialStructuralStop = lastConfirmedHigh.price + stopBuffer
                // Only ratchet DOWN
                if (potentialStructuralStop < newStop && lastConfirmedHigh.barIndex > 0) {
                    auditManagement.add(
                        TradeManagementAuditRecord(
                            tradeId = "trade_mgmt_short_$i",
                            eventType = TradeManagementEventType.STRUCTURAL_TRAILING_STOP,
                            barIndex = i,
                            timestamp = current.timestamp,
                            previousStop = newStop,
                            newStop = potentialStructuralStop,
                            triggeringStructure = "CONFIRMED_LOWER_HIGH_BAR_${lastConfirmedHigh.barIndex}",
                            rAtTrigger = currentMfeR
                        )
                    )
                    newStop = potentialStructuralStop
                }
            }

            if (config.exitOnCounterStructureBreak && structure == TrendlineMarketStructure.BULLISH_TREND) {
                val lastHigh = confirmedHighs.lastOrNull()?.price
                if (lastHigh != null && current.close > lastHigh) {
                    structureExitReason = ExitReason.STRUCTURE_EXIT
                    auditManagement.add(
                        TradeManagementAuditRecord(
                            tradeId = "trade_mgmt_short_$i",
                            eventType = TradeManagementEventType.COUNTER_STRUCTURE_EXIT,
                            barIndex = i,
                            timestamp = current.timestamp,
                            previousStop = newStop,
                            newStop = null,
                            triggeringStructure = structure.name,
                            rAtTrigger = currentMfeR,
                            exitReason = ExitReason.STRUCTURE_EXIT
                        )
                    )
                }
            }
        }

        return Pair(newStop, structureExitReason)
    }

    /**
     * Builds the visual debug data bundle.
     */
    fun getVisualData(candles: List<Candle>): APlusStrategyVisualData {
        val (highs, lows) = detectConfirmedSwingsUpTo(candles, candles.size - 1)
        val allSwings = (highs + lows).sortedBy { it.barIndex }

        return APlusStrategyVisualData(
            swingPoints = allSwings,
            trendlines = visualActiveTrendlines.distinctBy { it.id },
            structureStates = visualStructureMap.toMap(),
            breakBars = visualBreakBars.distinct().sorted(),
            retestBars = visualRetestBars.distinct().sorted(),
            entryBars = visualEntryBars.distinct().sorted(),
            breakEvenBars = visualBreakEvenBars.distinct().sorted()
        )
    }

    /**
     * Builds the forensic audit log bundle.
     */
    fun getForensicAuditLog(): APlusForensicAuditLog {
        return APlusForensicAuditLog(
            strategyName = APlusTrendlineConfig.STRATEGY_NAME,
            strategyVersion = APlusTrendlineConfig.STRATEGY_VERSION,
            profileName = APlusTrendlineConfig.PROFILE_NAME,
            parameters = config.toParameterMap(),
            swingAudits = auditSwings.toList(),
            trendlineAudits = auditTrendlines.toList(),
            breakAudits = auditBreaks.toList(),
            retestAudits = auditRetests.toList(),
            structuralStopAudits = auditStructuralStops.toList(),
            managementAudits = auditManagement.toList()
        )
    }
}
