package com.example.presentation.finance.collections

/**
 * فئات التصفية لشاشة التحصيلات الموحدة
 */
enum class CollectionsFilter {
    ALL,
    SUBSCRIPTIONS,
    CONTRIBUTIONS
}

/**
 * حالة واجهة شاشة التحصيلات المالية الموحدة (CollectionsUiState)
 */
data class CollectionsUiState(
    val items: List<CollectionActivityItem> = emptyList(),
    val filteredItems: List<CollectionActivityItem> = emptyList(),
    val selectedFilter: CollectionsFilter = CollectionsFilter.ALL,
    val allCount: Int = 0,
    val subsCount: Int = 0,
    val contribsCount: Int = 0,
    val totalRequired: Int = 0,
    val totalPaid: Int = 0,
    val totalDebt: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
