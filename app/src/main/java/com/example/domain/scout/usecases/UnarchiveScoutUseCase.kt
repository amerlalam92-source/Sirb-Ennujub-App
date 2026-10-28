package com.example.domain.scout.usecases

import com.example.domain.scout.Scout
import com.example.domain.scout.ScoutRepository

/**
 * حالة استخدام إلغاء أرشفة شبل وإعادته للحالة النشطة (Unarchive Scout Use Case)
 */
class UnarchiveScoutUseCase(
    private val repository: ScoutRepository
) {
    suspend operator fun invoke(id: String): Result<Unit> {
        val scout = repository.getScoutById(id)
            ?: return Result.failure(IllegalArgumentException("Scout with id $id not found"))
        return repository.updateScout(scout.copy(status = Scout.STATUS_ACTIVE))
    }
}
