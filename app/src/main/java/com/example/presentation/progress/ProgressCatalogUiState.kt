package com.example.presentation.progress

import com.example.domain.progress.BadgeLevel
import com.example.domain.progress.ProgressRequirement

sealed interface ProgressCatalogUiState {
    data object Loading : ProgressCatalogUiState

    data class Success(
        val selectedLevel: BadgeLevel = BadgeLevel.ACCEPTANCE,
        val allRequirements: List<ProgressRequirement> = emptyList(),
        val filteredRequirements: List<ProgressRequirement> = emptyList(),
        val hobbyBadges: List<com.example.domain.hobby.HobbyBadge> = emptyList(),
        val activeCount: Int = 0,
        val totalCount: Int = 0,
        val userMessage: String? = null
    ) : ProgressCatalogUiState

    data class Error(val message: String) : ProgressCatalogUiState
}
