package com.example.domain.attendance

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
        const val DEFAULT_LOOKBACK_MILLIS = 365L * 24L * 60L * 60L * 1000L

        fun defaultScoutingYearStart(): Long = System.currentTimeMillis() - DEFAULT_LOOKBACK_MILLIS
    }
}

