package com.example.domain.finance

import kotlinx.coroutines.flow.Flow

/**
 * واجهة مستودع الإدارة المالية (Finance Repository Contract)
 */
interface FinanceRepository {

    // === Transactions (المعاملات المالية) ===
    fun observeAllTransactions(): Flow<List<FinanceTransaction>>
    fun observeTransactionsBetween(from: Long, to: Long): Flow<List<FinanceTransaction>>
    fun observeTransactionsByType(type: FinanceTransactionType): Flow<List<FinanceTransaction>>
    suspend fun addTransaction(tx: FinanceTransaction): Result<String>
    suspend fun updateTransaction(
        tx: FinanceTransaction,
        awaitServer: Boolean = true,
        onAsyncFailure: ((Throwable) -> Unit)? = null,
        onServerResult: ((Result<Unit>) -> Unit)? = null
    ): Result<Unit>
    suspend fun upsertTransaction(
        transaction: FinanceTransaction,
        awaitServer: Boolean = true,
        onAsyncFailure: ((Throwable) -> Unit)? = null,
        onServerResult: ((Result<Unit>) -> Unit)? = null
    ): Result<Unit>
    suspend fun deleteTransaction(id: String): Result<Unit>

    // === Subscriptions (اشتراكات الأشبال) ===
    fun observeSubscriptionsForActivity(activityId: String): Flow<List<FinanceSubscription>>
    fun observeSubscriptionsForScout(scoutId: String): Flow<List<FinanceSubscription>>
    fun observeAllSubscriptions(): Flow<List<FinanceSubscription>>
    suspend fun upsertSubscription(
        sub: FinanceSubscription,
        awaitServer: Boolean = true,
        onAsyncFailure: ((Throwable) -> Unit)? = null,
        onServerResult: ((Result<Unit>) -> Unit)? = null
    ): Result<Unit>
    suspend fun deleteSubscription(activityId: String, scoutId: String): Result<Unit>
    suspend fun getRequiredAmountFor(activityId: String, status: SubscriptionStatus): Int
    suspend fun ensureSubscriptionForAttendance(
        activityId: String,
        scoutId: String,
        scoutFullName: String,
        activityTitle: String,
        activityDate: Long,
        attendanceStatus: SubscriptionStatus,
        awaitServer: Boolean = true,
        onAsyncFailure: ((Throwable) -> Unit)? = null,
        onServerResult: ((Result<Unit>) -> Unit)? = null
    ): Result<Unit>
    suspend fun payScoutDebts(
        scoutId: String,
        amountToPay: Int,
        awaitServer: Boolean = true,
        onAsyncFailure: ((Throwable) -> Unit)? = null,
        onServerResult: ((Result<Unit>) -> Unit)? = null
    ): Result<Unit>
    suspend fun rollbackScoutDebts(
        scoutId: String,
        amountToRollback: Int,
        excludeActivityId: String? = null,
        awaitServer: Boolean = true,
        onAsyncFailure: ((Throwable) -> Unit)? = null,
        onServerResult: ((Result<Unit>) -> Unit)? = null
    ): Result<Unit>
    suspend fun updateSubscriptionDebtManually(subscriptionId: String, newDebtAmount: Int): Result<Unit>

    // === Settings (إعدادات المالية) ===
    fun observeSettings(): Flow<FinanceSettings>
    suspend fun updateSettings(settings: FinanceSettings): Result<Unit>

    // === Computed (الإحصائيات والملخصات المحسوبة) ===
    fun observeSummary(): Flow<FinanceSummary>
    fun observeScoutDebts(): Flow<List<ScoutDebt>>
    fun observeTotalOutstandingDebt(): Flow<Int>

    // === Maintenance & Cascade Deletion (الصيانة والحذف المتتالي) ===
    suspend fun cleanupOrphanedRecords(): Result<Int>
    suspend fun deleteCollectionsForActivity(activityId: String): Result<Unit>
}
