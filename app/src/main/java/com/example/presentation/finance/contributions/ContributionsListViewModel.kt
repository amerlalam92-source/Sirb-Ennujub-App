package com.example.presentation.finance.contributions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.activity.ActivityRepository
import com.example.domain.finance.FinanceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * نموذج العرض لقائمة أنشطة المساهمات (ContributionsListViewModel)
 * يجمع المساهمات حسب كل نشاط كشفي، ويحسب المبالغ المحصلة والمطلوبة والديون مع تصفية ومجاميع إجمالية.
 */
class ContributionsListViewModel(
    private val financeRepository: FinanceRepository,
    private val activityRepository: ActivityRepository
) : ViewModel() {

    private val _filter = MutableStateFlow(ContributionActivityFilter.ALL)
    val filter: StateFlow<ContributionActivityFilter> = _filter

    val uiState: StateFlow<ContributionsListUiState> = combine(
        financeRepository.observeAllContributions(),
        activityRepository.observeActivities(),
        _filter
    ) { allContribs, allActivities, currentFilter ->
        val activitiesMap = allActivities.associateBy { it.id }
        val groupedByActivity = allContribs.groupBy { it.activityId }

        // الأنشطة التي تظهر هي: التي لها مساهمات مسجلة أو تم تفعيل المساهمات بها (contributionRequired == true)
        val relevantActivityIds = (groupedByActivity.keys + allActivities.filter { it.contributionRequired }.map { it.id }).distinct()

        val summaries = relevantActivityIds.mapNotNull { actId ->
            val contribs = groupedByActivity[actId].orEmpty()
            val act = activitiesMap[actId]
            val first = contribs.firstOrNull()

            val title = act?.title ?: first?.activityTitle.orEmpty()
            if (title.isBlank() && act == null && first == null) return@mapNotNull null

            val date = act?.startDate ?: first?.activityDate ?: 0L
            val type = act?.type ?: "camp"

            val sumRequired = contribs.sumOf { it.requiredAmount }
            val sumPaid = contribs.sumOf { it.paidAmount }
            val sumDebt = contribs.sumOf { it.debtAmount }

            ActivityContributionSummary(
                activityId = actId,
                activityTitle = title.ifBlank { "نشاط كشفي" },
                activityDate = date,
                activityType = type,
                sumRequired = sumRequired,
                sumPaid = sumPaid,
                sumDebt = sumDebt,
                participantsCount = contribs.size,
                isContributionRequired = act?.contributionRequired ?: true,
                defaultAmount = act?.contributionDefaultAmount
            )
        }.sortedByDescending { it.activityDate }

        val filtered = when (currentFilter) {
            ContributionActivityFilter.ALL -> summaries
            ContributionActivityFilter.PENDING -> summaries.filter { it.sumDebt > 0 || (it.participantsCount == 0 && it.isContributionRequired) }
            ContributionActivityFilter.COMPLETED -> summaries.filter { it.sumDebt == 0 && it.participantsCount > 0 }
        }

        val totalReq = allContribs.sumOf { it.requiredAmount }
        val totalPaid = allContribs.sumOf { it.paidAmount }
        val totalDebt = allContribs.sumOf { it.debtAmount }

        ContributionsListUiState(
            filter = currentFilter,
            activities = summaries,
            filteredActivities = filtered,
            globalStats = ContributionsGlobalStats(
                totalRequired = totalReq,
                totalPaid = totalPaid,
                totalDebt = totalDebt,
                activitiesCount = summaries.size,
                participantsCount = allContribs.size
            ),
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ContributionsListUiState(isLoading = true)
    )

    fun onFilterSelected(filter: ContributionActivityFilter) {
        _filter.value = filter
    }

    class Factory(
        private val financeRepository: FinanceRepository,
        private val activityRepository: ActivityRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ContributionsListViewModel(financeRepository, activityRepository) as T
        }
    }
}
