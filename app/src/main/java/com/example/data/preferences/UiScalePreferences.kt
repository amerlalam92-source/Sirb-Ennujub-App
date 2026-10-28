package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * مدير تخزين نسبة تكبير/تصغير واجهة المستخدم محلياً على الجهاز.
 * يتيح التحكم في حجم الواجهة من -20% (0.8f) إلى +20% (1.2f) مع استمرارية الحفظ.
 */
class UiScalePreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "scout_ui_scale_preferences"
        const val KEY_UI_SCALE = "app_ui_scale"
        const val DEFAULT_UI_SCALE = 1.0f
        const val MIN_UI_SCALE = 0.8f
        const val MAX_UI_SCALE = 1.2f
    }

    /**
     * تدفق يراقب التغيرات في نسبة تكبير واجهة التطبيق.
     */
    fun getUiScale(): Flow<Float> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_UI_SCALE) {
                trySend(readUiScale())
            }
        }
        trySend(readUiScale())
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    /**
     * قراءة القيمة الحالية المتزامنة لنسبة التكبير.
     */
    fun getCurrentUiScale(): Float = readUiScale()

    /**
     * حفظ نسبة تكبير الواجهة مع التحقق من النطاق المسموح (0.8f إلى 1.2f).
     */
    fun setUiScale(scale: Float) {
        val coerced = scale.coerceIn(MIN_UI_SCALE, MAX_UI_SCALE)
        prefs.edit().putFloat(KEY_UI_SCALE, coerced).apply()
    }

    private fun readUiScale(): Float {
        val value = prefs.getFloat(KEY_UI_SCALE, DEFAULT_UI_SCALE)
        return value.coerceIn(MIN_UI_SCALE, MAX_UI_SCALE)
    }
}
