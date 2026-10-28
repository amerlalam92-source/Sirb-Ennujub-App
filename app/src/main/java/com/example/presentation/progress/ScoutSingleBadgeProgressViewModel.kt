package com.example.presentation.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.auth.AuthRepository
import com.example.domain.progress.BadgeLevel
import com.example.domain.progress.BadgeProgress
import com.example.domain.progress.usecases.GrantBadgeUseCase
import com.example.domain.progress.usecases.ObserveBadgeProgressForScoutUseCase
import com.example.domain.progress.usecases.RevokeBadgeUseCase
import com.example.domain.progress.usecases.SetRequirementCompletedUseCase
import com.example.domain.scout.ScoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ScoutSingleBadgeProgressViewModel(
    val scoutId: String,
    val badgeKey: String,
    private val scoutRepository: ScoutRepository,
    private val observeBadgeProgressForScoutUseCase: ObserveBadgeProgressForScoutUseCase,
    private val setRequirementCompletedUseCase: SetRequirementCompletedUseCase,
    private val grantBadgeUseCase: GrantBadgeUseCase,
    private val revokeBadgeUseCase: RevokeBadgeUseCase,
    private val authRepository: AuthRepository,
    observeAllHobbyBadgesUseCase: com.example.domain.hobby.usecases.ObserveAllHobbyBadgesUseCase? = null
) : ViewModel() {

    val badgeLevel: BadgeLevel = BadgeLevel.fromKey(badgeKey) ?: BadgeLevel.ACCEPTANCE

    private val _uiState = MutableStateFlow<ScoutSingleBadgeProgressUiState>(ScoutSingleBadgeProgressUiState.Loading)
    val uiState: StateFlow<ScoutSingleBadgeProgressUiState> = _uiState.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val hobbyBadgesFlow = observeAllHobbyBadgesUseCase?.invoke() ?: kotlinx.coroutines.flow.flowOf(emptyList())

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            combine(
                scoutRepository.observeScouts().map { list -> list.firstOrNull { it.id == scoutId } },
                observeBadgeProgressForScoutUseCase(scoutId),
                hobbyBadgesFlow
            ) { scout, badgeList, hobbyBadges ->
                if (scout == null) {
                    ScoutSingleBadgeProgressUiState.Error("الشبل غير موجود")
                } else {
                    val badge = badgeList.firstOrNull { it.level == badgeLevel }
                        ?: BadgeProgress(
                            level = badgeLevel,
                            totalActiveRequirements = 0,
                            completedActiveRequirements = 0,
                            percentage = 0,
                            isGranted = false,
                            grantedAt = null,
                            grantedByName = null,
                            requirements = emptyList()
                        )
                    ScoutSingleBadgeProgressUiState.Success(
                        scout = scout,
                        badgeProgress = badge,
                        hobbyBadges = hobbyBadges
                    )
                }
            }.catch { e ->
                _uiState.value = ScoutSingleBadgeProgressUiState.Error(
                    e.localizedMessage ?: "حدث خطأ أثناء تحميل بيانات الشارة"
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun toggleRequirement(
        requirementId: String,
        currentCompleted: Boolean,
        onSuccess: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val leaderName = authRepository.currentUser?.displayName ?: "القائد"
            val targetCompleted = !currentCompleted
            val result = setRequirementCompletedUseCase(
                scoutId = scoutId,
                requirementId = requirementId,
                completed = targetCompleted,
                completedByName = leaderName
            )
            result.onSuccess {
                onSuccess(targetCompleted)
            }.onFailure { e ->
                _message.value = e.localizedMessage ?: "فشل تحديث حالة المتطلب"
            }
        }
    }

    fun grantBadge(onSuccess: () -> Unit) {
        viewModelScope.launch {
            val leaderName = authRepository.currentUser?.displayName ?: "القائد"
            val result = grantBadgeUseCase(
                scoutId = scoutId,
                level = badgeLevel,
                grantedByName = leaderName
            )
            result.onSuccess {
                onSuccess()
            }.onFailure { e ->
                _message.value = e.localizedMessage ?: "فشل منح الشارة"
            }
        }
    }

    fun revokeBadge(onSuccess: () -> Unit) {
        viewModelScope.launch {
            val leaderName = authRepository.currentUser?.displayName ?: "القائد"
            val result = revokeBadgeUseCase(
                scoutId = scoutId,
                level = badgeLevel,
                revokedByName = leaderName
            )
            result.onSuccess {
                onSuccess()
            }.onFailure { e ->
                _message.value = e.localizedMessage ?: "فشل سحب الشارة"
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    class Factory(
        private val scoutId: String,
        private val badgeKey: String,
        private val scoutRepository: ScoutRepository,
        private val observeBadgeProgressForScoutUseCase: ObserveBadgeProgressForScoutUseCase,
        private val setRequirementCompletedUseCase: SetRequirementCompletedUseCase,
        private val grantBadgeUseCase: GrantBadgeUseCase,
        private val revokeBadgeUseCase: RevokeBadgeUseCase,
        private val authRepository: AuthRepository,
        private val observeAllHobbyBadgesUseCase: com.example.domain.hobby.usecases.ObserveAllHobbyBadgesUseCase? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ScoutSingleBadgeProgressViewModel::class.java)) {
                return ScoutSingleBadgeProgressViewModel(
                    scoutId = scoutId,
                    badgeKey = badgeKey,
                    scoutRepository = scoutRepository,
                    observeBadgeProgressForScoutUseCase = observeBadgeProgressForScoutUseCase,
                    setRequirementCompletedUseCase = setRequirementCompletedUseCase,
                    grantBadgeUseCase = grantBadgeUseCase,
                    revokeBadgeUseCase = revokeBadgeUseCase,
                    authRepository = authRepository,
                    observeAllHobbyBadgesUseCase = observeAllHobbyBadgesUseCase
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
