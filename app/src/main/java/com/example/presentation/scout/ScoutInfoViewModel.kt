package com.example.presentation.scout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.leader.LeaderNameResolver
import com.example.domain.scout.ScoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * نموذج العرض لشاشة معلومات الشبل (Scout Info ViewModel)
 */
class ScoutInfoViewModel(
    private val scoutId: String,
    private val repository: ScoutRepository,
    private val leaderNameResolver: LeaderNameResolver? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScoutInfoUiState>(ScoutInfoUiState.Loading)
    val uiState: StateFlow<ScoutInfoUiState> = _uiState.asStateFlow()

    init {
        loadScout()
    }

    fun loadScout() {
        _uiState.value = ScoutInfoUiState.Loading
        viewModelScope.launch {
            val scout = repository.getScoutById(scoutId)
            if (scout != null) {
                val createdByName = if (scout.createdBy.isNotBlank()) {
                    leaderNameResolver?.getLeaderDisplayName(scout.createdBy)
                } else null

                val updatedByName = if (scout.updatedBy.isNotBlank()) {
                    leaderNameResolver?.getLeaderDisplayName(scout.updatedBy)
                } else null

                _uiState.value = ScoutInfoUiState.Success(
                    scout = scout,
                    createdByName = createdByName,
                    updatedByName = updatedByName
                )
            } else {
                _uiState.value = ScoutInfoUiState.Error("تعذر العثور على بيانات الشبل")
            }
        }
    }

    class Factory(
        private val scoutId: String,
        private val repository: ScoutRepository,
        private val leaderNameResolver: LeaderNameResolver? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ScoutInfoViewModel(scoutId, repository, leaderNameResolver) as T
        }
    }
}
