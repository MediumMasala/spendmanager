package com.spendmanager.app.service

import android.app.Service
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.IBinder
import android.util.Log
import com.spendmanager.app.BuildConfig
import com.spendmanager.app.data.local.AppDatabase
import com.spendmanager.app.data.local.PreferencesManager
import com.spendmanager.app.data.model.EventBatch
import com.spendmanager.app.data.model.EventPayload
import com.spendmanager.app.data.remote.ApiService
import com.spendmanager.app.util.Redactor
import com.spendmanager.app.util.SmsTransactionParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Service to read existing SMS messages from the inbox and send transaction SMS to the API.
 *
 * This allows backfilling historical transaction data when the user first enables SMS permissions.
 * Only transaction-related SMS are processed and sent to the API.
 *
 * IMPORTANT: This service only processes SMS from recognized bank/UPI sender IDs.
 */
class SmsReaderService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isRunning = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isRunning) {
            Log.d(TAG, "Service already running, skipping")
            return START_NOT_STICKY
        }

        val daysToRead = intent?.getIntExtra(EXTRA_DAYS_TO_READ, DEFAULT_DAYS_TO_READ) ?: DEFAULT_DAYS_TO_READ

        isRunning = true
        serviceScope.launch {
            try {
                readAndUploadSms(daysToRead)
            } catch (e: Exception) {
                Log.e(TAG, "Error reading SMS", e)
            } finally {
                isRunning = false
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private suspend fun readAndUploadSms(daysToRead: Int) {
        Log.d(TAG, "Starting SMS read for last $daysToRead days")

        val database = AppDatabase.getInstance(this)
        val preferences = PreferencesManager(this)
        val consent = preferences.consentFlow.first()

        // Only process if cloud mode is enabled
        if (!consent.cloudAiEnabled) {
            Log.d(TAG, "Cloud mode disabled, skipping SMS import")
            return
        }

        // Get auth info
        val deviceInfo = database.deviceInfoDao().get()
        val deviceId = deviceInfo?.deviceId
        val authToken = preferences.authTokenFlow.first()

        if (deviceId == null || authToken == null) {
            Log.w(TAG, "Device not registered or not authenticated")
            return
        }

        // Create API service
        val apiService = createApiService(authToken)

        // Calculate time threshold
        val cutoffTime = System.currentTimeMillis() - (daysToRead * 24 * 60 * 60 * 1000L)

        // Read SMS from inbox
        val transactionMessages = readTransactionSms(contentResolver, cutoffTime)
        Log.d(TAG, "Found ${transactionMessages.size} transaction SMS")

        if (transactionMessages.isEmpty()) {
            Log.d(TAG, "No transaction SMS to upload")
            return
        }

        // Convert to event payloads
        val shouldUploadRaw = consent.uploadRawEnabled
        val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val events = transactionMessages.map { sms ->
            EventPayload(
                eventId = UUID.randomUUID().toString(),
                deviceId = deviceId,
                appSource = SmsTransactionParser.getSenderType(sms.sender),
                postedAt = isoDateFormat.format(Date(sms.timestamp)),
                textRedacted = Redactor.redact(sms.body),
                textRaw = if (shouldUploadRaw) sms.body else null,
                locale = "en-IN",
                timezone = "Asia/Kolkata"
            )
        }

        // Upload in batches of 50
        val batchSize = 50
        var uploaded = 0
        var failed = 0

        events.chunked(batchSize).forEach { batch ->
            try {
                val response = apiService.ingestEvents(EventBatch(batch))
                if (response.isSuccessful) {
                    val result = response.body()
                    uploaded += result?.accepted ?: 0
                    Log.d(TAG, "Uploaded batch: ${result?.accepted} accepted, ${result?.duplicates} duplicates")
                } else {
                    failed += batch.size
                    Log.e(TAG, "Failed to upload batch: ${response.code()}")
                }
            } catch (e: Exception) {
                failed += batch.size
                Log.e(TAG, "Network error uploading batch", e)
            }
        }

        Log.d(TAG, "SMS import complete: $uploaded uploaded, $failed failed")

        // Notify completion via broadcast
        val resultIntent = Intent(ACTION_SMS_IMPORT_COMPLETE).apply {
            putExtra(EXTRA_UPLOADED_COUNT, uploaded)
            putExtra(EXTRA_FAILED_COUNT, failed)
            putExtra(EXTRA_TOTAL_COUNT, transactionMessages.size)
        }
        sendBroadcast(resultIntent)
    }

    private fun readTransactionSms(contentResolver: ContentResolver, cutoffTime: Long): List<SmsMessage> {
        val messages = mutableListOf<SmsMessage>()

        val uri = Uri.parse("content://sms/inbox")
        val projection = arrayOf("_id", "address", "body", "date")
        val selection = "date > ?"
        val selectionArgs = arrayOf(cutoffTime.toString())
        val sortOrder = "date DESC"

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)

            cursor?.let {
                val addressIndex = it.getColumnIndex("address")
                val bodyIndex = it.getColumnIndex("body")
                val dateIndex = it.getColumnIndex("date")

                while (it.moveToNext()) {
                    val sender = it.getString(addressIndex) ?: continue
                    val body = it.getString(bodyIndex) ?: continue
                    val timestamp = it.getLong(dateIndex)

                    // Only include transaction SMS
                    if (SmsTransactionParser.isTransactionSms(sender, body)) {
                        messages.add(SmsMessage(sender, body, timestamp))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading SMS inbox", e)
        } finally {
            cursor?.close()
        }

        return messages
    }

    private fun createApiService(authToken: String): ApiService {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $authToken")
                    .addHeader("Content-Type", "application/json")
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL + "/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(ApiService::class.java)
    }

    private data class SmsMessage(
        val sender: String,
        val body: String,
        val timestamp: Long
    )

    companion object {
        private const val TAG = "SmsReaderService"
        const val EXTRA_DAYS_TO_READ = "days_to_read"
        const val DEFAULT_DAYS_TO_READ = 30
        const val ACTION_SMS_IMPORT_COMPLETE = "com.spendmanager.app.SMS_IMPORT_COMPLETE"
        const val EXTRA_UPLOADED_COUNT = "uploaded_count"
        const val EXTRA_FAILED_COUNT = "failed_count"
        const val EXTRA_TOTAL_COUNT = "total_count"

        /**
         * Start the SMS reader service.
         *
         * @param context Application context
         * @param daysToRead Number of days of SMS history to read (default: 30)
         */
        fun start(context: Context, daysToRead: Int = DEFAULT_DAYS_TO_READ) {
            val intent = Intent(context, SmsReaderService::class.java).apply {
                putExtra(EXTRA_DAYS_TO_READ, daysToRead)
            }
            context.startService(intent)
        }
    }
}
