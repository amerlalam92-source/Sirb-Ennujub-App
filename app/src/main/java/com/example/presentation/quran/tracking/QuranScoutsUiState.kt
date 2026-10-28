package com.example.presentation.quran.tracking

import com.example.domain.quran.QuranSchool
import com.example.domain.quran.ScoutQuranEnrollment
import com.example.domain.scout.Scout

/**
 * فلاتر عرض الأشبال في شاشة متابعة القرآن
 */
enum class QuranScoutsFilter {
    ALL,
    ENROLLED,
    NOT_ENROLLED
}

/**
 * عنصر عرض الشبل متضمناً بياناته وحالة تسجيله القرآني
 */
data class QuranScoutItem(
    val scout: Scout,
    val enrollment: ScoutQuranEnrollment?
) {
    val isEnrolled: Boolean get() = enrollment != null
}

/**
 * حالة واجهة المستخدم لشاشة متابعة حفظ القرآن للأشبال
 */
sealed interface QuranScoutsUiState {
    data object Loading : QuranScoutsUiState

    data class Error(val message: String) : QuranScoutsUiState

    data class Success(
        val allScoutItems: List<QuranScoutItem>,
        val filteredScoutItems: List<QuranScoutItem>,
        val activeSchools: List<QuranSchool>,
        val selectedFilter: QuranScoutsFilter = QuranScoutsFilter.ALL,
        val enrolledCount: Int = 0,
        val totalScoutsCount: Int = 0,
        val userMessage: String? = null
    ) : QuranScoutsUiState
}
