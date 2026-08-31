package com.example.tradestrat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tradestrat.model.*
import com.example.ui.theme.LocalAppTheme
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeDetailSheet(
    trade: Trade,
    strategy: StrategyDefinition,
    asset: MarketAsset,
    onAddToJournal: (notes: String, tags: List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalAppTheme.current
    var journalNotes by remember { mutableStateOf("") }
    var selectedGrade by remember { mutableStateOf(if (trade.isWin) "A+" else "B") }
    var selectedTag by remember { mutableStateOf(if (trade.isWin) "Disciplined" else "Early Exit") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = theme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = theme.textMuted) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (trade.direction == TradeDirection.LONG) theme.accentGreen.copy(alpha = 0.18f) else theme.accentRed.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = trade.direction.name,
                                color = if (trade.direction == TradeDirection.LONG) theme.accentGreen else theme.accentRed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        Text(
                            text = asset.symbol,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = theme.textPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Strategy: ${strategy.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.textSecondary,
                        fontSize = 11.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (trade.isWin) theme.accentGreen.copy(alpha = 0.15f) else theme.accentRed.copy(alpha = 0.15f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = String.format(Locale.US, "%+.2f%%", trade.pnlPercent),
                            fontWeight = FontWeight.Bold,
                            color = if (trade.isWin) theme.accentGreen else theme.accentRed,
                            fontSize = 16.sp
                        )
                        Text(
                            text = String.format(Locale.US, "%+.2f R", trade.rMultiple),
                            color = if (trade.isWin) theme.accentGreen else theme.accentRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            HorizontalDivider(color = theme.borderSubtle, thickness = 1.dp)

            // Primary Trade Metrics Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricItemBox(
                    label = "ENTRY PRICE",
                    value = String.format(Locale.US, "$%.4f", trade.entryPrice),
                    subValue = trade.formattedEntryDate(),
                    modifier = Modifier.weight(1f),
                    theme = theme
                )

                MetricItemBox(
                    label = "EXIT PRICE",
                    value = String.format(Locale.US, "$%.4f", trade.exitPrice),
                    subValue = trade.formattedExitDate(),
                    modifier = Modifier.weight(1f),
                    theme = theme
                )
            }

            if (trade.stopLossPrice != null || trade.takeProfitPrice != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricItemBox(
                        label = "STOP LOSS",
                        value = trade.stopLossPrice?.let { String.format(Locale.US, "$%.4f", it) } ?: "None",
                        subValue = "Calculated Stop",
                        modifier = Modifier.weight(1f),
                        theme = theme,
                        valueColor = theme.accentRed
                    )

                    MetricItemBox(
                        label = "TAKE PROFIT",
                        value = trade.takeProfitPrice?.let { String.format(Locale.US, "$%.4f", it) } ?: "None",
                        subValue = "Calculated Target",
                        modifier = Modifier.weight(1f),
                        theme = theme,
                        valueColor = theme.accentGreen
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricItemBox(
                    label = "NET P&L ($)",
                    value = String.format(Locale.US, "%+.2f", trade.pnlDollars),
                    subValue = "Fees: $${String.format(Locale.US, "%.2f", trade.feesPaid)}",
                    modifier = Modifier.weight(1f),
                    theme = theme,
                    valueColor = if (trade.isWin) theme.accentGreen else theme.accentRed
                )

                MetricItemBox(
                    label = "EXIT REASON",
                    value = trade.exitReason.label,
                    subValue = "Duration: ${trade.holdingBars} bars",
                    modifier = Modifier.weight(1f),
                    theme = theme
                )
            }

            // Excursion (MAE & MFE)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = theme.surfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderSubtle)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("MAX RUN-UP (MFE)", fontSize = 10.sp, color = theme.textMuted, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = String.format(Locale.US, "+%.2f%%", trade.maxRunUpPct),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.accentGreen
                        )
                    }

                    VerticalDivider(
                        color = theme.borderSubtle,
                        modifier = Modifier
                            .height(30.dp)
                            .width(1.dp)
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("MAX DRAWDOWN (MAE)", fontSize = 10.sp, color = theme.textMuted, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = String.format(Locale.US, "-%.2f%%", trade.maxDrawdownPct),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.accentRed
                        )
                    }
                }
            }

            // Strategy Confluence & Overlays Context
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = theme.surfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderSubtle)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "STRATEGY SIGNAL PROVENANCE",
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.brandPrimary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Text(
                        text = trade.entryReason ?: when (strategy.strategyType) {
                            StrategyType.ICT_CONCEPTS -> "ICT Liquidity Sweep + Fair Value Gap (FVG) Displacement entry"
                            StrategyType.SMC_CONCEPTS -> "SMC Change of Character (CHOCH) + Order Block Retest confirmation"
                            StrategyType.SMC_ICT_CONCEPTS -> "Multi-Confluence: ICT FVG + SMC Order Block + Liquidity Mitigation"
                            StrategyType.TRENDLINE_BREAK, StrategyType.TRENDLINE_BOUNCE -> "Trendline Breakout/Bounce with volume validation"
                            StrategyType.TURTLE_BREAKOUT -> "Donchian 20-bar breakout with trailing ATR stop"
                            StrategyType.OPENING_RANGE_BREAKOUT -> "Session opening range break with expansion momentum"
                            else -> "Quantitative technical trigger"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.textPrimary,
                        fontSize = 12.sp
                    )
                }
            }

            // Journal Quick Note & Logging
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "LOG TO TRADE JOURNAL",
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.textSecondary,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = journalNotes,
                    onValueChange = { journalNotes = it },
                    placeholder = { Text("Enter thesis, execution notes, or psychology observation...", fontSize = 12.sp, color = theme.textMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = theme.brandPrimary,
                        unfocusedBorderColor = theme.borderSubtle,
                        focusedContainerColor = theme.surfaceElevated,
                        unfocusedContainerColor = theme.surfaceElevated,
                        focusedTextColor = theme.textPrimary,
                        unfocusedTextColor = theme.textPrimary
                    ),
                    maxLines = 3
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("A+", "A", "B", "C").forEach { grade ->
                        FilterChip(
                            selected = selectedGrade == grade,
                            onClick = { selectedGrade = grade },
                            label = { Text(grade, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = theme.brandPrimary.copy(alpha = 0.2f),
                                selectedLabelColor = theme.brandPrimary
                            )
                        )
                    }
                }

                Button(
                    onClick = {
                        onAddToJournal(journalNotes, listOf(selectedGrade, selectedTag))
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.brandPrimary)
                ) {
                    Icon(imageVector = Icons.Default.BookmarkBorder, contentDescription = "Save")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save to Trade Journal", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun MetricItemBox(
    label: String,
    value: String,
    subValue: String,
    modifier: Modifier = Modifier,
    theme: com.example.ui.theme.AppColors,
    valueColor: Color = theme.textPrimary
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = theme.surfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderSubtle)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = theme.textMuted
            )
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
            Text(
                text = subValue,
                fontSize = 10.sp,
                color = theme.textSecondary
            )
        }
    }
}
