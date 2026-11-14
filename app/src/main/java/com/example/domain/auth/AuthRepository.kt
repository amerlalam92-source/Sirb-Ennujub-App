package com.example.domain.auth

import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.Flow

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
    fun observeAllLeaders(): Flow<List<Leader>>
    fun observeCurrentLeader(): Flow<Leader?>
    suspend fun getAllLeaders(excludeUid: String): Result<List<Leader>>
    suspend fun getCurrentLeader(): Leader?
    suspend fun updateLeaderStatus(uid: String, status: String): Result<Unit>
    suspend fun deleteLeader(uid: String): Result<Unit>
    suspend fun toggleMainLeader(uid: String, isMainLeader: Boolean): Result<Unit>
    suspend fun updateMainLeader(newLeaderId: String): Result<Unit>
    suspend fun deleteAccount(user: FirebaseUser): Result<Unit>
    fun isSuperAdmin(): Boolean
}
