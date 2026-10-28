package com.example.presentation.scout

import com.example.domain.scout.Scout

/**
 * حالة واجهة المستخدم لشاشة معلومات الشبل (ScoutInfoScreen)
 */
sealed interface ScoutInfoUiState {
    data object Loading : ScoutInfoUiState
    data class Success(
        val scout: Scout,
        val createdByName: String? = null,
        val updatedByName: String? = null
    ) : ScoutInfoUiState
    data class Error(val message: String) : ScoutInfoUiState
}
