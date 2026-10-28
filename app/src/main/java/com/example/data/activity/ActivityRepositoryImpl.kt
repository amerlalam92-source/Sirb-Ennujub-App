package com.example.data.activity

import com.example.data.log.ActivityLogDataSource
import com.example.domain.activity.Activity
import com.example.domain.activity.ActivityRepository
import com.example.domain.activity.ActivityStatus
import com.example.domain.auth.AuthRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

/**
 * تنفيذ مستودع الأنشطة الكشفية (Activity Repository Implementation)
 * يربط مصدر البيانات السحابي بنظام التوثيق وتسجيل عمليات التدقيق (Audit Logging).
 */
class ActivityRepositoryImpl(
    private val dataSource: ActivityFirestoreDataSource,
    private val activityLogDataSource: ActivityLogDataSource,
    private val authRepository: AuthRepository
) : ActivityRepository {

    override fun observeActivities(): Flow<List<Activity>> {
        return dataSource.observeActivities()
    }

    override fun observeUpcomingActivities(limit: Int): Flow<List<Activity>> {
        return dataSource.observeUpcomingActivities(limit)
    }

    override suspend fun getActivityById(id: String): Activity? {
        return dataSource.getActivityById(id)
    }

    override suspend fun addActivity(activity: Activity): Result<String> {
        val currentUser = authRepository.currentUser
        val preparedActivity = activity.copy(
            createdBy = currentUser?.uid ?: "",
            updatedBy = currentUser?.uid ?: ""
        )
        val result = dataSource.addActivity(preparedActivity)
        if (result.isSuccess) {
            val activityId = result.getOrNull() ?: ""
            activityLogDataSource.logActivity(
                userId = currentUser?.uid ?: "",
                userName = currentUser?.displayName ?: "قائد كشفي",
                action = "add",
                targetType = "activity",
                targetId = activityId,
                targetName = preparedActivity.title
            )
        }
        return result
    }

    override suspend fun updateActivity(activity: Activity): Result<Unit> {
        val currentUser = authRepository.currentUser
        val preparedActivity = activity.copy(
            updatedBy = currentUser?.uid ?: ""
        )
        val result = dataSource.updateActivity(preparedActivity)
        if (result.isSuccess) {
            activityLogDataSource.logActivity(
                userId = currentUser?.uid ?: "",
                userName = currentUser?.displayName ?: "قائد كشفي",
                action = "update",
                targetType = "activity",
                targetId = activity.id,
                targetName = activity.title
            )
        }
        return result
    }

    override suspend fun markAsDone(id: String): Result<Unit> {
        val currentUser = authRepository.currentUser
        val result = dataSource.updateStatus(
            id = id,
            newStatus = ActivityStatus.DONE.key,
            updatedBy = currentUser?.uid ?: ""
        )
        if (result.isSuccess) {
            activityLogDataSource.logActivity(
                userId = currentUser?.uid ?: "",
                userName = currentUser?.displayName ?: "قائد كشفي",
                action = "mark_done",
                targetType = "activity",
                targetId = id,
                targetName = "نشاط"
            )
        }
        return result
    }

    override suspend fun markAsDoneWithReport(id: String, report: String): Result<Unit> {
        val currentUser = authRepository.currentUser
        val existing = dataSource.getActivityById(id)
        val title = existing?.title ?: "نشاط"

        val result = dataSource.markAsDoneWithReport(
            id = id,
            report = report,
            updatedBy = currentUser?.uid ?: ""
        )
        if (result.isSuccess) {
            activityLogDataSource.logActivity(
                userId = currentUser?.uid ?: "",
                userName = currentUser?.displayName ?: "قائد كشفي",
                action = "mark_done",
                targetType = "activity",
                targetId = id,
                targetName = title
            )
        }
        return result
    }

    override suspend fun updateReport(id: String, report: String): Result<Unit> {
        val currentUser = authRepository.currentUser
        val existing = dataSource.getActivityById(id)
        val title = existing?.title ?: "نشاط"

        val result = dataSource.updateReport(
            id = id,
            report = report,
            updatedBy = currentUser?.uid ?: ""
        )
        if (result.isSuccess) {
            activityLogDataSource.logActivity(
                userId = currentUser?.uid ?: "",
                userName = currentUser?.displayName ?: "قائد كشفي",
                action = "update_report",
                targetType = "activity",
                targetId = id,
                targetName = title
            )
        }
        return result
    }

    override suspend fun cancelActivity(id: String): Result<Unit> {
        val currentUser = authRepository.currentUser
        val result = dataSource.updateStatus(
            id = id,
            newStatus = ActivityStatus.CANCELLED.key,
            updatedBy = currentUser?.uid ?: ""
        )
        if (result.isSuccess) {
            activityLogDataSource.logActivity(
                userId = currentUser?.uid ?: "",
                userName = currentUser?.displayName ?: "قائد كشفي",
                action = "cancel",
                targetType = "activity",
                targetId = id,
                targetName = "نشاط"
            )
        }
        return result
    }

    override suspend fun deleteActivity(id: String): Result<Unit> {
        val currentUser = authRepository.currentUser
        val existing = dataSource.getActivityById(id)
        val title = existing?.title ?: "نشاط"

        var cascadedAttendanceCount = 0
        var cascadedSubscriptionsCount = 0
        var cascadedContributionsCount = 0

        try {
            val firestore = FirebaseFirestore.getInstance()

            // 1) Delete attendance subcollection
            val attendanceCollection = firestore.collection("activities")
                .document(id)
                .collection("attendance")
            val attendanceSnapshot = attendanceCollection.get().await()
            cascadedAttendanceCount = attendanceSnapshot.size()
            if (cascadedAttendanceCount > 0) {
                attendanceSnapshot.documents.chunked(500).forEach { chunk ->
                    val batch = firestore.batch()
                    chunk.forEach { doc ->
                        batch.delete(doc.reference)
                    }
                    batch.commit().await()
                }
            }

            // 2) Delete finance_subscriptions for this activity
            val subsSnapshot = firestore.collection("finance_subscriptions")
                .whereEqualTo("activityId", id)
                .get()
                .await()
            cascadedSubscriptionsCount = subsSnapshot.size()
            if (cascadedSubscriptionsCount > 0) {
                subsSnapshot.documents.chunked(500).forEach { chunk ->
                    val batch = firestore.batch()
                    chunk.forEach { doc ->
                        batch.delete(doc.reference)
                    }
                    batch.commit().await()
                }
            }

            // 3) Delete finance_contributions for this activity
            val contribsSnapshot = firestore.collection("finance_contributions")
                .whereEqualTo("activityId", id)
                .get()
                .await()
            cascadedContributionsCount = contribsSnapshot.size()
            if (cascadedContributionsCount > 0) {
                contribsSnapshot.documents.chunked(500).forEach { chunk ->
                    val batch = firestore.batch()
                    chunk.forEach { doc ->
                        batch.delete(doc.reference)
                    }
                    batch.commit().await()
                }
            }
        } catch (e: Exception) {
            // Non-fatal if cascaded cleanup fails
        }

        val result = dataSource.deleteActivity(id)
        if (!result.isSuccess) {
            return result
        }

        activityLogDataSource.logActivity(
            userId = currentUser?.uid ?: "",
            userName = currentUser?.displayName ?: "قائد كشفي",
            action = "delete",
            targetType = "activity",
            targetId = id,
            targetName = title,
            meta = mapOf(
                "cascadedAttendanceCount" to cascadedAttendanceCount,
                "cascadedSubscriptionsCount" to cascadedSubscriptionsCount,
                "cascadedContributionsCount" to cascadedContributionsCount
            )
        )

        return Result.success(Unit)
    }

    override suspend fun updateSubscriptionOverride(activityId: String, override: Map<String, Int>?): Result<Unit> {
        val currentUser = authRepository.currentUser
        val result = dataSource.updateSubscriptionOverride(activityId, override)
        if (result.isSuccess) {
            val existing = dataSource.getActivityById(activityId)
            activityLogDataSource.logActivity(
                userId = currentUser?.uid ?: "",
                userName = currentUser?.displayName ?: "قائد كشفي",
                action = "update_subscription_override",
                targetType = "activity",
                targetId = activityId,
                targetName = existing?.title ?: "نشاط"
            )
        }
        return result
    }

    override suspend fun updateContributionSettings(activityId: String, required: Boolean, defaultAmount: Int?): Result<Unit> {
        val currentUser = authRepository.currentUser
        val result = dataSource.updateContributionSettings(activityId, required, defaultAmount)
        if (result.isSuccess) {
            val existing = dataSource.getActivityById(activityId)
            activityLogDataSource.logActivity(
                userId = currentUser?.uid ?: "",
                userName = currentUser?.displayName ?: "قائد كشفي",
                action = "update_contribution_settings",
                targetType = "activity",
                targetId = activityId,
                targetName = existing?.title ?: "نشاط"
            )
        }
        return result
    }
}
