package com.spendmanager.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendmanager.app.data.local.AppDatabase
import com.spendmanager.app.data.model.Transaction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TransactionsUiState(
    val transactions: List<Transaction> = emptyList(),
    val isLoading: Boolean = false,
    val hasMore: Boolean = true,
    val offset: Int = 0
)

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val database: AppDatabase
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionsUiState())
    val uiState: StateFlow<TransactionsUiState> = _uiState.asStateFlow()

    private val pageSize = 20

    init {
        loadTransactions()
    }

    private fun loadTransactions() {
        viewModelScope.launch {
            database.transactionDao().observeAll().collect { transactions ->
                _uiState.value = _uiState.value.copy(
                    transactions = transactions,
                    hasMore = false // All loaded from local DB
                )
            }
        }
    }

    fun loadMore() {
        // For future implementation if we want to load from API with pagination
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val currentOffset = _uiState.value.offset
            val newTransactions = database.transactionDao()
                .getRecent(limit = pageSize, offset = currentOffset + pageSize)

            _uiState.value = _uiState.value.copy(
                transactions = _uiState.value.transactions + newTransactions,
                offset = currentOffset + pageSize,
                hasMore = newTransactions.size == pageSize,
                isLoading = false
            )
        }
    }
}
