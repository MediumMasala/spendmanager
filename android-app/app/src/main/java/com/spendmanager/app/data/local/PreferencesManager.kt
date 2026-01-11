package com.spendmanager.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.spendmanager.app.data.model.ConsentState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesManager(private val context: Context) {

    // Keys
    private object Keys {
        val LOCAL_ONLY_MODE = booleanPreferencesKey("local_only_mode")
        val CLOUD_AI_ENABLED = booleanPreferencesKey("cloud_ai_enabled")
        val UPLOAD_RAW_ENABLED = booleanPreferencesKey("upload_raw_enabled")
        val WHATSAPP_ENABLED = booleanPreferencesKey("whatsapp_enabled")

        val AUTH_TOKEN = stringPreferencesKey("auth_token")
        val USER_ID = stringPreferencesKey("user_id")
        val DEVICE_ID = stringPreferencesKey("device_id")

        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val LAST_SYNC_TIME = longPreferencesKey("last_sync_time")

        val FCM_TOKEN = stringPreferencesKey("fcm_token")
        val FCM_TOKEN_SYNCED = booleanPreferencesKey("fcm_token_synced")
    }

    // Consent state
    val consentFlow: Flow<ConsentState> = context.dataStore.data.map { prefs ->
        ConsentState(
            localOnlyMode = prefs[Keys.LOCAL_ONLY_MODE] ?: true,
            cloudAiEnabled = prefs[Keys.CLOUD_AI_ENABLED] ?: false,
            uploadRawEnabled = prefs[Keys.UPLOAD_RAW_ENABLED] ?: false,
            whatsappEnabled = prefs[Keys.WHATSAPP_ENABLED] ?: false
        )
    }

    suspend fun updateConsent(consent: ConsentState) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LOCAL_ONLY_MODE] = consent.localOnlyMode
            prefs[Keys.CLOUD_AI_ENABLED] = consent.cloudAiEnabled
            prefs[Keys.UPLOAD_RAW_ENABLED] = consent.uploadRawEnabled
            prefs[Keys.WHATSAPP_ENABLED] = consent.whatsappEnabled
        }
    }

    // Auth token
    val authTokenFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.AUTH_TOKEN]
    }

    suspend fun saveAuthInfo(token: String, userId: String, deviceId: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AUTH_TOKEN] = token
            prefs[Keys.USER_ID] = userId
            prefs[Keys.DEVICE_ID] = deviceId
        }
    }

    suspend fun clearAuthInfo() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.AUTH_TOKEN)
            prefs.remove(Keys.USER_ID)
            prefs.remove(Keys.DEVICE_ID)
        }
    }

    // User ID
    val userIdFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.USER_ID]
    }

    // Device ID
    val deviceIdFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.DEVICE_ID]
    }

    // Onboarding
    val onboardingCompletedFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.ONBOARDING_COMPLETED] ?: false
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ONBOARDING_COMPLETED] = completed
        }
    }

    // Last sync time
    val lastSyncTimeFlow: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[Keys.LAST_SYNC_TIME] ?: 0L
    }

    suspend fun updateLastSyncTime() {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_SYNC_TIME] = System.currentTimeMillis()
        }
    }

    // Check if logged in
    val isLoggedInFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.AUTH_TOKEN] != null
    }

    // FCM Token
    val fcmTokenFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.FCM_TOKEN]
    }

    val fcmTokenSyncedFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.FCM_TOKEN_SYNCED] ?: false
    }

    suspend fun saveFcmToken(token: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.FCM_TOKEN] = token
            prefs[Keys.FCM_TOKEN_SYNCED] = false // Mark as not synced
        }
    }

    suspend fun markFcmTokenSynced() {
        context.dataStore.edit { prefs ->
            prefs[Keys.FCM_TOKEN_SYNCED] = true
        }
    }

    // Clear all preferences
    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
