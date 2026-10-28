package com.example.presentation.attendance

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import com.example.presentation.common.AppAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.presentation.common.CustomTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.attendance.AttendanceStatus
import com.example.domain.scout.Scout
import com.example.presentation.activity.ActivityFormatters
import com.example.presentation.activity.AttendanceEditWindow
import kotlinx.coroutines.launch

/**
 * شاشة معالج تسجيل الحضور الكشفي (Attendance Wizard Entry Screen)
 * تعرض شبلاً واحداً في كل خطوة مع 4 خيارات تفاعلية واضحة ونقاط مخصصة وتأكيدات ذكية.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceEntryScreen(
    activityId: String,
    viewModel: AttendanceViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.entryUiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showFinishDialog by remember { mutableStateOf(false) }
    var pendingStatusChange by remember { mutableStateOf<AttendanceStatus?>(null) }
    var showAbsentToPresentWarning by remember { mutableStateOf(false) }

    LaunchedEffect(activityId) {
        viewModel.initAttendanceEntry(activityId)
    }

    val skippedMessage = stringResource(id = R.string.scout_skipped_readded_message)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CustomTopAppBar(
                title = state.activity?.title ?: stringResource(id = R.string.action_record_attendance),
                subtitle = stringResource(
                    id = R.string.scout_progress_format,
                    (state.currentIndex + 1).coerceAtMost(state.totalScouts),
                    state.totalScouts
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("attendance_entry_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.cancel_action),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    val editWindow = state.activity?.let { ActivityFormatters.attendanceEditWindow(it.startDate) } ?: AttendanceEditWindow.EDITABLE
                    val isEditable = editWindow == AttendanceEditWindow.EDITABLE
                    TextButton(
                        onClick = { if (isEditable) showFinishDialog = true },
                        enabled = isEditable,
                        modifier = Modifier.testTag("finish_save_top_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            tint = if (isEditable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(id = R.string.action_finish_and_save),
                            color = if (isEditable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            fontWeight = FontWeight.Bold
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
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .testTag("entry_loading_indicator"),
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (state.scoutsQueue.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(id = R.string.empty_active_scouts_for_attendance),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                val editWindow = state.activity?.let { ActivityFormatters.attendanceEditWindow(it.startDate) } ?: AttendanceEditWindow.EDITABLE
                val isEditable = editWindow == AttendanceEditWindow.EDITABLE

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    if (!isEditable) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Text(
                                text = if (editWindow == AttendanceEditWindow.EXPIRED) {
                                    stringResource(id = R.string.attendance_expired_caption)
                                } else {
                                    stringResource(id = R.string.attendance_too_early_caption)
                                },
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    // مؤشر التقدم العلوي
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val progress = if (state.totalScouts > 0) {
                            (state.currentIndex.toFloat() + 1f) / state.totalScouts.toFloat()
                        } else 0f

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // بطاقة الشبل الحالي بتأثير حركي سلس
                    AnimatedContent(
                        targetState = state.currentScout,
                        transitionSpec = {
                            (slideInHorizontally { width -> -width } + fadeIn()) togetherWith
                                    (slideOutHorizontally { width -> width } + fadeOut())
                        },
                        label = "scout_card_transition",
                        modifier = Modifier.weight(1f)
                    ) { currentScout ->
                        if (currentScout != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                ScoutWizardCard(
                                    scout = currentScout,
                                    selectedStatus = state.currentStatus,
                                    usesPoints = state.activity?.activityType?.usesPointsSystem() == true,
                                    pointsSettings = state.pointsSettings,
                                    onStatusSelected = { newStatus ->
                                        if (isEditable) {
                                            val existing = state.currentStatus
                                            if (existing != null && existing.isAbsent && newStatus.isPresentOrLate) {
                                                pendingStatusChange = newStatus
                                                showAbsentToPresentWarning = true
                                            } else {
                                                viewModel.onSelectStatus(newStatus)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // أزرار التنقل السفلية: السابق / التالي / إنهاء
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.onPrevious() },
                            enabled = !state.isFirst,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("wizard_previous_btn"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(id = R.string.action_previous))
                        }

                        if (state.isLast) {
                            Button(
                                onClick = { if (isEditable) showFinishDialog = true },
                                enabled = isEditable,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("wizard_finish_btn"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(id = R.string.action_finish_and_save))
                            }
                        } else {
                            Button(
                                onClick = {
                                    viewModel.onNext {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(skippedMessage)
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("wizard_next_btn"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(stringResource(id = R.string.action_next))
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // حوار تأكيد تغيير الحالة من غياب إلى حضور
    if (showAbsentToPresentWarning && pendingStatusChange != null) {
        AppAlertDialog(
            onDismissRequest = {
                showAbsentToPresentWarning = false
                pendingStatusChange = null
            },
            title = {
                Text(
                    text = stringResource(id = R.string.confirm_status_change_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = stringResource(id = R.string.confirm_absent_to_present_message),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingStatusChange?.let { viewModel.onSelectStatus(it) }
                        showAbsentToPresentWarning = false
                        pendingStatusChange = null
                    },
                    modifier = Modifier.testTag("confirm_absent_to_present_btn")
                ) {
                    Text(stringResource(id = R.string.confirm_action))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAbsentToPresentWarning = false
                        pendingStatusChange = null
                    }
                ) {
                    Text(stringResource(id = R.string.cancel_action))
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    // حوار تأكيد إنهاء وحفظ الحضور
    if (showFinishDialog) {
        AppAlertDialog(
            onDismissRequest = { showFinishDialog = false },
            title = {
                Text(
                    text = stringResource(id = R.string.confirm_finish_attendance_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = stringResource(id = R.string.confirm_finish_attendance_message),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFinishDialog = false
                        viewModel.saveAttendance {
                            onNavigateBack()
                        }
                    },
                    enabled = !state.isSaving,
                    modifier = Modifier.testTag("confirm_finish_attendance_btn")
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(stringResource(id = R.string.confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showFinishDialog = false }) {
                    Text(stringResource(id = R.string.cancel_action))
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }
}

/**
 * بطاقة الشبل الفردية داخل المعالج (Wizard Card)
 */
@Composable
private fun ScoutWizardCard(
    scout: Scout,
    selectedStatus: AttendanceStatus?,
    usesPoints: Boolean,
    pointsSettings: com.example.domain.attendance.AttendancePointsSettings,
    onStatusSelected: (AttendanceStatus) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // صورة رمزية للشبل
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Face,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // الاسم واللقب بخط عريض وواضح
            Text(
                text = scout.fullName,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            // العمر وهاتف الولي
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.age_years_format, scout.age),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                if (scout.guardianPhone.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = scout.guardianPhone,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 5 أزرار لاختيار الحالة
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // الصف الأول: حاضر + متأخر
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AttendanceOptionButton(
                        status = AttendanceStatus.PRESENT,
                        icon = Icons.Default.CheckCircle,
                        isSelected = selectedStatus == AttendanceStatus.PRESENT,
                        pointsText = if (usesPoints) "+${pointsSettings.presentPoints}" else null,
                        baseColor = Color(0xFF2E7D32),
                        containerColor = Color(0xFFE8F5E9),
                        onClick = { onStatusSelected(AttendanceStatus.PRESENT) },
                        testTag = "status_present_btn",
                        modifier = Modifier.weight(1f)
                    )

                    AttendanceOptionButton(
                        status = AttendanceStatus.LATE,
                        icon = Icons.Default.Schedule,
                        isSelected = selectedStatus == AttendanceStatus.LATE,
                        pointsText = if (usesPoints) "+${pointsSettings.latePoints}" else null,
                        baseColor = Color(0xFFF57F17),
                        containerColor = Color(0xFFFFFDE7),
                        onClick = { onStatusSelected(AttendanceStatus.LATE) },
                        testTag = "status_late_btn",
                        modifier = Modifier.weight(1f)
                    )
                }

                // الصف الثاني: متأخر جداً + غائب بعذر
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AttendanceOptionButton(
                        status = AttendanceStatus.VERY_LATE,
                        icon = Icons.Default.HourglassBottom,
                        isSelected = selectedStatus == AttendanceStatus.VERY_LATE,
                        pointsText = if (usesPoints) "+${pointsSettings.veryLatePoints}" else null,
                        baseColor = Color(0xFFE64A19),
                        containerColor = Color(0xFFFBE9E7),
                        onClick = { onStatusSelected(AttendanceStatus.VERY_LATE) },
                        testTag = "status_very_late_btn",
                        modifier = Modifier.weight(1f)
                    )

                    AttendanceOptionButton(
                        status = AttendanceStatus.ABSENT_EXCUSED,
                        icon = Icons.Default.Warning,
                        isSelected = selectedStatus == AttendanceStatus.ABSENT_EXCUSED,
                        pointsText = if (usesPoints) "+${pointsSettings.absentExcusedPoints}" else null,
                        baseColor = Color(0xFFE65100),
                        containerColor = Color(0xFFFFF3E0),
                        onClick = { onStatusSelected(AttendanceStatus.ABSENT_EXCUSED) },
                        testTag = "status_absent_excused_btn",
                        modifier = Modifier.weight(1f)
                    )
                }

                // الصف الثالث: غائب بدون عذر (عرض كامل)
                AttendanceOptionButton(
                    status = AttendanceStatus.ABSENT_UNEXCUSED,
                    icon = Icons.Default.Cancel,
                    isSelected = selectedStatus == AttendanceStatus.ABSENT_UNEXCUSED,
                    pointsText = if (usesPoints) "+${pointsSettings.absentUnexcusedPoints}" else null,
                    baseColor = Color(0xFFC62828),
                    containerColor = Color(0xFFFFEBEE),
                    onClick = { onStatusSelected(AttendanceStatus.ABSENT_UNEXCUSED) },
                    testTag = "status_absent_unexcused_btn",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * زر خيار الحضور الكبير القابل للنقر بمساحة لمس عريضة (52dp+)
 */
@Composable
private fun AttendanceOptionButton(
    status: AttendanceStatus,
    icon: ImageVector,
    isSelected: Boolean,
    pointsText: String?,
    baseColor: Color,
    containerColor: Color,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(54.dp)
            .testTag(testTag)
            .border(
                width = if (isSelected) 2.5.dp else 1.dp,
                color = if (isSelected) baseColor else Color.LightGray.copy(alpha = 0.5f),
                shape = RoundedCornerShape(14.dp)
            ),
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) baseColor else containerColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Color.White else baseColor,
                modifier = Modifier.size(22.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = status.arabicLabel,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = if (isSelected) Color.White else Color.Black.copy(alpha = 0.85f),
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )

            if (pointsText != null) {
                Surface(
                    color = if (isSelected) Color.White.copy(alpha = 0.25f) else baseColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = pointsText,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isSelected) Color.White else baseColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
