package com.example.tradestrat.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.example.tradestrat.model.Trade
import com.example.ui.theme.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class PnLChartMode(val label: String) {
    CUMULATIVE_AREA("Cumulative P&L"),
    TRADE_BARS("Trade Returns")
}

@Composable
fun ProfitLossTrendChart(
    equityCurve: List<EquityPoint>,
    trades: List<Trade>,
    initialCapital: Double,
    modifier: Modifier = Modifier
) {
    if (equityCurve.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(240.dp)
                .background(BentoCardBg, RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("No P&L performance data available", color = BentoTextMuted)
        }
        return
    }

    var chartMode by remember { mutableStateOf(PnLChartMode.CUMULATIVE_AREA) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val textMeasurer = rememberTextMeasurer()

    val totalNetPnl = (equityCurve.lastOrNull()?.equity ?: initialCapital) - initialCapital
    val totalNetPnlPct = if (initialCapital > 0) (totalNetPnl / initialCapital) * 100.0 else 0.0
    val isOverallProfit = totalNetPnl >= 0

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("pnl_trend_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = BentoCardBg),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BentoBorder))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header: Title, Metric Badge, and Mode Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "PROFIT & LOSS PERFORMANCE TREND",
                        style = MaterialTheme.typography.labelSmall,
                        color = BentoTextMuted,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${if (isOverallProfit) "+$" else "-$"}${String.format("%,.2f", abs(totalNetPnl))}",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isOverallProfit) BentoGreen else BentoRed,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = CircleShape,
                            color = if (isOverallProfit) BentoGreen.copy(alpha = 0.15f) else BentoRed.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "${if (isOverallProfit) "+" else ""}${String.format("%.2f%%", totalNetPnlPct)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isOverallProfit) BentoGreen else BentoRed,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Mode Toggle Pills
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PnLChartMode.values().forEach { mode ->
                        val isSelected = chartMode == mode
                        Surface(
                            modifier = Modifier
                                .clickable {
                                    chartMode = mode
                                    selectedIndex = null
                                }
                                .testTag("pnl_mode_${mode.name}"),
                            shape = CircleShape,
                            color = if (isSelected) BentoLilacContainer else BentoCardElevated
                        ) {
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) BentoLilacText else BentoTextSecondary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Interactive Chart Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .pointerInput(chartMode) {
                        detectTapGestures { offset ->
                            selectedIndex = when (chartMode) {
                                PnLChartMode.CUMULATIVE_AREA -> {
                                    val step = size.width / (equityCurve.size - 1).coerceAtLeast(1)
                                    (offset.x / step).toInt().coerceIn(0, equityCurve.size - 1)
                                }
                                PnLChartMode.TRADE_BARS -> {
                                    if (trades.isNotEmpty()) {
                                        val barSlotWidth = size.width / trades.size
                                        (offset.x / barSlotWidth).toInt().coerceIn(0, trades.size - 1)
                                    } else null
                                }
                            }
                        }
                    }
                    .pointerInput(chartMode) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            selectedIndex = when (chartMode) {
                                PnLChartMode.CUMULATIVE_AREA -> {
                                    val step = size.width / (equityCurve.size - 1).coerceAtLeast(1)
                                    (change.position.x / step).toInt().coerceIn(0, equityCurve.size - 1)
                                }
                                PnLChartMode.TRADE_BARS -> {
                                    if (trades.isNotEmpty()) {
                                        val barSlotWidth = size.width / trades.size
                                        (change.position.x / barSlotWidth).toInt().coerceIn(0, trades.size - 1)
                                    } else null
                                }
                            }
                        }
                    }
            ) {
                when (chartMode) {
                    PnLChartMode.CUMULATIVE_AREA -> {
                        CumulativePnLAreaCanvas(
                            equityCurve = equityCurve,
                            initialCapital = initialCapital,
                            selectedIndex = selectedIndex,
                            textMeasurer = textMeasurer
                        )
                    }
                    PnLChartMode.TRADE_BARS -> {
                        TradeBarsCanvas(
                            trades = trades,
                            selectedIndex = selectedIndex,
                            textMeasurer = textMeasurer
                        )
                    }
                }
            }

            // Bottom Metric Summary Tiles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val winningTrades = trades.filter { it.isWin }
                val losingTrades = trades.filter { !it.isWin }
                val grossProfit = winningTrades.sumOf { it.pnlDollars }
                val grossLoss = abs(losingTrades.sumOf { it.pnlDollars })

                PnLQuickMetric("Gross Profit", "+$${String.format("%,.0f", grossProfit)}", BentoGreen)
                PnLQuickMetric("Gross Loss", "-$${String.format("%,.0f", grossLoss)}", BentoRed)
                val maxProfitTrade = trades.maxOfOrNull { it.pnlDollars } ?: 0.0
                PnLQuickMetric("Best Trade", "+$${String.format("%,.0f", max(0.0, maxProfitTrade))}", BentoLilac)
            }
        }
    }
}

@Composable
private fun PnLQuickMetric(label: String, value: String, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = BentoTextMuted, fontSize = 9.sp)
        Text(value, style = MaterialTheme.typography.bodySmall, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CumulativePnLAreaCanvas(
    equityCurve: List<EquityPoint>,
    initialCapital: Double,
    selectedIndex: Int?,
    textMeasurer: androidx.compose.ui.text.TextMeasurer
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val paddingBottom = 22f
        val chartH = h - paddingBottom

        val pnlPoints = equityCurve.map { it.equity - initialCapital }
        val maxPnl = max(100.0, pnlPoints.maxOrNull() ?: 0.0)
        val minPnl = min(-100.0, pnlPoints.minOrNull() ?: 0.0)
        val pnlRange = (maxPnl - minPnl).coerceAtLeast(1.0)

        fun getY(pnl: Double): Float {
            val norm = (pnl - minPnl) / pnlRange
            return (chartH - (norm * chartH).toFloat()).coerceIn(4f, chartH - 4f)
        }

        val zeroY = getY(0.0)

        // Draw Zero Baseline
        drawLine(
            color = BentoBorder,
            start = Offset(0f, zeroY),
            end = Offset(w, zeroY),
            strokeWidth = 1.5f,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
        )

        // Zero label
        drawText(
            textMeasurer = textMeasurer,
            text = "$0",
            topLeft = Offset(4f, (zeroY - 14f).coerceAtLeast(2f)),
            style = TextStyle(color = BentoTextMuted, fontSize = 9.sp)
        )

        // Draw Area Fill Path & Line Path
        val stepX = w / (pnlPoints.size - 1).coerceAtLeast(1)
        val linePath = Path()
        val areaPath = Path()

        pnlPoints.forEachIndexed { i, pnl ->
            val x = i * stepX
            val y = getY(pnl)
            if (i == 0) {
                linePath.moveTo(x, y)
                areaPath.moveTo(x, zeroY)
                areaPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                areaPath.lineTo(x, y)
            }
        }
        areaPath.lineTo((pnlPoints.size - 1) * stepX, zeroY)
        areaPath.close()

        val trendColor = if ((pnlPoints.lastOrNull() ?: 0.0) >= 0) BentoGreen else BentoRed

        // Gradient Area
        drawPath(
            path = areaPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    trendColor.copy(alpha = 0.25f),
                    trendColor.copy(alpha = 0.02f)
                ),
                startY = 0f,
                endY = chartH
            )
        )

        // Trend Line
        drawPath(
            path = linePath,
            color = trendColor,
            style = Stroke(width = 2.5f, cap = StrokeCap.Round)
        )

        // Interactive Tooltip Crosshair
        selectedIndex?.let { idx ->
            if (idx in pnlPoints.indices) {
                val cx = idx * stepX
                val cy = getY(pnlPoints[idx])
                val curPnl = pnlPoints[idx]

                // Vertical Crosshair
                drawLine(
                    color = BentoLilac.copy(alpha = 0.7f),
                    start = Offset(cx, 0f),
                    end = Offset(cx, chartH),
                    strokeWidth = 1.5f,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                )

                // Point Circle
                drawCircle(color = BentoLilac, radius = 5f, center = Offset(cx, cy))
                drawCircle(color = BentoBackground, radius = 2.5f, center = Offset(cx, cy))

                // Value Tooltip Text
                val tooltipText = "${if (curPnl >= 0) "+$" else "-$"}${String.format("%,.0f", abs(curPnl))}"
                val textLayout = textMeasurer.measure(
                    text = tooltipText,
                    style = TextStyle(color = BentoTextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                )

                val boxW = textLayout.size.width + 16f
                val boxH = textLayout.size.height + 8f
                val boxX = (cx - boxW / 2).coerceIn(4f, w - boxW - 4f)
                val boxY = (cy - boxH - 10f).coerceAtLeast(4f)

                drawRoundRect(
                    color = BentoLilacContainer,
                    topLeft = Offset(boxX, boxY),
                    size = Size(boxW, boxH),
                    cornerRadius = CornerRadius(8f, 8f)
                )
                drawText(
                    textLayoutResult = textLayout,
                    topLeft = Offset(boxX + 8f, boxY + 4f)
                )
            }
        }
    }
}

@Composable
private fun TradeBarsCanvas(
    trades: List<Trade>,
    selectedIndex: Int?,
    textMeasurer: androidx.compose.ui.text.TextMeasurer
) {
    if (trades.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No trade history to chart", color = BentoTextMuted, fontSize = 11.sp)
        }
        return
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val paddingBottom = 20f
        val chartH = h - paddingBottom

        val maxAbsPnl = trades.maxOfOrNull { abs(it.pnlDollars) }?.coerceAtLeast(50.0) ?: 100.0
        val zeroY = chartH / 2f

        // Draw Zero Center Line
        drawLine(
            color = BentoBorder,
            start = Offset(0f, zeroY),
            end = Offset(w, zeroY),
            strokeWidth = 1.5f
        )

        val barSlot = w / trades.size
        val barWidth = (barSlot * 0.7f).coerceIn(3f, 18f)

        trades.forEachIndexed { i, tr ->
            val cx = i * barSlot + barSlot / 2f
            val isWin = tr.isWin
            val barColor = if (isWin) BentoGreen else BentoRed

            val barHeight = ((abs(tr.pnlDollars) / maxAbsPnl) * (chartH / 2f)).toFloat().coerceAtLeast(3f)
            val top = if (isWin) zeroY - barHeight else zeroY

            drawRoundRect(
                color = barColor,
                topLeft = Offset(cx - barWidth / 2f, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(4f, 4f)
            )
        }

        // Highlight selected trade
        selectedIndex?.let { idx ->
            if (idx in trades.indices) {
                val tr = trades[idx]
                val cx = idx * barSlot + barSlot / 2f
                val isWin = tr.isWin
                val barColor = if (isWin) BentoGreen else BentoRed
                val barHeight = ((abs(tr.pnlDollars) / maxAbsPnl) * (chartH / 2f)).toFloat().coerceAtLeast(3f)
                val top = if (isWin) zeroY - barHeight else zeroY

                // Outer halo
                drawRoundRect(
                    color = BentoLilac.copy(alpha = 0.5f),
                    topLeft = Offset(cx - barWidth / 2f - 2f, top - 2f),
                    size = Size(barWidth + 4f, barHeight + 4f),
                    cornerRadius = CornerRadius(6f, 6f),
                    style = Stroke(width = 2f)
                )

                // Tooltip
                val txt = "#${idx + 1}: ${if (isWin) "+" else ""}${String.format("%.1f%%", tr.pnlPercent)} (${if (isWin) "+$" else "-$"}${String.format("%.0f", abs(tr.pnlDollars))})"
                val textLayout = textMeasurer.measure(
                    text = txt,
                    style = TextStyle(color = BentoTextPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                )
                val boxW = textLayout.size.width + 12f
                val boxH = textLayout.size.height + 6f
                val boxX = (cx - boxW / 2).coerceIn(4f, w - boxW - 4f)
                val boxY = (if (isWin) top - boxH - 6f else top + barHeight + 6f).coerceIn(4f, chartH - boxH - 2f)

                drawRoundRect(
                    color = BentoLilacContainer,
                    topLeft = Offset(boxX, boxY),
                    size = Size(boxW, boxH),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                drawText(
                    textLayoutResult = textLayout,
                    topLeft = Offset(boxX + 6f, boxY + 3f)
                )
            }
        }
    }
}
