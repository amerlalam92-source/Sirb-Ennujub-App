package com.example.presentation.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * مدير تحميل وتثبيت تحديثات التطبيق عبر الهواء (OTA App Updater)
 */
object AppUpdater {

    private const val TAG = "AppUpdater"
    private const val APK_FILE_NAME = "app-update.apk"
    private val ALLOWED_HOSTS = setOf(
        "github.com",
        "objects.githubusercontent.com",
        "github-releases.githubusercontent.com"
    )
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeDownloadId: Long = -1L
    private var downloadReceiver: BroadcastReceiver? = null

    /**
     * التحقق من أن رابط التحميل يستخدم HTTPS ومن نطاق GitHub موثوق فقط
     */
    private fun isTrustedDownloadUrl(apkUrl: String): Boolean {
        val trimmed = apkUrl.trim()
        if (!trimmed.startsWith("https://", ignoreCase = true)) return false
        val uri = try {
            Uri.parse(trimmed)
        } catch (_: Exception) {
            return false
        }
        val scheme = uri.scheme?.lowercase(Locale.US)
        val host = uri.host?.lowercase(Locale.US)
        return scheme == "https" && !host.isNullOrBlank() && host in ALLOWED_HOSTS
    }

    /**
     * بدء تحميل وتثبيت ملف الـ APK
     */
    fun startDownloadAndInstall(
        context: Context,
        apkUrl: String,
        expectedSha256: String = "",
        onDownloadStarted: () -> Unit = {},
        onDownloadSuccess: () -> Unit = {},
        onDownloadFailed: (String) -> Unit = {}
    ) {
        val trimmedUrl = apkUrl.trim()
        if (trimmedUrl.isBlank()) {
            val msg = context.getString(R.string.update_download_failed)
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            onDownloadFailed(msg)
            return
        }

        if (!isTrustedDownloadUrl(trimmedUrl)) {
            Log.e(TAG, "Blocked untrusted OTA update URL: $trimmedUrl")
            val msg = context.getString(R.string.update_invalid_url)
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            onDownloadFailed(msg)
            return
        }

        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            if (downloadManager == null) {
                onDownloadFailed("DownloadManager not available")
                return
            }

            // التأكد من حذف أي نسخة قديمة تم تحميلها سابقاً
            val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val apkFile = File(downloadsDir, APK_FILE_NAME)
            if (apkFile.exists()) {
                apkFile.delete()
            }

            val uri = Uri.parse(trimmedUrl)
            val request = DownloadManager.Request(uri).apply {
                setTitle(context.getString(R.string.update_dialog_title))
                setDescription(context.getString(R.string.update_dialog_downloading))
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, APK_FILE_NAME)
                setMimeType("application/vnd.android.package-archive")
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }

            // إلغاء أي receiver سابق إن وجد
            unregisterReceiver(context)

            val newDownloadId = downloadManager.enqueue(request)
            activeDownloadId = newDownloadId
            onDownloadStarted()

            // تسجيل BroadcastReceiver لانتظار اكتمال التحميل
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(recvContext: Context?, intent: Intent?) {
                    val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
                    if (id == activeDownloadId && id != -1L) {
                        unregisterReceiver(context)
                        val appContext = (recvContext ?: context).applicationContext

                        var status = -1
                        downloadManager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                                if (statusIndex >= 0) {
                                    status = cursor.getInt(statusIndex)
                                }
                            }
                        }

                        if (status != DownloadManager.STATUS_SUCCESSFUL) {
                            if (apkFile.exists()) apkFile.delete()
                            val failMsg = appContext.getString(R.string.update_download_failed)
                            Toast.makeText(appContext, failMsg, Toast.LENGTH_SHORT).show()
                            onDownloadFailed(failMsg)
                            return
                        }

                        val pendingResult = goAsync()
                        ioScope.launch {
                            try {
                                val cleanExpected = expectedSha256.trim()
                                val actualSha = if (cleanExpected.isNotBlank() && apkFile.exists()) {
                                    computeSha256(apkFile)
                                } else {
                                    null
                                }

                                val isValid = cleanExpected.isNotBlank() &&
                                    actualSha != null &&
                                    actualSha.equals(cleanExpected, ignoreCase = true)

                                if (!isValid) {
                                    Log.e(
                                        TAG,
                                        "APK SHA-256 verification failed. Expected=${cleanExpected.ifBlank { "<blank>" }}, Actual=${actualSha ?: "<error>"}"
                                    )
                                    if (apkFile.exists()) {
                                        apkFile.delete()
                                    }
                                    withContext(Dispatchers.Main) {
                                        val hashMsg = appContext.getString(R.string.update_hash_verification_failed)
                                        Toast.makeText(appContext, hashMsg, Toast.LENGTH_LONG).show()
                                        onDownloadFailed(hashMsg)
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(appContext, R.string.update_download_success, Toast.LENGTH_SHORT).show()
                                        onDownloadSuccess()
                                        installApk(appContext, apkFile)
                                    }
                                }
                            } finally {
                                pendingResult.finish()
                            }
                        }
                    }
                }
            }
            downloadReceiver = receiver

            val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            ContextCompat.registerReceiver(
                context.applicationContext,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error initiating download for $trimmedUrl", e)
            onDownloadFailed(e.localizedMessage ?: context.getString(R.string.update_download_failed))
        }
    }

    private fun computeSha256(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            file.inputStream().use { input ->
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(Locale.US, it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to compute SHA-256 for ${file.absolutePath}", e)
            null
        }
    }

    /**
     * تشغيل تثبيت ملف الـ APK عبر FileProvider
     */
    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) {
            Log.e(TAG, "APK file not found: ${apkFile.absolutePath}")
            return
        }

        try {
            // التحقق من إذن تثبيت التطبيقات غير المعروفة لنظام Android 8.0 فما فوق
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                    Toast.makeText(context, R.string.update_permission_required, Toast.LENGTH_LONG).show()
                    return
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer", e)
            Toast.makeText(context, "فشل فتح مثبت الحزم: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun unregisterReceiver(context: Context) {
        downloadReceiver?.let {
            try {
                context.applicationContext.unregisterReceiver(it)
            } catch (_: Exception) {}
            downloadReceiver = null
        }
    }
}
