package com.example.presentation.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.presentation.common.CustomTopAppBar
import kotlinx.coroutines.launch

/**
 * الشاشة الرئيسية لقسم التقارير العامة
 * تعرض 5 بطاقات تقارير كشفية متكاملة
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralReportsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAttendanceReport: () -> Unit,
    onNavigateToActivitiesReport: () -> Unit,
    onNavigateToProgressReport: () -> Unit = {},
    onNavigateToEvaluationMatrix: () -> Unit = {},
    onNavigateToLeadersEvaluationReport: () -> Unit = {},
    onNavigateToAuditLog: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CustomTopAppBar(
                title = stringResource(id = R.string.general_reports_title),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("reports_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.back_action),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // البطاقة 1: تقرير الحضور الشامل
            item {
                ReportCategoryCard(
                    title = stringResource(id = R.string.report_comprehensive_attendance),
                    description = stringResource(id = R.string.report_comprehensive_attendance_desc),
                    icon = Icons.Default.CheckCircle,
                    iconContainerColor = Color(0xFF2E7D32).copy(alpha = 0.18f),
                    iconTint = Color(0xFF2E7D32),
                    badgeText = "جاهز",
                    badgeColor = Color(0xFF2E7D32),
                    testTag = "report_card_attendance",
                    onClick = onNavigateToAttendanceReport
                )
            }

            // البطاقة 2: تقرير النشاط العام
            item {
                ReportCategoryCard(
                    title = stringResource(id = R.string.report_general_activity),
                    description = stringResource(id = R.string.report_general_activity_desc),
                    icon = Icons.Default.Event,
                    iconContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    iconTint = MaterialTheme.colorScheme.primary,
                    badgeText = "جاهز",
                    badgeColor = MaterialTheme.colorScheme.primary,
                    testTag = "report_card_activities",
                    onClick = onNavigateToActivitiesReport
                )
            }

            // البطاقة 3: تقرير التقدم الشامل
            item {
                ReportCategoryCard(
                    title = stringResource(id = R.string.report_comprehensive_progress),
                    description = stringResource(id = R.string.report_comprehensive_progress_desc),
                    icon = Icons.Default.WorkspacePremium,
                    iconContainerColor = Color(0xFFF57C00).copy(alpha = 0.18f),
                    iconTint = Color(0xFFF57C00),
                    badgeText = "جاهز",
                    badgeColor = Color(0xFFF57C00),
                    testTag = "report_card_progress",
                    onClick = onNavigateToProgressReport
                )
            }

            // البطاقة 4: تقرير التقييم العام
            item {
                ReportCategoryCard(
                    title = stringResource(id = R.string.report_general_evaluation),
                    description = stringResource(id = R.string.report_general_evaluation_desc),
                    icon = Icons.Default.Assessment,
                    iconContainerColor = Color(0xFF7B1FA2).copy(alpha = 0.18f),
                    iconTint = Color(0xFF7B1FA2),
                    badgeText = "جاهز",
                    badgeColor = Color(0xFF7B1FA2),
                    testTag = "report_card_evaluation",
                    onClick = onNavigateToEvaluationMatrix
                )
            }

            // البطاقة 5: تقرير تقييم القادة
            item {
                ReportCategoryCard(
                    title = stringResource(id = R.string.report_leaders_evaluation),
                    description = stringResource(id = R.string.report_leaders_evaluation_desc),
                    icon = Icons.Default.Group,
                    iconContainerColor = Color(0xFF00838F).copy(alpha = 0.18f),
                    iconTint = Color(0xFF00838F),
                    badgeText = "جاهز",
                    badgeColor = Color(0xFF00838F),
                    testTag = "report_card_leaders",
                    onClick = onNavigateToLeadersEvaluationReport
                )
            }

            // البطاقة 6: سجل العمليات
            item {
                ReportCategoryCard(
                    title = stringResource(id = R.string.report_audit_log),
                    description = stringResource(id = R.string.report_audit_log_desc),
                    icon = Icons.Default.History,
                    iconContainerColor = Color(0xFF455A64).copy(alpha = 0.18f),
                    iconTint = Color(0xFF455A64),
                    badgeText = "جاهز",
                    badgeColor = Color(0xFF455A64),
                    testTag = "report_card_audit_log",
                    onClick = onNavigateToAuditLog
                )
            }
        }
    }
}

/**
 * عنصر بطاقة تقرير كشفي بتصميم أنيق وتفاعلي
 */
@Composable
private fun ReportCategoryCard(
    title: String,
    description: String,
    icon: ImageVector,
    iconContainerColor: Color,
    iconTint: Color,
    badgeText: String,
    badgeColor: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // حاوية الأيقونة
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(iconContainerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // النصوص والمعلومات
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    // شارة الحالة
                    Surface(
                        color = badgeColor.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // سهم التنقل
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
