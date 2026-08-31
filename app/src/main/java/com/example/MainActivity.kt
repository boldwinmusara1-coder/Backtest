package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tradestrat.ui.BacktestViewModel
import com.example.tradestrat.ui.screens.*
import com.example.ui.theme.*

enum class AppNavigationTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    DASHBOARD("Dashboard", Icons.Filled.Dashboard, Icons.Outlined.Dashboard, "nav_tab_dashboard"),
    BACKTEST("Backtest", Icons.AutoMirrored.Filled.ShowChart, Icons.AutoMirrored.Outlined.ShowChart, "nav_tab_backtest"),
    COMPARE("Compare", Icons.Filled.CompareArrows, Icons.Outlined.CompareArrows, "nav_tab_compare"),
    REPLAY("Replay", Icons.Filled.FastForward, Icons.Outlined.FastForward, "nav_tab_replay"),
    LAB("Lab", Icons.Filled.Science, Icons.Outlined.Science, "nav_tab_lab"),
    JOURNAL("Journal", Icons.Filled.BookmarkBorder, Icons.Outlined.BookmarkBorder, "nav_tab_journal"),
    RESULTS("Results", Icons.Filled.Assessment, Icons.Outlined.Assessment, "nav_tab_results"),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings, "nav_tab_settings")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val backtestViewModel: BacktestViewModel = viewModel()
                var currentTab by remember { mutableStateOf(AppNavigationTab.DASHBOARD) }
                var showSmcIctModal by remember { mutableStateOf(false) }
                var showAplusSpecScreen by remember { mutableStateOf(false) }
                val theme = LocalAppTheme.current

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = theme.background,
                    bottomBar = {
                        NavigationBar(
                            containerColor = theme.surface,
                            contentColor = theme.textPrimary,
                            tonalElevation = 0.dp
                        ) {
                            AppNavigationTab.values().forEach { tab ->
                                val isSelected = currentTab == tab && !showAplusSpecScreen
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = {
                                        showAplusSpecScreen = false
                                        currentTab = tab
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.title,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = tab.title,
                                            fontSize = 9.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = theme.brandPrimaryText,
                                        selectedTextColor = theme.brandPrimary,
                                        indicatorColor = theme.brandPrimaryContainer,
                                        unselectedIconColor = theme.textSecondary,
                                        unselectedTextColor = theme.textSecondary
                                    ),
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    if (showAplusSpecScreen) {
                        APlusStrategySpecScreen(
                            viewModel = backtestViewModel,
                            modifier = Modifier.padding(innerPadding),
                            onNavigateBack = { showAplusSpecScreen = false },
                            onApproved = {
                                showAplusSpecScreen = false
                                currentTab = AppNavigationTab.BACKTEST
                            }
                        )
                    } else {
                        when (currentTab) {
                            AppNavigationTab.DASHBOARD -> DashboardScreen(
                                viewModel = backtestViewModel,
                                modifier = Modifier.padding(innerPadding),
                                onNavigateToBacktest = { currentTab = AppNavigationTab.BACKTEST },
                                onNavigateToReplay = { currentTab = AppNavigationTab.REPLAY },
                                onNavigateToStrategyLab = { currentTab = AppNavigationTab.LAB },
                                onNavigateToJournal = { currentTab = AppNavigationTab.JOURNAL },
                                onNavigateToSmcIct = { currentTab = AppNavigationTab.BACKTEST },
                                onNavigateToResults = { currentTab = AppNavigationTab.RESULTS },
                                onNavigateToCompare = { currentTab = AppNavigationTab.COMPARE }
                            )

                            AppNavigationTab.BACKTEST -> BacktestScreen(
                                viewModel = backtestViewModel,
                                modifier = Modifier.padding(innerPadding),
                                onNavigateToSmcIct = { currentTab = AppNavigationTab.LAB },
                                onNavigateToAplusSpec = { showAplusSpecScreen = true },
                                onBacktestComplete = { currentTab = AppNavigationTab.RESULTS }
                            )

                            AppNavigationTab.COMPARE -> StrategyComparisonScreen(
                                viewModel = backtestViewModel,
                                modifier = Modifier.padding(innerPadding),
                                onNavigateBack = { currentTab = AppNavigationTab.DASHBOARD }
                            )

                            AppNavigationTab.REPLAY -> ReplayScreen(
                                viewModel = backtestViewModel,
                                modifier = Modifier.padding(innerPadding),
                                onNavigateBack = { currentTab = AppNavigationTab.DASHBOARD }
                            )

                            AppNavigationTab.LAB -> StrategyLabScreen(
                                viewModel = backtestViewModel,
                                modifier = Modifier.padding(innerPadding),
                                onNavigateBack = { currentTab = AppNavigationTab.DASHBOARD },
                                onApplyStrategy = { currentTab = AppNavigationTab.BACKTEST }
                            )

                            AppNavigationTab.JOURNAL -> JournalScreen(
                                viewModel = backtestViewModel,
                                modifier = Modifier.padding(innerPadding),
                                onNavigateBack = { currentTab = AppNavigationTab.DASHBOARD }
                            )

                            AppNavigationTab.RESULTS -> ResultsScreen(
                                viewModel = backtestViewModel,
                                modifier = Modifier.padding(innerPadding),
                                onNavigateToBacktest = { currentTab = AppNavigationTab.BACKTEST }
                            )

                            AppNavigationTab.SETTINGS -> SettingsScreen(
                                viewModel = backtestViewModel,
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
                }
            }
        }
    }
}
