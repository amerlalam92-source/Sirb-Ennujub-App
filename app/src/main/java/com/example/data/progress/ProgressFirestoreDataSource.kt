package com.example.data.progress

import android.util.Log
import com.example.domain.progress.BadgeLevel
import com.example.domain.progress.ProgressRequirement
import com.example.domain.progress.ScoutBadge
import com.example.domain.progress.ScoutProgressEntry
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * مصدر بيانات Firestore لبطاقة التقدم وشارات الكفاءة مع الدعم الكامل للعمل دون اتصال (Offline-first)
 * يعتمد على الاستعلامات البسيطة فقط دون دمج where مع orderBy لتجنب الحاجة إلى أي مؤشرات مركبة (Composite Indexes).
 * يتم الفرز والترشيح في الذاكرة (In-memory) عبر Kotlin.
 */
class ProgressFirestoreDataSource(
    private val firestore: FirebaseFirestore
) {
    companion object {
        private const val TAG = "ProgressDataSource"
        private const val COLLECTION_REQUIREMENTS = "progress_requirements"
        private const val COLLECTION_SCOUT_PROGRESS = "scout_progress"
        private const val COLLECTION_SCOUT_BADGES = "scout_badges"
    }

    // -------------------------------------------------------------
    // REQUIREMENTS
    // -------------------------------------------------------------

    fun observeAllRequirements(): Flow<List<ProgressRequirement>> = callbackFlow {
        val listener = firestore.collection(COLLECTION_REQUIREMENTS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing all requirements: ${error.message}", error)
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(ProgressRequirement::class.java)?.copy(id = doc.id)
                }.orEmpty().sortedBy { it.order }
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    fun observeRequirementsByLevel(level: BadgeLevel): Flow<List<ProgressRequirement>> = callbackFlow {
        val listener = firestore.collection(COLLECTION_REQUIREMENTS)
            .whereEqualTo("badgeLevel", level.key)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing requirements by level: ${error.message}", error)
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(ProgressRequirement::class.java)?.copy(id = doc.id)
                }.orEmpty().sortedBy { it.order }
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    suspend fun addRequirement(req: ProgressRequirement): String {
        val docRef = firestore.collection(COLLECTION_REQUIREMENTS).document()
        val toSave = req.copy(
            id = docRef.id,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        docRef.set(toSave).await()
        return docRef.id
    }

    suspend fun updateRequirement(req: ProgressRequirement) {
        firestore.collection(COLLECTION_REQUIREMENTS)
            .document(req.id)
            .update(
                mapOf(
                    "title" to req.title,
                    "description" to req.description,
                    "badgeLevel" to req.badgeLevel,
                    "linkedHobbyBadgesCount" to req.linkedHobbyBadgesCount,
                    "updatedAt" to System.currentTimeMillis(),
                    "updatedBy" to req.updatedBy
                )
            ).await()
    }

    suspend fun setArchived(id: String, archived: Boolean, updatedBy: String) {
        firestore.collection(COLLECTION_REQUIREMENTS)
            .document(id)
            .update(
                mapOf(
                    "archived" to archived,
                    "updatedAt" to System.currentTimeMillis(),
                    "updatedBy" to updatedBy
                )
            ).await()
    }

    suspend fun moveRequirement(id: String, moveUp: Boolean): Boolean {
        val targetDoc = firestore.collection(COLLECTION_REQUIREMENTS).document(id).get().await()
        val targetReq = targetDoc.toObject(ProgressRequirement::class.java)?.copy(id = targetDoc.id) ?: return false

        // استعلام بسيط بدون orderBy لتفادي المؤشرات المركبة
        val allInLevel = firestore.collection(COLLECTION_REQUIREMENTS)
            .whereEqualTo("badgeLevel", targetReq.badgeLevel)
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(ProgressRequirement::class.java)?.copy(id = it.id) }
            .sortedBy { it.order }

        val currentIndex = allInLevel.indexOfFirst { it.id == id }
        if (currentIndex < 0) return false

        val swapIndex = if (moveUp) currentIndex - 1 else currentIndex + 1
        if (swapIndex < 0 || swapIndex >= allInLevel.size) return false

        val otherReq = allInLevel[swapIndex]

        firestore.runTransaction { tx ->
            val ref1 = firestore.collection(COLLECTION_REQUIREMENTS).document(targetReq.id)
            val ref2 = firestore.collection(COLLECTION_REQUIREMENTS).document(otherReq.id)
            tx.update(ref1, "order", otherReq.order, "updatedAt", System.currentTimeMillis())
            tx.update(ref2, "order", targetReq.order, "updatedAt", System.currentTimeMillis())
        }.await()

        return true
    }

    suspend fun deleteRequirementIfUnused(id: String): Result<Unit> {
        val progressDocs = firestore.collection(COLLECTION_SCOUT_PROGRESS)
            .whereEqualTo("requirementId", id)
            .limit(1)
            .get()
            .await()

        if (!progressDocs.isEmpty) {
            return Result.failure(IllegalStateException("USED_BY_SCOUTS"))
        }

        firestore.collection(COLLECTION_REQUIREMENTS).document(id).delete().await()
        return Result.success(Unit)
    }

    suspend fun getMaxOrderByLevel(badgeLevel: String): Int {
        // استعلام بسيط بدون orderBy لتفادي المؤشرات المركبة، والبحث عن الحد الأقصى في الذاكرة
        val snapshot = firestore.collection(COLLECTION_REQUIREMENTS)
            .whereEqualTo("badgeLevel", badgeLevel)
            .get()
            .await()

        return snapshot.documents.mapNotNull { it.getLong("order")?.toInt() }.maxOrNull() ?: 0
    }

    // -------------------------------------------------------------
    // SCOUT PROGRESS
    // -------------------------------------------------------------

    fun observeScoutProgress(scoutId: String): Flow<List<ScoutProgressEntry>> = callbackFlow {
        val listener = firestore.collection(COLLECTION_SCOUT_PROGRESS)
            .whereEqualTo("scoutId", scoutId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing scout progress: ${error.message}", error)
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { it.toObject(ScoutProgressEntry::class.java) }.orEmpty()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    suspend fun setRequirementProgress(
        scoutId: String,
        requirementId: String,
        completed: Boolean,
        completedBy: String?,
        completedByName: String?
    ) {
        val docId = "${scoutId}_$requirementId"
        val now = System.currentTimeMillis()
        val entry = ScoutProgressEntry(
            scoutId = scoutId,
            requirementId = requirementId,
            completed = completed,
            completedAt = if (completed) now else null,
            completedBy = if (completed) completedBy else null,
            completedByName = if (completed) completedByName else null,
            updatedAt = now
        )
        firestore.collection(COLLECTION_SCOUT_PROGRESS).document(docId).set(entry).await()
    }

    // -------------------------------------------------------------
    // SCOUT BADGES
    // -------------------------------------------------------------

    fun observeScoutBadges(scoutId: String): Flow<List<ScoutBadge>> = callbackFlow {
        val listener = firestore.collection(COLLECTION_SCOUT_BADGES)
            .whereEqualTo("scoutId", scoutId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing scout badges: ${error.message}", error)
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { it.toObject(ScoutBadge::class.java) }.orEmpty()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    suspend fun grantBadge(
        scoutId: String,
        level: BadgeLevel,
        notes: String,
        grantedBy: String,
        grantedByName: String
    ) {
        val docId = "${scoutId}_${level.key}"
        val badge = ScoutBadge(
            scoutId = scoutId,
            badgeLevel = level.key,
            grantedAt = System.currentTimeMillis(),
            grantedBy = grantedBy,
            grantedByName = grantedByName,
            notes = notes,
            revokedAt = null,
            revokedBy = null,
            revokedByName = null
        )
        firestore.collection(COLLECTION_SCOUT_BADGES).document(docId).set(badge).await()
    }

    suspend fun revokeBadge(
        scoutId: String,
        level: BadgeLevel,
        revokedBy: String,
        revokedByName: String
    ) {
        val docId = "${scoutId}_${level.key}"
        val updates = mapOf(
            "revokedAt" to System.currentTimeMillis(),
            "revokedBy" to revokedBy,
            "revokedByName" to revokedByName
        )
        firestore.collection(COLLECTION_SCOUT_BADGES).document(docId).update(updates).await()
    }
}
