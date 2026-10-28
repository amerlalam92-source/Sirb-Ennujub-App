package com.example.domain.scout.usecases

import com.example.domain.scout.ScoutRepository

/**
 * حالة استخدام أرشفة شبل (Archive Scout Use Case)
 */
class ArchiveScoutUseCase(
    private val repository: ScoutRepository
) {
    suspend operator fun invoke(id: String): Result<Unit> {
        return repository.archiveScout(id)
    }
}
