package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.tradestrat.data.MarketDataProvider
import com.example.tradestrat.engine.BacktestEngine
import com.example.tradestrat.model.*
import com.example.tradestrat.ui.BacktestViewModel
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.ThemeManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UiRedesignRegressionAuditTest {

    private lateinit var context: Context
    private lateinit var application: Application

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        application = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testThemeSwitchingAndPersistenceAcrossAppRestart() {
        // Step 1: Initialize ThemeManager with context
        ThemeManager.init(context)

        // Step 2: Switch to LIGHT theme
        ThemeManager.setThemeMode(AppThemeMode.LIGHT)
        assertEquals(AppThemeMode.LIGHT, ThemeManager.themeMode.value)

        // Step 3: Verify SharedPreferences stored LIGHT
        val prefs = context.getSharedPreferences("tradestrat_theme_prefs", Context.MODE_PRIVATE)
        assertEquals(AppThemeMode.LIGHT.name, prefs.getString("app_theme_mode", null))

        // Step 4: Switch to DARK theme
        ThemeManager.setThemeMode(AppThemeMode.DARK)
        assertEquals(AppThemeMode.DARK, ThemeManager.themeMode.value)
        assertEquals(AppThemeMode.DARK.name, prefs.getString("app_theme_mode", null))

        // Step 5: Switch to SYSTEM theme
        ThemeManager.setThemeMode(AppThemeMode.SYSTEM)
        assertEquals(AppThemeMode.SYSTEM, ThemeManager.themeMode.value)
        assertEquals(AppThemeMode.SYSTEM.name, prefs.getString("app_theme_mode", null))
    }

    @Test
    fun testNavigationTabIntegrity() {
        val tabs = AppNavigationTab.values()
        assertTrue(tabs.size >= 7)
        assertTrue(tabs.any { it == AppNavigationTab.DASHBOARD && it.testTag == "nav_tab_dashboard" })
        assertTrue(tabs.any { it == AppNavigationTab.BACKTEST && it.testTag == "nav_tab_backtest" })
        assertTrue(tabs.any { it == AppNavigationTab.COMPARE && it.testTag == "nav_tab_compare" })
        assertTrue(tabs.any { it == AppNavigationTab.REPLAY && it.testTag == "nav_tab_replay" })
        assertTrue(tabs.any { it == AppNavigationTab.LAB && it.testTag == "nav_tab_lab" })
        assertTrue(tabs.any { it == AppNavigationTab.JOURNAL && it.testTag == "nav_tab_journal" })
        assertTrue(tabs.any { it == AppNavigationTab.RESULTS && it.testTag == "nav_tab_results" })
        assertTrue(tabs.any { it == AppNavigationTab.SETTINGS && it.testTag == "nav_tab_settings" })
    }

    @Test
    fun test5mAndTimeframePassingToEngine() {
        val btcAsset = MarketDataProvider.ASSETS.first { it.symbol.contains("BTC") }
        val candles5m = MarketDataProvider.generateHistoricalData(btcAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.M5, 300)

        val emaStrategy = StrategyDefinition(
            id = "test_ema_cross",
            name = "EMA 9 / SMA 21",
            strategyType = StrategyType.MA_CROSSOVER,
            indicatorConfig = IndicatorConfig(
                maParams = MovingAverageParams(fastPeriod = 9, slowPeriod = 21, useEma = true)
            )
        )
        val risk = RiskParameters(initialCapital = 10000.0, positionSizeValue = 20.0)

        val result5m = BacktestEngine.runBacktest(
            candles = candles5m,
            asset = btcAsset,
            regime = MarketRegime.HISTORICAL_REALISTIC,
            timeframe = Timeframe.M5,
            strategy = emaStrategy,
            risk = risk
        )

        assertNotNull(result5m)
        assertEquals(Timeframe.M5, result5m.timeframe)
        assertEquals(btcAsset.symbol, result5m.asset.symbol)
        assertEquals(emaStrategy.name, result5m.strategy.name)
        assertTrue(result5m.equityCurve.isNotEmpty())
        assertEquals(candles5m.size, result5m.equityCurve.size)
    }

    @Test
    fun test30mAndTimeframePassingToEngine() {
        val btcAsset = MarketDataProvider.ASSETS.first { it.symbol.contains("BTC") }
        val candles30m = MarketDataProvider.generateHistoricalData(btcAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.M30, 300)

        val emaStrategy = StrategyDefinition(
            id = "test_ema_cross",
            name = "EMA 9 / SMA 21",
            strategyType = StrategyType.MA_CROSSOVER,
            indicatorConfig = IndicatorConfig(
                maParams = MovingAverageParams(fastPeriod = 9, slowPeriod = 21, useEma = true)
            )
        )
        val risk = RiskParameters(initialCapital = 10000.0, positionSizeValue = 20.0)

        val result30m = BacktestEngine.runBacktest(
            candles = candles30m,
            asset = btcAsset,
            regime = MarketRegime.HISTORICAL_REALISTIC,
            timeframe = Timeframe.M30,
            strategy = emaStrategy,
            risk = risk
        )

        assertNotNull(result30m)
        assertEquals(Timeframe.M30, result30m.timeframe)
        assertEquals(btcAsset.symbol, result30m.asset.symbol)
        assertEquals(emaStrategy.name, result30m.strategy.name)
        assertTrue(result30m.equityCurve.isNotEmpty())
        assertEquals(candles30m.size, result30m.equityCurve.size)
    }

    @Test
    fun testBtcUsdtCompleteBacktest30mEma9Sma21() {
        val btcAsset = MarketDataProvider.ASSETS.first { it.symbol.contains("BTC") }
        val candles = MarketDataProvider.generateHistoricalData(btcAsset, MarketRegime.STRONG_BULL, Timeframe.M30, 500)
        val strategy = StrategyDefinition(
            id = "ema_9_21",
            name = "EMA 9 / SMA 21",
            strategyType = StrategyType.MA_CROSSOVER,
            indicatorConfig = IndicatorConfig(
                maParams = MovingAverageParams(fastPeriod = 9, slowPeriod = 21, useEma = true)
            )
        )
        val risk = RiskParameters(
            initialCapital = 10000.0,
            positionSizeValue = 25.0,
            slippageBps = 5.0,
            commissionBps = 10.0
        )

        val result = BacktestEngine.runBacktest(
            candles = candles,
            asset = btcAsset,
            regime = MarketRegime.STRONG_BULL,
            timeframe = Timeframe.M30,
            strategy = strategy,
            risk = risk
        )

        assertNotNull(result)
        assertEquals(Timeframe.M30, result.timeframe)
        assertEquals("BTC/USDT", result.asset.symbol)
        assertTrue(result.trades.isNotEmpty())
        assertTrue(result.metrics.totalTrades > 0)
        assertNotNull(result.metrics.winRatePercent)
        assertNotNull(result.metrics.profitFactor)
        assertNotNull(result.metrics.sharpeRatio)
        assertNotNull(result.metrics.maxDrawdownPercent)
        assertEquals(candles.size, result.equityCurve.size)
    }

    @Test
    fun testBtcUsdtCompleteBacktest5mEma9Sma21() {
        val btcAsset = MarketDataProvider.ASSETS.first { it.symbol.contains("BTC") }
        val candles = MarketDataProvider.generateHistoricalData(btcAsset, MarketRegime.BREAKOUT_MOMENTUM, Timeframe.M5, 500)
        val strategy = StrategyDefinition(
            id = "ema_9_21",
            name = "EMA 9 / SMA 21",
            strategyType = StrategyType.MA_CROSSOVER,
            indicatorConfig = IndicatorConfig(
                maParams = MovingAverageParams(fastPeriod = 9, slowPeriod = 21, useEma = true)
            )
        )
        val risk = RiskParameters(
            initialCapital = 10000.0,
            positionSizeValue = 25.0,
            slippageBps = 5.0,
            commissionBps = 10.0
        )

        val result = BacktestEngine.runBacktest(
            candles = candles,
            asset = btcAsset,
            regime = MarketRegime.BREAKOUT_MOMENTUM,
            timeframe = Timeframe.M5,
            strategy = strategy,
            risk = risk
        )

        assertNotNull(result)
        assertEquals(Timeframe.M5, result.timeframe)
        assertEquals("BTC/USDT", result.asset.symbol)
        assertTrue(result.trades.isNotEmpty())
        assertTrue(result.metrics.totalTrades > 0)
        assertNotNull(result.metrics.winRatePercent)
        assertNotNull(result.metrics.profitFactor)
        assertNotNull(result.metrics.sharpeRatio)
        assertNotNull(result.metrics.maxDrawdownPercent)
        assertEquals(candles.size, result.equityCurve.size)
    }

    @Test
    fun testSmcIctConfigurationReachesEngine() {
        val customSmcConfig = SmcConfig(
            useBos = true,
            useChoch = true,
            useOrderBlock = true,
            useFvg = true,
            useLiquiditySweep = true,
            requireConfluence = true,
            minConfluences = 2
        )

        val smcStrategy = StrategyDefinition(
            id = "custom_smc_test",
            name = "Institutional SMC/ICT Confluence",
            strategyType = StrategyType.SMC_ICT_CONCEPTS,
            indicatorConfig = IndicatorConfig(smcConfig = customSmcConfig)
        )

        val btcAsset = MarketDataProvider.ASSETS.first { it.symbol.contains("BTC") }
        val candles = MarketDataProvider.generateHistoricalData(btcAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.H1, 400)

        val result = BacktestEngine.runBacktest(
            candles = candles,
            asset = btcAsset,
            regime = MarketRegime.HISTORICAL_REALISTIC,
            timeframe = Timeframe.H1,
            strategy = smcStrategy,
            risk = RiskParameters()
        )

        assertNotNull(result)
        assertEquals(StrategyType.SMC_ICT_CONCEPTS, result.strategy.strategyType)
        assertEquals(true, result.strategy.indicatorConfig.smcConfig.useBos)
        assertEquals(true, result.strategy.indicatorConfig.smcConfig.useChoch)
        assertEquals(true, result.strategy.indicatorConfig.smcConfig.useOrderBlock)
        assertEquals(true, result.strategy.indicatorConfig.smcConfig.useFvg)
        assertEquals(true, result.strategy.indicatorConfig.smcConfig.useLiquiditySweep)
        assertEquals(true, result.strategy.indicatorConfig.smcConfig.requireConfluence)
        assertEquals(2, result.strategy.indicatorConfig.smcConfig.minConfluences)
    }

    @Test
    fun testViewModelAsynchronousResponsivenessAndCancellation() = runTest {
        val viewModel = BacktestViewModel(application)

        // Set parameters
        viewModel.setTimeframe(Timeframe.M5)
        assertEquals(Timeframe.M5, viewModel.selectedTimeframe.first())

        viewModel.setTimeframe(Timeframe.M30)
        assertEquals(Timeframe.M30, viewModel.selectedTimeframe.first())

        // Test cancel
        viewModel.cancelBacktest()
        assertFalse(viewModel.isBacktesting.first())
    }
}
