package com.example.domain.evaluation.usecases

import com.example.domain.evaluation.EvaluationRepository

class DeleteCriteriaUseCase(
    private val repository: EvaluationRepository
) {
    suspend operator fun invoke(id: String): Result<Unit> = repository.deleteCriteria(id)
}
