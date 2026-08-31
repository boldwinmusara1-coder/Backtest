package com.example.tradestrat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tradestrat.model.Trade
import com.example.ui.theme.LocalAppTheme
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MonthPerformance(
    val yearMonth: String, // e.g. "2023-08"
    val netPnlDollars: Double,
    val tradeCount: Int,
    val winCount: Int,
    val winRate: Double
)

@Composable
fun MonthlyPerformanceCard(
    trades: List<Trade>,
    initialCapital: Double = 10000.0,
    modifier: Modifier = Modifier
) {
    val theme = LocalAppTheme.current
    val df = remember { DecimalFormat("#,##0.00") }
    val monthFormat = remember { SimpleDateFormat("yyyy-MM", Locale.US) }

    val monthlyData = remember(trades) {
        val map = linkedMapOf<String, MutableList<Trade>>()
        for (t in trades) {
            val ym = try {
                monthFormat.format(Date(t.entryTimestamp))
            } catch (e: Exception) {
                "Unknown"
            }
            map.getOrPut(ym) { mutableListOf() }.add(t)
        }

        map.map { (ym, mTrades) ->
            val pnl = mTrades.sumOf { it.pnlDollars }
            val wins = mTrades.count { it.isWin }
            val wr = if (mTrades.isNotEmpty()) (wins.toDouble() / mTrades.size) * 100.0 else 0.0
            MonthPerformance(
                yearMonth = ym,
                netPnlDollars = pnl,
                tradeCount = mTrades.size,
                winCount = wins,
                winRate = wr
            )
        }.sortedByDescending { it.yearMonth }
    }

    if (monthlyData.isEmpty()) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("monthly_performance_card"),
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
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = "Monthly",
                        tint = theme.brandPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "MONTHLY PERFORMANCE BREAKDOWN",
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

            // Monthly cards horizontal row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                monthlyData.take(12).forEach { item ->
                    val isGreen = item.netPnlDollars >= 0
                    val pColor = if (isGreen) theme.accentGreen else theme.accentRed
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = theme.surfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isGreen) theme.accentGreen.copy(alpha = 0.4f) else theme.accentRed.copy(alpha = 0.4f)),
                        modifier = Modifier.width(110.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(item.yearMonth, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.textPrimary)
                            Text(
                                text = "${if (isGreen) "+" else ""}$${df.format(item.netPnlDollars)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = pColor
                            )
                            Text(
                                text = "${item.tradeCount} trades • ${df.format(item.winRate)}% WR",
                                fontSize = 9.sp,
                                color = theme.textSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
