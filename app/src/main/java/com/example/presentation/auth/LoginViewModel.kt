package com.example.presentation.auth

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.domain.auth.AuthRepository
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * نموذج العرض لشاشة تسجيل الدخول (Login ViewModel)
 * يدير الحالة والتحقق من صحة المدخلات والتواصل مع مستودع المصادقة.
 */
class LoginViewModel(
    private val authRepository: AuthRepository,
    private val googleSignInHelper: com.example.data.auth.GoogleSignInHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // تحديث حقل البريد الإلكتروني
    fun onEmailChanged(email: String) {
        _uiState.update {
            it.copy(
                emailInput = email.trim(),
                emailErrorRes = null,
                generalErrorRes = null
            )
        }
    }

    // تحديث حقل كلمة المرور
    fun onPasswordChanged(password: String) {
        _uiState.update {
            it.copy(
                passwordInput = password,
                passwordErrorRes = null,
                generalErrorRes = null
            )
        }
    }

    // تنفيذ عملية تسجيل الدخول
    fun login() {
        val state = _uiState.value
        val email = state.emailInput
        val password = state.passwordInput

        // التحقق من صحة المدخلات (Input Validation)
        var hasError = false
        var emailErrRes: Int? = null
        var passwordErrRes: Int? = null

        if (email.isBlank()) {
            emailErrRes = R.string.error_email_required
            hasError = true
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailErrRes = R.string.error_invalid_email
            hasError = true
        }

        if (password.isBlank()) {
            passwordErrRes = R.string.error_password_required
            hasError = true
        } else if (password.length < 6) {
            passwordErrRes = R.string.error_password_short
            hasError = true
        }

        if (hasError) {
            _uiState.update {
                it.copy(
                    emailErrorRes = emailErrRes,
                    passwordErrorRes = passwordErrRes
                )
            }
            return
        }

        // بدء تسجيل الدخول في الخلفية
        _uiState.update {
            it.copy(
                isLoading = true,
                generalErrorRes = null
            )
        }

        viewModelScope.launch {
            val result = authRepository.login(email, password)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoginSuccess = true
                        )
                    }
                },
                onFailure = { exception ->
                    val errorRes = when (exception) {
                        is FirebaseAuthInvalidUserException,
                        is FirebaseAuthInvalidCredentialsException -> {
                            R.string.error_invalid_credentials
                        }
                        else -> {
                            R.string.error_login_network
                        }
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

    // تسجيل الدخول باستخدام حساب Google عبر Credential Manager
    fun signInWithGoogle(activity: android.app.Activity) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, generalErrorRes = null) }
            val tokenResult = googleSignInHelper.getGoogleIdToken(activity)
            tokenResult.fold(
                onSuccess = { idToken ->
                    val authResult = authRepository.signInWithGoogle(idToken)
                    authResult.fold(
                        onSuccess = {
                            _uiState.update {
                                it.copy(isLoading = false, isLoginSuccess = true)
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

    // تسجيل الخروج وإعادة تعيين الحالة
    fun logout() {
        authRepository.logout()
        _uiState.update { LoginUiState() }
    }

    // إعادة ضبط علم النجاح بعد التنقل
    fun resetSuccessFlag() {
        _uiState.update { it.copy(isLoginSuccess = false) }
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
            return LoginViewModel(repository, googleSignInHelper) as T
        }
    }
}
