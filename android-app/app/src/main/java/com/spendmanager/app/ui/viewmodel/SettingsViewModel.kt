package com.spendmanager.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendmanager.app.data.local.AppDatabase
import com.spendmanager.app.data.local.PreferencesManager
import com.spendmanager.app.data.model.ConsentState
import com.spendmanager.app.data.model.ConsentUpdateRequest
import com.spendmanager.app.data.remote.ApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val consent: ConsentState = ConsentState(),
    val isLoading: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: PreferencesManager,
    private val apiService: ApiService,
    private val database: AppDatabase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.consentFlow.collect { consent ->
                _uiState.value = _uiState.value.copy(consent = consent)
            }
        }
    }

    fun setCloudAiEnabled(enabled: Boolean) {
        updateConsent(_uiState.value.consent.copy(
            cloudAiEnabled = enabled,
            localOnlyMode = !enabled
        ))
    }

    fun setUploadRawEnabled(enabled: Boolean) {
        updateConsent(_uiState.value.consent.copy(uploadRawEnabled = enabled))
    }

    fun setWhatsappEnabled(enabled: Boolean) {
        updateConsent(_uiState.value.consent.copy(whatsappEnabled = enabled))
    }

    private fun updateConsent(consent: ConsentState) {
        viewModelScope.launch {
            preferences.updateConsent(consent)

            // Sync to backend
            try {
                apiService.updateConsent(
                    ConsentUpdateRequest(
                        cloudAiEnabled = consent.cloudAiEnabled,
                        uploadRawEnabled = consent.uploadRawEnabled,
                        whatsappEnabled = consent.whatsappEnabled
                    )
                )
            } catch (e: Exception) {
                // Ignore network errors
            }
        }
    }

    fun exportData() {
        viewModelScope.launch {
            // TODO: Implement export - either via API or local CSV generation
        }
    }

    fun deleteAllData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                // Delete from backend
                apiService.deleteUser()
            } catch (e: Exception) {
                // Continue with local deletion
            }

            // Clear local data
            database.eventQueueDao().deleteAll()
            database.transactionDao().deleteAll()
            database.weeklySummaryDao().deleteAll()
            database.deviceInfoDao().clear()
            database.appSourceDao().deleteAll()
            preferences.clearAll()

            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }
}
