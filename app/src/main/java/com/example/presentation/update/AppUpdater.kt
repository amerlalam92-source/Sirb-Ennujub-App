package com.example.presentation.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.example.BuildConfig
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
    private const val PREFS_NAME = "app_updater_prefs"
    private const val KEY_DOWNLOAD_ID = "active_download_id"
    private const val KEY_APK_PATH = "active_apk_path"
    private const val KEY_EXPECTED_SHA256 = "active_expected_sha256"

    private val ALLOWED_HOSTS = setOf(
        "github.com",
        "objects.githubusercontent.com",
        "github-releases.githubusercontent.com"
    )
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateLock = Any()

    @Volatile
    private var activeDownloadId: Long = -1L
    @Volatile
    private var isAppInForeground: Boolean = true
    @Volatile
    private var pendingInstallApkFile: File? = null
    private var activeApkFile: File? = null
    private var activeExpectedSha256: String = ""
    private var activeCompletionHandled: AtomicBoolean? = null
    private var activeOnDownloadSuccess: (() -> Unit)? = null
    private var activeOnDownloadFailed: ((String) -> Unit)? = null
    private var downloadReceiver: BroadcastReceiver? = null
    private var pollingJob: Job? = null
    private var recoveryJob: Job? = null

    private data class PersistedDownload(
        val downloadId: Long,
        val apkPath: String,
        val expectedSha256: String
    )

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

    fun onAppPaused() {
        isAppInForeground = false
    }

    /**
     * التحقق عند بدء التشغيل أو عودة التطبيق للواجهة مما إذا كان هناك تحميل نشط قد اكتمل أو فشل في الخلفية
     * أو أثناء إغلاق التطبيق بالكامل (Cold Start Recovery).
     */
    fun checkActiveDownloadAndInstall(context: Context) {
        isAppInForeground = true
        val appContext = context.applicationContext

        val deferredInstallFile = synchronized(stateLock) {
            val file = pendingInstallApkFile
            pendingInstallApkFile = null
            file
        }
        if (deferredInstallFile != null) {
            installApk(appContext, deferredInstallFile)
            return
        }

        val downloadId: Long
        val apkFile: File?
        val expectedSha256: String
        val completionHandled: AtomicBoolean?
        val onSuccess: (() -> Unit)?
        val onFailed: ((String) -> Unit)?

        synchronized(stateLock) {
            downloadId = activeDownloadId
            apkFile = activeApkFile
            expectedSha256 = activeExpectedSha256
            completionHandled = activeCompletionHandled
            onSuccess = activeOnDownloadSuccess
            onFailed = activeOnDownloadFailed
        }

        val downloadManager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return

        if (downloadId != -1L && apkFile != null && completionHandled != null && !completionHandled.get()) {
            when (queryDownloadStatus(downloadManager, downloadId)) {
                DownloadManager.STATUS_RUNNING,
                DownloadManager.STATUS_PENDING,
                DownloadManager.STATUS_PAUSED -> {
                    // التحميل ما زال قيد التنفيذ
                    return
                }

                DownloadManager.STATUS_SUCCESSFUL,
                DownloadManager.STATUS_FAILED,
                null -> {
                    handleDownloadStatusCheck(
                        appContext = appContext,
                        downloadManager = downloadManager,
                        downloadId = downloadId,
                        apkFile = apkFile,
                        expectedSha256 = expectedSha256,
                        completionHandled = completionHandled,
                        onDownloadSuccess = onSuccess ?: {},
                        onDownloadFailed = onFailed ?: {}
                    )
                    return
                }
            }
        }

        if (downloadId == -1L) {
            recoverPersistedDownloadOnLaunch(appContext, downloadManager)
        }
    }

    /**
     * استعادة ومتابعة تحميل محفوظ بعد إعادة تشغيل باردة (Cold Start)
     */
    private fun recoverPersistedDownloadOnLaunch(
        appContext: Context,
        downloadManager: DownloadManager
    ) {
        val persisted = readPersistedDownloadState(appContext) ?: return
        val downloadsDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (downloadsDir == null) {
            clearPersistedDownloadState(appContext)
            return
        }

        val apkFile = File(persisted.apkPath)
        val isInsideDownloadsDir = try {
            apkFile.canonicalPath.startsWith(downloadsDir.canonicalPath + File.separator)
        } catch (_: Exception) {
            false
        }
        val cleanExpected = persisted.expectedSha256.trim()
        if (!isInsideDownloadsDir || cleanExpected.isBlank()) {
            clearPersistedDownloadState(appContext)
            return
        }

        when (queryDownloadStatus(downloadManager, persisted.downloadId)) {
            DownloadManager.STATUS_SUCCESSFUL -> {
                if (!apkFile.exists()) {
                    clearPersistedDownloadState(appContext)
                    return
                }
                synchronized(stateLock) {
                    if (recoveryJob?.isActive == true) {
                        return
                    }
                    recoveryJob = ioScope.launch {
                        try {
                            val archiveInfo = getApkArchiveInfo(appContext, apkFile)
                            if (archiveInfo != null) {
                                val apkVersionCode = PackageInfoCompat.getLongVersionCode(archiveInfo)
                                if (archiveInfo.packageName != appContext.packageName ||
                                    apkVersionCode <= BuildConfig.VERSION_CODE.toLong()
                                ) {
                                    Log.i(
                                        TAG,
                                        "Skipping stale/mismatched recovered OTA APK (pkg=${archiveInfo.packageName}, code=$apkVersionCode)"
                                    )
                                    clearPersistedDownloadState(appContext)
                                    if (apkFile.exists()) {
                                        apkFile.delete()
                                    }
                                    return@launch
                                }
                            }

                            val actualSha = computeSha256(apkFile)
                            val isValid = actualSha != null && actualSha.equals(cleanExpected, ignoreCase = true)
                            if (!isValid) {
                                Log.e(
                                    TAG,
                                    "Recovered APK SHA-256 verification failed. Expected=$cleanExpected, Actual=${actualSha ?: "<error>"}"
                                )
                                clearPersistedDownloadState(appContext)
                                if (apkFile.exists()) {
                                    apkFile.delete()
                                }
                                withContext(Dispatchers.Main) {
                                    val hashMsg = appContext.getString(R.string.update_hash_verification_failed)
                                    Toast.makeText(appContext, hashMsg, Toast.LENGTH_LONG).show()
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    if (isAppInForeground) {
                                        installApk(appContext, apkFile)
                                    } else {
                                        synchronized(stateLock) {
                                            pendingInstallApkFile = apkFile
                                        }
                                    }
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.e(TAG, "Error during cold-start OTA recovery", e)
                        }
                    }
                }
            }

            DownloadManager.STATUS_RUNNING,
            DownloadManager.STATUS_PENDING,
            DownloadManager.STATUS_PAUSED -> {
                val completionHandled = AtomicBoolean(false)
                val shouldAttach = synchronized(stateLock) {
                    if (activeDownloadId != -1L) {
                        false
                    } else {
                        activeDownloadId = persisted.downloadId
                        activeApkFile = apkFile
                        activeExpectedSha256 = cleanExpected
                        activeCompletionHandled = completionHandled
                        activeOnDownloadSuccess = null
                        activeOnDownloadFailed = null
                        true
                    }
                }
                if (shouldAttach) {
                    attachReceiverAndPolling(
                        appContext = appContext,
                        downloadManager = downloadManager,
                        downloadId = persisted.downloadId,
                        apkFile = apkFile,
                        expectedSha256 = cleanExpected,
                        completionHandled = completionHandled,
                        onDownloadSuccess = {},
                        onDownloadFailed = {}
                    )
                }
            }

            else -> {
                clearPersistedDownloadState(appContext)
                if (apkFile.exists()) {
                    apkFile.delete()
                }
            }
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
            val completionHandled = AtomicBoolean(false)
            synchronized(stateLock) {
                activeDownloadId = newDownloadId
                pendingInstallApkFile = null
                activeApkFile = apkFile
                activeExpectedSha256 = expectedSha256
                activeCompletionHandled = completionHandled
                activeOnDownloadSuccess = onDownloadSuccess
                activeOnDownloadFailed = onDownloadFailed
            }
            persistDownloadState(
                context = appContext,
                downloadId = newDownloadId,
                apkPath = apkFile.absolutePath,
                expectedSha256 = expectedSha256
            )
            onDownloadStarted()

            attachReceiverAndPolling(
                appContext = appContext,
                downloadManager = downloadManager,
                downloadId = newDownloadId,
                apkFile = apkFile,
                expectedSha256 = expectedSha256,
                completionHandled = completionHandled,
                onDownloadSuccess = onDownloadSuccess,
                onDownloadFailed = onDownloadFailed
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating download for $trimmedUrl", e)
            onDownloadFailed(e.localizedMessage ?: appContext.getString(R.string.update_download_failed))
        }
    }

    private fun attachReceiverAndPolling(
        appContext: Context,
        downloadManager: DownloadManager,
        downloadId: Long,
        apkFile: File,
        expectedSha256: String,
        completionHandled: AtomicBoolean,
        onDownloadSuccess: () -> Unit,
        onDownloadFailed: (String) -> Unit
    ) {
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

        unregisterReceiver(appContext)
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
                    if (!isActive || completionHandled.get() || activeDownloadId != downloadId) {
                        return@launch
                    }
                    handleDownloadStatusCheck(
                        appContext = appContext,
                        downloadManager = downloadManager,
                        downloadId = downloadId,
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
                if (activeDownloadId == downloadId && completionHandled.compareAndSet(false, true)) {
                    stopPollingAndClearId(downloadId)
                    unregisterReceiver(appContext)
                    clearPersistedDownloadState(appContext)
                    try {
                        downloadManager.remove(downloadId)
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
            pollingJob?.cancel()
            pollingJob = job
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
                            clearPersistedDownloadState(appContext)
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
                                if (isAppInForeground) {
                                    installApk(appContext, apkFile)
                                } else {
                                    synchronized(stateLock) {
                                        pendingInstallApkFile = apkFile
                                    }
                                }
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
                clearPersistedDownloadState(appContext)
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

    private fun getApkArchiveInfo(context: Context, apkFile: File): PackageInfo? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageArchiveInfo(
                    apkFile.absolutePath,
                    PackageManager.PackageInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to inspect OTA APK archive info", e)
            null
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

    private fun persistDownloadState(
        context: Context,
        downloadId: Long,
        apkPath: String,
        expectedSha256: String
    ) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_DOWNLOAD_ID, downloadId)
            .putString(KEY_APK_PATH, apkPath)
            .putString(KEY_EXPECTED_SHA256, expectedSha256)
            .apply()
    }

    private fun readPersistedDownloadState(context: Context): PersistedDownload? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val downloadId = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        val apkPath = prefs.getString(KEY_APK_PATH, null)
        val expectedSha256 = prefs.getString(KEY_EXPECTED_SHA256, null)
        if (downloadId == -1L || apkPath.isNullOrBlank() || expectedSha256.isNullOrBlank()) {
            return null
        }
        return PersistedDownload(
            downloadId = downloadId,
            apkPath = apkPath,
            expectedSha256 = expectedSha256
        )
    }

    private fun clearPersistedDownloadState(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_DOWNLOAD_ID)
            .remove(KEY_APK_PATH)
            .remove(KEY_EXPECTED_SHA256)
            .apply()
    }

    /**
     * تشغيل تثبيت ملف الـ APK عبر FileProvider
     */
    fun installApk(context: Context, apkFile: File) {
        val appContext = context.applicationContext
        if (!apkFile.exists()) {
            Log.e(TAG, "APK file not found: ${apkFile.absolutePath}")
            clearPersistedDownloadState(appContext)
            return
        }

        try {
            // التحقق من إذن تثبيت التطبيقات غير المعروفة لنظام Android 8.0 فما فوق
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!appContext.packageManager.canRequestPackageInstalls()) {
                    synchronized(stateLock) {
                        pendingInstallApkFile = apkFile
                    }
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${appContext.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    appContext.startActivity(settingsIntent)
                    Toast.makeText(appContext, R.string.update_permission_required, Toast.LENGTH_LONG).show()
                    return
                }
            }

            synchronized(stateLock) {
                pendingInstallApkFile = null
            }
            clearPersistedDownloadState(appContext)

            val apkUri = FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer", e)
            synchronized(stateLock) {
                pendingInstallApkFile = null
            }
            clearPersistedDownloadState(appContext)
            Toast.makeText(appContext, "فشل فتح مثبت الحزم: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun cleanup(context: Context, downloadManagerToRemove: DownloadManager? = null) {
        val previousId: Long
        synchronized(stateLock) {
            pollingJob?.cancel()
            pollingJob = null
            recoveryJob?.cancel()
            recoveryJob = null
            previousId = activeDownloadId
            if (downloadManagerToRemove != null) {
                activeDownloadId = -1L
                pendingInstallApkFile = null
                activeApkFile = null
                activeExpectedSha256 = ""
                activeCompletionHandled = null
                activeOnDownloadSuccess = null
                activeOnDownloadFailed = null
            }
        }
        unregisterReceiver(context)
        if (downloadManagerToRemove != null) {
            clearPersistedDownloadState(context)
            if (previousId != -1L) {
                try {
                    downloadManagerToRemove.remove(previousId)
                } catch (_: Exception) {
                }
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
