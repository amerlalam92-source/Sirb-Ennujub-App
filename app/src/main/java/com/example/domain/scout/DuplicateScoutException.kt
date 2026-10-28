package com.example.domain.scout

/**
 * استثناء يُرمى عند محاولة إضافة أو تعديل شبل مكرر (الاسم واللقب واسم الولي).
 */
class DuplicateScoutException(
    val existingFullName: String
) : Exception("Duplicate scout found: $existingFullName")
