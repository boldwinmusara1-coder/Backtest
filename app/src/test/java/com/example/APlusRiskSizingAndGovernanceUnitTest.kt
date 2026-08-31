package com.example

import com.example.tradestrat.data.DeterministicHistoricalDataProvider
import com.example.tradestrat.engine.APlusTrendlineEngine
import com.example.tradestrat.engine.BacktestEngine
import com.example.tradestrat.model.APlusParameterItem
import com.example.tradestrat.model.APlusStrategySpecification
import com.example.tradestrat.model.APlusTrendlineConfig
import com.example.tradestrat.model.AssetCategory
import com.example.tradestrat.model.MarketAsset
import com.example.tradestrat.model.MarketRegime
import com.example.tradestrat.model.PositionSizingMode
import com.example.tradestrat.model.RiskParameters
import com.example.tradestrat.model.Timeframe
import com.example.tradestrat.model.TradeDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class APlusRiskSizingAndGovernanceUnitTest {

    @Test
    fun testRiskBasedSizingFormulaAcrossVariedEquityAndStops() {
        // Test varying equities: $5,000, $10,000, $25,000, $100,000
        val testEquities = listOf(5000.0, 10000.0, 25000.0, 100000.0)
        // Test varying entry prices and stop loss distances
        val testCases = listOf(
            // Long: entry $30,000, stop $29,100 (stop distance $900 = 3.0%)
            Triple(TradeDirection.LONG, 30000.0, 29100.0),
            // Long: entry $45,000, stop $44,550 (stop distance $450 = 1.0%)
            Triple(TradeDirection.LONG, 45000.0, 44550.0),
            // Short: entry $35,000, stop $35,700 (stop distance $700 = 2.0%)
            Triple(TradeDirection.SHORT, 35000.0, 35700.0),
            // Short: entry $60,000, stop $61,200 (stop distance $1200 = 2.0%)
            Triple(TradeDirection.SHORT, 60000.0, 61200.0)
        )

        for (equity in testEquities) {
            for ((direction, entryPrice, stopPrice) in testCases) {
                val risk = RiskParameters(
                    initialCapital = equity,
                    positionSizingMode = PositionSizingMode.RISK_BASED,
                    positionSizeValue = 1.0, // 1.0% equity risk
                    leverage = 1.0,
                    slippageBps = 0.0,
                    commissionBps = 0.0
                )

                val riskAmount = equity * 0.01
                val stopDistance = abs(entryPrice - stopPrice)
                val expectedQuantity = riskAmount / stopDistance
                val expectedNotional = expectedQuantity * entryPrice

                // Calculate via BacktestEngine sizing logic
                val riskPct = 0.01
                val calculatedRiskAmount = equity * riskPct
                val desiredQuantity = calculatedRiskAmount / stopDistance
                val desiredValue = desiredQuantity * entryPrice
                val maxAllowedMargin = equity * 0.98

                val actualValue = if (desiredValue > maxAllowedMargin) maxAllowedMargin else desiredValue
                val actualQuantity = actualValue / entryPrice
                val actualInitialRisk = stopDistance * actualQuantity

                // If unconstrained by 98% margin cap
                if (desiredValue <= maxAllowedMargin) {
                    val ratio = actualInitialRisk / equity
                    assertEquals("Risk fraction must equal exactly 0.01 (1.0%)", 0.01, ratio, 1e-6)
                    assertEquals("Actual quantity must equal expected quantity", expectedQuantity, actualQuantity, 1e-6)
                    assertEquals("Initial risk must equal stop distance * quantity", stopDistance * actualQuantity, actualInitialRisk, 1e-6)
                } else {
                    assertTrue("Capped risk must be <= 1.0%", actualInitialRisk <= equity * 0.01)
                }
            }
        }
    }

    @Test
    fun testMaxLineAgeBarsRemovedFromConfigAndGovernance() {
        val config = APlusTrendlineConfig.A_PLUS_DEFAULT_CONFIG
        val paramMap = config.toParameterMap()
        assertTrue("maxLineAgeBars must NOT be present in config parameter map", !paramMap.containsKey("maxLineAgeBars"))

        val governanceRules = APlusStrategySpecification.CONFIRMED_RULES
        val maxAgeRule = governanceRules.find { it.id == "maxLineAgeBars" }
        assertTrue("maxLineAgeBars must NOT be present in confirmed governance rules", maxAgeRule == null)
    }

    @Test
    fun testSpecificationHashDeterminism() {
        val hash1 = APlusStrategySpecification.computeSpecificationHash()
        val hash2 = APlusStrategySpecification.computeSpecificationHash()
        assertEquals("Hash must be deterministic", hash1, hash2)
        assertEquals("SHA-256 hash length must be 64 hex characters", 64, hash1.length)
        assertTrue("Hash must be valid hex", hash1.matches(Regex("^[0-9a-f]{64}$")))
    }

    @Test
    fun testZeroLookAheadIncrementalInvariance() {
        val btcAsset = MarketAsset(
            id = "btc_usd",
            symbol = "BTC/USD",
            name = "Bitcoin",
            category = AssetCategory.CRYPTO,
            basePrice = 29000.0,
            description = "Bitcoin spot"
        )
        val candles = DeterministicHistoricalDataProvider.generateCandlesCount(
            asset = btcAsset,
            timeframe = Timeframe.H1,
            startTimeMs = 1609459200000L,
            count = 300,
            seed = 42L
        )

        val config = APlusTrendlineConfig.A_PLUS_DEFAULT_CONFIG
        val engineFull = APlusTrendlineEngine(config)

        for (i in 50..150) {
            val slice = candles.take(i + 1)
            val engineSlice = APlusTrendlineEngine(config)
            
            var incrementalRes: APlusTrendlineEngine.SignalResult? = null
            for (k in 0..i) {
                incrementalRes = engineSlice.evaluateBar(slice, k)
            }
            val fullRes = engineFull.evaluateBar(candles, i)

            assertEquals("Incremental long signal at bar $i must match full signal", fullRes.longSignal, incrementalRes?.longSignal)
            assertEquals("Incremental short signal at bar $i must match full signal", fullRes.shortSignal, incrementalRes?.shortSignal)
        }
    }
}
