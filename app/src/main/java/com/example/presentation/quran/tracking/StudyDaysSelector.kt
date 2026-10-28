package com.example.presentation.quran.tracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R

/**
 * خيارات أيام الأسبوع مع المفاتيح الموحدة
 */
enum class QuranStudyDay(val key: String, val labelRes: Int) {
    SATURDAY("saturday", R.string.day_saturday),
    SUNDAY("sunday", R.string.day_sunday),
    MONDAY("monday", R.string.day_monday),
    TUESDAY("tuesday", R.string.day_tuesday),
    WEDNESDAY("wednesday", R.string.day_wednesday),
    THURSDAY("thursday", R.string.day_thursday),
    FRIDAY("friday", R.string.day_friday);

    companion object {
        fun fromKey(key: String): QuranStudyDay? = entries.firstOrNull { it.key == key }
    }
}

/**
 * مكون متعدد الاختيارات لتحديد أيام الدوام بالكتّاب
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudyDaysSelector(
    selectedDays: List<String>,
    onDayToggled: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false
) {
    Column(modifier = modifier.fillMaxWidth()) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("study_days_selector"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            QuranStudyDay.entries.forEach { day ->
                val isSelected = selectedDays.contains(day.key)
                FilterChip(
                    selected = isSelected,
                    onClick = { onDayToggled(day.key) },
                    label = { Text(stringResource(day.labelRes)) },
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.testTag("study_day_chip_${day.key}")
                )
            }
        }
        if (isError) {
            Text(
                text = stringResource(R.string.quran_enrollment_days_error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("study_days_error_text")
            )
        }
    }
}
