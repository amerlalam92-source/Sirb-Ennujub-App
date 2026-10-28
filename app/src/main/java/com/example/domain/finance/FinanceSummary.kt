package com.example.domain.finance

/**
 * الملخص المالي الإجمالي المحسوب للخزينة
 */
data class FinanceSummary(
    val totalIncome: Int = 0,
    val totalExpenses: Int = 0,
    val balance: Int = 0,
    val totalOutstandingDebt: Int = 0,
    val monthlyIncome: Int = 0,     // للشهر الحالي
    val monthlyExpenses: Int = 0    // للشهر الحالي
)
