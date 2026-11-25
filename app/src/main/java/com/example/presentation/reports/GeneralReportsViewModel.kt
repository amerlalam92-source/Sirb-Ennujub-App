package com.example.presentation.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.activity.Activity
import com.example.domain.activity.ActivityRepository
import com.example.domain.activity.ActivityStatus
import com.example.domain.attendance.Attendance
import com.example.domain.attendance.AttendanceRepository
import com.example.domain.attendance.AttendanceStatus
import com.example.domain.auth.AuthRepository
import com.example.domain.evaluation.EvaluationCriteria
import com.example.domain.evaluation.EvaluationRepository
import com.example.domain.evaluation.ScoutEvaluation
import com.example.domain.hobby.HobbyBadgeRepository
import com.example.domain.hobby.ScoutHobbyBadge
import com.example.domain.progress.BadgeLevel
import com.example.domain.progress.ProgressRepository
import com.example.domain.progress.ScoutBadge
import com.example.domain.scout.Scout
import com.example.domain.scout.ScoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.text.Collator
import java.util.Locale

typealias HobbyRepository = HobbyBadgeRepository

/**
 * بيانات إحصائيات الحضور لنشاط معين
 */
data class ActivityReportStats(
    val attendeesCount: Int = 0,
    val absentsCount: Int = 0,
    val attendanceRate: Double = 0.0
)

/**
 * حالة واجهة التقارير العامة (للحضور والأنشطة)
 */
data class GeneralReportsUiState(
    val isLoading: Boolean = true,
    val scouts: List<Scout> = emptyList(), // الأشبال النشطون مرتبون أبجدياً
    val pastActivities: List<Activity> = emptyList(), // الأنشطة السابقة مرتبة زمنياً تصاعدياً للمصفوفة
    val pastActivitiesDescending: List<Activity> = emptyList(), // الأنشطة السابقة مرتبة تنازلياً لتقرير الأنشطة
    val attendanceMap: Map<String, Map<String, Attendance>> = emptyMap(), // activityId -> (scoutId -> Attendance)
    val errorMessage: String? = null
) {
    /**
     * إرجاع سجل الحضور لشبل في نشاط معين
     */
    fun getAttendance(activityId: String, scoutId: String): Attendance? {
        return attendanceMap[activityId]?.get(scoutId)
    }

    /**
     * إجمالي مرات حضور الشبل (حاضر، متأخر، متأخر جداً)
     */
    fun getScoutTotalAttendance(scoutId: String): Int {
        if (pastActivities.isEmpty()) return 0
        return pastActivities.count { activity ->
            val record = getAttendance(activity.id, scoutId)
            record != null && record.attendanceStatus.isPresentOrLate
        }
    }

    /**
     * نسبة حضور الشبل المئوية من إجمالي الأنشطة السابقة
     */
    fun getScoutAttendancePercentage(scoutId: String): Double {
        if (pastActivities.isEmpty()) return 0.0
        val attended = getScoutTotalAttendance(scoutId)
        return (attended.toDouble() * 100.0 / pastActivities.size.toDouble())
    }

    /**
     * حساب إحصائيات نشاط معين
     */
    fun getActivityStats(activityId: String): ActivityReportStats {
        val records = attendanceMap[activityId] ?: emptyMap()
        val attendees = records.values.count { it.attendanceStatus.isPresentOrLate }
        val absents = records.values.count { it.attendanceStatus.isAbsent }
        val effectiveTotal = if (attendees + absents > 0) (attendees + absents) else scouts.size
        val rate = if (effectiveTotal > 0) (attendees.toDouble() * 100.0 / effectiveTotal.toDouble()) else 0.0
        return ActivityReportStats(
            attendeesCount = attendees,
            absentsCount = absents,
            attendanceRate = rate
        )
    }

    /**
     * إجمالي عدد الحضور عبر جميع الأنشطة
     */
    val totalAttendancesCount: Int
        get() = pastActivities.sumOf { getActivityStats(it.id).attendeesCount }

    /**
     * متوسط نسبة الحضور العام عبر جميع الأنشطة
     */
    val averageAttendanceRate: Double
        get() {
            if (pastActivities.isEmpty()) return 0.0
            val rates = pastActivities.map { getActivityStats(it.id).attendanceRate }
            return rates.average()
        }
}

/**
 * سطر مصفوفة تقرير التقدم الشامل لشبل واحد
 */
data class ScoutProgressReportRow(
    val scout: Scout,
    val hasAcceptance: Boolean = false,
    val hasBeginner: Boolean = false,
    val hasSecond: Boolean = false,
    val hasFirst: Boolean = false,
    val hobbyBadgesCount: Int = 0
)

/**
 * حالة واجهة تقرير التقدم الشامل (Progress Report UiState)
 */
data class ProgressReportUiState(
    val isLoading: Boolean = true,
    val rows: List<ScoutProgressReportRow> = emptyList(),
    val totalScouts: Int = 0,
    val acceptanceCount: Int = 0,
    val beginnerCount: Int = 0,
    val secondCount: Int = 0,
    val firstCount: Int = 0,
    val totalHobbiesAwarded: Int = 0,
    val errorMessage: String? = null
)

/**
 * قائد قام بتقييم شبل واحد على الأقل
 */
data class EvaluatingLeader(
    val id: String,
    val name: String
)

/**
 * سطر مصفوفة تقييم القادة لشبل واحد
 */
data class ScoutLeaderEvaluationRow(
    val scout: Scout,
    val leaderScores: Map<String, Double?> = emptyMap(), // leaderId -> average score
    val overallAverage: Double? = null
)

/**
 * حالة واجهة تقرير تقييم القادة (Leaders Evaluation Report UiState)
 */
data class LeadersEvaluationReportUiState(
    val isLoading: Boolean = true,
    val leaders: List<EvaluatingLeader> = emptyList(),
    val rows: List<ScoutLeaderEvaluationRow> = emptyList(),
    val totalScouts: Int = 0,
    val totalEvaluationsCount: Int = 0,
    val groupOverallAverage: Double? = null,
    val errorMessage: String? = null
)

/**
 * ViewModel لإدارة بيانات التقارير العامة الكشفية
 */
class GeneralReportsViewModel(
    private val scoutRepository: ScoutRepository,
    private val activityRepository: ActivityRepository,
    private val attendanceRepository: AttendanceRepository,
    private val progressRepository: ProgressRepository,
    private val hobbyBadgeRepository: HobbyBadgeRepository,
    private val evaluationRepository: EvaluationRepository,
    private val authRepository: AuthRepository,
    private val termsRepository: com.example.domain.settings.TermsRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(GeneralReportsUiState())
    val uiState: StateFlow<GeneralReportsUiState> = _uiState.asStateFlow()

    private val _progressReportData = MutableStateFlow(ProgressReportUiState())
    val progressReportData: StateFlow<ProgressReportUiState> = _progressReportData.asStateFlow()

    private val _leadersEvaluationReportData = MutableStateFlow(LeadersEvaluationReportUiState())
    val leadersEvaluationReportData: StateFlow<LeadersEvaluationReportUiState> = _leadersEvaluationReportData.asStateFlow()

    private val _isExportingPdf = MutableStateFlow(false)
    val isExportingPdf: StateFlow<Boolean> = _isExportingPdf.asStateFlow()

    private var currentTerms = com.example.domain.settings.CustomTerms()

    init {
        termsRepository?.observeTerms()?.let { flow ->
            viewModelScope.launch {
                flow.collect { currentTerms = it }
            }
        }
        loadReportsData()
        loadProgressReportData()
        loadLeadersEvaluationReportData()
    }

    private fun loadReportsData() {
        val collator = Collator.getInstance(Locale("ar", "DZ")).apply {
            strength = Collator.PRIMARY
        }

        val sinceTimestamp = AttendanceRepository.defaultScoutingYearStart()
        viewModelScope.launch {
            combine(
                scoutRepository.observeScouts(),
                activityRepository.observeActivities(),
                attendanceRepository.observeAllAttendance(sinceTimestamp)
            ) { scouts, activities, attendanceRecords ->
                val activeScouts = scouts.filter { it.isActive }.sortedWith { a, b ->
                    val comp = collator.compare(a.name, b.name)
                    if (comp != 0) comp else collator.compare(a.surname, b.surname)
                }

                val currentTime = System.currentTimeMillis()
                val pastList = activities.filter { activity ->
                    activity.startDate >= sinceTimestamp &&
                        (activity.startDate <= currentTime || activity.isPast() || activity.activityStatus == ActivityStatus.DONE)
                }

                val pastAscending = pastList.sortedWith(
                    compareBy<Activity> { it.startDate }.thenBy { it.createdAt }
                )

                val pastDescending = pastList.sortedWith(
                    compareByDescending<Activity> { it.startDate }.thenByDescending { it.createdAt }
                )

                val activeScoutIds = activeScouts.map { it.id }.toSet()
                val attendanceGrouped = attendanceRecords
                    .filter { it.scoutId in activeScoutIds }
                    .groupBy { it.activityId }
                    .mapValues { entry ->
                        entry.value.associateBy { it.scoutId }
                    }

                GeneralReportsUiState(
                    isLoading = false,
                    scouts = activeScouts,
                    pastActivities = pastAscending,
                    pastActivitiesDescending = pastDescending,
                    attendanceMap = attendanceGrouped,
                    errorMessage = null
                )
            }.catch { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.localizedMessage ?: "حدث خطأ أثناء تحميل بيانات التقارير"
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    private fun loadProgressReportData() {
        val collator = Collator.getInstance(Locale("ar", "DZ")).apply {
            strength = Collator.PRIMARY
        }

        viewModelScope.launch {
            combine(
                scoutRepository.observeScouts(),
                progressRepository.observeAllScoutBadges(),
                hobbyBadgeRepository.observeAllHobbyBadgesGranted()
            ) { scouts, allBadges, allHobbies ->
                val activeScouts = scouts.filter { it.isActive }.sortedWith { a, b ->
                    val comp = collator.compare(a.name, b.name)
                    if (comp != 0) comp else collator.compare(a.surname, b.surname)
                }

                val activeKafaaBadges = allBadges.filter { it.isActive() }
                val activeHobbyBadges = allHobbies.filter { it.isActive() }

                val badgesByScout = activeKafaaBadges.groupBy { it.scoutId }
                val hobbiesByScout = activeHobbyBadges.groupBy { it.scoutId }

                val rows = activeScouts.map { scout ->
                    val scoutBadges = badgesByScout[scout.id].orEmpty()
                    val scoutHobbies = hobbiesByScout[scout.id].orEmpty()

                    ScoutProgressReportRow(
                        scout = scout,
                        hasAcceptance = scoutBadges.any { it.badgeLevel == BadgeLevel.ACCEPTANCE.key },
                        hasBeginner = scoutBadges.any { it.badgeLevel == BadgeLevel.BEGINNER.key },
                        hasSecond = scoutBadges.any { it.badgeLevel == BadgeLevel.SECOND.key },
                        hasFirst = scoutBadges.any { it.badgeLevel == BadgeLevel.FIRST.key },
                        hobbyBadgesCount = scoutHobbies.size
                    )
                }

                ProgressReportUiState(
                    isLoading = false,
                    rows = rows,
                    totalScouts = activeScouts.size,
                    acceptanceCount = rows.count { it.hasAcceptance },
                    beginnerCount = rows.count { it.hasBeginner },
                    secondCount = rows.count { it.hasSecond },
                    firstCount = rows.count { it.hasFirst },
                    totalHobbiesAwarded = rows.sumOf { it.hobbyBadgesCount },
                    errorMessage = null
                )
            }.catch { e ->
                _progressReportData.value = _progressReportData.value.copy(
                    isLoading = false,
                    errorMessage = e.localizedMessage ?: "حدث خطأ أثناء تحميل بيانات تقرير التقدم"
                )
            }.collect { state ->
                _progressReportData.value = state
            }
        }
    }

    private fun loadLeadersEvaluationReportData() {
        val collator = Collator.getInstance(Locale("ar", "DZ")).apply {
            strength = Collator.PRIMARY
        }

        viewModelScope.launch {
            combine(
                scoutRepository.observeScouts(),
                evaluationRepository.observeAllEvaluations(),
                authRepository.observeAllLeaders()
            ) { scouts, allEvaluations, registeredLeaders ->
                val activeScouts = scouts.filter { it.isActive }.sortedWith { a, b ->
                    val comp = collator.compare(a.name, b.name)
                    if (comp != 0) comp else collator.compare(a.surname, b.surname)
                }

                val activeScoutIds = activeScouts.map { it.id }.toSet()
                // تصفية تقييمات الأنشطة للأشبال النشطين فقط (استثناء التقييمات الفصلية TERM_)
                val activityEvaluations = allEvaluations.filter {
                    !it.activityId.startsWith("TERM_") && it.scoutId in activeScoutIds
                }

                // دمج جميع القادة المسجلين في النظام مع أي قادة وردت تقييماتهم
                val leadersMap = mutableMapOf<String, EvaluatingLeader>()

                // 1. إضافة كافة القادة المسجلين في مجموعة leaders
                registeredLeaders.forEach { leader ->
                    val effectiveName = leader.fullName.ifBlank { "قائد" }
                    leadersMap[leader.uid] = EvaluatingLeader(
                        id = leader.uid,
                        name = effectiveName
                    )
                }

                // 2. إضافة أي قادة إضافيين وردت لهم تقييمات سابقة
                activityEvaluations.forEach { eval ->
                    if (!leadersMap.containsKey(eval.leaderId)) {
                        val evalLeaderName = eval.leaderName.ifBlank { "قائد" }
                        leadersMap[eval.leaderId] = EvaluatingLeader(
                            id = eval.leaderId,
                            name = evalLeaderName
                        )
                    }
                }

                // ترتيب القادة أبجدياً
                val leaders = leadersMap.values.sortedWith { a, b -> collator.compare(a.name, b.name) }

                val evalsByScout = activityEvaluations.groupBy { it.scoutId }

                val rows = activeScouts.map { scout ->
                    val scoutEvals = evalsByScout[scout.id].orEmpty()

                    val leaderScores = leaders.associate { leader ->
                        val evalsByLeader = scoutEvals.filter { it.leaderId == leader.id }
                        val avg = if (evalsByLeader.isNotEmpty()) {
                            evalsByLeader.map { eval ->
                                (eval.criteriaScores.values.sum() - eval.penaltyAmount).coerceAtLeast(0).toDouble()
                            }.average()
                        } else null
                        leader.id to avg
                    }

                    val overallAvg = if (scoutEvals.isNotEmpty()) {
                        scoutEvals.map { eval ->
                            (eval.criteriaScores.values.sum() - eval.penaltyAmount).coerceAtLeast(0).toDouble()
                        }.average()
                    } else null

                    ScoutLeaderEvaluationRow(
                        scout = scout,
                        leaderScores = leaderScores,
                        overallAverage = overallAvg
                    )
                }

                val allScores = activityEvaluations.map { eval ->
                    (eval.criteriaScores.values.sum() - eval.penaltyAmount).coerceAtLeast(0).toDouble()
                }
                val groupAvg = if (allScores.isNotEmpty()) allScores.average() else null

                LeadersEvaluationReportUiState(
                    isLoading = false,
                    leaders = leaders,
                    rows = rows,
                    totalScouts = activeScouts.size,
                    totalEvaluationsCount = activityEvaluations.size,
                    groupOverallAverage = groupAvg,
                    errorMessage = null
                )
            }.catch { e ->
                _leadersEvaluationReportData.value = _leadersEvaluationReportData.value.copy(
                    isLoading = false,
                    errorMessage = e.localizedMessage ?: "حدث خطأ أثناء تحميل بيانات تقرير تقييم القادة"
                )
            }.collect { state ->
                _leadersEvaluationReportData.value = state
            }
        }
    }

    /**
     * تصدير ومشاركة تقرير الحضور الشامل كـ PDF
     */
    fun exportAndShareAttendanceReport(context: Context) {
        val state = _uiState.value
        if (state.scouts.isEmpty()) return
        viewModelScope.launch {
            _isExportingPdf.value = true
            try {
                val file = withContext(Dispatchers.IO) {
                    val attMap = state.scouts.associate { scout ->
                        val scoutActMap = state.pastActivities.associate { act ->
                            val record = state.getAttendance(act.id, scout.id)
                            act.id to (record?.attendanceStatus?.key ?: "")
                        }
                        scout.id to scoutActMap
                    }
                    PdfExporter.exportGeneralAttendancePdf(
                        scouts = state.scouts,
                        activities = state.pastActivities,
                        attendanceMap = attMap,
                        context = context,
                        terms = currentTerms
                    )
                }
                PdfExporter.shareGeneralAttendancePdf(file, context, terms = currentTerms)
            } catch (_: Exception) {
            } finally {
                _isExportingPdf.value = false
            }
        }
    }

    /**
     * تصدير ومشاركة تقرير النشاط العام كـ PDF
     */
    fun exportAndShareActivitiesReport(context: Context) {
        val state = _uiState.value
        if (state.pastActivitiesDescending.isEmpty()) return
        viewModelScope.launch {
            _isExportingPdf.value = true
            try {
                val file = withContext(Dispatchers.IO) {
                    val dateFormat = SimpleDateFormat("dd/MM/yyyy", com.example.presentation.common.AppLocale.AR_DZ)
                    val summaries = state.pastActivitiesDescending.map { act ->
                        val stats = state.getActivityStats(act.id)
                        val dateStr = if (act.startDate > 0) dateFormat.format(Date(act.startDate)) else "—"
                        PdfExporter.ActivitySummary(
                            title = act.title,
                            type = act.activityType.displayLabel(currentTerms),
                            date = dateStr,
                            presentCount = stats.attendeesCount,
                            absentCount = stats.absentsCount,
                            attendancePercentage = stats.attendanceRate
                        )
                    }
                    PdfExporter.exportActivitiesReportPdf(
                        activities = summaries,
                        context = context,
                        terms = currentTerms
                    )
                }
                PdfExporter.shareActivitiesReportPdf(file, context, terms = currentTerms)
            } catch (_: Exception) {
            } finally {
                _isExportingPdf.value = false
            }
        }
    }

    /**
     * تصدير ومشاركة تقرير التقدم الشامل كـ PDF
     */
    fun exportAndShareProgressReport(context: Context) {
        val state = _progressReportData.value
        if (state.rows.isEmpty()) return
        viewModelScope.launch {
            _isExportingPdf.value = true
            try {
                val file = withContext(Dispatchers.IO) {
                    val scouts = state.rows.map { it.scout }
                    val badgeMap = state.rows.associate { row ->
                        val badges = mutableListOf<String>()
                        if (row.hasAcceptance) badges.add("ACCEPTANCE")
                        if (row.hasBeginner) badges.add("BEGINNER")
                        if (row.hasSecond) badges.add("SECOND")
                        if (row.hasFirst) badges.add("FIRST")
                        row.scout.id to badges
                    }
                    val hobbyCountMap = state.rows.associate { it.scout.id to it.hobbyBadgesCount }
                    PdfExporter.exportProgressReportPdf(
                        scouts = scouts,
                        badgeMap = badgeMap,
                        hobbyCountMap = hobbyCountMap,
                        context = context,
                        terms = currentTerms
                    )
                }
                PdfExporter.shareProgressReportPdf(file, context, terms = currentTerms)
            } catch (_: Exception) {
            } finally {
                _isExportingPdf.value = false
            }
        }
    }

    /**
     * تصدير ومشاركة تقرير تقييم القادة كـ PDF
     */
    fun exportAndShareLeadersEvaluationReport(context: Context) {
        val state = _leadersEvaluationReportData.value
        if (state.rows.isEmpty()) return
        viewModelScope.launch {
            _isExportingPdf.value = true
            try {
                val file = withContext(Dispatchers.IO) {
                    val scoutNames = state.rows.map { "${it.scout.name} ${it.scout.surname}".trim() }
                    val leaderNames = state.leaders.map { it.name }
                    val matrixData = state.rows.associate { row ->
                        val scoutName = "${row.scout.name} ${row.scout.surname}".trim()
                        val scores = state.leaders.associate { leader ->
                            val score = row.leaderScores[leader.id]
                            val scoreText = if (score != null) {
                                if (score % 1.0 == 0.0) "${score.toInt()}/50" else String.format(Locale.US, "%.1f/50", score)
                            } else "—"
                            leader.name to scoreText
                        }.toMutableMap()

                        val overallText = if (row.overallAverage != null) {
                            if (row.overallAverage % 1.0 == 0.0) "${row.overallAverage.toInt()}/50" else String.format(Locale.US, "%.1f/50", row.overallAverage)
                        } else "—"
                        scores["المعدل العام"] = overallText

                        scoutName to scores
                    }
                    PdfExporter.exportLeadersEvaluationPdf(
                        scoutNames = scoutNames,
                        leaderNames = leaderNames,
                        matrixData = matrixData,
                        context = context,
                        terms = currentTerms
                    )
                }
                PdfExporter.shareLeadersEvaluationPdf(file, context, terms = currentTerms)
            } catch (_: Exception) {
            } finally {
                _isExportingPdf.value = false
            }
        }
    }

    /**
     * مصنع لإنشاء نموذج العرض بحقن المستودعات
     */
    class Factory(
        private val scoutRepository: ScoutRepository,
        private val activityRepository: ActivityRepository,
        private val attendanceRepository: AttendanceRepository,
        private val progressRepository: ProgressRepository,
        private val hobbyBadgeRepository: HobbyBadgeRepository,
        private val evaluationRepository: EvaluationRepository,
        private val authRepository: AuthRepository,
        private val termsRepository: com.example.domain.settings.TermsRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(GeneralReportsViewModel::class.java)) {
                return GeneralReportsViewModel(
                    scoutRepository = scoutRepository,
                    activityRepository = activityRepository,
                    attendanceRepository = attendanceRepository,
                    progressRepository = progressRepository,
                    hobbyBadgeRepository = hobbyBadgeRepository,
                    evaluationRepository = evaluationRepository,
                    authRepository = authRepository,
                    termsRepository = termsRepository
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
