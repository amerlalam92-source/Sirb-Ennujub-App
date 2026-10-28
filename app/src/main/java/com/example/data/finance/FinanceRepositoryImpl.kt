package com.example.data.finance

import com.example.data.log.ActivityLogDataSource
import com.example.domain.auth.AuthRepository
import com.example.domain.finance.FinanceContribution
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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * تنفيذ مستودع الإدارة المالية (Finance Repository Implementation)
 * يدير العمليات المالية وحساب الإحصائيات التراكمية وديون الأشبال والاشتراكات.
 */
class FinanceRepositoryImpl(
    private val dataSource: FinanceFirestoreDataSource,
    private val activityLogDataSource: ActivityLogDataSource,
    private val authRepository: AuthRepository,
    private val firestore: FirebaseFirestore
) : FinanceRepository {

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
        val prepared = tx.copy(
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
                targetName = "${prepared.type.arabicLabel}: ${prepared.amount} دج"
            )
        }
        return result
    }

    override suspend fun updateTransaction(tx: FinanceTransaction): Result<Unit> {
        val currentUser = authRepository.currentUser
        val result = dataSource.updateTransaction(tx)
        if (result.isSuccess) {
            activityLogDataSource.logActivity(
                userId = currentUser?.uid ?: "",
                userName = currentUser?.displayName ?: "قائد كشفي",
                action = "update",
                targetType = "finance_transaction",
                targetId = tx.id,
                targetName = "${tx.type.arabicLabel}: ${tx.amount} دج"
            )
        }
        return result
    }

    override suspend fun deleteTransaction(id: String): Result<Unit> {
        return dataSource.deleteTransaction(id)
    }

    // =============================================================
    // Subscriptions (اشتراكات الأشبال)
    // =============================================================

    override fun observeSubscriptionsForActivity(activityId: String): Flow<List<FinanceSubscription>> {
        return dataSource.observeSubscriptionsForActivity(activityId)
    }

    override fun observeSubscriptionsForScout(scoutId: String): Flow<List<FinanceSubscription>> {
        return dataSource.observeSubscriptionsForScout(scoutId)
    }

    override fun observeAllSubscriptions(): Flow<List<FinanceSubscription>> {
        return dataSource.observeAllSubscriptions()
    }

    override suspend fun upsertSubscription(sub: FinanceSubscription): Result<Unit> {
        val currentUser = authRepository.currentUser
        val existing = dataSource.getSubscription(sub.activityId, sub.scoutId)
        val prepared = sub.copy(
            debtAmount = (sub.requiredAmount - sub.paidAmount).coerceAtLeast(0),
            recordedBy = if (sub.recordedBy.isNotBlank()) sub.recordedBy else (currentUser?.uid ?: ""),
            recordedByName = if (sub.recordedByName.isNotBlank()) sub.recordedByName else (currentUser?.displayName ?: "قائد كشفي"),
            updatedAt = System.currentTimeMillis()
        )
        val result = dataSource.upsertSubscription(prepared)
        if (result.isSuccess) {
            activityLogDataSource.logActivity(
                userId = currentUser?.uid ?: "",
                userName = currentUser?.displayName ?: "قائد كشفي",
                action = if (existing == null) "add" else "update",
                targetType = "finance_subscription",
                targetId = prepared.id,
                targetName = "اشتراك: ${prepared.scoutFullName}"
            )
        }
        return result
    }

    override suspend fun deleteSubscription(activityId: String, scoutId: String): Result<Unit> {
        val currentUser = authRepository.currentUser
        val result = dataSource.deleteSubscription(activityId, scoutId)
        if (result.isSuccess) {
            activityLogDataSource.logActivity(
                userId = currentUser?.uid ?: "",
                userName = currentUser?.displayName ?: "قائد كشفي",
                action = "delete",
                targetType = "finance_subscription",
                targetId = "${activityId}_${scoutId}",
                targetName = "اشتراك شبل"
            )
        }
        return result
    }

    override suspend fun getRequiredAmountFor(activityId: String, status: SubscriptionStatus): Int {
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
            val settings = dataSource.getSettings()
            settings.requiredAmountFor(status)
        } catch (_: Exception) {
            val settings = dataSource.getSettings()
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
                    updatedAt = System.currentTimeMillis()
                )
                upsertSubscription(updatedSub)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =============================================================
    // Contributions (مساهمات الأنشطة الكبرى)
    // =============================================================

    override fun observeContributionsForActivity(activityId: String): Flow<List<FinanceContribution>> {
        return dataSource.observeContributionsForActivity(activityId)
    }

    override fun observeContributionsForScout(scoutId: String): Flow<List<FinanceContribution>> {
        return dataSource.observeContributionsForScout(scoutId)
    }

    override fun observeAllContributions(): Flow<List<FinanceContribution>> {
        return dataSource.observeAllContributions()
    }

    override suspend fun getContribution(activityId: String, scoutId: String): FinanceContribution? {
        return dataSource.getContribution(activityId, scoutId)
    }

    override suspend fun upsertContribution(contribution: FinanceContribution): Result<Unit> {
        val currentUser = authRepository.currentUser
        val prepared = contribution.copy(
            updatedAt = System.currentTimeMillis(),
            recordedBy = contribution.recordedBy.ifBlank { currentUser?.uid ?: "" },
            recordedByName = contribution.recordedByName.ifBlank { currentUser?.displayName ?: "قائد كشفي" }
        )
        val result = dataSource.upsertContribution(prepared)
        if (result.isSuccess) {
            activityLogDataSource.logActivity(
                userId = currentUser?.uid ?: "",
                userName = currentUser?.displayName ?: "قائد كشفي",
                action = "upsert",
                targetType = "finance_contribution",
                targetId = prepared.id,
                targetName = "مساهمة ${prepared.scoutFullName} في ${prepared.activityTitle}"
            )
        }
        return result
    }

    override suspend fun upsertContributionsBatch(contributions: List<FinanceContribution>): Result<Unit> {
        if (contributions.isEmpty()) return Result.success(Unit)
        val currentUser = authRepository.currentUser
        val preparedList = contributions.map { contrib ->
            val docId = if (contrib.id.isNotBlank()) contrib.id else "${contrib.activityId}_${contrib.scoutId}"
            contrib.copy(
                id = docId,
                updatedAt = System.currentTimeMillis(),
                recordedBy = contrib.recordedBy.ifBlank { currentUser?.uid ?: "" },
                recordedByName = contrib.recordedByName.ifBlank { currentUser?.displayName ?: "قائد كشفي" }
            )
        }
        val result = dataSource.upsertContributionsBatch(preparedList)
        if (result.isSuccess) {
            activityLogDataSource.logActivity(
                userId = currentUser?.uid ?: "",
                userName = currentUser?.displayName ?: "قائد كشفي",
                action = "upsert_batch",
                targetType = "finance_contribution",
                targetId = preparedList.firstOrNull()?.activityId ?: "",
                targetName = "إضافة ${preparedList.size} مشارك في مساهمات النشاط"
            )
        }
        return result
    }

    override suspend fun deleteContribution(activityId: String, scoutId: String): Result<Unit> {
        val currentUser = authRepository.currentUser
        val existing = dataSource.getContribution(activityId, scoutId)
        val result = dataSource.deleteContribution(activityId, scoutId)
        if (result.isSuccess && existing != null) {
            activityLogDataSource.logActivity(
                userId = currentUser?.uid ?: "",
                userName = currentUser?.displayName ?: "قائد كشفي",
                action = "delete",
                targetType = "finance_contribution",
                targetId = existing.id,
                targetName = "حذف مساهمة ${existing.scoutFullName} في ${existing.activityTitle}"
            )
        }
        return result
    }

    override suspend fun recordContributionPayment(
        activityId: String,
        scoutId: String,
        amountPaidNow: Int,
        notes: String?
    ): Result<Unit> {
        return try {
            val existing = dataSource.getContribution(activityId, scoutId)
                ?: return Result.failure(IllegalStateException("Contribution not found"))
            val currentUser = authRepository.currentUser
            val leaderUid = currentUser?.uid ?: ""
            val leaderName = currentUser?.displayName ?: "قائد كشفي"

            val newPaid = (existing.paidAmount + amountPaidNow).coerceAtMost(existing.requiredAmount)
            val newDebt = (existing.requiredAmount - newPaid).coerceAtLeast(0)
            val updated = existing.copy(
                paidAmount = newPaid,
                debtAmount = newDebt,
                notes = notes ?: existing.notes,
                updatedAt = System.currentTimeMillis()
            )
            dataSource.upsertContribution(updated)

            // تسجيل معاملة إيراد في finance_transactions تلقائياً
            if (amountPaidNow > 0) {
                val tx = FinanceTransaction(
                    type = FinanceTransactionType.CONTRIBUTION,
                    amount = amountPaidNow,
                    date = System.currentTimeMillis(),
                    description = "مساهمة: ${existing.activityTitle} - ${existing.scoutFullName}",
                    source = existing.scoutFullName,
                    createdBy = leaderUid,
                    createdByName = leaderName
                )
                dataSource.addTransaction(tx)
            }

            activityLogDataSource.logActivity(
                userId = leaderUid,
                userName = leaderName,
                action = "payment",
                targetType = "finance_contribution",
                targetId = updated.id,
                targetName = "تسديد مساهمة: ${existing.scoutFullName} ($amountPaidNow دج)"
            )
            Result.success(Unit)
        } catch (e: Exception) {
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
        val prepared = settings.copy(
            updatedBy = currentUser?.displayName ?: "قائد كشفي",
            updatedAt = System.currentTimeMillis()
        )
        val result = dataSource.updateSettings(prepared)
        if (result.isSuccess) {
            activityLogDataSource.logActivity(
                userId = currentUser?.uid ?: "",
                userName = currentUser?.displayName ?: "قائد كشفي",
                action = "update",
                targetType = "finance_settings",
                targetId = "defaults",
                targetName = "إعدادات المالية وسلّم الاشتراكات"
            )
        }
        return result
    }

    // =============================================================
    // Computed (الإحصائيات والملخصات المحسوبة)
    // =============================================================

    private fun observeValidActivityIds(): Flow<Set<String>> = callbackFlow {
        val listenerRegistration = firestore.collection("activities")
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
            dataSource.observeAllContributions(),
            dataSource.observeSettings(),
            observeValidActivityIds()
        ) { transactions, subscriptions, contributions, settings, validActivityIds ->
            val validSubscriptions = subscriptions.filter { it.activityId.isNotBlank() && it.activityId in validActivityIds }
            val validContributions = contributions.filter { it.activityId.isNotBlank() && it.activityId in validActivityIds }
            computeSummaryInMemory(transactions, validSubscriptions, validContributions, settings)
        }
    }

    override fun observeScoutDebts(): Flow<List<ScoutDebt>> {
        return combine(
            dataSource.observeAllSubscriptions(),
            dataSource.observeAllContributions(),
            observeValidActivityIds()
        ) { subscriptions, contributions, validActivityIds ->
            val validSubs = subscriptions.filter { it.activityId.isNotBlank() && it.activityId in validActivityIds }
            val validContribs = contributions.filter { it.activityId.isNotBlank() && it.activityId in validActivityIds }
            val scoutIds = (validSubs.map { it.scoutId } + validContribs.map { it.scoutId }).distinct()
            scoutIds.map { scoutId ->
                val subsForScout = validSubs.filter { it.scoutId == scoutId }
                val contribsForScout = validContribs.filter { it.scoutId == scoutId }
                val fullName = subsForScout.firstOrNull()?.scoutFullName
                    ?: contribsForScout.firstOrNull()?.scoutFullName
                    ?: ""
                val totalReq = subsForScout.sumOf { it.requiredAmount } + contribsForScout.sumOf { it.requiredAmount }
                val totalPaid = subsForScout.sumOf { it.paidAmount } + contribsForScout.sumOf { it.paidAmount }
                val totalDebt = subsForScout.sumOf { it.debtAmount } + contribsForScout.sumOf { it.debtAmount }
                val unpaidCount = subsForScout.count { it.debtAmount > 0 } + contribsForScout.count { it.debtAmount > 0 }
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
            dataSource.observeAllContributions(),
            observeValidActivityIds()
        ) { subscriptions, contributions, validActivityIds ->
            val validSubs = subscriptions.filter { it.activityId.isNotBlank() && it.activityId in validActivityIds }
            val validContribs = contributions.filter { it.activityId.isNotBlank() && it.activityId in validActivityIds }
            validSubs.sumOf { it.debtAmount } + validContribs.sumOf { it.debtAmount }
        }
    }

    private fun computeSummaryInMemory(
        transactions: List<FinanceTransaction>,
        subscriptions: List<FinanceSubscription>,
        contributions: List<FinanceContribution>,
        settings: FinanceSettings
    ): FinanceSummary {
        val donations = transactions
            .filter { it.type == FinanceTransactionType.DONATION }
            .sumOf { it.amount }

        val txContributions = transactions
            .filter { it.type == FinanceTransactionType.CONTRIBUTION }
            .sumOf { it.amount }

        val txOpeningBalance = transactions
            .filter { it.type == FinanceTransactionType.OPENING_BALANCE }
            .sumOf { it.amount }

        val effectiveOpeningBalance = if (txOpeningBalance > 0) txOpeningBalance else settings.openingBalance

        val subscriptionsPaid = subscriptions.sumOf { it.paidAmount }

        val totalIncome = donations + txContributions + effectiveOpeningBalance + subscriptionsPaid
        val totalExpenses = transactions
            .filter { it.type == FinanceTransactionType.EXPENSE }
            .sumOf { it.amount }

        val balance = totalIncome - totalExpenses
        val totalDebt = subscriptions.sumOf { it.debtAmount } + contributions.sumOf { it.debtAmount }

        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfMonth = cal.timeInMillis

        cal.add(Calendar.MONTH, 1)
        val endOfMonth = cal.timeInMillis

        val monthlyExpenses = transactions
            .filter { it.type == FinanceTransactionType.EXPENSE && it.date in startOfMonth until endOfMonth }
            .sumOf { it.amount }

        val monthlyTxIncome = transactions
            .filter { it.isIncome() && it.date in startOfMonth until endOfMonth }
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

            // 3. Check Finance Contributions
            val contribsSnapshot = firestore.collection("finance_contributions").get().await()
            val contribsToDelete = contribsSnapshot.documents.filter { 
                val actId = it.getString("activityId")
                actId != null && !validActivityIds.contains(actId)
            }
            // Batch delete
            if (contribsToDelete.isNotEmpty()) {
                contribsToDelete.chunked(500).forEach { chunk ->
                    val batch = firestore.batch()
                    chunk.forEach { batch.delete(it.reference) }
                    batch.commit().await()
                }
                deletedCount += contribsToDelete.size
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

            val contribsSnapshot = firestore.collection("finance_contributions")
                .whereEqualTo("activityId", activityId)
                .get()
                .await()

            val allDocs = subsSnapshot.documents + contribsSnapshot.documents
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
