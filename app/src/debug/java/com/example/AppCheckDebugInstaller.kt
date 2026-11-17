package com.example

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * مثبت مزود App Check لنسخ التطوير (Debug Builds)
 * يطبع الرمز التجريبي تلقائياً في Logcat دون تضمين أي رموز ثابتة في الكود.
 */
internal object AppCheckDebugInstaller {
    fun install() {
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )
    }
}
