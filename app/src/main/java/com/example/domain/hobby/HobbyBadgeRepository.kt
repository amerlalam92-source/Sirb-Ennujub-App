package com.example.domain.hobby

import kotlinx.coroutines.flow.Flow

/**
 * واجهة مستودع شارات الهواية (Hobby Badges Repository)
 */
interface HobbyBadgeRepository {
    // الكتالوج العام لشارات الهواية
    fun observeAllHobbyBadges(): Flow<List<HobbyBadge>>
    fun observeHobbyBadgeById(id: String): Flow<HobbyBadge?>
    suspend fun addHobbyBadge(badge: HobbyBadge): Result<String>
    suspend fun updateHobbyBadge(badge: HobbyBadge): Result<Unit>
    suspend fun archiveHobbyBadge(id: String): Result<Unit>
    suspend fun unarchiveHobbyBadge(id: String): Result<Unit>
    suspend fun moveHobbyBadgeUp(id: String): Result<Unit>
    suspend fun moveHobbyBadgeDown(id: String): Result<Unit>
    suspend fun deleteHobbyBadge(id: String): Result<Unit>

    // متطلبات شارات الهواية
    fun observeRequirementsForBadge(badgeId: String): Flow<List<HobbyBadgeRequirement>>
    fun observeAllHobbyRequirements(): Flow<List<HobbyBadgeRequirement>>
    suspend fun addHobbyRequirement(req: HobbyBadgeRequirement): Result<String>
    suspend fun updateHobbyRequirement(req: HobbyBadgeRequirement): Result<Unit>
    suspend fun archiveHobbyRequirement(id: String): Result<Unit>
    suspend fun unarchiveHobbyRequirement(id: String): Result<Unit>
    suspend fun moveHobbyRequirementUp(id: String): Result<Unit>
    suspend fun moveHobbyRequirementDown(id: String): Result<Unit>
    suspend fun deleteHobbyRequirement(id: String): Result<Unit>

    // إنجاز الأشبال للمتطلبات
    fun observeHobbyProgressForScout(scoutId: String): Flow<List<ScoutHobbyProgress>>
    suspend fun setHobbyRequirementCompleted(
        scoutId: String,
        requirementId: String,
        badgeId: String,
        completed: Boolean,
        completedByName: String?
    ): Result<Unit>

    // منح وسحب شارات الهواية
    fun observeHobbyBadgesForScout(scoutId: String): Flow<List<ScoutHobbyBadge>>
    fun observeAllHobbyBadgesGranted(): Flow<List<ScoutHobbyBadge>>
    suspend fun grantHobbyBadge(
        scoutId: String,
        badgeId: String,
        notes: String?,
        grantedByName: String?
    ): Result<Unit>
    suspend fun revokeHobbyBadge(
        scoutId: String,
        badgeId: String,
        revokedByName: String?
    ): Result<Unit>

    // الحساب التجميعي لشارات الهواية لشبل معين
    fun observeHobbyBadgeProgressForScout(scoutId: String): Flow<List<HobbyBadgeProgress>>
}
