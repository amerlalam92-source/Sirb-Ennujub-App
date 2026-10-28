package com.example.presentation.activity

import androidx.annotation.StringRes
import com.example.domain.activity.Activity
import com.example.domain.activity.ActivityStatus
import com.example.domain.activity.ActivityType

/**
 * فلاتر عرض الأنشطة الكشفية
 */
enum class ActivityFilter {
    UPCOMING, // القادمة
    PAST,     // السابقة
    ALL       // الكل
}

/**
 * حالات واجهة قائمة الأنشطة (Activity List UI State)
 */
sealed interface ActivityListUiState {
    data object Loading : ActivityListUiState
    data class Success(
        val allActivities: List<Activity> = emptyList(),
        val filteredActivities: List<Activity> = emptyList(),
        val groupedActivities: Map<String, List<Activity>> = emptyMap(),
        val upcomingCount: Int = 0,
        val pastCount: Int = 0,
        val totalCount: Int = 0,
        val filter: ActivityFilter = ActivityFilter.ALL,
        val searchQuery: String = ""
    ) : ActivityListUiState
    data class Error(val message: String) : ActivityListUiState
}

/**
 * حالات واجهة تفاصيل النشاط (Activity Detail UI State)
 */
sealed interface ActivityDetailUiState {
    data object Loading : ActivityDetailUiState
    data class Success(val activity: Activity) : ActivityDetailUiState
    data class Error(val message: String) : ActivityDetailUiState
    data object Deleted : ActivityDetailUiState
}

/**
 * حالة استمارة إضافة / تعديل النشاط (Activity Form UI State)
 */
data class ActivityFormUiState(
    val id: String = "",
    val title: String = "",
    val type: String = ActivityType.TRAINING_MEETING.key,
    val startDate: Long = System.currentTimeMillis(),
    val endDate: Long = System.currentTimeMillis(),
    val startTime: String = "",
    val endTime: String = "",
    val location: String = "",
    val objectives: String = "",
    val tools: String = "",
    val responsibleName: String = "",
    val status: String = ActivityStatus.PLANNED.key,
    val notes: String = "",
    val meetingTopics: List<String> = emptyList(),
    val finalReport: String = "",
    val collectSubscriptions: Boolean = true,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isSavedSuccess: Boolean = false,
    @param:StringRes val titleError: Int? = null,
    @param:StringRes val startDateError: Int? = null,
    @param:StringRes val endDateError: Int? = null,
    @param:StringRes val typeError: Int? = null,
    val generalError: String? = null
)
