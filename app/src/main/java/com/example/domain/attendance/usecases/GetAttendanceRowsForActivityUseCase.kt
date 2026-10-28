package com.example.domain.attendance.usecases

import com.example.domain.attendance.AttendanceRepository
import com.example.domain.attendance.AttendanceRow
import kotlinx.coroutines.flow.Flow

/**
 * حالة استخدام لاستخراج قائمة الحضور الكاملة للأشبال النشطين لنشاط محدد مرتبة أبجدياً
 */
class GetAttendanceRowsForActivityUseCase(
    private val repository: AttendanceRepository
) {
    operator fun invoke(activityId: String): Flow<List<AttendanceRow>> {
        return repository.getAttendanceRowsForActivity(activityId)
    }
}
