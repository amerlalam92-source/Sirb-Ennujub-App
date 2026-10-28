package com.example.domain.evaluation.usecases

import com.example.domain.evaluation.EvaluationCriteria
import com.example.domain.evaluation.EvaluationRepository

class UpdateCriteriaUseCase(
    private val repository: EvaluationRepository
) {
    suspend operator fun invoke(criteria: EvaluationCriteria): Result<Unit> = repository.updateCriteria(criteria)
}
