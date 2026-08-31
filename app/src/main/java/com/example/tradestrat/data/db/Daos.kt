package com.example.tradestrat.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StrategyDao {
    @Query("SELECT * FROM strategies ORDER BY createdAt DESC")
    fun getAllStrategies(): Flow<List<StrategyEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStrategy(strategy: StrategyEntity)

    @Query("DELETE FROM strategies WHERE id = :id")
    suspend fun deleteStrategyById(id: String)
}

@Dao
interface SavedBacktestDao {
    @Query("SELECT * FROM saved_backtests ORDER BY createdAt DESC")
    fun getAllBacktests(): Flow<List<SavedBacktestEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBacktest(backtest: SavedBacktestEntity)

    @Query("DELETE FROM saved_backtests WHERE id = :id")
    suspend fun deleteBacktestById(id: String)
}
