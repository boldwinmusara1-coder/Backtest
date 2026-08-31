package com.example.tradestrat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tradestrat.model.TradeDirection
import com.example.tradestrat.ui.BacktestViewModel
import com.example.tradestrat.ui.components.CandlestickChart
import com.example.ui.theme.LocalAppTheme
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplayScreen(
    viewModel: BacktestViewModel,
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {}
) {
    val theme = LocalAppTheme.current
    val currentResult by viewModel.currentResult.collectAsState()
    val isReplayActive by viewModel.isReplayActive.collectAsState()
    val replayIndex by viewModel.replayCurrentIndex.collectAsState()
    val isPlaying by viewModel.isReplayPlaying.collectAsState()
    val replaySpeed by viewModel.replaySpeed.collectAsState()
    val activePosition by viewModel.activeManualPosition.collectAsState()
    val manualTrades by viewModel.manualReplayTrades.collectAsState()
    val asset by viewModel.selectedAsset.collectAsState()
    val tf by viewModel.selectedTimeframe.collectAsState()

    // Initialize replay if not active
    LaunchedEffect(isReplayActive) {
        if (!isReplayActive && currentResult != null) {
            viewModel.startHistoricalReplay(30)
        }
    }

    val candles = currentResult?.candles ?: emptyList()
    val visibleCandles = remember(candles, replayIndex) {
        if (candles.isEmpty() || replayIndex <= 0) emptyList()
        else candles.take(replayIndex + 1)
    }

    val currentCandle = visibleCandles.lastOrNull()

    // Manual Trade Inputs
    var stopLossInput by remember(currentCandle) {
        mutableStateOf(currentCandle?.let { String.format(Locale.US, "%.4f", it.close * 0.98) } ?: "")
    }
    var takeProfitInput by remember(currentCandle) {
        mutableStateOf(currentCandle?.let { String.format(Locale.US, "%.4f", it.close * 1.04) } ?: "")
    }
    var tradeNotes by remember { mutableStateOf("") }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = theme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "HISTORICAL REPLAY",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = theme.textPrimary
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = theme.brandPrimary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "MANUAL TRADING",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = theme.brandPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "${asset.symbol} • ${tf.label} • Bar ${replayIndex + 1}/${candles.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = theme.textSecondary,
                            fontSize = 11.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.exitReplay()
                        onNavigateBack()
                    }) {
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
            // Chart Canvas Area
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.surface),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.borderSubtle))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = currentCandle?.formattedDate(tf.minutes) ?: "Loading...",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = theme.textPrimary
                            )
                            Text(
                                text = "Price: ${currentCandle?.let { String.format(Locale.US, "$%.4f", it.close) } ?: "-"}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.brandPrimary
                            )
                        }

                        if (visibleCandles.isNotEmpty()) {
                            CandlestickChart(
                                candles = visibleCandles,
                                timeFrameMinutes = tf.minutes,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = theme.brandPrimary)
                            }
                        }
                    }
                }
            }

            // Replay Playback Controls
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = theme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderSubtle)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Playback Button Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { viewModel.resetReplayToStart() }) {
                                Icon(Icons.Default.FirstPage, contentDescription = "Start", tint = theme.textPrimary)
                            }

                            IconButton(onClick = { viewModel.stepReplay(-1) }) {
                                Icon(Icons.Default.NavigateBefore, contentDescription = "Step Back", tint = theme.textPrimary)
                            }

                            FilledIconButton(
                                onClick = { viewModel.toggleReplayPlay() },
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = theme.brandPrimary)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White
                                )
                            }

                            IconButton(onClick = { viewModel.stepReplay(1) }) {
                                Icon(Icons.Default.NavigateNext, contentDescription = "Step Forward", tint = theme.textPrimary)
                            }

                            IconButton(onClick = { viewModel.stepReplay(10) }) {
                                Icon(Icons.Default.FastForward, contentDescription = "Jump 10", tint = theme.textPrimary)
                            }
                        }

                        // Speed Selector
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Speed:", fontSize = 11.sp, color = theme.textMuted, fontWeight = FontWeight.SemiBold)
                            listOf(0.25f, 0.5f, 1.0f, 2.0f, 5.0f).forEach { spd ->
                                FilterChip(
                                    selected = replaySpeed == spd,
                                    onClick = { viewModel.setReplaySpeed(spd) },
                                    label = { Text("${spd}x", fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = theme.brandPrimary.copy(alpha = 0.2f),
                                        selectedLabelColor = theme.brandPrimary
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Manual Trading Execution Panel
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.surface),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.borderSubtle))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "MANUAL REPLAY EXECUTION",
                            style = MaterialTheme.typography.labelSmall,
                            color = theme.brandPrimary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        if (activePosition != null) {
                            val pos = activePosition!!
                            val closePrice = currentCandle?.close ?: pos.entryPrice
                            val diff = if (pos.direction == TradeDirection.LONG) closePrice - pos.entryPrice else pos.entryPrice - closePrice
                            val pnl = diff * pos.quantity
                            val isWin = pnl >= 0

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = theme.surfaceElevated,
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isWin) theme.accentGreen else theme.accentRed)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Active ${pos.direction.name} Position", fontWeight = FontWeight.Bold, color = theme.textPrimary)
                                        Text(
                                            text = String.format(Locale.US, "%+.2f USD", pnl),
                                            fontWeight = FontWeight.Bold,
                                            color = if (isWin) theme.accentGreen else theme.accentRed
                                        )
                                    }
                                    Text("Entry: $${String.format(Locale.US, "%.4f", pos.entryPrice)} • SL: $${String.format(Locale.US, "%.4f", pos.stopLoss)} • TP: $${String.format(Locale.US, "%.4f", pos.takeProfit)}", fontSize = 11.sp, color = theme.textSecondary)

                                    Button(
                                        onClick = { viewModel.closeManualReplayPositionManually() },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = theme.accentRed),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Close Position at Market ($${String.format(Locale.US, "%.4f", closePrice)})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        } else {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = stopLossInput,
                                    onValueChange = { stopLossInput = it },
                                    label = { Text("Stop Loss ($)", fontSize = 10.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                )

                                OutlinedTextField(
                                    value = takeProfitInput,
                                    onValueChange = { takeProfitInput = it },
                                    label = { Text("Take Profit ($)", fontSize = 10.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = {
                                        val sl = stopLossInput.toDoubleOrNull() ?: (currentCandle?.close?.times(0.98) ?: 0.0)
                                        val tp = takeProfitInput.toDoubleOrNull() ?: (currentCandle?.close?.times(1.04) ?: 0.0)
                                        viewModel.placeManualReplayOrder(TradeDirection.LONG, sl, tp, tradeNotes)
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = theme.accentGreen),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = "Buy")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("BUY / LONG", fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        val sl = stopLossInput.toDoubleOrNull() ?: (currentCandle?.close?.times(1.02) ?: 0.0)
                                        val tp = takeProfitInput.toDoubleOrNull() ?: (currentCandle?.close?.times(0.96) ?: 0.0)
                                        viewModel.placeManualReplayOrder(TradeDirection.SHORT, sl, tp, tradeNotes)
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = theme.accentRed),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = "Sell")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("SELL / SHORT", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Completed Replay Trades
            if (manualTrades.isNotEmpty()) {
                item {
                    Text(
                        text = "MANUAL REPLAY TRADES (${manualTrades.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.textMuted,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                items(manualTrades) { trade ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = theme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderSubtle)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "${trade.direction.name} • ${trade.exitReason.label}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (trade.isWin) theme.accentGreen else theme.accentRed
                                )
                                Text(
                                    text = "Entry: $${String.format(Locale.US, "%.4f", trade.entryPrice)} → Exit: $${String.format(Locale.US, "%.4f", trade.exitPrice)} (${trade.holdingBars} bars)",
                                    fontSize = 10.sp,
                                    color = theme.textSecondary
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = String.format(Locale.US, "%+.2f USD", trade.pnlDollars),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (trade.isWin) theme.accentGreen else theme.accentRed
                                )
                                Text(
                                    text = String.format(Locale.US, "%+.2f R", trade.rMultiple),
                                    fontSize = 11.sp,
                                    color = if (trade.isWin) theme.accentGreen else theme.accentRed
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(30.dp)) }
        }
    }
}
