package com.example.domain.activity

import kotlinx.coroutines.flow.Flow

/**
 * واجهة مستودع الأنشطة الكشفية (Activity Repository Interface)
 */
interface ActivityRepository {
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
    suspend fun updateContributionSettings(activityId: String, required: Boolean, defaultAmount: Int?): Result<Unit>
}
