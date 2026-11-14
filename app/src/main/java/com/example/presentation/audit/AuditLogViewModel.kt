package com.example.presentation.audit

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.auth.AuthRepository
import com.example.domain.log.ActivityLog
import com.example.domain.log.ActivityLogRepository
import com.example.presentation.reports.PdfExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * ViewModel لإدارة سجل العمليات وتطبيق الفلاتر وتصدير الـ PDF
 */
class AuditLogViewModel(
    private val activityLogRepository: ActivityLogRepository,
    private val authRepository: AuthRepository,
    private val termsRepository: com.example.domain.settings.TermsRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuditLogUiState())
    val uiState: StateFlow<AuditLogUiState> = _uiState.asStateFlow()

    private var currentTerms = com.example.domain.settings.CustomTerms()

    init {
        termsRepository?.observeTerms()?.let { flow ->
            viewModelScope.launch {
                flow.collect { currentTerms = it }
            }
        }
        loadLogs()
    }

    private fun loadLogs() {
        viewModelScope.launch {
            activityLogRepository.observeLogs()
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "حدث خطأ أثناء تحميل سجل العمليات"
                    )
                }
                .collect { logs ->
                    val leaders = logs.map { it.userName.ifBlank { "قائد كشفي" } }.distinct().sorted()
                    val currentState = _uiState.value
                    val filtered = applyFilters(
                        logs = logs,
                        dateFilter = currentState.selectedDateFilter,
                        actionFilter = currentState.selectedActionFilter,
                        leader = currentState.selectedLeader
                    )
                    _uiState.value = currentState.copy(
                        isLoading = false,
                        allLogs = logs,
                        filteredLogs = filtered,
                        availableLeaders = leaders,
                        errorMessage = null
                    )
                }
        }
    }

    fun setDateFilter(filter: DateFilter) {
        val currentState = _uiState.value
        val filtered = applyFilters(
            logs = currentState.allLogs,
            dateFilter = filter,
            actionFilter = currentState.selectedActionFilter,
            leader = currentState.selectedLeader
        )
        _uiState.value = currentState.copy(
            selectedDateFilter = filter,
            filteredLogs = filtered
        )
    }

    fun setActionFilter(filter: ActionFilter) {
        val currentState = _uiState.value
        val filtered = applyFilters(
            logs = currentState.allLogs,
            dateFilter = currentState.selectedDateFilter,
            actionFilter = filter,
            leader = currentState.selectedLeader
        )
        _uiState.value = currentState.copy(
            selectedActionFilter = filter,
            filteredLogs = filtered
        )
    }

    fun setLeaderFilter(leader: String?) {
        val currentState = _uiState.value
        val filtered = applyFilters(
            logs = currentState.allLogs,
            dateFilter = currentState.selectedDateFilter,
            actionFilter = currentState.selectedActionFilter,
            leader = leader
        )
        _uiState.value = currentState.copy(
            selectedLeader = leader,
            filteredLogs = filtered
        )
    }

    fun onLogClicked(log: ActivityLog) {
        _uiState.value = _uiState.value.copy(selectedLog = log)
    }

    fun onDismissLogDetails() {
        _uiState.value = _uiState.value.copy(selectedLog = null)
    }

    fun exportAndSharePdf(context: Context) {
        val logsToExport = _uiState.value.filteredLogs.ifEmpty { _uiState.value.allLogs }
        if (logsToExport.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExportingPdf = true)
            try {
                val file = withContext(Dispatchers.IO) {
                    PdfExporter.exportAuditLogPdf(logsToExport, context, terms = currentTerms)
                }
                PdfExporter.shareAuditLogPdf(file, context, terms = currentTerms)
            } catch (_: Exception) {
            } finally {
                _uiState.value = _uiState.value.copy(isExportingPdf = false)
            }
        }
    }

    private fun applyFilters(
        logs: List<ActivityLog>,
        dateFilter: DateFilter,
        actionFilter: ActionFilter,
        leader: String?
    ): List<ActivityLog> {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()

        // حساب بداية اليوم
        calendar.timeInMillis = now
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis

        // بداية الأسبوع
        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        val startOfWeek = calendar.timeInMillis

        // بداية الشهر
        calendar.timeInMillis = now
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfMonth = calendar.timeInMillis

        return logs.filter { log ->
            // فلتر التاريخ
            val matchesDate = when (dateFilter) {
                DateFilter.ALL -> true
                DateFilter.TODAY -> log.timestamp >= startOfDay
                DateFilter.THIS_WEEK -> log.timestamp >= startOfWeek
                DateFilter.THIS_MONTH -> log.timestamp >= startOfMonth
            }

            // فلتر نوع العملية
            val actionLower = log.action.lowercase()
            val matchesAction = when (actionFilter) {
                ActionFilter.ALL -> true
                ActionFilter.ADD -> actionLower in listOf("add", "create", "grant")
                ActionFilter.UPDATE -> actionLower in listOf("update", "edit", "archive", "mark_done", "modify")
                ActionFilter.DELETE -> actionLower in listOf("delete", "remove", "revoke", "cancel")
            }

            // فلتر القائد
            val matchesLeader = leader == null || log.userName.equals(leader, ignoreCase = true)

            matchesDate && matchesAction && matchesLeader
        }
    }

    class Factory(
        private val activityLogRepository: ActivityLogRepository,
        private val authRepository: AuthRepository,
        private val termsRepository: com.example.domain.settings.TermsRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AuditLogViewModel::class.java)) {
                return AuditLogViewModel(
                    activityLogRepository = activityLogRepository,
                    authRepository = authRepository,
                    termsRepository = termsRepository
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
