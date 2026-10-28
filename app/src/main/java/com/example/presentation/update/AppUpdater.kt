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
import java.io.File

/**
 * مدير تحميل وتثبيت تحديثات التطبيق عبر الهواء (OTA App Updater)
 */
object AppUpdater {

    private const val TAG = "AppUpdater"
    private const val APK_FILE_NAME = "app-update.apk"
    private var activeDownloadId: Long = -1L
    private var downloadReceiver: BroadcastReceiver? = null

    /**
     * بدء تحميل وتثبيت ملف الـ APK
     */
    fun startDownloadAndInstall(
        context: Context,
        apkUrl: String,
        onDownloadStarted: () -> Unit = {},
        onDownloadFailed: (String) -> Unit = {}
    ) {
        if (apkUrl.isBlank()) {
            onDownloadFailed(context.getString(R.string.update_download_failed))
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

            val uri = Uri.parse(apkUrl)
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
                        val appContext = recvContext ?: context

                        val query = DownloadManager.Query().setFilterById(id)
                        val cursor = downloadManager.query(query)
                        if (cursor != null && cursor.moveToFirst()) {
                            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                            val status = if (statusIndex >= 0) cursor.getInt(statusIndex) else -1
                            cursor.close()

                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                Toast.makeText(appContext, R.string.update_download_success, Toast.LENGTH_SHORT).show()
                                installApk(appContext, apkFile)
                            } else {
                                Toast.makeText(appContext, R.string.update_download_failed, Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            cursor?.close()
                            installApk(appContext, apkFile)
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
                ContextCompat.RECEIVER_EXPORTED
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error initiating download for $apkUrl", e)
            onDownloadFailed(e.localizedMessage ?: context.getString(R.string.update_download_failed))
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
