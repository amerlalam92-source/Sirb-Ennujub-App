package com.example.presentation.hobby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.hobby.HobbyBadgeProgress
import com.example.domain.hobby.usecases.ObserveHobbyBadgeProgressForScoutUseCase
import com.example.domain.scout.ScoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ScoutHobbyBadgesUiState {
    data object Loading : ScoutHobbyBadgesUiState
    data class Success(
        val scoutName: String,
        val badges: List<HobbyBadgeProgress>
    ) : ScoutHobbyBadgesUiState
    data class Error(val message: String) : ScoutHobbyBadgesUiState
}

class ScoutHobbyBadgesViewModel(
    private val scoutId: String,
    private val scoutRepository: ScoutRepository,
    observeHobbyBadgeProgressForScoutUseCase: ObserveHobbyBadgeProgressForScoutUseCase
) : ViewModel() {

    private val _scoutName = MutableStateFlow("")

    init {
        viewModelScope.launch {
            val scout = scoutRepository.getScoutById(scoutId)
            _scoutName.value = scout?.fullName ?: "الشبل"
        }
    }

    val uiState: StateFlow<ScoutHobbyBadgesUiState> = combine(
        _scoutName,
        observeHobbyBadgeProgressForScoutUseCase(scoutId)
    ) { name, list ->
        ScoutHobbyBadgesUiState.Success(
            scoutName = name,
            badges = list.filter { it.badge.isActive() }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ScoutHobbyBadgesUiState.Loading
    )

    class Factory(
        private val scoutId: String,
        private val scoutRepository: ScoutRepository,
        private val observeHobbyBadgeProgressForScoutUseCase: ObserveHobbyBadgeProgressForScoutUseCase
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ScoutHobbyBadgesViewModel(
                scoutId,
                scoutRepository,
                observeHobbyBadgeProgressForScoutUseCase
            ) as T
        }
    }
}
