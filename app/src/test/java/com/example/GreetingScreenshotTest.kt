package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.tradestrat.model.BacktestMetrics
import com.example.tradestrat.ui.components.MetricsOverview
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    val sampleMetrics = BacktestMetrics(
      initialCapital = 10000.0,
      finalEquity = 14250.0,
      netProfitDollars = 4250.0,
      netProfitPercent = 42.50,
      benchmarkReturnPercent = 18.20,
      alphaPercent = 24.30,
      cagrPercent = 48.20,
      maxDrawdownPercent = 7.80,
      maxDrawdownDurationBars = 12,
      sharpeRatio = 1.92,
      sortinoRatio = 2.40,
      calmarRatio = 5.45,
      totalTrades = 24,
      winningTrades = 16,
      losingTrades = 8,
      winRatePercent = 66.67,
      profitFactor = 2.45,
      payoffRatio = 1.85,
      avgTradePercent = 1.77,
      avgWinningTradePercent = 3.20,
      avgLosingTradePercent = -1.50,
      largestWinningTradeDollars = 920.0,
      largestLosingTradeDollars = -340.0,
      maxConsecutiveWins = 6,
      maxConsecutiveLosses = 2,
      totalFeesPaid = 48.50,
      avgHoldingBars = 4.2,
      expectancyDollars = 177.08,
      expectancyR = 1.18
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        MetricsOverview(metrics = sampleMetrics)
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}
