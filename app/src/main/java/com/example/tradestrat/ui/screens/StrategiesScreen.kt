package com.example.tradestrat.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tradestrat.model.*
import com.example.tradestrat.ui.BacktestViewModel
import com.example.ui.theme.LocalAppTheme
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrategiesScreen(
    viewModel: BacktestViewModel,
    modifier: Modifier = Modifier,
    onNavigateToBacktest: () -> Unit = {}
) {
    val theme = LocalAppTheme.current
    val selectedStrategy by viewModel.selectedStrategy.collectAsState()
    val availableStrategies = StrategyDefinition.PRESETS
    val currentResult by viewModel.currentResult.collectAsState()

    var selectedTab by remember { mutableStateOf(0) } // 0: Catalog, 1: Parameter Tuner, 2: Optimizer, 3: Health Scorecard
    val tabs = listOf("Catalog", "Parameter Tuner", "Optimizer", "Health Scorecard")

    // Tuner state
    var fastMa by remember(selectedStrategy) { mutableStateOf(selectedStrategy.indicatorConfig.maParams.fastPeriod.toFloat()) }
    var slowMa by remember(selectedStrategy) { mutableStateOf(selectedStrategy.indicatorConfig.maParams.slowPeriod.toFloat()) }
    var rsiPeriod by remember(selectedStrategy) { mutableStateOf(selectedStrategy.indicatorConfig.rsiParams.period.toFloat()) }
    var rsiOverbought by remember(selectedStrategy) { mutableStateOf(selectedStrategy.indicatorConfig.rsiParams.overboughtThreshold.toFloat()) }
    var rsiOversold by remember(selectedStrategy) { mutableStateOf(selectedStrategy.indicatorConfig.rsiParams.oversoldThreshold.toFloat()) }

    var showSaveDialog by remember { mutableStateOf(false) }
    var customStrategyName by remember { mutableStateOf("") }
    var customStrategyDesc by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(theme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)
    ) {
        // Header
        item {
            Column(modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    text = "Strategy Engineering",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = theme.textPrimary,
                    fontSize = 24.sp
                )
                Text(
                    text = "Select, customize, and optimize quantitative trading rules",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textSecondary,
                    fontSize = 12.sp
                )
            }
        }

        // Navigation Tabs
        item {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = theme.surface,
                contentColor = theme.brandPrimary,
                edgePadding = 0.dp,
                divider = {},
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = theme.brandPrimary,
                            height = 3.dp
                        )
                    }
                },
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                fontSize = 13.sp,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTab == index) theme.brandPrimary else theme.textSecondary
                            )
                        },
                        modifier = Modifier.testTag("strategy_tab_$index")
                    )
                }
            }
        }

        // TAB 0: CATALOG
        if (selectedTab == 0) {
            items(availableStrategies) { strategy ->
                val isSelected = selectedStrategy.id == strategy.id
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { viewModel.setStrategy(strategy) }
                        .testTag("strategy_card_${strategy.id}"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isSelected) theme.brandPrimaryContainer.copy(alpha = 0.4f) else theme.surface),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(if (isSelected) theme.brandPrimary else theme.border)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) theme.brandPrimary else theme.surfaceElevated
                                ) {
                                    Text(
                                        text = strategy.strategyType.name,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else theme.textSecondary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Text(
                                    text = strategy.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = theme.textPrimary
                                )
                            }

                            if (isSelected) {
                                Surface(shape = CircleShape, color = theme.brandPrimary) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Active",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp).padding(2.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = strategy.description,
                            fontSize = 12.sp,
                            color = theme.textSecondary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (isSelected) {
                                TextButton(onClick = onNavigateToBacktest) {
                                    Text("Backtest This Strategy", fontWeight = FontWeight.Bold, color = theme.brandPrimary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // TAB 1: PARAMETER TUNER
        if (selectedTab == 1) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.surface),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.border))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = "Tuning Parameters: ${selectedStrategy.name}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = theme.textPrimary
                        )

                        // MA Fast Period Slider
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Fast EMA Period", fontSize = 13.sp, color = theme.textSecondary)
                                Text("${fastMa.toInt()} bars", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = theme.brandPrimary)
                            }
                            Slider(
                                value = fastMa,
                                onValueChange = { fastMa = it },
                                valueRange = 3f..50f,
                                steps = 46,
                                colors = SliderDefaults.colors(thumbColor = theme.brandPrimary, activeTrackColor = theme.brandPrimary)
                            )
                        }

                        // MA Slow Period Slider
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Slow SMA Period", fontSize = 13.sp, color = theme.textSecondary)
                                Text("${slowMa.toInt()} bars", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = theme.brandPrimary)
                            }
                            Slider(
                                value = slowMa,
                                onValueChange = { slowMa = it },
                                valueRange = 10f..200f,
                                steps = 189,
                                colors = SliderDefaults.colors(thumbColor = theme.brandPrimary, activeTrackColor = theme.brandPrimary)
                            )
                        }

                        // RSI Overbought / Oversold
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("RSI Overbought Threshold", fontSize = 13.sp, color = theme.textSecondary)
                                Text("${rsiOverbought.toInt()}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = theme.tradeRed)
                            }
                            Slider(
                                value = rsiOverbought,
                                onValueChange = { rsiOverbought = it },
                                valueRange = 55f..90f,
                                steps = 34,
                                colors = SliderDefaults.colors(thumbColor = theme.tradeRed, activeTrackColor = theme.tradeRed)
                            )
                        }

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("RSI Oversold Threshold", fontSize = 13.sp, color = theme.textSecondary)
                                Text("${rsiOversold.toInt()}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = theme.tradeGreen)
                            }
                            Slider(
                                value = rsiOversold,
                                onValueChange = { rsiOversold = it },
                                valueRange = 10f..45f,
                                steps = 34,
                                colors = SliderDefaults.colors(thumbColor = theme.tradeGreen, activeTrackColor = theme.tradeGreen)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showSaveDialog = true },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(theme.border))
                            ) {
                                Icon(Icons.Default.Save, contentDescription = "Save", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save Preset", fontSize = 13.sp, color = theme.textPrimary)
                            }

                            Button(
                                onClick = {
                                    val updated = selectedStrategy.copy(
                                        indicatorConfig = selectedStrategy.indicatorConfig.copy(
                                            maParams = selectedStrategy.indicatorConfig.maParams.copy(
                                                fastPeriod = fastMa.toInt(),
                                                slowPeriod = slowMa.toInt()
                                            ),
                                            rsiParams = selectedStrategy.indicatorConfig.rsiParams.copy(
                                                period = rsiPeriod.toInt(),
                                                overboughtThreshold = rsiOverbought.toDouble(),
                                                oversoldThreshold = rsiOversold.toDouble()
                                            )
                                        )
                                    )
                                    viewModel.setStrategy(updated)
                                    onNavigateToBacktest()
                                },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = theme.brandPrimary)
                            ) {
                                Text("Apply & Test", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        // TAB 2: STRATEGY OPTIMIZER
        if (selectedTab == 2) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.surface),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.border))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.AutoMode, contentDescription = null, tint = theme.brandPrimary)
                            Text(
                                text = "Grid Search Parameter Sweep",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = theme.textPrimary
                            )
                        }

                        Text(
                            text = "Simulate all 88 combinations of Fast EMA (5..15) and Slow SMA (18..30) to evaluate parameter sensitivity without curve fitting.",
                            fontSize = 12.sp,
                            color = theme.textSecondary
                        )

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = theme.surfaceElevated
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Optimization Space:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = theme.textPrimary)
                                Text("• Fast EMA: [5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15]", fontSize = 11.sp, color = theme.textSecondary)
                                Text("• Slow SMA: [18, 20, 21, 22, 24, 26, 28, 30]", fontSize = 11.sp, color = theme.textSecondary)
                                Text("• Optimization Metric: Profit Factor & Calmar Ratio", fontSize = 11.sp, color = theme.textSecondary)
                            }
                        }

                        Button(
                            onClick = { onNavigateToBacktest() },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.brandPrimary)
                        ) {
                            Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Launch Parameter Sweep", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        // TAB 3: HEALTH SCORECARD
        if (selectedTab == 3) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.surface),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.border))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Strategy Robustness & Health Scorecard",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = theme.textPrimary
                        )

                        val metrics = currentResult?.metrics
                        val trades = currentResult?.trades

                        HealthCheckItem(
                            title = "Sample Size Adequacy",
                            subtitle = if ((trades?.size ?: 0) >= 30) "Statistically significant (${trades?.size} trades)" else "Insufficient trades for high confidence (${trades?.size ?: 0} trades)",
                            passed = (trades?.size ?: 0) >= 30,
                            theme = theme
                        )

                        HealthCheckItem(
                            title = "Profit Factor Health",
                            subtitle = if ((metrics?.profitFactor ?: 0.0) >= 1.4) "Strong edge (${String.format(Locale.US, "%.2f", metrics?.profitFactor ?: 0.0)})" else "Marginal profit factor",
                            passed = (metrics?.profitFactor ?: 0.0) >= 1.4,
                            theme = theme
                        )

                        HealthCheckItem(
                            title = "Drawdown Risk Exposure",
                            subtitle = if ((metrics?.maxDrawdownPercent ?: 100.0) <= 25.0) "Contained drawdown (${String.format(Locale.US, "%.1f%%", metrics?.maxDrawdownPercent ?: 0.0)})" else "High drawdown risk",
                            passed = (metrics?.maxDrawdownPercent ?: 100.0) <= 25.0,
                            theme = theme
                        )

                        HealthCheckItem(
                            title = "Execution Realism",
                            subtitle = "Includes next-bar execution, 5 bps slippage, and exchange fees",
                            passed = true,
                            theme = theme
                        )
                    }
                }
            }
        }
    }

    // Save Custom Strategy Dialog
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save Custom Strategy", fontWeight = FontWeight.Bold, color = theme.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = customStrategyName,
                        onValueChange = { customStrategyName = it },
                        label = { Text("Strategy Name") },
                        placeholder = { Text("e.g., My EMA Trend Alpha") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = customStrategyDesc,
                        onValueChange = { customStrategyDesc = it },
                        label = { Text("Description") },
                        placeholder = { Text("Brief notes on indicators used") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (customStrategyName.isNotBlank()) {
                            val newStrat = StrategyDefinition(
                                id = "custom_${System.currentTimeMillis()}",
                                name = customStrategyName,
                                description = customStrategyDesc.ifBlank { "Custom user strategy" },
                                strategyType = selectedStrategy.strategyType,
                                indicatorConfig = selectedStrategy.indicatorConfig.copy(
                                    maParams = selectedStrategy.indicatorConfig.maParams.copy(
                                        fastPeriod = fastMa.toInt(),
                                        slowPeriod = slowMa.toInt()
                                    ),
                                    rsiParams = selectedStrategy.indicatorConfig.rsiParams.copy(
                                        period = rsiPeriod.toInt(),
                                        overboughtThreshold = rsiOverbought.toDouble(),
                                        oversoldThreshold = rsiOversold.toDouble()
                                    )
                                )
                            )
                            viewModel.setStrategy(newStrat)
                            showSaveDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.brandPrimary)
                ) {
                    Text("Save & Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancel", color = theme.textSecondary)
                }
            },
            containerColor = theme.surface,
            shape = RoundedCornerShape(18.dp)
        )
    }
}

@Composable
private fun HealthCheckItem(
    title: String,
    subtitle: String,
    passed: Boolean,
    theme: com.example.ui.theme.AppThemeColors
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = theme.textPrimary)
            Text(text = subtitle, fontSize = 11.sp, color = theme.textSecondary)
        }
        Surface(
            shape = CircleShape,
            color = if (passed) theme.tradeGreenContainer else theme.tradeRedContainer
        ) {
            Icon(
                imageVector = if (passed) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (passed) theme.tradeGreen else theme.tradeRed,
                modifier = Modifier.size(20.dp).padding(2.dp)
            )
        }
    }
}
