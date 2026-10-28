package com.example.domain.scout

import java.util.Calendar

/**
 * كائن الشبل الكشفي (Scout Domain Model)
 * يمثل الفتى المنتمي إلى سرب النُّجُب — فوج محمد رحال.
 */
data class Scout(
    val id: String = "",
    val name: String = "", // الاسم
    val surname: String = "", // اللقب
    val birthDate: Long = 0L, // تاريخ الميلاد (timestamp)
    val guardianName: String = "", // اسم الولي
    val guardianPhone: String = "", // هاتف الولي (9-10 أرقام)
    val additionalPhones: List<String> = emptyList(), // أرقام إضافية للولي
    val guardianRelation: String = RELATION_FATHER, // صلة القرابة (أب، جد، عم، خال، أخ، أخرى)
    val address: String = "", // العنوان
    val school: String = "", // المدرسة
    val joinYear: Int = Calendar.getInstance().get(Calendar.YEAR), // سنة الالتحاق بالكشافة
    val status: String = STATUS_ACTIVE, // "active" أو "archived"
    val notes: String = "", // ملاحظات
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val createdBy: String = "",
    val updatedBy: String = ""
) {
    /**
     * قائمة كافة أرقام هواتف الولي (الأساسي + الإضافية) دون تكرار أو فراغات
     */
    fun allPhones(): List<String> =
        buildList {
            if (guardianPhone.isNotBlank()) add(guardianPhone.trim())
            addAll(additionalPhones.map { it.trim() }.filter { it.isNotBlank() })
        }.distinct()

    /**
     * الاسم الكامل المدمج (الاسم + اللقب)
     */
    val fullName: String
        get() = "$name $surname".trim()

    /**
     * العمر المحسوب ديناميكياً بالسنوات بناءً على تاريخ الميلاد وتاريخ اليوم
     */
    val age: Int
        get() {
            if (birthDate <= 0L) return 0
            val birthCal = Calendar.getInstance().apply { timeInMillis = birthDate }
            val nowCal = Calendar.getInstance()
            var calculatedAge = nowCal.get(Calendar.YEAR) - birthCal.get(Calendar.YEAR)
            if (nowCal.get(Calendar.DAY_OF_YEAR) < birthCal.get(Calendar.DAY_OF_YEAR)) {
                calculatedAge--
            }
            return calculatedAge.coerceAtLeast(0)
        }

    val isActive: Boolean
        get() = status == STATUS_ACTIVE

    companion object {
        const val STATUS_ACTIVE = "active"
        const val STATUS_ARCHIVED = "archived"

        const val RELATION_FATHER = "أب"
        const val RELATION_GRANDFATHER = "جد"
        const val RELATION_UNCLE_PATERNAL = "عم"
        const val RELATION_UNCLE_MATERNAL = "خال"
        const val RELATION_BROTHER = "أخ"
        const val RELATION_OTHER = "أخرى"

        val GUARDIAN_RELATIONS = listOf(
            RELATION_FATHER,
            RELATION_GRANDFATHER,
            RELATION_UNCLE_PATERNAL,
            RELATION_UNCLE_MATERNAL,
            RELATION_BROTHER,
            RELATION_OTHER
        )
    }
}
