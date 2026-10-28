package com.example.presentation.scout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.WorkspacePremium
import com.example.presentation.common.AppAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.progress.BadgeLevel
import com.example.domain.progress.BadgeProgress
import com.example.domain.progress.RequirementWithState
import com.example.domain.progress.labelRes
import com.example.domain.scout.Scout
import java.lang.String.format
import java.text.SimpleDateFormat
import java.util.Date

/**
 * مكون قسم بطاقة التقدم لشارات الكفاءة الأربعة في ملف تفاصيل الشبل
 */
@Composable
fun ScoutProgressSection(
    scout: Scout,
    badgeProgressList: List<BadgeProgress>,
    onToggleRequirement: (requirementId: String, currentCompleted: Boolean) -> Unit,
    onGrantBadge: (level: BadgeLevel) -> Unit,
    onRevokeBadge: (level: BadgeLevel) -> Unit,
    modifier: Modifier = Modifier
) {
    val expandedStates = remember { mutableStateMapOf<String, Boolean>() }
    var badgeToGrant by remember { mutableStateOf<BadgeProgress?>(null) }
    var badgeToRevoke by remember { mutableStateOf<BadgeProgress?>(null) }

    val dateFormatter = remember {
        SimpleDateFormat("yyyy/MM/dd", com.example.presentation.common.AppLocale.AR_DZ)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ترويسة القسم
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.WorkspacePremium,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = stringResource(id = R.string.progress_section_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }

        // عرض بطاقات الشارات الأربعة (القبول، المبتدئ، الثاني، الأول)
        badgeProgressList.forEach { badge ->
            val levelKey = badge.level.key
            val isExpanded = expandedStates[levelKey] ?: false

            BadgeProgressCollapsibleCard(
                badge = badge,
                isExpanded = isExpanded,
                onToggleExpand = { expandedStates[levelKey] = !isExpanded },
                dateFormatter = dateFormatter,
                onToggleRequirement = { reqId, completed ->
                    onToggleRequirement(reqId, completed)
                },
                onRequestGrant = { badgeToGrant = badge },
                onRequestRevoke = { badgeToRevoke = badge }
            )
        }
    }

    // حوار تأكيد منح الشارة
    badgeToGrant?.let { badge ->
        val badgeName = stringResource(id = badge.level.labelRes())
        AppAlertDialog(
            onDismissRequest = { badgeToGrant = null },
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
                        badgeName,
                        scout.fullName
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val level = badge.level
                        badgeToGrant = null
                        onGrantBadge(level)
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
                TextButton(onClick = { badgeToGrant = null }) {
                    Text(stringResource(id = R.string.cancel_action))
                }
            }
        )
    }

    // حوار تأكيد سحب الشارة
    badgeToRevoke?.let { badge ->
        AppAlertDialog(
            onDismissRequest = { badgeToRevoke = null },
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
                        val level = badge.level
                        badgeToRevoke = null
                        onRevokeBadge(level)
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
                TextButton(onClick = { badgeToRevoke = null }) {
                    Text(stringResource(id = R.string.cancel_action))
                }
            }
        )
    }
}

@Composable
private fun BadgeProgressCollapsibleCard(
    badge: BadgeProgress,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    dateFormatter: SimpleDateFormat,
    onToggleRequirement: (requirementId: String, currentCompleted: Boolean) -> Unit,
    onRequestGrant: () -> Unit,
    onRequestRevoke: () -> Unit
) {
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "arrow_rotation"
    )

    val progressAnimated by animateFloatAsState(
        targetValue = badge.percentage / 100f,
        label = "progress_anim"
    )

    val badgeTitle = stringResource(id = badge.level.labelRes())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("badge_card_${badge.level.key}"),
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
                    .clickable { onToggleExpand() }
                    .testTag("toggle_badge_${badge.level.key}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = badgeTitle,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (badge.isGranted) {
                            Surface(
                                color = Color(0xFF2E7D32).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF2E7D32),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(id = R.string.progress_granted_chip),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF2E7D32)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = format(
                            com.example.presentation.common.AppLocale.AR_DZ,
                            stringResource(id = R.string.progress_summary_format),
                            badge.completedActiveRequirements,
                            badge.totalActiveRequirements,
                            badge.percentage
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (badge.isGranted && badge.grantedAt != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = format(
                                com.example.presentation.common.AppLocale.AR_DZ,
                                stringResource(id = R.string.progress_granted_on_format),
                                dateFormatter.format(Date(badge.grantedAt))
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }

                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.testTag("expand_icon_${badge.level.key}")
                ) {
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "طي" else "توسيع",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.rotate(rotationAngle)
                    )
                }
            }

            // شريط التقدم الخطي
            LinearProgressIndicator(
                progress = { progressAnimated },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = if (badge.isGranted || badge.percentage == 100) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            // جسم البطاقة (يظهر عند التوسيع)
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    if (badge.requirements.isEmpty()) {
                        Text(
                            text = stringResource(id = R.string.progress_empty_requirements),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        badge.requirements.forEach { reqState ->
                            RequirementCheckRow(
                                item = reqState,
                                dateFormatter = dateFormatter,
                                onToggle = {
                                    onToggleRequirement(reqState.requirement.id, reqState.completed)
                                }
                            )
                        }
                    }

                    // زر منح الشارة عند اكتمال 100% ولم تمنح بعد
                    if (badge.totalActiveRequirements > 0 && badge.percentage == 100 && !badge.isGranted) {
                        Spacer(modifier = Modifier.height(6.dp))
                        FilledTonalButton(
                            onClick = onRequestGrant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("grant_badge_button_${badge.level.key}"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFF2E7D32).copy(alpha = 0.18f),
                                contentColor = Color(0xFF1B5E20)
                            )
                        ) {
                            Icon(imageVector = Icons.Default.WorkspacePremium, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = format(
                                    com.example.presentation.common.AppLocale.AR_DZ,
                                    stringResource(id = R.string.progress_grant_badge_format),
                                    badgeTitle
                                ),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // خيار سحب الشارة في حال كانت ممنوحة
                    if (badge.isGranted) {
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(
                            onClick = onRequestRevoke,
                            modifier = Modifier
                                .align(Alignment.End)
                                .testTag("revoke_badge_button_${badge.level.key}"),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(
                                text = stringResource(id = R.string.progress_revoke_badge),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RequirementCheckRow(
    item: RequirementWithState,
    dateFormatter: SimpleDateFormat,
    onToggle: () -> Unit
) {
    val req = item.requirement
    val isArchived = req.archived

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .alpha(if (isArchived) 0.55f else 1.0f),
        verticalAlignment = Alignment.Top
    ) {
        if (!isArchived) {
            Checkbox(
                checked = item.completed,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = Color(0xFF2E7D32),
                    checkmarkColor = Color.White
                ),
                modifier = Modifier.testTag("req_checkbox_${req.id}")
            )
        } else {
            Spacer(modifier = Modifier.width(12.dp))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = if (!isArchived) 12.dp else 0.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = req.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isArchived) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )

                if (isArchived) {
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                text = stringResource(id = R.string.progress_archived_chip),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.outline
                        )
                    )
                }
            }

            if (req.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = req.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (item.completed && item.completedAt != null) {
                Spacer(modifier = Modifier.height(2.dp))
                val leaderName = item.completedByName ?: "قائد"
                val dateStr = dateFormatter.format(Date(item.completedAt))
                Text(
                    text = format(
                        com.example.presentation.common.AppLocale.AR_DZ,
                        stringResource(id = R.string.progress_completed_on_format),
                        dateStr,
                        leaderName
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF2E7D32)
                )
            }
        }
    }
}
