package com.example.tradestrat.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tradestrat.data.MarketDataProvider
import com.example.tradestrat.model.*
import com.example.tradestrat.ui.BacktestViewModel
import com.example.tradestrat.ui.components.EquityCurveChart
import com.example.ui.theme.LocalAppTheme
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: BacktestViewModel,
    modifier: Modifier = Modifier,
    onNavigateToBacktest: () -> Unit = {},
    onNavigateToReplay: () -> Unit = {},
    onNavigateToStrategyLab: () -> Unit = {},
    onNavigateToJournal: () -> Unit = {},
    onNavigateToSmcIct: () -> Unit = {},
    onNavigateToResults: () -> Unit = {},
    onNavigateToCompare: () -> Unit = {}
) {
    val theme = LocalAppTheme.current
    val selectedAsset by viewModel.selectedAsset.collectAsState()
    val selectedTimeframe by viewModel.selectedTimeframe.collectAsState()
    val selectedStrategy by viewModel.selectedStrategy.collectAsState()
    val currentResult by viewModel.currentResult.collectAsState()
    val isBacktesting by viewModel.isBacktesting.collectAsState()
    val dataFetchError by viewModel.dataFetchError.collectAsState()
    val progress by viewModel.backtestProgress.collectAsState()

    val result = currentResult
    val isProfitable = (result?.metrics?.netProfitDollars ?: 0.0) >= 0.0

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(theme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
    ) {
        // App Header
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
                        text = "Arasum Trading",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = theme.textPrimary,
                        fontSize = 24.sp
                    )
                    Text(
                        text = "Quantitative Trading Research & Backtesting",
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.textSecondary,
                        fontSize = 12.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isBacktesting) theme.brandPrimaryContainer else theme.surfaceElevated,
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.borderSubtle))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (isBacktesting) theme.brandPrimary else theme.accentGreen,
                                    shape = CircleShape
                                )
                        )
                        Text(
                            text = if (isBacktesting) "Testing..." else "Ready",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = theme.textPrimary
                        )
                    }
                }
            }
        }

        // Real-time Progress Card if running
        if (isBacktesting && progress.isRunning) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.surfaceElevated),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.brandPrimary))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Running Backtest...", fontWeight = FontWeight.Bold, color = theme.textPrimary)
                            TextButton(onClick = { viewModel.cancelBacktest() }) {
                                Text("Cancel", color = theme.accentRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = theme.brandPrimary)
                        Text(
                            text = "${progress.strategyName} • ${progress.symbol} (${progress.timeframe}) • ${progress.currentDateStr}",
                            fontSize = 11.sp,
                            color = theme.textSecondary
                        )
                    }
                }
            }
        }

        // Primary Action Buttons (2x2 Grid)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ActionBigCard(
                        title = "RUN BACKTEST",
                        subtitle = "Configure & execute",
                        icon = Icons.Default.PlayArrow,
                        accentColor = theme.brandPrimary,
                        onClick = onNavigateToBacktest,
                        modifier = Modifier.weight(1f),
                        theme = theme
                    )

                    ActionBigCard(
                        title = "REPLAY",
                        subtitle = "Historical bar replay",
                        icon = Icons.Default.FastForward,
                        accentColor = theme.brandSecondary,
                        onClick = onNavigateToReplay,
                        modifier = Modifier.weight(1f),
                        theme = theme
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ActionBigCard(
                        title = "COMPARE",
                        subtitle = "Multi-strategy matrix",
                        icon = Icons.Default.CompareArrows,
                        accentColor = Color(0xFF38BDF8),
                        onClick = onNavigateToCompare,
                        modifier = Modifier.weight(1f),
                        theme = theme
                    )

                    ActionBigCard(
                        title = "TRADE JOURNAL",
                        subtitle = "Notes & thesis",
                        icon = Icons.Default.BookmarkBorder,
                        accentColor = Color(0xFFF59E0B),
                        onClick = onNavigateToJournal,
                        modifier = Modifier.weight(1f),
                        theme = theme
                    )
                }
            }
        }

        // Error Banner if present
        if (dataFetchError != null) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = theme.accentRed.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, theme.accentRed.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Warning, contentDescription = "Error", tint = theme.accentRed)
                        Text(text = dataFetchError ?: "", color = theme.accentRed, fontSize = 12.sp)
                    }
                }
            }
        }

        // LAST BACKTEST CARD
        item {
            if (result != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToResults() }
                        .testTag("dashboard_last_backtest_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.surface),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.borderSubtle))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Card Header: Strategy & Timeframe Info
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "LAST BACKTEST",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = theme.brandPrimary,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = theme.brandPrimary.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = result.strategy.strategyType.name,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = theme.brandPrimary,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${result.strategy.name} • ${result.asset.symbol} (${result.timeframe.label})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = theme.textPrimary
                                )
                                Text(
                                    text = "${result.dataSource.startDate} → ${result.dataSource.endDate}",
                                    fontSize = 11.sp,
                                    color = theme.textMuted
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "View Details",
                                tint = theme.textMuted
                            )
                        }

                        HorizontalDivider(color = theme.borderSubtle, thickness = 1.dp)

                        // Primary Performance Row: Net PnL & ROI
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("NET P&L (USD)", fontSize = 10.sp, color = theme.textMuted, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = String.format(Locale.US, "%+.2f", result.metrics.netProfitDollars),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isProfitable) theme.accentGreen else theme.accentRed
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text("ROI / RETURN", fontSize = 10.sp, color = theme.textMuted, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = String.format(Locale.US, "%+.2f%%", result.metrics.netProfitPercent),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isProfitable) theme.accentGreen else theme.accentRed
                                )
                            }
                        }

                        // Secondary Performance Grid
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MiniStat("Win Rate", String.format(Locale.US, "%.1f%%", result.metrics.winRatePercent), theme)
                            MiniStat("Profit Factor", String.format(Locale.US, "%.2f", result.metrics.profitFactor), theme)
                            MiniStat("Max DD", String.format(Locale.US, "%.1f%%", result.metrics.maxDrawdownPercent), theme)
                            MiniStat("Trades", result.metrics.totalTrades.toString(), theme)
                        }

                        // Quick Sparkline of Equity Curve
                        if (result.equityCurve.isNotEmpty()) {
                            EquityCurveChart(
                                equityCurve = result.equityCurve,
                                initialCapital = result.metrics.initialCapital,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                            )
                        }

                        Button(
                            onClick = onNavigateToResults,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.surfaceElevated)
                        ) {
                            Text("Open Full Results Dashboard", color = theme.brandPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.surface),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.borderSubtle))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Analytics, contentDescription = "Ready", tint = theme.brandPrimary, modifier = Modifier.size(36.dp))
                        Text("No Backtest Executed Yet", fontWeight = FontWeight.Bold, color = theme.textPrimary)
                        Button(
                            onClick = { viewModel.runBacktest() },
                            colors = ButtonDefaults.buttonColors(containerColor = theme.brandPrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Run Default Backtest", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Quick Market Switcher Row
        item {
            Text(
                text = "POPULAR MARKETS",
                style = MaterialTheme.typography.labelSmall,
                color = theme.textMuted,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        item {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(MarketDataProvider.ASSETS.take(6)) { asset ->
                    val isSelected = asset.symbol == selectedAsset.symbol
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setAsset(asset) },
                        label = { Text(asset.symbol, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = theme.brandPrimary.copy(alpha = 0.2f),
                            selectedLabelColor = theme.brandPrimary
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionBigCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    theme: com.example.ui.theme.AppColors
) {
    Surface(
        modifier = modifier
            .clickable { onClick() }
            .height(80.dp),
        shape = RoundedCornerShape(16.dp),
        color = theme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = accentColor.copy(alpha = 0.18f),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = icon, contentDescription = title, tint = accentColor, modifier = Modifier.size(20.dp))
                }
            }

            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = theme.textPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = theme.textSecondary
                )
            }
        }
    }
}

@Composable
private fun MiniStat(
    label: String,
    value: String,
    theme: com.example.ui.theme.AppColors
) {
    Column {
        Text(text = label, fontSize = 9.sp, color = theme.textMuted, fontWeight = FontWeight.SemiBold)
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = theme.textPrimary)
    }
}
