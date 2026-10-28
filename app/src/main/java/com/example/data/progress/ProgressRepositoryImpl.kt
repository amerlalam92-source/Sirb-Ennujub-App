package com.example.data.progress

import com.example.data.log.ActivityLogDataSource
import com.example.domain.auth.AuthRepository
import com.example.domain.progress.BadgeLevel
import com.example.domain.progress.BadgeProgress
import com.example.domain.progress.ProgressRepository
import com.example.domain.progress.ProgressRequirement
import com.example.domain.progress.RequirementWithState
import com.example.domain.progress.ScoutBadge
import com.example.domain.progress.ScoutProgressEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlin.math.floor

/**
 * تنفيذ مستودع بطاقة التقدم وشارات الكفاءة
 */
class ProgressRepositoryImpl(
    private val dataSource: ProgressFirestoreDataSource,
    private val logDataSource: ActivityLogDataSource,
    private val authRepository: AuthRepository
) : ProgressRepository {

    override fun observeAllRequirements(): Flow<List<ProgressRequirement>> =
        dataSource.observeAllRequirements()

    override fun observeRequirementsByLevel(level: BadgeLevel): Flow<List<ProgressRequirement>> =
        dataSource.observeRequirementsByLevel(level)

    override suspend fun addRequirement(req: ProgressRequirement): Result<String> {
        return try {
            val user = authRepository.currentUser
            val userId = user?.uid ?: ""
            val userName = user?.displayName ?: "قائد"
            val maxOrder = dataSource.getMaxOrderByLevel(req.badgeLevel)
            val toSave = req.copy(
                order = if (req.order > 0) req.order else maxOrder + 1,
                createdBy = userId,
                updatedBy = userId
            )
            val id = dataSource.addRequirement(toSave)
            logDataSource.logActivity(
                userId = userId,
                userName = userName,
                action = "add",
                targetType = "requirement",
                targetId = id,
                targetName = toSave.title
            )
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateRequirement(req: ProgressRequirement): Result<Unit> {
        return try {
            val user = authRepository.currentUser
            val userId = user?.uid ?: ""
            val userName = user?.displayName ?: "قائد"
            val toSave = req.copy(updatedBy = userId)
            dataSource.updateRequirement(toSave)
            logDataSource.logActivity(
                userId = userId,
                userName = userName,
                action = "update",
                targetType = "requirement",
                targetId = req.id,
                targetName = req.title
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun archiveRequirement(id: String): Result<Unit> {
        return try {
            val user = authRepository.currentUser
            val userId = user?.uid ?: ""
            val userName = user?.displayName ?: "قائد"
            dataSource.setArchived(id, archived = true, updatedBy = userId)
            logDataSource.logActivity(
                userId = userId,
                userName = userName,
                action = "archive",
                targetType = "requirement",
                targetId = id,
                targetName = "متطلب $id"
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun unarchiveRequirement(id: String): Result<Unit> {
        return try {
            val user = authRepository.currentUser
            val userId = user?.uid ?: ""
            val userName = user?.displayName ?: "قائد"
            dataSource.setArchived(id, archived = false, updatedBy = userId)
            logDataSource.logActivity(
                userId = userId,
                userName = userName,
                action = "unarchive",
                targetType = "requirement",
                targetId = id,
                targetName = "متطلب $id"
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun moveRequirementUp(id: String): Result<Unit> {
        return try {
            val success = dataSource.moveRequirement(id, moveUp = true)
            if (success) {
                val user = authRepository.currentUser
                val userId = user?.uid ?: ""
                val userName = user?.displayName ?: "قائد"
                logDataSource.logActivity(
                    userId = userId,
                    userName = userName,
                    action = "move",
                    targetType = "requirement",
                    targetId = id,
                    targetName = "تحريك للأعلى"
                )
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("CANNOT_MOVE_UP"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun moveRequirementDown(id: String): Result<Unit> {
        return try {
            val success = dataSource.moveRequirement(id, moveUp = false)
            if (success) {
                val user = authRepository.currentUser
                val userId = user?.uid ?: ""
                val userName = user?.displayName ?: "قائد"
                logDataSource.logActivity(
                    userId = userId,
                    userName = userName,
                    action = "move",
                    targetType = "requirement",
                    targetId = id,
                    targetName = "تحريك للأسفل"
                )
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("CANNOT_MOVE_DOWN"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteRequirement(id: String): Result<Unit> {
        return try {
            val deleteResult = dataSource.deleteRequirementIfUnused(id)
            if (deleteResult.isSuccess) {
                val user = authRepository.currentUser
                val userId = user?.uid ?: ""
                val userName = user?.displayName ?: "قائد"
                logDataSource.logActivity(
                    userId = userId,
                    userName = userName,
                    action = "delete",
                    targetType = "requirement",
                    targetId = id,
                    targetName = "حذف متطلب $id"
                )
            }
            deleteResult
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun observeScoutProgress(scoutId: String): Flow<List<ScoutProgressEntry>> =
        dataSource.observeScoutProgress(scoutId)

    override suspend fun setRequirementCompleted(
        scoutId: String,
        requirementId: String,
        completed: Boolean,
        completedByName: String?
    ): Result<Unit> {
        return try {
            val user = authRepository.currentUser
            val userId = user?.uid ?: ""
            val effectiveLeaderName = completedByName ?: user?.displayName ?: "قائد"
            dataSource.setRequirementProgress(
                scoutId = scoutId,
                requirementId = requirementId,
                completed = completed,
                completedBy = userId,
                completedByName = effectiveLeaderName
            )
            logDataSource.logActivity(
                userId = userId,
                userName = effectiveLeaderName,
                action = if (completed) "complete" else "uncomplete",
                targetType = "scout_progress",
                targetId = "${scoutId}_$requirementId",
                targetName = "شبل $scoutId متطلب $requirementId"
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun observeScoutBadges(scoutId: String): Flow<List<ScoutBadge>> =
        dataSource.observeScoutBadges(scoutId)

    override suspend fun grantBadge(
        scoutId: String,
        level: BadgeLevel,
        notes: String,
        grantedByName: String
    ): Result<Unit> {
        return try {
            val user = authRepository.currentUser
            val userId = user?.uid ?: ""
            val effectiveLeaderName = if (grantedByName.isNotBlank()) grantedByName else user?.displayName ?: "قائد"
            dataSource.grantBadge(
                scoutId = scoutId,
                level = level,
                notes = notes,
                grantedBy = userId,
                grantedByName = effectiveLeaderName
            )
            logDataSource.logActivity(
                userId = userId,
                userName = effectiveLeaderName,
                action = "grant",
                targetType = "scout_badge",
                targetId = "${scoutId}_${level.key}",
                targetName = "شارة ${level.name} لشبل $scoutId"
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun revokeBadge(
        scoutId: String,
        level: BadgeLevel,
        revokedByName: String
    ): Result<Unit> {
        return try {
            val user = authRepository.currentUser
            val userId = user?.uid ?: ""
            val effectiveLeaderName = if (revokedByName.isNotBlank()) revokedByName else user?.displayName ?: "قائد"
            dataSource.revokeBadge(
                scoutId = scoutId,
                level = level,
                revokedBy = userId,
                revokedByName = effectiveLeaderName
            )
            logDataSource.logActivity(
                userId = userId,
                userName = effectiveLeaderName,
                action = "revoke",
                targetType = "scout_badge",
                targetId = "${scoutId}_${level.key}",
                targetName = "سحب شارة ${level.name} من شبل $scoutId"
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun observeBadgeProgress(scoutId: String): Flow<List<BadgeProgress>> {
        return combine(
            dataSource.observeAllRequirements(),
            dataSource.observeScoutProgress(scoutId),
            dataSource.observeScoutBadges(scoutId)
        ) { allReqs, progressEntries, badges ->
            val progressByReqId = progressEntries.associateBy { it.requirementId }
            val badgesByLevel = badges.filter { it.isActive() }.associateBy { it.badgeLevel }

            val reqsByLevel = allReqs.groupBy { it.badgeLevel }

            val levelsOrdered = listOf(
                BadgeLevel.ACCEPTANCE,
                BadgeLevel.BEGINNER,
                BadgeLevel.SECOND,
                BadgeLevel.FIRST
            )

            levelsOrdered.map { level ->
                val levelReqs = reqsByLevel[level.key].orEmpty().sortedBy { it.order }
                val activeReqs = levelReqs.filter { it.isActive() }

                val reqsWithState = levelReqs.map { req ->
                    val p = progressByReqId[req.id]
                    RequirementWithState(
                        requirement = req,
                        completed = p?.completed == true,
                        completedAt = p?.completedAt,
                        completedByName = p?.completedByName
                    )
                }

                val totalActive = activeReqs.size
                val completedActive = activeReqs.count { req ->
                    progressByReqId[req.id]?.completed == true
                }

                val percentage = if (totalActive > 0) {
                    floor((completedActive.toDouble() / totalActive.toDouble()) * 100.0).toInt().coerceIn(0, 100)
                } else {
                    0
                }

                val badge = badgesByLevel[level.key]

                BadgeProgress(
                    level = level,
                    totalActiveRequirements = totalActive,
                    completedActiveRequirements = completedActive,
                    percentage = percentage,
                    isGranted = badge != null,
                    grantedAt = badge?.grantedAt,
                    grantedByName = badge?.grantedByName,
                    requirements = reqsWithState
                )
            }
        }
    }
}
