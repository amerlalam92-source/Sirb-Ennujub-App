package com.example.presentation.finance.expenses

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import com.example.presentation.common.AppAlertDialog
import com.example.presentation.common.AppDatePickerDialog
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import com.example.domain.finance.ExpenseCategory
import com.example.presentation.common.AppLocale
import java.text.SimpleDateFormat

/**
 * حوار إضافة أو تعديل المصروف الكشفي (ExpenseFormDialog)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseFormDialog(
    formState: ExpenseFormState,
    onAmountChange: (String) -> Unit,
    onCategoryChange: (ExpenseCategory) -> Unit,
    onDateChange: (Long) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var categoryDropdownExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd", AppLocale.AR_DZ) }
    val formattedDate = remember(formState.date) {
        dateFormat.format(formState.date)
    }

    val isEditing = formState.id != null
    val title = if (isEditing) {
        stringResource(id = R.string.expenses_edit)
    } else {
        stringResource(id = R.string.expenses_add)
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = formState.date)
        AppDatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { onDateChange(it) }
                        showDatePicker = false
                    }
                ) {
                    Text(stringResource(id = R.string.confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(id = R.string.cancel_action))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag("expense_form_dialog"),
        title = {
            Text(
                text = title,
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
                // 1) المبلغ (إجباري)
                OutlinedTextField(
                    value = formState.amountText,
                    onValueChange = onAmountChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("expense_dialog_amount_input"),
                    label = { Text(stringResource(id = R.string.expenses_amount_label) + " *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = formState.amountError != null,
                    supportingText = formState.amountError?.let {
                        { Text(text = it, color = MaterialTheme.colorScheme.error) }
                    },
                    singleLine = true
                )

                // 2) التصنيف (إجباري عبر قائمة منسدلة)
                ExposedDropdownMenuBox(
                    expanded = categoryDropdownExpanded,
                    onExpandedChange = { categoryDropdownExpanded = !categoryDropdownExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = formState.category?.arabicLabel.orEmpty(),
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                            .testTag("expense_dialog_category_dropdown"),
                        label = { Text(stringResource(id = R.string.expenses_category_label) + " *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryDropdownExpanded) },
                        isError = formState.categoryError != null,
                        supportingText = formState.categoryError?.let {
                            { Text(text = it, color = MaterialTheme.colorScheme.error) }
                        }
                    )
                    ExposedDropdownMenu(
                        expanded = categoryDropdownExpanded,
                        onDismissRequest = { categoryDropdownExpanded = false }
                    ) {
                        ExpenseCategory.entries.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.arabicLabel) },
                                onClick = {
                                    onCategoryChange(cat)
                                    categoryDropdownExpanded = false
                                },
                                modifier = Modifier.testTag("expense_category_item_${cat.key}")
                            )
                        }
                    }
                }

                // 3) التاريخ
                OutlinedTextField(
                    value = formattedDate,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true }
                        .testTag("expense_dialog_date_button"),
                    label = { Text(stringResource(id = R.string.expenses_date_label)) },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = stringResource(id = R.string.expenses_date_label)
                        )
                    },
                    enabled = true
                )

                // 4) الوصف (اختياري / متعدد الأسطر)
                OutlinedTextField(
                    value = formState.description,
                    onValueChange = onDescriptionChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("expense_dialog_description_input"),
                    label = { Text(stringResource(id = R.string.expenses_description_label)) },
                    minLines = 2,
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = !formState.isSaving,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("expense_dialog_save_button")
            ) {
                if (formState.isSaving) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(id = R.string.confirm_action))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !formState.isSaving,
                modifier = Modifier.testTag("expense_dialog_cancel_button")
            ) {
                Text(stringResource(id = R.string.cancel_action))
            }
        }
    )
}
