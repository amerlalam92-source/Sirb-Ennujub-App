package com.example.presentation.attendance

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Remove
import androidx.compose.ui.unit.Dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import com.example.presentation.common.AppLocale
import com.example.presentation.common.CustomTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.attendance.AttendanceStatus
import com.example.domain.attendance.MeetingWithAttendance
import com.example.presentation.activity.ActivityFormatters
import com.example.presentation.reports.PdfExporter

/**
 * شاشة سجل الاجتماعات التدريبية الكشفية
 * تدعم عرضين:
 * 1. بطاقات الاجتماعات القابلة للطي (مرتبة بالأحدث في الأعلى مع كشف الحضور الكامل).
 * 2. جدول المصفوفة البيانية (صفوف للأشبال وأعمدة للاجتماعات مع تمرير أفقي).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingMeetingsRegisterScreen(
    viewModel: TrainingMeetingsRegisterViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val horizontalScrollState = rememberScrollState()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var expandedMeetingIds by rememberSaveable { mutableStateOf(setOf<String>()) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CustomTopAppBar(
                title = stringResource(id = R.string.training_meetings_register_title),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("training_register_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.back_action),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val successState = uiState as? TrainingMeetingsRegisterUiState.Success
                            if (successState == null || successState.meetings.isEmpty()) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.register_empty_message),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                val file = PdfExporter.exportTrainingMeetingsRegisterPdf(successState.meetings, context)
                                PdfExporter.shareTrainingMeetingsRegisterPdf(file, context)
                            }
                        },
                        modifier = Modifier.testTag("share_training_register_pdf_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(id = R.string.action_share_register_pdf),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        when (val state = uiState) {
            is TrainingMeetingsRegisterUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            is TrainingMeetingsRegisterUiState.Success -> {
                if (state.meetings.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.EventNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(64.dp)
                            )
                            Text(
                                text = stringResource(id = R.string.register_empty_message),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        // شريط التبديل بين بطاقات الاجتماعات وجدول المصفوفة
                        TabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("register_view_tabs")
                        ) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ViewAgenda,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = stringResource(id = R.string.register_view_cards),
                                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                },
                                modifier = Modifier.testTag("tab_cards_view")
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.GridView,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = stringResource(id = R.string.register_view_matrix),
                                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                },
                                modifier = Modifier.testTag("tab_matrix_view")
                            )
                        }

                        if (selectedTab == 0) {
                            // العرض 1: بطاقات الاجتماعات مرتبة تصاعدياً من الأحدث إلى الأقدم
                            val newestFirstMeetings = remember(state.meetings) {
                                state.meetings.sortedByDescending { it.activity.startDate }
                            }

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("training_meetings_cards_list"),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(
                                    items = newestFirstMeetings,
                                    key = { it.activity.id }
                                ) { meeting ->
                                    val isExpanded = meeting.activity.id in expandedMeetingIds
                                    MeetingExpandableCard(
                                        meeting = meeting,
                                        isExpanded = isExpanded,
                                        onToggleExpand = {
                                            expandedMeetingIds = if (isExpanded) {
                                                expandedMeetingIds - meeting.activity.id
                                            } else {
                                                expandedMeetingIds + meeting.activity.id
                                            }
                                        }
                                    )
                                }
                            }
                        } else {
                            // العرض 2: جدول المصفوفة البيانية (Matrix Table) - بنية آمنة خالية من الانهيارات
                            val verticalScrollState = rememberScrollState()
                            val horizontalScrollState = rememberScrollState()

                            val chronologicalMeetings = remember(state.meetings) {
                                state.meetings.sortedBy { it.number }
                            }

                            val scouts = remember(chronologicalMeetings) {
                                chronologicalMeetings.flatMap { it.rows.map { row -> row.scout } }
                                    .distinctBy { it.id }
                                    .sortedBy { it.fullName }
                            }

                            val meetingAttendanceMap = remember(chronologicalMeetings) {
                                chronologicalMeetings.associate { meeting ->
                                    meeting.activity.id to meeting.rows.associateBy { it.scout.id }
                                }
                            }

                            if (chronologicalMeetings.isEmpty() || scouts.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.register_empty_message),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(verticalScrollState) // ONLY ONE VERTICAL SCROLL
                                        .padding(16.dp)
                                        .testTag("training_matrix_table")
                                ) {
                                    // 1. Filter Chips / Header info
                                    Text(
                                        text = stringResource(id = R.string.register_view_matrix),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )

                                    // 2. The Table Box
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(horizontalScrollState) // ONLY ONE HORIZONTAL SCROLL
                                    ) {
                                        Column {
                                            // A. Table Header (Meeting Dates)
                                            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                                                // First cell (Title)
                                                TableCell(
                                                    text = stringResource(id = R.string.register_table_scout_column),
                                                    width = 120.dp,
                                                    isHeader = true
                                                )
                                                // Meeting columns
                                                chronologicalMeetings.forEach { meeting ->
                                                    val paddedNumber = String.format(AppLocale.AR_DZ, "%02d", meeting.number)
                                                    val dateFormatted = if (meeting.activity.startDate > 0) {
                                                        val sdf = SimpleDateFormat("dd/MM/yyyy", AppLocale.AR_DZ)
                                                        sdf.format(Date(meeting.activity.startDate))
                                                    } else {
                                                        "—"
                                                    }
                                                    TableCell(
                                                        width = 120.dp,
                                                        isHeader = true,
                                                        content = {
                                                            Column(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                                verticalArrangement = Arrangement.Center
                                                            ) {
                                                                Text(
                                                                    text = "الاجتماع التدريبي $paddedNumber",
                                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                                    textAlign = TextAlign.Center,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                                Spacer(modifier = Modifier.height(2.dp))
                                                                Text(
                                                                    text = dateFormatted,
                                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                    textAlign = TextAlign.Center,
                                                                    maxLines = 1
                                                                )
                                                            }
                                                        }
                                                    )
                                                }
                                                // Total column
                                                TableCell(
                                                    text = stringResource(id = R.string.register_column_total),
                                                    width = 72.dp,
                                                    isHeader = true
                                                )
                                            }

                                            // B. Table Rows (Scouts)
                                            scouts.forEach { scout ->
                                                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                                                    // 1. Scout Name Cell
                                                    TableCell(
                                                        text = scout.fullName,
                                                        width = 120.dp,
                                                        isHeader = false
                                                    )

                                                    // 2. Meeting Cells
                                                    chronologicalMeetings.forEach { meeting ->
                                                        val record = meetingAttendanceMap[meeting.activity.id]?.get(scout.id)?.attendance
                                                        val status = record?.status ?: "unrecorded"
                                                        val (color, icon) = getStatusColorAndIcon(status)

                                                        TableCell(
                                                            width = 120.dp,
                                                            isHeader = false,
                                                            content = {
                                                                Box(
                                                                    modifier = Modifier.fillMaxSize(),
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    Icon(
                                                                        imageVector = icon,
                                                                        contentDescription = null,
                                                                        tint = color,
                                                                        modifier = Modifier.size(20.dp)
                                                                    )
                                                                }
                                                            }
                                                        )
                                                    }

                                                    // 3. Total Cell
                                                    val totalPoints = chronologicalMeetings.sumOf { meeting ->
                                                        val record = meetingAttendanceMap[meeting.activity.id]?.get(scout.id)?.attendance
                                                        if (record != null && record.attendanceStatus != null) {
                                                            record.points
                                                        } else {
                                                            0
                                                        }
                                                    }
                                                    TableCell(
                                                        text = totalPoints.toString(),
                                                        width = 72.dp,
                                                        isHeader = false
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // 3. Legend (Safe layout)
                                    Spacer(modifier = Modifier.height(24.dp))
                                    LegendRow()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * بطاقة الاجتماع التدريبي القابلة للتوسيع والطي مع قائمة الحضور الكاملة
 */
@Composable
private fun MeetingExpandableCard(
    meeting: MeetingWithAttendance,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "meetingCardRotation"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("meeting_card_${meeting.activity.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // سطر ترويسة الاجتماع (قابل للنقر للطي والتوسيع)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .testTag("meeting_header_${meeting.activity.id}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.register_meeting_header_format, meeting.number),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = stringResource(id = R.string.register_meeting_badge_format, meeting.presentCount, meeting.totalScouts),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    val dateFormatted = if (meeting.activity.startDate > 0) {
                        ActivityFormatters.formatDate(meeting.activity.startDate)
                    } else ""
                    val infoText = buildString {
                        append(dateFormatted)
                        if (meeting.activity.location.isNotBlank()) {
                            append(" | ").append(meeting.activity.location)
                        }
                    }
                    Text(
                        text = infoText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.testTag("meeting_expand_btn_${meeting.activity.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "طي" else "توسيع",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.rotate(rotationAngle)
                    )
                }
            }

            // محتوى كشف الحضور الكامل (يظهر بحركة متحركة عند التوسيع)
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    meeting.rows.forEach { row ->
                        AttendanceRowItem(
                            row = row,
                            usesPointsSystem = meeting.activity.activityType.usesPointsSystem()
                        )
                    }
                }
            }
        }
    }
}

/**
 * دالة مساعدة آمنة لإرجاع اللون والأيقونة المقابلة لحالة الحضور بصيغة نصية
 */
fun getStatusColorAndIcon(status: String): Pair<Color, ImageVector> {
    return when (status.lowercase()) {
        "present" -> Color(0xFF2E7D32) to Icons.Default.Check
        "late" -> Color(0xFFFFB300) to Icons.Default.AccessTime
        "very_late", "verylate" -> Color(0xFFE64A19) to Icons.Default.HourglassBottom
        "absent_excused" -> Color(0xFFF57C00) to Icons.Default.Info
        "absent_unexcused" -> Color(0xFFD32F2F) to Icons.Default.Close
        "unrecorded", "—", "-" -> Color(0xFF9E9E9E) to Icons.Default.Remove
        else -> Color.Gray to Icons.Default.HelpOutline
    }
}

/**
 * دالة مساعدة آمنة لإرجاع اللون والأيقونة المقابلة لحالة الحضور بصيغة كائن التعداد
 */
fun getStatusColorAndIcon(status: AttendanceStatus?): Pair<Color, ImageVector> {
    return when (status) {
        AttendanceStatus.PRESENT -> Color(0xFF2E7D32) to Icons.Default.Check
        AttendanceStatus.LATE -> Color(0xFFFFB300) to Icons.Default.AccessTime
        AttendanceStatus.VERY_LATE -> Color(0xFFE64A19) to Icons.Default.HourglassBottom
        AttendanceStatus.ABSENT_EXCUSED -> Color(0xFFF57C00) to Icons.Default.Info
        AttendanceStatus.ABSENT_UNEXCUSED -> Color(0xFFD32F2F) to Icons.Default.Close
        null -> Color(0xFF9E9E9E) to Icons.Default.Remove
        else -> Color.Gray to Icons.Default.HelpOutline
    }
}

/**
 * خلية جدول آمنة وبسيطة بدون استخدام الأوزان (Weights) لتفادي أي انهيار في قيود الأبعاد
 */
@Composable
fun TableCell(
    width: Dp,
    isHeader: Boolean = false,
    text: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .width(width)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            .background(if (isHeader) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (text != null) {
            Text(
                text = text,
                style = if (isHeader) MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodySmall,
                maxLines = 2,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis
            )
        } else if (content != null) {
            content()
        }
    }
}

/**
 * شريط دليل الرموز التوضيحي الآمن للجدول
 */
@Composable
fun LegendRow(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(id = R.string.register_legend_title),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val (presentColor, presentIcon) = getStatusColorAndIcon("present")
                LegendItem(icon = presentIcon, iconTint = presentColor, label = stringResource(id = R.string.register_legend_present))

                val (lateColor, lateIcon) = getStatusColorAndIcon("late")
                LegendItem(icon = lateIcon, iconTint = lateColor, label = stringResource(id = R.string.register_legend_late))

                val (veryLateColor, veryLateIcon) = getStatusColorAndIcon("very_late")
                LegendItem(icon = veryLateIcon, iconTint = veryLateColor, label = stringResource(id = R.string.attendance_status_very_late))

                val (excusedColor, excusedIcon) = getStatusColorAndIcon("absent_excused")
                LegendItem(icon = excusedIcon, iconTint = excusedColor, label = stringResource(id = R.string.register_legend_absent_excused))

                val (unexcusedColor, unexcusedIcon) = getStatusColorAndIcon("absent_unexcused")
                LegendItem(icon = unexcusedIcon, iconTint = unexcusedColor, label = stringResource(id = R.string.register_legend_absent_unexcused))

                val (unrecColor, unrecIcon) = getStatusColorAndIcon("unrecorded")
                LegendItem(icon = unrecIcon, iconTint = unrecColor, label = stringResource(id = R.string.register_legend_unrecorded))
            }
            Text(
                text = stringResource(id = R.string.register_total_legend),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LegendItem(
    label: String,
    symbol: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(16.dp)
            )
        } else if (symbol != null) {
            Text(text = symbol, style = MaterialTheme.typography.labelSmall)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
