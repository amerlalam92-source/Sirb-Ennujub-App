package com.example.presentation.finance.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.presentation.common.AppAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import java.util.Calendar

/**
 * حوار اختيار الشهر والسنة لتصدير كشف الحساب الشهري كـ PDF
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthPickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (month: Int, year: Int) -> Unit,
    initialMonth: Int = Calendar.getInstance().get(Calendar.MONTH) + 1,
    initialYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    modifier: Modifier = Modifier
) {
    val months = listOf(
        "يناير (01)", "فبراير (02)", "مارس (03)", "أبريل (04)",
        "مايو (05)", "يونيو (06)", "يوليو (07)", "أغسطس (08)",
        "سبتمبر (09)", "أكتوبر (10)", "نوفمبر (11)", "ديسمبر (12)"
    )

    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val years = listOf(currentYear, currentYear - 1, currentYear - 2)

    var selectedMonth by remember { mutableIntStateOf(initialMonth.coerceIn(1, 12)) }
    var selectedYear by remember { mutableIntStateOf(initialYear) }

    var isMonthExpanded by remember { mutableStateOf(false) }
    var isYearExpanded by remember { mutableStateOf(false) }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag("month_picker_dialog"),
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = stringResource(id = R.string.month_picker_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Dropdown اختيار الشهر
                ExposedDropdownMenuBox(
                    expanded = isMonthExpanded,
                    onExpandedChange = { isMonthExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = months.getOrElse(selectedMonth - 1) { "شهر $selectedMonth" },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(id = R.string.month_picker_month)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isMonthExpanded) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                            .testTag("month_selector_dropdown")
                    )

                    ExposedDropdownMenu(
                        expanded = isMonthExpanded,
                        onDismissRequest = { isMonthExpanded = false }
                    ) {
                        months.forEachIndexed { index, name ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    selectedMonth = index + 1
                                    isMonthExpanded = false
                                }
                            )
                        }
                    }
                }

                // Dropdown اختيار السنة
                ExposedDropdownMenuBox(
                    expanded = isYearExpanded,
                    onExpandedChange = { isYearExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedYear.toString(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(id = R.string.month_picker_year)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isYearExpanded) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                            .testTag("year_selector_dropdown")
                    )

                    ExposedDropdownMenu(
                        expanded = isYearExpanded,
                        onDismissRequest = { isYearExpanded = false }
                    ) {
                        years.forEach { y ->
                            DropdownMenuItem(
                                text = { Text(y.toString()) },
                                onClick = {
                                    selectedYear = y
                                    isYearExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedMonth, selectedYear) },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("month_picker_confirm_button")
            ) {
                Text(stringResource(id = R.string.action_export_pdf))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("month_picker_cancel_button")
            ) {
                Text(stringResource(id = R.string.cancel_action))
            }
        }
    )
}
