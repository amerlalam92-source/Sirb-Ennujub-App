package com.example.presentation.attendance

import com.example.domain.activity.Activity
import com.example.domain.attendance.AttendancePointsSettings
import com.example.domain.attendance.AttendanceStatus
import com.example.domain.scout.Scout

/**
 * حالات واجهة اختيار النشاط لتسجيل الحضور
 */
sealed interface AttendancePickerUiState {
    data object Loading : AttendancePickerUiState
    data class Success(
        val activities: List<Activity> = emptyList(),
        val recordedActivityIds: Set<String> = emptySet()
    ) : AttendancePickerUiState
    data class Error(val message: String) : AttendancePickerUiState
}

/**
 * حالة استمارة الحضور المتسلسلة (Wizard)
 */
data class AttendanceEntryUiState(
    val activity: Activity? = null,
    val scoutsQueue: List<Scout> = emptyList(),
    val currentIndex: Int = 0,
    val recordedStatuses: Map<String, AttendanceStatus> = emptyMap(),
    val pointsSettings: AttendancePointsSettings = AttendancePointsSettings(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isFinished: Boolean = false,
    val errorMessage: String? = null
) {
    val currentScout: Scout?
        get() = scoutsQueue.getOrNull(currentIndex)

    val currentStatus: AttendanceStatus?
        get() = currentScout?.let { recordedStatuses[it.id] }

    val totalScouts: Int
        get() = scoutsQueue.size

    val isFirst: Boolean
        get() = currentIndex == 0

    val isLast: Boolean
        get() = scoutsQueue.isNotEmpty() && currentIndex >= scoutsQueue.size - 1
}
