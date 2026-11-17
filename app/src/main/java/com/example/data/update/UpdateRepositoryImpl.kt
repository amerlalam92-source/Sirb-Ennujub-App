package com.example.data.update

import android.util.Log
import com.example.data.firestore.safeInt
import com.example.data.firestore.safeString
import com.example.domain.update.AppUpdateInfo
import com.example.domain.update.UpdateRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * تنفيذ مستودع تحديثات التطبيق عبر مجموعة settings ومستند app_update في Firestore
 */
class UpdateRepositoryImpl(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : UpdateRepository {

    private val updateDocRef = firestore.collection("settings").document("app_update")

    override fun observeUpdateInfo(): Flow<AppUpdateInfo?> = callbackFlow {
        val listener = updateDocRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w("UpdateRepository", "Error observing settings/app_update", error)
                trySend(null)
                return@addSnapshotListener
            }

            try {
                if (snapshot != null && snapshot.exists()) {
                    val remoteCode = snapshot.safeInt("latestVersionCode", 0)
                        .let { if (it > 0) it else snapshot.safeInt("versionCode", 0) }
                    val info = AppUpdateInfo(
                        latestVersionCode = remoteCode,
                        latestVersionName = snapshot.safeString("latestVersionName"),
                        apkUrl = snapshot.safeString("apkUrl"),
                        changelog = snapshot.safeString("changelog"),
                        sha256 = snapshot.safeString("sha256")
                    )
                    trySend(info)
                } else {
                    trySend(null)
                }
            } catch (e: Exception) {
                Log.w("UpdateRepository", "Error mapping settings/app_update", e)
                trySend(null)
            }
        }

        awaitClose { listener.remove() }
    }

    override suspend fun getUpdateInfo(): AppUpdateInfo? {
        return try {
            val snapshot = updateDocRef.get().await()
            if (snapshot.exists()) {
                val remoteCode = snapshot.safeInt("latestVersionCode", 0)
                    .let { if (it > 0) it else snapshot.safeInt("versionCode", 0) }
                AppUpdateInfo(
                    latestVersionCode = remoteCode,
                    latestVersionName = snapshot.safeString("latestVersionName"),
                    apkUrl = snapshot.safeString("apkUrl"),
                    changelog = snapshot.safeString("changelog"),
                    sha256 = snapshot.safeString("sha256")
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("UpdateRepository", "Failed to fetch app_update document", e)
            null
        }
    }
}
