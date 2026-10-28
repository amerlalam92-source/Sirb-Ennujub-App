package com.example.presentation.finance.expenses

import com.example.domain.finance.ExpenseCategory
import com.example.domain.finance.FinanceTransaction

/**
 * حالة نموذج إضافة/تعديل المصروف
 */
data class ExpenseFormState(
    val id: String? = null,
    val amountText: String = "",
    val category: ExpenseCategory? = null,
    val date: Long = System.currentTimeMillis(),
    val description: String = "",
    val amountError: String? = null,
    val categoryError: String? = null,
    val isSaving: Boolean = false
)

/**
 * حالة واجهة شاشة المصاريف (ExpensesUiState)
 */
data class ExpensesUiState(
    val expenses: List<FinanceTransaction> = emptyList(),
    val filteredExpenses: List<FinanceTransaction> = emptyList(),
    val totalExpenses: Int = 0,
    val monthlyTotal: Int = 0,
    val totalCount: Int = 0,
    val selectedCategory: ExpenseCategory? = null,
    val isLoading: Boolean = false,
    val formState: ExpenseFormState? = null,
    val deleteConfirmExpense: FinanceTransaction? = null,
    val snackbarMessage: String? = null,
    val errorMessage: String? = null
)
