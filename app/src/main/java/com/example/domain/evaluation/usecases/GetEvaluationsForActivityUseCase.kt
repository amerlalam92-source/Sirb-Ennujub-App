package com.example.domain.evaluation.usecases

import com.example.domain.evaluation.EvaluationRepository
import com.example.domain.evaluation.ScoutEvaluation
import kotlinx.coroutines.flow.Flow

class GetEvaluationsForActivityUseCase(
    private val repository: EvaluationRepository
) {
    operator fun invoke(activityId: String): Flow<List<ScoutEvaluation>> =
        repository.getEvaluationsForActivity(activityId)
}
