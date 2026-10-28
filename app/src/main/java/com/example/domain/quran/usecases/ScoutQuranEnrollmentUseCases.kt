package com.example.domain.quran.usecases

import com.example.domain.quran.ScoutQuranEnrollment
import com.example.domain.quran.ScoutQuranEnrollmentRepository
import kotlinx.coroutines.flow.Flow

class ObserveEnrollmentForScoutUseCase(
    private val repository: ScoutQuranEnrollmentRepository
) {
    operator fun invoke(scoutId: String): Flow<ScoutQuranEnrollment?> =
        repository.observeEnrollmentForScout(scoutId)
}

class ObserveAllEnrollmentsUseCase(
    private val repository: ScoutQuranEnrollmentRepository
) {
    operator fun invoke(): Flow<List<ScoutQuranEnrollment>> =
        repository.observeAllEnrollments()
}

class ObserveEnrollmentsForSchoolUseCase(
    private val repository: ScoutQuranEnrollmentRepository
) {
    operator fun invoke(schoolId: String): Flow<List<ScoutQuranEnrollment>> =
        repository.observeEnrollmentsForSchool(schoolId)
}

class GetEnrollmentForScoutUseCase(
    private val repository: ScoutQuranEnrollmentRepository
) {
    suspend operator fun invoke(scoutId: String): ScoutQuranEnrollment? =
        repository.getEnrollmentForScout(scoutId)
}

class SaveEnrollmentUseCase(
    private val repository: ScoutQuranEnrollmentRepository
) {
    suspend operator fun invoke(enrollment: ScoutQuranEnrollment): Result<Unit> =
        repository.saveEnrollment(enrollment)
}

class DeleteEnrollmentUseCase(
    private val repository: ScoutQuranEnrollmentRepository
) {
    suspend operator fun invoke(scoutId: String): Result<Unit> =
        repository.deleteEnrollment(scoutId)
}
