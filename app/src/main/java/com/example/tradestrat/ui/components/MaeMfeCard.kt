package com.example.tradestrat.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tradestrat.model.MaeMfeDistribution
import com.example.ui.theme.LocalAppTheme
import java.util.Locale

@Composable
fun MaeMfeCard(
    distribution: MaeMfeDistribution,
    modifier: Modifier = Modifier
) {
    val theme = LocalAppTheme.current

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
                        imageVector = Icons.Default.ShowChart,
                        contentDescription = "MAE/MFE",
                        tint = theme.brandPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Excursion Analytics (MAE & MFE)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = theme.textPrimary
                    )
                }
            }

            Text(
                text = "Evaluate if stop losses are placed too tight (MAE) or if profits are left on the table before exits (MFE).",
                style = MaterialTheme.typography.bodySmall,
                color = theme.textSecondary,
                fontSize = 11.sp
            )

            // Metrics Summary Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExcursionStatBox(
                    title = "Avg Favorable (MFE)",
                    value = String.format(Locale.US, "+%.2f%%", distribution.avgMfePct),
                    color = theme.accentGreen,
                    modifier = Modifier.weight(1f),
                    theme = theme
                )

                ExcursionStatBox(
                    title = "Avg Adverse (MAE)",
                    value = String.format(Locale.US, "-%.2f%%", distribution.avgMaePct),
                    color = theme.accentRed,
                    modifier = Modifier.weight(1f),
                    theme = theme
                )

                ExcursionStatBox(
                    title = "Max Run-up",
                    value = String.format(Locale.US, "+%.2f%%", distribution.maxMfePct),
                    color = theme.accentGreen,
                    modifier = Modifier.weight(1f),
                    theme = theme
                )
            }

            // Scatter distribution Canvas
            Text(
                text = "MFE RUN-UP VS. REALIZED R-MULTIPLE",
                style = MaterialTheme.typography.labelSmall,
                color = theme.textMuted,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                shape = RoundedCornerShape(12.dp),
                color = theme.surfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderSubtle)
            ) {
                Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    val w = size.width
                    val h = size.height

                    // Draw center baseline
                    val midY = h * 0.6f
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.3f),
                        start = Offset(0f, midY),
                        end = Offset(w, midY),
                        strokeWidth = 1.5f
                    )

                    val maxMfe = distribution.maxMfePct.coerceAtLeast(1.0)
                    val points = distribution.points

                    points.forEach { pt ->
                        val x = ((pt.mfePct / maxMfe).toFloat() * (w - 20f)).coerceIn(10f, w - 10f)
                        val rClamped = pt.rMultiple.toFloat().coerceIn(-2f, 4f)
                        val y = (midY - (rClamped / 4f) * (midY - 10f)).coerceIn(10f, h - 10f)

                        val dotColor = if (pt.isWin) Color(0xFF10B981) else Color(0xFFEF4444)
                        drawCircle(
                            color = dotColor.copy(alpha = 0.8f),
                            radius = 4.5f,
                            center = Offset(x, y)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExcursionStatBox(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
    theme: com.example.ui.theme.AppColors
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = theme.surfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderSubtle)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = title, fontSize = 9.sp, color = theme.textMuted, fontWeight = FontWeight.SemiBold)
            Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}
