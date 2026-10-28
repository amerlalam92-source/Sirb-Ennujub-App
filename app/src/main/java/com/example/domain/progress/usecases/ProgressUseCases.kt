package com.example.domain.progress.usecases

import com.example.domain.progress.BadgeLevel
import com.example.domain.progress.BadgeProgress
import com.example.domain.progress.ProgressRepository
import com.example.domain.progress.ProgressRequirement
import com.example.domain.progress.ScoutBadge
import com.example.domain.progress.ScoutProgressEntry
import kotlinx.coroutines.flow.Flow

class ObserveAllRequirementsUseCase(private val repository: ProgressRepository) {
    operator fun invoke(): Flow<List<ProgressRequirement>> = repository.observeAllRequirements()
}

class ObserveRequirementsByLevelUseCase(private val repository: ProgressRepository) {
    operator fun invoke(level: BadgeLevel): Flow<List<ProgressRequirement>> =
        repository.observeRequirementsByLevel(level)
}

class AddRequirementUseCase(private val repository: ProgressRepository) {
    suspend operator fun invoke(requirement: ProgressRequirement): Result<String> =
        repository.addRequirement(requirement)
}

class UpdateRequirementUseCase(private val repository: ProgressRepository) {
    suspend operator fun invoke(requirement: ProgressRequirement): Result<Unit> =
        repository.updateRequirement(requirement)
}

class ArchiveRequirementUseCase(private val repository: ProgressRepository) {
    suspend operator fun invoke(id: String): Result<Unit> = repository.archiveRequirement(id)
}

class UnarchiveRequirementUseCase(private val repository: ProgressRepository) {
    suspend operator fun invoke(id: String): Result<Unit> = repository.unarchiveRequirement(id)
}

class MoveRequirementUseCase(private val repository: ProgressRepository) {
    suspend fun moveUp(id: String): Result<Unit> = repository.moveRequirementUp(id)
    suspend fun moveDown(id: String): Result<Unit> = repository.moveRequirementDown(id)
}

class DeleteRequirementUseCase(private val repository: ProgressRepository) {
    suspend operator fun invoke(id: String): Result<Unit> = repository.deleteRequirement(id)
}

class ObserveBadgeProgressForScoutUseCase(private val repository: ProgressRepository) {
    operator fun invoke(scoutId: String): Flow<List<BadgeProgress>> =
        repository.observeBadgeProgress(scoutId)
}

class SetRequirementCompletedUseCase(private val repository: ProgressRepository) {
    suspend operator fun invoke(
        scoutId: String,
        requirementId: String,
        completed: Boolean,
        completedByName: String? = null
    ): Result<Unit> = repository.setRequirementCompleted(scoutId, requirementId, completed, completedByName)
}

class GrantBadgeUseCase(private val repository: ProgressRepository) {
    suspend operator fun invoke(
        scoutId: String,
        level: BadgeLevel,
        notes: String = "",
        grantedByName: String
    ): Result<Unit> = repository.grantBadge(scoutId, level, notes, grantedByName)
}

class RevokeBadgeUseCase(private val repository: ProgressRepository) {
    suspend operator fun invoke(
        scoutId: String,
        level: BadgeLevel,
        revokedByName: String
    ): Result<Unit> = repository.revokeBadge(scoutId, level, revokedByName)
}

class ObserveScoutProgressUseCase(private val repository: ProgressRepository) {
    operator fun invoke(scoutId: String): Flow<List<ScoutProgressEntry>> =
        repository.observeScoutProgress(scoutId)
}

class ObserveScoutBadgesUseCase(private val repository: ProgressRepository) {
    operator fun invoke(scoutId: String): Flow<List<ScoutBadge>> =
        repository.observeScoutBadges(scoutId)
}
