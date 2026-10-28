package com.example.presentation.hobby

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import com.example.presentation.common.AppAlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.hobby.HobbyBadge
import com.example.domain.hobby.HobbyBadgeIcon

/**
 * حوار إضافة أو تعديل شارة هواية مع اختيار الأيقونة
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HobbyBadgeFormDialog(
    initialBadge: HobbyBadge? = null,
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String, iconKey: String) -> Unit
) {
    var name by remember { mutableStateOf(initialBadge?.name.orEmpty()) }
    var description by remember { mutableStateOf(initialBadge?.description.orEmpty()) }
    var selectedIconKey by remember { mutableStateOf(initialBadge?.iconKey ?: HobbyBadgeIcon.OTHER.key) }
    var isSubmitted by remember { mutableStateOf(false) }

    val isNameValid = name.trim().length >= 2

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialBadge == null) {
                    stringResource(id = R.string.hobby_add_badge)
                } else {
                    stringResource(id = R.string.hobby_edit_badge)
                },
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(id = R.string.hobby_badge_name_label)) },
                    isError = isSubmitted && !isNameValid,
                    supportingText = {
                        if (isSubmitted && !isNameValid) {
                            Text("يجب أن يحتوي اسم الشارة على حرفين على الأقل")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hobby_badge_name_input"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(id = R.string.hobby_badge_description_label)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hobby_badge_desc_input"),
                    minLines = 2,
                    maxLines = 4
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(id = R.string.hobby_badge_icon_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // منتقي الأيقونات (13 أيقونة معرفة مسبقاً)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HobbyBadgeIcon.entries.forEach { iconItem ->
                        val isSelected = iconItem.key == selectedIconKey
                        val bg = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        val borderCol = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        val tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(bg)
                                .border(if (isSelected) 2.dp else 1.dp, borderCol, CircleShape)
                                .clickable { selectedIconKey = iconItem.key }
                                .testTag("hobby_icon_${iconItem.key}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = HobbyBadgeIcon.resolveIcon(iconItem.key),
                                contentDescription = iconItem.labelAr,
                                tint = tint,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    isSubmitted = true
                    if (isNameValid) {
                        onConfirm(name.trim(), description.trim(), selectedIconKey)
                    }
                },
                modifier = Modifier.testTag("save_hobby_badge_button")
            ) {
                Text(stringResource(id = R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_hobby_badge_button")
            ) {
                Text(stringResource(id = R.string.cancel_action))
            }
        }
    )
}
