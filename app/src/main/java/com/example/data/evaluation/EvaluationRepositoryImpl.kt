package com.example.data.evaluation

import com.example.data.log.ActivityLogDataSource
import com.example.domain.auth.AuthRepository
import com.example.domain.evaluation.EvaluationCriteria
import com.example.domain.evaluation.EvaluationRepository
import com.example.domain.evaluation.ScoutEvaluation
import kotlinx.coroutines.flow.Flow

/**
 * تطبيق مستودع التقييم الديناميكي (Evaluation Repository Implementation)
 * يربط بين مصدر بيانات التقييم وسجل العمليات.
 */
class EvaluationRepositoryImpl(
    private val dataSource: EvaluationFirestoreDataSource,
    private val activityLogDataSource: ActivityLogDataSource? = null,
    private val authRepository: AuthRepository? = null
) : EvaluationRepository {

    private val currentUserId: String
        get() = authRepository?.currentUser?.uid ?: ""

    private val currentUserName: String
        get() = authRepository?.currentUser?.displayName?.ifBlank { "قائد كشفي" } ?: "قائد كشفي"

    override fun observeCriteria(): Flow<List<EvaluationCriteria>> {
        return dataSource.observeCriteria()
    }

    override suspend fun addCriteria(criteria: EvaluationCriteria): Result<Unit> {
        val result = dataSource.addCriteria(criteria)
        if (result.isSuccess) {
            activityLogDataSource?.logActivity(
                userId = currentUserId,
                userName = currentUserName,
                action = "add",
                targetType = "evaluation",
                targetId = criteria.id,
                targetName = "معيار تقييم: ${criteria.name}"
            )
        }
        return result
    }

    override suspend fun updateCriteria(criteria: EvaluationCriteria): Result<Unit> {
        val result = dataSource.updateCriteria(criteria)
        if (result.isSuccess) {
            activityLogDataSource?.logActivity(
                userId = currentUserId,
                userName = currentUserName,
                action = "update",
                targetType = "evaluation",
                targetId = criteria.id,
                targetName = "معيار تقييم: ${criteria.name}"
            )
        }
        return result
    }

    override suspend fun archiveCriteria(id: String): Result<Unit> {
        val result = dataSource.archiveCriteria(id)
        if (result.isSuccess) {
            activityLogDataSource?.logActivity(
                userId = currentUserId,
                userName = currentUserName,
                action = "archive",
                targetType = "evaluation",
                targetId = id,
                targetName = "معيار تقييم"
            )
        }
        return result
    }

    override suspend fun deleteCriteria(id: String): Result<Unit> {
        val result = dataSource.deleteCriteria(id)
        if (result.isSuccess) {
            activityLogDataSource?.logActivity(
                userId = currentUserId,
                userName = currentUserName,
                action = "delete",
                targetType = "evaluation",
                targetId = id,
                targetName = "معيار تقييم"
            )
        }
        return result
    }

    override suspend fun saveEvaluation(evaluation: ScoutEvaluation): Result<Unit> {
        val currentUnitId = evaluation.unitId ?: authRepository?.getCurrentLeader()?.unitId
        val docId = "${evaluation.scoutId}_${evaluation.activityId}_${evaluation.leaderId}"
        val isNew = evaluation.id.isBlank()
        val result = dataSource.saveEvaluation(evaluation.copy(id = docId, unitId = currentUnitId))
        if (result.isSuccess) {
            activityLogDataSource?.logActivity(
                userId = currentUserId.ifBlank { evaluation.leaderId },
                userName = currentUserName.ifBlank { evaluation.leaderName },
                action = if (isNew) "add" else "update",
                targetType = "evaluation",
                targetId = docId,
                targetName = "تقييم نشاط للشبل",
                unitId = currentUnitId
            )
        }
        return result
    }

    override suspend fun deleteEvaluation(id: String): Result<Unit> {
        val currentUnitId = authRepository?.getCurrentLeader()?.unitId
        val result = dataSource.deleteEvaluation(id)
        if (result.isSuccess) {
            activityLogDataSource?.logActivity(
                userId = currentUserId,
                userName = currentUserName,
                action = "delete",
                targetType = "evaluation",
                targetId = id,
                targetName = "تقييم نشاط للشبل",
                unitId = currentUnitId
            )
        }
        return result
    }

    override fun getEvaluationsForActivity(activityId: String): Flow<List<ScoutEvaluation>> {
        return dataSource.getEvaluationsForActivity(activityId)
    }

    override fun getEvaluationsForActivityByLeader(activityId: String, leaderId: String): Flow<List<ScoutEvaluation>> {
        return dataSource.getEvaluationsForActivityByLeader(activityId, leaderId)
    }

    override fun getEvaluationsForScout(scoutId: String): Flow<List<ScoutEvaluation>> {
        return dataSource.getEvaluationsForScout(scoutId)
    }

    override fun observeAllEvaluations(): Flow<List<ScoutEvaluation>> {
        return dataSource.observeAllEvaluations()
    }

    override suspend fun deleteAllActivityEvaluations(): Result<Unit> {
        val currentUnitId = authRepository?.getCurrentLeader()?.unitId
        val result = dataSource.deleteAllActivityEvaluations()
        if (result.isSuccess) {
            activityLogDataSource?.logActivity(
                userId = currentUserId,
                userName = currentUserName,
                action = "delete",
                targetType = "evaluation",
                targetId = "all_activity_evaluations",
                targetName = "حذف جميع تقييمات الأنشطة القديمة",
                unitId = currentUnitId
            )
        }
        return result
    }
}
