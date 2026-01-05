package com.spendmanager.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendmanager.app.data.local.AppDatabase
import com.spendmanager.app.data.local.PreferencesManager
import com.spendmanager.app.data.model.ConsentState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainUiState(
    val isLoggedIn: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val consent: ConsentState = ConsentState(),
    val isLoading: Boolean = true
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val preferences: PreferencesManager,
    private val database: AppDatabase
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                preferences.isLoggedInFlow,
                preferences.onboardingCompletedFlow,
                preferences.consentFlow
            ) { isLoggedIn, onboardingCompleted, consent ->
                MainUiState(
                    isLoggedIn = isLoggedIn,
                    onboardingCompleted = onboardingCompleted,
                    consent = consent,
                    isLoading = false
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            preferences.setOnboardingCompleted(true)
        }
    }

    fun updateConsent(consent: ConsentState) {
        viewModelScope.launch {
            preferences.updateConsent(consent)
        }
    }

    fun logout() {
        viewModelScope.launch {
            preferences.clearAll()
            database.eventQueueDao().deleteAll()
            database.transactionDao().deleteAll()
            database.weeklySummaryDao().deleteAll()
            database.deviceInfoDao().clear()
        }
    }
}
