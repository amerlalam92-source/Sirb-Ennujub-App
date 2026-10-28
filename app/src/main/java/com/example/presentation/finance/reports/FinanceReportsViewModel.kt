package com.example.presentation.finance.reports

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.ScoutApplication
import com.example.data.scout.ScoutFirestoreDataSource
import com.example.domain.finance.FinanceRepository
import com.example.domain.finance.FinanceSummary
import com.example.domain.finance.FinanceTransaction
import com.example.domain.finance.ScoutDebt
import com.example.domain.scout.Scout
import com.example.domain.scout.ScoutRepository
import com.example.presentation.reports.PdfExporter
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class FinanceReportsUiState(
    val allTransactions: List<FinanceTransaction> = emptyList(),
    val summary: FinanceSummary? = null,
    val scouts: List<Scout> = emptyList(),
    val debts: List<ScoutDebt> = emptyList(),
    val isLoading: Boolean = true,
    val isExporting: Boolean = false
)

sealed interface FinanceReportsEvent {
    data class ShowMessage(val message: String) : FinanceReportsEvent
    data class ExportSuccess(val file: File, val title: String) : FinanceReportsEvent
}

class FinanceReportsViewModel(
    private val financeRepository: FinanceRepository,
    private val scoutRepository: ScoutRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(FinanceReportsUiState())
    val uiState: StateFlow<FinanceReportsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<FinanceReportsEvent>()
    val events: SharedFlow<FinanceReportsEvent> = _events.asSharedFlow()

    init {
        loadTransactions()
    }

    fun loadTransactions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val scoutsFlow: Flow<List<Scout>> = scoutRepository?.observeScouts()
                ?: ScoutFirestoreDataSource(FirebaseFirestore.getInstance()).observeScouts()

            combine(
                financeRepository.observeAllTransactions(),
                financeRepository.observeSummary(),
                financeRepository.observeScoutDebts(),
                scoutsFlow
            ) { transactions, summary, debts, scouts ->
                _uiState.update {
                    it.copy(
                        allTransactions = transactions,
                        summary = summary,
                        debts = debts,
                        scouts = scouts,
                        isLoading = false
                    )
                }
            }.collect { }
        }
    }

    fun generateMonthlyPdf(month: Int, year: Int, context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            try {
                val transactions = _uiState.value.allTransactions
                val summary = _uiState.value.summary ?: FinanceSummary()
                val pdfFile = PdfExporter.exportMonthlyStatementPdf(
                    month = month,
                    year = year,
                    transactions = transactions,
                    summary = summary,
                    context = context
                )
                _uiState.update { it.copy(isExporting = false) }
                PdfExporter.shareMonthlyStatementPdf(pdfFile, month, year, context)
                _events.emit(FinanceReportsEvent.ShowMessage("تم تصدير كشف الحساب الشهري بنجاح"))
            } catch (e: Exception) {
                _uiState.update { it.copy(isExporting = false) }
                _events.emit(FinanceReportsEvent.ShowMessage("حدث خطأ أثناء تصدير كشف الحساب: ${e.localizedMessage ?: "خطأ غير متوقع"}"))
            }
        }
    }

    fun generateExpensesPdf(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            try {
                val transactions = _uiState.value.allTransactions
                val pdfFile = PdfExporter.exportExpensesByCategoryPdf(
                    transactions = transactions,
                    context = context
                )
                _uiState.update { it.copy(isExporting = false) }
                PdfExporter.shareExpensesByCategoryPdf(pdfFile, context)
                _events.emit(FinanceReportsEvent.ShowMessage("تم تصدير تقرير المصاريف حسب التصنيف بنجاح"))
            } catch (e: Exception) {
                _uiState.update { it.copy(isExporting = false) }
                _events.emit(FinanceReportsEvent.ShowMessage("حدث خطأ أثناء تصدير تقرير المصاريف: ${e.localizedMessage ?: "خطأ غير متوقع"}"))
            }
        }
    }

    fun generateSubscriptionsSummaryPdf(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            try {
                val subs = financeRepository.observeAllSubscriptions().first()
                val contribs = financeRepository.observeAllContributions().first()
                val summary = _uiState.value.summary ?: financeRepository.observeSummary().first()

                val totalSubsPaid = subs.sumOf { it.paidAmount }
                val totalContribsPaid = contribs.sumOf { it.paidAmount }
                val totalDebt = summary.totalOutstandingDebt

                // تجميع الأنشطة التي تحتوي على ديون متبقية
                val subActivities = subs.groupBy { it.activityTitle.ifBlank { "نشاط غير مسمى" } }
                val contribActivities = contribs.groupBy { it.activityTitle.ifBlank { "نشاط غير مسمى" } }

                val allActivityTitles = (subActivities.keys + contribActivities.keys).distinct()
                val activitiesWithDebt = allActivityTitles.mapNotNull { title ->
                    val sList = subActivities[title] ?: emptyList()
                    val cList = contribActivities[title] ?: emptyList()

                    val req = sList.sumOf { it.requiredAmount } + cList.sumOf { it.requiredAmount }
                    val paid = sList.sumOf { it.paidAmount } + cList.sumOf { it.paidAmount }
                    val debt = (req - paid).coerceAtLeast(0)

                    if (debt > 0) {
                        PdfExporter.ActivityDebtInfo(
                            activityTitle = title,
                            totalRequired = req,
                            totalPaid = paid,
                            totalDebt = debt
                        )
                    } else null
                }.sortedByDescending { it.totalDebt }

                val pdfFile = PdfExporter.exportSubscriptionsAndDebtsSummaryPdf(
                    totalSubscriptionsPaid = totalSubsPaid,
                    totalContributionsPaid = totalContribsPaid,
                    totalOutstandingDebt = totalDebt,
                    activitiesWithDebt = activitiesWithDebt,
                    context = context
                )
                _uiState.update { it.copy(isExporting = false) }
                PdfExporter.shareSubscriptionsAndDebtsSummaryPdf(pdfFile, context)
                _events.emit(FinanceReportsEvent.ShowMessage("تم تصدير تقرير الاشتراكات والديون بنجاح"))
            } catch (e: Exception) {
                _uiState.update { it.copy(isExporting = false) }
                _events.emit(FinanceReportsEvent.ShowMessage("حدث خطأ أثناء تصدير تقرير الاشتراكات: ${e.localizedMessage ?: "خطأ غير متوقع"}"))
            }
        }
    }

    fun generateDetailedDebtsPdf(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            try {
                val debts = financeRepository.observeScoutDebts().first()
                val pdfFile = PdfExporter.exportDetailedDebtsPdf(
                    debts = debts,
                    context = context
                )
                _uiState.update { it.copy(isExporting = false) }
                PdfExporter.shareDetailedDebtsPdf(pdfFile, context)
                _events.emit(FinanceReportsEvent.ShowMessage("تم تصدير تقرير الديون التفصيلي بنجاح"))
            } catch (e: Exception) {
                _uiState.update { it.copy(isExporting = false) }
                _events.emit(FinanceReportsEvent.ShowMessage("حدث خطأ أثناء تصدير تقرير الديون: ${e.localizedMessage ?: "خطأ غير متوقع"}"))
            }
        }
    }

    fun generateAnnualPdf(year: Int, context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            try {
                val transactions = _uiState.value.allTransactions.ifEmpty {
                    financeRepository.observeAllTransactions().first()
                }
                val summary = _uiState.value.summary ?: financeRepository.observeSummary().first()
                val pdfFile = PdfExporter.exportAnnualStatementPdf(
                    year = year,
                    transactions = transactions,
                    summary = summary,
                    context = context
                )
                _uiState.update { it.copy(isExporting = false) }
                PdfExporter.shareAnnualStatementPdf(pdfFile, year, context)
                _events.emit(FinanceReportsEvent.ShowMessage("تم تصدير التقرير السنوي لعام $year بنجاح"))
            } catch (e: Exception) {
                _uiState.update { it.copy(isExporting = false) }
                _events.emit(FinanceReportsEvent.ShowMessage("حدث خطأ أثناء تصدير التقرير السنوي: ${e.localizedMessage ?: "خطأ غير متوقع"}"))
            }
        }
    }

    fun generateScoutStatementPdf(scout: Scout, context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            try {
                val subscriptions = financeRepository.observeSubscriptionsForScout(scout.id).first()
                val contributions = financeRepository.observeContributionsForScout(scout.id).first()
                val pdfFile = PdfExporter.exportScoutStatementPdf(
                    scout = scout,
                    subscriptions = subscriptions,
                    contributions = contributions,
                    context = context
                )
                _uiState.update { it.copy(isExporting = false) }
                PdfExporter.shareScoutStatementPdf(pdfFile, scout.fullName, context)
                _events.emit(FinanceReportsEvent.ShowMessage("تم تصدير كشف حساب الشبل ${scout.fullName} بنجاح"))
            } catch (e: Exception) {
                _uiState.update { it.copy(isExporting = false) }
                _events.emit(FinanceReportsEvent.ShowMessage("حدث خطأ أثناء تصدير كشف حساب الشبل: ${e.localizedMessage ?: "خطأ غير متوقع"}"))
            }
        }
    }

    class Factory(
        private val financeRepository: FinanceRepository,
        private val scoutRepository: ScoutRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FinanceReportsViewModel(financeRepository, scoutRepository) as T
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val repo = scoutRepository
                ?: (extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as? ScoutApplication)?.container?.scoutRepository
            return FinanceReportsViewModel(financeRepository, repo) as T
        }
    }
}
