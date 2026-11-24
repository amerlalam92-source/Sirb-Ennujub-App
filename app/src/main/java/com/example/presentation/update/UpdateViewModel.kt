package com.example.presentation.update

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.R
import com.example.domain.auth.AuthRepository
import com.example.domain.update.AppUpdateInfo
import com.example.domain.update.UpdateRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel مخصص لمراقبة حالة التحديث الإجباري للتطبيق على مستوى التطبيق بالكامل
 * يراقب settings/app_update ويعيد الاشتراك تلقائياً عند تغير حالة المصادقة.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UpdateViewModel(
    updateRepository: UpdateRepository,
    authRepository: AuthRepository
) : ViewModel() {

    val appUpdateInfo: StateFlow<AppUpdateInfo?> = authRepository.observeCurrentLeader()
        .flatMapLatest {
            updateRepository.observeUpdateInfo()
                .catch { emit(null) }
        }
        .catch { emit(null) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    val isForceUpdateRequired: StateFlow<Boolean> = appUpdateInfo
        .map { info ->
            if (info == null) {
                false
            } else {
                val url = info.apkUrl.trim()
                val sha = info.sha256.trim()
                val hasValidHttpsUrl = url.startsWith("https", ignoreCase = true)
                val hasValidSha256 = sha.isNotBlank() &&
                    sha.length == 64 &&
                    sha.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
                hasValidHttpsUrl && hasValidSha256 && info.latestVersionCode > BuildConfig.VERSION_CODE
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _downloadError = MutableStateFlow<String?>(null)
    val downloadError: StateFlow<String?> = _downloadError.asStateFlow()

    private var appContext: Context? = null

    fun startUpdateDownload(context: Context) {
        val info = appUpdateInfo.value ?: return
        val applicationContext = context.applicationContext
        appContext = applicationContext
        _downloadError.value = null
        _isDownloading.value = true
        AppUpdater.startDownloadAndInstall(
            context = applicationContext,
            apkUrl = info.apkUrl,
            expectedSha256 = info.sha256,
            onDownloadStarted = {
                _isDownloading.value = true
                _downloadError.value = null
            },
            onDownloadSuccess = {
                _isDownloading.value = false
                _downloadError.value = null
            },
            onDownloadFailed = { errorMsg ->
                _isDownloading.value = false
                _downloadError.value = errorMsg.ifBlank {
                    applicationContext.getString(R.string.update_download_failed)
                }
            }
        )
    }

    fun onAppResumed(context: Context) {
        AppUpdater.checkActiveDownloadAndInstall(context)
    }

    override fun onCleared() {
        appContext?.let { AppUpdater.cleanup(it) }
        appContext = null
        super.onCleared()
    }

    class Factory(
        private val updateRepository: UpdateRepository,
        private val authRepository: AuthRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(UpdateViewModel::class.java)) {
                return UpdateViewModel(
                    updateRepository = updateRepository,
                    authRepository = authRepository
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
