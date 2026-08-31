package com.example.tradestrat.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    val asset by viewModel.selectedAsset.collectAsState()
    val tf by viewModel.selectedTimeframe.collectAsState()

    LaunchedEffect(Unit) {
        if (strategyLabItems.isEmpty()) {
            viewModel.runStrategyLabComparison()
        }
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
                            text = "Side-by-Side Evaluation on ${asset.symbol} (${tf.label})",
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
                    IconButton(onClick = { viewModel.runStrategyLabComparison() }) {
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
            // Explanatory Note
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = theme.surfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderSubtle)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Science, contentDescription = "Lab", tint = theme.brandPrimary)
                        Text(
                            text = "Evaluates Trendline, SMC, ICT, and SMC+ICT Confluence against identical market bars, spread, and risk rules to verify performance independence.",
                            fontSize = 11.sp,
                            color = theme.textSecondary,
                            lineHeight = 15.sp
                        )
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
