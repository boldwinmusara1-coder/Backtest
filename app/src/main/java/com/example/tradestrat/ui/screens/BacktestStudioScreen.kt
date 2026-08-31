package com.example.tradestrat.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.tradestrat.data.MarketDataProvider
import com.example.tradestrat.model.*
import com.example.tradestrat.ui.BacktestViewModel
import com.example.tradestrat.ui.components.*
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BacktestStudioScreen(
    viewModel: BacktestViewModel,
    modifier: Modifier = Modifier,
    onNavigateToStrategyBuilder: () -> Unit = {},
    onNavigateToRiskManager: () -> Unit = {}
) {
    val selectedAsset by viewModel.selectedAsset.collectAsState()
    val selectedRegime by viewModel.selectedRegime.collectAsState()
    val selectedTimeframe by viewModel.selectedTimeframe.collectAsState()
    val selectedStrategy by viewModel.selectedStrategy.collectAsState()
    val riskParams by viewModel.riskParameters.collectAsState()
    val currentResult by viewModel.currentResult.collectAsState()
    val isBacktesting by viewModel.isBacktesting.collectAsState()
    val dataSourceInfo by viewModel.dataSourceInfo.collectAsState()
    val dataFetchError by viewModel.dataFetchError.collectAsState()
    val selectedProvider by viewModel.selectedProvider.collectAsState()
    val selectedDatePreset by viewModel.selectedDatePreset.collectAsState()

    var showAssetPicker by remember { mutableStateOf(false) }
    var showRegimePicker by remember { mutableStateOf(false) }
    var saveConfirmationSnackbar by remember { mutableStateOf(false) }

    val lastCandle = currentResult?.candles?.lastOrNull()
    val prevCandle = currentResult?.candles?.let { if (it.size >= 2) it[it.size - 2] else null }
    val priceChangePct = if (lastCandle != null && prevCandle != null && prevCandle.close > 0) {
        ((lastCandle.close - prevCandle.close) / prevCandle.close) * 100.0
    } else 0.0
    val isPositive = priceChangePct >= 0

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = TvBackground,
        snackbarHost = {
            if (saveConfirmationSnackbar) {
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = {
                        TextButton(onClick = { saveConfirmationSnackbar = false }) {
                            Text("OK", color = TvBlue)
                        }
                    },
                    containerColor = TvSurfaceElevated,
                    contentColor = TvTextPrimary
                ) {
                    Text("Trading setup archived to Library!")
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // TradingView Top Bar: Symbol Ticker, Live Price, 24h Change, Save Button
            item {
                Spacer(modifier = Modifier.height(2.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = TvSurface,
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(TvBorder))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Symbol Pill & Market Info
                        Row(
                            modifier = Modifier
                                .clickable { showAssetPicker = true }
                                .testTag("asset_picker_trigger"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = TvBlueContainer
                            ) {
                                Text(
                                    text = selectedAsset.category.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TvBlueText,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = selectedAsset.symbol,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TvTextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Symbol", tint = TvTextSecondary, modifier = Modifier.size(18.dp))
                                }
                                Text(
                                    text = selectedAsset.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TvTextSecondary,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        // Current Price & 24h Delta Pill
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "$${lastCandle?.close ?: selectedAsset.basePrice}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TvTextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (isPositive) TvGreen.copy(alpha = 0.15f) else TvRed.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = String.format("%+.2f%%", priceChangePct),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isPositive) TvGreen else TvRed,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    viewModel.saveCurrentBacktest()
                                    saveConfirmationSnackbar = true
                                },
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(TvSurfaceElevated, RoundedCornerShape(8.dp))
                                    .testTag("save_backtest_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BookmarkAdd,
                                    contentDescription = "Save Setup",
                                    tint = TvBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // TradingView Timeframe Bar (1m, 5m, 15m, 1h, 4h, 1D, 1W)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Timeframe.values().forEach { tf ->
                        val isSelected = selectedTimeframe == tf
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                                .clickable { viewModel.setTimeframe(tf) }
                                .testTag("timeframe_btn_${tf.name}"),
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) TvBlue else TvSurface,
                            border = if (isSelected) null else CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(TvBorder))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = tf.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else TvTextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // Market Data Pipeline & Real Historical Provider Card
            item {
                MarketDataConfigCard(
                    asset = selectedAsset,
                    timeframe = selectedTimeframe,
                    dataSourceInfo = dataSourceInfo,
                    isLoading = isBacktesting,
                    errorMessage = dataFetchError,
                    selectedProvider = selectedProvider,
                    selectedPreset = selectedDatePreset,
                    onProviderSelected = { prov -> viewModel.setProvider(prov) },
                    onPresetSelected = { preset -> viewModel.setDatePreset(preset) },
                    onRefresh = { viewModel.runBacktest() },
                    onApiKeyConfigured = { key -> viewModel.setApiKey(key) },
                    onCustomCsvImported = { csv ->
                        viewModel.setCustomCsvContent(csv)
                        viewModel.runBacktest()
                    }
                )
            }

            // Quick Strategy & Regime Selector Bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Active Strategy Pill
                    Surface(
                        modifier = Modifier
                            .weight(1.3f)
                            .height(40.dp)
                            .clickable { onNavigateToStrategyBuilder() }
                            .testTag("switch_strategy_link"),
                        shape = RoundedCornerShape(8.dp),
                        color = TvSurface,
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(TvBorder))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Tune, contentDescription = "Strategy", tint = TvBlue, modifier = Modifier.size(16.dp))
                                Text(
                                    text = selectedStrategy.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TvTextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = "Edit", tint = TvTextSecondary, modifier = Modifier.size(16.dp))
                        }
                    }

                    // Market Regime Pill
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clickable { showRegimePicker = true }
                            .testTag("regime_picker_trigger"),
                        shape = RoundedCornerShape(8.dp),
                        color = TvSurface,
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(TvBorder))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = selectedRegime.title.take(10),
                                style = MaterialTheme.typography.labelMedium,
                                color = TvTextSecondary,
                                maxLines = 1
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Regime", tint = TvTextSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Interactive Candlestick Financial Chart (TradingView Canvas)
            currentResult?.let { res ->
                item {
                    CandlestickChart(
                        candles = res.candles,
                        indicators = res.indicators,
                        signalMarkers = res.signalMarkers,
                        asset = selectedAsset,
                        timeframe = selectedTimeframe,
                        smcMetrics = res.smcMetrics,
                        aPlusVisualData = res.aPlusVisualData
                    )
                }
            }

            // Trading Strategy Threshold Configuration Card
            item {
                IndicatorThresholdConfigurationCard(
                    strategy = selectedStrategy,
                    onStrategyChanged = { updatedStrategy ->
                        viewModel.setStrategy(updatedStrategy)
                    },
                    onApplyAndRun = {
                        viewModel.runBacktest()
                    }
                )
            }

            // SMC / ICT Concepts Configuration & Audit Card
            item {
                SmcConceptConfigCard(
                    strategy = selectedStrategy,
                    onStrategyChanged = { updatedStrategy ->
                        viewModel.setStrategy(updatedStrategy)
                    },
                    smcMetrics = currentResult?.smcMetrics,
                    backtestMetrics = currentResult?.metrics,
                    onApplyAndRun = {
                        viewModel.runBacktest()
                    }
                )
            }

            // Performance Metrics Overview
            currentResult?.let { res ->
                item {
                    MetricsOverview(metrics = res.metrics)
                }

                // Equity Curve & Underwater Drawdown Chart
                item {
                    EquityCurveChart(
                        equityCurve = res.equityCurve,
                        initialCapital = res.metrics.initialCapital
                    )
                }

                // Profit & Loss Performance Trend
                item {
                    ProfitLossTrendChart(
                        equityCurve = res.equityCurve,
                        trades = res.trades,
                        initialCapital = res.metrics.initialCapital
                    )
                }

                // Strategy Health Scorecard
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = TvSurface,
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(TvBorder))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Shield, contentDescription = "Risk", tint = TvBlue, modifier = Modifier.size(18.dp))
                                    Text("Strategy Execution Matrix", style = MaterialTheme.typography.titleSmall, color = TvTextPrimary, fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    text = "Risk ${riskParams.positionSizeValue.toInt()}% • Lev ${String.format("%.1fx", riskParams.leverage)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TvTextSecondary
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("TOTAL TRADES", style = MaterialTheme.typography.labelSmall, color = TvTextSecondary, fontSize = 9.sp)
                                    Text("${res.metrics.totalTrades}", style = MaterialTheme.typography.titleMedium, color = TvTextPrimary, fontWeight = FontWeight.Bold)
                                }
                                Column {
                                    Text("WIN / LOSS", style = MaterialTheme.typography.labelSmall, color = TvTextSecondary, fontSize = 9.sp)
                                    Text("${res.metrics.winningTrades} / ${res.metrics.losingTrades}", style = MaterialTheme.typography.titleMedium, color = TvGreen, fontWeight = FontWeight.Bold)
                                }
                                Column {
                                    Text("PROFIT FACTOR", style = MaterialTheme.typography.labelSmall, color = TvTextSecondary, fontSize = 9.sp)
                                    Text(String.format("%.2f", res.metrics.profitFactor), style = MaterialTheme.typography.titleMedium, color = if (res.metrics.profitFactor >= 1.5) TvGreen else TvTextPrimary, fontWeight = FontWeight.Bold)
                                }
                                Column {
                                    Text("SHARPE", style = MaterialTheme.typography.labelSmall, color = TvTextSecondary, fontSize = 9.sp)
                                    Text(String.format("%.2f", res.metrics.sharpeRatio), style = MaterialTheme.typography.titleMedium, color = TvBlue, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Trade Execution Journal Component
                item {
                    TradeJournalComponent(trades = res.trades)
                }
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }

        // TradingView Instrument Search / Asset Selection Modal
        if (showAssetPicker) {
            var selectedCategoryFilter by remember { mutableStateOf<AssetCategory?>(null) }
            val filteredAssets = remember(selectedCategoryFilter) {
                if (selectedCategoryFilter == null) MarketDataProvider.ASSETS
                else MarketDataProvider.ASSETS.filter { it.category == selectedCategoryFilter }
            }

            AlertDialog(
                onDismissRequest = { showAssetPicker = false },
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = TvBlue)
                            Text("Symbol Search", style = MaterialTheme.typography.titleMedium, color = TvTextPrimary, fontWeight = FontWeight.Bold)
                        }
                        // Category Filter Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            FilterChip(
                                selected = selectedCategoryFilter == null,
                                onClick = { selectedCategoryFilter = null },
                                label = { Text("ALL", fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = TvBlue,
                                    selectedLabelColor = Color.White,
                                    containerColor = TvSurfaceElevated,
                                    labelColor = TvTextSecondary
                                ),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(28.dp)
                            )
                            AssetCategory.values().forEach { cat ->
                                FilterChip(
                                    selected = selectedCategoryFilter == cat,
                                    onClick = { selectedCategoryFilter = cat },
                                    label = { Text(cat.label.take(5), fontSize = 10.sp) },
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
                    }
                },
                text = {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredAssets) { asset ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setAsset(asset)
                                        showAssetPicker = false
                                    }
                                    .testTag("asset_option_${asset.id}"),
                                shape = RoundedCornerShape(8.dp),
                                color = if (selectedAsset.id == asset.id) TvSurfaceElevated else TvSurface
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(asset.symbol, fontWeight = FontWeight.Bold, color = if (selectedAsset.id == asset.id) TvBlue else TvTextPrimary)
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = TvBackground
                                            ) {
                                                Text(asset.category.name, fontSize = 8.sp, color = TvTextSecondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                            }
                                        }
                                        Text(asset.name, style = MaterialTheme.typography.labelSmall, color = TvTextSecondary, maxLines = 1)
                                    }
                                    Text(
                                        text = "$${asset.basePrice}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TvTextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAssetPicker = false }) {
                        Text("Close", color = TvTextSecondary)
                    }
                },
                shape = RoundedCornerShape(16.dp),
                containerColor = TvSurface
            )
        }

        // Market Regime Selection Modal Sheet
        if (showRegimePicker) {
            AlertDialog(
                onDismissRequest = { showRegimePicker = false },
                title = { Text("Market Regime", style = MaterialTheme.typography.titleMedium, color = TvTextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(MarketRegime.values()) { regime ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setRegime(regime)
                                        showRegimePicker = false
                                    }
                                    .testTag("regime_option_${regime.name}"),
                                shape = RoundedCornerShape(8.dp),
                                color = if (selectedRegime == regime) TvSurfaceElevated else TvSurface
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = regime.title,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedRegime == regime) TvBlue else TvTextPrimary
                                    )
                                    Text(
                                        text = regime.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TvTextSecondary
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showRegimePicker = false }) {
                        Text("Close", color = TvTextSecondary)
                    }
                },
                shape = RoundedCornerShape(16.dp),
                containerColor = TvSurface
            )
        }
    }
}
