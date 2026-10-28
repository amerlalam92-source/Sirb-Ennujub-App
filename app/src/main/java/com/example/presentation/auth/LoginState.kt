package com.example.presentation.auth

import androidx.annotation.StringRes

/**
 * حالة واجهة تسجيل الدخول (UI State)
 * تعتمد على معرفات الموارد (String Resource IDs) لتجنب تسريب السياق (Context).
 */
data class LoginUiState(
    val emailInput: String = "",
    val passwordInput: String = "",
    val isLoading: Boolean = false,
    @param:StringRes val emailErrorRes: Int? = null,
    @param:StringRes val passwordErrorRes: Int? = null,
    @param:StringRes val generalErrorRes: Int? = null,
    val isLoginSuccess: Boolean = false,
    val snackbarMessage: String? = null
)
