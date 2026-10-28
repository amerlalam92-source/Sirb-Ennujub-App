package com.example.data.finance

import com.example.domain.finance.ExpenseCategory
import com.example.domain.finance.FinanceContribution
import com.example.domain.finance.FinanceSettings
import com.example.domain.finance.FinanceSubscription
import com.example.domain.finance.FinanceTransaction
import com.example.domain.finance.FinanceTransactionType
import com.example.domain.finance.SubscriptionStatus
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * مصدر بيانات المعاملات المالية واشتراكات الأشبال وإعدادات المالية والمساهمات عبر Cloud Firestore
 */
class FinanceFirestoreDataSource(
    private val firestore: FirebaseFirestore
) {
    companion object {
        const val COLLECTION_TRANSACTIONS = "finance_transactions"
        const val COLLECTION_SUBSCRIPTIONS = "finance_subscriptions"
        const val COLLECTION_CONTRIBUTIONS = "finance_contributions"
        const val COLLECTION_SETTINGS = "finance_settings"
        const val DOC_SETTINGS_DEFAULTS = "defaults"
    }

    private val transactionsCollection
        get() = firestore.collection(COLLECTION_TRANSACTIONS)

    private val subscriptionsCollection
        get() = firestore.collection(COLLECTION_SUBSCRIPTIONS)

    private val contributionsCollection
        get() = firestore.collection(COLLECTION_CONTRIBUTIONS)

    private val settingsDoc
        get() = firestore.collection(COLLECTION_SETTINGS).document(DOC_SETTINGS_DEFAULTS)

    // =============================================================
    // Transactions (المعاملات المالية)
    // =============================================================

    fun observeAllTransactions(): Flow<List<FinanceTransaction>> = callbackFlow {
        val listenerRegistration = transactionsCollection
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snapshot?.documents
                    ?.mapNotNull { mapDocumentToTransaction(it) }
                    ?.sortedByDescending { it.date }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { listenerRegistration.remove() }
    }

    fun observeTransactionsBetween(from: Long, to: Long): Flow<List<FinanceTransaction>> = callbackFlow {
        val listenerRegistration = transactionsCollection
            .whereGreaterThanOrEqualTo("date", from)
            .whereLessThanOrEqualTo("date", to)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snapshot?.documents
                    ?.mapNotNull { mapDocumentToTransaction(it) }
                    ?.sortedByDescending { it.date }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { listenerRegistration.remove() }
    }

    fun observeTransactionsByType(type: String): Flow<List<FinanceTransaction>> = callbackFlow {
        val listenerRegistration = transactionsCollection
            .whereEqualTo("type", type)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snapshot?.documents
                    ?.mapNotNull { mapDocumentToTransaction(it) }
                    ?.sortedByDescending { it.date }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { listenerRegistration.remove() }
    }

    suspend fun addTransaction(tx: FinanceTransaction): Result<String> {
        return try {
            val docRef = if (tx.id.isNotBlank()) transactionsCollection.document(tx.id) else transactionsCollection.document()
            val finalTx = tx.copy(
                id = docRef.id,
                createdAt = if (tx.createdAt > 0) tx.createdAt else System.currentTimeMillis()
            )
            docRef.set(mapTransactionToMap(finalTx)).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateTransaction(tx: FinanceTransaction): Result<Unit> {
        if (tx.id.isBlank()) return Result.failure(IllegalArgumentException("معرف المعاملة مفقود"))
        return try {
            transactionsCollection.document(tx.id).set(mapTransactionToMap(tx)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteTransaction(id: String): Result<Unit> {
        return try {
            firestore.collection("finance_transactions")
                .document(id)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FinanceDelete", "Failed to delete tx: $id", e)
            Result.failure(e)
        }
    }

    // =============================================================
    // Subscriptions (اشتراكات الأشبال)
    // =============================================================

    fun observeSubscriptionsForActivity(activityId: String): Flow<List<FinanceSubscription>> = callbackFlow {
        val listenerRegistration = subscriptionsCollection
            .whereEqualTo("activityId", activityId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { mapDocumentToSubscription(it) } ?: emptyList()
                trySend(list)
            }
        awaitClose { listenerRegistration.remove() }
    }

    fun observeSubscriptionsForScout(scoutId: String): Flow<List<FinanceSubscription>> = callbackFlow {
        val listenerRegistration = subscriptionsCollection
            .whereEqualTo("scoutId", scoutId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snapshot?.documents
                    ?.mapNotNull { mapDocumentToSubscription(it) }
                    ?.sortedByDescending { it.activityDate }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { listenerRegistration.remove() }
    }

    fun observeAllSubscriptions(): Flow<List<FinanceSubscription>> = callbackFlow {
        val listenerRegistration = subscriptionsCollection
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { mapDocumentToSubscription(it) } ?: emptyList()
                trySend(list)
            }
        awaitClose { listenerRegistration.remove() }
    }

    suspend fun getSubscription(activityId: String, scoutId: String): FinanceSubscription? {
        val docId = "${activityId}_${scoutId}"
        val doc = subscriptionsCollection.document(docId).get().await()
        return if (doc.exists()) mapDocumentToSubscription(doc) else null
    }

    suspend fun upsertSubscription(sub: FinanceSubscription): Result<Unit> {
        val docId = if (sub.id.isNotBlank()) sub.id else "${sub.activityId}_${sub.scoutId}"
        val finalSub = sub.copy(id = docId)
        return try {
            subscriptionsCollection.document(docId).set(mapSubscriptionToMap(finalSub)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteSubscription(activityId: String, scoutId: String): Result<Unit> {
        val docId = "${activityId}_${scoutId}"
        return try {
            subscriptionsCollection.document(docId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =============================================================
    // Contributions (مساهمات الأنشطة الكبرى)
    // =============================================================

    fun observeAllContributions(): Flow<List<FinanceContribution>> = callbackFlow {
        val listenerRegistration = contributionsCollection
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { mapDocumentToContribution(it) } ?: emptyList()
                trySend(list)
            }
        awaitClose { listenerRegistration.remove() }
    }

    fun observeContributionsForActivity(activityId: String): Flow<List<FinanceContribution>> = callbackFlow {
        val listenerRegistration = contributionsCollection
            .whereEqualTo("activityId", activityId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { mapDocumentToContribution(it) } ?: emptyList()
                trySend(list)
            }
        awaitClose { listenerRegistration.remove() }
    }

    fun observeContributionsForScout(scoutId: String): Flow<List<FinanceContribution>> = callbackFlow {
        val listenerRegistration = contributionsCollection
            .whereEqualTo("scoutId", scoutId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { mapDocumentToContribution(it) } ?: emptyList()
                trySend(list)
            }
        awaitClose { listenerRegistration.remove() }
    }

    suspend fun getContribution(activityId: String, scoutId: String): FinanceContribution? {
        val docId = "${activityId}_${scoutId}"
        val doc = contributionsCollection.document(docId).get().await()
        return if (doc.exists()) mapDocumentToContribution(doc) else null
    }

    suspend fun upsertContribution(contribution: FinanceContribution): Result<Unit> {
        val docId = if (contribution.id.isNotBlank()) contribution.id else "${contribution.activityId}_${contribution.scoutId}"
        val finalContribution = contribution.copy(id = docId)
        return try {
            contributionsCollection.document(docId).set(mapContributionToMap(finalContribution)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun upsertContributionsBatch(contributions: List<FinanceContribution>): Result<Unit> {
        if (contributions.isEmpty()) return Result.success(Unit)
        return try {
            val batch = firestore.batch()
            for (contrib in contributions) {
                val docId = if (contrib.id.isNotBlank()) contrib.id else "${contrib.activityId}_${contrib.scoutId}"
                val finalContrib = contrib.copy(id = docId)
                batch.set(contributionsCollection.document(docId), mapContributionToMap(finalContrib))
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteContribution(activityId: String, scoutId: String): Result<Unit> {
        val docId = "${activityId}_${scoutId}"
        return try {
            contributionsCollection.document(docId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =============================================================
    // Settings (إعدادات المالية)
    // =============================================================

    fun observeSettings(): Flow<FinanceSettings> = callbackFlow {
        val listenerRegistration = settingsDoc.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) {
                val defaults = FinanceSettings(
                    presentAmount = 20,
                    lateAmount = 30,
                    veryLateAmount = 40,
                    absentExcusedAmount = 20,
                    absentUnexcusedAmount = 50,
                    openingBalance = 0,
                    currency = "دج"
                )
                // إذا لم تكن الإعدادات موجودة مسبقاً، نقوم بتهيئتها افتراضياً
                if (snapshot != null && !snapshot.exists()) {
                    settingsDoc.set(mapSettingsToMap(defaults))
                }
                trySend(defaults)
                return@addSnapshotListener
            }
            trySend(mapDocumentToSettings(snapshot))
        }
        awaitClose { listenerRegistration.remove() }
    }

    suspend fun getSettings(): FinanceSettings {
        return try {
            val doc = settingsDoc.get().await()
            if (doc.exists()) {
                mapDocumentToSettings(doc)
            } else {
                val defaults = FinanceSettings(
                    presentAmount = 20,
                    lateAmount = 30,
                    veryLateAmount = 40,
                    absentExcusedAmount = 20,
                    absentUnexcusedAmount = 50,
                    openingBalance = 0,
                    currency = "دج"
                )
                settingsDoc.set(mapSettingsToMap(defaults)).await()
                defaults
            }
        } catch (_: Exception) {
            FinanceSettings(
                presentAmount = 20,
                lateAmount = 30,
                veryLateAmount = 40,
                absentExcusedAmount = 20,
                absentUnexcusedAmount = 50,
                openingBalance = 0,
                currency = "دج"
            )
        }
    }

    suspend fun updateSettings(settings: FinanceSettings): Result<Unit> {
        return try {
            val updated = settings.copy(updatedAt = System.currentTimeMillis())
            settingsDoc.set(mapSettingsToMap(updated)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =============================================================
    // Mappers
    // =============================================================

    private fun mapDocumentToTransaction(doc: DocumentSnapshot): FinanceTransaction {
        val typeKey = doc.getString("type") ?: FinanceTransactionType.EXPENSE.key
        val type = FinanceTransactionType.fromKey(typeKey)
        val categoryKey = doc.getString("category")
        val category = ExpenseCategory.fromKey(categoryKey)
        val amountLong = doc.getLong("amount") ?: 0L

        return FinanceTransaction(
            id = doc.id,
            type = type,
            amount = amountLong.toInt(),
            date = doc.getLong("date") ?: System.currentTimeMillis(),
            description = doc.getString("description") ?: "",
            category = category,
            source = doc.getString("source"),
            createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
            createdBy = doc.getString("createdBy") ?: "",
            createdByName = doc.getString("createdByName") ?: ""
        )
    }

    private fun mapTransactionToMap(tx: FinanceTransaction): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>(
            "id" to tx.id,
            "type" to tx.type.key,
            "amount" to tx.amount,
            "date" to tx.date,
            "description" to tx.description.trim(),
            "createdAt" to tx.createdAt,
            "createdBy" to tx.createdBy,
            "createdByName" to tx.createdByName
        )
        if (tx.type == FinanceTransactionType.EXPENSE && tx.category != null) {
            map["category"] = tx.category.key
        } else {
            map["category"] = null
        }
        if (tx.source != null) {
            map["source"] = tx.source.trim()
        } else {
            map["source"] = null
        }
        return map
    }

    private fun mapDocumentToSubscription(doc: DocumentSnapshot): FinanceSubscription {
        val statusKey = doc.getString("attendanceStatus") ?: SubscriptionStatus.PRESENT.key
        val status = SubscriptionStatus.fromKey(statusKey)
        val req = doc.getLong("requiredAmount")?.toInt() ?: 0
        val paid = doc.getLong("paidAmount")?.toInt() ?: 0
        val computedDebt = (req - paid).coerceAtLeast(0)

        return FinanceSubscription(
            id = doc.id,
            scoutId = doc.getString("scoutId") ?: "",
            scoutFullName = doc.getString("scoutFullName") ?: "",
            activityId = doc.getString("activityId") ?: "",
            activityTitle = doc.getString("activityTitle") ?: "",
            activityDate = doc.getLong("activityDate") ?: 0L,
            attendanceStatus = status,
            requiredAmount = req,
            paidAmount = paid,
            debtAmount = doc.getLong("debtAmount")?.toInt() ?: computedDebt,
            notes = doc.getString("notes"),
            recordedAt = doc.getLong("recordedAt") ?: System.currentTimeMillis(),
            recordedBy = doc.getString("recordedBy") ?: "",
            recordedByName = doc.getString("recordedByName") ?: "",
            updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
        )
    }

    private fun mapSubscriptionToMap(sub: FinanceSubscription): Map<String, Any?> {
        return hashMapOf(
            "id" to sub.id,
            "scoutId" to sub.scoutId,
            "scoutFullName" to sub.scoutFullName,
            "activityId" to sub.activityId,
            "activityTitle" to sub.activityTitle,
            "activityDate" to sub.activityDate,
            "attendanceStatus" to sub.attendanceStatus.key,
            "requiredAmount" to sub.requiredAmount,
            "paidAmount" to sub.paidAmount,
            "debtAmount" to sub.debtAmount,
            "notes" to sub.notes,
            "recordedAt" to sub.recordedAt,
            "recordedBy" to sub.recordedBy,
            "recordedByName" to sub.recordedByName,
            "updatedAt" to sub.updatedAt
        )
    }

    private fun mapDocumentToSettings(doc: DocumentSnapshot): FinanceSettings {
        val rawExcused = doc.getLong("absentExcusedAmount")?.toInt()
        val veryLate = doc.getLong("veryLateAmount")?.toInt()
        // إذا كانت القيمة المحفوظة سابقاً 40 (القيمة الافتراضية القديمة)، يتم تحويلها إلى 20
        val excused = if (rawExcused == null || (rawExcused == 40 && (veryLate == null || veryLate == 40))) 20 else rawExcused
        return FinanceSettings(
            presentAmount = doc.getLong("presentAmount")?.toInt() ?: 20,
            lateAmount = doc.getLong("lateAmount")?.toInt() ?: 30,
            veryLateAmount = veryLate ?: 40,
            absentExcusedAmount = excused,
            absentUnexcusedAmount = doc.getLong("absentUnexcusedAmount")?.toInt() ?: 50,
            openingBalance = doc.getLong("openingBalance")?.toInt() ?: 0,
            currency = doc.getString("currency") ?: "دج",
            updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis(),
            updatedBy = doc.getString("updatedBy") ?: ""
        )
    }

    private fun mapSettingsToMap(settings: FinanceSettings): Map<String, Any?> {
        return hashMapOf(
            "presentAmount" to settings.presentAmount,
            "lateAmount" to settings.lateAmount,
            "veryLateAmount" to settings.veryLateAmount,
            "absentExcusedAmount" to settings.absentExcusedAmount,
            "absentUnexcusedAmount" to settings.absentUnexcusedAmount,
            "openingBalance" to settings.openingBalance,
            "currency" to settings.currency,
            "updatedAt" to settings.updatedAt,
            "updatedBy" to settings.updatedBy
        )
    }

    private fun mapDocumentToContribution(doc: DocumentSnapshot): FinanceContribution {
        val req = doc.getLong("requiredAmount")?.toInt() ?: 0
        val paid = doc.getLong("paidAmount")?.toInt() ?: 0
        val computedDebt = (req - paid).coerceAtLeast(0)

        return FinanceContribution(
            id = doc.id,
            scoutId = doc.getString("scoutId") ?: "",
            scoutFullName = doc.getString("scoutFullName") ?: "",
            activityId = doc.getString("activityId") ?: "",
            activityTitle = doc.getString("activityTitle") ?: "",
            activityDate = doc.getLong("activityDate") ?: 0L,
            requiredAmount = req,
            paidAmount = paid,
            debtAmount = doc.getLong("debtAmount")?.toInt() ?: computedDebt,
            notes = doc.getString("notes"),
            recordedAt = doc.getLong("recordedAt") ?: System.currentTimeMillis(),
            recordedBy = doc.getString("recordedBy") ?: "",
            recordedByName = doc.getString("recordedByName") ?: "",
            updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
        )
    }

    private fun mapContributionToMap(contribution: FinanceContribution): Map<String, Any?> {
        return hashMapOf(
            "id" to contribution.id,
            "scoutId" to contribution.scoutId,
            "scoutFullName" to contribution.scoutFullName,
            "activityId" to contribution.activityId,
            "activityTitle" to contribution.activityTitle,
            "activityDate" to contribution.activityDate,
            "requiredAmount" to contribution.requiredAmount,
            "paidAmount" to contribution.paidAmount,
            "debtAmount" to contribution.debtAmount,
            "notes" to contribution.notes,
            "recordedAt" to contribution.recordedAt,
            "recordedBy" to contribution.recordedBy,
            "recordedByName" to contribution.recordedByName,
            "updatedAt" to contribution.updatedAt
        )
    }
}
