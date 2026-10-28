package com.example.presentation.activity

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cabin
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocalActivity
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.domain.activity.Activity
import com.example.domain.activity.ActivityType
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date
import java.util.Locale

/**
 * نافذة صلاحية تعديل أو تسجيل حضور النشاط
 */
enum class AttendanceEditWindow { FUTURE, EDITABLE, EXPIRED }

/**
 * أدوات تنسيق التاريخ والوقت والأيقونات للأنشطة الكشفية باللغة العربية
 */
object ActivityFormatters {

    fun attendanceEditWindow(
        activityStartDate: Long,
        now: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): AttendanceEditWindow {
        if (activityStartDate <= 0) return AttendanceEditWindow.EXPIRED
        val activityDate = Instant.ofEpochMilli(activityStartDate).atZone(zoneId).toLocalDate()
        val nowZoned = Instant.ofEpochMilli(now).atZone(zoneId)

        val windowStart = activityDate.atTime(LocalTime.of(4, 0)).atZone(zoneId)
        val windowEnd = activityDate.plusDays(6).atTime(LocalTime.of(23, 59, 59, 999_999_999)).atZone(zoneId)

        return when {
            nowZoned.isBefore(windowStart) -> AttendanceEditWindow.FUTURE
            nowZoned.isAfter(windowEnd) -> AttendanceEditWindow.EXPIRED
            else -> AttendanceEditWindow.EDITABLE
        }
    }

    fun isActivityStrictlyFuture(
        activityStartDate: Long,
        now: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        if (activityStartDate <= 0) return false
        val activityDate = Instant.ofEpochMilli(activityStartDate).atZone(zoneId).toLocalDate()
        val nowDate = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()
        return activityDate.isAfter(nowDate)
    }

    fun isActivityTodayBefore4Am(
        activityStartDate: Long,
        now: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        if (activityStartDate <= 0) return false
        val activityDate = Instant.ofEpochMilli(activityStartDate).atZone(zoneId).toLocalDate()
        val nowZoned = Instant.ofEpochMilli(now).atZone(zoneId)
        val nowDate = nowZoned.toLocalDate()
        if (activityDate != nowDate) return false
        val windowStart = activityDate.atTime(LocalTime.of(4, 0)).atZone(zoneId)
        return nowZoned.isBefore(windowStart)
    }

    private val arabicLocale = com.example.presentation.common.AppLocale.AR_DZ
    private val fullDateFormatter = SimpleDateFormat("EEEE d MMMM yyyy", arabicLocale)
    private val shortDateFormatter = SimpleDateFormat("d MMMM yyyy", arabicLocale)

    /**
     * تنسيق التاريخ أو النطاق الزمني باللغة العربية
     */
    fun formatDateRange(activity: Activity): String {
        return if (activity.isMultiDay()) {
            "من ${shortDateFormatter.format(Date(activity.startDate))} إلى ${fullDateFormatter.format(Date(activity.endDate))}"
        } else {
            fullDateFormatter.format(Date(activity.startDate))
        }
    }

    fun formatDate(timestamp: Long): String {
        return if (timestamp > 0) fullDateFormatter.format(Date(timestamp)) else "—"
    }

    /**
     * تحويل الوقت بصيغة "17:00" إلى "05:00 م" و "09:30" إلى "09:30 ص"
     */
    fun formatTimeArabic(time: String): String {
        if (time.isBlank()) return ""
        val parts = time.split(":")
        if (parts.size != 2) return time
        val hour = parts[0].toIntOrNull() ?: return time
        val minute = parts[1].toIntOrNull() ?: return time

        val period = if (hour < 12) "ص" else "م"
        val displayHour = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return String.format(com.example.presentation.common.AppLocale.AR_DZ, "%02d:%02d %s", displayHour, minute, period)
    }

    /**
     * اختيار الأيقونة المناسبة لكل نوع نشاط كشفي
     */
    fun getIconForType(type: ActivityType): ImageVector {
        return when (type) {
            ActivityType.TRAINING_MEETING -> Icons.Default.Groups
            ActivityType.HONOR_COUNCIL -> Icons.Default.Shield
            ActivityType.PATROL_MEETING -> Icons.Default.People
            ActivityType.SUMMER_CAMP -> Icons.Default.Cabin
            ActivityType.NATURE_TRIP -> Icons.Default.Forest
            ActivityType.TOURIST_TRIP -> Icons.Default.LocalActivity
            ActivityType.TRAINING -> Icons.Default.School
            ActivityType.PUBLIC_SERVICE -> Icons.Default.VolunteerActivism
            ActivityType.CEREMONY -> Icons.Default.Celebration
            ActivityType.OTHER -> Icons.Default.Event
        }
    }
}
