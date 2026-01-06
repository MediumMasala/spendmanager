package com.spendmanager.app.data.local

import androidx.room.*
import com.spendmanager.app.data.model.EventQueueItem
import com.spendmanager.app.data.model.UploadStatus
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
