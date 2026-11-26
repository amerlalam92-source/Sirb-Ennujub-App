package com.example.data.activity

import android.util.Log
import com.example.data.common.DataLoadErrorReporter
import com.example.data.common.ListenerFallbackState
import com.example.data.common.attachServerResultListeners
import com.example.data.common.singleShotResultCallback
import com.example.data.firestore.safeBoolean
import com.example.data.firestore.safeLong
import com.example.data.firestore.safeNullableInt
import com.example.data.firestore.safeNullableString
import com.example.data.firestore.safeString
import com.example.data.firestore.safeStringList
import com.example.domain.activity.Activity
import com.example.domain.activity.ActivityStatus
import com.example.domain.activity.ActivityType
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * مصدر بيانات الأنشطة الكشفية عبر Cloud Firestore (Data Layer)
 * يدعم الاستماع الفوري ومزامنة التحديثات دون اتصال، وحفظ مواضيع الاجتماع والتقرير الأدبي.
 */
class ActivityFirestoreDataSource(
    private val firestore: FirebaseFirestore,
    private val errorReporter: DataLoadErrorReporter? = null
) {
    companion object {
        const val COLLECTION_ACTIVITIES = "activities"
    }

    private val collection
        get() = firestore.collection(COLLECTION_ACTIVITIES)

    /**
     * الاستماع لجميع الأنشطة مرتبة زمنياً
     */
    fun observeActivities(@Suppress("UNUSED_PARAMETER") unitId: String? = null): Flow<List<Activity>> = callbackFlow {
        val fallbackState = ListenerFallbackState<Activity>("activities:all", errorReporter)
        val listenerRegistration = collection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w("ActivityFirestoreDS", "Error observing activities", error)
                trySend(fallbackState.onError(error))
                return@addSnapshotListener
            }
            val activities = snapshot?.documents?.mapNotNull { doc ->
                mapDocumentToActivity(doc)
            }?.sortedBy { it.startDate } ?: emptyList()
            trySend(fallbackState.onSnapshot(activities))
        }
        awaitClose {
            listenerRegistration.remove()
            fallbackState.clear()
        }
    }

    /**
     * الاستماع للأنشطة القادمة فقط
     */
    fun observeUpcomingActivities(limit: Int = 20, @Suppress("UNUSED_PARAMETER") unitId: String? = null): Flow<List<Activity>> = callbackFlow {
        val fallbackState = ListenerFallbackState<Activity>("activities:upcoming", errorReporter)
        val listenerRegistration = collection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w("ActivityFirestoreDS", "Error observing upcoming activities", error)
                trySend(fallbackState.onError(error))
                return@addSnapshotListener
            }
            val activities = snapshot?.documents?.mapNotNull { doc ->
                mapDocumentToActivity(doc)
            }?.filter { it.isUpcoming() }
                ?.sortedBy { it.endDate }
                ?.take(limit)
                ?: emptyList()
            trySend(fallbackState.onSnapshot(activities))
        }
        awaitClose {
            listenerRegistration.remove()
            fallbackState.clear()
        }
    }

    /**
     * جلب نشاط محدد بالمعرف
     */
    suspend fun getActivityById(id: String): Activity? {
        return try {
            val doc = collection.document(id).get().await()
            if (doc.exists()) mapDocumentToActivity(doc) else null
        } catch (e: Exception) {
            Log.w("ActivityFirestoreDS", "Error getting activity $id", e)
            null
        }
    }

    /**
     * جلب جميع الأنشطة لعمليات الترحيل والصيانة
     */
    suspend fun getAllActivities(fromServer: Boolean = false): List<Activity> {
        val snapshot = if (fromServer) {
            collection.get(Source.SERVER).await()
        } else {
            collection.get().await()
        }
        return snapshot.documents.mapNotNull { doc ->
            mapDocumentToActivity(doc)
        }
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
            val updateMap = mapActivityToMap(updated).toMutableMap().apply {
                remove("hasAttendanceRecorded")
            }
            collection.document(activity.id).set(updateMap, SetOptions.merge()).await()
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
     * تحديث علم وجود سجلات حضور مسجلة على مستند النشاط لتجنب المسح الكلي
     */
    suspend fun updateHasAttendanceRecorded(
        activityId: String,
        hasRecorded: Boolean,
        awaitServer: Boolean = false,
        onAsyncFailure: ((Throwable) -> Unit)? = null,
        onServerResult: ((Result<Unit>) -> Unit)? = null
    ): Result<Unit> {
        val safeOnServerResult = singleShotResultCallback(onServerResult)
        if (activityId.isBlank()) {
            safeOnServerResult?.invoke(Result.success(Unit))
            return Result.success(Unit)
        }
        return try {
            val updates = mapOf(
                "hasAttendanceRecorded" to hasRecorded,
                "updatedAt" to System.currentTimeMillis()
            )
            val task = collection.document(activityId).update(updates)
            if (awaitServer) {
                task.await()
                safeOnServerResult?.invoke(Result.success(Unit))
            } else {
                task.attachServerResultListeners(
                    logTag = "ActivityFirestoreDS",
                    failureMessage = "Async failure updating hasAttendanceRecorded for $activityId",
                    onAsyncFailure = onAsyncFailure,
                    onServerResult = safeOnServerResult
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            safeOnServerResult?.invoke(Result.failure(e))
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

    private fun mapDocumentToActivity(doc: DocumentSnapshot): Activity? {
        return try {
            val topicsList = doc.safeStringList("meetingTopics")
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

            val now = System.currentTimeMillis()
            val startDate = doc.safeLong("startDate", now)

            Activity(
                id = doc.id,
                title = doc.safeString("title"),
                type = doc.safeString("type", ActivityType.TRAINING_MEETING.key),
                startDate = startDate,
                endDate = doc.safeLong("endDate", startDate),
                startTime = doc.safeString("startTime"),
                endTime = doc.safeString("endTime"),
                location = doc.safeString("location"),
                objectives = doc.safeString("objectives"),
                tools = doc.safeString("tools"),
                responsibleName = doc.safeString("responsibleName"),
                status = doc.safeString("status", ActivityStatus.PLANNED.key),
                notes = doc.safeString("notes"),
                meetingTopics = topicsList,
                finalReport = doc.safeString("finalReport"),
                subscriptionOverride = parsedOverride,
                contributionRequired = doc.safeBoolean("contributionRequired", false),
                contributionDefaultAmount = doc.safeNullableInt("contributionDefaultAmount"),
                collectSubscriptions = doc.safeBoolean("collectSubscriptions", true),
                customSubscriptionAmount = doc.safeNullableInt("customSubscriptionAmount"),
                hasAttendanceRecorded = doc.safeBoolean("hasAttendanceRecorded", false),
                unitId = doc.safeNullableString("unitId"),
                createdAt = doc.safeLong("createdAt", doc.safeLong("startDate", 0L)),
                updatedAt = doc.safeLong("updatedAt", now),
                createdBy = doc.safeString("createdBy"),
                updatedBy = doc.safeString("updatedBy")
            )
        } catch (e: Exception) {
            Log.w("ActivityFirestoreDS", "Failed to map activity ${doc.id}", e)
            null
        }
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
            "customSubscriptionAmount" to activity.customSubscriptionAmount,
            "hasAttendanceRecorded" to activity.hasAttendanceRecorded,
            "unitId" to activity.unitId,
            "createdAt" to activity.createdAt,
            "updatedAt" to activity.updatedAt,
            "createdBy" to activity.createdBy,
            "updatedBy" to activity.updatedBy
        )
    }
}
