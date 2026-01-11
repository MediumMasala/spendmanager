package com.spendmanager.app.ui.viewmodel

import android.app.Activity
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendmanager.app.data.local.AppDatabase
import com.spendmanager.app.data.local.PreferencesManager
import com.spendmanager.app.data.model.*
import com.spendmanager.app.data.remote.ApiService
import com.spendmanager.app.data.remote.FcmTokenManager
import com.spendmanager.app.data.remote.FirebaseAuthManager
import com.spendmanager.app.data.remote.FirebaseAuthState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val phoneNumber: String = "",
    val otp: String = "",
    val otpSent: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val loginSuccess: Boolean = false,
    val verificationId: String? = null
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val apiService: ApiService,
    private val preferences: PreferencesManager,
    private val database: AppDatabase,
    private val firebaseAuthManager: FirebaseAuthManager,
    private val fcmTokenManager: FcmTokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onPhoneNumberChange(phone: String) {
        _uiState.value = _uiState.value.copy(
            phoneNumber = phone.filter { it.isDigit() || it == '+' },
            error = null
        )
    }

    fun onOtpChange(otp: String) {
        _uiState.value = _uiState.value.copy(
            otp = otp.filter { it.isDigit() }.take(6),
            error = null
        )
    }

    fun requestOtp(activity: Activity) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val phone = normalizePhone(_uiState.value.phoneNumber)

            firebaseAuthManager.sendOtp(phone, activity).collect { state ->
                when (state) {
                    is FirebaseAuthState.Loading -> {
                        _uiState.value = _uiState.value.copy(isLoading = true)
                    }
                    is FirebaseAuthState.CodeSent -> {
                        _uiState.value = _uiState.value.copy(
                            otpSent = true,
                            isLoading = false,
                            phoneNumber = phone,
                            verificationId = state.verificationId
                        )
                    }
                    is FirebaseAuthState.AutoVerified -> {
                        // Auto-verification happened, sign in directly
                        _uiState.value = _uiState.value.copy(isLoading = true)
                        val result = firebaseAuthManager.signInWithCredential(state.credential)
                        handleFirebaseResult(result)
                    }
                    is FirebaseAuthState.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = state.message
                        )
                    }
                    else -> {}
                }
            }
        }
    }

    fun verifyOtp() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val result = firebaseAuthManager.verifyOtp(_uiState.value.otp)
            handleFirebaseResult(result)
        }
    }

    private suspend fun handleFirebaseResult(result: FirebaseAuthState) {
        when (result) {
            is FirebaseAuthState.Verified -> {
                // Firebase verification successful, now authenticate with our backend
                authenticateWithBackend(result.idToken)
            }
            is FirebaseAuthState.Error -> {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = result.message
                )
            }
            else -> {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Unexpected error. Please try again."
                )
            }
        }
    }

    private suspend fun authenticateWithBackend(firebaseIdToken: String) {
        try {
            val response = apiService.verifyFirebaseToken(
                FirebaseAuthRequest(
                    firebaseToken = firebaseIdToken,
                    deviceInfo = DeviceInfoRequest(
                        platform = "android",
                        appVersion = "1.0.0",
                        osVersion = "Android ${Build.VERSION.RELEASE}"
                    )
                )
            )

            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true && body.token != null) {
                    // Save auth info
                    preferences.saveAuthInfo(
                        token = body.token,
                        userId = body.userId ?: "",
                        deviceId = body.deviceId ?: ""
                    )

                    // Save device info to local DB
                    database.deviceInfoDao().save(
                        DeviceInfo(
                            deviceId = body.deviceId,
                            userId = body.userId,
                            token = body.token,
                            appVersion = "1.0.0",
                            osVersion = "Android ${Build.VERSION.RELEASE}",
                            registeredAt = System.currentTimeMillis()
                        )
                    )

                    // Initialize default app sources
                    initializeAppSources()

                    // Sync FCM token for push notifications
                    fcmTokenManager.syncToken()

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        loginSuccess = true
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = body?.message ?: "Authentication failed"
                    )
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Server error. Please try again."
                )
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = "Network error. Please try again."
            )
        }
    }

    fun resetOtp() {
        _uiState.value = LoginUiState(phoneNumber = _uiState.value.phoneNumber)
    }

    private fun normalizePhone(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        return when {
            phone.startsWith("+") -> phone
            digits.length == 10 -> "+91$digits"
            digits.length == 12 && digits.startsWith("91") -> "+$digits"
            else -> "+91$digits"
        }
    }

    private suspend fun initializeAppSources() {
        val defaultSources = listOf(
            AppSource("com.google.android.apps.nbu.paisa.user", "Google Pay", true, AppCategory.UPI),
            AppSource("com.phonepe.app", "PhonePe", true, AppCategory.UPI),
            AppSource("net.one97.paytm", "Paytm", true, AppCategory.UPI),
            AppSource("in.amazon.mShop.android.shopping", "Amazon Pay", true, AppCategory.UPI),
            AppSource("in.org.npci.upiapp", "BHIM", true, AppCategory.UPI),
            AppSource("com.csam.icici.bank.imobile", "iMobile (ICICI)", true, AppCategory.BANK),
            AppSource("com.hdfc.mobilebanking", "HDFC MobileBanking", true, AppCategory.BANK),
            AppSource("com.sbi.SBIFreedomPlus", "YONO SBI", true, AppCategory.BANK),
            AppSource("com.axis.mobile", "Axis Mobile", true, AppCategory.BANK),
            AppSource("com.kotak.mobile", "Kotak Mobile", true, AppCategory.BANK),
            AppSource("com.dreamplug.androidapp", "CRED", true, AppCategory.OTHER),
        )

        database.appSourceDao().insertAll(defaultSources)
    }
}
