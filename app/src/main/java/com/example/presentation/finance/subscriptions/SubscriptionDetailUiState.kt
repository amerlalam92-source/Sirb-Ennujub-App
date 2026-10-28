package com.example.presentation.finance.subscriptions

import com.example.domain.finance.FinanceSubscription

/**
 * فلتر عرض اشتراكات النشاط التدريبي
 */
enum class SubscriptionDetailFilter {
    ALL,
    DEBTORS,
    PAID
}

/**
 * حالة واجهة تفاصيل اشتراكات النشاط التدريبي
 */
data class SubscriptionDetailUiState(
    val activityId: String = "",
    val activityTitle: String = "",
    val activityDate: Long = 0L,
    val subscriptions: List<FinanceSubscription> = emptyList(),
    val filteredSubscriptions: List<FinanceSubscription> = emptyList(),
    val selectedFilter: SubscriptionDetailFilter = SubscriptionDetailFilter.ALL,
    val totalRequired: Int = 0,
    val totalPaid: Int = 0,
    val totalDebt: Int = 0,
    val scoutsCount: Int = 0,
    val totalCount: Int = 0,
    val debtorsCount: Int = 0,
    val paidCount: Int = 0,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val savingScoutId: String? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null,

    // تخصيص مبالغ الاشتراكات للنشاط (Part 3A)
    val showOverrideDialog: Boolean = false,
    val isCustomOverride: Boolean = false,
    val overridePresentText: String = "",
    val overrideLateText: String = "",
    val overrideExcusedText: String = "",
    val overrideUnexcusedText: String = "",
    val defaultPresent: Int = 50,
    val defaultLate: Int = 50,
    val defaultExcused: Int = 0,
    val defaultUnexcused: Int = 100,
    val isSavingOverride: Boolean = false
)
