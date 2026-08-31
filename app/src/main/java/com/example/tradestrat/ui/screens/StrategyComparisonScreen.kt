package com.example.tradestrat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.example.tradestrat.data.MarketDataProvider
import com.example.tradestrat.model.*
import com.example.tradestrat.ui.BacktestViewModel
import com.example.tradestrat.ui.components.*
import com.example.ui.theme.LocalAppTheme
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StrategyComparisonScreen(
    viewModel: BacktestViewModel,
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {}
) {
    val theme = LocalAppTheme.current
    val selectedAsset by viewModel.selectedAsset.collectAsState()
    val selectedTimeframe by viewModel.selectedTimeframe.collectAsState()
    val selectedDatePreset by viewModel.selectedDatePreset.collectAsState()
    val riskParams by viewModel.riskParameters.collectAsState()
    val selectedStrategyIds by viewModel.comparisonSelectedStrategies.collectAsState()
    val comparisonResult by viewModel.multiStrategyComparisonResult.collectAsState()
    val isComparing by viewModel.isComparingMultiStrategies.collectAsState()
    val sortMetric by viewModel.comparisonSortMetric.collectAsState()
    val rankWeights by viewModel.rankWeights.collectAsState()
    val selectedDetailItem by viewModel.selectedComparisonDetailItem.collectAsState()

    var showConfigDialog by remember { mutableStateOf(false) }
    var showRankWeightsDialog by remember { mutableStateOf(false) }
    val showDualComparisonDialog by viewModel.showDualComparisonDialog.collectAsState()
    val dualComparisonData by viewModel.dualStrategyComparisonData.collectAsState()
    val isDualComparing by viewModel.isDualComparing.collectAsState()
    val df = remember { DecimalFormat("#,##0.00") }

    // Dual Strategy Comparison Dialog Modal
    if (showDualComparisonDialog) {
        DualStrategyComparisonDialog(
            comparisonData = dualComparisonData,
            isLoading = isDualComparing,
            onDismiss = { viewModel.dismissDualComparison() },
            onRerun = { viewModel.runDualStrategyComparison() }
        )
    }

    // Detail Bottom Sheet if item selected
    if (selectedDetailItem != null) {
        val item = selectedDetailItem!!
        ModalBottomSheet(
            onDismissRequest = { viewModel.selectComparisonDetailItem(null) },
            containerColor = theme.surfaceElevated,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = item.strategy.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = theme.textPrimary
                            )
                            Text(
                                text = item.strategy.strategyType.badge,
                                style = MaterialTheme.typography.bodySmall,
                                color = theme.brandPrimary
                            )
                        }
                        IconButton(onClick = { viewModel.selectComparisonDetailItem(null) }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = theme.textSecondary)
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = theme.surface),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.borderSubtle))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Strategy Logic Overview", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = theme.textPrimary)
                            Text(item.strategy.description, fontSize = 12.sp, color = theme.textSecondary)
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = theme.surface),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.borderSubtle))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Key Performance Summary", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = theme.textPrimary)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Net Profit:", color = theme.textSecondary, fontSize = 12.sp)
                                Text("$${df.format(item.result.metrics.netProfitDollars)} (${df.format(item.result.metrics.netProfitPercent)}%)", fontWeight = FontWeight.Bold, color = if (item.result.metrics.netProfitDollars >= 0) theme.accentGreen else theme.accentRed, fontSize = 12.sp)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Profit Factor:", color = theme.textSecondary, fontSize = 12.sp)
                                Text(df.format(item.result.metrics.profitFactor), fontWeight = FontWeight.Bold, color = theme.textPrimary, fontSize = 12.sp)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Win Rate:", color = theme.textSecondary, fontSize = 12.sp)
                                Text("${df.format(item.result.metrics.winRatePercent)}% (${item.result.metrics.winningTrades}/${item.result.metrics.totalTrades})", fontWeight = FontWeight.Bold, color = theme.textPrimary, fontSize = 12.sp)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Max Drawdown:", color = theme.textSecondary, fontSize = 12.sp)
                                Text("-${df.format(item.result.metrics.maxDrawdownPercent)}%", fontWeight = FontWeight.Bold, color = theme.accentRed, fontSize = 12.sp)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Sharpe / Sortino:", color = theme.textSecondary, fontSize = 12.sp)
                                Text("${item.riskAdjusted.sharpeRatio?.let { df.format(it) } ?: "N/A"} / ${item.riskAdjusted.sortinoRatio?.let { df.format(it) } ?: "N/A"}", fontWeight = FontWeight.Bold, color = theme.textPrimary, fontSize = 12.sp)
                            }
                        }
                    }
                }

                item {
                    EquityCurveChart(
                        equityCurve = item.result.equityCurve,
                        initialCapital = riskParams.initialCapital,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(theme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp)
    ) {
        // Top Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Strategy Comparison",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = theme.textPrimary,
                        fontSize = 22.sp
                    )
                    Text(
                        text = "Side-by-side backtest evaluation on identical market data",
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.textSecondary,
                        fontSize = 12.sp
                    )
                }

                FilledTonalButton(
                    onClick = { viewModel.runMultiStrategyComparison() },
                    enabled = !isComparing,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = theme.brandPrimary,
                        contentColor = theme.brandPrimaryText
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("btn_run_comparison")
                ) {
                    if (isComparing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = theme.brandPrimaryText)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Comparing...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Run Compare", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Dual Strategy Quick Comparison Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dual_strategy_benchmark_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.borderSubtle))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.CompareArrows, contentDescription = null, tint = theme.brandPrimary, modifier = Modifier.size(16.dp))
                            Text(
                                text = "High-Win-Rate vs A+ V1.0",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = theme.textPrimary
                            )
                        }
                        Text(
                            text = "Direct side-by-side performance & equity curve comparison",
                            fontSize = 11.sp,
                            color = theme.textSecondary
                        )
                    }

                    Button(
                        onClick = { viewModel.runDualStrategyComparison() },
                        enabled = !isDualComparing,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = theme.brandPrimary),
                        modifier = Modifier.testTag("btn_dual_compare_quick")
                    ) {
                        Text("Dual Compare", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        // Common Backtest Configuration Banner
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("common_config_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surfaceElevated),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.borderSubtle))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = theme.brandPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Common Backtest Environment", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = theme.textPrimary)
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = theme.brandPrimaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = "Strict Fair Comparison",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.brandPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Config Parameters Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ConfigBadge(label = "Asset", value = selectedAsset.symbol)
                        ConfigBadge(label = "Timeframe", value = selectedTimeframe.label)
                        ConfigBadge(label = "Period", value = selectedDatePreset.label)
                        ConfigBadge(label = "Capital", value = "$${df.format(riskParams.initialCapital)}")
                        ConfigBadge(label = "Fee", value = "${riskParams.commissionBps} bps")
                        ConfigBadge(label = "Slippage", value = "${riskParams.slippageBps} bps")
                        ConfigBadge(label = "Leverage", value = "${riskParams.leverage}x")
                    }
                }
            }
        }

        // Strategy Selector Chips
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("strategy_selection_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surfaceElevated),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.borderSubtle))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Checklist, contentDescription = null, tint = theme.brandPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Select Strategies (${selectedStrategyIds.size}/${StrategyDefinition.PRESETS.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = theme.textPrimary)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(
                                onClick = { viewModel.selectAllComparisonStrategies() },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("All", fontSize = 11.sp, color = theme.brandPrimary, fontWeight = FontWeight.Bold)
                            }
                            TextButton(
                                onClick = { viewModel.clearAllComparisonStrategies() },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("Clear", fontSize = 11.sp, color = theme.accentRed, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Chips
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StrategyDefinition.PRESETS.forEach { strat ->
                            val isSelected = selectedStrategyIds.contains(strat.id)
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.toggleComparisonStrategy(strat.id) },
                                label = {
                                    Text(
                                        text = strat.name,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = theme.brandPrimary,
                                    selectedLabelColor = theme.brandPrimaryText,
                                    containerColor = theme.surface,
                                    labelColor = theme.textSecondary
                                ),
                                modifier = Modifier.testTag("chip_strategy_${strat.id}")
                            )
                        }
                    }
                }
            }
        }

        // Comparison Results Display
        if (comparisonResult != null) {
            val res = comparisonResult!!

            // Fair-comparison Validation Status
            if (!res.validation.isValid) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = theme.accentRed.copy(alpha = 0.15f)),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.accentRed))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = theme.accentRed)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Validation Warning", fontWeight = FontWeight.Bold, color = theme.accentRed)
                            }
                            res.validation.validationErrors.forEach { err ->
                                Text("• $err", fontSize = 12.sp, color = theme.textPrimary)
                            }
                        }
                    }
                }
            } else {
                // 1. Transparent Strategy Ranking & Scoring
                item {
                    val rankings = remember(res.items, rankWeights) {
                        StrategyRankingCalculator.calculateRankings(res.items, rankWeights)
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("strategy_ranking_card"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = theme.surfaceElevated),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.borderSubtle))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = theme.accentGreen, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Multi-Metric Strategy Ranking", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = theme.textPrimary)
                                }

                                Text(
                                    text = "Transparent Composite",
                                    fontSize = 11.sp,
                                    color = theme.textSecondary
                                )
                            }

                            rankings.forEach { rankDetail ->
                                val item = res.items.firstOrNull { it.strategy.id == rankDetail.strategyId }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = theme.surface,
                                    border = CardDefaults.outlinedCardBorder().copy(
                                        brush = androidx.compose.ui.graphics.SolidColor(
                                            if (rankDetail.rank == 1) theme.brandPrimary else theme.borderSubtle
                                        )
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { item?.let { viewModel.selectComparisonDetailItem(it) } }
                                        .testTag("rank_item_${rankDetail.strategyId}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .background(
                                                        if (rankDetail.rank == 1) theme.brandPrimary else theme.surfaceElevated,
                                                        CircleShape
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "#${rankDetail.rank}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (rankDetail.rank == 1) theme.brandPrimaryText else theme.textPrimary
                                                )
                                            }

                                            Column {
                                                Text(rankDetail.strategyName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = theme.textPrimary)
                                                Text(
                                                    text = "PF #${rankDetail.metricRankings[ComparisonSortMetric.PROFIT_FACTOR]} • P&L #${rankDetail.metricRankings[ComparisonSortMetric.NET_PNL]} • DD #${rankDetail.metricRankings[ComparisonSortMetric.MAX_DRAWDOWN]}",
                                                    fontSize = 10.sp,
                                                    color = theme.textSecondary
                                                )
                                            }
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "${String.format("%.1f", rankDetail.compositeScore)} pts",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = theme.brandPrimary
                                            )
                                            Text(
                                                text = "Tap for detail",
                                                fontSize = 10.sp,
                                                color = theme.textSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Normalized Multi-Strategy Equity Curve ($10,000 baseline)
                item {
                    MultiStrategyEquityChart(items = res.items)
                }

                // 3. Multi-Strategy Drawdown Comparison Chart
                item {
                    MultiStrategyDrawdownChart(items = res.items)
                }

                // 4. Full Performance Comparison Table
                item {
                    StrategyComparisonTable(
                        items = res.items,
                        sortMetric = sortMetric,
                        onSortChange = { viewModel.setComparisonSortMetric(it) },
                        onStrategyClick = { viewModel.selectComparisonDetailItem(it) }
                    )
                }

                // 5. Monthly Performance Matrix Table
                item {
                    StrategyMonthlyMatrixTable(
                        monthlyMatrix = res.monthlyMatrix,
                        items = res.items
                    )
                }

                // 6. Risk-Adjusted Ratios & Trade Distribution
                item {
                    StrategyRiskAndDistributionCard(items = res.items)
                }
            }
        } else if (!isComparing) {
            // Initial placeholder state
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.surfaceElevated),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.borderSubtle))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.CompareArrows, contentDescription = null, tint = theme.brandPrimary, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Ready to Compare Strategies", fontWeight = FontWeight.Bold, color = theme.textPrimary, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Select strategies above and tap 'Run Compare' to evaluate performance on identical historical market data.",
                            fontSize = 12.sp,
                            color = theme.textSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigBadge(label: String, value: String) {
    val theme = LocalAppTheme.current
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = theme.surface,
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.borderSubtle))
    ) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, fontSize = 10.sp, color = theme.textSecondary)
            Text(value, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.textPrimary)
        }
    }
}
