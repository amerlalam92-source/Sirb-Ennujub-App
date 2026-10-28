package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.example.domain.preferences.ThemeMode

val LightColorScheme = lightColorScheme(
    primary = ScoutDarkBrown,               // #5D3A1A
    onPrimary = Color(0xFFFFFFFF),          // #FFFFFF
    primaryContainer = Color(0xFFEEDFC9),   // #EEDFC9
    onPrimaryContainer = Color(0xFF3E2A15), // #3E2A15

    secondary = ScoutOrange,                // #F57C00
    onSecondary = Color(0xFFFFFFFF),        // #FFFFFF
    secondaryContainer = Color(0xFFFFE0B2), // #FFE0B2
    onSecondaryContainer = Color(0xFF5D3A1A), // #5D3A1A

    tertiary = ScoutYellow,                 // #FFC107
    onTertiary = Color(0xFF3E2A15),         // #3E2A15
    tertiaryContainer = Color(0xFFFFF3C4),  // #FFF3C4
    onTertiaryContainer = Color(0xFF5D3A1A), // #5D3A1A

    background = CreamWhite,                // #FFFBF5
    onBackground = Color(0xFF2B1D10),       // #2B1D10

    surface = CreamCard,                    // #FFFFFF
    onSurface = Color(0xFF2B1D10),          // #2B1D10
    surfaceVariant = CreamCardTint,         // #FFF8E7
    onSurfaceVariant = Color(0xFF6B5A48),   // #6B5A48

    outline = Color(0xFFE8D9C0),            // #E8D9C0
    outlineVariant = Color(0xFFF2E5CC),     // #F2E5CC

    error = Color(0xFFC62828),              // #C62828
    onError = Color(0xFFFFFFFF)             // #FFFFFF
)

val DarkColorScheme = darkColorScheme(
    primary = ScoutYellow,                  // #FFC107 (vivid yellow — accents, FABs, buttons)
    onPrimary = Color(0xFF2B1D10),          // #2B1D10
    primaryContainer = Color(0xFF5D3A1A),   // #5D3A1A
    onPrimaryContainer = Color(0xFFFFE082), // #FFE082

    secondary = ScoutOrangeVivid,           // #FF9800
    onSecondary = Color(0xFF2B1D10),        // #2B1D10
    secondaryContainer = Color(0xFF6B3D00), // #6B3D00
    onSecondaryContainer = Color(0xFFFFE0B2), // #FFE0B2

    tertiary = Color(0xFFFFD54F),           // #FFD54F
    onTertiary = Color(0xFF2B1D10),         // #2B1D10
    tertiaryContainer = Color(0xFF5D3A1A),  // #5D3A1A
    onTertiaryContainer = Color(0xFFFFF3C4), // #FFF3C4

    background = Color(0xFF1A1410),         // #1A1410 (near-black warm brown)
    onBackground = Color(0xFFF5E6D3),       // #F5E6D3

    surface = Color(0xFF2E2419),            // #2E2419 (clearly lighter than background → real contrast)
    onSurface = Color(0xFFF5E6D3),          // #F5E6D3
    surfaceVariant = Color(0xFF3A2E20),     // #3A2E20 (cards that need a little pop)
    onSurfaceVariant = Color(0xFFD4C2A8),   // #D4C2A8

    outline = Color(0xFF5A4228),            // #5A4228
    outlineVariant = Color(0xFF40301A),     // #40301A

    error = Color(0xFFFFB4AB),              // #FFB4AB
    onError = Color(0xFF690005)             // #690005
)

@Composable
fun ScoutTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides AppTypography.bodyLarge.copy(color = colorScheme.onBackground),
            LocalContentColor provides colorScheme.onBackground,
            LocalLayoutDirection provides LayoutDirection.Rtl
        ) {
            content()
        }
    }
}

// توافقية مع الاستدعاءات التي تمرر قيمة منطقية (Boolean)
@Composable
fun ScoutTheme(
    darkTheme: Boolean,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    ScoutTheme(
        themeMode = if (darkTheme) ThemeMode.DARK else ThemeMode.LIGHT,
        dynamicColor = dynamicColor,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    ScoutTheme(themeMode = themeMode, dynamicColor = dynamicColor, content = content)
}
