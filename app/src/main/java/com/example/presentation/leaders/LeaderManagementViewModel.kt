package com.example.presentation.leaders

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.auth.AuthRepository
import com.example.domain.auth.Leader
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LeaderManagementUiState(
    val pendingLeaders: List<Leader> = emptyList(),
    val approvedLeaders: List<Leader> = emptyList(),
    val selectedTab: Int = 0, // 0 = Pending, 1 = Approved
    val isLoading: Boolean = true,
    val leaderPendingDelete: Leader? = null,
    val isActionInProgress: Boolean = false
) {
    val mainLeadersCount: Int
        get() = approvedLeaders.count { it.isMainLeader }
}

sealed interface LeaderManagementEvent {
    data class ShowMessage(val message: String) : LeaderManagementEvent
}

class LeaderManagementViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LeaderManagementUiState())
    val uiState: StateFlow<LeaderManagementUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<LeaderManagementEvent>()
    val events: SharedFlow<LeaderManagementEvent> = _events.asSharedFlow()

    var leaderToToggle by mutableStateOf<Leader?>(null)
        private set

    var isActionMakingMainLeader by mutableStateOf(true)
        private set

    init {
        observeLeaders()
    }

    private fun observeLeaders() {
        viewModelScope.launch {
            authRepository.observeAllLeaders().collect { leaders ->
                val pending = leaders.filter { it.status == "pending" }
                val approved = leaders.filter { it.status == "approved" }
                _uiState.update {
                    it.copy(
                        pendingLeaders = pending,
                        approvedLeaders = approved,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun selectTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
    }

    fun approveLeader(leader: Leader) {
        viewModelScope.launch {
            _uiState.update { it.copy(isActionInProgress = true) }
            val result = authRepository.updateLeaderStatus(leader.uid, "approved")
            _uiState.update { it.copy(isActionInProgress = false) }
            if (result.isSuccess) {
                _events.emit(LeaderManagementEvent.ShowMessage("تمت الموافقة على القائد ${leader.fullName}"))
            } else {
                _events.emit(LeaderManagementEvent.ShowMessage("فشلت عملية الموافقة"))
            }
        }
    }

    fun rejectLeader(leader: Leader) {
        viewModelScope.launch {
            _uiState.update { it.copy(isActionInProgress = true) }
            val result = authRepository.deleteLeader(leader.uid)
            _uiState.update { it.copy(isActionInProgress = false) }
            if (result.isSuccess) {
                _events.emit(LeaderManagementEvent.ShowMessage("تم رفض طلب القائد ${leader.fullName}"))
            } else {
                _events.emit(LeaderManagementEvent.ShowMessage("فشلت عملية الرفض"))
            }
        }
    }

    fun requestDeleteLeader(leader: Leader) {
        _uiState.update { it.copy(leaderPendingDelete = leader) }
    }

    fun dismissDeleteDialog() {
        _uiState.update { it.copy(leaderPendingDelete = null) }
    }

    fun confirmDeleteLeader() {
        val leader = _uiState.value.leaderPendingDelete ?: return
        _uiState.update { it.copy(leaderPendingDelete = null, isActionInProgress = true) }
        viewModelScope.launch {
            val result = authRepository.deleteLeader(leader.uid)
            _uiState.update { it.copy(isActionInProgress = false) }
            if (result.isSuccess) {
                _events.emit(LeaderManagementEvent.ShowMessage("تم حذف القائد ${leader.fullName}"))
            } else {
                _events.emit(LeaderManagementEvent.ShowMessage("فشل حذف القائد"))
            }
        }
    }

    fun getMainLeadersCount(): Int {
        return _uiState.value.approvedLeaders.count { it.isMainLeader }
    }

    fun onToggleMainLeaderClicked(leader: Leader) {
        val isRevoking = leader.isMainLeader
        val mainLeadersCount = getMainLeadersCount()
        if (isRevoking && mainLeadersCount <= 1) {
            viewModelScope.launch {
                _events.emit(LeaderManagementEvent.ShowMessage("لا يمكن إلغاء الرئاسة لأنه يجب أن يكون هناك قائد رئيسي واحد على الأقل."))
            }
            return
        }

        leaderToToggle = leader
        isActionMakingMainLeader = !leader.isMainLeader
    }

    fun toggleMainLeader(leader: Leader) {
        onToggleMainLeaderClicked(leader)
    }

    fun confirmToggleMainLeader() {
        val leader = leaderToToggle ?: return
        val newStatus = isActionMakingMainLeader
        viewModelScope.launch {
            _uiState.update { it.copy(isActionInProgress = true) }
            val result = authRepository.toggleMainLeader(leader.uid, newStatus)
            _uiState.update { it.copy(isActionInProgress = false) }
            if (result.isSuccess) {
                _events.emit(LeaderManagementEvent.ShowMessage("تم التحديث بنجاح"))
                leaderToToggle = null
            } else {
                _events.emit(LeaderManagementEvent.ShowMessage("فشل تحديث صفة القائد"))
            }
        }
    }

    fun dismissToggleMainLeaderDialog() {
        leaderToToggle = null
    }

    class Factory(
        private val authRepository: AuthRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LeaderManagementViewModel(authRepository) as T
        }
    }
}
