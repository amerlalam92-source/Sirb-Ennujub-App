package com.example.domain.evaluation

/**
 * أنواع معايير التقييم
 */
enum class CriteriaType(val value: String) {
    LEADER_CRITERIA(EvaluationCriteria.TYPE_LEADER_CRITERIA),
    QURAN(EvaluationCriteria.TYPE_QURAN),
    NOTEBOOK(EvaluationCriteria.TYPE_NOTEBOOK);

    companion object {
        fun fromValue(value: String): CriteriaType =
            values().firstOrNull { it.value == value } ?: LEADER_CRITERIA
    }
}

/**
 * معيار التقييم الديناميكي (Evaluation Criteria Domain Model)
 * يمثل معياراً لتقييم الأشبال (مثل: الانضباط، الأخلاق، الحفظ، الكراس...)
 */
data class EvaluationCriteria(
    val id: String = "",
    val name: String = "",          // e.g., "الانضباط", "الحفظ", "الكراس"
    val maxScore: Int = 10,         // e.g., 10 or 20
    val type: String = TYPE_LEADER_CRITERIA, // "LEADER_CRITERIA", "QURAN", "NOTEBOOK"
    val order: Int = 0,
    val archived: Boolean = false
) {
    companion object {
        const val TYPE_LEADER_CRITERIA = "LEADER_CRITERIA"
        const val TYPE_QURAN = "QURAN"
        const val TYPE_NOTEBOOK = "NOTEBOOK"
    }
}

