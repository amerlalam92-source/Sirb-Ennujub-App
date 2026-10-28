package com.example.presentation.scout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import com.example.presentation.common.AppDatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.presentation.common.CustomTopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.scout.Scout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * استمارة إضافة / تعديل بيانات الشبل (Scout Form Screen)
 * تضم حقول الاسم واللقب وصلة القرابة وسنة الالتحاق ومصممة بدقة لسرب النُّجُب.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoutFormScreen(
    scoutId: String?,
    viewModel: ScoutViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formState by viewModel.formUiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    val dateFormatter = remember { SimpleDateFormat("yyyy/MM/dd", com.example.presentation.common.AppLocale.AR_DZ) }
    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    val availableYears = remember { (currentYear downTo 2000).toList() }

    var showBirthDatePicker by remember { mutableStateOf(false) }
    var relationDropdownExpanded by remember { mutableStateOf(false) }
    var joinYearDropdownExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(scoutId) {
        viewModel.initForm(scoutId)
    }

    LaunchedEffect(formState.isSavedSuccess) {
        if (formState.isSavedSuccess) {
            viewModel.resetFormSuccess()
            onNavigateBack()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        topBar = {
            CustomTopAppBar(
                title = if (scoutId.isNullOrBlank()) {
                    stringResource(id = R.string.add_scout_button)
                } else {
                    stringResource(id = R.string.edit_scout_title)
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("scout_form_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.cancel_action),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    if (formState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(22.dp)
                                .testTag("save_scout_loading"),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        IconButton(
                            onClick = {
                                focusManager.clearFocus()
                                viewModel.saveScout()
                            },
                            enabled = !formState.isSaving && !formState.isLoading,
                            modifier = Modifier.testTag("save_scout_submit_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(id = R.string.save_scout_button),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (formState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .testTag("form_loading_indicator"),
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // رسالة الخطأ العامة
                    AnimatedVisibility(
                        visible = formState.generalError != null,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        formState.generalError?.let { err ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = err,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }

                    // قسم 1: المعلومات الشخصية والكشفية
                    FormSectionCard(title = stringResource(id = R.string.section_personal_info)) {
                        // الاسم واللقب جنباً إلى جنب (Row)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // حقل الاسم
                            OutlinedTextField(
                                value = formState.name,
                                onValueChange = viewModel::onFormNameChanged,
                                label = { Text(stringResource(id = R.string.name_label)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Face,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                singleLine = true,
                                isError = formState.nameError != null,
                                supportingText = {
                                    formState.nameError?.let { errRes ->
                                        Text(
                                            text = stringResource(id = errRes),
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Text,
                                    imeAction = ImeAction.Next
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("form_name_input"),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                )
                            )

                            // حقل اللقب
                            OutlinedTextField(
                                value = formState.surname,
                                onValueChange = viewModel::onFormSurnameChanged,
                                label = { Text(stringResource(id = R.string.surname_label)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Badge,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                singleLine = true,
                                isError = formState.surnameError != null,
                                supportingText = {
                                    formState.surnameError?.let { errRes ->
                                        Text(
                                            text = stringResource(id = errRes),
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Text,
                                    imeAction = ImeAction.Next
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("form_surname_input"),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // تاريخ الميلاد (DatePicker Dialog)
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = if (formState.birthDate > 0) dateFormatter.format(Date(formState.birthDate)) else "",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(id = R.string.birth_date_label)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Cake,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.CalendarToday,
                                        contentDescription = stringResource(id = R.string.select_date),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                isError = formState.birthDateError != null,
                                supportingText = {
                                    formState.birthDateError?.let { errRes ->
                                        Text(
                                            text = stringResource(id = errRes),
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("form_birth_date_input"),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                )
                            )
                            // تغطية الحقل لتلقي النقر وفتح منتقي التاريخ
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable {
                                        focusManager.clearFocus()
                                        showBirthDatePicker = true
                                    }
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // سنة الالتحاق بالكشافة (ExposedDropdownMenu من 2000 حتى السنة الحالية)
                        ExposedDropdownMenuBox(
                            expanded = joinYearDropdownExpanded,
                            onExpandedChange = { joinYearDropdownExpanded = !joinYearDropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = formState.joinYear.toString(),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(id = R.string.join_year_label)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.CalendarToday,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = joinYearDropdownExpanded)
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                                    .testTag("form_join_year_dropdown"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                )
                            )
                            ExposedDropdownMenu(
                                expanded = joinYearDropdownExpanded,
                                onDismissRequest = { joinYearDropdownExpanded = false }
                            ) {
                                availableYears.forEach { year ->
                                    DropdownMenuItem(
                                        text = { Text(year.toString()) },
                                        onClick = {
                                            viewModel.onFormJoinYearChanged(year)
                                            joinYearDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // قسم 2: معلومات ولي الأمر والاتصال
                    FormSectionCard(title = stringResource(id = R.string.section_guardian_info)) {
                        // اسم الولي
                        OutlinedTextField(
                            value = formState.guardianName,
                            onValueChange = viewModel::onFormGuardianNameChanged,
                            label = { Text(stringResource(id = R.string.guardian_name_label)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("form_guardian_name_input"),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // صلة القرابة (أب، جد، عم، خال، أخ، أخرى)
                        ExposedDropdownMenuBox(
                            expanded = relationDropdownExpanded,
                            onExpandedChange = { relationDropdownExpanded = !relationDropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = formState.guardianRelation,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(id = R.string.guardian_relation_label)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Group,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = relationDropdownExpanded)
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                                    .testTag("form_guardian_relation_dropdown"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                )
                            )
                            ExposedDropdownMenu(
                                expanded = relationDropdownExpanded,
                                onDismissRequest = { relationDropdownExpanded = false }
                            ) {
                                Scout.GUARDIAN_RELATIONS.forEach { rel ->
                                    DropdownMenuItem(
                                        text = { Text(rel) },
                                        onClick = {
                                            viewModel.onFormGuardianRelationChanged(rel)
                                            relationDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // هاتف الولي (الأساسي)
                        OutlinedTextField(
                            value = formState.guardianPhone,
                            onValueChange = viewModel::onFormGuardianPhoneChanged,
                            label = { Text(stringResource(id = R.string.guardian_phone_label)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            singleLine = true,
                            isError = formState.guardianPhoneError != null,
                            supportingText = {
                                formState.guardianPhoneError?.let { errRes ->
                                    Text(
                                        text = stringResource(id = errRes),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Phone,
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("form_guardian_phone_input"),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // أرقام إضافية للولي (اختياري)
                        Text(
                            text = stringResource(id = R.string.scout_additional_phones_section),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        formState.additionalPhones.forEachIndexed { index, extraPhone ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = extraPhone,
                                    onValueChange = { newText ->
                                        viewModel.onAdditionalPhoneChanged(index, newText)
                                    },
                                    label = {
                                        Text(stringResource(id = R.string.phone_picker_additional_format, index + 1))
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Phone,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                        )
                                    },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = { viewModel.onRemoveAdditionalPhone(index) },
                                            modifier = Modifier.testTag("remove_additional_phone_$index")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = stringResource(id = R.string.cancel_action),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Phone,
                                        imeAction = ImeAction.Next
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("form_additional_phone_input_$index"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                    )
                                )
                            }
                        }

                        TextButton(
                            onClick = { viewModel.onAddAdditionalPhone() },
                            modifier = Modifier.testTag("btn_add_another_phone")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = stringResource(id = R.string.scout_add_another_phone))
                        }
                    }

                    // قسم 3: معلومات إضافية والملاحظات
                    FormSectionCard(title = stringResource(id = R.string.section_additional_info)) {
                        // العنوان السكني
                        OutlinedTextField(
                            value = formState.address,
                            onValueChange = viewModel::onFormAddressChanged,
                            label = { Text(stringResource(id = R.string.address_label)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            maxLines = 3,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("form_address_input"),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // المدرسة / المستوى
                        OutlinedTextField(
                            value = formState.school,
                            onValueChange = viewModel::onFormSchoolChanged,
                            label = { Text(stringResource(id = R.string.school_label)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.School,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("form_school_input"),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // ملاحظات خاصة
                        OutlinedTextField(
                            value = formState.notes,
                            onValueChange = viewModel::onFormNotesChanged,
                            label = { Text(stringResource(id = R.string.notes_label)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Notes,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            maxLines = 4,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("form_notes_input"),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    }
                }
            }
        }
    }

    // منتقي تاريخ الميلاد (BirthDate Picker Dialog)
    if (showBirthDatePicker) {
        val initialMillis = if (formState.birthDate > 0) formState.birthDate else System.currentTimeMillis()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

        AppDatePickerDialog(
            onDismissRequest = { showBirthDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            viewModel.onFormBirthDateChanged(it)
                        }
                        showBirthDatePicker = false
                    },
                    modifier = Modifier.testTag("confirm_birth_date_picker")
                ) {
                    Text(stringResource(id = R.string.confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBirthDatePicker = false }) {
                    Text(stringResource(id = R.string.cancel_action))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun FormSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            content()
        }
    }
}
