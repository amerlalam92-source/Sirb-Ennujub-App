package com.example.presentation.finance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.finance.FinanceRepository
import com.example.domain.finance.FinanceTransactionType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * نموذج العرض للشاشة الرئيسية للإدارة المالية (FinanceHomeViewModel)
 * يراقب تدفق البيانات اللحظي للملخص التراكمي، إجمالي الاشتراكات، المساهمات، والعمليات المالية.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FinanceHomeViewModel(
    private val repository: FinanceRepository
) : ViewModel() {

    private val _refreshTrigger = MutableStateFlow(0)

    fun refresh() {
        _refreshTrigger.value += 1
    }

    val totalOutstandingDebt: StateFlow<Int> = _refreshTrigger.flatMapLatest {
        repository.observeTotalOutstandingDebt()
            .catch { e ->
                if (e is CancellationException) throw e
                emit(0)
            }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val uiState: StateFlow<FinanceHomeUiState> = _refreshTrigger.flatMapLatest {
        combine(
            repository.observeSummary(),
            repository.observeAllSubscriptions(),
            repository.observeAllTransactions()
        ) { summary, subscriptions, transactions ->
            val activeTransactions = transactions.filter { !it.isRolledBack }
            val pendingSubsCount = subscriptions.count { it.debtAmount > 0 }
            val donations = activeTransactions
                .filter { it.type == FinanceTransactionType.DONATION }
                .sumOf { it.amount }
            val contribsPaid = activeTransactions
                .filter { it.type == FinanceTransactionType.CONTRIBUTION }
                .sumOf { it.amount }

            FinanceHomeUiState(
                summary = summary,
                pendingSubscriptionsCount = pendingSubsCount,
                pendingContributionsCount = 0,
                totalOutstandingDebt = summary.totalOutstandingDebt,
                totalDonations = donations,
                totalContributionsPaid = contribsPaid,
                isLoading = false,
                errorMessage = null
            )
        }.catch { e ->
            if (e is CancellationException) throw e
            emit(
                FinanceHomeUiState(
                    isLoading = false,
                    errorMessage = "فشل تحميل البيانات"
                )
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FinanceHomeUiState(isLoading = true)
    )

    class Factory(
        private val repository: FinanceRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FinanceHomeViewModel(repository) as T
        }
    }
}
