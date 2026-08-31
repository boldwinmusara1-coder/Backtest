package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.tradestrat.data.MarketDataProvider
import com.example.tradestrat.engine.BacktestEngine
import com.example.tradestrat.model.MarketRegime
import com.example.tradestrat.model.RiskParameters
import com.example.tradestrat.model.StrategyDefinition
import com.example.tradestrat.model.Timeframe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Strategy Backtester", appName)
  }

  @Test
  fun `verify backtest engine runs simulation successfully`() {
    val asset = MarketDataProvider.ASSETS.first()
    val candles = MarketDataProvider.generateHistoricalData(asset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.D1, 100)
    val strategy = StrategyDefinition.PRESETS.first()
    val risk = RiskParameters()

    val result = BacktestEngine.runBacktest(candles, asset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.D1, strategy, risk)

    assertNotNull(result)
    assertTrue(result.equityCurve.isNotEmpty())
    assertTrue(result.metrics.initialCapital == 10000.0)
  }
}
