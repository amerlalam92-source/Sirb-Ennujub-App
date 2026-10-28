package com.example.presentation.quran.tracking

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MenuBook
import com.example.presentation.common.AppAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.quran.QuranSchool
import com.example.domain.quran.ScoutQuranEnrollment
import com.example.domain.quran.SelectedPeriod
import com.example.domain.quran.StudyPeriod
import com.example.domain.scout.Scout

/**
 * حوار تسجيل أو تعديل تسجيل الشبل بالكتّاب
 */
@Composable
fun EnrollmentFormDialog(
    scout: Scout,
    existingEnrollment: ScoutQuranEnrollment?,
    availableSchools: List<QuranSchool>,
    onDismiss: () -> Unit,
    onSave: (
        school: QuranSchool,
        currentMemorizedParts: Int,
        memorizationNote: String?,
        studyDays: List<String>,
        studyPeriods: List<SelectedPeriod>,
        generalNotes: String?
    ) -> Unit
) {
    val isEdit = existingEnrollment != null

    // اختيار المدرسة
    var selectedSchool by remember {
        mutableStateOf(
            availableSchools.firstOrNull { it.id == existingEnrollment?.schoolId }
                ?: availableSchools.firstOrNull()
        )
    }
    var schoolDropdownExpanded by remember { mutableStateOf(false) }

    // الأجزاء المحفوظة وملاحظة الحفظ
    var partsText by remember {
        mutableStateOf(existingEnrollment?.currentMemorizedParts?.toString() ?: "0")
    }
    var memoNote by remember {
        mutableStateOf(existingEnrollment?.memorizationNote.orEmpty())
    }

    // أيام الدوام
    var selectedDays by remember {
        mutableStateOf(existingEnrollment?.studyDays ?: listOf("saturday", "tuesday"))
    }

    // فترات الدوام
    var selectedPeriods by remember {
        mutableStateOf(
            existingEnrollment?.studyPeriods ?: listOf(
                SelectedPeriod(StudyPeriod.MORNING, "08:30")
            )
        )
    }

    // وقت البدء لفترة الصباح
    var morningStartTime by remember {
        mutableStateOf(
            existingEnrollment?.studyPeriods?.firstOrNull { it.period == StudyPeriod.MORNING }?.startTime
                ?: "08:30"
        )
    }

    // ملاحظات عامة
    var generalNotes by remember {
        mutableStateOf(existingEnrollment?.generalNotes.orEmpty())
    }

    // أخطاء التحقق
    var partsError by remember { mutableStateOf(false) }
    var daysError by remember { mutableStateOf(false) }
    var periodsError by remember { mutableStateOf(false) }
    var timeFormatError by remember { mutableStateOf(false) }
    var schoolError by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = stringResource(
                            if (isEdit) R.string.quran_enrollment_dialog_title_edit
                            else R.string.quran_enrollment_dialog_title_new
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = scout.fullName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .testTag("enrollment_form_column"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. اختيار المدرسة القرآنية
                if (availableSchools.isEmpty()) {
                    Text(
                        text = stringResource(R.string.quran_enrollment_no_active_schools),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedSchool?.name ?: stringResource(R.string.quran_enrollment_school_select_placeholder),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.quran_enrollment_school_label)) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null
                                )
                            },
                            isError = schoolError,
                            supportingText = if (schoolError) {
                                { Text(stringResource(R.string.quran_enrollment_school_error)) }
                            } else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { schoolDropdownExpanded = true }
                                .testTag("school_selector_dropdown_trigger")
                        )

                        DropdownMenu(
                            expanded = schoolDropdownExpanded,
                            onDismissRequest = { schoolDropdownExpanded = false }
                        ) {
                            availableSchools.forEach { school ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(school.name, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = "${school.teacherName} • ${school.teacherPhone}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedSchool = school
                                        schoolError = false
                                        schoolDropdownExpanded = false
                                    },
                                    modifier = Modifier.testTag("school_option_${school.id}")
                                )
                            }
                        }
                    }
                }

                // 2. عدد الأجزاء المحفوظة
                OutlinedTextField(
                    value = partsText,
                    onValueChange = {
                        if (it.length <= 2 && it.all { ch -> ch.isDigit() }) {
                            partsText = it
                            val num = it.toIntOrNull() ?: 0
                            partsError = num !in 0..60
                        }
                    },
                    label = { Text(stringResource(R.string.quran_enrollment_parts_label)) },
                    isError = partsError,
                    supportingText = if (partsError) {
                        { Text(stringResource(R.string.quran_enrollment_parts_error)) }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("parts_input_field")
                )

                // 3. ملاحظة الحفظ الحالية
                OutlinedTextField(
                    value = memoNote,
                    onValueChange = { memoNote = it },
                    label = { Text(stringResource(R.string.quran_enrollment_memo_note_label)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("memo_note_input_field")
                )

                // 4. أيام الدوام
                Text(
                    text = stringResource(R.string.quran_enrollment_days_label),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                StudyDaysSelector(
                    selectedDays = selectedDays,
                    onDayToggled = { dayKey ->
                        selectedDays = if (selectedDays.contains(dayKey)) {
                            selectedDays - dayKey
                        } else {
                            selectedDays + dayKey
                        }
                        daysError = selectedDays.isEmpty()
                    },
                    isError = daysError
                )

                // 5. فترات الدوام
                Text(
                    text = stringResource(R.string.quran_enrollment_periods_label),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                StudyPeriodsSelector(
                    selectedPeriods = selectedPeriods,
                    onPeriodToggled = { period ->
                        val exists = selectedPeriods.any { it.period == period }
                        selectedPeriods = if (exists) {
                            selectedPeriods.filterNot { it.period == period }
                        } else {
                            val newStartTime = if (period == StudyPeriod.MORNING) morningStartTime else null
                            selectedPeriods + SelectedPeriod(period, newStartTime)
                        }
                        periodsError = selectedPeriods.isEmpty()
                    },
                    onMorningStartTimeChanged = { time ->
                        morningStartTime = time
                        timeFormatError = time.isNotBlank() && !Regex("^\\d{1,2}:\\d{2}$").matches(time)
                        selectedPeriods = selectedPeriods.map { sp ->
                            if (sp.period == StudyPeriod.MORNING) sp.copy(startTime = time) else sp
                        }
                    },
                    morningStartTime = morningStartTime,
                    isError = periodsError,
                    timeFormatError = timeFormatError
                )

                // 6. ملاحظات عامة
                OutlinedTextField(
                    value = generalNotes,
                    onValueChange = { generalNotes = it },
                    label = { Text(stringResource(R.string.quran_enrollment_general_notes_label)) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("general_notes_input_field")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val school = selectedSchool
                    if (school == null) {
                        schoolError = true
                        return@Button
                    }
                    val parts = partsText.toIntOrNull()
                    if (parts == null || parts !in 0..60) {
                        partsError = true
                        return@Button
                    }
                    if (selectedDays.isEmpty()) {
                        daysError = true
                        return@Button
                    }
                    if (selectedPeriods.isEmpty()) {
                        periodsError = true
                        return@Button
                    }
                    val morningSelected = selectedPeriods.any { it.period == StudyPeriod.MORNING }
                    if (morningSelected && morningStartTime.isNotBlank() && !Regex("^\\d{1,2}:\\d{2}$").matches(morningStartTime)) {
                        timeFormatError = true
                        return@Button
                    }

                    // تحديث وقت فترة الصباح في القائمة النهائية
                    val finalPeriods = selectedPeriods.map { sp ->
                        if (sp.period == StudyPeriod.MORNING) {
                            sp.copy(startTime = morningStartTime.trim().takeIf { it.isNotBlank() })
                        } else {
                            sp.copy(startTime = null)
                        }
                    }

                    onSave(
                        school,
                        parts,
                        memoNote,
                        selectedDays,
                        finalPeriods,
                        generalNotes
                    )
                },
                enabled = availableSchools.isNotEmpty(),
                modifier = Modifier.testTag("submit_enrollment_button")
            ) {
                Text(stringResource(R.string.quran_schools_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_enrollment_button")
            ) {
                Text(stringResource(R.string.quran_schools_cancel))
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
