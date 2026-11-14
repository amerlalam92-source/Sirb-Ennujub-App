package com.example.data.log

import android.util.Log
import com.example.domain.log.ActivityLog
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * مصدر بيانات سجل العمليات والتدقيق (Activity Log Data Source)
 * يسجل ويسترجع كافة العمليات المنفذة في مجموعة "activity_log"
 * دون استخدام فهارس مركبة (composite indexes) حيث تتم التصفية والترتيب في الذاكرة.
 */
class ActivityLogDataSource(
    private val firestore: FirebaseFirestore
) {
    companion object {
        private const val TAG = "ActivityLog"
        private const val COLLECTION_NAME = "activity_log"
    }

    private val logScope = CoroutineScope(Dispatchers.IO)

    fun logActivity(
        userId: String,
        userName: String,
        action: String, // "add", "update", "archive", "delete", "mark_done", "cancel", etc.
        targetType: String = "scout", // "scout", "activity", "attendance", "finance", "evaluation", etc.
        targetId: String = "",
        targetName: String,
        details: String? = null,
        meta: Map<String, Any?>? = null,
        unitId: String? = null
    ) {
        logScope.launch {
            try {
                val logEntry = hashMapOf<String, Any?>(
                    "timestamp" to System.currentTimeMillis(),
                    "userId" to userId,
                    "userName" to userName,
                    "action" to action,
                    "targetType" to targetType,
                    "targetId" to targetId,
                    "targetName" to targetName
                )
                if (!details.isNullOrBlank()) {
                    logEntry["details"] = details
                }
                if (!unitId.isNullOrBlank()) {
                    logEntry["unitId"] = unitId
                }
                if (meta != null) {
                    logEntry["meta"] = meta
                }
                firestore.collection(COLLECTION_NAME).add(logEntry).await()
                Log.d(TAG, "Activity recorded: $action on $targetType ($targetName)")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to record activity log (non-fatal): ${e.message}")
            }
        }
    }

    suspend fun logAction(
        action: String,
        targetType: String,
        targetName: String,
        targetId: String = "",
        userId: String = "",
        userName: String = "قائد كشفي",
        details: String? = null,
        meta: Map<String, Any?>? = null,
        unitId: String? = null
    ) {
        logActivity(
            userId = userId,
            userName = userName,
            action = action,
            targetType = targetType,
            targetId = targetId,
            targetName = targetName,
            details = details,
            meta = meta,
            unitId = unitId
        )
    }

    /**
     * مراقبة كافة العمليات المسجلة بتدفق مباشر ومفلترة بالوحدة
     */
    fun observeLogs(unitId: String? = null): Flow<List<ActivityLog>> = callbackFlow {
        val query = if (!unitId.isNullOrBlank()) {
            firestore.collection(COLLECTION_NAME).whereEqualTo("unitId", unitId)
        } else {
            firestore.collection(COLLECTION_NAME)
        }
        val listenerRegistration = query
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Error observing activity logs: ${error.message}")
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val logs = snapshot.documents.mapNotNull { doc ->
                        try {
                            ActivityLog(
                                id = doc.id,
                                timestamp = doc.getLong("timestamp") ?: 0L,
                                userId = doc.getString("userId").orEmpty(),
                                userName = doc.getString("userName") ?: "قائد كشفي",
                                action = doc.getString("action").orEmpty(),
                                targetType = doc.getString("targetType").orEmpty(),
                                targetId = doc.getString("targetId").orEmpty(),
                                targetName = doc.getString("targetName").orEmpty(),
                                details = doc.getString("details"),
                                unitId = doc.getString("unitId"),
                                meta = doc.get("meta") as? Map<String, Any?>
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }.sortedByDescending { it.timestamp }
                    trySend(logs)
                }
            }
        awaitClose { listenerRegistration.remove() }
    }

    /**
     * جلب كافة العمليات المسجلة لمرة واحدة ومفلترة بالوحدة
     */
    suspend fun getLogs(unitId: String? = null): List<ActivityLog> {
        return try {
            val query = if (!unitId.isNullOrBlank()) {
                firestore.collection(COLLECTION_NAME).whereEqualTo("unitId", unitId)
            } else {
                firestore.collection(COLLECTION_NAME)
            }
            val snapshot = query.get().await()
            snapshot.documents.mapNotNull { doc ->
                try {
                    ActivityLog(
                        id = doc.id,
                        timestamp = doc.getLong("timestamp") ?: 0L,
                        userId = doc.getString("userId").orEmpty(),
                        userName = doc.getString("userName") ?: "قائد كشفي",
                        action = doc.getString("action").orEmpty(),
                        targetType = doc.getString("targetType").orEmpty(),
                        targetId = doc.getString("targetId").orEmpty(),
                        targetName = doc.getString("targetName").orEmpty(),
                        details = doc.getString("details"),
                        unitId = doc.getString("unitId"),
                        meta = doc.get("meta") as? Map<String, Any?>
                    )
                } catch (e: Exception) {
                    null
                }
            }.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get logs: ${e.message}")
            emptyList()
        }
    }
}
