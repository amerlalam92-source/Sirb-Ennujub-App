package com.example.presentation.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.progress.BadgeLevel
import com.example.domain.progress.ProgressRequirement
import com.example.domain.progress.usecases.AddRequirementUseCase
import com.example.domain.progress.usecases.ArchiveRequirementUseCase
import com.example.domain.progress.usecases.DeleteRequirementUseCase
import com.example.domain.progress.usecases.MoveRequirementUseCase
import com.example.domain.progress.usecases.ObserveAllRequirementsUseCase
import com.example.domain.progress.usecases.UnarchiveRequirementUseCase
import com.example.domain.progress.usecases.UpdateRequirementUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel لإدارة كتالوج متطلبات بطاقة التقدم (القبول، المبتدئ، الثاني، الأول)
 */
class ProgressCatalogViewModel(
    observeAllRequirementsUseCase: ObserveAllRequirementsUseCase,
    private val addRequirementUseCase: AddRequirementUseCase,
    private val updateRequirementUseCase: UpdateRequirementUseCase,
    private val archiveRequirementUseCase: ArchiveRequirementUseCase,
    private val unarchiveRequirementUseCase: UnarchiveRequirementUseCase,
    private val moveRequirementUseCase: MoveRequirementUseCase,
    private val deleteRequirementUseCase: DeleteRequirementUseCase,
    observeAllHobbyBadgesUseCase: com.example.domain.hobby.usecases.ObserveAllHobbyBadgesUseCase? = null
) : ViewModel() {

    private val _selectedLevel = MutableStateFlow(BadgeLevel.ACCEPTANCE)
    val selectedLevel: StateFlow<BadgeLevel> = _selectedLevel.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val hobbyBadgesFlow = observeAllHobbyBadgesUseCase?.invoke() ?: kotlinx.coroutines.flow.flowOf(emptyList())

    val uiState: StateFlow<ProgressCatalogUiState> = combine(
        observeAllRequirementsUseCase(),
        _selectedLevel,
        _userMessage,
        hobbyBadgesFlow
    ) { allReqs, level, msg, hobbyBadges ->
        val levelReqs = allReqs.filter { it.badgeLevel == level.key }.sortedBy { it.order }
        val activeCount = levelReqs.count { it.isActive() }
        ProgressCatalogUiState.Success(
            selectedLevel = level,
            allRequirements = allReqs,
            filteredRequirements = levelReqs,
            hobbyBadges = hobbyBadges,
            activeCount = activeCount,
            totalCount = levelReqs.size,
            userMessage = msg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ProgressCatalogUiState.Loading
    )

    fun selectLevel(level: BadgeLevel) {
        _selectedLevel.value = level
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun addRequirement(title: String, description: String, linkedHobbyBadgesCount: Int? = null, onComplete: () -> Unit) {
        val level = _selectedLevel.value
        viewModelScope.launch {
            val req = ProgressRequirement(
                badgeLevel = level.key,
                title = title.trim(),
                description = description.trim(),
                linkedHobbyBadgesCount = linkedHobbyBadgesCount
            )
            val result = addRequirementUseCase(req)
            if (result.isSuccess) {
                onComplete()
            } else {
                _userMessage.value = result.exceptionOrNull()?.localizedMessage ?: "حدث خطأ أثناء الإضافة"
            }
        }
    }

    fun updateRequirement(req: ProgressRequirement, onComplete: () -> Unit) {
        viewModelScope.launch {
            val result = updateRequirementUseCase(req)
            if (result.isSuccess) {
                onComplete()
            } else {
                _userMessage.value = result.exceptionOrNull()?.localizedMessage ?: "حدث خطأ أثناء التعديل"
            }
        }
    }

    fun toggleArchive(req: ProgressRequirement) {
        viewModelScope.launch {
            if (req.archived) {
                unarchiveRequirementUseCase(req.id)
            } else {
                archiveRequirementUseCase(req.id)
            }
        }
    }

    fun moveUp(id: String) {
        viewModelScope.launch {
            moveRequirementUseCase.moveUp(id)
        }
    }

    fun moveDown(id: String) {
        viewModelScope.launch {
            moveRequirementUseCase.moveDown(id)
        }
    }

    fun deleteRequirement(id: String, onCannotDelete: () -> Unit = {}) {
        viewModelScope.launch {
            val result = deleteRequirementUseCase(id)
            if (result.isFailure) {
                val err = result.exceptionOrNull()
                if (err is IllegalStateException && err.message == "USED_BY_SCOUTS") {
                    onCannotDelete()
                } else {
                    _userMessage.value = err?.localizedMessage ?: "تعذر حذف المتطلب"
                }
            }
        }
    }

    class Factory(
        private val observeAllRequirementsUseCase: ObserveAllRequirementsUseCase,
        private val addRequirementUseCase: AddRequirementUseCase,
        private val updateRequirementUseCase: UpdateRequirementUseCase,
        private val archiveRequirementUseCase: ArchiveRequirementUseCase,
        private val unarchiveRequirementUseCase: UnarchiveRequirementUseCase,
        private val moveRequirementUseCase: MoveRequirementUseCase,
        private val deleteRequirementUseCase: DeleteRequirementUseCase,
        private val observeAllHobbyBadgesUseCase: com.example.domain.hobby.usecases.ObserveAllHobbyBadgesUseCase? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ProgressCatalogViewModel(
                observeAllRequirementsUseCase,
                addRequirementUseCase,
                updateRequirementUseCase,
                archiveRequirementUseCase,
                unarchiveRequirementUseCase,
                moveRequirementUseCase,
                deleteRequirementUseCase,
                observeAllHobbyBadgesUseCase
            ) as T
        }
    }
}
