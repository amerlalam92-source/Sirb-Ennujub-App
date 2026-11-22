package com.example.data.log

import android.util.Log
import com.example.data.common.DataLoadErrorReporter
import com.example.data.common.ListenerFallbackState
import com.example.domain.log.ActivityLog
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * مصدر بيانات سجل العمليات والتدقيق (Activity Log Data Source)
 * يسجل ويسترجع أحدث العمليات المنفذة (حد أقصى 300 سجل) في مجموعة "activity_log"
 * مرتبة تنازلياً حسب الطابع الزمني.
 */
class ActivityLogDataSource(
    private val firestore: FirebaseFirestore,
    private val errorReporter: DataLoadErrorReporter? = null
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
        val sourceKey = if (!unitId.isNullOrBlank()) "activity_log:$unitId" else "activity_log"
        val fallbackState = ListenerFallbackState<ActivityLog>(sourceKey, errorReporter)
        val baseQuery = if (!unitId.isNullOrBlank()) {
            firestore.collection(COLLECTION_NAME).whereEqualTo("unitId", unitId)
        } else {
            firestore.collection(COLLECTION_NAME)
        }
        val query = baseQuery
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(300)
        val listenerRegistration = query
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Error observing activity logs: ${error.message}")
                    trySend(fallbackState.onError(error))
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
                    }
                    trySend(fallbackState.onSnapshot(logs))
                }
            }
        awaitClose {
            listenerRegistration.remove()
            fallbackState.clear()
        }
    }

    /**
     * جلب أحدث العمليات المسجلة لمرة واحدة ومفلترة بالوحدة
     */
    suspend fun getLogs(unitId: String? = null): List<ActivityLog> {
        return try {
            val baseQuery = if (!unitId.isNullOrBlank()) {
                firestore.collection(COLLECTION_NAME).whereEqualTo("unitId", unitId)
            } else {
                firestore.collection(COLLECTION_NAME)
            }
            val query = baseQuery
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(300)
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
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get logs: ${e.message}")
            emptyList()
        }
    }
}
