package com.example.presentation.evaluation.matrix

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.activity.ActivityStatus
import com.example.domain.attendance.AttendancePointsSettings
import com.example.domain.attendance.AttendanceRepository
import com.example.domain.attendance.MeetingWithAttendance
import com.example.domain.evaluation.CriteriaType
import com.example.domain.evaluation.EvaluationCriteria
import com.example.domain.evaluation.EvaluationRepository
import com.example.domain.evaluation.ScoutEvaluation
import com.example.domain.scout.Scout
import com.example.domain.scout.ScoutRepository
import com.example.domain.settings.SettingsRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.text.Collator
import java.util.Locale

/**
 * نموذج عرض مصفوفة التقييم الشاملة (Evaluation Matrix ViewModel)
 * يجمع بين نسب الحضور، تقييم القادة للأنشطة، وتقييم حفظ القرآن والكراس الفصلي،
 * بالإضافة لنقاط التقدم لشارات الكفاية والهوايات الإضافية، ويحسب المجموع الكلي والمعدل العام لكل شبل.
 */
class EvaluationMatrixViewModel(
    private val scoutRepository: ScoutRepository,
    private val attendanceRepository: AttendanceRepository,
    private val evaluationRepository: EvaluationRepository,
    private val settingsRepository: SettingsRepository,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")

    private data class ProgressCalculationData(
        val scoutBadges: List<Map<String, Any?>>,
        val requiredHobbyCount: Int,
        val scoutHobbyBadges: List<Map<String, Any?>>
    )

    private fun observeScoutBadges(): Flow<List<Map<String, Any?>>> = callbackFlow {
        val listener = firestore.collection("scout_badges").addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val list = snapshot?.documents?.mapNotNull { it.data }.orEmpty()
            trySend(list)
        }
        awaitClose { listener.remove() }
    }

    private fun observeProgressRequirements(): Flow<List<Map<String, Any?>>> = callbackFlow {
        val listener = firestore.collection("progress_requirements").addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val list = snapshot?.documents?.mapNotNull { it.data }.orEmpty()
            trySend(list)
        }
        awaitClose { listener.remove() }
    }

    private fun observeScoutHobbyBadges(): Flow<List<Map<String, Any?>>> = callbackFlow {
        val listener = firestore.collection("scout_hobby_badges").addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val list = snapshot?.documents?.mapNotNull { it.data }.orEmpty()
            trySend(list)
        }
        awaitClose { listener.remove() }
    }

    private val progressBonusFlow = combine(
        observeScoutBadges(),
        observeProgressRequirements(),
        observeScoutHobbyBadges()
    ) { badges, reqs, hobbies ->
        val maxRequiredHobby = reqs.mapNotNull { (it["linkedHobbyBadgesCount"] as? Number)?.toInt() }.maxOrNull() ?: 0
        ProgressCalculationData(
            scoutBadges = badges,
            requiredHobbyCount = maxRequiredHobby,
            scoutHobbyBadges = hobbies
        )
    }

    private data class BaseCalculationData(
        val activeScouts: List<Scout>,
        val passedMeetings: List<MeetingWithAttendance>,
        val totalPossibleAttendancePoints: Int,
        val allEvaluations: List<ScoutEvaluation>,
        val criteriaMap: Map<String, EvaluationCriteria>,
        val quranCriteria: EvaluationCriteria?,
        val notebookCriteria: EvaluationCriteria?,
        val pointsSettings: AttendancePointsSettings,
        val totalMeetingsHeld: Int
    )

    private val baseDataFlow = combine(
        scoutRepository.observeScouts(),
        attendanceRepository.getTrainingMeetingsRegister(),
        settingsRepository.observePointsSettings(),
        evaluationRepository.observeAllEvaluations(),
        evaluationRepository.observeCriteria()
    ) { scouts, meetings, pointsSettings, allEvaluations, criteriaList ->
        val activeScouts = scouts.filter { it.isActive }
        val criteriaMap = criteriaList.associateBy { it.id }

        val quranCriteria = criteriaList.firstOrNull {
            it.type == EvaluationCriteria.TYPE_QURAN ||
                    it.type == CriteriaType.QURAN.name ||
                    it.type == CriteriaType.QURAN.value ||
                    it.name.contains("قرآن") ||
                    it.name.contains("حفظ")
        }
        val notebookCriteria = criteriaList.firstOrNull {
            it.type == EvaluationCriteria.TYPE_NOTEBOOK ||
                    it.type == CriteriaType.NOTEBOOK.name ||
                    it.type == CriteriaType.NOTEBOOK.value ||
                    it.name.contains("كراس")
        }

        val currentTime = System.currentTimeMillis()
        val passedMeetings = meetings.filter { meeting ->
            meeting.activity.isPast() ||
                    meeting.activity.status == ActivityStatus.DONE.key ||
                    meeting.activity.endDate < currentTime
        }
        val totalMeetingsHeld = passedMeetings.size
        val maxPointsPerMeeting = pointsSettings.basePoints + pointsSettings.earlyBonus
        val totalPossibleAttendancePoints = totalMeetingsHeld * maxPointsPerMeeting

        BaseCalculationData(
            activeScouts = activeScouts,
            passedMeetings = passedMeetings,
            totalPossibleAttendancePoints = totalPossibleAttendancePoints,
            allEvaluations = allEvaluations,
            criteriaMap = criteriaMap,
            quranCriteria = quranCriteria,
            notebookCriteria = notebookCriteria,
            pointsSettings = pointsSettings,
            totalMeetingsHeld = totalMeetingsHeld
        )
    }

    private data class MatrixCalculationData(
        val matrixRows: List<ScoutMatrixRow>,
        val totalMeetingsHeld: Int,
        val totalScoutsCount: Int
    )

    private val matrixDataFlow = combine(
        baseDataFlow,
        progressBonusFlow
    ) { baseData, progressData ->
        val collator = Collator.getInstance(Locale("ar", "DZ")).apply {
            strength = Collator.PRIMARY
        }

        val unrankedRows = baseData.activeScouts.map { scout ->
            calculateScoutMatrixRow(
                scout = scout,
                passedMeetings = baseData.passedMeetings,
                totalPossibleAttendancePoints = baseData.totalPossibleAttendancePoints,
                allEvaluations = baseData.allEvaluations,
                criteriaMap = baseData.criteriaMap,
                quranCriteria = baseData.quranCriteria,
                notebookCriteria = baseData.notebookCriteria,
                progressData = progressData,
                pointsSettings = baseData.pointsSettings
            )
        }

        // الترتيب حسب المعدل العام تنازلياً (الأعلى أولاً)، ثم أبجدياً عند التساوي، وتعيين رتبة الترتيب
        val matrixRows = unrankedRows.sortedWith { a, b ->
            val aScore = a.overallScore
            val bScore = b.overallScore
            when {
                aScore != null && bScore != null -> {
                    val scoreDiff = bScore.compareTo(aScore)
                    if (scoreDiff != 0) scoreDiff else collator.compare(a.scoutName, b.scoutName)
                }
                aScore != null && bScore == null -> -1
                aScore == null && bScore != null -> 1
                else -> collator.compare(a.scoutName, b.scoutName)
            }
        }.mapIndexed { index, row ->
            row.copy(rank = index + 1)
        }

        MatrixCalculationData(
            matrixRows = matrixRows,
            totalMeetingsHeld = baseData.totalMeetingsHeld,
            totalScoutsCount = baseData.activeScouts.size
        )
    }

    val uiState: StateFlow<EvaluationMatrixUiState> = combine(
        matrixDataFlow,
        _searchQuery
    ) { data, query ->
        val filteredRows = if (query.isBlank()) {
            data.matrixRows
        } else {
            val trimmed = query.trim().lowercase(Locale("ar", "DZ"))
            data.matrixRows.filter {
                it.scoutName.lowercase(Locale("ar", "DZ")).contains(trimmed)
            }
        }

        EvaluationMatrixUiState(
            isLoading = false,
            rows = data.matrixRows,
            filteredRows = filteredRows,
            searchQuery = query,
            totalMeetingsHeld = data.totalMeetingsHeld,
            totalScoutsCount = data.totalScoutsCount,
            errorMessage = null
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = EvaluationMatrixUiState(isLoading = true)
    )

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }

    private fun calculateScoutMatrixRow(
        scout: Scout,
        passedMeetings: List<MeetingWithAttendance>,
        totalPossibleAttendancePoints: Int,
        allEvaluations: List<ScoutEvaluation>,
        criteriaMap: Map<String, EvaluationCriteria>,
        quranCriteria: EvaluationCriteria?,
        notebookCriteria: EvaluationCriteria?,
        progressData: ProgressCalculationData,
        pointsSettings: AttendancePointsSettings
    ): ScoutMatrixRow {
        // a) تقييم الحضور (من 20)
        val earnedAttendancePoints = passedMeetings.sumOf { meeting ->
            meeting.rows.firstOrNull { it.scout.id == scout.id }?.attendance?.points ?: 0
        }
        val attendanceScore = if (totalPossibleAttendancePoints > 0) {
            ((earnedAttendancePoints.toDouble() / totalPossibleAttendancePoints.toDouble()) * 20.0).coerceIn(0.0, 20.0)
        } else {
            null
        }

        // b) تقييم القادة (من 20)
        val activityEvaluations = allEvaluations.filter {
            it.scoutId == scout.id && !it.activityId.startsWith("TERM_")
        }
        val leaderScore: Double? = if (activityEvaluations.isNotEmpty()) {
            val totalScores = activityEvaluations.map { eval ->
                (eval.criteriaScores.values.sum() - eval.penaltyAmount).coerceAtLeast(0).toDouble()
            }
            val totalMaxScores = activityEvaluations.map { eval ->
                eval.criteriaScores.keys.mapNotNull { criteriaMap[it]?.maxScore }.sum().toDouble()
            }
            val averageScore = totalScores.average()
            val averageMax = totalMaxScores.average()
            if (averageMax > 0.0) {
                ((averageScore / averageMax) * 20.0).coerceIn(0.0, 20.0)
            } else {
                null
            }
        } else {
            null
        }

        // c) تقييم حفظ القرآن (من 20)
        val quarterlyEvaluations = allEvaluations.filter {
            it.scoutId == scout.id && it.activityId.startsWith("TERM_")
        }
        val latestQuarterly = quarterlyEvaluations.maxByOrNull { it.createdAt }

        val quranRawScore = latestQuarterly?.criteriaScores?.get(quranCriteria?.id)
        val quranScore: Double? = quranRawScore?.toDouble()?.coerceIn(0.0, 20.0)

        // d) تقييم الكراس (من 20)
        val notebookRawScore = latestQuarterly?.criteriaScores?.get(notebookCriteria?.id)
        val notebookScore: Double? = notebookRawScore?.toDouble()?.coerceIn(0.0, 20.0)

        val validScores = listOfNotNull(attendanceScore, leaderScore, quranScore, notebookScore)

        // e) نقاط شارات الكفاية وشارات الهواية الإضافية
        val kafaaCount = progressData.scoutBadges.count {
            it["scoutId"] == scout.id && it["revokedAt"] == null
        }
        val calculatedKafaaPoints = kafaaCount * pointsSettings.kafaaBadgePoints
        val kafaaPoints: Int? = if (calculatedKafaaPoints > 0) calculatedKafaaPoints else if (kafaaCount > 0) 0 else null

        val totalHobbyBadges = progressData.scoutHobbyBadges.count {
            it["scoutId"] == scout.id && it["revokedAt"] == null
        }
        val extraHobbyBadges = maxOf(0, totalHobbyBadges - progressData.requiredHobbyCount)
        val calculatedHobbyPoints = extraHobbyBadges * pointsSettings.extraHobbyBadgePoints
        val hobbyPoints: Int? = if (calculatedHobbyPoints > 0) calculatedHobbyPoints else if (totalHobbyBadges > 0) 0 else null

        // f) المجموع الكلي: مجموع درجات الحضور + القادة + الحفظ + الكراس + شارات الكفاية + شارات الهواية
        val baseSum = validScores.sum()
        val bonusSum = ((kafaaPoints ?: 0) + (hobbyPoints ?: 0)).toDouble()
        val totalScore: Double? = if (validScores.isNotEmpty()) {
            baseSum + bonusSum
        } else if (kafaaPoints != null || hobbyPoints != null) {
            bonusSum
        } else {
            null
        }

        // g) المعدل العام: ناتج قسمة المجموع الكلي على 4
        val overallScore: Double? = if (totalScore != null) {
            (totalScore / 4.0).coerceAtLeast(0.0)
        } else {
            null
        }

        return ScoutMatrixRow(
            rank = 0,
            scoutId = scout.id,
            scoutName = scout.fullName.ifBlank { "${scout.name} ${scout.surname}".trim() },
            attendanceScore = attendanceScore,
            leaderScore = leaderScore,
            quranScore = quranScore,
            notebookScore = notebookScore,
            overallScore = overallScore,
            kafaaPoints = kafaaPoints,
            hobbyPoints = hobbyPoints,
            totalScore = totalScore
        )
    }

    class Factory(
        private val scoutRepository: ScoutRepository,
        private val attendanceRepository: AttendanceRepository,
        private val evaluationRepository: EvaluationRepository,
        private val settingsRepository: SettingsRepository,
        private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(EvaluationMatrixViewModel::class.java)) {
                return EvaluationMatrixViewModel(
                    scoutRepository = scoutRepository,
                    attendanceRepository = attendanceRepository,
                    evaluationRepository = evaluationRepository,
                    settingsRepository = settingsRepository,
                    firestore = firestore
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
