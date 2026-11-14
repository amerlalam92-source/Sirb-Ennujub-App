package com.example.presentation.common

import androidx.compose.runtime.staticCompositionLocalOf
import com.example.domain.settings.CustomTerms

val LocalAppTerms = staticCompositionLocalOf { CustomTerms() }

fun replaceCustomWords(text: String, terms: CustomTerms): String {
    if (text.isEmpty() || terms.customWords.isEmpty()) return text
    var result = text
    terms.customWords.forEach { (original, replacement) ->
        if (original.isNotEmpty()) {
            result = result.replace(original, replacement)
        }
    }
    return result
}
