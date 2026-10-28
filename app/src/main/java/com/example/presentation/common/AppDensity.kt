package com.example.presentation.common

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Density

/**
 * كائن CompositionLocal لتمرير مقياس الكثافة العام للتطبيق (AppDensity)
 * يضمن ثبات مقياس الخط fontScale عند 1.0f وتطبيق uiScale الداخلي على كافة النوافذ بما فيها الحوارات (Dialogs).
 */
val LocalAppDensity = staticCompositionLocalOf<Density> {
    error("No AppDensity provided")
}
