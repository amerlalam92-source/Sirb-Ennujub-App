package com.example.presentation.finance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.finance.FinanceRepository
import com.example.domain.finance.FinanceTransactionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * نموذج العرض للشاشة الرئيسية للإدارة المالية (FinanceHomeViewModel)
 * يراقب تدفق البيانات اللحظي للملخص التراكمي، إجمالي الاشتراكات، المساهمات، والعمليات المالية.
 */
class FinanceHomeViewModel(
    private val repository: FinanceRepository
) : ViewModel() {

    private val _refreshTrigger = MutableStateFlow(0)

    fun refresh() {
        _refreshTrigger.value += 1
    }

    val totalOutstandingDebt: StateFlow<Int> = combine(
        _refreshTrigger,
        repository.observeTotalOutstandingDebt()
    ) { _, debt ->
        debt
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val uiState: StateFlow<FinanceHomeUiState> = combine(
        _refreshTrigger,
        repository.observeSummary(),
        repository.observeAllSubscriptions(),
        repository.observeAllContributions(),
        repository.observeAllTransactions()
    ) { _, summary, subscriptions, contributions, transactions ->
        val pendingSubsCount = subscriptions.count { it.debtAmount > 0 }
        val pendingContribsCount = contributions.count { it.debtAmount > 0 }
        val donations = transactions
            .filter { it.type == FinanceTransactionType.DONATION }
            .sumOf { it.amount }
        val contribsPaid = transactions
            .filter { it.type == FinanceTransactionType.CONTRIBUTION }
            .sumOf { it.amount }

        FinanceHomeUiState(
            summary = summary,
            pendingSubscriptionsCount = pendingSubsCount,
            pendingContributionsCount = pendingContribsCount,
            totalOutstandingDebt = summary.totalOutstandingDebt,
            totalDonations = donations,
            totalContributionsPaid = contribsPaid,
            isLoading = false
        )
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
