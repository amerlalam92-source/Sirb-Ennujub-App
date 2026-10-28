package com.example.presentation.finance.contributions

/**
 * فلاتر عرض قائمة أنشطة المساهمات
 */
enum class ContributionActivityFilter {
    ALL,
    PENDING,
    COMPLETED
}

/**
 * ملخص مساهمات النشاط الكشفي
 */
data class ActivityContributionSummary(
    val activityId: String,
    val activityTitle: String,
    val activityDate: Long,
    val activityType: String,
    val sumRequired: Int,
    val sumPaid: Int,
    val sumDebt: Int,
    val participantsCount: Int,
    val isContributionRequired: Boolean,
    val defaultAmount: Int?
) {
    val isCompleted: Boolean
        get() = sumDebt == 0 && participantsCount > 0

    val progressFraction: Float
        get() = if (sumRequired > 0) {
            (sumPaid.toFloat() / sumRequired.toFloat()).coerceIn(0f, 1f)
        } else if (participantsCount > 0) {
            1f
        } else {
            0f
        }
}

/**
 * إحصائيات إجمالية لجميع المساهمات
 */
data class ContributionsGlobalStats(
    val totalRequired: Int = 0,
    val totalPaid: Int = 0,
    val totalDebt: Int = 0,
    val activitiesCount: Int = 0,
    val participantsCount: Int = 0
)

/**
 * حالة واجهة شاشة قائمة المساهمات
 */
data class ContributionsListUiState(
    val filter: ContributionActivityFilter = ContributionActivityFilter.ALL,
    val activities: List<ActivityContributionSummary> = emptyList(),
    val filteredActivities: List<ActivityContributionSummary> = emptyList(),
    val globalStats: ContributionsGlobalStats = ContributionsGlobalStats(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
