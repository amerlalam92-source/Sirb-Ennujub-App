package com.example.domain.attendance

import java.util.Calendar
import kotlinx.coroutines.flow.Flow

/**
 * واجهة مستودع تسجيل الحضور (Attendance Repository Interface)
 */
interface AttendanceRepository {
    fun getAttendanceForActivity(activityId: String): Flow<List<Attendance>>
    fun getAttendanceForScout(scoutId: String): Flow<List<Attendance>>
    suspend fun saveAttendance(
        attendance: Attendance,
        syncSubscription: Boolean = true,
        awaitServer: Boolean = true,
        onAsyncFailure: ((Throwable) -> Unit)? = null
    ): Result<Unit>
    suspend fun saveAttendanceBatch(list: List<Attendance>): Result<Unit>
    suspend fun deleteAttendance(activityId: String, scoutId: String): Result<Unit>
    fun observePointsSettings(): Flow<AttendancePointsSettings>
    suspend fun updatePointsSettings(settings: AttendancePointsSettings): Result<Unit>
    fun computeScoutStatistics(scoutId: String): Flow<ScoutStatistics>
    fun getAttendanceRowsForActivity(activityId: String): Flow<List<AttendanceRow>>
    fun getTrainingMeetingsRegister(sinceTimestamp: Long = defaultScoutingYearStart()): Flow<List<MeetingWithAttendance>>
    suspend fun getTrainingMeetingNumber(activityId: String): Int
    fun observeAllAttendance(sinceTimestamp: Long = defaultScoutingYearStart()): Flow<List<Attendance>>

    companion object {
        fun defaultScoutingYearStart(): Long {
            val calendar = Calendar.getInstance()
            val currentMonth = calendar.get(Calendar.MONTH)
            val currentYear = calendar.get(Calendar.YEAR)
            val seasonStartYear = if (currentMonth >= Calendar.SEPTEMBER) currentYear else currentYear - 1
            calendar.set(Calendar.YEAR, seasonStartYear)
            calendar.set(Calendar.MONTH, Calendar.SEPTEMBER)
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            return calendar.timeInMillis
        }
    }
}

