package com.example.domain.settings

import com.example.domain.attendance.AttendancePointsSettings
import kotlinx.coroutines.flow.Flow

/**
 * واجهة مستودع إعدادات النظام وسلم النقاط (Settings Repository Interface)
 */
interface SettingsRepository {
    /**
     * الاستماع لإعدادات سلم نقاط الحضور ومكافآت التقييم التربوي
     */
    fun observePointsSettings(): Flow<AttendancePointsSettings>

    /**
     * تحديث وحفظ إعدادات سلم النقاط ومكافآت التقييم التربوي
     */
    suspend fun updatePointsSettings(settings: AttendancePointsSettings): Result<Unit>
}
