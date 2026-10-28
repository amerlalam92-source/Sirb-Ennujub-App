package com.example.domain.progress

/**
 * شارة ممنوحة لشبل معين (مجموعة scout_badges)
 * معرف الوثيقة: "{scoutId}_{badgeLevel}"
 */
data class ScoutBadge(
    val scoutId: String = "",
    val badgeLevel: String = "",
    val grantedAt: Long = 0L,
    val grantedBy: String = "",
    val grantedByName: String = "",
    val notes: String = "",
    val revokedAt: Long? = null,
    val revokedBy: String? = null,
    val revokedByName: String? = null
) {
    fun isActive(): Boolean = revokedAt == null

    val level: BadgeLevel?
        get() = BadgeLevel.fromKey(badgeLevel)
}
