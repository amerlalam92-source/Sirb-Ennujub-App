package com.example.domain.finance

/**
 * ملخص ديون واشتراكات الشبل المتراكمة
 */
data class ScoutDebt(
    val scoutId: String,
    val scoutFullName: String,
    val totalRequired: Int,
    val totalPaid: Int,
    val totalDebt: Int,
    val unpaidActivitiesCount: Int
)
