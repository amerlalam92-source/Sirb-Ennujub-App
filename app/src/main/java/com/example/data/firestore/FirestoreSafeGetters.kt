package com.example.data.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.util.Date

/**
 * دوال قراءة آمنة لحقول مستندات Firestore تمنع الأعطال الناتجة عن اختلاف نوع البيانات المخزنة
 * (مثل تخزين التاريخ كـ Timestamp أو String بدلاً من Long، أو الأرقام كـ String/Double).
 */
fun DocumentSnapshot.safeNullableLong(field: String): Long? {
    return try {
        when (val raw = get(field)) {
            is Number -> raw.toLong()
            is Timestamp -> raw.toDate().time
            is Date -> raw.time
            is String -> raw.trim().toLongOrNull() ?: raw.trim().toDoubleOrNull()?.toLong()
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}

fun DocumentSnapshot.safeLong(field: String, default: Long = 0L): Long {
    return safeNullableLong(field) ?: default
}

fun DocumentSnapshot.safeNullableInt(field: String): Int? {
    return safeNullableLong(field)?.toInt()
}

fun DocumentSnapshot.safeInt(field: String, default: Int = 0): Int {
    return safeNullableInt(field) ?: default
}

fun DocumentSnapshot.safeNullableString(field: String): String? {
    return try {
        when (val raw = get(field)) {
            null -> null
            is String -> raw
            is Number, is Boolean -> raw.toString()
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}

fun DocumentSnapshot.safeString(field: String, default: String = ""): String {
    return safeNullableString(field) ?: default
}

fun DocumentSnapshot.safeBoolean(field: String, default: Boolean = false): Boolean {
    return try {
        when (val raw = get(field)) {
            is Boolean -> raw
            is Number -> raw.toInt() != 0
            is String -> when (raw.trim().lowercase()) {
                "true", "1", "yes" -> true
                "false", "0", "no" -> false
                else -> default
            }
            else -> default
        }
    } catch (_: Exception) {
        default
    }
}

fun DocumentSnapshot.safeStringList(field: String): List<String> {
    return try {
        val raw = get(field) as? List<*> ?: return emptyList()
        raw.mapNotNull { it?.toString()?.trim() }.filter { it.isNotBlank() }
    } catch (_: Exception) {
        emptyList()
    }
}
