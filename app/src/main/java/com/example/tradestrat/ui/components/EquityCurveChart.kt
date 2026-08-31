package com.example.tradestrat.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tradestrat.model.EquityPoint
import com.example.ui.theme.LocalAppTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

@Composable
fun EquityCurveChart(
    equityCurve: List<EquityPoint>,
    modifier: Modifier = Modifier,
    initialCapital: Double = 10000.0
) {
    val theme = LocalAppTheme.current

    if (equityCurve.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(theme.surfaceElevated, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("No equity curve data available", color = theme.textMuted)
        }
        return
    }

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val textMeasurer = rememberTextMeasurer()
    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }

    val activePoint = selectedIndex?.let {
        if (it in equityCurve.indices) equityCurve[it] else null
    } ?: equityCurve.lastOrNull()

    val beginningEquity = initialCapital
    val endingEquity = equityCurve.lastOrNull()?.equity ?: initialCapital
    val peakEquity = equityCurve.maxOfOrNull { it.equity } ?: initialCapital
    val minEquity = min(
        equityCurve.minOf { it.equity },
        equityCurve.minOf { it.benchmarkEquity }
    )
    val maxEquity = max(
        equityCurve.maxOf { it.equity },
        equityCurve.maxOf { it.benchmarkEquity }
    )
    val maxDrawdown = equityCurve.maxOfOrNull { it.drawdownPct } ?: 0.0

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("equity_curve_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = theme.surface),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.borderSubtle))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = "Equity",
                        tint = theme.brandPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Interactive Equity Curve",
                        style = MaterialTheme.typography.titleSmall,
                        color = theme.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(8.dp).background(theme.accentGreen, CircleShape))
                        Text("Strategy", style = TextStyle(fontSize = 10.sp, color = theme.textSecondary))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(8.dp).background(Color(0xFFF59E0B), CircleShape))
                        Text("Buy & Hold", style = TextStyle(fontSize = 10.sp, color = theme.textSecondary))
                    }
                }
            }

            // High-Level Stat Bar (Beginning, Ending, Peak, Max DD)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = theme.surfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderSubtle)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Beginning", fontSize = 9.sp, color = theme.textSecondary)
                        Text("$${String.format("%,.0f", beginningEquity)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.textPrimary)
                    }
                    Column {
                        Text("Ending", fontSize = 9.sp, color = theme.textSecondary)
                        Text(
                            text = "$${String.format("%,.0f", endingEquity)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (endingEquity >= beginningEquity) theme.accentGreen else theme.accentRed
                        )
                    }
                    Column {
                        Text("Peak Equity", fontSize = 9.sp, color = theme.textSecondary)
                        Text("$${String.format("%,.0f", peakEquity)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.textPrimary)
                    }
                    Column {
                        Text("Max Drawdown", fontSize = 9.sp, color = theme.textSecondary)
                        Text("-${String.format("%.2f%%", maxDrawdown)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.accentRed)
                    }
                }
            }

            // Scrubber tooltip panel (Interactive hover/tap details)
            activePoint?.let { pt ->
                val profitDollars = pt.equity - initialCapital
                val profitPct = if (initialCapital > 0) (profitDollars / initialCapital) * 100.0 else 0.0
                val pColor = if (profitDollars >= 0) theme.accentGreen else theme.accentRed
                val formattedDate = try {
                    dateFormatter.format(Date(pt.timestamp))
                } catch (e: Exception) {
                    "Bar #${pt.barIndex}"
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = theme.surfaceElevated.copy(alpha = 0.85f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, theme.brandPrimary.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Point Date", style = TextStyle(fontSize = 9.sp, color = theme.textSecondary))
                            Text(formattedDate, style = TextStyle(fontSize = 11.sp, color = theme.textPrimary, fontWeight = FontWeight.SemiBold))
                        }
                        Column {
                            Text("Strategy Equity", style = TextStyle(fontSize = 9.sp, color = theme.textSecondary))
                            Text(
                                text = "$${String.format("%,.2f", pt.equity)} (${String.format("%+.1f%%", profitPct)})",
                                style = TextStyle(fontSize = 11.sp, color = pColor, fontWeight = FontWeight.Bold)
                            )
                        }
                        Column {
                            Text("Drawdown", style = TextStyle(fontSize = 9.sp, color = theme.textSecondary))
                            Text(
                                text = "-${String.format("%.2f%%", pt.drawdownPct)}",
                                style = TextStyle(fontSize = 11.sp, color = if (pt.drawdownPct > 15) theme.accentRed else theme.textSecondary, fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }

            // Main Canvas for Equity + Underwater DD
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(theme.surfaceElevated, RoundedCornerShape(10.dp))
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val step = size.width / equityCurve.size
                                val idx = (offset.x / step).toInt().coerceIn(0, equityCurve.size - 1)
                                selectedIndex = idx
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                val step = size.width / equityCurve.size
                                val idx = (change.position.x / step).toInt().coerceIn(0, equityCurve.size - 1)
                                selectedIndex = idx
                            }
                        }
                ) {
                    val count = equityCurve.size
                    if (count < 2) return@Canvas

                    val chartWidth = size.width
                    val totalHeight = size.height
                    val equityHeight = totalHeight * 0.70f
                    val ddHeight = totalHeight * 0.24f
                    val ddTop = totalHeight * 0.76f

                    val span = max(1.0, maxEquity - minEquity)
                    val paddedMin = minEquity - (span * 0.05)
                    val paddedMax = maxEquity + (span * 0.05)
                    val effectiveSpan = paddedMax - paddedMin

                    fun eqToY(eq: Double): Float {
                        val norm = (paddedMax - eq) / effectiveSpan
                        return (norm * equityHeight).toFloat().coerceIn(0f, equityHeight)
                    }

                    fun ddToY(dd: Double): Float {
                        val maxDdEffective = max(10.0, maxDrawdown * 1.1)
                        val norm = (dd / maxDdEffective).toFloat().coerceIn(0f, 1f)
                        return ddTop + (norm * ddHeight)
                    }

                    // Grid Lines
                    val gridLines = 3
                    for (g in 0..gridLines) {
                        val y = equityHeight * (g.toFloat() / gridLines)
                        val valAtGrid = paddedMax - (effectiveSpan * (g.toDouble() / gridLines))
                        drawLine(
                            color = theme.borderSubtle,
                            start = Offset(0f, y),
                            end = Offset(chartWidth, y),
                            strokeWidth = 1f
                        )
                        drawText(
                            textMeasurer = textMeasurer,
                            text = "$${String.format("%,.0f", valAtGrid)}",
                            topLeft = Offset(chartWidth - 50.dp.toPx(), y - 10.sp.toPx()),
                            style = TextStyle(fontSize = 8.sp, color = theme.textMuted)
                        )
                    }

                    // Baseline (Initial capital)
                    val baselineY = eqToY(initialCapital)
                    drawLine(
                        color = theme.textMuted.copy(alpha = 0.35f),
                        start = Offset(0f, baselineY),
                        end = Offset(chartWidth, baselineY),
                        strokeWidth = 1.2f,
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                    )

                    val stepX = chartWidth / (count - 1).toFloat()

                    // Build Benchmark Path (Buy & Hold)
                    val benchPath = Path()
                    benchPath.moveTo(0f, eqToY(equityCurve[0].benchmarkEquity))
                    for (i in 1 until count) {
                        benchPath.lineTo(i * stepX, eqToY(equityCurve[i].benchmarkEquity))
                    }
                    drawPath(benchPath, color = Color(0xFFF59E0B).copy(alpha = 0.75f), style = Stroke(width = 1.6f, cap = StrokeCap.Round))

                    // Build Strategy Equity Path & Fill Area
                    val equityPath = Path()
                    val equityFillPath = Path()
                    equityPath.moveTo(0f, eqToY(equityCurve[0].equity))
                    equityFillPath.moveTo(0f, equityHeight)
                    equityFillPath.lineTo(0f, eqToY(equityCurve[0].equity))

                    for (i in 1 until count) {
                        val x = i * stepX
                        val y = eqToY(equityCurve[i].equity)
                        equityPath.lineTo(x, y)
                        equityFillPath.lineTo(x, y)
                    }
                    equityFillPath.lineTo(chartWidth, equityHeight)
                    equityFillPath.close()

                    // Draw gradient fill below strategy equity
                    drawPath(
                        equityFillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(theme.accentGreen.copy(alpha = 0.25f), Color.Transparent),
                            startY = 0f,
                            endY = equityHeight
                        )
                    )
                    drawPath(equityPath, color = theme.accentGreen, style = Stroke(width = 2.2f, cap = StrokeCap.Round))

                    // Draw Underwater Drawdown Sub-chart
                    drawLine(
                        color = theme.borderSubtle,
                        start = Offset(0f, ddTop),
                        end = Offset(chartWidth, ddTop),
                        strokeWidth = 1f
                    )
                    drawText(
                        textMeasurer = textMeasurer,
                        text = "Drawdown %",
                        topLeft = Offset(4.dp.toPx(), ddTop + 2.dp.toPx()),
                        style = TextStyle(fontSize = 8.sp, color = theme.textMuted)
                    )

                    val ddFillPath = Path()
                    ddFillPath.moveTo(0f, ddTop)
                    for (i in 0 until count) {
                        val x = i * stepX
                        val y = ddToY(equityCurve[i].drawdownPct)
                        ddFillPath.lineTo(x, y)
                    }
                    ddFillPath.lineTo(chartWidth, ddTop)
                    ddFillPath.close()

                    drawPath(
                        ddFillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(theme.accentRed.copy(alpha = 0.4f), theme.accentRed.copy(alpha = 0.1f)),
                            startY = ddTop,
                            endY = totalHeight
                        )
                    )

                    // Draw selected vertical scrubber
                    selectedIndex?.let { idx ->
                        val x = idx * stepX
                        drawLine(
                            color = theme.brandPrimary,
                            start = Offset(x, 0f),
                            end = Offset(x, totalHeight),
                            strokeWidth = 1.5f,
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                        )
                        drawCircle(
                            color = theme.accentGreen,
                            radius = 4.dp.toPx(),
                            center = Offset(x, eqToY(equityCurve[idx].equity))
                        )
                    }
                }
            }
        }
    }
}

