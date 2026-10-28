package com.example.presentation.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.attendance.MeetingWithAttendance
import com.example.domain.attendance.usecases.GetTrainingMeetingsRegisterUseCase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * نموذج العرض لسجل الاجتماعات التدريبية الكشفية بصيغة جدول المصفوفة.
 * تُحسب نقاط حضور الأشبال وإجماليات النقاط بشكل ديناميكي بناءً على إعدادات سلم التنقيط الحالية.
 */
class TrainingMeetingsRegisterViewModel(
    getTrainingMeetingsRegisterUseCase: GetTrainingMeetingsRegisterUseCase
) : ViewModel() {

    private val shortDateFormatter = SimpleDateFormat("d/M", com.example.presentation.common.AppLocale.AR_DZ)

    val uiState: StateFlow<TrainingMeetingsRegisterUiState> =
        getTrainingMeetingsRegisterUseCase()
            .map { list ->
                if (list.isEmpty()) {
                    TrainingMeetingsRegisterUiState.Success(
                        meetings = emptyList(),
                        columns = emptyList(),
                        rows = emptyList()
                    )
                } else {
                    // ترتيب تصاعدي زمني للأعمدة (الاجتماع 1 إلى N)
                    val chronologicalMeetings = list.sortedBy { it.number }
                    val columns = chronologicalMeetings.map { meeting ->
                        val dateText = if (meeting.activity.startDate > 0) {
                            shortDateFormatter.format(Date(meeting.activity.startDate))
                        } else {
                            "—"
                        }
                        MeetingColumn(
                            activityId = meeting.activity.id,
                            number = meeting.number,
                            shortDate = dateText,
                            usesPoints = meeting.activity.activityType.usesPointsSystem()
                        )
                    }

                    // جمع قائمة الأشبال النشطين بنفس ترتيبهم الهجائي
                    val allScouts = chronologicalMeetings.firstOrNull()?.rows?.map { it.scout } ?: emptyList()
                    val meetingAttendanceMap = chronologicalMeetings.associate { meeting ->
                        meeting.activity.id to meeting.rows.associateBy { it.scout.id }
                    }

                    val rows = allScouts.map { scout ->
                        val attendances = chronologicalMeetings.associate { meeting ->
                            val row = meetingAttendanceMap[meeting.activity.id]?.get(scout.id)
                            meeting.activity.id to AttendanceCellData(
                                status = row?.attendance?.attendanceStatus,
                                points = if (meeting.activity.activityType.usesPointsSystem() && row?.attendance != null) {
                                    row.attendance.points
                                } else null
                            )
                        }
                        val totalPoints = chronologicalMeetings.sumOf { meeting ->
                            val row = meetingAttendanceMap[meeting.activity.id]?.get(scout.id)
                            if (row?.attendance != null && row.attendance.attendanceStatus != null) {
                                row.attendance.points
                            } else {
                                0
                            }
                        }

                        ScoutMatrixRow(
                            scout = scout,
                            attendances = attendances,
                            totalPoints = totalPoints
                        )
                    }

                    TrainingMeetingsRegisterUiState.Success(
                        meetings = chronologicalMeetings,
                        columns = columns,
                        rows = rows
                    )
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = TrainingMeetingsRegisterUiState.Loading
            )

    class Factory(
        private val getTrainingMeetingsRegisterUseCase: GetTrainingMeetingsRegisterUseCase
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TrainingMeetingsRegisterViewModel(getTrainingMeetingsRegisterUseCase) as T
        }
    }
}
