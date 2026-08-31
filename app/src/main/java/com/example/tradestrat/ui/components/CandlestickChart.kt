package com.example.tradestrat.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tradestrat.model.*
import com.example.ui.theme.*
import java.util.Locale
import kotlin.math.*

@Composable
fun CandlestickChart(
    candles: List<Candle>,
    indicators: CalculatedIndicators,
    signalMarkers: List<SignalMarker>,
    modifier: Modifier = Modifier,
    asset: MarketAsset? = null,
    timeframe: Timeframe? = null,
    showIndicators: Boolean = true,
    showSignals: Boolean = true,
    smcMetrics: SmcMetrics? = null,
    aPlusVisualData: APlusStrategyVisualData? = null
) {
    if (candles.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(BentoCardBg, RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("No candlestick data available", color = BentoTextMuted)
        }
        return
    }

    var visibleBars by remember { mutableIntStateOf(55) }
    var scrollOffset by remember { mutableIntStateOf(0) }
    var selectedBarIndex by remember { mutableStateOf<Int?>(null) }
    var showOverlayIndicators by remember { mutableStateOf(showIndicators) }
    var showOverlaySignals by remember { mutableStateOf(showSignals) }
    var showVolumeOverlay by remember { mutableStateOf(true) }

    val textMeasurer = rememberTextMeasurer()

    // Clamp visible bars & scroll
    val maxBars = candles.size
    val clampedVisibleBars = visibleBars.coerceIn(15, maxBars)
    val maxScroll = max(0, maxBars - clampedVisibleBars)
    val clampedScroll = scrollOffset.coerceIn(0, maxScroll)

    // Slice candles to visible viewport window (right-aligned for newest data)
    val startIdx = max(0, maxBars - clampedVisibleBars - clampedScroll)
    val endIdx = min(maxBars, startIdx + clampedVisibleBars)
    val visibleCandles = candles.subList(startIdx, endIdx)

    val visibleFastMa = if (indicators.fastMa.size == maxBars) indicators.fastMa.subList(startIdx, endIdx) else emptyList()
    val visibleSlowMa = if (indicators.slowMa.size == maxBars) indicators.slowMa.subList(startIdx, endIdx) else emptyList()
    val visibleBbUpper = if (indicators.bbUpper.size == maxBars) indicators.bbUpper.subList(startIdx, endIdx) else emptyList()
    val visibleBbLower = if (indicators.bbLower.size == maxBars) indicators.bbLower.subList(startIdx, endIdx) else emptyList()
    val visibleBbMid = if (indicators.bbMiddle.size == maxBars) indicators.bbMiddle.subList(startIdx, endIdx) else emptyList()

    val visibleSignals = signalMarkers.filter { it.barIndex in startIdx until endIdx }

    val activeSelectedCandle = selectedBarIndex?.let { selIdx ->
        if (selIdx in startIdx until endIdx && selIdx in candles.indices) candles[selIdx] else null
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("candlestick_chart_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = BentoCardBg),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BentoBorder))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Chart Header Controls & Meta
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(BentoLilacContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShowChart,
                            contentDescription = "Chart",
                            tint = BentoLilacText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    if (asset != null) {
                        Surface(
                            shape = CircleShape,
                            color = BentoLilacContainer
                        ) {
                            Text(
                                text = asset.category.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = BentoLilacText,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "${asset.symbol} • ${timeframe?.label ?: "D1"}",
                            style = MaterialTheme.typography.titleMedium,
                            color = BentoTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = "Interactive Price Chart",
                            style = MaterialTheme.typography.titleMedium,
                            color = BentoTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Interactive Zoom & Reset Controls
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (clampedScroll > 0) {
                        Surface(
                            modifier = Modifier
                                .clickable { scrollOffset = 0 }
                                .padding(end = 4.dp),
                            shape = RoundedCornerShape(6.dp),
                            color = BentoLilacContainer
                        ) {
                            Text(
                                text = "LATEST",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = BentoLilacText,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Zoom In Button
                    IconButton(
                        onClick = { visibleBars = (visibleBars - 12).coerceAtLeast(15) },
                        modifier = Modifier.size(32.dp).testTag("zoom_in_button")
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Zoom In", tint = BentoTextSecondary, modifier = Modifier.size(16.dp))
                    }
                    // Zoom Out Button
                    IconButton(
                        onClick = { visibleBars = (visibleBars + 12).coerceAtMost(maxBars) },
                        modifier = Modifier.size(32.dp).testTag("zoom_out_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Zoom Out", tint = BentoTextSecondary, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Quick Layer Filter Toggles
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    LegendPill(
                        label = "Fast MA",
                        color = FastMaLine,
                        isActive = showOverlayIndicators,
                        onClick = { showOverlayIndicators = !showOverlayIndicators }
                    )
                    LegendPill(
                        label = "Slow MA",
                        color = SlowMaLine,
                        isActive = showOverlayIndicators,
                        onClick = { showOverlayIndicators = !showOverlayIndicators }
                    )
                    LegendPill(
                        label = "Signals",
                        color = BentoLilac,
                        isActive = showOverlaySignals,
                        onClick = { showOverlaySignals = !showOverlaySignals }
                    )
                    LegendPill(
                        label = "Vol",
                        color = BentoBorder,
                        isActive = showVolumeOverlay,
                        onClick = { showVolumeOverlay = !showVolumeOverlay }
                    )
                }

                Text(
                    text = "${visibleCandles.size} / $maxBars bars",
                    style = MaterialTheme.typography.labelSmall,
                    color = BentoTextMuted,
                    fontSize = 10.sp
                )
            }

            // Selected Bar Tooltip Info Floating Bar
            AnimatedVisibility(
                visible = activeSelectedCandle != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                activeSelectedCandle?.let { sel ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = BentoCardElevated
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = sel.formattedDate(),
                                style = TextStyle(fontSize = 11.sp, color = BentoLilacText, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "O: ${String.format(Locale.US, "%.2f", sel.open)}  H: ${String.format(Locale.US, "%.2f", sel.high)}  L: ${String.format(Locale.US, "%.2f", sel.low)}  C: ${String.format(Locale.US, "%.2f", sel.close)}",
                                style = TextStyle(fontSize = 11.sp, color = BentoTextPrimary, fontWeight = FontWeight.Medium)
                            )
                            val changeColor = if (sel.isBullish) BentoGreen else BentoRed
                            Text(
                                text = String.format(Locale.US, "%+.2f%%", sel.changePct),
                                style = TextStyle(fontSize = 11.sp, color = changeColor, fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }

            // High Performance Canvas Chart
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(290.dp)
                    .background(BentoBackground, RoundedCornerShape(16.dp))
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                if (visibleCandles.isNotEmpty()) {
                                    val barWidth = size.width / visibleCandles.size
                                    val touchedRelativeIdx = (offset.x / barWidth).toInt().coerceIn(0, visibleCandles.size - 1)
                                    selectedBarIndex = startIdx + touchedRelativeIdx
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                if (visibleCandles.isNotEmpty()) {
                                    val barWidth = size.width / visibleCandles.size
                                    val barsDragged = (dragAmount.x / barWidth).toInt()
                                    if (barsDragged != 0) {
                                        scrollOffset = (scrollOffset + barsDragged).coerceIn(0, maxScroll)
                                    }
                                    val touchedRelativeIdx = (change.position.x / barWidth).toInt().coerceIn(0, visibleCandles.size - 1)
                                    selectedBarIndex = startIdx + touchedRelativeIdx
                                }
                            }
                        }
                ) {
                    if (visibleCandles.isEmpty()) return@Canvas

                    val chartWidth = size.width
                    val totalHeight = size.height
                    val priceAreaHeight = if (showVolumeOverlay) totalHeight * 0.78f else totalHeight * 0.95f
                    val volumeAreaHeight = totalHeight * 0.18f
                    val volumeAreaTop = totalHeight * 0.82f

                    // Calculate dynamic price range
                    var minPrice = visibleCandles.minOf { it.low }
                    var maxPrice = visibleCandles.maxOf { it.high }

                    if (showOverlayIndicators) {
                        visibleFastMa.filterNotNull().forEach {
                            minPrice = min(minPrice, it)
                            maxPrice = max(maxPrice, it)
                        }
                        visibleSlowMa.filterNotNull().forEach {
                            minPrice = min(minPrice, it)
                            maxPrice = max(maxPrice, it)
                        }
                        visibleBbLower.filterNotNull().forEach { minPrice = min(minPrice, it) }
                        visibleBbUpper.filterNotNull().forEach { maxPrice = max(maxPrice, it) }
                    }

                    // Price range padding (5%)
                    val priceSpan = max(0.0001, maxPrice - minPrice)
                    val paddedMinPrice = minPrice - priceSpan * 0.05
                    val paddedMaxPrice = maxPrice + priceSpan * 0.05
                    val effectivePriceSpan = paddedMaxPrice - paddedMinPrice

                    val maxVolume = max(1.0, visibleCandles.maxOf { it.volume })

                    // Horizontal Grid Lines & Price Labels
                    val gridLinesCount = 4
                    for (g in 0..gridLinesCount) {
                        val y = priceAreaHeight * (g.toFloat() / gridLinesCount)
                        val priceAtGrid = paddedMaxPrice - (effectivePriceSpan * (g.toDouble() / gridLinesCount))

                        drawLine(
                            color = BentoBorder.copy(alpha = 0.4f),
                            start = Offset(0f, y),
                            end = Offset(chartWidth, y),
                            strokeWidth = 1f
                        )

                        drawText(
                            textMeasurer = textMeasurer,
                            text = String.format(Locale.US, "%.2f", priceAtGrid),
                            topLeft = Offset(chartWidth - 52.dp.toPx(), y - 10.sp.toPx()),
                            style = TextStyle(fontSize = 9.sp, color = BentoTextMuted)
                        )
                    }

                    val barCount = visibleCandles.size
                    val candleSpacing = chartWidth / barCount
                    val candleBodyWidth = max(2f, candleSpacing * 0.68f)

                    fun priceToY(price: Double): Float {
                        val normalized = (paddedMaxPrice - price) / effectivePriceSpan
                        return (normalized * priceAreaHeight).toFloat().coerceIn(0f, priceAreaHeight)
                    }

                    // Bollinger Bands Cloud & Lines
                    if (showOverlayIndicators && visibleBbUpper.size == barCount && visibleBbLower.size == barCount) {
                        val upperPath = Path()
                        val lowerPath = Path()
                        var upperStarted = false
                        var lowerStarted = false

                        for (i in 0 until barCount) {
                            val up = visibleBbUpper[i]
                            val low = visibleBbLower[i]
                            val x = (i * candleSpacing) + (candleSpacing / 2f)

                            if (up != null) {
                                val yUp = priceToY(up)
                                if (!upperStarted) {
                                    upperPath.moveTo(x, yUp)
                                    upperStarted = true
                                } else {
                                    upperPath.lineTo(x, yUp)
                                }
                            }

                            if (low != null) {
                                val yLow = priceToY(low)
                                if (!lowerStarted) {
                                    lowerPath.moveTo(x, yLow)
                                    lowerStarted = true
                                } else {
                                    lowerPath.lineTo(x, yLow)
                                }
                            }
                        }

                        if (upperStarted) {
                            drawPath(upperPath, BollingerUpperLine.copy(alpha = 0.6f), style = Stroke(width = 1.2f))
                        }
                        if (lowerStarted) {
                            drawPath(lowerPath, BollingerLowerLine.copy(alpha = 0.6f), style = Stroke(width = 1.2f))
                        }
                    }

                    // Draw Fast MA Line
                    if (showOverlayIndicators && visibleFastMa.any { it != null }) {
                        val fastPath = Path()
                        var started = false
                        for (i in 0 until barCount) {
                            val v = visibleFastMa[i] ?: continue
                            val x = (i * candleSpacing) + (candleSpacing / 2f)
                            val y = priceToY(v)
                            if (!started) {
                                fastPath.moveTo(x, y)
                                started = true
                            } else {
                                fastPath.lineTo(x, y)
                            }
                        }
                        if (started) {
                            drawPath(fastPath, FastMaLine, style = Stroke(width = 2.0f, cap = StrokeCap.Round))
                        }
                    }

                    // Draw Slow MA Line
                    if (showOverlayIndicators && visibleSlowMa.any { it != null }) {
                        val slowPath = Path()
                        var started = false
                        for (i in 0 until barCount) {
                            val v = visibleSlowMa[i] ?: continue
                            val x = (i * candleSpacing) + (candleSpacing / 2f)
                            val y = priceToY(v)
                            if (!started) {
                                slowPath.moveTo(x, y)
                                started = true
                            } else {
                                slowPath.lineTo(x, y)
                            }
                        }
                        if (started) {
                            drawPath(slowPath, SlowMaLine, style = Stroke(width = 2.0f, cap = StrokeCap.Round))
                        }
                    }

                    // Draw Candlesticks & Volume Bars
                    for (i in 0 until barCount) {
                        val c = visibleCandles[i]
                        val centerX = (i * candleSpacing) + (candleSpacing / 2f)
                        val openY = priceToY(c.open)
                        val closeY = priceToY(c.close)
                        val highY = priceToY(c.high)
                        val lowY = priceToY(c.low)

                        val isBull = c.isBullish
                        val candleColor = if (isBull) BentoGreen else BentoRed

                        // Draw Wick
                        drawLine(
                            color = candleColor,
                            start = Offset(centerX, highY),
                            end = Offset(centerX, lowY),
                            strokeWidth = max(1.2f, candleBodyWidth * 0.15f)
                        )

                        // Draw Body
                        val bodyTop = min(openY, closeY)
                        val bodyBottom = max(openY, closeY)
                        val bodyHeight = max(2f, bodyBottom - bodyTop)

                        drawRoundRect(
                            color = candleColor,
                            topLeft = Offset(centerX - (candleBodyWidth / 2f), bodyTop),
                            size = Size(candleBodyWidth, bodyHeight),
                            cornerRadius = CornerRadius(2f, 2f)
                        )

                        // Draw Volume Histogram Bar
                        if (showVolumeOverlay) {
                            val volHeight = ((c.volume / maxVolume) * volumeAreaHeight).toFloat()
                            val volTop = totalHeight - volHeight
                            val volColor = if (isBull) BentoGreen.copy(alpha = 0.30f) else BentoRed.copy(alpha = 0.30f)

                            drawRect(
                                color = volColor,
                                topLeft = Offset(centerX - (candleBodyWidth / 2f), volTop),
                                size = Size(candleBodyWidth, volHeight)
                            )
                        }
                    }

                    // Draw A+ Visual Trendlines & Swings (Visual Debug Mode)
                    aPlusVisualData?.let { vData ->
                        // 1. Draw Active / Historical Trendlines
                        for (tl in vData.trendlines) {
                            val lineColor = if (tl.type == TrendlineType.ASCENDING_SUPPORT) TvCyan else TvAmber
                            val startBar = tl.firstSwing.barIndex
                            val endBar = min(maxBars - 1, tl.secondSwing.barIndex + 60)

                            if (endBar >= startIdx && startBar <= endIdx) {
                                val x1 = ((startBar - startIdx) * candleSpacing) + (candleSpacing / 2f)
                                val y1 = priceToY(tl.priceAt(startBar))
                                val x2 = ((endBar - startIdx) * candleSpacing) + (candleSpacing / 2f)
                                val y2 = priceToY(tl.priceAt(endBar))

                                drawLine(
                                    color = lineColor.copy(alpha = 0.85f),
                                    start = Offset(x1, y1),
                                    end = Offset(x2, y2),
                                    strokeWidth = 2.2f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f))
                                )

                                // Draw touch circles
                                for (touchBar in tl.touches) {
                                    if (touchBar in startIdx until endIdx) {
                                        val tx = ((touchBar - startIdx) * candleSpacing) + (candleSpacing / 2f)
                                        val ty = priceToY(tl.priceAt(touchBar))
                                        drawCircle(
                                            color = lineColor,
                                            radius = 3.5.dp.toPx(),
                                            center = Offset(tx, ty),
                                            style = Stroke(width = 1.8f)
                                        )
                                    }
                                }
                            }
                        }

                        // 2. Draw Confirmed Swing Points (HH / HL / LH / LL)
                        for (sw in vData.swingPoints) {
                            if (sw.barIndex in startIdx until endIdx) {
                                val sx = ((sw.barIndex - startIdx) * candleSpacing) + (candleSpacing / 2f)
                                val sy = priceToY(sw.price)
                                val swColor = if (sw.isHigh) TvAmber else TvCyan

                                if (sw.isHigh) {
                                    // Swing High marker above bar
                                    val path = Path().apply {
                                        moveTo(sx, sy - 8.dp.toPx())
                                        lineTo(sx - 4.dp.toPx(), sy - 14.dp.toPx())
                                        lineTo(sx + 4.dp.toPx(), sy - 14.dp.toPx())
                                        close()
                                    }
                                    drawPath(path, swColor)
                                } else {
                                    // Swing Low marker below bar
                                    val path = Path().apply {
                                        moveTo(sx, sy + 8.dp.toPx())
                                        lineTo(sx - 4.dp.toPx(), sy + 14.dp.toPx())
                                        lineTo(sx + 4.dp.toPx(), sy + 14.dp.toPx())
                                        close()
                                    }
                                    drawPath(path, swColor)
                                }
                            }
                        }
                    }

                    // Draw Execution Signal Markers
                    if (showOverlaySignals) {
                        for (sig in visibleSignals) {
                            val relIdx = sig.barIndex - startIdx
                            if (relIdx !in 0 until barCount) continue
                            val sigCandle = visibleCandles[relIdx]
                            val centerX = (relIdx * candleSpacing) + (candleSpacing / 2f)

                            if (sig.isEntry) {
                                if (sig.direction == TradeDirection.LONG) {
                                    val y = priceToY(sigCandle.low) + 14.dp.toPx()
                                    drawSignalArrow(centerX, y, isUp = true, color = BentoGreen)
                                } else {
                                    val y = priceToY(sigCandle.high) - 14.dp.toPx()
                                    drawSignalArrow(centerX, y, isUp = false, color = BentoRed)
                                }
                            } else {
                                val y = if (sig.direction == TradeDirection.LONG) {
                                    priceToY(sigCandle.high) - 12.dp.toPx()
                                } else {
                                    priceToY(sigCandle.low) + 12.dp.toPx()
                                }
                                drawCircle(
                                    color = if (sig.exitReason == ExitReason.TAKE_PROFIT) BentoGreen else BentoRed,
                                    radius = 4.dp.toPx(),
                                    center = Offset(centerX, y)
                                )
                            }
                        }
                    }

                    // Interactive Crosshair on touch
                    selectedBarIndex?.let { sel ->
                        val relIdx = sel - startIdx
                        if (relIdx in 0 until barCount) {
                            val centerX = (relIdx * candleSpacing) + (candleSpacing / 2f)
                            val candle = visibleCandles[relIdx]
                            val closeY = priceToY(candle.close)

                            // Vertical crosshair line
                            drawLine(
                                color = BentoLilac.copy(alpha = 0.7f),
                                start = Offset(centerX, 0f),
                                end = Offset(centerX, totalHeight),
                                strokeWidth = 1.2f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                            )

                            // Horizontal price line
                            drawLine(
                                color = BentoLilac.copy(alpha = 0.5f),
                                start = Offset(0f, closeY),
                                end = Offset(chartWidth, closeY),
                                strokeWidth = 1f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                            )

                            // Highlight dot at close
                            drawCircle(
                                color = BentoLilac,
                                radius = 4.dp.toPx(),
                                center = Offset(centerX, closeY)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawSignalArrow(x: Float, y: Float, isUp: Boolean, color: Color) {
    val size = 6.dp.toPx()
    val path = Path()
    if (isUp) {
        // Upward arrow for BUY
        path.moveTo(x, y - size)
        path.lineTo(x - size, y + size)
        path.lineTo(x + size, y + size)
        path.close()
    } else {
        // Downward arrow for SELL
        path.moveTo(x, y + size)
        path.lineTo(x - size, y - size)
        path.lineTo(x + size, y - size)
        path.close()
    }
    drawPath(path, color)
}

@Composable
private fun LegendPill(label: String, color: Color, isActive: Boolean = true, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .clickable { onClick() }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(if (isActive) color else BentoBorder, CircleShape)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = TextStyle(
                fontSize = 10.sp,
                color = if (isActive) BentoTextSecondary else BentoTextMuted,
                fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal
            )
        )
    }
}

@Composable
fun CandlestickChart(
    candles: List<Candle>,
    timeFrameMinutes: Int,
    modifier: Modifier = Modifier
) {
    CandlestickChart(
        candles = candles,
        indicators = CalculatedIndicators(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList()),
        signalMarkers = emptyList(),
        modifier = modifier
    )
}

