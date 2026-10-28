package com.example.data.hobby

import com.example.data.log.ActivityLogDataSource
import com.example.domain.auth.AuthRepository
import com.example.domain.hobby.HobbyBadge
import com.example.domain.hobby.HobbyBadgeProgress
import com.example.domain.hobby.HobbyBadgeRepository
import com.example.domain.hobby.HobbyBadgeRequirement
import com.example.domain.hobby.HobbyRequirementWithState
import com.example.domain.hobby.ScoutHobbyBadge
import com.example.domain.hobby.ScoutHobbyProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlin.math.floor

/**
 * تنفيذ مستودع شارات الهواية (HobbyBadgeRepositoryImpl)
 */
class HobbyBadgeRepositoryImpl(
    private val dataSource: HobbyBadgeFirestoreDataSource,
    private val logDataSource: ActivityLogDataSource,
    private val authRepository: AuthRepository
) : HobbyBadgeRepository {

    // -------------------------------------------------------------
    // CATALOG
    // -------------------------------------------------------------

    override fun observeAllHobbyBadges(): Flow<List<HobbyBadge>> =
        dataSource.observeAllHobbyBadges()

    override fun observeHobbyBadgeById(id: String): Flow<HobbyBadge?> =
        dataSource.observeHobbyBadgeById(id)

    override suspend fun addHobbyBadge(badge: HobbyBadge): Result<String> {
        return try {
            val user = authRepository.currentUser
            val userId = user?.uid ?: ""
            val userName = user?.displayName ?: "قائد"
            val maxOrder = dataSource.getMaxBadgeOrder()
            val toSave = badge.copy(
                order = if (badge.order > 0) badge.order else maxOrder + 1,
                createdBy = userId,
                updatedBy = userId
            )
            val id = dataSource.addHobbyBadge(toSave)
            logDataSource.logActivity(
                userId = userId,
                userName = userName,
                action = "add",
                targetType = "hobby_badge",
                targetId = id,
                targetName = toSave.name
            )
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateHobbyBadge(badge: HobbyBadge): Result<Unit> {
        return try {
            val user = authRepository.currentUser
            val userId = user?.uid ?: ""
            val userName = user?.displayName ?: "قائد"
            val toSave = badge.copy(updatedBy = userId)
            dataSource.updateHobbyBadge(toSave)
            logDataSource.logActivity(
                userId = userId,
                userName = userName,
                action = "update",
                targetType = "hobby_badge",
                targetId = badge.id,
                targetName = badge.name
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun archiveHobbyBadge(id: String): Result<Unit> {
        return try {
            val user = authRepository.currentUser
            val userId = user?.uid ?: ""
            val userName = user?.displayName ?: "قائد"
            dataSource.setHobbyBadgeArchived(id, true, userId)
            logDataSource.logActivity(
                userId = userId,
                userName = userName,
                action = "archive",
                targetType = "hobby_badge",
                targetId = id,
                targetName = id
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun unarchiveHobbyBadge(id: String): Result<Unit> {
        return try {
            val user = authRepository.currentUser
            val userId = user?.uid ?: ""
            val userName = user?.displayName ?: "قائد"
            dataSource.setHobbyBadgeArchived(id, false, userId)
            logDataSource.logActivity(
                userId = userId,
                userName = userName,
                action = "unarchive",
                targetType = "hobby_badge",
                targetId = id,
                targetName = id
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun moveHobbyBadgeUp(id: String): Result<Unit> {
        return try {
            val ok = dataSource.moveHobbyBadge(id, moveUp = true)
            if (ok) Result.success(Unit) else Result.failure(IllegalStateException("CANNOT_MOVE"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun moveHobbyBadgeDown(id: String): Result<Unit> {
        return try {
            val ok = dataSource.moveHobbyBadge(id, moveUp = false)
            if (ok) Result.success(Unit) else Result.failure(IllegalStateException("CANNOT_MOVE"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteHobbyBadge(id: String): Result<Unit> {
        return try {
            val user = authRepository.currentUser
            val userId = user?.uid ?: ""
            val userName = user?.displayName ?: "قائد"
            val result = dataSource.deleteHobbyBadgeIfUnused(id)
            if (result.isSuccess) {
                logDataSource.logActivity(
                    userId = userId,
                    userName = userName,
                    action = "delete",
                    targetType = "hobby_badge",
                    targetId = id,
                    targetName = id
                )
            }
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------
    // REQUIREMENTS
    // -------------------------------------------------------------

    override fun observeRequirementsForBadge(badgeId: String): Flow<List<HobbyBadgeRequirement>> =
        dataSource.observeRequirementsForBadge(badgeId)

    override fun observeAllHobbyRequirements(): Flow<List<HobbyBadgeRequirement>> =
        dataSource.observeAllHobbyRequirements()

    override suspend fun addHobbyRequirement(req: HobbyBadgeRequirement): Result<String> {
        return try {
            val user = authRepository.currentUser
            val userId = user?.uid ?: ""
            val userName = user?.displayName ?: "قائد"
            val maxOrder = dataSource.getMaxRequirementOrderByBadge(req.badgeId)
            val toSave = req.copy(
                order = if (req.order > 0) req.order else maxOrder + 1,
                createdBy = userId,
                updatedBy = userId
            )
            val id = dataSource.addHobbyRequirement(toSave)
            logDataSource.logActivity(
                userId = userId,
                userName = userName,
                action = "add",
                targetType = "hobby_requirement",
                targetId = id,
                targetName = toSave.title
            )
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateHobbyRequirement(req: HobbyBadgeRequirement): Result<Unit> {
        return try {
            val user = authRepository.currentUser
            val userId = user?.uid ?: ""
            val userName = user?.displayName ?: "قائد"
            val toSave = req.copy(updatedBy = userId)
            dataSource.updateHobbyRequirement(toSave)
            logDataSource.logActivity(
                userId = userId,
                userName = userName,
                action = "update",
                targetType = "hobby_requirement",
                targetId = req.id,
                targetName = req.title
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun archiveHobbyRequirement(id: String): Result<Unit> {
        return try {
            val user = authRepository.currentUser
            val userId = user?.uid ?: ""
            val userName = user?.displayName ?: "قائد"
            dataSource.setHobbyRequirementArchived(id, true, userId)
            logDataSource.logActivity(
                userId = userId,
                userName = userName,
                action = "archive",
                targetType = "hobby_requirement",
                targetId = id,
                targetName = id
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun unarchiveHobbyRequirement(id: String): Result<Unit> {
        return try {
            val user = authRepository.currentUser
            val userId = user?.uid ?: ""
            val userName = user?.displayName ?: "قائد"
            dataSource.setHobbyRequirementArchived(id, false, userId)
            logDataSource.logActivity(
                userId = userId,
                userName = userName,
                action = "unarchive",
                targetType = "hobby_requirement",
                targetId = id,
                targetName = id
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun moveHobbyRequirementUp(id: String): Result<Unit> {
        return try {
            val ok = dataSource.moveHobbyRequirement(id, moveUp = true)
            if (ok) Result.success(Unit) else Result.failure(IllegalStateException("CANNOT_MOVE"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun moveHobbyRequirementDown(id: String): Result<Unit> {
        return try {
            val ok = dataSource.moveHobbyRequirement(id, moveUp = false)
            if (ok) Result.success(Unit) else Result.failure(IllegalStateException("CANNOT_MOVE"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteHobbyRequirement(id: String): Result<Unit> {
        return try {
            val user = authRepository.currentUser
            val userId = user?.uid ?: ""
            val userName = user?.displayName ?: "قائد"
            val result = dataSource.deleteHobbyRequirementIfUnused(id)
            if (result.isSuccess) {
                logDataSource.logActivity(
                    userId = userId,
                    userName = userName,
                    action = "delete",
                    targetType = "hobby_requirement",
                    targetId = id,
                    targetName = id
                )
            }
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------
    // SCOUT PROGRESS
    // -------------------------------------------------------------

    override fun observeHobbyProgressForScout(scoutId: String): Flow<List<ScoutHobbyProgress>> =
        dataSource.observeHobbyProgressForScout(scoutId)

    override suspend fun setHobbyRequirementCompleted(
        scoutId: String,
        requirementId: String,
        badgeId: String,
        completed: Boolean,
        completedByName: String?
    ): Result<Unit> {
        return try {
            val user = authRepository.currentUser
            val userId = user?.uid ?: ""
            val leaderName = completedByName ?: user?.displayName ?: "قائد"

            dataSource.setHobbyRequirementProgress(
                scoutId = scoutId,
                requirementId = requirementId,
                badgeId = badgeId,
                completed = completed,
                completedBy = userId,
                completedByName = leaderName
            )
            logDataSource.logActivity(
                userId = userId,
                userName = leaderName,
                action = if (completed) "complete_requirement" else "uncomplete_requirement",
                targetType = "scout_hobby_progress",
                targetId = requirementId,
                targetName = "Scout: $scoutId"
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------
    // GRANTS & REVOCATIONS
    // -------------------------------------------------------------

    override fun observeHobbyBadgesForScout(scoutId: String): Flow<List<ScoutHobbyBadge>> =
        dataSource.observeHobbyBadgesForScout(scoutId)

    override fun observeAllHobbyBadgesGranted(): Flow<List<ScoutHobbyBadge>> =
        dataSource.observeAllHobbyBadgesGranted()

    override suspend fun grantHobbyBadge(
        scoutId: String,
        badgeId: String,
        notes: String?,
        grantedByName: String?
    ): Result<Unit> {
        return try {
            val user = authRepository.currentUser
            val userId = user?.uid ?: ""
            val leaderName = grantedByName ?: user?.displayName ?: "قائد"

            dataSource.grantHobbyBadgeWithLink(
                scoutId = scoutId,
                badgeId = badgeId,
                notes = notes,
                grantedBy = userId,
                grantedByName = leaderName
            )
            logDataSource.logActivity(
                userId = userId,
                userName = leaderName,
                action = "grant",
                targetType = "hobby_badge",
                targetId = badgeId,
                targetName = "Scout: $scoutId"
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun revokeHobbyBadge(
        scoutId: String,
        badgeId: String,
        revokedByName: String?
    ): Result<Unit> {
        return try {
            val user = authRepository.currentUser
            val userId = user?.uid ?: ""
            val leaderName = revokedByName ?: user?.displayName ?: "قائد"

            dataSource.revokeHobbyBadgeWithLink(
                scoutId = scoutId,
                badgeId = badgeId,
                revokedBy = userId,
                revokedByName = leaderName
            )
            logDataSource.logActivity(
                userId = userId,
                userName = leaderName,
                action = "revoke",
                targetType = "hobby_badge",
                targetId = badgeId,
                targetName = "Scout: $scoutId"
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------
    // COMPUTED PROGRESS
    // -------------------------------------------------------------

    override fun observeHobbyBadgeProgressForScout(scoutId: String): Flow<List<HobbyBadgeProgress>> {
        return combine(
            dataSource.observeAllHobbyBadges(),
            dataSource.observeAllHobbyRequirements(),
            dataSource.observeHobbyProgressForScout(scoutId),
            dataSource.observeHobbyBadgesForScout(scoutId)
        ) { allBadges, allRequirements, scoutProgress, scoutBadges ->
            allBadges.map { badge ->
                val badgeReqs = allRequirements.filter { it.badgeId == badge.id }.sortedBy { it.order }
                val activeReqs = badgeReqs.filter { it.isActive() }

                val progressMap = scoutProgress.filter { it.badgeId == badge.id || badgeReqs.any { r -> r.id == it.requirementId } }
                    .associateBy { it.requirementId }

                val reqsWithState = badgeReqs.map { req ->
                    val entry = progressMap[req.id]
                    HobbyRequirementWithState(
                        requirement = req,
                        completed = entry?.completed == true,
                        completedAt = entry?.completedAt,
                        completedByName = entry?.completedByName
                    )
                }

                val totalActive = activeReqs.size
                val completedActive = activeReqs.count { progressMap[it.id]?.completed == true }
                val percentage = if (totalActive > 0) {
                    floor((completedActive.toDouble() / totalActive) * 100.0).toInt()
                } else 0

                val activeGrant = scoutBadges.firstOrNull { it.badgeId == badge.id && it.isActive() }

                HobbyBadgeProgress(
                    badge = badge,
                    totalActiveRequirements = totalActive,
                    completedActiveRequirements = completedActive,
                    percentage = percentage,
                    isGranted = activeGrant != null,
                    grantedAt = activeGrant?.grantedAt,
                    grantedByName = activeGrant?.grantedByName,
                    requirements = reqsWithState
                )
            }
        }
    }
}
