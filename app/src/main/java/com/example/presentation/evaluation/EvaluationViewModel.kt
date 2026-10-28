package com.example.presentation.evaluation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.activity.Activity
import com.example.domain.activity.ActivityRepository
import com.example.domain.auth.AuthRepository
import com.example.domain.evaluation.CriteriaType
import com.example.domain.evaluation.EvaluationCriteria
import com.example.domain.evaluation.EvaluationRepository
import com.example.domain.evaluation.ScoutEvaluation
import com.example.domain.activity.ActivityStatus
import com.example.domain.attendance.AttendancePointsSettings
import com.example.domain.attendance.AttendanceRepository
import com.example.domain.quran.ScoutQuranEnrollmentRepository
import com.example.domain.scout.Scout
import com.example.domain.scout.ScoutRepository
import android.content.Context
import com.example.presentation.reports.PdfExporter
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Job
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Calendar
import java.util.Locale

/**
 * نموذج عرض شاشة تقييم الأشبال (Evaluation View Model)
 * يدير:
 * - تقييم متعدد القادة للأنشطة (لكل قائد تقييمه المستقل).
 * - التقييم الفصلي الموحد (تقييم مشترك يظهر للجميع ويبدأ برقم المعرف {scoutId}_{termId}).
 * - قفل التعديل بعد 7 أيام من تاريخ الإنشاء.
 * - جلب الحفظ المسجل للشبل من المدرسة القرآنية.
 * - حفظ وحذف التقييمات.
 */
class EvaluationViewModel(
    private val activityRepository: ActivityRepository,
    private val scoutRepository: ScoutRepository,
    private val evaluationRepository: EvaluationRepository,
    private val authRepository: AuthRepository,
    private val scoutQuranEnrollmentRepository: ScoutQuranEnrollmentRepository? = null,
    private val attendanceRepository: AttendanceRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(EvaluationUiState(isLoading = true))
    val uiState: StateFlow<EvaluationUiState> = _uiState.asStateFlow()

    private var evaluationsJob: Job? = null

    init {
        loadActivities()
        observeScouts()
        observeCriteria()
        observeQuranEnrollments()
    }

    /**
     * جلب الأنشطة المسجلة خلال آخر 30 يوماً
     */
    fun loadActivities() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            activityRepository.observeActivities()
                .catch { e ->
                    _uiState.update { it.copy(isLoading = false, snackbarMessage = e.message) }
                }
                .collect { allActivities ->
                    val thirtyDaysAgo = Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_YEAR, -30)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis

                    val recent = allActivities.filter { it.startDate >= thirtyDaysAgo || it.endDate >= thirtyDaysAgo }
                        .sortedByDescending { it.startDate }
                    val finalActivities = if (recent.isNotEmpty()) recent else allActivities.sortedByDescending { it.startDate }

                    _uiState.update {
                        it.copy(
                            activities = finalActivities,
                            isLoading = false
                        )
                    }
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
                    val activeList = criteriaList.filter { !it.archived }.sortedBy { it.order }
                    _uiState.update { state ->
                        state.copy(criteria = activeList)
                    }
                }
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
     * تغيير التبويب المختار (0: تقييم الأنشطة، 1: التقييم الفصلي)
     */
    fun onTabSelected(tabIndex: Int) {
        _uiState.update { it.copy(selectedTab = tabIndex, selectedScoutForEvaluation = null) }
        loadEvaluations()
    }

    /**
     * تغيير الفصل الدراسي المختار في التقييم الفصلي
     */
    fun onTermSelected(termId: String) {
        _uiState.update { it.copy(selectedTerm = termId) }
        if (_uiState.value.selectedTab == 1) {
            loadEvaluationsForTarget(termId)
        }
    }

    /**
     * اختيار نشاط للانتقال للخطوة 2 وبدء مراقبة تقييماته (في تبويب الأنشطة)
     */
    fun onActivitySelected(activity: Activity) {
        _uiState.update { it.copy(selectedActivity = activity) }
        loadEvaluationsForTarget(activity.id)
    }

    /**
     * العودة لاختيار نشاط آخر (الخطوة 1)
     */
    fun clearSelectedActivity() {
        evaluationsJob?.cancel()
        _uiState.update {
            it.copy(
                selectedActivity = null,
                evaluations = emptyMap(),
                allEvaluations = emptyList(),
                otherEvaluatedScoutIds = emptySet(),
                selectedScoutForEvaluation = null
            )
        }
    }

    /**
     * تحميل التقييمات وفقاً للتبويب النشط
     */
    fun loadEvaluations() {
        val state = _uiState.value
        if (state.selectedTab == 0) {
            val activity = state.selectedActivity
            if (activity != null) {
                loadEvaluationsForTarget(activity.id)
            } else {
                evaluationsJob?.cancel()
                _uiState.update {
                    it.copy(
                        evaluations = emptyMap(),
                        allEvaluations = emptyList(),
                        otherEvaluatedScoutIds = emptySet()
                    )
                }
            }
        } else {
            val term = state.selectedTerm ?: "TERM_1"
            loadEvaluationsForTarget(term)
        }
    }

    /**
     * جلب جميع التقييمات للنشاط أو الفصل
     * في التقييم الفصلي (Tab 2): توحيد التقييمات بحيث يظهر أي تقييم مسجل للشبل للجميع.
     * في تقييم الأنشطة (Tab 1): فصل تقييم القائد الحالي عن باقي القادة.
     */
    private fun loadEvaluationsForTarget(targetId: String) {
        evaluationsJob?.cancel()
        evaluationsJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            evaluationRepository.getEvaluationsForActivity(targetId)
                .catch { e ->
                    _uiState.update { it.copy(isLoading = false, snackbarMessage = e.message) }
                }
                .collect { evalList ->
                    val currentLeaderId = authRepository.currentUser?.uid ?: ""
                    val isQuarterly = _uiState.value.selectedTab == 1 || targetId.startsWith("TERM_")

                    if (isQuarterly) {
                        // التقييم الفصلي موحد: أي تقييم يظهر للجميع ويمكن تعديله
                        val unifiedEvalMap = evalList.associateBy { it.scoutId }
                        _uiState.update {
                            it.copy(
                                allEvaluations = evalList,
                                evaluations = unifiedEvalMap,
                                otherEvaluatedScoutIds = emptySet(),
                                isLoading = false
                            )
                        }
                    } else {
                        // تقييم الأنشطة: تقييم متعدد القادة
                        val myEvalMap = if (currentLeaderId.isNotBlank()) {
                            evalList.filter { it.leaderId == currentLeaderId }.associateBy { it.scoutId }
                        } else {
                            evalList.associateBy { it.scoutId }
                        }

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
        val existingEval = _uiState.value.allEvaluations.firstOrNull { it.id == evaluationId }
            ?: _uiState.value.evaluations.values.firstOrNull { it.id == evaluationId }

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
     * حفظ تقييم الشبل:
     * - التقييم الفصلي: معرف المستند "{scoutId}_{targetTerm}"
     * - تقييم الأنشطة: معرف المستند "{scoutId}_{targetActivity}_{leaderId}"
     * - الحفاظ على createdAt الأصلي إذا كان تعديلاً لمنع تجاوز قفل الـ 7 أيام
     */
    fun saveEvaluation(
        scoutId: String,
        scoresMap: Map<String, Int>,
        penaltyAmount: Int,
        penaltyReason: String?,
        testedAmount: String? = null
    ) {
        val currentState = _uiState.value
        val isTabActivity = currentState.selectedTab == 0
        val targetActivityId = if (isTabActivity) {
            currentState.selectedActivity?.id ?: return
        } else {
            currentState.selectedTerm ?: "TERM_1"
        }

        val existingEval = currentState.evaluations[scoutId]
            ?: currentState.allEvaluations.firstOrNull { it.scoutId == scoutId && it.activityId == targetActivityId }

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
            val leaderId = currentUser?.uid ?: ""
            val leaderName = currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "قائد كشفي"

            val evalDocId = if (targetActivityId.startsWith("TERM_")) {
                "${scoutId}_${targetActivityId}"
            } else {
                "${scoutId}_${targetActivityId}_$leaderId"
            }

            // الحفاظ على تاريخ الإنشاء الأصلي لضمان حساب مهلة الـ 7 أيام من تاريخ الرصد الأول
            val createdAt = existingEval?.createdAt ?: System.currentTimeMillis()

            // تصفية المعايير للتأكد من حفظ المعايير الخاصة بالتبويب الحالي فقط
            val relevantCriteriaIds = currentState.criteria.filter { criteria ->
                if (isTabActivity) {
                    criteria.type == EvaluationCriteria.TYPE_LEADER_CRITERIA ||
                            criteria.type == CriteriaType.LEADER_CRITERIA.name ||
                            criteria.type == CriteriaType.LEADER_CRITERIA.value
                } else {
                    criteria.type == EvaluationCriteria.TYPE_QURAN ||
                            criteria.type == CriteriaType.QURAN.name ||
                            criteria.type == CriteriaType.QURAN.value ||
                            criteria.type == EvaluationCriteria.TYPE_NOTEBOOK ||
                            criteria.type == CriteriaType.NOTEBOOK.name ||
                            criteria.type == CriteriaType.NOTEBOOK.value
                }
            }.map { it.id }.toSet()

            val filteredScores = scoresMap.filterKeys { it in relevantCriteriaIds }

            val evaluation = ScoutEvaluation(
                id = evalDocId,
                scoutId = scoutId,
                activityId = targetActivityId,
                leaderId = leaderId,
                leaderName = leaderName,
                criteriaScores = filteredScores,
                penaltyAmount = if (isTabActivity) penaltyAmount else 0,
                penaltyReason = if (isTabActivity) penaltyReason?.takeIf { it.isNotBlank() } else null,
                testedAmount = if (!isTabActivity) testedAmount?.takeIf { it.isNotBlank() } else null,
                createdAt = createdAt
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

                    // 2) تقييم القادة: متوسط جميع تقييمات الأنشطة لهذا الشبل عبر جميع القادة (من 20)
                    val activityEvals = allEvaluations.filter {
                        it.scoutId == scout.id && !it.activityId.startsWith("TERM_")
                    }
                    val leaderPoints: Double? = if (activityEvals.isNotEmpty()) {
                        val totalScores = activityEvals.map { eval ->
                            (eval.criteriaScores.values.sum() - eval.penaltyAmount).coerceAtLeast(0).toDouble()
                        }
                        val totalMaxScores = activityEvals.map { eval ->
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

                    // 3) حفظ القرآن: الدرجة الخام من آخر تقييم فصلي (من 20)
                    val quarterlyEvals = allEvaluations.filter {
                        it.scoutId == scout.id && it.activityId.startsWith("TERM_")
                    }
                    val latestQuarterly = quarterlyEvals.maxByOrNull { it.createdAt }
                    val quranPoints: Int? = latestQuarterly?.criteriaScores?.get(quranCriteria?.id)

                    // 4) تقييم الكراس: الدرجة الخام من آخر تقييم فصلي (من 20)
                    val notebookPoints: Int? = latestQuarterly?.criteriaScores?.get(notebookCriteria?.id)

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
                    _uiState.update { it.copy(snackbarMessage = "لا يوجد أشبال نشطون لتصدير بياناتهم") }
                    return@launch
                }

                val file = PdfExporter.exportRawPointsPdf(scoutPointsList, context)
                PdfExporter.shareRawPointsPdf(file, context)
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "فشل تصدير ملف PDF: ${e.localizedMessage}") }
            }
        }
    }

    class Factory(
        private val activityRepository: ActivityRepository,
        private val scoutRepository: ScoutRepository,
        private val evaluationRepository: EvaluationRepository,
        private val authRepository: AuthRepository,
        private val scoutQuranEnrollmentRepository: ScoutQuranEnrollmentRepository? = null,
        private val attendanceRepository: AttendanceRepository? = null
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
                    attendanceRepository = attendanceRepository
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
