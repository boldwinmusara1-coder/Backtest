package com.example.tradestrat.ui.screens

import androidx.compose.foundation.background
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
import com.example.tradestrat.engine.OptimizationResult
import com.example.tradestrat.engine.RegimeComparisonResult
import com.example.tradestrat.engine.StrategyOptimizer
import com.example.tradestrat.ui.BacktestViewModel
import com.example.ui.theme.*

enum class OptimizerTab(val label: String) {
    GRID_SWEEP("Parameter Grid Sweep"),
    REGIME_STRESS("Regime Stress Test")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptimizerScreen(
    viewModel: BacktestViewModel,
    modifier: Modifier = Modifier,
    onNavigateToStudio: () -> Unit = {}
) {
    val selectedStrategy by viewModel.selectedStrategy.collectAsState()
    val optimizationResults by viewModel.optimizationResults.collectAsState()
    val isOptimizing by viewModel.isOptimizing.collectAsState()

    val regimeResults by viewModel.regimeComparison.collectAsState()
    val isComparingRegimes by viewModel.isComparingRegimes.collectAsState()

    var activeTab by remember { mutableStateOf(OptimizerTab.GRID_SWEEP) }
    var selectedSortMetric by remember { mutableStateOf(StrategyOptimizer.OptimizationMetric.NET_PROFIT) }

    val sortedOptResults = remember(optimizationResults, selectedSortMetric) {
        when (selectedSortMetric) {
            StrategyOptimizer.OptimizationMetric.NET_PROFIT -> optimizationResults.sortedByDescending { it.netProfitPercent }
            StrategyOptimizer.OptimizationMetric.SHARPE_RATIO -> optimizationResults.sortedByDescending { it.sharpeRatio }
            StrategyOptimizer.OptimizationMetric.PROFIT_FACTOR -> optimizationResults.sortedByDescending { it.profitFactor }
            StrategyOptimizer.OptimizationMetric.WIN_RATE -> optimizationResults.sortedByDescending { it.winRatePercent }
            StrategyOptimizer.OptimizationMetric.MIN_DRAWDOWN -> optimizationResults.sortedBy { it.maxDrawdownPercent }
        }
    }

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
                Column {
                    Text(
                        text = "ALGORITHMIC OPTIMIZER",
                        style = MaterialTheme.typography.labelSmall,
                        color = BentoLilac,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = "Parameter Sweep & Stress Test",
                        style = MaterialTheme.typography.titleLarge,
                        color = BentoTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Tabs Selector
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OptimizerTab.values().forEach { tab ->
                        val isSelected = activeTab == tab
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { activeTab = tab },
                            shape = CircleShape,
                            color = if (isSelected) BentoLilacContainer else BentoCardBg,
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = androidx.compose.ui.graphics.SolidColor(if (isSelected) BentoLilac else BentoBorder)
                            )
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSelected) BentoLilacText else BentoTextSecondary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            when (activeTab) {
                OptimizerTab.GRID_SWEEP -> {
                    // Sweep Action Banner Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = BentoCardBg),
                            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BentoBorder))
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "2D PARAMETER GRID SEARCH",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BentoTextMuted,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Sweeps indicator parameter matrix for ${selectedStrategy.name} to find optimal alpha edge and Sharpe ratio.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BentoTextSecondary
                                )

                                Button(
                                    onClick = { viewModel.runOptimization() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                        .testTag("run_optimization_button"),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = BentoLilac, contentColor = BentoLilacContainer),
                                    enabled = !isOptimizing
                                ) {
                                    if (isOptimizing) {
                                        CircularProgressIndicator(color = BentoLilacContainer, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Sweeping Parameter Matrix...", fontWeight = FontWeight.Bold)
                                    } else {
                                        Icon(Icons.Default.AutoFixHigh, contentDescription = "Run")
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("RUN PARAMETER SWEEP", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    if (sortedOptResults.isNotEmpty()) {
                        // Metric Sorting Pills
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("RANK PARAMETER COMBINATIONS BY:", style = MaterialTheme.typography.labelSmall, color = BentoTextMuted, fontSize = 10.sp)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    StrategyOptimizer.OptimizationMetric.values().forEach { metric ->
                                        FilterChip(
                                            selected = selectedSortMetric == metric,
                                            onClick = { selectedSortMetric = metric },
                                            label = { Text(metric.displayName, fontSize = 10.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = BentoLilacContainer,
                                                selectedLabelColor = BentoLilacText,
                                                containerColor = BentoCardBg,
                                                labelColor = BentoTextSecondary
                                            ),
                                            shape = CircleShape,
                                            modifier = Modifier.height(28.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Results List
                        items(sortedOptResults) { opt ->
                            OptimizationResultCard(
                                result = opt,
                                onApply = {
                                    viewModel.setStrategy(opt.strategy)
                                    onNavigateToStudio()
                                }
                            )
                        }
                    }
                }

                OptimizerTab.REGIME_STRESS -> {
                    // Regime Stress Test Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = BentoCardBg),
                            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BentoBorder))
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "MULTI-REGIME STRESS SIMULATION",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BentoTextMuted,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Simulates ${selectedStrategy.name} across Bull, Bear, Choppy, Volatile Breakout, and Crash Recovery regimes simultaneously to measure survivability.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BentoTextSecondary
                                )

                                Button(
                                    onClick = { viewModel.runRegimeStressTest() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                        .testTag("run_stress_test_button"),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = BentoLilac, contentColor = BentoLilacContainer),
                                    enabled = !isComparingRegimes
                                ) {
                                    if (isComparingRegimes) {
                                        CircularProgressIndicator(color = BentoLilacContainer, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Stress Testing All Regimes...", fontWeight = FontWeight.Bold)
                                    } else {
                                        Icon(Icons.Default.Bolt, contentDescription = "Run")
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("RUN MULTI-REGIME STRESS TEST", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    if (regimeResults.isNotEmpty()) {
                        items(regimeResults) { regRes ->
                            RegimeStressResultCard(regRes)
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

@Composable
private fun OptimizationResultCard(
    result: OptimizationResult,
    onApply: () -> Unit
) {
    val isProfit = result.netProfitPercent >= 0
    val pColor = if (isProfit) BentoGreen else BentoRed

    Card(
        modifier = Modifier.fillMaxWidth().testTag("opt_result_card_${result.param1Label}_${result.param1Value}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = BentoCardBg),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BentoBorder))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${result.param1Label}: ${result.param1Value.toInt()} • ${result.param2Label}: ${result.param2Value.toInt()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BentoTextPrimary,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "${if (isProfit) "+" else ""}${String.format("%.2f%%", result.netProfitPercent)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = pColor,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Sharpe: ${String.format("%.2f", result.sharpeRatio)}", style = MaterialTheme.typography.labelSmall, color = BentoTextSecondary)
                Text("Win: ${String.format("%.1f%%", result.winRatePercent)}", style = MaterialTheme.typography.labelSmall, color = BentoTextSecondary)
                Text("Max DD: ${String.format("%.1f%%", result.maxDrawdownPercent)}", style = MaterialTheme.typography.labelSmall, color = BentoTextSecondary)
                Text("PF: ${String.format("%.2f", result.profitFactor)}", style = MaterialTheme.typography.labelSmall, color = BentoTextSecondary)
            }

            Button(
                onClick = onApply,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = BentoLilacContainer, contentColor = BentoLilacText),
                modifier = Modifier.fillMaxWidth().height(36.dp)
            ) {
                Text("Apply Parameters to Strategy", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RegimeStressResultCard(result: RegimeComparisonResult) {
    val isProfit = result.netProfitPercent >= 0
    val pColor = if (isProfit) BentoGreen else BentoRed

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = BentoCardBg),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BentoBorder))
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(result.regime.title, style = MaterialTheme.typography.bodyMedium, color = BentoTextPrimary, fontWeight = FontWeight.Bold)
                Text("${result.totalTrades} Trades • Win: ${String.format("%.1f%%", result.winRatePercent)}", style = MaterialTheme.typography.labelSmall, color = BentoTextSecondary)
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${if (isProfit) "+" else ""}${String.format("%.2f%%", result.netProfitPercent)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = pColor,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "DD: ${String.format("%.1f%%", result.maxDrawdownPercent)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = BentoTextMuted
                )
            }
        }
    }
}
