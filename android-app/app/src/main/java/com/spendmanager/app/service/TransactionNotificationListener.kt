package com.spendmanager.app.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.spendmanager.app.data.local.AppDatabase
import com.spendmanager.app.data.local.PreferencesManager
import com.spendmanager.app.data.model.EventQueueItem
import com.spendmanager.app.data.model.UploadStatus
import com.spendmanager.app.util.Redactor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * NotificationListenerService that captures transaction notifications.
 *
 * This service:
 * - Listens to notifications from user-selected financial apps
 * - Filters for likely transaction notifications
 * - Redacts sensitive information on-device
 * - Queues events for upload (if cloud mode enabled)
 *
 * NO foreground notification is shown (silent operation).
 * Play Store compliant - uses only NotificationListener permission.
 */
class TransactionNotificationListener : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var database: AppDatabase
    private lateinit var preferences: PreferencesManager

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        preferences = PreferencesManager(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        serviceScope.launch {
            processNotification(sbn)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // We don't need to do anything when notifications are removed
    }

    private suspend fun processNotification(sbn: StatusBarNotification) {
        try {
            val packageName = sbn.packageName
            android.util.Log.d(TAG, "Notification from: $packageName")

            // Check if this app is in our enabled list
            val enabledPackages = database.appSourceDao().getEnabledPackages()
            android.util.Log.d(TAG, "Enabled packages: $enabledPackages")
            if (packageName !in enabledPackages) {
                android.util.Log.d(TAG, "Package $packageName not in enabled list, skipping")
                return
            }

            // Extract notification text
            val extras = sbn.notification.extras
            val title = extras.getCharSequence("android.title")?.toString() ?: ""
            val text = extras.getCharSequence("android.text")?.toString() ?: ""
            val bigText = extras.getCharSequence("android.bigText")?.toString()

            // Use bigText if available, otherwise combine title + text
            val fullText = bigText ?: "$title $text".trim()
            android.util.Log.d(TAG, "Notification text: $fullText")

            if (fullText.isBlank()) {
                android.util.Log.d(TAG, "Empty notification, skipping")
                return
            }

            // Check if this looks like a transaction
            if (!Redactor.isLikelyTransaction(fullText)) {
                android.util.Log.d(TAG, "Not a transaction, skipping. Text: $fullText")
                return
            }
            android.util.Log.d(TAG, "Transaction detected! Processing...")

            // Get consent state
            val consent = preferences.consentFlow.first()

            // If local-only mode, we still store locally for potential future upload
            // but mark it appropriately
            val shouldUploadRaw = consent.cloudAiEnabled && consent.uploadRawEnabled

            // Redact the text
            val redactedText = Redactor.redact(fullText)

            // Get device info
            val deviceInfo = database.deviceInfoDao().get()
            val deviceId = deviceInfo?.deviceId ?: return // Not registered yet

            // Create event
            val event = EventQueueItem(
                eventId = UUID.randomUUID().toString(),
                deviceId = deviceId,
                appSource = Redactor.getAppSourceFromPackage(packageName),
                postedAt = sbn.postTime,
                textRedacted = redactedText,
                textRaw = if (shouldUploadRaw) fullText else null,
                locale = "en-IN",
                timezone = "Asia/Kolkata",
                uploadStatus = if (consent.cloudAiEnabled) {
                    UploadStatus.PENDING
                } else {
                    UploadStatus.PENDING // Store but won't upload until cloud mode enabled
                }
            )

            // Save to database
            database.eventQueueDao().insert(event)
            android.util.Log.d(TAG, "Event saved! ID: ${event.eventId}, Text: ${event.textRedacted}")

            // Trigger immediate sync if cloud mode is enabled
            if (consent.cloudAiEnabled) {
                triggerImmediateSync()
            }

        } catch (e: Exception) {
            // Log error but don't crash the service
            android.util.Log.e(TAG, "Error processing notification", e)
        }
    }

    private fun triggerImmediateSync() {
        try {
            val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setInitialDelay(5, TimeUnit.SECONDS) // Small delay to batch multiple events
                .build()
            WorkManager.getInstance(applicationContext).enqueue(syncRequest)
            android.util.Log.d(TAG, "Immediate sync triggered")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to trigger sync", e)
        }
    }

    companion object {
        private const val TAG = "TxNotificationListener"

        /**
         * Check if notification listener permission is enabled.
         */
        fun isEnabled(context: android.content.Context): Boolean {
            val enabledListeners = android.provider.Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            )
            return enabledListeners?.contains(context.packageName) == true
        }

        /**
         * Get intent to open notification listener settings.
         */
        fun getSettingsIntent(): android.content.Intent {
            return android.content.Intent(
                android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
            )
        }
    }
}
