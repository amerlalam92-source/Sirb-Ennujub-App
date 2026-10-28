package com.example.domain.quran

import kotlinx.coroutines.flow.Flow

interface QuranSchoolRepository {
    fun observeAllSchools(): Flow<List<QuranSchool>>
    fun observeActiveSchools(): Flow<List<QuranSchool>>
    suspend fun getSchoolById(id: String): QuranSchool?
    suspend fun addSchool(school: QuranSchool): Result<String>
    suspend fun updateSchool(school: QuranSchool): Result<Unit>
    suspend fun archiveSchool(id: String): Result<Unit>
    suspend fun unarchiveSchool(id: String): Result<Unit>
    suspend fun deleteSchool(id: String): Result<Unit>
}
