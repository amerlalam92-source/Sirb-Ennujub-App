package com.example.domain.hobby

/**
 * شارة الهواية الممنوحة للشبل (مجموعة "scout_hobby_badges")
 * معرف الوثيقة: "{scoutId}_{badgeId}"
 */
data class ScoutHobbyBadge(
    val scoutId: String = "",
    val badgeId: String = "",
    val grantedAt: Long = 0L,
    val grantedBy: String = "",
    val grantedByName: String = "",
    val notes: String? = null,
    val revokedAt: Long? = null,
    val revokedBy: String? = null,
    val revokedByName: String? = null
) {
    fun isActive(): Boolean = revokedAt == null
}
