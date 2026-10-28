package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// =============================================================
// ألوان الهوية الكشفية الرسمية لمرحلة الأشبال (Scout Brand Colors)
// مستخرجة ومطابقة لشعار مرحلة الأشبال
// =============================================================

// === BRAND CONSTANTS (from the الأشبال logo) ===
val ScoutDarkBrown      = Color(0xFF5D3A1A)
val ScoutBrown          = Color(0xFF7A4E25)
val ScoutLightBrown     = Color(0xFFA67C52)
val ScoutOrange         = Color(0xFFF57C00)
val ScoutOrangeVivid    = Color(0xFFFF9800)
val ScoutOrangeLight    = Color(0xFFFFB74D)
val ScoutYellow         = Color(0xFFFFC107)
val ScoutYellowLight    = Color(0xFFFFE082)
val CreamWhite          = Color(0xFFFFFBF5)
val CreamCard           = Color(0xFFFFFFFF)
val CreamCardTint       = Color(0xFFFFF8E7)

// Common / Brand Colors
val DarkTextPrimary     = Color(0xFF2B1D10)
val DarkTextSecondary   = Color(0xFF6B5A48)
val CreamText           = Color(0xFFF5E6D3)
val BorderLight         = Color(0xFFE8D9C0)
val BorderDark          = Color(0xFF5A4228)
val ErrorRed            = Color(0xFFC62828)
val SuccessGreen        = Color(0xFF2E7D32)

// Legacy Aliases & Fallbacks
val ScoutGreen10 = Color(0xFF002204)
val ScoutGreen20 = Color(0xFF0D3911)
val ScoutGreen30 = Color(0xFF1B5E20)
val ScoutGreen40 = SuccessGreen
val ScoutGreen80 = Color(0xFF81C784)
val ScoutGreen90 = Color(0xFFC8E6C9)

val ScoutGold10 = Color(0xFF281E00)
val ScoutGold20 = Color(0xFF423400)
val ScoutGold30 = Color(0xFF715B00)
val ScoutGold40 = ScoutYellow
val ScoutGold80 = ScoutYellowLight
val ScoutGold90 = Color(0xFFFBF4DC)

val ScoutBrown10 = Color(0xFF251A0E)
val ScoutBrown20 = Color(0xFF3F2E1E)
val ScoutBrown30 = Color(0xFF5B4430)
val ScoutBrown40 = ScoutBrown
val ScoutBrown80 = Color(0xFFD7CCC8)
val ScoutBrown90 = Color(0xFFEFEBE9)

val NeutralLightBackground = CreamWhite
val SurfaceLight = CreamCard
val LightSurfaceVariant = CreamCardTint
val LightTextPrimary = DarkTextPrimary
val LightTextSecondary = DarkTextSecondary
val LightBorder = BorderLight

val DarkBackground = Color(0xFF1A1410)
val DarkSurface = Color(0xFF2E2419)
val DarkSurfaceVariant = Color(0xFF3A2E20)
val DarkBorder = Color(0xFF5A4228)

val ErrorRedLight = Color(0xFFFFDAD6)

// Semantic colors for Quran Scout Enrollment status chips (theme-aware)
val EnrolledGreenContainerLight = Color(0xFFE8F5E9)
val EnrolledGreenContainerDark  = Color(0xFF1B4A2A)
val EnrolledGreenContentLight   = Color(0xFF1B5E20)
val EnrolledGreenContentDark    = Color(0xFFA5D6A7)
val NotEnrolledRedContainerLight = Color(0xFFFFEBEE)
val NotEnrolledRedContainerDark  = Color(0xFF4A1F1F)
val NotEnrolledRedContentLight   = Color(0xFFB71C1C)
val NotEnrolledRedContentDark    = Color(0xFFEF9A9A)
