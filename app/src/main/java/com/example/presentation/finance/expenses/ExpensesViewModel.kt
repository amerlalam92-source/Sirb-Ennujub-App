package com.example.presentation.finance.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.finance.ExpenseCategory
import com.example.domain.finance.FinanceRepository
import com.example.domain.finance.FinanceTransaction
import com.example.domain.finance.FinanceTransactionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * نموذج العرض لإدارة المصاريف (ExpensesViewModel)
 */
class ExpensesViewModel(
    private val repository: FinanceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExpensesUiState(isLoading = true))
    val uiState: StateFlow<ExpensesUiState> = _uiState.asStateFlow()

    init {
        observeExpenses()
    }

    private fun observeExpenses() {
        repository.observeTransactionsByType(FinanceTransactionType.EXPENSE)
            .onEach { list ->
                val sorted = list.sortedByDescending { it.date }
                val now = Calendar.getInstance()
                val currentYear = now.get(Calendar.YEAR)
                val currentMonth = now.get(Calendar.MONTH)

                val monthlySum = sorted.filter { tx ->
                    val cal = Calendar.getInstance().apply { timeInMillis = tx.date }
                    cal.get(Calendar.YEAR) == currentYear && cal.get(Calendar.MONTH) == currentMonth
                }.sumOf { it.amount }

                val totalSum = sorted.sumOf { it.amount }

                _uiState.update { current ->
                    val filtered = applyFilter(sorted, current.selectedCategory)
                    current.copy(
                        expenses = sorted,
                        filteredExpenses = filtered,
                        totalExpenses = totalSum,
                        monthlyTotal = monthlySum,
                        totalCount = sorted.size,
                        isLoading = false
                    )
                }
            }
            .catch { e ->
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
            .launchIn(viewModelScope)
    }

    fun onCategoryFilterChanged(category: ExpenseCategory?) {
        _uiState.update { current ->
            current.copy(
                selectedCategory = category,
                filteredExpenses = applyFilter(current.expenses, category)
            )
        }
    }

    private fun applyFilter(list: List<FinanceTransaction>, category: ExpenseCategory?): List<FinanceTransaction> {
        return if (category == null) {
            list
        } else {
            list.filter { it.category == category }
        }
    }

    // --- حوار الإضافة / التعديل ---

    fun onAddClick() {
        _uiState.update {
            it.copy(
                formState = ExpenseFormState(
                    id = null,
                    amountText = "",
                    category = null,
                    date = System.currentTimeMillis(),
                    description = ""
                )
            )
        }
    }

    fun onEditClick(expense: FinanceTransaction) {
        _uiState.update {
            it.copy(
                formState = ExpenseFormState(
                    id = expense.id,
                    amountText = if (expense.amount > 0) expense.amount.toString() else "",
                    category = expense.category,
                    date = expense.date,
                    description = expense.description
                )
            )
        }
    }

    fun onDismissForm() {
        _uiState.update { it.copy(formState = null) }
    }

    fun onAmountChanged(amountText: String) {
        _uiState.update { current ->
            current.formState?.let { form ->
                val cleaned = amountText.filter { it.isDigit() }
                current.copy(formState = form.copy(amountText = cleaned, amountError = null))
            } ?: current
        }
    }

    fun onCategoryChanged(category: ExpenseCategory) {
        _uiState.update { current ->
            current.formState?.let { form ->
                current.copy(formState = form.copy(category = category, categoryError = null))
            } ?: current
        }
    }

    fun onDateChanged(date: Long) {
        _uiState.update { current ->
            current.formState?.let { form ->
                current.copy(formState = form.copy(date = date))
            } ?: current
        }
    }

    fun onDescriptionChanged(description: String) {
        _uiState.update { current ->
            current.formState?.let { form ->
                current.copy(formState = form.copy(description = description))
            } ?: current
        }
    }

    fun onSaveExpense() {
        val form = _uiState.value.formState ?: return
        val amount = form.amountText.toIntOrNull()

        var hasError = false
        var amountError: String? = null
        var categoryError: String? = null

        if (amount == null) {
            amountError = "يرجى إدخال المبلغ"
            hasError = true
        } else if (amount <= 0) {
            amountError = "المبلغ يجب أن يكون أكبر من صفر"
            hasError = true
        }

        if (form.category == null) {
            categoryError = "يرجى اختيار التصنيف"
            hasError = true
        }

        if (hasError) {
            _uiState.update {
                it.copy(formState = form.copy(amountError = amountError, categoryError = categoryError))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(formState = form.copy(isSaving = true)) }

            val tx = FinanceTransaction(
                id = form.id ?: "",
                type = FinanceTransactionType.EXPENSE,
                amount = amount ?: 0,
                date = form.date,
                description = form.description.trim(),
                category = form.category,
                createdAt = if (form.id != null) form.date else System.currentTimeMillis()
            )

            val result = if (form.id == null) {
                repository.addTransaction(tx)
            } else {
                repository.updateTransaction(tx)
            }

            if (result.isSuccess) {
                val msg = if (form.id == null) "تمت إضافة المصروف" else "تم تعديل المصروف"
                _uiState.update { it.copy(formState = null, snackbarMessage = msg) }
            } else {
                _uiState.update {
                    it.copy(
                        formState = form.copy(isSaving = false),
                        errorMessage = result.exceptionOrNull()?.message ?: "حدث خطأ أثناء حفظ المصروف"
                    )
                }
            }
        }
    }

    // --- الحذف والتأكيد ---

    fun onDeleteClick(expense: FinanceTransaction) {
        _uiState.update { it.copy(deleteConfirmExpense = expense) }
    }

    fun onDismissDelete() {
        _uiState.update { it.copy(deleteConfirmExpense = null) }
    }

    fun onDeleteTransaction(id: String) {
        viewModelScope.launch {
            val result = repository.deleteTransaction(id)
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        deleteConfirmExpense = null,
                        snackbarMessage = "تم الحذف بنجاح"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        deleteConfirmExpense = null,
                        errorMessage = result.exceptionOrNull()?.message ?: "فشل في حذف المصروف"
                    )
                }
            }
        }
    }

    fun onConfirmDelete() {
        val target = _uiState.value.deleteConfirmExpense ?: return
        onDeleteTransaction(target.id)
    }

    fun clearMessages() {
        _uiState.update { it.copy(snackbarMessage = null, errorMessage = null) }
    }

    class Factory(private val repository: FinanceRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ExpensesViewModel(repository) as T
        }
    }
}
