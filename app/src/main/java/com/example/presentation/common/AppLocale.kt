package com.example.presentation.common

import java.util.Locale

/**
 * إعدادات اللغة الإقليمية الموحدة للتطبيق
 * استخدام Locale("ar", "DZ") يضمن كتابة أسماء الشهور والأيام باللغة العربية
 * مع استخدام الأرقام اللاتينية (0123456789) المعتمدة في الجزائر بدلاً من الأرقام الهندية (٠١٢٣٤٥٦٧٨٩).
 */
object AppLocale {
    val AR_DZ: Locale = Locale("ar", "DZ")
}
