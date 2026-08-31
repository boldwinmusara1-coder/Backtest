package com.example.tradestrat.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "strategies")
data class StrategyEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val strategyType: String,
    // MA params
    val fastMaPeriod: Int,
    val slowMaPeriod: Int,
    val useEma: Boolean,
    // RSI params
    val rsiPeriod: Int,
    val rsiOversold: Double,
    val rsiOverbought: Double,
    // MACD params
    val macdFast: Int,
    val macdSlow: Int,
    val macdSignal: Int,
    // Bollinger params
    val bbPeriod: Int,
    val bbStdDev: Double,
    // Supertrend params
    val stAtrPeriod: Int,
    val stMultiplier: Double,
    // Donchian
    val donchianPeriod: Int,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "saved_backtests")
data class SavedBacktestEntity(
    @PrimaryKey val id: String,
    val strategyName: String,
    val strategyType: String,
    val assetSymbol: String,
    val regimeName: String,
    val timeframe: String,
    val initialCapital: Double,
    val finalEquity: Double,
    val netProfitPercent: Double,
    val benchmarkReturnPercent: Double,
    val winRatePercent: Double,
    val totalTrades: Int,
    val profitFactor: Double,
    val maxDrawdownPercent: Double,
    val sharpeRatio: Double,
    val createdAt: Long = System.currentTimeMillis()
)
