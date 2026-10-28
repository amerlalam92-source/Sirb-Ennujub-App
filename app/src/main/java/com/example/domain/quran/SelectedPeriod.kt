package com.example.domain.quran

/**
 * فترة دوام محددة مع وقت البدء (اختياري، مخصص لفترة الصباح)
 */
data class SelectedPeriod(
    val period: StudyPeriod,
    val startTime: String? = null // e.g. "08:30" (only for MORNING)
)
