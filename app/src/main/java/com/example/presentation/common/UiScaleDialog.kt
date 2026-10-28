package com.example.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import kotlin.math.roundToInt

/**
 * نافذة حوار للتحكم في تكبير وتصغير واجهة التطبيق كاملة (UI Scale Dialog)
 * بنسبة تمتد من 80% إلى 120% بخطوات 10%.
 */
@Composable
fun UiScaleDialog(
    currentScale: Float,
    onScaleChanged: (Float) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalAppDensity.current
    val percentage = (currentScale * 100f).roundToInt()

    AppAlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag("ui_scale_dialog"),
        title = {
            Text(
                text = stringResource(id = R.string.ui_scale_dialog_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // عرض النسبة المئوية الحالية
                Text(
                    text = stringResource(id = R.string.ui_scale_current_format, percentage),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("ui_scale_current_text")
                )

                Spacer(modifier = Modifier.height(16.dp))

                // منزلق التحكم بالحجم (Slider)
                Slider(
                    value = currentScale,
                    onValueChange = { newValue ->
                        val rounded = (newValue * 10f).roundToInt() / 10f
                        onScaleChanged(rounded)
                    },
                    valueRange = 0.8f..1.2f,
                    steps = 3,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ui_scale_slider")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // زرا أصغر وأكبر
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { onScaleChanged(0.8f) },
                        modifier = Modifier.testTag("ui_scale_smaller_button")
                    ) {
                        Text(
                            text = stringResource(id = R.string.ui_scale_smaller),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }

                    TextButton(
                        onClick = { onScaleChanged(1.0f) },
                        modifier = Modifier.testTag("ui_scale_default_button")
                    ) {
                        Text(
                            text = "100%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    TextButton(
                        onClick = { onScaleChanged(1.2f) },
                        modifier = Modifier.testTag("ui_scale_larger_button")
                    ) {
                        Text(
                            text = stringResource(id = R.string.ui_scale_larger),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("ui_scale_confirm_button")
            ) {
                Text(
                    text = stringResource(id = R.string.ui_scale_confirm),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    )
}
