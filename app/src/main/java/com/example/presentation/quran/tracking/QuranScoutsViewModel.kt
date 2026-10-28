package com.example.presentation.quran.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.quran.QuranSchool
import com.example.domain.quran.ScoutQuranEnrollment
import com.example.domain.quran.SelectedPeriod
import com.example.domain.quran.usecases.DeleteEnrollmentUseCase
import com.example.domain.quran.usecases.ObserveActiveQuranSchoolsUseCase
import com.example.domain.quran.usecases.ObserveAllEnrollmentsUseCase
import com.example.domain.quran.usecases.SaveEnrollmentUseCase
import com.example.domain.scout.Scout
import com.example.domain.scout.ScoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

/**
 * ViewModel لإدارة متابعة حفظ القرآن وتسجيل الأشبال بالمدارس القرآنية
 */
class QuranScoutsViewModel(
    private val scoutRepository: ScoutRepository,
    private val observeAllEnrollmentsUseCase: ObserveAllEnrollmentsUseCase,
    private val observeActiveQuranSchoolsUseCase: ObserveActiveQuranSchoolsUseCase,
    private val saveEnrollmentUseCase: SaveEnrollmentUseCase,
    private val deleteEnrollmentUseCase: DeleteEnrollmentUseCase
) : ViewModel() {

    private val selectedFilterFlow = MutableStateFlow(QuranScoutsFilter.ALL)
    private val userMessageFlow = MutableStateFlow<String?>(null)

    private val arabicCollator = Collator.getInstance(Locale("ar")).apply {
        strength = Collator.PRIMARY
    }

    val uiState: StateFlow<QuranScoutsUiState> = combine(
        scoutRepository.observeScouts(),
        observeAllEnrollmentsUseCase(),
        observeActiveQuranSchoolsUseCase(),
        selectedFilterFlow,
        userMessageFlow
    ) { scouts, enrollments, schools, filter, message ->
        val enrollmentMap = enrollments.associateBy { it.scoutId }

        // ترتيب الأشبال أبجدياً بالاسم ثم اللقب
        val sortedScouts = scouts.sortedWith(
            Comparator { s1, s2 ->
                val nameComp = arabicCollator.compare(s1.name, s2.name)
                if (nameComp != 0) nameComp else arabicCollator.compare(s1.surname, s2.surname)
            }
        )

        val allItems = sortedScouts.map { scout ->
            QuranScoutItem(
                scout = scout,
                enrollment = enrollmentMap[scout.id]
            )
        }

        val filteredItems = when (filter) {
            QuranScoutsFilter.ALL -> allItems
            QuranScoutsFilter.ENROLLED -> allItems.filter { it.isEnrolled }
            QuranScoutsFilter.NOT_ENROLLED -> allItems.filter { !it.isEnrolled }
        }

        val sortedActiveSchools = schools
            .filter { !it.archived }
            .sortedWith { s1, s2 -> arabicCollator.compare(s1.name, s2.name) }

        val enrolledCount = allItems.count { it.isEnrolled }

        QuranScoutsUiState.Success(
            allScoutItems = allItems,
            filteredScoutItems = filteredItems,
            activeSchools = sortedActiveSchools,
            selectedFilter = filter,
            enrolledCount = enrolledCount,
            totalScoutsCount = allItems.size,
            userMessage = message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = QuranScoutsUiState.Loading
    )

    fun setFilter(filter: QuranScoutsFilter) {
        selectedFilterFlow.value = filter
    }

    fun clearUserMessage() {
        userMessageFlow.value = null
    }

    /**
     * حفظ أو تحديث تسجيل الشبل
     */
    fun saveEnrollment(
        scoutId: String,
        school: QuranSchool,
        currentMemorizedParts: Int,
        memorizationNote: String?,
        studyDays: List<String>,
        studyPeriods: List<SelectedPeriod>,
        generalNotes: String?,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val enrollment = ScoutQuranEnrollment(
                scoutId = scoutId,
                schoolId = school.id,
                schoolName = school.name,
                teacherName = school.teacherName,
                teacherPhone = school.teacherPhone,
                currentMemorizedParts = currentMemorizedParts,
                memorizationNote = memorizationNote?.trim()?.takeIf { it.isNotBlank() },
                studyDays = studyDays,
                studyPeriods = studyPeriods,
                generalNotes = generalNotes?.trim()?.takeIf { it.isNotBlank() }
            )

            val result = saveEnrollmentUseCase(enrollment)
            if (result.isSuccess) {
                userMessageFlow.value = "تم حفظ بيانات تسجيل الشبل بنجاح"
                onSuccess()
            } else {
                userMessageFlow.value = "حدث خطأ أثناء حفظ التسجيل: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    /**
     * إلغاء تسجيل الشبل في المدرسة القرآنية
     */
    fun deleteEnrollment(scoutId: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val result = deleteEnrollmentUseCase(scoutId)
            if (result.isSuccess) {
                userMessageFlow.value = "تم إلغاء تسجيل الشبل"
                onSuccess()
            } else {
                userMessageFlow.value = "تعذر إلغاء التسجيل: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    companion object {
        fun provideFactory(
            scoutRepository: ScoutRepository,
            observeAllEnrollmentsUseCase: ObserveAllEnrollmentsUseCase,
            observeActiveQuranSchoolsUseCase: ObserveActiveQuranSchoolsUseCase,
            saveEnrollmentUseCase: SaveEnrollmentUseCase,
            deleteEnrollmentUseCase: DeleteEnrollmentUseCase
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return QuranScoutsViewModel(
                    scoutRepository = scoutRepository,
                    observeAllEnrollmentsUseCase = observeAllEnrollmentsUseCase,
                    observeActiveQuranSchoolsUseCase = observeActiveQuranSchoolsUseCase,
                    saveEnrollmentUseCase = saveEnrollmentUseCase,
                    deleteEnrollmentUseCase = deleteEnrollmentUseCase
                ) as T
            }
        }
    }
}
