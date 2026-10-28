package com.example.presentation.quran.tracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.quran.SelectedPeriod
import com.example.domain.quran.StudyPeriod

/**
 * مكون اختيار فترات الدوام القرآني مع حقل وقت البدء المخصص للفترة الصباحية
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudyPeriodsSelector(
    selectedPeriods: List<SelectedPeriod>,
    onPeriodToggled: (StudyPeriod) -> Unit,
    onMorningStartTimeChanged: (String) -> Unit,
    morningStartTime: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    timeFormatError: Boolean = false
) {
    val morningIsSelected = selectedPeriods.any { it.period == StudyPeriod.MORNING }

    Column(modifier = modifier.fillMaxWidth()) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("study_periods_selector"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            StudyPeriod.entries.forEach { period ->
                val isSelected = selectedPeriods.any { it.period == period }
                FilterChip(
                    selected = isSelected,
                    onClick = { onPeriodToggled(period) },
                    label = { Text(stringResource(period.labelRes)) },
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier.testTag("study_period_chip_${period.key}")
                )
            }
        }

        if (isError) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.quran_enrollment_periods_error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("study_periods_error_text")
            )
        }

        // حقل وقت البدء يظهر فقط إذا تم اختيار الفترة الصباحية (Morning)
        if (morningIsSelected) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = morningStartTime,
                onValueChange = onMorningStartTimeChanged,
                label = { Text(stringResource(R.string.quran_enrollment_morning_start_time_label)) },
                placeholder = { Text("08:30") },
                isError = timeFormatError,
                supportingText = if (timeFormatError) {
                    { Text(stringResource(R.string.quran_enrollment_time_format_error)) }
                } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("morning_start_time_input")
            )
        }
    }
}
