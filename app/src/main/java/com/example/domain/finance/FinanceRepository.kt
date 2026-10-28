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
    suspend fun updateTransaction(tx: FinanceTransaction): Result<Unit>
    suspend fun deleteTransaction(id: String): Result<Unit>

    // === Subscriptions (اشتراكات الأشبال) ===
    fun observeSubscriptionsForActivity(activityId: String): Flow<List<FinanceSubscription>>
    fun observeSubscriptionsForScout(scoutId: String): Flow<List<FinanceSubscription>>
    fun observeAllSubscriptions(): Flow<List<FinanceSubscription>>
    suspend fun upsertSubscription(sub: FinanceSubscription): Result<Unit>
    suspend fun deleteSubscription(activityId: String, scoutId: String): Result<Unit>
    suspend fun getRequiredAmountFor(activityId: String, status: SubscriptionStatus): Int
    suspend fun ensureSubscriptionForAttendance(
        activityId: String,
        scoutId: String,
        scoutFullName: String,
        activityTitle: String,
        activityDate: Long,
        attendanceStatus: SubscriptionStatus
    ): Result<Unit>

    // === Contributions (مساهمات الأنشطة الكبرى) ===
    fun observeContributionsForActivity(activityId: String): Flow<List<FinanceContribution>>
    fun observeContributionsForScout(scoutId: String): Flow<List<FinanceContribution>>
    fun observeAllContributions(): Flow<List<FinanceContribution>>
    suspend fun getContribution(activityId: String, scoutId: String): FinanceContribution?
    suspend fun upsertContribution(contribution: FinanceContribution): Result<Unit>
    suspend fun upsertContributionsBatch(contributions: List<FinanceContribution>): Result<Unit>
    suspend fun deleteContribution(activityId: String, scoutId: String): Result<Unit>
    suspend fun recordContributionPayment(
        activityId: String,
        scoutId: String,
        amountPaidNow: Int,
        notes: String? = null
    ): Result<Unit>

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
