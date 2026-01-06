package com.spendmanager.app.data.local

import androidx.room.*
import com.spendmanager.app.data.model.Transaction
import com.spendmanager.app.data.model.TransactionDirection
import kotlinx.coroutines.flow.Flow

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
