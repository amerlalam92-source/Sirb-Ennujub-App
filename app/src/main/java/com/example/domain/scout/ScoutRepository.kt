package com.example.domain.scout

import kotlinx.coroutines.flow.Flow

/**
 * واجهة مستودع الأشبال (Domain Layer)
 * تحدد العمليات الأساسية لإدارة الأشبال بسرب النُّجُب.
 */
interface ScoutRepository {
    fun observeScouts(): Flow<List<Scout>>
    suspend fun getScoutById(id: String): Scout?
    suspend fun addScout(scout: Scout): Result<String>
    suspend fun updateScout(scout: Scout): Result<Unit>
    suspend fun archiveScout(id: String): Result<Unit>
    suspend fun deleteScout(id: String): Result<Unit>
    suspend fun findDuplicateScout(
        name: String,
        surname: String,
        guardianName: String,
        excludeScoutId: String? = null
    ): Scout?
}
