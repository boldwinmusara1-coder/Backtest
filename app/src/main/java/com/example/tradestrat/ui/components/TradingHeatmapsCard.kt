package com.example.tradestrat.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tradestrat.model.Trade
import com.example.ui.theme.LocalAppTheme
import java.util.Calendar
import java.util.TimeZone

@Composable
fun TradingHeatmapsCard(
    trades: List<Trade>,
    modifier: Modifier = Modifier
) {
    val theme = LocalAppTheme.current

    // Compute 5 days (Mon-Fri) x 6 4-hour buckets matrix
    val matrix = remember(trades) {
        val grid = Array(5) { Array(6) { 0.0 } }
        val counts = Array(5) { Array(6) { 0 } }
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))

        for (trade in trades) {
            cal.timeInMillis = trade.entryTimestamp
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1=Sun, 2=Mon, ..., 6=Fri, 7=Sat
            val dayIdx = (dayOfWeek - 2).coerceIn(0, 4) // Mon=0 .. Fri=4
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val bucketIdx = (hour / 4).coerceIn(0, 5)

            grid[dayIdx][bucketIdx] += trade.pnlDollars
            counts[dayIdx][bucketIdx] += 1
        }
        Pair(grid, counts)
    }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        imageVector = Icons.Default.GridOn,
                        contentDescription = "Heatmap",
                        tint = theme.brandPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Temporal Edge Heatmap",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = theme.textPrimary
                    )
                }
            }

            Text(
                text = "Trading edge distribution by Day of Week and 4-Hour Time Bucket (UTC).",
                style = MaterialTheme.typography.bodySmall,
                color = theme.textSecondary,
                fontSize = 11.sp
            )

            // Canvas Heatmap Grid
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                shape = RoundedCornerShape(12.dp),
                color = theme.surfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderSubtle)
            ) {
                Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    val w = size.width
                    val h = size.height

                    val days = 5
                    val buckets = 6
                    val cellW = (w / buckets) - 3f
                    val cellH = (h / days) - 3f

                    val (grid, counts) = matrix

                    var maxAbs = 1.0
                    for (d in 0 until days) {
                        for (b in 0 until buckets) {
                            val v = kotlin.math.abs(grid[d][b])
                            if (v > maxAbs) maxAbs = v
                        }
                    }

                    for (d in 0 until days) {
                        for (b in 0 until buckets) {
                            val x = b * (cellW + 3f)
                            val y = d * (cellH + 3f)
                            val pnl = grid[d][b]
                            val count = counts[d][b]

                            val intensity = if (count == 0) 0f else (kotlin.math.abs(pnl) / maxAbs).toFloat().coerceIn(0.15f, 0.9f)
                            val cellColor = when {
                                count == 0 -> Color.Gray.copy(alpha = 0.12f)
                                pnl > 0 -> Color(0xFF10B981).copy(alpha = intensity)
                                else -> Color(0xFFEF4444).copy(alpha = intensity)
                            }

                            drawRoundRect(
                                color = cellColor,
                                topLeft = Offset(x, y),
                                size = Size(cellW, cellH),
                                cornerRadius = CornerRadius(4f, 4f)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Mon – Fri (UTC)", fontSize = 10.sp, color = theme.textMuted)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(shape = RoundedCornerShape(3.dp), color = Color(0xFFEF4444), modifier = Modifier.size(8.dp)) {}
                    Text("Loss", fontSize = 10.sp, color = theme.textMuted)
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(shape = RoundedCornerShape(3.dp), color = Color(0xFF10B981), modifier = Modifier.size(8.dp)) {}
                    Text("Profit", fontSize = 10.sp, color = theme.textMuted)
                }
                Text("00:00 – 24:00", fontSize = 10.sp, color = theme.textMuted)
            }
        }
    }
}
