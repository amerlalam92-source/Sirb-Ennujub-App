package com.example.data.leader

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap

/**
 * فئة مساعدة لحل معرّفات القادة (UID) إلى أسمائهم الكاملة مع التخزين المؤقت في الذاكرة (Memory Cache).
 */
class LeaderNameResolver(
    private val firestore: FirebaseFirestore
) {
    private val cache = ConcurrentHashMap<String, String>()

    /**
     * استرجاع الاسم الكامل للقائد بناءً على UID الخاص به.
     * يقرأ من وثيقة "leaders/{uid}" في Firestore، وإذا لم تكن متوفرة يُرجع null.
     */
    suspend fun getLeaderDisplayName(uid: String): String? {
        if (uid.isBlank()) return null

        cache[uid]?.let { return it }

        return try {
            val snapshot = firestore.collection("leaders")
                .document(uid)
                .get()
                .await()

            val fullName = snapshot.getString("fullName")
                ?.takeIf { it.isNotBlank() }

            if (fullName != null) {
                cache[uid] = fullName
            }
            fullName
        } catch (e: Exception) {
            Log.w("LeaderNameResolver", "Failed to resolve leader name for uid=$uid", e)
            null
        }
    }
}
