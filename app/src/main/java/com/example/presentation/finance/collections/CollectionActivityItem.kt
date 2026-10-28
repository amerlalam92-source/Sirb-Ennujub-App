package com.example.presentation.finance.collections

/**
 * كائن نشاط في قائمة التحصيلات المالية الموحدة (Collection Activity Item)
 * يمثل نشاطاً يحوي اشتراكات أو مساهمات مع مؤشرات التحصيل الإجمالية.
 */
data class CollectionActivityItem(
    val activityId: String,
    val activityTitle: String,
    val activityDate: Long,
    val type: String, // "subscriptions" | "contributions"
    val participantsCount: Int,
    val totalRequired: Int,
    val totalPaid: Int,
    val totalDebt: Int,
    val isFullyCollected: Boolean = (totalDebt == 0)
)
