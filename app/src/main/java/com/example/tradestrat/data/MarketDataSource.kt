package com.example.tradestrat.data

import com.example.tradestrat.model.Candle
import com.example.tradestrat.model.MarketAsset
import com.example.tradestrat.model.Timeframe

/**
 * Common interface for real market data providers.
 */
interface MarketDataSource {
    val providerName: String
    val supportsApiKey: Boolean
    
    suspend fun fetchHistoricalCandles(
        asset: MarketAsset,
        timeframe: Timeframe,
        startTimeMs: Long,
        endTimeMs: Long,
        apiKey: String? = null
    ): Result<List<Candle>>

    fun supportsAsset(asset: MarketAsset): Boolean
}
