package com.example.domain.finance

/**
 * حالات حضور الشبل المحددة لقيمة الاشتراك المالي
 */
enum class SubscriptionStatus(val key: String, val arabicLabel: String) {
    PRESENT("present", "حاضر"),
    LATE("late", "متأخر"),
    VERY_LATE("very_late", "متأخر جداً"),
    ABSENT_EXCUSED("absent_excused", "غائب بعذر"),
    ABSENT_UNEXCUSED("absent_unexcused", "غائب بدون عذر");

    companion object {
        fun fromKey(key: String?): SubscriptionStatus {
            return entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: PRESENT
        }
    }
}
