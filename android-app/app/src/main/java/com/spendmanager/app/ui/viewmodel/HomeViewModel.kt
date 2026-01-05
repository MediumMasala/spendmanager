package com.spendmanager.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendmanager.app.data.local.AppDatabase
import com.spendmanager.app.data.local.PreferencesManager
import com.spendmanager.app.data.model.Transaction
import com.spendmanager.app.data.model.TransactionDirection
import com.spendmanager.app.data.model.UploadStatus
import com.spendmanager.app.data.remote.ApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class HomeUiState(
    val weeklySpent: Double = 0.0,
    val weeklyReceived: Double = 0.0,
    val transactionCount: Int = 0,
    val recentTransactions: List<Transaction> = emptyList(),
    val pendingUploads: Int = 0,
    val isLoading: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val database: AppDatabase,
    private val apiService: ApiService,
    private val preferences: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadData()
        observePendingUploads()
    }

    private fun loadData() {
        viewModelScope.launch {
            // Get week start (Monday)
            val calendar = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val weekStart = calendar.timeInMillis

            // Load transactions from local DB
            database.transactionDao().observeAll().collect { transactions ->
                val weeklyTransactions = transactions.filter { it.occurredAt >= weekStart }

                val spent = weeklyTransactions
                    .filter { it.direction == TransactionDirection.DEBIT }
                    .sumOf { it.amount }

                val received = weeklyTransactions
                    .filter { it.direction == TransactionDirection.CREDIT }
                    .sumOf { it.amount }

                _uiState.value = _uiState.value.copy(
                    weeklySpent = spent,
                    weeklyReceived = received,
                    transactionCount = weeklyTransactions.size,
                    recentTransactions = transactions.take(10)
                )
            }
        }
    }

    private fun observePendingUploads() {
        viewModelScope.launch {
            database.eventQueueDao().observePendingCount(UploadStatus.PENDING).collect { count ->
                _uiState.value = _uiState.value.copy(pendingUploads = count)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                // Fetch latest transactions from API
                val response = apiService.getRecentTransactions(limit = 50)
                if (response.isSuccessful) {
                    val transactions = response.body()?.transactions?.map { tx ->
                        Transaction(
                            id = tx.id,
                            occurredAt = java.time.Instant.parse(tx.occurredAt).toEpochMilli(),
                            amount = tx.amount,
                            currency = tx.currency,
                            direction = TransactionDirection.valueOf(tx.direction),
                            instrument = tx.instrument,
                            merchant = tx.merchant,
                            payee = tx.payee,
                            category = tx.category,
                            appSource = tx.appSource
                        )
                    } ?: emptyList()

                    database.transactionDao().insertAll(transactions)
                }
            } catch (e: Exception) {
                // Handle error silently, data will be refreshed on next sync
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
}
