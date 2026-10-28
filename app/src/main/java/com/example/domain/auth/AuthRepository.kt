package com.example.domain.auth

import com.google.firebase.auth.FirebaseUser

/**
 * واجهة مستودع المصادقة (Domain Layer)
 * تحدد العمليات المتاحة دون الاعتماد على طريقة التنفيذ.
 */
interface AuthRepository {
    val currentUser: FirebaseUser?
    suspend fun login(email: String, password: String): Result<FirebaseUser>
    suspend fun signInWithGoogle(idToken: String): Result<FirebaseUser>
    suspend fun signUp(fullName: String, email: String, password: String): Result<Unit>
    fun logout()
}
