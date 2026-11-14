package com.example.presentation.common

import androidx.compose.runtime.staticCompositionLocalOf

data class StageTerms(
    val singular: String = "شبل",
    val plural: String = "أشبال",
    val groupName: String = "سرب النُّجُب",
    val leaderTitle: String = "قائد السرب",
    val fojName: String = "فوج محمد رحال"
)

val LocalStageTerms = staticCompositionLocalOf { StageTerms() }

fun getStageTerms(unitType: String? = null): StageTerms = StageTerms()
