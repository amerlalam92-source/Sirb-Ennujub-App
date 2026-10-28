package com.example.presentation.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.auth.AuthRepository
import com.example.domain.progress.BadgeLevel
import com.example.domain.progress.usecases.GrantBadgeUseCase
import com.example.domain.progress.usecases.ObserveBadgeProgressForScoutUseCase
import com.example.domain.progress.usecases.RevokeBadgeUseCase
import com.example.domain.progress.usecases.SetRequirementCompletedUseCase
import com.example.domain.scout.ScoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * نموذج العرض لشاشة تفاصيل بطاقة التقدم لشبل معين
 */
class ScoutProgressDetailViewModel(
    private val scoutId: String,
    private val scoutRepository: ScoutRepository,
    private val observeBadgeProgressForScoutUseCase: ObserveBadgeProgressForScoutUseCase,
    private val setRequirementCompletedUseCase: SetRequirementCompletedUseCase,
    private val grantBadgeUseCase: GrantBadgeUseCase,
    private val revokeBadgeUseCase: RevokeBadgeUseCase,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _selectedBadgeIndex = MutableStateFlow(0)
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val uiState: StateFlow<ScoutProgressDetailUiState> = combine(
        scoutRepository.observeScouts().map { list -> list.firstOrNull { it.id == scoutId } },
        observeBadgeProgressForScoutUseCase(scoutId),
        _selectedBadgeIndex
    ) { scout, badges, selectedIndex ->
        if (scout == null) {
            ScoutProgressDetailUiState.Error("لم يتم العثور على بيانات الشبل")
        } else {
            ScoutProgressDetailUiState.Success(
                scout = scout,
                badgeProgressList = badges,
                selectedBadgeIndex = selectedIndex.coerceIn(0, (badges.size - 1).coerceAtLeast(0))
            )
        }
    }.catch { error ->
        emit(ScoutProgressDetailUiState.Error(error.localizedMessage ?: "حدث خطأ غير متوقع"))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ScoutProgressDetailUiState.Loading
    )

    fun selectBadge(index: Int) {
        _selectedBadgeIndex.value = index
    }

    fun clearMessage() {
        _message.value = null
    }

    fun toggleRequirement(
        requirementId: String,
        currentCompleted: Boolean,
        onSuccess: ((Boolean) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val user = authRepository.currentUser
            val leaderName = user?.displayName ?: "قائد"
            val newCompleted = !currentCompleted

            setRequirementCompletedUseCase(
                scoutId = scoutId,
                requirementId = requirementId,
                completed = newCompleted,
                completedByName = if (newCompleted) leaderName else null
            ).onSuccess {
                onSuccess?.invoke(newCompleted)
            }.onFailure { error ->
                _message.value = "تعذر تحديث المتطلب: ${error.localizedMessage ?: "خطأ في الشبكة"}"
            }
        }
    }

    fun grantBadge(
        level: BadgeLevel,
        notes: String = "",
        onSuccess: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            val user = authRepository.currentUser
            val leaderName = user?.displayName ?: "قائد"

            grantBadgeUseCase(
                scoutId = scoutId,
                level = level,
                notes = notes,
                grantedByName = leaderName
            ).onSuccess {
                onSuccess?.invoke()
            }.onFailure { error ->
                _message.value = "تعذر منح الشارة: ${error.localizedMessage ?: "خطأ في الشبكة"}"
            }
        }
    }

    fun revokeBadge(
        level: BadgeLevel,
        onSuccess: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            val user = authRepository.currentUser
            val leaderName = user?.displayName ?: "قائد"

            revokeBadgeUseCase(
                scoutId = scoutId,
                level = level,
                revokedByName = leaderName
            ).onSuccess {
                onSuccess?.invoke()
            }.onFailure { error ->
                _message.value = "تعذر سحب الشارة: ${error.localizedMessage ?: "خطأ في الشبكة"}"
            }
        }
    }

    class Factory(
        private val scoutId: String,
        private val scoutRepository: ScoutRepository,
        private val observeBadgeProgressForScoutUseCase: ObserveBadgeProgressForScoutUseCase,
        private val setRequirementCompletedUseCase: SetRequirementCompletedUseCase,
        private val grantBadgeUseCase: GrantBadgeUseCase,
        private val revokeBadgeUseCase: RevokeBadgeUseCase,
        private val authRepository: AuthRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ScoutProgressDetailViewModel::class.java)) {
                return ScoutProgressDetailViewModel(
                    scoutId = scoutId,
                    scoutRepository = scoutRepository,
                    observeBadgeProgressForScoutUseCase = observeBadgeProgressForScoutUseCase,
                    setRequirementCompletedUseCase = setRequirementCompletedUseCase,
                    grantBadgeUseCase = grantBadgeUseCase,
                    revokeBadgeUseCase = revokeBadgeUseCase,
                    authRepository = authRepository
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
