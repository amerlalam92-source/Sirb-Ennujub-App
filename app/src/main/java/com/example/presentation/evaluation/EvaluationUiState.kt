package com.example.presentation.evaluation

import com.example.domain.activity.Activity
import com.example.domain.evaluation.EvaluationCriteria
import com.example.domain.evaluation.ScoutEvaluation
import com.example.domain.quran.ScoutQuranEnrollment
import com.example.domain.scout.Scout

/**
 * حالة تقييم الشبل بالنسبة للقائد الحالي
 */
enum class ScoutEvaluationStatus {
    EVALUATED_BY_ME,     // تم التقييم بواسطة القائد الحالي (أو تقييم فصلي موحد)
    EVALUATED_BY_OTHERS, // مقيّم من قادة آخرين ولم يقيّمه القائد الحالي
    NOT_EVALUATED        // لم يتم تقييمه من أي قائد
}

/**
 * حالة واجهة شاشة تقييم الأشبال (Evaluation UI State)
 */
data class EvaluationUiState(
    val selectedTab: Int = 0, // 0: تقييم الأنشطة، 1: التقييم الفصلي
    val selectedTerm: String? = "TERM_1", // المعرف الفريد للفصل الدراسي (TERM_1, TERM_2, TERM_3)
    val activities: List<Activity> = emptyList(),
    val selectedActivity: Activity? = null,
    val scouts: List<Scout> = emptyList(),
    val criteria: List<EvaluationCriteria> = emptyList(),
    val evaluations: Map<String, ScoutEvaluation> = emptyMap(), // Key: scoutId -> تقييم القائد الحالي (أو التقييم الموحد في التقييم الفصلي)
    val allEvaluations: List<ScoutEvaluation> = emptyList(), // جميع التقييمات من كل القادة للنشاط/الفصل
    val otherEvaluatedScoutIds: Set<String> = emptySet(), // معرفات الأشبال المقيّمين من قادة آخرين
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
     * عدد الأشبال الذين تم تقييمهم
     */
    val evaluatedCount: Int
        get() = evaluations.size

    /**
     * إجمالي عدد الأشبال النشطين
     */
    val totalScoutsCount: Int
        get() = scouts.size

    /**
     * تحديد حالة تقييم الشبل (بواسطتي / بواسطة قادة آخرين / لم يتم)
     */
    fun getEvaluationStatus(scoutId: String): ScoutEvaluationStatus {
        return when {
            evaluations.containsKey(scoutId) -> ScoutEvaluationStatus.EVALUATED_BY_ME
            otherEvaluatedScoutIds.contains(scoutId) -> ScoutEvaluationStatus.EVALUATED_BY_OTHERS
            else -> ScoutEvaluationStatus.NOT_EVALUATED
        }
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
