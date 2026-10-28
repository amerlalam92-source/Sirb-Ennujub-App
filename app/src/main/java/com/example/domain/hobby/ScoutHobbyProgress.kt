package com.example.domain.hobby

/**
 * تقدم الشبل في متطلب شارة هواية (مجموعة "scout_hobby_progress")
 * معرف الوثيقة: "{scoutId}_{requirementId}"
 */
data class ScoutHobbyProgress(
    val scoutId: String = "",
    val requirementId: String = "",
    val badgeId: String = "",
    val completed: Boolean = false,
    val completedAt: Long? = null,
    val completedBy: String? = null,
    val completedByName: String? = null,
    val autoCompletedByHobbyBadge: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
