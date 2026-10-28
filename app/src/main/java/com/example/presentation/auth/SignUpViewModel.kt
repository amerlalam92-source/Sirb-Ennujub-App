package com.example.presentation.auth

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.domain.auth.AuthRepository
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * نموذج العرض لشاشة إنشاء حساب جديد (SignUp ViewModel)
 * يدير التحقق من المدخلات وعملية التسجيل مع تصنيف دقيق لرسائل الأخطاء.
 */
class SignUpViewModel(
    private val authRepository: AuthRepository,
    private val googleSignInHelper: com.example.data.auth.GoogleSignInHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignUpUiState())
    val uiState: StateFlow<SignUpUiState> = _uiState.asStateFlow()

    fun onFullNameChanged(name: String) {
        _uiState.update {
            it.copy(
                fullNameInput = name,
                fullNameErrorRes = null,
                generalErrorRes = null
            )
        }
    }

    fun onEmailChanged(email: String) {
        _uiState.update {
            it.copy(
                emailInput = email.trim(),
                emailErrorRes = null,
                generalErrorRes = null
            )
        }
    }

    fun onPasswordChanged(password: String) {
        _uiState.update {
            it.copy(
                passwordInput = password,
                passwordErrorRes = null,
                confirmPasswordErrorRes = null,
                generalErrorRes = null
            )
        }
    }

    fun onConfirmPasswordChanged(password: String) {
        _uiState.update {
            it.copy(
                confirmPasswordInput = password,
                confirmPasswordErrorRes = null,
                generalErrorRes = null
            )
        }
    }

    fun signUp() {
        val state = _uiState.value
        val fullName = state.fullNameInput.trim()
        val email = state.emailInput.trim()
        val password = state.passwordInput
        val confirmPassword = state.confirmPasswordInput

        var hasError = false
        var nameErrRes: Int? = null
        var emailErrRes: Int? = null
        var passwordErrRes: Int? = null
        var confirmErrRes: Int? = null

        // التحقق من صحة الاسم
        if (fullName.isBlank()) {
            nameErrRes = R.string.error_name_required
            hasError = true
        } else if (fullName.length < 3) {
            nameErrRes = R.string.error_name_short
            hasError = true
        }

        // التحقق من صحة البريد
        if (email.isBlank()) {
            emailErrRes = R.string.error_email_required
            hasError = true
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailErrRes = R.string.error_invalid_email
            hasError = true
        }

        // التحقق من كلمة المرور
        if (password.isBlank()) {
            passwordErrRes = R.string.error_password_required
            hasError = true
        } else if (password.length < 6) {
            passwordErrRes = R.string.error_password_short
            hasError = true
        }

        // التحقق من تطابق كلمتي المرور
        if (confirmPassword.isBlank()) {
            confirmErrRes = R.string.error_confirm_password_required
            hasError = true
        } else if (confirmPassword != password) {
            confirmErrRes = R.string.error_passwords_do_not_match
            hasError = true
        }

        if (hasError) {
            _uiState.update {
                it.copy(
                    fullNameErrorRes = nameErrRes,
                    emailErrorRes = emailErrRes,
                    passwordErrorRes = passwordErrRes,
                    confirmPasswordErrorRes = confirmErrRes
                )
            }
            return
        }

        // بدء عملية إنشاء الحساب
        _uiState.update {
            it.copy(
                isLoading = true,
                generalErrorRes = null
            )
        }

        viewModelScope.launch {
            val result = authRepository.signUp(fullName, email, password)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isSignUpSuccess = true
                        )
                    }
                },
                onFailure = { exception ->
                    val errorRes = when (exception) {
                        is FirebaseAuthUserCollisionException -> R.string.error_email_already_in_use
                        is FirebaseAuthWeakPasswordException -> R.string.error_weak_password
                        is FirebaseAuthInvalidCredentialsException -> R.string.error_invalid_email
                        is FirebaseFirestoreException -> R.string.error_profile_creation_deferred
                        else -> R.string.error_profile_creation_deferred
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            generalErrorRes = errorRes
                        )
                    }
                }
            )
        }
    }

    // تسجيل/إنشاء حساب باستخدام حساب Google عبر Credential Manager
    fun signUpWithGoogle(activity: android.app.Activity) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, generalErrorRes = null) }
            val tokenResult = googleSignInHelper.getGoogleIdToken(activity)
            tokenResult.fold(
                onSuccess = { idToken ->
                    val authResult = authRepository.signInWithGoogle(idToken)
                    authResult.fold(
                        onSuccess = {
                            _uiState.update {
                                it.copy(isLoading = false, isSignUpSuccess = true)
                            }
                        },
                        onFailure = { e ->
                            _uiState.update {
                                it.copy(isLoading = false, generalErrorRes = mapGoogleError(e))
                            }
                        }
                    )
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isLoading = false, generalErrorRes = mapGoogleError(e))
                    }
                }
            )
        }
    }

    private fun mapGoogleError(e: Throwable): Int {
        return when (e) {
            is androidx.credentials.exceptions.GetCredentialCancellationException ->
                R.string.error_google_signin_cancelled
            is androidx.credentials.exceptions.GetCredentialException ->
                R.string.error_google_no_credential
            is com.google.firebase.FirebaseNetworkException ->
                R.string.error_google_network
            else ->
                R.string.error_google_signin_failed
        }
    }

    fun resetSuccessFlag() {
        _uiState.update { it.copy(isSignUpSuccess = false) }
    }

    fun clearSnackbarMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    class Factory(
        private val repository: AuthRepository,
        private val googleSignInHelper: com.example.data.auth.GoogleSignInHelper
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SignUpViewModel(repository, googleSignInHelper) as T
        }
    }
}
