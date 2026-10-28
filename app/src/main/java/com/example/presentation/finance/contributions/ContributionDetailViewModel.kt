package com.example.presentation.finance.contributions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.activity.Activity
import com.example.domain.activity.ActivityRepository
import com.example.domain.auth.AuthRepository
import com.example.domain.finance.FinanceContribution
import com.example.domain.finance.FinanceRepository
import com.example.domain.scout.Scout
import com.example.domain.scout.ScoutRepository
import com.example.presentation.common.AppLocale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

/**
 * نموذج العرض لتفاصيل مساهمات النشاط الكشفي (ContributionDetailViewModel)
 * يدير قائمة المشاركين، تصفية المدينين والمسددين، وعمليات التسديد والإضافة المتعددة.
 */
class ContributionDetailViewModel(
    private val activityId: String,
    private val financeRepository: FinanceRepository,
    private val activityRepository: ActivityRepository,
    private val scoutRepository: ScoutRepository,
    private val authRepository: AuthRepository? = null
) : ViewModel() {

    private val arabicCollator = Collator.getInstance(Locale("ar")).apply {
        strength = Collator.PRIMARY
    }

    private val _selectedFilter = MutableStateFlow(ContributionDetailFilter.ALL)
    private val _savingScoutId = MutableStateFlow<String?>(null)
    private val _paymentDialogContribution = MutableStateFlow<FinanceContribution?>(null)
    private val _editDialogContribution = MutableStateFlow<FinanceContribution?>(null)
    private val _showAddScoutDialog = MutableStateFlow(false)
    private val _showDefaultSettingsDialog = MutableStateFlow(false)
    private val _deleteConfirmContribution = MutableStateFlow<FinanceContribution?>(null)
    private val _successMessage = MutableStateFlow<String?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)

    private val _activityMeta = MutableStateFlow<Activity?>(null)

    // إضافة المشاركين (Multi-Select)
    private val _newParticipantAmount = MutableStateFlow("")
    val newParticipantAmount: StateFlow<String> = _newParticipantAmount.asStateFlow()

    private val _selectedNewScoutIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedNewScoutIds: StateFlow<Set<String>> = _selectedNewScoutIds.asStateFlow()

    val availableScoutsForAddition: StateFlow<List<Scout>> = combine(
        scoutRepository.observeScouts(),
        financeRepository.observeContributionsForActivity(activityId)
    ) { scouts, contribs ->
        val registeredScoutIds = contribs.map { it.scoutId }.toSet()
        scouts.filter { it.isActive && it.id !in registeredScoutIds }
            .sortedWith { s1, s2 -> arabicCollator.compare(s1.fullName, s2.fullName) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    val uiState: StateFlow<ContributionDetailUiState> = combine(
        financeRepository.observeContributionsForActivity(activityId),
        scoutRepository.observeScouts(),
        _selectedFilter,
        _savingScoutId,
        _paymentDialogContribution,
        _editDialogContribution,
        _showAddScoutDialog,
        _showDefaultSettingsDialog,
        _deleteConfirmContribution,
        _successMessage,
        _errorMessage,
        _activityMeta,
        _newParticipantAmount,
        _selectedNewScoutIds
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val contribs = args[0] as List<FinanceContribution>
        @Suppress("UNCHECKED_CAST")
        val scouts = args[1] as List<Scout>
        val filter = args[2] as ContributionDetailFilter
        val savingId = args[3] as String?
        val payDialog = args[4] as FinanceContribution?
        val editDialog = args[5] as FinanceContribution?
        val showAdd = args[6] as Boolean
        val showDef = args[7] as Boolean
        val delConfirm = args[8] as FinanceContribution?
        val successMsg = args[9] as String?
        val errorMsg = args[10] as String?
        val actMeta = args[11] as Activity?
        val newAmount = args[12] as String
        @Suppress("UNCHECKED_CAST")
        val selectedIds = args[13] as Set<String>

        val first = contribs.firstOrNull()
        val totalRequired = contribs.sumOf { it.requiredAmount }
        val totalPaid = contribs.sumOf { it.paidAmount }
        val totalDebt = contribs.sumOf { it.debtAmount }
        val debtorsCount = contribs.count { it.debtAmount > 0 }
        val paidCount = contribs.count { it.debtAmount == 0 }

        val filtered = when (filter) {
            ContributionDetailFilter.ALL -> contribs
            ContributionDetailFilter.DEBTORS -> contribs.filter { it.debtAmount > 0 }
            ContributionDetailFilter.PAID -> contribs.filter { it.debtAmount == 0 }
        }

        val registeredScoutIds = contribs.map { it.scoutId }.toSet()
        val activeAvailableScouts = scouts.filter { it.isActive && it.id !in registeredScoutIds }
            .sortedWith { s1, s2 -> arabicCollator.compare(s1.fullName, s2.fullName) }

        val title = first?.activityTitle.orEmpty().ifBlank { actMeta?.title.orEmpty() }
        val date = first?.activityDate ?: (actMeta?.startDate ?: 0L)
        val defaultAmt = actMeta?.contributionDefaultAmount
        val isReq = actMeta?.contributionRequired ?: false

        ContributionDetailUiState(
            activityId = activityId,
            activityTitle = title,
            activityDate = date,
            isContributionRequired = isReq,
            defaultAmount = defaultAmt,
            contributions = contribs,
            filteredContributions = filtered,
            selectedFilter = filter,
            totalRequired = totalRequired,
            totalPaid = totalPaid,
            totalDebt = totalDebt,
            participantsCount = contribs.size,
            debtorsCount = debtorsCount,
            paidCount = paidCount,
            availableScouts = activeAvailableScouts,
            newParticipantAmount = newAmount,
            selectedNewScoutIds = selectedIds,
            availableScoutsForAddition = activeAvailableScouts,
            isLoading = false,
            isSaving = savingId != null,
            savingScoutId = savingId,
            paymentDialogContribution = payDialog,
            editDialogContribution = editDialog,
            showAddScoutDialog = showAdd,
            showDefaultSettingsDialog = showDef,
            deleteConfirmContribution = delConfirm,
            successMessage = successMsg,
            errorMessage = errorMsg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ContributionDetailUiState(activityId = activityId, isLoading = true)
    )

    init {
        loadActivityMeta()
    }

    private fun loadActivityMeta() {
        viewModelScope.launch {
            val act = activityRepository.getActivityById(activityId)
            _activityMeta.value = act
        }
    }

    fun onFilterSelected(filter: ContributionDetailFilter) {
        _selectedFilter.value = filter
    }

    // --- تسديد كامل فوري ---
    fun onPayFullClick(contribution: FinanceContribution) {
        val remaining = contribution.debtAmount
        if (remaining <= 0) return

        viewModelScope.launch {
            _savingScoutId.value = contribution.scoutId
            val result = financeRepository.recordContributionPayment(
                activityId = contribution.activityId,
                scoutId = contribution.scoutId,
                amountPaidNow = remaining,
                notes = "تسديد كامل"
            )
            _savingScoutId.value = null

            if (result.isSuccess) {
                _successMessage.value = "تم تسديد كامل المساهمة للشبل ${contribution.scoutFullName}"
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "فشلت عملية التسديد"
            }
        }
    }

    // --- تسديد جزئي ---
    fun onOpenPaymentDialog(contribution: FinanceContribution) {
        _paymentDialogContribution.value = contribution
    }

    fun onDismissPaymentDialog() {
        _paymentDialogContribution.value = null
    }

    fun onConfirmPartialPayment(amount: Int, notes: String?) {
        val target = _paymentDialogContribution.value ?: return
        if (amount <= 0) {
            _errorMessage.value = "المبلغ يجب أن يكون أكبر من صفر"
            return
        }

        viewModelScope.launch {
            _savingScoutId.value = target.scoutId
            _paymentDialogContribution.value = null

            val result = financeRepository.recordContributionPayment(
                activityId = target.activityId,
                scoutId = target.scoutId,
                amountPaidNow = amount,
                notes = notes
            )
            _savingScoutId.value = null

            if (result.isSuccess) {
                _successMessage.value = "تم تسجيل تسديد $amount دج للشبل ${target.scoutFullName}"
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "فشلت عملية التسديد"
            }
        }
    }

    // --- تعديل المساهمة (المبلغ المطلوب والملاحظات) ---
    fun onOpenEditDialog(contribution: FinanceContribution) {
        _editDialogContribution.value = contribution
    }

    fun onDismissEditDialog() {
        _editDialogContribution.value = null
    }

    fun onSaveEditedContribution(newRequired: Int, notes: String?) {
        val target = _editDialogContribution.value ?: return
        val newDebt = (newRequired - target.paidAmount).coerceAtLeast(0)
        val updated = target.copy(
            requiredAmount = newRequired,
            debtAmount = newDebt,
            notes = notes,
            updatedAt = System.currentTimeMillis()
        )

        viewModelScope.launch {
            _savingScoutId.value = target.scoutId
            _editDialogContribution.value = null

            val result = financeRepository.upsertContribution(updated)
            _savingScoutId.value = null

            if (result.isSuccess) {
                _successMessage.value = "تم تحديث مساهمة الشبل ${target.scoutFullName}"
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "فشل تحديث المساهمة"
            }
        }
    }

    // --- حذف شبل من المساهمات ---
    fun onDeleteClick(contribution: FinanceContribution) {
        _deleteConfirmContribution.value = contribution
    }

    fun onDismissDelete() {
        _deleteConfirmContribution.value = null
    }

    fun onConfirmDelete() {
        val target = _deleteConfirmContribution.value ?: return
        viewModelScope.launch {
            _savingScoutId.value = target.scoutId
            _deleteConfirmContribution.value = null

            val result = financeRepository.deleteContribution(target.activityId, target.scoutId)
            _savingScoutId.value = null

            if (result.isSuccess) {
                _successMessage.value = "تم حذف الشبل ${target.scoutFullName} من قائمة المساهمات"
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "فشل حذف الشبل"
            }
        }
    }

    // --- إضافة مشاركين باختيار متعدد (Multi-Select Participants Addition) ---
    fun onOpenAddScoutDialog() {
        val def = _activityMeta.value?.contributionDefaultAmount
        _newParticipantAmount.value = if (def != null && def > 0) def.toString() else ""
        _selectedNewScoutIds.value = emptySet()
        _showAddScoutDialog.value = true
    }

    fun onDismissAddScoutDialog() {
        _showAddScoutDialog.value = false
        _selectedNewScoutIds.value = emptySet()
    }

    fun onNewParticipantAmountChanged(value: String) {
        val filtered = value.filter { it.isDigit() }
        _newParticipantAmount.value = filtered
    }

    fun onNewScoutToggled(scoutId: String) {
        val current = _selectedNewScoutIds.value.toMutableSet()
        if (scoutId in current) {
            current.remove(scoutId)
        } else {
            current.add(scoutId)
        }
        _selectedNewScoutIds.value = current
    }

    fun onSelectAllAvailable() {
        val available = availableScoutsForAddition.value.ifEmpty {
            uiState.value.availableScoutsForAddition.ifEmpty {
                uiState.value.availableScouts
            }
        }
        val allIds = available.map { it.id }.toSet()
        _selectedNewScoutIds.value = allIds
    }

    fun onClearSelection() {
        _selectedNewScoutIds.value = emptySet()
    }

    fun onAddParticipantsConfirmed() {
        val amount = _newParticipantAmount.value.toIntOrNull()
        if (amount == null || amount <= 0) {
            _errorMessage.value = "المبلغ يجب أن يكون أكبر من صفر"
            return
        }
        val selectedIds = _selectedNewScoutIds.value
        if (selectedIds.isEmpty()) {
            _errorMessage.value = "يرجى تحديد شبل واحد على الأقل"
            return
        }

        viewModelScope.launch {
            _savingScoutId.value = "batch_add"
            val act = _activityMeta.value ?: activityRepository.getActivityById(activityId)
            val title = act?.title.orEmpty().ifBlank { uiState.value.activityTitle.ifBlank { "نشاط كشفي" } }
            val date = act?.startDate ?: uiState.value.activityDate

            val scoutsList = availableScoutsForAddition.value.ifEmpty {
                uiState.value.availableScoutsForAddition.ifEmpty {
                    uiState.value.availableScouts
                }
            }
            val scoutsMap = scoutsList.associateBy { it.id }
            val now = System.currentTimeMillis()
            val leaderUid = authRepository?.currentUser?.uid.orEmpty()
            val leaderName = authRepository?.currentUser?.displayName.orEmpty()

            val newContributions = selectedIds.mapNotNull { scoutId ->
                val scout = scoutsMap[scoutId] ?: return@mapNotNull null
                FinanceContribution(
                    id = "${activityId}_${scout.id}",
                    scoutId = scout.id,
                    scoutFullName = scout.fullName,
                    activityId = activityId,
                    activityTitle = title,
                    activityDate = date,
                    requiredAmount = amount,
                    paidAmount = 0,
                    debtAmount = amount,
                    notes = null,
                    recordedAt = now,
                    recordedBy = leaderUid,
                    recordedByName = leaderName,
                    updatedAt = now
                )
            }

            if (newContributions.isEmpty()) {
                _savingScoutId.value = null
                return@launch
            }

            val result = financeRepository.upsertContributionsBatch(newContributions)
            _savingScoutId.value = null

            if (result.isSuccess) {
                _showAddScoutDialog.value = false
                _selectedNewScoutIds.value = emptySet()
                _newParticipantAmount.value = ""
                _successMessage.value = String.format(
                    AppLocale.AR_DZ,
                    "تمت إضافة %d مشارك",
                    newContributions.size
                )
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "فشلت إضافة المشاركين"
            }
        }
    }

    fun onAddScoutToContributions(scout: Scout, requiredAmount: Int) {
        viewModelScope.launch {
            val act = _activityMeta.value ?: activityRepository.getActivityById(activityId)
            val title = act?.title.orEmpty().ifBlank { uiState.value.activityTitle.ifBlank { "نشاط كشفي" } }
            val date = act?.startDate ?: uiState.value.activityDate

            val newContribution = FinanceContribution(
                id = "${activityId}_${scout.id}",
                scoutId = scout.id,
                scoutFullName = scout.fullName,
                activityId = activityId,
                activityTitle = title,
                activityDate = date,
                requiredAmount = requiredAmount,
                paidAmount = 0,
                debtAmount = requiredAmount
            )

            val result = financeRepository.upsertContribution(newContribution)
            if (result.isSuccess) {
                _successMessage.value = "تمت إضافة الشبل ${newContribution.scoutFullName} للمساهمة"
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "فشلت إضافة الشبل"
            }
        }
    }

    fun onAddAllAvailableScouts(defaultAmount: Int) {
        viewModelScope.launch {
            val act = _activityMeta.value ?: activityRepository.getActivityById(activityId)
            val title = act?.title.orEmpty().ifBlank { uiState.value.activityTitle.ifBlank { "نشاط كشفي" } }
            val date = act?.startDate ?: uiState.value.activityDate

            val scoutsToAdd = availableScoutsForAddition.value
            val now = System.currentTimeMillis()
            val leaderUid = authRepository?.currentUser?.uid.orEmpty()
            val leaderName = authRepository?.currentUser?.displayName.orEmpty()

            val newContributions = scoutsToAdd.map { scout ->
                FinanceContribution(
                    id = "${activityId}_${scout.id}",
                    scoutId = scout.id,
                    scoutFullName = scout.fullName,
                    activityId = activityId,
                    activityTitle = title,
                    activityDate = date,
                    requiredAmount = defaultAmount,
                    paidAmount = 0,
                    debtAmount = defaultAmount,
                    recordedAt = now,
                    recordedBy = leaderUid,
                    recordedByName = leaderName,
                    updatedAt = now
                )
            }

            val result = financeRepository.upsertContributionsBatch(newContributions)
            _showAddScoutDialog.value = false
            if (result.isSuccess) {
                _successMessage.value = "تمت إضافة ${newContributions.size} شبل إلى قائمة المساهمات"
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "فشلت إضافة الأشبال"
            }
        }
    }

    // --- إعدادات المساهمة للنشاط ---
    fun onOpenDefaultSettingsDialog() {
        _showDefaultSettingsDialog.value = true
    }

    fun onDismissDefaultSettingsDialog() {
        _showDefaultSettingsDialog.value = false
    }

    fun onSaveActivityContributionSettings(required: Boolean, defaultAmount: Int?) {
        viewModelScope.launch {
            val result = activityRepository.updateContributionSettings(activityId, required, defaultAmount)
            _showDefaultSettingsDialog.value = false
            if (result.isSuccess) {
                loadActivityMeta()
                _successMessage.value = "تم تحديث إعدادات المساهمة للنشاط"
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "فشل تحديث الإعدادات"
            }
        }
    }

    fun deleteAllCollectionsForActivity(onDeleted: () -> Unit) {
        viewModelScope.launch {
            val result = financeRepository.deleteCollectionsForActivity(activityId)
            if (result.isSuccess) {
                onDeleted()
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "فشل حذف التحصيلات"
            }
        }
    }

    fun clearMessages() {
        _successMessage.value = null
        _errorMessage.value = null
    }

    class Factory(
        private val activityId: String,
        private val financeRepository: FinanceRepository,
        private val activityRepository: ActivityRepository,
        private val scoutRepository: ScoutRepository,
        private val authRepository: AuthRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ContributionDetailViewModel(activityId, financeRepository, activityRepository, scoutRepository, authRepository) as T
        }
    }
}
