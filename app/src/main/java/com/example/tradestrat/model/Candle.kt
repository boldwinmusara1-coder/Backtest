package com.example.tradestrat.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * OHLCV Candlestick data model.
 */
data class Candle(
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
) {
    val isBullish: Boolean get() = close >= open
    val changePct: Double get() = if (open > 0) ((close - open) / open) * 100 else 0.0
    val bodyHeight: Double get() = Math.abs(close - open)
    val upperWick: Double get() = high - Math.max(open, close)
    val lowerWick: Double get() = Math.min(open, close) - low

    fun formattedDate(timeFrameMinutes: Int = 1440): String {
        val pattern = if (timeFrameMinutes >= 1440) "MMM dd, yyyy" else "MMM dd, HH:mm"
        return SimpleDateFormat(pattern, Locale.US).format(Date(timestamp))
    }
}

enum class AssetCategory(val label: String) {
    CRYPTO("Crypto"),
    STOCKS("Stocks"),
    INDICES("Indices"),
    FOREX("Forex"),
    COMMODITIES("Commodities")
}

data class MarketAsset(
    val id: String,
    val symbol: String,
    val name: String,
    val category: AssetCategory,
    val basePrice: Double,
    val description: String,
    val defaultDecimals: Int = 2
)

enum class MarketRegime(val title: String, val description: String) {
    HISTORICAL_REALISTIC("Full Multi-Year Cycle", "Authentic multi-regime historical market behavior with trends, corrections, and consolidation"),
    STRONG_BULL("Mega Bull Trend", "Sustained upward momentum with higher highs, shallow pullbacks, and expanding volume"),
    BEAR_MARKET("Brutal Bear Market", "Protracted downtrend with panic sell-offs, lower lows, and bear market rallies"),
    CHOPPY_RANGE("High-Chop Sideways Range", "Mean-reverting consolidation range testing support & resistance levels"),
    VOLATILE_CRASH_RECOVERY("Flash Crash & V-Recovery", "Sudden high-volatility liquidity collapse followed by sharp mean reversion"),
    BREAKOUT_MOMENTUM("Squeeze & Breakout", "Tight volatility compression transitioning into violent explosive directional expansion")
}

enum class Timeframe(val label: String, val minutes: Int) {
    M5("5m", 5),
    M15("15m", 15),
    M30("30m", 30),
    H1("1h", 60),
    H4("4h", 240),
    D1("1D", 1440)
}
