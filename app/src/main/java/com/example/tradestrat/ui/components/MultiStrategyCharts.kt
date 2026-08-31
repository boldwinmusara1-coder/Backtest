package com.example.tradestrat.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tradestrat.model.StrategyComparisonItem
import com.example.ui.theme.*
import java.text.DecimalFormat
import kotlin.math.max
import kotlin.math.min

val STRATEGY_PALETTE = listOf(
    Color(0xFF38BDF8), // Cyan / Sky Blue
    Color(0xFF34D399), // Mint / Emerald
    Color(0xFFF472B6), // Pink / Rose
    Color(0xFFFBBF24), // Amber / Gold
    Color(0xFFA78BFA), // Purple / Indigo
    Color(0xFFFB923C), // Orange
    Color(0xFF60A5FA), // Blue
    Color(0xFFE879F9), // Fuchsia
    Color(0xFF2DD4BF), // Teal
    Color(0xFFA3E635)  // Lime
)

/**
 * Normalized Multi-Strategy Equity Curve Chart ($10,000 baseline).
 */
@Composable
fun MultiStrategyEquityChart(
    items: List<StrategyComparisonItem>,
    modifier: Modifier = Modifier
) {
    val theme = LocalAppTheme.current
    val textMeasurer = rememberTextMeasurer()
    val df = remember { DecimalFormat("#,##0") }

    if (items.isEmpty() || items.all { it.normalizedEquityCurve.isEmpty() }) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(240.dp)
                .background(theme.surfaceElevated, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("No comparison equity data available", color = theme.textSecondary, fontSize = 13.sp)
        }
        return
    }

    var minEquity = Double.MAX_VALUE
    var maxEquity = Double.MIN_VALUE
    var maxBars = 0

    items.forEach { item ->
        val curve = item.normalizedEquityCurve
        if (curve.isNotEmpty()) {
            minEquity = min(minEquity, curve.minOf { it.normalizedEquity })
            maxEquity = max(maxEquity, curve.maxOf { it.normalizedEquity })
            maxBars = max(maxBars, curve.size)
        }
    }

    if (minEquity == Double.MAX_VALUE) minEquity = 9000.0
    if (maxEquity == Double.MIN_VALUE) maxEquity = 11000.0
    if (minEquity >= maxEquity) {
        minEquity -= 500.0
        maxEquity += 500.0
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("multi_strategy_equity_chart"),
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
                        imageVector = Icons.Default.ShowChart,
                        contentDescription = "Equity Curves",
                        tint = theme.brandPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Normalized Equity Growth ($10k Base)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = theme.textPrimary
                    )
                }

                Text(
                    text = "${items.size} Strategies",
                    fontSize = 11.sp,
                    color = theme.brandPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Chart Canvas
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .testTag("canvas_multi_equity")
            ) {
                val w = size.width
                val h = size.height
                val padLeft = 45f
                val padRight = 10f
                val padTop = 15f
                val padBottom = 25f
                val chartW = w - padLeft - padRight
                val chartH = h - padTop - padBottom

                // Grid Lines
                val steps = 4
                for (s in 0..steps) {
                    val y = padTop + chartH * (s.toFloat() / steps)
                    val value = maxEquity - (s.toDouble() / steps) * (maxEquity - minEquity)

                    drawLine(
                        color = theme.borderSubtle.copy(alpha = 0.5f),
                        start = Offset(padLeft, y),
                        end = Offset(w - padRight, y),
                        strokeWidth = 1f
                    )

                    drawText(
                        textMeasurer = textMeasurer,
                        text = "$${df.format(value)}",
                        topLeft = Offset(2f, y - 8f),
                        style = TextStyle(color = theme.textSecondary.copy(alpha = 0.8f), fontSize = 9.sp)
                    )
                }

                // Baseline 10,000 Dash
                val baseNormY = padTop + chartH * (1.0f - ((10000.0 - minEquity) / (maxEquity - minEquity)).toFloat().coerceIn(0f, 1f))
                if (baseNormY in padTop..(padTop + chartH)) {
                    drawLine(
                        color = theme.textSecondary.copy(alpha = 0.4f),
                        start = Offset(padLeft, baseNormY),
                        end = Offset(w - padRight, baseNormY),
                        strokeWidth = 1.5f,
                        cap = StrokeCap.Round
                    )
                }

                // Draw each strategy curve
                items.forEachIndexed { sIdx, item ->
                    val color = STRATEGY_PALETTE[sIdx % STRATEGY_PALETTE.size]
                    val curve = item.normalizedEquityCurve
                    if (curve.size >= 2) {
                        val path = Path()
                        val stepX = chartW / (curve.size - 1).toFloat()

                        curve.forEachIndexed { i, pt ->
                            val x = padLeft + i * stepX
                            val normVal = ((pt.normalizedEquity - minEquity) / (maxEquity - minEquity)).toFloat().coerceIn(0f, 1f)
                            val y = padTop + chartH * (1.0f - normVal)

                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }

                        drawPath(
                            path = path,
                            color = color,
                            style = Stroke(width = 2.5f, cap = StrokeCap.Round)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Strategy Legend (Scrollable)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { sIdx, item ->
                    val color = STRATEGY_PALETTE[sIdx % STRATEGY_PALETTE.size]
                    val finalEq = item.normalizedEquityCurve.lastOrNull()?.normalizedEquity ?: 10000.0
                    val retPct = ((finalEq - 10000.0) / 10000.0) * 100.0

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.testTag("legend_strategy_${item.strategy.id}")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(color, CircleShape)
                        )
                        Text(
                            text = item.strategy.name.take(24),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = theme.textPrimary
                        )
                        Text(
                            text = "${if (retPct >= 0) "+" else ""}${String.format("%.1f", retPct)}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (retPct >= 0) theme.accentGreen else theme.accentRed
                        )
                    }
                }
            }
        }
    }
}

/**
 * Multi-Strategy Drawdown Curve Chart.
 */
@Composable
fun MultiStrategyDrawdownChart(
    items: List<StrategyComparisonItem>,
    modifier: Modifier = Modifier
) {
    val theme = LocalAppTheme.current
    val textMeasurer = rememberTextMeasurer()

    if (items.isEmpty() || items.all { it.drawdownCurve.isEmpty() }) {
        return
    }

    var maxDD = 0.0
    items.forEach { item ->
        val curve = item.drawdownCurve
        if (curve.isNotEmpty()) {
            maxDD = max(maxDD, curve.maxOf { it.drawdownPct })
        }
    }
    if (maxDD < 5.0) maxDD = 5.0

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("multi_strategy_drawdown_chart"),
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
                        imageVector = Icons.Default.TrendingDown,
                        contentDescription = "Drawdown Curves",
                        tint = theme.accentRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Historical Drawdown Comparison",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = theme.textPrimary
                    )
                }

                Text(
                    text = "Peak-to-Trough %",
                    fontSize = 11.sp,
                    color = theme.textSecondary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .testTag("canvas_multi_drawdown")
            ) {
                val w = size.width
                val h = size.height
                val padLeft = 40f
                val padRight = 10f
                val padTop = 15f
                val padBottom = 20f
                val chartW = w - padLeft - padRight
                val chartH = h - padTop - padBottom

                // Grid Lines
                val steps = 3
                for (s in 0..steps) {
                    val y = padTop + chartH * (s.toFloat() / steps)
                    val value = (s.toDouble() / steps) * maxDD

                    drawLine(
                        color = theme.borderSubtle.copy(alpha = 0.5f),
                        start = Offset(padLeft, y),
                        end = Offset(w - padRight, y),
                        strokeWidth = 1f
                    )

                    drawText(
                        textMeasurer = textMeasurer,
                        text = "-${String.format("%.1f", value)}%",
                        topLeft = Offset(2f, y - 7f),
                        style = TextStyle(color = theme.textSecondary.copy(alpha = 0.8f), fontSize = 9.sp)
                    )
                }

                // Draw curves
                items.forEachIndexed { sIdx, item ->
                    val color = STRATEGY_PALETTE[sIdx % STRATEGY_PALETTE.size]
                    val curve = item.drawdownCurve
                    if (curve.size >= 2) {
                        val path = Path()
                        val stepX = chartW / (curve.size - 1).toFloat()

                        curve.forEachIndexed { i, pt ->
                            val x = padLeft + i * stepX
                            val ddFraction = (pt.drawdownPct / maxDD).toFloat().coerceIn(0f, 1f)
                            val y = padTop + chartH * ddFraction

                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }

                        drawPath(
                            path = path,
                            color = color,
                            style = Stroke(width = 2f, cap = StrokeCap.Round)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Legend with Max DD stats
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items.forEachIndexed { sIdx, item ->
                    val color = STRATEGY_PALETTE[sIdx % STRATEGY_PALETTE.size]
                    val dd = item.result.metrics.maxDrawdownPercent

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
                        Text(
                            text = "${item.strategy.name.take(18)}: -${String.format("%.2f", dd)}%",
                            fontSize = 10.sp,
                            color = theme.textSecondary
                        )
                    }
                }
            }
        }
    }
}
