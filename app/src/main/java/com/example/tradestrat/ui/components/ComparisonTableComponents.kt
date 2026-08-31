package com.example.tradestrat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tradestrat.model.*
import com.example.ui.theme.LocalAppTheme
import java.text.DecimalFormat

/**
 * Full Performance Comparison Matrix Table with Sortable Columns.
 */
@Composable
fun StrategyComparisonTable(
    items: List<StrategyComparisonItem>,
    sortMetric: ComparisonSortMetric,
    onSortChange: (ComparisonSortMetric) -> Unit,
    onStrategyClick: (StrategyComparisonItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalAppTheme.current
    val df = remember { DecimalFormat("#,##0.00") }
    val dfInt = remember { DecimalFormat("#,##0") }

    val sortedItems = remember(items, sortMetric) {
        when (sortMetric) {
            ComparisonSortMetric.NET_PNL -> items.sortedByDescending { it.result.metrics.netProfitDollars }
            ComparisonSortMetric.PROFIT_FACTOR -> items.sortedByDescending { it.result.metrics.profitFactor }
            ComparisonSortMetric.EXPECTANCY -> items.sortedByDescending { it.result.metrics.expectancyDollars }
            ComparisonSortMetric.ROI -> items.sortedByDescending { it.result.metrics.netProfitPercent }
            ComparisonSortMetric.MAX_DRAWDOWN -> items.sortedBy { it.result.metrics.maxDrawdownPercent } // Lower is better
            ComparisonSortMetric.WIN_RATE -> items.sortedByDescending { it.result.metrics.winRatePercent }
            ComparisonSortMetric.TOTAL_TRADES -> items.sortedByDescending { it.result.metrics.totalTrades }
            ComparisonSortMetric.SHARPE_RATIO -> items.sortedByDescending { it.riskAdjusted.sharpeRatio ?: -999.0 }
            ComparisonSortMetric.CALMAR_RATIO -> items.sortedByDescending { it.riskAdjusted.calmarRatio ?: -999.0 }
            ComparisonSortMetric.SORTINO_RATIO -> items.sortedByDescending { it.riskAdjusted.sortinoRatio ?: -999.0 }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("strategy_comparison_table_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = theme.surfaceElevated),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.borderSubtle))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Table Header & Sort Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.TableChart,
                        contentDescription = "Comparison Table",
                        tint = theme.brandPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Performance Metric Matrix",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = theme.textPrimary
                    )
                }

                Text(
                    text = "Sorted by ${sortMetric.shortLabel}",
                    fontSize = 11.sp,
                    color = theme.brandPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Sort Pill Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ComparisonSortMetric.values().forEach { metric ->
                    val isSelected = sortMetric == metric
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSortChange(metric) },
                        label = { Text(metric.shortLabel, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = theme.brandPrimary,
                            selectedLabelColor = theme.brandPrimaryText,
                            containerColor = theme.surface,
                            labelColor = theme.textSecondary
                        ),
                        modifier = Modifier.testTag("sort_chip_${metric.name.lowercase()}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Horizontally Scrollable Table
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                Column {
                    // Header Row
                    Row(
                        modifier = Modifier
                            .background(theme.surface, RoundedCornerShape(8.dp))
                            .padding(vertical = 8.dp, horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TableCell("Strategy", 160.dp, Alignment.Start, isHeader = true, color = theme.textSecondary)
                        TableCell("Trades", 60.dp, Alignment.End, isHeader = true, color = theme.textSecondary)
                        TableCell("Win Rate", 75.dp, Alignment.End, isHeader = true, color = theme.textSecondary)
                        TableCell("Profit Factor", 85.dp, Alignment.End, isHeader = true, color = theme.textSecondary)
                        TableCell("Expectancy", 85.dp, Alignment.End, isHeader = true, color = theme.textSecondary)
                        TableCell("Net P&L", 90.dp, Alignment.End, isHeader = true, color = theme.textSecondary)
                        TableCell("ROI", 75.dp, Alignment.End, isHeader = true, color = theme.textSecondary)
                        TableCell("Max DD", 75.dp, Alignment.End, isHeader = true, color = theme.textSecondary)
                        TableCell("Avg Win", 80.dp, Alignment.End, isHeader = true, color = theme.textSecondary)
                        TableCell("Avg Loss", 80.dp, Alignment.End, isHeader = true, color = theme.textSecondary)
                        TableCell("W/L Ratio", 75.dp, Alignment.End, isHeader = true, color = theme.textSecondary)
                        TableCell("Max Consec W", 85.dp, Alignment.End, isHeader = true, color = theme.textSecondary)
                        TableCell("Max Consec L", 85.dp, Alignment.End, isHeader = true, color = theme.textSecondary)
                        TableCell("Ending Equity", 95.dp, Alignment.End, isHeader = true, color = theme.textSecondary)
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Strategy Data Rows
                    sortedItems.forEachIndexed { idx, item ->
                        val m = item.result.metrics
                        val isEven = idx % 2 == 0

                        Row(
                            modifier = Modifier
                                .clickable { onStrategyClick(item) }
                                .background(
                                    if (isEven) theme.surface.copy(alpha = 0.35f) else androidx.compose.ui.graphics.Color.Transparent,
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(vertical = 8.dp, horizontal = 10.dp)
                                .testTag("comparison_row_${item.strategy.id}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TableCell(item.strategy.name.take(22), 160.dp, Alignment.Start, color = theme.textPrimary, isBold = true)
                            TableCell(m.totalTrades.toString(), 60.dp, Alignment.End, color = theme.textPrimary)
                            TableCell("${df.format(m.winRatePercent)}%", 75.dp, Alignment.End, color = if (m.winRatePercent >= 50.0) theme.accentGreen else theme.accentRed)
                            TableCell(df.format(m.profitFactor), 85.dp, Alignment.End, color = if (m.profitFactor >= 1.5) theme.accentGreen else theme.textPrimary, isBold = true)
                            TableCell("$${df.format(m.expectancyDollars)}", 85.dp, Alignment.End, color = if (m.expectancyDollars >= 0) theme.accentGreen else theme.accentRed)
                            TableCell("$${df.format(m.netProfitDollars)}", 90.dp, Alignment.End, color = if (m.netProfitDollars >= 0) theme.accentGreen else theme.accentRed, isBold = true)
                            TableCell("${if (m.netProfitPercent >= 0) "+" else ""}${df.format(m.netProfitPercent)}%", 75.dp, Alignment.End, color = if (m.netProfitPercent >= 0) theme.accentGreen else theme.accentRed)
                            TableCell("${df.format(m.maxDrawdownPercent)}%", 75.dp, Alignment.End, color = theme.accentRed)
                            TableCell("$${df.format(m.avgWinDollars)}", 80.dp, Alignment.End, color = theme.textPrimary)
                            TableCell("-$${df.format(Math.abs(m.avgLossDollars))}", 80.dp, Alignment.End, color = theme.textPrimary)
                            TableCell(df.format(m.payoffRatio), 75.dp, Alignment.End, color = theme.textPrimary)
                            TableCell(m.maxConsecutiveWins.toString(), 85.dp, Alignment.End, color = theme.textSecondary)
                            TableCell(m.maxConsecutiveLosses.toString(), 85.dp, Alignment.End, color = theme.textSecondary)
                            TableCell("$${df.format(m.finalEquity)}", 95.dp, Alignment.End, color = theme.textPrimary, isBold = true)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Monthly Performance Matrix Table.
 */
@Composable
fun StrategyMonthlyMatrixTable(
    monthlyMatrix: List<MonthlyComparisonRow>,
    items: List<StrategyComparisonItem>,
    modifier: Modifier = Modifier
) {
    val theme = LocalAppTheme.current
    val df = remember { DecimalFormat("#,##0.00") }

    if (monthlyMatrix.isEmpty()) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("strategy_monthly_matrix_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = theme.surfaceElevated),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.borderSubtle))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = "Monthly Performance",
                        tint = theme.brandPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Monthly Performance Matrix",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = theme.textPrimary
                    )
                }

                Text(
                    text = "Net P&L ($)",
                    fontSize = 11.sp,
                    color = theme.textSecondary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Horizontally scrollable table
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                Column {
                    // Header Row
                    Row(
                        modifier = Modifier
                            .background(theme.surface, RoundedCornerShape(8.dp))
                            .padding(vertical = 8.dp, horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TableCell("Month", 100.dp, Alignment.Start, isHeader = true, color = theme.textSecondary)
                        items.forEach { item ->
                            TableCell(item.strategy.name.take(16), 110.dp, Alignment.End, isHeader = true, color = theme.textSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Month Rows
                    monthlyMatrix.forEachIndexed { idx, row ->
                        val isEven = idx % 2 == 0
                        Row(
                            modifier = Modifier
                                .background(
                                    if (isEven) theme.surface.copy(alpha = 0.35f) else androidx.compose.ui.graphics.Color.Transparent,
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(vertical = 7.dp, horizontal = 10.dp)
                                .testTag("monthly_row_${row.yearMonth}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TableCell(row.displayName, 100.dp, Alignment.Start, color = theme.textPrimary, isBold = true)
                            items.forEach { item ->
                                val pnl = row.strategyPnl[item.strategy.id] ?: 0.0
                                val pnlStr = if (pnl >= 0) "+$${df.format(pnl)}" else "-$${df.format(Math.abs(pnl))}"
                                val color = if (pnl > 0) theme.accentGreen else if (pnl < 0) theme.accentRed else theme.textSecondary
                                TableCell(pnlStr, 110.dp, Alignment.End, color = color, isBold = pnl != 0.0)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Summary Consistency Row (Profitable Months / Losing Months)
                    Row(
                        modifier = Modifier
                            .background(theme.brandPrimaryContainer.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .padding(vertical = 8.dp, horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TableCell("Profitable Months", 100.dp, Alignment.Start, color = theme.brandPrimary, isBold = true)
                        items.forEach { item ->
                            val totalM = item.profitableMonthsCount + item.losingMonthsCount
                            TableCell("${item.profitableMonthsCount}/$totalM (${if (totalM > 0) (item.profitableMonthsCount * 100 / totalM) else 0}%)", 110.dp, Alignment.End, color = theme.textPrimary, isBold = true)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Risk-Adjusted Ratios & Trade Distribution Component.
 */
@Composable
fun StrategyRiskAndDistributionCard(
    items: List<StrategyComparisonItem>,
    modifier: Modifier = Modifier
) {
    val theme = LocalAppTheme.current
    val df = remember { DecimalFormat("#,##0.00") }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("strategy_risk_distribution_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = theme.surfaceElevated),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.borderSubtle))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.QueryStats,
                        contentDescription = "Risk Adjusted",
                        tint = theme.brandPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Risk-Adjusted Ratios & Distribution",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = theme.textPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Horizontally Scrollable Table
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                Column {
                    // Header Row
                    Row(
                        modifier = Modifier
                            .background(theme.surface, RoundedCornerShape(8.dp))
                            .padding(vertical = 8.dp, horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TableCell("Strategy", 160.dp, Alignment.Start, isHeader = true, color = theme.textSecondary)
                        TableCell("Sharpe", 70.dp, Alignment.End, isHeader = true, color = theme.textSecondary)
                        TableCell("Sortino", 70.dp, Alignment.End, isHeader = true, color = theme.textSecondary)
                        TableCell("Calmar", 70.dp, Alignment.End, isHeader = true, color = theme.textSecondary)
                        TableCell("Median Trade", 90.dp, Alignment.End, isHeader = true, color = theme.textSecondary)
                        TableCell("P25 Trade", 85.dp, Alignment.End, isHeader = true, color = theme.textSecondary)
                        TableCell("P75 Trade", 85.dp, Alignment.End, isHeader = true, color = theme.textSecondary)
                        TableCell("Max Winner", 90.dp, Alignment.End, isHeader = true, color = theme.textSecondary)
                        TableCell("Max Loser", 90.dp, Alignment.End, isHeader = true, color = theme.textSecondary)
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    items.forEachIndexed { idx, item ->
                        val r = item.riskAdjusted
                        val dist = item.distribution
                        val isEven = idx % 2 == 0

                        Row(
                            modifier = Modifier
                                .background(
                                    if (isEven) theme.surface.copy(alpha = 0.35f) else androidx.compose.ui.graphics.Color.Transparent,
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(vertical = 7.dp, horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TableCell(item.strategy.name.take(22), 160.dp, Alignment.Start, color = theme.textPrimary, isBold = true)
                            TableCell(r.sharpeRatio?.let { df.format(it) } ?: "N/A", 70.dp, Alignment.End, color = theme.textPrimary)
                            TableCell(r.sortinoRatio?.let { df.format(it) } ?: "N/A", 70.dp, Alignment.End, color = theme.textPrimary)
                            TableCell(r.calmarRatio?.let { df.format(it) } ?: "N/A", 70.dp, Alignment.End, color = theme.textPrimary)
                            TableCell("$${df.format(dist.medianTradeDollars)}", 90.dp, Alignment.End, color = theme.textPrimary)
                            TableCell("$${df.format(dist.p25TradeDollars)}", 85.dp, Alignment.End, color = theme.textSecondary)
                            TableCell("$${df.format(dist.p75TradeDollars)}", 85.dp, Alignment.End, color = theme.textSecondary)
                            TableCell("+$${df.format(dist.largestWinnerDollars)}", 90.dp, Alignment.End, color = theme.accentGreen)
                            TableCell("-$${df.format(Math.abs(dist.largestLoserDollars))}", 90.dp, Alignment.End, color = theme.accentRed)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TableCell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    alignment: Alignment.Horizontal = Alignment.Start,
    isHeader: Boolean = false,
    color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    isBold: Boolean = false
) {
    Box(
        modifier = Modifier
            .width(width)
            .padding(horizontal = 4.dp),
        contentAlignment = when (alignment) {
            Alignment.End -> Alignment.CenterEnd
            Alignment.CenterHorizontally -> Alignment.Center
            else -> Alignment.CenterStart
        }
    ) {
        Text(
            text = text,
            fontSize = if (isHeader) 11.sp else 12.sp,
            fontWeight = if (isHeader || isBold) FontWeight.Bold else FontWeight.Normal,
            color = color,
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            textAlign = if (alignment == Alignment.End) TextAlign.End else TextAlign.Start
        )
    }
}
