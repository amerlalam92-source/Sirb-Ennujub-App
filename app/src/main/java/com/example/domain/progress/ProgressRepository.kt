package com.example.domain.progress

import kotlinx.coroutines.flow.Flow

/**
 * واجهة مستودع بطاقة التقدم وشارات الكفاءة
 */
interface ProgressRepository {

    // كتالوج المتطلبات (Requirements Catalog)
    fun observeAllRequirements(): Flow<List<ProgressRequirement>>
    fun observeRequirementsByLevel(level: BadgeLevel): Flow<List<ProgressRequirement>>
    suspend fun addRequirement(req: ProgressRequirement): Result<String>
    suspend fun updateRequirement(req: ProgressRequirement): Result<Unit>
    suspend fun archiveRequirement(id: String): Result<Unit>
    suspend fun unarchiveRequirement(id: String): Result<Unit>
    suspend fun moveRequirementUp(id: String): Result<Unit>
    suspend fun moveRequirementDown(id: String): Result<Unit>
    suspend fun deleteRequirement(id: String): Result<Unit>

    // تقدم الشبل (Scout Progress)
    fun observeScoutProgress(scoutId: String): Flow<List<ScoutProgressEntry>>
    suspend fun setRequirementCompleted(
        scoutId: String,
        requirementId: String,
        completed: Boolean,
        completedByName: String? = null
    ): Result<Unit>

    // الشارات الممنوحة (Scout Badges)
    fun observeScoutBadges(scoutId: String): Flow<List<ScoutBadge>>
    suspend fun grantBadge(
        scoutId: String,
        level: BadgeLevel,
        notes: String = "",
        grantedByName: String
    ): Result<Unit>
    suspend fun revokeBadge(
        scoutId: String,
        level: BadgeLevel,
        revokedByName: String
    ): Result<Unit>

    // التقدم المجمع للشبل لجميع الشارات الأربعة
    fun observeBadgeProgress(scoutId: String): Flow<List<BadgeProgress>>
}
