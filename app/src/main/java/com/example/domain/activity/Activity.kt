package com.example.domain.activity

import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * كائن النشاط الكشفي (Activity Domain Model)
 * يمثل نشاطاً مبرمجاً أو منجزاً ضمن أنشطة سرب النُّجُب.
 */
data class Activity(
    val id: String = "",
    val title: String = "",
    val type: String = ActivityType.TRAINING_MEETING.key,
    val startDate: Long = System.currentTimeMillis(),
    val endDate: Long = System.currentTimeMillis(),
    val startTime: String = "",
    val endTime: String = "",
    val location: String = "",
    val objectives: String = "",
    val tools: String = "",
    val responsibleName: String = "",
    val status: String = ActivityStatus.PLANNED.key,
    val notes: String = "",
    val meetingTopics: List<String> = emptyList(), // قائمة مواضيع الاجتماع (خاصة بأنشطة الاجتماعات)
    val finalReport: String = "", // التقرير الأدبي النهائي (يُسجل عند إنجاز النشاط)
    val subscriptionOverride: Map<String, Int>? = null, // مبالغ الاشتراكات المخصصة لهذا النشاط (حاضر، متأخر، بعذر، بدون عذر)
    val contributionRequired: Boolean = false, // هل يتطلب هذا النشاط مساهمة مالية من المشاركين
    val contributionDefaultAmount: Int? = null, // المبلغ الافتراضي لمساهمة الشبل في هذا النشاط
    val collectSubscriptions: Boolean = true, // تفعيل تحصيل الاشتراكات المالية لهذا النشاط
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val createdBy: String = "",
    val updatedBy: String = ""
) {
    val activityType: ActivityType
        get() = ActivityType.fromKey(type)

    val activityStatus: ActivityStatus
        get() = ActivityStatus.fromKey(status)

    /**
     * هل النشاط قادم أو جاري اليوم؟ (تاريخ نهايته >= بداية اليوم الحالي)
     */
    fun isUpcoming(): Boolean {
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return endDate >= todayStart
    }

    /**
     * هل النشاط سابق؟ (تاريخ نهايته < بداية اليوم الحالي)
     */
    fun isPast(): Boolean {
        return !isUpcoming()
    }

    /**
     * هل يمتد النشاط لعدة أيام؟
     */
    fun isMultiDay(): Boolean {
        val calStart = Calendar.getInstance().apply { timeInMillis = startDate }
        val calEnd = Calendar.getInstance().apply { timeInMillis = endDate }
        return calStart.get(Calendar.YEAR) != calEnd.get(Calendar.YEAR) ||
                calStart.get(Calendar.DAY_OF_YEAR) != calEnd.get(Calendar.DAY_OF_YEAR)
    }

    /**
     * مدة النشاط بالأيام (الحد الأدنى 1 يوم)
     */
    fun durationDays(): Int {
        val diffMillis = (endDate - startDate).coerceAtLeast(0)
        val days = TimeUnit.MILLISECONDS.toDays(diffMillis).toInt() + 1
        return days.coerceAtLeast(1)
    }
}
