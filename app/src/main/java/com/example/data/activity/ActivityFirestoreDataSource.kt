package com.example.data.activity

import com.example.domain.activity.Activity
import com.example.domain.activity.ActivityStatus
import com.example.domain.activity.ActivityType
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * مصدر بيانات الأنشطة الكشفية عبر Cloud Firestore (Data Layer)
 * يدعم الاستماع الفوري ومزامنة التحديثات دون اتصال، وحفظ مواضيع الاجتماع والتقرير الأدبي.
 */
class ActivityFirestoreDataSource(
    private val firestore: FirebaseFirestore
) {
    companion object {
        const val COLLECTION_ACTIVITIES = "activities"
    }

    private val collection
        get() = firestore.collection(COLLECTION_ACTIVITIES)

    /**
     * الاستماع لجميع الأنشطة مرتبة زمنياً
     */
    fun observeActivities(): Flow<List<Activity>> = callbackFlow {
        val listenerRegistration = collection
            .orderBy("startDate", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val activities = snapshot?.documents?.mapNotNull { doc ->
                    mapDocumentToActivity(doc)
                } ?: emptyList()
                trySend(activities)
            }
        awaitClose { listenerRegistration.remove() }
    }

    /**
     * الاستماع للأنشطة القادمة فقط
     */
    fun observeUpcomingActivities(limit: Int = 20): Flow<List<Activity>> = callbackFlow {
        val todayStart = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

        val listenerRegistration = collection
            .whereGreaterThanOrEqualTo("endDate", todayStart)
            .orderBy("endDate", Query.Direction.ASCENDING)
            .limit(limit.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val activities = snapshot?.documents?.mapNotNull { doc ->
                    mapDocumentToActivity(doc)
                } ?: emptyList()
                trySend(activities)
            }
        awaitClose { listenerRegistration.remove() }
    }

    /**
     * جلب نشاط محدد بالمعرف
     */
    suspend fun getActivityById(id: String): Activity? {
        val doc = collection.document(id).get().await()
        return if (doc.exists()) mapDocumentToActivity(doc) else null
    }

    /**
     * إضافة نشاط كشفي جديد
     */
    suspend fun addActivity(activity: Activity): Result<String> {
        val validation = validateActivity(activity)
        if (validation.isFailure) return Result.failure(validation.exceptionOrNull()!!)

        return try {
            val docRef = if (activity.id.isNotBlank()) collection.document(activity.id) else collection.document()
            val finalActivity = activity.copy(
                id = docRef.id,
                createdAt = if (activity.createdAt > 0) activity.createdAt else System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            docRef.set(mapActivityToMap(finalActivity)).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * تحديث بيانات نشاط كشفي
     */
    suspend fun updateActivity(activity: Activity): Result<Unit> {
        val validation = validateActivity(activity)
        if (validation.isFailure) return Result.failure(validation.exceptionOrNull()!!)
        if (activity.id.isBlank()) return Result.failure(IllegalArgumentException("معرف النشاط مفقود"))

        return try {
            val updated = activity.copy(updatedAt = System.currentTimeMillis())
            collection.document(activity.id).set(mapActivityToMap(updated)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * تحديث حالة النشاط (منجز / ملغى / مخطط)
     */
    suspend fun updateStatus(id: String, newStatus: String, updatedBy: String = ""): Result<Unit> {
        if (id.isBlank()) return Result.failure(IllegalArgumentException("معرف النشاط مفقود"))
        if (newStatus !in ActivityStatus.getAllKeys()) {
            return Result.failure(IllegalArgumentException("حالة النشاط غير صالحة"))
        }

        return try {
            val updates = mutableMapOf<String, Any>(
                "status" to newStatus,
                "updatedAt" to System.currentTimeMillis()
            )
            if (updatedBy.isNotBlank()) {
                updates["updatedBy"] = updatedBy
            }
            collection.document(id).update(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * تحديد النشاط كمنجز مع التقرير الأدبي الإلزامي
     */
    suspend fun markAsDoneWithReport(id: String, report: String, updatedBy: String = ""): Result<Unit> {
        val trimmedReport = report.trim()
        val wordsCount = if (trimmedReport.isEmpty()) 0 else trimmedReport.split("\\s+".toRegex()).count { it.isNotBlank() }
        if (wordsCount < 80) {
            return Result.failure(IllegalArgumentException("يرجى كتابة تقرير لا يقل عن 80 كلمة (حوالي 500 حرف)"))
        }

        return try {
            val updates = mutableMapOf<String, Any>(
                "status" to ActivityStatus.DONE.key,
                "finalReport" to trimmedReport,
                "updatedAt" to System.currentTimeMillis()
            )
            if (updatedBy.isNotBlank()) {
                updates["updatedBy"] = updatedBy
            }
            collection.document(id).update(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * تحديث التقرير الأدبي فقط للنشاط المنجز
     */
    suspend fun updateReport(id: String, report: String, updatedBy: String = ""): Result<Unit> {
        if (id.isBlank()) return Result.failure(IllegalArgumentException("معرف النشاط مفقود"))
        val trimmedReport = report.trim()
        val wordsCount = if (trimmedReport.isEmpty()) 0 else trimmedReport.split("\\s+".toRegex()).count { it.isNotBlank() }
        if (wordsCount < 80) {
            return Result.failure(IllegalArgumentException("يرجى كتابة تقرير لا يقل عن 80 كلمة (حوالي 500 حرف)"))
        }

        return try {
            val updates = mutableMapOf<String, Any>(
                "finalReport" to trimmedReport,
                "updatedAt" to System.currentTimeMillis()
            )
            if (updatedBy.isNotBlank()) {
                updates["updatedBy"] = updatedBy
            }
            collection.document(id).update(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * حذف نشاط كشفي
     */
    suspend fun deleteActivity(id: String): Result<Unit> {
        if (id.isBlank()) return Result.failure(IllegalArgumentException("معرف النشاط مفقود"))
        return try {
            collection.document(id).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * تحديث مبالغ الاشتراكات المخصصة لنشاط محدد
     */
    suspend fun updateSubscriptionOverride(id: String, override: Map<String, Int>?): Result<Unit> {
        if (id.isBlank()) return Result.failure(IllegalArgumentException("معرف النشاط مفقود"))
        return try {
            val updateData = hashMapOf<String, Any?>(
                "subscriptionOverride" to override,
                "updatedAt" to System.currentTimeMillis()
            )
            collection.document(id).update(updateData).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * تحديث إعدادات المساهمات المالية للنشاط (هل المساهمة مطلوبة والمبلغ الافتراضي)
     */
    suspend fun updateContributionSettings(id: String, required: Boolean, defaultAmount: Int?): Result<Unit> {
        if (id.isBlank()) return Result.failure(IllegalArgumentException("معرف النشاط مفقود"))
        return try {
            val updateData = hashMapOf<String, Any?>(
                "contributionRequired" to required,
                "contributionDefaultAmount" to defaultAmount,
                "updatedAt" to System.currentTimeMillis()
            )
            collection.document(id).update(updateData).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * التحقق الصارم من مدخلات النشاط
     */
    private fun validateActivity(activity: Activity): Result<Unit> {
        val title = activity.title.trim()
        if (title.isBlank() || title.length < 3) {
            return Result.failure(IllegalArgumentException("عنوان النشاط مطلوب ويجب ألا يقل عن 3 أحرف"))
        }

        if (activity.endDate < activity.startDate) {
            return Result.failure(IllegalArgumentException("تاريخ نهاية النشاط يجب أن يكون مساوياً أو بعد تاريخ البداية"))
        }

        if (activity.type !in ActivityType.getAllKeys()) {
            return Result.failure(IllegalArgumentException("نوع النشاط غير صحيح"))
        }

        if (activity.status !in ActivityStatus.getAllKeys()) {
            return Result.failure(IllegalArgumentException("حالة النشاط غير صحيحة"))
        }

        return Result.success(Unit)
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapDocumentToActivity(doc: DocumentSnapshot): Activity {
        val topicsList = doc.get("meetingTopics") as? List<String> ?: emptyList()
        val rawOverride = (doc.get("subscriptionOverride") ?: doc.get("subscriptionAmountOverride")) as? Map<*, *>
        val parsedOverride = rawOverride?.mapNotNull { (k, v) ->
            val key = k as? String
            val value = when (v) {
                is Number -> v.toInt()
                is String -> v.toIntOrNull()
                else -> null
            }
            if (key != null && value != null) key to value else null
        }?.toMap()

        return Activity(
            id = doc.id,
            title = doc.getString("title") ?: "",
            type = doc.getString("type") ?: ActivityType.TRAINING_MEETING.key,
            startDate = doc.getLong("startDate") ?: System.currentTimeMillis(),
            endDate = doc.getLong("endDate") ?: System.currentTimeMillis(),
            startTime = doc.getString("startTime") ?: "",
            endTime = doc.getString("endTime") ?: "",
            location = doc.getString("location") ?: "",
            objectives = doc.getString("objectives") ?: "",
            tools = doc.getString("tools") ?: "",
            responsibleName = doc.getString("responsibleName") ?: "",
            status = doc.getString("status") ?: ActivityStatus.PLANNED.key,
            notes = doc.getString("notes") ?: "",
            meetingTopics = topicsList,
            finalReport = doc.getString("finalReport") ?: "",
            subscriptionOverride = parsedOverride,
            contributionRequired = doc.getBoolean("contributionRequired") ?: false,
            contributionDefaultAmount = doc.getLong("contributionDefaultAmount")?.toInt(),
            collectSubscriptions = doc.getBoolean("collectSubscriptions") ?: true,
            createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
            updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis(),
            createdBy = doc.getString("createdBy") ?: "",
            updatedBy = doc.getString("updatedBy") ?: ""
        )
    }

    private fun mapActivityToMap(activity: Activity): Map<String, Any?> {
        val isMeeting = activity.activityType.isMeetingType()
        val cleanedTopics = if (isMeeting) {
            activity.meetingTopics.map { it.trim() }.filter { it.isNotBlank() }
        } else {
            emptyList()
        }

        return hashMapOf(
            "id" to activity.id,
            "title" to activity.title.trim(),
            "type" to activity.type,
            "startDate" to activity.startDate,
            "endDate" to activity.endDate,
            "startTime" to activity.startTime.trim(),
            "endTime" to activity.endTime.trim(),
            "location" to activity.location.trim(),
            "objectives" to activity.objectives.trim(),
            "tools" to activity.tools.trim(),
            "responsibleName" to activity.responsibleName.trim(),
            "status" to activity.status,
            "notes" to activity.notes.trim(),
            "meetingTopics" to cleanedTopics,
            "finalReport" to activity.finalReport.trim(),
            "subscriptionOverride" to activity.subscriptionOverride,
            "contributionRequired" to activity.contributionRequired,
            "contributionDefaultAmount" to activity.contributionDefaultAmount,
            "collectSubscriptions" to activity.collectSubscriptions,
            "createdAt" to activity.createdAt,
            "updatedAt" to activity.updatedAt,
            "createdBy" to activity.createdBy,
            "updatedBy" to activity.updatedBy
        )
    }
}
