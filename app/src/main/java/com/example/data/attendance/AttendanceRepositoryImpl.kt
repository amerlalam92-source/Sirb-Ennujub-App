package com.example.data.attendance

import android.util.Log
import com.example.data.activity.ActivityFirestoreDataSource
import com.example.data.log.ActivityLogDataSource
import com.example.data.scout.ScoutFirestoreDataSource
import com.example.domain.activity.Activity
import com.example.domain.activity.ActivityType
import com.example.domain.attendance.ActivitySummary
import com.example.domain.attendance.Attendance
import com.example.domain.attendance.AttendancePointsSettings
import com.example.domain.attendance.AttendanceRepository
import com.example.domain.attendance.AttendanceRow
import com.example.domain.attendance.AttendanceStatus
import com.example.domain.attendance.MeetingWithAttendance
import com.example.domain.attendance.ScoutStatistics
import com.example.domain.auth.AuthRepository
import com.example.domain.finance.FinanceRepository
import com.example.domain.finance.SubscriptionStatus
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/**
 * تنفيذ مستودع تسجيل الحضور (Attendance Repository Implementation)
 * يدير عمليات الحضور، التدقيق، وحساب إحصائيات الأشبال التراكمية وسجل الاجتماعات التدريبية.
 */
class AttendanceRepositoryImpl(
    private val dataSource: AttendanceFirestoreDataSource,
    private val activityDataSource: ActivityFirestoreDataSource,
    private val scoutDataSource: ScoutFirestoreDataSource,
    private val activityLogDataSource: ActivityLogDataSource,
    private val authRepository: AuthRepository,
    private val financeRepository: FinanceRepository
) : AttendanceRepository {

    override fun getAttendanceForActivity(activityId: String): Flow<List<Attendance>> {
        return combine(
            dataSource.getAttendanceForActivity(activityId),
            dataSource.observePointsSettings()
        ) { attendanceList, settings ->
            attendanceList.map { it.withDynamicPoints(settings) }
        }
    }

    override fun getAttendanceForScout(scoutId: String): Flow<List<Attendance>> {
        return combine(
            dataSource.getAttendanceForScout(scoutId),
            dataSource.observeAllAttendance(),
            dataSource.observePointsSettings()
        ) { directList, allList, settings ->
            val records = if (directList.isNotEmpty()) {
                directList
            } else {
                allList.filter { it.scoutId == scoutId }
            }
            records.map { it.withDynamicPoints(settings) }
        }
    }

    override suspend fun saveAttendance(attendance: Attendance): Result<Unit> {
        val currentUser = authRepository.currentUser
        val prepared = attendance.copy(
            recordedBy = currentUser?.uid ?: "",
            recordedByName = currentUser?.displayName ?: "قائد كشفي",
            updatedAt = System.currentTimeMillis()
        )
        val result = dataSource.saveAttendance(prepared)
        if (result.isSuccess) {
            activityLogDataSource.logActivity(
                userId = currentUser?.uid ?: "",
                userName = currentUser?.displayName ?: "قائد كشفي",
                action = "save",
                targetType = "attendance",
                targetId = prepared.id,
                targetName = prepared.scoutFullName
            )

            // مزامنة اشتراك الشبل في المالية إذا كان النشاط اجتماعاً تدريبياً ومفعلاً فيه تحصيل الاشتراكات
            if (prepared.activityId.isNotBlank()) {
                try {
                    val activity = activityDataSource.getActivityById(prepared.activityId)
                    if (activity != null && activity.type == ActivityType.TRAINING_MEETING.key && activity.collectSubscriptions) {
                        val subStatus = SubscriptionStatus.fromKey(prepared.status)
                        financeRepository.ensureSubscriptionForAttendance(
                            activityId = prepared.activityId,
                            scoutId = prepared.scoutId,
                            scoutFullName = prepared.scoutFullName,
                            activityTitle = activity.title,
                            activityDate = if (prepared.activityDate > 0) prepared.activityDate else activity.startDate,
                            attendanceStatus = subStatus
                        )
                    }
                } catch (_: Exception) {}
            }
        }
        return result
    }

    override suspend fun saveAttendanceBatch(list: List<Attendance>): Result<Unit> {
        if (list.isEmpty()) return Result.success(Unit)
        val currentUser = authRepository.currentUser
        val now = System.currentTimeMillis()
        val preparedList = list.map {
            it.copy(
                recordedBy = currentUser?.uid ?: "",
                recordedByName = currentUser?.displayName ?: "قائد كشفي",
                recordedAt = if (it.recordedAt > 0) it.recordedAt else now,
                updatedAt = now
            )
        }
        val result = dataSource.saveAttendanceBatch(preparedList)
        if (result.isSuccess) {
            val activityId = list.firstOrNull()?.activityId ?: ""
            activityLogDataSource.logActivity(
                userId = currentUser?.uid ?: "",
                userName = currentUser?.displayName ?: "قائد كشفي",
                action = "save_batch",
                targetType = "attendance",
                targetId = activityId,
                targetName = "${list.size} سجل حضور"
            )

            // مزامنة اشتراكات الأشبال تلقائياً للاجتماعات التدريبية فقط والمفعل فيها تحصيل الاشتراكات
            if (activityId.isNotBlank()) {
                try {
                    val activity = activityDataSource.getActivityById(activityId)
                    if (activity != null && activity.type == ActivityType.TRAINING_MEETING.key && activity.collectSubscriptions) {
                        for (item in preparedList) {
                            val subStatus = SubscriptionStatus.fromKey(item.status)
                            financeRepository.ensureSubscriptionForAttendance(
                                activityId = item.activityId,
                                scoutId = item.scoutId,
                                scoutFullName = item.scoutFullName,
                                activityTitle = activity.title,
                                activityDate = if (item.activityDate > 0) item.activityDate else activity.startDate,
                                attendanceStatus = subStatus
                            )
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        return result
    }

    override suspend fun deleteAttendance(activityId: String, scoutId: String): Result<Unit> {
        val currentUser = authRepository.currentUser
        val result = dataSource.deleteAttendance(activityId, scoutId)
        if (result.isSuccess) {
            activityLogDataSource.logActivity(
                userId = currentUser?.uid ?: "",
                userName = currentUser?.displayName ?: "قائد كشفي",
                action = "delete",
                targetType = "attendance",
                targetId = "${activityId}_${scoutId}",
                targetName = "سجل حضور"
            )
        }
        return result
    }

    override fun observePointsSettings(): Flow<AttendancePointsSettings> {
        return dataSource.observePointsSettings()
    }

    override suspend fun updatePointsSettings(settings: AttendancePointsSettings): Result<Unit> {
        val currentUser = authRepository.currentUser
        val prepared = settings.copy(
            updatedAt = System.currentTimeMillis(),
            updatedBy = currentUser?.uid ?: ""
        )
        val result = dataSource.updatePointsSettings(prepared)
        if (result.isSuccess) {
            activityLogDataSource.logActivity(
                userId = currentUser?.uid ?: "",
                userName = currentUser?.displayName ?: "قائد كشفي",
                action = "update",
                targetType = "settings",
                targetId = "attendance_points",
                targetName = "سلم التنقيط"
            )
        }
        return result
    }

    override fun computeScoutStatistics(scoutId: String): Flow<ScoutStatistics> {
        return combine(
            dataSource.getAttendanceForScout(scoutId),
            dataSource.observeAllAttendance(),
            activityDataSource.observeActivities(),
            dataSource.observePointsSettings()
        ) { directList, allList, activitiesList, settings ->
            try {
                val records = if (directList.isNotEmpty()) {
                    directList
                } else {
                    allList.filter { it.scoutId == scoutId }
                }

                val activitiesMap = activitiesList.associateBy { it.id }

                var present = 0
                var late = 0
                var veryLate = 0
                var excused = 0
                var unexcused = 0
                var totalPoints = 0
                val missed = mutableListOf<ActivitySummary>()

                for (record in records) {
                    when (record.status) {
                        "present" -> {
                            present++
                            totalPoints += settings.presentPoints
                        }
                        "late" -> {
                            late++
                            totalPoints += settings.latePoints
                        }
                        "very_late" -> {
                            veryLate++
                            totalPoints += settings.veryLatePoints
                        }
                        "absent_excused" -> {
                            excused++
                            totalPoints += settings.absentExcusedPoints
                            val act = activitiesMap[record.activityId]
                            missed.add(
                                ActivitySummary(
                                    activityId = record.activityId,
                                    title = act?.title ?: "نشاط كشفي",
                                    date = if (record.activityDate > 0) record.activityDate else (act?.startDate ?: 0L),
                                    status = AttendanceStatus.ABSENT_EXCUSED
                                )
                            )
                        }
                        "absent_unexcused" -> {
                            unexcused++
                            totalPoints += settings.absentUnexcusedPoints
                            val act = activitiesMap[record.activityId]
                            missed.add(
                                ActivitySummary(
                                    activityId = record.activityId,
                                    title = act?.title ?: "نشاط كشفي",
                                    date = if (record.activityDate > 0) record.activityDate else (act?.startDate ?: 0L),
                                    status = AttendanceStatus.ABSENT_UNEXCUSED
                                )
                            )
                        }
                        else -> {
                            Log.w("StatsError", "Unknown status '${record.status}' for scout $scoutId in activity ${record.activityId}")
                        }
                    }
                }

                val total = present + late + veryLate + excused + unexcused
                val attended = present + late + veryLate
                val percentage = if (total > 0) {
                    (attended.toDouble() / total.toDouble()) * 100.0
                } else {
                    0.0
                }

                // ترتيب الأنشطة الفائتة من الأحدث إلى الأقدم
                missed.sortByDescending { it.date }

                ScoutStatistics(
                    totalActivities = total,
                    presentCount = present,
                    lateCount = late,
                    veryLateCount = veryLate,
                    absentExcusedCount = excused,
                    absentUnexcusedCount = unexcused,
                    attendancePercentage = percentage,
                    totalPoints = totalPoints,
                    missedActivities = missed
                )
            } catch (e: Exception) {
                Log.e("StatsError", "Failed to compute scout statistics for scout: $scoutId", e)
                ScoutStatistics(
                    totalActivities = 0,
                    presentCount = 0,
                    lateCount = 0,
                    veryLateCount = 0,
                    absentExcusedCount = 0,
                    absentUnexcusedCount = 0,
                    attendancePercentage = 0.0,
                    totalPoints = 0,
                    missedActivities = emptyList()
                )
            }
        }
    }

    override fun observeRecordedActivityIds(): Flow<Set<String>> {
        return dataSource.observeAllRecordedActivityIds()
    }

    override fun getAttendanceRowsForActivity(activityId: String): Flow<List<AttendanceRow>> {
        val collator = Collator.getInstance(Locale("ar", "DZ")).apply {
            strength = Collator.PRIMARY
        }
        return combine(
            scoutDataSource.observeScouts(),
            dataSource.getAttendanceForActivity(activityId),
            dataSource.observePointsSettings()
        ) { scouts, rawAttendanceList, settings ->
            val attendanceList = rawAttendanceList.map { it.withDynamicPoints(settings) }
            val attendanceMap = attendanceList.associateBy { it.scoutId }
            val activeScouts = scouts.filter { it.isActive }.sortedWith { a, b ->
                val compName = collator.compare(a.name, b.name)
                if (compName != 0) compName else collator.compare(a.surname, b.surname)
            }
            activeScouts.map { scout ->
                AttendanceRow(
                    scout = scout,
                    attendance = attendanceMap[scout.id]
                )
            }
        }
    }

    override fun getTrainingMeetingsRegister(): Flow<List<MeetingWithAttendance>> {
        val collator = Collator.getInstance(Locale("ar", "DZ")).apply {
            strength = Collator.PRIMARY
        }
        return combine(
            activityDataSource.observeActivities(),
            scoutDataSource.observeScouts(),
            dataSource.observeAllAttendance(),
            dataSource.observePointsSettings()
        ) { activities, scouts, rawAttendance, settings ->
            val allAttendance = rawAttendance.map { it.withDynamicPoints(settings) }
            val activeScouts = scouts.filter { it.isActive }.sortedWith { a, b ->
                val compName = collator.compare(a.name, b.name)
                if (compName != 0) compName else collator.compare(a.surname, b.surname)
            }

            // تصفية الاجتماعات التدريبية وترتيبها تصاعدياً لترقيمها زمنياً (من 1 إلى N)
            val trainingMeetings = activities
                .filter { it.activityType == ActivityType.TRAINING_MEETING }
                .sortedWith(compareBy<Activity> { it.startDate }.thenBy { it.createdAt })

            val attendanceByActivity = allAttendance.groupBy { it.activityId }

            val meetingsWithAttendance = trainingMeetings.mapIndexed { index, activity ->
                val meetingAttendanceMap = attendanceByActivity[activity.id]?.associateBy { it.scoutId } ?: emptyMap()
                val rows = activeScouts.map { scout ->
                    AttendanceRow(
                        scout = scout,
                        attendance = meetingAttendanceMap[scout.id]
                    )
                }
                val presentCount = rows.count {
                    it.attendance?.attendanceStatus == AttendanceStatus.PRESENT
                }
                MeetingWithAttendance(
                    activity = activity,
                    number = index + 1,
                    rows = rows,
                    presentCount = presentCount,
                    totalScouts = activeScouts.size
                )
            }

            // عرض الأحدث أولاً في واجهة السجل
            meetingsWithAttendance.sortedByDescending { it.number }
        }
    }

    override suspend fun getTrainingMeetingNumber(activityId: String): Int {
        return try {
            val activities = activityDataSource.observeActivities().first()
            val trainingMeetings = activities
                .filter { it.activityType == ActivityType.TRAINING_MEETING }
                .sortedWith(compareBy<Activity> { it.startDate }.thenBy { it.createdAt })
            val index = trainingMeetings.indexOfFirst { it.id == activityId }
            if (index >= 0) index + 1 else 0
        } catch (_: Exception) {
            0
        }
    }

    private fun Attendance.withDynamicPoints(settings: AttendancePointsSettings): Attendance {
        val dynamicPoints = when (status) {
            "present" -> settings.presentPoints
            "late" -> settings.latePoints
            "very_late" -> settings.veryLatePoints
            "absent_excused" -> settings.absentExcusedPoints
            "absent_unexcused" -> settings.absentUnexcusedPoints
            else -> settings.getPointsForStatus(attendanceStatus)
        }
        return copy(points = dynamicPoints)
    }
}

