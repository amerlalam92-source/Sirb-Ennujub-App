package com.example.domain.attendance.usecases

import com.example.domain.attendance.AttendanceRepository
import com.example.domain.attendance.ScoutStatistics
import kotlinx.coroutines.flow.Flow

/**
 * حالة استخدام لحساب إحصائيات ونقاط الشبل الكشفي.
 * تحسب النقاط ديناميكياً بناءً على إعدادات سلم التنقيط الحالية وليس القيم الثابتة المخزنة.
 */
class ComputeScoutStatisticsUseCase(
    private val repository: AttendanceRepository
) {
    operator fun invoke(scoutId: String): Flow<ScoutStatistics> =
        repository.computeScoutStatistics(scoutId)
}
