package com.example.data.common

import android.util.Log
import com.google.android.gms.tasks.Task
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * يجمع نتائج تأكيد الخادم لعدة مهام Firestore غير متزامنة في رد نداء واحد Result<Unit>.
 */
class CompositeServerResultNotifier(
    expectedCount: Int,
    private val onComplete: (Result<Unit>) -> Unit
) {
    private val remaining = AtomicInteger(expectedCount)
    private val completed = AtomicBoolean(false)

    init {
        if (expectedCount <= 0 && completed.compareAndSet(false, true)) {
            onComplete(Result.success(Unit))
        }
    }

    fun onChildResult(result: Result<Unit>) {
        if (completed.get()) return
        val err = result.exceptionOrNull()
        if (err != null) {
            if (completed.compareAndSet(false, true)) {
                onComplete(Result.failure(err))
            }
            return
        }
        if (remaining.decrementAndGet() <= 0 && completed.compareAndSet(false, true)) {
            onComplete(Result.success(Unit))
        }
    }

    fun childCallback(): (Result<Unit>) -> Unit {
        val invoked = AtomicBoolean(false)
        return { result ->
            if (invoked.compareAndSet(false, true)) {
                onChildResult(result)
            }
        }
    }
}

/**
 * يضمن عدم استدعاء رد نداء النتيجة أكثر من مرة واحدة مهما تعددت مسارات الخروج أو المستمعات.
 */
fun singleShotResultCallback(
    onResult: ((Result<Unit>) -> Unit)?
): ((Result<Unit>) -> Unit)? {
    if (onResult == null) return null
    val invoked = AtomicBoolean(false)
    return { result ->
        if (invoked.compareAndSet(false, true)) {
            onResult(result)
        }
    }
}

fun <T> Task<T>.attachServerResultListeners(
    logTag: String,
    failureMessage: String,
    onAsyncFailure: ((Throwable) -> Unit)? = null,
    onServerResult: ((Result<Unit>) -> Unit)? = null
) {
    if (onAsyncFailure == null && onServerResult == null) return
    // تجنب سلسلة النداءات مباشرة في حال كانت النتيجة فارغة داخل اختبارات Mockito الافتراضية
    addOnSuccessListener {
        onServerResult?.invoke(Result.success(Unit))
    }
    addOnFailureListener { e ->
        Log.e(logTag, "$failureMessage: ${e.message}", e)
        onAsyncFailure?.invoke(e)
        onServerResult?.invoke(Result.failure(e))
    }
}
