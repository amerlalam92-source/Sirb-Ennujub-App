package com.example.presentation.evaluation

import com.example.domain.evaluation.EvaluationCriteria
import com.example.domain.evaluation.ScoutEvaluation
import com.example.domain.quran.ScoutQuranEnrollment
import com.example.domain.scout.Scout

/**
 * حالة تقييم الشبل بالنسبة للقائد الحالي
 */
enum class ScoutEvaluationStatus {
    EVALUATED_BY_ME,     // تم التقييم بواسطة القائد الحالي
    EVALUATED_BY_OTHERS, // مقيّم من قادة آخرين ولم يقيّمه القائد الحالي (يظهر للقائد الرئيسي فقط)
    NOT_EVALUATED        // لم يتم تقييمه من أي قائد
}

/**
 * حالة واجهة شاشة تقييم الأشبال (Evaluation UI State)
 * مخصصة لنظام التقييم الفصلي الموحد (6 معايير: 4 معايير للقائد + حفظ القرآن والكراس).
 */
data class EvaluationUiState(
    val selectedTerm: String = "TERM_1", // المعرف الفريد للفصل الدراسي (TERM_1, TERM_2, TERM_3)
    val scouts: List<Scout> = emptyList(),
    val criteria: List<EvaluationCriteria> = emptyList(),
    val evaluations: Map<String, ScoutEvaluation> = emptyMap(), // Key: scoutId -> تقييم القائد الحالي فقط
    val allEvaluations: List<ScoutEvaluation> = emptyList(), // للقائد الرئيسي: كل التقييمات، للقائد العادي: تقييماته فقط
    val otherEvaluatedScoutIds: Set<String> = emptySet(), // معرفات الأشبال المقيّمين من قادة آخرين (للقائد الرئيسي فقط)
    val isMainLeader: Boolean = false,
    val currentLeaderId: String = "",
    val leaderNames: Map<String, String> = emptyMap(),
    val quranEnrollments: Map<String, ScoutQuranEnrollment> = emptyMap(), // Key: scoutId -> بيانات المدرسة القرآنية
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val searchQuery: String = "",
    val selectedScoutForEvaluation: Scout? = null,
    val pendingDeleteEvaluationId: String? = null, // معرف التقييم المراد حذفه بعد التأكيد
    val snackbarMessage: String? = null
) {
    /**
     * الاسم المستعار لـ criteria لضمان التوافق مع أي مراجع سابقة
     */
    val criteriaList: List<EvaluationCriteria>
        get() = criteria

    /**
     * الأشبال المفلترون حسب استعلام البحث
     */
    val filteredScouts: List<Scout>
        get() {
            if (searchQuery.isBlank()) return scouts
            val query = searchQuery.trim().lowercase()
            return scouts.filter {
                it.fullName.lowercase().contains(query) ||
                        it.school.lowercase().contains(query)
            }
        }

    /**
     * عدد الأشبال النشطين الذين قيّمهم القائد الحالي في الفصل المحدد
     */
    val evaluatedCount: Int
        get() {
            val activeIds = scouts.map { it.id }.toSet()
            return evaluations.keys.count { it in activeIds }
        }

    /**
     * إجمالي عدد الأشبال النشطين
     */
    val totalScoutsCount: Int
        get() = scouts.size

    /**
     * تحديد حالة تقييم الشبل (بواسطتي / بواسطة قادة آخرين للقائد الرئيسي / لم يتم)
     */
    fun getEvaluationStatus(scoutId: String): ScoutEvaluationStatus {
        return when {
            evaluations.containsKey(scoutId) -> ScoutEvaluationStatus.EVALUATED_BY_ME
            isMainLeader && otherEvaluatedScoutIds.contains(scoutId) -> ScoutEvaluationStatus.EVALUATED_BY_OTHERS
            else -> ScoutEvaluationStatus.NOT_EVALUATED
        }
    }

    /**
     * جلب تقييمات القادة الآخرين لشبل معين (تُرجع قائمة فارغة إذا لم يكن المستخدم القائد الرئيسي)
     */
    fun getOtherLeaderEvaluationsForScout(scoutId: String): List<ScoutEvaluation> {
        if (!isMainLeader) return emptyList()
        return allEvaluations.filter { it.scoutId == scoutId && it.leaderId != currentLeaderId }
    }

    /**
     * جلب أحدث درجة مسجلة للمعايير المشتركة (القرآن أو الكراس)
     * القائد الرئيسي يرى أحدث تعديل عبر جميع القادة، بينما القائد العادي يرى تقييمه فقط.
     */
    fun getLatestSharedScore(scoutId: String, criteriaId: String): Int? {
        if (!isMainLeader) {
            return evaluations[scoutId]?.criteriaScores?.get(criteriaId)
        }
        return allEvaluations
            .filter { it.scoutId == scoutId && it.criteriaScores.containsKey(criteriaId) }
            .maxByOrNull { it.createdAt }
            ?.criteriaScores
            ?.get(criteriaId)
    }

    /**
     * جلب أحدث مقدار مطلوب اختباره مسجل للشبل
     */
    fun getLatestTestedAmount(scoutId: String): String? {
        if (!isMainLeader) {
            return evaluations[scoutId]?.testedAmount
        }
        return allEvaluations
            .filter { it.scoutId == scoutId && !it.testedAmount.isNullOrBlank() }
            .maxByOrNull { it.createdAt }
            ?.testedAmount
            ?: evaluations[scoutId]?.testedAmount
    }

    /**
     * فحص ما إذا كان التقييم مقفلاً بسبب مرور أكثر من 7 أيام على تاريخ إنشائه الأول
     */
    fun isEvaluationLocked(evaluation: ScoutEvaluation?): Boolean {
        if (evaluation == null) return false
        val sevenDaysInMillis = 7 * 24 * 60 * 60 * 1000L
        return (System.currentTimeMillis() - evaluation.createdAt) > sevenDaysInMillis
    }

    /**
     * صياغة نص المحفوظ المسجل للشبل
     */
    fun getMemorizedTextForScout(scoutId: String): String {
        val enrollment = quranEnrollments[scoutId] ?: return "غير مسجل في مدرسة قرآنية"
        return when {
            enrollment.currentMemorizedParts > 0 && !enrollment.memorizationNote.isNullOrBlank() ->
                "${enrollment.currentMemorizedParts} أجزاء (${enrollment.memorizationNote})"
            enrollment.currentMemorizedParts > 0 ->
                "${enrollment.currentMemorizedParts} أجزاء"
            !enrollment.memorizationNote.isNullOrBlank() ->
                enrollment.memorizationNote
            else -> "0 أجزاء"
        }
    }
}
