package com.example.presentation.settings

import com.example.domain.evaluation.EvaluationCriteria

enum class FinanceField {
    PRESENT_AMOUNT,
    LATE_AMOUNT,
    VERY_LATE_AMOUNT,
    ABSENT_EXCUSED_AMOUNT,
    ABSENT_UNEXCUSED_AMOUNT,
    OPENING_BALANCE
}

data class SettingsUiState(
    // سلم التنقيط الكشفي الجديد (Base 10 + Modifiers)
    val basePoints: Int = 10,
    val earlyBonus: Int = 2,
    val lateDeduction: Int = 3,
    val veryLateDeduction: Int = 8,
    val excusedDeduction: Int = 6,
    val unexcusedDeduction: Int = 10,
    val kafaaBadgePoints: Int = 10,
    val extraHobbyBadgePoints: Int = 5,
    val isLoadingPoints: Boolean = false,
    val isSavingPoints: Boolean = false,

    // معايير التقييم الديناميكية (Dynamic Evaluation Criteria)
    val criteriaList: List<EvaluationCriteria> = emptyList(),
    val isLoadingCriteria: Boolean = false,
    val isSavingCriteria: Boolean = false,
    val isAddCriteriaDialogOpen: Boolean = false,
    val pendingDeleteCriteria: EvaluationCriteria? = null,

    // إعدادات المالية والاشتراكات (Finance settings)
    val presentAmount: String = "20",
    val lateAmount: String = "30",
    val veryLateAmount: String = "40",
    val absentExcusedAmount: String = "20",
    val absentUnexcusedAmount: String = "50",
    val openingBalance: String = "0",
    val isLoadingFinance: Boolean = false,
    val isSavingFinance: Boolean = false,
    val isCleaningOrphanedRecords: Boolean = false,
    val isCleanOrphanedDialogOpen: Boolean = false,

    // حالات عامة
    val isLoading: Boolean = false,
    val isSaving: Boolean = false
) {
    // خصائص مشتقة للمجموع النهائي لكل حالة حضور
    val presentPoints: Int get() = basePoints + earlyBonus
    val latePoints: Int get() = basePoints - lateDeduction
    val veryLatePoints: Int get() = basePoints - veryLateDeduction
    val absentExcusedPoints: Int get() = basePoints - excusedDeduction
    val absentUnexcusedPoints: Int get() = basePoints - unexcusedDeduction
}

sealed interface SettingsEvent {
    data class ShowMessage(val message: String) : SettingsEvent
}
