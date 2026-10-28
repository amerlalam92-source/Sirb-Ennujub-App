package com.example.domain.quran.usecases

import com.example.domain.quran.QuranSchool
import com.example.domain.quran.QuranSchoolRepository
import kotlinx.coroutines.flow.Flow

class ObserveAllQuranSchoolsUseCase(
    private val repository: QuranSchoolRepository
) {
    operator fun invoke(): Flow<List<QuranSchool>> = repository.observeAllSchools()
}

class ObserveActiveQuranSchoolsUseCase(
    private val repository: QuranSchoolRepository
) {
    operator fun invoke(): Flow<List<QuranSchool>> = repository.observeActiveSchools()
}

class GetQuranSchoolByIdUseCase(
    private val repository: QuranSchoolRepository
) {
    suspend operator fun invoke(id: String): QuranSchool? = repository.getSchoolById(id)
}

class AddQuranSchoolUseCase(
    private val repository: QuranSchoolRepository
) {
    suspend operator fun invoke(school: QuranSchool): Result<String> = repository.addSchool(school)
}

class UpdateQuranSchoolUseCase(
    private val repository: QuranSchoolRepository
) {
    suspend operator fun invoke(school: QuranSchool): Result<Unit> = repository.updateSchool(school)
}

class ArchiveQuranSchoolUseCase(
    private val repository: QuranSchoolRepository
) {
    suspend operator fun invoke(id: String): Result<Unit> = repository.archiveSchool(id)
}

class UnarchiveQuranSchoolUseCase(
    private val repository: QuranSchoolRepository
) {
    suspend operator fun invoke(id: String): Result<Unit> = repository.unarchiveSchool(id)
}

class DeleteQuranSchoolUseCase(
    private val repository: QuranSchoolRepository
) {
    suspend operator fun invoke(id: String): Result<Unit> = repository.deleteSchool(id)
}
