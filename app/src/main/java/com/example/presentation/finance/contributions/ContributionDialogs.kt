package com.example.presentation.finance.contributions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.PersonAdd
import com.example.presentation.common.AppAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.finance.FinanceContribution
import com.example.domain.scout.Scout
import com.example.presentation.common.AppLocale
import java.text.Collator
import java.util.Locale

/**
 * حوار تسديد مساهمة جزئية أو محددة
 */
@Composable
fun ContributionPaymentDialog(
    contribution: FinanceContribution,
    onConfirm: (amount: Int, notes: String?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var amountText by remember { mutableStateOf(contribution.debtAmount.toString()) }
    var notesText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag("contribution_payment_dialog"),
        title = {
            Text(
                text = stringResource(id = R.string.contributions_payment_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // اسم الشبل وتفاصيل الدين الحالي
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = contribution.scoutFullName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "المطلوب: ${contribution.requiredAmount} دج",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "المسدد: ${contribution.paidAmount} دج",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "المتبقي: ${contribution.debtAmount} دج",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                // المبلغ المراد تسديده الآن
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        val filtered = it.filter { ch -> ch.isDigit() }
                        amountText = filtered
                        errorMessage = null
                    },
                    label = { Text(stringResource(id = R.string.contributions_payment_amount_label)) },
                    suffix = { Text(stringResource(id = R.string.finance_currency)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("contribution_payment_amount_input")
                )

                // ملاحظات التسديد
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text(stringResource(id = R.string.contributions_payment_notes_label)) },
                    placeholder = { Text("مثال: دفعة أولى نقداً...") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("contribution_payment_notes_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.toIntOrNull()
                    if (amount == null || amount <= 0) {
                        errorMessage = "يرجى إدخال مبلغ صحيح أكبر من صفر"
                        return@Button
                    }
                    if (amount > contribution.debtAmount) {
                        errorMessage = "المبلغ المدفوع يتجاوز الدين المتبقي (${contribution.debtAmount} دج)"
                        return@Button
                    }
                    onConfirm(amount, notesText.trim().ifBlank { null })
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("confirm_payment_btn")
            ) {
                Text(stringResource(id = R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_payment_btn")
            ) {
                Text(stringResource(id = R.string.cancel_action))
            }
        }
    )
}

/**
 * حوار تعديل بيانات مساهمة الشبل
 */
@Composable
fun ContributionEditDialog(
    contribution: FinanceContribution,
    onSave: (requiredAmount: Int, notes: String?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var requiredAmountText by remember { mutableStateOf(contribution.requiredAmount.toString()) }
    var notesText by remember { mutableStateOf(contribution.notes.orEmpty()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag("contribution_edit_dialog"),
        title = {
            Text(
                text = stringResource(id = R.string.contributions_edit_contribution),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "الشبل: ${contribution.scoutFullName}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedTextField(
                    value = requiredAmountText,
                    onValueChange = {
                        requiredAmountText = it.filter { ch -> ch.isDigit() }
                        errorMessage = null
                    },
                    label = { Text("المبلغ المطلوب للمساهمة") },
                    suffix = { Text(stringResource(id = R.string.finance_currency)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("contribution_edit_required_input")
                )

                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("ملاحظات المساهمة (اختياري)") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("contribution_edit_notes_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val req = requiredAmountText.toIntOrNull()
                    if (req == null || req < 0) {
                        errorMessage = "يرجى إدخال مبلغ صحيح"
                        return@Button
                    }
                    onSave(req, notesText.trim().ifBlank { null })
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("save_edit_contribution_btn")
            ) {
                Text(stringResource(id = R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_edit_contribution_btn")
            ) {
                Text(stringResource(id = R.string.cancel_action))
            }
        }
    )
}

/**
 * حوار إضافة مشاركين للمساهمة باختيار متعدد (Multi-Select Checklist)
 */
@Composable
fun ContributionAddScoutDialog(
    amount: String,
    selectedScoutIds: Set<String>,
    availableScouts: List<Scout>,
    onAmountChange: (String) -> Unit,
    onToggleScout: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val arabicCollator = remember {
        Collator.getInstance(Locale("ar")).apply {
            strength = Collator.PRIMARY
        }
    }
    val sortedScouts = remember(availableScouts) {
        availableScouts.sortedWith { s1, s2 -> arabicCollator.compare(s1.fullName, s2.fullName) }
    }

    val amountInt = amount.toIntOrNull() ?: 0
    val isAmountValid = amountInt > 0
    val isAnyScoutSelected = selectedScoutIds.isNotEmpty()
    val canSave = isAmountValid && isAnyScoutSelected && sortedScouts.isNotEmpty()

    AppAlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag("add_participants_contribution_dialog"),
        title = {
            Text(
                text = stringResource(id = R.string.contribution_add_participants_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1) AMOUNT FIELD
                OutlinedTextField(
                    value = amount,
                    onValueChange = onAmountChange,
                    label = { Text(stringResource(id = R.string.contribution_add_participants_amount_label)) },
                    suffix = { Text(stringResource(id = R.string.finance_currency)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = amount.isNotEmpty() && !isAmountValid,
                    supportingText = if (amount.isNotEmpty() && !isAmountValid) {
                        { Text(stringResource(id = R.string.error_contribution_amount_invalid), color = MaterialTheme.colorScheme.error) }
                    } else null,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_participants_amount_input")
                )

                if (sortedScouts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(id = R.string.contribution_add_participants_no_available),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    // 2) QUICK ACTIONS ROW
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = onSelectAll,
                                modifier = Modifier.testTag("select_all_scouts_btn")
                            ) {
                                Text(stringResource(id = R.string.contribution_add_participants_select_all))
                            }
                            TextButton(
                                onClick = onClearAll,
                                enabled = selectedScoutIds.isNotEmpty(),
                                modifier = Modifier.testTag("clear_all_scouts_btn")
                            ) {
                                Text(stringResource(id = R.string.contribution_add_participants_clear_all))
                            }
                        }

                        Text(
                            text = stringResource(
                                id = R.string.contribution_add_participants_selected_count_format,
                                selectedScoutIds.size
                            ),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("selected_scouts_count_text")
                        )
                    }

                    // 3) SCROLLABLE CHECKLIST (max height 300.dp)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        )
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(sortedScouts, key = { it.id }) { scout ->
                                val isSelected = scout.id in selectedScoutIds
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onToggleScout(scout.id) }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { onToggleScout(scout.id) },
                                        modifier = Modifier.testTag("scout_checkbox_${scout.id}")
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = scout.fullName,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (scout.age > 0) {
                                            Text(
                                                text = "${scout.age} سنة",
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
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = canSave,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("confirm_add_participants_btn")
            ) {
                Text(stringResource(id = R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_add_participants_btn")
            ) {
                Text(stringResource(id = R.string.cancel_action))
            }
        }
    )
}

/**
 * اسم بديل للحوار (ContributionAddParticipantsDialog)
 */
@Composable
fun ContributionAddParticipantsDialog(
    amount: String,
    selectedScoutIds: Set<String>,
    availableScouts: List<Scout>,
    onAmountChange: (String) -> Unit,
    onToggleScout: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    ContributionAddScoutDialog(
        amount = amount,
        selectedScoutIds = selectedScoutIds,
        availableScouts = availableScouts,
        onAmountChange = onAmountChange,
        onToggleScout = onToggleScout,
        onSelectAll = onSelectAll,
        onClearAll = onClearAll,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        modifier = modifier
    )
}

/**
 * دعم التوافقية للاستدعاء القديم
 */
@Composable
fun ContributionAddScoutDialog(
    availableScouts: List<Scout>,
    defaultAmount: Int,
    onAddSingle: (Scout, Int) -> Unit,
    onAddAll: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var amount by remember { mutableStateOf(if (defaultAmount > 0) defaultAmount.toString() else "") }
    var selectedScoutIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val scoutsMap = remember(availableScouts) { availableScouts.associateBy { it.id } }

    ContributionAddScoutDialog(
        amount = amount,
        selectedScoutIds = selectedScoutIds,
        availableScouts = availableScouts,
        onAmountChange = { amount = it.filter { ch -> ch.isDigit() } },
        onToggleScout = { scoutId ->
            selectedScoutIds = if (scoutId in selectedScoutIds) {
                selectedScoutIds - scoutId
            } else {
                selectedScoutIds + scoutId
            }
        },
        onSelectAll = { selectedScoutIds = availableScouts.map { it.id }.toSet() },
        onClearAll = { selectedScoutIds = emptySet() },
        onConfirm = {
            val amt = amount.toIntOrNull() ?: defaultAmount
            if (selectedScoutIds.size == 1) {
                scoutsMap[selectedScoutIds.first()]?.let { onAddSingle(it, amt) }
            } else {
                onAddAll(amt)
            }
            onDismiss()
        },
        onDismiss = onDismiss,
        modifier = modifier
    )
}

/**
 * حوار إعدادات مساهمة النشاط (تفعيل المساهمات والمبلغ الافتراضي)
 */
@Composable
fun ContributionActivitySettingsDialog(
    currentRequired: Boolean,
    currentDefaultAmount: Int?,
    onSave: (required: Boolean, defaultAmount: Int?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isRequired by remember { mutableStateOf(currentRequired) }
    var defaultAmountText by remember { mutableStateOf(currentDefaultAmount?.toString() ?: "1000") }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag("contribution_settings_dialog"),
        title = {
            Text(
                text = stringResource(id = R.string.contributions_activity_default_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // تفعيل المساهمات
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.contributions_activity_require_toggle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = isRequired,
                        onCheckedChange = { isRequired = it },
                        modifier = Modifier.testTag("contribution_required_switch")
                    )
                }

                // المبلغ الافتراضي
                if (isRequired) {
                    OutlinedTextField(
                        value = defaultAmountText,
                        onValueChange = { defaultAmountText = it.filter { ch -> ch.isDigit() } },
                        label = { Text(stringResource(id = R.string.contributions_activity_default_amount_label)) },
                        suffix = { Text(stringResource(id = R.string.finance_currency)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("contribution_default_amount_input")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = defaultAmountText.toIntOrNull()
                    onSave(isRequired, amount)
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("save_contribution_settings_btn")
            ) {
                Text(stringResource(id = R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_contribution_settings_btn")
            ) {
                Text(stringResource(id = R.string.cancel_action))
            }
        }
    )
}
