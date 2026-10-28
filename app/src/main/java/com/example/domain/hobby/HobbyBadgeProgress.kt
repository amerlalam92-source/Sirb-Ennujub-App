package com.example.domain.hobby

/**
 * نموذج حسابي لحالة تقدم الشبل في شارة هواية معينة
 */
data class HobbyBadgeProgress(
    val badge: HobbyBadge,
    val totalActiveRequirements: Int,
    val completedActiveRequirements: Int,
    val percentage: Int,
    val isGranted: Boolean,
    val grantedAt: Long?,
    val grantedByName: String?,
    val requirements: List<HobbyRequirementWithState>
)

data class HobbyRequirementWithState(
    val requirement: HobbyBadgeRequirement,
    val completed: Boolean,
    val completedAt: Long?,
    val completedByName: String?
)
