package com.example.data.log

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * مصدر بيانات سجل العمليات والتدقيق (Activity Log Data Source)
 * يسجل كافة العمليات المنفذة على الأشبال والأنشطة في مجموعة "activity_log"
 * بأسلوب Fire-and-forget دون التأثير على العمليات الأساسية في حال حدوث أي خطأ في الشبكة.
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
        action: String, // "add", "update", "archive", "delete", "mark_done", "cancel"
        targetType: String = "scout", // "scout", "activity"
        targetId: String,
        targetName: String,
        meta: Map<String, Any?>? = null
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
}
