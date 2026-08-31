package com.example.tradestrat.model

enum class IndicatorType(val displayName: String, val shortName: String, val category: String) {
    SMA("Simple Moving Average", "SMA", "Trend"),
    EMA("Exponential Moving Average", "EMA", "Trend"),
    RSI("Relative Strength Index", "RSI", "Momentum"),
    MACD("Moving Average Convergence Divergence", "MACD", "Momentum"),
    BOLLINGER("Bollinger Bands", "BB", "Volatility"),
    ATR("Average True Range", "ATR", "Volatility"),
    SUPERTREND("Supertrend Indicator", "ST", "Trend"),
    DONCHIAN("Donchian Channels", "DC", "Breakout"),
    ORB("Opening Range Breakout", "ORB", "Breakout"),
    TRENDLINE("Swing Pivot Trendlines", "TL", "Price Action"),
    SMC("Smart Money Concepts", "SMC", "Price Action")
}

data class MovingAverageParams(
    val fastPeriod: Int = 9,
    val slowPeriod: Int = 21,
    val useEma: Boolean = true
)

data class RsiParams(
    val period: Int = 14,
    val oversoldThreshold: Double = 30.0,
    val overboughtThreshold: Double = 70.0
)

data class MacdParams(
    val fastPeriod: Int = 12,
    val slowPeriod: Int = 26,
    val signalPeriod: Int = 9
)

data class BollingerParams(
    val period: Int = 20,
    val stdDevMultiplier: Double = 2.0
)

data class SupertrendParams(
    val atrPeriod: Int = 10,
    val multiplier: Double = 3.0
)

data class DonchianParams(
    val period: Int = 20
)

data class OrbParams(
    val sessionTimezone: String = "America/New_York",
    val sessionStartHour: Int = 9,
    val sessionStartMinute: Int = 30,
    val openingRangeMinutes: Int = 30, // Duration of opening range in minutes (e.g. 15, 30, 45, 60)
    val sessionEndHour: Int = 16,
    val sessionEndMinute: Int = 0,
    val rangeBars: Int = 15, // Fallback bar count if intraday timestamp is not sub-hourly
    val volumeMultiplier: Double = 1.2,
    val useEmaTrendFilter: Boolean = true,
    val emaTrendPeriod: Int = 50,
    val useRsiFilter: Boolean = true,
    val rsiPeriod: Int = 14,
    val rsiThreshold: Double = 50.0,
    val breakoutBufferPct: Double = 0.05, // Validated optimal 0.05% breakout confirmation buffer
    val rsiLongThreshold: Double = 50.0,
    val rsiShortThreshold: Double = 50.0
)

data class TrendlineParams(
    val pivotStrength: Int = 5, // Requires 5 bars left and 5 bars right strictly confirmed
    val pivotLookback: Int = 10, // Legacy lookback compatibility
    val minTouches: Int = 2,
    val maxLineAge: Int = 120, // Max candle bars a trendline remains valid
    val confirmationThresholdPct: Double = 0.25, // % price confirmation beyond trendline
    val retestRequired: Boolean = false,
    val retestTolerancePct: Double = 0.4,
    val useRsiFilter: Boolean = true,
    val rsiPeriod: Int = 14,
    val rsiOversoldThreshold: Double = 35.0,
    val rsiOverboughtThreshold: Double = 65.0,
    val useMaTrendFilter: Boolean = true,
    val maTrendPeriod: Int = 50
)

data class IndicatorConfig(
    val maParams: MovingAverageParams = MovingAverageParams(),
    val rsiParams: RsiParams = RsiParams(),
    val macdParams: MacdParams = MacdParams(),
    val bollingerParams: BollingerParams = BollingerParams(),
    val supertrendParams: SupertrendParams = SupertrendParams(),
    val donchianParams: DonchianParams = DonchianParams(),
    val orbParams: OrbParams = OrbParams(),
    val trendlineParams: TrendlineParams = TrendlineParams(),
    val smcConfig: SmcConfig = SmcConfig(),
    val aPlusTrendlineConfig: APlusTrendlineConfig = APlusTrendlineConfig()
)
