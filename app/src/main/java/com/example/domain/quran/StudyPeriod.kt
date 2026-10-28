package com.example.domain.quran

import androidx.annotation.StringRes
import com.example.R

/**
 * فترات الدوام القرآني
 */
enum class StudyPeriod(
    val key: String,
    @StringRes val labelRes: Int
) {
    FAJR("fajr", R.string.period_fajr),                     // "بعد الفجر"
    MORNING("morning", R.string.period_morning),             // "صباحًا"
    DHUHR("dhuhr", R.string.period_dhuhr),                   // "بعد الظهر"
    ASR("asr", R.string.period_asr),                         // "بعد العصر"
    MAGHRIB_ISHA("maghrib_isha", R.string.period_maghrib_isha), // "بين المغرب والعشاء"
    ISHA("isha", R.string.period_isha);                      // "بعد العشاء"

    companion object {
        fun fromKey(key: String): StudyPeriod? = entries.firstOrNull { it.key == key }
    }
}
