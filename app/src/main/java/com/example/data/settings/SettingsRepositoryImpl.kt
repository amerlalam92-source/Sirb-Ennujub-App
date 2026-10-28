package com.example.data.settings

import com.example.domain.attendance.AttendancePointsSettings
import com.example.domain.attendance.AttendanceRepository
import com.example.domain.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow

/**
 * تطبيق مستودع إعدادات النظام (Settings Repository Implementation)
 * يربط مع مستودع الحضور للحصول على وحفظ سلم النقاط الأساسية والمكافآت
 */
class SettingsRepositoryImpl(
    private val attendanceRepository: AttendanceRepository
) : SettingsRepository {

    override fun observePointsSettings(): Flow<AttendancePointsSettings> {
        return attendanceRepository.observePointsSettings()
    }

    override suspend fun updatePointsSettings(settings: AttendancePointsSettings): Result<Unit> {
        return attendanceRepository.updatePointsSettings(settings)
    }
}
