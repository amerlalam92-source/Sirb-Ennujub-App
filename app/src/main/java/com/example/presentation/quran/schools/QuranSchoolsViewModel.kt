package com.example.presentation.quran.schools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.quran.QuranSchool
import com.example.domain.quran.usecases.AddQuranSchoolUseCase
import com.example.domain.quran.usecases.ArchiveQuranSchoolUseCase
import com.example.domain.quran.usecases.DeleteQuranSchoolUseCase
import com.example.domain.quran.usecases.ObserveAllEnrollmentsUseCase
import com.example.domain.quran.usecases.ObserveAllQuranSchoolsUseCase
import com.example.domain.quran.usecases.UnarchiveQuranSchoolUseCase
import com.example.domain.quran.usecases.UpdateQuranSchoolUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

/**
 * ViewModel لإدارة دليل المدارس القرآنية
 */
class QuranSchoolsViewModel(
    observeAllSchoolsUseCase: ObserveAllQuranSchoolsUseCase,
    observeAllEnrollmentsUseCase: ObserveAllEnrollmentsUseCase,
    private val addQuranSchoolUseCase: AddQuranSchoolUseCase,
    private val updateQuranSchoolUseCase: UpdateQuranSchoolUseCase,
    private val archiveQuranSchoolUseCase: ArchiveQuranSchoolUseCase,
    private val unarchiveQuranSchoolUseCase: UnarchiveQuranSchoolUseCase,
    private val deleteQuranSchoolUseCase: DeleteQuranSchoolUseCase
) : ViewModel() {

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val collator: Collator = Collator.getInstance(Locale("ar")).apply {
        strength = Collator.PRIMARY
    }

    val uiState: StateFlow<QuranSchoolsUiState> = combine(
        observeAllSchoolsUseCase(),
        observeAllEnrollmentsUseCase(),
        _userMessage
    ) { schools, enrollments, msg ->
        val items = schools.map { school ->
            val count = enrollments.count { it.schoolId == school.id }
            QuranSchoolItem(school = school, enrolledScoutsCount = count)
        }.sortedWith { a, b ->
            collator.compare(a.school.name, b.school.name)
        }

        val activeCount = items.count { it.school.isActive() }

        QuranSchoolsUiState.Success(
            schools = items,
            activeCount = activeCount,
            userMessage = msg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = QuranSchoolsUiState.Loading
    )

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun addSchool(
        name: String,
        teacherName: String,
        teacherPhone: String,
        address: String?,
        notes: String?,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            val newSchool = QuranSchool(
                name = name.trim(),
                teacherName = teacherName.trim(),
                teacherPhone = teacherPhone.trim(),
                address = address?.trim()?.ifBlank { null },
                notes = notes?.trim()?.ifBlank { null }
            )
            val result = addQuranSchoolUseCase(newSchool)
            if (result.isSuccess) {
                _userMessage.value = "تمت إضافة المدرسة القرآنية بنجاح"
                onDone()
            } else {
                _userMessage.value = "تعذر إضافة المدرسة: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun updateSchool(
        school: QuranSchool,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            val result = updateQuranSchoolUseCase(school)
            if (result.isSuccess) {
                _userMessage.value = "تم تعديل بيانات المدرسة بنجاح"
                onDone()
            } else {
                _userMessage.value = "تعذر تعديل المدرسة: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun archiveSchool(id: String) {
        viewModelScope.launch {
            val result = archiveQuranSchoolUseCase(id)
            if (result.isSuccess) {
                _userMessage.value = "تمت أرشفة المدرسة"
            } else {
                _userMessage.value = "تعذر أرشفة المدرسة: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun unarchiveSchool(id: String) {
        viewModelScope.launch {
            val result = unarchiveQuranSchoolUseCase(id)
            if (result.isSuccess) {
                _userMessage.value = "تم إلغاء أرشفة المدرسة"
            } else {
                _userMessage.value = "تعذر إلغاء الأرشفة: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun deleteSchool(
        id: String,
        onCannotDelete: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = deleteQuranSchoolUseCase(id)
            if (result.isSuccess) {
                _userMessage.value = "تم حذف المدرسة القرآنية نهائياً"
            } else {
                val error = result.exceptionOrNull()
                if (error is IllegalStateException) {
                    onCannotDelete(error.message ?: "لا يمكن حذف المدرسة لأن هناك أشبالاً مسجلين بها.")
                } else {
                    _userMessage.value = "تعذر حذف المدرسة: ${error?.message}"
                }
            }
        }
    }

    class Factory(
        private val observeAllSchoolsUseCase: ObserveAllQuranSchoolsUseCase,
        private val observeAllEnrollmentsUseCase: ObserveAllEnrollmentsUseCase,
        private val addQuranSchoolUseCase: AddQuranSchoolUseCase,
        private val updateQuranSchoolUseCase: UpdateQuranSchoolUseCase,
        private val archiveQuranSchoolUseCase: ArchiveQuranSchoolUseCase,
        private val unarchiveQuranSchoolUseCase: UnarchiveQuranSchoolUseCase,
        private val deleteQuranSchoolUseCase: DeleteQuranSchoolUseCase
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return QuranSchoolsViewModel(
                observeAllSchoolsUseCase,
                observeAllEnrollmentsUseCase,
                addQuranSchoolUseCase,
                updateQuranSchoolUseCase,
                archiveQuranSchoolUseCase,
                unarchiveQuranSchoolUseCase,
                deleteQuranSchoolUseCase
            ) as T
        }
    }
}
