package com.example.data.scout

import com.example.data.log.ActivityLogDataSource
import com.example.domain.auth.AuthRepository
import com.example.domain.scout.Scout
import com.example.domain.scout.ScoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * تنفيذ مستودع الأشبال (Data Layer Implementation)
 * ينسق بين مصدر بيانات Firestore وسجل الأنشطة وإدارة هوية القائد الحالي.
 */
class ScoutRepositoryImpl(
    private val dataSource: ScoutFirestoreDataSource,
    private val activityLogDataSource: ActivityLogDataSource,
    private val authRepository: AuthRepository
) : ScoutRepository {

    private val currentUserId: String
        get() = authRepository.currentUser?.uid ?: ""

    private val currentUserName: String
        get() = authRepository.currentUser?.displayName ?: "قائد كشفي"

    override fun observeScouts(): Flow<List<Scout>> {
        return dataSource.observeScouts()
    }

    override suspend fun getScoutById(id: String): Scout? {
        return dataSource.getScoutById(id)
    }

    override suspend fun addScout(scout: Scout): Result<String> {
        val currentUnitId = scout.unitId ?: authRepository.getCurrentLeader()?.unitId
        val preparedScout = scout.copy(
            unitId = currentUnitId,
            createdBy = if (scout.createdBy.isNotBlank()) scout.createdBy else currentUserId,
            updatedBy = if (scout.updatedBy.isNotBlank()) scout.updatedBy else currentUserId
        )
        val result = dataSource.addScout(preparedScout)
        result.onSuccess { docId ->
            activityLogDataSource.logActivity(
                userId = currentUserId,
                userName = currentUserName,
                action = "add",
                targetType = "scout",
                targetId = docId,
                targetName = preparedScout.fullName,
                details = "تم إضافة الشبل: ${preparedScout.fullName} - العمر: ${preparedScout.age}",
                unitId = currentUnitId
            )
        }
        return result
    }

    override suspend fun updateScout(scout: Scout): Result<Unit> {
        val old = dataSource.getScoutById(scout.id) ?: return Result.failure(Exception("الشبل غير موجود"))

        val changes = mutableListOf<String>()

        fun isChanged(oldVal: String?, newVal: String?): Boolean {
            return oldVal?.trim().orEmpty() != newVal?.trim().orEmpty()
        }

        if (isChanged(old.name, scout.name)) {
            changes.add("الاسم: من '${old.name}' إلى '${scout.name}'")
        }
        if (isChanged(old.surname, scout.surname)) {
            changes.add("اللقب: من '${old.surname}' إلى '${scout.surname}'")
        }
        if (isChanged(old.guardianName, scout.guardianName)) {
            changes.add("اسم الولي: من '${old.guardianName}' إلى '${scout.guardianName}'")
        }
        if (isChanged(old.guardianPhone, scout.guardianPhone)) {
            changes.add("هاتف الولي: من '${old.guardianPhone}' إلى '${scout.guardianPhone}'")
        }
        if (isChanged(old.guardianRelation, scout.guardianRelation)) {
            changes.add("صلة القرابة: من '${old.guardianRelation}' إلى '${scout.guardianRelation}'")
        }
        if (isChanged(old.address, scout.address)) {
            changes.add("العنوان: من '${old.address}' إلى '${scout.address}'")
        }
        if (isChanged(old.school, scout.school)) {
            changes.add("المدرسة: من '${old.school}' إلى '${scout.school}'")
        }
        if (isChanged(old.notes, scout.notes)) {
            changes.add("ملاحظات: من '${old.notes}' إلى '${scout.notes}'")
        }
        if (isChanged(old.status, scout.status)) {
            changes.add("الحالة: من '${old.status}' إلى '${scout.status}'")
        }

        // Dates, Ints, and Lists
        if (old.birthDate != scout.birthDate) changes.add("تاريخ الميلاد تم تعديله")
        if (old.joinYear != scout.joinYear) changes.add("سنة الالتحاق: من ${old.joinYear} إلى ${scout.joinYear}")
        val oldPhones = old.additionalPhones.map { it.trim() }.filter { it.isNotEmpty() }
        val newPhones = scout.additionalPhones.map { it.trim() }.filter { it.isNotEmpty() }
        if (oldPhones != newPhones) changes.add("أرقام الهواتف الإضافية تم تعديلها")

        // إذا لم يتم تغيير أي حقل، لا يتم حفظ أي شيء في Firestore ولا يسجل أي عملية في سجل الأنشطة
        if (changes.isEmpty()) {
            return Result.success(Unit)
        }

        val currentUnitId = scout.unitId ?: old.unitId ?: authRepository.getCurrentLeader()?.unitId
        val preparedScout = scout.copy(
            unitId = currentUnitId,
            updatedBy = currentUserId
        )
        val result = dataSource.updateScout(preparedScout)
        result.onSuccess {
            val detailsText = changes.joinToString(" | ")
            activityLogDataSource.logActivity(
                userId = currentUserId,
                userName = currentUserName,
                action = "update",
                targetType = "scout",
                targetId = scout.id,
                targetName = scout.fullName,
                details = detailsText,
                unitId = currentUnitId
            )
        }
        return result
    }

    override suspend fun archiveScout(id: String): Result<Unit> {
        val scout = dataSource.getScoutById(id)
        val currentUnitId = scout?.unitId ?: authRepository.getCurrentLeader()?.unitId
        val result = dataSource.archiveScout(id, updatedBy = currentUserId)
        result.onSuccess {
            val scoutName = scout?.fullName ?: id
            activityLogDataSource.logActivity(
                userId = currentUserId,
                userName = currentUserName,
                action = "archive",
                targetType = "scout",
                targetId = id,
                targetName = scoutName,
                details = "تم أرشفة الشبل: $scoutName",
                unitId = currentUnitId
            )
        }
        return result
    }

    override suspend fun deleteScout(id: String): Result<Unit> {
        val scout = dataSource.getScoutById(id)
        val currentUnitId = scout?.unitId ?: authRepository.getCurrentLeader()?.unitId
        val result = dataSource.deleteScout(id)
        result.onSuccess {
            val scoutName = scout?.fullName ?: id
            activityLogDataSource.logActivity(
                userId = currentUserId,
                userName = currentUserName,
                action = "delete",
                targetType = "scout",
                targetId = id,
                targetName = scoutName,
                details = "تم حذف الشبل: $scoutName",
                unitId = currentUnitId
            )
        }
        return result
    }

    override suspend fun findDuplicateScout(
        name: String,
        surname: String,
        guardianName: String,
        excludeScoutId: String?
    ): Scout? {
        return dataSource.findDuplicateScout(name, surname, guardianName, excludeScoutId)
    }

    override suspend fun addCreditToScout(
        scoutId: String,
        amount: Int,
        awaitServer: Boolean,
        onAsyncFailure: ((Throwable) -> Unit)?
    ): Result<Unit> {
        if (scoutId.isBlank() || amount == 0) return Result.success(Unit)
        val scout = if (awaitServer) {
            dataSource.getScoutById(scoutId)
        } else {
            dataSource.observeScouts().first().find { it.id == scoutId }
        } ?: return Result.failure(Exception("الشبل غير موجود"))
        val newBalance = (scout.creditBalance + amount).coerceAtLeast(0)
        val result = dataSource.updateCreditBalance(
            id = scoutId,
            newBalance = newBalance,
            updatedBy = currentUserId,
            awaitServer = awaitServer,
            onAsyncFailure = onAsyncFailure
        )
        result.onSuccess {
            activityLogDataSource.logActivity(
                userId = currentUserId,
                userName = currentUserName,
                action = "update",
                targetType = "scout",
                targetId = scoutId,
                targetName = scout.fullName,
                details = "تحديث الرصيد المقدم: من ${scout.creditBalance} دج إلى $newBalance دج",
                unitId = scout.unitId ?: authRepository.getCurrentLeader()?.unitId
            )
        }
        return result
    }

    override suspend fun deductCreditFromScout(
        scoutId: String,
        amount: Int,
        awaitServer: Boolean,
        onAsyncFailure: ((Throwable) -> Unit)?
    ): Result<Unit> {
        if (scoutId.isBlank() || amount <= 0) return Result.success(Unit)
        val scout = if (awaitServer) {
            dataSource.getScoutById(scoutId)
        } else {
            dataSource.observeScouts().first().find { it.id == scoutId }
        } ?: return Result.failure(Exception("الشبل غير موجود"))
        val newBalance = (scout.creditBalance - amount).coerceAtLeast(0)
        if (newBalance == scout.creditBalance) return Result.success(Unit)
        val result = dataSource.updateCreditBalance(
            id = scoutId,
            newBalance = newBalance,
            updatedBy = currentUserId,
            awaitServer = awaitServer,
            onAsyncFailure = onAsyncFailure
        )
        result.onSuccess {
            activityLogDataSource.logActivity(
                userId = currentUserId,
                userName = currentUserName,
                action = "update",
                targetType = "scout",
                targetId = scoutId,
                targetName = scout.fullName,
                details = "خصم من الرصيد المقدم: من ${scout.creditBalance} دج إلى $newBalance دج",
                unitId = scout.unitId ?: authRepository.getCurrentLeader()?.unitId
            )
        }
        return result
    }

    override suspend fun updateCreditManually(scoutId: String, newBalance: Int): Result<Unit> {
        if (scoutId.isBlank()) {
            return Result.failure(IllegalArgumentException("معرف الشبل مفقود"))
        }
        val sanitizedBalance = newBalance.coerceAtLeast(0)
        val scout = dataSource.getScoutById(scoutId)
            ?: return Result.failure(Exception("الشبل غير موجود"))
        val oldBalance = scout.creditBalance
        val result = dataSource.updateCreditBalance(scoutId, sanitizedBalance, updatedBy = currentUserId)
        result.onSuccess {
            activityLogDataSource.logActivity(
                userId = currentUserId,
                userName = currentUserName,
                action = "manual_finance_adjustment",
                targetType = "scout",
                targetId = scoutId,
                targetName = scout.fullName,
                details = "تعديل يدوي: من $oldBalance إلى $sanitizedBalance",
                unitId = scout.unitId ?: authRepository.getCurrentLeader()?.unitId
            )
        }
        return result
    }
}
