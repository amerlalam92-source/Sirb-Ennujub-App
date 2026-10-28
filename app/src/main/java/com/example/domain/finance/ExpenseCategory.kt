package com.example.domain.finance

import com.example.R

/**
 * تصنيفات المصاريف الكشفية
 */
enum class ExpenseCategory(
    val key: String,
    val arabicLabel: String,
    val labelRes: Int
) {
    TRANSPORT("transport", "نقل ومواصلات", R.string.expense_category_transport),
    FOOD("food", "إعاشة وطعام", R.string.expense_category_food),
    SUPPLIES("supplies", "أدوات ومستلزمات", R.string.expense_category_supplies),
    ACTIVITIES("activities", "أنشطة وترفيه", R.string.expense_category_activities),
    ADMIN("admin", "اتصالات وإدارية", R.string.expense_category_admin),
    CAMP("camp", "تجهيزات المخيم", R.string.expense_category_camp),
    CHARITY("charity", "مساعدات وصدقات", R.string.expense_category_charity),
    OTHER("other", "أخرى", R.string.expense_category_other);

    companion object {
        fun fromKey(key: String?): ExpenseCategory? {
            if (key == null) return null
            return entries.firstOrNull { it.key.equals(key, ignoreCase = true) }
        }
    }
}
