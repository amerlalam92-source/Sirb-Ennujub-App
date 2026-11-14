package com.example.domain.log

import kotlinx.coroutines.flow.Flow

/**
 * واجهة مستودع سجل العمليات والتدقيق (Activity Log Repository)
 */
interface ActivityLogRepository {

    /**
     * مراقبة كافة العمليات المسجلة بتدفق مباشر
     */
    fun observeLogs(): Flow<List<ActivityLog>>

    /**
     * جلب كافة العمليات المسجلة لمرة واحدة
     */
    suspend fun getLogs(): List<ActivityLog>

    /**
     * تسجيل عملية جديدة في السجل
     */
    suspend fun logAction(
        action: String, // "add", "update", "delete", etc.
        targetType: String, // "scout", "activity", "attendance", "finance", "evaluation", etc.
        targetName: String, // e.g. "شبل: محمد", "نشاط: اجتماع"
        targetId: String = "",
        details: String? = null,
        meta: Map<String, Any?>? = null
    )
}
