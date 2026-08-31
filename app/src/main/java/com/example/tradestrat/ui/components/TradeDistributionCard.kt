package com.example.tradestrat.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tradestrat.model.Trade
import com.example.ui.theme.LocalAppTheme
import java.text.DecimalFormat
import kotlin.math.max

@Composable
fun TradeDistributionCard(
    trades: List<Trade>,
    modifier: Modifier = Modifier
) {
    val theme = LocalAppTheme.current
    val df = remember { DecimalFormat("#,##0.00") }

    if (trades.isEmpty()) return

    val distributionStats = remember(trades) {
        val sortedPnL = trades.map { it.pnlDollars }.sorted()
        val n = sortedPnL.size

        val p25 = sortedPnL[(n * 0.25).toInt().coerceIn(0, n - 1)]
        val median = sortedPnL[(n * 0.50).toInt().coerceIn(0, n - 1)]
        val p75 = sortedPnL[(n * 0.75).toInt().coerceIn(0, n - 1)]
        val iqr = p75 - p25

        val totalGrossProfit = trades.filter { it.pnlDollars > 0 }.sumOf { it.pnlDollars }
        val winningTradesSorted = trades.filter { it.pnlDollars > 0 }.map { it.pnlDollars }.sortedDescending()

        val top1PctCount = max(1, (winningTradesSorted.size * 0.01).toInt())
        val top5PctCount = max(1, (winningTradesSorted.size * 0.05).toInt())

        val top1PctProfit = winningTradesSorted.take(top1PctCount).sum()
        val top5PctProfit = winningTradesSorted.take(top5PctCount).sum()

        val top1PctContrib = if (totalGrossProfit > 0) (top1PctProfit / totalGrossProfit) * 100.0 else 0.0
        val top5PctContrib = if (totalGrossProfit > 0) (top5PctProfit / totalGrossProfit) * 100.0 else 0.0

        // Create 8 bins for visual histogram
        val minPnl = sortedPnL.first()
        val maxPnl = sortedPnL.last()
        val binCount = 8
        val binWidth = if (maxPnl > minPnl) (maxPnl - minPnl) / binCount else 1.0
        val bins = IntArray(binCount) { 0 }

        for (trade in trades) {
            val idx = if (binWidth > 0) {
                ((trade.pnlDollars - minPnl) / binWidth).toInt().coerceIn(0, binCount - 1)
            } else 0
            bins[idx]++
        }

        object {
            val p25Val = p25
            val medianVal = median
            val p75Val = p75
            val iqrVal = iqr
            val top1Contrib = top1PctContrib
            val top5Contrib = top5PctContrib
            val histogramBins = bins
            val maxBin = bins.maxOrNull() ?: 1
            val minPnlVal = minPnl
            val maxPnlVal = maxPnl
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("trade_distribution_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = theme.surface),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.borderSubtle))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        imageVector = Icons.Default.BarChart,
                        contentDescription = "Distribution",
                        tint = theme.brandPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "TRADE DISTRIBUTION & CONCENTRATION",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = theme.textPrimary,
                        letterSpacing = 0.5.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = theme.brandPrimary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "HISTORICAL BACKTEST RESULTS",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.brandPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Visual Histogram
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp),
                shape = RoundedCornerShape(10.dp),
                color = theme.surfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderSubtle.copy(alpha = 0.5f))
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    val w = size.width
                    val h = size.height
                    val barSpacing = 4.dp.toPx()
                    val totalBars = distributionStats.histogramBins.size
                    val barWidth = (w - (barSpacing * (totalBars - 1))) / totalBars

                    distributionStats.histogramBins.forEachIndexed { i, count ->
                        val barHeight = if (distributionStats.maxBin > 0) {
                            (count.toFloat() / distributionStats.maxBin.toFloat()) * (h * 0.85f)
                        } else 0f

                        val left = i * (barWidth + barSpacing)
                        val top = h - barHeight

                        // Estimate if bin is positive or negative
                        val binMidPnl = distributionStats.minPnlVal + (i + 0.5) * ((distributionStats.maxPnlVal - distributionStats.minPnlVal) / totalBars)
                        val barColor = if (binMidPnl >= 0) Color(0xFF10B981) else Color(0xFFEF4444)

                        drawRoundRect(
                            color = barColor.copy(alpha = 0.85f),
                            topLeft = Offset(left, top),
                            size = Size(barWidth, max(2f, barHeight)),
                            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                        )
                    }
                }
            }

            // Percentile Metrics 4-Col Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PercentileBox("P25 (Q1)", "$${df.format(distributionStats.p25Val)}", Modifier.weight(1f), theme)
                PercentileBox("Median (Q2)", "$${df.format(distributionStats.medianVal)}", Modifier.weight(1f), theme)
                PercentileBox("P75 (Q3)", "$${df.format(distributionStats.p75Val)}", Modifier.weight(1f), theme)
                PercentileBox("IQR", "$${df.format(distributionStats.iqrVal)}", Modifier.weight(1f), theme)
            }

            // Concentration Statistics (Outlier Dependency Guard)
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = theme.surfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderSubtle.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Top 1% Profit Contribution", fontSize = 10.sp, color = theme.textSecondary)
                        Text(
                            text = "${df.format(distributionStats.top1Contrib)}% of gross gains",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (distributionStats.top1Contrib > 35) Color(0xFFF59E0B) else theme.textPrimary
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Top 5% Profit Contribution", fontSize = 10.sp, color = theme.textSecondary)
                        Text(
                            text = "${df.format(distributionStats.top5Contrib)}% of gross gains",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (distributionStats.top5Contrib > 65) Color(0xFFF59E0B) else theme.textPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PercentileBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    theme: com.example.ui.theme.AppColors
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = theme.surfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderSubtle.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 9.sp, color = theme.textSecondary, fontWeight = FontWeight.Medium)
            Text(value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.textPrimary)
        }
    }
}
