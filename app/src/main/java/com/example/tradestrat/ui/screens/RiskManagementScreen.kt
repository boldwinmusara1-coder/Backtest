package com.example.tradestrat.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.example.tradestrat.model.*
import com.example.tradestrat.ui.BacktestViewModel
import com.example.tradestrat.ui.components.*
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiskManagementScreen(
    viewModel: BacktestViewModel,
    modifier: Modifier = Modifier,
    onBacktestNow: () -> Unit = {}
) {
    val riskParams by viewModel.riskParameters.collectAsState()
    var currentRisk by remember(riskParams) { mutableStateOf(riskParams) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BentoBackground
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "RISK CONTROL DESK",
                            style = MaterialTheme.typography.labelSmall,
                            color = BentoLilac,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = "Capital & Sizing Parameters",
                            style = MaterialTheme.typography.titleLarge,
                            color = BentoTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = BentoLilacContainer
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Shield",
                            tint = BentoLilacText,
                            modifier = Modifier.padding(10.dp).size(20.dp)
                        )
                    }
                }
            }

            // Live Risk & Sizing Calculator Widget (Bento Hero Card)
            item {
                RiskCalculatorCard(currentRisk)
            }

            // Capital Allocation & Position Sizing Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = BentoCardBg),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BentoBorder))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "CAPITAL ALLOCATION & SIZING",
                            style = MaterialTheme.typography.labelSmall,
                            color = BentoTextMuted,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        DoubleParameterSlider(
                            title = "Starting Account Capital",
                            value = currentRisk.initialCapital,
                            valueRange = 1000f..100000f,
                            displayFormatter = { "$${String.format("%,.0f", it)}" },
                            onValueChange = { newVal ->
                                val updated = currentRisk.copy(initialCapital = newVal)
                                currentRisk = updated
                                viewModel.updateRiskParameters(updated)
                            }
                        )

                        Text("Position Sizing Model", style = MaterialTheme.typography.bodySmall, color = BentoTextSecondary)

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            PositionSizingMode.values().forEach { mode ->
                                val isSelected = currentRisk.positionSizingMode == mode
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val updated = currentRisk.copy(positionSizingMode = mode)
                                            currentRisk = updated
                                            viewModel.updateRiskParameters(updated)
                                        }
                                        .testTag("sizing_mode_${mode.name}"),
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isSelected) BentoLilacContainer else BentoCardElevated
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(mode.displayName, style = MaterialTheme.typography.bodySmall, color = if (isSelected) BentoLilacText else BentoTextPrimary, fontWeight = FontWeight.Bold)
                                            Text(mode.description, style = MaterialTheme.typography.labelSmall, color = BentoTextSecondary, fontSize = 10.sp)
                                        }
                                        if (isSelected) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = "Active", tint = BentoLilac, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }

                        DoubleParameterSlider(
                            title = "Position Size Allocation",
                            value = currentRisk.positionSizeValue,
                            valueRange = 1f..100f,
                            displayFormatter = { "${it.toInt()}%" },
                            onValueChange = { newVal ->
                                val updated = currentRisk.copy(positionSizeValue = newVal)
                                currentRisk = updated
                                viewModel.updateRiskParameters(updated)
                            }
                        )

                        DoubleParameterSlider(
                            title = "Leverage Multiplier",
                            value = currentRisk.leverage,
                            valueRange = 1.0f..10.0f,
                            displayFormatter = { String.format("%.1fx", it) },
                            onValueChange = { newVal ->
                                val updated = currentRisk.copy(leverage = newVal)
                                currentRisk = updated
                                viewModel.updateRiskParameters(updated)
                            }
                        )
                    }
                }
            }

            // Stop Loss & Take Profit Bento Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = BentoCardBg),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BentoBorder))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "STOP LOSS & PROFIT TARGETS",
                            style = MaterialTheme.typography.labelSmall,
                            color = BentoTextMuted,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        Text("Stop Loss Order Type", style = MaterialTheme.typography.bodySmall, color = BentoTextSecondary)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StopLossType.values().forEach { st ->
                                FilterChip(
                                    selected = currentRisk.stopLossType == st,
                                    onClick = {
                                        val updated = currentRisk.copy(stopLossType = st)
                                        currentRisk = updated
                                        viewModel.updateRiskParameters(updated)
                                    },
                                    label = { Text(st.displayName.take(10), fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = BentoLilacContainer,
                                        selectedLabelColor = BentoLilacText,
                                        containerColor = BentoCardElevated,
                                        labelColor = BentoTextSecondary
                                    ),
                                    shape = CircleShape,
                                    modifier = Modifier.weight(1f).height(32.dp)
                                )
                            }
                        }

                        if (currentRisk.stopLossType != StopLossType.NONE) {
                            DoubleParameterSlider(
                                title = "Stop Loss Distance",
                                value = currentRisk.stopLossValue,
                                valueRange = 0.5f..15.0f,
                                displayFormatter = { String.format("%.1f%%", it) },
                                onValueChange = { newVal ->
                                    val updated = currentRisk.copy(stopLossValue = newVal)
                                    currentRisk = updated
                                    viewModel.updateRiskParameters(updated)
                                }
                            )
                        }

                        Divider(color = BentoBorder, thickness = 1.dp)

                        Text("Take Profit Target", style = MaterialTheme.typography.bodySmall, color = BentoTextSecondary)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TakeProfitType.values().forEach { tp ->
                                FilterChip(
                                    selected = currentRisk.takeProfitType == tp,
                                    onClick = {
                                        val updated = currentRisk.copy(takeProfitType = tp)
                                        currentRisk = updated
                                        viewModel.updateRiskParameters(updated)
                                    },
                                    label = { Text(tp.displayName.take(10), fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = BentoLilacContainer,
                                        selectedLabelColor = BentoLilacText,
                                        containerColor = BentoCardElevated,
                                        labelColor = BentoTextSecondary
                                    ),
                                    shape = CircleShape,
                                    modifier = Modifier.weight(1f).height(32.dp)
                                )
                            }
                        }

                        if (currentRisk.takeProfitType != TakeProfitType.NONE) {
                            DoubleParameterSlider(
                                title = "Take Profit Target",
                                value = currentRisk.takeProfitValue,
                                valueRange = 1.0f..30.0f,
                                displayFormatter = { String.format("%.1f", it) },
                                onValueChange = { newVal ->
                                    val updated = currentRisk.copy(takeProfitValue = newVal)
                                    currentRisk = updated
                                    viewModel.updateRiskParameters(updated)
                                }
                            )
                        }
                    }
                }
            }

            // Market Friction & Execution Realism
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = BentoCardBg),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BentoBorder))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "MARKET FRICTION & REALISM",
                            style = MaterialTheme.typography.labelSmall,
                            color = BentoTextMuted,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        DoubleParameterSlider(
                            title = "Broker Commission Fee",
                            value = currentRisk.commissionBps,
                            valueRange = 0.0f..50.0f,
                            displayFormatter = { "${it.toInt()} bps (${String.format("%.2f%%", it / 100.0)})" },
                            onValueChange = { newVal ->
                                val updated = currentRisk.copy(commissionBps = newVal)
                                currentRisk = updated
                                viewModel.updateRiskParameters(updated)
                            }
                        )

                        DoubleParameterSlider(
                            title = "Order Fill Slippage",
                            value = currentRisk.slippageBps,
                            valueRange = 0.0f..50.0f,
                            displayFormatter = { "${it.toInt()} bps (${String.format("%.2f%%", it / 100.0)})" },
                            onValueChange = { newVal ->
                                val updated = currentRisk.copy(slippageBps = newVal)
                                currentRisk = updated
                                viewModel.updateRiskParameters(updated)
                            }
                        )

                        Text("Order Execution Model (Look-Ahead Bias Elimination)", style = MaterialTheme.typography.bodySmall, color = BentoTextSecondary)

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ExecutionModel.values().forEach { model ->
                                val isSelected = currentRisk.executionModel == model
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val updated = currentRisk.copy(executionModel = model)
                                            currentRisk = updated
                                            viewModel.updateRiskParameters(updated)
                                        }
                                        .testTag("exec_model_${model.name}"),
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) BentoLilacContainer else BentoCardElevated,
                                    border = if (isSelected) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BentoLilac)) else null
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = model.label,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) BentoLilacText else BentoTextPrimary,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                                if (model == ExecutionModel.REALISTIC) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = BentoGreenContainer
                                                    ) {
                                                        Text(
                                                            text = "RECOMMENDED",
                                                            color = BentoGreenText,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Selected",
                                                    tint = BentoLilacText,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = model.description,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = BentoTextMuted,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }

                        Text("Intrabar Execution Assumption (SL vs TP Conflict)", style = MaterialTheme.typography.bodySmall, color = BentoTextSecondary)

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            IntrabarExecutionAssumption.values().forEach { assumption ->
                                val isSelected = currentRisk.intrabarExecution == assumption
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val updated = currentRisk.copy(intrabarExecution = assumption)
                                            currentRisk = updated
                                            viewModel.updateRiskParameters(updated)
                                        }
                                        .testTag("intrabar_exec_${assumption.name}"),
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) BentoLilacContainer else BentoCardElevated,
                                    border = if (isSelected) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BentoLilac)) else null
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = assumption.label,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) BentoLilacText else BentoTextPrimary,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Selected",
                                                    tint = BentoLilacText,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = assumption.description,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = BentoTextMuted,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Allow Short Selling Positions", style = MaterialTheme.typography.bodyMedium, color = BentoTextPrimary)
                            Switch(
                                checked = currentRisk.allowShorting,
                                onCheckedChange = { isShortAllowed ->
                                    val updated = currentRisk.copy(allowShorting = isShortAllowed)
                                    currentRisk = updated
                                    viewModel.updateRiskParameters(updated)
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = BentoLilac, checkedTrackColor = BentoLilacContainer)
                            )
                        }
                    }
                }
            }

            // Apply & Run CTA
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        viewModel.updateRiskParameters(currentRisk)
                        onBacktestNow()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("apply_risk_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BentoLilac, contentColor = BentoLilacContainer)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Run")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("APPLY RISK & RUN IN LAB", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

@Composable
private fun RiskCalculatorCard(risk: RiskParameters) {
    val estimatedMaxDollarRiskPerTrade = (risk.initialCapital * (risk.positionSizeValue / 100.0)) * (risk.stopLossValue / 100.0) * risk.leverage

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = BentoCardBg),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BentoBorder))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("RISK EXPOSURE CALCULATOR", style = MaterialTheme.typography.labelSmall, color = BentoLilac, fontWeight = FontWeight.Bold)
                Surface(
                    shape = CircleShape,
                    color = BentoLilacContainer
                ) {
                    Text(
                        text = "${String.format("%.1fx", risk.leverage)} LEV",
                        style = MaterialTheme.typography.labelSmall,
                        color = BentoLilacText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Est. Max $ Loss / Trade", style = MaterialTheme.typography.labelSmall, color = BentoTextMuted, fontSize = 10.sp)
                    Text("-$${String.format("%,.2f", estimatedMaxDollarRiskPerTrade)}", style = MaterialTheme.typography.titleMedium, color = BentoRed, fontWeight = FontWeight.Bold)
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("Max Capital Committed", style = MaterialTheme.typography.labelSmall, color = BentoTextMuted, fontSize = 10.sp)
                    val committed = (risk.initialCapital * (risk.positionSizeValue / 100.0)) * risk.leverage
                    Text("$${String.format("%,.0f", committed)}", style = MaterialTheme.typography.titleMedium, color = BentoTextPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
