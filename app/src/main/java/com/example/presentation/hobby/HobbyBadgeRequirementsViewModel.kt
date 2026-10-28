package com.example.presentation.hobby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.hobby.HobbyBadge
import com.example.domain.hobby.HobbyBadgeRequirement
import com.example.domain.hobby.usecases.AddHobbyRequirementUseCase
import com.example.domain.hobby.usecases.ArchiveHobbyRequirementUseCase
import com.example.domain.hobby.usecases.DeleteHobbyRequirementUseCase
import com.example.domain.hobby.usecases.MoveHobbyRequirementUseCase
import com.example.domain.hobby.usecases.ObserveHobbyBadgeByIdUseCase
import com.example.domain.hobby.usecases.ObserveRequirementsForBadgeUseCase
import com.example.domain.hobby.usecases.UnarchiveHobbyRequirementUseCase
import com.example.domain.hobby.usecases.UpdateHobbyRequirementUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface HobbyBadgeRequirementsUiState {
    data object Loading : HobbyBadgeRequirementsUiState
    data class Success(
        val badge: HobbyBadge?,
        val requirements: List<HobbyBadgeRequirement>,
        val userMessage: String? = null
    ) : HobbyBadgeRequirementsUiState
    data class Error(val message: String) : HobbyBadgeRequirementsUiState
}

class HobbyBadgeRequirementsViewModel(
    private val badgeId: String,
    observeHobbyBadgeByIdUseCase: ObserveHobbyBadgeByIdUseCase,
    observeRequirementsForBadgeUseCase: ObserveRequirementsForBadgeUseCase,
    private val addHobbyRequirementUseCase: AddHobbyRequirementUseCase,
    private val updateHobbyRequirementUseCase: UpdateHobbyRequirementUseCase,
    private val archiveHobbyRequirementUseCase: ArchiveHobbyRequirementUseCase,
    private val unarchiveHobbyRequirementUseCase: UnarchiveHobbyRequirementUseCase,
    private val moveHobbyRequirementUseCase: MoveHobbyRequirementUseCase,
    private val deleteHobbyRequirementUseCase: DeleteHobbyRequirementUseCase
) : ViewModel() {

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    val uiState: StateFlow<HobbyBadgeRequirementsUiState> = combine(
        observeHobbyBadgeByIdUseCase(badgeId),
        observeRequirementsForBadgeUseCase(badgeId),
        _userMessage
    ) { badge, reqs, msg ->
        HobbyBadgeRequirementsUiState.Success(
            badge = badge,
            requirements = reqs.sortedBy { it.order },
            userMessage = msg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HobbyBadgeRequirementsUiState.Loading
    )

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun addRequirement(title: String, description: String, onDone: () -> Unit) {
        viewModelScope.launch {
            val result = addHobbyRequirementUseCase(
                HobbyBadgeRequirement(
                    badgeId = badgeId,
                    title = title,
                    description = description
                )
            )
            if (result.isSuccess) {
                _userMessage.value = "تمت إضافة المتطلب بنجاح"
                onDone()
            } else {
                _userMessage.value = "تعذر إضافة المتطلب: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun updateRequirement(req: HobbyBadgeRequirement, onDone: () -> Unit) {
        viewModelScope.launch {
            val result = updateHobbyRequirementUseCase(req)
            if (result.isSuccess) {
                _userMessage.value = "تم تعديل المتطلب"
                onDone()
            } else {
                _userMessage.value = "تعذر تعديل المتطلب: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun archiveRequirement(id: String) {
        viewModelScope.launch {
            val result = archiveHobbyRequirementUseCase(id)
            if (result.isSuccess) {
                _userMessage.value = "تمت أرشفة المتطلب"
            } else {
                _userMessage.value = "تعذر أرشفة المتطلب"
            }
        }
    }

    fun unarchiveRequirement(id: String) {
        viewModelScope.launch {
            val result = unarchiveHobbyRequirementUseCase(id)
            if (result.isSuccess) {
                _userMessage.value = "تم إلغاء أرشفة المتطلب"
            } else {
                _userMessage.value = "تعذر إلغاء الأرشفة"
            }
        }
    }

    fun moveUp(id: String) {
        viewModelScope.launch {
            moveHobbyRequirementUseCase.moveUp(id)
        }
    }

    fun moveDown(id: String) {
        viewModelScope.launch {
            moveHobbyRequirementUseCase.moveDown(id)
        }
    }

    fun deleteRequirement(id: String, onCannotDelete: () -> Unit = {}) {
        viewModelScope.launch {
            val result = deleteHobbyRequirementUseCase(id)
            if (result.isSuccess) {
                _userMessage.value = "تم حذف المتطلب نهائياً"
            } else {
                val ex = result.exceptionOrNull()
                if (ex is IllegalStateException && ex.message == "USED_BY_SCOUTS") {
                    onCannotDelete()
                } else {
                    _userMessage.value = "تعذر حذف المتطلب: ${ex?.message}"
                }
            }
        }
    }

    class Factory(
        private val badgeId: String,
        private val observeHobbyBadgeByIdUseCase: ObserveHobbyBadgeByIdUseCase,
        private val observeRequirementsForBadgeUseCase: ObserveRequirementsForBadgeUseCase,
        private val addHobbyRequirementUseCase: AddHobbyRequirementUseCase,
        private val updateHobbyRequirementUseCase: UpdateHobbyRequirementUseCase,
        private val archiveHobbyRequirementUseCase: ArchiveHobbyRequirementUseCase,
        private val unarchiveHobbyRequirementUseCase: UnarchiveHobbyRequirementUseCase,
        private val moveHobbyRequirementUseCase: MoveHobbyRequirementUseCase,
        private val deleteHobbyRequirementUseCase: DeleteHobbyRequirementUseCase
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HobbyBadgeRequirementsViewModel(
                badgeId,
                observeHobbyBadgeByIdUseCase,
                observeRequirementsForBadgeUseCase,
                addHobbyRequirementUseCase,
                updateHobbyRequirementUseCase,
                archiveHobbyRequirementUseCase,
                unarchiveHobbyRequirementUseCase,
                moveHobbyRequirementUseCase,
                deleteHobbyRequirementUseCase
            ) as T
        }
    }
}
