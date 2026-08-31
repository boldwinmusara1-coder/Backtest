package com.example.tradestrat.data

import com.example.tradestrat.model.*
import java.util.Random
import kotlin.math.*

object MarketDataProvider {

    val ASSETS = listOf(
        // CRYPTO
        MarketAsset(
            id = "btc_usdt",
            symbol = "BTC/USDT",
            name = "Bitcoin",
            category = AssetCategory.CRYPTO,
            basePrice = 64200.0,
            description = "Benchmark decentralized cryptocurrency with 4-year halving cycles and high institutional liquidity",
            defaultDecimals = 1
        ),
        MarketAsset(
            id = "eth_usdt",
            symbol = "ETH/USDT",
            name = "Ethereum",
            category = AssetCategory.CRYPTO,
            basePrice = 3450.0,
            description = "Leading smart contract ecosystem and decentralized finance settlement layer",
            defaultDecimals = 2
        ),
        MarketAsset(
            id = "sol_usdt",
            symbol = "SOL/USDT",
            name = "Solana",
            category = AssetCategory.CRYPTO,
            basePrice = 148.0,
            description = "High-throughput Proof-of-History layer-1 blockchain with fast execution and aggressive beta",
            defaultDecimals = 2
        ),
        MarketAsset(
            id = "bnb_usdt",
            symbol = "BNB/USDT",
            name = "BNB Coin",
            category = AssetCategory.CRYPTO,
            basePrice = 585.0,
            description = "Native token of BNB Chain ecosystem with continuous quarterly burn tokenomics",
            defaultDecimals = 2
        ),
        MarketAsset(
            id = "xrp_usdt",
            symbol = "XRP/USDT",
            name = "XRP",
            category = AssetCategory.CRYPTO,
            basePrice = 0.5850,
            description = "Cross-border liquidity settlement network designed for financial institutions",
            defaultDecimals = 4
        ),

        // STOCKS
        MarketAsset(
            id = "nvda",
            symbol = "NVDA",
            name = "NVIDIA Corp",
            category = AssetCategory.STOCKS,
            basePrice = 124.50,
            description = "Global semiconductor and AI computing powerhouse with high explosive trend momentum",
            defaultDecimals = 2
        ),
        MarketAsset(
            id = "aapl",
            symbol = "AAPL",
            name = "Apple Inc",
            category = AssetCategory.STOCKS,
            basePrice = 224.00,
            description = "Consumer tech and services giant with massive cash reserves and steady trend persistence",
            defaultDecimals = 2
        ),
        MarketAsset(
            id = "msft",
            symbol = "MSFT",
            name = "Microsoft Corp",
            category = AssetCategory.STOCKS,
            basePrice = 445.00,
            description = "Enterprise software, cloud infrastructure, and AI enterprise workflow platform",
            defaultDecimals = 2
        ),
        MarketAsset(
            id = "tsla",
            symbol = "TSLA",
            name = "Tesla Inc",
            category = AssetCategory.STOCKS,
            basePrice = 215.00,
            description = "Electric vehicle and clean energy leader with high-beta momentum and retail participation",
            defaultDecimals = 2
        ),
        MarketAsset(
            id = "amzn",
            symbol = "AMZN",
            name = "Amazon.com Inc",
            category = AssetCategory.STOCKS,
            basePrice = 186.00,
            description = "Global e-commerce marketplace and AWS cloud computing leader",
            defaultDecimals = 2
        ),
        MarketAsset(
            id = "googl",
            symbol = "GOOGL",
            name = "Alphabet Inc",
            category = AssetCategory.STOCKS,
            basePrice = 178.50,
            description = "Search engine and generative intelligence enterprise leader",
            defaultDecimals = 2
        ),
        MarketAsset(
            id = "meta",
            symbol = "META",
            name = "Meta Platforms",
            category = AssetCategory.STOCKS,
            basePrice = 512.00,
            description = "Social connection technologies, digital advertising platform, and open-source AI developer",
            defaultDecimals = 2
        ),
        MarketAsset(
            id = "amd",
            symbol = "AMD",
            name = "Advanced Micro Devices",
            category = AssetCategory.STOCKS,
            basePrice = 156.00,
            description = "High-performance microprocessors, data center GPUs, and adaptive computing solutions",
            defaultDecimals = 2
        ),

        // INDICES
        MarketAsset(
            id = "spy",
            symbol = "SPY",
            name = "S&P 500 ETF",
            category = AssetCategory.INDICES,
            basePrice = 558.00,
            description = "Standard & Poor's 500 index tracking 500 large-cap US corporations",
            defaultDecimals = 2
        ),
        MarketAsset(
            id = "qqq",
            symbol = "QQQ",
            name = "Nasdaq 100 ETF",
            category = AssetCategory.INDICES,
            basePrice = 485.00,
            description = "Invesco QQQ trust tracking the top 100 non-financial tech-heavy innovators",
            defaultDecimals = 2
        ),
        MarketAsset(
            id = "dia",
            symbol = "DIA",
            name = "Dow Jones Industrial",
            category = AssetCategory.INDICES,
            basePrice = 405.00,
            description = "Benchmark tracking 30 blue-chip industrial US market leaders",
            defaultDecimals = 2
        ),
        MarketAsset(
            id = "iwm",
            symbol = "IWM",
            name = "Russell 2000 ETF",
            category = AssetCategory.INDICES,
            basePrice = 218.00,
            description = "Small-cap equity index sensitive to domestic monetary policy and economic cycles",
            defaultDecimals = 2
        ),

        // FOREX
        MarketAsset(
            id = "eur_usd",
            symbol = "EUR/USD",
            name = "Euro / US Dollar",
            category = AssetCategory.FOREX,
            basePrice = 1.0880,
            description = "Most actively traded global currency pair driven by ECB and Federal Reserve rate differentials",
            defaultDecimals = 4
        ),
        MarketAsset(
            id = "gbp_usd",
            symbol = "GBP/USD",
            name = "British Pound / USD",
            category = AssetCategory.FOREX,
            basePrice = 1.2950,
            description = "Cable pair reflecting UK economic data and Bank of England monetary policy",
            defaultDecimals = 4
        ),
        MarketAsset(
            id = "usd_jpy",
            symbol = "USD/JPY",
            name = "USD / Japanese Yen",
            category = AssetCategory.FOREX,
            basePrice = 154.50,
            description = "Major global carry-trade pair highly sensitive to Bank of Japan yield curve control",
            defaultDecimals = 2
        ),
        MarketAsset(
            id = "aud_usd",
            symbol = "AUD/USD",
            name = "Australian Dollar / USD",
            category = AssetCategory.FOREX,
            basePrice = 0.6680,
            description = "Commodity currency pair tied to global industrial demand and Australian terms of trade",
            defaultDecimals = 4
        ),
        MarketAsset(
            id = "usd_cad",
            symbol = "USD/CAD",
            name = "USD / Canadian Dollar",
            category = AssetCategory.FOREX,
            basePrice = 1.3650,
            description = "North American trade currency pair strongly correlated with crude oil pricing",
            defaultDecimals = 4
        ),

        // COMMODITIES
        MarketAsset(
            id = "xau_usd",
            symbol = "XAU/USD",
            name = "Gold Spot",
            category = AssetCategory.COMMODITIES,
            basePrice = 2410.0,
            description = "Benchmark monetary hedge against fiat devaluation and geopolitical risk",
            defaultDecimals = 2
        ),
        MarketAsset(
            id = "xag_usd",
            symbol = "XAG/USD",
            name = "Silver Spot",
            category = AssetCategory.COMMODITIES,
            basePrice = 29.50,
            description = "Precious and industrial metal with high volatility and solar/electronics manufacturing demand",
            defaultDecimals = 2
        ),
        MarketAsset(
            id = "usoil",
            symbol = "WTI/OIL",
            name = "Crude Oil (WTI)",
            category = AssetCategory.COMMODITIES,
            basePrice = 78.50,
            description = "West Texas Intermediate benchmark crude reflecting global energy supply and OPEC quotas",
            defaultDecimals = 2
        ),
        MarketAsset(
            id = "natgas",
            symbol = "NATGAS",
            name = "Natural Gas",
            category = AssetCategory.COMMODITIES,
            basePrice = 2.15,
            description = "Clean burning fuel commodity subject to seasonal storage and weather dynamics",
            defaultDecimals = 3
        ),

        // BONDS
        MarketAsset(
            id = "tlt",
            symbol = "TLT",
            name = "20+ Year Treasury ETF",
            category = AssetCategory.INDICES,
            basePrice = 96.00,
            description = "Long-duration US Treasury bond fund reflecting inflation expectations and yield curve dynamics",
            defaultDecimals = 2
        )
    )

    /**
     * Legacy demo generator kept solely for offline test fixtures.
     * Real backtests utilize MarketDataRepository with live API endpoints.
     */
    @Deprecated("Use MarketDataRepository to fetch authentic historical market data from real APIs.")
    fun generateHistoricalData(
        asset: MarketAsset,
        regime: MarketRegime,
        timeframe: Timeframe,
        barCount: Int = 300
    ): List<Candle> {
        val now = System.currentTimeMillis()
        val durationMs = barCount.toLong() * timeframe.minutes * 60 * 1000L
        return DemoMarketDataProvider.generateDemoCandles(
            asset = asset,
            timeframe = timeframe,
            startTimeMs = now - durationMs,
            endTimeMs = now,
            count = barCount
        )
    }
}
