package com.example.data.quran

import com.example.domain.auth.AuthRepository
import com.example.data.log.ActivityLogDataSource
import com.example.domain.quran.QuranSchoolRepository
import com.example.domain.quran.ScoutQuranEnrollment
import com.example.domain.quran.ScoutQuranEnrollmentRepository
import kotlinx.coroutines.flow.Flow

class ScoutQuranEnrollmentRepositoryImpl(
    private val dataSource: QuranFirestoreDataSource,
    private val schoolRepository: QuranSchoolRepository,
    private val activityLogDataSource: ActivityLogDataSource,
    private val authRepository: AuthRepository
) : ScoutQuranEnrollmentRepository {

    override fun observeEnrollmentForScout(scoutId: String): Flow<ScoutQuranEnrollment?> {
        return dataSource.observeEnrollmentForScout(scoutId)
    }

    override fun observeAllEnrollments(): Flow<List<ScoutQuranEnrollment>> {
        return dataSource.observeAllEnrollments()
    }

    override fun observeEnrollmentsForSchool(schoolId: String): Flow<List<ScoutQuranEnrollment>> {
        return dataSource.observeEnrollmentsForSchool(schoolId)
    }

    override suspend fun getEnrollmentForScout(scoutId: String): ScoutQuranEnrollment? {
        return dataSource.getEnrollmentForScout(scoutId)
    }

    override suspend fun saveEnrollment(enrollment: ScoutQuranEnrollment): Result<Unit> {
        return try {
            if (enrollment.scoutId.isBlank()) {
                return Result.failure(IllegalArgumentException("معرف الشبل مطلوب"))
            }
            if (enrollment.schoolId.isBlank()) {
                return Result.failure(IllegalArgumentException("يجب اختيار مدرسة قرآنية"))
            }
            if (enrollment.currentMemorizedParts !in 0..60) {
                return Result.failure(IllegalArgumentException("عدد الأجزاء المحفوظة يجب أن يكون بين 0 و 60"))
            }

            // Verify school exists and fetch fresh school details
            val school = schoolRepository.getSchoolById(enrollment.schoolId)
                ?: return Result.failure(NoSuchElementException("المدرسة القرآنية المحددة غير موجودة"))

            val existing = dataSource.getEnrollmentForScout(enrollment.scoutId)
            val currentUser = authRepository.currentUser
            val currentUserId = currentUser?.uid
            val currentUserName = currentUser?.displayName ?: "قائد"
            val now = System.currentTimeMillis()

            val toSave = enrollment.copy(
                schoolName = school.name,
                teacherName = school.teacherName,
                teacherPhone = school.teacherPhone,
                memorizationNote = enrollment.memorizationNote?.trim()?.ifBlank { null },
                generalNotes = enrollment.generalNotes?.trim()?.ifBlank { null },
                createdAt = existing?.createdAt ?: now,
                createdBy = existing?.createdBy ?: currentUserId,
                updatedAt = now,
                updatedBy = currentUserId
            )

            dataSource.saveEnrollment(toSave)

            val action = if (existing == null) "enroll" else "update_enrollment"
            activityLogDataSource.logActivity(
                userId = currentUserId ?: "system",
                userName = currentUserName,
                action = action,
                targetType = "scout_quran",
                targetId = enrollment.scoutId,
                targetName = school.name,
                meta = mapOf(
                    "schoolId" to school.id,
                    "parts" to enrollment.currentMemorizedParts,
                    "daysCount" to enrollment.studyDays.size,
                    "periodsCount" to enrollment.studyPeriods.size
                )
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteEnrollment(scoutId: String): Result<Unit> {
        return try {
            val existing = dataSource.getEnrollmentForScout(scoutId)
                ?: return Result.failure(NoSuchElementException("سجل الحفظ غير موجود"))

            dataSource.deleteEnrollment(scoutId)

            val currentUser = authRepository.currentUser
            val currentUserId = currentUser?.uid
            val currentUserName = currentUser?.displayName ?: "قائد"
            activityLogDataSource.logActivity(
                userId = currentUserId ?: "system",
                userName = currentUserName,
                action = "unenroll",
                targetType = "scout_quran",
                targetId = scoutId,
                targetName = existing.schoolName,
                meta = mapOf("schoolId" to existing.schoolId)
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
