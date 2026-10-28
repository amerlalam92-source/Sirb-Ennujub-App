package com.example.presentation.quran.schools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.School
import com.example.presentation.common.AppAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.quran.QuranSchool

/**
 * حوار إضافة أو تعديل مدرسة قرآنية
 */
@Composable
fun QuranSchoolFormDialog(
    initialSchool: QuranSchool? = null,
    onDismiss: () -> Unit,
    onSave: (name: String, teacherName: String, teacherPhone: String, address: String?, notes: String?) -> Unit
) {
    val isEditing = initialSchool != null

    var name by remember { mutableStateOf(initialSchool?.name.orEmpty()) }
    var teacherName by remember { mutableStateOf(initialSchool?.teacherName.orEmpty()) }
    var teacherPhone by remember { mutableStateOf(initialSchool?.teacherPhone.orEmpty()) }
    var address by remember { mutableStateOf(initialSchool?.address.orEmpty()) }
    var notes by remember { mutableStateOf(initialSchool?.notes.orEmpty()) }

    var hasAttemptedSubmit by remember { mutableStateOf(false) }

    val isNameValid = name.trim().length >= 3
    val isTeacherNameValid = teacherName.trim().length >= 2
    val cleanPhone = teacherPhone.filter { it.isDigit() }
    val isPhoneValid = cleanPhone.length in 9..10

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.School,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(
                        if (isEditing) R.string.quran_schools_edit else R.string.quran_schools_add_new
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // اسم المدرسة
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.quran_schools_school_name) + " *") },
                    leadingIcon = {
                        Icon(Icons.Default.School, contentDescription = null)
                    },
                    isError = hasAttemptedSubmit && !isNameValid,
                    supportingText = {
                        if (hasAttemptedSubmit && !isNameValid) {
                            Text(stringResource(R.string.quran_schools_name_error))
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("school_name_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                // اسم الشيخ / المعلم
                OutlinedTextField(
                    value = teacherName,
                    onValueChange = { teacherName = it },
                    label = { Text(stringResource(R.string.quran_schools_teacher_name) + " *") },
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null)
                    },
                    isError = hasAttemptedSubmit && !isTeacherNameValid,
                    supportingText = {
                        if (hasAttemptedSubmit && !isTeacherNameValid) {
                            Text(stringResource(R.string.quran_schools_teacher_error))
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("school_teacher_name_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                // رقم الهاتف
                OutlinedTextField(
                    value = teacherPhone,
                    onValueChange = { input ->
                        teacherPhone = input.filter { it.isDigit() }
                    },
                    label = { Text(stringResource(R.string.quran_schools_teacher_phone) + " *") },
                    placeholder = { Text("05xxxxxxxx / 06xxxxxxxx") },
                    leadingIcon = {
                        Icon(Icons.Default.Call, contentDescription = null)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    isError = hasAttemptedSubmit && !isPhoneValid,
                    supportingText = {
                        if (hasAttemptedSubmit && !isPhoneValid) {
                            Text(stringResource(R.string.quran_schools_phone_error))
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("school_teacher_phone_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                // العنوان
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text(stringResource(R.string.quran_schools_address)) },
                    leadingIcon = {
                        Icon(Icons.Default.Place, contentDescription = null)
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("school_address_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                // ملاحظات إضافية
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.quran_schools_notes)) },
                    leadingIcon = {
                        Icon(Icons.Default.Notes, contentDescription = null)
                    },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("school_notes_input"),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    hasAttemptedSubmit = true
                    if (isNameValid && isTeacherNameValid && isPhoneValid) {
                        onSave(
                            name.trim(),
                            teacherName.trim(),
                            cleanPhone,
                            address.trim().ifBlank { null },
                            notes.trim().ifBlank { null }
                        )
                    }
                },
                modifier = Modifier.testTag("school_save_button")
            ) {
                Text(stringResource(R.string.quran_schools_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("school_cancel_button")
            ) {
                Text(stringResource(R.string.quran_schools_cancel))
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
