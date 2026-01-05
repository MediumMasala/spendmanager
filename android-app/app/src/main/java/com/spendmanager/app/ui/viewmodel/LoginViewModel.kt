package com.spendmanager.app.ui.viewmodel

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendmanager.app.data.local.AppDatabase
import com.spendmanager.app.data.local.PreferencesManager
import com.spendmanager.app.data.model.*
import com.spendmanager.app.data.remote.ApiService
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
    val loginSuccess: Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val apiService: ApiService,
    private val preferences: PreferencesManager,
    private val database: AppDatabase
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

    fun requestOtp() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val phone = normalizePhone(_uiState.value.phoneNumber)
                val response = apiService.requestOtp(OtpRequest(phone = phone))

                if (response.isSuccessful && response.body()?.success == true) {
                    _uiState.value = _uiState.value.copy(
                        otpSent = true,
                        isLoading = false,
                        phoneNumber = phone
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = response.body()?.message ?: "Failed to send OTP"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Network error. Please try again."
                )
            }
        }
    }

    fun verifyOtp() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val response = apiService.verifyOtp(
                    VerifyOtpRequest(
                        phone = _uiState.value.phoneNumber,
                        otp = _uiState.value.otp,
                        deviceInfo = DeviceInfoRequest(
                            platform = "android",
                            appVersion = "1.0.0", // TODO: Get from BuildConfig
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

                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            loginSuccess = true
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = body?.message ?: "Verification failed"
                        )
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Verification failed. Please try again."
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Network error. Please try again."
                )
            }
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
