package com.example.domain.quran

/**
 * تسجيل الشبل في المدرسة القرآنية ومتابعة الحفظ
 */
data class ScoutQuranEnrollment(
    val scoutId: String = "",
    val schoolId: String = "",
    val schoolName: String = "",
    val teacherName: String = "",
    val teacherPhone: String = "",
    val currentMemorizedParts: Int = 0,
    val memorizationNote: String? = null,
    val studyDays: List<String> = emptyList(),
    val studyPeriods: List<SelectedPeriod> = emptyList(),
    val generalNotes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val createdBy: String? = null,
    val updatedBy: String? = null
)
