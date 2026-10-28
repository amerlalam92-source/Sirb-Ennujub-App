package com.example.presentation.finance.donations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
 * نموذج العرض لإدارة التبرعات الحرة (DonationsViewModel)
 */
class DonationsViewModel(
    private val repository: FinanceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DonationsUiState(isLoading = true))
    val uiState: StateFlow<DonationsUiState> = _uiState.asStateFlow()

    init {
        observeDonations()
    }

    private fun observeDonations() {
        repository.observeTransactionsByType(FinanceTransactionType.DONATION)
            .onEach { donationsList ->
                val sorted = donationsList.sortedByDescending { it.date }

                val now = Calendar.getInstance()
                val currentYear = now.get(Calendar.YEAR)
                val currentMonth = now.get(Calendar.MONTH)

                val monthly = sorted.filter { tx ->
                    val cal = Calendar.getInstance().apply { timeInMillis = tx.date }
                    cal.get(Calendar.YEAR) == currentYear && cal.get(Calendar.MONTH) == currentMonth
                }.sumOf { it.amount }

                val total = sorted.sumOf { it.amount }

                _uiState.update { current ->
                    current.copy(
                        donations = sorted,
                        totalDonations = total,
                        monthlyDonations = monthly,
                        donationsCount = sorted.size,
                        isLoading = false
                    )
                }
            }
            .catch { e ->
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
            .launchIn(viewModelScope)
    }

    // --- نموذج الإضافة والتعديل ---

    fun onAddClick() {
        _uiState.update {
            it.copy(
                formState = DonationFormState(
                    id = null,
                    amountText = "",
                    date = System.currentTimeMillis(),
                    source = "",
                    description = ""
                )
            )
        }
    }

    fun onEditClick(transaction: FinanceTransaction) {
        _uiState.update {
            it.copy(
                formState = DonationFormState(
                    id = transaction.id,
                    amountText = transaction.amount.toString(),
                    date = transaction.date,
                    source = transaction.source.orEmpty(),
                    description = transaction.description
                )
            )
        }
    }

    fun onDismissForm() {
        _uiState.update { it.copy(formState = null) }
    }

    fun onAmountChanged(amountText: String) {
        val filtered = amountText.filter { it.isDigit() }
        _uiState.update { current ->
            current.formState?.let { form ->
                current.copy(
                    formState = form.copy(
                        amountText = filtered,
                        amountError = null
                    )
                )
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

    fun onSourceChanged(source: String) {
        _uiState.update { current ->
            current.formState?.let { form ->
                current.copy(formState = form.copy(source = source))
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

    fun onSaveDonation() {
        val form = _uiState.value.formState ?: return
        val amount = form.amountText.toIntOrNull()

        if (amount == null || amount <= 0) {
            _uiState.update { current ->
                current.formState?.let {
                    current.copy(formState = it.copy(amountError = "المبلغ يجب أن يكون أكبر من صفر"))
                } ?: current
            }
            return
        }

        _uiState.update { current ->
            current.formState?.let {
                current.copy(formState = it.copy(isSaving = true))
            } ?: current
        }

        viewModelScope.launch {
            val isEdit = form.id != null
            val sourceText = form.source.trim()
            val descText = form.description.trim()

            val tx = FinanceTransaction(
                id = form.id.orEmpty(),
                type = FinanceTransactionType.DONATION,
                amount = amount,
                date = form.date,
                description = descText,
                source = sourceText.ifBlank { null }
            )

            val result = if (isEdit) {
                repository.updateTransaction(tx)
            } else {
                repository.addTransaction(tx)
            }

            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        formState = null,
                        snackbarMessage = if (isEdit) "تم تعديل التبرع" else "تمت إضافة التبرع"
                    )
                }
            } else {
                _uiState.update { current ->
                    current.copy(
                        formState = current.formState?.copy(isSaving = false),
                        errorMessage = result.exceptionOrNull()?.message ?: "فشلت العملية"
                    )
                }
            }
        }
    }

    // --- حذف تبرع ---

    fun onDeleteClick(transaction: FinanceTransaction) {
        _uiState.update { it.copy(deleteConfirmTransaction = transaction) }
    }

    fun onDismissDelete() {
        _uiState.update { it.copy(deleteConfirmTransaction = null) }
    }

    fun onDeleteTransaction(id: String) {
        viewModelScope.launch {
            val result = repository.deleteTransaction(id)
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        deleteConfirmTransaction = null,
                        snackbarMessage = "تم الحذف بنجاح"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        deleteConfirmTransaction = null,
                        errorMessage = result.exceptionOrNull()?.message ?: "فشل حذف التبرع"
                    )
                }
            }
        }
    }

    fun onConfirmDelete() {
        val tx = _uiState.value.deleteConfirmTransaction ?: return
        onDeleteTransaction(tx.id)
    }

    fun clearMessages() {
        _uiState.update { it.copy(snackbarMessage = null, errorMessage = null) }
    }

    class Factory(
        private val repository: FinanceRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DonationsViewModel(repository) as T
        }
    }
}
