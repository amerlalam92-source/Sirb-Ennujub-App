package com.example.domain.progress

/**
 * سجل إنجاز متطلب لشبل معين (مجموعة scout_progress)
 * معرف الوثيقة: "{scoutId}_{requirementId}"
 */
data class ScoutProgressEntry(
    val scoutId: String = "",
    val requirementId: String = "",
    val completed: Boolean = false,
    val completedAt: Long? = null,
    val completedBy: String? = null,
    val completedByName: String? = null,
    val autoCompletedByHobbyBadgesCount: Boolean = false,
    val autoSourceHobbyBadgesCount: Int? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
