package com.example.domain.evaluation.usecases

import com.example.domain.evaluation.EvaluationCriteria
import com.example.domain.evaluation.EvaluationRepository
import kotlinx.coroutines.flow.Flow

class ObserveCriteriaUseCase(
    private val repository: EvaluationRepository
) {
    operator fun invoke(): Flow<List<EvaluationCriteria>> = repository.observeCriteria()
}
