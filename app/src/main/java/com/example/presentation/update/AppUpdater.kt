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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * مدير تحميل وتثبيت تحديثات التطبيق عبر الهواء (OTA App Updater)
 */
object AppUpdater {

    private const val TAG = "AppUpdater"
    private const val APK_FILE_NAME = "app-update.apk"
    private const val POLL_INTERVAL_MS = 1_000L
    private const val MAX_POLL_ATTEMPTS = 15 * 60 // 15 دقيقة بفاصل ثانية واحدة
    private val ALLOWED_HOSTS = setOf(
        "github.com",
        "objects.githubusercontent.com",
        "github-releases.githubusercontent.com"
    )
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateLock = Any()

    @Volatile
    private var activeDownloadId: Long = -1L
    private var downloadReceiver: BroadcastReceiver? = null
    private var pollingJob: Job? = null

    /**
     * التحقق من أن رابط التحميل يستخدم HTTPS ومن نطاق GitHub موثوق فقط
     */
    fun isTrustedDownloadUrl(apkUrl: String): Boolean {
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
     * فتح رابط التحديث في المتصفح (تحميل يدوي) بعد التحقق من قائمة النطاقات المسموحة
     */
    fun openManualDownload(context: Context, apkUrl: String): Boolean {
        val trimmedUrl = apkUrl.trim()
        if (!isTrustedDownloadUrl(trimmedUrl)) {
            Log.e(TAG, "Blocked untrusted manual OTA URL: $trimmedUrl")
            Toast.makeText(context, R.string.update_invalid_url, Toast.LENGTH_LONG).show()
            return false
        }
        return try {
            val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(trimmedUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(viewIntent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open manual download URL", e)
            Toast.makeText(context, R.string.force_update_open_error, Toast.LENGTH_LONG).show()
            false
        }
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
        val appContext = context.applicationContext
        val trimmedUrl = apkUrl.trim()
        if (trimmedUrl.isBlank()) {
            val msg = appContext.getString(R.string.update_download_failed)
            Toast.makeText(appContext, msg, Toast.LENGTH_LONG).show()
            onDownloadFailed(msg)
            return
        }

        if (!isTrustedDownloadUrl(trimmedUrl)) {
            Log.e(TAG, "Blocked untrusted OTA update URL: $trimmedUrl")
            val msg = appContext.getString(R.string.update_invalid_url)
            Toast.makeText(appContext, msg, Toast.LENGTH_LONG).show()
            onDownloadFailed(msg)
            return
        }

        try {
            val downloadManager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            if (downloadManager == null) {
                onDownloadFailed("DownloadManager not available")
                return
            }

            // تنظيف أي تحميل أو مستقبل سابق وحذف أي ملف قديم قبل بدء التحميل الجديد
            cleanup(appContext, downloadManagerToRemove = downloadManager)

            val downloadsDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            if (downloadsDir == null) {
                val msg = appContext.getString(R.string.update_download_failed)
                Toast.makeText(appContext, msg, Toast.LENGTH_LONG).show()
                onDownloadFailed(msg)
                return
            }
            val apkFile = File(downloadsDir, APK_FILE_NAME)
            if (apkFile.exists()) {
                apkFile.delete()
            }

            val uri = Uri.parse(trimmedUrl)
            val request = DownloadManager.Request(uri).apply {
                setTitle(appContext.getString(R.string.update_dialog_title))
                setDescription(appContext.getString(R.string.update_dialog_downloading))
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_DOWNLOADS, APK_FILE_NAME)
                setMimeType("application/vnd.android.package-archive")
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }

            val newDownloadId = downloadManager.enqueue(request)
            activeDownloadId = newDownloadId
            val completionHandled = AtomicBoolean(false)
            onDownloadStarted()

            // تسجيل BroadcastReceiver بـ RECEIVER_EXPORTED لأن DownloadManager خدمة نظام خارجية،
            // مع التحقق الصارم من action و EXTRA_DOWNLOAD_ID وعدم قراءة أي مسار من Intent.
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(recvContext: Context?, intent: Intent?) {
                    if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                    if (id == -1L || id != activeDownloadId) return

                    val pendingResult = goAsync()
                    val finishPending = {
                        try {
                            pendingResult.finish()
                        } catch (_: Exception) {
                        }
                    }

                    val isAsyncVerificationLaunched = handleDownloadStatusCheck(
                        appContext = appContext,
                        downloadManager = downloadManager,
                        downloadId = id,
                        apkFile = apkFile,
                        expectedSha256 = expectedSha256,
                        completionHandled = completionHandled,
                        onDownloadSuccess = onDownloadSuccess,
                        onDownloadFailed = onDownloadFailed,
                        onAsyncFinished = finishPending
                    )
                    if (!isAsyncVerificationLaunched) {
                        finishPending()
                    }
                }
            }

            synchronized(stateLock) {
                downloadReceiver = receiver
            }

            val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )

            // آلية الاستعلام الدوري (Polling Fallback) لبعض الأجهزة التي قد تسقط البث في الخلفية
            val job = ioScope.launch {
                try {
                    repeat(MAX_POLL_ATTEMPTS) {
                        delay(POLL_INTERVAL_MS)
                        if (!isActive || completionHandled.get() || activeDownloadId != newDownloadId) {
                            return@launch
                        }
                        handleDownloadStatusCheck(
                            appContext = appContext,
                            downloadManager = downloadManager,
                            downloadId = newDownloadId,
                            apkFile = apkFile,
                            expectedSha256 = expectedSha256,
                            completionHandled = completionHandled,
                            onDownloadSuccess = onDownloadSuccess,
                            onDownloadFailed = onDownloadFailed
                        )
                        if (completionHandled.get()) {
                            return@launch
                        }
                    }

                    // انتهاء المهلة القصوى (15 دقيقة) دون اكتمال التحميل
                    if (activeDownloadId == newDownloadId && completionHandled.compareAndSet(false, true)) {
                        stopPollingAndClearId(newDownloadId)
                        unregisterReceiver(appContext)
                        try {
                            downloadManager.remove(newDownloadId)
                        } catch (_: Exception) {
                        }
                        if (apkFile.exists()) {
                            apkFile.delete()
                        }
                        val failMsg = appContext.getString(R.string.update_download_failed)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(appContext, failMsg, Toast.LENGTH_SHORT).show()
                            onDownloadFailed(failMsg)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Error during DownloadManager polling", e)
                }
            }

            synchronized(stateLock) {
                pollingJob = job
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating download for $trimmedUrl", e)
            onDownloadFailed(e.localizedMessage ?: appContext.getString(R.string.update_download_failed))
        }
    }

    /**
     * التحقق من حالة التحميل عبر DownloadManager.query وتنفيذ معالج الاكتمال بشكل آمن وموحّد (Idempotent).
     * يعيد true فقط إذا تم إطلاق تحقق غير متزامن مسؤول عن استدعاء [onAsyncFinished].
     */
    private fun handleDownloadStatusCheck(
        appContext: Context,
        downloadManager: DownloadManager,
        downloadId: Long,
        apkFile: File,
        expectedSha256: String,
        completionHandled: AtomicBoolean,
        onDownloadSuccess: () -> Unit,
        onDownloadFailed: (String) -> Unit,
        onAsyncFinished: (() -> Unit)? = null
    ): Boolean {
        if (downloadId == -1L || downloadId != activeDownloadId || completionHandled.get()) {
            return false
        }

        val status = queryDownloadStatus(downloadManager, downloadId)
        return when (status) {
            DownloadManager.STATUS_RUNNING,
            DownloadManager.STATUS_PENDING,
            DownloadManager.STATUS_PAUSED -> {
                // التحميل ما زال قيد التنفيذ؛ تجاهل وانتظر
                false
            }

            DownloadManager.STATUS_SUCCESSFUL -> {
                if (!completionHandled.compareAndSet(false, true)) {
                    return false
                }
                stopPollingAndClearId(downloadId)
                unregisterReceiver(appContext)

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
                        onAsyncFinished?.invoke()
                    }
                }
                true
            }

            else -> {
                // STATUS_FAILED أو تم إلغاء/إزالة التحميل من النظام
                if (!completionHandled.compareAndSet(false, true)) {
                    return false
                }
                stopPollingAndClearId(downloadId)
                unregisterReceiver(appContext)
                if (apkFile.exists()) {
                    apkFile.delete()
                }
                ioScope.launch(Dispatchers.Main) {
                    try {
                        val failMsg = appContext.getString(R.string.update_download_failed)
                        Toast.makeText(appContext, failMsg, Toast.LENGTH_SHORT).show()
                        onDownloadFailed(failMsg)
                    } finally {
                        onAsyncFinished?.invoke()
                    }
                }
                true
            }
        }
    }

    private fun queryDownloadStatus(downloadManager: DownloadManager, downloadId: Long): Int? {
        return try {
            downloadManager.query(DownloadManager.Query().setFilterById(downloadId))?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    if (statusIndex >= 0) cursor.getInt(statusIndex) else null
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query DownloadManager for id=$downloadId", e)
            null
        }
    }

    private fun stopPollingAndClearId(downloadId: Long) {
        synchronized(stateLock) {
            pollingJob?.cancel()
            pollingJob = null
            if (activeDownloadId == downloadId) {
                activeDownloadId = -1L
            }
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

    fun cleanup(context: Context, downloadManagerToRemove: DownloadManager? = null) {
        val previousId: Long
        synchronized(stateLock) {
            pollingJob?.cancel()
            pollingJob = null
            previousId = activeDownloadId
            if (downloadManagerToRemove != null) {
                activeDownloadId = -1L
            }
        }
        unregisterReceiver(context)
        if (downloadManagerToRemove != null && previousId != -1L) {
            try {
                downloadManagerToRemove.remove(previousId)
            } catch (_: Exception) {
            }
        }
    }

    fun unregisterReceiver(context: Context) {
        val receiverToUnregister = synchronized(stateLock) {
            val current = downloadReceiver
            downloadReceiver = null
            current
        }
        receiverToUnregister?.let {
            try {
                context.applicationContext.unregisterReceiver(it)
            } catch (_: Exception) {
            }
        }
    }
}
