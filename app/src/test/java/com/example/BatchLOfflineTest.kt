package com.example

import com.example.data.common.CompositeServerResultNotifier
import com.example.domain.activity.Activity
import com.example.domain.activity.ActivityRepository
import com.example.domain.activity.ActivityStatus
import com.example.domain.activity.ActivityType
import com.example.domain.activity.MaintenanceReport
import com.example.domain.attendance.Attendance
import com.example.domain.attendance.AttendancePointsSettings
import com.example.domain.attendance.AttendanceRepository
import com.example.domain.attendance.AttendanceRow
import com.example.domain.attendance.AttendanceStatus
import com.example.domain.attendance.MeetingWithAttendance
import com.example.domain.attendance.ScoutStatistics
import com.example.domain.attendance.usecases.GetAttendanceForActivityUseCase
import com.example.domain.attendance.usecases.ObservePointsSettingsUseCase
import com.example.domain.attendance.usecases.SaveAttendanceBatchUseCase
import com.example.domain.finance.FinanceRepository
import com.example.domain.finance.FinanceSettings
import com.example.domain.finance.FinanceSubscription
import com.example.domain.finance.FinanceSummary
import com.example.domain.finance.FinanceTransaction
import com.example.domain.finance.FinanceTransactionType
import com.example.domain.finance.ScoutDebt
import com.example.domain.finance.SubscriptionStatus
import com.example.domain.scout.Scout
import com.example.domain.scout.ScoutRepository
import com.example.presentation.attendance.AttendanceViewModel
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BatchLOfflineTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun resolveProjectRootFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("../$relativePath"),
            File("/app/applet/$relativePath")
        )
        return candidates.firstOrNull { it.exists() } ?: candidates.first()
    }

    // -------------------------------------------------------------------------
    // TASK 1: Firestore Rules Hardening verification
    // -------------------------------------------------------------------------

    @Test
    fun `firestore rules prevent main leader self deletion and restrict activity_log deletion`() {
        val rulesFile = resolveProjectRootFile("firestore.rules")
        assertTrue("Expected firestore.rules to exist", rulesFile.exists())
        val content = rulesFile.readText()

        assertTrue(
            "leaders/{uid} must forbid Main Leader from deleting own document",
            content.contains(
                "allow delete: if (isSignedIn() && request.auth.uid == uid && !isApprovedMainLeader()) || (isApprovedMainLeader() && request.auth.uid != uid);"
            )
        )
        assertTrue(
            "activity_log/{docId} delete rule must be restricted to Main Leader only",
            content.contains("allow delete: if isApprovedMainLeader();")
        )
    }

    // -------------------------------------------------------------------------
    // CompositeServerResultNotifier unit tests
    // -------------------------------------------------------------------------

    @Test
    fun `CompositeServerResultNotifier completes only after all children succeed or immediately on failure`() {
        val results = mutableListOf<Result<Unit>>()
        val allSuccessNotifier = CompositeServerResultNotifier(2) { results.add(it) }
        allSuccessNotifier.onChildResult(Result.success(Unit))
        assertTrue(results.isEmpty())
        allSuccessNotifier.onChildResult(Result.success(Unit))
        assertEquals(1, results.size)
        assertTrue(results.first().isSuccess)

        val failResults = mutableListOf<Result<Unit>>()
        val failNotifier = CompositeServerResultNotifier(2) { failResults.add(it) }
        failNotifier.onChildResult(Result.failure(IllegalStateException("denied")))
        failNotifier.onChildResult(Result.success(Unit))
        assertEquals(1, failResults.size)
        assertTrue(failResults.first().isFailure)
    }

    // -------------------------------------------------------------------------
    // TASK 2: Immediate Local Cache Dispatch & 30-second Server Timeout
    // -------------------------------------------------------------------------

    @Test
    fun `onSelectStatus and onNext dispatch local cache writes immediately without waiting for pending Channel confirmation`() =
        runTest(testDispatcher) {
            val scout1 = Scout(id = "s1", name = "أحمد", surname = "بن علي", status = Scout.STATUS_ACTIVE)
            val scout2 = Scout(id = "s2", name = "يوسف", surname = "قاسمي", status = Scout.STATUS_ACTIVE)
            val activity = Activity(
                id = "act1",
                title = "اجتماع تجريبي",
                type = ActivityType.TRAINING_MEETING.key,
                collectSubscriptions = false,
                startDate = 1_760_000_000_000L
            )

            val fakeAttendanceRepo = FakeAttendanceRepository(deferServerCallbacks = true)
            val viewModel = createViewModel(
                activities = listOf(activity),
                scouts = listOf(scout1, scout2),
                attendanceRepo = fakeAttendanceRepo
            )

            viewModel.initAttendanceEntry("act1")
            advanceUntilIdle()

            // Scout 1: selecting status immediately dispatches local write (awaitServer = false)
            viewModel.onSelectStatus(AttendanceStatus.PRESENT)
            runCurrent()

            assertEquals(1, fakeAttendanceRepo.savedAttendances.size)
            assertEquals("s1", fakeAttendanceRepo.savedAttendances[0].scoutId)
            assertFalse(fakeAttendanceRepo.awaitServerFlags[0])
            // Server callback for Scout 1 is intentionally NOT completed yet -> Channel is blocked on Scout 1
            assertEquals(1, fakeAttendanceRepo.pendingServerCallbacks.size)

            // Move to Scout 2 and mark LATE + Next while Scout 1's server ACK is still blocked in Channel
            viewModel.onNext {}
            runCurrent()
            viewModel.onSelectStatus(AttendanceStatus.LATE)
            runCurrent()
            viewModel.onNext {}
            runCurrent()

            // Scout 2 MUST have dispatched to local cache immediately despite Scout 1's pending Channel confirmation
            assertTrue(
                "Scout 2 local save must be dispatched immediately without waiting for Scout 1 server ACK",
                fakeAttendanceRepo.savedAttendances.any { it.scoutId == "s2" && it.status == AttendanceStatus.LATE.key }
            )
            assertTrue(fakeAttendanceRepo.awaitServerFlags.all { !it })

            // Cleanly advance past the 30s timeouts so background Channel finishes
            advanceTimeBy(AttendanceViewModel.DEFAULT_SERVER_CONFIRMATION_TIMEOUT_MS * 4 + 1_000L)
            advanceUntilIdle()
        }

    @Test
    fun `server confirmation times out after 30 seconds without cancelling local write or reporting error`() =
        runTest(testDispatcher) {
            val scout1 = Scout(id = "s1", name = "أحمد", surname = "بن علي", status = Scout.STATUS_ACTIVE)
            val activity = Activity(
                id = "act1",
                title = "اجتماع ضعيف الاتصال",
                type = ActivityType.TRAINING_MEETING.key,
                collectSubscriptions = false,
                startDate = 1_760_000_000_000L
            )

            // Never invoke server callback -> simulates weak connection where local cache succeeded but server hangs
            val fakeAttendanceRepo = FakeAttendanceRepository(deferServerCallbacks = true)
            val viewModel = createViewModel(
                activities = listOf(activity),
                scouts = listOf(scout1),
                attendanceRepo = fakeAttendanceRepo
            )

            viewModel.initAttendanceEntry("act1")
            advanceUntilIdle()

            viewModel.onSelectStatus(AttendanceStatus.PRESENT)
            runCurrent()
            assertEquals(1, fakeAttendanceRepo.savedAttendances.size)

            var finishedCallbackCalled = false
            viewModel.saveAttendance(onSuccess = { finishedCallbackCalled = true }, awaitServerConfirmation = true)
            runCurrent()

            // Before 30s timeout expires, server-confirmed finish is still awaiting
            advanceTimeBy(15_000L)
            runCurrent()
            assertFalse("Should still wait before 30s timeout", finishedCallbackCalled)

            // Once 30s timeout elapses, warning is logged, local write is preserved, no error is recorded, and finish completes
            advanceTimeBy(AttendanceViewModel.DEFAULT_SERVER_CONFIRMATION_TIMEOUT_MS + 1_000L)
            advanceUntilIdle()

            assertTrue("Should complete finish after 30s timeout", finishedCallbackCalled)
            assertEquals(0, viewModel.entryUiState.value.failedSaveCount)
            assertNull(viewModel.entryUiState.value.failedSaveError)
            assertTrue(viewModel.entryUiState.value.isFinished)
            assertEquals(1, fakeAttendanceRepo.savedAttendances.size)
        }

    @Test
    fun `offline saveAttendance with awaitServerConfirmation false exits immediately after local dispatch`() =
        runTest(testDispatcher) {
            val scout1 = Scout(id = "s1", name = "بلال", surname = "سالم", status = Scout.STATUS_ACTIVE)
            val activity = Activity(
                id = "act1",
                title = "نشاط بدون اتصال",
                type = ActivityType.TRAINING_MEETING.key,
                collectSubscriptions = false,
                startDate = 1_760_000_000_000L
            )
            val fakeAttendanceRepo = FakeAttendanceRepository(deferServerCallbacks = true)
            val viewModel = createViewModel(
                activities = listOf(activity),
                scouts = listOf(scout1),
                attendanceRepo = fakeAttendanceRepo
            )

            viewModel.initAttendanceEntry("act1")
            advanceUntilIdle()

            viewModel.onSelectStatus(AttendanceStatus.PRESENT)
            runCurrent()

            var exited = false
            viewModel.saveAttendance(onSuccess = { exited = true }, awaitServerConfirmation = false)
            runCurrent()

            assertTrue("Offline finish must exit immediately once local cache write is dispatched", exited)
            assertEquals(1, fakeAttendanceRepo.savedAttendances.size)
            assertTrue(viewModel.entryUiState.value.isFinished)

            advanceTimeBy(AttendanceViewModel.DEFAULT_SERVER_CONFIRMATION_TIMEOUT_MS + 1_000L)
            advanceUntilIdle()
        }

    // -------------------------------------------------------------------------
    // TASK 3: Idempotency Check on Retry (No Duplicate Finance / Attendance Docs)
    // -------------------------------------------------------------------------

    @Test
    fun `retries for same scout and activity use deterministic IDs and never duplicate debt payment or credit mutations`() =
        runTest(testDispatcher) {
            val scout1 = Scout(id = "s1", name = "عمر", surname = "منصور", status = Scout.STATUS_ACTIVE)
            val activity = Activity(
                id = "act1",
                title = "اجتماع مالي",
                type = ActivityType.TRAINING_MEETING.key,
                collectSubscriptions = true,
                startDate = 1_760_000_000_000L
            )
            val previousDebtSub = FinanceSubscription(
                id = "old_act_s1",
                scoutId = "s1",
                scoutFullName = scout1.fullName,
                activityId = "old_act",
                activityTitle = "اجتماع سابق",
                activityDate = 1_750_000_000_000L,
                attendanceStatus = SubscriptionStatus.PRESENT,
                requiredAmount = 30,
                paidAmount = 0,
                debtAmount = 30
            )
            val fakeAttendanceRepo = FakeAttendanceRepository(deferServerCallbacks = false)
            val fakeScoutRepo = FakeScoutRepository(listOf(scout1))
            val fakeFinanceRepo = FakeFinanceRepository(
                existingSubs = listOf(previousDebtSub),
                requiredAmount = 20
            )

            val viewModel = createViewModel(
                activities = listOf(activity),
                scouts = listOf(scout1),
                attendanceRepo = fakeAttendanceRepo,
                scoutRepo = fakeScoutRepo,
                financeRepo = fakeFinanceRepo
            )

            viewModel.initAttendanceEntry("act1")
            advanceUntilIdle()

            // First fail the 2nd upsertTransaction (ADVANCE_PAYMENT) synchronously to trigger a Channel retry
            fakeFinanceRepo.failNextTransactionUpsertCalls = 1

            viewModel.onSelectStatus(AttendanceStatus.PRESENT)
            runCurrent()
            // Total 70 = 20 (todaySub) + 30 (prevDebt) + 20 (advanceCredit)
            viewModel.onTotalPaidChanged("70")
            advanceUntilIdle()

            // Wait for any pending retry on Finish
            var completed = false
            viewModel.saveAttendance(onSuccess = { completed = true }, awaitServerConfirmation = true)
            advanceUntilIdle()

            assertTrue(completed)
            // Idempotency: even though upsertTransaction failed once and retried in the Channel,
            // payScoutDebts and addCreditToScout ran ONCE each!
            assertEquals(1, fakeFinanceRepo.payScoutDebtsCalls.get())
            assertEquals(1, fakeScoutRepo.addCreditCalls.get())

            // Deterministic IDs guarantee single document overwrite in Firestore (never duplicate documents)
            assertEquals(setOf("act1_s1"), fakeFinanceRepo.upsertedSubscriptions.map { it.id }.toSet())
            assertEquals(
                setOf(
                    "act1_s1_${FinanceTransactionType.DEBT_PAYMENT.key}",
                    "act1_s1_${FinanceTransactionType.ADVANCE_PAYMENT.key}"
                ),
                fakeFinanceRepo.upsertedTransactions.map { it.id }.toSet()
            )
        }

    // -------------------------------------------------------------------------
    // Helpers & Fakes
    // -------------------------------------------------------------------------

    private fun createViewModel(
        activities: List<Activity>,
        scouts: List<Scout>,
        attendanceRepo: FakeAttendanceRepository,
        scoutRepo: FakeScoutRepository = FakeScoutRepository(scouts),
        financeRepo: FinanceRepository? = null
    ): AttendanceViewModel {
        val activityRepo = FakeActivityRepository(activities)
        return AttendanceViewModel(
            activityRepository = activityRepo,
            scoutRepository = scoutRepo,
            attendanceRepository = attendanceRepo,
            getAttendanceForActivityUseCase = GetAttendanceForActivityUseCase(attendanceRepo),
            saveAttendanceBatchUseCase = SaveAttendanceBatchUseCase(attendanceRepo),
            observePointsSettingsUseCase = ObservePointsSettingsUseCase(attendanceRepo),
            financeRepository = financeRepo,
            settingsRepository = null
        )
    }

    private class FakeActivityRepository(
        activities: List<Activity>
    ) : ActivityRepository {
        private val flow = MutableStateFlow(activities)
        override fun observeActivities(): Flow<List<Activity>> = flow
        override fun observeUpcomingActivities(limit: Int): Flow<List<Activity>> = flow
        override suspend fun getActivityById(id: String): Activity? = flow.value.find { it.id == id }
        override suspend fun addActivity(activity: Activity): Result<String> = Result.success(activity.id)
        override suspend fun updateActivity(activity: Activity): Result<Unit> = Result.success(Unit)
        override suspend fun markAsDone(id: String): Result<Unit> = Result.success(Unit)
        override suspend fun markAsDoneWithReport(id: String, report: String): Result<Unit> = Result.success(Unit)
        override suspend fun updateReport(id: String, report: String): Result<Unit> = Result.success(Unit)
        override suspend fun cancelActivity(id: String): Result<Unit> = Result.success(Unit)
        override suspend fun deleteActivity(id: String): Result<Unit> = Result.success(Unit)
        override suspend fun updateSubscriptionOverride(activityId: String, override: Map<String, Int>?): Result<Unit> =
            Result.success(Unit)
        override suspend fun recalculateAllAttendanceFlags(): Result<MaintenanceReport> =
            Result.success(MaintenanceReport(0, 0, 0))
    }

    private class FakeScoutRepository(
        scouts: List<Scout>
    ) : ScoutRepository {
        private val flow = MutableStateFlow(scouts)
        val addCreditCalls = AtomicInteger(0)

        override fun observeScouts(): Flow<List<Scout>> = flow
        override suspend fun getScoutById(id: String): Scout? = flow.value.find { it.id == id }
        override suspend fun addScout(scout: Scout): Result<String> = Result.success(scout.id)
        override suspend fun updateScout(scout: Scout): Result<Unit> = Result.success(Unit)
        override suspend fun archiveScout(id: String): Result<Unit> = Result.success(Unit)
        override suspend fun deleteScout(id: String): Result<Unit> = Result.success(Unit)
        override suspend fun findDuplicateScout(
            name: String,
            surname: String,
            guardianName: String,
            excludeScoutId: String?
        ): Scout? = null

        override suspend fun addCreditToScout(
            scoutId: String,
            amount: Int,
            awaitServer: Boolean,
            onAsyncFailure: ((Throwable) -> Unit)?,
            onServerResult: ((Result<Unit>) -> Unit)?
        ): Result<Unit> {
            addCreditCalls.incrementAndGet()
            onServerResult?.invoke(Result.success(Unit))
            return Result.success(Unit)
        }

        override suspend fun deductCreditFromScout(
            scoutId: String,
            amount: Int,
            awaitServer: Boolean,
            onAsyncFailure: ((Throwable) -> Unit)?,
            onServerResult: ((Result<Unit>) -> Unit)?
        ): Result<Unit> {
            onServerResult?.invoke(Result.success(Unit))
            return Result.success(Unit)
        }

        override suspend fun updateCreditManually(scoutId: String, newBalance: Int): Result<Unit> = Result.success(Unit)
    }

    private class FakeAttendanceRepository(
        private val deferServerCallbacks: Boolean
    ) : AttendanceRepository {
        val savedAttendances = CopyOnWriteArrayList<Attendance>()
        val awaitServerFlags = CopyOnWriteArrayList<Boolean>()
        val pendingServerCallbacks = CopyOnWriteArrayList<(Result<Unit>) -> Unit>()

        override fun getAttendanceForActivity(activityId: String): Flow<List<Attendance>> = flowOf(emptyList())
        override fun getAttendanceForScout(scoutId: String): Flow<List<Attendance>> = flowOf(emptyList())

        override suspend fun saveAttendance(
            attendance: Attendance,
            syncSubscription: Boolean,
            awaitServer: Boolean,
            onAsyncFailure: ((Throwable) -> Unit)?,
            onServerResult: ((Result<Unit>) -> Unit)?
        ): Result<Unit> {
            savedAttendances.add(attendance)
            awaitServerFlags.add(awaitServer)
            if (deferServerCallbacks) {
                if (onServerResult != null) {
                    pendingServerCallbacks.add(onServerResult)
                }
            } else {
                onServerResult?.invoke(Result.success(Unit))
            }
            return Result.success(Unit)
        }

        override suspend fun saveAttendanceBatch(list: List<Attendance>): Result<Unit> = Result.success(Unit)
        override suspend fun deleteAttendance(activityId: String, scoutId: String): Result<Unit> = Result.success(Unit)
        override fun observePointsSettings(): Flow<AttendancePointsSettings> = flowOf(AttendancePointsSettings())
        override suspend fun updatePointsSettings(settings: AttendancePointsSettings): Result<Unit> = Result.success(Unit)
        override fun computeScoutStatistics(scoutId: String): Flow<ScoutStatistics> = flowOf(ScoutStatistics())
        override fun getAttendanceRowsForActivity(activityId: String): Flow<List<AttendanceRow>> = flowOf(emptyList())
        override fun getTrainingMeetingsRegister(sinceTimestamp: Long): Flow<List<MeetingWithAttendance>> =
            flowOf(emptyList())
        override suspend fun getTrainingMeetingNumber(activityId: String): Int = 0
        override fun observeAllAttendance(sinceTimestamp: Long): Flow<List<Attendance>> = flowOf(emptyList())
    }

    private class FakeFinanceRepository(
        existingSubs: List<FinanceSubscription>,
        private val requiredAmount: Int
    ) : FinanceRepository {
        private val subsFlow = MutableStateFlow(existingSubs)
        private val txFlow = MutableStateFlow<List<FinanceTransaction>>(emptyList())
        val payScoutDebtsCalls = AtomicInteger(0)
        val upsertedSubscriptions = CopyOnWriteArrayList<FinanceSubscription>()
        val upsertedTransactions = CopyOnWriteArrayList<FinanceTransaction>()
        var failNextTransactionUpsertCalls = 0
        private var transactionUpsertAttempts = 0

        override fun observeAllTransactions(): Flow<List<FinanceTransaction>> = txFlow
        override fun observeTransactionsBetween(from: Long, to: Long): Flow<List<FinanceTransaction>> = txFlow
        override fun observeTransactionsByType(type: FinanceTransactionType): Flow<List<FinanceTransaction>> = txFlow
        override suspend fun addTransaction(tx: FinanceTransaction): Result<String> = Result.success(tx.id)

        override suspend fun updateTransaction(
            tx: FinanceTransaction,
            awaitServer: Boolean,
            onAsyncFailure: ((Throwable) -> Unit)?,
            onServerResult: ((Result<Unit>) -> Unit)?
        ): Result<Unit> {
            onServerResult?.invoke(Result.success(Unit))
            return Result.success(Unit)
        }

        override suspend fun upsertTransaction(
            transaction: FinanceTransaction,
            awaitServer: Boolean,
            onAsyncFailure: ((Throwable) -> Unit)?,
            onServerResult: ((Result<Unit>) -> Unit)?
        ): Result<Unit> {
            transactionUpsertAttempts++
            if (transaction.type == FinanceTransactionType.ADVANCE_PAYMENT && failNextTransactionUpsertCalls > 0) {
                failNextTransactionUpsertCalls--
                val err = RuntimeException("Transient local write failure")
                onServerResult?.invoke(Result.failure(err))
                return Result.failure(err)
            }
            upsertedTransactions.add(transaction)
            onServerResult?.invoke(Result.success(Unit))
            return Result.success(Unit)
        }

        override suspend fun deleteTransaction(id: String): Result<Unit> = Result.success(Unit)
        override fun observeSubscriptionsForActivity(activityId: String): Flow<List<FinanceSubscription>> =
            MutableStateFlow(subsFlow.value.filter { it.activityId == activityId })
        override fun observeSubscriptionsForScout(scoutId: String): Flow<List<FinanceSubscription>> =
            MutableStateFlow(subsFlow.value.filter { it.scoutId == scoutId })
        override fun observeAllSubscriptions(): Flow<List<FinanceSubscription>> = subsFlow

        override suspend fun upsertSubscription(
            sub: FinanceSubscription,
            awaitServer: Boolean,
            onAsyncFailure: ((Throwable) -> Unit)?,
            onServerResult: ((Result<Unit>) -> Unit)?
        ): Result<Unit> {
            upsertedSubscriptions.add(sub)
            onServerResult?.invoke(Result.success(Unit))
            return Result.success(Unit)
        }

        override suspend fun deleteSubscription(activityId: String, scoutId: String): Result<Unit> = Result.success(Unit)
        override suspend fun getRequiredAmountFor(activityId: String, status: SubscriptionStatus): Int = requiredAmount

        override suspend fun ensureSubscriptionForAttendance(
            activityId: String,
            scoutId: String,
            scoutFullName: String,
            activityTitle: String,
            activityDate: Long,
            attendanceStatus: SubscriptionStatus,
            awaitServer: Boolean,
            onAsyncFailure: ((Throwable) -> Unit)?,
            onServerResult: ((Result<Unit>) -> Unit)?
        ): Result<Unit> {
            onServerResult?.invoke(Result.success(Unit))
            return Result.success(Unit)
        }

        override suspend fun payScoutDebts(
            scoutId: String,
            amountToPay: Int,
            awaitServer: Boolean,
            onAsyncFailure: ((Throwable) -> Unit)?,
            onServerResult: ((Result<Unit>) -> Unit)?
        ): Result<Unit> {
            payScoutDebtsCalls.incrementAndGet()
            onServerResult?.invoke(Result.success(Unit))
            return Result.success(Unit)
        }

        override suspend fun rollbackScoutDebts(
            scoutId: String,
            amountToRollback: Int,
            excludeActivityId: String?,
            awaitServer: Boolean,
            onAsyncFailure: ((Throwable) -> Unit)?,
            onServerResult: ((Result<Unit>) -> Unit)?
        ): Result<Unit> {
            onServerResult?.invoke(Result.success(Unit))
            return Result.success(Unit)
        }

        override suspend fun updateSubscriptionDebtManually(subscriptionId: String, newDebtAmount: Int): Result<Unit> =
            Result.success(Unit)

        override fun observeSettings(): Flow<FinanceSettings> = flowOf(
            FinanceSettings(
                presentAmount = requiredAmount,
                lateAmount = requiredAmount,
                veryLateAmount = requiredAmount,
                absentExcusedAmount = requiredAmount,
                absentUnexcusedAmount = requiredAmount
            )
        )

        override suspend fun updateSettings(settings: FinanceSettings): Result<Unit> = Result.success(Unit)
        override fun observeSummary(): Flow<FinanceSummary> = flowOf(FinanceSummary())
        override fun observeScoutDebts(): Flow<List<ScoutDebt>> = flowOf(emptyList())
        override fun observeTotalOutstandingDebt(): Flow<Int> = flowOf(0)
        override suspend fun cleanupOrphanedRecords(): Result<Int> = Result.success(0)
        override suspend fun deleteCollectionsForActivity(activityId: String): Result<Unit> = Result.success(Unit)
    }
}
