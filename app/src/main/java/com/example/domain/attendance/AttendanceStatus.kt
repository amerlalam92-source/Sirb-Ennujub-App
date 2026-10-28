package com.example.domain.attendance

/**
 * حالات حضور الشبل في الأنشطة الكشفية
 */
enum class AttendanceStatus(val key: String, val arabicLabel: String) {
    PRESENT("present", "حاضر"),
    LATE("late", "متأخر"),
    VERY_LATE("very_late", "متأخر جداً"),
    ABSENT_EXCUSED("absent_excused", "غائب بعذر"),
    ABSENT_UNEXCUSED("absent_unexcused", "غائب بدون عذر");

    val isPresentOrLate: Boolean
        get() = this == PRESENT || this == LATE || this == VERY_LATE

    val isAbsent: Boolean
        get() = this == ABSENT_EXCUSED || this == ABSENT_UNEXCUSED

    companion object {
        fun fromKey(key: String): AttendanceStatus {
            return entries.find { it.key.equals(key, ignoreCase = true) } ?: PRESENT
        }

        fun getAllKeys(): List<String> = entries.map { it.key }
    }
}
