package com.example.domain.attendance

/**
 * ملخص نشاط غائب عنه الشبل
 */
data class ActivitySummary(
    val activityId: String = "",
    val title: String = "",
    val date: Long = 0L,
    val status: AttendanceStatus = AttendanceStatus.ABSENT_UNEXCUSED
)

/**
 * إحصائيات حضور الشبل التراكمية
 */
data class ScoutStatistics(
    val totalActivities: Int = 0,
    val presentCount: Int = 0,
    val lateCount: Int = 0,
    val veryLateCount: Int = 0,
    val absentExcusedCount: Int = 0,
    val absentUnexcusedCount: Int = 0,
    val attendancePercentage: Double = 0.0,
    val totalPoints: Int = 0,
    val missedActivities: List<ActivitySummary> = emptyList()
) {
    val totalAttended: Int
        get() = presentCount + lateCount + veryLateCount

    val totalAbsent: Int
        get() = absentExcusedCount + absentUnexcusedCount
}
