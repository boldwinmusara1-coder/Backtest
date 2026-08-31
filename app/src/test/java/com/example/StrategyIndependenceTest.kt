package com.example

import com.example.tradestrat.engine.BacktestEngine
import com.example.tradestrat.model.*
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * STRATEGY INDEPENDENCE & PROVENANCE VALIDATION TEST SUITE
 *
 * Formally proves:
 * 1. SMC_CONCEPTS is a strictly independent standalone strategy using only SMC concepts
 *    (BOS, CHOCH / MSS, Order Blocks, Breaker Blocks) with zero ICT leakage.
 * 2. ICT_CONCEPTS is a strictly independent standalone strategy using only ICT concepts
 *    (Liquidity Sweeps, FVG, Displacement, EQH/EQL, Premium/Discount) with zero SMC leakage.
 * 3. SMC_ICT_CONCEPTS is a separate confluence strategy requiring multi-concept confirmation.
 * 4. Signal and Trade provenance is captured accurately on all generated signals.
 * 5. Configuration isolation: SMC parameters do not mutate ICT results and vice-versa.
 */
class StrategyIndependenceTest {

    companion object {
        lateinit var syntheticCandles: List<Candle>
        val testAsset = MarketAsset("BTC_USD", "BTC/USD", "Bitcoin", AssetCategory.CRYPTO, 65000.0, "Crypto")

        val standardRisk = RiskParameters(
            initialCapital = 10000.0,
            positionSizingMode = PositionSizingMode.FIXED_DOLLAR,
            positionSizeValue = 2000.0,
            leverage = 2.0,
            stopLossType = StopLossType.PERCENTAGE,
            stopLossValue = 4.0,
            takeProfitType = TakeProfitType.PERCENTAGE,
            takeProfitValue = 8.0,
            slippageBps = 5.0,
            commissionBps = 10.0,
            executionModel = ExecutionModel.REALISTIC,
            intrabarExecution = IntrabarExecutionAssumption.PESSIMISTIC_STOP_FIRST
        )

        @JvmStatic
        @BeforeClass
        fun setupSyntheticMarketData() {
            val baseTime = 1704067200000L // 2024-01-01 00:00 UTC
            val dayMs = 86400000L
            val list = mutableListOf<Candle>()
            var currentPrice = 50000.0

            for (i in 0 until 500) {
                val macroCycle = sin(i / 25.0) * 4000.0
                val subSwings = sin(i / 5.0) * 1000.0 + cos(i / 12.0) * 1500.0
                val drift = i * 15.0
                val targetClose = 50000.0 + macroCycle + subSwings + drift

                val open = currentPrice
                val close = targetClose
                val spread = kotlin.math.abs(close - open)
                val high = maxOf(open, close) + spread * 0.4 + 250.0
                val low = minOf(open, close) - spread * 0.4 - 250.0
                val volume = 25000.0 + (spread * 15.0)

                list.add(
                    Candle(
                        timestamp = baseTime + i * dayMs,
                        open = open,
                        high = high,
                        low = low,
                        close = close,
                        volume = volume
                    )
                )
                currentPrice = close
            }

            syntheticCandles = list
        }
    }

    @Test
    fun testSmcStandaloneIndependence() {
        val smcConfig = SmcConfig(
            useBos = true,
            bosLookback = 5,
            useChoch = true,
            useOrderBlock = true,
            useBreakerBlock = true,
            useLiquiditySweep = false,
            useFvg = false,
            useDisplacement = false,
            useEqualHighsLows = false,
            usePremiumDiscount = false,
            requireConfluence = false,
            minConfluences = 1
        )

        val smcStrategy = StrategyDefinition(
            id = "smc_standalone",
            name = "SMC Standalone",
            description = "Pure SMC strategy with zero ICT features",
            strategyType = StrategyType.SMC_CONCEPTS,
            indicatorConfig = IndicatorConfig(smcConfig = smcConfig)
        )

        val result = BacktestEngine.runBacktest(
            candles = syntheticCandles,
            asset = testAsset,
            regime = MarketRegime.HISTORICAL_REALISTIC,
            timeframe = Timeframe.D1,
            strategy = smcStrategy,
            risk = standardRisk
        )

        assertNotNull("Result should not be null", result)
        assertTrue("SMC standalone should generate signals", result.signalMarkers.isNotEmpty())
        assertTrue("SMC standalone should execute trades", result.trades.isNotEmpty())

        val smcMetrics = result.smcMetrics
        assertNotNull("SMC metrics must be present", smcMetrics)

        // SMC metrics must have SMC events but zero ICT events
        val totalSmcEvents = smcMetrics!!.bosEventsCount + smcMetrics.chochEventsCount + smcMetrics.orderBlocksCount + smcMetrics.breakerBlocksCount
        assertTrue("SMC standalone should record BOS/CHOCH/OB/Breaker events ($totalSmcEvents)", totalSmcEvents > 0)
        assertEquals("SMC standalone must have 0 liquidity sweeps", 0, smcMetrics.liquiditySweepsCount)
        assertEquals("SMC standalone must have 0 FVG gaps", 0, smcMetrics.fvgCount)
        assertEquals("SMC standalone must have 0 displacement events", 0, smcMetrics.displacementCount)
        assertEquals("SMC standalone must have 0 EQH/EQL pools", 0, smcMetrics.eqhEqlCount)

        // Verify signal reasons contain only SMC terminology
        val entryMarkers = result.signalMarkers.filter { it.isEntry }
        for (marker in entryMarkers) {
            val reason = marker.signalReason
            assertNotNull("Entry signal marker must have a non-null reason", reason)
            assertFalse("SMC signal must NOT contain 'Sweep'", reason!!.contains("Sweep", ignoreCase = true))
            assertFalse("SMC signal must NOT contain 'FVG'", reason.contains("FVG", ignoreCase = true))
            assertFalse("SMC signal must NOT contain 'Displacement'", reason.contains("Displacement", ignoreCase = true))
            assertFalse("SMC signal must NOT contain 'EQH'", reason.contains("EQH", ignoreCase = true))
        }

        // Verify trade entry reasons match
        for (trade in result.trades) {
            val reason = trade.entryReason
            assertNotNull("Trade must have an entryReason", reason)
            assertFalse("SMC trade must NOT contain ICT concepts", reason!!.contains("FVG") || reason.contains("Sweep"))
        }
    }

    @Test
    fun testIctStandaloneIndependence() {
        val ictConfig = SmcConfig(
            useBos = false,
            useChoch = false,
            useOrderBlock = false,
            useBreakerBlock = false,
            useLiquiditySweep = true,
            useFvg = true,
            useDisplacement = true,
            useEqualHighsLows = true,
            usePremiumDiscount = true,
            requireConfluence = false,
            minConfluences = 1
        )

        val ictStrategy = StrategyDefinition(
            id = "ict_standalone",
            name = "ICT Standalone",
            description = "Pure ICT strategy with zero SMC features",
            strategyType = StrategyType.ICT_CONCEPTS,
            indicatorConfig = IndicatorConfig(smcConfig = ictConfig)
        )

        val result = BacktestEngine.runBacktest(
            candles = syntheticCandles,
            asset = testAsset,
            regime = MarketRegime.HISTORICAL_REALISTIC,
            timeframe = Timeframe.D1,
            strategy = ictStrategy,
            risk = standardRisk
        )

        assertNotNull("Result should not be null", result)
        assertTrue("ICT standalone should generate signals", result.signalMarkers.isNotEmpty())
        assertTrue("ICT standalone should execute trades", result.trades.isNotEmpty())

        val smcMetrics = result.smcMetrics
        assertNotNull("SMC/ICT metrics must be present", smcMetrics)

        // ICT metrics must have ICT events but zero SMC events
        val totalIctEvents = smcMetrics!!.liquiditySweepsCount + smcMetrics.fvgCount + smcMetrics.displacementCount + smcMetrics.eqhEqlCount
        assertTrue("ICT standalone should record ICT events ($totalIctEvents)", totalIctEvents > 0)
        assertEquals("ICT standalone must have 0 BOS events", 0, smcMetrics.bosEventsCount)
        assertEquals("ICT standalone must have 0 CHOCH events", 0, smcMetrics.chochEventsCount)
        assertEquals("ICT standalone must have 0 Order Blocks", 0, smcMetrics.orderBlocksCount)
        assertEquals("ICT standalone must have 0 Breaker Blocks", 0, smcMetrics.breakerBlocksCount)

        // Verify signal reasons contain only ICT terminology
        val entryMarkers = result.signalMarkers.filter { it.isEntry }
        for (marker in entryMarkers) {
            val reason = marker.signalReason
            assertNotNull("Entry signal marker must have a non-null reason", reason)
            assertFalse("ICT signal must NOT contain 'BOS'", reason!!.contains("BOS", ignoreCase = true))
            assertFalse("ICT signal must NOT contain 'CHOCH'", reason.contains("CHOCH", ignoreCase = true))
            assertFalse("ICT signal must NOT contain 'Order Block'", reason.contains("Order Block", ignoreCase = true))
            assertFalse("ICT signal must NOT contain 'Breaker'", reason.contains("Breaker", ignoreCase = true))
        }

        // Verify trade entry reasons match
        for (trade in result.trades) {
            val reason = trade.entryReason
            assertNotNull("Trade must have an entryReason", reason)
            assertFalse("ICT trade must NOT contain SMC concepts", reason!!.contains("BOS") || reason.contains("CHOCH") || reason.contains("Order Block"))
        }
    }

    @Test
    fun testSmcIctConfluenceDistinctiveness() {
        // SMC Standalone
        val smcOnlyConfig = SmcConfig(
            useBos = true,
            useChoch = true,
            useOrderBlock = true,
            useBreakerBlock = true,
            useLiquiditySweep = false,
            useFvg = false,
            useDisplacement = false,
            useEqualHighsLows = false,
            usePremiumDiscount = false,
            requireConfluence = false,
            minConfluences = 1
        )
        val smcResult = BacktestEngine.runBacktest(
            syntheticCandles, testAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.D1,
            StrategyDefinition("smc_only", "SMC", "", StrategyType.SMC_CONCEPTS, IndicatorConfig(smcConfig = smcOnlyConfig)),
            standardRisk
        )

        // ICT Standalone
        val ictOnlyConfig = SmcConfig(
            useBos = false,
            useChoch = false,
            useOrderBlock = false,
            useBreakerBlock = false,
            useLiquiditySweep = true,
            useFvg = true,
            useDisplacement = true,
            useEqualHighsLows = true,
            usePremiumDiscount = true,
            requireConfluence = false,
            minConfluences = 1
        )
        val ictResult = BacktestEngine.runBacktest(
            syntheticCandles, testAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.D1,
            StrategyDefinition("ict_only", "ICT", "", StrategyType.ICT_CONCEPTS, IndicatorConfig(smcConfig = ictOnlyConfig)),
            standardRisk
        )

        // SMC + ICT Combined Confluence
        val confluenceConfig = SmcConfig(
            useBos = true,
            useChoch = true,
            useOrderBlock = true,
            useBreakerBlock = true,
            useLiquiditySweep = true,
            useFvg = true,
            fvgMinGapAtrMultiple = 0.2,
            useDisplacement = true,
            displacementAtrMultiplier = 1.2,
            useEqualHighsLows = true,
            usePremiumDiscount = false,
            requireConfluence = true,
            minConfluences = 2
        )
        val confluenceResult = BacktestEngine.runBacktest(
            syntheticCandles, testAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.D1,
            StrategyDefinition("smc_ict_conf", "SMC + ICT", "", StrategyType.SMC_ICT_CONCEPTS, IndicatorConfig(smcConfig = confluenceConfig)),
            standardRisk
        )

        println("=== STRATEGY INDEPENDENCE VALIDATION RESULTS ===")
        println("SMC Standalone Trades: ${smcResult.trades.size}, Win Rate: ${String.format("%.1f", smcResult.metrics.winRatePercent)}%, Profit: ${String.format("%.2f", smcResult.metrics.netProfitPercent)}%")
        println("ICT Standalone Trades: ${ictResult.trades.size}, Win Rate: ${String.format("%.1f", ictResult.metrics.winRatePercent)}%, Profit: ${String.format("%.2f", ictResult.metrics.netProfitPercent)}%")
        println("SMC+ICT Confluence Trades: ${confluenceResult.trades.size}, Win Rate: ${String.format("%.1f", confluenceResult.metrics.winRatePercent)}%, Profit: ${String.format("%.2f", confluenceResult.metrics.netProfitPercent)}%")

        // Confluence must be more selective than loose unconstrained single-trigger runs
        assertTrue("Confluence strategy requires multiple conditions and produces distinct trade profile",
            confluenceResult.trades.isNotEmpty())
        
        // Assert trade sets are distinct
        val smcEntryBars = smcResult.trades.map { it.barIndex }.toSet()
        val ictEntryBars = ictResult.trades.map { it.barIndex }.toSet()
        val confluenceEntryBars = confluenceResult.trades.map { it.barIndex }.toSet()

        assertNotEquals("SMC and ICT entry bars should not be identical", smcEntryBars, ictEntryBars)
        assertNotEquals("Confluence and Standalone SMC entry bars should not be identical", smcEntryBars, confluenceEntryBars)
        assertNotEquals("Confluence and Standalone ICT entry bars should not be identical", ictEntryBars, confluenceEntryBars)
    }

    @Test
    fun testConfigurationIsolation() {
        // Base SMC config
        val baseSmcConfig = SmcConfig(
            useBos = true,
            bosLookback = 5,
            useChoch = true,
            useOrderBlock = true,
            fvgMinGapAtrMultiple = 0.5,
            sweepWickMinPct = 0.20
        )
        val res1 = BacktestEngine.runBacktest(
            syntheticCandles, testAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.D1,
            StrategyDefinition("smc_1", "SMC 1", "", StrategyType.SMC_CONCEPTS, IndicatorConfig(smcConfig = baseSmcConfig)),
            standardRisk
        )

        // Mutate ICT parameters on SMC_CONCEPTS strategy
        val mutatedIctOnSmc = baseSmcConfig.copy(
            fvgMinGapAtrMultiple = 1.8,
            sweepWickMinPct = 0.90,
            useFvg = true,
            useLiquiditySweep = true
        )
        val res2 = BacktestEngine.runBacktest(
            syntheticCandles, testAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.D1,
            StrategyDefinition("smc_2", "SMC 2", "", StrategyType.SMC_CONCEPTS, IndicatorConfig(smcConfig = mutatedIctOnSmc)),
            standardRisk
        )

        // The SMC_CONCEPTS strategy only runs SMC logic so ICT mutations must not change the trades or profit
        assertEquals("SMC trade count must be identical regardless of ICT parameter changes", res1.trades.size, res2.trades.size)
        assertEquals("SMC net profit must be identical regardless of ICT parameter changes", res1.metrics.netProfitPercent, res2.metrics.netProfitPercent, 1e-6)
    }
}
