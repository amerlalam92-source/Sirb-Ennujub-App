package com.example.domain.attendance

/**
 * كائن تسجيل الحضور للشبل في النشاط الكشفي (Attendance Domain Model)
 */
data class Attendance(
    val activityId: String = "",
    val scoutId: String = "",
    val scoutFullName: String = "",
    val status: String = AttendanceStatus.PRESENT.key,
    val points: Int = 0,
    val activityDate: Long = System.currentTimeMillis(),
    val recordedBy: String = "",
    val recordedByName: String = "",
    val recordedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * المعرف المركب لمنع التكرار (activityId_scoutId)
     */
    val id: String
        get() = "${activityId}_${scoutId}"

    val attendanceStatus: AttendanceStatus
        get() = AttendanceStatus.fromKey(status)
}
