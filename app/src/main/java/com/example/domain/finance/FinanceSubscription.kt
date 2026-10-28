package com.example.domain.finance

/**
 * كائن اشتراك الشبل في النشاط (Finance Subscription Domain Model)
 * يربط بين الشبل والنشاط التدريبي وحالة حضوره والمبلغ المطلوب والمدفوع والديون المتبقية.
 */
data class FinanceSubscription(
    val id: String = "", // composite ID: "{activityId}_{scoutId}"
    val scoutId: String = "",
    val scoutFullName: String = "",
    val activityId: String = "",
    val activityTitle: String = "",
    val activityDate: Long = 0L,
    val attendanceStatus: SubscriptionStatus = SubscriptionStatus.PRESENT,
    val requiredAmount: Int = 0,
    val paidAmount: Int = 0,
    val debtAmount: Int = (requiredAmount - paidAmount).coerceAtLeast(0),
    val notes: String? = null,
    val recordedAt: Long = System.currentTimeMillis(),
    val recordedBy: String = "",
    val recordedByName: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)
