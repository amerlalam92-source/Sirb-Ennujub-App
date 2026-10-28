package com.example.domain.attendance.usecases

import com.example.domain.attendance.AttendancePointsSettings
import com.example.domain.attendance.AttendanceRepository
import kotlinx.coroutines.flow.Flow

class ObservePointsSettingsUseCase(
    private val repository: AttendanceRepository
) {
    operator fun invoke(): Flow<AttendancePointsSettings> =
        repository.observePointsSettings()
}
