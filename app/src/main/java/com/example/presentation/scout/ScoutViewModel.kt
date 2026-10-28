package com.example.presentation.scout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.domain.activity.Activity
import com.example.domain.activity.ActivityRepository
import com.example.domain.attendance.ScoutStatistics
import com.example.domain.attendance.usecases.ComputeScoutStatisticsUseCase
import com.example.domain.scout.DuplicateScoutException
import com.example.domain.scout.Scout
import com.example.domain.scout.ScoutRepository
import com.example.domain.scout.usecases.AddScoutUseCase
import com.example.domain.scout.usecases.ArchiveScoutUseCase
import com.example.domain.scout.usecases.DeleteScoutUseCase
import com.example.domain.scout.usecases.UpdateScoutUseCase
import com.example.domain.progress.BadgeLevel
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * نموذج العرض لإدارة الأشبال (Scout ViewModel)
 * يدير تدفق البيانات المباشر، البحث، الفرز، إجراءات CRUD الكاملة، وإحصائيات الحضور.
 */
class ScoutViewModel(
    private val repository: ScoutRepository,
    private val addScoutUseCase: AddScoutUseCase,
    private val updateScoutUseCase: UpdateScoutUseCase,
    private val archiveScoutUseCase: ArchiveScoutUseCase,
    private val deleteScoutUseCase: DeleteScoutUseCase,
    private val computeScoutStatisticsUseCase: ComputeScoutStatisticsUseCase? = null,
    private val activityRepository: ActivityRepository? = null,
    private val observeBadgeProgressForScoutUseCase: com.example.domain.progress.usecases.ObserveBadgeProgressForScoutUseCase? = null,
    private val setRequirementCompletedUseCase: com.example.domain.progress.usecases.SetRequirementCompletedUseCase? = null,
    private val grantBadgeUseCase: com.example.domain.progress.usecases.GrantBadgeUseCase? = null,
    private val revokeBadgeUseCase: com.example.domain.progress.usecases.RevokeBadgeUseCase? = null,
    private val authRepository: com.example.domain.auth.AuthRepository? = null,
    private val observeHobbyBadgeProgressForScoutUseCase: com.example.domain.hobby.usecases.ObserveHobbyBadgeProgressForScoutUseCase? = null,
    private val financeRepository: com.example.domain.finance.FinanceRepository? = null
) : ViewModel() {

    private val _scoutStats = MutableStateFlow<ScoutStatistics?>(null)
    val scoutStats: StateFlow<ScoutStatistics?> = _scoutStats.asStateFlow()

    private val _scoutDebt = MutableStateFlow<com.example.domain.finance.ScoutDebt?>(null)
    val scoutDebt: StateFlow<com.example.domain.finance.ScoutDebt?> = _scoutDebt.asStateFlow()

    private val _scoutSubscriptions = MutableStateFlow<List<com.example.domain.finance.FinanceSubscription>>(emptyList())
    val scoutSubscriptions: StateFlow<List<com.example.domain.finance.FinanceSubscription>> = _scoutSubscriptions.asStateFlow()

    private val _badgeProgressList = MutableStateFlow<List<com.example.domain.progress.BadgeProgress>>(emptyList())
    val badgeProgressList: StateFlow<List<com.example.domain.progress.BadgeProgress>> = _badgeProgressList.asStateFlow()

    private val _hobbyBadgeProgressList = MutableStateFlow<List<com.example.domain.hobby.HobbyBadgeProgress>>(emptyList())
    val hobbyBadgeProgressList: StateFlow<List<com.example.domain.hobby.HobbyBadgeProgress>> = _hobbyBadgeProgressList.asStateFlow()

    private val _progressMessage = MutableStateFlow<String?>(null)
    val progressMessage: StateFlow<String?> = _progressMessage.asStateFlow()

    private val _activities = MutableStateFlow<List<Activity>>(emptyList())
    val activities: StateFlow<List<Activity>> = _activities.asStateFlow()

    private val _grantedBadgesByScout = MutableStateFlow<Map<String, Set<BadgeLevel>>>(emptyMap())
    val grantedBadgesByScout: StateFlow<Map<String, Set<BadgeLevel>>> = _grantedBadgesByScout.asStateFlow()

    private val _inProgressBadgesByScout = MutableStateFlow<Map<String, Set<BadgeLevel>>>(emptyMap())
    val inProgressBadgesByScout: StateFlow<Map<String, Set<BadgeLevel>>> = _inProgressBadgesByScout.asStateFlow()

    init {
        if (activityRepository != null) {
            viewModelScope.launch {
                activityRepository.observeActivities().collect {
                    _activities.value = it
                }
            }
        }

        // تحميل ومراقبة الشارات الممنوحة وقيد الإنجاز
        try {
            val db = FirebaseFirestore.getInstance()
            var reqsMap = emptyMap<String, BadgeLevel>()
            var scoutCompletedMap = emptyMap<String, Set<String>>()

            fun recomputeInProgressBadges() {
                val granted = _grantedBadgesByScout.value
                val inProgress = mutableMapOf<String, MutableSet<BadgeLevel>>()
                scoutCompletedMap.forEach { (scoutId, completedReqIds) ->
                    val scoutGranted = granted[scoutId].orEmpty()
                    for (reqId in completedReqIds) {
                        val level = reqsMap[reqId] ?: continue
                        if (!scoutGranted.contains(level)) {
                            inProgress.getOrPut(scoutId) { mutableSetOf() }.add(level)
                        }
                    }
                }
                _inProgressBadgesByScout.value = inProgress
            }

            // مراقبة الشارات الممنوحة النشطة فقط (revokedAt == null)
            db.collection("scout_badges").addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val map = mutableMapOf<String, MutableSet<BadgeLevel>>()
                for (doc in snapshot.documents) {
                    val revokedAt = doc.getLong("revokedAt")
                    if (revokedAt == null) {
                        val scoutId = doc.getString("scoutId") ?: continue
                        val levelKey = doc.getString("badgeLevel") ?: continue
                        val level = BadgeLevel.fromKey(levelKey) ?: continue
                        map.getOrPut(scoutId) { mutableSetOf() }.add(level)
                    }
                }
                _grantedBadgesByScout.value = map
                recomputeInProgressBadges()
            }

            // مراقبة كتالوج المتطلبات لربط معرف المتطلب بمستوى الشارة
            db.collection("progress_requirements").addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val newReqs = mutableMapOf<String, BadgeLevel>()
                for (doc in snapshot.documents) {
                    val archived = doc.getBoolean("archived") ?: false
                    if (!archived) {
                        val levelKey = doc.getString("badgeLevel") ?: continue
                        val level = BadgeLevel.fromKey(levelKey) ?: continue
                        newReqs[doc.id] = level
                    }
                }
                reqsMap = newReqs
                recomputeInProgressBadges()
            }

            // مراقبة سجل إنجاز المتطلبات للأشبال (completed == true)
            db.collection("scout_progress").addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val map = mutableMapOf<String, MutableSet<String>>()
                for (doc in snapshot.documents) {
                    val completed = doc.getBoolean("completed") ?: false
                    if (completed) {
                        val scoutId = doc.getString("scoutId") ?: continue
                        val reqId = doc.getString("requirementId") ?: continue
                        map.getOrPut(scoutId) { mutableSetOf() }.add(reqId)
                    }
                }
                scoutCompletedMap = map
                recomputeInProgressBadges()
            }
        } catch (_: Exception) {}
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(ScoutFilter.ACTIVE)
    val selectedFilter: StateFlow<ScoutFilter> = _selectedFilter.asStateFlow()

    // تدفق قائمة الأشبال المجمعة من المستودع مع تطبيق البحث والفلتر والشارات
    val listUiState: StateFlow<ScoutListUiState> = combine(
        repository.observeScouts(),
        _searchQuery,
        _selectedFilter,
        _grantedBadgesByScout,
        _inProgressBadgesByScout
    ) { allScouts, query, filter, grantedMap, inProgressMap ->
        val filtered = allScouts.filter { scout ->
            val matchesFilter = when (filter) {
                ScoutFilter.ALL -> true
                ScoutFilter.ACTIVE -> scout.isActive
                ScoutFilter.ARCHIVED -> !scout.isActive
            }

            val q = query.trim().lowercase()
            val matchesQuery = if (q.isBlank()) true else {
                scout.fullName.lowercase().contains(q) ||
                        scout.name.lowercase().contains(q) ||
                        scout.surname.lowercase().contains(q) ||
                        scout.guardianName.lowercase().contains(q) ||
                        scout.guardianPhone.contains(q) ||
                        scout.additionalPhones.any { it.contains(q) } ||
                        scout.school.lowercase().contains(q)
            }

            matchesFilter && matchesQuery
        }

        val activeCount = allScouts.count { it.isActive }

        ScoutListUiState.Success(
            allScouts = allScouts,
            filteredScouts = filtered,
            activeCount = activeCount,
            totalCount = allScouts.size,
            searchQuery = query,
            filter = filter,
            grantedBadgesByScout = grantedMap,
            inProgressBadgesByScout = inProgressMap
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ScoutListUiState.Loading
    )

    // حالة حوار اختيار رقم الهاتف
    private val _phonePickerScout = MutableStateFlow<Scout?>(null)
    val phonePickerScout: StateFlow<Scout?> = _phonePickerScout.asStateFlow()

    fun onRequestPhonePicker(scout: Scout) {
        _phonePickerScout.value = scout
    }

    fun onDismissPhonePicker() {
        _phonePickerScout.value = null
    }

    // حالة تفاصيل الشبل
    private val _detailUiState = MutableStateFlow<ScoutDetailUiState>(ScoutDetailUiState.Loading)
    val detailUiState: StateFlow<ScoutDetailUiState> = _detailUiState.asStateFlow()

    // حالة استمارة إضافة / تعديل الشبل
    private val _formUiState = MutableStateFlow(ScoutFormUiState())
    val formUiState: StateFlow<ScoutFormUiState> = _formUiState.asStateFlow()

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun onFilterSelected(filter: ScoutFilter) {
        _selectedFilter.value = filter
    }

    /**
     * تحميل تفاصيل شبل محدد مع معالجة شاملة للأخطاء والاستثناءات
     */
    fun loadScoutDetail(id: String) {
        _detailUiState.value = ScoutDetailUiState.Loading
        _scoutStats.value = null
        viewModelScope.launch {
            try {
                val scout = repository.getScoutById(id)
                if (scout != null) {
                    _detailUiState.value = ScoutDetailUiState.Success(scout)
                } else {
                    _detailUiState.value = ScoutDetailUiState.Error("تعذر العثور على بيانات الشبل")
                }
            } catch (e: Exception) {
                _detailUiState.value = ScoutDetailUiState.Error(e.localizedMessage ?: "حدث خطأ أثناء تحميل بيانات الشبل")
            }
        }
        if (computeScoutStatisticsUseCase != null) {
            viewModelScope.launch {
                try {
                    computeScoutStatisticsUseCase(id)
                        .catch { e ->
                            _scoutStats.value = ScoutStatistics(
                                totalActivities = 0,
                                presentCount = 0,
                                lateCount = 0,
                                veryLateCount = 0,
                                absentExcusedCount = 0,
                                absentUnexcusedCount = 0,
                                attendancePercentage = 0.0,
                                totalPoints = 0,
                                missedActivities = emptyList()
                            )
                        }
                        .collect { stats ->
                            _scoutStats.value = stats
                        }
                } catch (e: Exception) {
                    _scoutStats.value = ScoutStatistics(
                        totalActivities = 0,
                        presentCount = 0,
                        lateCount = 0,
                        veryLateCount = 0,
                        absentExcusedCount = 0,
                        absentUnexcusedCount = 0,
                        attendancePercentage = 0.0,
                        totalPoints = 0,
                        missedActivities = emptyList()
                    )
                }
            }
        }
        if (observeBadgeProgressForScoutUseCase != null) {
            viewModelScope.launch {
                try {
                    observeBadgeProgressForScoutUseCase(id)
                        .catch { }
                        .collect { progressList ->
                            _badgeProgressList.value = progressList
                        }
                } catch (_: Exception) {}
            }
        }
        if (observeHobbyBadgeProgressForScoutUseCase != null) {
            viewModelScope.launch {
                try {
                    observeHobbyBadgeProgressForScoutUseCase(id)
                        .catch { }
                        .collect { progressList ->
                            _hobbyBadgeProgressList.value = progressList
                        }
                } catch (_: Exception) {}
            }
        }
        if (financeRepository != null) {
            viewModelScope.launch {
                try {
                    financeRepository.observeSubscriptionsForScout(id)
                        .catch { }
                        .collect { subs ->
                            _scoutSubscriptions.value = subs
                        }
                } catch (_: Exception) {}
            }
            viewModelScope.launch {
                try {
                    financeRepository.observeScoutDebts()
                        .catch { }
                        .collect { debts ->
                            _scoutDebt.value = debts.firstOrNull { it.scoutId == id }
                        }
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * تهيئة الاستمارة للإضافة أو التعديل
     */
    fun initForm(scoutId: String?) {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        if (scoutId.isNullOrBlank()) {
            _formUiState.value = ScoutFormUiState(
                joinYear = currentYear,
                guardianRelation = Scout.RELATION_FATHER,
                additionalPhones = emptyList()
            )
        } else {
            _formUiState.value = ScoutFormUiState(isLoading = true)
            viewModelScope.launch {
                val scout = repository.getScoutById(scoutId)
                if (scout != null) {
                    _formUiState.value = ScoutFormUiState(
                        id = scout.id,
                        name = scout.name,
                        surname = scout.surname,
                        birthDate = scout.birthDate,
                        guardianName = scout.guardianName,
                        guardianPhone = scout.guardianPhone,
                        additionalPhones = scout.additionalPhones,
                        guardianRelation = scout.guardianRelation,
                        address = scout.address,
                        school = scout.school,
                        joinYear = if (scout.joinYear in 2000..currentYear) scout.joinYear else currentYear,
                        status = scout.status,
                        notes = scout.notes,
                        isLoading = false
                    )
                } else {
                    _formUiState.value = ScoutFormUiState(
                        isLoading = false,
                        generalError = "تعذر تحميل بيانات الشبل"
                    )
                }
            }
        }
    }

    fun onFormNameChanged(name: String) {
        _formUiState.update { it.copy(name = name, nameError = null, generalError = null) }
    }

    fun onFormSurnameChanged(surname: String) {
        _formUiState.update { it.copy(surname = surname, surnameError = null, generalError = null) }
    }

    fun onFormBirthDateChanged(dateMillis: Long) {
        _formUiState.update { it.copy(birthDate = dateMillis, birthDateError = null, generalError = null) }
    }

    fun onFormGuardianNameChanged(name: String) {
        _formUiState.update { it.copy(guardianName = name) }
    }

    fun onFormGuardianPhoneChanged(phone: String) {
        _formUiState.update { it.copy(guardianPhone = phone, guardianPhoneError = null, generalError = null) }
    }

    fun onAddAdditionalPhone() {
        _formUiState.update { it.copy(additionalPhones = it.additionalPhones + "", generalError = null) }
    }

    fun onRemoveAdditionalPhone(index: Int) {
        _formUiState.update {
            val list = it.additionalPhones.toMutableList()
            if (index in list.indices) {
                list.removeAt(index)
            }
            it.copy(additionalPhones = list, generalError = null)
        }
    }

    fun onAdditionalPhoneChanged(index: Int, phone: String) {
        _formUiState.update {
            val list = it.additionalPhones.toMutableList()
            if (index in list.indices) {
                list[index] = phone
            }
            it.copy(additionalPhones = list, generalError = null)
        }
    }

    fun onFormGuardianRelationChanged(relation: String) {
        _formUiState.update { it.copy(guardianRelation = relation, guardianRelationError = null, generalError = null) }
    }

    fun onFormAddressChanged(address: String) {
        _formUiState.update { it.copy(address = address) }
    }

    fun onFormSchoolChanged(school: String) {
        _formUiState.update { it.copy(school = school) }
    }

    fun onFormJoinYearChanged(year: Int) {
        _formUiState.update { it.copy(joinYear = year, joinYearError = null, generalError = null) }
    }

    fun onFormNotesChanged(notes: String) {
        _formUiState.update { it.copy(notes = notes) }
    }

    /**
     * حفظ استمارة الشبل مع التحقق الدقيق وفق الشروط الجديدة
     */
    fun saveScout() {
        val state = _formUiState.value
        val name = state.name.trim()
        val surname = state.surname.trim()
        val birthDate = state.birthDate
        val phone = state.guardianPhone.trim().replace("+", "").replace("-", "").replace(" ", "")
        val relation = state.guardianRelation
        val joinYear = state.joinYear

        var hasError = false
        var nameErr: Int? = null
        var surnameErr: Int? = null
        var birthErr: Int? = null
        var phoneErr: Int? = null
        var relationErr: Int? = null
        var joinYearErr: Int? = null

        // التحقق من الاسم
        if (name.isBlank()) {
            nameErr = R.string.error_scout_name_required
            hasError = true
        } else if (name.length < 2) {
            nameErr = R.string.error_name_short
            hasError = true
        }

        // التحقق من اللقب
        if (surname.isBlank()) {
            surnameErr = R.string.error_surname_required
            hasError = true
        } else if (surname.length < 2) {
            surnameErr = R.string.error_surname_short
            hasError = true
        }

        // التحقق من تاريخ الميلاد
        val nowCal = Calendar.getInstance()
        val minCal = Calendar.getInstance().apply { set(2000, Calendar.JANUARY, 1) }

        if (birthDate <= 0L) {
            birthErr = R.string.error_birth_date_required
            hasError = true
        } else {
            val birthCal = Calendar.getInstance().apply { timeInMillis = birthDate }
            if (birthCal.after(nowCal) || birthCal.before(minCal)) {
                birthErr = R.string.error_birth_date_invalid
                hasError = true
            }
        }

        // التحقق من هاتف الولي (9-10 أرقام)
        if (phone.isBlank()) {
            phoneErr = R.string.error_guardian_phone_required
            hasError = true
        } else if (phone.length !in 9..10 || !phone.all { it.isDigit() }) {
            phoneErr = R.string.error_guardian_phone_invalid
            hasError = true
        }

        // التحقق من الأرقام الإضافية (9-10 أرقام أو فارغة)
        for ((idx, extra) in state.additionalPhones.withIndex()) {
            val clean = extra.trim().replace("+", "").replace("-", "").replace(" ", "")
            if (clean.isNotBlank()) {
                if (clean.length !in 9..10 || !clean.all { it.isDigit() }) {
                    _formUiState.update {
                        it.copy(
                            generalError = "الرقم الإضافي (${idx + 1}) غير صحيح: يجب أن يتكون من 9 إلى 10 أرقام"
                        )
                    }
                    return
                }
            }
        }

        // التحقق من صلة القرابة
        if (relation.isBlank() || relation !in Scout.GUARDIAN_RELATIONS) {
            relationErr = R.string.error_guardian_relation_required
            hasError = true
        }

        // التحقق من سنة الالتحاق
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        if (joinYear < 2000 || joinYear > currentYear) {
            joinYearErr = R.string.error_join_year_required
            hasError = true
        }

        if (hasError) {
            _formUiState.update {
                it.copy(
                    nameError = nameErr,
                    surnameError = surnameErr,
                    birthDateError = birthErr,
                    guardianPhoneError = phoneErr,
                    guardianRelationError = relationErr,
                    joinYearError = joinYearErr
                )
            }
            return
        }

        _formUiState.update { it.copy(isSaving = true, generalError = null) }

        val cleanedAdditional = state.additionalPhones
            .map { it.trim().replace("+", "").replace("-", "").replace(" ", "") }
            .filter { it.isNotBlank() }

        viewModelScope.launch {
            val scoutToSave = Scout(
                id = state.id,
                name = name,
                surname = surname,
                birthDate = birthDate,
                guardianName = state.guardianName.trim(),
                guardianPhone = phone,
                additionalPhones = cleanedAdditional,
                guardianRelation = relation,
                address = state.address.trim(),
                school = state.school.trim(),
                joinYear = joinYear,
                status = state.status,
                notes = state.notes.trim()
            )

            val result = if (state.id.isBlank()) {
                addScoutUseCase(scoutToSave).map { }
            } else {
                updateScoutUseCase(scoutToSave)
            }

            result.fold(
                onSuccess = {
                    _formUiState.update { it.copy(isSaving = false, isSavedSuccess = true) }
                },
                onFailure = { error ->
                    val errorMsg = if (error is DuplicateScoutException) {
                        "هذا الشبل مسجّل بالفعل: ${error.existingFullName}"
                    } else {
                        error.localizedMessage ?: "حدث خطأ أثناء حفظ البيانات"
                    }
                    _formUiState.update {
                        it.copy(
                            isSaving = false,
                            generalError = errorMsg
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
     * أرشفة شبل
     */
    fun archiveScout(id: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            archiveScoutUseCase(id)
            onComplete()
        }
    }

    /**
     * إلغاء أرشفة شبل وإعادته للحالة النشطة
     */
    fun unarchiveScout(id: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val scout = repository.getScoutById(id)
            if (scout != null) {
                updateScoutUseCase(scout.copy(status = Scout.STATUS_ACTIVE))
            }
            onComplete()
        }
    }

    /**
     * حذف شبل نهائياً
     */
    fun deleteScout(id: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val result = deleteScoutUseCase(id)
            if (result.isSuccess) {
                _detailUiState.value = ScoutDetailUiState.Deleted
                onComplete()
            }
        }
    }

    fun clearProgressMessage() {
        _progressMessage.value = null
    }

    fun toggleRequirementCompleted(
        scoutId: String,
        requirementId: String,
        currentCompleted: Boolean,
        onSuccess: (isNowCompleted: Boolean) -> Unit = {}
    ) {
        val useCase = setRequirementCompletedUseCase ?: return
        val user = authRepository?.currentUser
        val leaderName = user?.displayName ?: "قائد"
        viewModelScope.launch {
            val newCompleted = !currentCompleted
            val result = useCase(
                scoutId = scoutId,
                requirementId = requirementId,
                completed = newCompleted,
                completedByName = leaderName
            )
            if (result.isSuccess) {
                onSuccess(newCompleted)
            } else {
                _progressMessage.value = result.exceptionOrNull()?.localizedMessage ?: "حدث خطأ أثناء حفظ الإنجاز"
            }
        }
    }

    fun grantBadge(
        scoutId: String,
        level: com.example.domain.progress.BadgeLevel,
        notes: String = "",
        onSuccess: () -> Unit = {}
    ) {
        val useCase = grantBadgeUseCase ?: return
        val user = authRepository?.currentUser
        val leaderName = user?.displayName ?: "قائد"
        viewModelScope.launch {
            val result = useCase(
                scoutId = scoutId,
                level = level,
                notes = notes,
                grantedByName = leaderName
            )
            if (result.isSuccess) {
                onSuccess()
            } else {
                _progressMessage.value = result.exceptionOrNull()?.localizedMessage ?: "حدث خطأ أثناء منح الشارة"
            }
        }
    }

    fun revokeBadge(
        scoutId: String,
        level: com.example.domain.progress.BadgeLevel,
        onSuccess: () -> Unit = {}
    ) {
        val useCase = revokeBadgeUseCase ?: return
        val user = authRepository?.currentUser
        val leaderName = user?.displayName ?: "قائد"
        viewModelScope.launch {
            val result = useCase(
                scoutId = scoutId,
                level = level,
                revokedByName = leaderName
            )
            if (result.isSuccess) {
                onSuccess()
            } else {
                _progressMessage.value = result.exceptionOrNull()?.localizedMessage ?: "حدث خطأ أثناء سحب الشارة"
            }
        }
    }

    class Factory(
        private val repository: ScoutRepository,
        private val addScoutUseCase: AddScoutUseCase,
        private val updateScoutUseCase: UpdateScoutUseCase,
        private val archiveScoutUseCase: ArchiveScoutUseCase,
        private val deleteScoutUseCase: DeleteScoutUseCase,
        private val computeScoutStatisticsUseCase: ComputeScoutStatisticsUseCase? = null,
        private val activityRepository: ActivityRepository? = null,
        private val observeBadgeProgressForScoutUseCase: com.example.domain.progress.usecases.ObserveBadgeProgressForScoutUseCase? = null,
        private val setRequirementCompletedUseCase: com.example.domain.progress.usecases.SetRequirementCompletedUseCase? = null,
        private val grantBadgeUseCase: com.example.domain.progress.usecases.GrantBadgeUseCase? = null,
        private val revokeBadgeUseCase: com.example.domain.progress.usecases.RevokeBadgeUseCase? = null,
        private val authRepository: com.example.domain.auth.AuthRepository? = null,
        private val observeHobbyBadgeProgressForScoutUseCase: com.example.domain.hobby.usecases.ObserveHobbyBadgeProgressForScoutUseCase? = null,
        private val financeRepository: com.example.domain.finance.FinanceRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ScoutViewModel(
                repository,
                addScoutUseCase,
                updateScoutUseCase,
                archiveScoutUseCase,
                deleteScoutUseCase,
                computeScoutStatisticsUseCase,
                activityRepository,
                observeBadgeProgressForScoutUseCase,
                setRequirementCompletedUseCase,
                grantBadgeUseCase,
                revokeBadgeUseCase,
                authRepository,
                observeHobbyBadgeProgressForScoutUseCase,
                financeRepository
            ) as T
        }
    }
}

typealias ScoutListViewModel = ScoutViewModel

