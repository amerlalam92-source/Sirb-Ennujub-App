package com.example.presentation.progress

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.example.presentation.common.AppAlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.progress.ProgressRequirement

/**
 * حوار إضافة أو تعديل متطلب في بطاقة التقدم (مع قسم اختياري للربط بعدد شارات الهواية)
 */
@Composable
fun ProgressRequirementFormDialog(
    initialRequirement: ProgressRequirement? = null,
    onDismiss: () -> Unit,
    onConfirm: (title: String, description: String, linkedHobbyBadgesCount: Int?) -> Unit
) {
    var title by remember { mutableStateOf(initialRequirement?.title.orEmpty()) }
    var description by remember { mutableStateOf(initialRequirement?.description.orEmpty()) }
    var isSubmitted by remember { mutableStateOf(false) }

    val initialCount = initialRequirement?.linkedHobbyBadgesCount
    var isLinkedToCount by remember {
        mutableStateOf(initialCount != null && initialCount > 0)
    }
    var countText by remember {
        mutableStateOf(if (initialCount != null && initialCount > 0) initialCount.toString() else "3")
    }

    val isTitleValid = title.trim().length >= 3
    val parsedCount = countText.toIntOrNull()
    val isCountValid = !isLinkedToCount || (parsedCount != null && parsedCount in 1..50)

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialRequirement == null) {
                    stringResource(id = R.string.progress_add_requirement)
                } else {
                    stringResource(id = R.string.progress_edit_requirement)
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
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(id = R.string.progress_requirement_title_label)) },
                    isError = isSubmitted && !isTitleValid,
                    supportingText = {
                        if (isSubmitted && !isTitleValid) {
                            Text("يجب أن يحتوي نص المتطلب على 3 أحرف على الأقل")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("requirement_title_input"),
                    singleLine = false,
                    maxLines = 3
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(id = R.string.progress_requirement_description_label)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("requirement_desc_input"),
                    minLines = 2,
                    maxLines = 4
                )

                Spacer(modifier = Modifier.height(16.dp))

                // قسم اختياري: الربط بعدد شارات الهواية
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isLinkedToCount = !isLinkedToCount }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = isLinkedToCount,
                        onCheckedChange = { isLinkedToCount = it },
                        modifier = Modifier.testTag("link_hobby_badge_switch")
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(id = R.string.kafaa_link_count_switch),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (isLinkedToCount) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = countText,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() }) {
                                countText = input
                            }
                        },
                        label = { Text(stringResource(id = R.string.kafaa_link_count_field_label)) },
                        placeholder = { Text(stringResource(id = R.string.kafaa_link_count_hint)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = isSubmitted && !isCountValid,
                        supportingText = {
                            if (isSubmitted && !isCountValid) {
                                Text("يجب إدخال عدد صحيح بين 1 و 50")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("linked_hobby_badges_count_input"),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    isSubmitted = true
                    if (isTitleValid && isCountValid) {
                        val finalCount = if (isLinkedToCount) parsedCount else null
                        onConfirm(title.trim(), description.trim(), finalCount)
                    }
                },
                modifier = Modifier.testTag("save_requirement_button")
            ) {
                Text(stringResource(id = R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_requirement_button")
            ) {
                Text(stringResource(id = R.string.cancel_action))
            }
        }
    )
}
