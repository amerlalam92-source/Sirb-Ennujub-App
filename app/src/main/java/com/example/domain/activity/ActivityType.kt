package com.example.domain.activity

import com.example.domain.settings.CustomTerms
import com.example.domain.settings.replaceCustomWords

/**
 * أنواع الأنشطة الكشفية في سرب النُّجُب (Activity Types)
 */
enum class ActivityType(val key: String, val arabicLabel: String) {
    TRAINING_MEETING("training_meeting", "اجتماع تدريبي"),
    HONOR_COUNCIL("honor_council", "اجتماع مجلس الشرف"),
    PATROL_MEETING("patrol_meeting", "اجتماع السداسية"),
    SUMMER_CAMP("summer_camp", "مخيم صيفي"),
    NATURE_TRIP("nature_trip", "خرجة خلوية"),
    TOURIST_TRIP("tourist_trip", "رحلة سياحية"),
    TRAINING("training", "تدريب"),
    PUBLIC_SERVICE("public_service", "خدمة عامة"),
    CEREMONY("ceremony", "حفل"),
    OTHER("other", "أخرى");

    /**
     * التسمية الديناميكية لنوع النشاط وفق المصطلحات المخصصة
     */
    fun displayLabel(terms: CustomTerms): String {
        val base = if (this == PATROL_MEETING) {
            "اجتماع ${terms.patrolName.ifBlank { "السداسية" }}"
        } else {
            arabicLabel
        }
        return replaceCustomWords(base, terms)
    }

    /**
     * هل النشاط يمثل اجتماعاً كشفياً يستوجب جدول مواضيع؟
     */
    fun isMeetingType(): Boolean {
        return this == TRAINING_MEETING || this == HONOR_COUNCIL || this == PATROL_MEETING
    }

    /**
     * هل يخضع النشاط لسلم التنقيط (متاح فقط للاجتماع التدريبي)؟
     */
    fun usesPointsSystem(): Boolean {
        return this == TRAINING_MEETING
    }

    companion object {
        fun fromKey(key: String): ActivityType {
            return entries.find { it.key.equals(key, ignoreCase = true) } ?: OTHER
        }

        fun getAllKeys(): List<String> = entries.map { it.key }
    }
}
