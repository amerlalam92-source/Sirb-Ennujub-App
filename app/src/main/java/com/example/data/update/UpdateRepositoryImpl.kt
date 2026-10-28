package com.example.data.update

import android.util.Log
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

            if (snapshot != null && snapshot.exists()) {
                val info = AppUpdateInfo(
                    latestVersionCode = (snapshot.getLong("latestVersionCode") ?: 0L).toInt(),
                    latestVersionName = snapshot.getString("latestVersionName") ?: "",
                    apkUrl = snapshot.getString("apkUrl") ?: "",
                    changelog = snapshot.getString("changelog") ?: ""
                )
                trySend(info)
            } else {
                trySend(null)
            }
        }

        awaitClose { listener.remove() }
    }

    override suspend fun getUpdateInfo(): AppUpdateInfo? {
        return try {
            val snapshot = updateDocRef.get().await()
            if (snapshot.exists()) {
                AppUpdateInfo(
                    latestVersionCode = (snapshot.getLong("latestVersionCode") ?: 0L).toInt(),
                    latestVersionName = snapshot.getString("latestVersionName") ?: "",
                    apkUrl = snapshot.getString("apkUrl") ?: "",
                    changelog = snapshot.getString("changelog") ?: ""
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
