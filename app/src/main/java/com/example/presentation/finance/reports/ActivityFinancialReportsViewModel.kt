package com.example.presentation.finance.reports

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.activity.Activity
import com.example.domain.activity.ActivityRepository
import com.example.domain.finance.FinanceRepository
import com.example.domain.finance.FinanceTransactionType
import com.example.domain.scout.ScoutRepository
import com.example.domain.settings.CustomTerms
import com.example.domain.settings.TermsRepository
import com.example.presentation.reports.PdfExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * تفاصيل مدفوعات الشبل المجمّعة لنشاط محدد (اشتراك + سداد دين + دفعة مقدمة)
 */
data class ScoutPaymentDetail(
    val scoutName: String,
    val subPaid: Int,
    val debtPaid: Int,
    val advancePaid: Int,
    val totalPaid: Int
)

/**
 * نموذج العرض لشاشة التقارير المالية للأنشطة (ActivityFinancialReportsViewModel)
 * يجلب الأنشطة والبيانات المالية الخاصة بكل نشاط ويولد ملف PDF مفصل.
 */
class ActivityFinancialReportsViewModel(
    private val financeRepository: FinanceRepository,
    private val scoutRepository: ScoutRepository,
    private val activityRepository: ActivityRepository,
    private val termsRepository: TermsRepository? = null
) : ViewModel() {

    private var currentTerms: CustomTerms = CustomTerms()

    init {
        termsRepository?.observeTerms()?.let { flow ->
            viewModelScope.launch {
                flow.collect { currentTerms = it }
            }
        }
    }

    val activities: StateFlow<List<Activity>?> = activityRepository.observeActivities()
        .map { list -> list.sortedByDescending { it.startDate } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    /**
     * توليد التقرير المالي الكامل لنشاط محدد بصيغة PDF مع تجميع كامل مدفوعات كل شبل
     */
    suspend fun generateReportPdf(activityId: String, context: Context): Result<File> {
        _isExporting.value = true
        return try {
            val activity = activityRepository.getActivityById(activityId)
                ?: return Result.failure(IllegalStateException("تعذر العثور على بيانات النشاط"))

            val paidSubscriptions = financeRepository.observeSubscriptionsForActivity(activityId)
                .first()
                .filter { it.paidAmount > 0 }

            val allTransactions = financeRepository.observeAllTransactions().first()
            val activityTransactions = allTransactions.filter {
                it.relatedActivityId == activityId && !it.isRolledBack
            }

            val scoutsById = scoutRepository.observeScouts().first().associateBy { it.id }

            val paymentTransactions = activityTransactions.filter {
                it.type == FinanceTransactionType.DEBT_PAYMENT ||
                    it.type == FinanceTransactionType.ADVANCE_PAYMENT
            }

            val subsByScoutKey = paidSubscriptions.groupBy { sub ->
                sub.scoutId.ifBlank { "name_${sub.scoutFullName.trim()}" }
            }
            val txsByScoutKey = paymentTransactions.groupBy { tx ->
                tx.scoutId?.takeIf { it.isNotBlank() } ?: "name_${tx.source?.trim().orEmpty()}"
            }

            val allScoutKeys = (subsByScoutKey.keys + txsByScoutKey.keys).distinct()
            val scoutPaymentDetails = allScoutKeys.mapNotNull { key ->
                val scoutRecord = scoutsById[key]
                if (scoutRecord != null && !scoutRecord.isActive) {
                    return@mapNotNull null
                }

                val scoutSubs = subsByScoutKey[key].orEmpty()
                val scoutTxs = txsByScoutKey[key].orEmpty()

                val subPaid = scoutSubs.sumOf { it.paidAmount }
                val debtPaid = scoutTxs
                    .filter { it.type == FinanceTransactionType.DEBT_PAYMENT }
                    .sumOf { it.amount }
                val advancePaid = scoutTxs
                    .filter { it.type == FinanceTransactionType.ADVANCE_PAYMENT }
                    .sumOf { it.amount }
                val totalPaid = subPaid + debtPaid + advancePaid

                if (totalPaid <= 0) return@mapNotNull null

                val scoutName = scoutRecord?.fullName?.takeIf { it.isNotBlank() }
                    ?: scoutSubs.firstOrNull { it.scoutFullName.isNotBlank() }?.scoutFullName
                    ?: scoutTxs.firstOrNull { !it.source.isNullOrBlank() }?.source
                    ?: currentTerms.scoutSingular

                ScoutPaymentDetail(
                    scoutName = scoutName,
                    subPaid = subPaid,
                    debtPaid = debtPaid,
                    advancePaid = advancePaid,
                    totalPaid = totalPaid
                )
            }.sortedWith(
                compareByDescending<ScoutPaymentDetail> { it.totalPaid }
                    .thenBy { it.scoutName }
            )

            val expenseTransactions = activityTransactions
                .filter { it.type == FinanceTransactionType.EXPENSE }
                .sortedBy { it.date }

            val totalIncome = scoutPaymentDetails.sumOf { it.totalPaid }
            val totalExpenses = expenseTransactions.sumOf { it.amount }
            val netBalance = totalIncome - totalExpenses

            val pdfFile = withContext(Dispatchers.IO) {
                PdfExporter.exportActivityFinancePdf(
                    activity = activity,
                    scoutPayments = scoutPaymentDetails,
                    expenses = expenseTransactions,
                    netBalance = netBalance,
                    context = context,
                    terms = currentTerms
                )
            }

            Result.success(pdfFile)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            _isExporting.value = false
        }
    }

    class Factory(
        private val financeRepository: FinanceRepository,
        private val scoutRepository: ScoutRepository,
        private val activityRepository: ActivityRepository,
        private val termsRepository: TermsRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ActivityFinancialReportsViewModel(
                financeRepository = financeRepository,
                scoutRepository = scoutRepository,
                activityRepository = activityRepository,
                termsRepository = termsRepository
            ) as T
        }
    }
}
