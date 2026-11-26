package com.example

import androidx.lifecycle.ViewModelStore
import com.example.data.activity.ActivityFirestoreDataSource
import com.example.data.attendance.AttendanceFirestoreDataSource
import com.example.data.attendance.AttendanceRepositoryImpl
import com.example.data.common.CompositeServerResultNotifier
import com.example.data.common.WriteFailureKind
import com.example.data.common.WriteFailureReporter
import com.example.data.log.ActivityLogDataSource
import com.example.data.scout.ScoutFirestoreDataSource
import com.example.domain.activity.Activity
import com.example.domain.activity.ActivityRepository
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
import com.example.domain.auth.AuthRepository
import com.example.domain.auth.Leader
import com.example.domain.finance.FinanceMaintenanceReport
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
import com.example.presentation.attendance.FinishOutcome
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class BatchMOfflineTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -------------------------------------------------------------------------
    // ITEM 1: WriteFailureReporter unit tests & post-ViewModel-clear reporting
    // -------------------------------------------------------------------------

    @Test
    fun `WriteFailureReporter deduplicates by description and kind`() {
        val reporter = WriteFailureReporter(timeProvider = { 1_000L })
        val denied1 = FirebaseFirestoreException("denied 1", Code.PERMISSION_DENIED)
        val denied2 = FirebaseFirestoreException("denied 2", Code.UNAUTHENTICATED)
        val conflict = FirebaseFirestoreException("missing", Code.NOT_FOUND)

        reporter.report("تعذر حفظ حضور الشبل أحمد", denied1)
        // Same description & same WriteFailureKind (NO_PERMISSION) -> deduplicated
        reporter.report("  تعذر حفظ حضور الشبل أحمد  ", denied2)
        // Same description, different WriteFailureKind (NOT_FOUND_OR_CONFLICT) -> kept as distinct
        reporter.report("تعذر حفظ حضور الشبل أحمد", conflict)

        val failures = reporter.failures.value
        assertEquals(2, failures.size)
        assertEquals(WriteFailureKind.NO_PERMISSION, failures[0].kind)
        assertEquals(WriteFailureKind.NOT_FOUND_OR_CONFLICT, failures[1].kind)
    }

    @Test
    fun `WriteFailureReporter caps at 20 entries dropping oldest`() {
        val tick = AtomicLong(100L)
        val reporter = WriteFailureReporter(timeProvider = { tick.incrementAndGet() })
        val error = FirebaseFirestoreException("denied", Code.PERMISSION_DENIED)

        for (i in 1..25) {
            reporter.report("فشل العملية رقم $i", error)
        }

        val failures = reporter.failures.value
        assertEquals(WriteFailureReporter.MAX_FAILURES, failures.size)
        assertEquals("فشل العملية رقم 6", failures.first().description)
        assertEquals("فشل العملية رقم 25", failures.last().description)
    }

    @Test
    fun `WriteFailureReporter ignores recoverable transient errors UNAVAILABLE and DEADLINE_EXCEEDED`() {
        val reporter = WriteFailureReporter()

        reporter.report("tmp unavailable", FirebaseFirestoreException("offline", Code.UNAVAILABLE))
        reporter.report("tmp deadline", FirebaseFirestoreException("slow", Code.DEADLINE_EXCEEDED))

        assertTrue(reporter.failures.value.isEmpty())
    }

    @Test
    fun `WriteFailureReporter maps Firestore error codes accurately and supports dismiss and clear`() {
        val reporter = WriteFailureReporter()

        reporter.report("p", FirebaseFirestoreException("p", Code.PERMISSION_DENIED))
        reporter.report("u", FirebaseFirestoreException("u", Code.UNAUTHENTICATED))
        reporter.report("nf", FirebaseFirestoreException("nf", Code.NOT_FOUND))
        reporter.report("ab", FirebaseFirestoreException("ab", Code.ABORTED))
        reporter.report("fp", FirebaseFirestoreException("fp", Code.FAILED_PRECONDITION))
        reporter.report("ae", FirebaseFirestoreException("ae", Code.ALREADY_EXISTS))
        reporter.report("q", FirebaseFirestoreException("q", Code.RESOURCE_EXHAUSTED))
        reporter.report("o1", FirebaseFirestoreException("o1", Code.INTERNAL))
        reporter.report("o2", IllegalStateException("non-firestore"))

        val list = reporter.failures.value
        assertEquals(9, list.size)
        assertEquals(WriteFailureKind.NO_PERMISSION, list[0].kind)
        assertEquals(WriteFailureKind.NO_PERMISSION, list[1].kind)
        assertEquals(WriteFailureKind.NOT_FOUND_OR_CONFLICT, list[2].kind)
        assertEquals(WriteFailureKind.NOT_FOUND_OR_CONFLICT, list[3].kind)
        assertEquals(WriteFailureKind.NOT_FOUND_OR_CONFLICT, list[4].kind)
        assertEquals(WriteFailureKind.NOT_FOUND_OR_CONFLICT, list[5].kind)
        assertEquals(WriteFailureKind.QUOTA, list[6].kind)
        assertEquals(WriteFailureKind.OTHER, list[7].kind)
        assertEquals(WriteFailureKind.OTHER, list[8].kind)

        reporter.dismiss(list[0].id)
        assertEquals(8, reporter.failures.value.size)
        assertFalse(reporter.failures.value.any { it.description == "p" })

        reporter.clear()
        assertTrue(reporter.failures.value.isEmpty())
    }

    @Test
    fun `async failure callback reports directly to WriteFailureReporter even after ViewModel is cleared`() =
        runTest(testDispatcher) {
            val scout = Scout(id = "s1", name = "أحمد", surname = "بن علي", status = Scout.STATUS_ACTIVE)
            val activity = Activity(
                id = "act1",
                title = "اجتماع",
                type = ActivityType.TRAINING_MEETING.key,
                collectSubscriptions = true,
                startDate = 1_760_000_000_000L
            )
            val reporter = WriteFailureReporter()
            val attendanceRepo = FakeAttendanceRepository(deferServerCallbacks = true)
            val financeRepo = FakeFinanceRepository(existingSubs = emptyList(), requiredAmount = 20)
            val viewModel = createViewModel(
                activities = listOf(activity),
                scouts = listOf(scout),
                attendanceRepo = attendanceRepo,
                financeRepo = financeRepo,
                writeFailureReporter = reporter
            )

            // Attach ViewModel to a ViewModelStore so store.clear() simulates user leaving screen
            val store = ViewModelStore()
            store.put("attendance_vm", viewModel)

            viewModel.initAttendanceEntry("act1")
            advanceUntilIdle()

            viewModel.onSelectStatus(AttendanceStatus.PRESENT)
            runCurrent()
            viewModel.onTotalPaidChanged("20")
            runCurrent()

            assertTrue(attendanceRepo.capturedAsyncFailureCallbacks.isNotEmpty())
            assertTrue(financeRepo.capturedAsyncFailureCallbacks.isNotEmpty())

            // Simulate user exiting the attendance screen before server confirms/rejects
            store.clear()
            advanceUntilIdle()
            assertTrue(reporter.failures.value.isEmpty())

            // Transient error triggered after ViewModel cleared must still be ignored
            val attendanceCallback = attendanceRepo.capturedAsyncFailureCallbacks.first()
            val financeCallback = financeRepo.capturedAsyncFailureCallbacks.first()
            attendanceCallback(FirebaseFirestoreException("offline", Code.UNAVAILABLE))
            assertTrue(reporter.failures.value.isEmpty())

            // Real server rejection after ViewModel cleared MUST still reach WriteFailureReporter
            attendanceCallback(FirebaseFirestoreException("permission denied", Code.PERMISSION_DENIED))
            financeCallback(FirebaseFirestoreException("quota", Code.RESOURCE_EXHAUSTED))

            val reported = reporter.failures.value
            assertEquals(2, reported.size)
            assertTrue(reported.any { it.kind == WriteFailureKind.NO_PERMISSION })
            assertTrue(reported.any { it.kind == WriteFailureKind.QUOTA })
        }

    // -------------------------------------------------------------------------
    // ITEM 2: CompositeServerResultNotifier single-shot & AttendanceRepo propagation
    // -------------------------------------------------------------------------

    @Test
    fun `CompositeServerResultNotifier childCallback protects against double invocation and supports 0 expected`() {
        val zeroResults = mutableListOf<Result<Unit>>()
        CompositeServerResultNotifier(0) { zeroResults.add(it) }
        assertEquals(1, zeroResults.size)
        assertTrue(zeroResults.first().isSuccess)

        val results = mutableListOf<Result<Unit>>()
        val notifier = CompositeServerResultNotifier(2) { results.add(it) }
        val child1 = notifier.childCallback()
        val child2 = notifier.childCallback()

        // Double-invoking child1 must NOT decrement remaining twice or finish before child2
        child1(Result.success(Unit))
        child1(Result.success(Unit))
        assertTrue(results.isEmpty())

        // Now child2 fails -> composite must emit failure once
        val err = IllegalStateException("sub failed")
        child2(Result.failure(err))
        child2(Result.success(Unit))
        assertEquals(1, results.size)
        assertTrue(results.first().isFailure)
        assertEquals(err, results.first().exceptionOrNull())
    }

    @Test
    fun `AttendanceRepositoryImpl saveAttendance propagates subscription failure instead of reporting success`() =
        runTest(testDispatcher) {
            val mockFirestore = mock(FirebaseFirestore::class.java)
            val mockActivitiesCol = mock(CollectionReference::class.java)
            val mockActivityDoc = mock(DocumentReference::class.java)
            val mockAttendanceCol = mock(CollectionReference::class.java)
            val mockAttendanceDoc = mock(DocumentReference::class.java)
            val mockLogsCol = mock(CollectionReference::class.java)
            val mockActivitySnapshot = mock(DocumentSnapshot::class.java)
            val emptyAttendanceSnapshot = mock(QuerySnapshot::class.java)
            val mockReg = mock(ListenerRegistration::class.java)

            `when`(mockFirestore.collection(AttendanceFirestoreDataSource.COLLECTION_ACTIVITIES))
                .thenReturn(mockActivitiesCol)
            `when`(mockFirestore.collection("activity_log")).thenReturn(mockLogsCol)
            `when`(mockLogsCol.add(anyMap<String, Any?>())).thenReturn(Tasks.forResult(mock(DocumentReference::class.java)))
            `when`(mockActivitiesCol.document("act1")).thenReturn(mockActivityDoc)
            `when`(mockActivityDoc.collection(AttendanceFirestoreDataSource.SUBCOLLECTION_ATTENDANCE))
                .thenReturn(mockAttendanceCol)
            `when`(mockAttendanceCol.document("s1")).thenReturn(mockAttendanceDoc)
            `when`(mockAttendanceDoc.set(anyMap<String, Any?>())).thenReturn(Tasks.forResult(null))

            `when`(emptyAttendanceSnapshot.documents).thenReturn(emptyList())
            `when`(mockAttendanceCol.addSnapshotListener(any())).thenAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                val listener = invocation.getArgument<EventListener<QuerySnapshot>>(0)
                listener.onEvent(emptyAttendanceSnapshot, null)
                mockReg
            }

            `when`(mockActivityDoc.get()).thenReturn(Tasks.forResult(mockActivitySnapshot))
            `when`(mockActivitySnapshot.exists()).thenReturn(true)
            `when`(mockActivitySnapshot.id).thenReturn("act1")
            val activityFields = mapOf<String, Any?>(
                "title" to "اجتماع تدريبي",
                "type" to ActivityType.TRAINING_MEETING.key,
                "startDate" to 1_760_000_000_000L,
                "endDate" to 1_760_003_600_000L,
                "collectSubscriptions" to true,
                "hasAttendanceRecorded" to true
            )
            `when`(mockActivitySnapshot.get(anyString())).thenAnswer { inv ->
                activityFields[inv.getArgument<String>(0)]
            }

            val reporter = WriteFailureReporter()
            val deniedError = FirebaseFirestoreException("subscription write denied", Code.PERMISSION_DENIED)
            val failingFinanceRepo = object : FakeFinanceRepository(emptyList(), 20) {
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
                    onServerResult?.invoke(Result.failure(deniedError))
                    return Result.failure(deniedError)
                }
            }

            val repo = AttendanceRepositoryImpl(
                dataSource = AttendanceFirestoreDataSource(mockFirestore),
                activityDataSource = ActivityFirestoreDataSource(mockFirestore),
                scoutDataSource = ScoutFirestoreDataSource(mockFirestore),
                activityLogDataSource = ActivityLogDataSource(mockFirestore),
                authRepository = FakeAuthRepository(),
                financeRepository = failingFinanceRepo,
                writeFailureReporter = reporter
            )

            val compositeResults = CopyOnWriteArrayList<Result<Unit>>()
            val attendance = Attendance(
                activityId = "act1",
                scoutId = "s1",
                scoutFullName = "أحمد بن علي",
                status = AttendanceStatus.PRESENT.key,
                points = 2,
                activityDate = 1_760_000_000_000L
            )

            val saveResult = repo.saveAttendance(
                attendance = attendance,
                syncSubscription = true,
                awaitServer = true,
                onServerResult = { compositeResults.add(it) }
            )

            // Attendance document itself succeeded
            assertTrue(saveResult.isSuccess)
            // Composite server result MUST reflect subscription failure (never converted to success)
            assertEquals(1, compositeResults.size)
            assertTrue(compositeResults.first().isFailure)
            assertEquals(deniedError, compositeResults.first().exceptionOrNull())
            // Reporter MUST capture the sync failure directly
            assertEquals(1, reporter.failures.value.size)
            assertEquals(WriteFailureKind.NO_PERMISSION, reporter.failures.value.first().kind)
            assertTrue(reporter.failures.value.first().description.contains("أحمد بن علي"))
        }

    // -------------------------------------------------------------------------
    // ITEM 3: Connectivity-independent exit & FinishOutcome behavior
    // -------------------------------------------------------------------------

    @Test
    fun `onFinishClicked returns CONFIRMED when server confirms all writes`() = runTest(testDispatcher) {
        val scout = Scout(id = "s1", name = "أحمد", surname = "بن علي", status = Scout.STATUS_ACTIVE)
        val activity = Activity(
            id = "act1",
            title = "اجتماع",
            type = ActivityType.TRAINING_MEETING.key,
            collectSubscriptions = false,
            startDate = 1_760_000_000_000L
        )
        val attendanceRepo = FakeAttendanceRepository(deferServerCallbacks = false)
        val reporter = WriteFailureReporter()
        val viewModel = createViewModel(
            activities = listOf(activity),
            scouts = listOf(scout),
            attendanceRepo = attendanceRepo,
            writeFailureReporter = reporter
        )

        viewModel.initAttendanceEntry("act1")
        advanceUntilIdle()
        viewModel.onSelectStatus(AttendanceStatus.PRESENT)
        runCurrent()

        var outcome: FinishOutcome? = null
        viewModel.onFinishClicked(definitelyOffline = false) { outcome = it }
        advanceUntilIdle()

        assertEquals(FinishOutcome.CONFIRMED, outcome)
        assertTrue(viewModel.entryUiState.value.isFinished)
        assertEquals(0, viewModel.entryUiState.value.pendingServerConfirmationCount)
        assertTrue(reporter.failures.value.isEmpty())
    }

    @Test
    fun `onFinishClicked returns PENDING_SYNC on 30s timeout and when definitelyOffline is true`() =
        runTest(testDispatcher) {
            val scout = Scout(id = "s1", name = "بلال", surname = "سالم", status = Scout.STATUS_ACTIVE)
            val activity = Activity(
                id = "act1",
                title = "اجتماع",
                type = ActivityType.TRAINING_MEETING.key,
                collectSubscriptions = false,
                startDate = 1_760_000_000_000L
            )

            // Case A: online fast-path false, server hangs -> 30s timeout -> PENDING_SYNC
            val hangingRepo = FakeAttendanceRepository(deferServerCallbacks = true)
            val timeoutVm = createViewModel(
                activities = listOf(activity),
                scouts = listOf(scout),
                attendanceRepo = hangingRepo
            )
            timeoutVm.initAttendanceEntry("act1")
            advanceUntilIdle()
            timeoutVm.onSelectStatus(AttendanceStatus.PRESENT)
            runCurrent()

            assertTrue(timeoutVm.entryUiState.value.localWritesDispatched)
            assertEquals(1, timeoutVm.entryUiState.value.pendingServerConfirmationCount)

            var timeoutOutcome: FinishOutcome? = null
            timeoutVm.onFinishClicked(definitelyOffline = false) { timeoutOutcome = it }
            runCurrent()
            assertNull(timeoutOutcome)

            advanceTimeBy(AttendanceViewModel.DEFAULT_SERVER_CONFIRMATION_TIMEOUT_MS + 1_000L)
            advanceUntilIdle()
            assertEquals(FinishOutcome.PENDING_SYNC, timeoutOutcome)
            assertTrue(timeoutVm.entryUiState.value.isFinished)
            assertEquals(0, timeoutVm.entryUiState.value.failedSaveCount)

            // Case B: definitelyOffline = true -> immediate PENDING_SYNC after local dispatch
            val offlineRepo = FakeAttendanceRepository(deferServerCallbacks = true)
            val offlineVm = createViewModel(
                activities = listOf(activity),
                scouts = listOf(scout),
                attendanceRepo = offlineRepo
            )
            offlineVm.initAttendanceEntry("act1")
            advanceUntilIdle()
            offlineVm.onSelectStatus(AttendanceStatus.LATE)
            runCurrent()

            var offlineOutcome: FinishOutcome? = null
            offlineVm.onFinishClicked(definitelyOffline = true) { offlineOutcome = it }
            runCurrent()
            assertEquals(FinishOutcome.PENDING_SYNC, offlineOutcome)
            assertTrue(offlineVm.entryUiState.value.localWritesDispatched)
            assertTrue(offlineVm.entryUiState.value.isFinished)

            advanceTimeBy(AttendanceViewModel.DEFAULT_SERVER_CONFIRMATION_TIMEOUT_MS + 1_000L)
            advanceUntilIdle()
        }

    @Test
    fun `onFinishClicked treats transient UNAVAILABLE confirmation error as PENDING_SYNC and keeps real PERMISSION_DENIED on screen`() =
        runTest(testDispatcher) {
            val scout = Scout(id = "s1", name = "عمر", surname = "منصور", status = Scout.STATUS_ACTIVE)
            val activity = Activity(
                id = "act1",
                title = "اجتماع",
                type = ActivityType.TRAINING_MEETING.key,
                collectSubscriptions = false,
                startDate = 1_760_000_000_000L
            )

            // Transient UNAVAILABLE -> PENDING_SYNC without error banner
            val transientRepo = FakeAttendanceRepository(deferServerCallbacks = true)
            val transientReporter = WriteFailureReporter()
            val transientVm = createViewModel(
                activities = listOf(activity),
                scouts = listOf(scout),
                attendanceRepo = transientRepo,
                writeFailureReporter = transientReporter
            )
            transientVm.initAttendanceEntry("act1")
            advanceUntilIdle()
            transientVm.onSelectStatus(AttendanceStatus.PRESENT)
            runCurrent()

            transientRepo.pendingServerCallbacks.forEach {
                it(Result.failure(FirebaseFirestoreException("unavailable", Code.UNAVAILABLE)))
            }
            var transientOutcome: FinishOutcome? = null
            transientVm.onFinishClicked(definitelyOffline = false) { transientOutcome = it }
            advanceUntilIdle()

            assertEquals(FinishOutcome.PENDING_SYNC, transientOutcome)
            assertEquals(0, transientVm.entryUiState.value.failedSaveCount)
            assertTrue(transientReporter.failures.value.isEmpty())

            // Real PERMISSION_DENIED -> does NOT finish, keeps error state, reports to WriteFailureReporter
            val deniedRepo = FakeAttendanceRepository(deferServerCallbacks = true)
            val deniedReporter = WriteFailureReporter()
            val deniedVm = createViewModel(
                activities = listOf(activity),
                scouts = listOf(scout),
                attendanceRepo = deniedRepo,
                writeFailureReporter = deniedReporter
            )
            deniedVm.initAttendanceEntry("act1")
            advanceUntilIdle()
            deniedVm.onSelectStatus(AttendanceStatus.PRESENT)
            runCurrent()

            var deniedFinishCalled = false
            deniedVm.onFinishClicked(definitelyOffline = false) { deniedFinishCalled = true }
            runCurrent()

            // Initial attempt, queued PersistScout re-execution, and FlushBarrier retry all fail with PERMISSION_DENIED
            val deniedErr = FirebaseFirestoreException("denied", Code.PERMISSION_DENIED)
            var handledCallbacks = 0
            repeat(5) {
                val pending = deniedRepo.pendingServerCallbacks.drop(handledCallbacks)
                if (pending.isNotEmpty()) {
                    pending.forEach { it(Result.failure(deniedErr)) }
                    handledCallbacks += pending.size
                    runCurrent()
                }
            }
            advanceUntilIdle()

            assertFalse("Non-transient PERMISSION_DENIED must not exit as finished", deniedFinishCalled)
            assertFalse(deniedVm.entryUiState.value.isFinished)
            assertFalse(deniedVm.entryUiState.value.isFinishing)
            assertTrue(deniedVm.entryUiState.value.failedSaveCount > 0)
            assertNotNull(deniedVm.entryUiState.value.snackbarMessage)
            assertEquals(1, deniedReporter.failures.value.size)
            assertEquals(WriteFailureKind.NO_PERMISSION, deniedReporter.failures.value.first().kind)
        }

    // -------------------------------------------------------------------------
    // Helpers & Fakes
    // -------------------------------------------------------------------------

    private fun createViewModel(
        activities: List<Activity>,
        scouts: List<Scout>,
        attendanceRepo: FakeAttendanceRepository,
        scoutRepo: FakeScoutRepository = FakeScoutRepository(scouts),
        financeRepo: FinanceRepository? = null,
        writeFailureReporter: WriteFailureReporter? = null
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
            settingsRepository = null,
            writeFailureReporter = writeFailureReporter
        )
    }

    private class FakeAuthRepository : AuthRepository {
        override val currentUser: FirebaseUser? = null
        override val isEmailVerified: Boolean = false
        override fun observeAuthState(): Flow<FirebaseUser?> = flowOf(null)
        override suspend fun login(email: String, password: String): Result<FirebaseUser> =
            Result.failure(UnsupportedOperationException())
        override suspend fun signInWithGoogle(idToken: String): Result<FirebaseUser> =
            Result.failure(UnsupportedOperationException())
        override suspend fun signUp(fullName: String, email: String, password: String): Result<Unit> =
            Result.success(Unit)
        override suspend fun sendEmailVerification(): Result<Unit> = Result.success(Unit)
        override suspend fun reloadUser(): Result<Boolean> = Result.success(true)
        override fun logout() = Unit
        override fun observeAllLeaders(): Flow<List<Leader>> = flowOf(emptyList())
        override fun observeCurrentLeader(): Flow<Leader?> = flowOf(null)
        override suspend fun getAllLeaders(excludeUid: String): Result<List<Leader>> = Result.success(emptyList())
        override suspend fun getCurrentLeader(): Leader? = null
        override suspend fun updateLeaderStatus(uid: String, status: String): Result<Unit> = Result.success(Unit)
        override suspend fun deleteLeader(uid: String): Result<Unit> = Result.success(Unit)
        override suspend fun toggleMainLeader(uid: String, isMainLeader: Boolean): Result<Unit> = Result.success(Unit)
        override suspend fun updateMainLeader(newLeaderId: String): Result<Unit> = Result.success(Unit)
        override suspend fun deleteAccount(user: FirebaseUser): Result<Unit> = Result.success(Unit)
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
        val pendingServerCallbacks = CopyOnWriteArrayList<(Result<Unit>) -> Unit>()
        val capturedAsyncFailureCallbacks = CopyOnWriteArrayList<(Throwable) -> Unit>()

        override fun getAttendanceForActivity(activityId: String): Flow<List<Attendance>> = flowOf(emptyList())
        override fun getAttendanceForScout(scoutId: String): Flow<List<Attendance>> = flowOf(emptyList())

        override suspend fun saveAttendance(
            attendance: Attendance,
            syncSubscription: Boolean,
            awaitServer: Boolean,
            onAsyncFailure: ((Throwable) -> Unit)?,
            onServerResult: ((Result<Unit>) -> Unit)?
        ): Result<Unit> {
            onAsyncFailure?.let { capturedAsyncFailureCallbacks.add(it) }
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

    private open class FakeFinanceRepository(
        existingSubs: List<FinanceSubscription>,
        private val requiredAmount: Int
    ) : FinanceRepository {
        private val subsFlow = MutableStateFlow(existingSubs)
        private val txFlow = MutableStateFlow<List<FinanceTransaction>>(emptyList())
        val capturedAsyncFailureCallbacks = CopyOnWriteArrayList<(Throwable) -> Unit>()

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
            onAsyncFailure?.let { capturedAsyncFailureCallbacks.add(it) }
            onServerResult?.invoke(Result.success(Unit))
            return Result.success(Unit)
        }

        override suspend fun upsertTransaction(
            transaction: FinanceTransaction,
            awaitServer: Boolean,
            onAsyncFailure: ((Throwable) -> Unit)?,
            onServerResult: ((Result<Unit>) -> Unit)?
        ): Result<Unit> {
            onAsyncFailure?.let { capturedAsyncFailureCallbacks.add(it) }
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
            onAsyncFailure?.let { capturedAsyncFailureCallbacks.add(it) }
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
            onAsyncFailure?.let { capturedAsyncFailureCallbacks.add(it) }
            onServerResult?.invoke(Result.success(Unit))
            return Result.success(Unit)
        }

        override suspend fun payScoutDebts(
            scoutId: String,
            amountToPay: Int,
            awaitServer: Boolean,
            onAsyncFailure: ((Throwable) -> Unit)?,
            onServerResult: ((Result<Unit>) -> Unit)?,
            auditTransaction: FinanceTransaction?,
            creditDelta: Int
        ): Result<Unit> {
            onAsyncFailure?.let { capturedAsyncFailureCallbacks.add(it) }
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
            onAsyncFailure?.let { capturedAsyncFailureCallbacks.add(it) }
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
        override suspend fun reconcileFinancialBalances(): Result<FinanceMaintenanceReport> =
            Result.success(FinanceMaintenanceReport(0, 0, 0, 0, 0, emptyList()))
    }
}
