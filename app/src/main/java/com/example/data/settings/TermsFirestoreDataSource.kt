package com.example.data.settings

import android.util.Log
import com.example.data.firestore.safeString
import com.example.domain.settings.CustomTerms
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class TermsFirestoreDataSource(
    private val firestore: FirebaseFirestore
) {
    companion object {
        const val COLLECTION_SETTINGS = "settings"
        const val DOC_CUSTOM_TERMS = "custom_terms"
    }

    private val termsDoc
        get() = firestore.collection(COLLECTION_SETTINGS).document(DOC_CUSTOM_TERMS)

    fun observeTerms(): Flow<CustomTerms> = callbackFlow {
        val listenerRegistration = termsDoc.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(CustomTerms())
                return@addSnapshotListener
            }
            try {
                if (snapshot != null && snapshot.exists()) {
                    val data = snapshot.data
                    val defaultTerms = CustomTerms()
                    @Suppress("UNCHECKED_CAST")
                    val customWords = (data?.get("customWords") as? Map<*, *>)
                        ?.mapNotNull { (k, v) ->
                            val key = k as? String
                            val value = v as? String
                            if (key != null && value != null) key to value else null
                        }
                        ?.toMap() ?: defaultTerms.customWords

                    val terms = CustomTerms(
                        scoutSingular = snapshot.safeString("scoutSingular", defaultTerms.scoutSingular),
                        scoutSingularDefinite = snapshot.safeString("scoutSingularDefinite", defaultTerms.scoutSingularDefinite),
                        scoutPlural = snapshot.safeString("scoutPlural", defaultTerms.scoutPlural),
                        scoutPluralDefinite = snapshot.safeString("scoutPluralDefinite", defaultTerms.scoutPluralDefinite),
                        groupName = snapshot.safeString("groupName", defaultTerms.groupName),
                        groupNameDefinite = snapshot.safeString("groupNameDefinite", defaultTerms.groupNameDefinite),
                        unitName = snapshot.safeString("unitName", defaultTerms.unitName),
                        unitNameDefinite = snapshot.safeString("unitNameDefinite", defaultTerms.unitNameDefinite),
                        patrolName = snapshot.safeString("patrolName", defaultTerms.patrolName)
                            .ifBlank { defaultTerms.patrolName },
                        customWords = customWords
                    )
                    trySend(terms)
                } else {
                    trySend(CustomTerms())
                }
            } catch (e: Exception) {
                Log.w("TermsDS", "Error mapping custom terms", e)
                trySend(CustomTerms())
            }
        }
        awaitClose { listenerRegistration.remove() }
    }

    suspend fun updateTerms(terms: CustomTerms): Result<Unit> {
        return try {
            val map = mapOf(
                "scoutSingular" to terms.scoutSingular,
                "scoutSingularDefinite" to terms.scoutSingularDefinite,
                "scoutPlural" to terms.scoutPlural,
                "scoutPluralDefinite" to terms.scoutPluralDefinite,
                "groupName" to terms.groupName,
                "groupNameDefinite" to terms.groupNameDefinite,
                "unitName" to terms.unitName,
                "unitNameDefinite" to terms.unitNameDefinite,
                "patrolName" to terms.patrolName,
                "customWords" to terms.customWords
            )
            termsDoc.set(map, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
