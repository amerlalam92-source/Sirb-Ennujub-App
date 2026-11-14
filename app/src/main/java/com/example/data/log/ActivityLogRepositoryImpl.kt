package com.example.data.log

import com.example.domain.auth.AuthRepository
import com.example.domain.log.ActivityLog
import com.example.domain.log.ActivityLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * تنفيذ مستودع سجل العمليات والتدقيق (Data Layer Implementation)
 * يربط بين مصدر بيانات السجل وإدارة هوية القائد الحالي.
 */
class ActivityLogRepositoryImpl(
    private val dataSource: ActivityLogDataSource,
    private val authRepository: AuthRepository
) : ActivityLogRepository {

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun observeLogs(): Flow<List<ActivityLog>> {
        return authRepository.observeCurrentLeader().flatMapLatest { leader ->
            val unitId = leader?.unitId
            if (unitId.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                dataSource.observeLogs(unitId)
            }
        }
    }

    override suspend fun getLogs(): List<ActivityLog> {
        val unitId = authRepository.getCurrentLeader()?.unitId
        return if (unitId.isNullOrBlank()) {
            emptyList()
        } else {
            dataSource.getLogs(unitId)
        }
    }

    override suspend fun logAction(
        action: String,
        targetType: String,
        targetName: String,
        targetId: String,
        details: String?,
        meta: Map<String, Any?>?
    ) {
        val user = authRepository.currentUser
        val userId = user?.uid ?: ""
        val userName = user?.displayName?.ifBlank { "قائد كشفي" } ?: "قائد كشفي"
        val currentUnitId = authRepository.getCurrentLeader()?.unitId
        dataSource.logActivity(
            userId = userId,
            userName = userName,
            action = action,
            targetType = targetType,
            targetId = targetId,
            targetName = targetName,
            details = details,
            meta = meta,
            unitId = currentUnitId
        )
    }
}
