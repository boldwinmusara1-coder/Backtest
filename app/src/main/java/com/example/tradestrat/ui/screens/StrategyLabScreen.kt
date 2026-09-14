package com.example.tradestrat.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tradestrat.model.STRATEGY_ID_A_PLUS_V1_0
import com.example.tradestrat.model.STRATEGY_ID_TRENDLINE_BREAK_HIGH_WIN_RATE
import com.example.tradestrat.model.StrategyLabItem
import com.example.tradestrat.model.StrategyType
import com.example.tradestrat.ui.BacktestViewModel
import com.example.ui.theme.LocalAppTheme
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrategyLabScreen(
    viewModel: BacktestViewModel,
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
    onApplyStrategy: () -> Unit = {}
) {
    val theme = LocalAppTheme.current
    val strategyLabItems by viewModel.strategyLabItems.collectAsState()
    val isRunning by viewModel.isStrategyLabRunning.collectAsState()
    val currentAsset by viewModel.selectedAsset.collectAsState()
    val currentTf by viewModel.selectedTimeframe.collectAsState()
    val allStrategies by viewModel.allStrategies.collectAsState()

    var labAsset by remember { mutableStateOf(currentAsset) }
    var labTf by remember { mutableStateOf(currentTf) }
    var selectedStrategyIds by remember {
        mutableStateOf(
            setOf(
                STRATEGY_ID_TRENDLINE_BREAK_HIGH_WIN_RATE,
                STRATEGY_ID_A_PLUS_V1_0,
                allStrategies.firstOrNull { it.strategyType == StrategyType.SMC_CONCEPTS }?.id ?: "smc_default",
                allStrategies.firstOrNull { it.strategyType == StrategyType.ICT_CONCEPTS }?.id ?: "ict_default"
            )
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = theme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "STRATEGY LAB",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = theme.textPrimary
                        )
                        Text(
                            text = "Multi-Strategy Comparison Matrix • ${labAsset.symbol} (${labTf.label})",
                            style = MaterialTheme.typography.bodySmall,
                            color = theme.textSecondary,
                            fontSize = 11.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = theme.textPrimary)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val picked = allStrategies.filter { selectedStrategyIds.contains(it.id) }
                            if (picked.isNotEmpty()) {
                                viewModel.runStrategyLabComparison(
                                    strategiesToCompare = picked,
                                    explicitAsset = labAsset,
                                    explicitTf = labTf
                                )
                            }
                        },
                        enabled = !isRunning
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Rerun", tint = theme.brandPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = theme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Lab Configuration Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.surface),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.borderSubtle))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "LAB EXPERIMENT CONFIGURATION",
                            style = MaterialTheme.typography.labelSmall,
                            color = theme.brandPrimary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        // Asset Selector
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Asset", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = theme.textSecondary)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val popularAssets = listOf("BTC/USD", "ETH/USD", "SOL/USD", "SPY", "QQQ", "AAPL", "EUR/USD", "XAU/USD")
                                popularAssets.forEach { symbol ->
                                    val asset = com.example.tradestrat.data.MarketDataProvider.ASSETS.find { it.symbol == symbol } ?: com.example.tradestrat.data.MarketDataProvider.ASSETS.first()
                                    val isSel = labAsset.symbol == symbol
                                    FilterChip(
                                        selected = isSel,
                                        onClick = { labAsset = asset },
                                        label = { Text(symbol, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = theme.brandPrimary.copy(alpha = 0.2f),
                                            selectedLabelColor = theme.brandPrimary
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = isSel,
                                            borderColor = theme.borderSubtle,
                                            selectedBorderColor = theme.brandPrimary
                                        ),
                                        modifier = Modifier.height(30.dp)
                                    )
                                }
                            }
                        }

                        // Timeframe Selector
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Timeframe", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = theme.textSecondary)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    com.example.tradestrat.model.Timeframe.D1,
                                    com.example.tradestrat.model.Timeframe.H4,
                                    com.example.tradestrat.model.Timeframe.H1,
                                    com.example.tradestrat.model.Timeframe.M30,
                                    com.example.tradestrat.model.Timeframe.M15
                                ).forEach { tf ->
                                    val isSel = labTf == tf
                                    FilterChip(
                                        selected = isSel,
                                        onClick = { labTf = tf },
                                        label = { Text(tf.label, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = theme.brandPrimary.copy(alpha = 0.2f),
                                            selectedLabelColor = theme.brandPrimary
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = isSel,
                                            borderColor = theme.borderSubtle,
                                            selectedBorderColor = theme.brandPrimary
                                        ),
                                        modifier = Modifier.height(30.dp)
                                    )
                                }
                            }
                        }

                        // Strategy Selection Chips
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Select Strategies to Compare (2–6)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = theme.textSecondary)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                allStrategies.forEach { strat ->
                                    val isChecked = selectedStrategyIds.contains(strat.id)
                                    FilterChip(
                                        selected = isChecked,
                                        onClick = {
                                            selectedStrategyIds = if (isChecked) {
                                                if (selectedStrategyIds.size > 1) selectedStrategyIds - strat.id else selectedStrategyIds
                                            } else {
                                                selectedStrategyIds + strat.id
                                            }
                                        },
                                        label = { Text(strat.name, fontSize = 11.sp, fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Normal) },
                                        leadingIcon = if (isChecked) {
                                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                        } else null,
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = theme.brandPrimary.copy(alpha = 0.2f),
                                            selectedLabelColor = theme.brandPrimary
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = isChecked,
                                            borderColor = theme.borderSubtle,
                                            selectedBorderColor = theme.brandPrimary
                                        ),
                                        modifier = Modifier.height(30.dp)
                                    )
                                }
                            }
                        }

                        // Explicit Run Button
                        Button(
                            onClick = {
                                val picked = allStrategies.filter { selectedStrategyIds.contains(it.id) }
                                viewModel.runStrategyLabComparison(
                                    strategiesToCompare = picked,
                                    explicitAsset = labAsset,
                                    explicitTf = labTf
                                )
                            },
                            enabled = !isRunning,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.brandPrimary)
                        ) {
                            if (isRunning) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Running Lab Comparison...", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            } else {
                                Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("RUN STRATEGY LAB COMPARISON", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }

            // Multi-Strategy Equity Overlay Canvas
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.surface),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.borderSubtle))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "MULTI-EQUITY PERFORMANCE OVERLAY",
                            style = MaterialTheme.typography.labelSmall,
                            color = theme.brandPrimary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        val colors = listOf(
                            Color(0xFF38BDF8), // Cyan for Trendline
                            Color(0xFFA855F7), // Purple for SMC
                            Color(0xFFF59E0B), // Amber for ICT
                            Color(0xFF10B981)  // Emerald for Combined
                        )

                        if (isRunning) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = theme.brandPrimary)
                            }
                        } else {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = theme.surfaceElevated
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                                    val w = size.width
                                    val h = size.height

                                    // Find overall min and max across all equity curves
                                    var minEq = Double.MAX_VALUE
                                    var maxEq = Double.MIN_VALUE

                                    strategyLabItems.forEach { item ->
                                        item.result?.equityCurve?.forEach { pt ->
                                            if (pt.equity < minEq) minEq = pt.equity
                                            if (pt.equity > maxEq) maxEq = pt.equity
                                        }
                                    }

                                    if (minEq == Double.MAX_VALUE || maxEq == Double.MIN_VALUE || maxEq == minEq) {
                                        minEq = 9000.0
                                        maxEq = 11000.0
                                    }

                                    val range = (maxEq - minEq).coerceAtLeast(100.0)

                                    // Draw curves
                                    strategyLabItems.forEachIndexed { idx, item ->
                                        val eqList = item.result?.equityCurve ?: emptyList()
                                        if (eqList.size >= 2) {
                                            val strokeColor = colors.getOrElse(idx) { Color.White }
                                            val path = Path()
                                            eqList.forEachIndexed { ptIdx, pt ->
                                                val x = (ptIdx.toFloat() / (eqList.size - 1)) * w
                                                val y = (h - ((pt.equity - minEq) / range).toFloat() * h).coerceIn(4f, h - 4f)
                                                if (ptIdx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                            }
                                            drawPath(path, strokeColor, style = Stroke(width = 2.5f))
                                        }
                                    }
                                }
                            }
                        }

                        // Legend Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val labels = listOf("Trendline", "SMC", "ICT", "SMC+ICT")
                            labels.forEachIndexed { idx, label ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Surface(
                                        shape = RoundedCornerShape(3.dp),
                                        color = colors.getOrElse(idx) { Color.White },
                                        modifier = Modifier.size(8.dp)
                                    ) {}
                                    Text(text = label, fontSize = 10.sp, color = theme.textSecondary, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            // Strategy Scorecards
            item {
                Text(
                    text = "COMPARATIVE SCORECARD",
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.textMuted,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            items(strategyLabItems) { item ->
                val result = item.result
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.surface),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.borderSubtle))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = item.strategy.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = theme.textPrimary
                                )
                                Text(
                                    text = item.strategy.strategyType.name,
                                    fontSize = 10.sp,
                                    color = theme.brandPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            if (result != null) {
                                val netProfit = result.metrics.netProfitPercent
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (netProfit >= 0) theme.accentGreen.copy(alpha = 0.15f) else theme.accentRed.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = String.format(Locale.US, "%+.2f%%", netProfit),
                                        fontWeight = FontWeight.Bold,
                                        color = if (netProfit >= 0) theme.accentGreen else theme.accentRed,
                                        fontSize = 15.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        if (result != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                ScorecardMetric("Win Rate", String.format(Locale.US, "%.1f%%", result.metrics.winRatePercent), theme)
                                ScorecardMetric("Profit Factor", String.format(Locale.US, "%.2f", result.metrics.profitFactor), theme)
                                ScorecardMetric("Max Drawdown", String.format(Locale.US, "%.1f%%", result.metrics.maxDrawdownPercent), theme)
                                ScorecardMetric("Sharpe", String.format(Locale.US, "%.2f", result.metrics.sharpeRatio), theme)
                                ScorecardMetric("Trades", result.metrics.totalTrades.toString(), theme)
                            }

                            Button(
                                onClick = {
                                    viewModel.setStrategy(item.strategy)
                                    onApplyStrategy()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = theme.surfaceElevated)
                            ) {
                                Text("Select as Active Strategy", fontSize = 12.sp, color = theme.brandPrimary, fontWeight = FontWeight.Bold)
                            }
                        } else if (item.isEvaluating) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = theme.brandPrimary)
                        } else if (item.error != null) {
                            Text(text = "Error: ${item.error}", color = theme.accentRed, fontSize = 11.sp)
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(30.dp)) }
        }
    }
}

@Composable
private fun ScorecardMetric(
    label: String,
    value: String,
    theme: com.example.ui.theme.AppColors
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 9.sp, color = theme.textMuted, fontWeight = FontWeight.SemiBold)
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.textPrimary)
    }
}
