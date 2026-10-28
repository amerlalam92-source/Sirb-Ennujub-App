package com.example.domain.hobby

/**
 * شارة الهواية (مجموعة "hobby_badges")
 */
data class HobbyBadge(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val iconKey: String = HobbyBadgeIcon.OTHER.key,
    val order: Int = 0,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val createdBy: String = "",
    val updatedBy: String = ""
) {
    fun isActive(): Boolean = !archived
}
