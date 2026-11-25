package com.example.data.finance

import android.util.Log
import com.example.data.firestore.safeBoolean
import com.example.data.firestore.safeInt
import com.example.data.firestore.safeLong
import com.example.data.firestore.safeNullableInt
import com.example.data.firestore.safeNullableString
import com.example.data.firestore.safeString
import com.example.domain.finance.ExpenseCategory
import com.example.domain.finance.FinanceSettings
import com.example.domain.finance.FinanceSubscription
import com.example.domain.finance.FinanceTransaction
import com.example.domain.finance.FinanceTransactionType
import com.example.domain.finance.SubscriptionStatus
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CancellationException
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
        const val COLLECTION_SETTINGS = "finance_settings"
        const val DOC_SETTINGS_DEFAULTS = "defaults"
    }

    private val transactionsCollection
        get() = firestore.collection(COLLECTION_TRANSACTIONS)

    private val subscriptionsCollection
        get() = firestore.collection(COLLECTION_SUBSCRIPTIONS)

    private fun getSettingsDoc(unitId: String? = null) =
        firestore.collection(COLLECTION_SETTINGS).document(DOC_SETTINGS_DEFAULTS)

    // =============================================================
    // Transactions (المعاملات المالية)
    // =============================================================

    fun observeAllTransactions(unitId: String? = null): Flow<List<FinanceTransaction>> = callbackFlow {
        val listenerRegistration = transactionsCollection
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
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
                    close(error)
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

    fun observeTransactionsByType(type: String, unitId: String? = null): Flow<List<FinanceTransaction>> = callbackFlow {
        val listenerRegistration = transactionsCollection
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents
                    ?.mapNotNull { mapDocumentToTransaction(it) }
                    ?.filter { it.type.key == type }
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

    suspend fun updateTransaction(
        tx: FinanceTransaction,
        awaitServer: Boolean = true,
        onAsyncFailure: ((Throwable) -> Unit)? = null
    ): Result<Unit> {
        if (tx.id.isBlank()) return Result.failure(IllegalArgumentException("معرف المعاملة مفقود"))
        return try {
            val task = transactionsCollection.document(tx.id).set(mapTransactionToMap(tx))
            if (awaitServer) {
                task.await()
            } else if (onAsyncFailure != null) {
                task.addOnFailureListener { e ->
                    Log.e("FinanceFirestoreDS", "Async failure updating transaction ${tx.id}: ${e.message}", e)
                    onAsyncFailure(e)
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTransactionById(id: String): FinanceTransaction? {
        if (id.isBlank()) return null
        return try {
            val doc = transactionsCollection.document(id).get().await()
            if (doc.exists()) mapDocumentToTransaction(doc) else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("FinanceFirestoreDS", "Failed to fetch transaction $id", e)
            null
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

    fun observeSubscriptionsForActivity(activityId: String, unitId: String? = null): Flow<List<FinanceSubscription>> = callbackFlow {
        val listenerRegistration = subscriptionsCollection
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents
                    ?.mapNotNull { mapDocumentToSubscription(it) }
                    ?.filter { it.activityId == activityId }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { listenerRegistration.remove() }
    }

    fun observeSubscriptionsForScout(scoutId: String, unitId: String? = null): Flow<List<FinanceSubscription>> = callbackFlow {
        val listenerRegistration = subscriptionsCollection
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents
                    ?.mapNotNull { mapDocumentToSubscription(it) }
                    ?.filter { it.scoutId == scoutId }
                    ?.sortedByDescending { it.activityDate }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { listenerRegistration.remove() }
    }

    fun observeAllSubscriptions(unitId: String? = null): Flow<List<FinanceSubscription>> = callbackFlow {
        val listenerRegistration = subscriptionsCollection
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { mapDocumentToSubscription(it) } ?: emptyList()
                trySend(list)
            }
        awaitClose { listenerRegistration.remove() }
    }

    suspend fun getSubscription(activityId: String, scoutId: String): FinanceSubscription? {
        return try {
            val docId = "${activityId}_${scoutId}"
            val doc = subscriptionsCollection.document(docId).get().await()
            if (doc.exists()) mapDocumentToSubscription(doc) else null
        } catch (e: Exception) {
            Log.w("FinanceFirestoreDS", "Error getting subscription ${activityId}_$scoutId", e)
            null
        }
    }

    suspend fun getSubscriptionById(subscriptionId: String): FinanceSubscription? {
        if (subscriptionId.isBlank()) return null
        return try {
            val doc = subscriptionsCollection.document(subscriptionId).get().await()
            if (doc.exists()) mapDocumentToSubscription(doc) else null
        } catch (e: Exception) {
            Log.w("FinanceFirestoreDS", "Error getting subscription by id $subscriptionId", e)
            null
        }
    }

    suspend fun getSubscriptionReferencesForScout(scoutId: String): List<Pair<DocumentReference, FinanceSubscription>> {
        val snapshot = subscriptionsCollection
            .whereEqualTo("scoutId", scoutId)
            .get()
            .await()
        return snapshot.documents
            .mapNotNull { doc ->
                mapDocumentToSubscription(doc)?.let { sub -> doc.reference to sub }
            }
            .sortedBy { it.second.activityDate }
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
    // Settings (إعدادات المالية)
    // =============================================================

    fun observeSettings(unitId: String? = null): Flow<FinanceSettings> = callbackFlow {
        val docRef = getSettingsDoc(unitId)
        val listenerRegistration = docRef.addSnapshotListener { snapshot, error ->
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
                    docRef.set(mapSettingsToMap(defaults))
                }
                trySend(defaults)
                return@addSnapshotListener
            }
            try {
                trySend(mapDocumentToSettings(snapshot))
            } catch (e: Exception) {
                Log.w("FinanceFirestoreDS", "Error mapping finance settings", e)
                trySend(FinanceSettings())
            }
        }
        awaitClose { listenerRegistration.remove() }
    }

    suspend fun getSettings(unitId: String? = null): FinanceSettings {
        val docRef = getSettingsDoc(unitId)
        return try {
            val doc = docRef.get().await()
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
                docRef.set(mapSettingsToMap(defaults)).await()
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

    suspend fun updateSettings(settings: FinanceSettings, unitId: String? = null): Result<Unit> {
        return try {
            val updated = settings.copy(updatedAt = System.currentTimeMillis())
            getSettingsDoc(unitId).set(mapSettingsToMap(updated)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =============================================================
    // Mappers
    // =============================================================

    private fun mapDocumentToTransaction(doc: DocumentSnapshot): FinanceTransaction? {
        return try {
            val typeKey = doc.safeString("type", FinanceTransactionType.EXPENSE.key)
            val type = FinanceTransactionType.fromKey(typeKey)
            val categoryKey = doc.safeNullableString("category")
            val category = ExpenseCategory.fromKey(categoryKey)
            val now = System.currentTimeMillis()

            FinanceTransaction(
                id = doc.id,
                type = type,
                amount = doc.safeInt("amount", 0),
                date = doc.safeLong("date", now),
                description = doc.safeString("description"),
                category = category,
                source = doc.safeNullableString("source"),
                scoutId = doc.safeNullableString("scoutId"),
                relatedActivityId = doc.safeNullableString("relatedActivityId"),
                unitId = doc.safeNullableString("unitId"),
                createdAt = doc.safeLong("createdAt", now),
                createdBy = doc.safeString("createdBy"),
                createdByName = doc.safeString("createdByName"),
                isRolledBack = doc.safeBoolean("isRolledBack", false)
            )
        } catch (e: Exception) {
            Log.w("FinanceFirestoreDS", "Failed to map transaction ${doc.id}", e)
            null
        }
    }

    internal fun mapTransactionToMap(tx: FinanceTransaction): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>(
            "id" to tx.id,
            "type" to tx.type.key,
            "amount" to tx.amount,
            "date" to tx.date,
            "description" to tx.description.trim(),
            "scoutId" to tx.scoutId,
            "relatedActivityId" to tx.relatedActivityId,
            "unitId" to tx.unitId,
            "createdAt" to tx.createdAt,
            "createdBy" to tx.createdBy,
            "createdByName" to tx.createdByName,
            "isRolledBack" to tx.isRolledBack
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

    internal fun mapDocumentToSubscription(doc: DocumentSnapshot): FinanceSubscription? {
        return try {
            val statusKey = doc.safeNullableString("attendanceStatus")
                ?: doc.safeNullableString("status")
                ?: SubscriptionStatus.PRESENT.key
            val status = SubscriptionStatus.fromKey(statusKey)
            val req = doc.safeInt("requiredAmount", 0)
            val paid = doc.safeInt("paidAmount", 0)
            val computedDebt = (req - paid).coerceAtLeast(0)
            val debt = doc.safeNullableInt("debtAmount") ?: computedDebt

            val actDate = doc.safeLong("activityDate", 0L)
            val recAt = doc.safeLong("recordedAt", System.currentTimeMillis())
            val updAt = doc.safeLong("updatedAt", recAt)

            val parts = doc.id.split("_")
            val fallbackActivityId = if (parts.size >= 2) parts[0] else ""
            val fallbackScoutId = if (parts.size >= 2) parts.subList(1, parts.size).joinToString("_") else ""

            val rawActivityId = doc.safeString("activityId")
            val effectiveActivityId = rawActivityId.ifBlank { fallbackActivityId }

            val rawScoutId = doc.safeString("scoutId")
            val effectiveScoutId = rawScoutId.ifBlank { fallbackScoutId }

            FinanceSubscription(
                id = doc.id,
                scoutId = effectiveScoutId,
                scoutFullName = doc.safeNullableString("scoutFullName") ?: doc.safeString("scoutName"),
                activityId = effectiveActivityId,
                activityTitle = doc.safeNullableString("activityTitle") ?: doc.safeString("title"),
                activityDate = actDate,
                attendanceStatus = status,
                requiredAmount = req,
                paidAmount = paid,
                debtAmount = debt,
                notes = doc.safeNullableString("notes"),
                unitId = doc.safeNullableString("unitId"),
                recordedAt = recAt,
                recordedBy = doc.safeString("recordedBy"),
                recordedByName = doc.safeString("recordedByName"),
                updatedAt = updAt
            )
        } catch (e: Exception) {
            Log.w("FinanceFirestoreDS", "Failed to map subscription ${doc.id}", e)
            null
        }
    }

    internal fun mapSubscriptionToMap(sub: FinanceSubscription): Map<String, Any?> {
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
            "unitId" to sub.unitId,
            "recordedAt" to sub.recordedAt,
            "recordedBy" to sub.recordedBy,
            "recordedByName" to sub.recordedByName,
            "updatedAt" to sub.updatedAt
        )
    }

    private fun mapDocumentToSettings(doc: DocumentSnapshot): FinanceSettings {
        return try {
            val rawExcused = doc.safeNullableInt("absentExcusedAmount")
            val veryLate = doc.safeNullableInt("veryLateAmount")
            // إذا كانت القيمة المحفوظة سابقاً 40 (القيمة الافتراضية القديمة)، يتم تحويلها إلى 20
            val excused = if (rawExcused == null || (rawExcused == 40 && (veryLate == null || veryLate == 40))) 20 else rawExcused
            FinanceSettings(
                presentAmount = doc.safeInt("presentAmount", 20),
                lateAmount = doc.safeInt("lateAmount", 30),
                veryLateAmount = veryLate ?: 40,
                absentExcusedAmount = excused,
                absentUnexcusedAmount = doc.safeInt("absentUnexcusedAmount", 50),
                openingBalance = doc.safeInt("openingBalance", 0),
                currency = doc.safeString("currency", "دج"),
                updatedAt = doc.safeLong("updatedAt", System.currentTimeMillis()),
                updatedBy = doc.safeString("updatedBy")
            )
        } catch (e: Exception) {
            Log.w("FinanceFirestoreDS", "Failed to map finance settings", e)
            FinanceSettings()
        }
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

}
