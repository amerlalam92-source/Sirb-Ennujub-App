package com.example.data.common

import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class DataLoadErrorKind {
    NO_PERMISSION,
    QUOTA,
    MISSING_INDEX,
    SIGNED_OUT,
    OTHER
}

data class DataLoadError(
    val kind: DataLoadErrorKind,
    val message: String
)

/**
 * دالة نقية لتحويل اسم رمز خطأ Firestore إلى تصنيف خطأ واضح للواجهة.
 */
fun mapFirestoreErrorCodeNameToKind(codeName: String?): DataLoadErrorKind {
    return when (codeName) {
        "PERMISSION_DENIED" -> DataLoadErrorKind.NO_PERMISSION
        "RESOURCE_EXHAUSTED" -> DataLoadErrorKind.QUOTA
        "FAILED_PRECONDITION" -> DataLoadErrorKind.MISSING_INDEX
        "UNAUTHENTICATED" -> DataLoadErrorKind.SIGNED_OUT
        else -> DataLoadErrorKind.OTHER
    }
}

/**
 * دالة نقية لتحويل رمز خطأ Firestore إلى تصنيف خطأ واضح للواجهة.
 */
fun mapFirestoreErrorCodeToKind(code: FirebaseFirestoreException.Code?): DataLoadErrorKind =
    mapFirestoreErrorCodeNameToKind(code?.name)

fun mapThrowableToDataLoadError(error: Throwable): DataLoadError {
    val code = (error as? FirebaseFirestoreException)?.code
    return DataLoadError(
        kind = mapFirestoreErrorCodeToKind(code),
        message = error.localizedMessage ?: error.message ?: ""
    )
}

/**
 * مخزن مركزي لأخطاء مستمعي Firestore (Listeners) لعرض تنبيه غير معطل على مستوى التطبيق.
 */
class DataLoadErrorReporter {
    private val _errors = MutableStateFlow<Map<String, DataLoadError>>(emptyMap())
    val errors: StateFlow<Map<String, DataLoadError>> = _errors.asStateFlow()

    fun report(source: String, error: Throwable) {
        val mapped = mapThrowableToDataLoadError(error)
        _errors.update { current -> current + (source to mapped) }
    }

    fun clear(source: String) {
        _errors.update { current ->
            if (current.containsKey(source)) current - source else current
        }
    }
}

/**
 * مساعد محلي لكل callbackFlow يحتفظ بآخر قائمة ناجحة (lastGood) عند حدوث خطأ،
 * ويزيل الخطأ عند وصول لقطة ناجحة جديدة.
 */
class ListenerFallbackState<T>(
    private val source: String,
    private val reporter: DataLoadErrorReporter? = null
) {
    private var lastGood: List<T>? = null

    fun onSnapshot(items: List<T>): List<T> {
        lastGood = items
        reporter?.clear(source)
        return items
    }

    fun onError(error: Throwable): List<T> {
        reporter?.report(source, error)
        return lastGood ?: emptyList()
    }

    fun clear() {
        reporter?.clear(source)
    }
}
