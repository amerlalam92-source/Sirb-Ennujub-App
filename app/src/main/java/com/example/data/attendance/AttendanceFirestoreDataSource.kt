package com.example.data.attendance

import com.example.domain.attendance.Attendance
import com.example.domain.attendance.AttendancePointsSettings
import com.example.domain.attendance.AttendanceStatus
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * مصدر بيانات تسجيل الحضور وإعدادات النقاط عبر Cloud Firestore
 * يدعم الاستماع الفوري والحفظ الجماعي (Batch Writes) وإنشاء الإعدادات الافتراضية ذاتياً.
 */
class AttendanceFirestoreDataSource(
    private val firestore: FirebaseFirestore
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
        val listenerRegistration = getAttendanceSubcollection(activityId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    mapDocumentToAttendance(doc, defaultActivityId = activityId)
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listenerRegistration.remove() }
    }

    /**
     * الاستماع لجميع معرفات الأنشطة التي تم تسجيل حضور لها عبر استعلام المجموعة الكلي
     */
    fun observeAllRecordedActivityIds(): Flow<Set<String>> = callbackFlow {
        val listenerRegistration = firestore.collectionGroup(SUBCOLLECTION_ATTENDANCE)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptySet())
                    return@addSnapshotListener
                }
                val set = snapshot?.documents?.mapNotNull { doc ->
                    doc.getString("activityId") ?: doc.reference.parent.parent?.id
                }?.toSet() ?: emptySet()
                trySend(set)
            }
        awaitClose { listenerRegistration.remove() }
    }

    /**
     * الاستماع لسجلات الحضور لشبل محدد عبر جميع الأنشطة باستخدام Collection Group
     */
    fun getAttendanceForScout(scoutId: String): Flow<List<Attendance>> = callbackFlow {
        val listenerRegistration = firestore.collectionGroup(SUBCOLLECTION_ATTENDANCE)
            .whereEqualTo("scoutId", scoutId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    mapDocumentToAttendance(doc)
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listenerRegistration.remove() }
    }

    /**
     * الاستماع لجميع سجلات الحضور عبر التطبيق باستخدام Collection Group
     */
    fun observeAllAttendance(): Flow<List<Attendance>> = callbackFlow {
        val listenerRegistration = firestore.collectionGroup(SUBCOLLECTION_ATTENDANCE)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    mapDocumentToAttendance(doc)
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listenerRegistration.remove() }
    }

    /**
     * حفظ سجل حضور فردي في المسار الفرعي:
     * activities/{activityId}/attendance/{scoutId}
     */
    suspend fun saveAttendance(attendance: Attendance): Result<Unit> {
        return try {
            val docRef = getAttendanceSubcollection(attendance.activityId).document(attendance.scoutId)
            docRef.set(mapAttendanceToMap(attendance)).await()
            Result.success(Unit)
        } catch (e: Exception) {
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
     * الاستماع لإعدادات سلم التنقيط (مع الإنشاء التلقائي إذا لم تكن موجودة)
     */
    fun observePointsSettings(): Flow<AttendancePointsSettings> = callbackFlow {
        val listenerRegistration = settingsDoc.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
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

    private fun mapDocumentToAttendance(doc: DocumentSnapshot, defaultActivityId: String? = null): Attendance {
        val actId = doc.getString("activityId")
            ?: defaultActivityId
            ?: doc.reference.parent.parent?.id
            ?: ""
        val scId = doc.getString("scoutId") ?: doc.id
        return Attendance(
            activityId = actId,
            scoutId = scId,
            scoutFullName = doc.getString("scoutFullName") ?: "",
            status = doc.getString("status") ?: AttendanceStatus.PRESENT.key,
            points = doc.getLong("points")?.toInt() ?: 0,
            activityDate = doc.getLong("activityDate") ?: 0L,
            recordedBy = doc.getString("recordedBy") ?: "",
            recordedByName = doc.getString("recordedByName") ?: "",
            recordedAt = doc.getLong("recordedAt") ?: System.currentTimeMillis(),
            updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
        )
    }

    private fun mapAttendanceToMap(attendance: Attendance): Map<String, Any?> {
        return hashMapOf(
            "activityId" to attendance.activityId,
            "scoutId" to attendance.scoutId,
            "scoutFullName" to attendance.scoutFullName,
            "status" to attendance.status,
            "points" to attendance.points,
            "activityDate" to attendance.activityDate,
            "recordedBy" to attendance.recordedBy,
            "recordedByName" to attendance.recordedByName,
            "recordedAt" to attendance.recordedAt,
            "updatedAt" to attendance.updatedAt
        )
    }

    private fun mapDocumentToSettings(doc: DocumentSnapshot): AttendancePointsSettings {
        // إذا كانت الحقول الجديدة متوفرة نأخذها، وإلا نستنتجها أو نأخذ الافتراضيات
        val basePoints = doc.getLong("basePoints")?.toInt() ?: 10
        val earlyBonus = doc.getLong("earlyBonus")?.toInt()
            ?: ((doc.getLong("presentPoints")?.toInt() ?: 12) - basePoints).coerceAtLeast(0)
        val lateDeduction = doc.getLong("lateDeduction")?.toInt()
            ?: (basePoints - (doc.getLong("latePoints")?.toInt() ?: 7)).coerceAtLeast(0)
        val veryLateDeduction = doc.getLong("veryLateDeduction")?.toInt()
            ?: (basePoints - (doc.getLong("veryLatePoints")?.toInt() ?: 2)).coerceAtLeast(0)
        val excusedDeduction = doc.getLong("excusedDeduction")?.toInt()
            ?: (basePoints - (doc.getLong("absentExcusedPoints")?.toInt() ?: 4)).coerceAtLeast(0)
        val unexcusedDeduction = doc.getLong("unexcusedDeduction")?.toInt()
            ?: (basePoints - (doc.getLong("absentUnexcusedPoints")?.toInt() ?: 0)).coerceAtLeast(0)
        val kafaaBadgePoints = doc.getLong("kafaaBadgePoints")?.toInt() ?: 10
        val extraHobbyBadgePoints = doc.getLong("extraHobbyBadgePoints")?.toInt() ?: 5

        return AttendancePointsSettings(
            basePoints = basePoints,
            earlyBonus = earlyBonus,
            lateDeduction = lateDeduction,
            veryLateDeduction = veryLateDeduction,
            excusedDeduction = excusedDeduction,
            unexcusedDeduction = unexcusedDeduction,
            kafaaBadgePoints = kafaaBadgePoints,
            extraHobbyBadgePoints = extraHobbyBadgePoints,
            updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis(),
            updatedBy = doc.getString("updatedBy") ?: ""
        )
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
