package com.example.presentation.evaluation.matrix

/**
 * يمثل سطراً في مصفوفة التقييم الشاملة لشبل واحد مع الترتيب ونقاط الشارات
 */
data class ScoutMatrixRow(
    val rank: Int = 0,
    val scoutId: String = "",
    val scoutName: String = "",
    val attendanceScore: Double? = null,
    val leaderScore: Double? = null,
    val quranScore: Double? = null,
    val notebookScore: Double? = null,
    val overallScore: Double? = null,
    val kafaaPoints: Int? = null,
    val hobbyPoints: Int? = null,
    val totalScore: Double? = null
) {
    val overallAverage: Double? get() = overallScore
    val progressScore: Int? get() = if (kafaaPoints != null || hobbyPoints != null) (kafaaPoints ?: 0) + (hobbyPoints ?: 0) else null
}

/**
 * حالة واجهة مستخدم مصفوفة التقييم الشاملة
 */
data class EvaluationMatrixUiState(
    val isLoading: Boolean = true,
    val rows: List<ScoutMatrixRow> = emptyList(),
    val filteredRows: List<ScoutMatrixRow> = emptyList(),
    val searchQuery: String = "",
    val totalMeetingsHeld: Int = 0,
    val totalScoutsCount: Int = 0,
    val errorMessage: String? = null
)
