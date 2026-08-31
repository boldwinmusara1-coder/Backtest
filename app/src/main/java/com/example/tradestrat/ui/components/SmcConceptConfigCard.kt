package com.example.tradestrat.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tradestrat.model.*
import com.example.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmcConceptConfigCard(
    strategy: StrategyDefinition,
    onStrategyChanged: (StrategyDefinition) -> Unit,
    smcMetrics: SmcMetrics?,
    backtestMetrics: BacktestMetrics?,
    modifier: Modifier = Modifier,
    onApplyAndRun: (() -> Unit)? = null
) {
    var isExpanded by remember { mutableStateOf(true) }
    val smc = strategy.indicatorConfig.smcConfig

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("smc_concept_config_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = BentoCardBg),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(BentoBorder))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .testTag("smc_header_toggle"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(BentoLilacContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalance,
                            contentDescription = "SMC / ICT Concepts",
                            tint = BentoLilacText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "SMC / ICT CONCEPTS",
                                style = MaterialTheme.typography.titleMedium,
                                color = BentoTextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = BentoLilacContainer
                            ) {
                                Text(
                                    text = "DETERMINISTIC",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BentoLilacText,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Institutional Order Flow, Liquidity & Market Structure Engine",
                            style = MaterialTheme.typography.bodySmall,
                            color = BentoTextMuted
                        )
                    }
                }

                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = BentoTextSecondary
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Divider(color = BentoBorder, thickness = 1.dp)

                    // Strategy Archetype & Framework Selector Row
                    Text(
                        text = "INSTITUTIONAL STRATEGY ARCHETYPE",
                        style = MaterialTheme.typography.labelSmall,
                        color = BentoLilac,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SmcArchetypeChip(
                            label = "SMC Structure",
                            isSelected = strategy.strategyType == StrategyType.SMC_CONCEPTS,
                            onClick = {
                                val updated = smc.copy(
                                    useBos = true,
                                    useChoch = true,
                                    useLiquiditySweep = false,
                                    useFvg = false,
                                    useOrderBlock = true,
                                    useBreakerBlock = false,
                                    requireConfluence = false,
                                    minConfluences = 1
                                )
                                onStrategyChanged(strategy.copy(strategyType = StrategyType.SMC_CONCEPTS, indicatorConfig = strategy.indicatorConfig.copy(smcConfig = updated)))
                                onApplyAndRun?.invoke()
                            },
                            modifier = Modifier.weight(1f)
                        )
                        SmcArchetypeChip(
                            label = "ICT Imbalance",
                            isSelected = strategy.strategyType == StrategyType.ICT_CONCEPTS,
                            onClick = {
                                val updated = smc.copy(
                                    useBos = false,
                                    useChoch = false,
                                    useLiquiditySweep = true,
                                    useFvg = true,
                                    useOrderBlock = false,
                                    useBreakerBlock = false,
                                    requireConfluence = false,
                                    minConfluences = 1
                                )
                                onStrategyChanged(strategy.copy(strategyType = StrategyType.ICT_CONCEPTS, indicatorConfig = strategy.indicatorConfig.copy(smcConfig = updated)))
                                onApplyAndRun?.invoke()
                            },
                            modifier = Modifier.weight(1f)
                        )
                        SmcArchetypeChip(
                            label = "Combined Framework",
                            isSelected = strategy.strategyType == StrategyType.SMC_ICT_CONCEPTS,
                            onClick = {
                                val updated = smc.copy(
                                    useBos = true,
                                    useChoch = true,
                                    useLiquiditySweep = true,
                                    useFvg = true,
                                    useOrderBlock = true,
                                    useBreakerBlock = true,
                                    usePremiumDiscount = true,
                                    useDisplacement = true,
                                    useEqualHighsLows = true,
                                    requireConfluence = true,
                                    minConfluences = 2
                                )
                                onStrategyChanged(strategy.copy(strategyType = StrategyType.SMC_ICT_CONCEPTS, indicatorConfig = strategy.indicatorConfig.copy(smcConfig = updated)))
                                onApplyAndRun?.invoke()
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Global Controls (Trade Direction & Optional Confluence Requirement)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = BentoCardElevated
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Trade Direction Filter", style = MaterialTheme.typography.bodyMedium, color = BentoTextPrimary, fontWeight = FontWeight.SemiBold)
                                    Text("Permitted order execution bias", style = MaterialTheme.typography.bodySmall, color = BentoTextMuted)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    SmcTradeDirection.values().forEach { dir ->
                                        Surface(
                                            modifier = Modifier.clickable {
                                                val updated = smc.copy(tradeDirection = dir)
                                                onStrategyChanged(strategy.copy(indicatorConfig = strategy.indicatorConfig.copy(smcConfig = updated)))
                                                onApplyAndRun?.invoke()
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (smc.tradeDirection == dir) BentoLilacContainer else TvSurfaceElevated
                                        ) {
                                            Text(
                                                text = when (dir) {
                                                    SmcTradeDirection.BOTH -> "Both"
                                                    SmcTradeDirection.LONG_ONLY -> "Longs"
                                                    SmcTradeDirection.SHORT_ONLY -> "Shorts"
                                                },
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (smc.tradeDirection == dir) BentoLilacText else BentoTextSecondary,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Divider(color = BentoBorder.copy(alpha = 0.5f))

                            // Confluence Requirement Toggle (Optional Confluence vs Required Multi-Confluence)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Confluence Requirement", style = MaterialTheme.typography.bodyMedium, color = BentoTextPrimary, fontWeight = FontWeight.SemiBold)
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (smc.requireConfluence) BentoLilacContainer else BentoGreen.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = if (smc.requireConfluence) "REQUIRED" else "OPTIONAL (ANY RULE)",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (smc.requireConfluence) BentoLilacText else BentoGreen,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = if (smc.requireConfluence) "Requires ${smc.minConfluences} simultaneous concepts" else "Optional: Any single active concept triggers entry immediately",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = BentoTextMuted
                                    )
                                }
                                Switch(
                                    checked = smc.requireConfluence,
                                    onCheckedChange = { isReq ->
                                        val updated = smc.copy(
                                            requireConfluence = isReq,
                                            minConfluences = if (isReq) maxOf(2, smc.minConfluences) else 1
                                        )
                                        onStrategyChanged(strategy.copy(indicatorConfig = strategy.indicatorConfig.copy(smcConfig = updated)))
                                        onApplyAndRun?.invoke()
                                    },
                                    modifier = Modifier.testTag("smc_confluence_toggle")
                                )
                            }

                            if (smc.requireConfluence) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Minimum Confluences (${smc.minConfluences})", style = MaterialTheme.typography.bodySmall, color = BentoTextSecondary)
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        (2..4).forEach { count ->
                                            Surface(
                                                modifier = Modifier.clickable {
                                                    val updated = smc.copy(minConfluences = count)
                                                    onStrategyChanged(strategy.copy(indicatorConfig = strategy.indicatorConfig.copy(smcConfig = updated)))
                                                    onApplyAndRun?.invoke()
                                                },
                                                shape = CircleShape,
                                                color = if (smc.minConfluences == count) BentoLilac else TvSurfaceElevated
                                            ) {
                                                Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = "$count",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (smc.minConfluences == count) Color.White else BentoTextSecondary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 10 INDEPENDENT SMC CONCEPTS
                    Text(
                        text = "10 INDEPENDENT SMC / ICT MODULES",
                        style = MaterialTheme.typography.labelSmall,
                        color = BentoLilac,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    // 1. BOS — Break of Structure
                    SmcConceptItem(
                        title = "1. Break of Structure (BOS)",
                        subtitle = "Trend continuation on decisive break past prior swing high/low",
                        badge = "Trend",
                        isEnabled = smc.useBos,
                        onToggle = { enabled ->
                            val updated = smc.copy(useBos = enabled)
                            onStrategyChanged(strategy.copy(strategyType = StrategyType.SMC_ICT_CONCEPTS, indicatorConfig = strategy.indicatorConfig.copy(smcConfig = updated)))
                            onApplyAndRun?.invoke()
                        },
                        tag = "smc_bos_switch"
                    ) {
                        IntParameterStepper(
                            title = "Swing Pivot Lookback Bars",
                            value = smc.bosLookback,
                            range = 3..15,
                            onValueChange = { lookback ->
                                val updated = smc.copy(bosLookback = lookback)
                                onStrategyChanged(strategy.copy(indicatorConfig = strategy.indicatorConfig.copy(smcConfig = updated)))
                                onApplyAndRun?.invoke()
                            },
                            unit = "bars"
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Confirmation Mode", style = MaterialTheme.typography.bodySmall, color = BentoTextSecondary)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Surface(
                                    modifier = Modifier.clickable {
                                        val updated = smc.copy(bosCloseConfirmation = true)
                                        onStrategyChanged(strategy.copy(indicatorConfig = strategy.indicatorConfig.copy(smcConfig = updated)))
                                        onApplyAndRun?.invoke()
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (smc.bosCloseConfirmation) BentoLilacContainer else TvSurfaceElevated
                                ) {
                                    Text("Candle Close", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (smc.bosCloseConfirmation) BentoLilacText else BentoTextMuted, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                                Surface(
                                    modifier = Modifier.clickable {
                                        val updated = smc.copy(bosCloseConfirmation = false)
                                        onStrategyChanged(strategy.copy(indicatorConfig = strategy.indicatorConfig.copy(smcConfig = updated)))
                                        onApplyAndRun?.invoke()
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (!smc.bosCloseConfirmation) BentoLilacContainer else TvSurfaceElevated
                                ) {
                                    Text("Wick Touch", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (!smc.bosCloseConfirmation) BentoLilacText else BentoTextMuted, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }
                    }

                    // 2. CHOCH / MSS — Change of Character
                    SmcConceptItem(
                        title = "2. Change of Character (CHOCH / MSS)",
                        subtitle = "Market structure shift signaling initial macro trend reversal",
                        badge = "Reversal",
                        isEnabled = smc.useChoch,
                        onToggle = { enabled ->
                            val updated = smc.copy(useChoch = enabled)
                            onStrategyChanged(strategy.copy(strategyType = StrategyType.SMC_ICT_CONCEPTS, indicatorConfig = strategy.indicatorConfig.copy(smcConfig = updated)))
                            onApplyAndRun?.invoke()
                        },
                        tag = "smc_choch_switch"
                    )

                    // 3. Liquidity Sweep / Liquidity Grab
                    SmcConceptItem(
                        title = "3. Liquidity Sweep / Grab",
                        subtitle = "False breakout wick piercing swing level with close back inside range",
                        badge = "Liquidity",
                        isEnabled = smc.useLiquiditySweep,
                        onToggle = { enabled ->
                            val updated = smc.copy(useLiquiditySweep = enabled)
                            onStrategyChanged(strategy.copy(strategyType = StrategyType.SMC_ICT_CONCEPTS, indicatorConfig = strategy.indicatorConfig.copy(smcConfig = updated)))
                            onApplyAndRun?.invoke()
                        },
                        tag = "smc_sweep_switch"
                    ) {
                        DoubleParameterStepper(
                            title = "Minimum Sweep Wick Threshold",
                            value = smc.sweepWickMinPct,
                            range = 0.05..1.0,
                            step = 0.05,
                            onValueChange = { wick ->
                                val updated = smc.copy(sweepWickMinPct = wick)
                                onStrategyChanged(strategy.copy(indicatorConfig = strategy.indicatorConfig.copy(smcConfig = updated)))
                                onApplyAndRun?.invoke()
                            },
                            unit = "%"
                        )
                    }

                    // 4. Fair Value Gap (FVG)
                    SmcConceptItem(
                        title = "4. Fair Value Gap (FVG)",
                        subtitle = "3-candle institutional liquidity void / imbalance retest",
                        badge = "Imbalance",
                        isEnabled = smc.useFvg,
                        onToggle = { enabled ->
                            val updated = smc.copy(useFvg = enabled)
                            onStrategyChanged(strategy.copy(strategyType = StrategyType.SMC_ICT_CONCEPTS, indicatorConfig = strategy.indicatorConfig.copy(smcConfig = updated)))
                            onApplyAndRun?.invoke()
                        },
                        tag = "smc_fvg_switch"
                    ) {
                        DoubleParameterStepper(
                            title = "Min FVG Size (ATR Multiple)",
                            value = smc.fvgMinGapAtrMultiple,
                            range = 0.1..1.5,
                            step = 0.1,
                            onValueChange = { atrM ->
                                val updated = smc.copy(fvgMinGapAtrMultiple = atrM)
                                onStrategyChanged(strategy.copy(indicatorConfig = strategy.indicatorConfig.copy(smcConfig = updated)))
                                onApplyAndRun?.invoke()
                            },
                            unit = "x ATR"
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Mitigation Fill Type", style = MaterialTheme.typography.bodySmall, color = BentoTextSecondary)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                FvgMitigationType.values().forEach { mit ->
                                    Surface(
                                        modifier = Modifier.clickable {
                                            val updated = smc.copy(fvgMitigationType = mit)
                                            onStrategyChanged(strategy.copy(indicatorConfig = strategy.indicatorConfig.copy(smcConfig = updated)))
                                            onApplyAndRun?.invoke()
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (smc.fvgMitigationType == mit) BentoLilacContainer else TvSurfaceElevated
                                    ) {
                                        Text(
                                            text = when (mit) {
                                                FvgMitigationType.TOUCH -> "Touch"
                                                FvgMitigationType.CONSEQUENT_ENCROACHMENT -> "50% CE"
                                                FvgMitigationType.FULL_FILL -> "Full Fill"
                                            },
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (smc.fvgMitigationType == mit) BentoLilacText else BentoTextMuted,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 5. Order Blocks (OB)
                    SmcConceptItem(
                        title = "5. Order Blocks (OB)",
                        subtitle = "Last opposing candle before structural displacement breakout",
                        badge = "Institutional",
                        isEnabled = smc.useOrderBlock,
                        onToggle = { enabled ->
                            val updated = smc.copy(useOrderBlock = enabled)
                            onStrategyChanged(strategy.copy(strategyType = StrategyType.SMC_ICT_CONCEPTS, indicatorConfig = strategy.indicatorConfig.copy(smcConfig = updated)))
                            onApplyAndRun?.invoke()
                        },
                        tag = "smc_ob_switch"
                    ) {
                        IntParameterStepper(
                            title = "OB Search Lookback Window",
                            value = smc.obLookback,
                            range = 5..30,
                            onValueChange = { lb ->
                                val updated = smc.copy(obLookback = lb)
                                onStrategyChanged(strategy.copy(indicatorConfig = strategy.indicatorConfig.copy(smcConfig = updated)))
                                onApplyAndRun?.invoke()
                            },
                            unit = "bars"
                        )
                    }

                    // 6. Breaker Blocks
                    SmcConceptItem(
                        title = "6. Breaker Blocks",
                        subtitle = "Failed order block flipped into support/resistance polarity",
                        badge = "Polarity",
                        isEnabled = smc.useBreakerBlock,
                        onToggle = { enabled ->
                            val updated = smc.copy(useBreakerBlock = enabled)
                            onStrategyChanged(strategy.copy(strategyType = StrategyType.SMC_ICT_CONCEPTS, indicatorConfig = strategy.indicatorConfig.copy(smcConfig = updated)))
                            onApplyAndRun?.invoke()
                        },
                        tag = "smc_breaker_switch"
                    )

                    // 7. Premium / Discount Zones
                    SmcConceptItem(
                        title = "7. Premium / Discount Zones",
                        subtitle = "50% Equilibrium filter: Longs in Discount (<50%), Shorts in Premium (>50%)",
                        badge = "Value",
                        isEnabled = smc.usePremiumDiscount,
                        onToggle = { enabled ->
                            val updated = smc.copy(usePremiumDiscount = enabled)
                            onStrategyChanged(strategy.copy(strategyType = StrategyType.SMC_ICT_CONCEPTS, indicatorConfig = strategy.indicatorConfig.copy(smcConfig = updated)))
                            onApplyAndRun?.invoke()
                        },
                        tag = "smc_premium_discount_switch"
                    )

                    // 8. Displacement Candles
                    SmcConceptItem(
                        title = "8. Displacement Candles",
                        subtitle = "Large directional expansion bars confirming institutional participation",
                        badge = "Momentum",
                        isEnabled = smc.useDisplacement,
                        onToggle = { enabled ->
                            val updated = smc.copy(useDisplacement = enabled)
                            onStrategyChanged(strategy.copy(strategyType = StrategyType.SMC_ICT_CONCEPTS, indicatorConfig = strategy.indicatorConfig.copy(smcConfig = updated)))
                            onApplyAndRun?.invoke()
                        },
                        tag = "smc_displacement_switch"
                    ) {
                        DoubleParameterStepper(
                            title = "Displacement Body ATR Multiplier",
                            value = smc.displacementAtrMultiplier,
                            range = 1.0..3.0,
                            step = 0.2,
                            onValueChange = { mult ->
                                val updated = smc.copy(displacementAtrMultiplier = mult)
                                onStrategyChanged(strategy.copy(indicatorConfig = strategy.indicatorConfig.copy(smcConfig = updated)))
                                onApplyAndRun?.invoke()
                            },
                            unit = "x ATR"
                        )
                    }

                    // 9. Equal Highs / Equal Lows (EQH / EQL)
                    SmcConceptItem(
                        title = "9. Equal Highs / Equal Lows (EQH / EQL)",
                        subtitle = "Identifies resting liquidity pools above equal peaks and below equal troughs",
                        badge = "Pools",
                        isEnabled = smc.useEqualHighsLows,
                        onToggle = { enabled ->
                            val updated = smc.copy(useEqualHighsLows = enabled)
                            onStrategyChanged(strategy.copy(strategyType = StrategyType.SMC_ICT_CONCEPTS, indicatorConfig = strategy.indicatorConfig.copy(smcConfig = updated)))
                            onApplyAndRun?.invoke()
                        },
                        tag = "smc_eqh_eql_switch"
                    ) {
                        DoubleParameterStepper(
                            title = "Equal Level Price Tolerance",
                            value = smc.eqTolerancePct,
                            range = 0.05..0.50,
                            step = 0.05,
                            onValueChange = { tol ->
                                val updated = smc.copy(eqTolerancePct = tol)
                                onStrategyChanged(strategy.copy(indicatorConfig = strategy.indicatorConfig.copy(smcConfig = updated)))
                                onApplyAndRun?.invoke()
                            },
                            unit = "%"
                        )
                    }

                    // 10. Trading Session Filters (London / NY / Asia)
                    SmcConceptItem(
                        title = "10. Trading Session Filters (Killzones)",
                        subtitle = "Restricts trade execution strictly to active London, NY, or Asia sessions",
                        badge = "Time",
                        isEnabled = smc.useSessionFilter,
                        onToggle = { enabled ->
                            val updated = smc.copy(useSessionFilter = enabled)
                            onStrategyChanged(strategy.copy(strategyType = StrategyType.SMC_ICT_CONCEPTS, indicatorConfig = strategy.indicatorConfig.copy(smcConfig = updated)))
                            onApplyAndRun?.invoke()
                        },
                        tag = "smc_session_switch"
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            SmcSessionType.values().forEach { sess ->
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            val updated = smc.copy(sessionType = sess)
                                            onStrategyChanged(strategy.copy(indicatorConfig = strategy.indicatorConfig.copy(smcConfig = updated)))
                                            onApplyAndRun?.invoke()
                                        },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (smc.sessionType == sess) BentoLilacContainer else TvSurfaceElevated
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = when (sess) {
                                                SmcSessionType.LONDON -> "London"
                                                SmcSessionType.NEW_YORK -> "New York"
                                                SmcSessionType.ASIA -> "Asia"
                                                SmcSessionType.LONDON_NY_OVERLAP -> "Overlap"
                                                SmcSessionType.ALL -> "24/7"
                                            },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (smc.sessionType == sess) BentoLilacText else BentoTextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // SMC / ICT Performance & Signal Audit Dashboard
                    if (smcMetrics != null || backtestMetrics != null) {
                        Divider(color = BentoBorder, thickness = 1.dp)

                        Text(
                            text = "SMC / ICT AUDIT & CONFLUENCE METRICS",
                            style = MaterialTheme.typography.labelSmall,
                            color = BentoLilac,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = BentoCardElevated
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                // Signals vs Filtered
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    SmcStatBox(
                                        label = "Raw Signals",
                                        value = "${smcMetrics?.rawSignalsCount ?: 0}",
                                        color = BentoTextPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    SmcStatBox(
                                        label = "Filtered Signals",
                                        value = "${smcMetrics?.filteredSignalsCount ?: 0}",
                                        color = BentoAmber,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    SmcStatBox(
                                        label = "Executed Trades",
                                        value = "${backtestMetrics?.totalTrades ?: 0}",
                                        color = BentoLilac,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                // Structural Events Count Row
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    SmcStatPill(label = "BOS", count = smcMetrics?.bosEventsCount ?: 0)
                                    SmcStatPill(label = "CHOCH", count = smcMetrics?.chochEventsCount ?: 0)
                                    SmcStatPill(label = "Sweeps", count = smcMetrics?.liquiditySweepsCount ?: 0)
                                    SmcStatPill(label = "FVG", count = smcMetrics?.fvgCount ?: 0)
                                    SmcStatPill(label = "OB", count = smcMetrics?.orderBlocksCount ?: 0)
                                    SmcStatPill(label = "Breakers", count = smcMetrics?.breakerBlocksCount ?: 0)
                                }

                                if (backtestMetrics != null) {
                                    Divider(color = BentoBorder.copy(alpha = 0.5f))

                                    // Comprehensive performance breakdown requested by user
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        SmcStatBox(
                                            label = "Win Rate",
                                            value = String.format(Locale.US, "%.1f%%", backtestMetrics.winRatePercent),
                                            color = if (backtestMetrics.winRatePercent >= 50) BentoGreen else BentoAmber,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        SmcStatBox(
                                            label = "Net Realized P&L",
                                            value = String.format(Locale.US, "%s$%.2f", if (backtestMetrics.netProfitDollars >= 0) "+" else "", backtestMetrics.netProfitDollars),
                                            color = if (backtestMetrics.netProfitDollars >= 0) BentoGreen else BentoRed,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        SmcStatBox(
                                            label = "Profit Factor",
                                            value = String.format(Locale.US, "%.2f", backtestMetrics.profitFactor),
                                            color = if (backtestMetrics.profitFactor >= 1.3) BentoGreen else BentoTextPrimary,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        SmcStatBox(
                                            label = "Expectancy / Trade",
                                            value = String.format(Locale.US, "%s$%.2f", if (backtestMetrics.expectancyDollars >= 0) "+" else "", backtestMetrics.expectancyDollars),
                                            color = if (backtestMetrics.expectancyDollars >= 0) BentoGreen else BentoRed,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        SmcStatBox(
                                            label = "Max Drawdown",
                                            value = String.format(Locale.US, "%.2f%%", backtestMetrics.maxDrawdownPercent),
                                            color = if (backtestMetrics.maxDrawdownPercent <= 15) BentoGreen else BentoRed,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        SmcStatBox(
                                            label = "Max Consec Loss",
                                            value = "${backtestMetrics.maxConsecutiveLosses}",
                                            color = if (backtestMetrics.maxConsecutiveLosses <= 3) BentoGreen else BentoAmber,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        SmcStatBox(
                                            label = "Avg Win / Avg Loss",
                                            value = String.format(Locale.US, "$%.0f / $%.0f", backtestMetrics.avgWinDollars, backtestMetrics.avgLossDollars),
                                            color = BentoTextPrimary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        SmcStatBox(
                                            label = "Account ROI",
                                            value = String.format(Locale.US, "%s%.2f%%", if (backtestMetrics.netProfitPercent >= 0) "+" else "", backtestMetrics.netProfitPercent),
                                            color = if (backtestMetrics.netProfitPercent >= 0) BentoGreen else BentoRed,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmcArchetypeChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) BentoLilacContainer else BentoCardElevated,
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(if (isSelected) BentoLilac else BentoBorder))
    ) {
        Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) BentoLilacText else BentoTextSecondary
            )
        }
    }
}

@Composable
private fun SmcConceptItem(
    title: String,
    subtitle: String,
    badge: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (isEnabled) BentoCardElevated else TvSurfaceElevated.copy(alpha = 0.5f),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(if (isEnabled) BentoLilac.copy(alpha = 0.4f) else BentoBorder))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isEnabled) BentoTextPrimary else BentoTextMuted
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isEnabled) BentoLilacContainer else BentoBorder
                        ) {
                            Text(
                                text = badge,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isEnabled) BentoLilacText else BentoTextMuted,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = BentoTextMuted,
                        fontSize = 11.sp
                    )
                }

                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = BentoLilac,
                        uncheckedThumbColor = BentoTextMuted,
                        uncheckedTrackColor = TvSurfaceElevated
                    ),
                    modifier = Modifier.testTag(tag)
                )
            }

            if (isEnabled && content != null) {
                Column(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun SmcStatBox(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = TvSurfaceElevated
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(label, fontSize = 9.sp, color = BentoTextMuted, fontWeight = FontWeight.Medium)
            Text(value, fontSize = 13.sp, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SmcStatPill(
    label: String,
    count: Int
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = TvSurfaceElevated
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(label, fontSize = 9.sp, color = BentoTextMuted, fontWeight = FontWeight.Medium)
            Text("$count", fontSize = 10.sp, color = BentoLilac, fontWeight = FontWeight.Bold)
        }
    }
}
