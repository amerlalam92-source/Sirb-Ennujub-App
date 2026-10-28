package com.example.presentation.finance.donations

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
import androidx.compose.material3.ExperimentalMaterial3Api
import com.example.presentation.common.AppAlertDialog
import com.example.presentation.common.AppDatePickerDialog
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
import com.example.presentation.common.AppLocale
import java.text.SimpleDateFormat

/**
 * حوار إضافة وتعديل التبرع المالي (DonationFormDialog)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonationFormDialog(
    formState: DonationFormState,
    onAmountChange: (String) -> Unit,
    onDateChange: (Long) -> Unit,
    onSourceChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDatePicker by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd", AppLocale.AR_DZ) }
    val formattedDate = remember(formState.date) {
        dateFormat.format(formState.date)
    }

    val isEditing = formState.id != null
    val dialogTitle = if (isEditing) {
        stringResource(id = R.string.donations_edit_donation)
    } else {
        stringResource(id = R.string.donations_add_donation)
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
        modifier = modifier.testTag("donation_form_dialog"),
        title = {
            Text(
                text = dialogTitle,
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
                // المبلغ (دج)
                OutlinedTextField(
                    value = formState.amountText,
                    onValueChange = onAmountChange,
                    label = { Text(stringResource(id = R.string.donations_amount_label)) },
                    placeholder = { Text("مثال: 5000") },
                    suffix = { Text(stringResource(id = R.string.finance_currency)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = formState.amountError != null,
                    supportingText = formState.amountError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("donation_amount_input")
                )

                // التاريخ
                OutlinedTextField(
                    value = formattedDate,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(id = R.string.donations_date_label)) },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = stringResource(id = R.string.donations_date_label)
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true }
                        .testTag("donation_date_picker")
                )

                // اسم المتبرع / الجهة المتبرعة
                OutlinedTextField(
                    value = formState.source,
                    onValueChange = onSourceChange,
                    label = { Text(stringResource(id = R.string.donations_donor_label)) },
                    placeholder = { Text("مثال: فاعل خير، جمعية البر...") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("donation_source_input")
                )

                // الوصف أو الغرض
                OutlinedTextField(
                    value = formState.description,
                    onValueChange = onDescriptionChange,
                    label = { Text(stringResource(id = R.string.donations_description_label)) },
                    placeholder = { Text("ملاحظات إضافية حول التبرع...") },
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("donation_description_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = !formState.isSaving,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("donation_save_button")
            ) {
                if (formState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                }
                Text(stringResource(id = R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !formState.isSaving,
                modifier = Modifier.testTag("donation_cancel_button")
            ) {
                Text(stringResource(id = R.string.cancel_action))
            }
        }
    )
}
