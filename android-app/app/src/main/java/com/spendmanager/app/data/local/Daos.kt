package com.spendmanager.app.data.local

import androidx.room.*
import com.spendmanager.app.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EventQueueDao {
    @Query("SELECT * FROM event_queue WHERE uploadStatus = :status ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getPendingEvents(status: UploadStatus = UploadStatus.PENDING, limit: Int = 50): List<EventQueueItem>

    @Query("SELECT COUNT(*) FROM event_queue WHERE uploadStatus = :status")
    suspend fun countPending(status: UploadStatus = UploadStatus.PENDING): Int

    @Query("SELECT COUNT(*) FROM event_queue WHERE uploadStatus = :status")
    fun observePendingCount(status: UploadStatus = UploadStatus.PENDING): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: EventQueueItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<EventQueueItem>)

    @Update
    suspend fun update(event: EventQueueItem)

    @Query("UPDATE event_queue SET uploadStatus = :status WHERE eventId IN (:eventIds)")
    suspend fun updateStatus(eventIds: List<String>, status: UploadStatus)

    @Query("DELETE FROM event_queue WHERE uploadStatus = :status")
    suspend fun deleteUploaded(status: UploadStatus = UploadStatus.UPLOADED)

    @Query("DELETE FROM event_queue")
    suspend fun deleteAll()

    @Query("SELECT * FROM event_queue WHERE eventId = :eventId")
    suspend fun getById(eventId: String): EventQueueItem?

    @Query("UPDATE event_queue SET retryCount = retryCount + 1, uploadStatus = :status WHERE eventId = :eventId")
    suspend fun incrementRetry(eventId: String, status: UploadStatus = UploadStatus.PENDING)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getRecent(limit: Int = 20, offset: Int = 0): List<Transaction>

    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC")
    fun observeAll(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE occurredAt >= :startTime AND occurredAt <= :endTime ORDER BY occurredAt DESC")
    suspend fun getInRange(startTime: Long, endTime: Long): List<Transaction>

    @Query("SELECT SUM(amount) FROM transactions WHERE direction = :direction AND occurredAt >= :startTime")
    suspend fun sumByDirection(direction: TransactionDirection, startTime: Long): Double?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<Transaction>)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int
}

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

@Dao
interface DeviceInfoDao {
    @Query("SELECT * FROM device_info WHERE id = 1")
    suspend fun get(): DeviceInfo?

    @Query("SELECT * FROM device_info WHERE id = 1")
    fun observe(): Flow<DeviceInfo?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(info: DeviceInfo)

    @Query("DELETE FROM device_info")
    suspend fun clear()
}

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
