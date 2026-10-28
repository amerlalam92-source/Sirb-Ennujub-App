package com.example.domain.evaluation

/**
 * تقييم الشبل في نشاط كشفي محدد أو فصل دراسي (Scout Evaluation Domain Model)
 */
data class ScoutEvaluation(
    val id: String = "",            // "{scoutId}_{activityId}_{leaderId}"
    val scoutId: String = "",
    val activityId: String = "",
    val leaderId: String = "",
    val leaderName: String = "",
    val criteriaScores: Map<String, Int> = emptyMap(), // criteriaId -> score
    val penaltyAmount: Int = 0,
    val penaltyReason: String? = null,
    val testedAmount: String? = null, // المقدار المطلوب اختباره في التقييم الفصلي
    val createdAt: Long = System.currentTimeMillis()
)
