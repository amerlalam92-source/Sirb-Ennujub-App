package com.example.domain.hobby

/**
 * متطلب شارة الهواية (مجموعة "hobby_badge_requirements")
 */
data class HobbyBadgeRequirement(
    val id: String = "",
    val badgeId: String = "",
    val title: String = "",
    val description: String = "",
    val order: Int = 0,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val createdBy: String = "",
    val updatedBy: String = ""
) {
    fun isActive(): Boolean = !archived
}
