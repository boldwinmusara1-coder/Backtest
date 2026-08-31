package com.example.tradestrat.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tradestrat.model.*
import com.example.ui.theme.*

@Composable
fun IndicatorThresholdConfigurationCard(
    strategy: StrategyDefinition,
    onStrategyChanged: (StrategyDefinition) -> Unit,
    modifier: Modifier = Modifier,
    onApplyAndRun: (() -> Unit)? = null
) {
    var isExpanded by remember { mutableStateOf(true) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("indicator_threshold_config_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = BentoCardBg),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(BentoBorder))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(BentoLilacContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Indicators",
                            tint = BentoLilacText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "INDICATOR THRESHOLDS",
                            style = MaterialTheme.typography.labelSmall,
                            color = BentoLilac,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.1.sp
                        )
                        Text(
                            text = when (strategy.strategyType) {
                                StrategyType.OPENING_RANGE_BREAKOUT -> "ORB & Technical Filters"
                                StrategyType.TRENDLINE_BOUNCE -> "Trendline & Oscillator Thresholds"
                                StrategyType.TRENDLINE_BREAK -> "Trendline Breakout Filters"
                                else -> "Strategy Technical Indicators"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = BentoTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.testTag("toggle_indicator_config_expand")
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand",
                        tint = BentoLilac
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (strategy.strategyType) {
                        StrategyType.A_PLUS_TRENDLINE -> {
                            APlusTrendlineThresholdControls(
                                strategy = strategy,
                                onStrategyChanged = onStrategyChanged
                            )
                        }

                        StrategyType.OPENING_RANGE_BREAKOUT -> {
                            OrbThresholdControls(
                                strategy = strategy,
                                onStrategyChanged = onStrategyChanged
                            )
                        }

                        StrategyType.TRENDLINE_BOUNCE -> {
                            TrendlineBounceThresholdControls(
                                strategy = strategy,
                                onStrategyChanged = onStrategyChanged
                            )
                        }

                        StrategyType.TRENDLINE_BREAK -> {
                            TrendlineBreakThresholdControls(
                                strategy = strategy,
                                onStrategyChanged = onStrategyChanged
                            )
                        }

                        StrategyType.MA_CROSSOVER, StrategyType.MULTI_CONFLUENCE -> {
                            MaThresholdControls(
                                strategy = strategy,
                                onStrategyChanged = onStrategyChanged
                            )
                        }

                        StrategyType.RSI_MEAN_REVERSION -> {
                            RsiThresholdControls(
                                strategy = strategy,
                                onStrategyChanged = onStrategyChanged
                            )
                        }

                        else -> {
                            DefaultThresholdControls(
                                strategy = strategy,
                                onStrategyChanged = onStrategyChanged
                            )
                        }
                    }

                    onApplyAndRun?.let { onRun ->
                        Button(
                            onClick = onRun,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("apply_indicator_thresholds_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BentoLilac,
                                contentColor = BentoLilacContainer
                            )
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = "Run", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Re-Run Simulation with Thresholds", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrbThresholdControls(
    strategy: StrategyDefinition,
    onStrategyChanged: (StrategyDefinition) -> Unit
) {
    val cfg = strategy.indicatorConfig.orbParams

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Section: Session & Timezone Configuration
        Text(
            text = "Session Schedule & Timezone (Causal Boundary)",
            style = MaterialTheme.typography.labelSmall,
            color = BentoTextSecondary,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Session Timezone", style = MaterialTheme.typography.bodySmall, color = BentoTextPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("America/New_York", "UTC", "Europe/London", "Asia/Tokyo").forEach { tz ->
                    val isSelected = cfg.sessionTimezone == tz
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            onStrategyChanged(
                                strategy.copy(
                                    indicatorConfig = strategy.indicatorConfig.copy(
                                        orbParams = cfg.copy(sessionTimezone = tz)
                                    )
                                )
                            )
                        },
                        label = {
                            Text(
                                when (tz) {
                                    "America/New_York" -> "NY (EST)"
                                    "UTC" -> "UTC"
                                    "Europe/London" -> "LON (GMT)"
                                    "Asia/Tokyo" -> "TKY (JST)"
                                    else -> tz
                                },
                                fontSize = 10.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BentoLilacContainer,
                            selectedLabelColor = BentoLilacText,
                            containerColor = BentoCardElevated,
                            labelColor = BentoTextSecondary
                        ),
                        shape = CircleShape,
                        modifier = Modifier.height(28.dp)
                    )
                }
            }
        }

        IntParameterStepper(
            title = "Opening Range Duration",
            value = cfg.openingRangeMinutes,
            range = 5..120,
            step = 5,
            unit = "min window",
            onValueChange = { newVal ->
                onStrategyChanged(
                    strategy.copy(
                        indicatorConfig = strategy.indicatorConfig.copy(
                            orbParams = cfg.copy(openingRangeMinutes = newVal)
                        )
                    )
                )
            }
        )

        IntParameterStepper(
            title = "Session Start Hour",
            value = cfg.sessionStartHour,
            range = 0..23,
            step = 1,
            unit = ":00 local",
            onValueChange = { newVal ->
                onStrategyChanged(
                    strategy.copy(
                        indicatorConfig = strategy.indicatorConfig.copy(
                            orbParams = cfg.copy(sessionStartHour = newVal)
                        )
                    )
                )
            }
        )

        Divider(color = BentoBorder, thickness = 0.8.dp)

        // Section: Base ORB
        Text(
            text = "Opening Range Window & Volume Surge",
            style = MaterialTheme.typography.labelSmall,
            color = BentoTextSecondary,
            fontWeight = FontWeight.Bold
        )

        IntParameterStepper(
            title = "Opening Range Period",
            value = cfg.rangeBars,
            range = 5..30,
            unit = "bars",
            onValueChange = { newVal ->
                onStrategyChanged(
                    strategy.copy(
                        indicatorConfig = strategy.indicatorConfig.copy(
                            orbParams = cfg.copy(rangeBars = newVal)
                        )
                    )
                )
            }
        )

        DoubleParameterSlider(
            title = "Volume Expansion Multiplier",
            value = cfg.volumeMultiplier,
            valueRange = 1.0f..3.0f,
            displayFormatter = { String.format("%.1fx Volume", it) },
            onValueChange = { newVal ->
                onStrategyChanged(
                    strategy.copy(
                        indicatorConfig = strategy.indicatorConfig.copy(
                            orbParams = cfg.copy(volumeMultiplier = newVal)
                        )
                    )
                )
            }
        )

        Divider(color = BentoBorder, thickness = 0.8.dp)

        // Section: Moving Average Filter
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Moving Average (EMA) Trend Filter", style = MaterialTheme.typography.bodyMedium, color = BentoTextPrimary, fontWeight = FontWeight.SemiBold)
                Text("Trades only when price is above EMA", style = MaterialTheme.typography.labelSmall, color = BentoTextMuted)
            }
            Switch(
                checked = cfg.useEmaTrendFilter,
                onCheckedChange = { isEnabled ->
                    onStrategyChanged(
                        strategy.copy(
                            indicatorConfig = strategy.indicatorConfig.copy(
                                orbParams = cfg.copy(useEmaTrendFilter = isEnabled)
                            )
                        )
                    )
                },
                colors = SwitchDefaults.colors(checkedThumbColor = BentoLilac, checkedTrackColor = BentoLilacContainer),
                modifier = Modifier.testTag("orb_ema_filter_switch")
            )
        }

        if (cfg.useEmaTrendFilter) {
            IntParameterStepper(
                title = "EMA Trend Filter Period",
                value = cfg.emaTrendPeriod,
                range = 10..200,
                step = 10,
                unit = "bars (EMA)",
                onValueChange = { newVal ->
                    onStrategyChanged(
                        strategy.copy(
                            indicatorConfig = strategy.indicatorConfig.copy(
                                orbParams = cfg.copy(emaTrendPeriod = newVal)
                            )
                        )
                    )
                }
            )
        }

        Divider(color = BentoBorder, thickness = 0.8.dp)

        // Section: RSI Filter
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("RSI Momentum Filter", style = MaterialTheme.typography.bodyMedium, color = BentoTextPrimary, fontWeight = FontWeight.SemiBold)
                Text("Requires RSI threshold confirmation on breakout", style = MaterialTheme.typography.labelSmall, color = BentoTextMuted)
            }
            Switch(
                checked = cfg.useRsiFilter,
                onCheckedChange = { isEnabled ->
                    onStrategyChanged(
                        strategy.copy(
                            indicatorConfig = strategy.indicatorConfig.copy(
                                orbParams = cfg.copy(useRsiFilter = isEnabled)
                            )
                        )
                    )
                },
                colors = SwitchDefaults.colors(checkedThumbColor = BentoLilac, checkedTrackColor = BentoLilacContainer),
                modifier = Modifier.testTag("orb_rsi_filter_switch")
            )
        }

        if (cfg.useRsiFilter) {
            IntParameterStepper(
                title = "RSI Calculation Period",
                value = cfg.rsiPeriod,
                range = 5..28,
                unit = "bars",
                onValueChange = { newVal ->
                    onStrategyChanged(
                        strategy.copy(
                            indicatorConfig = strategy.indicatorConfig.copy(
                                orbParams = cfg.copy(rsiPeriod = newVal)
                            )
                        )
                    )
                }
            )

            DoubleParameterSlider(
                title = "RSI Momentum Threshold",
                value = cfg.rsiThreshold,
                valueRange = 40.0f..65.0f,
                displayFormatter = { String.format("RSI >= %.0f", it) },
                onValueChange = { newVal ->
                    onStrategyChanged(
                        strategy.copy(
                            indicatorConfig = strategy.indicatorConfig.copy(
                                orbParams = cfg.copy(rsiThreshold = newVal)
                            )
                        )
                    )
                }
            )
        }
    }
}

@Composable
private fun TrendlineBounceThresholdControls(
    strategy: StrategyDefinition,
    onStrategyChanged: (StrategyDefinition) -> Unit
) {
    val cfg = strategy.indicatorConfig.trendlineParams

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Pivot Lookback & Confirmation",
            style = MaterialTheme.typography.labelSmall,
            color = BentoTextSecondary,
            fontWeight = FontWeight.Bold
        )

        IntParameterStepper(
            title = "Swing Pivot Lookback Window",
            value = cfg.pivotLookback,
            range = 5..25,
            unit = "bars",
            onValueChange = { newVal ->
                onStrategyChanged(
                    strategy.copy(
                        indicatorConfig = strategy.indicatorConfig.copy(
                            trendlineParams = cfg.copy(pivotLookback = newVal)
                        )
                    )
                )
            }
        )

        IntParameterStepper(
            title = "Causal Pivot Strength (Confirmation Lag)",
            value = cfg.pivotStrength,
            range = 1..10,
            unit = "bars",
            onValueChange = { newVal ->
                onStrategyChanged(
                    strategy.copy(
                        indicatorConfig = strategy.indicatorConfig.copy(
                            trendlineParams = cfg.copy(pivotStrength = newVal)
                        )
                    )
                )
            }
        )

        IntParameterStepper(
            title = "Minimum Trendline Touches",
            value = cfg.minTouches,
            range = 2..5,
            unit = "touches",
            onValueChange = { newVal ->
                onStrategyChanged(
                    strategy.copy(
                        indicatorConfig = strategy.indicatorConfig.copy(
                            trendlineParams = cfg.copy(minTouches = newVal)
                        )
                    )
                )
            }
        )

        DoubleParameterSlider(
            title = "Proximity Buffer Threshold",
            value = cfg.confirmationThresholdPct,
            valueRange = 0.1f..1.5f,
            displayFormatter = { String.format("%.2f%%", it) },
            onValueChange = { newVal ->
                onStrategyChanged(
                    strategy.copy(
                        indicatorConfig = strategy.indicatorConfig.copy(
                            trendlineParams = cfg.copy(confirmationThresholdPct = newVal)
                        )
                    )
                )
            }
        )

        Divider(color = BentoBorder, thickness = 0.8.dp)

        // Moving Average Trend Filter
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Moving Average (EMA) Trend Filter", style = MaterialTheme.typography.bodyMedium, color = BentoTextPrimary, fontWeight = FontWeight.SemiBold)
                Text("Only bounce in direction of macro trend", style = MaterialTheme.typography.labelSmall, color = BentoTextMuted)
            }
            Switch(
                checked = cfg.useMaTrendFilter,
                onCheckedChange = { isEnabled ->
                    onStrategyChanged(
                        strategy.copy(
                            indicatorConfig = strategy.indicatorConfig.copy(
                                trendlineParams = cfg.copy(useMaTrendFilter = isEnabled)
                            )
                        )
                    )
                },
                colors = SwitchDefaults.colors(checkedThumbColor = BentoLilac, checkedTrackColor = BentoLilacContainer),
                modifier = Modifier.testTag("tl_ma_filter_switch")
            )
        }

        if (cfg.useMaTrendFilter) {
            IntParameterStepper(
                title = "Moving Average Period",
                value = cfg.maTrendPeriod,
                range = 10..200,
                step = 10,
                unit = "bars (EMA)",
                onValueChange = { newVal ->
                    onStrategyChanged(
                        strategy.copy(
                            indicatorConfig = strategy.indicatorConfig.copy(
                                trendlineParams = cfg.copy(maTrendPeriod = newVal)
                            )
                        )
                    )
                }
            )
        }

        Divider(color = BentoBorder, thickness = 0.8.dp)

        // RSI Oversold / Overbought Thresholds
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("RSI Oscillator Thresholds", style = MaterialTheme.typography.bodyMedium, color = BentoTextPrimary, fontWeight = FontWeight.SemiBold)
                Text("Confirms bounce with RSI reversal level", style = MaterialTheme.typography.labelSmall, color = BentoTextMuted)
            }
            Switch(
                checked = cfg.useRsiFilter,
                onCheckedChange = { isEnabled ->
                    onStrategyChanged(
                        strategy.copy(
                            indicatorConfig = strategy.indicatorConfig.copy(
                                trendlineParams = cfg.copy(useRsiFilter = isEnabled)
                            )
                        )
                    )
                },
                colors = SwitchDefaults.colors(checkedThumbColor = BentoLilac, checkedTrackColor = BentoLilacContainer),
                modifier = Modifier.testTag("tl_rsi_filter_switch")
            )
        }

        if (cfg.useRsiFilter) {
            IntParameterStepper(
                title = "RSI Period",
                value = cfg.rsiPeriod,
                range = 5..28,
                unit = "bars",
                onValueChange = { newVal ->
                    onStrategyChanged(
                        strategy.copy(
                            indicatorConfig = strategy.indicatorConfig.copy(
                                trendlineParams = cfg.copy(rsiPeriod = newVal)
                            )
                        )
                    )
                }
            )

            DoubleParameterSlider(
                title = "RSI Oversold Level (Support Bounce)",
                value = cfg.rsiOversoldThreshold,
                valueRange = 20.0f..45.0f,
                displayFormatter = { String.format("RSI <= %.0f", it) },
                onValueChange = { newVal ->
                    onStrategyChanged(
                        strategy.copy(
                            indicatorConfig = strategy.indicatorConfig.copy(
                                trendlineParams = cfg.copy(rsiOversoldThreshold = newVal)
                            )
                        )
                    )
                }
            )

            DoubleParameterSlider(
                title = "RSI Overbought Level (Resistance Rejection)",
                value = cfg.rsiOverboughtThreshold,
                valueRange = 55.0f..80.0f,
                displayFormatter = { String.format("RSI >= %.0f", it) },
                onValueChange = { newVal ->
                    onStrategyChanged(
                        strategy.copy(
                            indicatorConfig = strategy.indicatorConfig.copy(
                                trendlineParams = cfg.copy(rsiOverboughtThreshold = newVal)
                            )
                        )
                    )
                }
            )
        }
    }
}

@Composable
private fun TrendlineBreakThresholdControls(
    strategy: StrategyDefinition,
    onStrategyChanged: (StrategyDefinition) -> Unit
) {
    val cfg = strategy.indicatorConfig.trendlineParams
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        IntParameterStepper(
            title = "Swing Pivot Lookback Window",
            value = cfg.pivotLookback,
            range = 5..25,
            unit = "bars",
            onValueChange = { newVal ->
                onStrategyChanged(
                    strategy.copy(
                        indicatorConfig = strategy.indicatorConfig.copy(
                            trendlineParams = cfg.copy(pivotLookback = newVal)
                        )
                    )
                )
            }
        )

        IntParameterStepper(
            title = "Causal Pivot Strength (Confirmation Lag)",
            value = cfg.pivotStrength,
            range = 1..10,
            unit = "bars",
            onValueChange = { newVal ->
                onStrategyChanged(
                    strategy.copy(
                        indicatorConfig = strategy.indicatorConfig.copy(
                            trendlineParams = cfg.copy(pivotStrength = newVal)
                        )
                    )
                )
            }
        )

        IntParameterStepper(
            title = "Minimum Trendline Touches",
            value = cfg.minTouches,
            range = 2..5,
            unit = "touches",
            onValueChange = { newVal ->
                onStrategyChanged(
                    strategy.copy(
                        indicatorConfig = strategy.indicatorConfig.copy(
                            trendlineParams = cfg.copy(minTouches = newVal)
                        )
                    )
                )
            }
        )

        DoubleParameterSlider(
            title = "Breakout Confirmation Buffer",
            value = cfg.confirmationThresholdPct,
            valueRange = 0.1f..2.0f,
            displayFormatter = { String.format("%.2f%%", it) },
            onValueChange = { newVal ->
                onStrategyChanged(
                    strategy.copy(
                        indicatorConfig = strategy.indicatorConfig.copy(
                            trendlineParams = cfg.copy(confirmationThresholdPct = newVal)
                        )
                    )
                )
            }
        )
    }
}

@Composable
private fun MaThresholdControls(
    strategy: StrategyDefinition,
    onStrategyChanged: (StrategyDefinition) -> Unit
) {
    val cfg = strategy.indicatorConfig.maParams
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        IntParameterStepper(
            title = "Fast MA Period",
            value = cfg.fastPeriod,
            range = 3..50,
            unit = "bars",
            onValueChange = { newVal ->
                onStrategyChanged(
                    strategy.copy(
                        indicatorConfig = strategy.indicatorConfig.copy(
                            maParams = cfg.copy(fastPeriod = newVal)
                        )
                    )
                )
            }
        )

        IntParameterStepper(
            title = "Slow MA Period",
            value = cfg.slowPeriod,
            range = 10..200,
            step = 5,
            unit = "bars",
            onValueChange = { newVal ->
                onStrategyChanged(
                    strategy.copy(
                        indicatorConfig = strategy.indicatorConfig.copy(
                            maParams = cfg.copy(slowPeriod = newVal)
                        )
                    )
                )
            }
        )
    }
}

@Composable
private fun RsiThresholdControls(
    strategy: StrategyDefinition,
    onStrategyChanged: (StrategyDefinition) -> Unit
) {
    val cfg = strategy.indicatorConfig.rsiParams
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        IntParameterStepper(
            title = "RSI Period",
            value = cfg.period,
            range = 5..30,
            unit = "bars",
            onValueChange = { newVal ->
                onStrategyChanged(
                    strategy.copy(
                        indicatorConfig = strategy.indicatorConfig.copy(
                            rsiParams = cfg.copy(period = newVal)
                        )
                    )
                )
            }
        )

        DoubleParameterSlider(
            title = "Oversold Level (Buy Trigger)",
            value = cfg.oversoldThreshold,
            valueRange = 15.0f..45.0f,
            displayFormatter = { String.format("RSI <= %.0f", it) },
            onValueChange = { newVal ->
                onStrategyChanged(
                    strategy.copy(
                        indicatorConfig = strategy.indicatorConfig.copy(
                            rsiParams = cfg.copy(oversoldThreshold = newVal)
                        )
                    )
                )
            }
        )

        DoubleParameterSlider(
            title = "Overbought Level (Sell Trigger)",
            value = cfg.overboughtThreshold,
            valueRange = 55.0f..85.0f,
            displayFormatter = { String.format("RSI >= %.0f", it) },
            onValueChange = { newVal ->
                onStrategyChanged(
                    strategy.copy(
                        indicatorConfig = strategy.indicatorConfig.copy(
                            rsiParams = cfg.copy(overboughtThreshold = newVal)
                        )
                    )
                )
            }
        )
    }
}

@Composable
private fun DefaultThresholdControls(
    strategy: StrategyDefinition,
    onStrategyChanged: (StrategyDefinition) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        IntParameterStepper(
            title = "ATR Volatility Period",
            value = strategy.indicatorConfig.supertrendParams.atrPeriod,
            range = 5..30,
            unit = "bars",
            onValueChange = { newVal ->
                onStrategyChanged(
                    strategy.copy(
                        indicatorConfig = strategy.indicatorConfig.copy(
                            supertrendParams = strategy.indicatorConfig.supertrendParams.copy(atrPeriod = newVal)
                        )
                    )
                )
            }
        )

        DoubleParameterSlider(
            title = "Volatility Multiplier",
            value = strategy.indicatorConfig.supertrendParams.multiplier,
            valueRange = 1.0f..5.0f,
            displayFormatter = { String.format("%.1fx", it) },
            onValueChange = { newVal ->
                onStrategyChanged(
                    strategy.copy(
                        indicatorConfig = strategy.indicatorConfig.copy(
                            supertrendParams = strategy.indicatorConfig.supertrendParams.copy(multiplier = newVal)
                        )
                    )
                )
            }
        )
    }
}

@Composable
private fun APlusTrendlineThresholdControls(
    strategy: StrategyDefinition,
    onStrategyChanged: (StrategyDefinition) -> Unit
) {
    val cfg = strategy.indicatorConfig.aPlusTrendlineConfig

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Setup Mode Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Setup Mode",
                style = MaterialTheme.typography.bodyMedium,
                color = BentoTextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TrendlineSetupMode.values().forEach { mode ->
                    val isSelected = cfg.setupMode == mode
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            onStrategyChanged(
                                strategy.copy(
                                    indicatorConfig = strategy.indicatorConfig.copy(
                                        aPlusTrendlineConfig = cfg.copy(setupMode = mode)
                                    )
                                )
                            )
                        },
                        label = {
                            Text(mode.label, fontSize = 10.sp)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BentoLilacContainer,
                            selectedLabelColor = BentoLilacText,
                            containerColor = BentoCardElevated,
                            labelColor = BentoTextSecondary
                        ),
                        shape = CircleShape,
                        modifier = Modifier.height(28.dp)
                    )
                }
            }
        }

        IntParameterStepper(
            title = "Swing Lookback",
            value = cfg.swingLookback,
            range = 3..15,
            unit = "bars",
            onValueChange = { newVal ->
                onStrategyChanged(
                    strategy.copy(
                        indicatorConfig = strategy.indicatorConfig.copy(
                            aPlusTrendlineConfig = cfg.copy(swingLookback = newVal)
                        )
                    )
                )
            }
        )

        IntParameterStepper(
            title = "Confirmation Bars",
            value = cfg.confirmationBars,
            range = 1..5,
            unit = "bars",
            onValueChange = { newVal ->
                onStrategyChanged(
                    strategy.copy(
                        indicatorConfig = strategy.indicatorConfig.copy(
                            aPlusTrendlineConfig = cfg.copy(confirmationBars = newVal)
                        )
                    )
                )
            }
        )

        DoubleParameterSlider(
            title = "Retest Tolerance (ATR)",
            value = cfg.retestToleranceATR,
            valueRange = 0.05f..1.0f,
            displayFormatter = { String.format("%.2f ATR", it) },
            onValueChange = { newVal ->
                onStrategyChanged(
                    strategy.copy(
                        indicatorConfig = strategy.indicatorConfig.copy(
                            aPlusTrendlineConfig = cfg.copy(retestToleranceATR = newVal)
                        )
                    )
                )
            }
        )

        IntParameterStepper(
            title = "Retest Window Limit",
            value = cfg.retestMaxBars,
            range = 3..25,
            unit = "bars",
            onValueChange = { newVal ->
                onStrategyChanged(
                    strategy.copy(
                        indicatorConfig = strategy.indicatorConfig.copy(
                            aPlusTrendlineConfig = cfg.copy(retestMaxBars = newVal)
                        )
                    )
                )
            }
        )

        DoubleParameterSlider(
            title = "Break-Even Trigger",
            value = cfg.breakEvenTriggerR,
            valueRange = 0.5f..3.0f,
            displayFormatter = { String.format("%.1fR", it) },
            onValueChange = { newVal ->
                onStrategyChanged(
                    strategy.copy(
                        indicatorConfig = strategy.indicatorConfig.copy(
                            aPlusTrendlineConfig = cfg.copy(breakEvenTriggerR = newVal)
                        )
                    )
                )
            }
        )

        // Trailing & Exit Toggles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Structural Trailing Stop", style = MaterialTheme.typography.bodyMedium, color = BentoTextPrimary, fontWeight = FontWeight.SemiBold)
                Text("Trails stop loss behind newest confirmed swing points", style = MaterialTheme.typography.labelSmall, color = BentoTextMuted)
            }
            Switch(
                checked = cfg.enableStructuralTrailing,
                onCheckedChange = { checked ->
                    onStrategyChanged(
                        strategy.copy(
                            indicatorConfig = strategy.indicatorConfig.copy(
                                aPlusTrendlineConfig = cfg.copy(enableStructuralTrailing = checked)
                            )
                        )
                    )
                },
                colors = SwitchDefaults.colors(checkedThumbColor = BentoLilac, checkedTrackColor = BentoLilacContainer)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Exit on Counter-Structure Break", style = MaterialTheme.typography.bodyMedium, color = BentoTextPrimary, fontWeight = FontWeight.SemiBold)
                Text("Closes trade immediately if opposing market structure forms", style = MaterialTheme.typography.labelSmall, color = BentoTextMuted)
            }
            Switch(
                checked = cfg.exitOnCounterStructureBreak,
                onCheckedChange = { checked ->
                    onStrategyChanged(
                        strategy.copy(
                            indicatorConfig = strategy.indicatorConfig.copy(
                                aPlusTrendlineConfig = cfg.copy(exitOnCounterStructureBreak = checked)
                            )
                        )
                    )
                },
                colors = SwitchDefaults.colors(checkedThumbColor = BentoLilac, checkedTrackColor = BentoLilacContainer)
            )
        }
    }
}
