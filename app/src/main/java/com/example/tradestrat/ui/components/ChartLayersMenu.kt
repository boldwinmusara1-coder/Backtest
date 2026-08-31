package com.example.tradestrat.ui.components

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
import com.example.ui.theme.LocalAppTheme

data class ChartLayersConfig(
    val showCandles: Boolean = true,
    val showVolume: Boolean = true,
    val showEntries: Boolean = true,
    val showExits: Boolean = true,
    val showBos: Boolean = true,
    val showChoch: Boolean = true,
    val showFvg: Boolean = true,
    val showOrderBlocks: Boolean = true,
    val showLiquidity: Boolean = true,
    val showSessions: Boolean = true,
    val showMovingAverages: Boolean = true,
    val showBollingerBands: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartLayersSheet(
    config: ChartLayersConfig,
    onConfigChange: (ChartLayersConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalAppTheme.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = theme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = theme.textMuted) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = "Layers",
                        tint = theme.brandPrimary
                    )
                    Text(
                        text = "Chart Layers & Overlays",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = theme.textPrimary
                    )
                }

                TextButton(
                    onClick = {
                        onConfigChange(ChartLayersConfig())
                    }
                ) {
                    Text("Reset All", fontSize = 12.sp, color = theme.brandPrimary)
                }
            }

            Text(
                text = "Toggle visibility of strategy signals, price action structures, and indicators on canvas.",
                style = MaterialTheme.typography.bodySmall,
                color = theme.textSecondary,
                fontSize = 12.sp
            )

            HorizontalDivider(color = theme.borderSubtle, thickness = 1.dp)

            // Layer Items
            LayerToggleItem(
                title = "Candlesticks",
                description = "Primary OHLC candle bars",
                checked = config.showCandles,
                onCheckedChange = { onConfigChange(config.copy(showCandles = it)) },
                theme = theme
            )

            LayerToggleItem(
                title = "Volume Sub-Pane",
                description = "Trading volume histogram at bottom of chart",
                checked = config.showVolume,
                onCheckedChange = { onConfigChange(config.copy(showVolume = it)) },
                theme = theme
            )

            LayerToggleItem(
                title = "Trade Entries & Exits",
                description = "Long/Short entry arrows, TP/SL target lines",
                checked = config.showEntries,
                onCheckedChange = { onConfigChange(config.copy(showEntries = it, showExits = it)) },
                theme = theme
            )

            LayerToggleItem(
                title = "Break of Structure (BOS)",
                description = "SMC institutional trend continuation lines",
                checked = config.showBos,
                onCheckedChange = { onConfigChange(config.copy(showBos = it)) },
                theme = theme
            )

            LayerToggleItem(
                title = "Change of Character (CHOCH / MSS)",
                description = "Market structure shift reversal levels",
                checked = config.showChoch,
                onCheckedChange = { onConfigChange(config.copy(showChoch = it)) },
                theme = theme
            )

            LayerToggleItem(
                title = "Fair Value Gaps (FVG)",
                description = "ICT 3-candle price imbalance imbalances & zones",
                checked = config.showFvg,
                onCheckedChange = { onConfigChange(config.copy(showFvg = it)) },
                theme = theme
            )

            LayerToggleItem(
                title = "Order Blocks & Breakers",
                description = "Institutional supply & demand zones",
                checked = config.showOrderBlocks,
                onCheckedChange = { onConfigChange(config.copy(showOrderBlocks = it)) },
                theme = theme
            )

            LayerToggleItem(
                title = "Liquidity Sweeps & EQH/EQL",
                description = "Equal highs/lows and stop run markers",
                checked = config.showLiquidity,
                onCheckedChange = { onConfigChange(config.copy(showLiquidity = it)) },
                theme = theme
            )

            LayerToggleItem(
                title = "Trading Sessions / Killzones",
                description = "Asia, London, New York session boundary highlights",
                checked = config.showSessions,
                onCheckedChange = { onConfigChange(config.copy(showSessions = it)) },
                theme = theme
            )
        }
    }
}

@Composable
private fun LayerToggleItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    theme: com.example.ui.theme.AppColors
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = theme.textPrimary,
                fontSize = 14.sp
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = theme.textSecondary,
                fontSize = 11.sp
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = theme.brandPrimary,
                uncheckedThumbColor = theme.textMuted,
                uncheckedTrackColor = theme.surfaceElevated
            )
        )
    }
}
