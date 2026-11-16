package com.example.presentation.finance.reports

import android.content.Context
import androidx.core.content.FileProvider
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
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext
import java.io.File

data class FinanceReportsUiState(
    val allTransactions: List<FinanceTransaction> = emptyList(),
    val summary: FinanceSummary? = null,
    val scouts: List<Scout> = emptyList(),
    val debts: List<ScoutDebt> = emptyList(),
    val isLoading: Boolean = true,
    val isExporting: Boolean = false,
    val isExportingMultiple: Boolean = false,
    val exportMultipleProgress: Pair<Int, Int>? = null,
    val selectedDebtFilter: String = "all",
    val searchQuery: String = "",
    val selectedScoutIds: Set<String> = emptySet()
)

sealed interface FinanceReportsEvent {
    data class ShowMessage(val message: String) : FinanceReportsEvent
    data class ExportSuccess(val file: File, val title: String) : FinanceReportsEvent
}

class FinanceReportsViewModel(
    private val financeRepository: FinanceRepository,
    private val scoutRepository: ScoutRepository? = null,
    private val termsRepository: com.example.domain.settings.TermsRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(FinanceReportsUiState())
    val uiState: StateFlow<FinanceReportsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<FinanceReportsEvent>()
    val events: SharedFlow<FinanceReportsEvent> = _events.asSharedFlow()

    val selectedDebtFilter: String
        get() = _uiState.value.selectedDebtFilter

    private var currentTerms = com.example.domain.settings.CustomTerms()

    init {
        termsRepository?.observeTerms()?.let { flow ->
            viewModelScope.launch {
                flow.collect { currentTerms = it }
            }
        }
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
                val activeScouts = scouts.filter { it.isActive }
                _uiState.update {
                    it.copy(
                        allTransactions = transactions,
                        summary = summary,
                        debts = debts,
                        scouts = activeScouts,
                        isLoading = false
                    )
                }
            }.collect { }
        }
    }

    fun onDebtFilterChanged(filter: String) {
        _uiState.update { it.copy(selectedDebtFilter = filter) }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun toggleScoutSelection(scoutId: String) {
        _uiState.update { state ->
            val updated = if (state.selectedScoutIds.contains(scoutId)) {
                state.selectedScoutIds - scoutId
            } else {
                state.selectedScoutIds + scoutId
            }
            state.copy(selectedScoutIds = updated)
        }
    }

    fun selectAll() {
        val visibleIds = getFilteredScouts(_uiState.value.searchQuery).map { it.id }.toSet()
        _uiState.update { state ->
            state.copy(selectedScoutIds = state.selectedScoutIds + visibleIds)
        }
    }

    fun deselectAll() {
        val visibleIds = getFilteredScouts(_uiState.value.searchQuery).map { it.id }.toSet()
        _uiState.update { state ->
            state.copy(selectedScoutIds = state.selectedScoutIds - visibleIds)
        }
    }

    fun getFilteredScouts(searchQuery: String = ""): List<Scout> {
        val state = _uiState.value
        val activeScouts = state.scouts.filter { it.isActive }
        val baseList = if (state.selectedDebtFilter == "debtors") {
            val debtorIds = state.debts.filter { it.totalDebt > 0 }.map { it.scoutId }.toSet()
            activeScouts.filter { debtorIds.contains(it.id) }
        } else {
            activeScouts
        }
        val query = searchQuery.trim().lowercase()
        val finalList = if (query.isEmpty()) {
            baseList
        } else {
            baseList.filter { it.fullName.lowercase().contains(query) }
        }
        return finalList.sortedBy { it.fullName }
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
                    context = context,
                    terms = currentTerms
                )
                _uiState.update { it.copy(isExporting = false) }
                PdfExporter.shareMonthlyStatementPdf(pdfFile, month, year, context, terms = currentTerms)
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
                    context = context,
                    terms = currentTerms
                )
                _uiState.update { it.copy(isExporting = false) }
                PdfExporter.shareExpensesByCategoryPdf(pdfFile, context, terms = currentTerms)
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
                val summary = _uiState.value.summary ?: financeRepository.observeSummary().first()

                val totalSubsPaid = subs.sumOf { it.paidAmount }
                val totalDebt = summary.totalOutstandingDebt

                // تجميع الأنشطة التي تحتوي على ديون متبقية
                val subActivities = subs.groupBy { it.activityTitle.ifBlank { "نشاط غير مسمى" } }

                val allActivityTitles = subActivities.keys.distinct()
                val activitiesWithDebt = allActivityTitles.mapNotNull { title ->
                    val sList = subActivities[title] ?: emptyList()

                    val req = sList.sumOf { it.requiredAmount }
                    val paid = sList.sumOf { it.paidAmount }
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
                    totalContributionsPaid = 0,
                    totalOutstandingDebt = totalDebt,
                    activitiesWithDebt = activitiesWithDebt,
                    context = context,
                    terms = currentTerms
                )
                _uiState.update { it.copy(isExporting = false) }
                PdfExporter.shareSubscriptionsAndDebtsSummaryPdf(pdfFile, context, terms = currentTerms)
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
                    context = context,
                    terms = currentTerms
                )
                _uiState.update { it.copy(isExporting = false) }
                PdfExporter.shareDetailedDebtsPdf(pdfFile, context, terms = currentTerms)
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
                    context = context,
                    terms = currentTerms
                )
                _uiState.update { it.copy(isExporting = false) }
                PdfExporter.shareAnnualStatementPdf(pdfFile, year, context, terms = currentTerms)
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
                val pdfFile = PdfExporter.exportScoutStatementPdf(
                    scout = scout,
                    subscriptions = subscriptions,
                    contributions = emptyList(),
                    context = context,
                    terms = currentTerms
                )
                _uiState.update { it.copy(isExporting = false) }
                PdfExporter.shareScoutStatementPdf(pdfFile, scout.fullName, context, terms = currentTerms)
                _events.emit(FinanceReportsEvent.ShowMessage("تم تصدير كشف حساب ${currentTerms.scoutSingularDefinite} ${scout.fullName} بنجاح"))
            } catch (e: Exception) {
                _uiState.update { it.copy(isExporting = false) }
                _events.emit(FinanceReportsEvent.ShowMessage("حدث خطأ أثناء تصدير كشف حساب ${currentTerms.scoutSingularDefinite}: ${e.localizedMessage ?: "خطأ غير متوقع"}"))
            }
        }
    }

    /**
     * تصدير كشوفات الحساب لمجموعة من الأشبال في الذاكرة المؤقتة ومشاركتها مباشرة عبر Share Sheet
     */
    fun generateMultipleScoutStatementsPdf(scouts: List<Scout>, context: Context) {
        viewModelScope.launch {
            if (scouts.isEmpty()) {
                _events.emit(FinanceReportsEvent.ShowMessage("لم يتم تحديد أي ${currentTerms.scoutSingular} للتصدير"))
                return@launch
            }
            _uiState.update {
                it.copy(
                    isExportingMultiple = true,
                    exportMultipleProgress = Pair(0, scouts.size)
                )
            }
            try {
                val generatedFiles = withContext(Dispatchers.IO) {
                    val files = mutableListOf<File>()
                    val usedFileNames = mutableSetOf<String>()
                    scouts.forEachIndexed { index, scout ->
                        _uiState.update { it.copy(exportMultipleProgress = Pair(index + 1, scouts.size)) }
                        val subscriptions = financeRepository.observeSubscriptionsForScout(scout.id).first()
                        val rawName = scout.fullName.ifBlank { scout.name }.ifBlank { scout.id }
                        val safeName = rawName.replace(Regex("[^a-zA-Z0-9\\u0600-\\u06FF_-]"), "_")
                            .trim('_')
                            .ifBlank { scout.id.ifBlank { "scout_${index + 1}" } }
                        var candidateName = "Scout_Statement_${safeName}.pdf"
                        if (!usedFileNames.add(candidateName)) {
                            candidateName = "Scout_Statement_${safeName}_${index + 1}.pdf"
                            usedFileNames.add(candidateName)
                        }
                        val pdfFile = PdfExporter.exportScoutStatementPdf(
                            scout = scout,
                            subscriptions = subscriptions,
                            contributions = emptyList(),
                            context = context,
                            terms = currentTerms,
                            fileNameOverride = candidateName
                        )
                        files.add(pdfFile)
                    }
                    files
                }

                _uiState.update {
                    it.copy(
                        isExportingMultiple = false,
                        exportMultipleProgress = null
                    )
                }

                if (generatedFiles.size == 1) {
                    PdfExporter.shareScoutStatementPdf(
                        file = generatedFiles.first(),
                        scoutName = scouts.first().fullName,
                        context = context,
                        terms = currentTerms
                    )
                } else {
                    val uris = generatedFiles.map { file ->
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                    }
                    PdfExporter.shareMultiplePdfs(
                        uris = uris,
                        subject = "كشوفات حساب ${currentTerms.scoutPluralDefinite} — ${currentTerms.groupName} ${currentTerms.unitNameDefinite}",
                        chooserTitle = "مشاركة كشوفات الحساب كـ PDF",
                        context = context
                    )
                }

                _events.emit(
                    FinanceReportsEvent.ShowMessage("تم إنشاء ومشاركة ${generatedFiles.size} كشف حساب بنجاح")
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isExportingMultiple = false,
                        exportMultipleProgress = null
                    )
                }
                _events.emit(
                    FinanceReportsEvent.ShowMessage("حدث خطأ أثناء تصدير كشوفات الحساب: ${e.localizedMessage ?: "خطأ غير متوقع"}")
                )
            }
        }
    }

    class Factory(
        private val financeRepository: FinanceRepository,
        private val scoutRepository: ScoutRepository? = null,
        private val termsRepository: com.example.domain.settings.TermsRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FinanceReportsViewModel(financeRepository, scoutRepository, termsRepository) as T
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as? ScoutApplication
            val repo = scoutRepository ?: app?.container?.scoutRepository
            val termsRepo = termsRepository ?: app?.container?.termsRepository
            return FinanceReportsViewModel(financeRepository, repo, termsRepo) as T
        }
    }
}
