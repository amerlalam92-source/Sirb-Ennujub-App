package com.example.presentation.audit

import com.example.R
import com.example.domain.log.ActivityLog

/**
 * فلترة سجل العمليات حسب التاريخ
 */
enum class DateFilter(val labelResId: Int) {
    ALL(R.string.audit_filter_date_all),
    TODAY(R.string.audit_filter_date_today),
    THIS_WEEK(R.string.audit_filter_date_week),
    THIS_MONTH(R.string.audit_filter_date_month)
}

/**
 * فلترة سجل العمليات حسب نوع العملية
 */
enum class ActionFilter(val labelResId: Int) {
    ALL(R.string.audit_filter_action_all),
    ADD(R.string.audit_filter_action_add),
    UPDATE(R.string.audit_filter_action_update),
    DELETE(R.string.audit_filter_action_delete)
}

/**
 * حالة واجهة سجل العمليات (Audit Log Ui State)
 */
data class AuditLogUiState(
    val isLoading: Boolean = true,
    val allLogs: List<ActivityLog> = emptyList(),
    val filteredLogs: List<ActivityLog> = emptyList(),
    val selectedDateFilter: DateFilter = DateFilter.ALL,
    val selectedActionFilter: ActionFilter = ActionFilter.ALL,
    val selectedLeader: String? = null, // null تعني كافة القادة
    val availableLeaders: List<String> = emptyList(),
    val isExportingPdf: Boolean = false,
    val selectedLog: ActivityLog? = null,
    val errorMessage: String? = null
)
