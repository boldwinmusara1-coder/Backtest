package com.example.tradestrat.engine

import com.example.tradestrat.data.MarketDataValidator
import com.example.tradestrat.model.*
import java.util.Calendar
import java.util.TimeZone
import java.util.UUID
import kotlin.math.*

object BacktestEngine {

    /**
     * Executes a complete backtest with strictly causal indicators, realistic order execution,
     * accurate risk-based sizing, and deterministic intrabar SL/TP resolution.
     */
    fun runBacktest(
        candles: List<Candle>,
        asset: MarketAsset,
        regime: MarketRegime,
        timeframe: Timeframe,
        strategy: StrategyDefinition,
        risk: RiskParameters,
        dataSourceInfo: DataSourceInfo? = null
    ): BacktestResult {
        if (candles.size < 10) {
            return emptyResult(asset, regime, timeframe, strategy, risk, candles, dataSourceInfo)
        }

        // 1. Strictly causal indicator precalculation
        val indicators = precalculateIndicators(candles, strategy)

        // 2. State tracking
        var cash = risk.initialCapital
        var peakEquity = risk.initialCapital
        var maxDrawdownPct = 0.0
        var maxDrawdownDurationBars = 0
        var currentDrawdownDuration = 0

        val trades = mutableListOf<Trade>()
        val equityCurve = ArrayList<EquityPoint>(candles.size)
        val signalMarkers = mutableListOf<SignalMarker>()

        var activePosition: ActivePosition? = null
        val slippageRate = (risk.slippageBps / 10000.0)
        val feeRate = (risk.commissionBps / 10000.0)
        val benchmarkInitialPrice = candles.first().close

        // Realistic Execution: Pending order state generated at bar i-1 close, executed at bar i open
        data class PendingOrder(
            val direction: TradeDirection,
            val signalBarIndex: Int,
            val signalTimestamp: Long,
            val isReversal: Boolean,
            val reason: String? = null,
            val structuralStopLoss: Double? = null,
            val explanation: TradeExplanation? = null
        )
        var pendingEntryOrder: PendingOrder? = null

        // ORB Session Tracker
        val orbSessionState = OrbSessionTracker(strategy.indicatorConfig.orbParams, timeframe)

        // SMC / ICT Concepts Engine
        val smcEngine = SmcEngine(strategy.indicatorConfig.smcConfig)

        // A+ Trendline Engine
        val aPlusTrendlineEngine = APlusTrendlineEngine(strategy.indicatorConfig.aPlusTrendlineConfig)

        for (i in candles.indices) {
            val candle = candles[i]

            // =========================================================================
            // STEP 1: Process Pending Orders from Previous Bar Close (Realistic Execution)
            // =========================================================================
            if (risk.executionModel == ExecutionModel.REALISTIC && pendingEntryOrder != null) {
                val order = pendingEntryOrder!!
                pendingEntryOrder = null

                // If this is a reversal order and we still have a position, close it on next open
                if (activePosition != null && order.isReversal) {
                    val pos = activePosition!!
                    val exitPrice = if (pos.direction == TradeDirection.LONG) {
                        candle.open * (1.0 - slippageRate)
                    } else {
                        candle.open * (1.0 + slippageRate)
                    }
                    val closedTrade = closePosition(
                        pos, exitPrice, candle.timestamp, i, ExitReason.SIGNAL_REVERSAL, feeRate
                    )
                    cash += pos.marginAllocated + (closedTrade.pnlDollars + pos.entryFeePaid)
                    trades.add(closedTrade)
                    signalMarkers.add(
                        SignalMarker(
                            barIndex = i,
                            timestamp = candle.timestamp,
                            price = exitPrice,
                            direction = pos.direction,
                            isEntry = false,
                            exitReason = ExitReason.SIGNAL_REVERSAL
                        )
                    )
                    activePosition = null
                }

                // Execute New Entry at Candle Open with slippage
                if (activePosition == null) {
                    val direction = order.direction
                    val entryPrice = if (direction == TradeDirection.LONG) {
                        candle.open * (1.0 + slippageRate)
                    } else {
                        candle.open * (1.0 - slippageRate)
                    }

                    val currentEquity = cash
                    val currentAtr = indicators.atr.getOrNull(i) ?: (candle.open * 0.02)

                    val (posSizeResult, slPrice, tpPrice, isTrailing) = calculateOrderSizingAndStops(
                        equity = currentEquity,
                        risk = risk,
                        direction = direction,
                        entryPrice = entryPrice,
                        atr = currentAtr,
                        tradesHistory = trades,
                        overrideSlPrice = order.structuralStopLoss
                    )

                    if (posSizeResult != null && posSizeResult.quantity > 0 && cash >= (posSizeResult.marginAllocated + posSizeResult.entryFee)) {
                        cash -= (posSizeResult.marginAllocated + posSizeResult.entryFee)

                        val entrySlip = abs(entryPrice - candle.open) * posSizeResult.quantity
                        activePosition = ActivePosition(
                            direction = direction,
                            entryBarIndex = i,
                            entryTimestamp = candle.timestamp,
                            entryPrice = entryPrice,
                            quantity = posSizeResult.quantity,
                            positionValue = posSizeResult.positionValue,
                            marginAllocated = posSizeResult.marginAllocated,
                            stopLossPrice = slPrice,
                            takeProfitPrice = tpPrice,
                            isTrailingStop = isTrailing,
                            trailingPeakPrice = entryPrice,
                            entryFeePaid = posSizeResult.entryFee,
                            initialRiskDollars = posSizeResult.initialRiskDollars,
                            entryReason = order.reason,
                            strategyName = strategy.name,
                            strategyId = strategy.id,
                            symbol = asset.symbol,
                            timeframe = timeframe.label,
                            entrySlippageDollars = entrySlip,
                            explanation = order.explanation,
                            highestPriceSeen = entryPrice,
                            lowestPriceSeen = entryPrice
                        )

                        signalMarkers.add(
                            SignalMarker(
                                barIndex = i,
                                timestamp = candle.timestamp,
                                price = entryPrice,
                                direction = direction,
                                isEntry = true,
                                signalReason = order.reason
                            )
                        )
                    }
                }
            }

            // =========================================================================
            // STEP 2: Evaluate Active Position Exit / Stops (Intrabar & Gaps)
            // =========================================================================
            if (activePosition != null) {
                val pos = activePosition!!
                var exitTrade = false
                var exitPrice = 0.0
                var exitReason = ExitReason.STOP_LOSS

                // Check Circuit Breaker
                val currentUnrealizedEquity = calculateEquity(cash, pos, candle.close)
                val currentDd = if (peakEquity > 0) ((peakEquity - currentUnrealizedEquity) / peakEquity) * 100.0 else 0.0
                if (currentDd >= risk.maxDrawdownCircuitBreakerPct) {
                    exitTrade = true
                    exitPrice = candle.close * (if (pos.direction == TradeDirection.LONG) (1.0 - slippageRate) else (1.0 + slippageRate))
                    exitReason = ExitReason.CIRCUIT_BREAKER
                }

                if (!exitTrade) {
                    val slAtBarOpen = pos.stopLossPrice
                    val tpAtBarOpen = pos.takeProfitPrice

                    if (pos.direction == TradeDirection.LONG) {
                        // Check Gap-through-Stop or Low-through-Stop using stop active at bar start (strictly causal: no intrabar high ratchet before SL check)
                        val slHit = slAtBarOpen != null && candle.low <= slAtBarOpen
                        val tpHit = tpAtBarOpen != null && candle.high >= tpAtBarOpen

                        if (slHit && tpHit) {
                            // Intrabar SL vs TP Collision
                            val slFirst = when (risk.intrabarExecution) {
                                IntrabarExecutionAssumption.PESSIMISTIC_STOP_FIRST -> true
                                IntrabarExecutionAssumption.BAR_DIRECTION -> {
                                    if (candle.close >= candle.open) {
                                        abs(candle.open - candle.low) <= abs(candle.open - candle.high)
                                    } else {
                                        true
                                    }
                                }
                                IntrabarExecutionAssumption.OPTIMISTIC_TP_FIRST -> false
                            }

                            if (slFirst) {
                                exitTrade = true
                                exitReason = if (pos.isTrailingStop) ExitReason.TRAILING_STOP else ExitReason.STOP_LOSS
                                val isGap = slAtBarOpen != null && candle.open <= slAtBarOpen
                                exitPrice = if (isGap) {
                                    candle.open * (1.0 - slippageRate) // Gap down fill
                                } else {
                                    slAtBarOpen!! * (1.0 - slippageRate)
                                }
                            } else {
                                exitTrade = true
                                exitReason = ExitReason.TAKE_PROFIT
                                val isGap = tpAtBarOpen != null && candle.open >= tpAtBarOpen
                                exitPrice = if (isGap) {
                                    candle.open * (1.0 - slippageRate) // Gap up fill
                                } else {
                                    tpAtBarOpen!! * (1.0 - slippageRate)
                                }
                            }
                        } else if (slHit) {
                            exitTrade = true
                            exitReason = if (pos.isTrailingStop) ExitReason.TRAILING_STOP else ExitReason.STOP_LOSS
                            val isGap = slAtBarOpen != null && candle.open <= slAtBarOpen
                            exitPrice = if (isGap) {
                                candle.open * (1.0 - slippageRate) // Gap down past stop
                            } else {
                                slAtBarOpen!! * (1.0 - slippageRate)
                            }
                        } else if (tpHit) {
                            exitTrade = true
                            exitReason = ExitReason.TAKE_PROFIT
                            val isGap = tpAtBarOpen != null && candle.open >= tpAtBarOpen
                            exitPrice = if (isGap) {
                                candle.open * (1.0 - slippageRate) // Gap up past TP
                            } else {
                                tpAtBarOpen!! * (1.0 - slippageRate)
                            }
                        }
                    } else { // SHORT Position
                        // Check Gap-through-Stop or High-through-Stop using stop active at bar start
                        val slHit = slAtBarOpen != null && candle.high >= slAtBarOpen
                        val tpHit = tpAtBarOpen != null && candle.low <= tpAtBarOpen

                        if (slHit && tpHit) {
                            val slFirst = when (risk.intrabarExecution) {
                                IntrabarExecutionAssumption.PESSIMISTIC_STOP_FIRST -> true
                                IntrabarExecutionAssumption.BAR_DIRECTION -> {
                                    if (candle.close <= candle.open) {
                                        abs(candle.open - candle.high) <= abs(candle.open - candle.low)
                                    } else {
                                        true
                                    }
                                }
                                IntrabarExecutionAssumption.OPTIMISTIC_TP_FIRST -> false
                            }

                            if (slFirst) {
                                exitTrade = true
                                exitReason = if (pos.isTrailingStop) ExitReason.TRAILING_STOP else ExitReason.STOP_LOSS
                                val isGap = slAtBarOpen != null && candle.open >= slAtBarOpen
                                exitPrice = if (isGap) {
                                    candle.open * (1.0 + slippageRate) // Gap up fill
                                } else {
                                    slAtBarOpen!! * (1.0 + slippageRate)
                                }
                            } else {
                                exitTrade = true
                                exitReason = ExitReason.TAKE_PROFIT
                                val isGap = tpAtBarOpen != null && candle.open <= tpAtBarOpen
                                exitPrice = if (isGap) {
                                    candle.open * (1.0 + slippageRate) // Gap down fill
                                } else {
                                    tpAtBarOpen!! * (1.0 + slippageRate)
                                }
                            }
                        } else if (slHit) {
                            exitTrade = true
                            exitReason = if (pos.isTrailingStop) ExitReason.TRAILING_STOP else ExitReason.STOP_LOSS
                            val isGap = slAtBarOpen != null && candle.open >= slAtBarOpen
                            exitPrice = if (isGap) {
                                candle.open * (1.0 + slippageRate) // Gap up past stop
                            } else {
                                slAtBarOpen!! * (1.0 + slippageRate)
                            }
                        } else if (tpHit) {
                            exitTrade = true
                            exitReason = ExitReason.TAKE_PROFIT
                            val isGap = tpAtBarOpen != null && candle.open <= tpAtBarOpen
                            exitPrice = if (isGap) {
                                candle.open * (1.0 + slippageRate) // Gap down past TP
                            } else {
                                tpAtBarOpen!! * (1.0 + slippageRate)
                            }
                        }
                    }

                    // Intrabar Trailing Stop Ratchet:
                    // Only after candle i's stops are evaluated and position survives may the trailing stop
                    // be ratcheted using candle i's favorable extreme. This ratcheted stop becomes active starting from NEXT candle.
                    if (!exitTrade && pos.isTrailingStop) {
                        if (pos.direction == TradeDirection.LONG && candle.high > pos.trailingPeakPrice) {
                            pos.trailingPeakPrice = candle.high
                            if (risk.stopLossType == StopLossType.TRAILING_PERCENTAGE) {
                                pos.stopLossPrice = pos.trailingPeakPrice * (1.0 - (risk.stopLossValue / 100.0))
                            } else if (risk.stopLossType == StopLossType.TRAILING_ATR) {
                                val atrVal = indicators.atr.getOrNull(i) ?: (candle.close * 0.02)
                                pos.stopLossPrice = pos.trailingPeakPrice - (risk.stopLossValue * atrVal)
                            }
                        } else if (pos.direction == TradeDirection.SHORT && candle.low < pos.trailingPeakPrice) {
                            pos.trailingPeakPrice = candle.low
                            if (risk.stopLossType == StopLossType.TRAILING_PERCENTAGE) {
                                pos.stopLossPrice = pos.trailingPeakPrice * (1.0 + (risk.stopLossValue / 100.0))
                            } else if (risk.stopLossType == StopLossType.TRAILING_ATR) {
                                val atrVal = indicators.atr.getOrNull(i) ?: (candle.close * 0.02)
                                pos.stopLossPrice = pos.trailingPeakPrice + (risk.stopLossValue * atrVal)
                            }
                        }
                    }

                    // A+ Trendline Structural Management (Break-Even, Structural Trailing, Counter-Trend Exit)
                    if (!exitTrade && strategy.strategyType == StrategyType.A_PLUS_TRENDLINE) {
                        pos.highestPriceSeen = max(pos.highestPriceSeen, candle.high)
                        pos.lowestPriceSeen = min(pos.lowestPriceSeen, candle.low)

                        val (newStop, structExitReason) = aPlusTrendlineEngine.evaluateStructuralManagement(
                            candles = candles,
                            i = i,
                            direction = pos.direction,
                            entryPrice = pos.entryPrice,
                            currentStopLoss = pos.stopLossPrice,
                            initialRiskDollars = pos.initialRiskDollars,
                            highestPriceSeen = pos.highestPriceSeen,
                            lowestPriceSeen = pos.lowestPriceSeen
                        )
                        if (structExitReason != null) {
                            exitTrade = true
                            exitReason = structExitReason
                            exitPrice = if (pos.direction == TradeDirection.LONG) candle.close * (1.0 - slippageRate) else candle.close * (1.0 + slippageRate)
                        } else if (newStop != null && newStop != pos.stopLossPrice) {
                            pos.stopLossPrice = newStop
                        }
                    }
                }

                // Update runup and drawdown of open position
                val unrealizedPct = if (pos.direction == TradeDirection.LONG) {
                    ((candle.close - pos.entryPrice) / pos.entryPrice) * 100.0
                } else {
                    ((pos.entryPrice - candle.close) / pos.entryPrice) * 100.0
                }
                pos.maxRunUpPct = max(pos.maxRunUpPct, unrealizedPct)
                pos.maxDrawdownPct = min(pos.maxDrawdownPct, unrealizedPct)

                if (exitTrade) {
                    val closedTrade = closePosition(
                        pos, exitPrice, candle.timestamp, i, exitReason, feeRate
                    )
                    cash += pos.marginAllocated + (closedTrade.pnlDollars + pos.entryFeePaid)
                    trades.add(closedTrade)
                    signalMarkers.add(
                        SignalMarker(
                            barIndex = i,
                            timestamp = candle.timestamp,
                            price = exitPrice,
                            direction = pos.direction,
                            isEntry = false,
                            exitReason = exitReason
                        )
                    )
                    activePosition = null
                }
            }

            // =========================================================================
            // STEP 3: Strategy Signal Evaluation at Bar Close
            // =========================================================================
            val signalEval = evaluateSignal(strategy, i, candles, indicators, orbSessionState, smcEngine, aPlusTrendlineEngine)
            val longSignal = signalEval.longSignal
            val shortSignal = signalEval.shortSignal
            val signalReason = signalEval.reason

            if (risk.executionModel == ExecutionModel.REALISTIC) {
                // Realistic Execution: Signal confirmed at bar close -> generate order for NEXT bar open
                if (activePosition != null) {
                    val pos = activePosition!!
                    if ((pos.direction == TradeDirection.LONG && shortSignal && risk.allowShorting) ||
                        (pos.direction == TradeDirection.SHORT && longSignal)
                    ) {
                        pendingEntryOrder = PendingOrder(
                            direction = if (pos.direction == TradeDirection.LONG) TradeDirection.SHORT else TradeDirection.LONG,
                            signalBarIndex = i,
                            signalTimestamp = candle.timestamp,
                            isReversal = true,
                            reason = signalReason,
                            structuralStopLoss = signalEval.structuralStopLoss,
                            explanation = signalEval.explanation
                        )
                    }
                } else if (longSignal || (shortSignal && risk.allowShorting)) {
                    val direction = if (longSignal) TradeDirection.LONG else TradeDirection.SHORT
                    pendingEntryOrder = PendingOrder(
                        direction = direction,
                        signalBarIndex = i,
                        signalTimestamp = candle.timestamp,
                        isReversal = false,
                        reason = signalReason,
                        structuralStopLoss = signalEval.structuralStopLoss,
                        explanation = signalEval.explanation
                    )
                }
            } else {
                // Idealized Execution: Signal evaluated and filled immediately at Candle Close
                if (activePosition != null) {
                    val pos = activePosition!!
                    if ((pos.direction == TradeDirection.LONG && shortSignal) ||
                        (pos.direction == TradeDirection.SHORT && longSignal)
                    ) {
                        val exitPrice = if (pos.direction == TradeDirection.LONG) {
                            candle.close * (1.0 - slippageRate)
                        } else {
                            candle.close * (1.0 + slippageRate)
                        }
                        val closedTrade = closePosition(
                            pos, exitPrice, candle.timestamp, i, ExitReason.SIGNAL_REVERSAL, feeRate
                        )
                        cash += pos.marginAllocated + (closedTrade.pnlDollars + pos.entryFeePaid)
                        trades.add(closedTrade)
                        signalMarkers.add(
                            SignalMarker(
                                barIndex = i,
                                timestamp = candle.timestamp,
                                price = exitPrice,
                                direction = pos.direction,
                                isEntry = false,
                                exitReason = ExitReason.SIGNAL_REVERSAL
                            )
                        )
                        activePosition = null
                    }
                }

                if (activePosition == null && (longSignal || (shortSignal && risk.allowShorting))) {
                    val direction = if (longSignal) TradeDirection.LONG else TradeDirection.SHORT
                    val entryPrice = if (direction == TradeDirection.LONG) {
                        candle.close * (1.0 + slippageRate)
                    } else {
                        candle.close * (1.0 - slippageRate)
                    }

                    val currentEquity = cash
                    val currentAtr = indicators.atr.getOrNull(i) ?: (candle.close * 0.02)

                    val (posSizeResult, slPrice, tpPrice, isTrailing) = calculateOrderSizingAndStops(
                        equity = currentEquity,
                        risk = risk,
                        direction = direction,
                        entryPrice = entryPrice,
                        atr = currentAtr,
                        tradesHistory = trades,
                        overrideSlPrice = signalEval.structuralStopLoss
                    )

                    if (posSizeResult != null && posSizeResult.quantity > 0 && cash >= (posSizeResult.marginAllocated + posSizeResult.entryFee)) {
                        cash -= (posSizeResult.marginAllocated + posSizeResult.entryFee)

                        val entrySlip = abs(entryPrice - candle.close) * posSizeResult.quantity
                        activePosition = ActivePosition(
                            direction = direction,
                            entryBarIndex = i,
                            entryTimestamp = candle.timestamp,
                            entryPrice = entryPrice,
                            quantity = posSizeResult.quantity,
                            positionValue = posSizeResult.positionValue,
                            marginAllocated = posSizeResult.marginAllocated,
                            stopLossPrice = slPrice,
                            takeProfitPrice = tpPrice,
                            isTrailingStop = isTrailing,
                            trailingPeakPrice = entryPrice,
                            entryFeePaid = posSizeResult.entryFee,
                            initialRiskDollars = posSizeResult.initialRiskDollars,
                            entryReason = signalReason,
                            strategyName = strategy.name,
                            strategyId = strategy.id,
                            symbol = asset.symbol,
                            timeframe = timeframe.label,
                            entrySlippageDollars = entrySlip,
                            explanation = signalEval.explanation,
                            highestPriceSeen = entryPrice,
                            lowestPriceSeen = entryPrice
                        )

                        signalMarkers.add(
                            SignalMarker(
                                barIndex = i,
                                timestamp = candle.timestamp,
                                price = entryPrice,
                                direction = direction,
                                isEntry = true,
                                signalReason = signalReason
                            )
                        )
                    }
                }
            }

            // =========================================================================
            // STEP 4: Mark-To-Market Equity Accounting
            // =========================================================================
            val barEquity = calculateEquity(cash, activePosition, candle.close)
            if (barEquity >= peakEquity) {
                peakEquity = barEquity
                currentDrawdownDuration = 0
            } else {
                currentDrawdownDuration++
                maxDrawdownDurationBars = max(maxDrawdownDurationBars, currentDrawdownDuration)
            }

            val currentDdPct = if (peakEquity > 0) ((peakEquity - barEquity) / peakEquity) * 100.0 else 0.0
            maxDrawdownPct = max(maxDrawdownPct, currentDdPct)
            val benchmarkEquity = (candle.close / benchmarkInitialPrice) * risk.initialCapital

            equityCurve.add(
                EquityPoint(
                    barIndex = i,
                    timestamp = candle.timestamp,
                    equity = barEquity,
                    cash = cash,
                    drawdownPct = currentDdPct,
                    benchmarkEquity = benchmarkEquity,
                    price = candle.close
                )
            )
        }

        // Close any remaining active position at final bar close BEFORE computing metrics
        if (activePosition != null) {
            val lastCandle = candles.last()
            val pos = activePosition!!
            val exitPrice = lastCandle.close * (if (pos.direction == TradeDirection.LONG) (1.0 - slippageRate) else (1.0 + slippageRate))
            val closedTrade = closePosition(
                pos, exitPrice, lastCandle.timestamp, candles.size - 1, ExitReason.END_OF_DATA, feeRate
            )
            cash += pos.marginAllocated + (closedTrade.pnlDollars + pos.entryFeePaid)
            trades.add(closedTrade)
            activePosition = null

            // Update the final equity point so final equity and net profit reflect the closed trade's realized PnL
            if (equityCurve.isNotEmpty()) {
                val lastIdx = equityCurve.size - 1
                val lastPt = equityCurve[lastIdx]
                val finalDdPct = if (peakEquity > 0) (((peakEquity - cash) / peakEquity) * 100.0).coerceAtLeast(0.0) else 0.0
                maxDrawdownPct = max(maxDrawdownPct, finalDdPct)
                equityCurve[lastIdx] = lastPt.copy(
                    equity = cash,
                    cash = cash,
                    drawdownPct = finalDdPct
                )
            }
        }

        // Compute performance metrics
        val metrics = computeMetrics(
            initialCapital = risk.initialCapital,
            equityCurve = equityCurve,
            trades = trades,
            candles = candles,
            maxDrawdownPct = maxDrawdownPct,
            maxDrawdownDurationBars = maxDrawdownDurationBars,
            timeframe = timeframe,
            assetCategory = asset.category
        )

        val calculatedHash = MarketDataValidator.computeDataHash(candles)
        val dsInfo = (dataSourceInfo ?: DataSourceInfo(
            provider = "Real Historical API",
            symbol = asset.symbol,
            market = asset.category.label,
            timeframe = timeframe.label,
            startDate = if (candles.isNotEmpty()) candles.first().formattedDate(timeframe.minutes) else "",
            endDate = if (candles.isNotEmpty()) candles.last().formattedDate(timeframe.minutes) else "",
            startTimestamp = candles.firstOrNull()?.timestamp ?: 0L,
            endTimestamp = candles.lastOrNull()?.timestamp ?: 0L,
            candleCount = candles.size,
            isRealHistorical = true,
            intrabarExecutionRule = risk.intrabarExecution.label,
            executionModel = risk.executionModel.label,
            dataHash = calculatedHash,
            datasetId = "${asset.symbol}_${timeframe.name}_${candles.firstOrNull()?.timestamp}_${candles.lastOrNull()?.timestamp}"
        )).let {
            if (it.dataHash.isEmpty()) it.copy(dataHash = calculatedHash) else it
        }

        return BacktestResult(
            id = UUID.randomUUID().toString(),
            asset = asset,
            regime = regime,
            timeframe = timeframe,
            strategy = strategy,
            riskParams = risk,
            candles = candles,
            indicators = indicators,
            trades = trades,
            equityCurve = equityCurve,
            signalMarkers = signalMarkers,
            metrics = metrics,
            smcMetrics = if (strategy.strategyType == StrategyType.SMC_CONCEPTS || strategy.strategyType == StrategyType.ICT_CONCEPTS || strategy.strategyType == StrategyType.SMC_ICT_CONCEPTS) smcEngine.getMetrics() else null,
            aPlusVisualData = if (strategy.strategyType == StrategyType.A_PLUS_TRENDLINE) aPlusTrendlineEngine.getVisualData(candles) else null,
            aPlusForensicAuditLog = if (strategy.strategyType == StrategyType.A_PLUS_TRENDLINE) aPlusTrendlineEngine.getForensicAuditLog() else null,
            dataSource = dsInfo
        )
    }

    private data class ActivePosition(
        val direction: TradeDirection,
        val entryBarIndex: Int,
        val entryTimestamp: Long,
        val entryPrice: Double,
        val quantity: Double,
        val positionValue: Double,
        val marginAllocated: Double,
        var stopLossPrice: Double?,
        var takeProfitPrice: Double?,
        val isTrailingStop: Boolean,
        var trailingPeakPrice: Double,
        val entryFeePaid: Double,
        val initialRiskDollars: Double,
        var maxRunUpPct: Double = 0.0,
        var maxDrawdownPct: Double = 0.0,
        val entryReason: String? = null,
        val strategyName: String? = null,
        val strategyId: String? = null,
        val symbol: String? = null,
        val timeframe: String? = null,
        val entrySlippageDollars: Double = 0.0,
        val explanation: TradeExplanation? = null,
        var highestPriceSeen: Double = entryPrice,
        var lowestPriceSeen: Double = entryPrice
    )

    data class PositionSizingResult(
        val quantity: Double,
        val positionValue: Double,
        val marginAllocated: Double,
        val entryFee: Double,
        val initialRiskDollars: Double
    )

    /**
     * Precise Mark-To-Market Accounting:
     * equity = cash + marginAllocated + unrealizedPnl
     */
    private fun calculateEquity(cash: Double, pos: ActivePosition?, currentPrice: Double): Double {
        if (pos == null) return cash
        val unrealizedGrossPnl = if (pos.direction == TradeDirection.LONG) {
            (currentPrice - pos.entryPrice) * pos.quantity
        } else {
            (pos.entryPrice - currentPrice) * pos.quantity
        }
        return cash + pos.marginAllocated + unrealizedGrossPnl
    }

    /**
     * Closes an active position and calculates realized PnL, fees, and R-multiples.
     */
    private fun closePosition(
        pos: ActivePosition,
        exitPrice: Double,
        exitTimestamp: Long,
        exitBarIndex: Int,
        reason: ExitReason,
        feeRate: Double
    ): Trade {
        val grossPnl = if (pos.direction == TradeDirection.LONG) {
            (exitPrice - pos.entryPrice) * pos.quantity
        } else {
            (pos.entryPrice - exitPrice) * pos.quantity
        }
        val exitFee = (exitPrice * pos.quantity) * feeRate
        val totalFees = pos.entryFeePaid + exitFee
        val netPnlDollars = grossPnl - totalFees
        val netPnlPercent = if (pos.marginAllocated > 0) (netPnlDollars / pos.marginAllocated) * 100.0 else 0.0
        val rMultiple = if (pos.initialRiskDollars > 0) netPnlDollars / pos.initialRiskDollars else 0.0
        val exitSlippageDollars = 0.0 // Incorporated directly in exitPrice execution
        val totalSlippage = pos.entrySlippageDollars + exitSlippageDollars

        return Trade(
            id = UUID.randomUUID().toString().take(8),
            barIndex = pos.entryBarIndex,
            exitBarIndex = exitBarIndex,
            entryTimestamp = pos.entryTimestamp,
            exitTimestamp = exitTimestamp,
            direction = pos.direction,
            entryPrice = pos.entryPrice,
            exitPrice = exitPrice,
            quantity = pos.quantity,
            positionValue = pos.positionValue,
            pnlDollars = netPnlDollars,
            pnlPercent = netPnlPercent,
            exitReason = reason,
            feesPaid = totalFees,
            rMultiple = rMultiple,
            holdingBars = max(1, exitBarIndex - pos.entryBarIndex),
            maxRunUpPct = pos.maxRunUpPct,
            maxDrawdownPct = pos.maxDrawdownPct,
            entryReason = pos.entryReason,
            stopLossPrice = pos.stopLossPrice,
            takeProfitPrice = pos.takeProfitPrice,
            strategyName = pos.strategyName,
            strategyId = pos.strategyId,
            symbol = pos.symbol,
            timeframe = pos.timeframe,
            grossPnlDollars = grossPnl,
            slippagePaid = totalSlippage,
            explanation = pos.explanation,
            result = if (netPnlDollars > 0) "WIN" else if (netPnlDollars < 0) "LOSS" else "BREAKEVEN"
        )
    }

    /**
     * Calculates stops, targets, and precise position sizing adhering strictly to user risk %.
     */
    fun calculateOrderSizingAndStops(
        equity: Double,
        risk: RiskParameters,
        direction: TradeDirection,
        entryPrice: Double,
        atr: Double,
        tradesHistory: List<Trade>,
        overrideSlPrice: Double? = null
    ): Tuple4<PositionSizingResult?, Double?, Double?, Boolean> {
        val (calcSlPrice, tpPrice, isTrailing) = calculateStopsAndTargets(direction, entryPrice, atr, risk)
        val slPrice = overrideSlPrice ?: calcSlPrice
        val feeRate = (risk.commissionBps / 10000.0)

        if (equity <= 10.0 || entryPrice <= 0.0) {
            return Tuple4(null, slPrice, tpPrice, isTrailing)
        }

        val maxAllowedMargin = equity * 0.98

        val positionValue: Double
        val initialRiskDollars: Double

        when (risk.positionSizingMode) {
            PositionSizingMode.RISK_BASED -> {
                // Risk configured % of account equity (e.g. 1% of $10,000 = $100)
                val riskPct = (risk.positionSizeValue / 100.0).coerceIn(0.001, 0.20)
                val riskAmount = equity * riskPct
                initialRiskDollars = riskAmount

                val stopDistance = if (slPrice != null) abs(entryPrice - slPrice) else entryPrice * 0.03
                if (stopDistance <= 0.0) {
                    return Tuple4(null, slPrice, tpPrice, isTrailing)
                }

                // Quantity = Risk Amount / Stop Distance
                val desiredQuantity = riskAmount / stopDistance
                val desiredValue = desiredQuantity * entryPrice
                val desiredMargin = desiredValue / risk.leverage

                // Cap by available equity
                if (desiredMargin > maxAllowedMargin) {
                    val cappedValue = maxAllowedMargin * risk.leverage
                    positionValue = cappedValue
                } else {
                    positionValue = desiredValue
                }
            }

            PositionSizingMode.PERCENT_EQUITY -> {
                val pct = (risk.positionSizeValue / 100.0).coerceIn(0.01, 1.0)
                val margin = min(maxAllowedMargin, equity * pct)
                positionValue = margin * risk.leverage
                initialRiskDollars = if (slPrice != null) {
                    abs(entryPrice - slPrice) * (positionValue / entryPrice)
                } else {
                    margin * 0.03
                }
            }

            PositionSizingMode.FIXED_DOLLAR -> {
                val margin = min(maxAllowedMargin, risk.positionSizeValue)
                positionValue = margin * risk.leverage
                initialRiskDollars = if (slPrice != null) {
                    abs(entryPrice - slPrice) * (positionValue / entryPrice)
                } else {
                    margin * 0.03
                }
            }

            PositionSizingMode.KELLY_CRITERION -> {
                if (tradesHistory.size >= 8) {
                    val wins = tradesHistory.filter { it.isWin }
                    val winRate = wins.size.toDouble() / tradesHistory.size
                    val avgWin = if (wins.isNotEmpty()) wins.map { it.pnlDollars }.average() else 1.0
                    val losses = tradesHistory.filter { !it.isWin }
                    val avgLoss = if (losses.isNotEmpty()) abs(losses.map { it.pnlDollars }.average()) else 1.0
                    val b = if (avgLoss > 0) avgWin / avgLoss else 1.0
                    val kelly = if (b > 0) (winRate * (b + 1.0) - 1.0) / b else 0.1
                    val halfKelly = (kelly / 2.0).coerceIn(0.02, 0.50)
                    val margin = min(maxAllowedMargin, equity * halfKelly)
                    positionValue = margin * risk.leverage
                } else {
                    val margin = min(maxAllowedMargin, equity * 0.15)
                    positionValue = margin * risk.leverage
                }
                initialRiskDollars = if (slPrice != null) {
                    abs(entryPrice - slPrice) * (positionValue / entryPrice)
                } else {
                    (positionValue / risk.leverage) * 0.03
                }
            }
        }

        val quantity = positionValue / entryPrice
        val marginAllocated = positionValue / risk.leverage
        val entryFee = positionValue * feeRate

        // Recalculate actualInitialRisk based on actual (possibly capped) position size
        val actualInitialRisk = if (slPrice != null) {
            abs(entryPrice - slPrice) * quantity
        } else {
            (positionValue / risk.leverage) * 0.03
        }

        val result = PositionSizingResult(
            quantity = quantity,
            positionValue = positionValue,
            marginAllocated = marginAllocated,
            entryFee = entryFee,
            initialRiskDollars = actualInitialRisk
        )

        return Tuple4(result, slPrice, tpPrice, isTrailing)
    }

    data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    private fun calculateStopsAndTargets(
        direction: TradeDirection,
        entryPrice: Double,
        atr: Double,
        risk: RiskParameters
    ): Triple<Double?, Double?, Boolean> {
        var slPrice: Double? = null
        var isTrailing = false

        when (risk.stopLossType) {
            StopLossType.NONE -> slPrice = null
            StopLossType.PERCENTAGE -> {
                val offset = entryPrice * (risk.stopLossValue / 100.0)
                slPrice = if (direction == TradeDirection.LONG) entryPrice - offset else entryPrice + offset
            }
            StopLossType.ATR_MULTIPLE -> {
                val offset = atr * risk.stopLossValue
                slPrice = if (direction == TradeDirection.LONG) entryPrice - offset else entryPrice + offset
            }
            StopLossType.TRAILING_PERCENTAGE -> {
                val offset = entryPrice * (risk.stopLossValue / 100.0)
                slPrice = if (direction == TradeDirection.LONG) entryPrice - offset else entryPrice + offset
                isTrailing = true
            }
            StopLossType.TRAILING_ATR -> {
                val offset = atr * risk.stopLossValue
                slPrice = if (direction == TradeDirection.LONG) entryPrice - offset else entryPrice + offset
                isTrailing = true
            }
        }

        var tpPrice: Double? = null
        when (risk.takeProfitType) {
            TakeProfitType.NONE -> tpPrice = null
            TakeProfitType.PERCENTAGE -> {
                val offset = entryPrice * (risk.takeProfitValue / 100.0)
                tpPrice = if (direction == TradeDirection.LONG) entryPrice + offset else entryPrice - offset
            }
            TakeProfitType.RISK_REWARD_RATIO -> {
                if (slPrice != null) {
                    val slDistance = abs(entryPrice - slPrice)
                    val tpDistance = slDistance * risk.takeProfitValue
                    tpPrice = if (direction == TradeDirection.LONG) entryPrice + tpDistance else entryPrice - tpDistance
                } else {
                    val offset = entryPrice * 0.05
                    tpPrice = if (direction == TradeDirection.LONG) entryPrice + offset else entryPrice - offset
                }
            }
            TakeProfitType.ATR_MULTIPLE -> {
                val offset = atr * risk.takeProfitValue
                tpPrice = if (direction == TradeDirection.LONG) entryPrice + offset else entryPrice - offset
            }
        }

        return Triple(slPrice, tpPrice, isTrailing)
    }

    private fun precalculateIndicators(candles: List<Candle>, strategy: StrategyDefinition): CalculatedIndicators {
        val cfg = strategy.indicatorConfig

        val fastMa = when (strategy.strategyType) {
            StrategyType.OPENING_RANGE_BREAKOUT -> {
                if (cfg.orbParams.useEmaTrendFilter) IndicatorCalculators.calculateEMA(candles, cfg.orbParams.emaTrendPeriod)
                else IndicatorCalculators.calculateSMA(candles, cfg.maParams.fastPeriod)
            }
            else -> {
                if (cfg.maParams.useEma) IndicatorCalculators.calculateEMA(candles, cfg.maParams.fastPeriod)
                else IndicatorCalculators.calculateSMA(candles, cfg.maParams.fastPeriod)
            }
        }

        val slowMa = when (strategy.strategyType) {
            StrategyType.TRENDLINE_BREAK, StrategyType.TRENDLINE_BOUNCE -> {
                if (cfg.trendlineParams.useMaTrendFilter) IndicatorCalculators.calculateEMA(candles, cfg.trendlineParams.maTrendPeriod)
                else IndicatorCalculators.calculateSMA(candles, cfg.maParams.slowPeriod)
            }
            else -> {
                if (cfg.maParams.useEma) IndicatorCalculators.calculateEMA(candles, cfg.maParams.slowPeriod)
                else IndicatorCalculators.calculateSMA(candles, cfg.maParams.slowPeriod)
            }
        }

        val rsiPeriod = when (strategy.strategyType) {
            StrategyType.OPENING_RANGE_BREAKOUT -> if (cfg.orbParams.useRsiFilter) cfg.orbParams.rsiPeriod else cfg.rsiParams.period
            StrategyType.TRENDLINE_BREAK, StrategyType.TRENDLINE_BOUNCE -> if (cfg.trendlineParams.useRsiFilter) cfg.trendlineParams.rsiPeriod else cfg.rsiParams.period
            else -> cfg.rsiParams.period
        }
        val rsi = IndicatorCalculators.calculateRSI(candles, rsiPeriod)

        val macd = IndicatorCalculators.calculateMACD(
            candles,
            cfg.macdParams.fastPeriod,
            cfg.macdParams.slowPeriod,
            cfg.macdParams.signalPeriod
        )

        val bb = IndicatorCalculators.calculateBollingerBands(
            candles,
            cfg.bollingerParams.period,
            cfg.bollingerParams.stdDevMultiplier
        )

        val atr = IndicatorCalculators.calculateATR(candles, cfg.supertrendParams.atrPeriod)
        val supertrend = IndicatorCalculators.calculateSupertrend(
            candles,
            cfg.supertrendParams.atrPeriod,
            cfg.supertrendParams.multiplier
        )

        val donchian = IndicatorCalculators.calculateDonchian(candles, cfg.donchianParams.period)

        return CalculatedIndicators(
            fastMa = fastMa,
            slowMa = slowMa,
            rsi = rsi,
            macdLine = macd.macdLine,
            macdSignal = macd.signalLine,
            macdHist = macd.histogram,
            bbUpper = bb.upper,
            bbMiddle = bb.middle,
            bbLower = bb.lower,
            atr = atr,
            supertrend = supertrend,
            donchianUpper = donchian.upper,
            donchianLower = donchian.lower
        )
    }

    /**
     * Session tracker for Opening Range Breakout (ORB) using actual candle timestamps.
     */
    class OrbSessionTracker(
        private val params: OrbParams,
        private val timeframe: Timeframe
    ) {
        private val timeZone = try {
            TimeZone.getTimeZone(params.sessionTimezone)
        } catch (e: Exception) {
            TimeZone.getTimeZone("UTC")
        }

        private var currentSessionDate: String = ""
        private var sessionCandles = mutableListOf<Candle>()
        var orbHigh: Double? = null
        var orbLow: Double? = null
        var isOpeningRangeComplete: Boolean = false
        var isWithinTradingSession: Boolean = false

        fun update(candle: Candle, barIndex: Int) {
            val cal = Calendar.getInstance(timeZone).apply {
                timeInMillis = candle.timestamp
            }
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH) + 1
            val day = cal.get(Calendar.DAY_OF_MONTH)
            val dateStr = "$year-$month-$day"
            val minuteOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

            val sessionStartMin = params.sessionStartHour * 60 + params.sessionStartMinute
            val sessionEndMin = params.sessionEndHour * 60 + params.sessionEndMinute
            val orbEndMin = sessionStartMin + params.openingRangeMinutes

            // Daily reset if timestamp is intraday
            if (dateStr != currentSessionDate) {
                currentSessionDate = dateStr
                sessionCandles.clear()
                orbHigh = null
                orbLow = null
                isOpeningRangeComplete = false
                isWithinTradingSession = false
            }

            // Check if timeframe is daily/higher or if intraday minutes apply
            if (timeframe.minutes >= 1440) {
                // On daily timeframe, use rolling opening range bars
                isWithinTradingSession = true
                isOpeningRangeComplete = true
                orbHigh = candle.high
                orbLow = candle.low
                return
            }

            if (minuteOfDay in sessionStartMin until sessionEndMin) {
                isWithinTradingSession = true
                if (minuteOfDay < orbEndMin) {
                    sessionCandles.add(candle)
                } else {
                    if (!isOpeningRangeComplete && sessionCandles.isNotEmpty()) {
                        orbHigh = sessionCandles.maxOf { it.high }
                        orbLow = sessionCandles.minOf { it.low }
                        isOpeningRangeComplete = true
                    }
                }
            } else {
                isWithinTradingSession = false
            }
        }
    }

    data class SignalEvaluation(
        val longSignal: Boolean,
        val shortSignal: Boolean,
        val reason: String? = null,
        val structuralStopLoss: Double? = null,
        val explanation: TradeExplanation? = null
    )

    /**
     * Strictly causal signal evaluation.
     */
    private fun evaluateSignal(
        strategy: StrategyDefinition,
        i: Int,
        candles: List<Candle>,
        ind: CalculatedIndicators,
        orbTracker: OrbSessionTracker,
        smcEngine: SmcEngine? = null,
        aPlusTrendlineEngine: APlusTrendlineEngine? = null
    ): SignalEvaluation {
        if (i < 2) return SignalEvaluation(false, false)

        val current = candles[i]
        val prev = candles[i - 1]

        // Update ORB tracker
        orbTracker.update(current, i)

        when (strategy.strategyType) {
            StrategyType.MA_CROSSOVER -> {
                val fastCurr = ind.fastMa.getOrNull(i) ?: return SignalEvaluation(false, false)
                val fastPrev = ind.fastMa.getOrNull(i - 1) ?: return SignalEvaluation(false, false)
                val slowCurr = ind.slowMa.getOrNull(i) ?: return SignalEvaluation(false, false)
                val slowPrev = ind.slowMa.getOrNull(i - 1) ?: return SignalEvaluation(false, false)

                val longSignal = fastPrev <= slowPrev && fastCurr > slowCurr
                val shortSignal = fastPrev >= slowPrev && fastCurr < slowCurr
                val reason = if (longSignal) "Fast MA Bullish Crossover" else if (shortSignal) "Fast MA Bearish Crossover" else null
                return SignalEvaluation(longSignal, shortSignal, reason)
            }

            StrategyType.RSI_MEAN_REVERSION -> {
                val rsiCurr = ind.rsi.getOrNull(i) ?: return SignalEvaluation(false, false)
                val rsiPrev = ind.rsi.getOrNull(i - 1) ?: return SignalEvaluation(false, false)
                val oversold = strategy.indicatorConfig.rsiParams.oversoldThreshold
                val overbought = strategy.indicatorConfig.rsiParams.overboughtThreshold

                val longSignal = rsiPrev <= oversold && rsiCurr > oversold
                val shortSignal = rsiPrev >= overbought && rsiCurr < overbought
                val reason = if (longSignal) "RSI Oversold Rebound (<$oversold)" else if (shortSignal) "RSI Overbought Pullback (>$overbought)" else null
                return SignalEvaluation(longSignal, shortSignal, reason)
            }

            StrategyType.MACD_MOMENTUM -> {
                val macdCurr = ind.macdLine.getOrNull(i) ?: return SignalEvaluation(false, false)
                val macdPrev = ind.macdLine.getOrNull(i - 1) ?: return SignalEvaluation(false, false)
                val sigCurr = ind.macdSignal.getOrNull(i) ?: return SignalEvaluation(false, false)
                val sigPrev = ind.macdSignal.getOrNull(i - 1) ?: return SignalEvaluation(false, false)

                val longSignal = macdPrev <= sigPrev && macdCurr > sigCurr
                val shortSignal = macdPrev >= sigPrev && macdCurr < sigCurr
                val reason = if (longSignal) "MACD Bullish Signal Line Cross" else if (shortSignal) "MACD Bearish Signal Line Cross" else null
                return SignalEvaluation(longSignal, shortSignal, reason)
            }

            StrategyType.BOLLINGER_REVERSION -> {
                val bbLowerCurr = ind.bbLower.getOrNull(i) ?: return SignalEvaluation(false, false)
                val bbLowerPrev = ind.bbLower.getOrNull(i - 1) ?: return SignalEvaluation(false, false)
                val bbUpperCurr = ind.bbUpper.getOrNull(i) ?: return SignalEvaluation(false, false)
                val bbUpperPrev = ind.bbUpper.getOrNull(i - 1) ?: return SignalEvaluation(false, false)

                val longSignal = prev.low <= bbLowerPrev && current.close > bbLowerCurr
                val shortSignal = prev.high >= bbUpperPrev && current.close < bbUpperCurr
                val reason = if (longSignal) "Bollinger Lower Band Rejection" else if (shortSignal) "Bollinger Upper Band Rejection" else null
                return SignalEvaluation(longSignal, shortSignal, reason)
            }

            StrategyType.BOLLINGER_BREAKOUT -> {
                val bbUpperCurr = ind.bbUpper.getOrNull(i) ?: return SignalEvaluation(false, false)
                val bbUpperPrev = ind.bbUpper.getOrNull(i - 1) ?: return SignalEvaluation(false, false)
                val bbLowerCurr = ind.bbLower.getOrNull(i) ?: return SignalEvaluation(false, false)
                val bbLowerPrev = ind.bbLower.getOrNull(i - 1) ?: return SignalEvaluation(false, false)

                val longSignal = prev.close <= bbUpperPrev && current.close > bbUpperCurr
                val shortSignal = prev.close >= bbLowerPrev && current.close < bbLowerCurr
                val reason = if (longSignal) "Bollinger Upper Band Volatility Breakout" else if (shortSignal) "Bollinger Lower Band Volatility Breakdown" else null
                return SignalEvaluation(longSignal, shortSignal, reason)
            }

            StrategyType.SUPERTREND_RUN -> {
                val stCurr = ind.supertrend.getOrNull(i) ?: return SignalEvaluation(false, false)
                val stPrev = ind.supertrend.getOrNull(i - 1) ?: return SignalEvaluation(false, false)

                val longSignal = prev.close <= stPrev && current.close > stCurr
                val shortSignal = prev.close >= stPrev && current.close < stCurr
                val reason = if (longSignal) "Supertrend Bullish Flip" else if (shortSignal) "Supertrend Bearish Flip" else null
                return SignalEvaluation(longSignal, shortSignal, reason)
            }

            StrategyType.TURTLE_BREAKOUT -> {
                val upperPrev = ind.donchianUpper.getOrNull(i) ?: return SignalEvaluation(false, false)
                val lowerPrev = ind.donchianLower.getOrNull(i) ?: return SignalEvaluation(false, false)

                val longSignal = current.high > upperPrev
                val shortSignal = current.low < lowerPrev
                val reason = if (longSignal) "Donchian 20-Period High Breakout" else if (shortSignal) "Donchian 20-Period Low Breakdown" else null
                return SignalEvaluation(longSignal, shortSignal, reason)
            }

            StrategyType.OPENING_RANGE_BREAKOUT -> {
                val orbParams = strategy.indicatorConfig.orbParams

                // Timestamp-driven ORB evaluation
                val orbHigh = orbTracker.orbHigh
                val orbLow = orbTracker.orbLow

                if (orbHigh == null || orbLow == null || !orbTracker.isOpeningRangeComplete || !orbTracker.isWithinTradingSession) {
                    return SignalEvaluation(false, false)
                }

                val recentVolAvg = candles.subList((i - 5).coerceAtLeast(0), i).map { it.volume }.average().coerceAtLeast(1.0)
                val volThreshold = recentVolAvg * orbParams.volumeMultiplier

                val bufferFactor = 1.0 + (orbParams.breakoutBufferPct / 100.0)
                val bufferFactorShort = 1.0 - (orbParams.breakoutBufferPct / 100.0)

                var longSignal = prev.close <= orbHigh && current.close > (orbHigh * bufferFactor) && current.volume >= volThreshold
                var shortSignal = prev.close >= orbLow && current.close < (orbLow * bufferFactorShort) && current.volume >= volThreshold

                // EMA Filter
                if (orbParams.useEmaTrendFilter) {
                    val ema = ind.fastMa.getOrNull(i) ?: current.close
                    longSignal = longSignal && current.close > ema
                    shortSignal = shortSignal && current.close < ema
                }

                // RSI Filter
                if (orbParams.useRsiFilter) {
                    val rsi = ind.rsi.getOrNull(i) ?: 50.0
                    val longThresh = if (orbParams.rsiLongThreshold != 50.0 || orbParams.rsiShortThreshold != 50.0) orbParams.rsiLongThreshold else orbParams.rsiThreshold
                    val shortThresh = if (orbParams.rsiLongThreshold != 50.0 || orbParams.rsiShortThreshold != 50.0) orbParams.rsiShortThreshold else (100.0 - orbParams.rsiThreshold)
                    longSignal = longSignal && rsi >= longThresh
                    shortSignal = shortSignal && rsi <= shortThresh
                }

                val reason = if (longSignal) "ORB High Breakout + Volume Confirmation" else if (shortSignal) "ORB Low Breakdown + Volume Confirmation" else null
                return SignalEvaluation(longSignal, shortSignal, reason)
            }

            StrategyType.TRENDLINE_BREAK -> {
                val tlParams = strategy.indicatorConfig.trendlineParams
                val strength = tlParams.pivotStrength.coerceIn(3, 15)
                val minTouches = tlParams.minTouches.coerceAtLeast(2)
                val maxAge = tlParams.maxLineAge.coerceIn(20, 300)

                // Causal swing pivot high detection: Pivot at p is confirmed at p + strength <= i
                val confirmedHighs = mutableListOf<Pair<Int, Double>>() // (barIndex, price)
                val confirmedLows = mutableListOf<Pair<Int, Double>>()

                val maxSearchStart = max(0, i - maxAge)
                val maxSearchEnd = i - strength

                for (p in maxSearchStart..maxSearchEnd) {
                    if (p >= strength) {
                        // Check if p is a swing high
                        val candidateHigh = candles[p].high
                        var isHigh = true
                        for (k in (p - strength)..(p + strength)) {
                            if (k != p && candles[k].high > candidateHigh) {
                                isHigh = false
                                break
                            }
                        }
                        if (isHigh) confirmedHighs.add(Pair(p, candidateHigh))

                        // Check if p is a swing low
                        val candidateLow = candles[p].low
                        var isLow = true
                        for (k in (p - strength)..(p + strength)) {
                            if (k != p && candles[k].low < candidateLow) {
                                isLow = false
                                break
                            }
                        }
                        if (isLow) confirmedLows.add(Pair(p, candidateLow))
                    }
                }

                var longSignal = false
                val resistanceLine = findValidResistanceTrendline(
                    confirmedHighs = confirmedHighs,
                    candles = candles,
                    currentBarIndex = i,
                    minTouches = minTouches,
                    tolerancePct = tlParams.retestTolerancePct
                )
                if (resistanceLine != null) {
                    val linePrice = resistanceLine.linePriceAtCurrent
                    val confirmMultiplier = 1.0 + (tlParams.confirmationThresholdPct / 100.0)
                    if (resistanceLine.slope <= 0.005 * resistanceLine.p2.second) {
                        longSignal = prev.close <= linePrice && current.close >= linePrice * confirmMultiplier
                    }
                }

                var shortSignal = false
                val supportLine = findValidSupportTrendline(
                    confirmedLows = confirmedLows,
                    candles = candles,
                    currentBarIndex = i,
                    minTouches = minTouches,
                    tolerancePct = tlParams.retestTolerancePct
                )
                if (supportLine != null) {
                    val linePrice = supportLine.linePriceAtCurrent
                    val confirmMultiplier = 1.0 - (tlParams.confirmationThresholdPct / 100.0)
                    if (supportLine.slope >= -0.005 * supportLine.p2.second) {
                        shortSignal = prev.close >= linePrice && current.close <= linePrice * confirmMultiplier
                    }
                }

                val reason = if (longSignal) "Resistance Trendline Breakout" else if (shortSignal) "Support Trendline Breakdown" else null
                return SignalEvaluation(longSignal, shortSignal, reason)
            }

            StrategyType.TRENDLINE_BOUNCE -> {
                val tlParams = strategy.indicatorConfig.trendlineParams
                val strength = tlParams.pivotStrength.coerceIn(3, 15)
                val minTouches = tlParams.minTouches.coerceAtLeast(2)
                val maxAge = tlParams.maxLineAge.coerceIn(20, 300)

                val confirmedHighs = mutableListOf<Pair<Int, Double>>()
                val confirmedLows = mutableListOf<Pair<Int, Double>>()

                val maxSearchStart = max(0, i - maxAge)
                val maxSearchEnd = i - strength

                for (p in maxSearchStart..maxSearchEnd) {
                    if (p >= strength) {
                        val candidateHigh = candles[p].high
                        var isHigh = true
                        for (k in (p - strength)..(p + strength)) {
                            if (k != p && candles[k].high > candidateHigh) {
                                isHigh = false
                                break
                            }
                        }
                        if (isHigh) confirmedHighs.add(Pair(p, candidateHigh))

                        val candidateLow = candles[p].low
                        var isLow = true
                        for (k in (p - strength)..(p + strength)) {
                            if (k != p && candles[k].low < candidateLow) {
                                isLow = false
                                break
                            }
                        }
                        if (isLow) confirmedLows.add(Pair(p, candidateLow))
                    }
                }

                var longSignal = false
                val supportLine = findValidSupportTrendline(
                    confirmedLows = confirmedLows,
                    candles = candles,
                    currentBarIndex = i,
                    minTouches = minTouches,
                    tolerancePct = tlParams.retestTolerancePct
                )
                if (supportLine != null) {
                    val linePrice = supportLine.linePriceAtCurrent
                    val tolerance = 1.0 + (tlParams.retestTolerancePct / 100.0)
                    val touchedSupport = current.low <= linePrice * tolerance && current.close > linePrice
                    val bullishRejection = current.close > current.open && (current.close - current.low) > (current.high - current.close)

                    var valid = touchedSupport && bullishRejection
                    if (valid && tlParams.useMaTrendFilter) {
                        val ma = ind.slowMa.getOrNull(i) ?: current.close
                        valid = current.close >= ma * 0.98
                    }
                    if (valid && tlParams.useRsiFilter) {
                        val rsi = ind.rsi.getOrNull(i) ?: 50.0
                        valid = rsi <= tlParams.rsiOversoldThreshold + 20.0
                    }
                    longSignal = valid
                }

                var shortSignal = false
                val resistanceLine = findValidResistanceTrendline(
                    confirmedHighs = confirmedHighs,
                    candles = candles,
                    currentBarIndex = i,
                    minTouches = minTouches,
                    tolerancePct = tlParams.retestTolerancePct
                )
                if (resistanceLine != null) {
                    val linePrice = resistanceLine.linePriceAtCurrent
                    val tolerance = 1.0 - (tlParams.retestTolerancePct / 100.0)
                    val touchedResistance = current.high >= linePrice * tolerance && current.close < linePrice
                    val bearishRejection = current.close < current.open && (current.high - current.close) > (current.close - current.low)

                    var valid = touchedResistance && bearishRejection
                    if (valid && tlParams.useMaTrendFilter) {
                        val ma = ind.slowMa.getOrNull(i) ?: current.close
                        valid = current.close <= ma * 1.02
                    }
                    if (valid && tlParams.useRsiFilter) {
                        val rsi = ind.rsi.getOrNull(i) ?: 50.0
                        valid = rsi >= tlParams.rsiOverboughtThreshold - 20.0
                    }
                    shortSignal = valid
                }

                val reason = if (longSignal) "Support Trendline Bounce Rejection" else if (shortSignal) "Resistance Trendline Rejection" else null
                return SignalEvaluation(longSignal, shortSignal, reason)
            }

            StrategyType.MULTI_CONFLUENCE -> {
                val slowMa = ind.slowMa.getOrNull(i) ?: return SignalEvaluation(false, false)
                val rsiCurr = ind.rsi.getOrNull(i) ?: return SignalEvaluation(false, false)
                val histCurr = ind.macdHist.getOrNull(i) ?: return SignalEvaluation(false, false)
                val histPrev = ind.macdHist.getOrNull(i - 1) ?: return SignalEvaluation(false, false)

                val trendBullish = current.close > slowMa
                val rsiBullish = rsiCurr in 40.0..60.0
                val macdTurningUp = histCurr > histPrev && histCurr > 0

                val longSignal = trendBullish && rsiBullish && macdTurningUp

                val trendBearish = current.close < slowMa
                val rsiBearish = rsiCurr in 50.0..70.0
                val macdTurningDown = histCurr < histPrev && histCurr < 0

                val shortSignal = trendBearish && rsiBearish && macdTurningDown
                val reason = if (longSignal) "EMA Trend + RSI Pullback + MACD Bullish Confluence" else if (shortSignal) "EMA Trend + RSI + MACD Bearish Confluence" else null
                return SignalEvaluation(longSignal, shortSignal, reason)
            }
            StrategyType.SMC_CONCEPTS -> {
                val engine = smcEngine ?: SmcEngine(strategy.indicatorConfig.smcConfig)
                val smcEval = engine.evaluateBar(i, candles, ind.atr)
                val reason = if (smcEval.longSignal || smcEval.shortSignal) {
                    val dir = if (smcEval.longSignal) TradeDirection.LONG else TradeDirection.SHORT
                    val events = smcEval.detectedEvents.filter { it.direction == dir }
                    if (events.isNotEmpty()) {
                        events.map { event ->
                            when (event.type) {
                                StructureType.BULLISH_BOS, StructureType.BEARISH_BOS -> "BOS"
                                StructureType.BULLISH_CHOCH, StructureType.BEARISH_CHOCH -> "CHOCH / MSS"
                                StructureType.BULLISH_ORDER_BLOCK, StructureType.BEARISH_ORDER_BLOCK -> "Order Block Retest"
                                StructureType.BULLISH_BREAKER_BLOCK, StructureType.BEARISH_BREAKER_BLOCK -> "Breaker Block Retest"
                                else -> event.type.label
                            }
                        }.distinct().joinToString(" + ")
                    } else "SMC Structure Break"
                } else null
                return SignalEvaluation(smcEval.longSignal, smcEval.shortSignal, reason)
            }
            StrategyType.ICT_CONCEPTS -> {
                val engine = smcEngine ?: SmcEngine(strategy.indicatorConfig.smcConfig)
                val smcEval = engine.evaluateBar(i, candles, ind.atr)
                val reason = if (smcEval.longSignal || smcEval.shortSignal) {
                    val dir = if (smcEval.longSignal) TradeDirection.LONG else TradeDirection.SHORT
                    val events = smcEval.detectedEvents.filter { it.direction == dir }
                    if (events.isNotEmpty()) {
                        events.map { event ->
                            when (event.type) {
                                StructureType.BULLISH_LIQUIDITY_SWEEP, StructureType.BEARISH_LIQUIDITY_SWEEP -> "Liquidity Sweep"
                                StructureType.BULLISH_FVG, StructureType.BEARISH_FVG -> "FVG Retest"
                                StructureType.DISPLACEMENT_BULLISH, StructureType.DISPLACEMENT_BEARISH -> "Displacement"
                                StructureType.EQUAL_HIGHS, StructureType.EQUAL_LOWS -> "EQH/EQL Pool"
                                StructureType.PREMIUM_ZONE, StructureType.DISCOUNT_ZONE -> event.type.label
                                else -> event.type.label
                            }
                        }.distinct().joinToString(" + ")
                    } else "ICT Imbalance / Liquidity Run"
                } else null
                return SignalEvaluation(smcEval.longSignal, smcEval.shortSignal, reason)
            }
            StrategyType.SMC_ICT_CONCEPTS -> {
                val engine = smcEngine ?: SmcEngine(strategy.indicatorConfig.smcConfig)
                val smcEval = engine.evaluateBar(i, candles, ind.atr)
                val reason = if (smcEval.longSignal || smcEval.shortSignal) {
                    val dir = if (smcEval.longSignal) TradeDirection.LONG else TradeDirection.SHORT
                    val events = smcEval.detectedEvents.filter { it.direction == dir }
                    if (events.isNotEmpty()) {
                        events.map { event ->
                            when (event.type) {
                                StructureType.BULLISH_BOS, StructureType.BEARISH_BOS -> "SMC BOS"
                                StructureType.BULLISH_CHOCH, StructureType.BEARISH_CHOCH -> "SMC CHOCH"
                                StructureType.BULLISH_ORDER_BLOCK, StructureType.BEARISH_ORDER_BLOCK -> "SMC Order Block"
                                StructureType.BULLISH_BREAKER_BLOCK, StructureType.BEARISH_BREAKER_BLOCK -> "SMC Breaker Block"
                                StructureType.BULLISH_LIQUIDITY_SWEEP, StructureType.BEARISH_LIQUIDITY_SWEEP -> "ICT Liquidity Sweep"
                                StructureType.BULLISH_FVG, StructureType.BEARISH_FVG -> "ICT FVG"
                                StructureType.DISPLACEMENT_BULLISH, StructureType.DISPLACEMENT_BEARISH -> "ICT Displacement"
                                StructureType.EQUAL_HIGHS, StructureType.EQUAL_LOWS -> "ICT EQH/EQL"
                                StructureType.PREMIUM_ZONE, StructureType.DISCOUNT_ZONE -> "ICT ${event.type.label}"
                            }
                        }.distinct().joinToString(" + ")
                    } else "SMC & ICT Confluence"
                } else null
                return SignalEvaluation(smcEval.longSignal, smcEval.shortSignal, reason)
            }
            StrategyType.A_PLUS_TRENDLINE -> {
                val engine = aPlusTrendlineEngine ?: APlusTrendlineEngine(strategy.indicatorConfig.aPlusTrendlineConfig)
                val res = engine.evaluateBar(candles, i)
                return SignalEvaluation(
                    longSignal = res.longSignal,
                    shortSignal = res.shortSignal,
                    reason = res.explanation?.entryReason ?: res.setupType,
                    structuralStopLoss = res.structuralStopLoss,
                    explanation = res.explanation
                )
            }
        }
    }

    private fun computeMetrics(
        initialCapital: Double,
        equityCurve: List<EquityPoint>,
        trades: List<Trade>,
        candles: List<Candle>,
        maxDrawdownPct: Double,
        maxDrawdownDurationBars: Int,
        timeframe: Timeframe = Timeframe.D1,
        assetCategory: AssetCategory? = null
    ): BacktestMetrics {
        val finalEquity = equityCurve.lastOrNull()?.equity ?: initialCapital
        val netProfitDollars = finalEquity - initialCapital
        val netProfitPercent = (netProfitDollars / initialCapital) * 100.0

        val initialBenchmark = candles.firstOrNull()?.close ?: 1.0
        val finalBenchmark = candles.lastOrNull()?.close ?: 1.0
        val benchmarkReturnPercent = if (initialBenchmark > 0) ((finalBenchmark - initialBenchmark) / initialBenchmark) * 100.0 else 0.0
        val alphaPercent = netProfitPercent - benchmarkReturnPercent

        val totalTrades = trades.size
        val winningTradesList = trades.filter { it.isWin }
        val losingTradesList = trades.filter { !it.isWin }
        val winningTrades = winningTradesList.size
        val losingTrades = losingTradesList.size
        val winRatePercent = if (totalTrades > 0) (winningTrades.toDouble() / totalTrades) * 100.0 else 0.0

        val grossProfits = winningTradesList.sumOf { it.pnlDollars }
        val grossLosses = abs(losingTradesList.sumOf { it.pnlDollars })
        val profitFactor = if (grossLosses > 0.0) grossProfits / grossLosses else if (grossProfits > 0) 99.99 else 0.0

        val avgWinDollars = if (winningTrades > 0) grossProfits / winningTrades else 0.0
        val avgLossDollars = if (losingTrades > 0) grossLosses / losingTrades else 1.0
        val payoffRatio = if (avgLossDollars > 0) avgWinDollars / avgLossDollars else 0.0

        val avgTradePercent = if (totalTrades > 0) trades.map { it.pnlPercent }.average() else 0.0
        val avgWinningTradePercent = if (winningTrades > 0) winningTradesList.map { it.pnlPercent }.average() else 0.0
        val avgLosingTradePercent = if (losingTrades > 0) losingTradesList.map { it.pnlPercent }.average() else 0.0

        val largestWinningTradeDollars = winningTradesList.maxOfOrNull { it.pnlDollars } ?: 0.0
        val largestLosingTradeDollars = losingTradesList.minOfOrNull { it.pnlDollars } ?: 0.0

        var maxConsWins = 0
        var currentConsWins = 0
        var maxConsLosses = 0
        var currentConsLosses = 0

        for (t in trades) {
            if (t.isWin) {
                currentConsWins++
                currentConsLosses = 0
                maxConsWins = max(maxConsWins, currentConsWins)
            } else {
                currentConsLosses++
                currentConsWins = 0
                maxConsLosses = max(maxConsLosses, currentConsLosses)
            }
        }

        val totalFeesPaid = trades.sumOf { it.feesPaid }
        val avgHoldingBars = if (totalTrades > 0) trades.map { it.holdingBars }.average() else 0.0

        val winProb = if (totalTrades > 0) winningTrades.toDouble() / totalTrades else 0.0
        val lossProb = 1.0 - winProb
        val expectancyDollars = (winProb * avgWinDollars) - (lossProb * avgLossDollars)
        val expectancyR = if (totalTrades > 0) trades.map { it.rMultiple }.average() else 0.0

        val barReturns = mutableListOf<Double>()
        for (k in 1 until equityCurve.size) {
            val prevEq = equityCurve[k - 1].equity
            val currEq = equityCurve[k].equity
            if (prevEq > 0) {
                barReturns.add((currEq - prevEq) / prevEq)
            }
        }

        val meanBarReturn = if (barReturns.isNotEmpty()) barReturns.average() else 0.0
        val variance = if (barReturns.size > 1) {
            barReturns.sumOf { (it - meanBarReturn) * (it - meanBarReturn) } / (barReturns.size - 1)
        } else 0.0
        val stdDev = sqrt(variance)

        // Timeframe-aware annualization factor
        val tradingDaysPerYear = if (assetCategory == AssetCategory.CRYPTO) 365.0 else 252.0
        val barsPerDay = 1440.0 / timeframe.minutes.toDouble()
        val annualizationFactor = sqrt(tradingDaysPerYear * barsPerDay)

        val sharpeRatio = if (stdDev > 0.0) (meanBarReturn / stdDev) * annualizationFactor else 0.0

        val downsideReturns = barReturns.filter { it < 0.0 }
        val downsideVariance = if (downsideReturns.size > 1) {
            downsideReturns.sumOf { it * it } / downsideReturns.size
        } else 0.0
        val downsideStdDev = sqrt(downsideVariance)
        val sortinoRatio = if (downsideStdDev > 0.0) (meanBarReturn / downsideStdDev) * annualizationFactor else 0.0

        val calmarRatio = if (maxDrawdownPct > 0.0) netProfitPercent / maxDrawdownPct else 0.0

        // Actual elapsed time-based CAGR
        val cagrPercent = if (candles.size >= 2) {
            val firstTs = candles.first().timestamp
            val lastTs = candles.last().timestamp
            val elapsedMs = (lastTs - firstTs).toDouble()
            val msPerYear = 365.25 * 86400000.0 // 31,557,600,000 ms per year
            val years = elapsedMs / msPerYear

            if (years > 0.0 && finalEquity > 0.0 && initialCapital > 0.0) {
                val cagr = ((finalEquity / initialCapital).pow(1.0 / years) - 1.0) * 100.0
                cagr.coerceIn(-100.0, 100000.0)
            } else if (finalEquity <= 0.0) {
                -100.0
            } else {
                netProfitPercent
            }
        } else {
            netProfitPercent
        }

        // Drawdown & Recovery Periods calculation
        var peak = initialCapital
        var maxDdDollars = 0.0
        var maxRecoveryBars = 0
        var currentRecoveryBars = 0
        for (ep in equityCurve) {
            if (ep.equity >= peak) {
                peak = ep.equity
                maxRecoveryBars = max(maxRecoveryBars, currentRecoveryBars)
                currentRecoveryBars = 0
            } else {
                val ddDollars = peak - ep.equity
                maxDdDollars = max(maxDdDollars, ddDollars)
                currentRecoveryBars++
            }
        }
        maxRecoveryBars = max(maxRecoveryBars, currentRecoveryBars)

        // Trade Distribution Percentiles
        val pnls = trades.map { it.pnlDollars }.sorted()
        val p25TradeDollars = if (pnls.isNotEmpty()) calculatePercentile(pnls, 25.0) else 0.0
        val medianTradeDollars = if (pnls.isNotEmpty()) calculatePercentile(pnls, 50.0) else 0.0
        val p75TradeDollars = if (pnls.isNotEmpty()) calculatePercentile(pnls, 75.0) else 0.0
        val iqrTradeDollars = p75TradeDollars - p25TradeDollars

        return BacktestMetrics(
            initialCapital = initialCapital,
            finalEquity = finalEquity,
            netProfitDollars = netProfitDollars,
            netProfitPercent = netProfitPercent,
            grossProfitDollars = grossProfits,
            grossLossDollars = grossLosses,
            benchmarkReturnPercent = benchmarkReturnPercent,
            alphaPercent = alphaPercent,
            cagrPercent = cagrPercent,
            maxDrawdownDollars = maxDdDollars,
            maxDrawdownPercent = maxDrawdownPct,
            maxDrawdownDurationBars = maxDrawdownDurationBars,
            recoveryPeriodBars = maxRecoveryBars,
            sharpeRatio = sharpeRatio.coerceIn(-10.0, 20.0),
            sortinoRatio = sortinoRatio.coerceIn(-10.0, 30.0),
            calmarRatio = calmarRatio.coerceIn(-10.0, 50.0),
            totalTrades = totalTrades,
            winningTrades = winningTrades,
            losingTrades = losingTrades,
            winRatePercent = winRatePercent,
            profitFactor = profitFactor.coerceIn(0.0, 99.99),
            payoffRatio = payoffRatio.coerceIn(0.0, 99.99),
            avgTradePercent = avgTradePercent,
            avgWinningTradePercent = avgWinningTradePercent,
            avgLosingTradePercent = avgLosingTradePercent,
            avgWinDollars = avgWinDollars,
            avgLossDollars = avgLossDollars,
            avgRMultiple = expectancyR,
            largestWinningTradeDollars = largestWinningTradeDollars,
            largestLosingTradeDollars = largestLosingTradeDollars,
            maxConsecutiveWins = maxConsWins,
            maxConsecutiveLosses = maxConsLosses,
            totalFeesPaid = totalFeesPaid,
            avgHoldingBars = avgHoldingBars,
            expectancyDollars = expectancyDollars,
            expectancyR = expectancyR,
            p25TradeDollars = p25TradeDollars,
            medianTradeDollars = medianTradeDollars,
            p75TradeDollars = p75TradeDollars,
            iqrTradeDollars = iqrTradeDollars
        )
    }

    private fun calculatePercentile(sortedList: List<Double>, p: Double): Double {
        if (sortedList.isEmpty()) return 0.0
        if (sortedList.size == 1) return sortedList.first()
        val index = (p / 100.0) * (sortedList.size - 1)
        val lower = index.toInt()
        val upper = ceil(index).toInt()
        val weight = index - lower
        return sortedList[lower] * (1.0 - weight) + sortedList[upper] * weight
    }

    private data class ValidTrendline(
        val p1: Pair<Int, Double>,
        val p2: Pair<Int, Double>,
        val slope: Double,
        val touchCount: Int,
        val linePriceAtCurrent: Double
    )

    private fun findValidResistanceTrendline(
        confirmedHighs: List<Pair<Int, Double>>,
        candles: List<Candle>,
        currentBarIndex: Int,
        minTouches: Int,
        tolerancePct: Double
    ): ValidTrendline? {
        if (confirmedHighs.size < minTouches) return null
        val tolerance = tolerancePct / 100.0

        for (idx2 in confirmedHighs.indices.reversed()) {
            val p2 = confirmedHighs[idx2]
            for (idx1 in (idx2 - 1) downTo 0) {
                val p1 = confirmedHighs[idx1]
                if (p2.first <= p1.first) continue
                val slope = (p2.second - p1.second) / (p2.first - p1.first)

                var touches = 0
                for (p in confirmedHighs) {
                    if (p.first < p1.first) continue
                    val expectedLinePrice = p1.second + slope * (p.first - p1.first)
                    if (expectedLinePrice > 0) {
                        val diffPct = abs(p.second - expectedLinePrice) / expectedLinePrice
                        if (diffPct <= tolerance) {
                            touches++
                        }
                    }
                }

                if (touches >= minTouches) {
                    var breached = false
                    val penetrationTol = max(tolerance, 0.015)
                    for (bar in p1.first until currentBarIndex) {
                        val lineAtBar = p1.second + slope * (bar - p1.first)
                        if (candles[bar].close > lineAtBar * (1.0 + penetrationTol)) {
                            breached = true
                            break
                        }
                    }

                    if (!breached) {
                        val linePriceAtCurrent = p1.second + slope * (currentBarIndex - p1.first)
                        return ValidTrendline(p1, p2, slope, touches, linePriceAtCurrent)
                    }
                }
            }
        }
        return null
    }

    private fun findValidSupportTrendline(
        confirmedLows: List<Pair<Int, Double>>,
        candles: List<Candle>,
        currentBarIndex: Int,
        minTouches: Int,
        tolerancePct: Double
    ): ValidTrendline? {
        if (confirmedLows.size < minTouches) return null
        val tolerance = tolerancePct / 100.0

        for (idx2 in confirmedLows.indices.reversed()) {
            val p2 = confirmedLows[idx2]
            for (idx1 in (idx2 - 1) downTo 0) {
                val p1 = confirmedLows[idx1]
                if (p2.first <= p1.first) continue
                val slope = (p2.second - p1.second) / (p2.first - p1.first)

                var touches = 0
                for (p in confirmedLows) {
                    if (p.first < p1.first) continue
                    val expectedLinePrice = p1.second + slope * (p.first - p1.first)
                    if (expectedLinePrice > 0) {
                        val diffPct = abs(p.second - expectedLinePrice) / expectedLinePrice
                        if (diffPct <= tolerance) {
                            touches++
                        }
                    }
                }

                if (touches >= minTouches) {
                    var breached = false
                    val penetrationTol = max(tolerance, 0.015)
                    for (bar in p1.first until currentBarIndex) {
                        val lineAtBar = p1.second + slope * (bar - p1.first)
                        if (candles[bar].close < lineAtBar * (1.0 - penetrationTol)) {
                            breached = true
                            break
                        }
                    }

                    if (!breached) {
                        val linePriceAtCurrent = p1.second + slope * (currentBarIndex - p1.first)
                        return ValidTrendline(p1, p2, slope, touches, linePriceAtCurrent)
                    }
                }
            }
        }
        return null
    }

    private fun emptyResult(
        asset: MarketAsset,
        regime: MarketRegime,
        timeframe: Timeframe,
        strategy: StrategyDefinition,
        risk: RiskParameters,
        candles: List<Candle>,
        dataSourceInfo: DataSourceInfo?
    ): BacktestResult {
        val dsInfo = dataSourceInfo ?: DataSourceInfo(
            provider = "No Data",
            symbol = asset.symbol,
            market = asset.category.label,
            timeframe = timeframe.label,
            startDate = "",
            endDate = "",
            startTimestamp = 0L,
            endTimestamp = 0L,
            candleCount = candles.size,
            isRealHistorical = true,
            intrabarExecutionRule = risk.intrabarExecution.label,
            executionModel = risk.executionModel.label
        )
        return BacktestResult(
            id = UUID.randomUUID().toString(),
            asset = asset,
            regime = regime,
            timeframe = timeframe,
            strategy = strategy,
            riskParams = risk,
            candles = candles,
            indicators = CalculatedIndicators(),
            trades = emptyList(),
            equityCurve = emptyList(),
            signalMarkers = emptyList(),
            metrics = BacktestMetrics(
                initialCapital = risk.initialCapital,
                finalEquity = risk.initialCapital,
                netProfitDollars = 0.0,
                netProfitPercent = 0.0,
                benchmarkReturnPercent = 0.0,
                alphaPercent = 0.0,
                cagrPercent = 0.0,
                maxDrawdownPercent = 0.0,
                maxDrawdownDurationBars = 0,
                sharpeRatio = 0.0,
                sortinoRatio = 0.0,
                calmarRatio = 0.0,
                totalTrades = 0,
                winningTrades = 0,
                losingTrades = 0,
                winRatePercent = 0.0,
                profitFactor = 0.0,
                payoffRatio = 0.0,
                avgTradePercent = 0.0,
                avgWinningTradePercent = 0.0,
                avgLosingTradePercent = 0.0,
                largestWinningTradeDollars = 0.0,
                largestLosingTradeDollars = 0.0,
                maxConsecutiveWins = 0,
                maxConsecutiveLosses = 0,
                totalFeesPaid = 0.0,
                avgHoldingBars = 0.0,
                expectancyDollars = 0.0,
                expectancyR = 0.0
            ),
            dataSource = dsInfo
        )
    }
}
