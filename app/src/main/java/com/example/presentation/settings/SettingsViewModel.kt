package com.example.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.attendance.AttendancePointsSettings
import com.example.domain.attendance.usecases.ObservePointsSettingsUseCase
import com.example.domain.attendance.usecases.UpdatePointsSettingsUseCase
import com.example.domain.evaluation.CriteriaType
import com.example.domain.evaluation.EvaluationCriteria
import com.example.domain.evaluation.usecases.AddCriteriaUseCase
import com.example.domain.evaluation.usecases.ArchiveCriteriaUseCase
import com.example.domain.evaluation.usecases.DeleteCriteriaUseCase
import com.example.domain.evaluation.usecases.ObserveCriteriaUseCase
import com.example.domain.finance.FinanceRepository
import com.example.domain.finance.FinanceSettings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val observePointsSettingsUseCase: ObservePointsSettingsUseCase,
    private val updatePointsSettingsUseCase: UpdatePointsSettingsUseCase,
    private val financeRepository: FinanceRepository,
    private val observeCriteriaUseCase: ObserveCriteriaUseCase,
    private val addCriteriaUseCase: AddCriteriaUseCase,
    private val archiveCriteriaUseCase: ArchiveCriteriaUseCase,
    private val deleteCriteriaUseCase: DeleteCriteriaUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            isLoading = true,
            isLoadingPoints = true,
            isLoadingFinance = true,
            isLoadingCriteria = true
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    init {
        // مراقبة إعدادات سلم التنقيط الكشفي (Base 10 + Modifiers)
        viewModelScope.launch {
            observePointsSettingsUseCase().collect { settings ->
                _uiState.update {
                    it.copy(
                        basePoints = settings.basePoints,
                        earlyBonus = settings.earlyBonus,
                        lateDeduction = settings.lateDeduction,
                        veryLateDeduction = settings.veryLateDeduction,
                        excusedDeduction = settings.excusedDeduction,
                        unexcusedDeduction = settings.unexcusedDeduction,
                        kafaaBadgePoints = settings.kafaaBadgePoints,
                        extraHobbyBadgePoints = settings.extraHobbyBadgePoints,
                        isLoadingPoints = false,
                        isLoading = false
                    )
                }
            }
        }

        // مراقبة معايير التقييم الديناميكية للأشبال
        viewModelScope.launch {
            observeCriteriaUseCase().collect { criteria ->
                _uiState.update {
                    it.copy(
                        criteriaList = criteria,
                        isLoadingCriteria = false
                    )
                }
            }
        }

        // مراقبة إعدادات المالية ومبالغ الاشتراكات الافتراضية
        viewModelScope.launch {
            financeRepository.observeSettings().collect { financeSettings ->
                _uiState.update {
                    it.copy(
                        presentAmount = financeSettings.presentAmount.toString(),
                        lateAmount = financeSettings.lateAmount.toString(),
                        veryLateAmount = financeSettings.veryLateAmount.toString(),
                        absentExcusedAmount = if (financeSettings.absentExcusedAmount == 40) "20" else financeSettings.absentExcusedAmount.toString(),
                        absentUnexcusedAmount = financeSettings.absentUnexcusedAmount.toString(),
                        openingBalance = financeSettings.openingBalance.toString(),
                        isLoadingFinance = false
                    )
                }
            }
        }
    }

    // === دوال سلم التنقيط (Base 10 + Modifiers) ===

    fun onBasePointsChanged(value: Int) {
        _uiState.update { it.copy(basePoints = value.coerceAtLeast(0)) }
    }

    fun onEarlyBonusChanged(value: Int) {
        _uiState.update { it.copy(earlyBonus = value.coerceAtLeast(0)) }
    }

    fun onLateDeductionChanged(value: Int) {
        _uiState.update { it.copy(lateDeduction = value.coerceAtLeast(0)) }
    }

    fun onVeryLateDeductionChanged(value: Int) {
        _uiState.update { it.copy(veryLateDeduction = value.coerceAtLeast(0)) }
    }

    fun onExcusedDeductionChanged(value: Int) {
        _uiState.update { it.copy(excusedDeduction = value.coerceAtLeast(0)) }
    }

    fun onUnexcusedDeductionChanged(value: Int) {
        _uiState.update { it.copy(unexcusedDeduction = value.coerceAtLeast(0)) }
    }

    // للتوافق العكسي مع أي اختبارات أو استدعاءات قديمة
    fun onPresentPointsChanged(value: Int) {
        val diff = value - _uiState.value.basePoints
        _uiState.update { it.copy(earlyBonus = diff.coerceAtLeast(0)) }
    }

    fun onLatePointsChanged(value: Int) {
        val diff = _uiState.value.basePoints - value
        _uiState.update { it.copy(lateDeduction = diff.coerceAtLeast(0)) }
    }

    fun onVeryLatePointsChanged(value: Int) {
        val diff = _uiState.value.basePoints - value
        _uiState.update { it.copy(veryLateDeduction = diff.coerceAtLeast(0)) }
    }

    fun onAbsentExcusedPointsChanged(value: Int) {
        val diff = _uiState.value.basePoints - value
        _uiState.update { it.copy(excusedDeduction = diff.coerceAtLeast(0)) }
    }

    fun onAbsentUnexcusedPointsChanged(value: Int) {
        val diff = _uiState.value.basePoints - value
        _uiState.update { it.copy(unexcusedDeduction = diff.coerceAtLeast(0)) }
    }

    fun onKafaaBadgePointsChanged(value: Int) {
        _uiState.update { it.copy(kafaaBadgePoints = value.coerceAtLeast(0)) }
    }

    fun onExtraHobbyBadgePointsChanged(value: Int) {
        _uiState.update { it.copy(extraHobbyBadgePoints = value.coerceAtLeast(0)) }
    }

    fun resetToDefaults() {
        _uiState.update {
            it.copy(
                basePoints = 10,
                earlyBonus = 2,
                lateDeduction = 3,
                veryLateDeduction = 8,
                excusedDeduction = 6,
                unexcusedDeduction = 10,
                kafaaBadgePoints = 10,
                extraHobbyBadgePoints = 5
            )
        }
    }

    fun saveSettings() {
        val current = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, isSavingPoints = true) }
            val settings = AttendancePointsSettings(
                basePoints = current.basePoints,
                earlyBonus = current.earlyBonus,
                lateDeduction = current.lateDeduction,
                veryLateDeduction = current.veryLateDeduction,
                excusedDeduction = current.excusedDeduction,
                unexcusedDeduction = current.unexcusedDeduction,
                kafaaBadgePoints = current.kafaaBadgePoints,
                extraHobbyBadgePoints = current.extraHobbyBadgePoints
            )
            val result = updatePointsSettingsUseCase(settings)
            _uiState.update { it.copy(isSaving = false, isSavingPoints = false) }
            if (result.isSuccess) {
                _events.emit(SettingsEvent.ShowMessage("تم حفظ الإعدادات بنجاح"))
            } else {
                _events.emit(SettingsEvent.ShowMessage("فشل حفظ الإعدادات، يرجى المحاولة لاحقاً"))
            }
        }
    }

    // === دوال معايير التقييم الديناميكية ===

    fun openAddCriteriaDialog() {
        _uiState.update { it.copy(isAddCriteriaDialogOpen = true) }
    }

    fun closeAddCriteriaDialog() {
        _uiState.update { it.copy(isAddCriteriaDialogOpen = false) }
    }

    fun addCriteria(name: String, maxScore: Int, type: CriteriaType) {
        if (name.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingCriteria = true) }
            val highestOrder = _uiState.value.criteriaList.maxOfOrNull { it.order } ?: 0
            val criterion = EvaluationCriteria(
                id = "",
                name = name.trim(),
                maxScore = maxScore,
                type = type.value,
                order = highestOrder + 1,
                archived = false
            )
            val result = addCriteriaUseCase(criterion)
            _uiState.update { it.copy(isSavingCriteria = false, isAddCriteriaDialogOpen = false) }
            if (result.isSuccess) {
                _events.emit(SettingsEvent.ShowMessage("تمت إضافة معيار التقييم بنجاح"))
            } else {
                _events.emit(SettingsEvent.ShowMessage("فشل إضافة المعيار، يرجى المحاولة لاحقاً"))
            }
        }
    }

    fun onDeleteCriteriaClicked(criteria: EvaluationCriteria) {
        _uiState.update { it.copy(pendingDeleteCriteria = criteria) }
    }

    fun onDeleteCriteriaConfirmed() {
        val target = _uiState.value.pendingDeleteCriteria ?: return
        _uiState.update { it.copy(pendingDeleteCriteria = null) }
        archiveOrDeleteCriteria(target.id)
    }

    fun onDeleteCriteriaDismissed() {
        _uiState.update { it.copy(pendingDeleteCriteria = null) }
    }

    fun archiveOrDeleteCriteria(criterionId: String) {
        viewModelScope.launch {
            val result = archiveCriteriaUseCase(criterionId)
            if (result.isSuccess) {
                _events.emit(SettingsEvent.ShowMessage("تم حذف المعيار بنجاح"))
            } else {
                // محاولة الحذف النهائي إن لم يكن للأرشفة
                val delResult = deleteCriteriaUseCase(criterionId)
                if (delResult.isSuccess) {
                    _events.emit(SettingsEvent.ShowMessage("تم حذف المعيار نهائياً"))
                } else {
                    _events.emit(SettingsEvent.ShowMessage("فشل حذف المعيار"))
                }
            }
        }
    }

    // === دوال إعدادات المالية ===

    fun onFinanceFieldChanged(field: FinanceField, value: String) {
        val digitsOnly = value.filter { it.isDigit() }
        _uiState.update { current ->
            when (field) {
                FinanceField.PRESENT_AMOUNT -> current.copy(presentAmount = digitsOnly)
                FinanceField.LATE_AMOUNT -> current.copy(lateAmount = digitsOnly)
                FinanceField.VERY_LATE_AMOUNT -> current.copy(veryLateAmount = digitsOnly)
                FinanceField.ABSENT_EXCUSED_AMOUNT -> current.copy(absentExcusedAmount = digitsOnly)
                FinanceField.ABSENT_UNEXCUSED_AMOUNT -> current.copy(absentUnexcusedAmount = digitsOnly)
                FinanceField.OPENING_BALANCE -> current.copy(openingBalance = digitsOnly)
            }
        }
    }

    fun onFinanceFieldChanged(field: String, value: String) {
        val enumField = when (field.lowercase()) {
            "present_amount", "present", "presentamount" -> FinanceField.PRESENT_AMOUNT
            "late_amount", "late", "lateamount" -> FinanceField.LATE_AMOUNT
            "very_late_amount", "very_late", "verylate", "verylateamount" -> FinanceField.VERY_LATE_AMOUNT
            "absent_excused_amount", "excused", "absent_excused", "absentexcusedamount" -> FinanceField.ABSENT_EXCUSED_AMOUNT
            "absent_unexcused_amount", "unexcused", "absent_unexcused", "absentunexcusedamount" -> FinanceField.ABSENT_UNEXCUSED_AMOUNT
            "opening_balance", "openingbalance", "balance" -> FinanceField.OPENING_BALANCE
            else -> null
        }
        if (enumField != null) {
            onFinanceFieldChanged(enumField, value)
        }
    }

    fun resetFinanceToDefaults() {
        _uiState.update {
            it.copy(
                presentAmount = "20",
                lateAmount = "30",
                veryLateAmount = "40",
                absentExcusedAmount = "20",
                absentUnexcusedAmount = "50",
                openingBalance = "0"
            )
        }
    }

    fun saveFinanceSettings() {
        val current = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingFinance = true) }
            val newSettings = FinanceSettings(
                presentAmount = current.presentAmount.toIntOrNull() ?: 20,
                lateAmount = current.lateAmount.toIntOrNull() ?: 30,
                veryLateAmount = current.veryLateAmount.toIntOrNull() ?: 40,
                absentExcusedAmount = current.absentExcusedAmount.toIntOrNull() ?: 20,
                absentUnexcusedAmount = current.absentUnexcusedAmount.toIntOrNull() ?: 50,
                openingBalance = current.openingBalance.toIntOrNull() ?: 0,
                currency = "دج",
                updatedAt = System.currentTimeMillis()
            )
            val result = financeRepository.updateSettings(newSettings)
            _uiState.update { it.copy(isSavingFinance = false) }
            if (result.isSuccess) {
                _events.emit(SettingsEvent.ShowMessage("تم حفظ إعدادات المالية"))
            } else {
                _events.emit(SettingsEvent.ShowMessage("فشل حفظ إعدادات المالية، يرجى المحاولة لاحقاً"))
            }
        }
    }

    fun onOpenCleanOrphanedDialog() {
        _uiState.update { it.copy(isCleanOrphanedDialogOpen = true) }
    }

    fun onDismissCleanOrphanedDialog() {
        _uiState.update { it.copy(isCleanOrphanedDialogOpen = false) }
    }

    fun onConfirmCleanOrphanedRecords() {
        _uiState.update { it.copy(isCleanOrphanedDialogOpen = false, isCleaningOrphanedRecords = true) }
        viewModelScope.launch {
            val result = financeRepository.cleanupOrphanedRecords()
            _uiState.update { it.copy(isCleaningOrphanedRecords = false) }
            result.onSuccess { count ->
                _events.emit(SettingsEvent.ShowMessage("تم حذف $count سجل معلق"))
            }.onFailure { e ->
                _events.emit(SettingsEvent.ShowMessage("فشل تنظيف السجلات المعلقة: ${e.message ?: "خطأ غير معروف"}"))
            }
        }
    }

    class Factory(
        private val observePointsSettingsUseCase: ObservePointsSettingsUseCase,
        private val updatePointsSettingsUseCase: UpdatePointsSettingsUseCase,
        private val financeRepository: FinanceRepository,
        private val observeCriteriaUseCase: ObserveCriteriaUseCase,
        private val addCriteriaUseCase: AddCriteriaUseCase,
        private val archiveCriteriaUseCase: ArchiveCriteriaUseCase,
        private val deleteCriteriaUseCase: DeleteCriteriaUseCase
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(
                observePointsSettingsUseCase,
                updatePointsSettingsUseCase,
                financeRepository,
                observeCriteriaUseCase,
                addCriteriaUseCase,
                archiveCriteriaUseCase,
                deleteCriteriaUseCase
            ) as T
        }
    }
}

