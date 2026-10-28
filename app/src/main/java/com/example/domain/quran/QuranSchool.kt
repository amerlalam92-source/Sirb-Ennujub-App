package com.example.domain.quran

/**
 * المدرسة القرآنية (دليل مركزي)
 */
data class QuranSchool(
    val id: String = "",
    val name: String = "",
    val teacherName: String = "",
    val teacherPhone: String = "",
    val address: String? = null,
    val notes: String? = null,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val createdBy: String? = null,
    val updatedBy: String? = null
) {
    fun isActive(): Boolean = !archived
}
