package com.example.presentation.reports

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.attendance.AttendanceStatus
import com.example.presentation.common.AppLocale
import com.example.presentation.common.CustomTopAppBar
import com.example.presentation.common.LocalAppTerms
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date

/**
 * شاشة تقرير الحضور الشامل
 * مصفوفة بيانية كاملة (جدول الحضور) لجميع الأشبال النشطين عبر كافة الأنشطة السابقة
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceReportScreen(
    viewModel: GeneralReportsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isExportingPdf by viewModel.isExportingPdf.collectAsStateWithLifecycle()
    val terms = LocalAppTerms.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CustomTopAppBar(
                title = stringResource(id = R.string.report_comprehensive_attendance),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("attendance_report_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.back_action),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    if (isExportingPdf) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .size(22.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        IconButton(
                            onClick = {
                                viewModel.exportAndShareAttendanceReport(context)
                            },
                            modifier = Modifier.testTag("action_share_attendance_pdf")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringResource(id = R.string.action_share_pdf),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.scouts.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "لا يوجد ${terms.scoutPlural} نشطون لعرض التقرير",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            uiState.pastActivities.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(id = R.string.report_empty_activities),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            else -> {
                val horizontalScrollState = rememberScrollState()
                val verticalScrollState = rememberScrollState()
                val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", AppLocale.AR_DZ) }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(verticalScrollState)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // جدول المصفوفة البيانية الشاملة
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(horizontalScrollState)
                        ) {
                            Column {
                                // 1. الصف الرأسي لعناوين الأعمدة
                                Row(
                                    modifier = Modifier.height(IntrinsicSize.Min)
                                ) {
                                    // عمود اسم الشبل
                                    ReportTableCell(
                                        text = "اسم ${terms.scoutSingularDefinite}",
                                        width = 140.dp,
                                        isHeader = true
                                    )

                                    // أعمدة الأنشطة السابقة
                                    uiState.pastActivities.forEach { activity ->
                                        val dateStr = if (activity.startDate > 0) {
                                            dateFormat.format(Date(activity.startDate))
                                        } else {
                                            "—"
                                        }
                                        ReportTableCell(
                                            width = 120.dp,
                                            isHeader = true,
                                            content = {
                                                Column(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    Text(
                                                        text = activity.title,
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                        textAlign = TextAlign.Center,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = dateStr,
                                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        textAlign = TextAlign.Center,
                                                        maxLines = 1
                                                    )
                                                    Spacer(modifier = Modifier.height(1.dp))
                                                    Text(
                                                        text = activity.activityType.displayLabel(terms),
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontSize = 9.sp,
                                                            color = MaterialTheme.colorScheme.primary
                                                        ),
                                                        textAlign = TextAlign.Center,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        )
                                    }

                                    // عمود إجمالي الحضور
                                    ReportTableCell(
                                        text = stringResource(id = R.string.report_column_total_attendance),
                                        width = 80.dp,
                                        isHeader = true
                                    )

                                    // عمود النسبة
                                    ReportTableCell(
                                        text = stringResource(id = R.string.report_column_percentage),
                                        width = 80.dp,
                                        isHeader = true
                                    )
                                }

                                // 2. صفوف الأشبال
                                uiState.scouts.forEach { scout ->
                                    Row(
                                        modifier = Modifier.height(IntrinsicSize.Min)
                                    ) {
                                        // اسم الشبل
                                        ReportTableCell(
                                            text = scout.fullName,
                                            width = 140.dp,
                                            isHeader = false
                                        )

                                        // خلايا الأنشطة
                                        uiState.pastActivities.forEach { activity ->
                                            val record = uiState.getAttendance(activity.id, scout.id)
                                            val (color, icon) = getAttendanceStatusColorAndIcon(record?.attendanceStatus)

                                            ReportTableCell(
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

                                        // إجمالي الحضور
                                        val totalAttended = uiState.getScoutTotalAttendance(scout.id)
                                        ReportTableCell(
                                            text = totalAttended.toString(),
                                            width = 80.dp,
                                            isHeader = false
                                        )

                                        // النسبة المئوية
                                        val percentage = uiState.getScoutAttendancePercentage(scout.id)
                                        val pctFormatted = String.format(AppLocale.AR_DZ, "%.0f%%", percentage)
                                        ReportTableCell(
                                            text = pctFormatted,
                                            width = 80.dp,
                                            isHeader = false
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // شريط دليل الرموز في الأسفل
                    AttendanceMatrixLegend()
                }
            }
        }
    }
}

/**
 * خلية جدول بتصميم مرن وحدود واضحة
 */
@Composable
fun ReportTableCell(
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
 * إرجاع اللون والأيقونة الخاصة بحالة الحضور
 */
fun getAttendanceStatusColorAndIcon(status: AttendanceStatus?): Pair<Color, ImageVector> {
    return when (status) {
        AttendanceStatus.PRESENT -> Color(0xFF2E7D32) to Icons.Default.Check
        AttendanceStatus.LATE -> Color(0xFFFFB300) to Icons.Default.AccessTime
        AttendanceStatus.VERY_LATE -> Color(0xFFE64A19) to Icons.Default.HourglassBottom
        AttendanceStatus.ABSENT_EXCUSED -> Color(0xFFF57C00) to Icons.Default.Info
        AttendanceStatus.ABSENT_UNEXCUSED -> Color(0xFFD32F2F) to Icons.Default.Close
        null -> Color(0xFF9E9E9E) to Icons.Default.Remove
    }
}

/**
 * شريط دليل الرموز التوضيحي لحالات الحضور
 */
@Composable
fun AttendanceMatrixLegend(modifier: Modifier = Modifier) {
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
                val (presentColor, presentIcon) = getAttendanceStatusColorAndIcon(AttendanceStatus.PRESENT)
                MatrixLegendItem(icon = presentIcon, iconTint = presentColor, label = stringResource(id = R.string.register_legend_present))

                val (lateColor, lateIcon) = getAttendanceStatusColorAndIcon(AttendanceStatus.LATE)
                MatrixLegendItem(icon = lateIcon, iconTint = lateColor, label = stringResource(id = R.string.register_legend_late))

                val (veryLateColor, veryLateIcon) = getAttendanceStatusColorAndIcon(AttendanceStatus.VERY_LATE)
                MatrixLegendItem(icon = veryLateIcon, iconTint = veryLateColor, label = stringResource(id = R.string.attendance_status_very_late))

                val (excusedColor, excusedIcon) = getAttendanceStatusColorAndIcon(AttendanceStatus.ABSENT_EXCUSED)
                MatrixLegendItem(icon = excusedIcon, iconTint = excusedColor, label = stringResource(id = R.string.register_legend_absent_excused))

                val (unexcusedColor, unexcusedIcon) = getAttendanceStatusColorAndIcon(AttendanceStatus.ABSENT_UNEXCUSED)
                MatrixLegendItem(icon = unexcusedIcon, iconTint = unexcusedColor, label = stringResource(id = R.string.register_legend_absent_unexcused))

                val (unrecColor, unrecIcon) = getAttendanceStatusColorAndIcon(null)
                MatrixLegendItem(icon = unrecIcon, iconTint = unrecColor, label = stringResource(id = R.string.register_legend_unrecorded))
            }
        }
    }
}

@Composable
private fun MatrixLegendItem(
    label: String,
    icon: ImageVector,
    iconTint: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
