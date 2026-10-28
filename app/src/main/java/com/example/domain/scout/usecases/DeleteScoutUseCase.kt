package com.example.domain.scout.usecases

import com.example.domain.scout.ScoutRepository

/**
 * حالة استخدام حذف شبل (Delete Scout Use Case)
 */
class DeleteScoutUseCase(
    private val repository: ScoutRepository
) {
    suspend operator fun invoke(id: String): Result<Unit> {
        return repository.deleteScout(id)
    }
}
