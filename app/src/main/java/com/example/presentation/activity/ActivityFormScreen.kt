package com.example.presentation.activity

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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import com.example.presentation.common.AppAlertDialog
import com.example.presentation.common.AppDatePickerDialog
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import com.example.presentation.common.CustomTopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import com.example.domain.activity.ActivityType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * استمارة إضافة وتعديل النشاط الكشفي (Activity Form Screen)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityFormScreen(
    activityId: String?,
    viewModel: ActivityViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formState by viewModel.formUiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    val dateFormatter = remember { SimpleDateFormat("yyyy/MM/dd", com.example.presentation.common.AppLocale.AR_DZ) }

    var typeDropdownExpanded by remember { mutableStateOf(false) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    LaunchedEffect(activityId) {
        viewModel.initForm(activityId)
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
                title = if (activityId.isNullOrBlank()) {
                    stringResource(id = R.string.add_activity_button)
                } else {
                    stringResource(id = R.string.edit_activity_title)
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("activity_form_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.cancel_action),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
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
                        .testTag("activity_form_loading"),
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
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

                    // بطاقة 1: عنوان ونوع النشاط
                    FormCard(title = stringResource(id = R.string.section_activity_basic_info)) {
                        // عنوان النشاط
                        OutlinedTextField(
                            value = formState.title,
                            onValueChange = viewModel::onFormTitleChanged,
                            label = { Text(stringResource(id = R.string.activity_title_label)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Title,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            singleLine = true,
                            isError = formState.titleError != null,
                            supportingText = {
                                formState.titleError?.let { errRes ->
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
                                .fillMaxWidth()
                                .testTag("form_activity_title_input"),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // نوع النشاط (ExposedDropdownMenu)
                        ExposedDropdownMenuBox(
                            expanded = typeDropdownExpanded,
                            onExpandedChange = { typeDropdownExpanded = !typeDropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = ActivityType.fromKey(formState.type).arabicLabel,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(id = R.string.activity_type_label)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Label,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeDropdownExpanded)
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                                    .testTag("form_activity_type_dropdown"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                )
                            )
                            ExposedDropdownMenu(
                                expanded = typeDropdownExpanded,
                                onDismissRequest = { typeDropdownExpanded = false }
                            ) {
                                ActivityType.entries.forEach { item ->
                                    DropdownMenuItem(
                                        text = { Text(item.arabicLabel) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = ActivityFormatters.getIconForType(item),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        },
                                        onClick = {
                                            viewModel.onFormTypeChanged(item.key)
                                            typeDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // تفعيل تحصيل الاشتراكات المالية
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("form_collect_subscriptions_row"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(id = R.string.collect_subscriptions_label),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(id = R.string.collect_subscriptions_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = formState.collectSubscriptions,
                                onCheckedChange = viewModel::onFormCollectSubscriptionsChanged,
                                modifier = Modifier.testTag("form_collect_subscriptions_switch")
                            )
                        }
                    }

                    // بطاقة مواضيع الاجتماع (تظهر فقط عند اختيار نوع نشاط اجتماعي)
                    val isMeetingType = ActivityType.fromKey(formState.type).isMeetingType()
                    AnimatedVisibility(
                        visible = isMeetingType,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        FormCard(title = stringResource(id = R.string.section_meeting_topics)) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                formState.meetingTopics.forEachIndexed { index, topic ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = topic,
                                            onValueChange = { viewModel.onUpdateTopic(index, it) },
                                            label = { Text("${stringResource(id = R.string.label_topic)} ${index + 1}") },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("form_meeting_topic_$index"),
                                            shape = RoundedCornerShape(12.dp),
                                            singleLine = true,
                                            trailingIcon = {
                                                IconButton(
                                                    onClick = { viewModel.onRemoveTopic(index) },
                                                    modifier = Modifier.testTag("remove_topic_$index")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Clear,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }

                                TextButton(
                                    onClick = { viewModel.onAddTopic() },
                                    modifier = Modifier
                                        .align(Alignment.Start)
                                        .testTag("add_topic_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(id = R.string.action_add_topic),
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    // بطاقة 2: التواريخ والمواعيد
                    FormCard(title = stringResource(id = R.string.activity_date_label)) {
                        // تاريخ البداية وتاريخ النهاية جنباً إلى جنب
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // تاريخ البداية
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = dateFormatter.format(Date(formState.startDate)),
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(stringResource(id = R.string.activity_start_date_label)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.CalendarMonth,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clickable {
                                            focusManager.clearFocus()
                                            showStartDatePicker = true
                                        }
                                )
                            }

                            // تاريخ النهاية
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = dateFormatter.format(Date(formState.endDate)),
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(stringResource(id = R.string.activity_end_date_label)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.CalendarMonth,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    isError = formState.endDateError != null,
                                    supportingText = {
                                        formState.endDateError?.let { errRes ->
                                            Text(
                                                text = stringResource(id = errRes),
                                                color = MaterialTheme.colorScheme.error,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clickable {
                                            focusManager.clearFocus()
                                            showEndDatePicker = true
                                        }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // وقت البداية ووقت النهاية (اختياري)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // وقت البداية
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = if (formState.startTime.isNotBlank()) ActivityFormatters.formatTimeArabic(formState.startTime) else "",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(stringResource(id = R.string.activity_start_time_label)) },
                                    placeholder = { Text("بدون وقت") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.AccessTime,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    trailingIcon = {
                                        if (formState.startTime.isNotBlank()) {
                                            IconButton(onClick = { viewModel.onFormStartTimeChanged("") }) {
                                                Icon(
                                                    imageVector = Icons.Default.Clear,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clickable {
                                            focusManager.clearFocus()
                                            showStartTimePicker = true
                                        }
                                )
                            }

                            // وقت النهاية
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = if (formState.endTime.isNotBlank()) ActivityFormatters.formatTimeArabic(formState.endTime) else "",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(stringResource(id = R.string.activity_end_time_label)) },
                                    placeholder = { Text("بدون وقت") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.AccessTime,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    trailingIcon = {
                                        if (formState.endTime.isNotBlank()) {
                                            IconButton(onClick = { viewModel.onFormEndTimeChanged("") }) {
                                                Icon(
                                                    imageVector = Icons.Default.Clear,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clickable {
                                            focusManager.clearFocus()
                                            showEndTimePicker = true
                                        }
                                )
                            }
                        }
                    }

                    // بطاقة 3: المكان والمسؤول
                    FormCard(title = stringResource(id = R.string.section_activity_leadership)) {
                        // المكان
                        OutlinedTextField(
                            value = formState.location,
                            onValueChange = viewModel::onFormLocationChanged,
                            label = { Text(stringResource(id = R.string.activity_location_label)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
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
                                .testTag("form_activity_location_input"),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // القائد المسؤول
                        OutlinedTextField(
                            value = formState.responsibleName,
                            onValueChange = viewModel::onFormResponsibleNameChanged,
                            label = { Text(stringResource(id = R.string.activity_responsible_label)) },
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
                                .testTag("form_activity_responsible_input"),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    }

                    // بطاقة 4: الأهداف التربوية والأدوات والملاحظات
                    FormCard(title = stringResource(id = R.string.section_activity_objectives_tools)) {
                        // الأهداف التربوية
                        OutlinedTextField(
                            value = formState.objectives,
                            onValueChange = viewModel::onFormObjectivesChanged,
                            label = { Text(stringResource(id = R.string.activity_objectives_label)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.FactCheck,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            maxLines = 4,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("form_activity_objectives_input"),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // الأدوات واللوازم
                        OutlinedTextField(
                            value = formState.tools,
                            onValueChange = viewModel::onFormToolsChanged,
                            label = { Text(stringResource(id = R.string.activity_tools_label)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Handyman,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            maxLines = 4,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("form_activity_tools_input"),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // ملاحظات
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
                                .testTag("form_activity_notes_input"),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // زر الحفظ
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.saveActivity()
                        },
                        enabled = !formState.isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("save_activity_submit_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        if (formState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(id = R.string.saving))
                        } else {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(id = R.string.save_activity_button),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }
    }

    // منتقي تاريخ البداية
    if (showStartDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = formState.startDate)
        AppDatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { viewModel.onFormStartDateChanged(it) }
                    showStartDatePicker = false
                }) {
                    Text(stringResource(id = R.string.confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) {
                    Text(stringResource(id = R.string.cancel_action))
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    // منتقي تاريخ النهاية
    if (showEndDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = formState.endDate)
        AppDatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { viewModel.onFormEndDateChanged(it) }
                    showEndDatePicker = false
                }) {
                    Text(stringResource(id = R.string.confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) {
                    Text(stringResource(id = R.string.cancel_action))
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    // منتقي وقت البداية
    if (showStartTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = if (formState.startTime.contains(":")) formState.startTime.split(":")[0].toIntOrNull() ?: 9 else 9,
            initialMinute = if (formState.startTime.contains(":")) formState.startTime.split(":")[1].toIntOrNull() ?: 0 else 0,
            is24Hour = true
        )
        AppAlertDialog(
            onDismissRequest = { showStartTimePicker = false },
            title = { Text(stringResource(id = R.string.activity_start_time_label)) },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    val formatted = String.format(Locale.US, "%02d:%02d", timePickerState.hour, timePickerState.minute)
                    viewModel.onFormStartTimeChanged(formatted)
                    showStartTimePicker = false
                }) {
                    Text(stringResource(id = R.string.confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartTimePicker = false }) {
                    Text(stringResource(id = R.string.cancel_action))
                }
            }
        )
    }

    // منتقي وقت النهاية
    if (showEndTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = if (formState.endTime.contains(":")) formState.endTime.split(":")[0].toIntOrNull() ?: 17 else 17,
            initialMinute = if (formState.endTime.contains(":")) formState.endTime.split(":")[1].toIntOrNull() ?: 0 else 0,
            is24Hour = true
        )
        AppAlertDialog(
            onDismissRequest = { showEndTimePicker = false },
            title = { Text(stringResource(id = R.string.activity_end_time_label)) },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    val formatted = String.format(Locale.US, "%02d:%02d", timePickerState.hour, timePickerState.minute)
                    viewModel.onFormEndTimeChanged(formatted)
                    showEndTimePicker = false
                }) {
                    Text(stringResource(id = R.string.confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndTimePicker = false }) {
                    Text(stringResource(id = R.string.cancel_action))
                }
            }
        )
    }
}

@Composable
private fun FormCard(
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
