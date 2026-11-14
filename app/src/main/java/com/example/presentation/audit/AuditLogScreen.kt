package com.example.presentation.audit

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HistoryToggleOff
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.log.ActivityLog
import com.example.presentation.common.LocalAppTerms
import com.example.domain.settings.replaceCustomWords
import com.example.presentation.common.AppAlertDialog
import com.example.presentation.common.AppLocale
import com.example.presentation.common.CustomTopAppBar
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

/**
 * شاشة سجل العمليات (Audit Log Screen)
 * تعرض كافة الأنشطة والتعديلات المنفذة مع خيارات التصفية بالوقت ونوع العملية والقائد،
 * وإمكانية تصدير السجل بصيغة PDF.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditLogScreen(
    viewModel: AuditLogViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CustomTopAppBar(
                title = stringResource(id = R.string.audit_log_title),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("audit_log_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.back_action),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    if (uiState.isExportingPdf) {
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
                                viewModel.exportAndSharePdf(context)
                            },
                            modifier = Modifier.testTag("action_share_audit_pdf")
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // شريط الفلاتر العلوي
            AuditFiltersSection(
                selectedDateFilter = uiState.selectedDateFilter,
                onDateFilterSelected = { viewModel.setDateFilter(it) },
                selectedActionFilter = uiState.selectedActionFilter,
                onActionFilterSelected = { viewModel.setActionFilter(it) },
                selectedLeader = uiState.selectedLeader,
                availableLeaders = uiState.availableLeaders,
                onLeaderSelected = { viewModel.setLeaderFilter(it) }
            )

            // محتوى القائمة أو حالة التحميل/الفراغ
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                uiState.filteredLogs.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.HistoryToggleOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(id = R.string.audit_empty),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = uiState.filteredLogs,
                            key = { it.id.ifBlank { "${it.timestamp}_${it.hashCode()}" } }
                        ) { log ->
                            AuditLogItemCard(
                                log = log,
                                onClick = { viewModel.onLogClicked(log) }
                            )
                        }
                    }
                }
            }
        }
    }

    // نافذة تفاصيل العملية المنبثقة
    if (uiState.selectedLog != null) {
        AuditLogDetailsDialog(
            log = uiState.selectedLog!!,
            onDismiss = { viewModel.onDismissLogDetails() }
        )
    }
}

/**
 * قسم الفلاتر الأنيق (فلاتر التاريخ، نوع العملية، والقائد)
 */
@Composable
private fun AuditFiltersSection(
    selectedDateFilter: DateFilter,
    onDateFilterSelected: (DateFilter) -> Unit,
    selectedActionFilter: ActionFilter,
    onActionFilterSelected: (ActionFilter) -> Unit,
    selectedLeader: String?,
    availableLeaders: List<String>,
    onLeaderSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. فلتر التاريخ
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DateFilter.entries.forEach { filter ->
                    val isSelected = selectedDateFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = { onDateFilterSelected(filter) },
                        label = {
                            Text(
                                text = stringResource(id = filter.labelResId),
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("filter_date_${filter.name.lowercase()}")
                    )
                }
            }

            // 2. فلتر نوع العملية
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionFilter.entries.forEach { filter ->
                    val isSelected = selectedActionFilter == filter
                    val chipColor = when (filter) {
                        ActionFilter.ALL -> MaterialTheme.colorScheme.primary
                        ActionFilter.ADD -> Color(0xFF2E7D32)
                        ActionFilter.UPDATE -> Color(0xFFF57C00)
                        ActionFilter.DELETE -> Color(0xFFD32F2F)
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = { onActionFilterSelected(filter) },
                        label = {
                            Text(
                                text = stringResource(id = filter.labelResId),
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = chipColor.copy(alpha = 0.18f),
                            selectedLabelColor = chipColor
                        ),
                        modifier = Modifier.testTag("filter_action_${filter.name.lowercase()}")
                    )
                }
            }

            // 3. فلتر القادة (إذا كان هناك أكثر من قائد)
            if (availableLeaders.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedLeader == null,
                        onClick = { onLeaderSelected(null) },
                        label = {
                            Text(
                                text = stringResource(id = R.string.audit_filter_leader_all),
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        modifier = Modifier.testTag("filter_leader_all")
                    )

                    availableLeaders.forEach { leader ->
                        FilterChip(
                            selected = selectedLeader == leader,
                            onClick = { onLeaderSelected(leader) },
                            label = {
                                Text(
                                    text = leader,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            modifier = Modifier.testTag("filter_leader_${leader.hashCode()}")
                        )
                    }
                }
            }
        }
    }
}

/**
 * بطاقة تمثيل عملية مسجلة في سجل العمليات
 */
@Composable
private fun AuditLogItemCard(
    log: ActivityLog,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val terms = LocalAppTerms.current
    val (actionArabic, icon, iconColor) = getActionVisuals(log.action)
    val leaderName = log.userName.ifBlank { "قائد كشفي" }
    val targetText = getTargetDisplayText(log, terms)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("audit_log_item_${log.id}")
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // أيقونة نوع العملية الملونة
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = actionArabic,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // بيانات العملية: القائد، العملية، والهدف
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(id = R.string.audit_log_item_format, leaderName, actionArabic, targetText),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = formatAuditTimestamp(log.timestamp),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // شارة نوع العنصر المستهدف
            val typeLabel = getTargetTypeLabel(log.targetType, terms)
            if (typeLabel.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 6.dp)
                ) {
                    Text(
                        text = typeLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

/**
 * نافذة حوار عرض تفاصيل العملية المسجلة
 */
@Composable
private fun AuditLogDetailsDialog(
    log: ActivityLog,
    onDismiss: () -> Unit
) {
    val terms = LocalAppTerms.current
    val (actionArabic, _, actionColor) = getActionVisuals(log.action)
    val targetTypeArabic = getTargetTypeTranslated(log.targetType)
    val formattedDateTime = formatAuditTimestampFull(log.timestamp)
    val leaderName = log.userName.ifBlank { "قائد كشفي" }
    val targetName = log.targetName.ifBlank { getTargetDisplayText(log, terms) }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(id = R.string.audit_details_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // القائد
                AuditDetailRow(
                    label = stringResource(id = R.string.audit_detail_leader),
                    value = leaderName
                )

                // نوع العملية
                AuditDetailRow(
                    label = stringResource(id = R.string.audit_detail_action),
                    value = actionArabic,
                    valueColor = actionColor
                )

                // العنصر المتأثر
                AuditDetailRow(
                    label = stringResource(id = R.string.audit_detail_target),
                    value = targetName
                )

                // نوع العنصر
                AuditDetailRow(
                    label = stringResource(id = R.string.audit_detail_type),
                    value = targetTypeArabic
                )

                // التاريخ والوقت
                AuditDetailRow(
                    label = stringResource(id = R.string.audit_detail_date),
                    value = formattedDateTime
                )

                // تفاصيل العملية إن وجدت
                // تفاصيل العملية إن وجدت
                if (!log.details.isNullOrBlank()) {
                    val isUpdateAction = log.action.lowercase() in listOf("update", "edit", "modify")
                    val borderColor = if (isUpdateAction) Color(0xFFF57C00).copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    val bgColor = if (isUpdateAction) Color(0xFFFFF8E1).copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = bgColor,
                        border = BorderStroke(1.dp, borderColor)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isUpdateAction) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        tint = Color(0xFFF57C00),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = if (isUpdateAction) "سجل التغييرات:" else stringResource(id = R.string.audit_detail_description),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (isUpdateAction) Color(0xFFE65100) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))

                            if (isUpdateAction && log.details.contains(" | ")) {
                                val diffs = log.details.split(" | ").map { it.trim() }.filter { it.isNotBlank() }
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    diffs.forEach { diff ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Text(
                                                text = "•",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                color = Color(0xFFF57C00),
                                                modifier = Modifier.padding(end = 6.dp)
                                            )
                                            Text(
                                                text = diff,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = log.details,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("audit_details_close_button")
            ) {
                Text(
                    text = stringResource(id = R.string.audit_dialog_close),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    )
}

/**
 * صف لعرض تفاصيل العملية داخل الحوار
 */
@Composable
private fun AuditDetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    isSecondary: Boolean = false
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = if (isSecondary) MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp) else MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = valueColor,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

/**
 * ترجمة نوع العنصر بالعربية للاستخدام في نافذة التفاصيل
 */
@Composable
private fun getTargetTypeTranslated(targetType: String): String {
    val type = targetType.lowercase().trim()
    return when {
        type.startsWith("finance") || type in listOf("transaction", "contribution", "subscription", "expense", "donation") ->
            stringResource(id = R.string.audit_target_finance)
        type.startsWith("scout") ->
            replaceCustomWords(stringResource(id = R.string.audit_target_scout), LocalAppTerms.current)
        type.startsWith("activity") ->
            stringResource(id = R.string.audit_target_activity)
        type.startsWith("attendance") ->
            stringResource(id = R.string.audit_target_attendance)
        type.startsWith("evaluation") ->
            stringResource(id = R.string.audit_target_evaluation)
        type.startsWith("progress") || type.startsWith("badge") ->
            stringResource(id = R.string.audit_target_progress)
        type.startsWith("hobby") ->
            stringResource(id = R.string.audit_target_hobby)
        type.startsWith("quran") ->
            stringResource(id = R.string.audit_target_quran)
        else ->
            stringResource(id = R.string.audit_target_other)
    }
}

/**
 * تحديد مظهر العملية (النص العربي، الأيقونة، واللون)
 */
@Composable
private fun getActionVisuals(action: String): Triple<String, ImageVector, Color> {
    return when (action.lowercase()) {
        "add", "create", "grant" -> Triple(stringResource(id = R.string.audit_action_add), Icons.Default.AddCircle, Color(0xFF2E7D32))
        "update", "edit", "modify" -> Triple(stringResource(id = R.string.audit_action_update), Icons.Default.Edit, Color(0xFFF57C00))
        "delete", "remove", "revoke" -> Triple(stringResource(id = R.string.audit_action_delete), Icons.Default.Delete, Color(0xFFD32F2F))
        "archive" -> Triple("أرشفة", Icons.Default.Edit, Color(0xFFE65100))
        "mark_done" -> Triple("إتمام", Icons.Default.AddCircle, Color(0xFF2E7D32))
        "cancel" -> Triple("إلغاء", Icons.Default.Delete, Color(0xFFD32F2F))
        "save_batch", "save" -> Triple("تسجيل حضور", Icons.Default.AddCircle, Color(0xFF1976D2))
        "manual_finance_adjustment" -> Triple("تعديل مالي يدوي", Icons.Default.Edit, Color(0xFFF57C00))
        else -> Triple("إجراء آخر", Icons.Default.History, Color(0xFF455A64))
    }
}

/**
 * نص نوع العنصر بالعربية
 */
private fun getTargetTypeLabel(targetType: String, terms: com.example.domain.settings.CustomTerms? = null): String {
    val scoutWord = terms?.scoutSingular ?: "شبل"
    val type = targetType.lowercase().trim()
    return when {
        type.startsWith("scout") -> scoutWord
        type.startsWith("activity") -> "نشاط"
        type.startsWith("attendance") -> "حضور"
        type.startsWith("finance") || type in listOf("transaction", "contribution", "subscription", "expense", "donation") -> "مالية"
        type.startsWith("evaluation") -> "تقييم"
        type.startsWith("progress") || type.startsWith("badge") -> "تقدم"
        type.startsWith("hobby") -> "هواية"
        type.startsWith("quran") -> "قرآن"
        else -> ""
    }
}

/**
 * استخراج اسم الهدف التوضيحي
 */
private fun getTargetDisplayText(log: ActivityLog, terms: com.example.domain.settings.CustomTerms? = null): String {
    val name = log.targetName.trim()
    if (name.isNotBlank()) return name

    val scoutWord = terms?.scoutSingular ?: "شبل"
    return when (log.targetType.lowercase()) {
        "scout" -> scoutWord
        "activity" -> "نشاط كشفي"
        "attendance" -> "سجل حضور"
        "finance" -> "معاملة مالية"
        "evaluation" -> "تقييم كشفي"
        "progress" -> "شارة كفاءة"
        "hobby" -> "شارة هواية"
        "quran" -> "المدرسة القرآنية"
        else -> "عنصر"
    }
}

/**
 * تنسيق الوقت والتاريخ بأسلوب عربي طبيعي (اليوم، أمس، أو التاريخ الكامل)
 */
private fun formatAuditTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return "—"

    val now = Calendar.getInstance()
    val timeCal = Calendar.getInstance().apply { timeInMillis = timestamp }

    val timeFormatter = SimpleDateFormat("hh:mm a", AppLocale.AR_DZ)
    val timeStr = timeFormatter.format(Date(timestamp))

    val isSameDay = now.get(Calendar.YEAR) == timeCal.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == timeCal.get(Calendar.DAY_OF_YEAR)

    if (isSameDay) {
        return "اليوم $timeStr"
    }

    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val isYesterday = yesterday.get(Calendar.YEAR) == timeCal.get(Calendar.YEAR) &&
            yesterday.get(Calendar.DAY_OF_YEAR) == timeCal.get(Calendar.DAY_OF_YEAR)

    if (isYesterday) {
        return "أمس $timeStr"
    }

    val fullDateFormatter = SimpleDateFormat("dd MMMM yyyy، hh:mm a", AppLocale.AR_DZ)
    return fullDateFormatter.format(Date(timestamp))
}

/**
 * تنسيق الوقت والتاريخ بالكامل لنافذة التفاصيل
 */
private fun formatAuditTimestampFull(timestamp: Long): String {
    if (timestamp <= 0L) return "—"
    val fullDateFormatter = SimpleDateFormat("dd MMMM yyyy، hh:mm a", AppLocale.AR_DZ)
    return fullDateFormatter.format(Date(timestamp))
}
