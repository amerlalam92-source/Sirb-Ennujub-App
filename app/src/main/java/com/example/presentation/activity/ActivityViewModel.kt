package com.example.presentation.activity

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.domain.activity.Activity
import com.example.domain.activity.ActivityRepository
import com.example.domain.activity.ActivityStatus
import com.example.domain.activity.ActivityType
import com.example.domain.activity.usecases.AddActivityUseCase
import com.example.domain.activity.usecases.CancelActivityUseCase
import com.example.domain.activity.usecases.DeleteActivityUseCase
import com.example.domain.activity.usecases.GetActivityByIdUseCase
import com.example.domain.activity.usecases.MarkActivityDoneUseCase
import com.example.domain.activity.usecases.MarkActivityDoneWithReportUseCase
import com.example.domain.activity.usecases.ObserveActivitiesUseCase
import com.example.domain.activity.usecases.UpdateActivityReportUseCase
import com.example.domain.activity.usecases.UpdateActivityUseCase
import com.example.domain.attendance.Attendance
import com.example.domain.attendance.AttendanceRepository
import com.example.domain.attendance.AttendanceRow
import com.example.domain.attendance.usecases.GetAttendanceForActivityUseCase
import com.example.domain.attendance.usecases.GetAttendanceRowsForActivityUseCase
import java.util.Calendar
import java.util.LinkedHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * نموذج العرض لإدارة الأنشطة الكشفية (Activity ViewModel)
 * يدير تدفق قائمة الأنشطة، التجميع الذكي، تفاصيل النشاط، مواضيع الاجتماعات، والتقرير الأدبي النهائي، وحالة وقائمة تسجيل الحضور.
 */
class ActivityViewModel(
    private val repository: ActivityRepository,
    private val observeActivitiesUseCase: ObserveActivitiesUseCase,
    private val getActivityByIdUseCase: GetActivityByIdUseCase,
    private val addActivityUseCase: AddActivityUseCase,
    private val updateActivityUseCase: UpdateActivityUseCase,
    private val markActivityDoneUseCase: MarkActivityDoneUseCase,
    private val markActivityDoneWithReportUseCase: MarkActivityDoneWithReportUseCase,
    private val updateActivityReportUseCase: UpdateActivityReportUseCase,
    private val cancelActivityUseCase: CancelActivityUseCase,
    private val deleteActivityUseCase: DeleteActivityUseCase,
    private val attendanceRepository: AttendanceRepository? = null,
    private val getAttendanceForActivityUseCase: GetAttendanceForActivityUseCase? = null,
    private val getAttendanceRowsForActivityUseCase: GetAttendanceRowsForActivityUseCase? = null
) : ViewModel() {

    // معرفات الأنشطة التي تم تسجيل حضور لها مسبقاً (لتجنب N استعلامات)
    val recordedAttendanceActivityIds: StateFlow<Set<String>> = attendanceRepository?.observeRecordedActivityIds()
        ?.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
        ?: MutableStateFlow(emptySet())

    fun observeAttendanceForActivity(activityId: String): Flow<List<Attendance>> {
        return getAttendanceForActivityUseCase?.invoke(activityId) ?: flowOf(emptyList())
    }

    fun observeAttendanceRowsForActivity(activityId: String): Flow<List<AttendanceRow>> {
        return getAttendanceRowsForActivityUseCase?.invoke(activityId) ?: flowOf(emptyList())
    }

    fun shareActivityFullReport(context: Context, activity: Activity) {
        viewModelScope.launch {
            val attendanceRows = getAttendanceRowsForActivityUseCase?.invoke(activity.id)?.let { flow ->
                try {
                    flow.first()
                } catch (_: Exception) {
                    emptyList()
                }
            } ?: emptyList()
            val file = com.example.presentation.reports.PdfExporter.exportActivityFullReportPdf(
                activity = activity,
                attendanceRows = attendanceRows,
                meetingTopics = activity.meetingTopics,
                context = context
            )
            com.example.presentation.reports.PdfExporter.shareActivityPdf(file, activity.title, context)
        }
    }

    fun shareActivityAsPdf(context: Context, activity: Activity) {
        shareActivityFullReport(context, activity)
    }

    private val _meetingNumbers = MutableStateFlow<Map<String, Int>>(emptyMap())
    val meetingNumbers: StateFlow<Map<String, Int>> = _meetingNumbers.asStateFlow()

    fun loadTrainingMeetingNumber(activityId: String) {
        if (_meetingNumbers.value.containsKey(activityId)) return
        viewModelScope.launch {
            val num = attendanceRepository?.getTrainingMeetingNumber(activityId) ?: 0
            if (num > 0) {
                _meetingNumbers.update { it + (activityId to num) }
            }
        }
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(ActivityFilter.ALL)
    val selectedFilter: StateFlow<ActivityFilter> = _selectedFilter.asStateFlow()

    // تدفق قائمة الأنشطة المجمعة والمفروزة ذكياً حسب الفترات الزمنية
    val listUiState: StateFlow<ActivityListUiState> = combine(
        observeActivitiesUseCase(),
        _searchQuery,
        _selectedFilter
    ) { allActivities, query, filter ->
        val filtered = allActivities.filter { activity ->
            val matchesFilter = when (filter) {
                ActivityFilter.UPCOMING -> activity.isUpcoming()
                ActivityFilter.PAST -> activity.isPast()
                ActivityFilter.ALL -> true
            }

            val q = query.trim().lowercase()
            val matchesQuery = if (q.isBlank()) true else {
                activity.title.lowercase().contains(q) ||
                        activity.location.lowercase().contains(q) ||
                        activity.activityType.arabicLabel.lowercase().contains(q) ||
                        activity.responsibleName.lowercase().contains(q)
            }

            matchesFilter && matchesQuery
        }

        val upcomingCount = allActivities.count { it.isUpcoming() }
        val pastCount = allActivities.count { it.isPast() }
        val grouped = groupActivitiesSmartly(filtered)

        ActivityListUiState.Success(
            allActivities = allActivities,
            filteredActivities = filtered,
            groupedActivities = grouped,
            upcomingCount = upcomingCount,
            pastCount = pastCount,
            totalCount = allActivities.size,
            filter = filter,
            searchQuery = query
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ActivityListUiState.Loading
    )

    // حالة تفاصيل النشاط
    private val _detailUiState = MutableStateFlow<ActivityDetailUiState>(ActivityDetailUiState.Loading)
    val detailUiState: StateFlow<ActivityDetailUiState> = _detailUiState.asStateFlow()

    // حالة استمارة إضافة / تعديل النشاط
    private val _formUiState = MutableStateFlow(ActivityFormUiState())
    val formUiState: StateFlow<ActivityFormUiState> = _formUiState.asStateFlow()

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun onFilterSelected(filter: ActivityFilter) {
        _selectedFilter.value = filter
    }

    /**
     * تحميل تفاصيل نشاط محدد
     */
    fun loadActivityDetail(id: String) {
        _detailUiState.value = ActivityDetailUiState.Loading
        viewModelScope.launch {
            val activity = getActivityByIdUseCase(id)
            if (activity != null) {
                _detailUiState.value = ActivityDetailUiState.Success(activity)
            } else {
                _detailUiState.value = ActivityDetailUiState.Error("تعذر العثور على بيانات النشاط")
            }
        }
    }

    /**
     * تهيئة الاستمارة للإضافة أو التعديل
     */
    fun initForm(activityId: String?) {
        val today = Calendar.getInstance().timeInMillis
        if (activityId.isNullOrBlank()) {
            _formUiState.value = ActivityFormUiState(
                startDate = today,
                endDate = today,
                type = ActivityType.TRAINING_MEETING.key,
                status = ActivityStatus.PLANNED.key,
                meetingTopics = listOf("") // موضوع افتراضي للاجتماع التدريبي
            )
        } else {
            _formUiState.value = ActivityFormUiState(isLoading = true)
            viewModelScope.launch {
                val activity = getActivityByIdUseCase(activityId)
                if (activity != null) {
                    val topics = if (activity.activityType.isMeetingType()) {
                        activity.meetingTopics.ifEmpty { listOf("") }
                    } else {
                        emptyList()
                    }
                    _formUiState.value = ActivityFormUiState(
                        id = activity.id,
                        title = activity.title,
                        type = activity.type,
                        startDate = activity.startDate,
                        endDate = activity.endDate,
                        startTime = activity.startTime,
                        endTime = activity.endTime,
                        location = activity.location,
                        objectives = activity.objectives,
                        tools = activity.tools,
                        responsibleName = activity.responsibleName,
                        status = activity.status,
                        notes = activity.notes,
                        meetingTopics = topics,
                        finalReport = activity.finalReport,
                        collectSubscriptions = activity.collectSubscriptions,
                        isLoading = false
                    )
                } else {
                    _formUiState.value = ActivityFormUiState(
                        isLoading = false,
                        generalError = "تعذر تحميل بيانات النشاط"
                    )
                }
            }
        }
    }

    fun onFormTitleChanged(title: String) {
        _formUiState.update { it.copy(title = title, titleError = null, generalError = null) }
    }

    fun onFormTypeChanged(type: String) {
        _formUiState.update { current ->
            val isMeeting = ActivityType.fromKey(type).isMeetingType()
            val updatedTopics = if (isMeeting) {
                if (current.meetingTopics.isEmpty()) listOf("") else current.meetingTopics
            } else {
                current.meetingTopics
            }
            current.copy(type = type, typeError = null, generalError = null, meetingTopics = updatedTopics)
        }
    }

    fun onAddTopic() {
        _formUiState.update { current ->
            current.copy(meetingTopics = current.meetingTopics + "")
        }
    }

    fun onUpdateTopic(index: Int, text: String) {
        _formUiState.update { current ->
            val updated = current.meetingTopics.toMutableList()
            if (index in updated.indices) {
                updated[index] = text
            }
            current.copy(meetingTopics = updated)
        }
    }

    fun onRemoveTopic(index: Int) {
        _formUiState.update { current ->
            val updated = current.meetingTopics.toMutableList()
            if (index in updated.indices) {
                updated.removeAt(index)
            }
            // إذا فرغت القائمة ونوع النشاط اجتماع، نضمن وجود حقل فارغ واحد على الأقل
            val finalTopics = if (updated.isEmpty() && ActivityType.fromKey(current.type).isMeetingType()) {
                listOf("")
            } else {
                updated
            }
            current.copy(meetingTopics = finalTopics)
        }
    }

    fun onFormStartDateChanged(millis: Long) {
        _formUiState.update { current ->
            val newEnd = if (current.endDate < millis) millis else current.endDate
            current.copy(startDate = millis, endDate = newEnd, startDateError = null, endDateError = null)
        }
    }

    fun onFormEndDateChanged(millis: Long) {
        _formUiState.update { it.copy(endDate = millis, endDateError = null, generalError = null) }
    }

    fun onFormStartTimeChanged(time: String) {
        _formUiState.update { it.copy(startTime = time) }
    }

    fun onFormEndTimeChanged(time: String) {
        _formUiState.update { it.copy(endTime = time) }
    }

    fun onFormLocationChanged(loc: String) {
        _formUiState.update { it.copy(location = loc) }
    }

    fun onFormObjectivesChanged(obj: String) {
        _formUiState.update { it.copy(objectives = obj) }
    }

    fun onFormToolsChanged(tools: String) {
        _formUiState.update { it.copy(tools = tools) }
    }

    fun onFormResponsibleNameChanged(name: String) {
        _formUiState.update { it.copy(responsibleName = name) }
    }

    fun onFormNotesChanged(notes: String) {
        _formUiState.update { it.copy(notes = notes) }
    }

    fun onFormCollectSubscriptionsChanged(collect: Boolean) {
        _formUiState.update { it.copy(collectSubscriptions = collect) }
    }

    /**
     * حفظ استمارة النشاط مع تنظيف مواضيع الاجتماعات الفارغة
     */
    fun saveActivity() {
        val state = _formUiState.value
        val title = state.title.trim()

        var hasError = false
        var titleErr: Int? = null
        var dateErr: Int? = null
        var typeErr: Int? = null

        if (title.isBlank()) {
            titleErr = R.string.error_title_required
            hasError = true
        } else if (title.length < 3) {
            titleErr = R.string.error_title_short
            hasError = true
        }

        if (state.endDate < state.startDate) {
            dateErr = R.string.error_end_before_start
            hasError = true
        }

        if (state.type !in ActivityType.getAllKeys()) {
            typeErr = R.string.error_type_required
            hasError = true
        }

        if (hasError) {
            _formUiState.update {
                it.copy(
                    titleError = titleErr,
                    endDateError = dateErr,
                    typeError = typeErr
                )
            }
            return
        }

        _formUiState.update { it.copy(isSaving = true, generalError = null) }

        viewModelScope.launch {
            val isMeeting = ActivityType.fromKey(state.type).isMeetingType()
            val cleanedTopics = if (isMeeting) {
                state.meetingTopics.map { it.trim() }.filter { it.isNotBlank() }
            } else {
                emptyList()
            }

            val activityToSave = Activity(
                id = state.id,
                title = title,
                type = state.type,
                startDate = state.startDate,
                endDate = state.endDate,
                startTime = state.startTime.trim(),
                endTime = state.endTime.trim(),
                location = state.location.trim(),
                objectives = state.objectives.trim(),
                tools = state.tools.trim(),
                responsibleName = state.responsibleName.trim(),
                status = state.status,
                notes = state.notes.trim(),
                meetingTopics = cleanedTopics,
                finalReport = state.finalReport.trim(),
                collectSubscriptions = state.collectSubscriptions
            )

            val result = if (state.id.isBlank()) {
                addActivityUseCase(activityToSave).map { }
            } else {
                updateActivityUseCase(activityToSave)
            }

            result.fold(
                onSuccess = {
                    _formUiState.update { it.copy(isSaving = false, isSavedSuccess = true) }
                },
                onFailure = { error ->
                    _formUiState.update {
                        it.copy(
                            isSaving = false,
                            generalError = error.localizedMessage ?: "حدث خطأ أثناء حفظ النشاط"
                        )
                    }
                }
            )
        }
    }

    fun resetFormSuccess() {
        _formUiState.update { it.copy(isSavedSuccess = false) }
    }

    /**
     * تحديد النشاط كمنجز مع التقرير الأدبي الإلزامي
     */
    fun markAsDoneWithReport(id: String, report: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val result = markActivityDoneWithReportUseCase(id, report)
            if (result.isSuccess) {
                loadActivityDetail(id)
                onComplete()
            }
        }
    }

    /**
     * تعديل التقرير الأدبي للنشاط المنجز
     */
    fun updateReport(id: String, report: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val result = updateActivityReportUseCase(id, report)
            if (result.isSuccess) {
                loadActivityDetail(id)
                onComplete()
            }
        }
    }

    /**
     * إلغاء النشاط
     */
    fun cancelActivity(id: String) {
        viewModelScope.launch {
            val result = cancelActivityUseCase(id)
            if (result.isSuccess) {
                loadActivityDetail(id)
            }
        }
    }

    /**
     * حذف النشاط نهائياً
     */
    fun deleteActivity(id: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val result = deleteActivityUseCase(id)
            if (result.isSuccess) {
                _detailUiState.value = ActivityDetailUiState.Deleted
                onComplete()
            }
        }
    }

    /**
     * تجميع الأنشطة ذكياً تحت العناوين: اليوم، غداً، هذا الأسبوع، قادم، سابق
     */
    private fun groupActivitiesSmartly(activities: List<Activity>): Map<String, List<Activity>> {
        val grouped = LinkedHashMap<String, MutableList<Activity>>()

        val nowCal = Calendar.getInstance()
        val todayStart = (nowCal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val todayEnd = (nowCal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        val tomorrowEnd = todayEnd + (24 * 60 * 60 * 1000L)
        val endOfWeek = todayEnd + (7 * 24 * 60 * 60 * 1000L)

        val keyToday = "اليوم"
        val keyTomorrow = "غدًا"
        val keyThisWeek = "هذا الأسبوع"
        val keyUpcoming = "قادم"
        val keyPast = "سابق"

        for (activity in activities) {
            val header = when {
                activity.startDate in todayStart..todayEnd || (activity.startDate <= todayStart && activity.endDate >= todayStart) -> keyToday
                activity.startDate in (todayEnd + 1)..tomorrowEnd -> keyTomorrow
                activity.startDate in (tomorrowEnd + 1)..endOfWeek -> keyThisWeek
                activity.startDate > endOfWeek -> keyUpcoming
                activity.endDate < todayStart -> keyPast
                else -> keyUpcoming
            }

            grouped.getOrPut(header) { mutableListOf() }.add(activity)
        }

        return grouped
    }

    class Factory(
        private val repository: ActivityRepository,
        private val observeActivitiesUseCase: ObserveActivitiesUseCase,
        private val getActivityByIdUseCase: GetActivityByIdUseCase,
        private val addActivityUseCase: AddActivityUseCase,
        private val updateActivityUseCase: UpdateActivityUseCase,
        private val markActivityDoneUseCase: MarkActivityDoneUseCase,
        private val markActivityDoneWithReportUseCase: MarkActivityDoneWithReportUseCase,
        private val updateActivityReportUseCase: UpdateActivityReportUseCase,
        private val cancelActivityUseCase: CancelActivityUseCase,
        private val deleteActivityUseCase: DeleteActivityUseCase,
        private val attendanceRepository: AttendanceRepository? = null,
        private val getAttendanceForActivityUseCase: GetAttendanceForActivityUseCase? = null,
        private val getAttendanceRowsForActivityUseCase: GetAttendanceRowsForActivityUseCase? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ActivityViewModel(
                repository,
                observeActivitiesUseCase,
                getActivityByIdUseCase,
                addActivityUseCase,
                updateActivityUseCase,
                markActivityDoneUseCase,
                markActivityDoneWithReportUseCase,
                updateActivityReportUseCase,
                cancelActivityUseCase,
                deleteActivityUseCase,
                attendanceRepository,
                getAttendanceForActivityUseCase,
                getAttendanceRowsForActivityUseCase
            ) as T
        }
    }
}
