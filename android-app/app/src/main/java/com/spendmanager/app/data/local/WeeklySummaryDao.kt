package com.spendmanager.app.data.local

import androidx.room.*
import com.spendmanager.app.data.model.WeeklySummary
import kotlinx.coroutines.flow.Flow

@Dao
interface WeeklySummaryDao {
    @Query("SELECT * FROM weekly_summaries ORDER BY weekStart DESC LIMIT 1")
    suspend fun getLatest(): WeeklySummary?

    @Query("SELECT * FROM weekly_summaries ORDER BY weekStart DESC LIMIT 1")
    fun observeLatest(): Flow<WeeklySummary?>

    @Query("SELECT * FROM weekly_summaries ORDER BY weekStart DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 4): List<WeeklySummary>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(summary: WeeklySummary)

    @Query("DELETE FROM weekly_summaries")
    suspend fun deleteAll()
}
