package com.example.data.activity

import com.example.data.log.ActivityLogDataSource
import com.example.domain.activity.Activity
import com.example.domain.activity.ActivityRepository
import com.example.domain.activity.ActivityStatus
import com.example.domain.auth.AuthRepository
import com.example.domain.finance.FinanceRepository
import com.example.domain.finance.FinanceTransactionType
import com.example.domain.scout.ScoutRepository
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
    private val authRepository: AuthRepository,
    private val financeRepository: FinanceRepository,
    private val scoutRepository: ScoutRepository
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
        val currentUnitId = activity.unitId ?: authRepository.getCurrentLeader()?.unitId
        val preparedActivity = activity.copy(
            unitId = currentUnitId,
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
                targetName = preparedActivity.title,
                unitId = currentUnitId
            )
        }
        return result
    }

    override suspend fun updateActivity(activity: Activity): Result<Unit> {
        val currentUser = authRepository.currentUser
        val oldActivity = dataSource.getActivityById(activity.id) ?: return Result.failure(Exception("النشاط غير موجود"))

        val changes = mutableListOf<String>()

        fun isChanged(oldVal: String?, newVal: String?): Boolean {
            return oldVal?.trim().orEmpty() != newVal?.trim().orEmpty()
        }

        if (isChanged(oldActivity.title, activity.title)) {
            changes.add("العنوان: من '${oldActivity.title}' إلى '${activity.title}'")
        }
        if (isChanged(oldActivity.location, activity.location)) {
            changes.add("المكان: من '${oldActivity.location}' إلى '${activity.location}'")
        }
        if (isChanged(oldActivity.objectives, activity.objectives)) {
            changes.add("الأهداف تم تعديلها")
        }
        if (isChanged(oldActivity.tools, activity.tools)) {
            changes.add("الوسائل تم تعديلها")
        }
        if (isChanged(oldActivity.responsibleName, activity.responsibleName)) {
            changes.add("المسؤول: من '${oldActivity.responsibleName}' إلى '${activity.responsibleName}'")
        }
        if (isChanged(oldActivity.notes, activity.notes)) {
            changes.add("الملاحظات تم تعديلها")
        }
        if (isChanged(oldActivity.startTime, activity.startTime)) {
            changes.add("وقت البدء: من '${oldActivity.startTime}' إلى '${activity.startTime}'")
        }
        if (isChanged(oldActivity.endTime, activity.endTime)) {
            changes.add("وقت الانتهاء: من '${oldActivity.endTime}' إلى '${activity.endTime}'")
        }
        if (isChanged(oldActivity.type, activity.type)) {
            changes.add("نوع النشاط: من '${oldActivity.activityType.arabicLabel}' إلى '${activity.activityType.arabicLabel}'")
        }
        if (isChanged(oldActivity.status, activity.status)) {
            changes.add("الحالة: من '${oldActivity.activityStatus.arabicLabel}' إلى '${activity.activityStatus.arabicLabel}'")
        }
        if (isChanged(oldActivity.finalReport, activity.finalReport)) {
            changes.add("التقرير النهائي تم تعديله")
        }

        // Dates, Ints, Booleans, Lists
        if (oldActivity.startDate != activity.startDate) changes.add("تاريخ البدء تم تعديله")
        if (oldActivity.endDate != activity.endDate) changes.add("تاريخ الانتهاء تم تعديله")
        if (oldActivity.collectSubscriptions != activity.collectSubscriptions) {
            changes.add("تحصيل الاشتراكات: من ${oldActivity.collectSubscriptions} إلى ${activity.collectSubscriptions}")
        }
        if (oldActivity.customSubscriptionAmount != activity.customSubscriptionAmount) {
            changes.add("قيمة الاشتراك المخصصة: من ${oldActivity.customSubscriptionAmount ?: 0} إلى ${activity.customSubscriptionAmount ?: 0}")
        }
        val oldTopics = oldActivity.meetingTopics.map { it.trim() }.filter { it.isNotEmpty() }
        val newTopics = activity.meetingTopics.map { it.trim() }.filter { it.isNotEmpty() }
        if (oldTopics != newTopics) changes.add("جدول الأعمال تم تعديله")

        // إذا لم يتم تغيير أي حقل، لا يتم حفظ أي شيء في Firestore ولا يسجل أي عملية في سجل الأنشطة
        if (changes.isEmpty()) {
            return Result.success(Unit)
        }

        val currentUnitId = activity.unitId ?: oldActivity.unitId ?: authRepository.getCurrentLeader()?.unitId
        val preparedActivity = activity.copy(
            unitId = currentUnitId,
            updatedBy = currentUser?.uid ?: ""
        )
        val result = dataSource.updateActivity(preparedActivity)
        if (result.isSuccess) {
            val detailsText = changes.joinToString(" | ")
            activityLogDataSource.logActivity(
                userId = currentUser?.uid ?: "",
                userName = currentUser?.displayName ?: "قائد كشفي",
                action = "update",
                targetType = "activity",
                targetId = activity.id,
                targetName = activity.title,
                details = detailsText,
                unitId = currentUnitId
            )
        }
        return result
    }

    override suspend fun markAsDone(id: String): Result<Unit> {
        val currentUser = authRepository.currentUser
        val existing = dataSource.getActivityById(id)
        val currentUnitId = existing?.unitId ?: authRepository.getCurrentLeader()?.unitId
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
                targetName = existing?.title ?: "نشاط",
                unitId = currentUnitId
            )
        }
        return result
    }

    override suspend fun markAsDoneWithReport(id: String, report: String): Result<Unit> {
        val currentUser = authRepository.currentUser
        val existing = dataSource.getActivityById(id)
        val title = existing?.title ?: "نشاط"
        val currentUnitId = existing?.unitId ?: authRepository.getCurrentLeader()?.unitId

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
                targetName = title,
                unitId = currentUnitId
            )
        }
        return result
    }

    override suspend fun updateReport(id: String, report: String): Result<Unit> {
        val currentUser = authRepository.currentUser
        val existing = dataSource.getActivityById(id)
        val title = existing?.title ?: "نشاط"
        val currentUnitId = existing?.unitId ?: authRepository.getCurrentLeader()?.unitId

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
                targetName = title,
                unitId = currentUnitId
            )
        }
        return result
    }

    override suspend fun cancelActivity(id: String): Result<Unit> {
        val currentUser = authRepository.currentUser
        val existing = dataSource.getActivityById(id)
        val currentUnitId = existing?.unitId ?: authRepository.getCurrentLeader()?.unitId
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
                targetName = existing?.title ?: "نشاط",
                unitId = currentUnitId
            )
        }
        return result
    }

    override suspend fun deleteActivity(id: String): Result<Unit> {
        val currentUser = authRepository.currentUser
        val existing = dataSource.getActivityById(id)
        if (existing != null && existing.isLegacy()) {
            return Result.failure(
                IllegalStateException("لا يمكن حذف نشاط قديم لحماية البيانات المالية. يرجى تعديل البيانات يدوياً أولاً.")
            )
        }
        val title = existing?.title ?: "نشاط"
        val currentUnitId = existing?.unitId ?: authRepository.getCurrentLeader()?.unitId

        var cascadedAttendanceCount = 0
        var cascadedSubscriptionsCount = 0
        var cascadedTransactionsCount = 0

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

            // 3) Rollback debts & advance credits linked via relatedActivityId BEFORE deleting transactions
            val txByRelatedActivityId = firestore.collection("finance_transactions")
                .whereEqualTo("relatedActivityId", id)
                .get()
                .await()

            for (doc in txByRelatedActivityId.documents) {
                val txType = FinanceTransactionType.fromKey(doc.getString("type"))
                val scoutId = doc.getString("scoutId")?.takeIf { it.isNotBlank() }
                val amount = (doc.get("amount") as? Number)?.toInt()
                    ?: doc.getLong("amount")?.toInt()
                    ?: 0
                if (scoutId != null && amount > 0) {
                    when (txType) {
                        FinanceTransactionType.DEBT_PAYMENT -> {
                            financeRepository.rollbackScoutDebts(scoutId, amount)
                        }
                        FinanceTransactionType.ADVANCE_PAYMENT -> {
                            scoutRepository.deductCreditFromScout(scoutId, amount)
                        }
                        else -> Unit
                    }
                }
            }

            // 4) Delete finance_transactions for this activity (activityId == id OR targetId == id OR relatedActivityId == id)
            val txByActivityId = firestore.collection("finance_transactions")
                .whereEqualTo("activityId", id)
                .get()
                .await()
            val txByTargetId = firestore.collection("finance_transactions")
                .whereEqualTo("targetId", id)
                .get()
                .await()
            val allTxDocs = (txByActivityId.documents + txByTargetId.documents + txByRelatedActivityId.documents)
                .distinctBy { it.id }
            cascadedTransactionsCount = allTxDocs.size
            if (cascadedTransactionsCount > 0) {
                allTxDocs.chunked(500).forEach { chunk ->
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
            unitId = currentUnitId,
            meta = mapOf(
                "cascadedAttendanceCount" to cascadedAttendanceCount,
                "cascadedSubscriptionsCount" to cascadedSubscriptionsCount,
                "cascadedTransactionsCount" to cascadedTransactionsCount
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
}
