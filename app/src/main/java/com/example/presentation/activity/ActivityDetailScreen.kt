package com.example.presentation.activity

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import com.example.presentation.common.AppAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.activity.Activity
import com.example.domain.activity.ActivityStatus
import com.example.domain.activity.ActivityType
import com.example.domain.attendance.AttendanceStatus
import com.example.presentation.attendance.AttendanceRowItem
import com.example.presentation.common.CustomTopAppBar
import com.example.presentation.reports.PdfExporter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * شاشة تفاصيل النشاط الكشفي (Activity Detail Screen)
 * تعرض كافة بيانات النشاط مع إمكانية التعديل، تغيير الحالة (إنجاز مع تقرير أدبي إلزامي / إلغاء)،
 * وعرض مواضيع الاجتماعات والتقرير الأدبي ومشاركة الملخص.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ActivityDetailScreen(
    activityId: String,
    viewModel: ActivityViewModel,
    onNavigateToEdit: (String) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToAttendance: (String) -> Unit = {},
    onNavigateToContributions: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val detailState by viewModel.detailUiState.collectAsStateWithLifecycle()
    val attendanceList by viewModel.observeAttendanceForActivity(activityId).collectAsStateWithLifecycle(initialValue = emptyList())
    val attendanceRows by viewModel.observeAttendanceRowsForActivity(activityId).collectAsStateWithLifecycle(initialValue = emptyList())
    val meetingNumbers by viewModel.meetingNumbers.collectAsStateWithLifecycle()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var isEditingReportOnly by remember { mutableStateOf(false) }
    var reportText by remember { mutableStateOf("") }
    var isAttendanceListExpanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(activityId) {
        viewModel.loadActivityDetail(activityId)
        viewModel.loadTrainingMeetingNumber(activityId)
    }

    LaunchedEffect(detailState) {
        if (detailState is ActivityDetailUiState.Deleted) {
            onNavigateBack()
        }
    }

    val dateFormatter = remember { SimpleDateFormat("yyyy/MM/dd", com.example.presentation.common.AppLocale.AR_DZ) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CustomTopAppBar(
                title = stringResource(id = R.string.activity_detail_title),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("activity_detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.cancel_action),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    if (detailState is ActivityDetailUiState.Success) {
                        val activity = (detailState as ActivityDetailUiState.Success).activity

                        // زر المشاركة كملف PDF شامل
                        IconButton(
                            onClick = {
                                viewModel.shareActivityFullReport(context, activity)
                            },
                            modifier = Modifier.testTag("share_activity_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringResource(id = R.string.share_action),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // زر التعديل
                        IconButton(
                            onClick = { onNavigateToEdit(activityId) },
                            modifier = Modifier.testTag("edit_activity_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(id = R.string.edit_activity_title),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // زر حذف النشاط
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.testTag("delete_activity_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "حذف النشاط",
                                tint = MaterialTheme.colorScheme.error
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
            when (val state = detailState) {
                is ActivityDetailUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .testTag("activity_detail_loading"),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                is ActivityDetailUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadActivityDetail(activityId) }) {
                            Text(stringResource(id = R.string.confirm_action))
                        }
                    }
                }

                is ActivityDetailUiState.Success -> {
                    val activity = state.activity
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // ترويسة النشاط الكشفي
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = ActivityFormatters.getIconForType(activity.activityType),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(34.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = activity.title,
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = activity.activityType.arabicLabel,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        ActivityStatusChip(status = activity.activityStatus)

                                        if (attendanceList.isNotEmpty()) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.CheckCircle,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onPrimary,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = stringResource(id = R.string.attendance_recorded_badge),
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                        color = MaterialTheme.colorScheme.onPrimary
                                                    )
                                                }
                                            }
                                        } else {
                                            Surface(
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = stringResource(id = R.string.attendance_not_recorded_badge),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // بطاقة: قائمة الحضور لجميع الأشبال النشطين (قابلة للطي افتراضياً)
                        val meetingNum = meetingNumbers[activity.id]
                        val dateFormatted = if (activity.startDate > 0) dateFormatter.format(Date(activity.startDate)) else ""
                        val listHeaderSubtitle = if (activity.activityType == ActivityType.TRAINING_MEETING && meetingNum != null && meetingNum > 0) {
                            "الاجتماع التدريبي رقم $meetingNum — $dateFormatted"
                        } else {
                            "${activity.activityType.arabicLabel} — $dateFormatted"
                        }

                        val presentCount = attendanceRows.count { it.attendance?.attendanceStatus == AttendanceStatus.PRESENT }
                        val totalScouts = attendanceRows.size
                        val summaryText = stringResource(id = R.string.attendance_list_summary_format, presentCount, totalScouts)

                        val rotationAngle by animateFloatAsState(
                            targetValue = if (isAttendanceListExpanded) 180f else 0f,
                            label = "expandRotation"
                        )

                        val editWindow = ActivityFormatters.attendanceEditWindow(activity.startDate)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("attendance_list_card"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // سطر الترويسة القابل للنقر (دائماً مرئي)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { isAttendanceListExpanded = !isAttendanceListExpanded }
                                        .testTag("toggle_attendance_list_btn"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = stringResource(id = R.string.attendance_list_title),
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = summaryText,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = listHeaderSubtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    IconButton(
                                        onClick = { isAttendanceListExpanded = !isAttendanceListExpanded },
                                        modifier = Modifier.testTag("attendance_list_expand_icon")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ExpandMore,
                                            contentDescription = if (isAttendanceListExpanded) "طي" else "توسيع",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.rotate(rotationAngle)
                                        )
                                    }
                                }

                                // زر مشاركة قائمة الحضور كـ PDF (يبقى مرئياً دائماً حتى لو كانت القائمة مطوية)
                                OutlinedButton(
                                    onClick = {
                                        val file = PdfExporter.exportActivityAttendancePdf(
                                            activity = activity,
                                            rows = attendanceRows,
                                            trainingMeetingNumber = meetingNum,
                                            context = context
                                        )
                                        PdfExporter.shareActivityAttendancePdf(file, activity.title, context)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("share_attendance_pdf_button"),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PictureAsPdf,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(id = R.string.action_share_attendance_pdf),
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                    )
                                }

                                // جسم البطاقة: يظهر فقط عند التوسيع
                                AnimatedVisibility(
                                    visible = isAttendanceListExpanded,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (attendanceList.isEmpty()) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = stringResource(id = R.string.attendance_list_empty_hint),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    if (editWindow == AttendanceEditWindow.EDITABLE) {
                                                        Button(
                                                            onClick = { onNavigateToAttendance(activity.id) },
                                                            colors = ButtonDefaults.buttonColors(
                                                                containerColor = MaterialTheme.colorScheme.primary
                                                            ),
                                                            shape = RoundedCornerShape(8.dp),
                                                            modifier = Modifier.testTag("start_attendance_btn")
                                                        ) {
                                                            Text(
                                                                text = stringResource(id = R.string.attendance_start_button),
                                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        attendanceRows.forEach { row ->
                                            AttendanceRowItem(
                                                row = row,
                                                usesPointsSystem = activity.activityType.usesPointsSystem()
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // بطاقة 1: المعلومات الأساسية
                        DetailSectionCard(title = stringResource(id = R.string.section_activity_basic_info)) {
                            DetailItemRow(
                                icon = Icons.Default.CalendarMonth,
                                label = stringResource(id = R.string.activity_date_label),
                                value = ActivityFormatters.formatDateRange(activity)
                            )

                            if (activity.startTime.isNotBlank() || activity.endTime.isNotBlank()) {
                                val timeText = buildString {
                                    if (activity.startTime.isNotBlank()) append(ActivityFormatters.formatTimeArabic(activity.startTime))
                                    if (activity.endTime.isNotBlank()) append(" — ").append(ActivityFormatters.formatTimeArabic(activity.endTime))
                                }
                                DetailItemRow(
                                    icon = Icons.Default.AccessTime,
                                    label = stringResource(id = R.string.activity_time_label),
                                    value = timeText
                                )
                            }

                            DetailItemRow(
                                icon = Icons.Default.LocationOn,
                                label = stringResource(id = R.string.activity_location_label),
                                value = activity.location.ifBlank { "غير محدد" }
                            )
                        }

                        // بطاقة مواضيع الاجتماع (إذا كانت موجودة ومسجلة)
                        if (activity.meetingTopics.any { it.isNotBlank() }) {
                            DetailSectionCard(title = stringResource(id = R.string.section_meeting_topics)) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    activity.meetingTopics.filter { it.isNotBlank() }.forEachIndexed { index, topic ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Text(
                                                text = "${index + 1}.",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = topic,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // بطاقة 2: الأهداف التربوية والأدوات
                        if (activity.objectives.isNotBlank() || activity.tools.isNotBlank()) {
                            DetailSectionCard(title = stringResource(id = R.string.section_activity_objectives_tools)) {
                                if (activity.objectives.isNotBlank()) {
                                    DetailItemRow(
                                        icon = Icons.Default.FactCheck,
                                        label = stringResource(id = R.string.activity_objectives_label),
                                        value = activity.objectives
                                    )
                                }
                                if (activity.tools.isNotBlank()) {
                                    DetailItemRow(
                                        icon = Icons.Default.Handyman,
                                        label = stringResource(id = R.string.activity_tools_label),
                                        value = activity.tools
                                    )
                                }
                            }
                        }

                        // بطاقة 3: القائد المسؤول
                        DetailSectionCard(title = stringResource(id = R.string.section_activity_leadership)) {
                            DetailItemRow(
                                icon = Icons.Default.Person,
                                label = stringResource(id = R.string.activity_responsible_label),
                                value = activity.responsibleName.ifBlank { "سرب النُّجُب" }
                            )
                        }

                        // بطاقة 4: الملاحظات إن وجدت
                        if (activity.notes.isNotBlank()) {
                            DetailSectionCard(title = stringResource(id = R.string.notes_label)) {
                                DetailItemRow(
                                    icon = Icons.Default.Notes,
                                    label = stringResource(id = R.string.notes_label),
                                    value = activity.notes
                                )
                            }
                        }

                        // بطاقة 5: التقرير الأدبي النهائي (إذا كان النشاط منجزاً أو يحتوي على تقرير)
                        if (activity.finalReport.isNotBlank()) {
                            DetailSectionCard(title = stringResource(id = R.string.label_report)) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = activity.finalReport,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    if (activity.activityStatus == ActivityStatus.DONE) {
                                        OutlinedButton(
                                            onClick = {
                                                reportText = activity.finalReport
                                                isEditingReportOnly = true
                                                showReportDialog = true
                                            },
                                            modifier = Modifier
                                                .align(Alignment.End)
                                                .testTag("edit_report_button"),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(stringResource(id = R.string.action_edit_report))
                                        }
                                    }
                                }
                            }
                        }

                        // بطاقة المساهمات المالية للنشاط (خاصة بالأنشطة غير الروتينية كالمخيمات والرحلات)
                        if (activity.activityType != ActivityType.TRAINING_MEETING || activity.contributionRequired) {
                            DetailSectionCard(title = stringResource(id = R.string.activity_contributions_section_title)) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (activity.contributionRequired) {
                                                    if (activity.contributionDefaultAmount != null && activity.contributionDefaultAmount > 0) {
                                                        "مساهمة مطلوبة: ${activity.contributionDefaultAmount} دج"
                                                    } else {
                                                        "المساهمات المالية مفعلة لهذا النشاط"
                                                    }
                                                } else {
                                                    "لم يتم تفعيل المساهمة بعد"
                                                },
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        Button(
                                            onClick = { onNavigateToContributions(activity.id) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.secondary
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.testTag("manage_contributions_btn")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Payments,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = stringResource(id = R.string.activity_manage_contributions),
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // أزرار العمليات وتغيير الحالة (حضور / إنجاز مع تقرير أدبي / إلغاء / حذف)
                        if (activity.activityStatus != ActivityStatus.CANCELLED) {
                            val isEditable = editWindow == AttendanceEditWindow.EDITABLE
                            val buttonLabel = if (attendanceList.isNotEmpty()) {
                                stringResource(id = R.string.attendance_button_edit)
                            } else {
                                stringResource(id = R.string.attendance_button_register)
                            }

                            Column(modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = {
                                        if (isEditable) {
                                            onNavigateToAttendance(activity.id)
                                        }
                                    },
                                    enabled = isEditable,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("record_attendance_activity_button"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary,
                                        contentColor = MaterialTheme.colorScheme.onSecondary,
                                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FactCheck,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = buttonLabel,
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                    )
                                }

                                when (editWindow) {
                                    AttendanceEditWindow.EXPIRED -> {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = stringResource(id = R.string.attendance_expired_caption),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 4.dp)
                                                .testTag("attendance_expired_caption_text")
                                        )
                                    }
                                    AttendanceEditWindow.FUTURE -> {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = stringResource(id = R.string.attendance_too_early_caption),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 4.dp)
                                                .testTag("attendance_too_early_caption_text")
                                        )
                                    }
                                    AttendanceEditWindow.EDITABLE -> {}
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        if (activity.activityStatus == ActivityStatus.PLANNED) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        reportText = ""
                                        isEditingReportOnly = false
                                        showReportDialog = true
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("mark_done_activity_button"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(id = R.string.action_mark_done),
                                        maxLines = 2,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }

                                OutlinedButton(
                                    onClick = { showCancelDialog = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("cancel_activity_button"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Cancel,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(id = R.string.action_cancel_activity),
                                        maxLines = 2,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }



                        // تذييل الصفحة (تواريخ الإنشاء والتحديث)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (activity.createdAt > 0) {
                                Text(
                                    text = stringResource(
                                        id = R.string.created_at_format,
                                        dateFormatter.format(Date(activity.createdAt))
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                is ActivityDetailUiState.Deleted -> {
                    // تم الرجوع تلقائياً
                }
            }
        }
    }

    // مربع حوار التقرير الأدبي الإلزامي (عند الإنجاز أو تعديل التقرير)
    if (showReportDialog) {
        val wordCount = ReportValidator.countWords(reportText)
        val charCount = reportText.trim().length
        val isValid = ReportValidator.isReportValid(reportText)

        AppAlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = {
                Text(
                    text = stringResource(id = R.string.dialog_report_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = reportText,
                        onValueChange = { reportText = it },
                        label = { Text(stringResource(id = R.string.label_report)) },
                        minLines = 5,
                        maxLines = 10,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("final_report_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // إحصائيات حية لعدد الكلمات والأحرف مع التلوين الديناميكي
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = if (isValid) {
                                stringResource(id = R.string.report_word_count_valid_label, wordCount)
                            } else {
                                stringResource(id = R.string.report_word_count_label, wordCount)
                            },
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("report_word_count_text")
                        )

                        Text(
                            text = stringResource(id = R.string.report_char_count_label, charCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("report_char_count_text")
                        )

                        if (!isValid) {
                            Text(
                                text = stringResource(id = R.string.error_report_too_short),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val report = reportText.trim()
                        if (ReportValidator.isReportValid(report)) {
                            showReportDialog = false
                            if (isEditingReportOnly) {
                                viewModel.updateReport(activityId, report)
                            } else {
                                viewModel.markAsDoneWithReport(activityId, report)
                            }
                        }
                    },
                    enabled = isValid,
                    modifier = Modifier.testTag("confirm_save_report_btn")
                ) {
                    Text(
                        text = if (isEditingReportOnly) stringResource(id = R.string.save_scout_button)
                        else stringResource(id = R.string.action_save_and_mark_done)
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) {
                    Text(stringResource(id = R.string.cancel_action))
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    // حوار تأكيد الحذف
    if (showDeleteDialog) {
        AppAlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    text = stringResource(id = R.string.confirm_delete_activity_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Text(
                    text = stringResource(id = R.string.activity_delete_confirm_cascade),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteActivity(activityId)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_delete_activity_btn")
                ) {
                    Text(stringResource(id = R.string.delete_activity_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(id = R.string.cancel_action))
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    // حوار تأكيد الإلغاء
    if (showCancelDialog) {
        AppAlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = {
                Text(
                    text = stringResource(id = R.string.confirm_cancel_activity_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = stringResource(id = R.string.confirm_cancel_activity_message),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCancelDialog = false
                        viewModel.cancelActivity(activityId)
                    },
                    modifier = Modifier.testTag("confirm_cancel_activity_btn")
                ) {
                    Text(stringResource(id = R.string.confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text(stringResource(id = R.string.cancel_action))
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }
}

/**
 * إنشاء ملخص عربي أنيق لمشاركة النشاط
 */
private fun buildActivityShareText(activity: Activity): String {
    return buildString {
        append("⚜️ سرب النُّجُب — فوج محمد رحال ⚜️\n\n")
        append("📌 النشاط: ${activity.title}\n")
        append("🏷️ النوع: ${activity.activityType.arabicLabel}\n")
        append("📅 التاريخ: ${ActivityFormatters.formatDateRange(activity)}\n")
        if (activity.startTime.isNotBlank()) {
            append("⏰ التوقيت: ${ActivityFormatters.formatTimeArabic(activity.startTime)}")
            if (activity.endTime.isNotBlank()) append(" إلى ${ActivityFormatters.formatTimeArabic(activity.endTime)}")
            append("\n")
        }
        if (activity.location.isNotBlank()) append("📍 المكان: ${activity.location}\n")
        if (activity.responsibleName.isNotBlank()) append("👤 القائد المسؤول: ${activity.responsibleName}\n")

        if (activity.meetingTopics.any { it.isNotBlank() }) {
            append("\n📋 مواضيع الاجتماع:\n")
            activity.meetingTopics.filter { it.isNotBlank() }.forEachIndexed { index, topic ->
                append("${index + 1}. $topic\n")
            }
        }

        if (activity.objectives.isNotBlank()) append("\n🎯 الأهداف التربوية:\n${activity.objectives}\n")
        if (activity.tools.isNotBlank()) append("\n🎒 الأدوات واللوازم:\n${activity.tools}\n")

        if (activity.finalReport.isNotBlank()) {
            append("\n📝 التقرير الأدبي النهائي:\n${activity.finalReport}\n")
        }
    }
}

@Composable
private fun DetailSectionCard(
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

@Composable
private fun DetailItemRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(20.dp)
                .padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
