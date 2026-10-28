package com.example.domain.progress

import com.example.R

/**
 * شارات الكفاءة الأربعة المعتمدة لسرب النُّجُب (مرتبة تسلسلياً):
 * القبول -> الشبل المبتدئ -> الشبل الثاني -> الشبل الأول
 */
enum class BadgeLevel(val key: String) {
    ACCEPTANCE("acceptance"),
    BEGINNER("beginner"),
    SECOND("second"),
    FIRST("first");

    fun order(): Int = when (this) {
        ACCEPTANCE -> 1
        BEGINNER -> 2
        SECOND -> 3
        FIRST -> 4
    }

    fun next(): BadgeLevel? = when (this) {
        ACCEPTANCE -> BEGINNER
        BEGINNER -> SECOND
        SECOND -> FIRST
        FIRST -> null
    }

    companion object {
        fun fromKey(key: String): BadgeLevel? = entries.firstOrNull { it.key == key }
    }
}

fun BadgeLevel.labelRes(): Int = when (this) {
    BadgeLevel.ACCEPTANCE -> R.string.badge_acceptance
    BadgeLevel.BEGINNER -> R.string.badge_beginner
    BadgeLevel.SECOND -> R.string.badge_second
    BadgeLevel.FIRST -> R.string.badge_first
}

fun BadgeLevel.shortLabelRes(): Int = when (this) {
    BadgeLevel.ACCEPTANCE -> R.string.progress_badge_acceptance_short
    BadgeLevel.BEGINNER -> R.string.progress_badge_beginner_short
    BadgeLevel.SECOND -> R.string.progress_badge_second_short
    BadgeLevel.FIRST -> R.string.progress_badge_first_short
}
