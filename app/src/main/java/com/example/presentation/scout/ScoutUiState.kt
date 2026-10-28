package com.example.presentation.scout

import androidx.annotation.StringRes
import com.example.domain.progress.BadgeLevel
import com.example.domain.scout.Scout
import java.util.Calendar

/**
 * فلاتر عرض قائمة الأشبال
 */
enum class ScoutFilter {
    ALL,
    ACTIVE,
    ARCHIVED
}

/**
 * حالات واجهة قائمة الأشبال (Scout List UI State)
 */
sealed interface ScoutListUiState {
    data object Loading : ScoutListUiState
    data class Success(
        val allScouts: List<Scout> = emptyList(),
        val filteredScouts: List<Scout> = emptyList(),
        val activeCount: Int = 0,
        val totalCount: Int = 0,
        val searchQuery: String = "",
        val filter: ScoutFilter = ScoutFilter.ACTIVE,
        val grantedBadgesByScout: Map<String, Set<BadgeLevel>> = emptyMap(),
        val inProgressBadgesByScout: Map<String, Set<BadgeLevel>> = emptyMap(),
        val phonePickerScout: Scout? = null
    ) : ScoutListUiState
    data class Error(val message: String) : ScoutListUiState
}

/**
 * حالات واجهة تفاصيل الشبل (Scout Detail UI State)
 */
sealed interface ScoutDetailUiState {
    data object Loading : ScoutDetailUiState
    data class Success(val scout: Scout) : ScoutDetailUiState
    data class Error(val message: String) : ScoutDetailUiState
    data object Deleted : ScoutDetailUiState
}

/**
 * حالة استمارة الشبل (إضافة / تعديل) وفق الهيكلية الجديدة
 */
data class ScoutFormUiState(
    val id: String = "",
    val name: String = "",
    val surname: String = "",
    val birthDate: Long = 0L,
    val guardianName: String = "",
    val guardianPhone: String = "",
    val additionalPhones: List<String> = emptyList(),
    val guardianRelation: String = Scout.RELATION_FATHER,
    val address: String = "",
    val school: String = "",
    val joinYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val status: String = Scout.STATUS_ACTIVE,
    val notes: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isSavedSuccess: Boolean = false,
    @param:StringRes val nameError: Int? = null,
    @param:StringRes val surnameError: Int? = null,
    @param:StringRes val birthDateError: Int? = null,
    @param:StringRes val guardianPhoneError: Int? = null,
    @param:StringRes val guardianRelationError: Int? = null,
    @param:StringRes val joinYearError: Int? = null,
    val generalError: String? = null
)
