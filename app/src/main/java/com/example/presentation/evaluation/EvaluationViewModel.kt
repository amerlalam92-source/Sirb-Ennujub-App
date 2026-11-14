package com.example.presentation.evaluation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.activity.ActivityRepository
import com.example.domain.activity.ActivityStatus
import com.example.domain.attendance.AttendancePointsSettings
import com.example.domain.attendance.AttendanceRepository
import com.example.domain.auth.AuthRepository
import com.example.domain.evaluation.CriteriaType
import com.example.domain.evaluation.EvaluationCriteria
import com.example.domain.evaluation.EvaluationRepository
import com.example.domain.evaluation.ScoutEvaluation
import com.example.domain.quran.ScoutQuranEnrollmentRepository
import com.example.domain.scout.Scout
import com.example.domain.scout.ScoutRepository
import com.example.domain.settings.CustomTerms
import com.example.domain.settings.TermsRepository
import com.example.presentation.reports.PdfExporter
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.Collator
import java.util.Locale

/**
 * نموذج عرض شاشة التقييم الفصلي للأشبال (Evaluation ViewModel)
 * يدير:
 * - التقييم الفصلي بـ 6 معايير (4 معايير للقائد + حفظ القرآن والكراس) في مستند "{scoutId}_{termId}_{leaderId}".
 * - صلاحيات القادة: القائد العادي يجلب تقييماته فقط ولا يرى درجات القادة الآخرين،
 *   بينما القائد الرئيسي (isMainLeader) يجلب جميع التقييمات ويرى درجات القادة الآخرين للقراءة فقط.
 * - حفظ وتحديث المعايير المشتركة (القرآن والكراس) بحيث يُعتمد أحدث تعديل حسب createdAt.
 * - قفل التعديل بعد 7 أيام وجلب الحفظ المسجل من المدرسة القرآنية.
 */
class EvaluationViewModel(
    @Suppress("unused") private val activityRepository: ActivityRepository? = null,
    private val scoutRepository: ScoutRepository,
    private val evaluationRepository: EvaluationRepository,
    private val authRepository: AuthRepository,
    private val scoutQuranEnrollmentRepository: ScoutQuranEnrollmentRepository? = null,
    private val attendanceRepository: AttendanceRepository? = null,
    private val termsRepository: TermsRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        EvaluationUiState(
            currentLeaderId = authRepository.currentUser?.uid.orEmpty(),
            isLoading = true
        )
    )
    val uiState: StateFlow<EvaluationUiState> = _uiState.asStateFlow()

    private var evaluationsJob: Job? = null
    private var leadersNamesJob: Job? = null
    private var currentTerms = CustomTerms()

    init {
        observeCurrentLeaderRole()
        observeScouts()
        observeCriteria()
        observeQuranEnrollments()
        termsRepository?.let { repo ->
            viewModelScope.launch {
                repo.observeTerms().collect { terms ->
                    currentTerms = terms
                }
            }
        }
        loadEvaluations()
    }

    /**
     * مراقبة صلاحية القائد الحالي (رئيسي أم عادي) وتحديث جلب التقييمات تلقائياً
     */
    private fun observeCurrentLeaderRole() {
        viewModelScope.launch {
            authRepository.observeCurrentLeader()
                .catch { /* keep default non-main role */ }
                .collect { leader ->
                    val leaderId = leader?.uid?.ifBlank { null } ?: authRepository.currentUser?.uid.orEmpty()
                    val isMain = leader?.isMainLeader == true
                    val prevState = _uiState.value
                    val changed = prevState.isMainLeader != isMain || prevState.currentLeaderId != leaderId

                    _uiState.update {
                        it.copy(
                            isMainLeader = isMain,
                            currentLeaderId = leaderId,
                            otherEvaluatedScoutIds = if (isMain) it.otherEvaluatedScoutIds else emptySet()
                        )
                    }

                    if (isMain) {
                        observeLeaderNames()
                    } else {
                        leadersNamesJob?.cancel()
                        leadersNamesJob = null
                        _uiState.update { it.copy(leaderNames = emptyMap()) }
                    }

                    if (changed) {
                        loadEvaluations()
                    }
                }
        }
    }

    private fun observeLeaderNames() {
        if (leadersNamesJob?.isActive == true) return
        leadersNamesJob = viewModelScope.launch {
            authRepository.observeAllLeaders()
                .catch { /* ignore */ }
                .collect { leaders ->
                    val map = leaders.associate { it.uid to it.fullName.ifBlank { "قائد كشفي" } }
                    _uiState.update { it.copy(leaderNames = map) }
                }
        }
    }

    /**
     * مراقبة الأشبال النشطين
     */
    private fun observeScouts() {
        viewModelScope.launch {
            scoutRepository.observeScouts()
                .catch { /* ignore or log */ }
                .collect { scouts ->
                    _uiState.update { state ->
                        state.copy(scouts = scouts.filter { it.isActive })
                    }
                }
        }
    }

    /**
     * مراقبة المعايير النشطة (غير المؤرشفة) مرتبة تصاعدياً
     */
    private fun observeCriteria() {
        viewModelScope.launch {
            evaluationRepository.observeCriteria()
                .catch { /* ignore or log */ }
                .collect { criteriaList ->
                    val activeList = criteriaList
                        .filter { !it.archived }
                        .sortedWith(
                            compareBy<EvaluationCriteria>(
                                { criteriaSectionOrder(it) },
                                { it.order },
                                { it.name }
                            )
                        )
                    _uiState.update { state ->
                        state.copy(criteria = activeList)
                    }
                }
        }
    }

    private fun criteriaSectionOrder(criteria: EvaluationCriteria): Int {
        val isQuran = criteria.type == EvaluationCriteria.TYPE_QURAN ||
                criteria.type == CriteriaType.QURAN.name ||
                criteria.type == CriteriaType.QURAN.value ||
                criteria.name.contains("قرآن") ||
                criteria.name.contains("حفظ")
        val isNotebook = criteria.type == EvaluationCriteria.TYPE_NOTEBOOK ||
                criteria.type == CriteriaType.NOTEBOOK.name ||
                criteria.type == CriteriaType.NOTEBOOK.value ||
                criteria.name.contains("كراس")
        return when {
            isQuran -> 1
            isNotebook -> 2
            else -> 0
        }
    }

    /**
     * مراقبة تسجيلات الأشبال في المدارس القرآنية لعرض المحفوظ المسجل
     */
    private fun observeQuranEnrollments() {
        val repo = scoutQuranEnrollmentRepository ?: return
        viewModelScope.launch {
            repo.observeAllEnrollments()
                .catch { /* ignore */ }
                .collect { enrollments ->
                    val enrollMap = enrollments.associateBy { it.scoutId }
                    _uiState.update { it.copy(quranEnrollments = enrollMap) }
                }
        }
    }

    /**
     * تغيير الفصل الدراسي المختار في التقييم الفصلي
     */
    fun onTermSelected(termId: String) {
        _uiState.update { it.copy(selectedTerm = termId, selectedScoutForEvaluation = null) }
        loadEvaluationsForTarget(termId)
    }

    /**
     * تحميل تقييمات الفصل الدراسي النشط
     */
    fun loadEvaluations() {
        val term = _uiState.value.selectedTerm.ifBlank { "TERM_1" }
        loadEvaluationsForTarget(term)
    }

    /**
     * جلب التقييمات للفصل الدراسي حسب صلاحية القائد:
     * - القائد العادي: يجلب تقييماته الخاصة فقط ({scoutId}_{termId}_{leaderId}) ولا يرى تقييمات الآخرين.
     * - القائد الرئيسي (isMainLeader): يجلب جميع تقييمات الفصل لكل القادة.
     */
    private fun loadEvaluationsForTarget(termId: String) {
        evaluationsJob?.cancel()
        evaluationsJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val currentLeaderId = _uiState.value.currentLeaderId.ifBlank {
                authRepository.currentUser?.uid.orEmpty()
            }
            val isMainLeader = _uiState.value.isMainLeader

            val sourceFlow = if (isMainLeader) {
                evaluationRepository.getEvaluationsForActivity(termId)
            } else {
                evaluationRepository.getEvaluationsForActivityByLeader(termId, currentLeaderId)
            }

            sourceFlow
                .catch { e ->
                    _uiState.update { it.copy(isLoading = false, snackbarMessage = e.message) }
                }
                .collect { evalList ->
                    if (isMainLeader) {
                        val myEvalMap = evalList
                            .filter { it.leaderId == currentLeaderId }
                            .associateBy { it.scoutId }

                        val otherScoutIds = evalList
                            .filter { it.leaderId != currentLeaderId && !myEvalMap.containsKey(it.scoutId) }
                            .map { it.scoutId }
                            .toSet()

                        _uiState.update {
                            it.copy(
                                allEvaluations = evalList,
                                evaluations = myEvalMap,
                                otherEvaluatedScoutIds = otherScoutIds,
                                isLoading = false
                            )
                        }
                    } else {
                        val ownList = evalList.filter { it.leaderId == currentLeaderId }
                        val myEvalMap = ownList.associateBy { it.scoutId }
                        _uiState.update {
                            it.copy(
                                allEvaluations = ownList,
                                evaluations = myEvalMap,
                                otherEvaluatedScoutIds = emptySet(),
                                isLoading = false
                            )
                        }
                    }
                }
        }
    }

    /**
     * فتح نافذة التقييم لشبل محدد
     */
    fun onScoutSelectedForEvaluation(scout: Scout) {
        _uiState.update { it.copy(selectedScoutForEvaluation = scout) }
    }

    /**
     * إغلاق نافذة التقييم
     */
    fun dismissEvaluationDialog() {
        _uiState.update { it.copy(selectedScoutForEvaluation = null) }
    }

    /**
     * تعيين أو إلغاء تقييم مراد حذفه بعد تأكيد المستخدم
     */
    fun setPendingDeleteEvaluation(evaluationId: String?) {
        _uiState.update { it.copy(pendingDeleteEvaluationId = evaluationId) }
    }

    /**
     * حذف تقييم محدد مع التحقق من شرط قفل الـ 7 أيام
     */
    fun deleteEvaluation(evaluationId: String) {
        val existingEval = _uiState.value.evaluations.values.firstOrNull { it.id == evaluationId }
            ?: _uiState.value.allEvaluations.firstOrNull { it.id == evaluationId }

        if (_uiState.value.isEvaluationLocked(existingEval)) {
            _uiState.update {
                it.copy(
                    pendingDeleteEvaluationId = null,
                    snackbarMessage = "انتهت مدة التعديل (مر أسبوع منذ التقييم)."
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val result = evaluationRepository.deleteEvaluation(evaluationId)
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        selectedScoutForEvaluation = null,
                        pendingDeleteEvaluationId = null,
                        snackbarMessage = "تم حذف التقييم بنجاح"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        pendingDeleteEvaluationId = null,
                        snackbarMessage = result.exceptionOrNull()?.message ?: "فشل حذف التقييم"
                    )
                }
            }
        }
    }

    /**
     * حفظ التقييم الفصلي للشبل بـ 6 معايير:
     * - معرف المستند دائماً "{scoutId}_{termId}_{leaderId}".
     * - تحديث createdAt إلى الوقت الحالي عند الحفظ ليصبح أحدث تعديل لمعايير القرآن والكراس في هذا الفصل.
     */
    fun saveEvaluation(
        scoutId: String,
        scoresMap: Map<String, Int>,
        @Suppress("unused") penaltyAmount: Int = 0,
        @Suppress("unused") penaltyReason: String? = null,
        testedAmount: String? = null
    ) {
        val currentState = _uiState.value
        val targetTermId = currentState.selectedTerm.ifBlank { "TERM_1" }
        val existingEval = currentState.evaluations[scoutId]

        // التحقق من قفل التعديل بعد 7 أيام
        if (currentState.isEvaluationLocked(existingEval)) {
            _uiState.update {
                it.copy(snackbarMessage = "انتهت مدة التعديل (مر أسبوع منذ التقييم).")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            val currentUser = authRepository.currentUser
            val leaderId = currentState.currentLeaderId.ifBlank { currentUser?.uid ?: "" }
            val leaderName = currentState.leaderNames[leaderId]
                ?: currentUser?.displayName?.takeIf { it.isNotBlank() }
                ?: "قائد كشفي"

            val evalDocId = "${scoutId}_${targetTermId}_$leaderId"
            val relevantCriteriaIds = currentState.criteria.map { it.id }.toSet()
            val filteredScores = scoresMap.filterKeys { it in relevantCriteriaIds }

            val evaluation = ScoutEvaluation(
                id = evalDocId,
                scoutId = scoutId,
                activityId = targetTermId,
                leaderId = leaderId,
                leaderName = leaderName,
                criteriaScores = filteredScores,
                penaltyAmount = 0,
                penaltyReason = null,
                testedAmount = testedAmount?.takeIf { it.isNotBlank() },
                createdAt = System.currentTimeMillis()
            )

            val result = evaluationRepository.saveEvaluation(evaluation)
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        selectedScoutForEvaluation = null,
                        snackbarMessage = "تم حفظ التقييم بنجاح"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        snackbarMessage = result.exceptionOrNull()?.message ?: "فشل حفظ التقييم"
                    )
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    /**
     * تصدير ومشاركة سجل النقاط الخام للأشبال (تقييم القادة، حفظ القرآن، الكراس) كملف PDF
     */
    fun shareRawPointsPdf(context: Context) {
        viewModelScope.launch {
            try {
                val scouts = scoutRepository.observeScouts().first().filter { it.isActive }
                val allEvaluations = evaluationRepository.observeAllEvaluations().first()
                val criteriaList = evaluationRepository.observeCriteria().first()
                val criteriaMap = criteriaList.associateBy { it.id }

                val meetings = attendanceRepository?.getTrainingMeetingsRegister()?.first() ?: emptyList()
                val pointsSettings = attendanceRepository?.observePointsSettings()?.first()
                    ?: AttendancePointsSettings()

                val currentTime = System.currentTimeMillis()
                val passedMeetings = meetings.filter { meeting ->
                    meeting.activity.isPast() ||
                            meeting.activity.status == ActivityStatus.DONE.key ||
                            meeting.activity.endDate < currentTime
                }
                val totalMeetingsHeld = passedMeetings.size
                val maxPointsPerMeeting = pointsSettings.basePoints + pointsSettings.earlyBonus
                val totalPossibleAttendancePoints = totalMeetingsHeld * maxPointsPerMeeting

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

                // جلب بيانات الشارات الممنوحة والمتطلبات لحساب نقاط التقدم
                val firestore = FirebaseFirestore.getInstance()
                val scoutBadgesSnapshot = firestore.collection("scout_badges").get().await()
                val progressReqsSnapshot = firestore.collection("progress_requirements").get().await()
                val hobbyBadgesSnapshot = firestore.collection("scout_hobby_badges").get().await()

                val scoutBadges = scoutBadgesSnapshot.documents.mapNotNull { it.data }
                val progressReqs = progressReqsSnapshot.documents.mapNotNull { it.data }
                val hobbyBadges = hobbyBadgesSnapshot.documents.mapNotNull { it.data }

                val requiredHobbyCount = progressReqs.mapNotNull { (it["linkedHobbyBadgesCount"] as? Number)?.toInt() }.maxOrNull() ?: 0

                val collator = Collator.getInstance(Locale("ar", "DZ")).apply {
                    strength = Collator.PRIMARY
                }

                val scoutPointsList = scouts.map { scout ->
                    // 1) تقييم الحضور (نقاط من 20)
                    val earnedAttendancePoints = passedMeetings.sumOf { meeting ->
                        meeting.rows.firstOrNull { it.scout.id == scout.id }?.attendance?.points ?: 0
                    }
                    val attendanceScore: Double? = if (totalPossibleAttendancePoints > 0) {
                        ((earnedAttendancePoints.toDouble() / totalPossibleAttendancePoints.toDouble()) * 20.0).coerceIn(0.0, 20.0)
                    } else {
                        null
                    }

                    val quarterlyEvals = allEvaluations.filter {
                        it.scoutId == scout.id && it.activityId.startsWith("TERM_")
                    }

                    // 2) تقييم القادة من التقييمات الفصلية (من 20)
                    val leaderAverages = quarterlyEvals
                        .groupBy { it.leaderId }
                        .mapNotNull { (_, leaderEvals) ->
                            val evalAverages = leaderEvals.mapNotNull { eval ->
                                val leaderScores = eval.criteriaScores.filterKeys { criteriaId ->
                                    val c = criteriaMap[criteriaId]
                                    criteriaId != quranCriteria?.id &&
                                            criteriaId != notebookCriteria?.id &&
                                            c != null &&
                                            c.type != EvaluationCriteria.TYPE_QURAN &&
                                            c.type != CriteriaType.QURAN.name &&
                                            c.type != CriteriaType.QURAN.value &&
                                            c.type != EvaluationCriteria.TYPE_NOTEBOOK &&
                                            c.type != CriteriaType.NOTEBOOK.name &&
                                            c.type != CriteriaType.NOTEBOOK.value
                                }.values
                                if (leaderScores.isNotEmpty()) leaderScores.sum().toDouble() / 4.0 else null
                            }
                            if (evalAverages.isNotEmpty()) evalAverages.average() else null
                        }
                    val leaderPoints: Double? = if (leaderAverages.isNotEmpty()) {
                        leaderAverages.average().coerceIn(0.0, 20.0)
                    } else {
                        null
                    }

                    // 3) حفظ القرآن: الدرجة الخام من أحدث تقييم فصلي يحتوي على تقييم القرآن (من 20)
                    val quranId = quranCriteria?.id
                    val quranPoints: Int? = if (quranId != null) {
                        quarterlyEvals
                            .filter { it.criteriaScores.containsKey(quranId) }
                            .maxByOrNull { it.createdAt }
                            ?.criteriaScores?.get(quranId)
                    } else null

                    // 4) تقييم الكراس: الدرجة الخام من أحدث تقييم فصلي يحتوي على تقييم الكراس (من 20)
                    val notebookId = notebookCriteria?.id
                    val notebookPoints: Int? = if (notebookId != null) {
                        quarterlyEvals
                            .filter { it.criteriaScores.containsKey(notebookId) }
                            .maxByOrNull { it.createdAt }
                            ?.criteriaScores?.get(notebookId)
                    } else null

                    // 5) نقاط التقدم (شارات الكفاية + شارات الهواية الإضافية)
                    val kafaaCount = scoutBadges.count {
                        it["scoutId"] == scout.id && it["revokedAt"] == null
                    }
                    val kafaaPoints = kafaaCount * pointsSettings.kafaaBadgePoints

                    val totalHobbyBadges = hobbyBadges.count {
                        it["scoutId"] == scout.id && it["revokedAt"] == null
                    }
                    val extraHobbyBadges = maxOf(0, totalHobbyBadges - requiredHobbyCount)
                    val extraHobbyPoints = extraHobbyBadges * pointsSettings.extraHobbyBadgePoints

                    val calculatedProgress = kafaaPoints + extraHobbyPoints
                    val hasAnyProgressData = kafaaCount > 0 || totalHobbyBadges > 0
                    val progressScore: Int? = if (calculatedProgress > 0) {
                        calculatedProgress
                    } else if (hasAnyProgressData) {
                        0
                    } else {
                        null
                    }

                    // 6) المجموع الكلي: مجموع درجات الحضور، القادة، القرآن، الكراس، ونقاط التقدم
                    val baseScores = listOfNotNull(attendanceScore, leaderPoints, quranPoints?.toDouble(), notebookPoints?.toDouble())
                    val totalScore: Int? = if (baseScores.isNotEmpty()) {
                        Math.round(baseScores.sum() + (progressScore ?: 0)).toInt()
                    } else {
                        progressScore
                    }

                    PdfExporter.ScoutRawPoints(
                        scoutName = scout.fullName.ifBlank { "${scout.name} ${scout.surname}".trim() },
                        attendanceScore = attendanceScore,
                        leaderPoints = leaderPoints,
                        quranPoints = quranPoints,
                        notebookPoints = notebookPoints,
                        progressScore = progressScore,
                        totalScore = totalScore
                    )
                }.sortedWith { a, b ->
                    collator.compare(a.scoutName, b.scoutName)
                }

                if (scoutPointsList.isEmpty()) {
                    _uiState.update { it.copy(snackbarMessage = "لا يوجد ${currentTerms.scoutPlural} نشطون لتصدير بياناتهم") }
                    return@launch
                }

                val file = PdfExporter.exportRawPointsPdf(scoutPointsList, context, terms = currentTerms)
                PdfExporter.shareRawPointsPdf(file, context, terms = currentTerms)
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "فشل تصدير ملف PDF: ${e.localizedMessage}") }
            }
        }
    }

    class Factory(
        private val activityRepository: ActivityRepository? = null,
        private val scoutRepository: ScoutRepository,
        private val evaluationRepository: EvaluationRepository,
        private val authRepository: AuthRepository,
        private val scoutQuranEnrollmentRepository: ScoutQuranEnrollmentRepository? = null,
        private val attendanceRepository: AttendanceRepository? = null,
        private val termsRepository: TermsRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(EvaluationViewModel::class.java)) {
                return EvaluationViewModel(
                    activityRepository = activityRepository,
                    scoutRepository = scoutRepository,
                    evaluationRepository = evaluationRepository,
                    authRepository = authRepository,
                    scoutQuranEnrollmentRepository = scoutQuranEnrollmentRepository,
                    attendanceRepository = attendanceRepository,
                    termsRepository = termsRepository
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
