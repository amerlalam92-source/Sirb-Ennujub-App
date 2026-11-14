package com.example.domain.log

/**
 * نموذج عملية مدونة في سجل العمليات والتدقيق (Activity / Audit Log)
 */
data class ActivityLog(
    val id: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val userId: String = "",
    val userName: String = "",
    val action: String = "", // "add", "update", "delete", "archive", "cancel", "mark_done", etc.
    val targetType: String = "", // "scout", "activity", "attendance", "finance", "evaluation", "progress", etc.
    val targetId: String = "",
    val targetName: String = "",
    val details: String? = null,
    val unitId: String? = null,
    val meta: Map<String, Any?>? = null
)
