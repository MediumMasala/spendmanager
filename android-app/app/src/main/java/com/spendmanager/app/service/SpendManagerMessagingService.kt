package com.spendmanager.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.spendmanager.app.MainActivity
import com.spendmanager.app.R
import com.spendmanager.app.data.local.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Firebase Cloud Messaging service for receiving push notifications.
 *
 * Handles:
 * - New FCM token generation and updates
 * - Incoming push notifications from backend
 * - Permission reminder notifications
 */
class SpendManagerMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var preferences: PreferencesManager

    override fun onCreate() {
        super.onCreate()
        preferences = PreferencesManager(this)
        createNotificationChannel()
    }

    /**
     * Called when a new FCM token is generated.
     * This happens on first app launch or when the token is refreshed.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        android.util.Log.d(TAG, "New FCM token generated")

        // Store token locally for later upload
        serviceScope.launch {
            preferences.saveFcmToken(token)
        }
    }

    /**
     * Called when a message is received from FCM.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        android.util.Log.d(TAG, "FCM message received from: ${message.from}")

        // Handle data payload
        if (message.data.isNotEmpty()) {
            handleDataMessage(message.data)
        }

        // Handle notification payload (shown automatically if app is in background)
        message.notification?.let { notification ->
            showNotification(
                title = notification.title ?: "SpendManager",
                body = notification.body ?: "",
                data = message.data
            )
        }
    }

    private fun handleDataMessage(data: Map<String, String>) {
        when (data["type"]) {
            "permission_reminder" -> {
                // Check if notification listener is actually disabled
                if (!TransactionNotificationListener.isEnabled(this)) {
                    showPermissionReminderNotification()
                }
            }
            "weekly_summary" -> {
                showNotification(
                    title = data["title"] ?: "Weekly Summary Ready",
                    body = data["body"] ?: "Check your spending summary for this week",
                    data = data
                )
            }
            "update_available" -> {
                showNotification(
                    title = data["title"] ?: "Update Available",
                    body = data["body"] ?: "A new version of SpendManager is available",
                    data = data
                )
            }
            else -> {
                // Generic notification
                data["title"]?.let { title ->
                    showNotification(
                        title = title,
                        body = data["body"] ?: "",
                        data = data
                    )
                }
            }
        }
    }

    private fun showPermissionReminderNotification() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "setup")
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Notification Access Disabled")
            .setContentText("Tap to re-enable transaction tracking")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("SpendManager needs notification access to track your transactions. Tap to enable it in settings."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(PERMISSION_NOTIFICATION_ID, notification)
    }

    private fun showNotification(title: String, body: String, data: Map<String, String>) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            data.forEach { (key, value) -> putExtra(key, value) }
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SpendManager Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications from SpendManager"
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "FCMService"
        private const val CHANNEL_ID = "spendmanager_notifications"
        private const val PERMISSION_NOTIFICATION_ID = 1001
    }
}
