package com.example.presentation.attendance

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.common.WriteFailureReporter
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
import com.example.domain.settings.CustomTerms
import com.example.domain.settings.SettingsRepository
import com.example.presentation.reports.PdfExporter
import com.google.firebase.firestore.FirebaseFirestoreException
import java.lang.ref.WeakReference
import java.text.Collator
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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
    private val settingsRepository: SettingsRepository? = null,
    private val serverConfirmationTimeoutMs: Long = DEFAULT_SERVER_CONFIRMATION_TIMEOUT_MS,
    private val writeFailureReporter: WriteFailureReporter? = null
) : ViewModel() {

    companion object {
        const val DEFAULT_SERVER_CONFIRMATION_TIMEOUT_MS = 30_000L
    }

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

    private sealed interface SaveQueueItem {
        data class PersistScout(
            val scout: Scout,
            val activity: Activity,
            val status: AttendanceStatus,
            val pointsSettings: AttendancePointsSettings,
            val syncFinance: Boolean,
            val todayPaid: Int,
            val prevPaid: Int,
            val todayReq: Int,
            val credit: Int,
            val syncAttendanceSubscription: Boolean = !syncFinance,
            val localResultDeferred: CompletableDeferred<Result<Unit>>? = null,
            val serverConfirmations: CopyOnWriteArrayList<CompletableDeferred<Result<Unit>>> = CopyOnWriteArrayList()
        ) : SaveQueueItem

        data class FlushBarrier(
            val completion: CompletableDeferred<ConfirmationOutcome>
        ) : SaveQueueItem
    }

    private sealed interface ConfirmationOutcome {
        data object Confirmed : ConfirmationOutcome
        data object PendingSync : ConfirmationOutcome
        data class Failed(val error: Throwable) : ConfirmationOutcome
    }

    private data class FinanceSnapshot(
        val status: AttendanceStatus,
        val todayPaid: Int,
        val prevPaid: Int,
        val todayReq: Int,
        val credit: Int
    )

    private data class ScoutFinanceDraft(
        val prevDebtRequired: Int,
        val todaySubRequired: Int,
        val todaySubPaid: Int,
        val prevDebtPaid: Int,
        val totalPaid: Int,
        val overpaymentCredit: Int
    )

    private val saveQueue = Channel<SaveQueueItem>(Channel.UNLIMITED)
    private val pendingSavesCounter = AtomicInteger(0)
    private val pendingLocalDispatchesCounter = AtomicInteger(0)
    private val pendingServerConfirmationsCounter = AtomicInteger(0)
    @Volatile
    private var sessionHadPendingSync = false

    private fun scoutSessionKey(activityId: String, scoutId: String): String = "${activityId}_${scoutId}"

    private val persistedAttendanceStatus = ConcurrentHashMap<String, AttendanceStatus>()
    private val persistedFinanceSignature = ConcurrentHashMap<String, FinanceSnapshot>()
    private val scoutFinanceCache = mutableMapOf<String, ScoutFinanceDraft>()
    private val cachedRequiredAmounts = mutableMapOf<SubscriptionStatus, Int>()
    private val scoutsWithReversibleTxs: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val failedSaveTasks = ConcurrentHashMap<String, SaveQueueItem.PersistScout>()
    private data class RollbackTxStepKey(
        val scoutKey: String,
        val snapshot: FinanceSnapshot?,
        val txId: String
    )

    private val completedReverseStep = ConcurrentHashMap<String, FinanceSnapshot>()
    private val completedUpsertSubStep = ConcurrentHashMap<String, FinanceSnapshot>()
    private val completedPayDebtStep = ConcurrentHashMap<String, FinanceSnapshot>()
    private val completedAddCreditStep = ConcurrentHashMap<String, FinanceSnapshot>()
    private val completedRollbackTxSteps: MutableSet<RollbackTxStepKey> = ConcurrentHashMap.newKeySet()
    private val perScoutLocalJobs = ConcurrentHashMap<String, Job>()
    private var transactionsPreloaded = false
    private var currentSessionActivityId: String? = null

    init {
        startSaveWorker()
    }

    private fun isRecoverableOfflineOrTransient(error: Throwable?): Boolean {
        val fsCode = (error as? FirebaseFirestoreException)?.code
        return fsCode == FirebaseFirestoreException.Code.UNAVAILABLE ||
            fsCode == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED
    }

    private fun formatSaveFailureMessage(scout: Scout, error: Throwable?): String {
        val fsCode = (error as? FirebaseFirestoreException)?.code
        return when {
            fsCode == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "لا تملك صلاحية حفظ بيانات ${scout.fullName} في السحابة."
            !error?.message.isNullOrBlank() ->
                "فشل حفظ بيانات ${scout.fullName}: ${error!!.message}"
            else ->
                "فشل حفظ بيانات ${scout.fullName}. يرجى إعادة المحاولة."
        }
    }

    private fun recordSaveFailure(
        item: SaveQueueItem.PersistScout,
        error: Throwable?,
        description: String = "تعذر حفظ بيانات ${item.scout.fullName}"
    ) {
        if (error != null) {
            writeFailureReporter?.report(description, error)
        }
        val key = scoutSessionKey(item.activity.id, item.scout.id)
        failedSaveTasks[key] = item
        persistedAttendanceStatus.remove(key)
        persistedFinanceSignature.remove(key)
        val msg = formatSaveFailureMessage(item.scout, error)
        _entryUiState.update {
            it.copy(
                failedSaveCount = failedSaveTasks.size,
                failedSaveError = msg,
                snackbarMessage = msg
            )
        }
    }

    private fun makeAsyncFailureHandler(
        item: SaveQueueItem.PersistScout,
        description: String,
        onNonTransient: (() -> Unit)? = null
    ): (Throwable) -> Unit {
        val reporter = writeFailureReporter
        val vmRef = WeakReference(this)
        return { error ->
            // التبليغ المباشر لـ WriteFailureReporter بدون الاعتماد على viewModelScope أو بقاء ViewModel حياً
            reporter?.report(description, error)
            if (!isRecoverableOfflineOrTransient(error)) {
                onNonTransient?.invoke()
            }
            vmRef.get()?.onBackgroundWriteFailed(
                item = item,
                error = error,
                description = description,
                alreadyReported = true
            )
        }
    }

    private fun onBackgroundWriteFailed(
        item: SaveQueueItem.PersistScout,
        error: Throwable,
        description: String = "تعذر حفظ بيانات ${item.scout.fullName}",
        alreadyReported: Boolean = false
    ) {
        if (!alreadyReported) {
            writeFailureReporter?.report(description, error)
        }
        if (isRecoverableOfflineOrTransient(error)) {
            Log.w("AttendanceVM", "Recoverable async write warning for ${item.scout.fullName}: ${error.message}")
            return
        }
        Log.e("AttendanceVM", "Async write failure for ${item.scout.fullName}: ${error.message}", error)
        viewModelScope.launch {
            recordSaveFailure(item, error, description)
        }
    }

    fun retryFailedSaves() {
        val itemsToRetry = failedSaveTasks.values.toList()
        if (itemsToRetry.isEmpty()) {
            _entryUiState.update { it.copy(failedSaveCount = 0, failedSaveError = null) }
            return
        }
        failedSaveTasks.clear()
        _entryUiState.update { it.copy(failedSaveCount = 0, failedSaveError = null) }
        itemsToRetry.forEach { enqueueScoutSave(it) }
    }

    fun dismissFailedSaveBanner() {
        _entryUiState.update { it.copy(failedSaveError = null) }
    }

    private fun trackServerConfirmation(
        deferred: CompletableDeferred<Result<Unit>>,
        collectServerConfirmation: ((CompletableDeferred<Result<Unit>>) -> Unit)?
    ) {
        val count = pendingServerConfirmationsCounter.incrementAndGet()
        _entryUiState.update { it.copy(pendingServerConfirmationCount = count) }
        deferred.invokeOnCompletion {
            val remaining = pendingServerConfirmationsCounter.decrementAndGet().coerceAtLeast(0)
            _entryUiState.update { it.copy(pendingServerConfirmationCount = remaining) }
        }
        collectServerConfirmation?.invoke(deferred)
    }

    private suspend fun awaitServerConfirmations(
        confirmations: List<CompletableDeferred<Result<Unit>>>,
        scoutName: String
    ): ConfirmationOutcome {
        if (confirmations.isEmpty()) return ConfirmationOutcome.Confirmed
        val confirmResult = withTimeoutOrNull(serverConfirmationTimeoutMs) {
            for (deferred in confirmations) {
                val res = deferred.await()
                if (res.isFailure) return@withTimeoutOrNull res
            }
            Result.success(Unit)
        }
        if (confirmResult == null) {
            Log.w(
                "AttendanceVM",
                "Timed out after ${serverConfirmationTimeoutMs}ms awaiting server confirmation for $scoutName; local cache write preserved."
            )
            return ConfirmationOutcome.PendingSync
        }
        if (confirmResult.isFailure) {
            val err = confirmResult.exceptionOrNull()
            if (isRecoverableOfflineOrTransient(err)) {
                Log.w(
                    "AttendanceVM",
                    "Weak/offline connection confirming $scoutName; local cache write preserved: ${err?.message}"
                )
                return ConfirmationOutcome.PendingSync
            }
            return ConfirmationOutcome.Failed(err ?: Exception("فشل تأكيد الحفظ للشبل $scoutName"))
        }
        return ConfirmationOutcome.Confirmed
    }

    private fun startSaveWorker() {
        viewModelScope.launch {
            for (item in saveQueue) {
                when (item) {
                    is SaveQueueItem.PersistScout -> {
                        val key = scoutSessionKey(item.activity.id, item.scout.id)
                        try {
                            var activeItem: SaveQueueItem.PersistScout = item
                            var localResult = item.localResultDeferred?.await()
                                ?: run {
                                    val retryConfirmations = CopyOnWriteArrayList<CompletableDeferred<Result<Unit>>>()
                                    val fallbackItem = item.copy(serverConfirmations = retryConfirmations)
                                    activeItem = fallbackItem
                                    executePersistScout(fallbackItem) { deferred ->
                                        retryConfirmations.add(deferred)
                                    }
                                }

                            if (localResult.isFailure || (failedSaveTasks.containsKey(key) && activeItem.serverConfirmations.isEmpty())) {
                                val retryConfirmations = CopyOnWriteArrayList<CompletableDeferred<Result<Unit>>>()
                                val retryItem = item.copy(serverConfirmations = retryConfirmations)
                                activeItem = retryItem
                                localResult = executePersistScout(retryItem) { deferred ->
                                    retryConfirmations.add(deferred)
                                }
                            }

                            if (localResult.isSuccess) {
                                when (
                                    val confirmOutcome = awaitServerConfirmations(
                                        confirmations = activeItem.serverConfirmations.toList(),
                                        scoutName = item.scout.fullName
                                    )
                                ) {
                                    ConfirmationOutcome.Confirmed -> {
                                        failedSaveTasks.remove(key)
                                        _entryUiState.update { state ->
                                            state.copy(
                                                failedSaveCount = failedSaveTasks.size,
                                                failedSaveError = if (failedSaveTasks.isEmpty()) null else state.failedSaveError
                                            )
                                        }
                                    }
                                    ConfirmationOutcome.PendingSync -> {
                                        sessionHadPendingSync = true
                                        failedSaveTasks.remove(key)
                                        _entryUiState.update { state ->
                                            state.copy(
                                                failedSaveCount = failedSaveTasks.size,
                                                failedSaveError = if (failedSaveTasks.isEmpty()) null else state.failedSaveError
                                            )
                                        }
                                    }
                                    is ConfirmationOutcome.Failed -> {
                                        recordSaveFailure(activeItem, confirmOutcome.error)
                                    }
                                }
                            } else {
                                recordSaveFailure(activeItem, localResult.exceptionOrNull())
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.e("AttendanceVM", "Unexpected error saving ${item.scout.fullName}: ${e.message}", e)
                            recordSaveFailure(item, e)
                        } finally {
                            val remaining = pendingSavesCounter.decrementAndGet().coerceAtLeast(0)
                            _entryUiState.update { state ->
                                state.copy(
                                    pendingSyncCount = remaining,
                                    isSaving = remaining > 0 || state.isFinishing
                                )
                            }
                        }
                    }
                    is SaveQueueItem.FlushBarrier -> {
                        var flushError: Throwable? = null
                        var anyPending = sessionHadPendingSync || pendingServerConfirmationsCounter.get() > 0
                        if (failedSaveTasks.isNotEmpty()) {
                            for ((key, failedItem) in failedSaveTasks.entries.toList()) {
                                try {
                                    val retryConfirmations = CopyOnWriteArrayList<CompletableDeferred<Result<Unit>>>()
                                    val retryResult = executePersistScout(failedItem) { deferred ->
                                        retryConfirmations.add(deferred)
                                    }
                                    if (retryResult.isSuccess) {
                                        when (
                                            val confirmOutcome = awaitServerConfirmations(
                                                confirmations = retryConfirmations.toList(),
                                                scoutName = failedItem.scout.fullName
                                            )
                                        ) {
                                            ConfirmationOutcome.Confirmed -> {
                                                failedSaveTasks.remove(key)
                                                _entryUiState.update { state ->
                                                    state.copy(
                                                        failedSaveCount = failedSaveTasks.size,
                                                        failedSaveError = if (failedSaveTasks.isEmpty()) null else state.failedSaveError
                                                    )
                                                }
                                            }
                                            ConfirmationOutcome.PendingSync -> {
                                                sessionHadPendingSync = true
                                                anyPending = true
                                                failedSaveTasks.remove(key)
                                                _entryUiState.update { state ->
                                                    state.copy(
                                                        failedSaveCount = failedSaveTasks.size,
                                                        failedSaveError = if (failedSaveTasks.isEmpty()) null else state.failedSaveError
                                                    )
                                                }
                                            }
                                            is ConfirmationOutcome.Failed -> {
                                                flushError = confirmOutcome.error
                                                recordSaveFailure(failedItem, flushError)
                                            }
                                        }
                                    } else {
                                        flushError = retryResult.exceptionOrNull()
                                        recordSaveFailure(failedItem, flushError)
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    flushError = e
                                    recordSaveFailure(failedItem, e)
                                }
                            }
                        }
                        if (flushError != null) {
                            item.completion.complete(ConfirmationOutcome.Failed(flushError))
                        } else if (anyPending || sessionHadPendingSync || pendingServerConfirmationsCounter.get() > 0) {
                            item.completion.complete(ConfirmationOutcome.PendingSync)
                        } else {
                            item.completion.complete(ConfirmationOutcome.Confirmed)
                        }
                    }
                }
            }
        }
    }

    private fun enqueueScoutSave(item: SaveQueueItem.PersistScout) {
        val count = pendingSavesCounter.incrementAndGet()
        val localCount = pendingLocalDispatchesCounter.incrementAndGet()
        _entryUiState.update {
            it.copy(
                isSaving = true,
                pendingSyncCount = count,
                localWritesDispatched = localCount == 0
            )
        }

        val key = scoutSessionKey(item.activity.id, item.scout.id)
        val localResultDeferred = CompletableDeferred<Result<Unit>>()
        val serverConfirmations = CopyOnWriteArrayList<CompletableDeferred<Result<Unit>>>()
        val previousScoutJob = perScoutLocalJobs[key]

        val localJob = viewModelScope.launch {
            try {
                previousScoutJob?.join()
                val result = executePersistScout(
                    item = item,
                    collectServerConfirmation = { deferred ->
                        serverConfirmations.add(deferred)
                    }
                )
                localResultDeferred.complete(result)
            } catch (e: CancellationException) {
                localResultDeferred.cancel(e)
                throw e
            } catch (e: Exception) {
                localResultDeferred.complete(Result.failure(e))
            } finally {
                val remainingLocal = pendingLocalDispatchesCounter.decrementAndGet().coerceAtLeast(0)
                _entryUiState.update { it.copy(localWritesDispatched = remainingLocal == 0) }
            }
        }
        perScoutLocalJobs[key] = localJob

        val queuedItem = item.copy(
            localResultDeferred = localResultDeferred,
            serverConfirmations = serverConfirmations
        )
        val sendResult = saveQueue.trySend(queuedItem)
        if (sendResult.isFailure) {
            val remaining = pendingSavesCounter.decrementAndGet().coerceAtLeast(0)
            _entryUiState.update { state ->
                state.copy(
                    pendingSyncCount = remaining,
                    isSaving = remaining > 0 || state.isFinishing
                )
            }
        }
    }

    private suspend fun executePersistScout(
        item: SaveQueueItem.PersistScout,
        collectServerConfirmation: ((CompletableDeferred<Result<Unit>>) -> Unit)? = null
    ): Result<Unit> {
        val key = scoutSessionKey(item.activity.id, item.scout.id)
        var attendanceFailed = false
        var attendanceError: Throwable? = null
        if (persistedAttendanceStatus[key] != item.status) {
            val singleAttendance = buildAttendanceRecord(
                scout = item.scout,
                activity = item.activity,
                status = item.status,
                pointsSettings = item.pointsSettings
            )
            val attDeferred = CompletableDeferred<Result<Unit>>()
            trackServerConfirmation(attDeferred, collectServerConfirmation)
            val attFailureHandler = makeAsyncFailureHandler(
                item = item,
                description = "تعذر حفظ حضور الشبل ${item.scout.fullName}"
            )
            try {
                // عند تفعيل التحصيل المالي يتكفل saveFinancePayment بحفظ الاشتراك مرة واحدة لتفادي التكرار
                val attResult = attendanceRepository.saveAttendance(
                    attendance = singleAttendance,
                    syncSubscription = item.syncAttendanceSubscription,
                    awaitServer = false,
                    onAsyncFailure = attFailureHandler,
                    onServerResult = { res -> attDeferred.complete(res) }
                )
                if (!attDeferred.isCompleted && attResult.isFailure) {
                    attDeferred.complete(attResult)
                }
                if (attResult.isSuccess) {
                    persistedAttendanceStatus[key] = item.status
                } else {
                    attendanceFailed = true
                    attendanceError = attResult.exceptionOrNull()
                    Log.e(
                        "AttendanceVM",
                        "Error saving attendance for ${item.scout.fullName}: ${attendanceError?.message}",
                        attendanceError
                    )
                }
            } catch (e: CancellationException) {
                attDeferred.cancel(e)
                throw e
            } catch (e: Exception) {
                if (!attDeferred.isCompleted) {
                    attDeferred.complete(Result.failure(e))
                }
                attendanceFailed = true
                attendanceError = e
                Log.e("AttendanceVM", "Error saving attendance for ${item.scout.fullName}: ${e.message}", e)
            }
        }

        if (item.syncFinance) {
            val financeResult = saveFinancePayment(
                scout = item.scout,
                activity = item.activity,
                currentStatus = item.status,
                todayPaid = item.todayPaid,
                prevPaid = item.prevPaid,
                todayReq = item.todayReq,
                overpaymentCredit = item.credit,
                isFinanceEnabled = true,
                queueItem = item,
                collectServerConfirmation = collectServerConfirmation
            )
            if (financeResult.isFailure) {
                return financeResult
            }
        }

        if (attendanceFailed) {
            val err = attendanceError ?: Exception("فشل حفظ سجل الحضور")
            return Result.failure(err)
        }

        return Result.success(Unit)
    }

    private fun isTrainingMeeting(activity: Activity): Boolean {
        return activity.activityType == ActivityType.TRAINING_MEETING ||
            activity.type == ActivityType.TRAINING_MEETING.key ||
            activity.type == "اجتماع تدريبي"
    }

    private fun computeCanPay(isTrainingMeeting: Boolean, status: AttendanceStatus?): Boolean {
        if (status == null) return false
        return if (isTrainingMeeting) {
            status == AttendanceStatus.PRESENT ||
                status == AttendanceStatus.LATE ||
                status == AttendanceStatus.VERY_LATE
        } else {
            status == AttendanceStatus.PRESENT
        }
    }

    private fun computeRequiredAmountFromCache(
        activity: Activity,
        isTrainingMeeting: Boolean,
        status: AttendanceStatus?
    ): Int? {
        if (status == null) return 0
        if (!isTrainingMeeting) {
            return if (status == AttendanceStatus.PRESENT) {
                activity.customSubscriptionAmount ?: 0
            } else {
                0
            }
        }
        val subStatus = SubscriptionStatus.fromKey(status.key)
        return cachedRequiredAmounts[subStatus]
    }

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
     * تحميل البيانات المالية الخاصة بالشبل المحدد (احتياطياً في حال لم تُخزن مسبقاً)
     */
    private suspend fun loadScoutFinanceData(scout: Scout, activity: Activity) {
        val existingStatus = _entryUiState.value.recordedStatuses[scout.id]
        val trainingMeeting = isTrainingMeeting(activity)
        val canPay = computeCanPay(trainingMeeting, existingStatus)

        val subRequired = if (existingStatus != null) {
            computeRequiredAmountFromCache(activity, trainingMeeting, existingStatus)
                ?: if (trainingMeeting && financeRepository != null) {
                    val subStatus = SubscriptionStatus.fromKey(existingStatus.key)
                    financeRepository.getRequiredAmountFor(activity.id, subStatus).also {
                        cachedRequiredAmounts[subStatus] = it
                    }
                } else 0
        } else 0

        val rawPrevDebt = calculatePreviousDebt(scout.id, activity.id)

        val existingSub = try {
            if (financeRepository != null) {
                val subs = financeRepository.observeSubscriptionsForActivity(activity.id).first()
                subs.find { it.scoutId == scout.id }
            } else null
        } catch (_: Exception) { null }
        val subPaid = existingSub?.paidAmount ?: 0

        val activityTransactions = try {
            if (financeRepository != null) {
                financeRepository.observeAllTransactions().first()
                    .filter {
                        it.relatedActivityId == activity.id &&
                            it.scoutId == scout.id &&
                            !it.isRolledBack
                    }
            } else emptyList()
        } catch (_: Exception) { emptyList() }

        val debtPaid = activityTransactions
            .filter { it.type == FinanceTransactionType.DEBT_PAYMENT }
            .sumOf { it.amount }
        val advancePaid = activityTransactions
            .filter { it.type == FinanceTransactionType.ADVANCE_PAYMENT }
            .sumOf { it.amount }

        val key = scoutSessionKey(activity.id, scout.id)
        if (debtPaid > 0 || advancePaid > 0) {
            scoutsWithReversibleTxs.add(key)
        }

        val totalPaid = subPaid + debtPaid + advancePaid
        val prevDebt = rawPrevDebt + debtPaid

        if (existingSub != null && existingStatus != null) {
            persistedFinanceSignature[key] = FinanceSnapshot(
                status = existingStatus,
                todayPaid = subPaid,
                prevPaid = debtPaid,
                todayReq = existingSub.requiredAmount,
                credit = advancePaid
            )
        } else {
            persistedFinanceSignature.remove(key)
        }

        val effectivePaid = if (existingStatus != null && canPay && totalPaid > 0) totalPaid else 0
        val todayPaid = minOf(effectivePaid, subRequired)
        val remaining = effectivePaid - todayPaid
        val prevPaid = minOf(remaining, prevDebt)
        val credit = remaining - prevPaid

        scoutFinanceCache[scout.id] = ScoutFinanceDraft(
            prevDebtRequired = prevDebt,
            todaySubRequired = subRequired,
            todaySubPaid = todayPaid,
            prevDebtPaid = prevPaid,
            totalPaid = effectivePaid,
            overpaymentCredit = credit
        )

        if (effectivePaid > 0) {
            _sessionPaidByScout.update { currentMap ->
                currentMap + (scout.id to effectivePaid)
            }
        } else {
            _sessionPaidByScout.update { it - scout.id }
        }

        if (_entryUiState.value.currentScout?.id != scout.id) return

        _liveAmountInput.value = ""
        _entryUiState.update {
            it.copy(
                selectedStatus = existingStatus,
                canPay = canPay,
                previousDebtRequired = prevDebt,
                todaySubRequired = subRequired,
                previousDebtPaid = prevPaid,
                todaySubPaid = todayPaid,
                totalPaidToday = effectivePaid,
                overpaymentCredit = credit,
                quickInputAmount = ""
            )
        }
    }

    /**
     * الانتقال الفوري المتفائل لشبل محدد من الذاكرة المحلية دون انتظار الشبكة
     */
    private fun transitionToScout(
        targetIndex: Int,
        updatedQueue: List<Scout>? = null
    ) {
        _liveAmountInput.value = ""
        val currentState = _entryUiState.value
        val queue = updatedQueue ?: currentState.scoutsQueue
        val targetScout = queue.getOrNull(targetIndex)
        val activity = currentState.activity
        val targetStatus = targetScout?.let { currentState.recordedStatuses[it.id] }
        val trainingMeeting = activity?.let { isTrainingMeeting(it) } ?: true
        val canPay = computeCanPay(trainingMeeting, targetStatus)
        val draft = targetScout?.let { scoutFinanceCache[it.id] }

        val todayRequired = if (activity != null && activity.collectSubscriptions && targetStatus != null) {
            draft?.todaySubRequired
                ?: computeRequiredAmountFromCache(activity, trainingMeeting, targetStatus)
                ?: 0
        } else 0

        _entryUiState.update { state ->
            state.copy(
                scoutsQueue = queue,
                currentIndex = targetIndex,
                selectedStatus = targetStatus,
                canPay = canPay,
                previousDebtRequired = draft?.prevDebtRequired ?: 0,
                todaySubRequired = todayRequired,
                previousDebtPaid = draft?.prevDebtPaid ?: 0,
                todaySubPaid = draft?.todaySubPaid ?: 0,
                totalPaidToday = draft?.totalPaid ?: 0,
                overpaymentCredit = draft?.overpaymentCredit ?: 0,
                quickInputAmount = ""
            )
        }

        if (targetScout != null && activity != null && activity.collectSubscriptions && draft == null) {
            viewModelScope.launch {
                loadScoutFinanceData(targetScout, activity)
            }
        }
    }

    /**
     * تهيئة معالج الحضور للنشاط المحدد مع التحميل المسبق للبيانات المالية في الذاكرة
     */
    fun initAttendanceEntry(activityId: String) {
        currentSessionActivityId = activityId
        _sessionPaidByScout.value = emptyMap()
        _liveAmountInput.value = ""
        val prefix = "${activityId}_"
        persistedAttendanceStatus.keys.removeAll { it.startsWith(prefix) }
        persistedFinanceSignature.keys.removeAll { it.startsWith(prefix) }
        scoutFinanceCache.clear()
        cachedRequiredAmounts.clear()
        scoutsWithReversibleTxs.removeAll { it.startsWith(prefix) }
        failedSaveTasks.keys.removeAll { it.startsWith(prefix) }
        completedReverseStep.keys.removeAll { it.startsWith(prefix) }
        completedUpsertSubStep.keys.removeAll { it.startsWith(prefix) }
        completedPayDebtStep.keys.removeAll { it.startsWith(prefix) }
        completedAddCreditStep.keys.removeAll { it.startsWith(prefix) }
        completedRollbackTxSteps.removeAll { it.scoutKey.startsWith(prefix) }
        perScoutLocalJobs.keys.removeAll { it.startsWith(prefix) }
        transactionsPreloaded = false
        sessionHadPendingSync = false
        _entryUiState.update {
            it.copy(
                isLoading = true,
                isFinishing = false,
                localWritesDispatched = pendingLocalDispatchesCounter.get() == 0,
                pendingServerConfirmationCount = pendingServerConfirmationsCounter.get(),
                errorMessage = null,
                failedSaveCount = failedSaveTasks.size,
                failedSaveError = if (failedSaveTasks.isEmpty()) null else it.failedSaveError
            )
        }

        viewModelScope.launch {
            try {
                val activity = activityRepository.observeActivities().first().find { it.id == activityId }
                    ?: activityRepository.getActivityById(activityId)
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
                existingMap.forEach { (scoutId, status) ->
                    persistedAttendanceStatus[scoutSessionKey(activityId, scoutId)] = status
                }

                val pointsSettings = observePointsSettingsUseCase().first()
                val trainingMeeting = isTrainingMeeting(activity)
                val collectSubs = activity.collectSubscriptions

                val initialSessionPaid = mutableMapOf<String, Int>()
                if (collectSubs && financeRepository != null) {
                    if (trainingMeeting) {
                        val settings = try {
                            financeRepository.observeSettings().first()
                        } catch (_: Exception) {
                            null
                        }
                        for (subStatus in SubscriptionStatus.entries) {
                            try {
                                val overrideAmount = activity.subscriptionOverride?.get(subStatus.key)
                                cachedRequiredAmounts[subStatus] = overrideAmount
                                    ?: settings?.requiredAmountFor(subStatus)
                                    ?: financeRepository.getRequiredAmountFor(activity.id, subStatus)
                            } catch (_: Exception) {
                            }
                        }
                    }

                    val allSubscriptions = try {
                        financeRepository.observeAllSubscriptions().first()
                    } catch (_: Exception) {
                        emptyList()
                    }
                    val allTransactions = try {
                        val txs = financeRepository.observeAllTransactions().first()
                        transactionsPreloaded = true
                        txs
                    } catch (_: Exception) {
                        transactionsPreloaded = false
                        emptyList()
                    }

                    val prevDebtByScout = allSubscriptions
                        .filter { it.activityId != activity.id }
                        .groupBy { it.scoutId }
                        .mapValues { (_, subs) -> subs.sumOf { it.debtAmount } }

                    val activitySubByScout = allSubscriptions
                        .filter { it.activityId == activity.id }
                        .associateBy { it.scoutId }

                    val activityTxsByScout = allTransactions
                        .filter { it.relatedActivityId == activity.id && !it.isRolledBack }
                        .groupBy { it.scoutId }

                    for (scout in sortedScouts) {
                        val existingStatus = existingMap[scout.id]
                        val canPay = computeCanPay(trainingMeeting, existingStatus)
                        val subRequired = if (existingStatus != null) {
                            computeRequiredAmountFromCache(activity, trainingMeeting, existingStatus) ?: 0
                        } else 0

                        val rawPrevDebt = prevDebtByScout[scout.id] ?: 0
                        val existingSub = activitySubByScout[scout.id]
                        val subPaid = existingSub?.paidAmount ?: 0
                        val scoutTxs = activityTxsByScout[scout.id] ?: emptyList()

                        val debtPaid = scoutTxs
                            .filter { it.type == FinanceTransactionType.DEBT_PAYMENT }
                            .sumOf { it.amount }
                        val advancePaid = scoutTxs
                            .filter { it.type == FinanceTransactionType.ADVANCE_PAYMENT }
                            .sumOf { it.amount }

                        val key = scoutSessionKey(activity.id, scout.id)
                        if (debtPaid > 0 || advancePaid > 0) {
                            scoutsWithReversibleTxs.add(key)
                        }

                        val totalPaid = subPaid + debtPaid + advancePaid
                        val prevDebt = rawPrevDebt + debtPaid

                        if (existingSub != null && existingStatus != null) {
                            persistedFinanceSignature[key] = FinanceSnapshot(
                                status = existingStatus,
                                todayPaid = subPaid,
                                prevPaid = debtPaid,
                                todayReq = existingSub.requiredAmount,
                                credit = advancePaid
                            )
                        }

                        val effectivePaid = if (existingStatus != null && canPay && totalPaid > 0) totalPaid else 0
                        val todayPaid = minOf(effectivePaid, subRequired)
                        val remaining = effectivePaid - todayPaid
                        val prevPaid = minOf(remaining, prevDebt)
                        val credit = remaining - prevPaid

                        scoutFinanceCache[scout.id] = ScoutFinanceDraft(
                            prevDebtRequired = prevDebt,
                            todaySubRequired = subRequired,
                            todaySubPaid = todayPaid,
                            prevDebtPaid = prevPaid,
                            totalPaid = effectivePaid,
                            overpaymentCredit = credit
                        )

                        if (effectivePaid > 0) {
                            initialSessionPaid[scout.id] = effectivePaid
                        }
                    }
                }

                _sessionPaidByScout.value = initialSessionPaid

                val firstScout = sortedScouts.firstOrNull()
                val initialSelectedStatus = firstScout?.let { existingMap[it.id] }
                val initialCanPay = computeCanPay(trainingMeeting, initialSelectedStatus)
                val initialDraft = firstScout?.let { scoutFinanceCache[it.id] }

                _entryUiState.value = AttendanceEntryUiState(
                    activity = activity,
                    scoutsQueue = sortedScouts,
                    currentIndex = 0,
                    recordedStatuses = existingMap,
                    pointsSettings = pointsSettings,
                    isFinanceSectionVisible = collectSubs,
                    isLoading = false,
                    localWritesDispatched = pendingLocalDispatchesCounter.get() == 0,
                    pendingServerConfirmationCount = pendingServerConfirmationsCounter.get(),
                    selectedStatus = initialSelectedStatus,
                    canPay = initialCanPay,
                    previousDebtRequired = initialDraft?.prevDebtRequired ?: 0,
                    todaySubRequired = initialDraft?.todaySubRequired ?: 0,
                    previousDebtPaid = initialDraft?.prevDebtPaid ?: 0,
                    todaySubPaid = initialDraft?.todaySubPaid ?: 0,
                    totalPaidToday = initialDraft?.totalPaid ?: 0,
                    overpaymentCredit = initialDraft?.overpaymentCredit ?: 0
                )
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
        val trainingMeeting = activity?.let { isTrainingMeeting(it) } ?: true
        val canPay = computeCanPay(trainingMeeting, status)

        val cachedRequired = if (activity != null && activity.collectSubscriptions) {
            computeRequiredAmountFromCache(activity, trainingMeeting, status)
        } else 0

        _sessionPaidByScout.update { it - currentScout.id }
        _liveAmountInput.value = ""
        _entryUiState.update { current ->
            val updated = current.recordedStatuses.toMutableMap()
            updated[currentScout.id] = status
            current.copy(
                recordedStatuses = updated,
                selectedStatus = status,
                canPay = canPay,
                todaySubRequired = cachedRequired ?: current.todaySubRequired,
                todaySubPaid = 0,
                previousDebtPaid = 0,
                totalPaidToday = 0,
                overpaymentCredit = 0,
                quickInputAmount = ""
            )
        }

        val existingDraft = scoutFinanceCache[currentScout.id]
        scoutFinanceCache[currentScout.id] = ScoutFinanceDraft(
            prevDebtRequired = existingDraft?.prevDebtRequired ?: _entryUiState.value.previousDebtRequired,
            todaySubRequired = cachedRequired ?: _entryUiState.value.todaySubRequired,
            todaySubPaid = 0,
            prevDebtPaid = 0,
            totalPaid = 0,
            overpaymentCredit = 0
        )

        if (activity != null) {
            val stateAfterStatus = _entryUiState.value
            val isFinanceActive = stateAfterStatus.isFinanceSectionVisible && financeRepository != null
            enqueueScoutSave(
                SaveQueueItem.PersistScout(
                    scout = currentScout,
                    activity = activity,
                    status = status,
                    pointsSettings = stateAfterStatus.pointsSettings,
                    syncFinance = false,
                    todayPaid = 0,
                    prevPaid = 0,
                    todayReq = stateAfterStatus.todaySubRequired,
                    credit = 0,
                    syncAttendanceSubscription = !isFinanceActive
                )
            )
        }

        if (activity != null && activity.collectSubscriptions && trainingMeeting && cachedRequired == null && financeRepository != null) {
            viewModelScope.launch {
                val subStatus = SubscriptionStatus.fromKey(status.key)
                val required = financeRepository.getRequiredAmountFor(activity.id, subStatus)
                cachedRequiredAmounts[subStatus] = required
                val curDraft = scoutFinanceCache[currentScout.id]
                if (curDraft != null) {
                    scoutFinanceCache[currentScout.id] = curDraft.copy(todaySubRequired = required)
                }
                if (_entryUiState.value.currentScout?.id == currentScout.id) {
                    _entryUiState.update {
                        it.copy(
                            todaySubRequired = required,
                            canPay = canPay
                        )
                    }
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
            enqueueScoutSave(
                SaveQueueItem.PersistScout(
                    scout = scout,
                    activity = activity,
                    status = currentStatus,
                    pointsSettings = updatedState.pointsSettings,
                    syncFinance = updatedState.isFinanceSectionVisible && financeRepository != null,
                    todayPaid = updatedState.todaySubPaid,
                    prevPaid = updatedState.previousDebtPaid,
                    todayReq = updatedState.todaySubRequired,
                    credit = updatedState.overpaymentCredit
                )
            )
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
     * تعديل الإجمالي المدفوع يدوياً (للحقل القابل للتعديل) وحفظه عبر طابور الخلفية
     */
    fun onTotalPaidChanged(newTotal: String) {
        val state = _entryUiState.value
        if (!state.canPay) return
        val scout = state.currentScout ?: return
        val sanitized = normalizeDigits(newTotal)
        val parsed = sanitized.toIntOrNull() ?: 0
        distributePayment(parsed.coerceAtLeast(0))

        val updatedState = _entryUiState.value
        val newTotalPaid = updatedState.totalPaidToday
        _sessionPaidByScout.update { currentMap ->
            if (newTotalPaid == 0) currentMap - scout.id else currentMap + (scout.id to newTotalPaid)
        }

        val activity = updatedState.activity
        val currentStatus = updatedState.recordedStatuses[scout.id]
        if (activity != null && currentStatus != null) {
            enqueueScoutSave(
                SaveQueueItem.PersistScout(
                    scout = scout,
                    activity = activity,
                    status = currentStatus,
                    pointsSettings = updatedState.pointsSettings,
                    syncFinance = updatedState.isFinanceSectionVisible && financeRepository != null,
                    todayPaid = updatedState.todaySubPaid,
                    prevPaid = updatedState.previousDebtPaid,
                    todayReq = updatedState.todaySubRequired,
                    credit = updatedState.overpaymentCredit
                )
            )
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

        state.currentScout?.let { scout ->
            scoutFinanceCache[scout.id] = ScoutFinanceDraft(
                prevDebtRequired = state.previousDebtRequired,
                todaySubRequired = state.todaySubRequired,
                todaySubPaid = todayPaid,
                prevDebtPaid = prevPaid,
                totalPaid = sanitizedAmount,
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
            activityDate = activity.startDate,
            unitId = activity.unitId
        )
    }

    /**
     * عكس معاملات سداد الديون والدفعات المقدمة المسجلة مسبقاً لهذا الشبل في هذا النشاط قبل حفظ التحديثات
     */
    private suspend fun reverseOldPayments(
        activityId: String,
        scoutId: String,
        scoutName: String = "",
        snapshot: FinanceSnapshot? = null,
        queueItem: SaveQueueItem.PersistScout? = null,
        collectServerConfirmation: ((CompletableDeferred<Result<Unit>>) -> Unit)? = null
    ): Result<Unit> {
        if (financeRepository == null) return Result.success(Unit)
        val key = scoutSessionKey(activityId, scoutId)
        if (transactionsPreloaded && key !in scoutsWithReversibleTxs) {
            return Result.success(Unit)
        }
        val label = scoutName.ifBlank { queueItem?.scout?.fullName ?: scoutId }
        val updateTxFailure = queueItem?.let {
            makeAsyncFailureHandler(it, "تعذر تحديث سجل المعاملة السابقة للشبل $label")
        }
        val trackedDeferreds = mutableListOf<CompletableDeferred<Result<Unit>>>()
        return try {
            val existingTxs = financeRepository.observeAllTransactions().first()
                .filter {
                    it.relatedActivityId == activityId &&
                        it.scoutId == scoutId &&
                        !it.isRolledBack &&
                        (it.type == FinanceTransactionType.DEBT_PAYMENT ||
                            it.type == FinanceTransactionType.ADVANCE_PAYMENT)
                }

            for (tx in existingTxs) {
                if (tx.isRolledBack) continue
                val rollbackStepKey = RollbackTxStepKey(key, snapshot, tx.id)
                when (tx.type) {
                    FinanceTransactionType.DEBT_PAYMENT -> {
                        if (tx.amount > 0 && rollbackStepKey !in completedRollbackTxSteps) {
                            // FieldValue.increment ليس متكرر الأثر (non-idempotent): نسجل علامة الإرسال قبل عودة الاستدعاء،
                            // ولا تُحذف إلا عند فشل متزامن قبل الإرسال أو رفض غير مؤقت من الخادم (لأن المهلة أو UNAVAILABLE تبقي الكتابة في الكاش المحلي).
                            completedRollbackTxSteps.add(rollbackStepKey)
                            val rollbackFailure = queueItem?.let {
                                makeAsyncFailureHandler(
                                    item = it,
                                    description = "تعذر التراجع عن سداد ديون الشبل $label",
                                    onNonTransient = {
                                        completedRollbackTxSteps.remove(rollbackStepKey)
                                        completedReverseStep.remove(key)
                                    }
                                )
                            }
                            val rollbackDeferred = CompletableDeferred<Result<Unit>>().also { trackedDeferreds.add(it) }
                            trackServerConfirmation(rollbackDeferred, collectServerConfirmation)
                            val rollbackResult = financeRepository.rollbackScoutDebts(
                                scoutId = scoutId,
                                amountToRollback = tx.amount,
                                excludeActivityId = activityId,
                                awaitServer = false,
                                onAsyncFailure = rollbackFailure,
                                onServerResult = { res ->
                                    if (res.isFailure && !isRecoverableOfflineOrTransient(res.exceptionOrNull())) {
                                        completedRollbackTxSteps.remove(rollbackStepKey)
                                        completedReverseStep.remove(key)
                                    }
                                    rollbackDeferred.complete(res)
                                }
                            )
                            if (rollbackResult.isFailure) {
                                completedRollbackTxSteps.remove(rollbackStepKey)
                                if (!rollbackDeferred.isCompleted) {
                                    rollbackDeferred.complete(rollbackResult)
                                }
                                return Result.failure(
                                    Exception("فشل التراجع عن الدفعات السابقة. يرجى المحاولة مرة أخرى.")
                                )
                            }
                        }
                    }
                    FinanceTransactionType.ADVANCE_PAYMENT -> {
                        if (tx.amount > 0 && rollbackStepKey !in completedRollbackTxSteps) {
                            // حراسة عدم تكرار خصم الرصيد المقدم بالطريقة نفسها: لا يُعاد الإرسال بعد المهلة أو UNAVAILABLE.
                            completedRollbackTxSteps.add(rollbackStepKey)
                            val deductFailure = queueItem?.let {
                                makeAsyncFailureHandler(
                                    item = it,
                                    description = "تعذر خصم الرصيد المقدم للشبل $label",
                                    onNonTransient = {
                                        completedRollbackTxSteps.remove(rollbackStepKey)
                                        completedReverseStep.remove(key)
                                    }
                                )
                            }
                            val deductDeferred = CompletableDeferred<Result<Unit>>().also { trackedDeferreds.add(it) }
                            trackServerConfirmation(deductDeferred, collectServerConfirmation)
                            val deductResult = scoutRepository.deductCreditFromScout(
                                scoutId = scoutId,
                                amount = tx.amount,
                                awaitServer = false,
                                onAsyncFailure = deductFailure,
                                onServerResult = { res ->
                                    if (res.isFailure && !isRecoverableOfflineOrTransient(res.exceptionOrNull())) {
                                        completedRollbackTxSteps.remove(rollbackStepKey)
                                        completedReverseStep.remove(key)
                                    }
                                    deductDeferred.complete(res)
                                }
                            )
                            if (deductResult.isFailure) {
                                completedRollbackTxSteps.remove(rollbackStepKey)
                                if (!deductDeferred.isCompleted) {
                                    deductDeferred.complete(deductResult)
                                }
                                return Result.failure(
                                    Exception("فشل التراجع عن الدفعات السابقة. يرجى المحاولة مرة أخرى.")
                                )
                            }
                        }
                    }
                    else -> Unit
                }
                val auditDeferred = CompletableDeferred<Result<Unit>>().also { trackedDeferreds.add(it) }
                trackServerConfirmation(auditDeferred, collectServerConfirmation)
                val auditResult = financeRepository.updateTransaction(
                    tx = tx.copy(isRolledBack = true),
                    awaitServer = false,
                    onAsyncFailure = updateTxFailure,
                    onServerResult = { res -> auditDeferred.complete(res) }
                )
                if (!auditDeferred.isCompleted && auditResult.isFailure) {
                    auditDeferred.complete(auditResult)
                }
                if (auditResult.isFailure) {
                    return Result.failure(
                        Exception("فشل التراجع عن الدفعات السابقة. يرجى المحاولة مرة أخرى.")
                    )
                }
            }
            scoutsWithReversibleTxs.remove(key)
            Result.success(Unit)
        } catch (e: CancellationException) {
            trackedDeferreds.forEach { if (!it.isCompleted) it.cancel(e) }
            throw e
        } catch (e: Exception) {
            trackedDeferreds.forEach { if (!it.isCompleted) it.complete(Result.failure(e)) }
            Log.e("AttendanceVM", "Error reversing old payments for $scoutId in $activityId: ${e.message}", e)
            Result.failure(Exception("فشل التراجع عن الدفعات السابقة. يرجى المحاولة مرة أخرى.", e))
        }
    }

    /**
     * حفظ المعاملات والمدفوعات المالية للشبل في النشاط وقاعدة البيانات بطريقة محمية ضد تكرار عمليات FieldValue.increment
     */
    private suspend fun saveFinancePayment(
        scout: Scout,
        activity: Activity,
        currentStatus: AttendanceStatus,
        todayPaid: Int = _entryUiState.value.todaySubPaid,
        prevPaid: Int = _entryUiState.value.previousDebtPaid,
        todayReq: Int = _entryUiState.value.todaySubRequired,
        overpaymentCredit: Int = _entryUiState.value.overpaymentCredit,
        isFinanceEnabled: Boolean = _entryUiState.value.isFinanceSectionVisible,
        queueItem: SaveQueueItem.PersistScout? = null,
        collectServerConfirmation: ((CompletableDeferred<Result<Unit>>) -> Unit)? = null
    ): Result<Unit> {
        if (financeRepository == null || !isFinanceEnabled) {
            return Result.success(Unit)
        }
        val key = scoutSessionKey(activity.id, scout.id)
        val snapshot = FinanceSnapshot(
            status = currentStatus,
            todayPaid = todayPaid,
            prevPaid = prevPaid,
            todayReq = todayReq,
            credit = overpaymentCredit
        )
        if (persistedFinanceSignature[key] == snapshot) return Result.success(Unit)

        val upsertSubFailure = queueItem?.let {
            makeAsyncFailureHandler(
                item = it,
                description = "تعذر حفظ اشتراك الشبل ${scout.fullName}",
                onNonTransient = { completedUpsertSubStep.remove(key) }
            )
        }
        val payDebtFailure = queueItem?.let {
            makeAsyncFailureHandler(
                item = it,
                description = "تعذر تسجيل سداد ديون الشبل ${scout.fullName}",
                onNonTransient = {
                    completedPayDebtStep.remove(key)
                    if (overpaymentCredit > 0) {
                        completedAddCreditStep.remove(key)
                    }
                }
            )
        }
        val addCreditFailure = queueItem?.let {
            makeAsyncFailureHandler(
                item = it,
                description = "تعذر إضافة الرصيد المقدم للشبل ${scout.fullName}",
                onNonTransient = { completedAddCreditStep.remove(key) }
            )
        }
        val upsertAdvanceTxFailure = queueItem?.let {
            makeAsyncFailureHandler(it, "تعذر حفظ معاملة الدفعة المقدمة للشبل ${scout.fullName}")
        }

        val trackedDeferreds = mutableListOf<CompletableDeferred<Result<Unit>>>()
        return try {
            // 1. عكس معاملات سداد الديون والرصيد المقدم السابقة لتفادي الازدواجية عند التعديل أو الحذف
            if (completedReverseStep[key] != snapshot) {
                val reverseResult = reverseOldPayments(
                    activityId = activity.id,
                    scoutId = scout.id,
                    scoutName = scout.fullName,
                    snapshot = snapshot,
                    queueItem = queueItem,
                    collectServerConfirmation = collectServerConfirmation
                )
                if (reverseResult.isFailure) {
                    val msg = reverseResult.exceptionOrNull()?.message
                        ?: "فشل التراجع عن الدفعات السابقة. يرجى المحاولة مرة أخرى."
                    return Result.failure(reverseResult.exceptionOrNull() ?: Exception(msg))
                }
                completedReverseStep[key] = snapshot
                completedUpsertSubStep.remove(key)
                completedPayDebtStep.remove(key)
                completedAddCreditStep.remove(key)
            }

            // 2. تحديث اشتراك النشاط الحالي أولاً (يستخدم FieldValue.increment عند تغيّر المدفوع)
            if (completedUpsertSubStep[key] != snapshot) {
                // تسجيل علامة الإرسال قبل الاستدعاء لأن التحديث التزايدي غير قابل للتكرار الآمن بعد المهلة أو UNAVAILABLE
                completedUpsertSubStep[key] = snapshot
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
                    unitId = activity.unitId,
                    updatedAt = System.currentTimeMillis()
                )
                val subDeferred = CompletableDeferred<Result<Unit>>().also { trackedDeferreds.add(it) }
                trackServerConfirmation(subDeferred, collectServerConfirmation)
                val subResult = financeRepository.upsertSubscription(
                    sub = todaySub,
                    awaitServer = false,
                    onAsyncFailure = upsertSubFailure,
                    onServerResult = { res ->
                        if (res.isFailure && !isRecoverableOfflineOrTransient(res.exceptionOrNull())) {
                            completedUpsertSubStep.remove(key)
                        }
                        subDeferred.complete(res)
                    }
                )
                if (subResult.isFailure) {
                    completedUpsertSubStep.remove(key)
                    if (!subDeferred.isCompleted) {
                        subDeferred.complete(subResult)
                    }
                    val err = subResult.exceptionOrNull() ?: Exception("فشل حفظ اشتراك النشاط")
                    return Result.failure(err)
                }
            }

            // 3. تطبيق سداد الديون السابقة + معاملة DEBT_PAYMENT (+ الرصيد المقدم إن وجد) في WriteBatch واحد دون اتصال
            if (prevPaid > 0) {
                scoutsWithReversibleTxs.add(key)
                if (completedPayDebtStep[key] != snapshot) {
                    // تسجيل علامات الإرسال قبل عودة الاستدعاء: التحديثات التزايدية (FieldValue.increment)
                    // لا يُعاد إرسالها أبداً بعد المهلة أو UNAVAILABLE لأنها ما تزال في طابور SDK المحلي،
                    // ولا يُسمح بإعادة المحاولة إلا عند الفشل المتزامن قبل الإرسال أو الرفض غير المؤقت من الخادم.
                    completedPayDebtStep[key] = snapshot
                    if (overpaymentCredit > 0) {
                        completedAddCreditStep[key] = snapshot
                    }
                    val debtTxId = "${activity.id}_${scout.id}_${FinanceTransactionType.DEBT_PAYMENT.key}"
                    val debtTx = FinanceTransaction(
                        id = debtTxId,
                        type = FinanceTransactionType.DEBT_PAYMENT,
                        amount = prevPaid,
                        date = activity.startDate,
                        description = "سداد ديون سابقة",
                        source = scout.fullName,
                        scoutId = scout.id,
                        relatedActivityId = activity.id,
                        unitId = activity.unitId,
                        isRolledBack = false
                    )
                    val payDebtDeferred = CompletableDeferred<Result<Unit>>().also { trackedDeferreds.add(it) }
                    trackServerConfirmation(payDebtDeferred, collectServerConfirmation)
                    val payDebtResult = financeRepository.payScoutDebts(
                        scoutId = scout.id,
                        amountToPay = prevPaid,
                        awaitServer = false,
                        onAsyncFailure = payDebtFailure,
                        onServerResult = { res ->
                            if (res.isFailure && !isRecoverableOfflineOrTransient(res.exceptionOrNull())) {
                                completedPayDebtStep.remove(key)
                                if (overpaymentCredit > 0) {
                                    completedAddCreditStep.remove(key)
                                }
                            }
                            payDebtDeferred.complete(res)
                        },
                        auditTransaction = debtTx,
                        creditDelta = overpaymentCredit
                    )
                    if (payDebtResult.isFailure) {
                        completedPayDebtStep.remove(key)
                        if (overpaymentCredit > 0) {
                            completedAddCreditStep.remove(key)
                        }
                        if (!payDebtDeferred.isCompleted) {
                            payDebtDeferred.complete(payDebtResult)
                        }
                        val err = payDebtResult.exceptionOrNull() ?: Exception("فشل تسجيل سداد الديون السابقة")
                        return Result.failure(err)
                    }
                }
            }

            // 4. تطبيق الدفعة المقدمة (الرصيد المقدم) إذا لم تُدمج مع سداد الديون، وحفظ معاملة ADVANCE_PAYMENT
            if (overpaymentCredit > 0) {
                scoutsWithReversibleTxs.add(key)
                if (prevPaid <= 0 && completedAddCreditStep[key] != snapshot) {
                    // تسجيل علامة الإرسال قبل عودة الاستدعاء لمنع مضاعفة زيادة الرصيد بعد المهلة أو UNAVAILABLE
                    completedAddCreditStep[key] = snapshot
                    val addCreditDeferred = CompletableDeferred<Result<Unit>>().also { trackedDeferreds.add(it) }
                    trackServerConfirmation(addCreditDeferred, collectServerConfirmation)
                    val addCreditResult = scoutRepository.addCreditToScout(
                        scoutId = scout.id,
                        amount = overpaymentCredit,
                        awaitServer = false,
                        onAsyncFailure = addCreditFailure,
                        onServerResult = { res ->
                            if (res.isFailure && !isRecoverableOfflineOrTransient(res.exceptionOrNull())) {
                                completedAddCreditStep.remove(key)
                            }
                            addCreditDeferred.complete(res)
                        }
                    )
                    if (addCreditResult.isFailure) {
                        completedAddCreditStep.remove(key)
                        if (!addCreditDeferred.isCompleted) {
                            addCreditDeferred.complete(addCreditResult)
                        }
                        val err = addCreditResult.exceptionOrNull() ?: Exception("فشل تحديث الرصيد المقدم")
                        return Result.failure(err)
                    }
                }
                val advanceTxId = "${activity.id}_${scout.id}_${FinanceTransactionType.ADVANCE_PAYMENT.key}"
                val advanceTx = FinanceTransaction(
                    id = advanceTxId,
                    type = FinanceTransactionType.ADVANCE_PAYMENT,
                    amount = overpaymentCredit,
                    date = activity.startDate,
                    description = "دفعة مقدمة (رصيد)",
                    source = scout.fullName,
                    scoutId = scout.id,
                    relatedActivityId = activity.id,
                    unitId = activity.unitId,
                    isRolledBack = false
                )
                val advanceTxDeferred = CompletableDeferred<Result<Unit>>().also { trackedDeferreds.add(it) }
                trackServerConfirmation(advanceTxDeferred, collectServerConfirmation)
                val advanceTxResult = financeRepository.upsertTransaction(
                    transaction = advanceTx,
                    awaitServer = false,
                    onAsyncFailure = upsertAdvanceTxFailure,
                    onServerResult = { res -> advanceTxDeferred.complete(res) }
                )
                if (!advanceTxDeferred.isCompleted && advanceTxResult.isFailure) {
                    advanceTxDeferred.complete(advanceTxResult)
                }
                if (advanceTxResult.isFailure) {
                    val err = advanceTxResult.exceptionOrNull() ?: Exception("فشل حفظ معاملة الدفعة المقدمة")
                    return Result.failure(err)
                }
            }

            persistedFinanceSignature[key] = snapshot
            Result.success(Unit)
        } catch (e: CancellationException) {
            trackedDeferreds.forEach { if (!it.isCompleted) it.cancel(e) }
            throw e
        } catch (e: Exception) {
            trackedDeferreds.forEach { if (!it.isCompleted) it.complete(Result.failure(e)) }
            Log.e("AttendanceVM", "Error saving finance payment for ${scout.fullName}: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * الانتقال الفوري للشبل التالي مع جدولة الحفظ المتسلسل في الخلفية
     */
    fun onNext(onSkipped: () -> Unit) {
        val state = _entryUiState.value
        if (state.isFinishing) return
        val scout = state.currentScout ?: return
        val activity = state.activity ?: return
        val currentStatus = state.recordedStatuses[scout.id]

        if (currentStatus == null) {
            val updatedQueue = state.scoutsQueue.toMutableList()
            updatedQueue.removeAt(state.currentIndex)
            updatedQueue.add(scout)
            transitionToScout(state.currentIndex, updatedQueue = updatedQueue)
            onSkipped()
            return
        }

        // تطبيق أي مبلغ مكتوب في حقل الإدخال السريع قبل الحفظ والانتقال
        applyPendingQuickInputIfNeeded()
        val updatedState = _entryUiState.value
        val saveSnapshot = SaveQueueItem.PersistScout(
            scout = scout,
            activity = activity,
            status = currentStatus,
            pointsSettings = updatedState.pointsSettings,
            syncFinance = updatedState.isFinanceSectionVisible && financeRepository != null,
            todayPaid = updatedState.todaySubPaid,
            prevPaid = updatedState.previousDebtPaid,
            todayReq = updatedState.todaySubRequired,
            credit = updatedState.overpaymentCredit
        )

        // تحديث الواجهة فوراً دون انتظار الشبكة
        if (updatedState.currentIndex < updatedState.scoutsQueue.size - 1) {
            transitionToScout(updatedState.currentIndex + 1)
        } else {
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
                    quickInputAmount = ""
                )
            }
        }

        enqueueScoutSave(saveSnapshot)
    }

    /**
     * العودة الفورية للشبل السابق مع جدولة حفظ حالة الشبل الحالي في الخلفية
     */
    fun onPrevious() {
        val state = _entryUiState.value
        if (state.isFinishing) return
        if (state.currentIndex <= 0) return

        val prevIndex = state.currentIndex - 1
        val currentScout = state.currentScout
        val activity = state.activity
        val currentStatus = currentScout?.let { state.recordedStatuses[it.id] }

        val saveSnapshot = if (currentScout != null && activity != null && currentStatus != null) {
            applyPendingQuickInputIfNeeded()
            val updatedState = _entryUiState.value
            SaveQueueItem.PersistScout(
                scout = currentScout,
                activity = activity,
                status = currentStatus,
                pointsSettings = updatedState.pointsSettings,
                syncFinance = updatedState.isFinanceSectionVisible && financeRepository != null,
                todayPaid = updatedState.todaySubPaid,
                prevPaid = updatedState.previousDebtPaid,
                todayReq = updatedState.todaySubRequired,
                credit = updatedState.overpaymentCredit
            )
        } else null

        transitionToScout(prevIndex)

        if (saveSnapshot != null) {
            enqueueScoutSave(saveSnapshot)
        }
    }

    /**
     * القفز الفوري إلى شبل محدد عبر البحث مع جدولة حفظ الشبل الحالي في الخلفية
     */
    fun jumpToScout(scoutId: String, saveCurrent: Boolean = true) {
        val state = _entryUiState.value
        if (state.isFinishing) return
        val targetIndex = state.scoutsQueue.indexOfFirst { it.id == scoutId }
        if (targetIndex < 0 || targetIndex == state.currentIndex) return

        val currentScout = state.currentScout
        val activity = state.activity
        val currentStatus = currentScout?.let { state.recordedStatuses[it.id] }

        val saveSnapshot = if (saveCurrent && currentScout != null && activity != null && currentStatus != null) {
            applyPendingQuickInputIfNeeded()
            val updatedState = _entryUiState.value
            SaveQueueItem.PersistScout(
                scout = currentScout,
                activity = activity,
                status = currentStatus,
                pointsSettings = updatedState.pointsSettings,
                syncFinance = updatedState.isFinanceSectionVisible && financeRepository != null,
                todayPaid = updatedState.todaySubPaid,
                prevPaid = updatedState.previousDebtPaid,
                todayReq = updatedState.todaySubRequired,
                credit = updatedState.overpaymentCredit
            )
        } else null

        transitionToScout(targetIndex)

        if (saveSnapshot != null) {
            enqueueScoutSave(saveSnapshot)
        }
    }

    /**
     * إنهاء تسجيل الحضور: يجدول الشبل الأخير وينفذ الحفظ المحلي فوراً،
     * ثم ينتظر تأكيد الخادم (ما لم يكن الجهاز غير متصل قطعاً) ويُرجع FinishOutcome الحقيقي.
     */
    fun saveAttendance(
        definitelyOffline: Boolean = false,
        onFinish: (FinishOutcome) -> Unit
    ) {
        val state = _entryUiState.value
        if (state.isFinishing) return
        val activity = state.activity ?: return
        val currentScout = state.currentScout
        val currentStatus = currentScout?.let { state.recordedStatuses[it.id] }

        if (currentScout != null && currentStatus != null) {
            applyPendingQuickInputIfNeeded()
            val updatedState = _entryUiState.value
            enqueueScoutSave(
                SaveQueueItem.PersistScout(
                    scout = currentScout,
                    activity = activity,
                    status = currentStatus,
                    pointsSettings = updatedState.pointsSettings,
                    syncFinance = updatedState.isFinanceSectionVisible && financeRepository != null,
                    todayPaid = updatedState.todaySubPaid,
                    prevPaid = updatedState.previousDebtPaid,
                    todayReq = updatedState.todaySubRequired,
                    credit = updatedState.overpaymentCredit
                )
            )
        }

        _entryUiState.update { it.copy(isSaving = true, isFinishing = true) }

        viewModelScope.launch {
            if (definitelyOffline) {
                perScoutLocalJobs.values.toList().joinAll()
                _liveAmountInput.value = ""
                val remaining = pendingSavesCounter.get().coerceAtLeast(0)
                val remainingLocal = pendingLocalDispatchesCounter.get().coerceAtLeast(0)
                val pendingServer = pendingServerConfirmationsCounter.get().coerceAtLeast(0)
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
                        isSaving = remaining > 0,
                        isFinishing = false,
                        localWritesDispatched = remainingLocal == 0,
                        pendingServerConfirmationCount = pendingServer,
                        pendingSyncCount = remaining,
                        isFinished = true
                    )
                }
                onFinish(FinishOutcome.PENDING_SYNC)
                return@launch
            }

            val completion = CompletableDeferred<ConfirmationOutcome>()
            saveQueue.send(SaveQueueItem.FlushBarrier(completion))
            val flushOutcome = withTimeoutOrNull(serverConfirmationTimeoutMs) {
                completion.await()
            }

            val finalOutcome = when (flushOutcome) {
                null -> {
                    Log.w(
                        "AttendanceVM",
                        "Timed out after ${serverConfirmationTimeoutMs}ms awaiting Finish confirmation; local cache writes preserved."
                    )
                    perScoutLocalJobs.values.toList().joinAll()
                    if (failedSaveTasks.isNotEmpty()) {
                        val remaining = pendingSavesCounter.get().coerceAtLeast(0)
                        val fallbackMsg = "فشل حفظ بعض البيانات، يرجى المحاولة مرة أخرى."
                        _entryUiState.update {
                            it.copy(
                                isFinishing = false,
                                isSaving = remaining > 0,
                                pendingSyncCount = remaining,
                                snackbarMessage = it.snackbarMessage ?: fallbackMsg
                            )
                        }
                        return@launch
                    }
                    FinishOutcome.PENDING_SYNC
                }
                is ConfirmationOutcome.Failed -> {
                    val remaining = pendingSavesCounter.get().coerceAtLeast(0)
                    val fallbackMsg = flushOutcome.error.message ?: "فشل حفظ بعض البيانات، يرجى المحاولة مرة أخرى."
                    _entryUiState.update {
                        it.copy(
                            isFinishing = false,
                            isSaving = remaining > 0,
                            pendingSyncCount = remaining,
                            snackbarMessage = it.snackbarMessage ?: fallbackMsg
                        )
                    }
                    return@launch
                }
                ConfirmationOutcome.PendingSync -> {
                    perScoutLocalJobs.values.toList().joinAll()
                    FinishOutcome.PENDING_SYNC
                }
                ConfirmationOutcome.Confirmed -> {
                    perScoutLocalJobs.values.toList().joinAll()
                    if (sessionHadPendingSync || pendingServerConfirmationsCounter.get() > 0) {
                        FinishOutcome.PENDING_SYNC
                    } else {
                        FinishOutcome.CONFIRMED
                    }
                }
            }

            _liveAmountInput.value = ""
            val remainingLocal = pendingLocalDispatchesCounter.get().coerceAtLeast(0)
            val pendingServer = pendingServerConfirmationsCounter.get().coerceAtLeast(0)
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
                    isFinishing = false,
                    localWritesDispatched = remainingLocal == 0,
                    pendingServerConfirmationCount = pendingServer,
                    pendingSyncCount = 0,
                    isFinished = true
                )
            }
            onFinish(finalOutcome)
        }
    }

    fun saveAttendance(
        onSuccess: () -> Unit,
        awaitServerConfirmation: Boolean = true
    ) = saveAttendance(
        definitelyOffline = !awaitServerConfirmation,
        onFinish = { onSuccess() }
    )

    fun onNextClicked(onSkipped: () -> Unit = {}) = onNext(onSkipped)

    fun onFinishClicked(
        definitelyOffline: Boolean = false,
        onFinish: (FinishOutcome) -> Unit
    ) = saveAttendance(definitelyOffline = definitelyOffline, onFinish = onFinish)

    fun onFinishClicked(
        awaitServerConfirmation: Boolean = true,
        onSuccess: () -> Unit = {}
    ) = saveAttendance(
        definitelyOffline = !awaitServerConfirmation,
        onFinish = { onSuccess() }
    )

    /**
     * تصدير استمارة تسجيل الحضور القابلة للطباعة (صفحة واحدة A4 عمودي) ومشاركتها
     */
    fun exportAttendanceFormPdf(
        activity: Activity,
        context: Context,
        terms: CustomTerms = CustomTerms()
    ) {
        viewModelScope.launch {
            try {
                val activeScouts = scoutRepository.observeScouts().first().filter { it.isActive }
                val collator = Collator.getInstance(Locale("ar", "DZ"))
                val sortedScouts = activeScouts.sortedWith(
                    compareBy(collator) { it.fullName.ifBlank { "${it.name} ${it.surname}".trim() } }
                )

                val debtsMap = try {
                    financeRepository?.observeScoutDebts()?.first()
                        ?.associate { it.scoutId to it.totalDebt }
                        ?: emptyMap()
                } catch (_: Exception) {
                    emptyMap()
                }

                val file = withContext(Dispatchers.IO) {
                    PdfExporter.exportAttendanceFormPdf(
                        activityTitle = activity.title,
                        scouts = sortedScouts,
                        debtsMap = debtsMap,
                        context = context,
                        leaderNames = activity.responsibleName,
                        activityDateMillis = activity.startDate,
                        terms = terms
                    )
                }
                PdfExporter.shareAttendanceFormPdf(
                    file = file,
                    activityTitle = activity.title,
                    context = context,
                    terms = terms
                )
            } catch (e: Exception) {
                Log.e("AttendanceVM", "Error exporting attendance form PDF: ${e.message}", e)
                android.widget.Toast.makeText(
                    context,
                    context.getString(com.example.R.string.attendance_print_form_error),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    class Factory(
        private val activityRepository: ActivityRepository,
        private val scoutRepository: ScoutRepository,
        private val attendanceRepository: AttendanceRepository,
        private val getAttendanceForActivityUseCase: GetAttendanceForActivityUseCase,
        private val saveAttendanceBatchUseCase: SaveAttendanceBatchUseCase,
        private val observePointsSettingsUseCase: ObservePointsSettingsUseCase,
        private val financeRepository: FinanceRepository? = null,
        private val settingsRepository: SettingsRepository? = null,
        private val writeFailureReporter: WriteFailureReporter? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AttendanceViewModel(
                activityRepository = activityRepository,
                scoutRepository = scoutRepository,
                attendanceRepository = attendanceRepository,
                getAttendanceForActivityUseCase = getAttendanceForActivityUseCase,
                saveAttendanceBatchUseCase = saveAttendanceBatchUseCase,
                observePointsSettingsUseCase = observePointsSettingsUseCase,
                financeRepository = financeRepository,
                settingsRepository = settingsRepository,
                writeFailureReporter = writeFailureReporter
            ) as T
        }
    }
}
