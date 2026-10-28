package com.example.presentation.auth

import androidx.annotation.StringRes

/**
 * حالة واجهة إنشاء حساب جديد (Sign Up UI State)
 * تعتمد على معرفات الموارد (String Resource IDs) لتجنب تسريب السياق.
 */
data class SignUpUiState(
    val fullNameInput: String = "",
    val emailInput: String = "",
    val passwordInput: String = "",
    val confirmPasswordInput: String = "",
    val isLoading: Boolean = false,
    @param:StringRes val fullNameErrorRes: Int? = null,
    @param:StringRes val emailErrorRes: Int? = null,
    @param:StringRes val passwordErrorRes: Int? = null,
    @param:StringRes val confirmPasswordErrorRes: Int? = null,
    @param:StringRes val generalErrorRes: Int? = null,
    val isSignUpSuccess: Boolean = false,
    val snackbarMessage: String? = null
)
