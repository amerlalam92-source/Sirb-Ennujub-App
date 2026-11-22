package com.example.data.auth

import android.util.Log
import com.example.data.common.DataLoadErrorReporter
import com.example.data.common.ListenerFallbackState
import com.example.data.firestore.safeBoolean
import com.example.data.firestore.safeNullableString
import com.example.data.firestore.safeString
import com.example.domain.auth.AuthRepository
import com.example.domain.auth.Leader
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

/**
 * تنفيذ مستودع المصادقة (Data Layer Implementation)
 * يربط بين Firebase Auth ومجموعة "leaders" في Cloud Firestore
 * مع إدارة حالة القادة (قيد المراجعة / معتمد) والرئاسة الكشفية.
 */
class AuthRepositoryImpl(
    private val dataSource: FirebaseAuthDataSource,
    private val firestore: FirebaseFirestore,
    private val errorReporter: DataLoadErrorReporter? = null
) : AuthRepository {

    companion object {
        private const val TAG = "AuthRepository"
    }

    override val currentUser: FirebaseUser?
        get() = dataSource.currentUser

    override val isEmailVerified: Boolean
        get() = dataSource.currentUser?.isEmailVerified == true

    override suspend fun login(email: String, password: String): Result<FirebaseUser> {
        val result = dataSource.signInWithEmail(email, password)
        return result.map { user ->
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
        val authResult = dataSource.createUserWithEmail(email, password)
        val user = authResult.getOrElse { exception ->
            Log.e(TAG, "Failed to create Firebase Auth user for $email", exception)
            return Result.failure(exception)
        }

        try {
            val profileUpdate = UserProfileChangeRequest.Builder()
                .setDisplayName(fullName.trim())
                .build()
            user.updateProfile(profileUpdate).await()
            Log.d(TAG, "Successfully updated display name for user ${user.uid}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update profile display name for ${user.uid}, continuing anyway", e)
        }

        try {
            user.sendEmailVerification().await()
            Log.d(TAG, "Verification email sent for user ${user.uid}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send verification email for ${user.uid}", e)
        }

        val leaderData = hashMapOf(
            "uid" to user.uid,
            "fullName" to fullName.trim(),
            "email" to email.trim(),
            "createdAt" to Timestamp.now(),
            "role" to "leader",
            "isMainLeader" to false,
            "status" to "pending",
            "unitId" to "default"
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
            }
        }

        return Result.success(Unit)
    }

    override suspend fun sendEmailVerification(): Result<Unit> {
        val user = currentUser ?: return Result.failure(IllegalStateException("لا يوجد مستخدم مسجل حالياً"))
        return try {
            user.sendEmailVerification().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send verification email for ${user.uid}", e)
            Result.failure(e)
        }
    }

    override suspend fun reloadUser(): Result<Boolean> {
        val user = currentUser ?: return Result.failure(IllegalStateException("لا يوجد مستخدم مسجل حالياً"))
        return try {
            user.reload().await()
            Result.success(currentUser?.isEmailVerified == true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reload Firebase user ${user.uid}", e)
            Result.failure(e)
        }
    }

    /**
     * دالة مساعدة للتأكد من وجود وثيقة القائد في Firestore واستكمالها تلقائياً إن كانت مفقودة.
     * يتم إنشاء أي حساب جديد بصلاحيات قائد عادي وبحالة انتظار الاعتماد من الخادم فقط.
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
                    "role" to "leader",
                    "isMainLeader" to false,
                    "status" to "pending",
                    "unitId" to "default"
                )
                docRef.set(leaderData).await()
                Log.d(TAG, "Leader document created successfully for ${user.uid}")
            } else {
                val updates = mutableMapOf<String, Any>()
                val storedPhotoUrl = snapshot.safeString("photoUrl")
                if (storedPhotoUrl.isEmpty() && userPhotoUrl.isNotEmpty()) {
                    updates["photoUrl"] = userPhotoUrl
                }
                if (updates.isNotEmpty()) {
                    docRef.update(updates).await()
                    Log.d(TAG, "Updated leader profile photo for ${user.uid}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to ensure leader document for ${user.uid}", e)
        }
    }

    override fun logout() {
        dataSource.signOut()
    }

    private fun mapSnapshotToLeader(doc: DocumentSnapshot): Leader? {
        return try {
            val uid = doc.safeNullableString("uid") ?: doc.id
            val fullName = doc.safeNullableString("fullName")
                ?: doc.safeNullableString("displayName")
                ?: doc.safeNullableString("name")
                ?: "قائد"
            val email = doc.safeString("email")
            val role = doc.safeString("role", "leader")
            val isMainLeader = doc.safeBoolean("isMainLeader", false)
            val status = doc.safeString("status", "pending")
            val unitId = doc.safeString("unitId", "default")
            Leader(
                uid = uid,
                fullName = fullName.trim(),
                email = email.trim(),
                role = role,
                isMainLeader = isMainLeader,
                status = status,
                unitId = unitId,
                isSuperAdmin = false
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to map leader document ${doc.id}", e)
            null
        }
    }

    override fun observeAllLeaders(): Flow<List<Leader>> = callbackFlow {
        val fallbackState = ListenerFallbackState<Leader>("leaders", errorReporter)
        val listener = firestore.collection("leaders")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing all leaders: ${error.message}", error)
                    trySend(fallbackState.onError(error))
                    return@addSnapshotListener
                }
                val leaders = snapshot?.documents?.mapNotNull { doc ->
                    mapSnapshotToLeader(doc)
                }.orEmpty()
                trySend(fallbackState.onSnapshot(leaders))
            }
        awaitClose { listener.remove() }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun observeCurrentLeader(): Flow<Leader?> {
        return dataSource.observeAuthState().flatMapLatest { user ->
            if (user == null) {
                flowOf(null)
            } else {
                callbackFlow {
                    val listener = firestore.collection("leaders").document(user.uid)
                        .addSnapshotListener { snapshot, error ->
                            if (error != null) {
                                Log.w(TAG, "Error observing current leader ${user.uid}", error)
                                trySend(null)
                                return@addSnapshotListener
                            }
                            if (snapshot != null && snapshot.exists()) {
                                trySend(mapSnapshotToLeader(snapshot))
                            } else {
                                trySend(null)
                            }
                        }
                    awaitClose { listener.remove() }
                }
            }
        }
    }

    override suspend fun getAllLeaders(excludeUid: String): Result<List<Leader>> {
        return try {
            val snapshot = firestore.collection("leaders").get().await()
            val leaders = snapshot.documents.mapNotNull { doc ->
                mapSnapshotToLeader(doc)
            }.filter { it.uid != excludeUid }
            Result.success(leaders)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get all leaders", e)
            Result.failure(e)
        }
    }

    override suspend fun getCurrentLeader(): Leader? {
        val user = currentUser ?: return null
        return try {
            val doc = firestore.collection("leaders").document(user.uid).get().await()
            if (doc.exists()) {
                mapSnapshotToLeader(doc)
            } else {
                Leader(
                    uid = user.uid,
                    fullName = user.displayName ?: "قائد",
                    email = user.email ?: "",
                    role = "leader",
                    isMainLeader = false,
                    status = "pending",
                    unitId = "default"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get current leader profile for ${user.uid}", e)
            null
        }
    }

    override suspend fun updateLeaderStatus(uid: String, status: String): Result<Unit> {
        return try {
            firestore.collection("leaders")
                .document(uid)
                .update("status", status)
                .await()
            Log.d(TAG, "Updated leader $uid status to $status")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update leader status for $uid", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteLeader(uid: String): Result<Unit> {
        return try {
            firestore.collection("leaders")
                .document(uid)
                .delete()
                .await()
            Log.d(TAG, "Deleted leader document $uid")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete leader $uid", e)
            Result.failure(e)
        }
    }

    override suspend fun toggleMainLeader(uid: String, isMainLeader: Boolean): Result<Unit> {
        return try {
            firestore.collection("leaders")
                .document(uid)
                .update("isMainLeader", isMainLeader)
                .await()
            Log.d(TAG, "Toggled main leader for $uid to $isMainLeader")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle main leader for $uid", e)
            Result.failure(e)
        }
    }

    override suspend fun updateMainLeader(newLeaderId: String): Result<Unit> {
        return toggleMainLeader(newLeaderId, true)
    }

    override suspend fun deleteAccount(user: FirebaseUser): Result<Unit> {
        val uid = user.uid
        return try {
            val docsToDelete = mutableListOf<DocumentReference>()
            docsToDelete.add(firestore.collection("leaders").document(uid))

            val evals = firestore.collection("evaluations")
                .whereEqualTo("leaderId", uid)
                .get()
                .await()
            docsToDelete.addAll(evals.documents.map { it.reference })

            val logs = firestore.collection("activity_log")
                .whereEqualTo("userId", uid)
                .get()
                .await()
            docsToDelete.addAll(logs.documents.map { it.reference })

            docsToDelete.chunked(400).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { docRef -> batch.delete(docRef) }
                batch.commit().await()
            }

            user.delete().await()
            Result.success(Unit)
        } catch (e: FirebaseAuthRecentLoginRequiredException) {
            Log.w(TAG, "Recent login required to delete account for $uid", e)
            Result.failure(Exception("يرجى تسجيل الخروج والدخول مرة أخرى قبل الحذف."))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete account for $uid", e)
            Result.failure(e)
        }
    }
}
