package com.example.tradestrat.ui.screens

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
import com.example.tradestrat.data.db.SavedBacktestEntity
import com.example.tradestrat.engine.StrategyHealthScorecard
import com.example.tradestrat.engine.StrategyRecommendation
import com.example.tradestrat.model.StrategyDefinition
import com.example.tradestrat.ui.BacktestViewModel
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedStrategiesScreen(
    viewModel: BacktestViewModel,
    modifier: Modifier = Modifier,
    onNavigateToStudio: () -> Unit = {}
) {
    val savedStrategies by viewModel.savedStrategies.collectAsState()
    val savedBacktests by viewModel.savedBacktests.collectAsState()
    val healthScorecard by viewModel.healthScorecard.collectAsState()

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
                Column {
                    Text(
                        text = "PORTFOLIO INTELLIGENCE",
                        style = MaterialTheme.typography.labelSmall,
                        color = BentoLilac,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = "Strategy Health & History",
                        style = MaterialTheme.typography.titleLarge,
                        color = BentoTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Strategy Health Diagnostic Scorecard
            healthScorecard?.let { score ->
                item {
                    HealthScorecardCard(score)
                }
            }

            // Custom Strategies Section
            item {
                Text(
                    text = "SAVED STRATEGY TEMPLATES (${savedStrategies.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = BentoTextMuted,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            if (savedStrategies.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = BentoCardBg,
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BentoBorder))
                    ) {
                        Box(modifier = Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
                            Text("No custom strategies saved yet. Create and save one in the Strategy Architect!", color = BentoTextMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                items(savedStrategies) { strat ->
                    SavedStrategyCard(
                        strategy = strat,
                        onLoad = {
                            viewModel.setStrategy(strat)
                            onNavigateToStudio()
                        },
                        onDelete = { viewModel.deleteSavedStrategy(strat.id) }
                    )
                }
            }

            // Saved Backtest Runs Section
            item {
                Text(
                    text = "BACKTEST RUN HISTORY (${savedBacktests.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = BentoTextMuted,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            if (savedBacktests.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = BentoCardBg,
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BentoBorder))
                    ) {
                        Box(modifier = Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
                            Text("No simulation runs archived. Tap the bookmark icon on the Backtest Studio to record a run!", color = BentoTextMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                items(savedBacktests) { bt ->
                    SavedBacktestCard(
                        backtest = bt,
                        onDelete = { viewModel.deleteSavedBacktest(bt.id) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

@Composable
private fun HealthScorecardCard(score: StrategyHealthScorecard) {
    val scoreColor = when {
        score.overallScore >= 75 -> BentoGreen
        score.overallScore >= 55 -> BentoAmber
        else -> BentoRed
    }

    Card(
        modifier = Modifier.fillMaxWidth().testTag("health_scorecard_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = BentoCardBg),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BentoBorder))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("ALGORITHMIC HEALTH AUDIT", style = MaterialTheme.typography.labelSmall, color = BentoTextMuted, fontWeight = FontWeight.Bold)
                    Text(score.summaryVerdict, style = MaterialTheme.typography.titleMedium, color = BentoTextPrimary, fontWeight = FontWeight.Bold)
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = BentoLilacContainer
                ) {
                    Text(
                        text = "${score.overallScore}/100",
                        style = MaterialTheme.typography.titleLarge,
                        color = BentoLilacText,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }

            Divider(color = BentoBorder, thickness = 1.dp)

            // 4 Pillars
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PillarMetric("Risk/Reward", "${score.riskRewardScore}%")
                PillarMetric("Drawdown", "${score.drawdownScore}%")
                PillarMetric("Alpha Edge", "${score.consistencyScore}%")
                PillarMetric("Sample Size", "${score.statisticalSignificanceScore}%")
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text("AUDIT RECOMMENDATIONS", style = MaterialTheme.typography.labelSmall, color = BentoTextMuted, fontWeight = FontWeight.Bold)

            score.recommendations.forEach { rec ->
                RecommendationRow(rec)
            }
        }
    }
}

@Composable
private fun PillarMetric(title: String, score: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = BentoTextMuted, fontSize = 9.sp)
        Text(score, style = MaterialTheme.typography.bodyMedium, color = BentoTextPrimary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RecommendationRow(rec: StrategyRecommendation) {
    val icon = if (rec.isPositive) Icons.Default.CheckCircle else Icons.Default.Warning
    val tint = if (rec.isPositive) BentoGreen else BentoAmber

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = BentoCardElevated,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = "Rec", tint = tint, modifier = Modifier.size(16.dp))
            Column {
                Text(rec.title, style = MaterialTheme.typography.bodySmall, color = BentoTextPrimary, fontWeight = FontWeight.Bold)
                Text(rec.description, style = MaterialTheme.typography.labelSmall, color = BentoTextSecondary, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun SavedStrategyCard(
    strategy: StrategyDefinition,
    onLoad: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = BentoCardBg),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BentoBorder))
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).clickable { onLoad() }) {
                Text(strategy.name, style = MaterialTheme.typography.bodyMedium, color = BentoTextPrimary, fontWeight = FontWeight.Bold)
                Text(strategy.strategyType.title, style = MaterialTheme.typography.labelSmall, color = BentoLilac)
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onLoad) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Load", tint = BentoLilac)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = BentoRed)
                }
            }
        }
    }
}

@Composable
private fun SavedBacktestCard(
    backtest: SavedBacktestEntity,
    onDelete: () -> Unit
) {
    val isProfit = backtest.netProfitPercent >= 0
    val pColor = if (isProfit) BentoGreen else BentoRed

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = BentoCardBg),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BentoBorder))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("${backtest.assetSymbol} • ${backtest.strategyName}", style = MaterialTheme.typography.bodyMedium, color = BentoTextPrimary, fontWeight = FontWeight.Bold)
                    Text("${backtest.timeframe} • ${backtest.regimeName}", style = MaterialTheme.typography.labelSmall, color = BentoTextSecondary)
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${if (isProfit) "+" else ""}${String.format("%.2f%%", backtest.netProfitPercent)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = pColor,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Win: ${String.format("%.1f%%", backtest.winRatePercent)} | Sharpe: ${String.format("%.2f", backtest.sharpeRatio)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = BentoTextMuted,
                            fontSize = 10.sp
                        )
                    }

                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = BentoTextMuted, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
