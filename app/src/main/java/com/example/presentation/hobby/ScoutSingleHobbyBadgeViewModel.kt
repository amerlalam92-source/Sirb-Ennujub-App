package com.example.presentation.hobby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.hobby.HobbyBadgeProgress
import com.example.domain.hobby.usecases.GrantHobbyBadgeUseCase
import com.example.domain.hobby.usecases.ObserveHobbyBadgeProgressForScoutUseCase
import com.example.domain.hobby.usecases.RevokeHobbyBadgeUseCase
import com.example.domain.hobby.usecases.SetHobbyRequirementCompletedUseCase
import com.example.domain.scout.ScoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ScoutSingleHobbyBadgeUiState {
    data object Loading : ScoutSingleHobbyBadgeUiState
    data class Success(
        val scoutName: String,
        val badgeProgress: HobbyBadgeProgress?,
        val message: String? = null
    ) : ScoutSingleHobbyBadgeUiState
    data class Error(val message: String) : ScoutSingleHobbyBadgeUiState
}

class ScoutSingleHobbyBadgeViewModel(
    private val scoutId: String,
    private val badgeId: String,
    private val scoutRepository: ScoutRepository,
    observeHobbyBadgeProgressForScoutUseCase: ObserveHobbyBadgeProgressForScoutUseCase,
    private val setHobbyRequirementCompletedUseCase: SetHobbyRequirementCompletedUseCase,
    private val grantHobbyBadgeUseCase: GrantHobbyBadgeUseCase,
    private val revokeHobbyBadgeUseCase: RevokeHobbyBadgeUseCase
) : ViewModel() {

    private val _scoutName = MutableStateFlow("")
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            val scout = scoutRepository.getScoutById(scoutId)
            _scoutName.value = scout?.fullName ?: "الشبل"
        }
    }

    val uiState: StateFlow<ScoutSingleHobbyBadgeUiState> = combine(
        _scoutName,
        observeHobbyBadgeProgressForScoutUseCase(scoutId),
        _message
    ) { name, list, msg ->
        val progress = list.firstOrNull { it.badge.id == badgeId }
        ScoutSingleHobbyBadgeUiState.Success(
            scoutName = name,
            badgeProgress = progress,
            message = msg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ScoutSingleHobbyBadgeUiState.Loading
    )

    fun clearMessage() {
        _message.value = null
    }

    fun toggleRequirement(requirementId: String, currentCompleted: Boolean) {
        viewModelScope.launch {
            val result = setHobbyRequirementCompletedUseCase(
                scoutId = scoutId,
                requirementId = requirementId,
                badgeId = badgeId,
                completed = !currentCompleted
            )
            if (result.isFailure) {
                _message.value = "تعذر تحديث المتطلب: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun grantBadge(notes: String? = null) {
        viewModelScope.launch {
            val result = grantHobbyBadgeUseCase(
                scoutId = scoutId,
                badgeId = badgeId,
                notes = notes
            )
            if (result.isSuccess) {
                _message.value = "تم منح الشارة بنجاح"
            } else {
                _message.value = "تعذر منح الشارة: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun revokeBadge() {
        viewModelScope.launch {
            val result = revokeHobbyBadgeUseCase(
                scoutId = scoutId,
                badgeId = badgeId
            )
            if (result.isSuccess) {
                _message.value = "تم سحب الشارة"
            } else {
                _message.value = "تعذر سحب الشارة: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    class Factory(
        private val scoutId: String,
        private val badgeId: String,
        private val scoutRepository: ScoutRepository,
        private val observeHobbyBadgeProgressForScoutUseCase: ObserveHobbyBadgeProgressForScoutUseCase,
        private val setHobbyRequirementCompletedUseCase: SetHobbyRequirementCompletedUseCase,
        private val grantHobbyBadgeUseCase: GrantHobbyBadgeUseCase,
        private val revokeHobbyBadgeUseCase: RevokeHobbyBadgeUseCase
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ScoutSingleHobbyBadgeViewModel(
                scoutId,
                badgeId,
                scoutRepository,
                observeHobbyBadgeProgressForScoutUseCase,
                setHobbyRequirementCompletedUseCase,
                grantHobbyBadgeUseCase,
                revokeHobbyBadgeUseCase
            ) as T
        }
    }
}
