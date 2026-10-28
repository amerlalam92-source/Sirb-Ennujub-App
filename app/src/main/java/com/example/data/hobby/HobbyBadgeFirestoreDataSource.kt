package com.example.data.hobby

import android.util.Log
import com.example.domain.hobby.HobbyBadge
import com.example.domain.hobby.HobbyBadgeRequirement
import com.example.domain.hobby.ScoutHobbyBadge
import com.example.domain.hobby.ScoutHobbyProgress
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * مصدر بيانات Firestore لشارات الهواية (Hobby Badges) ومتطلباتها وإنجاز الأشبال
 * يعتمد على الاستعلامات البسيطة فقط دون دمج where مع orderBy لتجنب الحاجة إلى مؤشرات مركبة.
 */
class HobbyBadgeFirestoreDataSource(
    private val firestore: FirebaseFirestore
) {
    companion object {
        private const val TAG = "HobbyBadgeDataSource"
        private const val COLLECTION_BADGES = "hobby_badges"
        private const val COLLECTION_REQUIREMENTS = "hobby_badge_requirements"
        private const val COLLECTION_SCOUT_PROGRESS = "scout_hobby_progress"
        private const val COLLECTION_SCOUT_BADGES = "scout_hobby_badges"

        // للمزامنة التلقائية مع شارات الكفاءة
        private const val COLLECTION_KAFAA_REQUIREMENTS = "progress_requirements"
        private const val COLLECTION_KAFAA_PROGRESS = "scout_progress"
    }

    // =========================================================================
    // 1. HOBBY BADGES CATALOG
    // =========================================================================

    fun observeAllHobbyBadges(): Flow<List<HobbyBadge>> = callbackFlow {
        val listener = firestore.collection(COLLECTION_BADGES)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing all hobby badges: ${error.message}", error)
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(HobbyBadge::class.java)?.copy(id = doc.id)
                }.orEmpty().sortedBy { it.order }
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    fun observeHobbyBadgeById(id: String): Flow<HobbyBadge?> = callbackFlow {
        val listener = firestore.collection(COLLECTION_BADGES).document(id)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing hobby badge by id: ${error.message}", error)
                    close(error)
                    return@addSnapshotListener
                }
                val badge = snapshot?.toObject(HobbyBadge::class.java)?.copy(id = snapshot.id)
                trySend(badge)
            }
        awaitClose { listener.remove() }
    }

    suspend fun getMaxBadgeOrder(): Int {
        val snapshot = firestore.collection(COLLECTION_BADGES).get().await()
        return snapshot.documents.mapNotNull { it.getLong("order")?.toInt() }.maxOrNull() ?: 0
    }

    suspend fun addHobbyBadge(badge: HobbyBadge): String {
        val docRef = firestore.collection(COLLECTION_BADGES).document()
        val toSave = badge.copy(
            id = docRef.id,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        docRef.set(toSave).await()
        return docRef.id
    }

    suspend fun updateHobbyBadge(badge: HobbyBadge) {
        firestore.collection(COLLECTION_BADGES)
            .document(badge.id)
            .update(
                mapOf(
                    "name" to badge.name,
                    "description" to badge.description,
                    "iconKey" to badge.iconKey,
                    "updatedAt" to System.currentTimeMillis(),
                    "updatedBy" to badge.updatedBy
                )
            ).await()
    }

    suspend fun setHobbyBadgeArchived(id: String, archived: Boolean, updatedBy: String) {
        firestore.collection(COLLECTION_BADGES)
            .document(id)
            .update(
                mapOf(
                    "archived" to archived,
                    "updatedAt" to System.currentTimeMillis(),
                    "updatedBy" to updatedBy
                )
            ).await()
    }

    suspend fun moveHobbyBadge(id: String, moveUp: Boolean): Boolean {
        val snapshot = firestore.collection(COLLECTION_BADGES).get().await()
        val allBadges = snapshot.documents
            .mapNotNull { it.toObject(HobbyBadge::class.java)?.copy(id = it.id) }
            .sortedBy { it.order }

        val currentIndex = allBadges.indexOfFirst { it.id == id }
        if (currentIndex < 0) return false

        val swapIndex = if (moveUp) currentIndex - 1 else currentIndex + 1
        if (swapIndex < 0 || swapIndex >= allBadges.size) return false

        val currentBadge = allBadges[currentIndex]
        val otherBadge = allBadges[swapIndex]

        firestore.runTransaction { tx ->
            val ref1 = firestore.collection(COLLECTION_BADGES).document(currentBadge.id)
            val ref2 = firestore.collection(COLLECTION_BADGES).document(otherBadge.id)
            tx.update(ref1, "order", otherBadge.order, "updatedAt", System.currentTimeMillis())
            tx.update(ref2, "order", currentBadge.order, "updatedAt", System.currentTimeMillis())
        }.await()

        return true
    }

    suspend fun deleteHobbyBadgeIfUnused(id: String): Result<Unit> {
        val grantedDocs = firestore.collection(COLLECTION_SCOUT_BADGES)
            .whereEqualTo("badgeId", id)
            .get()
            .await()

        val activeGrants = grantedDocs.documents.any { doc ->
            doc.getLong("revokedAt") == null
        }

        if (activeGrants) {
            return Result.failure(IllegalStateException("GRANTED_TO_SCOUTS"))
        }

        // حذف المتطلبات التابعة للشارة وسجلاتها
        val reqsSnapshot = firestore.collection(COLLECTION_REQUIREMENTS)
            .whereEqualTo("badgeId", id)
            .get()
            .await()

        val batch = firestore.batch()
        batch.delete(firestore.collection(COLLECTION_BADGES).document(id))
        reqsSnapshot.documents.forEach { doc ->
            batch.delete(doc.reference)
        }
        batch.commit().await()

        return Result.success(Unit)
    }

    // =========================================================================
    // 2. HOBBY BADGE REQUIREMENTS
    // =========================================================================

    fun observeRequirementsForBadge(badgeId: String): Flow<List<HobbyBadgeRequirement>> = callbackFlow {
        val listener = firestore.collection(COLLECTION_REQUIREMENTS)
            .whereEqualTo("badgeId", badgeId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing requirements for badge: ${error.message}", error)
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(HobbyBadgeRequirement::class.java)?.copy(id = doc.id)
                }.orEmpty().sortedBy { it.order }
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    fun observeAllHobbyRequirements(): Flow<List<HobbyBadgeRequirement>> = callbackFlow {
        val listener = firestore.collection(COLLECTION_REQUIREMENTS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing all hobby requirements: ${error.message}", error)
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(HobbyBadgeRequirement::class.java)?.copy(id = doc.id)
                }.orEmpty().sortedBy { it.order }
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    suspend fun getMaxRequirementOrderByBadge(badgeId: String): Int {
        val snapshot = firestore.collection(COLLECTION_REQUIREMENTS)
            .whereEqualTo("badgeId", badgeId)
            .get()
            .await()
        return snapshot.documents.mapNotNull { it.getLong("order")?.toInt() }.maxOrNull() ?: 0
    }

    suspend fun addHobbyRequirement(req: HobbyBadgeRequirement): String {
        val docRef = firestore.collection(COLLECTION_REQUIREMENTS).document()
        val toSave = req.copy(
            id = docRef.id,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        docRef.set(toSave).await()
        return docRef.id
    }

    suspend fun updateHobbyRequirement(req: HobbyBadgeRequirement) {
        firestore.collection(COLLECTION_REQUIREMENTS)
            .document(req.id)
            .update(
                mapOf(
                    "title" to req.title,
                    "description" to req.description,
                    "badgeId" to req.badgeId,
                    "updatedAt" to System.currentTimeMillis(),
                    "updatedBy" to req.updatedBy
                )
            ).await()
    }

    suspend fun setHobbyRequirementArchived(id: String, archived: Boolean, updatedBy: String) {
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

    suspend fun moveHobbyRequirement(id: String, moveUp: Boolean): Boolean {
        val targetDoc = firestore.collection(COLLECTION_REQUIREMENTS).document(id).get().await()
        val targetReq = targetDoc.toObject(HobbyBadgeRequirement::class.java)?.copy(id = targetDoc.id) ?: return false

        val snapshot = firestore.collection(COLLECTION_REQUIREMENTS)
            .whereEqualTo("badgeId", targetReq.badgeId)
            .get()
            .await()

        val allInBadge = snapshot.documents
            .mapNotNull { it.toObject(HobbyBadgeRequirement::class.java)?.copy(id = it.id) }
            .sortedBy { it.order }

        val currentIndex = allInBadge.indexOfFirst { it.id == id }
        if (currentIndex < 0) return false

        val swapIndex = if (moveUp) currentIndex - 1 else currentIndex + 1
        if (swapIndex < 0 || swapIndex >= allInBadge.size) return false

        val otherReq = allInBadge[swapIndex]

        firestore.runTransaction { tx ->
            val ref1 = firestore.collection(COLLECTION_REQUIREMENTS).document(targetReq.id)
            val ref2 = firestore.collection(COLLECTION_REQUIREMENTS).document(otherReq.id)
            tx.update(ref1, "order", otherReq.order, "updatedAt", System.currentTimeMillis())
            tx.update(ref2, "order", targetReq.order, "updatedAt", System.currentTimeMillis())
        }.await()

        return true
    }

    suspend fun deleteHobbyRequirementIfUnused(id: String): Result<Unit> {
        val progressDocs = firestore.collection(COLLECTION_SCOUT_PROGRESS)
            .whereEqualTo("requirementId", id)
            .whereEqualTo("completed", true)
            .limit(1)
            .get()
            .await()

        if (!progressDocs.isEmpty) {
            return Result.failure(IllegalStateException("USED_BY_SCOUTS"))
        }

        firestore.collection(COLLECTION_REQUIREMENTS).document(id).delete().await()
        return Result.success(Unit)
    }

    // =========================================================================
    // 3. SCOUT HOBBY PROGRESS
    // =========================================================================

    fun observeHobbyProgressForScout(scoutId: String): Flow<List<ScoutHobbyProgress>> = callbackFlow {
        val listener = firestore.collection(COLLECTION_SCOUT_PROGRESS)
            .whereEqualTo("scoutId", scoutId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing hobby progress for scout: ${error.message}", error)
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { it.toObject(ScoutHobbyProgress::class.java) }.orEmpty()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    suspend fun setHobbyRequirementProgress(
        scoutId: String,
        requirementId: String,
        badgeId: String,
        completed: Boolean,
        completedBy: String?,
        completedByName: String?
    ) {
        val docId = "${scoutId}_$requirementId"
        val now = System.currentTimeMillis()
        val entry = ScoutHobbyProgress(
            scoutId = scoutId,
            requirementId = requirementId,
            badgeId = badgeId,
            completed = completed,
            completedAt = if (completed) now else null,
            completedBy = if (completed) completedBy else null,
            completedByName = if (completed) completedByName else null,
            autoCompletedByHobbyBadge = false,
            updatedAt = now
        )
        firestore.collection(COLLECTION_SCOUT_PROGRESS).document(docId).set(entry).await()
    }

    // =========================================================================
    // 4. SCOUT HOBBY BADGES (GRANTS & REVOCATIONS WITH KAFAA LINK)
    // =========================================================================

    fun observeHobbyBadgesForScout(scoutId: String): Flow<List<ScoutHobbyBadge>> = callbackFlow {
        val listener = firestore.collection(COLLECTION_SCOUT_BADGES)
            .whereEqualTo("scoutId", scoutId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing hobby badges for scout: ${error.message}", error)
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { it.toObject(ScoutHobbyBadge::class.java) }.orEmpty()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    fun observeAllHobbyBadgesGranted(): Flow<List<ScoutHobbyBadge>> = callbackFlow {
        val listener = firestore.collection(COLLECTION_SCOUT_BADGES)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing all granted hobby badges: ${error.message}", error)
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { it.toObject(ScoutHobbyBadge::class.java) }.orEmpty()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    suspend fun grantHobbyBadgeWithLink(
        scoutId: String,
        badgeId: String,
        notes: String?,
        grantedBy: String,
        grantedByName: String
    ) {
        val now = System.currentTimeMillis()
        val grantDocId = "${scoutId}_$badgeId"
        val badgeGrant = ScoutHobbyBadge(
            scoutId = scoutId,
            badgeId = badgeId,
            grantedAt = now,
            grantedBy = grantedBy,
            grantedByName = grantedByName,
            notes = notes,
            revokedAt = null,
            revokedBy = null,
            revokedByName = null
        )

        // 1. تسجيل منح شارة الهواية
        firestore.collection(COLLECTION_SCOUT_BADGES).document(grantDocId).set(badgeGrant).await()

        // 2. حساب عدد شارات الهواية النشطة للشبل
        val badgesSnapshot = firestore.collection(COLLECTION_SCOUT_BADGES)
            .whereEqualTo("scoutId", scoutId)
            .get()
            .await()
        val activeCount = badgesSnapshot.documents
            .mapNotNull { it.toObject(ScoutHobbyBadge::class.java) }
            .count { it.isActive() }

        // 3. جلب كافة متطلبات الكفاءة المرتبطة بعدد شارات الهواية
        val allReqsSnapshot = firestore.collection(COLLECTION_KAFAA_REQUIREMENTS)
            .get()
            .await()
        val linkedReqs = allReqsSnapshot.documents
            .mapNotNull { it.toObject(com.example.domain.progress.ProgressRequirement::class.java)?.copy(id = it.id) }
            .filter { it.linkedHobbyBadgesCount != null && (it.linkedHobbyBadgesCount ?: 0) > 0 }

        if (linkedReqs.isEmpty()) return

        val batch = firestore.batch()
        var batchHasOperations = false

        // 4. فحص كل متطلب مؤهل وتحديثه تلقائياً
        for (req in linkedReqs) {
            val reqCount = req.linkedHobbyBadgesCount ?: 0
            if (activeCount >= reqCount) {
                val scoutProgressRef = firestore.collection(COLLECTION_KAFAA_PROGRESS)
                    .document("${scoutId}_${req.id}")
                val currentDoc = scoutProgressRef.get().await()

                if (currentDoc.exists()) {
                    val isCompleted = currentDoc.getBoolean("completed") ?: false
                    val isAuto = currentDoc.getBoolean("autoCompletedByHobbyBadgesCount") ?: false
                    val sourceCount = currentDoc.getLong("autoSourceHobbyBadgesCount")?.toInt()

                    // إذا تم إنجازه يدوياً من قبل القائد -> لا نلمسه
                    if (isCompleted && !isAuto) {
                        continue
                    }
                    // إذا كان منجزاً تلقائياً بنفس العدد مسبقاً -> تخطي
                    if (isAuto && isCompleted && sourceCount == reqCount) {
                        continue
                    }
                }

                val autoProgressData = mapOf(
                    "scoutId" to scoutId,
                    "requirementId" to req.id,
                    "completed" to true,
                    "autoCompletedByHobbyBadgesCount" to true,
                    "autoSourceHobbyBadgesCount" to reqCount,
                    "completedAt" to now,
                    "completedBy" to grantedBy,
                    "completedByName" to grantedByName,
                    "updatedAt" to now
                )
                batch.set(scoutProgressRef, autoProgressData)
                batchHasOperations = true
            }
        }

        if (batchHasOperations) {
            batch.commit().await()
        }
    }

    suspend fun revokeHobbyBadgeWithLink(
        scoutId: String,
        badgeId: String,
        revokedBy: String,
        revokedByName: String
    ) {
        val now = System.currentTimeMillis()
        val grantDocId = "${scoutId}_$badgeId"
        val updates = mapOf(
            "revokedAt" to now,
            "revokedBy" to revokedBy,
            "revokedByName" to revokedByName
        )

        // 1. تسجيل سحب شارة الهواية
        firestore.collection(COLLECTION_SCOUT_BADGES).document(grantDocId).update(updates).await()

        // 2. إعادة حساب عدد شارات الهواية النشطة للشبل
        val badgesSnapshot = firestore.collection(COLLECTION_SCOUT_BADGES)
            .whereEqualTo("scoutId", scoutId)
            .get()
            .await()
        val activeCount = badgesSnapshot.documents
            .mapNotNull { it.toObject(ScoutHobbyBadge::class.java) }
            .count { it.isActive() }

        // 3. جلب كافة متطلبات الكفاءة المرتبطة بعدد شارات الهواية
        val allReqsSnapshot = firestore.collection(COLLECTION_KAFAA_REQUIREMENTS)
            .get()
            .await()
        val linkedReqs = allReqsSnapshot.documents
            .mapNotNull { it.toObject(com.example.domain.progress.ProgressRequirement::class.java)?.copy(id = it.id) }
            .filter { it.linkedHobbyBadgesCount != null && (it.linkedHobbyBadgesCount ?: 0) > 0 }

        if (linkedReqs.isEmpty()) return

        val batch = firestore.batch()
        var batchHasOperations = false

        // 4. إلغاء إنجاز المتطلبات التلقائية إذا انخفض عدد الشارات عن المطلوب
        for (req in linkedReqs) {
            val reqCount = req.linkedHobbyBadgesCount ?: 0
            val scoutProgressRef = firestore.collection(COLLECTION_KAFAA_PROGRESS)
                .document("${scoutId}_${req.id}")
            val currentDoc = scoutProgressRef.get().await()

            if (currentDoc.exists()) {
                val isAuto = currentDoc.getBoolean("autoCompletedByHobbyBadgesCount") ?: false
                if (isAuto) {
                    if (activeCount < reqCount) {
                        batch.update(
                            scoutProgressRef,
                            mapOf(
                                "completed" to false,
                                "autoCompletedByHobbyBadgesCount" to false,
                                "autoSourceHobbyBadgesCount" to null,
                                "completedAt" to null,
                                "updatedAt" to now
                            )
                        )
                        batchHasOperations = true
                    }
                }
            }
        }

        if (batchHasOperations) {
            batch.commit().await()
        }
    }
}
