package com.example.presentation.progress

import com.example.domain.progress.BadgeProgress
import com.example.domain.scout.Scout

/**
 * حالة واجهة المستخدم لشاشة التقدم في شارة واحدة لشبل معين (ScoutSingleBadgeProgressScreen)
 */
sealed interface ScoutSingleBadgeProgressUiState {
    data object Loading : ScoutSingleBadgeProgressUiState
    data class Error(val message: String) : ScoutSingleBadgeProgressUiState
    data class Success(
        val scout: Scout,
        val badgeProgress: BadgeProgress,
        val hobbyBadges: List<com.example.domain.hobby.HobbyBadge> = emptyList()
    ) : ScoutSingleBadgeProgressUiState
}
