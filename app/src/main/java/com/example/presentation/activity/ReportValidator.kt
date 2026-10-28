package com.example.presentation.activity

/**
 * أداة التحقق من صحة التقرير الأدبي الكشفي (Report Validator)
 * تشترط ألا يقل التقرير الأدبي عن 80 كلمة (حوالي 500 حرف) لضمان القيمة التربوية والتوثيقية.
 */
object ReportValidator {
    const val MIN_REPORT_WORDS = 80

    /**
     * حساب عدد الكلمات بدقة للنصوص العربية
     * تقسيم النص عبر الفراغات وفواصل الأسطر مع استبعاد المقاطع الفارغة
     */
    fun countWords(text: String): Int {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return 0
        return trimmed.split("\\s+".toRegex()).count { it.isNotBlank() }
    }

    /**
     * هل التقرير الأدبي مستوفٍ للحد الأدنى الإلزامي (80 كلمة على الأقل)؟
     */
    fun isReportValid(text: String): Boolean {
        return countWords(text) >= MIN_REPORT_WORDS
    }
}
