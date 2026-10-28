package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.example.domain.preferences.ThemeMode
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * مدير تخزين إعدادات السمة محلياً لكل جهاز باستخدام SharedPreferences.
 */
class ThemePreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "scout_theme_preferences"
        private const val KEY_THEME_MODE = "app_theme_mode"
    }

    /**
     * مراقبة حية لتغير وضع السمة عبر [Flow].
     */
    fun getThemeMode(): Flow<ThemeMode> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_THEME_MODE) {
                trySend(readThemeMode())
            }
        }
        // إرسال القيمة الابتدائية فور بدء الاستماع
        trySend(readThemeMode())
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    /**
     * قراءة القيمة الحالية المتزامنة لوضع السمة.
     */
    fun getCurrentThemeMode(): ThemeMode = readThemeMode()

    /**
     * حفظ وضع السمة المختار محلياً على الجهاز.
     */
    fun setThemeMode(mode: ThemeMode) {
        val value = when (mode) {
            ThemeMode.LIGHT -> "light"
            ThemeMode.DARK -> "dark"
            ThemeMode.SYSTEM -> "system"
        }
        prefs.edit().putString(KEY_THEME_MODE, value).apply()
    }

    private fun readThemeMode(): ThemeMode {
        return when (prefs.getString(KEY_THEME_MODE, "system")) {
            "light" -> ThemeMode.LIGHT
            "dark" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }
}
