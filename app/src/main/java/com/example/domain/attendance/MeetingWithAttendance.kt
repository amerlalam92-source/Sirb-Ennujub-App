package com.example.domain.attendance

import com.example.domain.activity.Activity

/**
 * يمثل اجتماعاً تدريبياً مع رقم تسلسله وقائمة الحضور الكاملة للأشبال النشطين
 */
data class MeetingWithAttendance(
    val activity: Activity,
    val number: Int,
    val rows: List<AttendanceRow>,
    val presentCount: Int,
    val totalScouts: Int
)
