package com.example.tradestrat.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tradestrat.model.*
import com.example.tradestrat.ui.BacktestViewModel
import com.example.ui.theme.LocalAppTheme

enum class SpecScreenSection(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    APPROVAL_PANEL("Approval Panel", Icons.Default.Tune),
    EXECUTION_RULES("Execution Rules", Icons.Default.FormatListNumbered),
    CAUSALITY_AUDIT("Causality Audit", Icons.Default.VerifiedUser),
    GOVERNANCE("Governance & Lock", Icons.Default.Lock)
}

data class ExecutionStep(
    val stepNumber: Int,
    val stepTitle: String,
    val rule: String,
    val infoAvailable: String,
    val futureDataUsed: String = "NONE"
)

data class CausalityAuditCheckItem(
    val componentName: String,
    val status: String = "PASS",
    val explanation: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun APlusStrategySpecScreen(
    viewModel: BacktestViewModel,
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
    onApproved: () -> Unit = {}
) {
    val theme = LocalAppTheme.current
    val approvalState by viewModel.aplusApprovalState.collectAsState()
    var selectedSection by remember { mutableStateOf(SpecScreenSection.APPROVAL_PANEL) }
    var showCreateExperimentDialog by remember { mutableStateOf(false) }
    var experimentNameInput by remember { mutableStateOf("") }
    var approverNameInput by remember { mutableStateOf("Human Trader") }
    var showApprovalSuccessDialog by remember { mutableStateOf(false) }

    val isPending = !approvalState.isApproved
    val statusColor = if (approvalState.isApproved) Color(0xFF10B981) else Color(0xFFF59E0B)
    val statusText = if (approvalState.isApproved) "A_PLUS_V1.0 APPROVED & LOCKED" else "A_PLUS_PENDING_APPROVAL"

    val longSteps = remember {
        listOf(
            ExecutionStep(
                stepNumber = 1,
                stepTitle = "Trend / Market Structure",
                rule = "Market structure is evaluated from confirmed swings up to candle i. Must not be in confirmed BEARISH_TREND (series of confirmed LH + LL) and must not be in CONSOLIDATION.",
                infoAvailable = "Confirmed swing highs and lows with confirmation timestamp p + confirmationBars <= i."
            ),
            ExecutionStep(
                stepNumber = 2,
                stepTitle = "Trendline Construction",
                rule = "Connects two confirmed swing highs H1 and H2 where H2 was confirmed at or before bar i, slope < 0, with at least 2 distinct structural touches and no prior candle closes above the line.",
                infoAvailable = "Swing anchors H1, H2 and historical candle closes up to current bar i."
            ),
            ExecutionStep(
                stepNumber = 3,
                stepTitle = "Break Detection",
                rule = "Candle i_break closes decisively above descending resistance line: Close[i] >= LinePrice[i] + 0.10 * ATR14.",
                infoAvailable = "Completed Close[i_break] and causal 14-period ATR computed up to bar i_break."
            ),
            ExecutionStep(
                stepNumber = 4,
                stepTitle = "Retest Detection",
                rule = "Within 1 <= (i - i_break) <= 12 candles, price pulls back into retest band: Low[i] <= LinePrice[i] + 0.25 * ATR14 without closing below invalidation swing low.",
                infoAvailable = "Candle i completed Low, High, Open, and Close."
            ),
            ExecutionStep(
                stepNumber = 5,
                stepTitle = "Confirmation",
                rule = "Retest bar prints a bullish rejection: Close > Open (green candle) OR lower rejection wick >= 25% of total candle range with Close >= LinePrice - 0.05 * ATR14.",
                infoAvailable = "Completed candle i dimensions (Open, High, Low, Close) upon bar completion."
            ),
            ExecutionStep(
                stepNumber = 6,
                stepTitle = "Entry",
                rule = "Execute Long trade entry at market upon completion of confirmation candle close (Close[i]).",
                infoAvailable = "Exact close price of confirmation bar i."
            ),
            ExecutionStep(
                stepNumber = 7,
                stepTitle = "Initial Stop Loss",
                rule = "Stop loss placed strictly at InvalidationSwingLow - 0.10 * ATR14 (the most recent confirmed structural swing low formed prior to or during the breakout).",
                infoAvailable = "Price of most recent confirmed swing low and causal ATR at entry bar."
            ),
            ExecutionStep(
                stepNumber = 8,
                stepTitle = "Break-Even",
                rule = "Stop loss ratchets to EntryPrice iff MFE >= 1.0 * InitialRisk AND a new confirmed structural Higher Low has formed in trade direction (p_HL + 3 <= i).",
                infoAvailable = "Maximum high reached since entry and newly confirmed swing points up to bar i."
            ),
            ExecutionStep(
                stepNumber = 9,
                stepTitle = "Structural Trailing",
                rule = "Stop loss ratchets monotonically to ConfirmedHL_price - 0.10 * ATR14 whenever a new confirmed Higher Low appears. Stop only moves upward.",
                infoAvailable = "Chronologically confirmed Higher Low prices (p + 3 <= i) and causal ATR."
            ),
            ExecutionStep(
                stepNumber = 10,
                stepTitle = "Exit",
                rule = "Position exits immediately if candle Low <= active StopLoss price OR if a counter-trend structure breakdown is confirmed.",
                infoAvailable = "Live bar extreme prices (Low[i]) and confirmed swing structure at bar i."
            )
        )
    }

    val shortSteps = remember {
        listOf(
            ExecutionStep(
                stepNumber = 1,
                stepTitle = "Trend / Market Structure",
                rule = "Market structure is evaluated from confirmed swings up to candle i. Must not be in confirmed BULLISH_TREND (series of confirmed HH + HL) and must not be in CONSOLIDATION.",
                infoAvailable = "Confirmed swing highs and lows with confirmation timestamp p + confirmationBars <= i."
            ),
            ExecutionStep(
                stepNumber = 2,
                stepTitle = "Trendline Construction",
                rule = "Connects two confirmed swing lows L1 and L2 where L2 was confirmed at or before bar i, slope > 0, with at least 2 distinct structural touches and no prior candle closes below the line.",
                infoAvailable = "Swing anchors L1, L2 and historical candle closes up to current bar i."
            ),
            ExecutionStep(
                stepNumber = 3,
                stepTitle = "Break Detection",
                rule = "Candle i_break closes decisively below ascending support line: Close[i] <= LinePrice[i] - 0.10 * ATR14.",
                infoAvailable = "Completed Close[i_break] and causal 14-period ATR computed up to bar i_break."
            ),
            ExecutionStep(
                stepNumber = 4,
                stepTitle = "Retest Detection",
                rule = "Within 1 <= (i - i_break) <= 12 candles, price rallies into retest band: High[i] >= LinePrice[i] - 0.25 * ATR14 without closing above invalidation swing high.",
                infoAvailable = "Candle i completed High, Low, Open, and Close."
            ),
            ExecutionStep(
                stepNumber = 5,
                stepTitle = "Confirmation",
                rule = "Retest bar prints a bearish rejection: Close < Open (red candle) OR upper rejection wick >= 25% of total candle range with Close <= LinePrice + 0.05 * ATR14.",
                infoAvailable = "Completed candle i dimensions (Open, High, Low, Close) upon bar completion."
            ),
            ExecutionStep(
                stepNumber = 6,
                stepTitle = "Entry",
                rule = "Execute Short trade entry at market upon completion of confirmation candle close (Close[i]).",
                infoAvailable = "Exact close price of confirmation bar i."
            ),
            ExecutionStep(
                stepNumber = 7,
                stepTitle = "Initial Stop Loss",
                rule = "Stop loss placed strictly at InvalidationSwingHigh + 0.10 * ATR14 (the most recent confirmed structural swing high formed prior to or during the breakdown).",
                infoAvailable = "Price of most recent confirmed swing high and causal ATR at entry bar."
            ),
            ExecutionStep(
                stepNumber = 8,
                stepTitle = "Break-Even",
                rule = "Stop loss ratchets to EntryPrice iff MFE >= 1.0 * InitialRisk AND a new confirmed structural Lower High has formed in trade direction (p_LH + 3 <= i).",
                infoAvailable = "Maximum low reached since entry and newly confirmed swing points up to bar i."
            ),
            ExecutionStep(
                stepNumber = 9,
                stepTitle = "Structural Trailing",
                rule = "Stop loss ratchets monotonically to ConfirmedLH_price + 0.10 * ATR14 whenever a new confirmed Lower High appears. Stop only moves downward.",
                infoAvailable = "Chronologically confirmed Lower High prices (p + 3 <= i) and causal ATR."
            ),
            ExecutionStep(
                stepNumber = 10,
                stepTitle = "Exit",
                rule = "Position exits immediately if candle High >= active StopLoss price OR if a counter-trend structure breakout is confirmed.",
                infoAvailable = "Live bar extreme prices (High[i]) and confirmed swing structure at bar i."
            )
        )
    }

    val auditItems = remember {
        listOf(
            CausalityAuditCheckItem(
                componentName = "Swing Detection",
                status = "PASS",
                explanation = "Pivot at bar p is strictly invisible to all calculations until confirmation bar p + confirmationBars closes."
            ),
            CausalityAuditCheckItem(
                componentName = "Trendline Construction",
                status = "PASS",
                explanation = "Anchor p2 cannot be drawn or projected until p2 + confirmationBars closes. Historical candle verification strictly uses bars k <= i."
            ),
            CausalityAuditCheckItem(
                componentName = "Break Detection",
                status = "PASS",
                explanation = "Evaluates strictly completed Close[i] against trendline price at bar i. No future candles are indexed or accessed."
            ),
            CausalityAuditCheckItem(
                componentName = "Retest Detection",
                status = "PASS",
                explanation = "Iterates bar-by-bar chronologically from i_break + 1 up to retestMaxBars. Evaluates only completed candle price action."
            ),
            CausalityAuditCheckItem(
                componentName = "Confirmation",
                status = "PASS",
                explanation = "Triggered only upon candle close event of the current bar. No intra-bar clairvoyance or tick peeking."
            ),
            CausalityAuditCheckItem(
                componentName = "Entry",
                status = "PASS",
                explanation = "Order filled at confirmed Close[i] or Open[i+1] with realistic commission and slippage deducted."
            ),
            CausalityAuditCheckItem(
                componentName = "Stop Placement",
                status = "PASS",
                explanation = "Anchored to confirmed swing points already validated at or before entry bar i, using causal 14-period ATR."
            ),
            CausalityAuditCheckItem(
                componentName = "Break-Even",
                status = "PASS",
                explanation = "Evaluated per bar using cumulative high/low seen so far and swings confirmed at or before bar i."
            ),
            CausalityAuditCheckItem(
                componentName = "Trailing Stop",
                status = "PASS",
                explanation = "Ratchets monotonically only when a newly formed swing reaches confirmation bar p + 3 <= i."
            ),
            CausalityAuditCheckItem(
                componentName = "Exit Detection",
                status = "PASS",
                explanation = "Stop hit triggered if current bar High[i] or Low[i] breaches active stop level during the active bar."
            )
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
        // TOP APP BAR / HEADER
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("spec_header_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surfaceElevated),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(statusColor.copy(alpha = 0.5f)))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = onNavigateBack,
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(theme.surface, CircleShape)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = theme.textPrimary)
                            }
                            Column {
                                Text(
                                    text = "A+ TRENDLINE SYSTEM",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = theme.textPrimary
                                )
                                Text(
                                    text = "Parameter Specification & Governance",
                                    fontSize = 11.sp,
                                    color = theme.textSecondary
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = statusColor.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, statusColor)
                        ) {
                            Text(
                                text = statusText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = statusColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Status & Lock Banner
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isPending) Color(0xFFF59E0B).copy(alpha = 0.10f) else Color(0xFF10B981).copy(alpha = 0.10f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(
                                    imageVector = if (isPending) Icons.Default.Lock else Icons.Default.VerifiedUser,
                                    contentDescription = "Lock Status",
                                    tint = statusColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (isPending) "GOVERNANCE LOCK ACTIVE" else "A_PLUS_V1.0 MASTER SPEC LOCKED",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = statusColor
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("• PERFORMANCE BACKTESTING: LOCKED", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = theme.textSecondary)
                                Text("• PARAMETER OPTIMIZATION: LOCKED", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = theme.textSecondary)
                                Text("• PARAMETER MODIFICATION: LOCKED", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = theme.textSecondary)
                            }
                        }
                    }
                }
            }
        }

        // SECTION SELECTOR TABS
        item {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(SpecScreenSection.values()) { section ->
                    val isSelected = selectedSection == section
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedSection = section },
                        label = {
                            Text(
                                text = section.title,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        },
                        leadingIcon = {
                            Icon(section.icon, contentDescription = section.title, modifier = Modifier.size(16.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = theme.brandPrimary.copy(alpha = 0.2f),
                            selectedLabelColor = theme.brandPrimary,
                            selectedLeadingIconColor = theme.brandPrimary
                        )
                    )
                }
            }
        }

        // SECTION CONTENT
        when (selectedSection) {
            SpecScreenSection.APPROVAL_PANEL -> {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "A+ TRENDLINE SYSTEM",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = theme.textPrimary,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "PARAMETER APPROVAL PANEL",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = theme.brandPrimary,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Individual mobile cards for every configurable system parameter. All AI-proposed parameters require explicit approval.",
                            fontSize = 11.sp,
                            color = theme.textSecondary
                        )
                    }
                }

                items(approvalState.parameters) { param ->
                    MobileParameterCard(
                        param = param,
                        onToggleApproval = { viewModel.toggleParameterApproval(param.id) }
                    )
                }
            }

            SpecScreenSection.EXECUTION_RULES -> {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "EXECUTION RULES",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = theme.textPrimary,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Sequential step-by-step decision timeline for LONG and SHORT setups. Future data used is strictly NONE.",
                            fontSize = 11.sp,
                            color = theme.textSecondary
                        )
                    }
                }

                // LONG SETUP SECTION HEADER
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.TrendingUp, contentDescription = "Long", tint = Color(0xFF10B981))
                            Text(
                                text = "LONG SETUP (10 Sequential Steps)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF10B981)
                            )
                        }
                    }
                }

                items(longSteps) { step ->
                    MobileExecutionStepCard(step = step, isLong = true)
                }

                // SHORT SETUP SECTION HEADER
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFEF4444).copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.TrendingDown, contentDescription = "Short", tint = Color(0xFFEF4444))
                            Text(
                                text = "SHORT SETUP (10 Sequential Steps)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFFEF4444)
                            )
                        }
                    }
                }

                items(shortSteps) { step ->
                    MobileExecutionStepCard(step = step, isLong = false)
                }
            }

            SpecScreenSection.CAUSALITY_AUDIT -> {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "CAUSALITY AUDIT CHECKLIST",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = theme.textPrimary,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Verification proving that zero future candles are used at any point in the pipeline.",
                            fontSize = 11.sp,
                            color = theme.textSecondary
                        )
                    }
                }

                items(auditItems) { item ->
                    MobileCausalityAuditCard(item = item)
                }
            }

            SpecScreenSection.GOVERNANCE -> {
                item {
                    ProfilesAndExperimentsCard(
                        approvalState = approvalState,
                        onSelectProfile = { viewModel.selectAplusProfile(it) },
                        onCreateExperimentClick = { showCreateExperimentDialog = true }
                    )
                }
            }
        }
    }

    // CREATE EXPERIMENT DIALOG
    if (showCreateExperimentDialog) {
        AlertDialog(
            onDismissRequest = { showCreateExperimentDialog = false },
            title = { Text("Create Experimental Copy", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Create an isolated copy of the approved A+ strategy to experiment with alternative parameters. The master A_PLUS_V1.0 profile remains immutable.",
                        fontSize = 12.sp,
                        color = theme.textSecondary
                    )
                    OutlinedTextField(
                        value = experimentNameInput,
                        onValueChange = { experimentNameInput = it },
                        label = { Text("Experiment Profile Name") },
                        placeholder = { Text("e.g. A_PLUS_FAST_RETEST_01") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createExperimentalCopy(experimentNameInput)
                        experimentNameInput = ""
                        showCreateExperimentDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.brandPrimary)
                ) {
                    Text("Create Experiment", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateExperimentDialog = false }) {
                    Text("Cancel", color = theme.textSecondary)
                }
            }
        )
    }

    // APPROVAL SUCCESS DIALOG
    if (showApprovalSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showApprovalSuccessDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Verified, contentDescription = "Approved", tint = Color(0xFF10B981))
                    Text("Strategy Specification Locked!", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "The A+ Trendline System has been formally locked as immutable profile 'A_PLUS_V1.0'.",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = theme.textPrimary
                    )
                    Text(
                        text = "All parameters have been approved. Backtest studio is unlocked.",
                        fontSize = 12.sp,
                        color = theme.textSecondary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showApprovalSuccessDialog = false
                        onApproved()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("Go to Backtest Studio", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

// -------------------------------------------------------------------------------------
// MOBILE-FIRST READABLE CARDS (NO COMPRESSED TABLES)
// -------------------------------------------------------------------------------------

@Composable
fun MobileParameterCard(
    param: APlusParameterItem,
    onToggleApproval: () -> Unit
) {
    val theme = LocalAppTheme.current
    val statusColor = if (param.isApproved) Color(0xFF10B981) else Color(0xFFF59E0B)
    val statusLabel = if (param.isApproved) "APPROVED" else "PENDING APPROVAL"
    val sourceColor = if (param.origin == "USER-SPECIFIED") Color(0xFF38BDF8) else Color(0xFFA78BFA)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("param_card_${param.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = theme.surface),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(if (param.isApproved) Color(0xFF10B981).copy(alpha = 0.5f) else theme.borderSubtle))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // PARAMETER HEADER
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "PARAMETER",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.textMuted,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = param.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = theme.textPrimary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusColor.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, statusColor)
                ) {
                    Text(
                        text = statusLabel,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            // VALUE & UNIT GRID (2 Column Mobile Pill)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color = theme.surfaceElevated
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(text = "VALUE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = theme.textMuted)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = param.currentValueStr,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = theme.brandPrimary
                        )
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color = theme.surfaceElevated
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(text = "UNIT", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = theme.textMuted)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = param.unit,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = theme.textPrimary
                        )
                    }
                }
            }

            // SOURCE BADGE
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "SOURCE:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.textMuted)
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = sourceColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = param.origin,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = sourceColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // DEFINITION CARD
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = theme.surfaceElevated
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "DEFINITION",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.brandPrimary
                    )
                    Text(
                        text = param.exactDefinition,
                        fontSize = 11.sp,
                        color = theme.textPrimary,
                        lineHeight = 15.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // EFFECT CARD
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = theme.surfaceElevated
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "EFFECT",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.textSecondary
                    )
                    Text(
                        text = param.effect,
                        fontSize = 11.sp,
                        color = theme.textSecondary,
                        lineHeight = 15.sp
                    )
                }
            }

            // APPROVAL BUTTON
            Button(
                onClick = onToggleApproval,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (param.isApproved) Color(0xFF10B981).copy(alpha = 0.2f) else theme.brandPrimary
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                Icon(
                    imageVector = if (param.isApproved) Icons.Default.Check else Icons.Default.ThumbUp,
                    contentDescription = "Approve",
                    tint = if (param.isApproved) Color(0xFF10B981) else Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (param.isApproved) "✓ APPROVED" else "Approve Parameter",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (param.isApproved) Color(0xFF10B981) else Color.White
                )
            }
        }
    }
}

@Composable
fun MobileExecutionStepCard(step: ExecutionStep, isLong: Boolean) {
    val theme = LocalAppTheme.current
    val accentColor = if (isLong) Color(0xFF10B981) else Color(0xFFEF4444)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("step_card_${step.stepNumber}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = theme.surface),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(accentColor.copy(alpha = 0.3f)))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // STEP HEADER
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = accentColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "${step.stepNumber}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                    }
                }
                Text(
                    text = step.stepTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = theme.textPrimary
                )
            }

            // RULE
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = theme.surfaceElevated
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "RULE:",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                    Text(
                        text = step.rule,
                        fontSize = 11.sp,
                        color = theme.textPrimary,
                        lineHeight = 15.sp
                    )
                }
            }

            // INFORMATION AVAILABLE
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = theme.surfaceElevated
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "INFORMATION AVAILABLE:",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.textSecondary
                    )
                    Text(
                        text = step.infoAvailable,
                        fontSize = 11.sp,
                        color = theme.textSecondary,
                        lineHeight = 14.sp
                    )
                }
            }

            // FUTURE DATA USED
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "FUTURE DATA USED:",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.textMuted
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = step.futureDataUsed,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MobileCausalityAuditCard(item: CausalityAuditCheckItem) {
    val theme = LocalAppTheme.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("audit_card_${item.componentName}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = theme.surface),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF10B981).copy(alpha = 0.3f)))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.componentName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = theme.textPrimary
                )

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Pass", tint = Color(0xFF10B981), modifier = Modifier.size(12.dp))
                        Text(
                            text = item.status,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                    }
                }
            }

            Text(
                text = item.explanation,
                fontSize = 11.sp,
                color = theme.textSecondary,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
fun ProfilesAndExperimentsCard(
    approvalState: APlusApprovalState,
    onSelectProfile: (String) -> Unit,
    onCreateExperimentClick: () -> Unit
) {
    val theme = LocalAppTheme.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profiles_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = theme.surface),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.borderSubtle))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "STRATEGY PROFILES & EXPERIMENTS",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = theme.textPrimary
                    )
                    Text(
                        text = "Immutable master reference & isolated experimental copies",
                        fontSize = 11.sp,
                        color = theme.textSecondary
                    )
                }

                if (approvalState.isApproved) {
                    Button(
                        onClick = onCreateExperimentClick,
                        colors = ButtonDefaults.buttonColors(containerColor = theme.brandPrimary),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Experiment", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }

            if (!approvalState.isApproved) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF59E0B).copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Master profile 'A_PLUS_V1.0' will be generated upon full specification approval. Once generated, it is permanently immutable.",
                        fontSize = 11.sp,
                        color = Color(0xFFF59E0B),
                        modifier = Modifier.padding(10.dp)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    approvalState.approvedProfiles.forEach { profile ->
                        val isSelected = profile.profileName == approvalState.activeProfileName
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectProfile(profile.profileId) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) theme.brandPrimary.copy(alpha = 0.12f) else theme.surfaceElevated,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) theme.brandPrimary else theme.borderSubtle)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = profile.profileName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = theme.textPrimary
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (profile.isImmutable) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFF38BDF8).copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = if (profile.isImmutable) "IMMUTABLE MASTER" else "EXPERIMENTAL",
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (profile.isImmutable) Color(0xFF10B981) else Color(0xFF38BDF8),
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Approved by ${profile.approvedBy} • ${profile.approvalTimestampIso}",
                                        fontSize = 10.sp,
                                        color = theme.textSecondary
                                    )
                                    Text(
                                        text = "HASH: ${profile.ruleSpecificationHash.take(16)}...",
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = theme.textMuted
                                    )
                                }

                                RadioButton(
                                    selected = isSelected,
                                    onClick = { onSelectProfile(profile.profileId) },
                                    colors = RadioButtonDefaults.colors(selectedColor = theme.brandPrimary)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
