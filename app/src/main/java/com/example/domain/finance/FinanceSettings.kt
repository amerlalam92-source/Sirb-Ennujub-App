package com.example.domain.finance

/**
 * إعدادات سلم الاشتراكات المالية والرصيد الافتتاحي
 */
data class FinanceSettings(
    val presentAmount: Int = 20,
    val lateAmount: Int = 30,
    val veryLateAmount: Int = 40,
    val absentExcusedAmount: Int = 20,
    val absentUnexcusedAmount: Int = 50,
    val openingBalance: Int = 0,
    val currency: String = "دج",
    val updatedAt: Long = System.currentTimeMillis(),
    val updatedBy: String = ""
) {
    /**
     * المبلغ المطلوب للاشتراك بحسب حالة الحضور
     */
    fun requiredAmountFor(status: SubscriptionStatus): Int = when (status) {
        SubscriptionStatus.PRESENT -> presentAmount
        SubscriptionStatus.LATE -> lateAmount
        SubscriptionStatus.VERY_LATE -> veryLateAmount
        SubscriptionStatus.ABSENT_EXCUSED -> absentExcusedAmount
        SubscriptionStatus.ABSENT_UNEXCUSED -> absentUnexcusedAmount
    }
}
