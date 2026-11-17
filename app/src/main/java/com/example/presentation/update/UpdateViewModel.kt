package com.example.presentation.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.domain.auth.AuthRepository
import com.example.domain.update.AppUpdateInfo
import com.example.domain.update.UpdateRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
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

    private val isTemporarilySkipped = MutableStateFlow(false)

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

    val isForceUpdateRequired: StateFlow<Boolean> = combine(
        appUpdateInfo,
        isTemporarilySkipped
    ) { info, skipped ->
        if (skipped || info == null) {
            false
        } else {
            val url = info.apkUrl.trim()
            val hasValidUrl = url.isNotBlank() &&
                (url.startsWith("http://", ignoreCase = true) ||
                    url.startsWith("https://", ignoreCase = true))
            hasValidUrl && info.latestVersionCode > BuildConfig.VERSION_CODE
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = false
    )

    fun skipForceUpdate() {
        isTemporarilySkipped.value = true
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
