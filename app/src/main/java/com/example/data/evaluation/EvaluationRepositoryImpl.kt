package com.example.data.evaluation

import com.example.domain.evaluation.EvaluationCriteria
import com.example.domain.evaluation.EvaluationRepository
import com.example.domain.evaluation.ScoutEvaluation
import kotlinx.coroutines.flow.Flow

/**
 * تطبيق مستودع التقييم الديناميكي (Evaluation Repository Implementation)
 */
class EvaluationRepositoryImpl(
    private val dataSource: EvaluationFirestoreDataSource
) : EvaluationRepository {

    override fun observeCriteria(): Flow<List<EvaluationCriteria>> {
        return dataSource.observeCriteria()
    }

    override suspend fun addCriteria(criteria: EvaluationCriteria): Result<Unit> {
        return dataSource.addCriteria(criteria)
    }

    override suspend fun updateCriteria(criteria: EvaluationCriteria): Result<Unit> {
        return dataSource.updateCriteria(criteria)
    }

    override suspend fun archiveCriteria(id: String): Result<Unit> {
        return dataSource.archiveCriteria(id)
    }

    override suspend fun deleteCriteria(id: String): Result<Unit> {
        return dataSource.deleteCriteria(id)
    }

    override suspend fun saveEvaluation(evaluation: ScoutEvaluation): Result<Unit> {
        val docId = evaluation.id.ifBlank {
            if (evaluation.activityId.startsWith("TERM_")) {
                "${evaluation.scoutId}_${evaluation.activityId}"
            } else {
                "${evaluation.scoutId}_${evaluation.activityId}_${evaluation.leaderId}"
            }
        }
        return dataSource.saveEvaluation(evaluation.copy(id = docId))
    }

    override suspend fun deleteEvaluation(id: String): Result<Unit> {
        return dataSource.deleteEvaluation(id)
    }

    override fun getEvaluationsForActivity(activityId: String): Flow<List<ScoutEvaluation>> {
        return dataSource.getEvaluationsForActivity(activityId)
    }

    override fun getEvaluationsForScout(scoutId: String): Flow<List<ScoutEvaluation>> {
        return dataSource.getEvaluationsForScout(scoutId)
    }

    override fun observeAllEvaluations(): Flow<List<ScoutEvaluation>> {
        return dataSource.observeAllEvaluations()
    }
}
