package com.example.domain.attendance.usecases

import com.example.domain.attendance.Attendance
import com.example.domain.attendance.AttendanceRepository
import kotlinx.coroutines.flow.Flow

class GetAttendanceForScoutUseCase(
    private val repository: AttendanceRepository
) {
    operator fun invoke(scoutId: String): Flow<List<Attendance>> =
        repository.getAttendanceForScout(scoutId)
}
