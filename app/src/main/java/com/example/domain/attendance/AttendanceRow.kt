package com.example.domain.attendance

import com.example.domain.scout.Scout

/**
 * سطر الحضور لشبل محدد في نشاط كشفي
 * إذا كان [attendance] مساوياً لـ null فهذا يعني أن الحضور لم يُسجّل له بعد ("لم يُسجَّل").
 */
data class AttendanceRow(
    val scout: Scout,
    val attendance: Attendance?
)
