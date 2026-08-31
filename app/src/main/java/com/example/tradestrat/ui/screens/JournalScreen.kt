package com.example.tradestrat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tradestrat.model.JournalEntry
import com.example.tradestrat.model.TradeDirection
import com.example.tradestrat.ui.BacktestViewModel
import com.example.ui.theme.LocalAppTheme
import java.util.Locale

enum class JournalFilter(val label: String) {
    ALL("All Trades"),
    WINS("Profitable"),
    LOSSES("Losses"),
    A_GRADES("A+ / A Setups"),
    MANUAL_REPLAY("Manual Replay")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(
    viewModel: BacktestViewModel,
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {}
) {
    val theme = LocalAppTheme.current
    val journalEntries by viewModel.journalEntries.collectAsState()
    var selectedFilter by remember { mutableStateOf(JournalFilter.ALL) }

    val filteredEntries = remember(journalEntries, selectedFilter) {
        when (selectedFilter) {
            JournalFilter.ALL -> journalEntries
            JournalFilter.WINS -> journalEntries.filter { it.isWin }
            JournalFilter.LOSSES -> journalEntries.filter { !it.isWin }
            JournalFilter.A_GRADES -> journalEntries.filter { it.setupGrade.name.startsWith("A") }
            JournalFilter.MANUAL_REPLAY -> journalEntries.filter { it.isManualReplay }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = theme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "TRADE JOURNAL",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = theme.textPrimary
                        )
                        Text(
                            text = "${journalEntries.size} Recorded Entries • Execution & Psychology",
                            style = MaterialTheme.typography.bodySmall,
                            color = theme.textSecondary,
                            fontSize = 11.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = theme.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = theme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Filter Chips
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(JournalFilter.values()) { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter.label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = theme.brandPrimary.copy(alpha = 0.2f),
                                selectedLabelColor = theme.brandPrimary
                            )
                        )
                    }
                }
            }

            if (filteredEntries.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = theme.surface),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.borderSubtle))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.EditNote, contentDescription = "Empty", tint = theme.textMuted, modifier = Modifier.size(42.dp))
                            Text("No Journal Entries Found", fontWeight = FontWeight.Bold, color = theme.textPrimary)
                            Text(
                                "Log trades directly from the Trade Execution Log, Trade Detail Sheet, or Historical Replay mode.",
                                fontSize = 12.sp,
                                color = theme.textSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(filteredEntries, key = { it.id }) { entry ->
                    JournalItemCard(
                        entry = entry,
                        onDelete = { viewModel.deleteJournalEntry(entry.id) },
                        theme = theme
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(30.dp)) }
        }
    }
}

@Composable
private fun JournalItemCard(
    entry: JournalEntry,
    onDelete: () -> Unit,
    theme: com.example.ui.theme.AppColors
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = theme.surface),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.borderSubtle))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Row: Symbol, Direction, Date, PnL
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (entry.direction == TradeDirection.LONG) theme.accentGreen.copy(alpha = 0.15f) else theme.accentRed.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = entry.direction.name,
                            color = if (entry.direction == TradeDirection.LONG) theme.accentGreen else theme.accentRed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = entry.symbol,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = theme.textPrimary
                    )

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = theme.brandPrimary.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "Grade: ${entry.setupGrade.label}",
                            color = theme.brandPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = String.format(Locale.US, "%+.2f%%", entry.pnlPercent),
                        fontWeight = FontWeight.Bold,
                        color = if (entry.isWin) theme.accentGreen else theme.accentRed,
                        fontSize = 14.sp
                    )
                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = "Delete", tint = theme.textMuted, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Strategy & Provenance
            Text(
                text = "${entry.strategyName} • ${entry.entryReason ?: "Standard Trigger"}",
                fontSize = 11.sp,
                color = theme.textSecondary
            )

            // Price Details
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = theme.surfaceElevated
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Entry: $${String.format(Locale.US, "%.4f", entry.entryPrice)}", fontSize = 11.sp, color = theme.textMuted)
                    Text(text = "Exit: $${String.format(Locale.US, "%.4f", entry.exitPrice)}", fontSize = 11.sp, color = theme.textMuted)
                    Text(
                        text = String.format(Locale.US, "%+.2f R", entry.rMultiple),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (entry.isWin) theme.accentGreen else theme.accentRed
                    )
                }
            }

            // Thesis / Notes
            if (entry.thesis.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = theme.surfaceElevated
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(text = "THESIS & NOTES", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = theme.textMuted)
                        Text(text = entry.thesis, fontSize = 12.sp, color = theme.textPrimary)
                    }
                }
            }

            // Tags
            if (entry.tags.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    entry.tags.forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = theme.brandSecondary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "#$tag",
                                fontSize = 10.sp,
                                color = theme.brandSecondary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
