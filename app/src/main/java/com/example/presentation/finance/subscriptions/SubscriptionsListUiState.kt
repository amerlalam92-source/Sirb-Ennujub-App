package com.example.presentation.finance.subscriptions

/**
 * فلاتر عرض قائمة الاشتراكات للأنشطة
 */
enum class SubscriptionFilter {
    ALL,
    PENDING,
    COMPLETED
}

/**
 * ملخص اشتراكات النشاط التدريبي
 */
data class ActivitySubscriptionSummary(
    val activityId: String,
    val activityTitle: String,
    val activityDate: Long,
    val sumRequired: Int,
    val sumPaid: Int,
    val sumDebt: Int,
    val scoutsCount: Int
) {
    val isCompleted: Boolean
        get() = sumDebt == 0 && scoutsCount > 0

    val progressFraction: Float
        get() = if (sumRequired > 0) {
            (sumPaid.toFloat() / sumRequired.toFloat()).coerceIn(0f, 1f)
        } else if (scoutsCount > 0) {
            1f
        } else {
            0f
        }
}

/**
 * حالة واجهة شاشة قائمة الاشتراكات
 */
data class SubscriptionsListUiState(
    val filter: SubscriptionFilter = SubscriptionFilter.ALL,
    val activities: List<ActivitySubscriptionSummary> = emptyList(),
    val filteredActivities: List<ActivitySubscriptionSummary> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
