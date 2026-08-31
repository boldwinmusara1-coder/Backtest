package com.example.tradestrat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Sort
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
import com.example.ui.theme.LocalAppTheme
import java.text.DecimalFormat

enum class TradeFilter(val label: String) {
    ALL("All Trades"),
    WINS("Wins"),
    LOSSES("Losses"),
    LONGS("Longs"),
    SHORTS("Shorts")
}

enum class TradeSort(val label: String) {
    DEFAULT("# (Sequence)"),
    DATE_DESC("Newest Date"),
    DATE_ASC("Oldest Date"),
    PNL_DESC("Highest P&L"),
    PNL_ASC("Lowest P&L"),
    R_DESC("Highest R")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeLogList(
    trades: List<Trade>,
    modifier: Modifier = Modifier,
    onTradeClick: (Trade) -> Unit = {}
) {
    val theme = LocalAppTheme.current
    var selectedFilter by remember { mutableStateOf(TradeFilter.ALL) }
    var selectedSort by remember { mutableStateOf(TradeSort.DEFAULT) }
    var showSortMenu by remember { mutableStateOf(false) }

    val filteredAndSortedTrades = remember(trades, selectedFilter, selectedSort) {
        val filtered = when (selectedFilter) {
            TradeFilter.ALL -> trades
            TradeFilter.WINS -> trades.filter { it.isWin }
            TradeFilter.LOSSES -> trades.filter { !it.isWin }
            TradeFilter.LONGS -> trades.filter { it.direction == TradeDirection.LONG }
            TradeFilter.SHORTS -> trades.filter { it.direction == TradeDirection.SHORT }
        }

        when (selectedSort) {
            TradeSort.DEFAULT -> filtered.sortedBy { it.id }
            TradeSort.DATE_DESC -> filtered.sortedByDescending { it.entryTimestamp }
            TradeSort.DATE_ASC -> filtered.sortedBy { it.entryTimestamp }
            TradeSort.PNL_DESC -> filtered.sortedByDescending { it.pnlDollars }
            TradeSort.PNL_ASC -> filtered.sortedBy { it.pnlDollars }
            TradeSort.R_DESC -> filtered.sortedByDescending { it.rMultiple }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("trade_log_card"),
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
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "Trades",
                        tint = theme.brandPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Trade Execution Log (${trades.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = theme.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Sort Action
                Box {
                    OutlinedButton(
                        onClick = { showSortMenu = true },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(Icons.Default.Sort, contentDescription = "Sort", modifier = Modifier.size(14.dp), tint = theme.brandPrimary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(selectedSort.label, fontSize = 11.sp, color = theme.textPrimary)
                    }

                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        TradeSort.values().forEach { sortOption ->
                            DropdownMenuItem(
                                text = { Text(sortOption.label, fontSize = 12.sp) },
                                onClick = {
                                    selectedSort = sortOption
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
            }

            // Filter Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TradeFilter.values().forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter.label, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = theme.brandPrimary.copy(alpha = 0.18f),
                            selectedLabelColor = theme.brandPrimary,
                            containerColor = theme.surfaceElevated,
                            labelColor = theme.textSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedFilter == filter,
                            borderColor = theme.borderSubtle,
                            selectedBorderColor = theme.brandPrimary
                        ),
                        modifier = Modifier.height(30.dp)
                    )
                }
            }

            if (filteredAndSortedTrades.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No trades match the selected filter", color = theme.textMuted, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    filteredAndSortedTrades.take(25).forEach { trade ->
                        TradeItemRow(trade = trade, onClick = { onTradeClick(trade) })
                    }

                    if (filteredAndSortedTrades.size > 25) {
                        Text(
                            text = "+ ${filteredAndSortedTrades.size - 25} more trades in full history",
                            style = MaterialTheme.typography.labelSmall,
                            color = theme.textSecondary,
                            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TradeItemRow(trade: Trade, onClick: () -> Unit) {
    val theme = LocalAppTheme.current
    val df = remember { DecimalFormat("#,##0.00") }
    val isLong = trade.direction == TradeDirection.LONG
    val dirColor = if (isLong) theme.accentGreen else theme.accentRed
    val pColor = if (trade.isWin) theme.accentGreen else theme.accentRed
    val reasonColor = Color(trade.exitReason.badgeColor)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("trade_item_${trade.id}"),
        shape = RoundedCornerShape(10.dp),
        color = theme.surfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderSubtle.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Row 1: Direction badge, ID, Exit Reason, PnL
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Direction badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = dirColor.copy(alpha = 0.18f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isLong) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                contentDescription = trade.direction.name,
                                tint = dirColor,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = trade.direction.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = dirColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Text(
                        text = "#${trade.id}",
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.textSecondary,
                        fontWeight = FontWeight.Bold
                    )

                    // Exit Reason badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = reasonColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = trade.exitReason.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = reasonColor,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // PnL & R Multiple
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${if (trade.isWin) "+" else ""}$${df.format(trade.pnlDollars)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = pColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${if (trade.isWin) "+" else ""}${df.format(trade.pnlPercent)}% (${String.format("%+.2fR", trade.rMultiple)})",
                        style = MaterialTheme.typography.labelSmall,
                        color = pColor,
                        fontSize = 10.sp
                    )
                }
            }

            // Row 2: Price Details (Entry, Stop, Exit)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Entry: $${df.format(trade.entryPrice)}",
                    fontSize = 10.sp,
                    color = theme.textPrimary
                )
                Text(
                    text = "Stop: $${df.format(trade.stopLossPrice)}",
                    fontSize = 10.sp,
                    color = theme.textSecondary
                )
                Text(
                    text = "Exit: $${df.format(trade.exitPrice)}",
                    fontSize = 10.sp,
                    color = theme.textPrimary
                )
            }

            // Row 3: Dates & Duration
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "In: ${trade.formattedEntryDate()}",
                    fontSize = 9.sp,
                    color = theme.textMuted
                )
                Text(
                    text = "Out: ${trade.formattedExitDate()} (${trade.holdingBars} bars)",
                    fontSize = 9.sp,
                    color = theme.textMuted
                )
            }
        }
    }
}
