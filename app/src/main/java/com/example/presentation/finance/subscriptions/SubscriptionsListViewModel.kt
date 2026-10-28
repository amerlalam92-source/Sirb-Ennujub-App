package com.example.presentation.finance.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.finance.FinanceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * نموذج العرض لقائمة اشتراكات الأنشطة (SubscriptionsListViewModel)
 * يجمع الاشتراكات حسب كل نشاط، ويحسب المبالغ المحصلة والمطلوبة والديون مع إمكانية التصفية.
 */
class SubscriptionsListViewModel(
    private val repository: FinanceRepository
) : ViewModel() {

    private val _filter = MutableStateFlow(SubscriptionFilter.ALL)
    val filter: StateFlow<SubscriptionFilter> = _filter

    val uiState: StateFlow<SubscriptionsListUiState> = combine(
        repository.observeAllSubscriptions(),
        _filter
    ) { allSubs, currentFilter ->
        val groupedByActivity = allSubs.groupBy { it.activityId }
        val summaries = groupedByActivity.map { (activityId, subs) ->
            val first = subs.first()
            val sumRequired = subs.sumOf { it.requiredAmount }
            val sumPaid = subs.sumOf { it.paidAmount }
            val sumDebt = subs.sumOf { it.debtAmount }

            ActivitySubscriptionSummary(
                activityId = activityId,
                activityTitle = first.activityTitle.ifBlank { "نشاط تدريبي" },
                activityDate = first.activityDate,
                sumRequired = sumRequired,
                sumPaid = sumPaid,
                sumDebt = sumDebt,
                scoutsCount = subs.size
            )
        }.sortedByDescending { it.activityDate }

        val filtered = when (currentFilter) {
            SubscriptionFilter.ALL -> summaries
            SubscriptionFilter.PENDING -> summaries.filter { it.sumDebt > 0 }
            SubscriptionFilter.COMPLETED -> summaries.filter { it.sumDebt == 0 }
        }

        SubscriptionsListUiState(
            filter = currentFilter,
            activities = summaries,
            filteredActivities = filtered,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SubscriptionsListUiState(isLoading = true)
    )

    fun onFilterSelected(filter: SubscriptionFilter) {
        _filter.value = filter
    }

    class Factory(
        private val repository: FinanceRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SubscriptionsListViewModel(repository) as T
        }
    }
}
