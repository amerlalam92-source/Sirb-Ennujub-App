package com.example.presentation.finance.donations

import com.example.domain.finance.FinanceTransaction

/**
 * حالة نموذج إضافة/تعديل تبرع مالي حر
 */
data class DonationFormState(
    val id: String? = null,
    val amountText: String = "",
    val date: Long = System.currentTimeMillis(),
    val source: String = "", // اسم المتبرع / الجهة المتبرعة
    val description: String = "", // الغرض أو الوصف
    val amountError: String? = null,
    val isSaving: Boolean = false
)

/**
 * حالة واجهة شاشة التبرعات المالية (DonationsUiState)
 * تقتصر حصراً على التبرعات الحرة غير المرتبطة بأنشطة أو أشبال
 */
data class DonationsUiState(
    val donations: List<FinanceTransaction> = emptyList(),
    val totalDonations: Int = 0,
    val monthlyDonations: Int = 0,
    val donationsCount: Int = 0,
    val isLoading: Boolean = false,
    val formState: DonationFormState? = null,
    val deleteConfirmTransaction: FinanceTransaction? = null,
    val snackbarMessage: String? = null,
    val errorMessage: String? = null
)
