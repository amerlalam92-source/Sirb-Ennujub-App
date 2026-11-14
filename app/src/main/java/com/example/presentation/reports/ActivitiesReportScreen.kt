package com.example.presentation.reports

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.presentation.common.AppLocale
import com.example.presentation.common.CustomTopAppBar
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date

/**
 * شاشة تقرير النشاط العام
 * بطاقة ملخص عامة في الأعلى متبوعة بجدول إحصائيات كل نشاط كشفي
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivitiesReportScreen(
    viewModel: GeneralReportsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isExportingPdf by viewModel.isExportingPdf.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CustomTopAppBar(
                title = stringResource(id = R.string.report_general_activity),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("activities_report_back_button")
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
                                viewModel.exportAndShareActivitiesReport(context)
                            },
                            modifier = Modifier.testTag("action_share_activities_pdf")
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
                    // 1. بطاقة الملخص الإحصائي العام
                    ActivitiesSummaryCard(
                        totalActivities = uiState.pastActivities.size,
                        totalAttendances = uiState.totalAttendancesCount,
                        avgAttendanceRate = uiState.averageAttendanceRate
                    )

                    // 2. جدول إحصائيات الأنشطة
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
                                // صف رؤوس الأعمدة
                                Row(
                                    modifier = Modifier.height(IntrinsicSize.Min)
                                ) {
                                    // 1. اسم النشاط
                                    ReportTableCell(
                                        text = stringResource(id = R.string.report_column_activity_title),
                                        width = 140.dp,
                                        isHeader = true
                                    )

                                    // 2. نوع النشاط
                                    ReportTableCell(
                                        text = stringResource(id = R.string.report_column_activity_type),
                                        width = 90.dp,
                                        isHeader = true
                                    )

                                    // 3. التاريخ
                                    ReportTableCell(
                                        text = stringResource(id = R.string.report_column_date),
                                        width = 85.dp,
                                        isHeader = true
                                    )

                                    // 4. عدد الحاضرين
                                    ReportTableCell(
                                        text = stringResource(id = R.string.report_column_attendees_count),
                                        width = 80.dp,
                                        isHeader = true
                                    )

                                    // 5. عدد الغائبين
                                    ReportTableCell(
                                        text = stringResource(id = R.string.report_column_absents_count),
                                        width = 80.dp,
                                        isHeader = true
                                    )

                                    // 6. نسبة الحضور
                                    ReportTableCell(
                                        text = stringResource(id = R.string.report_column_attendance_rate),
                                        width = 80.dp,
                                        isHeader = true
                                    )
                                }

                                // صفوف بيانات الأنشطة (مرتبة بالأحدث أولاً)
                                uiState.pastActivitiesDescending.forEach { activity ->
                                    val stats = uiState.getActivityStats(activity.id)
                                    val dateStr = if (activity.startDate > 0) {
                                        dateFormat.format(Date(activity.startDate))
                                    } else {
                                        "—"
                                    }
                                    val rateFormatted = String.format(AppLocale.AR_DZ, "%.0f%%", stats.attendanceRate)

                                    Row(
                                        modifier = Modifier.height(IntrinsicSize.Min)
                                    ) {
                                        // اسم النشاط
                                        ReportTableCell(
                                            text = activity.title,
                                            width = 140.dp,
                                            isHeader = false
                                        )

                                        // نوع النشاط
                                        ReportTableCell(
                                            text = activity.activityType.arabicLabel,
                                            width = 90.dp,
                                            isHeader = false
                                        )

                                        // التاريخ
                                        ReportTableCell(
                                            text = dateStr,
                                            width = 85.dp,
                                            isHeader = false
                                        )

                                        // عدد الحاضرين
                                        ReportTableCell(
                                            text = stats.attendeesCount.toString(),
                                            width = 80.dp,
                                            isHeader = false
                                        )

                                        // عدد الغائبين
                                        ReportTableCell(
                                            text = stats.absentsCount.toString(),
                                            width = 80.dp,
                                            isHeader = false
                                        )

                                        // نسبة الحضور
                                        ReportTableCell(
                                            text = rateFormatted,
                                            width = 80.dp,
                                            isHeader = false
                                        )
                                    }
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
 * بطاقة الملخص الإحصائي التجميعي للأنشطة
 */
@Composable
private fun ActivitiesSummaryCard(
    totalActivities: Int,
    totalAttendances: Int,
    avgAttendanceRate: Double,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "ملخص المؤشرات العامة للأنشطة",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // مؤشر 1: إجمالي الأنشطة
                SummaryMetricItem(
                    title = stringResource(id = R.string.report_total_activities),
                    value = totalActivities.toString(),
                    icon = Icons.Default.Event,
                    iconTint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )

                // مؤشر 2: إجمالي الحضور
                SummaryMetricItem(
                    title = stringResource(id = R.string.report_total_attendees),
                    value = totalAttendances.toString(),
                    icon = Icons.Default.Group,
                    iconTint = Color(0xFF2E7D32),
                    modifier = Modifier.weight(1f)
                )

                // مؤشر 3: متوسط نسبة الحضور
                SummaryMetricItem(
                    title = stringResource(id = R.string.report_avg_attendance_rate),
                    value = String.format(AppLocale.AR_DZ, "%.0f%%", avgAttendanceRate),
                    icon = Icons.Default.Insights,
                    iconTint = Color(0xFFF57C00),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SummaryMetricItem(
    title: String,
    value: String,
    icon: ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}
