package com.example.data.common

import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class WriteFailureKind {
    NO_PERMISSION,
    NOT_FOUND_OR_CONFLICT,
    QUOTA,
    OTHER
}

data class WriteFailure(
    val id: String,
    val description: String,
    val kind: WriteFailureKind,
    val timestamp: Long
)

/**
 * تحديد ما إذا كان خطأ الكتابة مؤقتاً وقابلاً للتعافي تلقائياً عبر الكاش المحلي في Firestore.
 */
fun isRecoverableWriteError(error: Throwable?): Boolean {
    val code = (error as? FirebaseFirestoreException)?.code ?: return false
    return code == FirebaseFirestoreException.Code.UNAVAILABLE ||
        code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED
}

fun mapFirestoreErrorCodeToWriteKind(code: FirebaseFirestoreException.Code?): WriteFailureKind {
    return when (code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED,
        FirebaseFirestoreException.Code.UNAUTHENTICATED -> WriteFailureKind.NO_PERMISSION

        FirebaseFirestoreException.Code.NOT_FOUND,
        FirebaseFirestoreException.Code.ABORTED,
        FirebaseFirestoreException.Code.FAILED_PRECONDITION,
        FirebaseFirestoreException.Code.ALREADY_EXISTS -> WriteFailureKind.NOT_FOUND_OR_CONFLICT

        FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED -> WriteFailureKind.QUOTA

        else -> WriteFailureKind.OTHER
    }
}

/**
 * مخزن مركزي على مستوى التطبيق لتتبع إخفاقات الكتابة غير المتزامنة في Firestore بعد مغادرة الشاشة أو انتهاء المهلة.
 * آمن للاستدعاء من أي خيط ولا يعتمد على دورة حياة ViewModel أو الكوروتينات.
 */
class WriteFailureReporter(
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {
    companion object {
        const val MAX_FAILURES = 20
    }

    private val sequence = AtomicLong(0L)
    private val _failures = MutableStateFlow<List<WriteFailure>>(emptyList())
    val failures: StateFlow<List<WriteFailure>> = _failures.asStateFlow()

    fun report(description: String, error: Throwable) {
        if (isRecoverableWriteError(error)) return
        val normalizedDesc = description.trim().ifBlank { "عملية حفظ غير معروفة" }
        val kind = mapFirestoreErrorCodeToWriteKind((error as? FirebaseFirestoreException)?.code)
        val now = timeProvider()
        _failures.update { current ->
            if (current.any { it.description == normalizedDesc && it.kind == kind }) {
                current
            } else {
                val entry = WriteFailure(
                    id = "wf_${sequence.incrementAndGet()}",
                    description = normalizedDesc,
                    kind = kind,
                    timestamp = now
                )
                val updated = current + entry
                if (updated.size > MAX_FAILURES) updated.takeLast(MAX_FAILURES) else updated
            }
        }
    }

    fun dismiss(id: String) {
        _failures.update { current ->
            if (current.any { it.id == id }) current.filterNot { it.id == id } else current
        }
    }

    fun clear() {
        _failures.update { emptyList() }
    }
}
