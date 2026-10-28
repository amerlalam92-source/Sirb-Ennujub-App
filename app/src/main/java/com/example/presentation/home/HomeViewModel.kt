package com.example.presentation.home

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.update.UpdateRepositoryImpl
import com.example.domain.auth.AuthRepository
import com.example.domain.update.AppUpdateInfo
import com.example.domain.update.UpdateRepository
import com.example.presentation.update.AppUpdater
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * نموذج العرض للشاشة الرئيسية (HomeViewModel)
 * يراقب بيانات القائد الكشفي الحالي من مجموعة "leaders/{uid}" في Firestore بشكل لحظي (Realtime).
 * ويفحص توفر تحديثات جديدة للتطبيق عبر OTA.
 */
class HomeViewModel(
    private val authRepository: AuthRepository,
    private val firestore: FirebaseFirestore,
    private val financeRepository: com.example.domain.finance.FinanceRepository? = null,
    private val updateRepository: UpdateRepository = UpdateRepositoryImpl(firestore)
) : ViewModel() {

    private val _leaderFullName = MutableStateFlow(
        authRepository.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "قائد كشفي"
    )
    val leaderFullName: StateFlow<String> = _leaderFullName.asStateFlow()

    private val _leaderPhotoUrl = MutableStateFlow<String?>(
        authRepository.currentUser?.photoUrl?.toString()
    )
    val leaderPhotoUrl: StateFlow<String?> = _leaderPhotoUrl.asStateFlow()

    private val _pendingSubscriptionsCount = MutableStateFlow(0)
    val pendingSubscriptionsCount: StateFlow<Int> = _pendingSubscriptionsCount.asStateFlow()

    private val _outstandingDebtTotal = MutableStateFlow(0)
    val outstandingDebtTotal: StateFlow<Int> = _outstandingDebtTotal.asStateFlow()

    // حالة التحديث التلقائي OTA
    private val _appUpdateInfo = MutableStateFlow<AppUpdateInfo?>(null)
    val appUpdateInfo: StateFlow<AppUpdateInfo?> = _appUpdateInfo.asStateFlow()

    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog: StateFlow<Boolean> = _showUpdateDialog.asStateFlow()

    private val _isDownloadingUpdate = MutableStateFlow(false)
    val isDownloadingUpdate: StateFlow<Boolean> = _isDownloadingUpdate.asStateFlow()

    private var leaderListenerRegistration: ListenerRegistration? = null

    init {
        observeLeaderProfile()
        observeFinanceData()
        observeAppUpdate()
    }

    private fun observeAppUpdate() {
        viewModelScope.launch {
            try {
                updateRepository.observeUpdateInfo()
                    .catch { Log.w("HomeViewModel", "Error observing app_update", it) }
                    .collect { update ->
                        _appUpdateInfo.value = update
                        if (update != null && update.latestVersionCode > BuildConfig.VERSION_CODE && update.apkUrl.isNotBlank()) {
                            _showUpdateDialog.value = true
                        }
                    }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Failed to check for app updates", e)
            }
        }
    }

    fun dismissUpdateDialog() {
        _showUpdateDialog.value = false
    }

    fun triggerUpdate(context: Context) {
        val info = _appUpdateInfo.value ?: return
        _isDownloadingUpdate.value = true
        AppUpdater.startDownloadAndInstall(
            context = context,
            apkUrl = info.apkUrl,
            onDownloadStarted = {
                _isDownloadingUpdate.value = true
            },
            onDownloadFailed = {
                _isDownloadingUpdate.value = false
            }
        )
    }

    private fun observeFinanceData() {
        val repo = financeRepository ?: return
        viewModelScope.launch {
            try {
                repo.observeAllSubscriptions()
                    .catch { }
                    .collect { subs ->
                        _pendingSubscriptionsCount.value = subs.count { it.debtAmount > 0 }
                    }
            } catch (_: Exception) {}
        }
        viewModelScope.launch {
            try {
                repo.observeTotalOutstandingDebt()
                    .catch { }
                    .collect { debt ->
                        _outstandingDebtTotal.value = debt
                    }
            } catch (_: Exception) {}
        }
    }

    private fun observeLeaderProfile() {
        val user = authRepository.currentUser ?: return
        val uid = user.uid

        try {
            leaderListenerRegistration = firestore.collection("leaders")
                .document(uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w("HomeViewModel", "Error observing leaders/$uid", error)
                        return@addSnapshotListener
                    }

                    if (snapshot != null && snapshot.exists()) {
                        val name = snapshot.getString("name")?.trim()
                        val surname = snapshot.getString("surname")?.trim()
                        val docFullName = if (!name.isNullOrBlank() && !surname.isNullOrBlank()) {
                            "$name $surname".trim()
                        } else {
                            snapshot.getString("fullName")?.trim()
                        }

                        val displayName = user.displayName?.trim()
                        val resolvedName: String = when {
                            !docFullName.isNullOrBlank() -> docFullName
                            !displayName.isNullOrBlank() -> displayName
                            else -> "قائد كشفي"
                        }
                        _leaderFullName.value = resolvedName

                        val photo = snapshot.getString("photoUrl")
                            ?: snapshot.getString("photo_url")
                            ?: user.photoUrl?.toString()
                        _leaderPhotoUrl.value = photo
                    } else {
                        val fallbackName: String = user.displayName?.trim()?.takeIf { it.isNotBlank() } ?: "قائد كشفي"
                        _leaderFullName.value = fallbackName
                        _leaderPhotoUrl.value = user.photoUrl?.toString()
                    }
                }
        } catch (e: Exception) {
            Log.e("HomeViewModel", "Failed to register snapshot listener for leader $uid", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        leaderListenerRegistration?.remove()
        leaderListenerRegistration = null
    }

    class Factory(
        private val authRepository: AuthRepository,
        private val firestore: FirebaseFirestore,
        private val financeRepository: com.example.domain.finance.FinanceRepository? = null,
        private val updateRepository: UpdateRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val updateRepo = updateRepository ?: UpdateRepositoryImpl(firestore)
            return HomeViewModel(authRepository, firestore, financeRepository, updateRepo) as T
        }
    }
}
