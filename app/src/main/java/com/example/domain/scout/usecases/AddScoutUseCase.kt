package com.example.domain.scout.usecases

import com.example.domain.scout.DuplicateScoutException
import com.example.domain.scout.Scout
import com.example.domain.scout.ScoutRepository

/**
 * حالة استخدام إضافة شبل جديد (Add Scout Use Case)
 */
class AddScoutUseCase(
    private val repository: ScoutRepository
) {
    suspend operator fun invoke(scout: Scout): Result<String> {
        val duplicate = repository.findDuplicateScout(
            name = scout.name,
            surname = scout.surname,
            guardianName = scout.guardianName,
            excludeScoutId = null
        )
        if (duplicate != null) {
            return Result.failure(DuplicateScoutException(duplicate.fullName))
        }
        return repository.addScout(scout)
    }
}
