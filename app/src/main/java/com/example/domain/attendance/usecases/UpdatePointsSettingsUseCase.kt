package com.example.domain.attendance.usecases

import com.example.domain.attendance.AttendancePointsSettings
import com.example.domain.attendance.AttendanceRepository

class UpdatePointsSettingsUseCase(
    private val repository: AttendanceRepository
) {
    suspend operator fun invoke(settings: AttendancePointsSettings): Result<Unit> =
        repository.updatePointsSettings(settings)
}
