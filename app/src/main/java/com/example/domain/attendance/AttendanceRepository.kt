package com.example.domain.attendance

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
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
        onAsyncFailure: ((Throwable) -> Unit)? = null,
        onServerResult: ((Result<Unit>) -> Unit)? = null
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
        val ALGIERS_ZONE: ZoneId = ZoneId.of("Africa/Algiers")

        fun defaultScoutingYearStart(
            nowMillis: Long = System.currentTimeMillis(),
            zoneId: ZoneId = ALGIERS_ZONE
        ): Long {
            val zonedNow = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
            val seasonStartYear = if (zonedNow.monthValue >= 9) zonedNow.year else zonedNow.year - 1
            return ZonedDateTime.of(seasonStartYear, 9, 1, 0, 0, 0, 0, zoneId)
                .toInstant()
                .toEpochMilli()
        }
    }
}

