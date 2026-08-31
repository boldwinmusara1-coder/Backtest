package com.example.tradestrat.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.example.tradestrat.model.BacktestMetrics
import com.example.ui.theme.*

@Composable
fun MetricsOverview(
    metrics: BacktestMetrics,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Bento Hero Card (col-span-2)
        BentoHeroProfitCard(metrics)

        // Bento 2x2 Modular Stat Tiles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BentoStatTile(
                title = "NET PROFIT",
                value = "${if (metrics.netProfitPercent >= 0) "+" else ""}${String.format("%.1f%%", metrics.netProfitPercent)}",
                subValue = "$${String.format("%,.0f", metrics.netProfitDollars)}",
                valueColor = if (metrics.netProfitPercent >= 0) BentoGreen else BentoRed,
                modifier = Modifier.weight(1f)
            )
            BentoStatTile(
                title = "WIN RATE",
                value = String.format("%.1f%%", metrics.winRatePercent),
                subValue = "${metrics.winningTrades}W / ${metrics.losingTrades}L",
                valueColor = BentoLilac,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BentoStatTile(
                title = "PROFIT FACTOR",
                value = String.format("%.2f", metrics.profitFactor),
                subValue = "Payoff: ${String.format("%.2f", metrics.payoffRatio)}x",
                valueColor = if (metrics.profitFactor >= 1.5) BentoGreen else if (metrics.profitFactor >= 1.0) BentoLilac else BentoRed,
                modifier = Modifier.weight(1f)
            )
            BentoStatTile(
                title = "MAX DRAWDOWN",
                value = String.format("-%.1f%%", metrics.maxDrawdownPercent),
                subValue = "Dur: ${metrics.maxDrawdownDurationBars} bars",
                valueColor = if (metrics.maxDrawdownPercent > 20) BentoRed else BentoTextSecondary,
                modifier = Modifier.weight(1f)
            )
        }

        // Expandable Detailed Metrics Bento Accordion
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .testTag("expand_metrics_button"),
            shape = RoundedCornerShape(20.dp),
            color = BentoCardBg,
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BentoBorder))
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.Analytics,
                        contentDescription = "Analytics",
                        tint = BentoLilac,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (expanded) "Hide Quantitative Metrics" else "Detailed Quantitative Metrics",
                        style = MaterialTheme.typography.labelMedium,
                        color = BentoLilac,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Expand",
                    tint = BentoLilac,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            BentoDetailedStatsGrid(metrics)
        }
    }
}

@Composable
private fun BentoHeroProfitCard(metrics: BacktestMetrics) {
    val isProfit = metrics.netProfitDollars >= 0
    val pColor = if (isProfit) BentoGreen else BentoRed

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hero_profit_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = BentoCardBg),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BentoBorder))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = "Strategy Performance",
                        style = MaterialTheme.typography.bodySmall,
                        color = BentoTextSecondary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${if (isProfit) "+" else ""}$${String.format("%,.2f", metrics.netProfitDollars)}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = BentoLilac,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Live Data Pill Badge
                Surface(
                    shape = CircleShape,
                    color = BentoLilacContainer
                ) {
                    Text(
                        text = "Live Data",
                        style = MaterialTheme.typography.labelSmall,
                        color = BentoLilacText,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Mini visual progress bar bento aesthetic
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                val heights = listOf(0.4f, 0.6f, 0.85f, 0.45f, 0.7f, 1.0f, 0.8f, 0.55f, 0.9f, 0.65f)
                heights.forEachIndexed { idx, frac ->
                    val barColor = if (idx == 5) BentoLilac else BentoLilac.copy(alpha = 0.25f + frac * 0.45f)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(frac)
                            .background(barColor, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Divider(color = BentoBorder, thickness = 1.dp)
            Spacer(modifier = Modifier.height(10.dp))

            // Capital Flow Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Initial Capital", style = MaterialTheme.typography.labelSmall, color = BentoTextMuted, fontSize = 10.sp)
                    Text("$${String.format("%,.0f", metrics.initialCapital)}", style = MaterialTheme.typography.bodySmall, color = BentoTextPrimary, fontWeight = FontWeight.Medium)
                }
                Column {
                    Text("Portfolio Value", style = MaterialTheme.typography.labelSmall, color = BentoTextMuted, fontSize = 10.sp)
                    Text("$${String.format("%,.2f", metrics.finalEquity)}", style = MaterialTheme.typography.bodySmall, color = BentoTextPrimary, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("Alpha vs Hold", style = MaterialTheme.typography.labelSmall, color = BentoTextMuted, fontSize = 10.sp)
                    val isAlphaPos = metrics.alphaPercent >= 0
                    Text(
                        text = "${if (isAlphaPos) "+" else ""}${String.format("%.1f%%", metrics.alphaPercent)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isAlphaPos) BentoGreen else BentoAmber,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun BentoStatTile(
    title: String,
    value: String,
    subValue: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = BentoCardBg),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BentoBorder))
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = BentoTextSecondary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                fontSize = 10.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = valueColor,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subValue,
                style = MaterialTheme.typography.labelSmall,
                color = BentoTextMuted,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun BentoDetailedStatsGrid(metrics: BacktestMetrics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = BentoCardBg),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BentoBorder))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "QUANTITATIVE EXECUTION METRICS",
                style = MaterialTheme.typography.labelSmall,
                color = BentoTextMuted,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            StatRow("CAGR (Annualized Return)", String.format("%+.2f%%", metrics.cagrPercent))
            StatRow("Sharpe Ratio", String.format("%.2f", metrics.sharpeRatio))
            StatRow("Sortino Ratio", String.format("%.2f", metrics.sortinoRatio))
            StatRow("Calmar Ratio (CAGR / Max DD)", String.format("%.2f", metrics.calmarRatio))
            StatRow("Trade Expectancy ($ / Trade)", String.format("%+$%.2f", metrics.expectancyDollars))
            StatRow("Expectancy R-Multiple", String.format("%+.2f R", metrics.expectancyR))
            StatRow("Average Trade PnL", String.format("%+.2f%%", metrics.avgTradePercent))
            StatRow("Avg Winning Trade", String.format("+%.2f%%", metrics.avgWinningTradePercent))
            StatRow("Avg Losing Trade", String.format("-%.2f%%", Math.abs(metrics.avgLosingTradePercent)))
            StatRow("Largest Winning Trade", String.format("+$%,.2f", metrics.largestWinningTradeDollars))
            StatRow("Largest Losing Trade", String.format("-$%,.2f", Math.abs(metrics.largestLosingTradeDollars)))
            StatRow("Max Consecutive Streak", "${metrics.maxConsecutiveWins} Wins / ${metrics.maxConsecutiveLosses} Losses")
            StatRow("Avg Holding Period", String.format("%.1f bars", metrics.avgHoldingBars))
            StatRow("Total Broker Fees & Slippage Paid", "$${String.format("%,.2f", metrics.totalFeesPaid)}")
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = BentoTextSecondary)
        Text(text = value, style = MaterialTheme.typography.bodySmall, color = BentoTextPrimary, fontWeight = FontWeight.SemiBold)
    }
}
