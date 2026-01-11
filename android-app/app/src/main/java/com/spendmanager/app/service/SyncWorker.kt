package com.spendmanager.app.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.spendmanager.app.data.local.AppDatabase
import com.spendmanager.app.data.local.PreferencesManager
import com.spendmanager.app.data.model.*
import com.spendmanager.app.data.remote.ApiService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * WorkManager worker for periodic sync.
 *
 * Responsibilities:
 * 1. Upload pending events to backend
 * 2. Download recent transactions
 * 3. Download latest weekly summary
 *
 * Runs silently - NO notifications.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val apiService: ApiService,
    private val database: AppDatabase,
    private val preferences: PreferencesManager
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        android.util.Log.d(TAG, "SyncWorker started")
        return try {
            // Check if user is logged in
            val token = preferences.authTokenFlow.first()
            if (token == null) {
                android.util.Log.d(TAG, "User not logged in, skipping sync")
                return Result.success() // Not logged in, nothing to sync
            }
            android.util.Log.d(TAG, "User logged in, token present")

            // Check if cloud mode is enabled
            val consent = preferences.consentFlow.first()
            android.util.Log.d(TAG, "Consent state: cloudAiEnabled=${consent.cloudAiEnabled}, uploadRawEnabled=${consent.uploadRawEnabled}")
            if (!consent.cloudAiEnabled) {
                android.util.Log.d(TAG, "Cloud AI disabled, skipping sync")
                return Result.success() // Cloud mode disabled, skip sync
            }

            // 1. Upload pending events
            uploadPendingEvents()

            // 2. Download recent transactions
            downloadTransactions()

            // 3. Download latest summary
            downloadSummary()

            // Update last sync time
            preferences.updateLastSyncTime()

            Result.success()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Sync failed", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    private suspend fun uploadPendingEvents() {
        val pendingEvents = database.eventQueueDao().getPendingEvents(limit = 50)
        android.util.Log.d(TAG, "Found ${pendingEvents.size} pending events to upload")
        if (pendingEvents.isEmpty()) return

        // Mark as uploading
        val eventIds = pendingEvents.map { it.eventId }
        database.eventQueueDao().updateStatus(eventIds, UploadStatus.UPLOADING)

        try {
            val batch = EventBatch(
                events = pendingEvents.map { event ->
                    EventPayload(
                        eventId = event.eventId,
                        deviceId = event.deviceId,
                        appSource = event.appSource,
                        postedAt = Instant.ofEpochMilli(event.postedAt)
                            .toString(),
                        textRedacted = event.textRedacted,
                        textRaw = event.textRaw,
                        locale = event.locale,
                        timezone = event.timezone
                    )
                }
            )

            android.util.Log.d(TAG, "Uploading ${batch.events.size} events to backend...")
            val response = apiService.ingestEvents(batch)
            android.util.Log.d(TAG, "Upload response: ${response.code()} - ${response.message()}")

            if (response.isSuccessful) {
                val body = response.body()
                android.util.Log.d(TAG, "Upload success! Accepted: ${body?.accepted}, Duplicates: ${body?.duplicates}, Errors: ${body?.errors}")
                // Mark as uploaded
                database.eventQueueDao().updateStatus(eventIds, UploadStatus.UPLOADED)
                // Clean up uploaded events
                database.eventQueueDao().deleteUploaded()
            } else {
                android.util.Log.e(TAG, "Upload failed: ${response.code()} - ${response.errorBody()?.string()}")
                // Mark as failed for retry
                database.eventQueueDao().updateStatus(eventIds, UploadStatus.FAILED)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Upload exception: ${e.message}", e)
            // Mark as failed
            database.eventQueueDao().updateStatus(eventIds, UploadStatus.FAILED)
            throw e
        }
    }

    private suspend fun downloadTransactions() {
        try {
            val response = apiService.getRecentTransactions(limit = 50)
            if (response.isSuccessful) {
                val body = response.body() ?: return

                val transactions = body.transactions.map { tx ->
                    Transaction(
                        id = tx.id,
                        occurredAt = Instant.parse(tx.occurredAt).toEpochMilli(),
                        amount = tx.amount,
                        currency = tx.currency,
                        direction = TransactionDirection.valueOf(tx.direction),
                        instrument = tx.instrument,
                        merchant = tx.merchant,
                        payee = tx.payee,
                        category = tx.category,
                        appSource = tx.appSource
                    )
                }

                database.transactionDao().insertAll(transactions)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to download transactions", e)
            // Don't fail the whole sync for this
        }
    }

    private suspend fun downloadSummary() {
        try {
            val response = apiService.getLatestSummary()
            if (response.isSuccessful) {
                val body = response.body() ?: return

                val summary = WeeklySummary(
                    id = "${body.weekStart}_${body.weekEnd}",
                    weekStart = Instant.parse(body.weekStart).toEpochMilli(),
                    weekEnd = Instant.parse(body.weekEnd).toEpochMilli(),
                    totalSpent = body.totals.totalSpent,
                    totalReceived = body.totals.totalReceived,
                    netFlow = body.totals.netFlow,
                    transactionCount = body.transactionCount,
                    topMerchantsJson = com.google.gson.Gson().toJson(body.topMerchants),
                    categoriesJson = com.google.gson.Gson().toJson(body.categories)
                )

                database.weeklySummaryDao().insert(summary)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to download summary", e)
            // Don't fail the whole sync for this
        }
    }

    companion object {
        private const val TAG = "SyncWorker"
    }
}
