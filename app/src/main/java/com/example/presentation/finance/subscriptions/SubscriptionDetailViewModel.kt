package com.example.presentation.finance.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.activity.ActivityRepository
import com.example.domain.finance.FinanceRepository
import com.example.domain.finance.FinanceSettings
import com.example.domain.finance.FinanceSubscription
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * نموذج العرض لشاشة تفاصيل اشتراكات النشاط التدريبي (SubscriptionDetailViewModel)
 * يدير تحديث اشتراك كل شبل، حفظ المبالغ المدفوعة، تصفية القائمة، وتخصيص مبالغ الاشتراكات للنشاط.
 */
class SubscriptionDetailViewModel(
    private val activityId: String,
    private val repository: FinanceRepository,
    private val activityRepository: ActivityRepository
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow(SubscriptionDetailFilter.ALL)
    private val _savingScoutId = MutableStateFlow<String?>(null)
    private val _successMessage = MutableStateFlow<String?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)

    // حالة تخصيص مبالغ الاشتراكات
    private val _showOverrideDialog = MutableStateFlow(false)
    private val _isCustomOverride = MutableStateFlow(false)
    private val _overridePresentText = MutableStateFlow("50")
    private val _overrideLateText = MutableStateFlow("50")
    private val _overrideExcusedText = MutableStateFlow("0")
    private val _overrideUnexcusedText = MutableStateFlow("100")
    private val _isSavingOverride = MutableStateFlow(false)

    val uiState: StateFlow<SubscriptionDetailUiState> = combine(
        repository.observeSubscriptionsForActivity(activityId),
        repository.observeSettings(),
        _selectedFilter,
        _savingScoutId,
        _successMessage,
        _errorMessage,
        _showOverrideDialog,
        _isCustomOverride,
        _overridePresentText,
        _overrideLateText,
        _overrideExcusedText,
        _overrideUnexcusedText,
        _isSavingOverride
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val subs = args[0] as List<FinanceSubscription>
        val settings = args[1] as FinanceSettings
        val filter = args[2] as SubscriptionDetailFilter
        val savingId = args[3] as String?
        val successMsg = args[4] as String?
        val errorMsg = args[5] as String?
        val showDialog = args[6] as Boolean
        val isCustom = args[7] as Boolean
        val pText = args[8] as String
        val lText = args[9] as String
        val eText = args[10] as String
        val uText = args[11] as String
        val isSavingOv = args[12] as Boolean

        val first = subs.firstOrNull()
        val totalRequired = subs.sumOf { it.requiredAmount }
        val totalPaid = subs.sumOf { it.paidAmount }
        val totalDebt = subs.sumOf { it.debtAmount }
        val debtorsCount = subs.count { it.debtAmount > 0 }
        val paidCount = subs.count { it.debtAmount == 0 }

        val filtered = when (filter) {
            SubscriptionDetailFilter.ALL -> subs
            SubscriptionDetailFilter.DEBTORS -> subs.filter { it.debtAmount > 0 }
            SubscriptionDetailFilter.PAID -> subs.filter { it.debtAmount == 0 }
        }

        SubscriptionDetailUiState(
            activityId = activityId,
            activityTitle = first?.activityTitle.orEmpty(),
            activityDate = first?.activityDate ?: 0L,
            subscriptions = subs,
            filteredSubscriptions = filtered,
            selectedFilter = filter,
            totalRequired = totalRequired,
            totalPaid = totalPaid,
            totalDebt = totalDebt,
            scoutsCount = subs.size,
            totalCount = subs.size,
            debtorsCount = debtorsCount,
            paidCount = paidCount,
            isLoading = false,
            isSaving = savingId != null,
            savingScoutId = savingId,
            errorMessage = errorMsg,
            successMessage = successMsg,
            showOverrideDialog = showDialog,
            isCustomOverride = isCustom,
            overridePresentText = pText,
            overrideLateText = lText,
            overrideExcusedText = eText,
            overrideUnexcusedText = uText,
            defaultPresent = settings.presentAmount,
            defaultLate = settings.lateAmount,
            defaultExcused = settings.absentExcusedAmount,
            defaultUnexcused = settings.absentUnexcusedAmount,
            isSavingOverride = isSavingOv
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SubscriptionDetailUiState(activityId = activityId, isLoading = true)
    )

    fun setFilter(filter: SubscriptionDetailFilter) {
        _selectedFilter.value = filter
    }

    fun onFilterSelected(filter: SubscriptionDetailFilter) {
        _selectedFilter.value = filter
    }

    fun savePayment(subscription: FinanceSubscription, newPaid: Int) {
        val updated = subscription.copy(
            paidAmount = newPaid,
            debtAmount = (subscription.requiredAmount - newPaid).coerceAtLeast(0),
            updatedAt = System.currentTimeMillis()
        )

        viewModelScope.launch {
            _savingScoutId.value = subscription.scoutId
            val result = repository.upsertSubscription(updated)
            _savingScoutId.value = null
            if (result.isSuccess) {
                _successMessage.value = "تم حفظ الدفع"
            } else {
                _errorMessage.value = result.exceptionOrNull()?.localizedMessage ?: "حدث خطأ أثناء حفظ الدفع"
            }
        }
    }

    // --- تخصيص مبالغ الاشتراكات لهذا النشاط ---

    fun onOpenOverrideDialog() {
        viewModelScope.launch {
            val activity = activityRepository.getActivityById(activityId)
            val settings = repository.observeSettings().firstOrNull() ?: FinanceSettings()

            val override = activity?.subscriptionOverride
            if (override != null && override.isNotEmpty()) {
                _isCustomOverride.value = true
                _overridePresentText.value = (override["present"] ?: settings.presentAmount).toString()
                _overrideLateText.value = (override["late"] ?: settings.lateAmount).toString()
                _overrideExcusedText.value = (override["absent_excused"] ?: settings.absentExcusedAmount).toString()
                _overrideUnexcusedText.value = (override["absent_unexcused"] ?: settings.absentUnexcusedAmount).toString()
            } else {
                _isCustomOverride.value = false
                _overridePresentText.value = settings.presentAmount.toString()
                _overrideLateText.value = settings.lateAmount.toString()
                _overrideExcusedText.value = settings.absentExcusedAmount.toString()
                _overrideUnexcusedText.value = settings.absentUnexcusedAmount.toString()
            }

            _showOverrideDialog.value = true
        }
    }

    fun onDismissOverrideDialog() {
        _showOverrideDialog.value = false
    }

    fun onToggleCustomOverride(isCustom: Boolean) {
        _isCustomOverride.value = isCustom
    }

    fun onOverridePresentChanged(value: String) {
        _overridePresentText.value = value.filter { it.isDigit() }
    }

    fun onOverrideLateChanged(value: String) {
        _overrideLateText.value = value.filter { it.isDigit() }
    }

    fun onOverrideExcusedChanged(value: String) {
        _overrideExcusedText.value = value.filter { it.isDigit() }
    }

    fun onOverrideUnexcusedChanged(value: String) {
        _overrideUnexcusedText.value = value.filter { it.isDigit() }
    }

    fun onSaveOverride() {
        viewModelScope.launch {
            _isSavingOverride.value = true

            val isCustom = _isCustomOverride.value
            val overrideMap: Map<String, Int>? = if (isCustom) {
                mapOf(
                    "present" to (_overridePresentText.value.toIntOrNull() ?: 0),
                    "late" to (_overrideLateText.value.toIntOrNull() ?: 0),
                    "absent_excused" to (_overrideExcusedText.value.toIntOrNull() ?: 0),
                    "absent_unexcused" to (_overrideUnexcusedText.value.toIntOrNull() ?: 0)
                )
            } else {
                null
            }

            // 1) تحديث النشاط في مستودع الأنشطة
            val updateRes = activityRepository.updateSubscriptionOverride(activityId, overrideMap)
            if (updateRes.isFailure) {
                _isSavingOverride.value = false
                _errorMessage.value = updateRes.exceptionOrNull()?.message ?: "فشل في تحديث مبالغ الاشتراكات"
                return@launch
            }

            // 2) إعادة حساب مبالغ الاشتراكات الحالية للأشبال في هذا النشاط
            val settings = repository.observeSettings().firstOrNull() ?: FinanceSettings()
            val currentSubs = uiState.value.subscriptions
            currentSubs.forEach { sub ->
                val newRequired = if (overrideMap != null && overrideMap.containsKey(sub.attendanceStatus.key)) {
                    overrideMap[sub.attendanceStatus.key] ?: settings.requiredAmountFor(sub.attendanceStatus)
                } else {
                    settings.requiredAmountFor(sub.attendanceStatus)
                }
                val newDebt = (newRequired - sub.paidAmount).coerceAtLeast(0)
                val updatedSub = sub.copy(
                    requiredAmount = newRequired,
                    debtAmount = newDebt,
                    updatedAt = System.currentTimeMillis()
                )
                repository.upsertSubscription(updatedSub)
            }

            _isSavingOverride.value = false
            _showOverrideDialog.value = false
            _successMessage.value = "تم حفظ مبالغ الاشتراكات لهذا النشاط"
        }
    }

    fun deleteAllCollectionsForActivity(onDeleted: () -> Unit) {
        viewModelScope.launch {
            val result = repository.deleteCollectionsForActivity(activityId)
            if (result.isSuccess) {
                onDeleted()
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "فشل حذف التحصيلات"
            }
        }
    }

    fun clearMessages() {
        _successMessage.value = null
        _errorMessage.value = null
    }

    class Factory(
        private val activityId: String,
        private val repository: FinanceRepository,
        private val activityRepository: ActivityRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SubscriptionDetailViewModel(activityId, repository, activityRepository) as T
        }
    }
}
