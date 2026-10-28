package com.example.presentation.quran.schools

import com.example.domain.quran.QuranSchool

/**
 * عنصر المدرسة القرآنية متضمناً عدد الأشبال المسجلين فيها
 */
data class QuranSchoolItem(
    val school: QuranSchool,
    val enrolledScoutsCount: Int = 0
)

/**
 * حالة واجهة مستخدم قائمة المدارس القرآنية
 */
sealed interface QuranSchoolsUiState {
    data object Loading : QuranSchoolsUiState

    data class Success(
        val schools: List<QuranSchoolItem>,
        val activeCount: Int,
        val userMessage: String? = null
    ) : QuranSchoolsUiState

    data class Error(val message: String) : QuranSchoolsUiState
}
