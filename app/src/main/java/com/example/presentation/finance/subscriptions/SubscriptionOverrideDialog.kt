package com.example.presentation.finance.subscriptions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.presentation.common.AppAlertDialog

/**
 * حوار تخصيص مبالغ الاشتراكات لنشاط محدد (SubscriptionOverrideDialog)
 */
@Composable
fun SubscriptionOverrideDialog(
    isCustomOverride: Boolean,
    overridePresentText: String,
    overrideLateText: String,
    overrideExcusedText: String,
    overrideUnexcusedText: String,
    defaultPresent: Int,
    defaultLate: Int,
    defaultExcused: Int,
    defaultUnexcused: Int,
    isSaving: Boolean,
    onToggleCustomOverride: (Boolean) -> Unit,
    onPresentChange: (String) -> Unit,
    onLateChange: (String) -> Unit,
    onExcusedChange: (String) -> Unit,
    onUnexcusedChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag("subscription_override_dialog"),
        title = {
            Text(
                text = stringResource(id = R.string.subscription_override_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // نص توضيحي للمبالغ الافتراضية
                Text(
                    text = stringResource(
                        id = R.string.subscription_override_info,
                        defaultPresent,
                        defaultLate,
                        defaultExcused,
                        defaultUnexcused
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // مفتاح التبديل بين الافتراضي والمخصص
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.subscription_override_use_defaults),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Switch(
                        checked = !isCustomOverride,
                        onCheckedChange = { useDefaults ->
                            onToggleCustomOverride(!useDefaults)
                        },
                        modifier = Modifier.testTag("subscription_override_use_defaults_switch")
                    )
                }

                if (isCustomOverride) {
                    Spacer(modifier = Modifier.height(4.dp))

                    // مبلغ الحاضر
                    OutlinedTextField(
                        value = overridePresentText,
                        onValueChange = onPresentChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("subscription_override_present_input"),
                        label = { Text(stringResource(id = R.string.subscription_override_amount_present)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )

                    // مبلغ المتأخر
                    OutlinedTextField(
                        value = overrideLateText,
                        onValueChange = onLateChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("subscription_override_late_input"),
                        label = { Text(stringResource(id = R.string.subscription_override_amount_late)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )

                    // مبلغ الغائب بعذر
                    OutlinedTextField(
                        value = overrideExcusedText,
                        onValueChange = onExcusedChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("subscription_override_excused_input"),
                        label = { Text(stringResource(id = R.string.subscription_override_amount_excused)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )

                    // مبلغ الغائب بدون عذر
                    OutlinedTextField(
                        value = overrideUnexcusedText,
                        onValueChange = onUnexcusedChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("subscription_override_unexcused_input"),
                        label = { Text(stringResource(id = R.string.subscription_override_amount_unexcused)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = !isSaving,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("subscription_override_save_button")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(stringResource(id = R.string.action_save))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSaving,
                modifier = Modifier.testTag("subscription_override_cancel_button")
            ) {
                Text(stringResource(id = R.string.cancel_action))
            }
        }
    )
}
