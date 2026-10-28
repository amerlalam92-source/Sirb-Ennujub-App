package com.example.domain.finance

/**
 * كائن مساهمة الشبل في النشاط الكشفي الكبير (Finance Contribution Domain Model)
 * يمثل التزاماً مالياً ومساهمة فردية للشبل (أو ولي أمره) للمشاركة في نشاط كشفي
 * غير روتيني (مخيم، خرجة، رحلة، دورة تدريبية، خدمة عامة، حفل...).
 * Document ID في فايرستور: "{activityId}_{scoutId}"
 */
data class FinanceContribution(
    val id: String = "", // composite ID: "${activityId}_${scoutId}"
    val scoutId: String = "",
    val scoutFullName: String = "",
    val activityId: String = "",
    val activityTitle: String = "",
    val activityDate: Long = 0L,
    val requiredAmount: Int = 0,
    val paidAmount: Int = 0,
    val debtAmount: Int = (requiredAmount - paidAmount).coerceAtLeast(0),
    val notes: String? = null,
    val recordedAt: Long = System.currentTimeMillis(),
    val recordedBy: String = "",
    val recordedByName: String = "",
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * هل تم تسديد كامل مبلغ المساهمة؟
     */
    fun isFullyPaid(): Boolean = debtAmount == 0
}
