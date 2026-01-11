package com.spendmanager.app.data.remote

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import com.spendmanager.app.data.local.PreferencesManager
import com.spendmanager.app.data.model.FcmTokenRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages FCM token retrieval and synchronization with backend.
 */
@Singleton
class FcmTokenManager @Inject constructor(
    private val apiService: ApiService,
    private val preferencesManager: PreferencesManager
) {
    /**
     * Get the current FCM token, fetching a new one if necessary.
     */
    suspend fun getToken(): String? {
        return try {
            FirebaseMessaging.getInstance().token.await()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to get FCM token", e)
            null
        }
    }

    /**
     * Sync the FCM token with the backend.
     * Should be called after login and whenever the token changes.
     */
    suspend fun syncToken(): Boolean {
        return try {
            val token = getToken() ?: return false
            val deviceId = preferencesManager.deviceIdFlow.first()

            // Save locally first
            preferencesManager.saveFcmToken(token)

            // Sync with backend
            val response = apiService.updateFcmToken(
                FcmTokenRequest(
                    fcmToken = token,
                    deviceId = deviceId
                )
            )

            if (response.isSuccessful && response.body()?.success == true) {
                preferencesManager.markFcmTokenSynced()
                android.util.Log.d(TAG, "FCM token synced successfully")
                true
            } else {
                android.util.Log.e(TAG, "Failed to sync FCM token: ${response.code()}")
                false
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error syncing FCM token", e)
            false
        }
    }

    /**
     * Check if token needs to be synced and sync if necessary.
     */
    suspend fun syncIfNeeded(): Boolean {
        val isSynced = preferencesManager.fcmTokenSyncedFlow.first()
        return if (!isSynced) {
            syncToken()
        } else {
            true
        }
    }

    companion object {
        private const val TAG = "FcmTokenManager"
    }
}
