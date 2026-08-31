package com.example.tradestrat.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.tradestrat.model.ExitReason
import com.example.tradestrat.model.Trade
import com.example.tradestrat.model.TradeDirection
import com.example.ui.theme.*

enum class JournalFilter(val label: String) {
    ALL("All Trades"),
    WINS("Profits"),
    LOSSES("Losses"),
    LONGS("Longs"),
    SHORTS("Shorts")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeJournalComponent(
    trades: List<Trade>,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf(JournalFilter.ALL) }
    var expandedTradeId by remember { mutableStateOf<String?>(null) }

    val filteredTrades = remember(trades, selectedFilter) {
        when (selectedFilter) {
            JournalFilter.ALL -> trades
            JournalFilter.WINS -> trades.filter { it.isWin }
            JournalFilter.LOSSES -> trades.filter { !it.isWin }
            JournalFilter.LONGS -> trades.filter { it.direction == TradeDirection.LONG }
            JournalFilter.SHORTS -> trades.filter { it.direction == TradeDirection.SHORT }
        }
    }

    val totalWins = trades.count { it.isWin }
    val totalLosses = trades.count { !it.isWin }
    val netPnl = trades.sumOf { it.pnlDollars }
    val winRate = if (trades.isNotEmpty()) (totalWins.toDouble() / trades.size) * 100.0 else 0.0

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("trade_journal_card"),
        shape = RoundedCornerShape(14.dp),
        color = TvSurface,
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(TvBorder))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Journal Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = TvBlueContainer
                    ) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = "Trade Journal",
                            tint = TvBlue,
                            modifier = Modifier
                                .padding(6.dp)
                                .size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Trade Execution Journal",
                            style = MaterialTheme.typography.titleMedium,
                            color = TvTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${trades.size} Total Executions • ${String.format("%.1f%%", winRate)} Win Rate",
                            style = MaterialTheme.typography.labelSmall,
                            color = TvTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                // Net Profit Badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (netPnl >= 0) TvGreen.copy(alpha = 0.15f) else TvRed.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "${if (netPnl >= 0) "+" else ""}$${String.format("%,.0f", netPnl)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (netPnl >= 0) TvGreen else TvRed,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Journal Stats Summary Bar
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = TvSurfaceElevated
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("WINNERS", style = MaterialTheme.typography.labelSmall, color = TvTextSecondary, fontSize = 9.sp)
                        Text("$totalWins", style = MaterialTheme.typography.titleSmall, color = TvGreen, fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text("LOSERS", style = MaterialTheme.typography.labelSmall, color = TvTextSecondary, fontSize = 9.sp)
                        Text("$totalLosses", style = MaterialTheme.typography.titleSmall, color = TvRed, fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text("WIN RATE", style = MaterialTheme.typography.labelSmall, color = TvTextSecondary, fontSize = 9.sp)
                        Text(String.format("%.1f%%", winRate), style = MaterialTheme.typography.titleSmall, color = TvBlue, fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text("SHOWING", style = MaterialTheme.typography.labelSmall, color = TvTextSecondary, fontSize = 9.sp)
                        Text("${filteredTrades.size}", style = MaterialTheme.typography.titleSmall, color = TvTextPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                JournalFilter.values().forEach { filter ->
                    val isSelected = selectedFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter.label, fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TvBlue,
                            selectedLabelColor = Color.White,
                            containerColor = TvSurfaceElevated,
                            labelColor = TvTextSecondary
                        ),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Trade Journal Entries List
            if (filteredTrades.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No trades match the selected filter",
                        color = TvTextMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    filteredTrades.forEachIndexed { index, trade ->
                        val isExpanded = expandedTradeId == trade.id
                        TradeJournalEntryCard(
                            tradeNumber = filteredTrades.size - index,
                            trade = trade,
                            isExpanded = isExpanded,
                            onToggleExpand = {
                                expandedTradeId = if (isExpanded) null else trade.id
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TradeJournalEntryCard(
    tradeNumber: Int,
    trade: Trade,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val isLong = trade.direction == TradeDirection.LONG
    val dirColor = if (isLong) TvGreen else TvRed
    val pnlColor = if (trade.isWin) TvGreen else TvRed
    val reasonColor = when (trade.exitReason) {
        ExitReason.TAKE_PROFIT -> TvGreen
        ExitReason.STOP_LOSS -> TvRed
        ExitReason.TRAILING_STOP -> TvAmber
        ExitReason.SIGNAL_REVERSAL -> TvCyan
        ExitReason.CIRCUIT_BREAKER -> TvPurple
        ExitReason.BREAK_EVEN -> TvCyan
        ExitReason.STRUCTURE_EXIT -> TvAmber
        ExitReason.END_OF_DATA -> TvTextSecondary
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleExpand() }
            .testTag("journal_trade_${trade.id}"),
        shape = RoundedCornerShape(8.dp),
        color = TvSurfaceElevated,
        border = if (isExpanded) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(TvBlue.copy(alpha = 0.5f))) else null
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Main Summary Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Trade # + Direction Pill + Exit Reason
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "#$tradeNumber",
                        style = MaterialTheme.typography.labelSmall,
                        color = TvTextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )

                    // Direction Badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = dirColor.copy(alpha = 0.15f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = if (isLong) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                contentDescription = trade.direction.label,
                                tint = dirColor,
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = trade.direction.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = dirColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                    }

                    // Exit Reason Tag
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = reasonColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = trade.exitReason.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = reasonColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }

                // Right: Profit / Loss per trade ($ & %)
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${if (trade.isWin) "+" else ""}$${String.format("%,.2f", trade.pnlDollars)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = pnlColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "${if (trade.isWin) "+" else ""}${String.format("%.2f%%", trade.pnlPercent)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = pnlColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Entry & Exit Price / Timestamps Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Entry details
                Column {
                    Text(
                        text = "ENTRY: $${String.format("%,.2f", trade.entryPrice)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TvTextPrimary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.sp
                    )
                    Text(
                        text = trade.formattedEntryDate(),
                        style = MaterialTheme.typography.labelSmall,
                        color = TvTextSecondary,
                        fontSize = 9.sp
                    )
                }

                // Arrow indicator
                Icon(
                    imageVector = Icons.Default.East,
                    contentDescription = "to",
                    tint = TvTextMuted,
                    modifier = Modifier.size(12.dp)
                )

                // Exit details
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "EXIT: $${String.format("%,.2f", trade.exitPrice)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TvTextPrimary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.sp
                    )
                    Text(
                        text = trade.formattedExitDate(),
                        style = MaterialTheme.typography.labelSmall,
                        color = TvTextSecondary,
                        fontSize = 9.sp
                    )
                }
            }

            // Expanded Journal Analytics Details
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Divider(color = TvBorder, thickness = 0.8.dp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("HOLDING PERIOD", style = MaterialTheme.typography.labelSmall, color = TvTextSecondary, fontSize = 8.sp)
                            Text("${trade.holdingBars} bars", style = MaterialTheme.typography.labelSmall, color = TvTextPrimary, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                        Column {
                            Text("R-MULTIPLE", style = MaterialTheme.typography.labelSmall, color = TvTextSecondary, fontSize = 8.sp)
                            Text(String.format("%+.2fR", trade.rMultiple), style = MaterialTheme.typography.labelSmall, color = pnlColor, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                        Column {
                            Text("MAX RUN-UP", style = MaterialTheme.typography.labelSmall, color = TvTextSecondary, fontSize = 8.sp)
                            Text(String.format("+%.2f%%", trade.maxRunUpPct), style = MaterialTheme.typography.labelSmall, color = TvGreen, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                        Column {
                            Text("MAX DRAWDOWN", style = MaterialTheme.typography.labelSmall, color = TvTextSecondary, fontSize = 8.sp)
                            Text(String.format("-%.2f%%", trade.maxDrawdownPct), style = MaterialTheme.typography.labelSmall, color = TvRed, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                    }

                    if (trade.explanation != null) {
                        val exp = trade.explanation!!
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = TvBackground,
                            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(TvBlue.copy(alpha = 0.3f)))
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "A+ AUDIT: ${exp.setupType}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TvBlue,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        text = "Structure: ${exp.trend}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TvCyan,
                                        fontSize = 9.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Line: ${exp.trendlineDescription}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TvTextPrimary,
                                    fontSize = 9.sp
                                )
                                Text(
                                    text = "Entry: ${exp.entryReason}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TvTextSecondary,
                                    fontSize = 9.sp
                                )
                                Text(
                                    text = "Stop: ${exp.stopReason}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TvAmber,
                                    fontSize = 9.sp
                                )
                                if (exp.retestStatus.isNotEmpty()) {
                                    Text(
                                        text = "Retest: ${exp.retestStatus}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TvGreen,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
