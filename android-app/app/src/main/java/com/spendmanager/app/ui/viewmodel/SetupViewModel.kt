package com.spendmanager.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spendmanager.app.data.local.PreferencesManager
import com.spendmanager.app.data.model.ConsentState
import com.spendmanager.app.data.model.ConsentUpdateRequest
import com.spendmanager.app.data.remote.ApiService
import com.spendmanager.app.service.TransactionNotificationListener
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SetupUiState(
    val notificationListenerEnabled: Boolean = false,
    val consent: ConsentState = ConsentState(),
    val isLoading: Boolean = false
)

@HiltViewModel
class SetupViewModel @Inject constructor(
    application: Application,
    private val preferences: PreferencesManager,
    private val apiService: ApiService
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.consentFlow.collect { consent ->
                _uiState.value = _uiState.value.copy(consent = consent)
            }
        }

        // Check notification listener status periodically
        checkNotificationListener()
    }

    fun checkNotificationListener() {
        val enabled = TransactionNotificationListener.isEnabled(getApplication())
        _uiState.value = _uiState.value.copy(notificationListenerEnabled = enabled)
    }

    fun setLocalOnlyMode(localOnly: Boolean) {
        val newConsent = _uiState.value.consent.copy(
            localOnlyMode = localOnly,
            cloudAiEnabled = !localOnly
        )
        _uiState.value = _uiState.value.copy(consent = newConsent)
    }

    fun setUploadRaw(enabled: Boolean) {
        val newConsent = _uiState.value.consent.copy(uploadRawEnabled = enabled)
        _uiState.value = _uiState.value.copy(consent = newConsent)
    }

    fun setWhatsappEnabled(enabled: Boolean) {
        val newConsent = _uiState.value.consent.copy(whatsappEnabled = enabled)
        _uiState.value = _uiState.value.copy(consent = newConsent)
    }

    fun saveAndContinue() {
        viewModelScope.launch {
            val consent = _uiState.value.consent
            preferences.updateConsent(consent)

            // Sync to backend if cloud mode enabled
            if (consent.cloudAiEnabled) {
                try {
                    apiService.updateConsent(
                        ConsentUpdateRequest(
                            cloudAiEnabled = consent.cloudAiEnabled,
                            uploadRawEnabled = consent.uploadRawEnabled,
                            whatsappEnabled = consent.whatsappEnabled
                        )
                    )
                } catch (e: Exception) {
                    // Ignore network errors, consent saved locally
                }
            }
        }
    }
}
