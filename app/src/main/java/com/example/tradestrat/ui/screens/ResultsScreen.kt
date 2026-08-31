package com.example.tradestrat.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.example.ui.theme.LocalAppTheme
import java.text.DecimalFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    viewModel: BacktestViewModel,
    modifier: Modifier = Modifier,
    onNavigateToBacktest: () -> Unit = {}
) {
    val theme = LocalAppTheme.current
    val currentResult by viewModel.currentResult.collectAsState()
    val isBacktesting by viewModel.isBacktesting.collectAsState()
    val sessionAnalytics by viewModel.sessionAnalytics.collectAsState()
    val selectedTradeForDetail by viewModel.selectedTradeForDetail.collectAsState()
    val df = remember { DecimalFormat("#,##0.00") }

    val result = currentResult

    if (result == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(theme.background)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = theme.surfaceElevated,
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.QueryStats,
                            contentDescription = null,
                            tint = theme.brandPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                Text(
                    text = "No Backtest Results Yet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = theme.textPrimary
                )
                Text(
                    text = "Select a strategy and run a historical backtest to view comprehensive quantitative analytics and performance curves.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.textSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Button(
                    onClick = onNavigateToBacktest,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.brandPrimary)
                ) {
                    Text("Go to Backtest Setup", fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    val metrics = result.metrics
    val isProfitable = metrics.netProfitDollars >= 0.0

    // Trade Detail Sheet
    if (selectedTradeForDetail != null) {
        TradeDetailSheet(
            trade = selectedTradeForDetail!!,
            strategy = result.strategy,
            asset = result.asset,
            onAddToJournal = { notes, tags ->
                viewModel.createJournalEntryFromTrade(selectedTradeForDetail!!, notes, tags)
            },
            onDismiss = { viewModel.selectTradeForDetail(null) }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(theme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)
    ) {
        // 1. MANDATORY HEADER & COMPLIANCE DISCLAIMER
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("results_header_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.borderSubtle))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = theme.brandPrimary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = result.strategy.strategyType.badge,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = theme.brandPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = result.strategy.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = theme.textPrimary
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = theme.accentGreen.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "HISTORICAL BACKTEST RESULTS",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.accentGreen,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = "${result.asset.symbol} • ${result.timeframe.label} • ${result.dataSource.startDate} → ${result.dataSource.endDate}",
                        fontSize = 11.sp,
                        color = theme.textSecondary
                    )

                    // Mandatory Compliance Disclaimer
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = theme.surfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderSubtle.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "Historical backtest results are based on simulated executions using past market data. Past performance is not indicative of future returns.",
                            fontSize = 9.sp,
                            color = theme.textMuted,
                            lineHeight = 13.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        // 2. PRIMARY HERO METRICS: Net P&L & ROI
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("primary_kpi_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.borderSubtle))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("NET P&L (USD)", fontSize = 10.sp, color = theme.textMuted, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "${if (isProfitable) "+" else ""}$${df.format(metrics.netProfitDollars)}",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isProfitable) theme.accentGreen else theme.accentRed
                        )
                        Text(
                            text = "Capital: $${df.format(metrics.initialCapital)} → $${df.format(metrics.finalEquity)}",
                            fontSize = 10.sp,
                            color = theme.textSecondary
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text("RETURN ON INVESTMENT", fontSize = 10.sp, color = theme.textMuted, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "${if (isProfitable) "+" else ""}${df.format(metrics.netProfitPercent)}%",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isProfitable) theme.accentGreen else theme.accentRed
                        )
                        Text(
                            text = "Benchmark: ${df.format(metrics.benchmarkReturnPercent)}%",
                            fontSize = 10.sp,
                            color = theme.textSecondary
                        )
                    }
                }
            }
        }

        // 3. CORE METRICS GRID (All 17 Required Metrics)
        // Row A: Win Rate, Profit Factor, Expectancy, Max Drawdown
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricBox("Win Rate", "${df.format(metrics.winRatePercent)}%", "${metrics.winningTrades}W / ${metrics.losingTrades}L", Modifier.weight(1f), theme)
                MetricBox("Profit Factor", df.format(metrics.profitFactor), "Payoff: ${df.format(metrics.payoffRatio)}x", Modifier.weight(1f), theme)
                MetricBox("Expectancy", "$${df.format(metrics.expectancyDollars)}", "${String.format("%+.2fR", metrics.expectancyR)}", Modifier.weight(1f), theme)
                MetricBox("Max Drawdown", "-${df.format(metrics.maxDrawdownPercent)}%", "${metrics.maxDrawdownDurationBars} bars", Modifier.weight(1f), theme, valueColor = theme.accentRed)
            }
        }

        // Row B: Sharpe Ratio, Total Trades, Winning Trades, Losing Trades
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricBox("Sharpe Ratio", df.format(metrics.sharpeRatio), "Sortino: ${df.format(metrics.sortinoRatio)}", Modifier.weight(1f), theme)
                MetricBox("Total Trades", "${metrics.totalTrades}", "Avg Hold: ${metrics.avgHoldingBars.toInt()}b", Modifier.weight(1f), theme)
                MetricBox("Winning Trades", "${metrics.winningTrades}", "Gains: $${df.format(metrics.grossProfitDollars)}", Modifier.weight(1f), theme, valueColor = theme.accentGreen)
                MetricBox("Losing Trades", "${metrics.losingTrades}", "Losses: $${df.format(metrics.grossLossDollars)}", Modifier.weight(1f), theme, valueColor = theme.accentRed)
            }
        }

        // Row C: Average Win, Average Loss, Largest Win, Largest Loss
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricBox("Average Win", "+$${df.format(metrics.avgWinDollars)}", "${df.format(metrics.avgWinningTradePercent)}%", Modifier.weight(1f), theme, valueColor = theme.accentGreen)
                MetricBox("Average Loss", "-$${df.format(metrics.avgLossDollars)}", "${df.format(metrics.avgLosingTradePercent)}%", Modifier.weight(1f), theme, valueColor = theme.accentRed)
                MetricBox("Largest Win", "+$${df.format(metrics.largestWinningTradeDollars)}", "", Modifier.weight(1f), theme, valueColor = theme.accentGreen)
                MetricBox("Largest Loss", "-$${df.format(metrics.largestLosingTradeDollars)}", "", Modifier.weight(1f), theme, valueColor = theme.accentRed)
            }
        }

        // Row D: Max Consecutive Losses, Long Trades, Short Trades
        item {
            val longTrades = result.trades.count { it.direction == TradeDirection.LONG }
            val shortTrades = result.trades.count { it.direction == TradeDirection.SHORT }
            val longWins = result.trades.count { it.direction == TradeDirection.LONG && it.isWin }
            val shortWins = result.trades.count { it.direction == TradeDirection.SHORT && it.isWin }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricBox("Max Consec Losses", "${metrics.maxConsecutiveLosses}", "Wins: ${metrics.maxConsecutiveWins}", Modifier.weight(1f), theme, valueColor = if (metrics.maxConsecutiveLosses > 5) theme.accentRed else theme.textPrimary)
                MetricBox("Long Trades", "$longTrades", "WR: ${if (longTrades > 0) df.format((longWins.toDouble() / longTrades) * 100) else "0"}%", Modifier.weight(1f), theme)
                MetricBox("Short Trades", "$shortTrades", "WR: ${if (shortTrades > 0) df.format((shortWins.toDouble() / shortTrades) * 100) else "0"}%", Modifier.weight(1f), theme)
            }
        }

        // 4. VISUAL CHART 1 & 2: EQUITY CURVE & UNDERWATER DRAWDOWN CURVE
        item {
            EquityCurveChart(
                equityCurve = result.equityCurve,
                initialCapital = metrics.initialCapital,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 5. VISUAL CHART 3: MONTHLY PERFORMANCE
        item {
            MonthlyPerformanceCard(
                trades = result.trades,
                initialCapital = metrics.initialCapital,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 6. VISUAL CHART 4: TRADE DISTRIBUTION & CONCENTRATION
        item {
            TradeDistributionCard(
                trades = result.trades,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 7. ADDITIONAL PERFORMANCE BREAKDOWNS (Long vs Short & Global Sessions)
        item {
            PerformanceBreakdownCard(
                trades = result.trades,
                metrics = metrics,
                sessionAnalytics = sessionAnalytics
            )
        }

        // 8. TEMPORAL EDGE HEATMAP
        item {
            TradingHeatmapsCard(trades = result.trades)
        }

        // 9. TRADE EXECUTION LOG (Complete with sorting, filtering, and all trade fields)
        item {
            TradeLogList(
                trades = result.trades,
                onTradeClick = { trade -> viewModel.selectTradeForDetail(trade) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun MetricBox(
    label: String,
    value: String,
    subValue: String,
    modifier: Modifier = Modifier,
    theme: com.example.ui.theme.AppColors,
    valueColor: Color = theme.textPrimary
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = theme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderSubtle)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = label, fontSize = 9.sp, color = theme.textMuted, fontWeight = FontWeight.SemiBold)
            Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = valueColor)
            Text(text = subValue, fontSize = 8.5.sp, color = theme.textSecondary)
        }
    }
}
