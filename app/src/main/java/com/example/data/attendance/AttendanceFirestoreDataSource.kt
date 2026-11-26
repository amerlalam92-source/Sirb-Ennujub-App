package com.example.data.attendance

import android.util.Log
import com.example.data.common.DataLoadErrorReporter
import com.example.data.common.ListenerFallbackState
import com.example.data.common.attachServerResultListeners
import com.example.data.firestore.safeInt
import com.example.data.firestore.safeLong
import com.example.data.firestore.safeNullableInt
import com.example.data.firestore.safeNullableString
import com.example.data.firestore.safeString
import com.example.domain.attendance.Attendance
import com.example.domain.attendance.AttendancePointsSettings
import com.example.domain.attendance.AttendanceStatus
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * مصدر بيانات تسجيل الحضور وإعدادات النقاط عبر Cloud Firestore
 * يدعم الاستماع الفوري والحفظ الجماعي (Batch Writes) وإنشاء الإعدادات الافتراضية ذاتياً.
 */
class AttendanceFirestoreDataSource(
    private val firestore: FirebaseFirestore,
    private val errorReporter: DataLoadErrorReporter? = null
) {
    companion object {
        const val COLLECTION_ACTIVITIES = "activities"
        const val SUBCOLLECTION_ATTENDANCE = "attendance"
        const val COLLECTION_SETTINGS = "settings"
        const val DOC_POINTS_SETTINGS = "attendance_points"
    }

    private fun getAttendanceSubcollection(activityId: String) =
        firestore.collection(COLLECTION_ACTIVITIES)
            .document(activityId)
            .collection(SUBCOLLECTION_ATTENDANCE)

    private val settingsDoc
        get() = firestore.collection(COLLECTION_SETTINGS).document(DOC_POINTS_SETTINGS)

    /**
     * الاستماع لسجلات الحضور لنشاط محدد عبر المجموعة الفرعية
     * activities/{activityId}/attendance
     */
    fun getAttendanceForActivity(activityId: String): Flow<List<Attendance>> = callbackFlow {
        val fallbackState = ListenerFallbackState<Attendance>("attendance:activity:$activityId", errorReporter)
        val listenerRegistration = getAttendanceSubcollection(activityId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("AttendanceFirestoreDS", "Error observing attendance for activity $activityId", error)
                    trySend(fallbackState.onError(error))
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    mapDocumentToAttendance(doc, defaultActivityId = activityId)
                } ?: emptyList()
                trySend(fallbackState.onSnapshot(list))
            }
        awaitClose {
            listenerRegistration.remove()
            fallbackState.clear()
        }
    }

    /**
     * الاستماع لسجلات الحضور لشبل محدد عبر جميع الأنشطة باستخدام Collection Group
     */
    fun getAttendanceForScout(scoutId: String): Flow<List<Attendance>> = callbackFlow {
        val fallbackState = ListenerFallbackState<Attendance>("attendance:scout:$scoutId", errorReporter)
        val listenerRegistration = firestore.collectionGroup(SUBCOLLECTION_ATTENDANCE)
            .whereEqualTo("scoutId", scoutId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("AttendanceFirestoreDS", "Error observing attendance for scout $scoutId", error)
                    trySend(fallbackState.onError(error))
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    mapDocumentToAttendance(doc)
                } ?: emptyList()
                trySend(fallbackState.onSnapshot(list))
            }
        awaitClose {
            listenerRegistration.remove()
            fallbackState.clear()
        }
    }

    /**
     * الاستماع لسجلات الحضور عبر التطبيق منذ تاريخ محدد (لتقليل استهلاك القراءات في Firestore)
     */
    fun observeAllAttendance(sinceTimestamp: Long): Flow<List<Attendance>> = callbackFlow {
        val fallbackState = ListenerFallbackState<Attendance>("attendance:all:$sinceTimestamp", errorReporter)
        val listenerRegistration = firestore.collectionGroup(SUBCOLLECTION_ATTENDANCE)
            .whereGreaterThanOrEqualTo("activityDate", sinceTimestamp)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("AttendanceFirestoreDS", "Error observing attendance since $sinceTimestamp", error)
                    trySend(fallbackState.onError(error))
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    mapDocumentToAttendance(doc)
                } ?: emptyList()
                trySend(fallbackState.onSnapshot(list))
            }
        awaitClose {
            listenerRegistration.remove()
            fallbackState.clear()
        }
    }

    /**
     * حفظ سجل حضور فردي في المسار الفرعي:
     * activities/{activityId}/attendance/{scoutId}
     */
    suspend fun saveAttendance(
        attendance: Attendance,
        awaitServer: Boolean = true,
        onAsyncFailure: ((Throwable) -> Unit)? = null,
        onServerResult: ((Result<Unit>) -> Unit)? = null
    ): Result<Unit> {
        return try {
            val docRef = getAttendanceSubcollection(attendance.activityId).document(attendance.scoutId)
            val data = mapAttendanceToMap(attendance)
            val task = docRef.set(data)
            if (awaitServer) {
                task.await()
                onServerResult?.invoke(Result.success(Unit))
            } else {
                task.attachServerResultListeners(
                    logTag = "AttendanceFirestoreDS",
                    failureMessage = "Async failure saving attendance ${attendance.id}",
                    onAsyncFailure = onAsyncFailure,
                    onServerResult = onServerResult
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onServerResult?.invoke(Result.failure(e))
            Result.failure(e)
        }
    }

    /**
     * حفظ قائمة سجلات الحضور دفعة واحدة عبر Firestore Batch
     */
    suspend fun saveAttendanceBatch(list: List<Attendance>): Result<Unit> {
        if (list.isEmpty()) return Result.success(Unit)
        return try {
            list.chunked(500).forEach { chunk ->
                val batch = firestore.batch()
                for (item in chunk) {
                    val docRef = getAttendanceSubcollection(item.activityId).document(item.scoutId)
                    batch.set(docRef, mapAttendanceToMap(item))
                }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * حذف سجل حضور محدد من المجموعة الفرعية
     */
    suspend fun deleteAttendance(activityId: String, scoutId: String): Result<Unit> {
        return try {
            getAttendanceSubcollection(activityId).document(scoutId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * التحقق من بقاء أي سجل حضور في النشاط مباشرة من الخادم (قراءة مستند واحد فقط).
     * يعيد تمرير جميع الاستثناءات للمستدعي دون تحويل الخطأ إلى true أبداً.
     */
    suspend fun hasAnyAttendanceForActivity(activityId: String): Boolean {
        if (activityId.isBlank()) return false
        return !getAttendanceSubcollection(activityId)
            .limit(1)
            .get(Source.SERVER)
            .await()
            .isEmpty
    }

    /**
     * جلب جميع مستندات الحضور لنشاط محدد مباشرة من الخادم لأغراض الصيانة والمزامنة.
     */
    suspend fun getAttendanceDocumentsForActivityFromServer(activityId: String): List<DocumentSnapshot> {
        if (activityId.isBlank()) return emptyList()
        return getAttendanceSubcollection(activityId)
            .get(Source.SERVER)
            .await()
            .documents
    }

    /**
     * تحديث حقل activityDate و updatedAt لمجموعة من مستندات الحضور باستخدام batch.update() (بحد أقصى 500 لكل دفعة).
     */
    suspend fun updateAttendanceActivityDateBatch(
        documents: List<DocumentSnapshot>,
        newActivityDate: Long,
        updatedAt: Long = System.currentTimeMillis()
    ): Int {
        if (documents.isEmpty()) return 0
        documents.chunked(500).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { doc ->
                batch.update(
                    doc.reference,
                    mapOf(
                        "activityDate" to newActivityDate,
                        "updatedAt" to updatedAt
                    )
                )
            }
            batch.commit().await()
        }
        return documents.size
    }

    /**
     * الاستماع لإعدادات سلم التنقيط (مع الإنشاء التلقائي إذا لم تكن موجودة)
     */
    fun observePointsSettings(): Flow<AttendancePointsSettings> = callbackFlow {
        val listenerRegistration = settingsDoc.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w("AttendanceFirestoreDS", "Error observing points settings", error)
                trySend(AttendancePointsSettings())
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                trySend(mapDocumentToSettings(snapshot))
            } else {
                // إنشاء الإعدادات الافتراضية تلقائياً
                val defaultSettings = AttendancePointsSettings()
                settingsDoc.set(mapSettingsToMap(defaultSettings))
                trySend(defaultSettings)
            }
        }
        awaitClose { listenerRegistration.remove() }
    }

    /**
     * تحديث إعدادات سلم التنقيط
     */
    suspend fun updatePointsSettings(settings: AttendancePointsSettings): Result<Unit> {
        return try {
            settingsDoc.set(mapSettingsToMap(settings)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun mapDocumentToAttendance(doc: DocumentSnapshot, defaultActivityId: String? = null): Attendance? {
        return try {
            val actId = doc.safeNullableString("activityId")
                ?: defaultActivityId
                ?: doc.reference.parent.parent?.id
                ?: ""
            val scId = doc.safeNullableString("scoutId") ?: doc.id
            val now = System.currentTimeMillis()
            Attendance(
                activityId = actId,
                scoutId = scId,
                scoutFullName = doc.safeString("scoutFullName"),
                status = doc.safeString("status", AttendanceStatus.PRESENT.key),
                points = doc.safeInt("points", 0),
                activityDate = doc.safeLong("activityDate", 0L),
                unitId = doc.safeNullableString("unitId"),
                recordedBy = doc.safeString("recordedBy"),
                recordedByName = doc.safeString("recordedByName"),
                recordedAt = doc.safeLong("recordedAt", now),
                updatedAt = doc.safeLong("updatedAt", now)
            )
        } catch (e: Exception) {
            Log.w("AttendanceFirestoreDS", "Failed to map attendance ${doc.id}", e)
            null
        }
    }

    private fun mapAttendanceToMap(attendance: Attendance): Map<String, Any?> {
        return hashMapOf(
            "activityId" to attendance.activityId,
            "scoutId" to attendance.scoutId,
            "scoutFullName" to attendance.scoutFullName,
            "status" to attendance.status,
            "points" to attendance.points,
            "activityDate" to attendance.activityDate,
            "unitId" to attendance.unitId,
            "recordedBy" to attendance.recordedBy,
            "recordedByName" to attendance.recordedByName,
            "recordedAt" to attendance.recordedAt,
            "updatedAt" to attendance.updatedAt
        )
    }

    private fun mapDocumentToSettings(doc: DocumentSnapshot): AttendancePointsSettings {
        return try {
            // إذا كانت الحقول الجديدة متوفرة نأخذها، وإلا نستنتجها أو نأخذ الافتراضيات
            val basePoints = doc.safeInt("basePoints", 10)
            val earlyBonus = doc.safeNullableInt("earlyBonus")
                ?: (doc.safeInt("presentPoints", 12) - basePoints).coerceAtLeast(0)
            val lateDeduction = doc.safeNullableInt("lateDeduction")
                ?: (basePoints - doc.safeInt("latePoints", 7)).coerceAtLeast(0)
            val veryLateDeduction = doc.safeNullableInt("veryLateDeduction")
                ?: (basePoints - doc.safeInt("veryLatePoints", 2)).coerceAtLeast(0)
            val excusedDeduction = doc.safeNullableInt("excusedDeduction")
                ?: (basePoints - doc.safeInt("absentExcusedPoints", 4)).coerceAtLeast(0)
            val unexcusedDeduction = doc.safeNullableInt("unexcusedDeduction")
                ?: (basePoints - doc.safeInt("absentUnexcusedPoints", 0)).coerceAtLeast(0)
            val kafaaBadgePoints = doc.safeInt("kafaaBadgePoints", 10)
            val extraHobbyBadgePoints = doc.safeInt("extraHobbyBadgePoints", 5)

            AttendancePointsSettings(
                basePoints = basePoints,
                earlyBonus = earlyBonus,
                lateDeduction = lateDeduction,
                veryLateDeduction = veryLateDeduction,
                excusedDeduction = excusedDeduction,
                unexcusedDeduction = unexcusedDeduction,
                kafaaBadgePoints = kafaaBadgePoints,
                extraHobbyBadgePoints = extraHobbyBadgePoints,
                updatedAt = doc.safeLong("updatedAt", System.currentTimeMillis()),
                updatedBy = doc.safeString("updatedBy")
            )
        } catch (e: Exception) {
            Log.w("AttendanceFirestoreDS", "Failed to map attendance points settings", e)
            AttendancePointsSettings()
        }
    }

    private fun mapSettingsToMap(settings: AttendancePointsSettings): Map<String, Any?> {
        return hashMapOf(
            "basePoints" to settings.basePoints,
            "earlyBonus" to settings.earlyBonus,
            "lateDeduction" to settings.lateDeduction,
            "veryLateDeduction" to settings.veryLateDeduction,
            "excusedDeduction" to settings.excusedDeduction,
            "unexcusedDeduction" to settings.unexcusedDeduction,
            "kafaaBadgePoints" to settings.kafaaBadgePoints,
            "extraHobbyBadgePoints" to settings.extraHobbyBadgePoints,
            "presentPoints" to settings.presentPoints,
            "latePoints" to settings.latePoints,
            "veryLatePoints" to settings.veryLatePoints,
            "absentExcusedPoints" to settings.absentExcusedPoints,
            "absentUnexcusedPoints" to settings.absentUnexcusedPoints,
            "updatedAt" to settings.updatedAt,
            "updatedBy" to settings.updatedBy
        )
    }
}
