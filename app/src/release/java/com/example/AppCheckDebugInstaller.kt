package com.example

/**
 * نسخة الإصدار (Release) لا تحتوي على مكتبة debug App Check
 * وتعتمد على PlayIntegrityAppCheckProviderFactory في ScoutApplication.
 */
internal object AppCheckDebugInstaller {
    fun install() {
        // No-op in release builds
    }
}
