package com.example.domain.attendance

/**
 * إعدادات سلم التنقيط المخصص للاجتماع التدريبي (Attendance Points Settings)
 * تعتمد نظام "النقطة الأساسية + المكافآت والخصومات" (Base 10 Rule)
 *
 * الافتراضيات الجديدة:
 * - النقطة الأساسية: 10
 * - مكافأة الحضور المبكر: +2 (المجموع: 12)
 * - خصم التأخر: -3 (المجموع: 7)
 * - خصم التأخر جداً: -8 (المجموع: 2)
 * - خصم الغياب بعذر: -6 (المجموع: 4)
 * - خصم الغياب بدون عذر: -10 (المجموع: 0)
 */
data class AttendancePointsSettings(
    val basePoints: Int = 10,
    val earlyBonus: Int = 2,
    val lateDeduction: Int = 3,
    val veryLateDeduction: Int = 8,
    val excusedDeduction: Int = 6,
    val unexcusedDeduction: Int = 10,
    val kafaaBadgePoints: Int = 10,
    val extraHobbyBadgePoints: Int = 5,
    val updatedAt: Long = System.currentTimeMillis(),
    val updatedBy: String = ""
) {
    // خصائص حسابية متوافقة مع الكود السابق والتقارير
    val presentPoints: Int get() = basePoints + earlyBonus
    val latePoints: Int get() = basePoints - lateDeduction
    val veryLatePoints: Int get() = basePoints - veryLateDeduction
    val absentExcusedPoints: Int get() = basePoints - excusedDeduction
    val absentUnexcusedPoints: Int get() = basePoints - unexcusedDeduction

    /**
     * استخراج النقاط المستحقة حسب الحالة الكشفية
     */
    fun getPointsForStatus(status: AttendanceStatus): Int {
        return when (status) {
            AttendanceStatus.PRESENT -> basePoints + earlyBonus
            AttendanceStatus.LATE -> basePoints - lateDeduction
            AttendanceStatus.VERY_LATE -> basePoints - veryLateDeduction
            AttendanceStatus.ABSENT_EXCUSED -> basePoints - excusedDeduction
            AttendanceStatus.ABSENT_UNEXCUSED -> basePoints - unexcusedDeduction
        }
    }
}
