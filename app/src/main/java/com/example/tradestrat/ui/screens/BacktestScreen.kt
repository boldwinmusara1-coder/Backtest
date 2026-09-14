package com.example.tradestrat.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tradestrat.data.MarketDataProvider
import com.example.tradestrat.model.*
import com.example.tradestrat.ui.BacktestViewModel
import com.example.tradestrat.ui.components.*
import com.example.ui.theme.LocalAppTheme
import java.text.DecimalFormat

enum class BacktestResultTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    DASHBOARD("Dashboard", Icons.Default.Assessment),
    EQUITY_CURVE("Equity & Drawdown", Icons.Default.TrendingUp),
    MONTHLY("Monthly", Icons.Default.CalendarMonth),
    DISTRIBUTION("Distribution", Icons.Default.BarChart),
    TRADE_LOG("Trade Log", Icons.Default.ListAlt)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BacktestScreen(
    viewModel: BacktestViewModel,
    modifier: Modifier = Modifier,
    onNavigateToSmcIct: () -> Unit = {},
    onNavigateToAplusSpec: () -> Unit = {},
    onBacktestComplete: () -> Unit = {}
) {
    val theme = LocalAppTheme.current
    val selectedAsset by viewModel.selectedAsset.collectAsState()
    val selectedTimeframe by viewModel.selectedTimeframe.collectAsState()
    val selectedStrategy by viewModel.selectedStrategy.collectAsState()
    val riskParameters by viewModel.riskParameters.collectAsState()
    val selectedDatePreset by viewModel.selectedDatePreset.collectAsState()
    val selectedProvider by viewModel.selectedProvider.collectAsState()
    val isBacktesting by viewModel.isBacktesting.collectAsState()
    val progress by viewModel.backtestProgress.collectAsState()
    val currentResult by viewModel.currentResult.collectAsState()
    val latestHwrResult by viewModel.latestHwrResult.collectAsState()
    val latestAplusResult by viewModel.latestAplusResult.collectAsState()
    val showDualComparisonDialog by viewModel.showDualComparisonDialog.collectAsState()
    val dualComparisonData by viewModel.dualStrategyComparisonData.collectAsState()
    val isDualComparing by viewModel.isDualComparing.collectAsState()
    val dataFetchError by viewModel.dataFetchError.collectAsState()

    val allStrategies by viewModel.allStrategies.collectAsState()
    val pendingConfiguration by viewModel.pendingConfiguration.collectAsState()

    var showEngineValidationDialog by remember { mutableStateOf(false) }
    var showConfigEditorDialog by remember { mutableStateOf(false) }
    var showStrategyCustomizerDialog by remember { mutableStateOf(false) }
    var selectedResultTab by remember { mutableStateOf(BacktestResultTab.DASHBOARD) }
    var inspectedTrade by remember { mutableStateOf<Trade?>(null) }

    val df = remember { DecimalFormat("#,##0.00") }
    val isAplusSelected = selectedStrategy.strategyType == StrategyType.A_PLUS_TRENDLINE
    val isHighWinRateSelected = selectedStrategy.id == STRATEGY_ID_TRENDLINE_BREAK_HIGH_WIN_RATE || selectedStrategy.strategyType == StrategyType.TRENDLINE_BREAK

    // Engine Validation Dialog Modal
    if (showEngineValidationDialog) {
        EngineValidationDialog(
            onDismiss = { showEngineValidationDialog = false }
        )
    }

    // Dual Strategy Comparison Dialog Modal
    if (showDualComparisonDialog) {
        DualStrategyComparisonDialog(
            comparisonData = dualComparisonData,
            isLoading = isDualComparing,
            onDismiss = { viewModel.dismissDualComparison() },
            onRerun = { viewModel.runDualStrategyComparison() }
        )
    }

    // Trade Detail Bottom Sheet
    if (inspectedTrade != null) {
        TradeDetailSheet(
            trade = inspectedTrade!!,
            strategy = selectedStrategy,
            asset = selectedAsset,
            onAddToJournal = { notes, tags ->
                viewModel.createJournalEntryFromTrade(inspectedTrade!!, notes, tags)
                inspectedTrade = null
            },
            onDismiss = { inspectedTrade = null }
        )
    }

    // Strategy Customizer Dialog Modal
    if (showStrategyCustomizerDialog) {
        StrategyCustomizerDialog(
            strategy = selectedStrategy,
            onDismiss = { showStrategyCustomizerDialog = false },
            onSave = { updatedStrat ->
                viewModel.setStrategy(updatedStrat)
                showStrategyCustomizerDialog = false
            },
            onDuplicate = { newName ->
                viewModel.duplicateStrategyAsExperiment(selectedStrategy, newName)
                showStrategyCustomizerDialog = false
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(theme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)
    ) {
        // 1. TOP HEADER & COMPLIANCE BADGE
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Strategy Backtester",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = theme.textPrimary,
                        fontSize = 22.sp
                    )
                    Text(
                        text = "Quantitative institutional engine • Zero lookahead bias",
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.textSecondary,
                        fontSize = 12.sp
                    )
                }

                // Status Badge: ENGINE: ✓ VALIDATED (Clickable)
                Surface(
                    onClick = { showEngineValidationDialog = true },
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                    modifier = Modifier.testTag("engine_validation_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = "Validated",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "ENGINE: ✓ VALIDATED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                    }
                }
            }
        }

        // 2. STRATEGY SELECTOR (EXACTLY TWO STRATEGIES WITH DYNAMIC PERFORMANCE NUMBERS)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("strategy_selector_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.borderSubtle))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "STRATEGY SELECTOR",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = theme.textSecondary,
                            letterSpacing = 1.sp
                        )

                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = theme.brandPrimary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "READ-ONLY / FROZEN",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.brandPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Strategy 1: High-Win-Rate Trendline Break Card
                    val hwrActive = isHighWinRateSelected
                    Surface(
                        onClick = { viewModel.selectHighWinRateStrategy() },
                        shape = RoundedCornerShape(12.dp),
                        color = if (hwrActive) Color(0xFF38BDF8).copy(alpha = 0.12f) else theme.surfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (hwrActive) 1.8.dp else 1.dp,
                            color = if (hwrActive) Color(0xFF38BDF8) else theme.borderSubtle
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("btn_select_high_win_rate")
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "High-Win-Rate Trendline Break",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (hwrActive) Color(0xFF38BDF8) else theme.textPrimary
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFF38BDF8).copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = "REFERENCE",
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF38BDF8),
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                if (hwrActive) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Active", tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                                }
                            }

                            Text(
                                text = "Trendline breakout strategy tuned for high win frequency using confirmed pivot high/low structure.",
                                fontSize = 11.sp,
                                color = theme.textSecondary,
                                lineHeight = 15.sp
                            )

                            // Dynamic Metrics from actual user test (un-hardcoded)
                            val hwrMetrics = latestHwrResult?.metrics ?: if (hwrActive) currentResult?.metrics else null
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = theme.surface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderSubtle.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    StrategyMetricMini("Trades", hwrMetrics?.totalTrades?.toString() ?: "--", theme)
                                    StrategyMetricMini("Win Rate", hwrMetrics?.let { "${df.format(it.winRatePercent)}%" } ?: "--", theme, if ((hwrMetrics?.winRatePercent ?: 0.0) >= 50) theme.accentGreen else theme.textPrimary)
                                    StrategyMetricMini("Profit Factor", hwrMetrics?.let { df.format(it.profitFactor) } ?: "--", theme)
                                    StrategyMetricMini("Expectancy", hwrMetrics?.let { "$${df.format(it.expectancyDollars)}" } ?: "--", theme)
                                    StrategyMetricMini("Max DD", hwrMetrics?.let { "-${df.format(it.maxDrawdownPercent)}%" } ?: "--", theme, theme.accentRed)
                                }
                            }
                        }
                    }

                    // Strategy 2: A+ Trendline V1.0 Card
                    val aplusActive = isAplusSelected
                    Surface(
                        onClick = { viewModel.selectAPlusStrategy() },
                        shape = RoundedCornerShape(12.dp),
                        color = if (aplusActive) Color(0xFF10B981).copy(alpha = 0.12f) else theme.surfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (aplusActive) 1.8.dp else 1.dp,
                            color = if (aplusActive) Color(0xFF10B981) else theme.borderSubtle
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("btn_select_aplus_v1")
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "A+ Trendline V1.0",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (aplusActive) Color(0xFF10B981) else theme.textPrimary
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFF10B981).copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = "FROZEN SPEC",
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF10B981),
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                if (aplusActive) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Active", tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                                }
                            }

                            Text(
                                text = "Institutional trendline setup requiring 3-touch validation, dynamic ATR buffer stops, and scale exits.",
                                fontSize = 11.sp,
                                color = theme.textSecondary,
                                lineHeight = 15.sp
                            )

                            // Dynamic Metrics from actual user test (un-hardcoded)
                            val aplusMetrics = latestAplusResult?.metrics ?: if (aplusActive) currentResult?.metrics else null
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = theme.surface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderSubtle.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    StrategyMetricMini("Trades", aplusMetrics?.totalTrades?.toString() ?: "--", theme)
                                    StrategyMetricMini("Win Rate", aplusMetrics?.let { "${df.format(it.winRatePercent)}%" } ?: "--", theme, if ((aplusMetrics?.winRatePercent ?: 0.0) >= 50) theme.accentGreen else theme.textPrimary)
                                    StrategyMetricMini("Profit Factor", aplusMetrics?.let { df.format(it.profitFactor) } ?: "--", theme)
                                    StrategyMetricMini("Expectancy", aplusMetrics?.let { "$${df.format(it.expectancyDollars)}" } ?: "--", theme)
                                    StrategyMetricMini("Max DD", aplusMetrics?.let { "-${df.format(it.maxDrawdownPercent)}%" } ?: "--", theme, theme.accentRed)
                                }
                            }
                        }
                    }

                    // Additional Strategies & Custom Experiments
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "OTHER STRATEGIES & EXPERIMENTS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.textSecondary,
                            letterSpacing = 0.5.sp
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val otherStrategies = allStrategies.filter {
                                it.id != STRATEGY_ID_TRENDLINE_BREAK_HIGH_WIN_RATE && it.id != STRATEGY_ID_A_PLUS_V1_0
                            }
                            otherStrategies.forEach { strat ->
                                val isSel = selectedStrategy.id == strat.id
                                FilterChip(
                                    selected = isSel,
                                    onClick = { viewModel.setStrategy(strat) },
                                    label = { Text(strat.name, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                                    leadingIcon = if (strat.isCustom) {
                                        { Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                    } else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = theme.brandPrimary.copy(alpha = 0.2f),
                                        selectedLabelColor = theme.brandPrimary
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSel,
                                        borderColor = theme.borderSubtle,
                                        selectedBorderColor = theme.brandPrimary
                                    ),
                                    modifier = Modifier.height(30.dp)
                                )
                            }
                        }
                    }

                    // Active Selected Strategy Card (if not one of the two top cards)
                    if (!isHighWinRateSelected && !isAplusSelected) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = theme.brandPrimary.copy(alpha = 0.08f),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, theme.brandPrimary)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = selectedStrategy.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = theme.brandPrimary
                                        )
                                        Text(
                                            text = "Type: ${selectedStrategy.strategyType.name} ${if (selectedStrategy.isCustom) "• Custom Experiment" else "• Preset"}",
                                            fontSize = 10.sp,
                                            color = theme.textSecondary
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = { showStrategyCustomizerDialog = true },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Parameters", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Text(
                                    text = selectedStrategy.description,
                                    fontSize = 11.sp,
                                    color = theme.textSecondary,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    } else {
                        // Option to duplicate frozen strategy as experiment
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    viewModel.duplicateStrategyAsExperiment(
                                        baseStrategy = selectedStrategy,
                                        customName = "${selectedStrategy.name} (Custom)"
                                    )
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = theme.brandPrimary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Duplicate as Mutable Experiment", fontSize = 11.sp, color = theme.brandPrimary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        // 3. BACKTEST CONFIGURATION SECTION
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("market_configuration_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.borderSubtle))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = theme.brandPrimary, modifier = Modifier.size(16.dp))
                            Text(
                                text = "BACKTEST CONFIGURATION",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = theme.textSecondary,
                                letterSpacing = 1.sp
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = theme.brandPrimary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "${selectedAsset.symbol} • ${selectedTimeframe.label}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.brandPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // A. Asset Selection
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Asset", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = theme.textSecondary)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val popularAssets = listOf("BTC/USD", "ETH/USD", "SOL/USD", "SPY", "QQQ", "AAPL", "EUR/USD", "GBP/USD", "XAU/USD")
                            popularAssets.forEach { symbol ->
                                val asset = MarketDataProvider.ASSETS.find { it.symbol == symbol } ?: MarketDataProvider.ASSETS.first()
                                val isSel = selectedAsset.symbol == symbol
                                FilterChip(
                                    selected = isSel,
                                    onClick = { viewModel.setAsset(asset) },
                                    label = { Text(symbol, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = theme.brandPrimary.copy(alpha = 0.2f),
                                        selectedLabelColor = theme.brandPrimary
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSel,
                                        borderColor = theme.borderSubtle,
                                        selectedBorderColor = theme.brandPrimary
                                    ),
                                    modifier = Modifier.height(30.dp)
                                )
                            }
                        }
                    }

                    // B. Timeframe Selection
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Timeframe", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = theme.textSecondary)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(Timeframe.D1, Timeframe.H4, Timeframe.H1, Timeframe.M30, Timeframe.M15).forEach { tf ->
                                val isSel = selectedTimeframe == tf
                                FilterChip(
                                    selected = isSel,
                                    onClick = { viewModel.setTimeframe(tf) },
                                    label = { Text(tf.label, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = theme.brandPrimary.copy(alpha = 0.2f),
                                        selectedLabelColor = theme.brandPrimary
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSel,
                                        borderColor = theme.borderSubtle,
                                        selectedBorderColor = theme.brandPrimary
                                    ),
                                    modifier = Modifier.height(30.dp)
                                )
                            }
                        }
                    }

                    // C. Historical Dataset Range Presets
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Historical Dataset Period", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = theme.textSecondary)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            DateRangePreset.values().forEach { preset ->
                                val isSel = selectedDatePreset == preset
                                FilterChip(
                                    selected = isSel,
                                    onClick = { viewModel.setDatePreset(preset) },
                                    label = { Text(preset.label, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = theme.brandPrimary.copy(alpha = 0.2f),
                                        selectedLabelColor = theme.brandPrimary
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSel,
                                        borderColor = theme.borderSubtle,
                                        selectedBorderColor = theme.brandPrimary
                                    ),
                                    modifier = Modifier.height(30.dp)
                                )
                            }
                        }
                    }

                    // D. Quantitative Parameters Grid (Capital, Risk, Commission, Slippage)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = theme.surfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderSubtle.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ConfigSummaryRow("INITIAL CAPITAL", "$${df.format(riskParameters.initialCapital)}")
                            ConfigSummaryRow("RISK PER TRADE", if (isAplusSelected) "1.0% (Strict A+ Model)" else "${riskParameters.riskPerTradePercent}%")
                            ConfigSummaryRow("COMMISSION", "${(riskParameters.commissionPercent * 10000).toInt()} bps (0.10%)")
                            ConfigSummaryRow("SLIPPAGE", "${(riskParameters.slippagePercent * 10000).toInt()} bps (0.05%)")
                            ConfigSummaryRow("EXECUTION RULE", riskParameters.intrabarExecution.label)
                        }
                    }

                    // Data fetch error if any
                    dataFetchError?.let { err ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = theme.accentRed.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, theme.accentRed.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = err,
                                color = theme.accentRed,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }

        // 4. RUN BACKTEST & COMPARISON CONTROLS
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // RUN BACKTEST Button
                    Button(
                        onClick = { viewModel.runBacktest() },
                        enabled = !isBacktesting,
                        modifier = Modifier
                            .weight(1.3f)
                            .height(52.dp)
                            .testTag("btn_run_backtest"),
                        colors = ButtonDefaults.buttonColors(containerColor = theme.brandPrimary),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (isBacktesting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(progress.stageMessage.ifBlank { "Running backtest..." }, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("RUN BACKTEST", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    // COMPARE Button (High-Win-Rate vs A+ V1.0)
                    OutlinedButton(
                        onClick = { viewModel.runDualStrategyComparison() },
                        enabled = !isDualComparing,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("btn_compare_strategies"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.brandPrimary),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, theme.brandPrimary)
                    ) {
                        if (isDualComparing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = theme.brandPrimary, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.CompareArrows, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("COMPARE", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Staged Non-blocking Progress Indicator
                if (isBacktesting) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { progress.progressPct },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = theme.brandPrimary,
                            trackColor = theme.borderSubtle
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = progress.stageMessage.ifBlank { "Processing..." },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = theme.brandPrimary
                            )
                            Text(
                                text = "${(progress.progressPct * 100).toInt()}%",
                                fontSize = 11.sp,
                                color = theme.textSecondary
                            )
                        }
                    }
                }
            }
        }

        // 5. COMPLETED RESULTS (INTEGRATED RESULTS DASHBOARD)
        if (currentResult != null) {
            val result = currentResult!!

            item {
                // Results Tab Bar
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = theme.surfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderSubtle)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        BacktestResultTab.values().forEach { tab ->
                            val isSelected = selectedResultTab == tab
                            Surface(
                                onClick = { selectedResultTab = tab },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) theme.brandPrimary else Color.Transparent
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = null,
                                        tint = if (isSelected) Color.White else theme.textSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (tab == BacktestResultTab.TRADE_LOG) "Trade Log (${result.trades.size})" else tab.label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else theme.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            when (selectedResultTab) {
                BacktestResultTab.DASHBOARD -> {
                    // Compliance Disclaimer
                    item {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = theme.surfaceElevated,
                            border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderSubtle.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "HISTORICAL BACKTEST RESULTS",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = theme.accentGreen
                                )
                                Text(
                                    text = "Past performance ≠ Future results",
                                    fontSize = 9.sp,
                                    color = theme.textMuted
                                )
                            }
                        }
                    }

                    // Configuration Snapshot of This Run
                    item {
                        val runConfig = result.configuration ?: pendingConfiguration
                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("result_config_snapshot_card"),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = theme.surface),
                            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.borderSubtle))
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "EXPERIMENT PARAMETER SNAPSHOT",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = theme.brandPrimary,
                                        letterSpacing = 1.sp
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = theme.brandPrimary.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = runConfig.strategy.name,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = theme.brandPrimary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Asset / Timeframe:", fontSize = 11.sp, color = theme.textSecondary)
                                    Text("${runConfig.asset.symbol} • ${runConfig.timeframe.label} (${runConfig.datePreset.label})", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = theme.textPrimary)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Risk & Capital:", fontSize = 11.sp, color = theme.textSecondary)
                                    Text("$${df.format(runConfig.initialCapital)} • ${runConfig.riskPerTrade}% risk/trade", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = theme.textPrimary)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Execution & Slippage:", fontSize = 11.sp, color = theme.textSecondary)
                                    Text("${runConfig.commissionBps.toInt()} bps comm • ${runConfig.slippageBps.toInt()} bps slip • ${runConfig.intrabarExecution.label}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = theme.textPrimary)
                                }
                            }
                        }
                    }

                    // Key Performance Metrics (17 Metrics)
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("key_metrics_card"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = theme.surface),
                            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.borderSubtle))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "KEY PERFORMANCE METRICS",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = theme.textSecondary,
                                    letterSpacing = 1.sp
                                )

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    MetricBox(title = "Final Equity", value = "$${df.format(result.metrics.finalEquity)}", modifier = Modifier.weight(1f), isPositive = result.metrics.finalEquity >= result.metrics.initialCapital)
                                    MetricBox(title = "Net P&L", value = "${if (result.metrics.netProfitDollars >= 0) "+" else ""}$${df.format(result.metrics.netProfitDollars)}", modifier = Modifier.weight(1f), isPositive = result.metrics.netProfitDollars >= 0)
                                }

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    MetricBox(title = "ROI", value = "${if (result.metrics.netProfitPercent >= 0) "+" else ""}${df.format(result.metrics.netProfitPercent)}%", modifier = Modifier.weight(1f), isPositive = result.metrics.netProfitPercent >= 0)
                                    MetricBox(title = "Win Rate", value = "${df.format(result.metrics.winRatePercent)}%", modifier = Modifier.weight(1f), isPositive = result.metrics.winRatePercent >= 50.0)
                                }

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    MetricBox(title = "Profit Factor", value = df.format(result.metrics.profitFactor), modifier = Modifier.weight(1f), isPositive = result.metrics.profitFactor >= 1.0)
                                    MetricBox(title = "Expectancy", value = "${if (result.metrics.expectancyDollars >= 0) "+" else ""}$${df.format(result.metrics.expectancyDollars)}", modifier = Modifier.weight(1f), isPositive = result.metrics.expectancyDollars >= 0)
                                }

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    MetricBox(title = "Max Drawdown", value = "-${df.format(result.metrics.maxDrawdownPercent)}%", modifier = Modifier.weight(1f), isPositive = false)
                                    MetricBox(title = "Sharpe Ratio", value = df.format(result.metrics.sharpeRatio), modifier = Modifier.weight(1f), isPositive = result.metrics.sharpeRatio > 1.0)
                                }
                            }
                        }
                    }

                    // Trade Analysis & Breakdown
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("trade_analysis_card"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = theme.surface),
                            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.borderSubtle))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "TRADE ANALYSIS & STREAKS",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = theme.textSecondary,
                                    letterSpacing = 1.sp
                                )

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    MetricBox(title = "Total Trades", value = "${result.metrics.totalTrades}", modifier = Modifier.weight(1f))
                                    MetricBox(title = "Winning Trades", value = "${result.metrics.winningTrades}", modifier = Modifier.weight(1f), isPositive = true)
                                    MetricBox(title = "Losing Trades", value = "${result.metrics.losingTrades}", modifier = Modifier.weight(1f), isPositive = false)
                                }

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    MetricBox(title = "Average Win", value = "+$${df.format(result.metrics.avgWinDollars)}", modifier = Modifier.weight(1f), isPositive = true)
                                    MetricBox(title = "Average Loss", value = "-$${df.format(result.metrics.avgLossDollars)}", modifier = Modifier.weight(1f), isPositive = false)
                                    MetricBox(title = "Average R", value = "${String.format("%+.2f", result.metrics.avgRMultiple)}R", modifier = Modifier.weight(1f), isPositive = result.metrics.avgRMultiple > 0)
                                }

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    MetricBox(title = "Largest Win", value = "+$${df.format(result.metrics.largestWinningTradeDollars)}", modifier = Modifier.weight(1f), isPositive = true)
                                    MetricBox(title = "Largest Loss", value = "-$${df.format(result.metrics.largestLosingTradeDollars)}", modifier = Modifier.weight(1f), isPositive = false)
                                    MetricBox(title = "Max Consec Loss", value = "${result.metrics.maxConsecutiveLosses}", modifier = Modifier.weight(1f), isPositive = result.metrics.maxConsecutiveLosses <= 3)
                                }
                            }
                        }
                    }
                }

                BacktestResultTab.EQUITY_CURVE -> {
                    item {
                        EquityCurveChart(
                            equityCurve = result.equityCurve,
                            initialCapital = result.metrics.initialCapital,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                BacktestResultTab.MONTHLY -> {
                    item {
                        MonthlyPerformanceCard(
                            trades = result.trades,
                            initialCapital = result.metrics.initialCapital,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                BacktestResultTab.DISTRIBUTION -> {
                    item {
                        TradeDistributionCard(
                            trades = result.trades,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                BacktestResultTab.TRADE_LOG -> {
                    item {
                        TradeLogList(
                            trades = result.trades,
                            onTradeClick = { trade -> inspectedTrade = trade },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StrategyMetricMini(
    label: String,
    value: String,
    theme: com.example.ui.theme.AppColors,
    valueColor: Color = theme.textPrimary
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 9.sp, color = theme.textSecondary, fontWeight = FontWeight.Medium)
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
private fun ConfigSummaryRow(
    label: String,
    value: String
) {
    val theme = LocalAppTheme.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = theme.textSecondary,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            fontSize = 11.sp,
            color = theme.textPrimary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun MetricBox(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    isPositive: Boolean? = null
) {
    val theme = LocalAppTheme.current
    val valueColor = when (isPositive) {
        true -> theme.accentGreen
        false -> theme.accentRed
        null -> theme.textPrimary
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = theme.surfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderSubtle.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = theme.textSecondary,
                fontSize = 10.sp
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun StrategyCustomizerDialog(
    strategy: StrategyDefinition,
    onDismiss: () -> Unit,
    onSave: (StrategyDefinition) -> Unit,
    onDuplicate: (String) -> Unit
) {
    val theme = LocalAppTheme.current
    var name by remember { mutableStateOf(if (strategy.isCustom) strategy.name else "${strategy.name} (Custom)") }
    var currentConfig by remember { mutableStateOf(strategy.indicatorConfig) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = if (strategy.isCustom) "Edit Strategy Parameters" else "Strategy Experiment Parameters",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = theme.textPrimary
                )
                Text(
                    text = if (strategy.isCustom) "Customized Strategy Definition" else "Creates a new custom strategy experiment",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textSecondary,
                    fontSize = 11.sp
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Strategy Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                when (strategy.strategyType) {
                    StrategyType.MA_CROSSOVER -> {
                        item {
                            var fast by remember { mutableStateOf(currentConfig.maParams.fastPeriod.toString()) }
                            OutlinedTextField(
                                value = fast,
                                onValueChange = {
                                    fast = it
                                    it.toIntOrNull()?.let { v ->
                                        currentConfig = currentConfig.copy(maParams = currentConfig.maParams.copy(fastPeriod = v))
                                    }
                                },
                                label = { Text("Fast MA Period") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                        item {
                            var slow by remember { mutableStateOf(currentConfig.maParams.slowPeriod.toString()) }
                            OutlinedTextField(
                                value = slow,
                                onValueChange = {
                                    slow = it
                                    it.toIntOrNull()?.let { v ->
                                        currentConfig = currentConfig.copy(maParams = currentConfig.maParams.copy(slowPeriod = v))
                                    }
                                },
                                label = { Text("Slow MA Period") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    }
                    StrategyType.RSI_MEAN_REVERSION -> {
                        item {
                            var period by remember { mutableStateOf(currentConfig.rsiParams.period.toString()) }
                            OutlinedTextField(
                                value = period,
                                onValueChange = {
                                    period = it
                                    it.toIntOrNull()?.let { v ->
                                        currentConfig = currentConfig.copy(rsiParams = currentConfig.rsiParams.copy(period = v))
                                    }
                                },
                                label = { Text("RSI Period") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                        item {
                            var oversold by remember { mutableStateOf(currentConfig.rsiParams.oversoldThreshold.toString()) }
                            OutlinedTextField(
                                value = oversold,
                                onValueChange = {
                                    oversold = it
                                    it.toDoubleOrNull()?.let { v ->
                                        currentConfig = currentConfig.copy(rsiParams = currentConfig.rsiParams.copy(oversoldThreshold = v))
                                    }
                                },
                                label = { Text("Oversold Threshold") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    }
                    StrategyType.BOLLINGER_BREAKOUT, StrategyType.BOLLINGER_REVERSION -> {
                        item {
                            var period by remember { mutableStateOf(currentConfig.bollingerParams.period.toString()) }
                            OutlinedTextField(
                                value = period,
                                onValueChange = {
                                    period = it
                                    it.toIntOrNull()?.let { v ->
                                        currentConfig = currentConfig.copy(bollingerParams = currentConfig.bollingerParams.copy(period = v))
                                    }
                                },
                                label = { Text("Bollinger Period") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                        item {
                            var stdDev by remember { mutableStateOf(currentConfig.bollingerParams.stdDevMultiplier.toString()) }
                            OutlinedTextField(
                                value = stdDev,
                                onValueChange = {
                                    stdDev = it
                                    it.toDoubleOrNull()?.let { v ->
                                        currentConfig = currentConfig.copy(bollingerParams = currentConfig.bollingerParams.copy(stdDevMultiplier = v))
                                    }
                                },
                                label = { Text("StdDev Multiplier") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    }
                    StrategyType.SUPERTREND_RUN -> {
                        item {
                            var period by remember { mutableStateOf(currentConfig.supertrendParams.atrPeriod.toString()) }
                            OutlinedTextField(
                                value = period,
                                onValueChange = {
                                    period = it
                                    it.toIntOrNull()?.let { v ->
                                        currentConfig = currentConfig.copy(supertrendParams = currentConfig.supertrendParams.copy(atrPeriod = v))
                                    }
                                },
                                label = { Text("ATR Period") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                        item {
                            var mult by remember { mutableStateOf(currentConfig.supertrendParams.multiplier.toString()) }
                            OutlinedTextField(
                                value = mult,
                                onValueChange = {
                                    mult = it
                                    it.toDoubleOrNull()?.let { v ->
                                        currentConfig = currentConfig.copy(supertrendParams = currentConfig.supertrendParams.copy(multiplier = v))
                                    }
                                },
                                label = { Text("Multiplier") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    }
                    else -> {
                        item {
                            Text(
                                text = "Strategy Type: ${strategy.strategyType.title}",
                                fontSize = 12.sp,
                                color = theme.textSecondary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = strategy.copy(
                        id = if (strategy.isCustom) strategy.id else "custom_${System.currentTimeMillis()}_${strategy.id}",
                        name = name,
                        indicatorConfig = currentConfig,
                        isCustom = true
                    )
                    onSave(updated)
                },
                colors = ButtonDefaults.buttonColors(containerColor = theme.brandPrimary)
            ) {
                Text("Save Experiment")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = theme.textSecondary)
            }
        }
    )
}

