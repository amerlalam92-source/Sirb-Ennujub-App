package com.example.domain.update

/**
 * بيانات تحديث التطبيق عبر الهواء (OTA Update Info)
 * مسترجعة من مسار Firestore: settings/app_update
 */
data class AppUpdateInfo(
    val latestVersionCode: Int = 0,
    val latestVersionName: String = "",
    val apkUrl: String = "",
    val changelog: String = ""
)
