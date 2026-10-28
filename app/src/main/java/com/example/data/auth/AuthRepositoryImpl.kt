package com.example.data.auth

import android.util.Log
import com.example.domain.auth.AuthRepository
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

/**
 * تنفيذ مستودع المصادقة (Data Layer Implementation)
 * يربط بين Firebase Auth ومجموعة "leaders" في Cloud Firestore
 * مع المعالجة الذاتية (Self-healing) والتحقق من وجود وثيقة القائد عند تسجيل الدخول.
 */
class AuthRepositoryImpl(
    private val dataSource: FirebaseAuthDataSource,
    private val firestore: FirebaseFirestore
) : AuthRepository {

    companion object {
        private const val TAG = "AuthRepository"
    }

    override val currentUser: FirebaseUser?
        get() = dataSource.currentUser

    override suspend fun login(email: String, password: String): Result<FirebaseUser> {
        val result = dataSource.signInWithEmail(email, password)
        return result.map { user ->
            // فحص واستكمال وثيقة القائد في Firestore إذا كانت مفقودة
            ensureLeaderDocument(user)
            user
        }
    }

    override suspend fun signInWithGoogle(idToken: String): Result<FirebaseUser> {
        val result = dataSource.signInWithGoogle(idToken)
        return result.map { user ->
            ensureLeaderDocument(user)
            user
        }
    }

    override suspend fun signUp(fullName: String, email: String, password: String): Result<Unit> {
        // الخطوة 1: إنشاء مستخدم Firebase Auth، وإذا فشل نرجع الخطأ مباشرة
        val authResult = dataSource.createUserWithEmail(email, password)
        val user = authResult.getOrElse { exception ->
            Log.e(TAG, "Failed to create Firebase Auth user for $email", exception)
            return Result.failure(exception)
        }

        // الخطوة 2: محاولة تحديث الاسم المعروض في الملف الشخصي (إذا فشل، نسجل الخطأ ونتابع)
        try {
            val profileUpdate = UserProfileChangeRequest.Builder()
                .setDisplayName(fullName.trim())
                .build()
            user.updateProfile(profileUpdate).await()
            Log.d(TAG, "Successfully updated display name for user ${user.uid}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update profile display name for ${user.uid}, continuing anyway", e)
        }

        // الخطوة 3: محاولة حفظ وثيقة القائد في Firestore مع إعادة المحاولة
        val leaderData = hashMapOf(
            "uid" to user.uid,
            "fullName" to fullName.trim(),
            "email" to email.trim(),
            "createdAt" to Timestamp.now(),
            "role" to "leader"
        )

        try {
            firestore.collection("leaders")
                .document(user.uid)
                .set(leaderData)
                .await()
            Log.d(TAG, "Leader document written to Firestore for ${user.uid}")
        } catch (firstError: Exception) {
            Log.w(TAG, "First attempt to write leader doc failed for ${user.uid}, retrying after 1.5s", firstError)
            try {
                delay(1500)
                firestore.collection("leaders")
                    .document(user.uid)
                    .set(leaderData)
                    .await()
                Log.d(TAG, "Leader document written successfully on retry for ${user.uid}")
            } catch (retryError: Exception) {
                Log.e(TAG, "Retry write leader doc also failed for ${user.uid}. Document will be auto-created on next login.", retryError)
                // لا نُفشل عملية إنشاء الحساب ككل لأن حساب Auth أُنشئ بنجاح، وسيتم استكمال الوثيقة تلقائياً عند أول تسجيل دخول
            }
        }

        return Result.success(Unit)
    }

    /**
     * دالة مساعدة للتأكد من وجود وثيقة القائد في Firestore واستكمالها تلقائياً إن كانت مفقودة،
     * مع تحديث صورة الملف الشخصي إذا كانت غير مسجلة مسبقاً.
     */
    private suspend fun ensureLeaderDocument(user: FirebaseUser) {
        try {
            val docRef = firestore.collection("leaders").document(user.uid)
            val snapshot = docRef.get().await()
            val userPhotoUrl = user.photoUrl?.toString() ?: ""
            if (!snapshot.exists()) {
                Log.d(TAG, "Leader document missing for ${user.uid}, auto-creating now.")
                val leaderData = hashMapOf(
                    "uid" to user.uid,
                    "fullName" to (user.displayName ?: "قائد كشفي"),
                    "email" to (user.email ?: ""),
                    "photoUrl" to userPhotoUrl,
                    "createdAt" to Timestamp.now(),
                    "role" to "leader"
                )
                docRef.set(leaderData).await()
                Log.d(TAG, "Leader document created successfully for ${user.uid}")
            } else {
                val storedPhotoUrl = snapshot.getString("photoUrl") ?: ""
                if (storedPhotoUrl.isEmpty() && userPhotoUrl.isNotEmpty()) {
                    docRef.update("photoUrl", userPhotoUrl).await()
                    Log.d(TAG, "Updated photoUrl for leader ${user.uid}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to ensure leader document for ${user.uid}", e)
        }
    }

    override fun logout() {
        dataSource.signOut()
    }
}
