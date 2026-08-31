package com.example.tradestrat.engine

import com.example.tradestrat.model.*
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * DETERMINISTIC SMC / ICT CONCEPT ENGINE
 * 
 * Implements mathematical, causal, non-lookahead rules for all 10 SMC/ICT components:
 *  1. Break of Structure (BOS)
 *  2. Change of Character / Market Structure Shift (CHOCH / MSS)
 *  3. Liquidity Sweep / Grab
 *  4. Fair Value Gap (FVG)
 *  5. Order Blocks (OB)
 *  6. Breaker Blocks
 *  7. Premium / Discount Equilibrium Zones
 *  8. Displacement Institutional Candles
 *  9. Equal Highs / Equal Lows (EQH / EQL)
 * 10. Killzone / Session Filters (London, NY, Asia)
 */
class SmcEngine(
    private val config: SmcConfig
) {
    private data class SwingPoint(
        val barIndex: Int,
        val timestamp: Long,
        val price: Double,
        val isHigh: Boolean
    )

    private val swingHighs = mutableListOf<SwingPoint>()
    private val swingLows = mutableListOf<SwingPoint>()

    private val activeFvgZones = mutableListOf<SmcZone>()
    private val activeOrderBlocks = mutableListOf<SmcZone>()
    private val activeBreakerBlocks = mutableListOf<SmcZone>()

    private var currentTrendIsBullish: Boolean? = null
    private var lastStructureHigh: SwingPoint? = null
    private var lastStructureLow: SwingPoint? = null

    // Track total detected metrics for reporting
    var bosCount = 0
        private set
    var chochCount = 0
        private set
    var sweepCount = 0
        private set
    var fvgCount = 0
        private set
    var obCount = 0
        private set
    var breakerCount = 0
        private set
    var displacementCount = 0
        private set
    var eqhEqlCount = 0
        private set
    var rawSignalsCount = 0
        private set
    var filteredSignalsCount = 0
        private set

    /**
     * Resets internal tracking state for a new backtest run.
     */
    fun reset() {
        swingHighs.clear()
        swingLows.clear()
        activeFvgZones.clear()
        activeOrderBlocks.clear()
        activeBreakerBlocks.clear()
        currentTrendIsBullish = null
        lastStructureHigh = null
        lastStructureLow = null
        bosCount = 0
        chochCount = 0
        sweepCount = 0
        fvgCount = 0
        obCount = 0
        breakerCount = 0
        displacementCount = 0
        eqhEqlCount = 0
        rawSignalsCount = 0
        filteredSignalsCount = 0
    }

    /**
     * Evaluates all enabled SMC/ICT concepts at bar [i] strictly causally.
     * Guaranteed NO lookahead: only candles up to index [i] are inspected.
     */
    fun evaluateBar(
        i: Int,
        candles: List<Candle>,
        atrList: List<Double?>
    ): SmcEvaluationResult {
        if (i < 5) {
            return SmcEvaluationResult(
                longSignal = false,
                shortSignal = false,
                rawSignalsCount = rawSignalsCount,
                confluenceCount = 0
            )
        }

        val current = candles[i]
        val prev = candles[i - 1]
        val atr = atrList.getOrNull(i) ?: ((current.high - current.low).coerceAtLeast(1.0))

        val detectedEvents = mutableListOf<SmcSignalEvent>()
        var bullishVotes = 0
        var bearishVotes = 0

        // ---------------------------------------------------------------------
        // 1. Causal Swing High & Swing Low Detection
        // ---------------------------------------------------------------------
        val lookback = max(3, if (config.useBos) config.bosLookback else if (config.useChoch) config.chochLookback else config.sweepLookback)
        val pivotCandidateIdx = i - lookback
        if (pivotCandidateIdx >= lookback) {
            val candidate = candles[pivotCandidateIdx]
            
            // Check if pivotCandidate is Swing High
            var isSwingHigh = true
            for (offset in 1..lookback) {
                if (candles[pivotCandidateIdx - offset].high > candidate.high ||
                    candles[pivotCandidateIdx + offset].high >= candidate.high) {
                    isSwingHigh = false
                    break
                }
            }
            if (isSwingHigh && swingHighs.none { it.barIndex == pivotCandidateIdx }) {
                val sw = SwingPoint(pivotCandidateIdx, candidate.timestamp, candidate.high, true)
                swingHighs.add(sw)
                lastStructureHigh = sw
            }

            // Check if pivotCandidate is Swing Low
            var isSwingLow = true
            for (offset in 1..lookback) {
                if (candles[pivotCandidateIdx - offset].low < candidate.low ||
                    candles[pivotCandidateIdx + offset].low <= candidate.low) {
                    isSwingLow = false
                    break
                }
            }
            if (isSwingLow && swingLows.none { it.barIndex == pivotCandidateIdx }) {
                val sw = SwingPoint(pivotCandidateIdx, candidate.timestamp, candidate.low, false)
                swingLows.add(sw)
                lastStructureLow = sw
            }
        }

        // ---------------------------------------------------------------------
        // 2. Equal Highs (EQH) / Equal Lows (EQL) Detection
        // ---------------------------------------------------------------------
        if (config.useEqualHighsLows) {
            if (swingHighs.size >= 2) {
                val h1 = swingHighs[swingHighs.size - 1]
                val h2 = swingHighs[swingHighs.size - 2]
                val diffPct = (abs(h1.price - h2.price) / h1.price) * 100.0
                if (diffPct <= config.eqTolerancePct && h1.barIndex == pivotCandidateIdx) {
                    eqhEqlCount++
                    detectedEvents.add(
                        SmcSignalEvent(i, current.timestamp, StructureType.EQUAL_HIGHS, TradeDirection.SHORT, h1.price, "Equal Highs detected (Liquidity Pool above)")
                    )
                }
            }
            if (swingLows.size >= 2) {
                val l1 = swingLows[swingLows.size - 1]
                val l2 = swingLows[swingLows.size - 2]
                val diffPct = (abs(l1.price - l2.price) / l1.price) * 100.0
                if (diffPct <= config.eqTolerancePct && l1.barIndex == pivotCandidateIdx) {
                    eqhEqlCount++
                    detectedEvents.add(
                        SmcSignalEvent(i, current.timestamp, StructureType.EQUAL_LOWS, TradeDirection.LONG, l1.price, "Equal Lows detected (Liquidity Pool below)")
                    )
                }
            }
        }

        // ---------------------------------------------------------------------
        // 3. Displacement Candles
        // ---------------------------------------------------------------------
        var isBullishDisplacement = false
        var isBearishDisplacement = false
        if (config.useDisplacement) {
            val body = abs(current.close - current.open)
            val range = max(0.001, current.high - current.low)
            val isExpansion = body >= atr * config.displacementAtrMultiplier && (body / range) >= 0.65

            if (isExpansion && current.close > current.open) {
                isBullishDisplacement = true
                displacementCount++
                bullishVotes++
                detectedEvents.add(
                    SmcSignalEvent(i, current.timestamp, StructureType.DISPLACEMENT_BULLISH, TradeDirection.LONG, current.close, "Institutional Bullish Displacement")
                )
            } else if (isExpansion && current.close < current.open) {
                isBearishDisplacement = true
                displacementCount++
                bearishVotes++
                detectedEvents.add(
                    SmcSignalEvent(i, current.timestamp, StructureType.DISPLACEMENT_BEARISH, TradeDirection.SHORT, current.close, "Institutional Bearish Displacement")
                )
            }
        }

        // ---------------------------------------------------------------------
        // 4. Break of Structure (BOS) & Change of Character (CHOCH / MSS)
        // ---------------------------------------------------------------------
        val recentHigh = lastStructureHigh
        val recentLow = lastStructureLow

        if (recentHigh != null && i > recentHigh.barIndex) {
            val broken = if (config.bosCloseConfirmation) (prev.close <= recentHigh.price && current.close > recentHigh.price)
                         else (prev.high <= recentHigh.price && current.high > recentHigh.price)
            if (broken) {
                val isReversal = (currentTrendIsBullish == false)
                if (isReversal && config.useChoch) {
                    // Change of Character / Market Structure Shift
                    chochCount++
                    bullishVotes++
                    detectedEvents.add(
                        SmcSignalEvent(i, current.timestamp, StructureType.BULLISH_CHOCH, TradeDirection.LONG, current.close, "Bullish CHOCH / Market Structure Shift")
                    )
                } else if (config.useBos) {
                    // Break of Structure (Trend Continuation)
                    bosCount++
                    bullishVotes++
                    detectedEvents.add(
                        SmcSignalEvent(i, current.timestamp, StructureType.BULLISH_BOS, TradeDirection.LONG, current.close, "Bullish Break of Structure (BOS)")
                    )
                }
                currentTrendIsBullish = true

                // Register potential Bullish Order Block (Last bearish candle before breakout)
                if (config.useOrderBlock) {
                    for (k in i - 1 downTo max(0, i - config.obLookback)) {
                        val c = candles[k]
                        if (c.close < c.open) {
                            val ob = SmcZone(
                                id = UUID.randomUUID().toString().take(6),
                                type = StructureType.BULLISH_ORDER_BLOCK,
                                startBarIndex = k,
                                startTimestamp = c.timestamp,
                                topPrice = c.high,
                                bottomPrice = c.low
                            )
                            activeOrderBlocks.add(ob)
                            obCount++
                            break
                        }
                    }
                }
            }
        }

        if (recentLow != null && i > recentLow.barIndex) {
            val broken = if (config.bosCloseConfirmation) (prev.close >= recentLow.price && current.close < recentLow.price)
                         else (prev.low >= recentLow.price && current.low < recentLow.price)
            if (broken) {
                val isReversal = (currentTrendIsBullish == true)
                if (isReversal && config.useChoch) {
                    // Bearish CHOCH / MSS
                    chochCount++
                    bearishVotes++
                    detectedEvents.add(
                        SmcSignalEvent(i, current.timestamp, StructureType.BEARISH_CHOCH, TradeDirection.SHORT, current.close, "Bearish CHOCH / Market Structure Shift")
                    )
                } else if (config.useBos) {
                    // Bearish BOS
                    bosCount++
                    bearishVotes++
                    detectedEvents.add(
                        SmcSignalEvent(i, current.timestamp, StructureType.BEARISH_BOS, TradeDirection.SHORT, current.close, "Bearish Break of Structure (BOS)")
                    )
                }
                currentTrendIsBullish = false

                // Register potential Bearish Order Block (Last bullish candle before breakdown)
                if (config.useOrderBlock) {
                    for (k in i - 1 downTo max(0, i - config.obLookback)) {
                        val c = candles[k]
                        if (c.close > c.open) {
                            val ob = SmcZone(
                                id = UUID.randomUUID().toString().take(6),
                                type = StructureType.BEARISH_ORDER_BLOCK,
                                startBarIndex = k,
                                startTimestamp = c.timestamp,
                                topPrice = c.high,
                                bottomPrice = c.low
                            )
                            activeOrderBlocks.add(ob)
                            obCount++
                            break
                        }
                    }
                }
            }
        }

        // ---------------------------------------------------------------------
        // 5. Liquidity Sweep / Liquidity Grab
        // ---------------------------------------------------------------------
        if (config.useLiquiditySweep) {
            if (recentHigh != null && i > recentHigh.barIndex) {
                val swept = current.high > recentHigh.price && current.close < recentHigh.price
                val wickDistance = current.high - recentHigh.price
                val minWick = (recentHigh.price * (config.sweepWickMinPct / 100.0))
                if (swept && wickDistance >= minWick) {
                    sweepCount++
                    bearishVotes++
                    detectedEvents.add(
                        SmcSignalEvent(i, current.timestamp, StructureType.BEARISH_LIQUIDITY_SWEEP, TradeDirection.SHORT, current.close, "Bearish Buy-Side Liquidity Sweep (Rejection at High)")
                    )
                }
            }

            if (recentLow != null && i > recentLow.barIndex) {
                val swept = current.low < recentLow.price && current.close > recentLow.price
                val wickDistance = recentLow.price - current.low
                val minWick = (recentLow.price * (config.sweepWickMinPct / 100.0))
                if (swept && wickDistance >= minWick) {
                    sweepCount++
                    bullishVotes++
                    detectedEvents.add(
                        SmcSignalEvent(i, current.timestamp, StructureType.BULLISH_LIQUIDITY_SWEEP, TradeDirection.LONG, current.close, "Bullish Sell-Side Liquidity Sweep (Rejection at Low)")
                    )
                }
            }
        }

        // ---------------------------------------------------------------------
        // 6. Fair Value Gap (FVG) Formation & Mitigation
        // ---------------------------------------------------------------------
        if (i >= 2) {
            val c0 = candles[i - 2]
            val c2 = candles[i]
            val gapMin = atr * config.fvgMinGapAtrMultiple

            // Bullish FVG: Low of bar i > High of bar i-2
            if (c2.low > c0.high && (c2.low - c0.high) >= gapMin) {
                val fvg = SmcZone(
                    id = UUID.randomUUID().toString().take(6),
                    type = StructureType.BULLISH_FVG,
                    startBarIndex = i,
                    startTimestamp = current.timestamp,
                    topPrice = c2.low,
                    bottomPrice = c0.high
                )
                activeFvgZones.add(fvg)
                fvgCount++
            }

            // Bearish FVG: High of bar i < Low of bar i-2
            if (c2.high < c0.low && (c0.low - c2.high) >= gapMin) {
                val fvg = SmcZone(
                    id = UUID.randomUUID().toString().take(6),
                    type = StructureType.BEARISH_FVG,
                    startBarIndex = i,
                    startTimestamp = current.timestamp,
                    topPrice = c0.low,
                    bottomPrice = c2.high
                )
                activeFvgZones.add(fvg)
                fvgCount++
            }
        }

        // FVG Retest / Mitigation evaluation
        if (config.useFvg) {
            val fvgIterator = activeFvgZones.iterator()
            while (fvgIterator.hasNext()) {
                val fvg = fvgIterator.next()
                if (fvg.startBarIndex == i) continue // Don't trigger on creation bar

                if (fvg.type == StructureType.BULLISH_FVG) {
                    val isMitigated = when (config.fvgMitigationType) {
                        FvgMitigationType.TOUCH -> current.low <= fvg.topPrice
                        FvgMitigationType.CONSEQUENT_ENCROACHMENT -> current.low <= fvg.midPrice
                        FvgMitigationType.FULL_FILL -> current.low <= fvg.bottomPrice
                    }
                    if (isMitigated) {
                        fvg.isMitigated = true
                        fvg.mitigatedBarIndex = i
                        fvgIterator.remove()
                        bullishVotes++
                        detectedEvents.add(
                            SmcSignalEvent(i, current.timestamp, StructureType.BULLISH_FVG, TradeDirection.LONG, current.close, "Bullish FVG Mitigation & Retest")
                        )
                    }
                } else if (fvg.type == StructureType.BEARISH_FVG) {
                    val isMitigated = when (config.fvgMitigationType) {
                        FvgMitigationType.TOUCH -> current.high >= fvg.bottomPrice
                        FvgMitigationType.CONSEQUENT_ENCROACHMENT -> current.high >= fvg.midPrice
                        FvgMitigationType.FULL_FILL -> current.high >= fvg.topPrice
                    }
                    if (isMitigated) {
                        fvg.isMitigated = true
                        fvg.mitigatedBarIndex = i
                        fvgIterator.remove()
                        bearishVotes++
                        detectedEvents.add(
                            SmcSignalEvent(i, current.timestamp, StructureType.BEARISH_FVG, TradeDirection.SHORT, current.close, "Bearish FVG Mitigation & Retest")
                        )
                    }
                }
            }
        }

        // ---------------------------------------------------------------------
        // 7. Order Block & Breaker Block Retest Execution
        // ---------------------------------------------------------------------
        val obIterator = activeOrderBlocks.iterator()
        while (obIterator.hasNext()) {
            val ob = obIterator.next()
            if (ob.startBarIndex == i) continue

            // Age expiration (remove stale OBs after 50 bars)
            if (i - ob.startBarIndex > 50) {
                obIterator.remove()
                continue
            }

            if (ob.type == StructureType.BULLISH_ORDER_BLOCK) {
                if (current.low <= ob.topPrice && current.close >= ob.bottomPrice) {
                    // Valid pullback retest into Order Block
                    if (config.useOrderBlock) {
                        bullishVotes++
                        detectedEvents.add(
                            SmcSignalEvent(i, current.timestamp, StructureType.BULLISH_ORDER_BLOCK, TradeDirection.LONG, current.close, "Bullish Order Block Retest")
                        )
                    }
                    obIterator.remove()
                } else if (current.close < ob.bottomPrice) {
                    // Violated Bullish OB -> transforms into Bearish Breaker Block
                    if (config.useBreakerBlock) {
                        val breaker = SmcZone(
                            id = UUID.randomUUID().toString().take(6),
                            type = StructureType.BEARISH_BREAKER_BLOCK,
                            startBarIndex = i,
                            startTimestamp = current.timestamp,
                            topPrice = ob.topPrice,
                            bottomPrice = ob.bottomPrice
                        )
                        activeBreakerBlocks.add(breaker)
                        breakerCount++
                    }
                    obIterator.remove()
                }
            } else if (ob.type == StructureType.BEARISH_ORDER_BLOCK) {
                if (current.high >= ob.bottomPrice && current.close <= ob.topPrice) {
                    if (config.useOrderBlock) {
                        bearishVotes++
                        detectedEvents.add(
                            SmcSignalEvent(i, current.timestamp, StructureType.BEARISH_ORDER_BLOCK, TradeDirection.SHORT, current.close, "Bearish Order Block Retest")
                        )
                    }
                    obIterator.remove()
                } else if (current.close > ob.topPrice) {
                    // Violated Bearish OB -> transforms into Bullish Breaker Block
                    if (config.useBreakerBlock) {
                        val breaker = SmcZone(
                            id = UUID.randomUUID().toString().take(6),
                            type = StructureType.BULLISH_BREAKER_BLOCK,
                            startBarIndex = i,
                            startTimestamp = current.timestamp,
                            topPrice = ob.topPrice,
                            bottomPrice = ob.bottomPrice
                        )
                        activeBreakerBlocks.add(breaker)
                        breakerCount++
                    }
                    obIterator.remove()
                }
            }
        }

        // Breaker Block Retest Execution
        if (config.useBreakerBlock) {
            val bbIterator = activeBreakerBlocks.iterator()
            while (bbIterator.hasNext()) {
                val bb = bbIterator.next()
                if (bb.startBarIndex == i) continue
                if (i - bb.startBarIndex > 50) {
                    bbIterator.remove()
                    continue
                }

                if (bb.type == StructureType.BULLISH_BREAKER_BLOCK && current.low <= bb.topPrice && current.close >= bb.bottomPrice) {
                    bullishVotes++
                    detectedEvents.add(
                        SmcSignalEvent(i, current.timestamp, StructureType.BULLISH_BREAKER_BLOCK, TradeDirection.LONG, current.close, "Bullish Breaker Block Support Retest")
                    )
                    bbIterator.remove()
                } else if (bb.type == StructureType.BEARISH_BREAKER_BLOCK && current.high >= bb.bottomPrice && current.close <= bb.topPrice) {
                    bearishVotes++
                    detectedEvents.add(
                        SmcSignalEvent(i, current.timestamp, StructureType.BEARISH_BREAKER_BLOCK, TradeDirection.SHORT, current.close, "Bearish Breaker Block Resistance Retest")
                    )
                    bbIterator.remove()
                }
            }
        }

        // ---------------------------------------------------------------------
        // 8. Premium / Discount Filter
        // ---------------------------------------------------------------------
        var isPermittedByDiscount = true
        var isPermittedByPremium = true
        if (config.usePremiumDiscount && recentHigh != null && recentLow != null && recentHigh.price > recentLow.price) {
            val rangeEq = (recentHigh.price + recentLow.price) / 2.0
            val isDiscount = current.close < rangeEq
            val isPremium = current.close > rangeEq

            isPermittedByDiscount = isDiscount
            isPermittedByPremium = isPremium

            if (isDiscount) {
                detectedEvents.add(
                    SmcSignalEvent(i, current.timestamp, StructureType.DISCOUNT_ZONE, TradeDirection.LONG, current.close, "Discount Zone (<50% Equilibrium)")
                )
            } else if (isPremium) {
                detectedEvents.add(
                    SmcSignalEvent(i, current.timestamp, StructureType.PREMIUM_ZONE, TradeDirection.SHORT, current.close, "Premium Zone (>50% Equilibrium)")
                )
            }
        }

        // ---------------------------------------------------------------------
        // 9. Trading Session Filter (London / NY / Asia)
        // ---------------------------------------------------------------------
        var isSessionActive = true
        if (config.useSessionFilter && config.sessionType != SmcSessionType.ALL) {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                timeInMillis = current.timestamp
            }
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            isSessionActive = hour in config.sessionType.startUtcHour until config.sessionType.endUtcHour
        }

        // ---------------------------------------------------------------------
        // 10. Confluence Evaluation & Signal Aggregation
        // ---------------------------------------------------------------------
        val requiredConfluence = if (config.requireConfluence) max(2, config.minConfluences) else 1
        var rawLong = bullishVotes >= requiredConfluence
        var rawShort = bearishVotes >= requiredConfluence

        if (rawLong || rawShort) {
            rawSignalsCount++
        }

        var isFiltered = false
        var filterReason: String? = null

        // Apply Premium/Discount Filter
        if (rawLong && config.usePremiumDiscount && !isPermittedByDiscount) {
            rawLong = false
            isFiltered = true
            filterReason = "Filtered: Long not permitted in Premium Zone (>50% Eq)"
        }
        if (rawShort && config.usePremiumDiscount && !isPermittedByPremium) {
            rawShort = false
            isFiltered = true
            filterReason = "Filtered: Short not permitted in Discount Zone (<50% Eq)"
        }

        // Apply Session Filter
        if ((rawLong || rawShort) && !isSessionActive) {
            rawLong = false
            rawShort = false
            isFiltered = true
            filterReason = "Filtered: Outside of active ${config.sessionType.displayName} session"
        }

        // Apply Direction Filter
        if (rawLong && config.tradeDirection == SmcTradeDirection.SHORT_ONLY) {
            rawLong = false
            isFiltered = true
            filterReason = "Filtered: Strategy set to Short Only"
        }
        if (rawShort && config.tradeDirection == SmcTradeDirection.LONG_ONLY) {
            rawShort = false
            isFiltered = true
            filterReason = "Filtered: Strategy set to Long Only"
        }

        if (isFiltered) {
            filteredSignalsCount++
        }

        return SmcEvaluationResult(
            longSignal = rawLong,
            shortSignal = rawShort,
            rawSignalsCount = rawSignalsCount,
            confluenceCount = max(bullishVotes, bearishVotes),
            detectedEvents = detectedEvents,
            activeZones = activeFvgZones + activeOrderBlocks + activeBreakerBlocks,
            isFiltered = isFiltered,
            filterReason = filterReason
        )
    }

    fun getMetrics(): SmcMetrics {
        return SmcMetrics(
            rawSignalsCount = rawSignalsCount,
            filteredSignalsCount = filteredSignalsCount,
            bosEventsCount = bosCount,
            chochEventsCount = chochCount,
            liquiditySweepsCount = sweepCount,
            fvgCount = fvgCount,
            orderBlocksCount = obCount,
            breakerBlocksCount = breakerCount,
            displacementCount = displacementCount,
            eqhEqlCount = eqhEqlCount
        )
    }
}
