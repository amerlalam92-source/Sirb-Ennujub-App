package com.example

import com.example.data.activity.ActivityRepositoryImpl
import com.example.data.common.WriteFailureReporter
import com.example.data.finance.FinanceFirestoreDataSource
import com.example.data.finance.FinanceRepositoryImpl
import com.example.data.log.ActivityLogDataSource
import com.example.data.scout.ScoutFirestoreDataSource
import com.example.data.scout.ScoutRepositoryImpl
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
import com.example.domain.attendance.usecases.UpdatePointsSettingsUseCase
import com.example.domain.auth.AuthRepository
import com.example.domain.auth.Leader
import com.example.domain.evaluation.EvaluationCriteria
import com.example.domain.evaluation.EvaluationRepository
import com.example.domain.evaluation.usecases.AddCriteriaUseCase
import com.example.domain.evaluation.usecases.ArchiveCriteriaUseCase
import com.example.domain.evaluation.usecases.DeleteCriteriaUseCase
import com.example.domain.evaluation.usecases.ObserveCriteriaUseCase
import com.example.domain.finance.DebtAllocationCalculator
import com.example.domain.finance.FinanceMaintenancePolicy
import com.example.domain.finance.FinanceMaintenanceReport
import com.example.domain.finance.FinanceRepository
import com.example.domain.finance.FinanceSettings
import com.example.domain.finance.FinanceSubscription
import com.example.domain.finance.FinanceSummary
import com.example.domain.finance.FinanceTransaction
import com.example.domain.finance.FinanceTransactionType
import com.example.domain.finance.OfflineDebtBatchOp
import com.example.domain.finance.OfflineDebtPaymentBatchPlanner
import com.example.domain.finance.ScoutDebt
import com.example.domain.finance.StoredScoutCreditState
import com.example.domain.finance.StoredSubscriptionState
import com.example.domain.finance.SubscriptionDeltaPlanner
import com.example.domain.finance.SubscriptionPaidDelta
import com.example.domain.finance.SubscriptionStatus
import com.example.domain.scout.Scout
import com.example.domain.scout.ScoutRepository
import com.example.domain.settings.BrandingRepository
import com.example.domain.settings.CustomTerms
import com.example.domain.settings.TermsRepository
import com.example.presentation.attendance.AttendanceViewModel
import com.example.presentation.attendance.FinishOutcome
import com.example.presentation.settings.SettingsViewModel
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.WriteBatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class BatchNOfflineTest {

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
    // ITEM 1: SubscriptionDeltaPlanner & Merge-Safe Increment Simulation
    // -------------------------------------------------------------------------

    @Test
    fun `SubscriptionDeltaPlanner computes payment and rollback deltas and only sets unitId when blank`() {
        val subMissingUnit = FinanceSubscription(
            id = "a1_s1",
            scoutId = "s1",
            scoutFullName = "أحمد بن علي",
            activityId = "a1",
            activityTitle = "نشاط 1",
            activityDate = 1000L,
            attendanceStatus = SubscriptionStatus.PRESENT,
            requiredAmount = 300,
            paidAmount = 0,
            debtAmount = 300,
            unitId = null
        )
        val subWithUnit = FinanceSubscription(
            id = "a2_s1",
            scoutId = "s1",
            scoutFullName = "أحمد بن علي",
            activityId = "a2",
            activityTitle = "نشاط 2",
            activityDate = 2000L,
            attendanceStatus = SubscriptionStatus.PRESENT,
            requiredAmount = 300,
            paidAmount = 100,
            debtAmount = 200,
            unitId = "unit_existing"
        )
        val untouchedSub = FinanceSubscription(
            id = "a3_s1",
            scoutId = "s1",
            scoutFullName = "أحمد بن علي",
            activityId = "a3",
            activityTitle = "نشاط 3",
            activityDate = 3000L,
            attendanceStatus = SubscriptionStatus.PRESENT,
            requiredAmount = 200,
            paidAmount = 0,
            debtAmount = 200,
            unitId = "unit_existing"
        )

        val originals = listOf(subMissingUnit, subWithUnit, untouchedSub)
        val paymentAllocation = DebtAllocationCalculator.allocatePayment(originals, amountToPay = 350, now = 9000L)
        val paymentDeltas = SubscriptionDeltaPlanner.planFromAllocation(
            originalSubscriptions = originals,
            allocation = paymentAllocation,
            fallbackUnitId = "unit_fallback",
            fallbackRecordedBy = "u1",
            fallbackRecordedByName = "قائد"
        )

        assertEquals(2, paymentDeltas.size)
        val d1 = paymentDeltas.first { it.docId == "a1_s1" }
        val d2 = paymentDeltas.first { it.docId == "a2_s1" }
        assertEquals(300, d1.paidDelta)
        assertEquals("unit_fallback", d1.unitId)
        assertEquals(50, d2.paidDelta)
        assertNull(d2.unitId)

        val rollbackTargets = listOf(
            subMissingUnit.copy(paidAmount = 300, debtAmount = 0),
            subWithUnit.copy(paidAmount = 150, debtAmount = 150)
        )
        val rollbackAllocation = DebtAllocationCalculator.allocateRollback(
            subscriptions = rollbackTargets,
            amountToRollback = 350,
            excludeActivityId = "a1",
            now = 9500L
        )
        val rollbackDeltas = SubscriptionDeltaPlanner.planFromAllocation(
            originalSubscriptions = rollbackTargets,
            allocation = rollbackAllocation,
            fallbackUnitId = "unit_fallback"
        )
        assertEquals(1, rollbackDeltas.size)
        assertEquals("a2_s1", rollbackDeltas[0].docId)
        assertEquals(-150, rollbackDeltas[0].paidDelta)
    }

    @Test
    fun `merge-safe increment simulation preserves concurrent offline payments and flags concurrent overpayment without auto-fixing`() {
        val initialStaleSnapshot = FinanceSubscription(
            id = "act1_s1",
            scoutId = "s1",
            scoutFullName = "كريم",
            activityId = "act1",
            activityTitle = "مخيم",
            activityDate = 1000L,
            attendanceStatus = SubscriptionStatus.PRESENT,
            requiredAmount = 500,
            paidAmount = 0,
            debtAmount = 500
        )

        val allocLeaderA = DebtAllocationCalculator.allocatePayment(listOf(initialStaleSnapshot), 200, now = 10L)
        val allocLeaderB = DebtAllocationCalculator.allocatePayment(listOf(initialStaleSnapshot), 150, now = 20L)
        val deltaA = SubscriptionDeltaPlanner.planFromAllocation(listOf(initialStaleSnapshot), allocLeaderA).single().paidDelta
        val deltaB = SubscriptionDeltaPlanner.planFromAllocation(listOf(initialStaleSnapshot), allocLeaderB).single().paidDelta

        // Apply A then B -> 350; apply B then A -> 350 (commutative, zero lost update)
        val paidAB = 0 + deltaA + deltaB
        val paidBA = 0 + deltaB + deltaA
        assertEquals(350, paidAB)
        assertEquals(350, paidBA)

        // Rollback delta -150 -> 200 and derived debt 300
        val afterRollbackPaid = paidAB + (-deltaB)
        assertEquals(200, afterRollbackPaid)
        assertEquals(200 to 300, FinanceFirestoreDataSource.deriveSubscriptionAmounts(500, afterRollbackPaid))

        // Concurrent overpayment from stale cache: +400 and +400 on required=500 -> paid=800, derivedDebt=0
        val overAlloc1 = DebtAllocationCalculator.allocatePayment(listOf(initialStaleSnapshot), 400, now = 30L)
        val overAlloc2 = DebtAllocationCalculator.allocatePayment(listOf(initialStaleSnapshot), 400, now = 40L)
        val overDelta1 = SubscriptionDeltaPlanner.planFromAllocation(listOf(initialStaleSnapshot), overAlloc1).single().paidDelta
        val overDelta2 = SubscriptionDeltaPlanner.planFromAllocation(listOf(initialStaleSnapshot), overAlloc2).single().paidDelta
        val overPaid = 0 + overDelta1 + overDelta2
        assertEquals(800, overPaid)
        assertEquals(800 to 0, FinanceFirestoreDataSource.deriveSubscriptionAmounts(500, overPaid))

        // Maintenance policy flags excess=300 without auto-fixing paidAmount
        val plan = FinanceMaintenancePolicy.planReconciliation(
            subscriptions = listOf(
                StoredSubscriptionState(
                    id = "act1_s1",
                    scoutFullName = "كريم",
                    activityTitle = "مخيم",
                    requiredAmount = 500,
                    storedPaidAmount = overPaid,
                    storedDebtAmount = 0
                )
            ),
            scouts = emptyList()
        )
        assertTrue(plan.subscriptionFixes.isEmpty())
        assertEquals(1, plan.overpayments.size)
        assertEquals(300, plan.overpayments.single().excessAmount)
    }

    // -------------------------------------------------------------------------
    // ITEM 1 & ITEM 2: Update Map Builders & Credit / Mapper Clamping
    // -------------------------------------------------------------------------

    @Test
    fun `update map builders emit increment markers and exclude debtAmount and requiredAmount`() {
        val delta = SubscriptionPaidDelta(
            docId = "act1_s1",
            paidDelta = 120,
            updatedAt = 5555L,
            recordedAt = 4444L,
            unitId = "unit_1",
            recordedBy = "leader_1",
            recordedByName = "القائد"
        )
        val deltaMap = FinanceFirestoreDataSource.buildSubscriptionPaidDeltaUpdateMap(delta) { amount ->
            "INC($amount)"
        }
        assertEquals("INC(120)", deltaMap["paidAmount"])
        assertEquals(5555L, deltaMap["updatedAt"])
        assertFalse(deltaMap.containsKey("debtAmount"))
        assertFalse(deltaMap.containsKey("requiredAmount"))

        val existing = FinanceSubscription(
            id = "act1_s1",
            scoutId = "s1",
            scoutFullName = "أحمد",
            activityId = "act1",
            activityTitle = "نشاط",
            activityDate = 1000L,
            attendanceStatus = SubscriptionStatus.PRESENT,
            requiredAmount = 200,
            paidAmount = 50,
            debtAmount = 150,
            unitId = "unit_1",
            recordedBy = "l1",
            recordedByName = "قائد"
        )
        val prepared = existing.copy(
            attendanceStatus = SubscriptionStatus.LATE,
            requiredAmount = 300,
            paidAmount = 150,
            debtAmount = 150,
            updatedAt = 8888L
        )
        val existingUpdateMap = FinanceFirestoreDataSource.buildExistingSubscriptionUpdateMap(
            prepared = prepared,
            existing = existing,
            paidDelta = 100
        ) { amount -> "INC($amount)" }

        assertEquals("INC(100)", existingUpdateMap["paidAmount"])
        assertEquals(300, existingUpdateMap["requiredAmount"])
        assertEquals(8888L, existingUpdateMap["updatedAt"])
        assertFalse(existingUpdateMap.containsKey("debtAmount"))

        val noPaidChangeMap = FinanceFirestoreDataSource.buildExistingSubscriptionUpdateMap(
            prepared = prepared.copy(paidAmount = 50),
            existing = existing,
            paidDelta = 0
        ) { amount -> "INC($amount)" }
        assertFalse(noPaidChangeMap.containsKey("paidAmount"))
        assertFalse(noPaidChangeMap.containsKey("debtAmount"))

        val creditMap = ScoutFirestoreDataSource.buildAdjustCreditBalanceUpdateMap(
            delta = -75,
            updatedAt = 9999L,
            updatedBy = "leader_1"
        ) { amount -> "INC($amount)" }
        assertEquals("INC(-75)", creditMap["creditBalance"])
        assertEquals(9999L, creditMap["updatedAt"])
        assertEquals("leader_1", creditMap["updatedBy"])
    }

    @Test
    fun `ScoutRepositoryImpl addCreditToScout and deductCreditFromScout use atomic increment and skip deduction on zero balance`() =
        runTest(testDispatcher) {
            val firestore = mock(FirebaseFirestore::class.java)
            val scoutsCol = mock(CollectionReference::class.java)
            val s1Ref = mock(DocumentReference::class.java)
            val sZeroRef = mock(DocumentReference::class.java)
            val logsCol = mock(CollectionReference::class.java)
            val snapshot = mock(QuerySnapshot::class.java)
            val reg = mock(ListenerRegistration::class.java)

            val docActive = mockDocSnapshot(
                id = "s1",
                fields = mapOf(
                    "name" to "أحمد",
                    "surname" to "علي",
                    "status" to Scout.STATUS_ACTIVE,
                    "creditBalance" to 100L
                )
            )
            val docZero = mockDocSnapshot(
                id = "s_zero",
                fields = mapOf(
                    "name" to "بلال",
                    "surname" to "سالم",
                    "status" to Scout.STATUS_ACTIVE,
                    "creditBalance" to -50L
                )
            )
            `when`(snapshot.documents).thenReturn(listOf(docActive, docZero))
            `when`(firestore.collection(ScoutFirestoreDataSource.COLLECTION_SCOUTS)).thenReturn(scoutsCol)
            `when`(firestore.collection("activity_log")).thenReturn(logsCol)
            `when`(logsCol.add(anyMap<String, Any?>())).thenReturn(Tasks.forResult(mock(DocumentReference::class.java)))
            `when`(scoutsCol.addSnapshotListener(any())).thenAnswer { inv ->
                @Suppress("UNCHECKED_CAST")
                val listener = inv.getArgument<EventListener<QuerySnapshot>>(0)
                listener.onEvent(snapshot, null)
                reg
            }
            `when`(scoutsCol.document("s1")).thenReturn(s1Ref)
            `when`(scoutsCol.document("s_zero")).thenReturn(sZeroRef)
            `when`(s1Ref.update(anyMap<String, Any?>())).thenReturn(Tasks.forResult(null))
            `when`(sZeroRef.update(anyMap<String, Any?>())).thenReturn(Tasks.forResult(null))

            val scoutDS = ScoutFirestoreDataSource(firestore)
            // Negative stored creditBalance (-50) is clamped to 0 by mapper
            val mappedZero = scoutDS.observeScouts().first().firstOrNull { it.id == "s_zero" }
            assertNotNull(mappedZero)
            assertEquals(0, mappedZero!!.creditBalance)

            val repo = ScoutRepositoryImpl(
                dataSource = scoutDS,
                activityLogDataSource = ActivityLogDataSource(firestore),
                authRepository = FakeAuthRepository()
            )

            // addCreditToScout (+40) uses FieldValue.increment(40)
            val addRes = repo.addCreditToScout("s1", 40, awaitServer = false)
            assertTrue(addRes.isSuccess)

            // deductCreditFromScout (500 > cached 100) caps delta at -100 via FieldValue.increment(-100)
            val deductRes = repo.deductCreditFromScout("s1", 500, awaitServer = false)
            assertTrue(deductRes.isSuccess)

            @Suppress("UNCHECKED_CAST")
            val captor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, Any?>>
            verify(s1Ref, org.mockito.Mockito.times(2)).update(captor.capture())
            assertTrue(captor.allValues[0]["creditBalance"] is FieldValue)
            assertTrue(captor.allValues[1]["creditBalance"] is FieldValue)

            // Zero / clamped-negative cached balance skips Firestore write completely
            val zeroRes = repo.deductCreditFromScout("s_zero", 50, awaitServer = false)
            assertTrue(zeroRes.isSuccess)
            verify(sZeroRef, never()).update(anyMap<String, Any?>())
        }

    // -------------------------------------------------------------------------
    // ITEM 3: Derived Debt Calculation & Negative Paid Clamping in Mapper
    // -------------------------------------------------------------------------

    @Test
    fun `FinanceFirestoreDataSource mapDocumentToSubscription ignores stale stored debtAmount and clamps negative paidAmount`() {
        val firestore = mock(FirebaseFirestore::class.java)
        val dataSource = FinanceFirestoreDataSource(firestore)

        val staleDoc = mockDocSnapshot(
            id = "act1_s1",
            fields = mapOf(
                "scoutId" to "s1",
                "activityId" to "act1",
                "attendanceStatus" to SubscriptionStatus.PRESENT.key,
                "requiredAmount" to 500L,
                "paidAmount" to 200L,
                "debtAmount" to 999L
            )
        )
        val mapped = dataSource.mapDocumentToSubscription(staleDoc)
        assertNotNull(mapped)
        assertEquals(200, mapped!!.paidAmount)
        assertEquals(300, mapped.debtAmount)

        val negativePaidDoc = mockDocSnapshot(
            id = "act2_s1",
            fields = mapOf(
                "scoutId" to "s1",
                "activityId" to "act2",
                "attendanceStatus" to SubscriptionStatus.PRESENT.key,
                "requiredAmount" to 500L,
                "paidAmount" to -30L,
                "debtAmount" to 999L
            )
        )
        val mappedNegative = dataSource.mapDocumentToSubscription(negativePaidDoc)
        assertNotNull(mappedNegative)
        assertEquals(0, mappedNegative!!.paidAmount)
        assertEquals(500, mappedNegative.debtAmount)

        assertEquals(50 to 0, FinanceFirestoreDataSource.deriveSubscriptionAmounts(requiredAmount = 30, rawPaidAmount = 50))
    }

    // -------------------------------------------------------------------------
    // ITEM 4 & ITEM 5: AttendanceViewModel Increment Retry Safety & Atomic Batch
    // -------------------------------------------------------------------------

    @Test
    fun `AttendanceViewModel never re-dispatches increment steps after timeout or UNAVAILABLE, but allows retry after PERMISSION_DENIED`() =
        runTest(testDispatcher) {
            val scout = Scout(id = "s1", name = "عمر", surname = "منصور", status = Scout.STATUS_ACTIVE)
            val scout2 = Scout(id = "s2", name = "يوسف", surname = "كامل", status = Scout.STATUS_ACTIVE)
            val activity = Activity(
                id = "act1",
                title = "اجتماع مالي",
                type = ActivityType.TRAINING_MEETING.key,
                collectSubscriptions = true,
                startDate = 1_760_000_000_000L
            )
            val oldDebt = FinanceSubscription(
                id = "old_act_s1",
                scoutId = "s1",
                scoutFullName = scout.fullName,
                activityId = "old_act",
                activityTitle = "سابق",
                activityDate = 1_750_000_000_000L,
                attendanceStatus = SubscriptionStatus.PRESENT,
                requiredAmount = 30,
                paidAmount = 0,
                debtAmount = 30
            )

            // Case 1: Timeout / PendingSync + subsequent Finish must NOT re-dispatch upsertSubscription/payScoutDebts
            val financeTimeoutRepo = TrackingFinanceRepository(listOf(oldDebt), requiredAmount = 20).apply {
                deferServerCallbacks = true
            }
            val vmTimeout = createAttendanceVm(
                activities = listOf(activity),
                scouts = listOf(scout, scout2),
                financeRepo = financeTimeoutRepo
            )
            vmTimeout.initAttendanceEntry("act1")
            advanceUntilIdle()
            vmTimeout.onSelectStatus(AttendanceStatus.PRESENT)
            runCurrent()
            // 70 = 20 (today) + 30 (prevDebt) + 20 (overpayment credit)
            vmTimeout.onTotalPaidChanged("70")
            runCurrent()
            vmTimeout.onNext {}
            runCurrent()

            assertEquals(1, financeTimeoutRepo.upsertSubCalls.get())
            assertEquals(1, financeTimeoutRepo.payDebtsCalls.get())
            // Item 5 verification: auditTransaction + creditDelta passed atomically via payScoutDebts, NOT addCreditToScout!
            assertNotNull(financeTimeoutRepo.lastAuditTx)
            assertEquals(20, financeTimeoutRepo.lastCreditDelta)

            // Let server confirmation time out (30s) -> PendingSync
            var firstOutcome: FinishOutcome? = null
            vmTimeout.onFinishClicked(definitelyOffline = false) { firstOutcome = it }
            advanceTimeBy(AttendanceViewModel.DEFAULT_SERVER_CONFIRMATION_TIMEOUT_MS + 2_000L)
            advanceUntilIdle()
            assertEquals(FinishOutcome.PENDING_SYNC, firstOutcome)

            // Neither increment step was re-dispatched during FinishBarrier / 30s timeout
            assertEquals(1, financeTimeoutRepo.upsertSubCalls.get())
            assertEquals(1, financeTimeoutRepo.payDebtsCalls.get())

            // Case 2: Transient UNAVAILABLE on payScoutDebts -> treated as PendingSync, never retried
            val financeUnavailableRepo = TrackingFinanceRepository(listOf(oldDebt), requiredAmount = 20).apply {
                deferServerCallbacks = true
            }
            val vmUnavailable = createAttendanceVm(
                activities = listOf(activity),
                scouts = listOf(scout, scout2),
                financeRepo = financeUnavailableRepo
            )
            vmUnavailable.initAttendanceEntry("act1")
            advanceUntilIdle()
            vmUnavailable.onSelectStatus(AttendanceStatus.PRESENT)
            runCurrent()
            vmUnavailable.onTotalPaidChanged("70")
            runCurrent()
            vmUnavailable.onNext {}
            runCurrent()

            // Confirm initial upsertSub & send UNAVAILABLE on initial payDebts
            financeUnavailableRepo.pendingSubCallbacks.forEach { it(Result.success(Unit)) }
            financeUnavailableRepo.pendingPayDebtCallbacks.forEach {
                it(Result.failure(FirebaseFirestoreException("unavailable", Code.UNAVAILABLE)))
            }
            advanceUntilIdle()

            var unavailableOutcome: FinishOutcome? = null
            vmUnavailable.onFinishClicked(definitelyOffline = false) { unavailableOutcome = it }
            advanceUntilIdle()
            assertEquals(FinishOutcome.PENDING_SYNC, unavailableOutcome)
            assertEquals(1, financeUnavailableRepo.upsertSubCalls.get())
            assertEquals(1, financeUnavailableRepo.payDebtsCalls.get())

            // Case 2b: When step 4 (ADVANCE_PAYMENT tx) fails synchronously after increment steps already dispatched,
            // automatic worker retry re-runs ONLY step 4 without ever re-dispatching upsertSubscription or payScoutDebts!
            val financeRetryStep4Repo = TrackingFinanceRepository(listOf(oldDebt), requiredAmount = 20).apply {
                failNextAdvanceTxCalls = 1
            }
            val vmRetryStep4 = createAttendanceVm(
                activities = listOf(activity),
                scouts = listOf(scout, scout2),
                financeRepo = financeRetryStep4Repo
            )
            vmRetryStep4.initAttendanceEntry("act1")
            advanceUntilIdle()
            vmRetryStep4.onSelectStatus(AttendanceStatus.PRESENT)
            runCurrent()
            vmRetryStep4.onTotalPaidChanged("70")
            runCurrent()
            vmRetryStep4.onNext {}
            advanceUntilIdle()

            var retryStep4Outcome: FinishOutcome? = null
            vmRetryStep4.onFinishClicked(definitelyOffline = false) { retryStep4Outcome = it }
            advanceUntilIdle()
            assertEquals(FinishOutcome.CONFIRMED, retryStep4Outcome)
            assertEquals(1, financeRetryStep4Repo.upsertSubCalls.get())
            assertEquals(1, financeRetryStep4Repo.payDebtsCalls.get())
            assertEquals(2, financeRetryStep4Repo.advanceTxCalls.get())

            // Case 3: Real PERMISSION_DENIED rejection on payScoutDebts clears marker so retryFailedSaves DOES re-dispatch payScoutDebts
            // while keeping completed upsertSubscription intact (1 call)
            val financeDeniedRepo = TrackingFinanceRepository(listOf(oldDebt), requiredAmount = 20).apply {
                deferServerCallbacks = true
            }
            val vmDenied = createAttendanceVm(
                activities = listOf(activity),
                scouts = listOf(scout, scout2),
                financeRepo = financeDeniedRepo
            )
            vmDenied.initAttendanceEntry("act1")
            advanceUntilIdle()
            vmDenied.onSelectStatus(AttendanceStatus.PRESENT)
            runCurrent()
            vmDenied.onTotalPaidChanged("50")
            runCurrent()

            financeDeniedRepo.pendingSubCallbacks.forEach { it(Result.success(Unit)) }
            financeDeniedRepo.pendingPayDebtCallbacks.forEach {
                it(Result.failure(FirebaseFirestoreException("denied", Code.PERMISSION_DENIED)))
            }
            advanceUntilIdle()

            assertTrue(vmDenied.entryUiState.value.failedSaveCount > 0)
            assertEquals(1, financeDeniedRepo.payDebtsCalls.get())

            // Retry now -> marker was cleared on PERMISSION_DENIED so payScoutDebts runs again (2nd time), upsertSub stays 1
            financeDeniedRepo.deferServerCallbacks = false
            vmDenied.retryFailedSaves()
            advanceUntilIdle()

            assertEquals(1, financeDeniedRepo.upsertSubCalls.get())
            assertEquals(2, financeDeniedRepo.payDebtsCalls.get())
            assertEquals(0, vmDenied.entryUiState.value.failedSaveCount)
        }

    // -------------------------------------------------------------------------
    // ITEM 5: OfflineDebtPaymentBatchPlanner 500-write batching
    // -------------------------------------------------------------------------

    @Test
    fun `OfflineDebtPaymentBatchPlanner respects 500-write limit and keeps audit transaction and credit increment in first batch`() {
        val auditTx = FinanceTransaction(
            id = "act1_s1_debt_payment",
            type = FinanceTransactionType.DEBT_PAYMENT,
            amount = 100
        )

        fun makeDeltas(count: Int) = (1..count).map { idx ->
            SubscriptionPaidDelta(
                docId = "sub_$idx",
                paidDelta = 10,
                updatedAt = 1000L,
                recordedAt = null,
                unitId = null,
                recordedBy = null,
                recordedByName = null
            )
        }

        // Case < 500
        val smallBatches = OfflineDebtPaymentBatchPlanner.planBatches(
            subscriptionDeltas = makeDeltas(10),
            auditTransaction = auditTx,
            scoutId = "s1",
            creditDelta = 50
        )
        assertEquals(1, smallBatches.size)
        assertEquals(12, smallBatches[0].size)
        assertTrue(smallBatches[0].any { it is OfflineDebtBatchOp.AuditTransactionSet })
        assertTrue(smallBatches[0].any { it is OfflineDebtBatchOp.ScoutCreditIncrement })

        // Case == 500 total writes (498 subs + tx + credit == 500)
        val exactBatches = OfflineDebtPaymentBatchPlanner.planBatches(
            subscriptionDeltas = makeDeltas(498),
            auditTransaction = auditTx,
            scoutId = "s1",
            creditDelta = 50
        )
        assertEquals(1, exactBatches.size)
        assertEquals(500, exactBatches[0].size)

        // Case > 500 (1200 subs + tx + credit == 1202 writes)
        val largeBatches = OfflineDebtPaymentBatchPlanner.planBatches(
            subscriptionDeltas = makeDeltas(1200),
            auditTransaction = auditTx,
            scoutId = "s1",
            creditDelta = 50
        )
        assertEquals(3, largeBatches.size)
        assertEquals(500, largeBatches[0].size)
        assertEquals(500, largeBatches[1].size)
        assertEquals(202, largeBatches[2].size)
        assertTrue(largeBatches.all { it.size <= OfflineDebtPaymentBatchPlanner.MAX_BATCH_WRITES })
        assertTrue(largeBatches[0].any { it is OfflineDebtBatchOp.AuditTransactionSet })
        assertTrue(largeBatches[0].any { it is OfflineDebtBatchOp.ScoutCreditIncrement })
    }

    // -------------------------------------------------------------------------
    // ITEM 6: FinanceMaintenancePolicy & Repository & SettingsViewModel
    // -------------------------------------------------------------------------

    @Test
    fun `FinanceMaintenancePolicy fixes negative paid, negative credit, debt mismatches and reports overpayments without auto-fixing`() {
        val subs = listOf(
            // Negative paid & mismatched debt -> fixed to paid=0, debt=200
            StoredSubscriptionState("sub_neg", "أحمد", "نشاط 1", requiredAmount = 200, storedPaidAmount = -40, storedDebtAmount = 240),
            // Mismatched debt (or null) -> fixed to debt=150
            StoredSubscriptionState("sub_mismatch", "سالم", "نشاط 2", requiredAmount = 300, storedPaidAmount = 150, storedDebtAmount = 999),
            // Clean subscription -> untouched
            StoredSubscriptionState("sub_clean", "بلال", "نشاط 3", requiredAmount = 100, storedPaidAmount = 100, storedDebtAmount = 0),
            // Overpaid -> debt=0 is consistent, NOT auto-corrected, reported with excess=200
            StoredSubscriptionState("sub_over", "عمر", "نشاط 4", requiredAmount = 500, storedPaidAmount = 700, storedDebtAmount = 0)
        )
        val scouts = listOf(
            StoredScoutCreditState("s_neg", "أحمد", storedCreditBalance = -25),
            StoredScoutCreditState("s_ok", "بلال", storedCreditBalance = 60)
        )

        val plan = FinanceMaintenancePolicy.planReconciliation(subs, scouts)
        assertEquals(2, plan.subscriptionFixes.size)
        val negFix = plan.subscriptionFixes.first { it.subscriptionId == "sub_neg" }
        assertEquals(0, negFix.newPaidAmount)
        assertEquals(200, negFix.newDebtAmount)

        val mismatchFix = plan.subscriptionFixes.first { it.subscriptionId == "sub_mismatch" }
        assertNull(mismatchFix.newPaidAmount)
        assertEquals(150, mismatchFix.newDebtAmount)

        assertEquals(1, plan.creditFixes.size)
        assertEquals("s_neg", plan.creditFixes.single().scoutId)
        assertEquals(0, plan.creditFixes.single().newCreditBalance)

        assertEquals(1, plan.overpayments.size)
        assertEquals("sub_over", plan.overpayments.single().subscriptionId)
        assertEquals(200, plan.overpayments.single().excessAmount)
    }

    @Test
    fun `reconcileFinancialBalances aborts cleanly when server read fails and commits updates via WriteBatch on success`() =
        runTest(testDispatcher) {
            // Part A: Server read failure aborts without touching batch()
            val failFirestore = mock(FirebaseFirestore::class.java)
            val failSubsCol = mock(CollectionReference::class.java)
            `when`(failFirestore.collection(FinanceFirestoreDataSource.COLLECTION_SUBSCRIPTIONS)).thenReturn(failSubsCol)
            `when`(failSubsCol.get(Source.SERVER)).thenReturn(Tasks.forException(RuntimeException("Offline")))

            val failRepo = FinanceRepositoryImpl(
                dataSource = FinanceFirestoreDataSource(failFirestore),
                scoutDataSource = ScoutFirestoreDataSource(failFirestore),
                activityLogDataSource = mock(ActivityLogDataSource::class.java),
                authRepository = FakeAuthRepository(),
                firestore = failFirestore
            )
            val failResult = failRepo.reconcileFinancialBalances()
            assertTrue(failResult.isFailure)
            assertTrue(failResult.exceptionOrNull()?.message?.contains("اتصال") == true)
            verify(failFirestore, never()).batch()

            // Part B: Success path reads both collections with Source.SERVER and updates only fixed docs
            val firestore = mock(FirebaseFirestore::class.java)
            val subsCol = mock(CollectionReference::class.java)
            val scoutsCol = mock(CollectionReference::class.java)
            val logsCol = mock(CollectionReference::class.java)
            val subsSnap = mock(QuerySnapshot::class.java)
            val scoutsSnap = mock(QuerySnapshot::class.java)
            val batch = mock(WriteBatch::class.java)

            val negSubRef = mock(DocumentReference::class.java)
            val overSubRef = mock(DocumentReference::class.java)
            val negScoutRef = mock(DocumentReference::class.java)

            val negSubDoc = mockDocSnapshot(
                id = "act1_s1",
                fields = mapOf(
                    "scoutFullName" to "أحمد",
                    "activityTitle" to "اجتماع",
                    "requiredAmount" to 200L,
                    "paidAmount" to -10L,
                    "debtAmount" to 210L
                ),
                reference = negSubRef
            )
            val overSubDoc = mockDocSnapshot(
                id = "act2_s1",
                fields = mapOf(
                    "scoutFullName" to "أحمد",
                    "activityTitle" to "رحلة",
                    "requiredAmount" to 300L,
                    "paidAmount" to 450L,
                    "debtAmount" to 0L
                ),
                reference = overSubRef
            )
            val negScoutDoc = mockDocSnapshot(
                id = "s1",
                fields = mapOf("name" to "أحمد", "surname" to "علي", "creditBalance" to -30L),
                reference = negScoutRef
            )

            `when`(subsSnap.documents).thenReturn(listOf(negSubDoc, overSubDoc))
            `when`(scoutsSnap.documents).thenReturn(listOf(negScoutDoc))
            `when`(firestore.collection(FinanceFirestoreDataSource.COLLECTION_SUBSCRIPTIONS)).thenReturn(subsCol)
            `when`(firestore.collection(ScoutFirestoreDataSource.COLLECTION_SCOUTS)).thenReturn(scoutsCol)
            `when`(firestore.collection("activity_log")).thenReturn(logsCol)
            `when`(subsCol.get(Source.SERVER)).thenReturn(Tasks.forResult(subsSnap))
            `when`(scoutsCol.get(Source.SERVER)).thenReturn(Tasks.forResult(scoutsSnap))
            `when`(firestore.batch()).thenReturn(batch)
            `when`(batch.commit()).thenReturn(Tasks.forResult(null))

            val repo = FinanceRepositoryImpl(
                dataSource = FinanceFirestoreDataSource(firestore),
                scoutDataSource = ScoutFirestoreDataSource(firestore),
                activityLogDataSource = mock(ActivityLogDataSource::class.java),
                authRepository = FakeAuthRepository(),
                firestore = firestore
            )
            val res = repo.reconcileFinancialBalances()
            assertTrue(res.isSuccess)
            val report = res.getOrThrow()
            assertEquals(2, report.subscriptionsChecked)
            assertEquals(1, report.scoutsChecked)
            assertEquals(1, report.negativePaidFixed)
            assertEquals(1, report.debtMismatchFixed)
            assertEquals(1, report.negativeCreditFixed)
            assertEquals(1, report.overpayments.size)
            assertEquals(150, report.overpayments.single().excessAmount)

            verify(subsCol).get(Source.SERVER)
            verify(scoutsCol).get(Source.SERVER)
            verify(batch).update(org.mockito.ArgumentMatchers.eq(negSubRef), anyMap<String, Any?>())
            verify(batch).update(org.mockito.ArgumentMatchers.eq(negScoutRef), anyMap<String, Any?>())
            verify(batch, never()).update(org.mockito.ArgumentMatchers.eq(overSubRef), anyMap<String, Any?>())
            verify(batch).commit()
        }

    @Test
    fun `SettingsViewModel reconcileFinancialBalances exposes report for main leader and dismisses cleanly`() =
        runTest(testDispatcher) {
            val expectedReport = FinanceMaintenanceReport(
                subscriptionsChecked = 5,
                scoutsChecked = 3,
                negativePaidFixed = 1,
                debtMismatchFixed = 2,
                negativeCreditFixed = 1,
                overpayments = emptyList()
            )
            val financeRepo = TrackingFinanceRepository(emptyList(), 20).apply {
                reconcileResult = Result.success(expectedReport)
            }
            val authRepo = FakeAuthRepository(Leader(uid = "l1", fullName = "رئيس", isMainLeader = true))
            val settingsVm = createSettingsViewModel(financeRepo = financeRepo, authRepo = authRepo)
            advanceUntilIdle()

            settingsVm.reconcileFinancialBalances()
            advanceUntilIdle()

            assertFalse(settingsVm.uiState.value.isReconcilingFinance)
            assertEquals(expectedReport, settingsVm.uiState.value.financeMaintenanceReport)

            settingsVm.dismissFinanceMaintenanceReport()
            assertNull(settingsVm.uiState.value.financeMaintenanceReport)
        }

    // -------------------------------------------------------------------------
    // Helpers & Fakes
    // -------------------------------------------------------------------------

    private fun mockDocSnapshot(
        id: String,
        fields: Map<String, Any?>,
        reference: DocumentReference = mock(DocumentReference::class.java)
    ): DocumentSnapshot {
        val doc = mock(DocumentSnapshot::class.java)
        `when`(doc.id).thenReturn(id)
        `when`(doc.exists()).thenReturn(true)
        `when`(doc.reference).thenReturn(reference)
        `when`(doc.get(anyString())).thenAnswer { inv -> fields[inv.getArgument<String>(0)] }
        return doc
    }

    private fun createAttendanceVm(
        activities: List<Activity>,
        scouts: List<Scout>,
        financeRepo: FinanceRepository
    ): AttendanceViewModel {
        val attendanceRepo = object : AttendanceRepository {
            override fun getAttendanceForActivity(activityId: String): Flow<List<Attendance>> = flowOf(emptyList())
            override fun getAttendanceForScout(scoutId: String): Flow<List<Attendance>> = flowOf(emptyList())
            override suspend fun saveAttendance(
                attendance: Attendance,
                syncSubscription: Boolean,
                awaitServer: Boolean,
                onAsyncFailure: ((Throwable) -> Unit)?,
                onServerResult: ((Result<Unit>) -> Unit)?
            ): Result<Unit> {
                onServerResult?.invoke(Result.success(Unit))
                return Result.success(Unit)
            }

            override suspend fun saveAttendanceBatch(list: List<Attendance>): Result<Unit> = Result.success(Unit)
            override suspend fun deleteAttendance(activityId: String, scoutId: String): Result<Unit> = Result.success(Unit)
            override fun observePointsSettings(): Flow<AttendancePointsSettings> = flowOf(AttendancePointsSettings())
            override suspend fun updatePointsSettings(settings: AttendancePointsSettings): Result<Unit> = Result.success(Unit)
            override fun computeScoutStatistics(scoutId: String): Flow<ScoutStatistics> = flowOf(ScoutStatistics())
            override fun getAttendanceRowsForActivity(activityId: String): Flow<List<AttendanceRow>> = flowOf(emptyList())
            override fun getTrainingMeetingsRegister(sinceTimestamp: Long): Flow<List<MeetingWithAttendance>> = flowOf(emptyList())
            override suspend fun getTrainingMeetingNumber(activityId: String): Int = 0
            override fun observeAllAttendance(sinceTimestamp: Long): Flow<List<Attendance>> = flowOf(emptyList())
        }
        val activityRepo = object : ActivityRepository {
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
            override suspend fun updateSubscriptionOverride(activityId: String, override: Map<String, Int>?): Result<Unit> = Result.success(Unit)
            override suspend fun recalculateAllAttendanceFlags(): Result<MaintenanceReport> = Result.success(MaintenanceReport(0, 0, 0))
        }
        val scoutRepo = object : ScoutRepository {
            private val flow = MutableStateFlow(scouts)
            override fun observeScouts(): Flow<List<Scout>> = flow
            override suspend fun getScoutById(id: String): Scout? = flow.value.find { it.id == id }
            override suspend fun addScout(scout: Scout): Result<String> = Result.success(scout.id)
            override suspend fun updateScout(scout: Scout): Result<Unit> = Result.success(Unit)
            override suspend fun archiveScout(id: String): Result<Unit> = Result.success(Unit)
            override suspend fun deleteScout(id: String): Result<Unit> = Result.success(Unit)
            override suspend fun findDuplicateScout(name: String, surname: String, guardianName: String, excludeScoutId: String?): Scout? = null
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
        return AttendanceViewModel(
            activityRepository = activityRepo,
            scoutRepository = scoutRepo,
            attendanceRepository = attendanceRepo,
            getAttendanceForActivityUseCase = GetAttendanceForActivityUseCase(attendanceRepo),
            saveAttendanceBatchUseCase = SaveAttendanceBatchUseCase(attendanceRepo),
            observePointsSettingsUseCase = ObservePointsSettingsUseCase(attendanceRepo),
            financeRepository = financeRepo,
            settingsRepository = null,
            writeFailureReporter = WriteFailureReporter()
        )
    }

    private fun createSettingsViewModel(
        financeRepo: FinanceRepository,
        authRepo: AuthRepository
    ): SettingsViewModel {
        val attendanceRepo = mock(AttendanceRepository::class.java)
        `when`(attendanceRepo.observePointsSettings()).thenReturn(flowOf(AttendancePointsSettings()))
        val evalRepo = mock(EvaluationRepository::class.java)
        `when`(evalRepo.observeCriteria()).thenReturn(flowOf(emptyList()))
        val brandingRepo = mock(BrandingRepository::class.java)
        val termsRepo = object : TermsRepository {
            override fun observeTerms(): Flow<CustomTerms> = flowOf(CustomTerms())
            override suspend fun updateTerms(terms: CustomTerms): Result<Unit> = Result.success(Unit)
            override fun replaceCustomWords(text: String, terms: CustomTerms): String = text
        }
        val activityRepo = mock(ActivityRepository::class.java)
        return SettingsViewModel(
            observePointsSettingsUseCase = ObservePointsSettingsUseCase(attendanceRepo),
            updatePointsSettingsUseCase = UpdatePointsSettingsUseCase(attendanceRepo),
            financeRepository = financeRepo,
            observeCriteriaUseCase = ObserveCriteriaUseCase(evalRepo),
            addCriteriaUseCase = AddCriteriaUseCase(evalRepo),
            archiveCriteriaUseCase = ArchiveCriteriaUseCase(evalRepo),
            deleteCriteriaUseCase = DeleteCriteriaUseCase(evalRepo),
            authRepository = authRepo,
            brandingRepository = brandingRepo,
            termsRepository = termsRepo,
            activityRepository = activityRepo
        )
    }

    private class FakeAuthRepository(
        private val currentLeader: Leader? = Leader(uid = "l1", fullName = "قائد", unitId = "u1")
    ) : AuthRepository {
        override val currentUser: FirebaseUser? = null
        override val isEmailVerified: Boolean = false
        override fun observeAuthState(): Flow<FirebaseUser?> = flowOf(null)
        override suspend fun login(email: String, password: String): Result<FirebaseUser> = Result.failure(UnsupportedOperationException())
        override suspend fun signInWithGoogle(idToken: String): Result<FirebaseUser> = Result.failure(UnsupportedOperationException())
        override suspend fun signUp(fullName: String, email: String, password: String): Result<Unit> = Result.success(Unit)
        override suspend fun sendEmailVerification(): Result<Unit> = Result.success(Unit)
        override suspend fun reloadUser(): Result<Boolean> = Result.success(true)
        override fun logout() = Unit
        override fun observeAllLeaders(): Flow<List<Leader>> = flowOf(emptyList())
        override fun observeCurrentLeader(): Flow<Leader?> = flowOf(currentLeader)
        override suspend fun getAllLeaders(excludeUid: String): Result<List<Leader>> = Result.success(emptyList())
        override suspend fun getCurrentLeader(): Leader? = currentLeader
        override suspend fun updateLeaderStatus(uid: String, status: String): Result<Unit> = Result.success(Unit)
        override suspend fun deleteLeader(uid: String): Result<Unit> = Result.success(Unit)
        override suspend fun toggleMainLeader(uid: String, isMainLeader: Boolean): Result<Unit> = Result.success(Unit)
        override suspend fun updateMainLeader(newLeaderId: String): Result<Unit> = Result.success(Unit)
        override suspend fun deleteAccount(user: FirebaseUser): Result<Unit> = Result.success(Unit)
    }

    private class TrackingFinanceRepository(
        existingSubs: List<FinanceSubscription>,
        private val requiredAmount: Int
    ) : FinanceRepository {
        private val subsFlow = MutableStateFlow(existingSubs)
        private val txFlow = MutableStateFlow<List<FinanceTransaction>>(emptyList())
        var deferServerCallbacks: Boolean = false
        var failNextAdvanceTxCalls: Int = 0
        val upsertSubCalls = AtomicInteger(0)
        val payDebtsCalls = AtomicInteger(0)
        val advanceTxCalls = AtomicInteger(0)
        val pendingSubCallbacks = CopyOnWriteArrayList<(Result<Unit>) -> Unit>()
        val pendingPayDebtCallbacks = CopyOnWriteArrayList<(Result<Unit>) -> Unit>()
        var lastAuditTx: FinanceTransaction? = null
        var lastCreditDelta: Int = 0
        var reconcileResult: Result<FinanceMaintenanceReport> =
            Result.success(FinanceMaintenanceReport(0, 0, 0, 0, 0, emptyList()))

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
            if (transaction.type == FinanceTransactionType.ADVANCE_PAYMENT) {
                advanceTxCalls.incrementAndGet()
            }
            if (transaction.type == FinanceTransactionType.ADVANCE_PAYMENT && failNextAdvanceTxCalls > 0) {
                failNextAdvanceTxCalls--
                val err = RuntimeException("Transient local tx write error")
                onServerResult?.invoke(Result.failure(err))
                return Result.failure(err)
            }
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
            upsertSubCalls.incrementAndGet()
            if (deferServerCallbacks) {
                onServerResult?.let { pendingSubCallbacks.add(it) }
            } else {
                onServerResult?.invoke(Result.success(Unit))
            }
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
            onServerResult: ((Result<Unit>) -> Unit)?,
            auditTransaction: FinanceTransaction?,
            creditDelta: Int
        ): Result<Unit> {
            payDebtsCalls.incrementAndGet()
            lastAuditTx = auditTransaction
            lastCreditDelta = creditDelta
            if (deferServerCallbacks) {
                onServerResult?.let { pendingPayDebtCallbacks.add(it) }
            } else {
                onServerResult?.invoke(Result.success(Unit))
            }
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

        override suspend fun updateSubscriptionDebtManually(subscriptionId: String, newDebtAmount: Int): Result<Unit> = Result.success(Unit)
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
        override suspend fun reconcileFinancialBalances(): Result<FinanceMaintenanceReport> = reconcileResult
    }
}
