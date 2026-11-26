package com.example.data.scout

import android.util.Log
import com.example.data.common.DataLoadErrorReporter
import com.example.data.common.ListenerFallbackState
import com.example.data.common.attachServerResultListeners
import com.example.data.common.singleShotResultCallback
import com.example.data.firestore.safeInt
import com.example.data.firestore.safeLong
import com.example.data.firestore.safeNullableLong
import com.example.data.firestore.safeNullableString
import com.example.data.firestore.safeString
import com.example.data.firestore.safeStringList
import com.example.domain.scout.Scout
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * مصدر بيانات الأشبال عبر Cloud Firestore (Data Layer)
 * يدعم الاستماع الفوري (Realtime Flow)، والتحقق الصارم، والترحيل التسامحي للحقول السابقة (Legacy Migration).
 */
class ScoutFirestoreDataSource(
    private val firestore: FirebaseFirestore,
    private val errorReporter: DataLoadErrorReporter? = null
) {
    companion object {
        const val COLLECTION_SCOUTS = "scouts"

        internal fun clampCreditBalance(rawBalance: Int): Int = rawBalance.coerceAtLeast(0)

        internal fun buildAdjustCreditBalanceUpdateMap(
            delta: Int,
            updatedAt: Long = System.currentTimeMillis(),
            updatedBy: String = "",
            incrementFactory: (Long) -> Any = FieldValue::increment
        ): Map<String, Any> {
            val updates = mutableMapOf<String, Any>(
                "creditBalance" to incrementFactory(delta.toLong()),
                "updatedAt" to updatedAt
            )
            if (updatedBy.isNotBlank()) {
                updates["updatedBy"] = updatedBy
            }
            return updates
        }
    }

    private val collection
        get() = firestore.collection(COLLECTION_SCOUTS)

    /**
     * الاستماع المباشر لقائمة الأشبال مع التحديث الفوري
     */
    fun observeScouts(@Suppress("UNUSED_PARAMETER") unitId: String? = null): Flow<List<Scout>> = callbackFlow {
        val fallbackState = ListenerFallbackState<Scout>("scouts", errorReporter)
        val listenerRegistration = collection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w("ScoutFirestoreDS", "Error observing scouts", error)
                trySend(fallbackState.onError(error))
                return@addSnapshotListener
            }
            val scouts = snapshot?.documents?.mapNotNull { doc ->
                mapDocumentToScout(doc)
            } ?: emptyList()
            trySend(fallbackState.onSnapshot(scouts))
        }
        awaitClose {
            listenerRegistration.remove()
            fallbackState.clear()
        }
    }

    /**
     * جلب شبل محدد بالمعرف
     */
    suspend fun getScoutById(id: String): Scout? {
        return try {
            val doc = collection.document(id).get().await()
            if (doc.exists()) mapDocumentToScout(doc) else null
        } catch (e: Exception) {
            Log.w("ScoutFirestoreDS", "Error getting scout $id", e)
            null
        }
    }

    /**
     * البحث عن شبل مكرر (مطابقة الاسم واللقب واسم الولي للشبل النشط)
     */
    suspend fun findDuplicateScout(
        name: String,
        surname: String,
        guardianName: String,
        excludeScoutId: String? = null
    ): Scout? {
        val trimmedName = name.trim()
        val trimmedSurname = surname.trim()
        val trimmedGuardian = guardianName.trim()
        if (trimmedName.isBlank() || trimmedSurname.isBlank() || trimmedGuardian.isBlank()) {
            return null
        }

        return try {
            val snapshot = collection.whereEqualTo("name", trimmedName).get().await()
            snapshot.documents.mapNotNull { doc ->
                mapDocumentToScout(doc)
            }.firstOrNull { scout ->
                scout.status == Scout.STATUS_ACTIVE &&
                    scout.surname.trim().equals(trimmedSurname, ignoreCase = false) &&
                    scout.guardianName.trim().equals(trimmedGuardian, ignoreCase = true) &&
                    (excludeScoutId == null || scout.id != excludeScoutId)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * إضافة شبل جديد بعد التحقق من صحة البيانات
     */
    suspend fun addScout(scout: Scout): Result<String> {
        val validation = validateScout(scout)
        if (validation.isFailure) return Result.failure(validation.exceptionOrNull()!!)

        return try {
            val docRef = if (scout.id.isNotBlank()) collection.document(scout.id) else collection.document()
            val finalScout = scout.copy(
                id = docRef.id,
                createdAt = if (scout.createdAt > 0) scout.createdAt else System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            docRef.set(mapScoutToMap(finalScout)).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * تحديث بيانات شبل
     */
    suspend fun updateScout(scout: Scout): Result<Unit> {
        val validation = validateScout(scout)
        if (validation.isFailure) return Result.failure(validation.exceptionOrNull()!!)
        if (scout.id.isBlank()) return Result.failure(IllegalArgumentException("معرف الشبل مفقود"))

        return try {
            val updated = scout.copy(updatedAt = System.currentTimeMillis())
            collection.document(scout.id).set(mapScoutToMap(updated)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * أرشفة شبل بتغيير حالته إلى "archived"
     */
    suspend fun archiveScout(id: String, updatedBy: String = ""): Result<Unit> {
        if (id.isBlank()) return Result.failure(IllegalArgumentException("معرف الشبل مفقود"))
        return try {
            val updates = mutableMapOf<String, Any>(
                "status" to Scout.STATUS_ARCHIVED,
                "updatedAt" to System.currentTimeMillis()
            )
            if (updatedBy.isNotBlank()) {
                updates["updatedBy"] = updatedBy
            }
            collection.document(id).update(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * حذف شبل نهائياً مع تنظيف سجلات التقدم والشارات المرتبطة به
     */
    suspend fun deleteScout(id: String): Result<Unit> {
        if (id.isBlank()) return Result.failure(IllegalArgumentException("معرف الشبل مفقود"))
        return try {
            val relatedCollections = listOf(
                "scout_progress",
                "scout_hobby_progress",
                "scout_badges",
                "scout_hobby_badges"
            )

            val refsToDelete = mutableListOf<com.google.firebase.firestore.DocumentReference>()
            for (collectionName in relatedCollections) {
                val snapshot = firestore.collection(collectionName)
                    .whereEqualTo("scoutId", id)
                    .get()
                    .await()
                snapshot.documents.forEach { refsToDelete.add(it.reference) }
            }

            if (refsToDelete.isNotEmpty()) {
                refsToDelete.distinctBy { it.path }.chunked(450).forEach { chunk ->
                    val batch = firestore.batch()
                    chunk.forEach { ref -> batch.delete(ref) }
                    batch.commit().await()
                }
            }

            collection.document(id).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * تحديث الرصيد المقدم للشبل (Credit Balance)
     */
    suspend fun updateCreditBalance(
        id: String,
        newBalance: Int,
        updatedBy: String = "",
        awaitServer: Boolean = true,
        onAsyncFailure: ((Throwable) -> Unit)? = null,
        onServerResult: ((Result<Unit>) -> Unit)? = null
    ): Result<Unit> {
        val safeOnServerResult = singleShotResultCallback(onServerResult)
        if (id.isBlank()) {
            val err = IllegalArgumentException("معرف الشبل مفقود")
            safeOnServerResult?.invoke(Result.failure(err))
            return Result.failure(err)
        }
        return try {
            val updates = mutableMapOf<String, Any>(
                "creditBalance" to newBalance,
                "updatedAt" to System.currentTimeMillis()
            )
            if (updatedBy.isNotBlank()) {
                updates["updatedBy"] = updatedBy
            }
            val task = collection.document(id).update(updates)
            if (awaitServer) {
                task.await()
                safeOnServerResult?.invoke(Result.success(Unit))
            } else {
                task.attachServerResultListeners(
                    logTag = "ScoutFirestoreDS",
                    failureMessage = "Async failure updating credit balance for $id",
                    onAsyncFailure = onAsyncFailure,
                    onServerResult = safeOnServerResult
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            safeOnServerResult?.invoke(Result.failure(e))
            Result.failure(e)
        }
    }

    /**
     * تعديل الرصيد المقدم للشبل تزايدياً باستخدام FieldValue.increment لتجنب فقدان التحديثات المتزامنة
     */
    suspend fun adjustCreditBalance(
        id: String,
        delta: Int,
        updatedBy: String = "",
        awaitServer: Boolean = true,
        onAsyncFailure: ((Throwable) -> Unit)? = null,
        onServerResult: ((Result<Unit>) -> Unit)? = null
    ): Result<Unit> {
        val safeOnServerResult = singleShotResultCallback(onServerResult)
        if (id.isBlank()) {
            val err = IllegalArgumentException("معرف الشبل مفقود")
            safeOnServerResult?.invoke(Result.failure(err))
            return Result.failure(err)
        }
        if (delta == 0) {
            safeOnServerResult?.invoke(Result.success(Unit))
            return Result.success(Unit)
        }
        return try {
            val updates = buildAdjustCreditBalanceUpdateMap(
                delta = delta,
                updatedAt = System.currentTimeMillis(),
                updatedBy = updatedBy
            )
            val task = collection.document(id).update(updates)
            if (awaitServer) {
                task.await()
                safeOnServerResult?.invoke(Result.success(Unit))
            } else {
                task.attachServerResultListeners(
                    logTag = "ScoutFirestoreDS",
                    failureMessage = "Async failure adjusting credit balance for $id",
                    onAsyncFailure = onAsyncFailure,
                    onServerResult = safeOnServerResult
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            safeOnServerResult?.invoke(Result.failure(e))
            Result.failure(e)
        }
    }

    /**
     * التحقق الصارم من صحة بيانات الشبل وفق قواعد السرب
     */
    private fun validateScout(scout: Scout): Result<Unit> {
        val name = scout.name.trim()
        if (name.isBlank() || name.length < 2) {
            return Result.failure(IllegalArgumentException("الاسم مطلوب ويجب ألا يقل عن حرفين"))
        }

        val surname = scout.surname.trim()
        if (surname.isBlank() || surname.length < 2) {
            return Result.failure(IllegalArgumentException("اللقب مطلوب ويجب ألا يقل عن حرفين"))
        }

        if (scout.birthDate <= 0L) {
            return Result.failure(IllegalArgumentException("تاريخ الميلاد مطلوب"))
        }

        val phone = scout.guardianPhone.trim().replace("+", "").replace("-", "").replace(" ", "")
        if (phone.isBlank()) {
            return Result.failure(IllegalArgumentException("رقم هاتف الولي مطلوب"))
        }
        if (phone.length !in 9..10 || !phone.all { it.isDigit() }) {
            return Result.failure(IllegalArgumentException("رقم هاتف الولي غير صحيح (يجب أن يتكون من 9 إلى 10 أرقام)"))
        }

        for (extraPhone in scout.additionalPhones) {
            val clean = extraPhone.trim().replace("+", "").replace("-", "").replace(" ", "")
            if (clean.isNotBlank()) {
                if (clean.length !in 9..10 || !clean.all { it.isDigit() }) {
                    return Result.failure(IllegalArgumentException("رقم الهاتف الإضافي غير صحيح (يجب أن يتكون من 9 إلى 10 أرقام)"))
                }
            }
        }

        if (scout.guardianRelation.isBlank() || scout.guardianRelation !in Scout.GUARDIAN_RELATIONS) {
            return Result.failure(IllegalArgumentException("صلة القرابة غير صحيحة"))
        }

        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        if (scout.joinYear < 2000 || scout.joinYear > currentYear) {
            return Result.failure(IllegalArgumentException("سنة الالتحاق غير صحيحة (بين 2000 و $currentYear)"))
        }

        return Result.success(Unit)
    }

    /**
     * قراءة وثيقة الشبل مع الترحيل التسامحي للملفات القديمة (Tolerant Migration)
     */
    private fun mapDocumentToScout(doc: DocumentSnapshot): Scout? {
        return try {
            // قراءة الاسم واللقب مع التوافق التراجعي لـ fullName السابق
            var rawName = doc.safeString("name")
            var rawSurname = doc.safeString("surname")

            if (rawName.isBlank() && rawSurname.isBlank()) {
                val legacyFullName = doc.safeString("fullName").trim()
                if (legacyFullName.isNotBlank()) {
                    val parts = legacyFullName.split("\\s+".toRegex(), limit = 2)
                    rawName = parts.getOrNull(0) ?: ""
                    rawSurname = parts.getOrNull(1) ?: ""
                }
            }

            // قراءة سنة الالتحاق مع التوافق التراجعي لـ joinDate السابق
            val rawJoinYearLong = doc.safeNullableLong("joinYear")
            val joinYear: Int = if (rawJoinYearLong != null && rawJoinYearLong > 0) {
                rawJoinYearLong.toInt()
            } else {
                val legacyJoinDate = doc.safeLong("joinDate", 0L)
                if (legacyJoinDate > 0) {
                    val cal = Calendar.getInstance().apply { timeInMillis = legacyJoinDate }
                    cal.get(Calendar.YEAR)
                } else {
                    Calendar.getInstance().get(Calendar.YEAR)
                }
            }

            val relation = doc.safeString("guardianRelation", Scout.RELATION_FATHER)
            val additionalList = doc.safeStringList("additionalPhones")

            Scout(
                id = doc.id,
                name = rawName,
                surname = rawSurname,
                birthDate = doc.safeLong("birthDate", 0L),
                guardianName = doc.safeString("guardianName"),
                guardianPhone = doc.safeString("guardianPhone"),
                additionalPhones = additionalList,
                guardianRelation = if (relation in Scout.GUARDIAN_RELATIONS) relation else Scout.RELATION_FATHER,
                address = doc.safeString("address"),
                school = doc.safeString("school"),
                joinYear = joinYear,
                status = doc.safeString("status", Scout.STATUS_ACTIVE),
                notes = doc.safeString("notes"),
                creditBalance = clampCreditBalance(doc.safeInt("creditBalance", 0)),
                unitId = doc.safeNullableString("unitId"),
                createdAt = doc.safeLong("createdAt", System.currentTimeMillis()),
                updatedAt = doc.safeLong("updatedAt", System.currentTimeMillis()),
                createdBy = doc.safeString("createdBy"),
                updatedBy = doc.safeString("updatedBy")
            )
        } catch (e: Exception) {
            Log.w("ScoutFirestoreDS", "Failed to map scout document ${doc.id}", e)
            null
        }
    }

    /**
     * تحويل كائن الشبل إلى Map لحفظه في Firestore
     */
    private fun mapScoutToMap(scout: Scout): Map<String, Any?> {
        return hashMapOf(
            "id" to scout.id,
            "name" to scout.name.trim(),
            "surname" to scout.surname.trim(),
            "fullName" to scout.fullName, // حفظ fullName للمساعدة في الفهرسة والبحث
            "birthDate" to scout.birthDate,
            "guardianName" to scout.guardianName.trim(),
            "guardianPhone" to scout.guardianPhone.trim(),
            "additionalPhones" to scout.additionalPhones.map { it.trim() }.filter { it.isNotBlank() },
            "guardianRelation" to scout.guardianRelation,
            "address" to scout.address.trim(),
            "school" to scout.school.trim(),
            "joinYear" to scout.joinYear,
            "status" to scout.status,
            "notes" to scout.notes.trim(),
            "creditBalance" to scout.creditBalance,
            "unitId" to scout.unitId,
            "createdAt" to scout.createdAt,
            "updatedAt" to scout.updatedAt,
            "createdBy" to scout.createdBy,
            "updatedBy" to scout.updatedBy
        )
    }
}
