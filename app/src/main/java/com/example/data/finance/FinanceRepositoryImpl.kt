package com.example.data.finance

import com.example.data.log.ActivityLogDataSource
import com.example.data.scout.ScoutFirestoreDataSource
import com.example.domain.auth.AuthRepository
import com.example.domain.finance.DebtAllocationCalculator
import com.example.domain.finance.FinanceRepository
import com.example.domain.finance.FinanceSettings
import com.example.domain.finance.FinanceSubscription
import com.example.domain.finance.FinanceSummary
import com.example.domain.finance.FinanceTransaction
import com.example.domain.finance.FinanceTransactionType
import com.example.domain.finance.ScoutDebt
import com.example.domain.finance.SubscriptionStatus
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * تنفيذ مستودع الإدارة المالية (Finance Repository Implementation)
 * يدير العمليات المالية وحساب الإحصائيات التراكمية وديون الأشبال والاشتراكات.
 */
class FinanceRepositoryImpl(
    private val dataSource: FinanceFirestoreDataSource,
    private val scoutDataSource: ScoutFirestoreDataSource,
    private val activityLogDataSource: ActivityLogDataSource,
    private val authRepository: AuthRepository,
    private val firestore: FirebaseFirestore
) : FinanceRepository {

    private fun observeActiveScoutIds(): Flow<Set<String>> {
        return scoutDataSource.observeScouts().map { scouts ->
            scouts.filter { it.isActive }.map { it.id }.toSet()
        }
    }

    // =============================================================
    // Transactions (المعاملات المالية)
    // =============================================================

    override fun observeAllTransactions(): Flow<List<FinanceTransaction>> {
        return dataSource.observeAllTransactions()
    }

    override fun observeTransactionsBetween(from: Long, to: Long): Flow<List<FinanceTransaction>> {
        return dataSource.observeTransactionsBetween(from, to)
    }

    override fun observeTransactionsByType(type: FinanceTransactionType): Flow<List<FinanceTransaction>> {
        return dataSource.observeTransactionsByType(type.key)
    }

    override suspend fun addTransaction(tx: FinanceTransaction): Result<String> {
        val currentUser = authRepository.currentUser
        val currentUnitId = tx.unitId ?: authRepository.getCurrentLeader()?.unitId
        val prepared = tx.copy(
            unitId = currentUnitId,
            createdBy = currentUser?.uid ?: "",
            createdByName = currentUser?.displayName ?: "قائد كشفي",
            createdAt = if (tx.createdAt > 0) tx.createdAt else System.currentTimeMillis()
        )
        val result = dataSource.addTransaction(prepared)
        if (result.isSuccess) {
            val generatedId = result.getOrNull() ?: prepared.id
            activityLogDataSource.logActivity(
                userId = currentUser?.uid ?: "",
                userName = currentUser?.displayName ?: "قائد كشفي",
                action = "add",
                targetType = "finance_transaction",
                targetId = generatedId,
                targetName = "${prepared.type.arabicLabel}: ${prepared.amount} دج",
                details = "المبلغ: ${prepared.amount} دج | البيان: ${prepared.description.ifBlank { prepared.type.arabicLabel }}",
                unitId = currentUnitId
            )
        }
        return result
    }

    override suspend fun updateTransaction(tx: FinanceTransaction): Result<Unit> {
        val currentUser = authRepository.currentUser
        val currentUnitId = tx.unitId ?: authRepository.getCurrentLeader()?.unitId
        val prepared = tx.copy(unitId = currentUnitId)
        val result = dataSource.updateTransaction(prepared)
        if (result.isSuccess) {
            activityLogDataSource.logActivity(
                userId = currentUser?.uid ?: "",
                userName = currentUser?.displayName ?: "قائد كشفي",
                action = "update",
                targetType = "finance_transaction",
                targetId = tx.id,
                targetName = "${tx.type.arabicLabel}: ${tx.amount} دج",
                details = "المبلغ المحدث: ${tx.amount} دج | البيان: ${tx.description.ifBlank { tx.type.arabicLabel }}",
                unitId = currentUnitId
            )
        }
        return result
    }

    override suspend fun upsertTransaction(transaction: FinanceTransaction): Result<Unit> {
        val currentUser = authRepository.currentUser
        val currentUnitId = transaction.unitId ?: authRepository.getCurrentLeader()?.unitId
        val exists = if (transaction.id.isNotBlank()) {
            try {
                firestore.collection(FinanceFirestoreDataSource.COLLECTION_TRANSACTIONS)
                    .document(transaction.id)
                    .get()
                    .await()
                    .exists()
            } catch (_: Exception) {
                false
            }
        } else {
            false
        }

        val prepared = transaction.copy(
            unitId = currentUnitId,
            createdBy = if (transaction.createdBy.isNotBlank()) transaction.createdBy else (currentUser?.uid ?: ""),
            createdByName = if (transaction.createdByName.isNotBlank()) transaction.createdByName else (currentUser?.displayName ?: "قائد كشفي"),
            createdAt = if (transaction.createdAt > 0) transaction.createdAt else System.currentTimeMillis()
        )

        return if (exists) {
            updateTransaction(prepared)
        } else {
            addTransaction(prepared).map { }
        }
    }

    override suspend fun deleteTransaction(id: String): Result<Unit> {
        val currentUser = authRepository.currentUser
        val result = dataSource.deleteTransaction(id)
        if (result.isSuccess) {
            val currentUnitId = authRepository.getCurrentLeader()?.unitId
            activityLogDataSource.logActivity(
                userId = currentUser?.uid ?: "",
                userName = currentUser?.displayName ?: "قائد كشفي",
                action = "delete",
                targetType = "finance_transaction",
                targetId = id,
                targetName = "معاملة مالية",
                details = "تم حذف المعاملة المالية",
                unitId = currentUnitId
            )
        }
        return result
    }

    // =============================================================
    // Subscriptions (اشتراكات الأشبال)
    // =============================================================

    override fun observeSubscriptionsForActivity(activityId: String): Flow<List<FinanceSubscription>> {
        return combine(
            dataSource.observeSubscriptionsForActivity(activityId),
            observeActiveScoutIds()
        ) { subscriptions, activeScoutIds ->
            subscriptions.filter { it.scoutId in activeScoutIds }
        }
    }

    override fun observeSubscriptionsForScout(scoutId: String): Flow<List<FinanceSubscription>> {
        return dataSource.observeSubscriptionsForScout(scoutId)
    }

    override fun observeAllSubscriptions(): Flow<List<FinanceSubscription>> {
        return combine(
            dataSource.observeAllSubscriptions(),
            observeActiveScoutIds()
        ) { subscriptions, activeScoutIds ->
            subscriptions.filter { it.scoutId in activeScoutIds }
        }
    }

    override suspend fun upsertSubscription(sub: FinanceSubscription): Result<Unit> {
        val currentUser = authRepository.currentUser
        val existing = dataSource.getSubscription(sub.activityId, sub.scoutId)
        val currentUnitId = sub.unitId ?: existing?.unitId ?: authRepository.getCurrentLeader()?.unitId
        val prepared = sub.copy(
            unitId = currentUnitId,
            debtAmount = (sub.requiredAmount - sub.paidAmount).coerceAtLeast(0),
            recordedBy = if (sub.recordedBy.isNotBlank()) sub.recordedBy else (currentUser?.uid ?: ""),
            recordedByName = if (sub.recordedByName.isNotBlank()) sub.recordedByName else (currentUser?.displayName ?: "قائد كشفي"),
            updatedAt = System.currentTimeMillis()
        )

        val changes = mutableListOf<String>()

        fun isChanged(oldVal: String?, newVal: String?): Boolean {
            return oldVal?.trim().orEmpty() != newVal?.trim().orEmpty()
        }

        if (existing != null) {
            if (existing.paidAmount != prepared.paidAmount) {
                changes.add("المدفوع: من ${existing.paidAmount} دج إلى ${prepared.paidAmount} دج")
            }
            if (existing.requiredAmount != prepared.requiredAmount) {
                changes.add("المطلوب: من ${existing.requiredAmount} دج إلى ${prepared.requiredAmount} دج")
            }
            if (existing.debtAmount != prepared.debtAmount) {
                changes.add("المتبقي: من ${existing.debtAmount} دج إلى ${prepared.debtAmount} دج")
            }
            if (existing.attendanceStatus != prepared.attendanceStatus) {
                changes.add("حالة الحضور: من '${existing.attendanceStatus.arabicLabel}' إلى '${prepared.attendanceStatus.arabicLabel}'")
            }
            if (isChanged(existing.notes, prepared.notes)) {
                changes.add("الملاحظات: من '${existing.notes.orEmpty()}' إلى '${prepared.notes.orEmpty()}'")
            }
            // إذا لم يتم تغيير أي حقل، لا يتم حفظ أي شيء في Firestore ولا يسجل أي عملية في سجل الأنشطة
            if (changes.isEmpty()) {
                return Result.success(Unit)
            }
        }

        val result = dataSource.upsertSubscription(prepared)
        if (result.isSuccess) {
            val statusLabel = when {
                prepared.paidAmount >= prepared.requiredAmount -> "مسدد"
                prepared.paidAmount > 0 -> "مدفوع جزئياً"
                else -> "غير مدفوع"
            }
            val detailsText = if (existing != null) {
                changes.joinToString(" | ")
            } else {
                "تسجيل اشتراك جديد: المطلوب: ${prepared.requiredAmount} دج | المدفوع: ${prepared.paidAmount} دج | الحالة: $statusLabel"
            }
            activityLogDataSource.logActivity(
                userId = currentUser?.uid ?: "",
                userName = currentUser?.displayName ?: "قائد كشفي",
                action = if (existing == null) "add" else "update",
                targetType = "finance_subscription",
                targetId = prepared.id,
                targetName = "اشتراك: ${prepared.scoutFullName}",
                details = detailsText,
                unitId = currentUnitId
            )
        }
        return result
    }

    override suspend fun deleteSubscription(activityId: String, scoutId: String): Result<Unit> {
        val currentUser = authRepository.currentUser
        val existing = dataSource.getSubscription(activityId, scoutId)
        val currentUnitId = existing?.unitId ?: authRepository.getCurrentLeader()?.unitId
        val result = dataSource.deleteSubscription(activityId, scoutId)
        if (result.isSuccess) {
            activityLogDataSource.logActivity(
                userId = currentUser?.uid ?: "",
                userName = currentUser?.displayName ?: "قائد كشفي",
                action = "delete",
                targetType = "finance_subscription",
                targetId = "${activityId}_${scoutId}",
                targetName = "اشتراك شبل",
                details = "تم حذف سجل اشتراك الشبل في النشاط",
                unitId = currentUnitId
            )
        }
        return result
    }

    override suspend fun getRequiredAmountFor(activityId: String, status: SubscriptionStatus): Int {
        val currentUnitId = authRepository.getCurrentLeader()?.unitId
        return try {
            if (activityId.isNotBlank()) {
                val doc = firestore.collection("activities").document(activityId).get().await()
                if (doc.exists()) {
                    val overrideMap = (doc.get("subscriptionOverride") ?: doc.get("subscriptionAmountOverride")) as? Map<*, *>
                    if (overrideMap != null && overrideMap.containsKey(status.key)) {
                        val value = overrideMap[status.key]
                        val amount = when (value) {
                            is Number -> value.toInt()
                            is String -> value.toIntOrNull()
                            else -> null
                        }
                        if (amount != null) return amount
                    }
                }
            }
            val settings = dataSource.getSettings(currentUnitId)
            settings.requiredAmountFor(status)
        } catch (_: Exception) {
            val settings = dataSource.getSettings(currentUnitId)
            settings.requiredAmountFor(status)
        }
    }

    override suspend fun ensureSubscriptionForAttendance(
        activityId: String,
        scoutId: String,
        scoutFullName: String,
        activityTitle: String,
        activityDate: Long,
        attendanceStatus: SubscriptionStatus
    ): Result<Unit> {
        return try {
            val existing = dataSource.getSubscription(activityId, scoutId)
            val required = getRequiredAmountFor(activityId, attendanceStatus)
            val currentUser = authRepository.currentUser
            val leaderUid = currentUser?.uid ?: ""
            val leaderName = currentUser?.displayName ?: "قائد كشفي"

            val currentUnitId = authRepository.getCurrentLeader()?.unitId
            if (existing == null) {
                val newSub = FinanceSubscription(
                    id = "${activityId}_${scoutId}",
                    scoutId = scoutId,
                    scoutFullName = scoutFullName,
                    activityId = activityId,
                    activityTitle = activityTitle,
                    activityDate = activityDate,
                    attendanceStatus = attendanceStatus,
                    requiredAmount = required,
                    paidAmount = 0,
                    debtAmount = required,
                    notes = null,
                    unitId = currentUnitId,
                    recordedAt = System.currentTimeMillis(),
                    recordedBy = leaderUid,
                    recordedByName = leaderName,
                    updatedAt = System.currentTimeMillis()
                )
                upsertSubscription(newSub)
            } else {
                val updatedDebt = (required - existing.paidAmount).coerceAtLeast(0)
                val updatedSub = existing.copy(
                    attendanceStatus = attendanceStatus,
                    requiredAmount = required,
                    debtAmount = updatedDebt,
                    unitId = existing.unitId ?: currentUnitId,
                    updatedAt = System.currentTimeMillis()
                )
                upsertSubscription(updatedSub)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private data class TransactionDebtResult(
        val appliedAmount: Int,
        val updatedCount: Int,
        val firstUpdated: FinanceSubscription?
    )

    override suspend fun payScoutDebts(scoutId: String, amountToPay: Int): Result<Unit> {
        if (amountToPay <= 0 || scoutId.isBlank()) return Result.success(Unit)
        return try {
            val candidateRefs = dataSource.getSubscriptionReferencesForScout(scoutId)
                .filter { (_, sub) -> sub.paidAmount < sub.requiredAmount && sub.debtAmount > 0 }
                .sortedBy { (_, sub) -> sub.activityDate }
                .map { (ref, _) -> ref }

            if (candidateRefs.isEmpty()) return Result.success(Unit)

            val currentUser = authRepository.currentUser
            val leaderUnitId = authRepository.getCurrentLeader()?.unitId
            val now = System.currentTimeMillis()
            var remainingPayment = amountToPay
            var totalPaidApplied = 0
            var totalUpdatedCount = 0
            var firstUpdatedSub: FinanceSubscription? = null

            for (chunk in candidateRefs.chunked(500)) {
                if (remainingPayment <= 0) break
                val chunkTarget = remainingPayment

                val chunkResult = firestore.runTransaction { tx ->
                    // 1. Re-read all candidate documents in this chunk before any writes
                    val freshPairs = chunk.mapNotNull { ref ->
                        val snap = tx.get(ref)
                        if (!snap.exists()) {
                            null
                        } else {
                            dataSource.mapDocumentToSubscription(snap)?.let { sub -> ref to sub }
                        }
                    }
                    val refsById = freshPairs.associate { (ref, sub) -> sub.id to ref }

                    // 2. Allocate payment on fresh snapshot data (oldest first, skips externally paid docs)
                    val allocation = DebtAllocationCalculator.allocatePayment(
                        subscriptions = freshPairs.map { it.second },
                        amountToPay = chunkTarget,
                        now = now
                    )

                    // 3. Apply all writes after all reads
                    val preparedSubs = allocation.updatedSubscriptions.mapNotNull { updated ->
                        val ref = refsById[updated.id] ?: return@mapNotNull null
                        val prepared = updated.copy(
                            unitId = updated.unitId ?: leaderUnitId,
                            recordedBy = updated.recordedBy.ifBlank { currentUser?.uid ?: "" },
                            recordedByName = updated.recordedByName.ifBlank { currentUser?.displayName ?: "قائد كشفي" }
                        )
                        tx.set(ref, dataSource.mapSubscriptionToMap(prepared))
                        prepared
                    }

                    TransactionDebtResult(
                        appliedAmount = allocation.totalApplied,
                        updatedCount = preparedSubs.size,
                        firstUpdated = preparedSubs.firstOrNull()
                    )
                }.await()

                remainingPayment -= chunkResult.appliedAmount
                totalPaidApplied += chunkResult.appliedAmount
                totalUpdatedCount += chunkResult.updatedCount
                if (firstUpdatedSub == null) {
                    firstUpdatedSub = chunkResult.firstUpdated
                }
            }

            if (totalUpdatedCount > 0 && firstUpdatedSub != null) {
                activityLogDataSource.logActivity(
                    userId = currentUser?.uid ?: "",
                    userName = currentUser?.displayName ?: "قائد كشفي",
                    action = "update",
                    targetType = "finance_subscription",
                    targetId = firstUpdatedSub.id,
                    targetName = "تسديد ديون: ${firstUpdatedSub.scoutFullName}",
                    details = "تسديد مبلغ $totalPaidApplied دج عبر $totalUpdatedCount اشتراك(ات)",
                    unitId = firstUpdatedSub.unitId ?: leaderUnitId
                )
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("FinanceRepo", "Error paying scout debts: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun rollbackScoutDebts(
        scoutId: String,
        amountToRollback: Int,
        excludeActivityId: String?
    ): Result<Unit> {
        if (amountToRollback <= 0 || scoutId.isBlank()) return Result.success(Unit)
        return try {
            val candidateRefs = dataSource.getSubscriptionReferencesForScout(scoutId)
                .filter { (_, sub) ->
                    sub.paidAmount > 0 &&
                        (excludeActivityId.isNullOrBlank() || sub.activityId != excludeActivityId)
                }
                .sortedBy { (_, sub) -> sub.activityDate }
                .map { (ref, _) -> ref }

            if (candidateRefs.isEmpty()) return Result.success(Unit)

            val currentUser = authRepository.currentUser
            val leaderUnitId = authRepository.getCurrentLeader()?.unitId
            val now = System.currentTimeMillis()
            var remainingRollback = amountToRollback
            var totalRolledBack = 0
            var totalUpdatedCount = 0
            var firstUpdatedSub: FinanceSubscription? = null

            for (chunk in candidateRefs.chunked(500)) {
                if (remainingRollback <= 0) break
                val chunkTarget = remainingRollback

                val chunkResult = firestore.runTransaction { tx ->
                    // 1. Re-read all candidate documents in this chunk before any writes
                    val freshPairs = chunk.mapNotNull { ref ->
                        val snap = tx.get(ref)
                        if (!snap.exists()) {
                            null
                        } else {
                            dataSource.mapDocumentToSubscription(snap)?.let { sub -> ref to sub }
                        }
                    }
                    val refsById = freshPairs.associate { (ref, sub) -> sub.id to ref }

                    // 2. Allocate rollback on fresh snapshot data (oldest first, preserves excludeActivityId)
                    val allocation = DebtAllocationCalculator.allocateRollback(
                        subscriptions = freshPairs.map { it.second },
                        amountToRollback = chunkTarget,
                        excludeActivityId = excludeActivityId,
                        now = now
                    )

                    // 3. Apply all writes after all reads
                    val preparedSubs = allocation.updatedSubscriptions.mapNotNull { updated ->
                        val ref = refsById[updated.id] ?: return@mapNotNull null
                        val prepared = updated.copy(
                            unitId = updated.unitId ?: leaderUnitId,
                            recordedBy = updated.recordedBy.ifBlank { currentUser?.uid ?: "" },
                            recordedByName = updated.recordedByName.ifBlank { currentUser?.displayName ?: "قائد كشفي" }
                        )
                        tx.set(ref, dataSource.mapSubscriptionToMap(prepared))
                        prepared
                    }

                    TransactionDebtResult(
                        appliedAmount = allocation.totalApplied,
                        updatedCount = preparedSubs.size,
                        firstUpdated = preparedSubs.firstOrNull()
                    )
                }.await()

                remainingRollback -= chunkResult.appliedAmount
                totalRolledBack += chunkResult.appliedAmount
                totalUpdatedCount += chunkResult.updatedCount
                if (firstUpdatedSub == null) {
                    firstUpdatedSub = chunkResult.firstUpdated
                }
            }

            if (totalUpdatedCount > 0 && firstUpdatedSub != null) {
                activityLogDataSource.logActivity(
                    userId = currentUser?.uid ?: "",
                    userName = currentUser?.displayName ?: "قائد كشفي",
                    action = "update",
                    targetType = "finance_subscription",
                    targetId = firstUpdatedSub.id,
                    targetName = "التراجع عن تسديد ديون: ${firstUpdatedSub.scoutFullName}",
                    details = "استرجاع مبلغ $totalRolledBack دج عبر $totalUpdatedCount اشتراك(ات)",
                    unitId = firstUpdatedSub.unitId ?: leaderUnitId
                )
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("FinanceRepo", "Error rolling back scout debts: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun updateSubscriptionDebtManually(subscriptionId: String, newDebtAmount: Int): Result<Unit> {
        if (subscriptionId.isBlank()) {
            return Result.failure(IllegalArgumentException("معرف الاشتراك مفقود"))
        }
        return try {
            val existing = dataSource.getSubscriptionById(subscriptionId)
                ?: return Result.failure(Exception("الاشتراك غير موجود"))

            val currentUser = authRepository.currentUser
            val currentUnitId = existing.unitId ?: authRepository.getCurrentLeader()?.unitId
            val sanitizedDebt = newDebtAmount.coerceAtLeast(0)
            val newPaidAmount = (existing.requiredAmount - sanitizedDebt).coerceAtLeast(0)
            val oldDebt = existing.debtAmount

            val updatedSub = existing.copy(
                paidAmount = newPaidAmount,
                debtAmount = sanitizedDebt,
                unitId = currentUnitId,
                updatedAt = System.currentTimeMillis()
            )
            val result = dataSource.upsertSubscription(updatedSub)
            if (result.isSuccess) {
                activityLogDataSource.logActivity(
                    userId = currentUser?.uid ?: "",
                    userName = currentUser?.displayName ?: "قائد كشفي",
                    action = "manual_finance_adjustment",
                    targetType = "finance_subscription",
                    targetId = subscriptionId,
                    targetName = "${existing.scoutFullName} - ${existing.activityTitle.ifBlank { "نشاط" }}",
                    details = "تعديل يدوي: من $oldDebt إلى $sanitizedDebt",
                    unitId = currentUnitId
                )
            }
            result
        } catch (e: Exception) {
            Log.e("FinanceRepo", "Error updating subscription debt manually: ${e.message}", e)
            Result.failure(e)
        }
    }

    // =============================================================
    // Settings (إعدادات المالية)
    // =============================================================

    override fun observeSettings(): Flow<FinanceSettings> {
        return dataSource.observeSettings()
    }

    override suspend fun updateSettings(settings: FinanceSettings): Result<Unit> {
        val currentUser = authRepository.currentUser
        val currentUnitId = authRepository.getCurrentLeader()?.unitId
        val prepared = settings.copy(
            updatedBy = currentUser?.displayName ?: "قائد كشفي",
            updatedAt = System.currentTimeMillis()
        )
        val result = dataSource.updateSettings(prepared, currentUnitId)
        if (result.isSuccess) {
            activityLogDataSource.logActivity(
                userId = currentUser?.uid ?: "",
                userName = currentUser?.displayName ?: "قائد كشفي",
                action = "update",
                targetType = "finance_settings",
                targetId = if (!currentUnitId.isNullOrBlank()) "${currentUnitId}_defaults" else "defaults",
                targetName = "إعدادات المالية وسلّم الاشتراكات",
                unitId = currentUnitId
            )
        }
        return result
    }

    // =============================================================
    // Computed (الإحصائيات والملخصات المحسوبة)
    // =============================================================

    private fun observeValidActivityIds(unitId: String?): Flow<Set<String>> = callbackFlow {
        val baseQuery = if (!unitId.isNullOrBlank()) {
            firestore.collection("activities").whereEqualTo("unitId", unitId)
        } else {
            firestore.collection("activities")
        }
        val listenerRegistration = baseQuery
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptySet())
                    return@addSnapshotListener
                }
                val ids = snapshot?.documents?.map { it.id }?.toSet() ?: emptySet()
                trySend(ids)
            }
        awaitClose { listenerRegistration.remove() }
    }

    override fun observeSummary(): Flow<FinanceSummary> {
        return combine(
            dataSource.observeAllTransactions(),
            dataSource.observeAllSubscriptions(),
            dataSource.observeSettings(),
            observeActiveScoutIds()
        ) { transactions, subscriptions, settings, activeScoutIds ->
            val validSubscriptions = subscriptions.filter { it.activityId.isNotBlank() }
            computeSummaryInMemory(transactions, validSubscriptions, settings, activeScoutIds)
        }
    }

    override fun observeScoutDebts(): Flow<List<ScoutDebt>> {
        return combine(
            dataSource.observeAllSubscriptions(),
            observeActiveScoutIds()
        ) { subscriptions, activeScoutIds ->
            val validSubs = subscriptions.filter {
                it.activityId.isNotBlank() && it.scoutId in activeScoutIds
            }
            val scoutIds = validSubs.map { it.scoutId }.distinct()
            scoutIds.map { scoutId ->
                val subsForScout = validSubs.filter { it.scoutId == scoutId }
                val fullName = subsForScout.firstOrNull()?.scoutFullName ?: ""
                val totalReq = subsForScout.sumOf { it.requiredAmount }
                val totalPaid = subsForScout.sumOf { it.paidAmount }
                val totalDebt = subsForScout.sumOf { it.debtAmount }
                val unpaidCount = subsForScout.count { it.debtAmount > 0 }
                ScoutDebt(
                    scoutId = scoutId,
                    scoutFullName = fullName,
                    totalRequired = totalReq,
                    totalPaid = totalPaid,
                    totalDebt = totalDebt,
                    unpaidActivitiesCount = unpaidCount
                )
            }.sortedByDescending { it.totalDebt }
        }
    }

    override fun observeTotalOutstandingDebt(): Flow<Int> {
        return combine(
            dataSource.observeAllSubscriptions(),
            observeActiveScoutIds()
        ) { subscriptions, activeScoutIds ->
            val validSubs = subscriptions.filter {
                it.activityId.isNotBlank() && it.scoutId in activeScoutIds
            }
            validSubs.sumOf { it.debtAmount }
        }
    }

    private fun computeSummaryInMemory(
        transactions: List<FinanceTransaction>,
        subscriptions: List<FinanceSubscription>,
        settings: FinanceSettings,
        activeScoutIds: Set<String>
    ): FinanceSummary {
        val activeTransactions = transactions.filter { !it.isRolledBack }

        val donations = activeTransactions
            .filter { it.type == FinanceTransactionType.DONATION }
            .sumOf { it.amount }

        val advancePayments = activeTransactions
            .filter { it.type == FinanceTransactionType.ADVANCE_PAYMENT }
            .sumOf { it.amount }

        val contributions = activeTransactions
            .filter { it.type == FinanceTransactionType.CONTRIBUTION }
            .sumOf { it.amount }

        val txOpeningBalance = activeTransactions
            .filter { it.type == FinanceTransactionType.OPENING_BALANCE }
            .sumOf { it.amount }

        val effectiveOpeningBalance = if (txOpeningBalance > 0) txOpeningBalance else settings.openingBalance

        // إجمالي مبالغ الاشتراكات المدفوعة يظل يشمل السجلات التاريخية للحفاظ على رصيد الخزينة العام دقيقاً
        // (ويتضمن تلقائياً تسديدات الديون DEBT_PAYMENT عبر تحديث paidAmount للاشتراكات القديمة)
        val subscriptionsPaid = subscriptions.sumOf { it.paidAmount }

        val totalIncome = donations + effectiveOpeningBalance + subscriptionsPaid + advancePayments + contributions
        val totalExpenses = activeTransactions
            .filter { it.type == FinanceTransactionType.EXPENSE }
            .sumOf { it.amount }

        val balance = totalIncome - totalExpenses
        val totalDebt = subscriptions
            .filter { it.scoutId in activeScoutIds }
            .sumOf { it.debtAmount }

        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfMonth = cal.timeInMillis

        cal.add(Calendar.MONTH, 1)
        val endOfMonth = cal.timeInMillis

        val monthlyExpenses = activeTransactions
            .filter { it.type == FinanceTransactionType.EXPENSE && it.date in startOfMonth until endOfMonth }
            .sumOf { it.amount }

        val monthlyTxIncome = activeTransactions
            .filter {
                (it.type == FinanceTransactionType.DONATION ||
                    it.type == FinanceTransactionType.ADVANCE_PAYMENT ||
                    it.type == FinanceTransactionType.CONTRIBUTION) &&
                    it.date in startOfMonth until endOfMonth
            }
            .sumOf { it.amount }

        val monthlySubsPaid = subscriptions
            .filter { (it.activityDate in startOfMonth until endOfMonth) || (it.recordedAt in startOfMonth until endOfMonth) }
            .sumOf { it.paidAmount }

        val monthlyIncome = monthlyTxIncome + monthlySubsPaid

        return FinanceSummary(
            totalIncome = totalIncome,
            totalExpenses = totalExpenses,
            balance = balance,
            totalOutstandingDebt = totalDebt,
            monthlyIncome = monthlyIncome,
            monthlyExpenses = monthlyExpenses
        )
    }

    // =============================================================
    // Maintenance & Cascade Deletion (الصيانة والحذف المتتالي)
    // =============================================================

    override suspend fun cleanupOrphanedRecords(): Result<Int> {
        return try {
            // 1. Get all existing activity IDs
            val activitiesSnapshot = firestore.collection("activities").get().await()
            val validActivityIds = activitiesSnapshot.documents.map { it.id }.toSet()

            var deletedCount = 0

            // 2. Check Finance Subscriptions
            val subsSnapshot = firestore.collection("finance_subscriptions").get().await()
            val subsToDelete = subsSnapshot.documents.filter { 
                val actId = it.getString("activityId")
                actId != null && !validActivityIds.contains(actId)
            }
            // Batch delete
            if (subsToDelete.isNotEmpty()) {
                subsToDelete.chunked(500).forEach { chunk ->
                    val batch = firestore.batch()
                    chunk.forEach { batch.delete(it.reference) }
                    batch.commit().await()
                }
                deletedCount += subsToDelete.size
            }



            // 4. Check Finance Transactions linked to deleted activities (activityId or targetId)
            val txSnapshot = firestore.collection("finance_transactions").get().await()
            val txToDelete = txSnapshot.documents.filter { doc ->
                val actId = doc.getString("activityId") ?: doc.getString("targetId")
                !actId.isNullOrBlank() && !validActivityIds.contains(actId)
            }
            if (txToDelete.isNotEmpty()) {
                txToDelete.chunked(500).forEach { chunk ->
                    val batch = firestore.batch()
                    chunk.forEach { batch.delete(it.reference) }
                    batch.commit().await()
                }
                deletedCount += txToDelete.size
            }

            Result.success(deletedCount)
        } catch (e: Exception) {
            Log.e("FinanceCleanup", "Error cleaning orphaned records", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteCollectionsForActivity(activityId: String): Result<Unit> {
        if (activityId.isBlank()) return Result.success(Unit)
        return try {
            val subsSnapshot = firestore.collection("finance_subscriptions")
                .whereEqualTo("activityId", activityId)
                .get()
                .await()



            val txByActivityId = firestore.collection("finance_transactions")
                .whereEqualTo("activityId", activityId)
                .get()
                .await()

            val txByTargetId = firestore.collection("finance_transactions")
                .whereEqualTo("targetId", activityId)
                .get()
                .await()

            val allDocs = (subsSnapshot.documents + txByActivityId.documents + txByTargetId.documents)
                .distinctBy { it.id }

            if (allDocs.isNotEmpty()) {
                allDocs.chunked(500).forEach { chunk ->
                    val batch = firestore.batch()
                    chunk.forEach { doc ->
                        batch.delete(doc.reference)
                    }
                    batch.commit().await()
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
