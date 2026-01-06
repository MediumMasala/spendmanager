package com.spendmanager.app.data.local

import androidx.room.*
import com.spendmanager.app.data.model.AppSource
import kotlinx.coroutines.flow.Flow

@Dao
interface AppSourceDao {
    @Query("SELECT * FROM app_sources ORDER BY displayName ASC")
    fun observeAll(): Flow<List<AppSource>>

    @Query("SELECT * FROM app_sources WHERE enabled = 1")
    suspend fun getEnabled(): List<AppSource>

    @Query("SELECT packageName FROM app_sources WHERE enabled = 1")
    suspend fun getEnabledPackages(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sources: List<AppSource>)

    @Update
    suspend fun update(source: AppSource)

    @Query("UPDATE app_sources SET enabled = :enabled WHERE packageName = :packageName")
    suspend fun setEnabled(packageName: String, enabled: Boolean)

    @Query("DELETE FROM app_sources")
    suspend fun deleteAll()
}
