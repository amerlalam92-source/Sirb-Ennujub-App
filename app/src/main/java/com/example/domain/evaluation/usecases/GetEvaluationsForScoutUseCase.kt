package com.example.domain.evaluation.usecases

import com.example.domain.evaluation.EvaluationRepository
import com.example.domain.evaluation.ScoutEvaluation
import kotlinx.coroutines.flow.Flow

class GetEvaluationsForScoutUseCase(
    private val repository: EvaluationRepository
) {
    operator fun invoke(scoutId: String): Flow<List<ScoutEvaluation>> =
        repository.getEvaluationsForScout(scoutId)
}
