package com.example.presentation.hobby

import com.example.domain.hobby.HobbyBadge

data class HobbyBadgeWithCount(
    val badge: HobbyBadge,
    val activeRequirementsCount: Int
)

sealed interface HobbyBadgesCatalogUiState {
    data object Loading : HobbyBadgesCatalogUiState
    data class Success(
        val badgesWithReqCount: List<HobbyBadgeWithCount>,
        val userMessage: String? = null
    ) : HobbyBadgesCatalogUiState
    data class Error(val message: String) : HobbyBadgesCatalogUiState
}
