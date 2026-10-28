package com.example.domain.scout.usecases

import com.example.domain.scout.DuplicateScoutException
import com.example.domain.scout.Scout
import com.example.domain.scout.ScoutRepository

/**
 * حالة استخدام تحديث بيانات شبل (Update Scout Use Case)
 */
class UpdateScoutUseCase(
    private val repository: ScoutRepository
) {
    suspend operator fun invoke(scout: Scout): Result<Unit> {
        val duplicate = repository.findDuplicateScout(
            name = scout.name,
            surname = scout.surname,
            guardianName = scout.guardianName,
            excludeScoutId = scout.id.ifBlank { null }
        )
        if (duplicate != null) {
            return Result.failure(DuplicateScoutException(duplicate.fullName))
        }
        return repository.updateScout(scout)
    }
}
