package com.example.data.scout

import com.example.data.log.ActivityLogDataSource
import com.example.domain.auth.AuthRepository
import com.example.domain.scout.Scout
import com.example.domain.scout.ScoutRepository
import kotlinx.coroutines.flow.Flow

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
        val preparedScout = scout.copy(
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
                targetName = preparedScout.fullName
            )
        }
        return result
    }

    override suspend fun updateScout(scout: Scout): Result<Unit> {
        val preparedScout = scout.copy(
            updatedBy = currentUserId
        )
        val result = dataSource.updateScout(preparedScout)
        result.onSuccess {
            activityLogDataSource.logActivity(
                userId = currentUserId,
                userName = currentUserName,
                action = "update",
                targetType = "scout",
                targetId = scout.id,
                targetName = scout.fullName
            )
        }
        return result
    }

    override suspend fun archiveScout(id: String): Result<Unit> {
        val scout = dataSource.getScoutById(id)
        val result = dataSource.archiveScout(id, updatedBy = currentUserId)
        result.onSuccess {
            activityLogDataSource.logActivity(
                userId = currentUserId,
                userName = currentUserName,
                action = "archive",
                targetType = "scout",
                targetId = id,
                targetName = scout?.fullName ?: id
            )
        }
        return result
    }

    override suspend fun deleteScout(id: String): Result<Unit> {
        val scout = dataSource.getScoutById(id)
        val result = dataSource.deleteScout(id)
        result.onSuccess {
            activityLogDataSource.logActivity(
                userId = currentUserId,
                userName = currentUserName,
                action = "delete",
                targetType = "scout",
                targetId = id,
                targetName = scout?.fullName ?: id
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
}
