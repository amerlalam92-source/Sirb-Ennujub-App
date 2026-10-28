package com.example.presentation.progress

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WorkspacePremium
import com.example.presentation.common.AppAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.progress.BadgeProgress
import com.example.domain.progress.RequirementWithState
import com.example.domain.progress.labelRes
import kotlinx.coroutines.launch
import java.lang.String.format
import java.text.SimpleDateFormat
import java.util.Date

/**
 * شاشة تفاصيل شارة واحدة للشبل (ScoutSingleBadgeProgressScreen)
 * تعرض:
 * A) SUMMARY HEADER: اسم الشارة، نسبة الإنجاز الكبيرة، مؤشر التقدم السميك، عدد المتطلبات، وشارة "ممنوحة" إذا كانت ممنوحة.
 * B) REQUIREMENTS LIST: قائمة المتطلبات مع مربعات الاختيار، الوصف، وبيانات الإنجاز أو شارة "مؤرشف".
 * C) ACTION BUTTON: زر منح الشارة (عند 100% وليست ممنوحة) أو زر سحب الشارة (عند كونها ممنوحة) مع حوارات التأكيد.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoutSingleBadgeProgressScreen(
    viewModel: ScoutSingleBadgeProgressViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val errorMessage by viewModel.message.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showGrantDialog by remember { mutableStateOf(false) }
    var showRevokeDialog by remember { mutableStateOf(false) }

    val dateFormatter = remember {
        SimpleDateFormat("yyyy/MM/dd", com.example.presentation.common.AppLocale.AR_DZ)
    }

    BackHandler { onNavigateBack() }

    LaunchedEffect(errorMessage) {
        errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    val badgeLabel = stringResource(id = viewModel.badgeLevel.labelRes())
    val scoutName = (uiState as? ScoutSingleBadgeProgressUiState.Success)?.scout?.fullName ?: ""

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CustomTopAppBar(
                title = badgeLabel,
                subtitle = scoutName.ifBlank { null },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("single_badge_back_button")
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
            when (val state = uiState) {
                is ScoutSingleBadgeProgressUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .testTag("single_badge_loading"),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                is ScoutSingleBadgeProgressUiState.Error -> {
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
                        Button(onClick = onNavigateBack) {
                            Text(stringResource(id = R.string.cancel_action))
                        }
                    }
                }

                is ScoutSingleBadgeProgressUiState.Success -> {
                    val badge = state.badgeProgress
                    val requirements = badge.requirements

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("single_badge_scroll_content"),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // A) SUMMARY HEADER at top
                        item {
                            SingleBadgeSummaryHeader(
                                badge = badge,
                                badgeLabel = badgeLabel,
                                dateFormatter = dateFormatter
                            )
                        }

                        // B) REQUIREMENTS LIST
                        item {
                            Text(
                                text = "قائمة المتطلبات والبنود",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                            )
                        }

                        if (requirements.isEmpty()) {
                            item {
                                ElevatedCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = stringResource(id = R.string.progress_empty_requirements),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else {
                            val hobbyBadges = (uiState as? ScoutSingleBadgeProgressUiState.Success)?.hobbyBadges.orEmpty()
                            items(
                                items = requirements,
                                key = { it.requirement.id }
                            ) { reqState ->
                                SingleBadgeRequirementRow(
                                    item = reqState,
                                    hobbyBadges = hobbyBadges,
                                    dateFormatter = dateFormatter,
                                    onToggle = { reqId, currentDone ->
                                        viewModel.toggleRequirement(
                                            requirementId = reqId,
                                            currentCompleted = currentDone,
                                            onSuccess = { isDone ->
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        if (isDone) "تم تسجيل الإنجاز" else "تم إلغاء الإنجاز"
                                                    )
                                                }
                                            }
                                        )
                                    }
                                )
                            }
                        }

                        // C) ACTION BUTTON at bottom
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            SingleBadgeActionButtonSection(
                                badge = badge,
                                badgeLabel = badgeLabel,
                                onGrantClick = { showGrantDialog = true },
                                onRevokeClick = { showRevokeDialog = true }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }

    // Confirmation dialog for Granting Badge
    if (showGrantDialog) {
        AppAlertDialog(
            onDismissRequest = { showGrantDialog = false },
            title = {
                Text(
                    text = stringResource(id = R.string.progress_grant_dialog_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = format(
                        com.example.presentation.common.AppLocale.AR_DZ,
                        stringResource(id = R.string.progress_grant_dialog_body_format),
                        badgeLabel,
                        scoutName
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showGrantDialog = false
                        viewModel.grantBadge {
                            scope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.progress_snack_granted))
                            }
                        }
                    },
                    modifier = Modifier.testTag("confirm_grant_badge_button")
                ) {
                    Text(
                        text = stringResource(id = R.string.progress_grant_confirm_action),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32)
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showGrantDialog = false }) {
                    Text(stringResource(id = R.string.cancel_action))
                }
            }
        )
    }

    // Confirmation dialog for Revoking Badge
    if (showRevokeDialog) {
        AppAlertDialog(
            onDismissRequest = { showRevokeDialog = false },
            title = {
                Text(
                    text = stringResource(id = R.string.progress_revoke_dialog_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(stringResource(id = R.string.progress_revoke_dialog_body))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRevokeDialog = false
                        viewModel.revokeBadge {
                            scope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.progress_snack_revoked))
                            }
                        }
                    },
                    modifier = Modifier.testTag("confirm_revoke_badge_button")
                ) {
                    Text(
                        text = stringResource(id = R.string.progress_revoke_badge),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showRevokeDialog = false }) {
                    Text(stringResource(id = R.string.cancel_action))
                }
            }
        )
    }
}

/**
 * A) SUMMARY HEADER at top:
 * - اسم الشارة (كبير وغامق)
 * - نسبة الإنجاز الكبيرة "{percent}%"
 * - مؤشر تقدم سميك ومتحرك
 * - نص "{completed} من {total} متطلب"
 * - إذا كانت ممنوحة: شارة بارزة "ممنوحة" + تاريخ المنح + اسم القائد
 */
@Composable
private fun SingleBadgeSummaryHeader(
    badge: BadgeProgress,
    badgeLabel: String,
    dateFormatter: SimpleDateFormat,
    modifier: Modifier = Modifier
) {
    val progressAnimated by animateFloatAsState(
        targetValue = badge.percentage / 100f,
        label = "single_badge_progress_anim"
    )

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("single_badge_header_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // سطر العنوان والنسبة
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = if (badge.isGranted) Color(0xFF2E7D32).copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (badge.isGranted) Icons.Default.Check else Icons.Default.WorkspacePremium,
                                contentDescription = null,
                                tint = if (badge.isGranted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = badgeLabel,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = format(
                                com.example.presentation.common.AppLocale.AR_DZ,
                                "%d من %d متطلب نشط",
                                badge.completedActiveRequirements,
                                badge.totalActiveRequirements
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // النسبة المئوية بخط كبير
                Text(
                    text = format(
                        com.example.presentation.common.AppLocale.AR_DZ,
                        "%d%%",
                        badge.percentage
                    ),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (badge.isGranted || badge.percentage == 100) Color(0xFF2E7D32)
                    else MaterialTheme.colorScheme.primary
                )
            }

            // مؤشر التقدم الخطي السميك
            LinearProgressIndicator(
                progress = { progressAnimated },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
                color = if (badge.isGranted || badge.percentage == 100) Color(0xFF2E7D32)
                else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            // في حال كانت الشارة ممنوحة: شارة بارزة مع التاريخ والقائد المانح
            if (badge.isGranted) {
                HorizontalDivider(modifier = Modifier.alpha(0.3f))
                Surface(
                    color = Color(0xFF2E7D32).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = Color(0xFF2E7D32),
                            shape = CircleShape,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "شارة ممنوعة رسمياً",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF2E7D32)
                            )
                            val grantedDate = badge.grantedAt?.let { dateFormatter.format(Date(it)) } ?: ""
                            val leader = badge.grantedByName.orEmpty()
                            val detailsText = when {
                                grantedDate.isNotBlank() && leader.isNotBlank() ->
                                    "تاريخ المنح: $grantedDate — بواسطة: $leader"
                                grantedDate.isNotBlank() -> "تاريخ المنح: $grantedDate"
                                else -> "معتمدة وممنوحة للشبل"
                            }
                            Text(
                                text = detailsText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * B) REQUIREMENTS LIST ROW:
 * - Checkbox (leading) for active requirements
 * - Title (bold) + description (small, gray)
 * - If completed: subtitle "أُنجز في {date} — بواسطة {leaderName}"
 * - If archived: gray text + "(مؤرشف)" chip, NO checkbox, NOT counted in percentage
 */
@Composable
private fun SingleBadgeRequirementRow(
    item: RequirementWithState,
    hobbyBadges: List<com.example.domain.hobby.HobbyBadge> = emptyList(),
    dateFormatter: SimpleDateFormat,
    onToggle: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val req = item.requirement
    val isArchived = req.archived
    val linkedCount = req.linkedHobbyBadgesCount
    val isLinked = linkedCount != null && linkedCount > 0

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("requirement_card_${req.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isArchived) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // خانة الاختيار (فقط للمتطلبات النشطة)
            if (!isArchived) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = item.completed,
                        onCheckedChange = { if (!isLinked) onToggle(req.id, item.completed) },
                        enabled = !isLinked,
                        modifier = Modifier.testTag("requirement_checkbox_${req.id}"),
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    if (isLinked) {
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "مرتبط بشارة هواية",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.progress_archived_chip),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // العنوان والوصف وسطر الإنجاز
            Column(
                modifier = Modifier
                    .weight(1f)
                    .alpha(if (isArchived) 0.6f else 1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = req.title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (item.completed) FontWeight.Bold else FontWeight.SemiBold
                    ),
                    color = if (isArchived) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
                )

                if (req.description.isNotBlank()) {
                    Text(
                        text = req.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isLinked && !isArchived) {
                    Text(
                        text = format(
                            com.example.presentation.common.AppLocale.AR_DZ,
                            stringResource(id = R.string.kafaa_link_count_auto_hint_format),
                            linkedCount ?: 0
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (item.completed && !isArchived) {
                    val dateStr = item.completedAt?.let { dateFormatter.format(Date(it)) } ?: ""
                    val leaderName = item.completedByName ?: "القائد"
                    Text(
                        text = format(
                            com.example.presentation.common.AppLocale.AR_DZ,
                            stringResource(id = R.string.progress_completed_on_format),
                            dateStr,
                            leaderName
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * C) ACTION BUTTON at bottom:
 * - If totalActive > 0 AND percentage == 100 AND NOT granted:
 *     FilledTonalButton: "منح شارة {badgeLabel}" -> confirmation dialog
 * - If granted:
 *     TextButton with error color: "سحب الشارة" -> confirmation dialog
 */
@Composable
private fun SingleBadgeActionButtonSection(
    badge: BadgeProgress,
    badgeLabel: String,
    onGrantClick: () -> Unit,
    onRevokeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        when {
            badge.isGranted -> {
                TextButton(
                    onClick = onRevokeClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("single_badge_revoke_button")
                ) {
                    Text(
                        text = stringResource(id = R.string.progress_revoke_badge),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }

            badge.totalActiveRequirements > 0 && badge.percentage == 100 -> {
                FilledTonalButton(
                    onClick = onGrantClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("single_badge_grant_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WorkspacePremium,
                        contentDescription = null,
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "منح شارة $badgeLabel",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32),
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}
