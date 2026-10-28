package com.example.presentation.attendance

import com.example.domain.attendance.AttendanceStatus
import com.example.domain.attendance.MeetingWithAttendance
import com.example.domain.scout.Scout

/**
 * عمود الاجتماع التدريبي في جدول المصفوفة
 */
data class MeetingColumn(
    val activityId: String,
    val number: Int,
    val shortDate: String,
    val usesPoints: Boolean
)

/**
 * خلية حضور شبل في اجتماع محدد
 */
data class AttendanceCellData(
    val status: AttendanceStatus?,
    val points: Int?
)

/**
 * سطر الشبل في جدول المصفوفة
 */
data class ScoutMatrixRow(
    val scout: Scout,
    val attendances: Map<String, AttendanceCellData>, // activityId -> AttendanceCellData
    val totalPoints: Int = 0
)

/**
 * حالة واجهة مستخدم سجل الاجتماعات التدريبية
 */
sealed interface TrainingMeetingsRegisterUiState {
    object Loading : TrainingMeetingsRegisterUiState
    data class Success(
        val meetings: List<MeetingWithAttendance>,
        val columns: List<MeetingColumn>,
        val rows: List<ScoutMatrixRow>
    ) : TrainingMeetingsRegisterUiState
}
