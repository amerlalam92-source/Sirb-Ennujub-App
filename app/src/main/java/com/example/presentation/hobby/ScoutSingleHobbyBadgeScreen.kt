package com.example.presentation.hobby

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.WorkspacePremium
import com.example.presentation.common.AppAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.hobby.HobbyBadgeIcon
import com.example.domain.hobby.HobbyBadgeProgress
import com.example.domain.hobby.HobbyRequirementWithState
import java.lang.String.format
import java.text.SimpleDateFormat
import java.util.Date

/**
 * شاشة إنجاز شارة هواية واحدة لشبل معين
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoutSingleHobbyBadgeScreen(
    viewModel: ScoutSingleHobbyBadgeViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val dateFormatter = remember { SimpleDateFormat("yyyy/MM/dd", com.example.presentation.common.AppLocale.AR_DZ) }

    var showGrantDialog by remember { mutableStateOf(false) }
    var showRevokeDialog by remember { mutableStateOf(false) }

    val userMessage = (uiState as? ScoutSingleHobbyBadgeUiState.Success)?.message
    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val scoutName = (uiState as? ScoutSingleHobbyBadgeUiState.Success)?.scoutName.orEmpty()
    val badgeProgress = (uiState as? ScoutSingleHobbyBadgeUiState.Success)?.badgeProgress
    val badgeName = badgeProgress?.badge?.name ?: "شارة الهواية"

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CustomTopAppBar(
                title = format(
                    com.example.presentation.common.AppLocale.AR_DZ,
                    stringResource(id = R.string.hobby_single_screen_title_format),
                    badgeName,
                    scoutName
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("single_hobby_badge_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.back_action)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is ScoutSingleHobbyBadgeUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is ScoutSingleHobbyBadgeUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = state.message, color = MaterialTheme.colorScheme.error)
                    }
                }
                is ScoutSingleHobbyBadgeUiState.Success -> {
                    val progress = state.badgeProgress
                    if (progress == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("لم يتم العثور على الشارة", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // A) بطاقة الترويسة والملخص
                            item {
                                SingleHobbyBadgeHeaderCard(
                                    progress = progress,
                                    dateFormatter = dateFormatter
                                )
                            }

                            // B) قائمة المتطلبات
                            if (progress.requirements.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "متطلبات الشارة (${progress.completedActiveRequirements}/${progress.totalActiveRequirements})",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                items(progress.requirements, key = { it.requirement.id }) { reqState ->
                                    SingleHobbyRequirementRow(
                                        item = reqState,
                                        dateFormatter = dateFormatter,
                                        onToggle = { reqId, currentDone ->
                                            viewModel.toggleRequirement(reqId, currentDone)
                                        }
                                    )
                                }
                            } else {
                                item {
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(24.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = stringResource(id = R.string.hobby_no_requirements),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }
                                }
                            }

                            // C) أزرار الإجراءات
                            item {
                                SingleHobbyBadgeActionSection(
                                    progress = progress,
                                    onGrantClick = { showGrantDialog = true },
                                    onRevokeClick = { showRevokeDialog = true }
                                )
                            }

                            item {
                                Spacer(modifier = Modifier.height(32.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // حوار تأكيد منح الشارة
    if (showGrantDialog && badgeProgress != null) {
        AppAlertDialog(
            onDismissRequest = { showGrantDialog = false },
            title = {
                Text(
                    text = format(
                        com.example.presentation.common.AppLocale.AR_DZ,
                        stringResource(id = R.string.hobby_grant_button_format),
                        badgeName
                    ),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = format(
                        com.example.presentation.common.AppLocale.AR_DZ,
                        stringResource(id = R.string.hobby_grant_confirm_format),
                        badgeName,
                        scoutName
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showGrantDialog = false
                        viewModel.grantBadge()
                    },
                    modifier = Modifier.testTag("confirm_grant_hobby_badge_button")
                ) {
                    Text(stringResource(id = R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showGrantDialog = false }) {
                    Text(stringResource(id = R.string.cancel_action))
                }
            }
        )
    }

    // حوار تأكيد سحب الشارة
    if (showRevokeDialog) {
        AppAlertDialog(
            onDismissRequest = { showRevokeDialog = false },
            title = {
                Text(
                    text = stringResource(id = R.string.hobby_revoke_button),
                    fontWeight = FontWeight.Bold
                )
            },
            text = { Text(stringResource(id = R.string.hobby_revoke_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRevokeDialog = false
                        viewModel.revokeBadge()
                    },
                    modifier = Modifier.testTag("confirm_revoke_hobby_badge_button")
                ) {
                    Text(
                        text = stringResource(id = R.string.delete_action),
                        color = MaterialTheme.colorScheme.error
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

@Composable
private fun SingleHobbyBadgeHeaderCard(
    progress: HobbyBadgeProgress,
    dateFormatter: SimpleDateFormat
) {
    val badge = progress.badge
    val isGranted = progress.isGranted

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // الأيقونة الكبيرة
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(
                        if (isGranted) Color(0xFFE8F5E9)
                        else MaterialTheme.colorScheme.primaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = HobbyBadgeIcon.resolveIcon(badge.iconKey),
                    contentDescription = null,
                    tint = if (isGranted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = badge.name,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            if (badge.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = badge.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // شارة "ممنوحة" إذا كانت ممنوحة
            if (isGranted) {
                Surface(
                    color = Color(0xFFE8F5E9),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = format(
                                com.example.presentation.common.AppLocale.AR_DZ,
                                stringResource(id = R.string.hobby_granted_on_format),
                                progress.grantedAt?.let { dateFormatter.format(Date(it)) } ?: ""
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // نسبة التقدم
            if (progress.totalActiveRequirements > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "نسبة الإنجاز",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${progress.percentage}%",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = if (isGranted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { progress.percentage / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    color = if (isGranted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SingleHobbyRequirementRow(
    item: HobbyRequirementWithState,
    dateFormatter: SimpleDateFormat,
    onToggle: (String, Boolean) -> Unit
) {
    val req = item.requirement
    val isArchived = req.archived

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("scout_hobby_req_${req.id}"),
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
            if (!isArchived) {
                Checkbox(
                    checked = item.completed,
                    onCheckedChange = { onToggle(req.id, item.completed) },
                    modifier = Modifier.testTag("hobby_req_checkbox_${req.id}"),
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary
                    )
                )
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

@Composable
private fun SingleHobbyBadgeActionSection(
    progress: HobbyBadgeProgress,
    onGrantClick: () -> Unit,
    onRevokeClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (!progress.isGranted) {
            val canGrant = (progress.totalActiveRequirements > 0 && progress.percentage == 100) || progress.totalActiveRequirements == 0
            if (canGrant) {
                FilledTonalButton(
                    onClick = onGrantClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("grant_hobby_badge_button"),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF2E7D32),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WorkspacePremium,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = format(
                            com.example.presentation.common.AppLocale.AR_DZ,
                            stringResource(id = R.string.hobby_grant_button_format),
                            progress.badge.name
                        ),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        } else {
            TextButton(
                onClick = onRevokeClick,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("revoke_hobby_badge_button")
            ) {
                Text(
                    text = stringResource(id = R.string.hobby_revoke_button),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}
