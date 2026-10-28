package com.example.domain.finance.usecases

import com.example.domain.finance.FinanceRepository
import com.example.domain.finance.FinanceSettings
import com.example.domain.finance.FinanceSummary
import com.example.domain.finance.FinanceTransaction
import com.example.domain.finance.FinanceTransactionType
import com.example.domain.finance.FinanceSubscription
import com.example.domain.finance.ScoutDebt
import kotlinx.coroutines.flow.Flow

// === Transaction Use Cases ===

class ObserveAllTransactionsUseCase(
    private val repository: FinanceRepository
) {
    operator fun invoke(): Flow<List<FinanceTransaction>> = repository.observeAllTransactions()
}

class ObserveTransactionsBetweenUseCase(
    private val repository: FinanceRepository
) {
    operator fun invoke(from: Long, to: Long): Flow<List<FinanceTransaction>> =
        repository.observeTransactionsBetween(from, to)
}

class ObserveTransactionsByTypeUseCase(
    private val repository: FinanceRepository
) {
    operator fun invoke(type: FinanceTransactionType): Flow<List<FinanceTransaction>> =
        repository.observeTransactionsByType(type)
}

class AddTransactionUseCase(
    private val repository: FinanceRepository
) {
    suspend operator fun invoke(tx: FinanceTransaction): Result<String> =
        repository.addTransaction(tx)
}

class UpdateTransactionUseCase(
    private val repository: FinanceRepository
) {
    suspend operator fun invoke(tx: FinanceTransaction): Result<Unit> =
        repository.updateTransaction(tx)
}

class DeleteTransactionUseCase(
    private val repository: FinanceRepository
) {
    suspend operator fun invoke(id: String): Result<Unit> =
        repository.deleteTransaction(id)
}

// === Subscription Use Cases ===

class ObserveSubscriptionsForActivityUseCase(
    private val repository: FinanceRepository
) {
    operator fun invoke(activityId: String): Flow<List<FinanceSubscription>> =
        repository.observeSubscriptionsForActivity(activityId)
}

class ObserveSubscriptionsForScoutUseCase(
    private val repository: FinanceRepository
) {
    operator fun invoke(scoutId: String): Flow<List<FinanceSubscription>> =
        repository.observeSubscriptionsForScout(scoutId)
}

class ObserveAllSubscriptionsUseCase(
    private val repository: FinanceRepository
) {
    operator fun invoke(): Flow<List<FinanceSubscription>> =
        repository.observeAllSubscriptions()
}

class UpsertSubscriptionUseCase(
    private val repository: FinanceRepository
) {
    suspend operator fun invoke(sub: FinanceSubscription): Result<Unit> =
        repository.upsertSubscription(sub)
}

class DeleteSubscriptionUseCase(
    private val repository: FinanceRepository
) {
    suspend operator fun invoke(activityId: String, scoutId: String): Result<Unit> =
        repository.deleteSubscription(activityId, scoutId)
}

// === Settings Use Cases ===

class ObserveFinanceSettingsUseCase(
    private val repository: FinanceRepository
) {
    operator fun invoke(): Flow<FinanceSettings> = repository.observeSettings()
}

class UpdateFinanceSettingsUseCase(
    private val repository: FinanceRepository
) {
    suspend operator fun invoke(settings: FinanceSettings): Result<Unit> =
        repository.updateSettings(settings)
}

// === Summary & Debt Use Cases ===

class ObserveFinanceSummaryUseCase(
    private val repository: FinanceRepository
) {
    operator fun invoke(): Flow<FinanceSummary> = repository.observeSummary()
}

class ObserveScoutDebtsUseCase(
    private val repository: FinanceRepository
) {
    operator fun invoke(): Flow<List<ScoutDebt>> = repository.observeScoutDebts()
}

class ObserveTotalOutstandingDebtUseCase(
    private val repository: FinanceRepository
) {
    operator fun invoke(): Flow<Int> = repository.observeTotalOutstandingDebt()
}
