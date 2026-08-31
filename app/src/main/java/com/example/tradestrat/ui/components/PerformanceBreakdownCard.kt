package com.example.tradestrat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tradestrat.model.BacktestMetrics
import com.example.tradestrat.model.SessionAnalytics
import com.example.tradestrat.model.Trade
import com.example.tradestrat.model.TradeDirection
import com.example.ui.theme.LocalAppTheme
import java.util.Locale

@Composable
fun PerformanceBreakdownCard(
    trades: List<Trade>,
    metrics: BacktestMetrics,
    sessionAnalytics: List<SessionAnalytics>,
    modifier: Modifier = Modifier
) {
    val theme = LocalAppTheme.current

    val longTrades = trades.filter { it.direction == TradeDirection.LONG }
    val shortTrades = trades.filter { it.direction == TradeDirection.SHORT }

    val longWins = longTrades.count { it.isWin }
    val shortWins = shortTrades.count { it.isWin }

    val longWinRate = if (longTrades.isNotEmpty()) (longWins.toDouble() / longTrades.size) * 100.0 else 0.0
    val shortWinRate = if (shortTrades.isNotEmpty()) (shortWins.toDouble() / shortTrades.size) * 100.0 else 0.0

    val longPnl = longTrades.sumOf { it.pnlDollars }
    val shortPnl = shortTrades.sumOf { it.pnlDollars }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = theme.surface),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.borderSubtle))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        imageVector = Icons.Default.PieChart,
                        contentDescription = "Breakdown",
                        tint = theme.brandPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Performance Breakdown",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = theme.textPrimary
                    )
                }
            }

            // Directional (Long vs Short) Breakdown
            Text(
                text = "LONG VS. SHORT DISTRIBUTION",
                style = MaterialTheme.typography.labelSmall,
                color = theme.textMuted,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DirectionalCard(
                    title = "Long Positions",
                    count = longTrades.size,
                    winRate = longWinRate,
                    pnl = longPnl,
                    color = theme.accentGreen,
                    modifier = Modifier.weight(1f),
                    theme = theme
                )

                DirectionalCard(
                    title = "Short Positions",
                    count = shortTrades.size,
                    winRate = shortWinRate,
                    pnl = shortPnl,
                    color = theme.accentRed,
                    modifier = Modifier.weight(1f),
                    theme = theme
                )
            }

            HorizontalDivider(color = theme.borderSubtle, thickness = 1.dp)

            // Trading Sessions Breakdown
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = "Sessions",
                    tint = theme.brandPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "GLOBAL SESSION PERFORMANCE",
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.textMuted,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sessionAnalytics.forEach { session ->
                    SessionRowItem(session = session, theme = theme)
                }
            }
        }
    }
}

@Composable
private fun DirectionalCard(
    title: String,
    count: Int,
    winRate: Double,
    pnl: Double,
    color: Color,
    modifier: Modifier = Modifier,
    theme: com.example.ui.theme.AppColors
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = theme.surfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderSubtle)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = "$count trades (${String.format(Locale.US, "%.1f%%", winRate)} Win)",
                fontSize = 11.sp,
                color = theme.textSecondary
            )
            Text(
                text = String.format(Locale.US, "%+.2f USD", pnl),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (pnl >= 0) theme.accentGreen else theme.accentRed
            )
        }
    }
}

@Composable
private fun SessionRowItem(
    session: SessionAnalytics,
    theme: com.example.ui.theme.AppColors
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = theme.surfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = session.sessionName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.textPrimary
                )
                Text(
                    text = "${session.tradeCount} trades • ${String.format(Locale.US, "%.1f%%", session.winRate)} Win • Avg ${String.format(Locale.US, "%+.2f R", session.avgR)}",
                    fontSize = 10.sp,
                    color = theme.textSecondary
                )
            }

            Text(
                text = String.format(Locale.US, "%+.2f", session.netPnl),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = if (session.netPnl >= 0) theme.accentGreen else theme.accentRed
            )
        }
    }
}
