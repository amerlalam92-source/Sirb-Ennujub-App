package com.example.domain.attendance.usecases

import com.example.domain.attendance.Attendance
import com.example.domain.attendance.AttendanceRepository

class SaveAttendanceBatchUseCase(
    private val repository: AttendanceRepository
) {
    suspend operator fun invoke(list: List<Attendance>): Result<Unit> =
        repository.saveAttendanceBatch(list)
}
