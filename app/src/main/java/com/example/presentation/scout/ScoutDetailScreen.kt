package com.example.presentation.scout

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.School
import com.example.presentation.common.AppAlertDialog
import com.example.presentation.common.AppModalBottomSheet
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
import androidx.compose.material3.Scaffold
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
import kotlinx.coroutines.launch
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.attendance.AttendanceStatus
import com.example.domain.scout.Scout
import com.example.presentation.common.ResponsiveInfoRow
import com.example.presentation.reports.PdfExporter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * شاشة تفاصيل ملف الشبل (Scout Detail Screen)
 * تعرض البيانات المنقحة وإحصائيات الحضور وزر الاتصال المباشر وتصدير تقرير PDF.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoutDetailScreen(
    scoutId: String,
    viewModel: ScoutViewModel,
    onNavigateToEdit: (String) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToProgress: (String) -> Unit = {},
    onNavigateToHobbyBadges: (String) -> Unit = {},
    onNavigateToInfo: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val detailState by viewModel.detailUiState.collectAsStateWithLifecycle()
    val stats by viewModel.scoutStats.collectAsStateWithLifecycle()
    val activities by viewModel.activities.collectAsStateWithLifecycle()
    val badgeProgressList by viewModel.badgeProgressList.collectAsStateWithLifecycle()
    val hobbyProgressList by viewModel.hobbyBadgeProgressList.collectAsStateWithLifecycle()
    val scoutDebt by viewModel.scoutDebt.collectAsStateWithLifecycle()
    val scoutSubscriptions by viewModel.scoutSubscriptions.collectAsStateWithLifecycle()
    val progressMessage by viewModel.progressMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showArchiveDialog by remember { mutableStateOf(false) }
    var showUnarchiveDialog by remember { mutableStateOf(false) }
    var showMissedActivitiesSheet by remember { mutableStateOf(false) }

    LaunchedEffect(scoutId) {
        viewModel.loadScoutDetail(scoutId)
    }

    LaunchedEffect(progressMessage) {
        progressMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearProgressMessage()
        }
    }

    LaunchedEffect(detailState) {
        if (detailState is ScoutDetailUiState.Deleted) {
            onNavigateBack()
        }
    }

    val dateFormatter = remember { SimpleDateFormat("yyyy/MM/dd", com.example.presentation.common.AppLocale.AR_DZ) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        topBar = {
            CustomTopAppBar(
                title = stringResource(id = R.string.scout_detail_title),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("scout_detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.cancel_action),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    if (detailState is ScoutDetailUiState.Success) {
                        val scout = (detailState as ScoutDetailUiState.Success).scout
                        // زر التعديل
                        IconButton(
                            onClick = { onNavigateToEdit(scoutId) },
                            modifier = Modifier.testTag("edit_scout_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(id = R.string.edit_scout_title),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // زر الأرشفة أو إلغاء الأرشفة
                        if (scout.isActive) {
                            IconButton(
                                onClick = { showArchiveDialog = true },
                                modifier = Modifier.testTag("archive_scout_topbar_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Archive,
                                    contentDescription = stringResource(id = R.string.action_archive_scout),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            IconButton(
                                onClick = { showUnarchiveDialog = true },
                                modifier = Modifier.testTag("unarchive_scout_topbar_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Unarchive,
                                    contentDescription = stringResource(id = R.string.action_unarchive_scout),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // زر الحذف
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.testTag("delete_scout_topbar_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(id = R.string.action_delete_scout),
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
                is ScoutDetailUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .testTag("detail_loading_indicator"),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                is ScoutDetailUiState.Error -> {
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
                        Button(onClick = { viewModel.loadScoutDetail(scoutId) }) {
                            Text("إعادة المحاولة")
                        }
                    }
                }

                is ScoutDetailUiState.Success -> {
                    val scout = state.scout
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // ترويسة ملف الشبل مع الاسم الكامل والحالة الكشفية
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
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Face,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    // الاسم واللقب
                                    Text(
                                        text = scout.fullName,
                                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = stringResource(id = R.string.age_years_format, scout.age),
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }

                                        Surface(
                                            color = if (scout.isActive) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outline,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = if (scout.isActive) stringResource(id = R.string.status_active)
                                                else stringResource(id = R.string.status_archived),
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // بطاقة معلومات الشبل القابلة للنقر (تفتح شاشة معلومات الشبل الكاملة)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToInfo(scout.id) }
                                .testTag("scout_info_card"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(id = R.string.scout_info_card_title),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    val previewText = buildString {
                                        append(scout.fullName)
                                        append(" • ")
                                        append(stringResource(id = R.string.age_years_format, scout.age))
                                        if (scout.guardianPhone.isNotBlank()) {
                                            append(" • ")
                                            append(scout.guardianPhone)
                                        }
                                    }
                                    Text(
                                        text = previewText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // بطاقة إحصائيات الحضور الكشفي
                        DetailCategoryCard(title = stringResource(id = R.string.attendance_stats_title)) {
                            val currentStats = stats
                            if (currentStats != null) {
                                val percentage = currentStats.attendancePercentage
                                val percentageColor = when {
                                    percentage >= 80.0 -> Color(0xFF2E7D32)
                                    percentage >= 50.0 -> Color(0xFFF57C00)
                                    else -> MaterialTheme.colorScheme.error
                                }
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "${percentage.toInt()}%",
                                        style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                                        color = percentageColor
                                    )
                                    Text(
                                        text = stringResource(id = R.string.attendance_percentage_label),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    StatTile(
                                        label = stringResource(id = R.string.stat_present_label),
                                        count = currentStats.presentCount,
                                        color = Color(0xFF2E7D32),
                                        modifier = Modifier.weight(1f).testTag("stat_present")
                                    )
                                    StatTile(
                                        label = stringResource(id = R.string.stat_late_label),
                                        count = currentStats.lateCount,
                                        color = Color(0xFFF57C00),
                                        modifier = Modifier.weight(1f).testTag("stat_late")
                                    )
                                    StatTile(
                                        label = stringResource(id = R.string.stat_very_late_label),
                                        count = currentStats.veryLateCount,
                                        color = Color(0xFFE64A19),
                                        modifier = Modifier.weight(1f).testTag("stat_very_late")
                                    )
                                    StatTile(
                                        label = stringResource(id = R.string.stat_absent_excused_label),
                                        count = currentStats.absentExcusedCount,
                                        color = Color(0xFF0288D1),
                                        modifier = Modifier.weight(1f).testTag("stat_absent_excused")
                                    )
                                    StatTile(
                                        label = stringResource(id = R.string.stat_absent_unexcused_label),
                                        count = currentStats.absentUnexcusedCount,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.weight(1f).testTag("stat_absent_unexcused")
                                    )
                                }

                                if (currentStats.totalPoints > 0) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        ResponsiveInfoRow(
                                            label = stringResource(id = R.string.stat_total_points_label),
                                            value = "${currentStats.totalPoints} نقطة",
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                            valueColor = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                TextButton(
                                    onClick = { showMissedActivitiesSheet = true },
                                    modifier = Modifier.fillMaxWidth().testTag("show_missed_activities_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.EventBusy,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(id = R.string.action_show_missed_activities))
                                }
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "جاري تحميل إحصائيات الحضور...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // بطاقة ملخص بطاقة التقدم (شارات الكفاءة الأربعة) - قابلة للنقر لفتح شاشة التفاصيل
                        ScoutProgressSummaryCard(
                            badgeProgressList = badgeProgressList,
                            onClick = { onNavigateToProgress(scout.id) }
                        )

                        // بطاقة ملخص شارات الهواية - قابلة للنقر لفتح شاشة شارات الهواية
                        ScoutHobbyBadgesSummaryCard(
                            hobbyProgressList = hobbyProgressList,
                            onClick = { onNavigateToHobbyBadges(scout.id) }
                        )

                        // بطاقة ملخص الديون المالية للشبل
                        ScoutDebtSummaryCard(
                            debt = scoutDebt,
                            subscriptions = scoutSubscriptions
                        )

                        // زر مشاركة كـ PDF
                        Button(
                            onClick = {
                                val currentStats = stats ?: com.example.domain.attendance.ScoutStatistics(
                                    totalActivities = 0,
                                    presentCount = 0,
                                    lateCount = 0,
                                    veryLateCount = 0,
                                    absentExcusedCount = 0,
                                    absentUnexcusedCount = 0,
                                    attendancePercentage = 0.0,
                                    totalPoints = 0,
                                    missedActivities = emptyList()
                                )
                                val file = PdfExporter.exportScoutReport(
                                    scout = scout,
                                    stats = currentStats,
                                    activities = activities,
                                    context = context
                                )
                                PdfExporter.shareScoutPdf(file, scout.fullName, context)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("share_scout_pdf_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(id = R.string.action_share_pdf),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        // تذييل الصفحة (تواريخ الإضافة والتعديل)
                        Column(
                            modifier = Modifier
                                 .fillMaxWidth()
                                 .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (scout.createdAt > 0) {
                                val formattedCreated = try {
                                    dateFormatter.format(Date(scout.createdAt))
                                } catch (_: Exception) { "" }
                                if (formattedCreated.isNotBlank()) {
                                    Text(
                                        text = stringResource(
                                            id = R.string.created_at_format,
                                            formattedCreated
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (scout.updatedAt > 0) {
                                val formattedUpdated = try {
                                    dateFormatter.format(Date(scout.updatedAt))
                                } catch (_: Exception) { "" }
                                if (formattedUpdated.isNotBlank()) {
                                    Text(
                                        text = stringResource(
                                            id = R.string.updated_at_format,
                                            formattedUpdated
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                is ScoutDetailUiState.Deleted -> {
                    // تم الرجوع تلقائياً عبر LaunchedEffect
                }
            }
        }
    }

    // مربع حوار تأكيد الحذف
    if (showDeleteDialog) {
        val scoutName = (detailState as? ScoutDetailUiState.Success)?.scout?.fullName ?: ""
        AppAlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    text = stringResource(id = R.string.action_delete_scout),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Text(
                    text = stringResource(id = R.string.confirm_delete_scout_body_format, scoutName),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteScout(scoutId) {
                            onNavigateBack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_delete_scout_button")
                ) {
                    Text(stringResource(id = R.string.action_delete))
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

    // مربع حوار تأكيد الأرشفة
    if (showArchiveDialog) {
        val scoutName = (detailState as? ScoutDetailUiState.Success)?.scout?.fullName ?: ""
        AppAlertDialog(
            onDismissRequest = { showArchiveDialog = false },
            title = {
                Text(
                    text = stringResource(id = R.string.action_archive_scout),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = stringResource(id = R.string.confirm_archive_scout_body_format, scoutName),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showArchiveDialog = false
                        viewModel.archiveScout(scoutId) {
                            onNavigateBack()
                        }
                    },
                    modifier = Modifier.testTag("confirm_archive_scout_button")
                ) {
                    Text(stringResource(id = R.string.action_archive))
                }
            },
            dismissButton = {
                TextButton(onClick = { showArchiveDialog = false }) {
                    Text(stringResource(id = R.string.cancel_action))
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    // مربع حوار تأكيد إلغاء الأرشفة
    if (showUnarchiveDialog) {
        AppAlertDialog(
            onDismissRequest = { showUnarchiveDialog = false },
            title = {
                Text(
                    text = stringResource(id = R.string.action_unarchive_scout),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = "هل ترغب في إعادة هذا الشبل إلى القائمة النشطة؟",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUnarchiveDialog = false
                        viewModel.unarchiveScout(scoutId) {
                            onNavigateBack()
                        }
                    },
                    modifier = Modifier.testTag("confirm_unarchive_scout_button")
                ) {
                    Text(stringResource(id = R.string.confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnarchiveDialog = false }) {
                    Text(stringResource(id = R.string.cancel_action))
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    // أسفل الشاشة المنبثق لعرض الأنشطة الفائتة
    if (showMissedActivitiesSheet) {
        AppModalBottomSheet(
            onDismissRequest = { showMissedActivitiesSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.EventBusy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(id = R.string.action_show_missed_activities),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                val missedList = stats?.missedActivities ?: emptyList()
                if (missedList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(id = R.string.no_missed_activities),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(items = missedList, key = { it.activityId }) { item ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (item.date > 0) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            val formattedDate = try {
                                                dateFormatter.format(Date(item.date))
                                            } catch (_: Exception) { "" }
                                            if (formattedDate.isNotBlank()) {
                                                Text(
                                                    text = formattedDate,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                                )
                                            }
                                        }
                                    }

                                    Surface(
                                        color = if (item.status == AttendanceStatus.ABSENT_EXCUSED)
                                            Color(0xFF0288D1)
                                        else
                                            MaterialTheme.colorScheme.error,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = item.status.arabicLabel,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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

@Composable
private fun DetailCategoryCard(
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
private fun DetailRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        ResponsiveInfoRow(
            label = label,
            value = value,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatTile(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = color
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                maxLines = 2,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ScoutHobbyBadgesSummaryCard(
    hobbyProgressList: List<com.example.domain.hobby.HobbyBadgeProgress>,
    onClick: () -> Unit
) {
    val grantedList = hobbyProgressList.filter { it.isGranted }
    val grantedCount = grantedList.size

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("scout_hobby_summary_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MilitaryTech,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(id = R.string.hobby_scout_summary_card_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = String.format(
                                com.example.presentation.common.AppLocale.AR_DZ,
                                stringResource(id = R.string.hobby_scout_summary_count_format),
                                grantedCount
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (grantedCount > 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (grantedCount > 0) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3-line preview of last granted badges (or empty message)
            if (grantedList.isNotEmpty()) {
                val previewList = grantedList.sortedByDescending { it.grantedAt ?: 0L }.take(3)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    previewList.forEach { badgeProgress ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = com.example.domain.hobby.HobbyBadgeIcon.resolveIcon(badgeProgress.badge.iconKey),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = badgeProgress.badge.name,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = stringResource(id = R.string.hobby_scout_summary_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

/**
 * بطاقة ملخص الديون المالية للشبل (ScoutDebtSummaryCard)
 * بطاقة قابلة للطي تعرض إجمالي الديون وقائمة بالأنشطة غير المسددة.
 */
@Composable
private fun ScoutDebtSummaryCard(
    debt: com.example.domain.finance.ScoutDebt?,
    subscriptions: List<com.example.domain.finance.FinanceSubscription>,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    val totalDebt = debt?.totalDebt ?: subscriptions.sumOf { it.debtAmount }
    val hasSubscriptions = subscriptions.isNotEmpty()
    val unpaidSubs = subscriptions.filter { it.debtAmount > 0 }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded }
            .testTag("scout_debt_summary_card"),
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = stringResource(id = R.string.scout_debt_card_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!hasSubscriptions) {
                            Text(
                                text = stringResource(id = R.string.scout_debt_none),
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                color = MaterialTheme.colorScheme.outline
                            )
                        } else {
                            Text(
                                text = if (totalDebt > 0) {
                                    stringResource(id = R.string.scout_debt_total_format, totalDebt)
                                } else {
                                    stringResource(id = R.string.scout_debt_paid_full)
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (totalDebt > 0) MaterialTheme.colorScheme.error else if (isDark) Color(0xFFA5D6A7) else Color(0xFF1B5E20)
                            )
                        }
                    }
                }

                Surface(
                    color = if (!hasSubscriptions) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else if (totalDebt > 0) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        if (isDark) Color(0xFF1B4A2A) else Color(0xFFE8F5E9)
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (!hasSubscriptions) {
                            "0"
                        } else if (totalDebt > 0) {
                            String.format(com.example.presentation.common.AppLocale.AR_DZ, "%d دج", totalDebt)
                        } else {
                            stringResource(id = R.string.scout_debt_paid_full)
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (!hasSubscriptions) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else if (totalDebt > 0) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            if (isDark) Color(0xFFA5D6A7) else Color(0xFF1B5E20)
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // المحتوى الموسع عند النقر
            androidx.compose.animation.AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    if (!hasSubscriptions) {
                        Text(
                            text = stringResource(id = R.string.scout_debt_none),
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = MaterialTheme.colorScheme.outline
                        )
                    } else if (unpaidSubs.isEmpty()) {
                        Text(
                            text = "لا توجد ديون متبقية، تم تسديد جميع الاشتراكات بالكامل.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDark) Color(0xFFA5D6A7) else Color(0xFF1B5E20),
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        val dateFormatter = java.text.SimpleDateFormat("yyyy/MM/dd", com.example.presentation.common.AppLocale.AR_DZ)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            unpaidSubs.forEach { sub ->
                                val dateStr = if (sub.activityDate > 0) {
                                    dateFormatter.format(java.util.Date(sub.activityDate))
                                } else {
                                    "—"
                                }
                                Text(
                                    text = stringResource(
                                        id = R.string.scout_debt_item_format,
                                        sub.activityTitle.ifBlank { "نشاط تدريبي" },
                                        dateStr,
                                        sub.debtAmount
                                    ),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}



