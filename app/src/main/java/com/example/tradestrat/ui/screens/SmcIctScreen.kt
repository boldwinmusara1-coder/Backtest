package com.example.tradestrat.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tradestrat.model.*
import com.example.tradestrat.ui.BacktestViewModel
import com.example.ui.theme.LocalAppTheme
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmcIctScreen(
    viewModel: BacktestViewModel,
    modifier: Modifier = Modifier,
    onNavigateToBacktest: () -> Unit = {}
) {
    val theme = LocalAppTheme.current
    val selectedStrategy by viewModel.selectedStrategy.collectAsState()
    val smcConfig = selectedStrategy.indicatorConfig.smcConfig

    var selectedTab by remember { mutableStateOf(0) } // 0: Concepts, 1: ICT Killzones, 2: Confluence Engine, 3: Zone Inspector
    val tabs = listOf("Concepts & Structure", "ICT Killzones", "Confluence Matrix", "Detected Zones")

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(theme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)
    ) {
        // Header
        item {
            Column(modifier = Modifier.padding(top = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "SMC / ICT Engine",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = theme.textPrimary,
                            fontSize = 24.sp
                        )
                        Text(
                            text = "Smart Money Concepts & Inner Circle Trader Setup",
                            style = MaterialTheme.typography.bodySmall,
                            color = theme.textSecondary,
                            fontSize = 12.sp
                        )
                    }

                    // Strategy Mode Badge
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = theme.brandPrimaryContainer
                    ) {
                        Text(
                            text = selectedStrategy.strategyType.name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.brandPrimaryText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // Mode Selector: SMC vs ICT vs Hybrid
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.border))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Strategy Framework Mode",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = theme.textPrimary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val modes = listOf(
                            Triple("Pure SMC", StrategyType.SMC_CONCEPTS, "BOS, CHoCH, OBs"),
                            Triple("Pure ICT", StrategyType.ICT_CONCEPTS, "FVG, Killzones, CE"),
                            Triple("SMC + ICT", StrategyType.SMC_ICT_CONCEPTS, "Confluence")
                        )

                        modes.forEach { (label, type, sub) ->
                            val isSelected = selectedStrategy.strategyType == type
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        viewModel.setStrategy(selectedStrategy.copy(strategyType = type))
                                    }
                                    .testTag("smc_mode_${type.name.lowercase()}"),
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) theme.brandPrimary else theme.surfaceElevated,
                                border = CardDefaults.outlinedCardBorder().copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(if (isSelected) theme.brandPrimary else theme.border)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        color = if (isSelected) Color.White else theme.textPrimary
                                    )
                                    Text(
                                        text = sub,
                                        fontSize = 9.sp,
                                        color = if (isSelected) Color.White.copy(alpha = 0.8f) else theme.textMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Sub Tabs Navigation
        item {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = theme.surface,
                contentColor = theme.brandPrimary,
                edgePadding = 0.dp,
                divider = {},
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = theme.brandPrimary,
                            height = 3.dp
                        )
                    }
                },
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                fontSize = 13.sp,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTab == index) theme.brandPrimary else theme.textSecondary
                            )
                        },
                        modifier = Modifier.testTag("smc_tab_$index")
                    )
                }
            }
        }

        // TAB 0: CONCEPTS & STRUCTURE
        if (selectedTab == 0) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.surface),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.border))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = "Market Structure Rules",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = theme.textPrimary
                        )

                        // BOS Toggle & Slider
                        ConceptToggleCard(
                            title = "Break of Structure (BOS)",
                            subtitle = "Identifies trend continuation upon breaking prior swing highs/lows",
                            enabled = smcConfig.useBos,
                            onToggle = { viewModel.updateSmcConfig(smcConfig.copy(useBos = it)) },
                            theme = theme
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("BOS Swing Lookback", fontSize = 12.sp, color = theme.textSecondary)
                                    Text("${smcConfig.bosLookback} bars", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.brandPrimary)
                                }
                                Slider(
                                    value = smcConfig.bosLookback.toFloat(),
                                    onValueChange = { viewModel.updateSmcConfig(smcConfig.copy(bosLookback = it.toInt())) },
                                    valueRange = 2f..20f,
                                    steps = 17,
                                    colors = SliderDefaults.colors(thumbColor = theme.brandPrimary, activeTrackColor = theme.brandPrimary)
                                )
                            }
                        }

                        // CHoCH Toggle
                        ConceptToggleCard(
                            title = "Change of Character (CHoCH / MSS)",
                            subtitle = "Early trend reversal signal when structural low/high violates previous swing",
                            enabled = smcConfig.useChoch,
                            onToggle = { viewModel.updateSmcConfig(smcConfig.copy(useChoch = it)) },
                            theme = theme
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("CHoCH Lookback", fontSize = 12.sp, color = theme.textSecondary)
                                    Text("${smcConfig.chochLookback} bars", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.brandPrimary)
                                }
                                Slider(
                                    value = smcConfig.chochLookback.toFloat(),
                                    onValueChange = { viewModel.updateSmcConfig(smcConfig.copy(chochLookback = it.toInt())) },
                                    valueRange = 2f..20f,
                                    steps = 17,
                                    colors = SliderDefaults.colors(thumbColor = theme.brandPrimary, activeTrackColor = theme.brandPrimary)
                                )
                            }
                        }

                        // Order Blocks
                        ConceptToggleCard(
                            title = "Order Blocks (OB)",
                            subtitle = "Institutional footprint candles preceding aggressive displacement",
                            enabled = smcConfig.useOrderBlock,
                            onToggle = { viewModel.updateSmcConfig(smcConfig.copy(useOrderBlock = it)) },
                            theme = theme
                        )

                        // Liquidity Sweeps
                        ConceptToggleCard(
                            title = "Liquidity Sweeps / Stop Hunts",
                            subtitle = "Detects wicks taking out swing points and snapping back into range",
                            enabled = smcConfig.useLiquiditySweep,
                            onToggle = { viewModel.updateSmcConfig(smcConfig.copy(useLiquiditySweep = it)) },
                            theme = theme
                        )
                    }
                }
            }
        }

        // TAB 1: ICT KILLZONES & FVG
        if (selectedTab == 1) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.surface),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.border))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = "ICT Specialized Mechanisms",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = theme.textPrimary
                        )

                        // Fair Value Gap (FVG)
                        ConceptToggleCard(
                            title = "Fair Value Gaps (FVG / Imbalances)",
                            subtitle = "3-bar inefficiency imbalances targeting gap fills or 50% CE",
                            enabled = smcConfig.useFvg,
                            onToggle = { viewModel.updateSmcConfig(smcConfig.copy(useFvg = it)) },
                            theme = theme
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("FVG Mitigation Trigger Rule:", fontSize = 12.sp, color = theme.textSecondary)
                                FvgMitigationType.values().forEach { mitigation ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.updateSmcConfig(smcConfig.copy(fvgMitigationType = mitigation)) }
                                            .padding(vertical = 4.dp)
                                    ) {
                                        RadioButton(
                                            selected = smcConfig.fvgMitigationType == mitigation,
                                            onClick = { viewModel.updateSmcConfig(smcConfig.copy(fvgMitigationType = mitigation)) },
                                            colors = RadioButtonDefaults.colors(selectedColor = theme.brandPrimary)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = mitigation.displayName, fontSize = 12.sp, color = theme.textPrimary)
                                    }
                                }
                            }
                        }

                        // Session Filter / Killzones
                        ConceptToggleCard(
                            title = "Time-Based Killzones & Session Filter",
                            subtitle = "Restrict trade execution exclusively to high-liquidity session windows",
                            enabled = smcConfig.useSessionFilter,
                            onToggle = { viewModel.updateSmcConfig(smcConfig.copy(useSessionFilter = it)) },
                            theme = theme
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                SmcSessionType.values().forEach { session ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.updateSmcConfig(smcConfig.copy(sessionType = session)) }
                                            .padding(vertical = 4.dp)
                                    ) {
                                        RadioButton(
                                            selected = smcConfig.sessionType == session,
                                            onClick = { viewModel.updateSmcConfig(smcConfig.copy(sessionType = session)) },
                                            colors = RadioButtonDefaults.colors(selectedColor = theme.brandPrimary)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = session.displayName, fontSize = 12.sp, color = theme.textPrimary)
                                    }
                                }
                            }
                        }

                        // Premium vs Discount Zone
                        ConceptToggleCard(
                            title = "Premium & Discount Equilibrium Filter",
                            subtitle = "Only buys in Discount (<50%) and sells in Premium (>50%)",
                            enabled = smcConfig.usePremiumDiscount,
                            onToggle = { viewModel.updateSmcConfig(smcConfig.copy(usePremiumDiscount = it)) },
                            theme = theme
                        )
                    }
                }
            }
        }

        // TAB 2: CONFLUENCE MATRIX
        if (selectedTab == 2) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.surface),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.border))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = "Multi-Factor Confluence Engine",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = theme.textPrimary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Require Multi-Factor Confluence", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = theme.textPrimary)
                                Text("Trades are only opened when multiple independent SMC/ICT signals align simultaneously", fontSize = 11.sp, color = theme.textSecondary)
                            }
                            Switch(
                                checked = smcConfig.requireConfluence,
                                onCheckedChange = { viewModel.updateSmcConfig(smcConfig.copy(requireConfluence = it)) },
                                colors = SwitchDefaults.colors(checkedThumbColor = theme.brandPrimary, checkedTrackColor = theme.brandPrimaryContainer)
                            )
                        }

                        if (smcConfig.requireConfluence) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Minimum Confluent Factors", fontSize = 12.sp, color = theme.textSecondary)
                                    Text("${smcConfig.minConfluences} Factors", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.brandPrimary)
                                }
                                Slider(
                                    value = smcConfig.minConfluences.toFloat(),
                                    onValueChange = { viewModel.updateSmcConfig(smcConfig.copy(minConfluences = it.toInt())) },
                                    valueRange = 1f..5f,
                                    steps = 3,
                                    colors = SliderDefaults.colors(thumbColor = theme.brandPrimary, activeTrackColor = theme.brandPrimary)
                                )
                            }
                        }

                        Divider(color = theme.borderSubtle)

                        Text("Trade Direction Mode", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = theme.textPrimary)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SmcTradeDirection.values().forEach { dir ->
                                val isSelected = smcConfig.tradeDirection == dir
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { viewModel.updateSmcConfig(smcConfig.copy(tradeDirection = dir)) },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) theme.brandPrimary else theme.surfaceElevated
                                ) {
                                    Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = dir.displayName,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) Color.White else theme.textPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // TAB 3: DETECTED ZONES
        if (selectedTab == 3) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.surface),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.border))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Detected Structural Zones & Inefficiencies",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = theme.textPrimary
                        )

                        Text(
                            text = "Real-time SMC/ICT zones identified by the engine on the selected asset and timeframe.",
                            fontSize = 12.sp,
                            color = theme.textSecondary
                        )

                        listOf(
                            Triple("Bullish Order Block (OB)", "$64,250.00 - $64,800.00", true),
                            Triple("Fair Value Gap (FVG)", "$66,100.00 - $66,450.00", false),
                            Triple("Discount Equilibrium Zone", "$63,000.00 - $65,500.00", true),
                            Triple("Equal Highs Liquidity Pool", "$68,900.00", false)
                        ).forEach { (label, range, isBullish) ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = theme.surfaceElevated
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(text = label, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = theme.textPrimary)
                                        Text(text = range, fontSize = 11.sp, color = theme.textSecondary)
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isBullish) theme.tradeGreenContainer else theme.tradeRedContainer
                                    ) {
                                        Text(
                                            text = if (isBullish) "DEMAND / LONG" else "SUPPLY / SHORT",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isBullish) theme.tradeGreenText else theme.tradeRedText,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Action Button: Simulate SMC/ICT
        item {
            Button(
                onClick = onNavigateToBacktest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("run_smc_simulation_btn"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = theme.brandPrimary)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Simulate SMC/ICT Strategy", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun ConceptToggleCard(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    theme: com.example.ui.theme.AppThemeColors,
    content: (@Composable () -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = theme.surfaceElevated,
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(if (enabled) theme.brandPrimary.copy(alpha = 0.5f) else theme.border)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = theme.textPrimary)
                    Text(text = subtitle, fontSize = 11.sp, color = theme.textSecondary)
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = theme.brandPrimary,
                        checkedTrackColor = theme.brandPrimaryContainer
                    )
                )
            }

            if (enabled && content != null) {
                Divider(color = theme.borderSubtle, thickness = 1.dp)
                content()
            }
        }
    }
}
