package com.example.presentation.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.activity.Activity
import com.example.domain.activity.ActivityRepository
import com.example.domain.activity.ActivityType
import com.example.domain.attendance.Attendance
import com.example.domain.attendance.AttendancePointsSettings
import com.example.domain.attendance.AttendanceRepository
import com.example.domain.attendance.AttendanceStatus
import com.example.domain.attendance.usecases.GetAttendanceForActivityUseCase
import com.example.domain.attendance.usecases.ObservePointsSettingsUseCase
import com.example.domain.attendance.usecases.SaveAttendanceBatchUseCase
import com.example.domain.scout.Scout
import com.example.domain.scout.ScoutRepository
import java.text.Collator
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * نموذج العرض لتسجيل الحضور الكشفي (Attendance ViewModel)
 * يدير معالج الحضور المتسلسل (Wizard)، التحقق، حساب النقاط، والفرز الهجائي باللغة العربية.
 */
class AttendanceViewModel(
    private val activityRepository: ActivityRepository,
    private val scoutRepository: ScoutRepository,
    private val attendanceRepository: AttendanceRepository,
    private val getAttendanceForActivityUseCase: GetAttendanceForActivityUseCase,
    private val saveAttendanceBatchUseCase: SaveAttendanceBatchUseCase,
    private val observePointsSettingsUseCase: ObservePointsSettingsUseCase
) : ViewModel() {

    private val arabicCollator = Collator.getInstance(com.example.presentation.common.AppLocale.AR_DZ).apply {
        strength = Collator.PRIMARY
    }

    // تدفق شاشة اختيار النشاط
    val pickerUiState: StateFlow<AttendancePickerUiState> = combine(
        activityRepository.observeActivities(),
        observePointsSettingsUseCase()
    ) { activities, _ ->
        // الأنشطة المؤهلة: جميع الأنشطة غير الملغاة مرتبة من الأحدث إلى الأقدم
        val eligible = activities.filter { act ->
            act.status != com.example.domain.activity.ActivityStatus.CANCELLED.key
        }.sortedByDescending { it.startDate }

        AttendancePickerUiState.Success(
            activities = eligible,
            recordedActivityIds = emptySet()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AttendancePickerUiState.Loading
    )

    // حالة معالج تسجيل الحضور (Wizard)
    private val _entryUiState = MutableStateFlow(AttendanceEntryUiState())
    val entryUiState: StateFlow<AttendanceEntryUiState> = _entryUiState.asStateFlow()

    /**
     * تهيئة معالج الحضور للنشاط المحدد
     */
    fun initAttendanceEntry(activityId: String) {
        _entryUiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val activity = activityRepository.getActivityById(activityId)
                if (activity == null) {
                    _entryUiState.update { it.copy(isLoading = false, errorMessage = "النشاط غير موجود") }
                    return@launch
                }

                // جلب الأشبال النشطين فقط وترتيبهم أبجدياً (الاسم ثم اللقب)
                val scouts = scoutRepository.observeScouts().first()
                val sortedScouts = scouts.filter { it.isActive }.sortedWith { s1, s2 ->
                    val nameCompare = arabicCollator.compare(s1.name, s2.name)
                    if (nameCompare != 0) nameCompare else arabicCollator.compare(s1.surname, s2.surname)
                }

                // جلب سجلات الحضور السابقة إن وُجدت
                val existingRecords = getAttendanceForActivityUseCase(activityId).first()
                val existingMap = existingRecords.associate {
                    it.scoutId to it.attendanceStatus
                }

                // مراقبة إعدادات النقاط
                val pointsSettings = observePointsSettingsUseCase().first()
                _entryUiState.value = AttendanceEntryUiState(
                    activity = activity,
                    scoutsQueue = sortedScouts,
                    currentIndex = 0,
                    recordedStatuses = existingMap,
                    pointsSettings = pointsSettings,
                    isLoading = false
                )
            } catch (e: Exception) {
                _entryUiState.update { it.copy(isLoading = false, errorMessage = e.localizedMessage) }
            }
        }
    }

    /**
     * تسجيل حالة حضور الشبل الحالي
     */
    fun onSelectStatus(status: AttendanceStatus) {
        val currentScout = _entryUiState.value.currentScout ?: return
        _entryUiState.update { current ->
            val updated = current.recordedStatuses.toMutableMap()
            updated[currentScout.id] = status
            current.copy(recordedStatuses = updated)
        }
    }

    /**
     * الانتقال للشبل التالي
     * إذا لم يتم تحديد حالة للشبل، يُعاد إدراجه في نهاية القائمة وتُطلق رسالة توضيحية.
     */
    fun onNext(onSkipped: () -> Unit) {
        val state = _entryUiState.value
        val scout = state.currentScout ?: return
        val currentStatus = state.recordedStatuses[scout.id]

        if (currentStatus == null) {
            // إعادة الشبل إلى نهاية قائمة الانتظار
            val updatedQueue = state.scoutsQueue.toMutableList()
            updatedQueue.removeAt(state.currentIndex)
            updatedQueue.add(scout)

            _entryUiState.update { it.copy(scoutsQueue = updatedQueue) }
            onSkipped()
        } else {
            if (state.currentIndex < state.scoutsQueue.size - 1) {
                _entryUiState.update { it.copy(currentIndex = it.currentIndex + 1) }
            }
        }
    }

    /**
     * العودة للشبل السابق
     */
    fun onPrevious() {
        _entryUiState.update { current ->
            if (current.currentIndex > 0) {
                current.copy(currentIndex = current.currentIndex - 1)
            } else current
        }
    }

    /**
     * إنهاء وحفظ سجل الحضور بالكامل دفعة واحدة
     */
    fun saveAttendance(onSuccess: () -> Unit) {
        val state = _entryUiState.value
        val activity = state.activity ?: return
        val usesPoints = activity.activityType.usesPointsSystem()

        _entryUiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            val listToSave = state.scoutsQueue.mapNotNull { scout ->
                val status = state.recordedStatuses[scout.id]
                if (status != null) {
                    val points = if (usesPoints) {
                        state.pointsSettings.getPointsForStatus(status)
                    } else 0

                    Attendance(
                        activityId = activity.id,
                        scoutId = scout.id,
                        scoutFullName = scout.fullName,
                        status = status.key,
                        points = points,
                        activityDate = activity.startDate
                    )
                } else null
            }

            val result = saveAttendanceBatchUseCase(listToSave)
            result.fold(
                onSuccess = {
                    _entryUiState.update { it.copy(isSaving = false, isFinished = true) }
                    onSuccess()
                },
                onFailure = { err ->
                    _entryUiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = err.localizedMessage ?: "حدث خطأ أثناء حفظ الحضور"
                        )
                    }
                }
            )
        }
    }

    class Factory(
        private val activityRepository: ActivityRepository,
        private val scoutRepository: ScoutRepository,
        private val attendanceRepository: AttendanceRepository,
        private val getAttendanceForActivityUseCase: GetAttendanceForActivityUseCase,
        private val saveAttendanceBatchUseCase: SaveAttendanceBatchUseCase,
        private val observePointsSettingsUseCase: ObservePointsSettingsUseCase
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AttendanceViewModel(
                activityRepository,
                scoutRepository,
                attendanceRepository,
                getAttendanceForActivityUseCase,
                saveAttendanceBatchUseCase,
                observePointsSettingsUseCase
            ) as T
        }
    }
}
