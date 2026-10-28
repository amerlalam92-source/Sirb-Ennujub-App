package com.example.domain.quran

import kotlinx.coroutines.flow.Flow

interface ScoutQuranEnrollmentRepository {
    fun observeEnrollmentForScout(scoutId: String): Flow<ScoutQuranEnrollment?>
    fun observeAllEnrollments(): Flow<List<ScoutQuranEnrollment>>
    fun observeEnrollmentsForSchool(schoolId: String): Flow<List<ScoutQuranEnrollment>>
    suspend fun getEnrollmentForScout(scoutId: String): ScoutQuranEnrollment?
    suspend fun saveEnrollment(enrollment: ScoutQuranEnrollment): Result<Unit>
    suspend fun deleteEnrollment(scoutId: String): Result<Unit>
}
