package com.example.data.settings

import android.util.Log
import com.example.data.firestore.safeNullableString
import com.example.domain.settings.BrandingSettings
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class BrandingFirestoreDataSource(
    private val firestore: FirebaseFirestore
) {
    companion object {
        const val COLLECTION_SETTINGS = "settings"
        const val DOC_BRANDING = "branding"
        const val FIELD_LOGO_BASE64 = "logoBase64"
    }

    private val brandingDoc
        get() = firestore.collection(COLLECTION_SETTINGS).document(DOC_BRANDING)

    fun observeBranding(): Flow<BrandingSettings> = callbackFlow {
        val listenerRegistration = brandingDoc.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(BrandingSettings(null))
                return@addSnapshotListener
            }
            try {
                if (snapshot != null && snapshot.exists()) {
                    val logo = snapshot.safeNullableString(FIELD_LOGO_BASE64)
                    trySend(BrandingSettings(logoBase64 = logo))
                } else {
                    trySend(BrandingSettings(logoBase64 = null))
                }
            } catch (e: Exception) {
                Log.w("BrandingDS", "Error mapping branding settings", e)
                trySend(BrandingSettings(logoBase64 = null))
            }
        }
        awaitClose { listenerRegistration.remove() }
    }

    suspend fun updateLogo(base64: String): Result<Unit> {
        return try {
            val data = mapOf(FIELD_LOGO_BASE64 to base64)
            brandingDoc.set(data, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
