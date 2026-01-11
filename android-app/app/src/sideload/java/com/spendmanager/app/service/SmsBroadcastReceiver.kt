package com.spendmanager.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
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
 * BroadcastReceiver for incoming SMS messages.
 *
 * This is ONLY available in the sideload build flavor.
 * SMS messages are sent DIRECTLY to the API - no local storage.
 *
 * OnePlus/OxygenOS specific considerations:
 * - Uses high priority (999) to receive SMS before other apps
 * - Processes SMS in background coroutine to avoid ANR
 * - Designed to work with OxygenOS aggressive battery management
 */
class SmsBroadcastReceiver : BroadcastReceiver() {

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) {
            return
        }

        // Combine multi-part SMS
        val fullMessage = StringBuilder()
        var sender: String? = null
        var timestamp: Long = System.currentTimeMillis()

        messages.forEach { sms ->
            fullMessage.append(sms.messageBody)
            if (sender == null) {
                sender = sms.originatingAddress
                timestamp = sms.timestampMillis
            }
        }

        val messageText = fullMessage.toString()
        val senderAddress = sender ?: "unknown"

        // Process asynchronously to avoid ANR
        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                processSmsAndSendToApi(context, senderAddress, messageText, timestamp)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing SMS", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun processSmsAndSendToApi(
        context: Context,
        sender: String,
        messageText: String,
        timestamp: Long
    ) {
        // Check if this looks like a transaction SMS
        if (!SmsTransactionParser.isTransactionSms(sender, messageText)) {
            Log.d(TAG, "SMS from $sender is not a transaction, skipping")
            return
        }

        Log.d(TAG, "Processing transaction SMS from $sender")

        val database = AppDatabase.getInstance(context)
        val preferences = PreferencesManager(context)
        val consent = preferences.consentFlow.first()

        // Only process if cloud mode is enabled
        if (!consent.cloudAiEnabled) {
            Log.d(TAG, "Cloud mode disabled, skipping SMS upload")
            return
        }

        // Get device info
        val deviceInfo = database.deviceInfoDao().get()
        val deviceId = deviceInfo?.deviceId
        val authToken = preferences.authTokenFlow.first()

        if (deviceId == null || authToken == null) {
            Log.w(TAG, "Device not registered or not authenticated, skipping SMS processing")
            return
        }

        // Create API service
        val apiService = createApiService(authToken)

        // Determine if we should upload raw text
        val shouldUploadRaw = consent.uploadRawEnabled

        // Redact sensitive information
        val redactedText = Redactor.redact(messageText)

        // Format timestamp to ISO 8601
        val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val postedAtIso = isoDateFormat.format(Date(timestamp))

        // Create event payload
        val eventPayload = EventPayload(
            eventId = UUID.randomUUID().toString(),
            deviceId = deviceId,
            appSource = SmsTransactionParser.getSenderType(sender),
            postedAt = postedAtIso,
            textRedacted = redactedText,
            textRaw = if (shouldUploadRaw) messageText else null,
            locale = "en-IN",
            timezone = "Asia/Kolkata"
        )

        // Send directly to API
        try {
            val response = apiService.ingestEvents(EventBatch(listOf(eventPayload)))
            if (response.isSuccessful) {
                Log.d(TAG, "SMS sent to API successfully: ${response.body()?.accepted} accepted")
            } else {
                Log.e(TAG, "Failed to send SMS to API: ${response.code()} - ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error sending SMS to API", e)
        }
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

    companion object {
        private const val TAG = "SmsBroadcastReceiver"
    }
}
