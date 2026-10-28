package com.example.domain.attendance

import kotlinx.coroutines.flow.Flow

/**
 * واجهة مستودع تسجيل الحضور (Attendance Repository Interface)
 */
interface AttendanceRepository {
    fun getAttendanceForActivity(activityId: String): Flow<List<Attendance>>
    fun getAttendanceForScout(scoutId: String): Flow<List<Attendance>>
    suspend fun saveAttendance(attendance: Attendance): Result<Unit>
    suspend fun saveAttendanceBatch(list: List<Attendance>): Result<Unit>
    suspend fun deleteAttendance(activityId: String, scoutId: String): Result<Unit>
    fun observePointsSettings(): Flow<AttendancePointsSettings>
    suspend fun updatePointsSettings(settings: AttendancePointsSettings): Result<Unit>
    fun computeScoutStatistics(scoutId: String): Flow<ScoutStatistics>
    fun observeRecordedActivityIds(): Flow<Set<String>>
    fun getAttendanceRowsForActivity(activityId: String): Flow<List<AttendanceRow>>
    fun getTrainingMeetingsRegister(): Flow<List<MeetingWithAttendance>>
    suspend fun getTrainingMeetingNumber(activityId: String): Int
}

