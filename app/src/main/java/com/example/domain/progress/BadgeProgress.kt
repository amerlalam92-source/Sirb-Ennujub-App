package com.example.domain.progress

/**
 * حالة تقدم الشبل في شارة معينة مع المتطلبات ونسبة الإنجاز
 */
data class BadgeProgress(
    val level: BadgeLevel,
    val totalActiveRequirements: Int,
    val completedActiveRequirements: Int,
    val percentage: Int, // 0..100
    val isGranted: Boolean,
    val grantedAt: Long?,
    val grantedByName: String?,
    val requirements: List<RequirementWithState>
)

data class RequirementWithState(
    val requirement: ProgressRequirement,
    val completed: Boolean,
    val completedAt: Long?,
    val completedByName: String?
)
