package com.example.domain.finance

import com.example.R

/**
 * أنواع المعاملات المالية في سجل المالية
 */
enum class FinanceTransactionType(
    val key: String,
    val arabicLabel: String,
    val labelRes: Int
) {
    EXPENSE("expense", "مصروف", R.string.finance_type_expense),
    DONATION("donation", "تبرع", R.string.finance_type_donation),
    CONTRIBUTION("contribution", "مساهمة", R.string.finance_type_contribution),
    OPENING_BALANCE("opening_balance", "رصيد افتتاحي", R.string.finance_type_opening_balance),
    DEBT_PAYMENT("debt_payment", "سداد دين", R.string.finance_type_debt_payment),
    ADVANCE_PAYMENT("advance_payment", "دفعة مقدمة", R.string.finance_type_advance_payment);

    val isIncome: Boolean
        get() = this != EXPENSE

    companion object {
        fun fromKey(key: String?): FinanceTransactionType {
            return entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: EXPENSE
        }
    }
}
