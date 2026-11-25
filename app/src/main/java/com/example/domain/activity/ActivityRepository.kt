package com.example.domain.activity

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * نتيجة عملية صيانة بيانات وتواريخ الحضور للأنشطة
 */
data class MaintenanceReport(
    val activitiesChecked: Int,
    val flagsFixed: Int,
    val attendanceDatesFixed: Int
)

/**
 * قرار تحديث علم وجود الحضور (hasAttendanceRecorded)
 */
sealed interface AttendanceFlagDecision {
    data class Update(val hasAttendanceRecorded: Boolean) : AttendanceFlagDecision
    data object NoChange : AttendanceFlagDecision
    data object UnknownSkip : AttendanceFlagDecision
}

/**
 * دوال نقية لقواعد صيانة أعلام وتواريخ الحضور قابلة للاختبار بدون Firebase
 */
object AttendanceMaintenancePolicy {
    fun decideAttendanceFlagUpdate(
        currentFlag: Boolean,
        hasAttendanceOnServer: Boolean?
    ): AttendanceFlagDecision {
        if (hasAttendanceOnServer == null) return AttendanceFlagDecision.UnknownSkip
        return if (currentFlag != hasAttendanceOnServer) {
            AttendanceFlagDecision.Update(hasAttendanceOnServer)
        } else {
            AttendanceFlagDecision.NoChange
        }
    }

    fun shouldUpdateAttendanceActivityDate(
        currentActivityDate: Long?,
        activityStartDate: Long
    ): Boolean {
        if (activityStartDate <= 0L) return false
        return currentActivityDate == null || currentActivityDate <= 0L || currentActivityDate != activityStartDate
    }
}

/**
 * واجهة مستودع الأنشطة الكشفية (Activity Repository Interface)
 */
interface ActivityRepository {
    val dateSyncWarnings: StateFlow<String?>
        get() = MutableStateFlow(null)

    fun clearDateSyncWarning() {}

    fun observeActivities(): Flow<List<Activity>>
    fun observeUpcomingActivities(limit: Int = 20): Flow<List<Activity>>
    suspend fun getActivityById(id: String): Activity?
    suspend fun addActivity(activity: Activity): Result<String>
    suspend fun updateActivity(activity: Activity): Result<Unit>
    suspend fun markAsDone(id: String): Result<Unit>
    suspend fun markAsDoneWithReport(id: String, report: String): Result<Unit>
    suspend fun updateReport(id: String, report: String): Result<Unit>
    suspend fun cancelActivity(id: String): Result<Unit>
    suspend fun deleteActivity(id: String): Result<Unit>
    suspend fun updateSubscriptionOverride(activityId: String, override: Map<String, Int>?): Result<Unit>
    suspend fun recalculateAllAttendanceFlags(): Result<MaintenanceReport>
}
