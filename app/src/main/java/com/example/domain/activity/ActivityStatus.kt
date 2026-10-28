package com.example.domain.activity

/**
 * حالات النشاط الكشفي (Activity Status)
 */
enum class ActivityStatus(val key: String, val arabicLabel: String) {
    PLANNED("planned", "مخطط"),
    DONE("done", "منجز"),
    CANCELLED("cancelled", "ملغى");

    companion object {
        fun fromKey(key: String): ActivityStatus {
            return entries.find { it.key.equals(key, ignoreCase = true) } ?: PLANNED
        }

        fun getAllKeys(): List<String> = entries.map { it.key }
    }
}
