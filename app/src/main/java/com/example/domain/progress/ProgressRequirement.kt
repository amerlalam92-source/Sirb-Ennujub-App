package com.example.domain.progress

/**
 * متطلب من متطلبات بطاقة التقدم لشارات الكفاءة (مجموعة progress_requirements)
 */
data class ProgressRequirement(
    val id: String = "",
    val badgeLevel: String = BadgeLevel.ACCEPTANCE.key,
    val title: String = "",
    val description: String = "",
    val order: Int = 0,
    val linkedHobbyBadgesCount: Int? = null,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val createdBy: String = "",
    val updatedBy: String = ""
) {
    fun isActive(): Boolean = !archived

    val level: BadgeLevel
        get() = BadgeLevel.fromKey(badgeLevel) ?: BadgeLevel.ACCEPTANCE
}
