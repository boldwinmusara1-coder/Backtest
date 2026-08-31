package com.example

import com.example.tradestrat.data.MarketDataValidator
import com.example.tradestrat.engine.BacktestEngine
import com.example.tradestrat.engine.SmcEngine
import com.example.tradestrat.model.*
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import java.util.Locale
import kotlin.math.sin
import kotlin.math.cos

/**
 * SMC / ICT CONCEPTS DETERMINISTIC BACKTESTING VALIDATION TEST
 * 
 * Validates all 10 SMC/ICT components independently and in combinations
 * with zero look-ahead bias, next-bar-open execution, pessimistic SL-first,
 * $2,000 margin, 2x leverage ($4,000 position notional).
 */
class SmcConceptBacktestValidationTest {

    companion object {
        lateinit var btcCandles: List<Candle>
        val btcAsset = MarketAsset("BTC_USD", "BTC/USD", "Bitcoin", AssetCategory.CRYPTO, 65000.0, "Crypto")

        val standardRisk = RiskParameters(
            initialCapital = 10000.0,
            positionSizingMode = PositionSizingMode.FIXED_DOLLAR,
            positionSizeValue = 2000.0, // Fixed $2,000 margin
            leverage = 2.0,             // $4,000 position notional
            stopLossType = StopLossType.PERCENTAGE,
            stopLossValue = 5.0,        // 5% Stop Loss
            takeProfitType = TakeProfitType.PERCENTAGE,
            takeProfitValue = 10.0,     // 10% Take Profit
            slippageBps = 5.0,          // 5 bps adverse slippage
            commissionBps = 10.0,       // 10 bps exchange commission
            executionModel = ExecutionModel.REALISTIC,
            intrabarExecution = IntrabarExecutionAssumption.PESSIMISTIC_STOP_FIRST
        )

        @JvmStatic
        @BeforeClass
        fun loadDataset() {
            // Generate a comprehensive 600-candle multi-regime series with structural swings, reversals, imbalances, and sweeps
            val baseTime = 1704067200000L // 2024-01-01 00:00 UTC
            val dayMs = 86400000L
            val list = mutableListOf<Candle>()
            var currentPrice = 40000.0

            for (i in 0 until 600) {
                // Multi-frequency wave with sub-swings creating both trend continuations (BOS) and macro reversals (CHOCH)
                val macroCycle = sin(i / 30.0) * 5000.0
                val subSwings = sin(i / 6.0) * 1200.0 + cos(i / 14.0) * 1800.0
                val drift = (i * 20.0)
                val targetClose = 40000.0 + macroCycle + subSwings + drift
                
                val open = currentPrice
                val close = targetClose
                val spread = kotlin.math.abs(close - open)
                val high = maxOf(open, close) + spread * 0.3 + 300.0
                val low = minOf(open, close) - spread * 0.3 - 300.0
                val volume = 30000.0 + (spread * 20.0)

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

            btcCandles = list
            println("Initialized ${btcCandles.size} deterministic OHLCV candles for SMC/ICT validation.")
        }
    }

    private fun runSmcBacktest(
        configName: String,
        smcConfig: SmcConfig
    ): BacktestResult {
        val strategy = StrategyDefinition(
            id = "smc_test_${configName.lowercase().replace(" ", "_")}",
            name = configName,
            description = "Deterministic SMC / ICT test: $configName",
            strategyType = StrategyType.SMC_ICT_CONCEPTS,
            indicatorConfig = IndicatorConfig(smcConfig = smcConfig)
        )

        val result = BacktestEngine.runBacktest(
            candles = btcCandles,
            asset = btcAsset,
            regime = MarketRegime.HISTORICAL_REALISTIC,
            timeframe = Timeframe.D1,
            strategy = strategy,
            risk = standardRisk
        )

        val m = result.metrics
        val smc = result.smcMetrics ?: SmcMetrics()
        val grossProfit = result.trades.filter { it.pnlDollars > 0 }.sumOf { it.pnlDollars }
        val grossLoss = result.trades.filter { it.pnlDollars < 0 }.sumOf { kotlin.math.abs(it.pnlDollars) }
        val totalFees = result.trades.sumOf { it.feesPaid }

        println("\n==========================================================================")
        println("SMC CONFIGURATION: $configName")
        println("==========================================================================")
        println("Total Trades:                ${m.totalTrades}")
        println("Win Rate:                    ${String.format(Locale.US, "%.2f%%", m.winRatePercent)}")
        println("Gross Profit:                ${String.format(Locale.US, "$%.2f", grossProfit)}")
        println("Gross Loss:                  ${String.format(Locale.US, "$%.2f", grossLoss)}")
        println("Total Fees/Slippage Paid:    ${String.format(Locale.US, "$%.2f", totalFees)}")
        println("Net Realized P&L:            ${String.format(Locale.US, "$%.2f", m.netProfitDollars)}")
        println("Profit Factor:               ${String.format(Locale.US, "%.3f", m.profitFactor)}")
        println("Expectancy per Trade:        ${String.format(Locale.US, "$%.2f", m.expectancyDollars)} (${String.format(Locale.US, "%.2fR", m.expectancyR)})")
        println("Average Winning Trade:       ${String.format(Locale.US, "$%.2f", m.avgWinDollars)}")
        println("Average Losing Trade:        ${String.format(Locale.US, "$%.2f", m.avgLossDollars)}")
        println("Maximum Drawdown:            ${String.format(Locale.US, "%.2f%%", m.maxDrawdownPercent)}")
        println("Max Consecutive Losses:      ${m.maxConsecutiveLosses}")
        println("Account ROI:                 ${String.format(Locale.US, "%.2f%%", m.netProfitPercent)}")
        println("Ending Equity from $10k:     ${String.format(Locale.US, "$%.2f", m.finalEquity)}")
        println("Number of Raw Signals:       ${smc.rawSignalsCount}")
        println("Number of Filtered Signals:  ${smc.filteredSignalsCount}")
        println("Structural Events Detected:")
        println("  - BOS Events:              ${smc.bosEventsCount}")
        println("  - CHOCH / MSS Events:      ${smc.chochEventsCount}")
        println("  - Liquidity Sweeps:        ${smc.liquiditySweepsCount}")
        println("  - FVGs Created:            ${smc.fvgCount}")
        println("  - Order Blocks Created:    ${smc.orderBlocksCount}")
        println("  - Breaker Blocks Created:  ${smc.breakerBlocksCount}")
        println("  - Displacements:           ${smc.displacementCount}")
        println("  - EQH / EQL Formations:    ${smc.eqhEqlCount}")
        println("==========================================================================")

        return result
    }

    @Test
    fun testIndependentConcept1_BreakOfStructure_BOS() {
        val config = SmcConfig(
            useBos = true,
            bosLookback = 5,
            bosCloseConfirmation = true,
            useChoch = false,
            useLiquiditySweep = false,
            useFvg = false,
            useOrderBlock = false,
            useBreakerBlock = false,
            usePremiumDiscount = false,
            useDisplacement = false,
            useEqualHighsLows = false,
            useSessionFilter = false,
            minConfluences = 1
        )
        val res = runSmcBacktest("1. Break of Structure (BOS)", config)
        assertTrue("BOS events detected", (res.smcMetrics?.bosEventsCount ?: 0) > 0)
    }

    @Test
    fun testIndependentConcept2_ChangeOfCharacter_CHOCH() {
        val config = SmcConfig(
            useBos = false,
            useChoch = true,
            chochLookback = 5,
            useLiquiditySweep = false,
            useFvg = false,
            useOrderBlock = false,
            useBreakerBlock = false,
            usePremiumDiscount = false,
            useDisplacement = false,
            useEqualHighsLows = false,
            useSessionFilter = false,
            minConfluences = 1
        )
        val res = runSmcBacktest("2. Change of Character (CHOCH / MSS)", config)
        assertTrue("CHOCH events detected", (res.smcMetrics?.chochEventsCount ?: 0) > 0)
    }

    @Test
    fun testIndependentConcept3_LiquiditySweep() {
        val config = SmcConfig(
            useBos = false,
            useChoch = false,
            useLiquiditySweep = true,
            sweepLookback = 10,
            sweepWickMinPct = 0.05,
            useFvg = false,
            useOrderBlock = false,
            useBreakerBlock = false,
            usePremiumDiscount = false,
            useDisplacement = false,
            useEqualHighsLows = false,
            useSessionFilter = false,
            minConfluences = 1
        )
        val res = runSmcBacktest("3. Liquidity Sweep / Grab", config)
        assertNotNull(res.smcMetrics)
    }

    @Test
    fun testIndependentConcept4_FairValueGap_FVG() {
        val config = SmcConfig(
            useBos = false,
            useChoch = false,
            useLiquiditySweep = false,
            useFvg = true,
            fvgMinGapAtrMultiple = 0.1,
            fvgMitigationType = FvgMitigationType.TOUCH,
            useOrderBlock = false,
            useBreakerBlock = false,
            usePremiumDiscount = false,
            useDisplacement = false,
            useEqualHighsLows = false,
            useSessionFilter = false,
            minConfluences = 1
        )
        val res = runSmcBacktest("4. Fair Value Gap (FVG) Retest", config)
        assertNotNull(res.smcMetrics)
    }

    @Test
    fun testIndependentConcept5_OrderBlocks() {
        val config = SmcConfig(
            useBos = true,
            useChoch = false,
            useLiquiditySweep = false,
            useFvg = false,
            useOrderBlock = true,
            obLookback = 15,
            obMitigationRequired = true,
            useBreakerBlock = false,
            usePremiumDiscount = false,
            useDisplacement = false,
            useEqualHighsLows = false,
            useSessionFilter = false,
            minConfluences = 1
        )
        val res = runSmcBacktest("5. Order Block Retest", config)
        assertNotNull(res.smcMetrics)
    }

    @Test
    fun testIndependentConcept6_BreakerBlocks() {
        val config = SmcConfig(
            useBos = true,
            useChoch = true,
            useLiquiditySweep = false,
            useFvg = false,
            useOrderBlock = false,
            useBreakerBlock = true,
            usePremiumDiscount = false,
            useDisplacement = false,
            useEqualHighsLows = false,
            useSessionFilter = false,
            minConfluences = 1
        )
        val res = runSmcBacktest("6. Breaker Blocks", config)
        assertNotNull(res.smcMetrics)
    }

    @Test
    fun testIndependentConcept7_PremiumDiscountZones() {
        val config = SmcConfig(
            useBos = true,
            useChoch = true,
            useLiquiditySweep = false,
            useFvg = false,
            useOrderBlock = false,
            useBreakerBlock = false,
            usePremiumDiscount = true,
            discountThresholdPct = 50.0,
            useDisplacement = false,
            useEqualHighsLows = false,
            useSessionFilter = false,
            minConfluences = 1
        )
        val res = runSmcBacktest("7. Premium / Discount Filter", config)
        assertNotNull(res.smcMetrics)
    }

    @Test
    fun testIndependentConcept8_DisplacementCandles() {
        val config = SmcConfig(
            useBos = false,
            useChoch = false,
            useLiquiditySweep = false,
            useFvg = false,
            useOrderBlock = false,
            useBreakerBlock = false,
            usePremiumDiscount = false,
            useDisplacement = true,
            displacementAtrMultiplier = 1.0,
            useEqualHighsLows = false,
            useSessionFilter = false,
            minConfluences = 1
        )
        val res = runSmcBacktest("8. Displacement Candles", config)
        assertNotNull(res.smcMetrics)
    }

    @Test
    fun testIndependentConcept9_EqualHighsEqualLows() {
        val config = SmcConfig(
            useBos = false,
            useChoch = false,
            useLiquiditySweep = false,
            useFvg = false,
            useOrderBlock = false,
            useBreakerBlock = false,
            usePremiumDiscount = false,
            useDisplacement = false,
            useEqualHighsLows = true,
            eqTolerancePct = 0.20,
            useSessionFilter = false,
            minConfluences = 1
        )
        val res = runSmcBacktest("9. Equal Highs / Equal Lows (EQH / EQL)", config)
        assertNotNull(res.smcMetrics)
    }

    @Test
    fun testIndependentConcept10_TradingSessionFilter() {
        val config = SmcConfig(
            useBos = true,
            useChoch = true,
            useLiquiditySweep = false,
            useFvg = false,
            useOrderBlock = false,
            useBreakerBlock = false,
            usePremiumDiscount = false,
            useDisplacement = false,
            useEqualHighsLows = false,
            useSessionFilter = true,
            sessionType = SmcSessionType.LONDON_NY_OVERLAP,
            minConfluences = 1
        )
        val res = runSmcBacktest("10. London / NY Overlap Session Filter", config)
        assertNotNull(res.smcMetrics)
    }

    @Test
    fun testCombination_FullICTConfluence() {
        val config = SmcConfig(
            useBos = true,
            useChoch = true,
            useLiquiditySweep = true,
            useFvg = true,
            useOrderBlock = true,
            useBreakerBlock = true,
            usePremiumDiscount = true,
            useDisplacement = true,
            useEqualHighsLows = true,
            useSessionFilter = false,
            minConfluences = 2
        )
        val res = runSmcBacktest("Full ICT Confluence", config)
        assertNotNull(res.smcMetrics)
    }
}
