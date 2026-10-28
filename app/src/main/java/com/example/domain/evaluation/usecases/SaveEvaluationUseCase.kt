package com.example.domain.evaluation.usecases

import com.example.domain.evaluation.EvaluationRepository
import com.example.domain.evaluation.ScoutEvaluation

class SaveEvaluationUseCase(
    private val repository: EvaluationRepository
) {
    suspend operator fun invoke(evaluation: ScoutEvaluation): Result<Unit> = repository.saveEvaluation(evaluation)
}
