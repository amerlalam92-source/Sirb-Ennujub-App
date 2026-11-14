package com.example.presentation.attendance

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.activity.Activity
import com.example.domain.activity.ActivityRepository
import com.example.domain.activity.ActivityType
import com.example.domain.attendance.Attendance
import com.example.domain.attendance.AttendancePointsSettings
import com.example.domain.attendance.AttendanceRepository
import com.example.domain.attendance.AttendanceStatus
import com.example.domain.attendance.usecases.GetAttendanceForActivityUseCase
import com.example.domain.attendance.usecases.ObservePointsSettingsUseCase
import com.example.domain.attendance.usecases.SaveAttendanceBatchUseCase
import com.example.domain.finance.FinanceRepository
import com.example.domain.finance.FinanceSubscription
import com.example.domain.finance.FinanceTransaction
import com.example.domain.finance.FinanceTransactionType
import com.example.domain.finance.SubscriptionStatus
import com.example.domain.scout.Scout
import com.example.domain.scout.ScoutRepository
import com.example.domain.settings.SettingsRepository
import java.text.Collator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * نموذج العرض لتسجيل الحضور الكشفي (Attendance ViewModel)
 * يدير معالج الحضور المتسلسل (Wizard)، الحفظ الفوري، الإدخال السريع، وتوزيع المدفوعات والديون السابقة تلقائياً.
 */
class AttendanceViewModel(
    private val activityRepository: ActivityRepository,
    private val scoutRepository: ScoutRepository,
    private val attendanceRepository: AttendanceRepository,
    private val getAttendanceForActivityUseCase: GetAttendanceForActivityUseCase,
    private val saveAttendanceBatchUseCase: SaveAttendanceBatchUseCase,
    private val observePointsSettingsUseCase: ObservePointsSettingsUseCase,
    private val financeRepository: FinanceRepository? = null,
    private val settingsRepository: SettingsRepository? = null
) : ViewModel() {

    private val arabicCollator = Collator.getInstance(com.example.presentation.common.AppLocale.AR_DZ).apply {
        strength = Collator.PRIMARY
    }

    // تدفق شاشة اختيار النشاط
    val pickerUiState: StateFlow<AttendancePickerUiState> = combine(
        activityRepository.observeActivities(),
        observePointsSettingsUseCase()
    ) { activities, _ ->
        val eligible = activities.filter { act ->
            act.status != com.example.domain.activity.ActivityStatus.CANCELLED.key
        }.sortedByDescending { it.startDate }

        AttendancePickerUiState.Success(
            activities = eligible,
            recordedActivityIds = emptySet()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AttendancePickerUiState.Loading
    )

    // حالة معالج تسجيل الحضور (Wizard)
    private val _entryUiState = MutableStateFlow(AttendanceEntryUiState())
    val entryUiState: StateFlow<AttendanceEntryUiState> = _entryUiState.asStateFlow()

    // تتبع المبالغ المدخلة خلال الجلسة الحالية لكل شبل لحساب الإجمالي اللحظي
    private val _sessionPaidByScout = MutableStateFlow<Map<String, Int>>(emptyMap())

    // القيمة الحية المكتوبة حالياً في حقل "المبلغ المدفوع"
    private val _liveAmountInput = MutableStateFlow("")

    val totalSessionPaid: StateFlow<Int> = combine(
        _sessionPaidByScout,
        _liveAmountInput
    ) { paidMap, liveInput ->
        val savedSum = paidMap.values.sum()
        val liveValue = liveInput.toIntOrNull() ?: 0
        savedSum + liveValue
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val currentScoutTotalPaid: StateFlow<Int> = combine(
        _entryUiState,
        _sessionPaidByScout,
        _liveAmountInput
    ) { state, paidMap, liveInput ->
        val currentScoutId = state.currentScout?.id
        val savedForCurrentScout = if (currentScoutId != null) {
            paidMap[currentScoutId] ?: 0
        } else 0
        val liveValue = liveInput.toIntOrNull() ?: 0
        savedForCurrentScout + liveValue
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    private var isTransitioning = false
    private var activeSaveJob: Job? = null
    private val committedPrevDebtByScout = mutableMapOf<String, Int>()
    private val committedOverpaymentByScout = mutableMapOf<String, Int>()

    private fun normalizeDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                in '0'..'9' -> sb.append(ch)
                in '\u0660'..'\u0669' -> sb.append(('0'.code + (ch.code - '\u0660'.code)).toChar())
                in '\u06F0'..'\u06F9' -> sb.append(('0'.code + (ch.code - '\u06F0'.code)).toChar())
            }
        }
        return sb.toString()
    }

    /**
     * حساب الديون السابقة للشبل من كافة الأنشطة باستثناء النشاط الحالي
     */
    private suspend fun calculatePreviousDebt(scoutId: String, currentActivityId: String): Int {
        if (financeRepository == null) return 0
        return try {
            val subs = financeRepository.observeSubscriptionsForScout(scoutId).first()
            subs.filter { it.activityId != currentActivityId }.sumOf { it.debtAmount }
        } catch (_: Exception) {
            0
        }
    }

    /**
     * تحميل البيانات المالية الخاصة بالشبل المحدد وإعادة تعيين القيم المسددة والإدخال السريع
     */
    private suspend fun loadScoutFinanceData(scout: Scout, activity: Activity) {
        val existingStatus = _entryUiState.value.recordedStatuses[scout.id]
        val isTrainingMeeting = activity.activityType == ActivityType.TRAINING_MEETING ||
            activity.type == ActivityType.TRAINING_MEETING.key ||
            activity.type == "اجتماع تدريبي"

        val canPay = if (isTrainingMeeting) {
            existingStatus != null && (existingStatus == AttendanceStatus.PRESENT || existingStatus == AttendanceStatus.LATE || existingStatus == AttendanceStatus.VERY_LATE)
        } else {
            existingStatus == AttendanceStatus.PRESENT
        }

        val subRequired = if (existingStatus != null) {
            if (isTrainingMeeting) {
                if (financeRepository != null) {
                    val subStatus = SubscriptionStatus.fromKey(existingStatus.key)
                    financeRepository.getRequiredAmountFor(activity.id, subStatus)
                } else 0
            } else {
                if (existingStatus == AttendanceStatus.PRESENT) {
                    activity.customSubscriptionAmount ?: 0
                } else {
                    0
                }
            }
        } else 0
        val prevDebt = calculatePreviousDebt(scout.id, activity.id)

        // التحقق إن كان هناك دفع سابق مسجل لهذا الشبل في هذا النشاط
        val existingPaid = try {
            if (financeRepository != null) {
                val subs = financeRepository.observeSubscriptionsForActivity(activity.id).first()
                subs.find { it.scoutId == scout.id }?.paidAmount ?: 0
            } else 0
        } catch (_: Exception) { 0 }

        committedPrevDebtByScout.remove(scout.id)
        committedOverpaymentByScout.remove(scout.id)
        _liveAmountInput.value = ""
        _entryUiState.update {
            it.copy(
                selectedStatus = existingStatus,
                canPay = canPay,
                previousDebtRequired = prevDebt,
                todaySubRequired = subRequired,
                previousDebtPaid = 0,
                todaySubPaid = 0,
                totalPaidToday = 0,
                overpaymentCredit = 0,
                quickInputAmount = ""
            )
        }

        if (existingStatus != null && canPay && existingPaid > 0) {
            distributePayment(existingPaid)
            _sessionPaidByScout.update { currentMap ->
                if (scout.id in currentMap) currentMap else currentMap + (scout.id to existingPaid)
            }
        }
    }

    /**
     * تهيئة معالج الحضور للنشاط المحدد
     */
    fun initAttendanceEntry(activityId: String) {
        _sessionPaidByScout.value = emptyMap()
        _liveAmountInput.value = ""
        committedPrevDebtByScout.clear()
        committedOverpaymentByScout.clear()
        isTransitioning = false
        _entryUiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val activity = activityRepository.getActivityById(activityId)
                if (activity == null) {
                    _entryUiState.update { it.copy(isLoading = false, errorMessage = "النشاط غير موجود") }
                    return@launch
                }

                // جلب الأشبال النشطين فقط وترتيبهم أبجدياً (الاسم ثم اللقب)
                val scouts = scoutRepository.observeScouts().first()
                val sortedScouts = scouts.filter { it.isActive }.sortedWith { s1, s2 ->
                    val nameCompare = arabicCollator.compare(s1.name, s2.name)
                    if (nameCompare != 0) nameCompare else arabicCollator.compare(s1.surname, s2.surname)
                }

                // جلب سجلات الحضور السابقة إن وُجدت
                val existingRecords = getAttendanceForActivityUseCase(activityId).first()
                val existingMap = existingRecords.associate {
                    it.scoutId to it.attendanceStatus
                }

                val pointsSettings = observePointsSettingsUseCase().first()
                val isTrainingMeeting = activity.activityType == ActivityType.TRAINING_MEETING ||
                    activity.type == ActivityType.TRAINING_MEETING.key ||
                    activity.type == "اجتماع تدريبي"
                val collectSubs = activity.collectSubscriptions

                val initialSelectedStatus = sortedScouts.firstOrNull()?.let { existingMap[it.id] }
                val initialCanPay = if (isTrainingMeeting) {
                    initialSelectedStatus != null && (initialSelectedStatus == AttendanceStatus.PRESENT || initialSelectedStatus == AttendanceStatus.LATE || initialSelectedStatus == AttendanceStatus.VERY_LATE)
                } else {
                    initialSelectedStatus == AttendanceStatus.PRESENT
                }

                _entryUiState.value = AttendanceEntryUiState(
                    activity = activity,
                    scoutsQueue = sortedScouts,
                    currentIndex = 0,
                    recordedStatuses = existingMap,
                    pointsSettings = pointsSettings,
                    isFinanceSectionVisible = collectSubs,
                    isLoading = false,
                    selectedStatus = initialSelectedStatus,
                    canPay = initialCanPay,
                    todaySubRequired = 0
                )

                if (sortedScouts.isNotEmpty() && collectSubs) {
                    loadScoutFinanceData(sortedScouts[0], activity)
                }
            } catch (e: Exception) {
                _entryUiState.update { it.copy(isLoading = false, errorMessage = e.localizedMessage) }
            }
        }
    }

    /**
     * تسجيل حالة حضور الشبل الحالي وتحديث المبلغ المطلوب آلياً وإعادة توزيع المدفوعات
     */
    fun onSelectStatus(status: AttendanceStatus) {
        val currentScout = _entryUiState.value.currentScout ?: return
        val activity = _entryUiState.value.activity
        val isTrainingMeeting = activity?.let {
            it.activityType == ActivityType.TRAINING_MEETING ||
            it.type == ActivityType.TRAINING_MEETING.key ||
            it.type == "اجتماع تدريبي"
        } ?: true

        val canPay = if (isTrainingMeeting) {
            status == AttendanceStatus.PRESENT || status == AttendanceStatus.LATE || status == AttendanceStatus.VERY_LATE
        } else {
            status == AttendanceStatus.PRESENT
        }

        _sessionPaidByScout.update { it - currentScout.id }
        committedPrevDebtByScout.remove(currentScout.id)
        committedOverpaymentByScout.remove(currentScout.id)
        _liveAmountInput.value = ""
        _entryUiState.update { current ->
            val updated = current.recordedStatuses.toMutableMap()
            updated[currentScout.id] = status
            current.copy(
                recordedStatuses = updated,
                selectedStatus = status,
                canPay = canPay,
                todaySubPaid = 0,
                previousDebtPaid = 0,
                totalPaidToday = 0,
                overpaymentCredit = 0,
                quickInputAmount = ""
            )
        }

        if (activity != null && activity.collectSubscriptions) {
            if (isTrainingMeeting) {
                if (financeRepository != null) {
                    viewModelScope.launch {
                        val subStatus = SubscriptionStatus.fromKey(status.key)
                        val required = financeRepository.getRequiredAmountFor(activity.id, subStatus)
                        _entryUiState.update {
                            it.copy(
                                todaySubRequired = required,
                                canPay = canPay
                            )
                        }
                    }
                }
            } else {
                val required = if (status == AttendanceStatus.PRESENT) {
                    activity.customSubscriptionAmount ?: 0
                } else {
                    0
                }
                _entryUiState.update {
                    it.copy(
                        todaySubRequired = required,
                        canPay = canPay
                    )
                }
            }
        }
    }

    /**
     * النقر على حقل الدفع دون تحديد حالة الحضور أولاً
     */
    fun onPaymentFieldClickedWithoutStatus() {
        _entryUiState.update { it.copy(snackbarMessage = "error_select_status_first") }
    }

    fun onSnackbarDismissed() {
        _entryUiState.update { it.copy(snackbarMessage = null) }
    }

    /**
     * تحديث حقل الإدخال السريع المباشر وتحديث الإجمالي اللحظي للجلسة
     */
    fun onAmountInputChanged(text: String) {
        val sanitized = normalizeDigits(text)
        _liveAmountInput.value = sanitized
        _entryUiState.update { it.copy(quickInputAmount = sanitized) }
    }

    fun onQuickInputChanged(amount: String) = onAmountInputChanged(amount)

    /**
     * تطبيق القيمة المدخلة حالياً إلى حالة الشبل ومجموع الجلسة دون فقدان أي مبلغ مدفوع
     */
    private fun applyQuickInputToState(): Boolean {
        val state = _entryUiState.value
        if (!state.canPay) return false
        val amount = (_liveAmountInput.value.ifBlank { state.quickInputAmount }).toIntOrNull() ?: 0
        if (amount <= 0) {
            _liveAmountInput.value = ""
            _entryUiState.update { it.copy(quickInputAmount = "") }
            return false
        }
        val scout = state.currentScout ?: return false
        _sessionPaidByScout.update { currentMap ->
            currentMap + (scout.id to ((currentMap[scout.id] ?: 0) + amount))
        }
        val newTotal = state.totalPaidToday + amount
        _liveAmountInput.value = ""
        _entryUiState.update { it.copy(quickInputAmount = "") }
        distributePayment(newTotal)
        return true
    }

    /**
     * تطبيق الإدخال السريع (عند الضغط على ✅):
     * يضيف القيمة المدخلة إلى إجمالي الجلسة، يوزع الدفع، يحفظ السجل في قاعدة البيانات، ويفرغ الحقل
     */
    fun onApplyQuickInput() {
        val stateBefore = _entryUiState.value
        val scout = stateBefore.currentScout ?: return
        val activity = stateBefore.activity
        val currentStatus = stateBefore.recordedStatuses[scout.id]

        if (!applyQuickInputToState()) return

        if (activity != null && currentStatus != null) {
            val updatedState = _entryUiState.value
            val todayPaid = updatedState.todaySubPaid
            val prevPaid = updatedState.previousDebtPaid
            val todayReq = updatedState.todaySubRequired
            val credit = updatedState.overpaymentCredit
            val singleAttendance = buildAttendanceRecord(
                scout = scout,
                activity = activity,
                status = currentStatus,
                pointsSettings = updatedState.pointsSettings
            )
            val previousJob = activeSaveJob
            activeSaveJob = viewModelScope.launch {
                previousJob?.join()
                try {
                    attendanceRepository.saveAttendance(singleAttendance)
                } catch (e: Exception) {
                    Log.e("AttendanceVM", "Error saving attendance on quick apply for ${scout.fullName}: ${e.message}", e)
                }
                saveFinancePayment(scout, activity, currentStatus, todayPaid, prevPaid, todayReq, credit)
            }
        }
    }

    /**
     * تطبيق المبلغ المكتوب في حقل الإدخال السريع تلقائياً إذا كان أكبر من 0
     */
    private fun applyPendingQuickInputIfNeeded() {
        val state = _entryUiState.value
        if (!state.canPay) return
        val toAdd = (_liveAmountInput.value.ifBlank { state.quickInputAmount }).toIntOrNull() ?: 0
        if (toAdd <= 0) return
        applyQuickInputToState()
    }

    /**
     * تعديل الإجمالي المدفوع يدوياً (للحقل القابل للتعديل)
     */
    fun onTotalPaidChanged(newTotal: String) {
        val state = _entryUiState.value
        if (!state.canPay) return
        val scout = state.currentScout
        val oldTotal = state.totalPaidToday
        val parsed = newTotal.filter { it.isDigit() }.toIntOrNull() ?: 0
        distributePayment(parsed.coerceAtLeast(0))
        val delta = _entryUiState.value.totalPaidToday - oldTotal
        if (scout != null && delta != 0) {
            _sessionPaidByScout.update { currentMap ->
                val updated = ((currentMap[scout.id] ?: 0) + delta).coerceAtLeast(0)
                if (updated == 0) currentMap - scout.id else currentMap + (scout.id to updated)
            }
        }
    }

    /**
     * دالة التوزيع المالي الآلية:
     * - يسدد اشتراك اليوم أولاً حتى اكتماله
     * - يوجه الفائض لتسديد الديون السابقة
     * - يحفظ الفائض المتبقي كرصيد مقدم للشبل (overpaymentCredit)
     */
    private fun distributePayment(amount: Int) {
        val state = _entryUiState.value
        val sanitizedAmount = amount.coerceAtLeast(0)

        val todayPaid = minOf(sanitizedAmount, state.todaySubRequired)
        val remaining = sanitizedAmount - todayPaid
        val prevPaid = minOf(remaining, state.previousDebtRequired)
        val credit = remaining - prevPaid

        _entryUiState.update {
            it.copy(
                totalPaidToday = sanitizedAmount,
                todaySubPaid = todayPaid,
                previousDebtPaid = prevPaid,
                overpaymentCredit = credit
            )
        }
    }

    /**
     * دوال الملاءمة السريعة المتوافقة مع الواجهة الحالية
     */
    fun onPaidAmountChanged(amount: String) = onTotalPaidChanged(amount)

    fun incrementPaidAmount(step: Int = 5) {
        val current = _entryUiState.value.totalPaidToday
        distributePayment(current + step)
    }

    fun decrementPaidAmount(step: Int = 5) {
        val current = _entryUiState.value.totalPaidToday
        distributePayment((current - step).coerceAtLeast(0))
    }

    fun setPaidAmountToFull() {
        val total = _entryUiState.value.todaySubRequired + _entryUiState.value.previousDebtRequired
        distributePayment(total)
    }

    private fun buildAttendanceRecord(
        scout: Scout,
        activity: Activity,
        status: AttendanceStatus,
        pointsSettings: AttendancePointsSettings
    ): Attendance {
        val points = if (activity.activityType.usesPointsSystem()) {
            pointsSettings.getPointsForStatus(status)
        } else 0

        return Attendance(
            activityId = activity.id,
            scoutId = scout.id,
            scoutFullName = scout.fullName,
            status = status.key,
            points = points,
            activityDate = activity.startDate
        )
    }

    /**
     * حفظ المعاملات والمدفوعات المالية للشبل في النشاط وقاعدة البيانات
     */
    private suspend fun saveFinancePayment(
        scout: Scout,
        activity: Activity,
        currentStatus: AttendanceStatus,
        todayPaid: Int = _entryUiState.value.todaySubPaid,
        prevPaid: Int = _entryUiState.value.previousDebtPaid,
        todayReq: Int = _entryUiState.value.todaySubRequired,
        overpaymentCredit: Int = _entryUiState.value.overpaymentCredit
    ) {
        if (financeRepository == null || !_entryUiState.value.isFinanceSectionVisible) return
        try {
            val subStatus = SubscriptionStatus.fromKey(currentStatus.key)
            val todaySub = FinanceSubscription(
                id = "${activity.id}_${scout.id}",
                scoutId = scout.id,
                scoutFullName = scout.fullName,
                activityId = activity.id,
                activityTitle = activity.title.ifBlank { "نشاط كشفي" },
                activityDate = activity.startDate,
                attendanceStatus = subStatus,
                requiredAmount = todayReq,
                paidAmount = todayPaid,
                debtAmount = (todayReq - todayPaid).coerceAtLeast(0),
                updatedAt = System.currentTimeMillis()
            )
            financeRepository.upsertSubscription(todaySub)

            if (prevPaid > 0) {
                val alreadyCommitted = committedPrevDebtByScout[scout.id] ?: 0
                val deltaPrev = (prevPaid - alreadyCommitted).coerceAtLeast(0)
                if (deltaPrev > 0) {
                    val debtResult = financeRepository.payScoutDebts(scout.id, deltaPrev)
                    if (debtResult.isSuccess) {
                        committedPrevDebtByScout[scout.id] = alreadyCommitted + deltaPrev
                    }
                }
                val debtTx = FinanceTransaction(
                    id = "debt_pay_${activity.id}_${scout.id}",
                    type = FinanceTransactionType.DEBT_PAYMENT,
                    amount = prevPaid,
                    date = activity.startDate,
                    description = "سداد ديون سابقة",
                    source = scout.fullName,
                    scoutId = scout.id,
                    relatedActivityId = activity.id
                )
                financeRepository.upsertTransaction(debtTx)
            }

            if (overpaymentCredit > 0) {
                val alreadyCommittedCredit = committedOverpaymentByScout[scout.id] ?: 0
                val deltaCredit = (overpaymentCredit - alreadyCommittedCredit).coerceAtLeast(0)
                if (deltaCredit > 0) {
                    val creditResult = scoutRepository.addCreditToScout(scout.id, deltaCredit)
                    if (creditResult.isSuccess) {
                        committedOverpaymentByScout[scout.id] = alreadyCommittedCredit + deltaCredit
                    }
                }
                val advanceTx = FinanceTransaction(
                    id = "advance_pay_${activity.id}_${scout.id}",
                    type = FinanceTransactionType.ADVANCE_PAYMENT,
                    amount = overpaymentCredit,
                    date = activity.startDate,
                    description = "دفعة مقدمة (رصيد)",
                    source = scout.fullName,
                    scoutId = scout.id,
                    relatedActivityId = activity.id
                )
                financeRepository.upsertTransaction(advanceTx)
            }
        } catch (e: Exception) {
            Log.e("AttendanceVM", "Error saving finance payment for ${scout.fullName}: ${e.message}", e)
        } finally {
            _entryUiState.update { it.copy(overpaymentCredit = 0) }
        }
    }

    /**
     * الانتقال للشبل التالي وحفظ سجل الشبل الحالي فوراً
     */
    fun onNext(onSkipped: () -> Unit) {
        if (isTransitioning) return
        val state = _entryUiState.value
        val scout = state.currentScout ?: return
        val activity = state.activity ?: return
        val currentStatus = state.recordedStatuses[scout.id]

        if (currentStatus == null) {
            val updatedQueue = state.scoutsQueue.toMutableList()
            updatedQueue.removeAt(state.currentIndex)
            updatedQueue.add(scout)

            val nextScout = updatedQueue.getOrNull(state.currentIndex)
            _liveAmountInput.value = ""
            _entryUiState.update {
                it.copy(
                    scoutsQueue = updatedQueue,
                    selectedStatus = nextScout?.let { s -> it.recordedStatuses[s.id] },
                    canPay = false,
                    todaySubRequired = 0,
                    todaySubPaid = 0,
                    previousDebtPaid = 0,
                    totalPaidToday = 0,
                    overpaymentCredit = 0,
                    quickInputAmount = ""
                )
            }
            onSkipped()
            if (nextScout != null && activity.collectSubscriptions) {
                viewModelScope.launch {
                    isTransitioning = true
                    try {
                        loadScoutFinanceData(nextScout, activity)
                    } finally {
                        isTransitioning = false
                    }
                }
            }
            return
        }

        // تطبيق أي مبلغ مكتوب في حقل الإدخال السريع قبل الحفظ والانتقال
        applyPendingQuickInputIfNeeded()
        val updatedState = _entryUiState.value
        val todayPaid = updatedState.todaySubPaid
        val prevPaid = updatedState.previousDebtPaid
        val todayReq = updatedState.todaySubRequired
        val credit = updatedState.overpaymentCredit
        val singleAttendance = buildAttendanceRecord(scout, activity, currentStatus, updatedState.pointsSettings)

        viewModelScope.launch {
            isTransitioning = true
            try {
                activeSaveJob?.join()
                // 1. حفظ الحضور فوراً
                try {
                    attendanceRepository.saveAttendance(singleAttendance)
                } catch (e: Exception) {
                    Log.e("AttendanceVM", "Error saving attendance for ${scout.fullName}: ${e.message}", e)
                }

                // 2. حفظ المدفوعات المالية
                saveFinancePayment(scout, activity, currentStatus, todayPaid, prevPaid, todayReq, credit)

                // 3. الانتقال للشبل التالي وتحميل بياناته المالية
                _liveAmountInput.value = ""
                if (updatedState.currentIndex < updatedState.scoutsQueue.size - 1) {
                    val nextIndex = updatedState.currentIndex + 1
                    val nextScout = updatedState.scoutsQueue[nextIndex]
                    _entryUiState.update {
                        it.copy(
                            currentIndex = nextIndex,
                            selectedStatus = it.recordedStatuses[nextScout.id],
                            canPay = false,
                            todaySubRequired = 0,
                            todaySubPaid = 0,
                            previousDebtPaid = 0,
                            totalPaidToday = 0,
                            overpaymentCredit = 0,
                            quickInputAmount = ""
                        )
                    }
                    if (activity.collectSubscriptions) {
                        loadScoutFinanceData(nextScout, activity)
                    }
                } else {
                    _entryUiState.update {
                        it.copy(
                            selectedStatus = null,
                            canPay = false,
                            todaySubRequired = 0,
                            todaySubPaid = 0,
                            previousDebtPaid = 0,
                            totalPaidToday = 0,
                            overpaymentCredit = 0,
                            quickInputAmount = ""
                        )
                    }
                }
            } finally {
                isTransitioning = false
            }
        }
    }

    /**
     * العودة للشبل السابق مع حفظ حالة الشبل الحالي وأي مبلغ مدخل
     */
    fun onPrevious() {
        if (isTransitioning) return
        val state = _entryUiState.value
        if (state.currentIndex <= 0) return

        val prevIndex = state.currentIndex - 1
        val prevScout = state.scoutsQueue.getOrNull(prevIndex)
        val currentScout = state.currentScout
        val activity = state.activity
        val currentStatus = currentScout?.let { state.recordedStatuses[it.id] }

        if (currentScout != null && activity != null && currentStatus != null) {
            applyPendingQuickInputIfNeeded()
            val updatedState = _entryUiState.value
            val todayPaid = updatedState.todaySubPaid
            val prevPaid = updatedState.previousDebtPaid
            val todayReq = updatedState.todaySubRequired
            val credit = updatedState.overpaymentCredit
            val singleAttendance = buildAttendanceRecord(currentScout, activity, currentStatus, updatedState.pointsSettings)

            viewModelScope.launch {
                isTransitioning = true
                try {
                    activeSaveJob?.join()
                    try {
                        attendanceRepository.saveAttendance(singleAttendance)
                    } catch (e: Exception) {
                        Log.e("AttendanceVM", "Error saving attendance before going previous for ${currentScout.fullName}: ${e.message}", e)
                    }

                    saveFinancePayment(currentScout, activity, currentStatus, todayPaid, prevPaid, todayReq, credit)

                    _liveAmountInput.value = ""
                    _entryUiState.update {
                        it.copy(
                            currentIndex = prevIndex,
                            selectedStatus = prevScout?.let { s -> it.recordedStatuses[s.id] },
                            canPay = false,
                            todaySubRequired = 0,
                            todaySubPaid = 0,
                            previousDebtPaid = 0,
                            totalPaidToday = 0,
                            overpaymentCredit = 0,
                            quickInputAmount = ""
                        )
                    }
                    if (prevScout != null && activity.collectSubscriptions) {
                        loadScoutFinanceData(prevScout, activity)
                    }
                } finally {
                    isTransitioning = false
                }
            }
        } else {
            _liveAmountInput.value = ""
            _entryUiState.update {
                it.copy(
                    currentIndex = prevIndex,
                    selectedStatus = prevScout?.let { s -> it.recordedStatuses[s.id] },
                    canPay = false,
                    todaySubRequired = 0,
                    todaySubPaid = 0,
                    previousDebtPaid = 0,
                    totalPaidToday = 0,
                    overpaymentCredit = 0,
                    quickInputAmount = ""
                )
            }
            if (prevScout != null && activity != null && activity.collectSubscriptions) {
                viewModelScope.launch {
                    isTransitioning = true
                    try {
                        loadScoutFinanceData(prevScout, activity)
                    } finally {
                        isTransitioning = false
                    }
                }
            }
        }
    }

    /**
     * القفز مباشرة إلى شبل محدد عبر البحث (مع حفظ الشبل الحالي إن أمكن)
     */
    fun jumpToScout(scoutId: String, saveCurrent: Boolean = true) {
        if (isTransitioning) return
        val state = _entryUiState.value
        val targetIndex = state.scoutsQueue.indexOfFirst { it.id == scoutId }
        if (targetIndex < 0 || targetIndex == state.currentIndex) return

        val targetScout = state.scoutsQueue[targetIndex]
        val currentScout = state.currentScout
        val activity = state.activity
        val currentStatus = currentScout?.let { state.recordedStatuses[it.id] }

        if (saveCurrent && currentScout != null && activity != null && currentStatus != null) {
            applyPendingQuickInputIfNeeded()
            val updatedState = _entryUiState.value
            val todayPaid = updatedState.todaySubPaid
            val prevPaid = updatedState.previousDebtPaid
            val todayReq = updatedState.todaySubRequired
            val credit = updatedState.overpaymentCredit
            val singleAttendance = buildAttendanceRecord(currentScout, activity, currentStatus, updatedState.pointsSettings)

            viewModelScope.launch {
                isTransitioning = true
                try {
                    activeSaveJob?.join()
                    try {
                        attendanceRepository.saveAttendance(singleAttendance)
                    } catch (e: Exception) {
                        Log.e("AttendanceVM", "Error saving attendance before jump for ${currentScout.fullName}: ${e.message}", e)
                    }

                    saveFinancePayment(currentScout, activity, currentStatus, todayPaid, prevPaid, todayReq, credit)

                    _liveAmountInput.value = ""
                    _entryUiState.update {
                        it.copy(
                            currentIndex = targetIndex,
                            selectedStatus = it.recordedStatuses[targetScout.id],
                            canPay = false,
                            todaySubRequired = 0,
                            todaySubPaid = 0,
                            previousDebtPaid = 0,
                            totalPaidToday = 0,
                            overpaymentCredit = 0,
                            quickInputAmount = ""
                        )
                    }
                    if (activity.collectSubscriptions) {
                        loadScoutFinanceData(targetScout, activity)
                    }
                } finally {
                    isTransitioning = false
                }
            }
        } else {
            _liveAmountInput.value = ""
            _entryUiState.update {
                it.copy(
                    currentIndex = targetIndex,
                    selectedStatus = it.recordedStatuses[targetScout.id],
                    canPay = false,
                    todaySubRequired = 0,
                    todaySubPaid = 0,
                    previousDebtPaid = 0,
                    totalPaidToday = 0,
                    overpaymentCredit = 0,
                    quickInputAmount = ""
                )
            }
            if (activity != null && activity.collectSubscriptions) {
                viewModelScope.launch {
                    isTransitioning = true
                    try {
                        loadScoutFinanceData(targetScout, activity)
                    } finally {
                        isTransitioning = false
                    }
                }
            }
        }
    }

    /**
     * إنهاء تسجيل الحضور وحفظ الشبل الأخير
     */
    fun saveAttendance(onSuccess: () -> Unit) {
        val state = _entryUiState.value
        val activity = state.activity ?: return
        val currentScout = state.currentScout
        val currentStatus = currentScout?.let { state.recordedStatuses[it.id] }

        if (currentScout != null && currentStatus != null) {
            applyPendingQuickInputIfNeeded()
        }
        val updatedState = _entryUiState.value
        val todayPaid = updatedState.todaySubPaid
        val prevPaid = updatedState.previousDebtPaid
        val todayReq = updatedState.todaySubRequired
        val credit = updatedState.overpaymentCredit

        _entryUiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            activeSaveJob?.join()
            if (currentScout != null && currentStatus != null) {
                val singleAttendance = buildAttendanceRecord(
                    currentScout,
                    activity,
                    currentStatus,
                    updatedState.pointsSettings
                )
                try {
                    attendanceRepository.saveAttendance(singleAttendance)
                } catch (e: Exception) {
                    Log.e("AttendanceVM", "Error saving last scout attendance: ${e.message}", e)
                }

                saveFinancePayment(currentScout, activity, currentStatus, todayPaid, prevPaid, todayReq, credit)
            }

            _liveAmountInput.value = ""
            _entryUiState.update {
                it.copy(
                    selectedStatus = null,
                    canPay = false,
                    todaySubRequired = 0,
                    todaySubPaid = 0,
                    previousDebtPaid = 0,
                    totalPaidToday = 0,
                    overpaymentCredit = 0,
                    quickInputAmount = "",
                    isSaving = false,
                    isFinished = true
                )
            }
            onSuccess()
        }
    }

    fun onNextClicked(onSkipped: () -> Unit = {}) = onNext(onSkipped)
    fun onFinishClicked(onSuccess: () -> Unit = {}) = saveAttendance(onSuccess)

    class Factory(
        private val activityRepository: ActivityRepository,
        private val scoutRepository: ScoutRepository,
        private val attendanceRepository: AttendanceRepository,
        private val getAttendanceForActivityUseCase: GetAttendanceForActivityUseCase,
        private val saveAttendanceBatchUseCase: SaveAttendanceBatchUseCase,
        private val observePointsSettingsUseCase: ObservePointsSettingsUseCase,
        private val financeRepository: FinanceRepository? = null,
        private val settingsRepository: SettingsRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AttendanceViewModel(
                activityRepository,
                scoutRepository,
                attendanceRepository,
                getAttendanceForActivityUseCase,
                saveAttendanceBatchUseCase,
                observePointsSettingsUseCase,
                financeRepository,
                settingsRepository
            ) as T
        }
    }
}
