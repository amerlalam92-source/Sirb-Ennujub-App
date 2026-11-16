package com.example.domain.settings

data class CustomTerms(
    val scoutSingular: String = "شبل",
    val scoutSingularDefinite: String = "الشبل",
    val scoutPlural: String = "أشبال",
    val scoutPluralDefinite: String = "الأشبال",
    val groupName: String = "سرب",
    val groupNameDefinite: String = "السرب",
    val unitName: String = "النجب",
    val unitNameDefinite: String = "النجب",
    val patrolName: String = "السداسية",
    val customWords: Map<String, String> = emptyMap() // Key: original, Value: replacement
)

val LocalAppTerms get() = com.example.presentation.common.LocalAppTerms

fun replaceCustomWords(text: String, terms: CustomTerms): String =
    com.example.presentation.common.replaceCustomWords(text, terms)
