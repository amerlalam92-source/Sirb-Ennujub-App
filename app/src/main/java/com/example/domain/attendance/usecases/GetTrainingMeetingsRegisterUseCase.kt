package com.example.domain.attendance.usecases

import com.example.domain.attendance.AttendanceRepository
import com.example.domain.attendance.MeetingWithAttendance
import kotlinx.coroutines.flow.Flow

/**
 * حالة استخدام لاستخراج سجل الاجتماعات التدريبية الكاملة مع قوائم حضور الأشبال لكل اجتماع
 */
class GetTrainingMeetingsRegisterUseCase(
    private val repository: AttendanceRepository
) {
    operator fun invoke(): Flow<List<MeetingWithAttendance>> {
        return repository.getTrainingMeetingsRegister()
    }
}
