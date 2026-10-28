package com.example.domain.evaluation.usecases

import com.example.domain.evaluation.EvaluationRepository

class ArchiveCriteriaUseCase(
    private val repository: EvaluationRepository
) {
    suspend operator fun invoke(id: String): Result<Unit> = repository.archiveCriteria(id)
}
