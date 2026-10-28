package com.example.presentation.progress

import com.example.domain.progress.BadgeProgress
import com.example.domain.scout.Scout

/**
 * حالة واجهة المستخدم لشاشة تفاصيل بطاقة التقدم لشبل معين (ScoutProgressDetailScreen)
 */
sealed interface ScoutProgressDetailUiState {
    data object Loading : ScoutProgressDetailUiState

    data class Error(val message: String) : ScoutProgressDetailUiState

    data class Success(
        val scout: Scout,
        val badgeProgressList: List<BadgeProgress>,
        val selectedBadgeIndex: Int = 0
    ) : ScoutProgressDetailUiState
}
