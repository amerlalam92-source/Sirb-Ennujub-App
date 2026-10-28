package com.example.presentation.finance.collections

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
 * نموذج العرض لقائمة التحصيلات المالية الموحدة (CollectionsViewModel)
 * يدمج ويراقب بيانات الاشتراكات والمساهمات لكل نشاط ويحسب الإجماليات والمؤشرات.
 */
class CollectionsViewModel(
    private val repository: FinanceRepository
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow(CollectionsFilter.ALL)

    val uiState: StateFlow<CollectionsUiState> = combine(
        repository.observeAllSubscriptions(),
        repository.observeAllContributions(),
        _selectedFilter
    ) { subscriptions, contributions, filter ->
        // تجميع الاشتراكات حسب النشاط
        val subsItems = subscriptions.groupBy { it.activityId }.map { (actId, list) ->
            val first = list.first()
            val totalReq = list.sumOf { it.requiredAmount }
            val totalPaid = list.sumOf { it.paidAmount }
            val totalDebt = list.sumOf { it.debtAmount }
            CollectionActivityItem(
                activityId = actId,
                activityTitle = first.activityTitle,
                activityDate = first.activityDate,
                type = "subscriptions",
                participantsCount = list.size,
                totalRequired = totalReq,
                totalPaid = totalPaid,
                totalDebt = totalDebt,
                isFullyCollected = totalDebt == 0
            )
        }

        // تجميع المساهمات حسب النشاط
        val contribsItems = contributions.groupBy { it.activityId }.map { (actId, list) ->
            val first = list.first()
            val totalReq = list.sumOf { it.requiredAmount }
            val totalPaid = list.sumOf { it.paidAmount }
            val totalDebt = list.sumOf { it.debtAmount }
            CollectionActivityItem(
                activityId = actId,
                activityTitle = first.activityTitle,
                activityDate = first.activityDate,
                type = "contributions",
                participantsCount = list.size,
                totalRequired = totalReq,
                totalPaid = totalPaid,
                totalDebt = totalDebt,
                isFullyCollected = totalDebt == 0
            )
        }

        val allItems = (subsItems + contribsItems).sortedByDescending { it.activityDate }
        val filtered = when (filter) {
            CollectionsFilter.ALL -> allItems
            CollectionsFilter.SUBSCRIPTIONS -> allItems.filter { it.type == "subscriptions" }
            CollectionsFilter.CONTRIBUTIONS -> allItems.filter { it.type == "contributions" }
        }

        CollectionsUiState(
            items = allItems,
            filteredItems = filtered,
            selectedFilter = filter,
            allCount = allItems.size,
            subsCount = subsItems.size,
            contribsCount = contribsItems.size,
            totalRequired = allItems.sumOf { it.totalRequired },
            totalPaid = allItems.sumOf { it.totalPaid },
            totalDebt = allItems.sumOf { it.totalDebt },
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CollectionsUiState(isLoading = true)
    )

    fun onFilterSelected(filter: CollectionsFilter) {
        _selectedFilter.value = filter
    }

    class Factory(
        private val repository: FinanceRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CollectionsViewModel(repository) as T
        }
    }
}
