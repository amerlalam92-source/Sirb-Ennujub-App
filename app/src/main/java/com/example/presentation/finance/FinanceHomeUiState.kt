package com.example.presentation.finance

import com.example.domain.finance.FinanceSummary

/**
 * حالة واجهة شاشة الإدارة المالية الرئيسية (Finance Home Ui State)
 */
data class FinanceHomeUiState(
    val summary: FinanceSummary? = null,
    val pendingSubscriptionsCount: Int = 0,
    val pendingContributionsCount: Int = 0,
    val totalOutstandingDebt: Int = 0,
    val totalDonations: Int = 0,
    val totalContributionsPaid: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
