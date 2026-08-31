package com.example.tradestrat.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tradestrat.model.*
import com.example.tradestrat.ui.BacktestViewModel
import com.example.tradestrat.ui.components.*
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrategyBuilderScreen(
    viewModel: BacktestViewModel,
    modifier: Modifier = Modifier,
    onBacktestNow: () -> Unit = {}
) {
    val selectedStrategy by viewModel.selectedStrategy.collectAsState()
    var currentStrategy by remember(selectedStrategy) { mutableStateOf(selectedStrategy) }

    var showSaveDialog by remember { mutableStateOf(false) }
    var saveNameInput by remember { mutableStateOf(currentStrategy.name) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BentoBackground
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "STRATEGY ARCHITECT",
                            style = MaterialTheme.typography.labelSmall,
                            color = BentoLilac,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = "Custom Indicators & Logic",
                            style = MaterialTheme.typography.titleLarge,
                            color = BentoTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Save Strategy Button
                    Button(
                        onClick = {
                            saveNameInput = currentStrategy.name
                            showSaveDialog = true
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = BentoLilacContainer, contentColor = BentoLilacText),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("save_strategy_dialog_trigger")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Save", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Strategy Type Selector Presets
            item {
                Text(
                    text = "SELECT STRATEGY ARCHETYPE",
                    style = MaterialTheme.typography.labelSmall,
                    color = BentoTextMuted,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StrategyDefinition.PRESETS.forEach { preset ->
                        val isSelected = currentStrategy.strategyType == preset.strategyType && currentStrategy.id == preset.id
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentStrategy = preset
                                    viewModel.setStrategy(preset)
                                }
                                .testTag("preset_${preset.id}"),
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) BentoLilacContainer else BentoCardBg,
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = androidx.compose.ui.graphics.SolidColor(if (isSelected) BentoLilac else BentoBorder)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            preset.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isSelected) BentoLilacText else BentoTextPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Surface(
                                            shape = CircleShape,
                                            color = if (isSelected) BentoCardBg else BentoLilacContainer
                                        ) {
                                            Text(
                                                text = preset.strategyType.badge,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = BentoLilacText,
                                                fontSize = 9.sp,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        preset.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) BentoLilacText.copy(alpha = 0.8f) else BentoTextSecondary,
                                        fontSize = 11.sp
                                    )
                                }

                                if (isSelected) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Active", tint = BentoLilac, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Indicator Parameters Configuration Section
            item {
                Text(
                    text = "CUSTOMIZE INDICATOR PARAMETERS",
                    style = MaterialTheme.typography.labelSmall,
                    color = BentoTextMuted,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            // Dynamic parameter sliders based on current strategy
            when (currentStrategy.strategyType) {
                StrategyType.MA_CROSSOVER, StrategyType.MULTI_CONFLUENCE -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = BentoCardBg),
                            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BentoBorder))
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Moving Average Parameters", style = MaterialTheme.typography.titleSmall, color = BentoLilac, fontWeight = FontWeight.Bold)

                                IntParameterStepper(
                                    title = "Fast Period",
                                    value = currentStrategy.indicatorConfig.maParams.fastPeriod,
                                    range = 3..50,
                                    unit = "bars",
                                    onValueChange = { newVal ->
                                        val updated = currentStrategy.copy(
                                            indicatorConfig = currentStrategy.indicatorConfig.copy(
                                                maParams = currentStrategy.indicatorConfig.maParams.copy(fastPeriod = newVal)
                                            )
                                        )
                                        currentStrategy = updated
                                        viewModel.setStrategy(updated)
                                    }
                                )

                                IntParameterStepper(
                                    title = "Slow Period",
                                    value = currentStrategy.indicatorConfig.maParams.slowPeriod,
                                    range = 10..200,
                                    unit = "bars",
                                    onValueChange = { newVal ->
                                        val updated = currentStrategy.copy(
                                            indicatorConfig = currentStrategy.indicatorConfig.copy(
                                                maParams = currentStrategy.indicatorConfig.maParams.copy(slowPeriod = newVal)
                                            )
                                        )
                                        currentStrategy = updated
                                        viewModel.setStrategy(updated)
                                    }
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Use Exponential MA (EMA)", style = MaterialTheme.typography.bodyMedium, color = BentoTextPrimary)
                                    Switch(
                                        checked = currentStrategy.indicatorConfig.maParams.useEma,
                                        onCheckedChange = { isEma ->
                                            val updated = currentStrategy.copy(
                                                indicatorConfig = currentStrategy.indicatorConfig.copy(
                                                    maParams = currentStrategy.indicatorConfig.maParams.copy(useEma = isEma)
                                                )
                                            )
                                            currentStrategy = updated
                                            viewModel.setStrategy(updated)
                                        },
                                        colors = SwitchDefaults.colors(checkedThumbColor = BentoLilac, checkedTrackColor = BentoLilacContainer)
                                    )
                                }
                            }
                        }
                    }
                }

                StrategyType.RSI_MEAN_REVERSION -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = BentoCardBg),
                            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BentoBorder))
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("RSI Oscillator Settings", style = MaterialTheme.typography.titleSmall, color = BentoLilac, fontWeight = FontWeight.Bold)

                                IntParameterStepper(
                                    title = "RSI Period",
                                    value = currentStrategy.indicatorConfig.rsiParams.period,
                                    range = 5..30,
                                    unit = "bars",
                                    onValueChange = { newVal ->
                                        val updated = currentStrategy.copy(
                                            indicatorConfig = currentStrategy.indicatorConfig.copy(
                                                rsiParams = currentStrategy.indicatorConfig.rsiParams.copy(period = newVal)
                                            )
                                        )
                                        currentStrategy = updated
                                        viewModel.setStrategy(updated)
                                    }
                                )

                                DoubleParameterSlider(
                                    title = "Oversold Threshold (Buy Trigger)",
                                    value = currentStrategy.indicatorConfig.rsiParams.oversoldThreshold,
                                    valueRange = 15f..45f,
                                    displayFormatter = { "${it.toInt()}" },
                                    onValueChange = { newVal ->
                                        val updated = currentStrategy.copy(
                                            indicatorConfig = currentStrategy.indicatorConfig.copy(
                                                rsiParams = currentStrategy.indicatorConfig.rsiParams.copy(oversoldThreshold = newVal)
                                            )
                                        )
                                        currentStrategy = updated
                                        viewModel.setStrategy(updated)
                                    }
                                )

                                DoubleParameterSlider(
                                    title = "Overbought Threshold (Sell Trigger)",
                                    value = currentStrategy.indicatorConfig.rsiParams.overboughtThreshold,
                                    valueRange = 55f..85f,
                                    displayFormatter = { "${it.toInt()}" },
                                    onValueChange = { newVal ->
                                        val updated = currentStrategy.copy(
                                            indicatorConfig = currentStrategy.indicatorConfig.copy(
                                                rsiParams = currentStrategy.indicatorConfig.rsiParams.copy(overboughtThreshold = newVal)
                                            )
                                        )
                                        currentStrategy = updated
                                        viewModel.setStrategy(updated)
                                    }
                                )
                            }
                        }
                    }
                }

                StrategyType.MACD_MOMENTUM -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = BentoCardBg),
                            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BentoBorder))
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("MACD Parameters", style = MaterialTheme.typography.titleSmall, color = BentoLilac, fontWeight = FontWeight.Bold)

                                IntParameterStepper(
                                    title = "Fast Period",
                                    value = currentStrategy.indicatorConfig.macdParams.fastPeriod,
                                    range = 5..20,
                                    unit = "bars",
                                    onValueChange = { newVal ->
                                        val updated = currentStrategy.copy(
                                            indicatorConfig = currentStrategy.indicatorConfig.copy(
                                                macdParams = currentStrategy.indicatorConfig.macdParams.copy(fastPeriod = newVal)
                                            )
                                        )
                                        currentStrategy = updated
                                        viewModel.setStrategy(updated)
                                    }
                                )

                                IntParameterStepper(
                                    title = "Slow Period",
                                    value = currentStrategy.indicatorConfig.macdParams.slowPeriod,
                                    range = 15..50,
                                    unit = "bars",
                                    onValueChange = { newVal ->
                                        val updated = currentStrategy.copy(
                                            indicatorConfig = currentStrategy.indicatorConfig.copy(
                                                macdParams = currentStrategy.indicatorConfig.macdParams.copy(slowPeriod = newVal)
                                            )
                                        )
                                        currentStrategy = updated
                                        viewModel.setStrategy(updated)
                                    }
                                )

                                IntParameterStepper(
                                    title = "Signal Smoothing Period",
                                    value = currentStrategy.indicatorConfig.macdParams.signalPeriod,
                                    range = 3..15,
                                    unit = "bars",
                                    onValueChange = { newVal ->
                                        val updated = currentStrategy.copy(
                                            indicatorConfig = currentStrategy.indicatorConfig.copy(
                                                macdParams = currentStrategy.indicatorConfig.macdParams.copy(signalPeriod = newVal)
                                            )
                                        )
                                        currentStrategy = updated
                                        viewModel.setStrategy(updated)
                                    }
                                )
                            }
                        }
                    }
                }

                StrategyType.BOLLINGER_BREAKOUT, StrategyType.BOLLINGER_REVERSION -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = BentoCardBg),
                            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BentoBorder))
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Bollinger Bands Settings", style = MaterialTheme.typography.titleSmall, color = BentoLilac, fontWeight = FontWeight.Bold)

                                IntParameterStepper(
                                    title = "Moving Average Period",
                                    value = currentStrategy.indicatorConfig.bollingerParams.period,
                                    range = 10..50,
                                    unit = "bars",
                                    onValueChange = { newVal ->
                                        val updated = currentStrategy.copy(
                                            indicatorConfig = currentStrategy.indicatorConfig.copy(
                                                bollingerParams = currentStrategy.indicatorConfig.bollingerParams.copy(period = newVal)
                                            )
                                        )
                                        currentStrategy = updated
                                        viewModel.setStrategy(updated)
                                    }
                                )

                                DoubleParameterSlider(
                                    title = "Standard Deviation Multiplier",
                                    value = currentStrategy.indicatorConfig.bollingerParams.stdDevMultiplier,
                                    valueRange = 1.0f..3.5f,
                                    displayFormatter = { String.format("%.2fσ", it) },
                                    onValueChange = { newVal ->
                                        val updated = currentStrategy.copy(
                                            indicatorConfig = currentStrategy.indicatorConfig.copy(
                                                bollingerParams = currentStrategy.indicatorConfig.bollingerParams.copy(stdDevMultiplier = newVal)
                                            )
                                        )
                                        currentStrategy = updated
                                        viewModel.setStrategy(updated)
                                    }
                                )
                            }
                        }
                    }
                }

                StrategyType.SUPERTREND_RUN -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = BentoCardBg),
                            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BentoBorder))
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Supertrend ATR Settings", style = MaterialTheme.typography.titleSmall, color = BentoLilac, fontWeight = FontWeight.Bold)

                                IntParameterStepper(
                                    title = "ATR Period",
                                    value = currentStrategy.indicatorConfig.supertrendParams.atrPeriod,
                                    range = 5..30,
                                    unit = "bars",
                                    onValueChange = { newVal ->
                                        val updated = currentStrategy.copy(
                                            indicatorConfig = currentStrategy.indicatorConfig.copy(
                                                supertrendParams = currentStrategy.indicatorConfig.supertrendParams.copy(atrPeriod = newVal)
                                            )
                                        )
                                        currentStrategy = updated
                                        viewModel.setStrategy(updated)
                                    }
                                )

                                DoubleParameterSlider(
                                    title = "ATR Multiplier",
                                    value = currentStrategy.indicatorConfig.supertrendParams.multiplier,
                                    valueRange = 1.5f..5.0f,
                                    displayFormatter = { String.format("%.1fx", it) },
                                    onValueChange = { newVal ->
                                        val updated = currentStrategy.copy(
                                            indicatorConfig = currentStrategy.indicatorConfig.copy(
                                                supertrendParams = currentStrategy.indicatorConfig.supertrendParams.copy(multiplier = newVal)
                                            )
                                        )
                                        currentStrategy = updated
                                        viewModel.setStrategy(updated)
                                    }
                                )
                            }
                        }
                    }
                }

                StrategyType.TURTLE_BREAKOUT -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = BentoCardBg),
                            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BentoBorder))
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Turtle Donchian Channel", style = MaterialTheme.typography.titleSmall, color = BentoLilac, fontWeight = FontWeight.Bold)

                                IntParameterStepper(
                                    title = "Breakout Channel Period",
                                    value = currentStrategy.indicatorConfig.donchianParams.period,
                                    range = 10..55,
                                    unit = "bars",
                                    onValueChange = { newVal ->
                                        val updated = currentStrategy.copy(
                                            indicatorConfig = currentStrategy.indicatorConfig.copy(
                                                donchianParams = currentStrategy.indicatorConfig.donchianParams.copy(period = newVal)
                                            )
                                        )
                                        currentStrategy = updated
                                        viewModel.setStrategy(updated)
                                    }
                                )
                            }
                        }
                    }
                }

                StrategyType.OPENING_RANGE_BREAKOUT, StrategyType.TRENDLINE_BOUNCE, StrategyType.TRENDLINE_BREAK, StrategyType.A_PLUS_TRENDLINE -> {
                    item {
                        IndicatorThresholdConfigurationCard(
                            strategy = currentStrategy,
                            onStrategyChanged = { updated ->
                                currentStrategy = updated
                                viewModel.setStrategy(updated)
                            }
                        )
                    }
                }
                StrategyType.SMC_CONCEPTS, StrategyType.ICT_CONCEPTS, StrategyType.SMC_ICT_CONCEPTS -> {
                    item {
                        SmcConceptConfigCard(
                            strategy = currentStrategy,
                            onStrategyChanged = { updated ->
                                currentStrategy = updated
                                viewModel.setStrategy(updated)
                            },
                            smcMetrics = null,
                            backtestMetrics = null
                        )
                    }
                }
            }

            // Apply & Run CTA
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        viewModel.setStrategy(currentStrategy)
                        onBacktestNow()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("apply_strategy_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BentoLilac, contentColor = BentoLilacContainer)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Run")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("APPLY & RUN IN LAB", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(30.dp))
            }
        }

        // Save Strategy Name Dialog
        if (showSaveDialog) {
            AlertDialog(
                onDismissRequest = { showSaveDialog = false },
                title = { Text("Save Custom Strategy", color = BentoTextPrimary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Enter a unique name to store this strategy setup in your local database:", color = BentoTextSecondary, style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(
                            value = saveNameInput,
                            onValueChange = { saveNameInput = it },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BentoLilac,
                                unfocusedBorderColor = BentoBorder,
                                focusedTextColor = BentoTextPrimary,
                                unfocusedTextColor = BentoTextPrimary
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().testTag("strategy_name_input")
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (saveNameInput.isNotBlank()) {
                                viewModel.saveCurrentStrategy(saveNameInput.trim())
                                showSaveDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BentoLilac, contentColor = BentoLilacContainer),
                        shape = CircleShape,
                        modifier = Modifier.testTag("confirm_save_strategy_button")
                    ) {
                        Text("Save to Library", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSaveDialog = false }) {
                        Text("Cancel", color = BentoTextSecondary)
                    }
                },
                shape = RoundedCornerShape(24.dp),
                containerColor = BentoCardBg
            )
        }
    }
}
