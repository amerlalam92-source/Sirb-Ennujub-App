package com.example.domain.attendance.usecases

import com.example.domain.attendance.Attendance
import com.example.domain.attendance.AttendanceRepository
import kotlinx.coroutines.flow.Flow

class GetAttendanceForActivityUseCase(
    private val repository: AttendanceRepository
) {
    operator fun invoke(activityId: String): Flow<List<Attendance>> =
        repository.getAttendanceForActivity(activityId)
}
