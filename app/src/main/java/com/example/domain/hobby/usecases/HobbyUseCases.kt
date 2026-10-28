package com.example.domain.hobby.usecases

import com.example.domain.hobby.HobbyBadge
import com.example.domain.hobby.HobbyBadgeProgress
import com.example.domain.hobby.HobbyBadgeRepository
import com.example.domain.hobby.HobbyBadgeRequirement
import com.example.domain.hobby.ScoutHobbyBadge
import com.example.domain.hobby.ScoutHobbyProgress
import kotlinx.coroutines.flow.Flow

class ObserveAllHobbyBadgesUseCase(private val repository: HobbyBadgeRepository) {
    operator fun invoke(): Flow<List<HobbyBadge>> = repository.observeAllHobbyBadges()
}

class ObserveHobbyBadgeByIdUseCase(private val repository: HobbyBadgeRepository) {
    operator fun invoke(id: String): Flow<HobbyBadge?> = repository.observeHobbyBadgeById(id)
}

class AddHobbyBadgeUseCase(private val repository: HobbyBadgeRepository) {
    suspend operator fun invoke(badge: HobbyBadge): Result<String> = repository.addHobbyBadge(badge)
}

class UpdateHobbyBadgeUseCase(private val repository: HobbyBadgeRepository) {
    suspend operator fun invoke(badge: HobbyBadge): Result<Unit> = repository.updateHobbyBadge(badge)
}

class ArchiveHobbyBadgeUseCase(private val repository: HobbyBadgeRepository) {
    suspend operator fun invoke(id: String): Result<Unit> = repository.archiveHobbyBadge(id)
}

class UnarchiveHobbyBadgeUseCase(private val repository: HobbyBadgeRepository) {
    suspend operator fun invoke(id: String): Result<Unit> = repository.unarchiveHobbyBadge(id)
}

class MoveHobbyBadgeUseCase(private val repository: HobbyBadgeRepository) {
    suspend fun moveUp(id: String): Result<Unit> = repository.moveHobbyBadgeUp(id)
    suspend fun moveDown(id: String): Result<Unit> = repository.moveHobbyBadgeDown(id)
}

class DeleteHobbyBadgeUseCase(private val repository: HobbyBadgeRepository) {
    suspend operator fun invoke(id: String): Result<Unit> = repository.deleteHobbyBadge(id)
}

class ObserveRequirementsForBadgeUseCase(private val repository: HobbyBadgeRepository) {
    operator fun invoke(badgeId: String): Flow<List<HobbyBadgeRequirement>> =
        repository.observeRequirementsForBadge(badgeId)
}

class ObserveAllHobbyRequirementsUseCase(private val repository: HobbyBadgeRepository) {
    operator fun invoke(): Flow<List<HobbyBadgeRequirement>> = repository.observeAllHobbyRequirements()
}

class AddHobbyRequirementUseCase(private val repository: HobbyBadgeRepository) {
    suspend operator fun invoke(req: HobbyBadgeRequirement): Result<String> =
        repository.addHobbyRequirement(req)
}

class UpdateHobbyRequirementUseCase(private val repository: HobbyBadgeRepository) {
    suspend operator fun invoke(req: HobbyBadgeRequirement): Result<Unit> =
        repository.updateHobbyRequirement(req)
}

class ArchiveHobbyRequirementUseCase(private val repository: HobbyBadgeRepository) {
    suspend operator fun invoke(id: String): Result<Unit> = repository.archiveHobbyRequirement(id)
}

class UnarchiveHobbyRequirementUseCase(private val repository: HobbyBadgeRepository) {
    suspend operator fun invoke(id: String): Result<Unit> = repository.unarchiveHobbyRequirement(id)
}

class MoveHobbyRequirementUseCase(private val repository: HobbyBadgeRepository) {
    suspend fun moveUp(id: String): Result<Unit> = repository.moveHobbyRequirementUp(id)
    suspend fun moveDown(id: String): Result<Unit> = repository.moveHobbyRequirementDown(id)
}

class DeleteHobbyRequirementUseCase(private val repository: HobbyBadgeRepository) {
    suspend operator fun invoke(id: String): Result<Unit> = repository.deleteHobbyRequirement(id)
}

class ObserveHobbyProgressForScoutUseCase(private val repository: HobbyBadgeRepository) {
    operator fun invoke(scoutId: String): Flow<List<ScoutHobbyProgress>> =
        repository.observeHobbyProgressForScout(scoutId)
}

class SetHobbyRequirementCompletedUseCase(private val repository: HobbyBadgeRepository) {
    suspend operator fun invoke(
        scoutId: String,
        requirementId: String,
        badgeId: String,
        completed: Boolean,
        completedByName: String? = null
    ): Result<Unit> = repository.setHobbyRequirementCompleted(scoutId, requirementId, badgeId, completed, completedByName)
}

class ObserveHobbyBadgesForScoutUseCase(private val repository: HobbyBadgeRepository) {
    operator fun invoke(scoutId: String): Flow<List<ScoutHobbyBadge>> =
        repository.observeHobbyBadgesForScout(scoutId)
}

class ObserveAllHobbyBadgesGrantedUseCase(private val repository: HobbyBadgeRepository) {
    operator fun invoke(): Flow<List<ScoutHobbyBadge>> =
        repository.observeAllHobbyBadgesGranted()
}

class GrantHobbyBadgeUseCase(private val repository: HobbyBadgeRepository) {
    suspend operator fun invoke(
        scoutId: String,
        badgeId: String,
        notes: String? = null,
        grantedByName: String? = null
    ): Result<Unit> = repository.grantHobbyBadge(scoutId, badgeId, notes, grantedByName)
}

class RevokeHobbyBadgeUseCase(private val repository: HobbyBadgeRepository) {
    suspend operator fun invoke(
        scoutId: String,
        badgeId: String,
        revokedByName: String? = null
    ): Result<Unit> = repository.revokeHobbyBadge(scoutId, badgeId, revokedByName)
}

class ObserveHobbyBadgeProgressForScoutUseCase(private val repository: HobbyBadgeRepository) {
    operator fun invoke(scoutId: String): Flow<List<HobbyBadgeProgress>> =
        repository.observeHobbyBadgeProgressForScout(scoutId)
}
