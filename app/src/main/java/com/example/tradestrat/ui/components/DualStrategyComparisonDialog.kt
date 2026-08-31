package com.example.tradestrat.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.tradestrat.model.DualStrategyComparisonData
import com.example.tradestrat.model.EquityPoint
import com.example.ui.theme.LocalAppTheme
import kotlin.math.max
import kotlin.math.min

@Composable
fun DualStrategyComparisonDialog(
    comparisonData: DualStrategyComparisonData?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onRerun: () -> Unit
) {
    val theme = LocalAppTheme.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.94f)
                .testTag("dual_strategy_comparison_dialog"),
            shape = RoundedCornerShape(20.dp),
            color = theme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderSubtle)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                imageVector = Icons.Default.CompareArrows,
                                contentDescription = null,
                                tint = theme.brandPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Performance Comparison",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = theme.textPrimary
                            )
                        }
                        Text(
                            text = "High-Win-Rate vs A+ V1.0 (Identical Dataset & Testing Conditions)",
                            style = MaterialTheme.typography.bodySmall,
                            color = theme.textSecondary,
                            fontSize = 12.sp
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = theme.textSecondary)
                    }
                }

                // Governance Notice Box (MANDATORY REQUIREMENT)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF3B82F6).copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Gavel,
                            contentDescription = "Governance Notice",
                            tint = Color(0xFF3B82F6),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "GOVERNANCE DIRECTIVE: Strategy comparison is presented objectively. The application does not automatically declare a winner based solely on win rate. Win rate is only one performance dimension.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = theme.textPrimary,
                            lineHeight = 15.sp
                        )
                    }
                }

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(color = theme.brandPrimary)
                            Text("Running side-by-side simulation on identical dataset...", color = theme.textSecondary, fontSize = 13.sp)
                        }
                    }
                } else if (comparisonData != null) {
                    // Comparison Content
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Dual Equity Curves Chart
                        item {
                            DualCurvesChartItem(
                                highCurve = comparisonData.highWinRateResult.equityCurve,
                                aPlusCurve = comparisonData.aPlusV1Result.equityCurve,
                                initialCapital = comparisonData.initialCapital
                            )
                        }

                        // Strategy Summary Column Headers
                        item {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = theme.surfaceElevated,
                                border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderSubtle)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp, horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "METRIC",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = theme.textSecondary,
                                        modifier = Modifier.weight(1.3f)
                                    )
                                    Column(modifier = Modifier.weight(1.2f), horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "HIGH WIN RATE",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = Color(0xFF38BDF8)
                                        )
                                        Text(
                                            text = "Historical Reference",
                                            fontSize = 9.sp,
                                            color = theme.textSecondary
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1.2f), horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "A+ V1.0 (FROZEN)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = Color(0xFF10B981)
                                        )
                                        Text(
                                            text = "Approved Baseline",
                                            fontSize = 9.sp,
                                            color = theme.textSecondary
                                        )
                                    }
                                }
                            }
                        }

                        // Metric Rows
                        items(comparisonData.metricRows) { row ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = theme.surfaceElevated.copy(alpha = 0.5f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderSubtle.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp, horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1.3f)) {
                                        Text(
                                            text = row.metricLabel,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            color = theme.textPrimary
                                        )
                                        if (row.note.isNotEmpty()) {
                                            Text(
                                                text = row.note,
                                                fontSize = 10.sp,
                                                color = theme.textSecondary
                                            )
                                        }
                                    }
                                    Text(
                                        text = row.highWinRateValue,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color(0xFF38BDF8),
                                        modifier = Modifier.weight(1.2f),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = row.aPlusV1Value,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color(0xFF10B981),
                                        modifier = Modifier.weight(1.2f),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                                    )
                                }
                            }
                        }

                        // Validation summary
                        item {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (comparisonData.validation.isValid) Color(0xFF10B981).copy(alpha = 0.1f) else Color(0xFFEF4444).copy(alpha = 0.1f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (comparisonData.validation.isValid) Color(0xFF10B981).copy(alpha = 0.3f) else Color(0xFFEF4444).copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (comparisonData.validation.isValid) Icons.Default.CheckCircle else Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = if (comparisonData.validation.isValid) Color(0xFF10B981) else Color(0xFFEF4444),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (comparisonData.validation.isValid) "Fair Comparison Audit: Identical candle count (${comparisonData.highWinRateResult.trades.size + comparisonData.highWinRateResult.metrics.losingTrades} trades simulated, identical timestamps & capital conditions)." else "Warning: Differences detected in underlying dataset.",
                                        fontSize = 11.sp,
                                        color = theme.textPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                // Footer Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onRerun,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Rerun", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Re-Run Comparison", fontSize = 13.sp)
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = theme.brandPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Done", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun DualCurvesChartItem(
    highCurve: List<EquityPoint>,
    aPlusCurve: List<EquityPoint>,
    initialCapital: Double
) {
    val theme = LocalAppTheme.current
    val textMeasurer = rememberTextMeasurer()

    if (highCurve.isEmpty() && aPlusCurve.isEmpty()) return

    val highMin = if (highCurve.isNotEmpty()) highCurve.minOf { it.equity } else initialCapital
    val highMax = if (highCurve.isNotEmpty()) highCurve.maxOf { it.equity } else initialCapital
    val aPlusMin = if (aPlusCurve.isNotEmpty()) aPlusCurve.minOf { it.equity } else initialCapital
    val aPlusMax = if (aPlusCurve.isNotEmpty()) aPlusCurve.maxOf { it.equity } else initialCapital

    val minEquity = min(initialCapital * 0.95, min(highMin, aPlusMin))
    val maxEquity = max(initialCapital * 1.05, max(highMax, aPlusMax))
    val span = max(1.0, maxEquity - minEquity)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = theme.surfaceElevated),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.borderSubtle))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Comparative Equity Growth ($${initialCapital.toInt()} Base)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.textPrimary
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(8.dp).background(Color(0xFF38BDF8), CircleShape))
                        Text("High Win Rate", fontSize = 10.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(8.dp).background(Color(0xFF10B981), CircleShape))
                        Text("A+ V1.0", fontSize = 10.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(theme.surface, RoundedCornerShape(8.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 6.dp)) {
                    val w = size.width
                    val h = size.height

                    fun eqToY(eq: Double): Float {
                        val norm = (maxEquity - eq) / span
                        return (norm * h).toFloat().coerceIn(0f, h)
                    }

                    // Baseline (Initial capital)
                    val baseLineY = eqToY(initialCapital)
                    drawLine(
                        color = theme.borderSubtle,
                        start = Offset(0f, baseLineY),
                        end = Offset(w, baseLineY),
                        strokeWidth = 1f,
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
                    )

                    // Draw High-Win-Rate Curve (Cyan)
                    if (highCurve.size >= 2) {
                        val stepX = w / (highCurve.size - 1)
                        val highPath = Path()
                        highPath.moveTo(0f, eqToY(highCurve[0].equity))
                        for (i in 1 until highCurve.size) {
                            highPath.lineTo(i * stepX, eqToY(highCurve[i].equity))
                        }
                        drawPath(highPath, color = Color(0xFF38BDF8), style = Stroke(width = 2f, cap = StrokeCap.Round))
                    }

                    // Draw A+ V1.0 Curve (Green)
                    if (aPlusCurve.size >= 2) {
                        val stepX = w / (aPlusCurve.size - 1)
                        val aPlusPath = Path()
                        aPlusPath.moveTo(0f, eqToY(aPlusCurve[0].equity))
                        for (i in 1 until aPlusCurve.size) {
                            aPlusPath.lineTo(i * stepX, eqToY(aPlusCurve[i].equity))
                        }
                        drawPath(aPlusPath, color = Color(0xFF10B981), style = Stroke(width = 2f, cap = StrokeCap.Round))
                    }
                }
            }
        }
    }
}

