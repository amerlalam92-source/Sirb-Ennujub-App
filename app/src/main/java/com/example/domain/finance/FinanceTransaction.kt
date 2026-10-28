package com.example.domain.finance

/**
 * كائن المعاملة المالية (Finance Transaction Domain Model)
 * يمثل حركة مالية في السجل المالي (مصروف، تبرع، مساهمة، رصيد افتتاحي).
 */
data class FinanceTransaction(
    val id: String = "",
    val type: FinanceTransactionType = FinanceTransactionType.EXPENSE,
    val amount: Int = 0, // بالدينار الجزائري الصحيح (DZD whole dinars)
    val date: Long = System.currentTimeMillis(),
    val description: String = "",
    val category: ExpenseCategory? = null, // فقط عندما يكون النوع = EXPENSE
    val source: String? = null, // اسم المتبرع أو جهة المساهمة
    val createdAt: Long = System.currentTimeMillis(),
    val createdBy: String = "",
    val createdByName: String = ""
) {
    fun isIncome(): Boolean = type != FinanceTransactionType.EXPENSE
}
