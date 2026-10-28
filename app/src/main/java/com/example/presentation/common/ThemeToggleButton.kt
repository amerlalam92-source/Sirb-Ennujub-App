package com.example.presentation.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.data.preferences.ThemePreferences
import com.example.domain.preferences.ThemeMode
import kotlinx.coroutines.launch

/**
 * زر تبديل وضع العرض (فاتح / داكن / تلقائي) المدمج في شريط التطبيق العلوي TopAppBar.
 * يتنقل بالترتيب: SYSTEM -> LIGHT -> DARK -> SYSTEM.
 */
@Composable
fun ThemeToggleButton(
    themePreferences: ThemePreferences,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null
) {
    val currentMode by themePreferences.getThemeMode().collectAsState(initial = themePreferences.getCurrentThemeMode())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val icon = when (currentMode) {
        ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
        ThemeMode.LIGHT -> Icons.Default.LightMode
        ThemeMode.DARK -> Icons.Default.DarkMode
    }

    IconButton(
        onClick = {
            val nextMode = when (currentMode) {
                ThemeMode.SYSTEM -> ThemeMode.LIGHT
                ThemeMode.LIGHT -> ThemeMode.DARK
                ThemeMode.DARK -> ThemeMode.SYSTEM
            }
            themePreferences.setThemeMode(nextMode)

            snackbarHostState?.let { host ->
                val modeLabelRes = when (nextMode) {
                    ThemeMode.LIGHT -> R.string.theme_mode_light
                    ThemeMode.DARK -> R.string.theme_mode_dark
                    ThemeMode.SYSTEM -> R.string.theme_mode_system
                }
                val modeLabel = context.getString(modeLabelRes)
                val message = context.getString(R.string.theme_changed_format, modeLabel)
                scope.launch {
                    host.currentSnackbarData?.dismiss()
                    host.showSnackbar(
                        message = message,
                        duration = SnackbarDuration.Short
                    )
                }
            }
        },
        modifier = modifier.testTag("theme_toggle_button")
    ) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(id = R.string.action_toggle_theme),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
