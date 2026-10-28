package com.example.presentation.hobby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.hobby.HobbyBadge
import com.example.domain.hobby.usecases.AddHobbyBadgeUseCase
import com.example.domain.hobby.usecases.ArchiveHobbyBadgeUseCase
import com.example.domain.hobby.usecases.DeleteHobbyBadgeUseCase
import com.example.domain.hobby.usecases.MoveHobbyBadgeUseCase
import com.example.domain.hobby.usecases.ObserveAllHobbyBadgesUseCase
import com.example.domain.hobby.usecases.ObserveAllHobbyRequirementsUseCase
import com.example.domain.hobby.usecases.UnarchiveHobbyBadgeUseCase
import com.example.domain.hobby.usecases.UpdateHobbyBadgeUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel لإدارة كتالوج شارات الهواية
 */
class HobbyBadgesCatalogViewModel(
    observeAllHobbyBadgesUseCase: ObserveAllHobbyBadgesUseCase,
    observeAllHobbyRequirementsUseCase: ObserveAllHobbyRequirementsUseCase,
    private val addHobbyBadgeUseCase: AddHobbyBadgeUseCase,
    private val updateHobbyBadgeUseCase: UpdateHobbyBadgeUseCase,
    private val archiveHobbyBadgeUseCase: ArchiveHobbyBadgeUseCase,
    private val unarchiveHobbyBadgeUseCase: UnarchiveHobbyBadgeUseCase,
    private val moveHobbyBadgeUseCase: MoveHobbyBadgeUseCase,
    private val deleteHobbyBadgeUseCase: DeleteHobbyBadgeUseCase
) : ViewModel() {

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    val uiState: StateFlow<HobbyBadgesCatalogUiState> = combine(
        observeAllHobbyBadgesUseCase(),
        observeAllHobbyRequirementsUseCase(),
        _userMessage
    ) { badges, requirements, msg ->
        val list = badges.map { badge ->
            val count = requirements.count { it.badgeId == badge.id && it.isActive() }
            HobbyBadgeWithCount(badge = badge, activeRequirementsCount = count)
        }
        HobbyBadgesCatalogUiState.Success(
            badgesWithReqCount = list,
            userMessage = msg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HobbyBadgesCatalogUiState.Loading
    )

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun addBadge(name: String, description: String, iconKey: String, onDone: () -> Unit) {
        viewModelScope.launch {
            val result = addHobbyBadgeUseCase(
                HobbyBadge(
                    name = name,
                    description = description,
                    iconKey = iconKey
                )
            )
            if (result.isSuccess) {
                _userMessage.value = "تمت إضافة الشارة بنجاح"
                onDone()
            } else {
                _userMessage.value = "تعذر حفظ الشارة: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun updateBadge(badge: HobbyBadge, onDone: () -> Unit) {
        viewModelScope.launch {
            val result = updateHobbyBadgeUseCase(badge)
            if (result.isSuccess) {
                _userMessage.value = "تم تعديل الشارة بنجاح"
                onDone()
            } else {
                _userMessage.value = "تعذر تعديل الشارة: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun archiveBadge(id: String) {
        viewModelScope.launch {
            val result = archiveHobbyBadgeUseCase(id)
            if (result.isSuccess) {
                _userMessage.value = "تمت أرشفة الشارة"
            } else {
                _userMessage.value = "تعذر أرشفة الشارة"
            }
        }
    }

    fun unarchiveBadge(id: String) {
        viewModelScope.launch {
            val result = unarchiveHobbyBadgeUseCase(id)
            if (result.isSuccess) {
                _userMessage.value = "تم إلغاء أرشفة الشارة"
            } else {
                _userMessage.value = "تعذر إلغاء الأرشفة"
            }
        }
    }

    fun moveUp(id: String) {
        viewModelScope.launch {
            moveHobbyBadgeUseCase.moveUp(id)
        }
    }

    fun moveDown(id: String) {
        viewModelScope.launch {
            moveHobbyBadgeUseCase.moveDown(id)
        }
    }

    fun deleteBadge(id: String, onCannotDelete: () -> Unit = {}) {
        viewModelScope.launch {
            val result = deleteHobbyBadgeUseCase(id)
            if (result.isSuccess) {
                _userMessage.value = "تم حذف الشارة نهائياً"
            } else {
                val ex = result.exceptionOrNull()
                if (ex is IllegalStateException && ex.message == "GRANTED_TO_SCOUTS") {
                    onCannotDelete()
                } else {
                    _userMessage.value = "تعذر حذف الشارة: ${ex?.message}"
                }
            }
        }
    }

    class Factory(
        private val observeAllHobbyBadgesUseCase: ObserveAllHobbyBadgesUseCase,
        private val observeAllHobbyRequirementsUseCase: ObserveAllHobbyRequirementsUseCase,
        private val addHobbyBadgeUseCase: AddHobbyBadgeUseCase,
        private val updateHobbyBadgeUseCase: UpdateHobbyBadgeUseCase,
        private val archiveHobbyBadgeUseCase: ArchiveHobbyBadgeUseCase,
        private val unarchiveHobbyBadgeUseCase: UnarchiveHobbyBadgeUseCase,
        private val moveHobbyBadgeUseCase: MoveHobbyBadgeUseCase,
        private val deleteHobbyBadgeUseCase: DeleteHobbyBadgeUseCase
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HobbyBadgesCatalogViewModel(
                observeAllHobbyBadgesUseCase,
                observeAllHobbyRequirementsUseCase,
                addHobbyBadgeUseCase,
                updateHobbyBadgeUseCase,
                archiveHobbyBadgeUseCase,
                unarchiveHobbyBadgeUseCase,
                moveHobbyBadgeUseCase,
                deleteHobbyBadgeUseCase
            ) as T
        }
    }
}
