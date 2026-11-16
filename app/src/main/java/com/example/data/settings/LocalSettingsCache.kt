package com.example.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.example.domain.settings.CustomTerms
import org.json.JSONObject

/**
 * ذاكرة تخزين مؤقتة محلية لإعدادات الهوية البصرية والمصطلحات المخصصة (SharedPreferences)
 * تتيح عرض الشعار والمصطلحات المخصصة في شاشة تسجيل الدخول قبل المصادقة مع Firebase.
 */
object LocalSettingsCache {

    private const val PREFS_NAME = "app_settings_cache"
    private const val KEY_LOGO_BASE64 = "logo_base64"
    private const val KEY_CUSTOM_TERMS_JSON = "custom_terms_json"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveLogo(context: Context, base64: String?) {
        val editor = getPrefs(context).edit()
        if (base64.isNullOrBlank()) {
            editor.remove(KEY_LOGO_BASE64)
        } else {
            editor.putString(KEY_LOGO_BASE64, base64)
        }
        editor.apply()
    }

    fun getLogo(context: Context): String? {
        return getPrefs(context).getString(KEY_LOGO_BASE64, null)?.takeIf { it.isNotBlank() }
    }

    fun saveTerms(context: Context, terms: CustomTerms) {
        val customWordsJson = JSONObject().apply {
            terms.customWords.forEach { (original, replacement) ->
                put(original, replacement)
            }
        }
        val json = JSONObject().apply {
            put("scoutSingular", terms.scoutSingular)
            put("scoutSingularDefinite", terms.scoutSingularDefinite)
            put("scoutPlural", terms.scoutPlural)
            put("scoutPluralDefinite", terms.scoutPluralDefinite)
            put("groupName", terms.groupName)
            put("groupNameDefinite", terms.groupNameDefinite)
            put("unitName", terms.unitName)
            put("unitNameDefinite", terms.unitNameDefinite)
            put("patrolName", terms.patrolName)
            put("customWords", customWordsJson)
        }
        getPrefs(context).edit()
            .putString(KEY_CUSTOM_TERMS_JSON, json.toString())
            .apply()
    }

    fun getTerms(context: Context): CustomTerms? {
        val raw = getPrefs(context).getString(KEY_CUSTOM_TERMS_JSON, null) ?: return null
        return try {
            val json = JSONObject(raw)
            val defaults = CustomTerms()
            val wordsObj = json.optJSONObject("customWords")
            val wordsMap = buildMap {
                if (wordsObj != null) {
                    val keys = wordsObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val value = wordsObj.optString(key, "")
                        if (key.isNotEmpty()) {
                            put(key, value)
                        }
                    }
                }
            }
            CustomTerms(
                scoutSingular = json.optString("scoutSingular", defaults.scoutSingular),
                scoutSingularDefinite = json.optString("scoutSingularDefinite", defaults.scoutSingularDefinite),
                scoutPlural = json.optString("scoutPlural", defaults.scoutPlural),
                scoutPluralDefinite = json.optString("scoutPluralDefinite", defaults.scoutPluralDefinite),
                groupName = json.optString("groupName", defaults.groupName),
                groupNameDefinite = json.optString("groupNameDefinite", defaults.groupNameDefinite),
                unitName = json.optString("unitName", defaults.unitName),
                unitNameDefinite = json.optString("unitNameDefinite", defaults.unitNameDefinite),
                patrolName = json.optString("patrolName", defaults.patrolName),
                customWords = wordsMap
            )
        } catch (_: Exception) {
            null
        }
    }
}
