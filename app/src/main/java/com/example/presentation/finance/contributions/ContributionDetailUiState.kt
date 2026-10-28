package com.example.presentation.finance.contributions

import com.example.domain.finance.FinanceContribution
import com.example.domain.scout.Scout

/**
 * فلتر عرض المساهمين في النشاط
 */
enum class ContributionDetailFilter {
    ALL,
    DEBTORS,
    PAID
}

/**
 * حالة واجهة تفاصيل مساهمات النشاط الكشفي (ContributionDetailUiState)
 */
data class ContributionDetailUiState(
    val activityId: String = "",
    val activityTitle: String = "",
    val activityDate: Long = 0L,
    val isContributionRequired: Boolean = false,
    val defaultAmount: Int? = null,
    val contributions: List<FinanceContribution> = emptyList(),
    val filteredContributions: List<FinanceContribution> = emptyList(),
    val selectedFilter: ContributionDetailFilter = ContributionDetailFilter.ALL,
    val totalRequired: Int = 0,
    val totalPaid: Int = 0,
    val totalDebt: Int = 0,
    val participantsCount: Int = 0,
    val debtorsCount: Int = 0,
    val paidCount: Int = 0,
    val availableScouts: List<Scout> = emptyList(),
    val newParticipantAmount: String = "",
    val selectedNewScoutIds: Set<String> = emptySet(),
    val availableScoutsForAddition: List<Scout> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val savingScoutId: String? = null,
    val paymentDialogContribution: FinanceContribution? = null,
    val editDialogContribution: FinanceContribution? = null,
    val showAddScoutDialog: Boolean = false,
    val showDefaultSettingsDialog: Boolean = false,
    val deleteConfirmContribution: FinanceContribution? = null,
    val successMessage: String? = null,
    val errorMessage: String? = null
)
