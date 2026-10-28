package com.example.data.quran

import com.example.domain.auth.AuthRepository
import com.example.data.log.ActivityLogDataSource
import com.example.domain.quran.QuranSchool
import com.example.domain.quran.QuranSchoolRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class QuranSchoolRepositoryImpl(
    private val dataSource: QuranFirestoreDataSource,
    private val activityLogDataSource: ActivityLogDataSource,
    private val authRepository: AuthRepository
) : QuranSchoolRepository {

    override fun observeAllSchools(): Flow<List<QuranSchool>> {
        return dataSource.observeAllSchools()
    }

    override fun observeActiveSchools(): Flow<List<QuranSchool>> {
        return dataSource.observeAllSchools().map { list ->
            list.filter { it.isActive() }
        }
    }

    override suspend fun getSchoolById(id: String): QuranSchool? {
        return dataSource.getSchoolById(id)
    }

    override suspend fun addSchool(school: QuranSchool): Result<String> {
        return try {
            val trimmedName = school.name.trim()
            val trimmedTeacherName = school.teacherName.trim()
            val phoneDigits = school.teacherPhone.filter { it.isDigit() }

            if (trimmedName.length < 3) {
                return Result.failure(IllegalArgumentException("اسم المدرسة يجب أن يكون 3 أحرف على الأقل"))
            }
            if (trimmedTeacherName.length < 2) {
                return Result.failure(IllegalArgumentException("اسم المعلم يجب أن يكون حرفين على الأقل"))
            }
            if (phoneDigits.length !in 9..10) {
                return Result.failure(IllegalArgumentException("رقم الهاتف يجب أن يتكون من 9 إلى 10 أرقام"))
            }

            val currentUser = authRepository.currentUser
            val currentUserId = currentUser?.uid
            val currentUserName = currentUser?.displayName ?: "قائد"
            val now = System.currentTimeMillis()
            val toSave = school.copy(
                name = trimmedName,
                teacherName = trimmedTeacherName,
                teacherPhone = phoneDigits,
                address = school.address?.trim()?.ifBlank { null },
                notes = school.notes?.trim()?.ifBlank { null },
                createdAt = now,
                updatedAt = now,
                createdBy = currentUserId,
                updatedBy = currentUserId
            )

            val generatedId = dataSource.saveSchool(toSave)

            activityLogDataSource.logActivity(
                userId = currentUserId ?: "system",
                userName = currentUserName,
                action = "add",
                targetType = "quran_school",
                targetId = generatedId,
                targetName = trimmedName,
                meta = mapOf(
                    "teacherName" to trimmedTeacherName,
                    "teacherPhone" to phoneDigits
                )
            )

            Result.success(generatedId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateSchool(school: QuranSchool): Result<Unit> {
        return try {
            val trimmedName = school.name.trim()
            val trimmedTeacherName = school.teacherName.trim()
            val phoneDigits = school.teacherPhone.filter { it.isDigit() }

            if (trimmedName.length < 3) {
                return Result.failure(IllegalArgumentException("اسم المدرسة يجب أن يكون 3 أحرف على الأقل"))
            }
            if (trimmedTeacherName.length < 2) {
                return Result.failure(IllegalArgumentException("اسم المعلم يجب أن يكون حرفين على الأقل"))
            }
            if (phoneDigits.length !in 9..10) {
                return Result.failure(IllegalArgumentException("رقم الهاتف يجب أن يتكون من 9 إلى 10 أرقام"))
            }

            val existing = dataSource.getSchoolById(school.id)
                ?: return Result.failure(NoSuchElementException("المدرسة القرآنية غير موجودة"))

            val currentUser = authRepository.currentUser
            val currentUserId = currentUser?.uid
            val currentUserName = currentUser?.displayName ?: "قائد"
            val now = System.currentTimeMillis()
            val toSave = school.copy(
                name = trimmedName,
                teacherName = trimmedTeacherName,
                teacherPhone = phoneDigits,
                address = school.address?.trim()?.ifBlank { null },
                notes = school.notes?.trim()?.ifBlank { null },
                createdAt = existing.createdAt,
                createdBy = existing.createdBy,
                updatedAt = now,
                updatedBy = currentUserId
            )

            dataSource.updateSchool(toSave)

            // Update denormalized school fields in all scout enrollments
            val cascadedCount = dataSource.updateDenormalizedSchoolInEnrollments(
                schoolId = school.id,
                schoolName = trimmedName,
                teacherName = trimmedTeacherName,
                teacherPhone = phoneDigits,
                updatedBy = currentUserId
            )

            activityLogDataSource.logActivity(
                userId = currentUserId ?: "system",
                userName = currentUserName,
                action = "update",
                targetType = "quran_school",
                targetId = school.id,
                targetName = trimmedName,
                meta = mapOf(
                    "teacherName" to trimmedTeacherName,
                    "teacherPhone" to phoneDigits,
                    "cascadedEnrollmentsCount" to cascadedCount
                )
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun archiveSchool(id: String): Result<Unit> {
        return try {
            val existing = dataSource.getSchoolById(id)
                ?: return Result.failure(NoSuchElementException("المدرسة القرآنية غير موجودة"))

            val currentUser = authRepository.currentUser
            val currentUserId = currentUser?.uid
            val currentUserName = currentUser?.displayName ?: "قائد"
            dataSource.setSchoolArchived(id = id, archived = true, updatedBy = currentUserId)

            activityLogDataSource.logActivity(
                userId = currentUserId ?: "system",
                userName = currentUserName,
                action = "archive",
                targetType = "quran_school",
                targetId = id,
                targetName = existing.name
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun unarchiveSchool(id: String): Result<Unit> {
        return try {
            val existing = dataSource.getSchoolById(id)
                ?: return Result.failure(NoSuchElementException("المدرسة القرآنية غير موجودة"))

            val currentUser = authRepository.currentUser
            val currentUserId = currentUser?.uid
            val currentUserName = currentUser?.displayName ?: "قائد"
            dataSource.setSchoolArchived(id = id, archived = false, updatedBy = currentUserId)

            activityLogDataSource.logActivity(
                userId = currentUserId ?: "system",
                userName = currentUserName,
                action = "unarchive",
                targetType = "quran_school",
                targetId = id,
                targetName = existing.name
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteSchool(id: String): Result<Unit> {
        return try {
            val existing = dataSource.getSchoolById(id)
                ?: return Result.failure(NoSuchElementException("المدرسة القرآنية غير موجودة"))

            val enrollmentsCount = dataSource.getEnrollmentsCountForSchool(id)
            if (enrollmentsCount > 0) {
                return Result.failure(
                    IllegalStateException(
                        "لا يمكن حذف المدرسة — يوجد $enrollmentsCount شبل مسجّل فيها. استخدم الأرشفة أو احذف التسجيلات أولًا."
                    )
                )
            }

            dataSource.deleteSchoolDoc(id)

            val currentUser = authRepository.currentUser
            val currentUserId = currentUser?.uid
            val currentUserName = currentUser?.displayName ?: "قائد"
            activityLogDataSource.logActivity(
                userId = currentUserId ?: "system",
                userName = currentUserName,
                action = "delete",
                targetType = "quran_school",
                targetId = id,
                targetName = existing.name
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
