package com.example.data.evaluation

import android.util.Log
import com.example.domain.evaluation.CriteriaType
import com.example.domain.evaluation.EvaluationCriteria
import com.example.domain.evaluation.ScoutEvaluation
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * مصدر بيانات فايربيس لمعايير وتقييمات الأشبال (Evaluation Firestore Data Source)
 */
class EvaluationFirestoreDataSource(
    private val firestore: FirebaseFirestore
) {
    private val criteriaCollection = firestore.collection("evaluation_criteria")
    private val evaluationsCollection = firestore.collection("evaluations")

    /**
     * الاستماع لجميع معايير التقييم مع بذر المعايير الافتراضية فقط عند خلو السحابة (وليس الكاش)
     */
    fun observeCriteria(): Flow<List<EvaluationCriteria>> = callbackFlow {
        val listenerRegistration = criteriaCollection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }

            if (snapshot == null) {
                return@addSnapshotListener
            }

            if (snapshot.isEmpty) {
                if (!snapshot.metadata.isFromCache) {
                    // بذر المعايير الافتراضية تلقائياً فقط بعد التأكد من السحابة
                    seedDefaultCriteria()
                }
                return@addSnapshotListener
            }

            // هجرة سريعة لتحديث المعايير القديمة في فايرستور إلى النظام الفصلي من 20 (عند عدم كون اللقطة من الكاش فقط)
            if (!snapshot.metadata.isFromCache) {
                snapshot.documents.forEach { doc ->
                    val rawName = doc.getString("name")
                    val rawType = doc.getString("type")
                    val rawMaxScore = doc.getLong("maxScore")
                    if (rawName == "تقييم الحفظ") {
                        criteriaCollection.document(doc.id).update("name", "تقييم حفظ القرآن")
                    }
                    if (rawName == "الحيوية") {
                        criteriaCollection.document(doc.id).update("name", "الاهتمام")
                    }
                    val isLeaderType = rawType == null ||
                        rawType == EvaluationCriteria.TYPE_LEADER_CRITERIA ||
                        rawType == CriteriaType.LEADER_CRITERIA.name ||
                        rawType == CriteriaType.LEADER_CRITERIA.value
                    if (isLeaderType && rawMaxScore == 10L) {
                        criteriaCollection.document(doc.id).update("maxScore", 20)
                    }
                }
            }

            val list = snapshot.documents.mapNotNull { doc ->
                mapDocumentToCriteria(doc)
            }.filter { !it.archived }
                .sortedBy { it.order }

            trySend(list)
        }

        awaitClose { listenerRegistration.remove() }
    }

    private fun seedDefaultCriteria() {
        val defaultList = listOf(
            EvaluationCriteria(id = "default_akhlaq", name = "الأخلاق", maxScore = 20, type = EvaluationCriteria.TYPE_LEADER_CRITERIA, order = 1),
            EvaluationCriteria(id = "default_participation", name = "المشاركة", maxScore = 20, type = EvaluationCriteria.TYPE_LEADER_CRITERIA, order = 2),
            EvaluationCriteria(id = "default_interest", name = "الاهتمام", maxScore = 20, type = EvaluationCriteria.TYPE_LEADER_CRITERIA, order = 3),
            EvaluationCriteria(id = "default_discipline", name = "الانضباط", maxScore = 20, type = EvaluationCriteria.TYPE_LEADER_CRITERIA, order = 4),
            EvaluationCriteria(id = "default_quran", name = "تقييم حفظ القرآن", maxScore = 20, type = EvaluationCriteria.TYPE_QURAN, order = 5),
            EvaluationCriteria(id = "default_notebook", name = "تقييم الكراس", maxScore = 20, type = EvaluationCriteria.TYPE_NOTEBOOK, order = 6)
        )

        val batch = firestore.batch()
        defaultList.forEach { item ->
            val docRef = criteriaCollection.document(item.id)
            batch.set(docRef, mapCriteriaToMap(item))
        }
        batch.commit().addOnFailureListener { e ->
            Log.e("EvaluationDataSource", "Failed to seed default criteria: ${e.message}", e)
        }
    }

    suspend fun addCriteria(criteria: EvaluationCriteria): Result<Unit> {
        return try {
            val docRef = if (criteria.id.isNotBlank()) {
                criteriaCollection.document(criteria.id)
            } else {
                criteriaCollection.document()
            }
            val itemToSave = criteria.copy(id = docRef.id)
            docRef.set(mapCriteriaToMap(itemToSave)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateCriteria(criteria: EvaluationCriteria): Result<Unit> {
        return try {
            criteriaCollection.document(criteria.id).set(mapCriteriaToMap(criteria)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun archiveCriteria(id: String): Result<Unit> {
        return try {
            criteriaCollection.document(id).update("archived", true).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteCriteria(id: String): Result<Unit> {
        return try {
            criteriaCollection.document(id).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveEvaluation(evaluation: ScoutEvaluation): Result<Unit> {
        return try {
            val docId = "${evaluation.scoutId}_${evaluation.activityId}_${evaluation.leaderId}"
            val toSave = evaluation.copy(id = docId)

            var quranId: String? = null
            var notebookId: String? = null
            var oldQuranScore: Int? = null
            var oldNotebookScore: Int? = null

            try {
                val criteriaSnapshot = criteriaCollection.get().await()
                val criteriaList = criteriaSnapshot.documents.mapNotNull { mapDocumentToCriteria(it) }
                val quranCriteria = pickQuranCriterion(criteriaList)
                val notebookCriteria = pickNotebookCriterion(criteriaList)
                quranId = quranCriteria?.id
                notebookId = notebookCriteria?.id

                if (quranId != null || notebookId != null) {
                    val existingSnapshot = evaluationsCollection
                        .whereEqualTo("scoutId", evaluation.scoutId)
                        .whereEqualTo("activityId", evaluation.activityId)
                        .get()
                        .await()
                    val existingEvals = existingSnapshot.documents.mapNotNull { mapDocumentToEvaluation(it) }

                    oldQuranScore = if (quranId != null) {
                        existingEvals
                            .filter { it.criteriaScores.containsKey(quranId) }
                            .maxByOrNull { it.createdAt }
                            ?.criteriaScores?.get(quranId)
                    } else null

                    oldNotebookScore = if (notebookId != null) {
                        existingEvals
                            .filter { it.criteriaScores.containsKey(notebookId) }
                            .maxByOrNull { it.createdAt }
                            ?.criteriaScores?.get(notebookId)
                    } else null
                }
            } catch (e: Exception) {
                Log.w("EvaluationDataSource", "Failed to read previous Quran/Notebook scores for audit log: ${e.message}")
            }

            evaluationsCollection.document(docId).set(mapEvaluationToMap(toSave)).await()

            val newQuranScore = if (quranId != null) evaluation.criteriaScores[quranId] else null
            val newNotebookScore = if (notebookId != null) evaluation.criteriaScores[notebookId] else null

            val changes = mutableListOf<String>()
            val meta = hashMapOf<String, Any?>(
                "scoutId" to evaluation.scoutId,
                "activityId" to evaluation.activityId
            )

            if (newQuranScore != null && newQuranScore != oldQuranScore) {
                changes.add("تقييم حفظ القرآن: من ${oldQuranScore?.toString() ?: "غير مسجل"} إلى $newQuranScore")
                meta["oldQuranScore"] = oldQuranScore
                meta["newQuranScore"] = newQuranScore
            }

            if (newNotebookScore != null && newNotebookScore != oldNotebookScore) {
                changes.add("تقييم الكراس: من ${oldNotebookScore?.toString() ?: "غير مسجل"} إلى $newNotebookScore")
                meta["oldNotebookScore"] = oldNotebookScore
                meta["newNotebookScore"] = newNotebookScore
            }

            if (changes.isNotEmpty()) {
                try {
                    val logEntry = hashMapOf<String, Any?>(
                        "timestamp" to System.currentTimeMillis(),
                        "userId" to evaluation.leaderId,
                        "userName" to evaluation.leaderName.ifBlank { "قائد كشفي" },
                        "action" to "update",
                        "targetType" to "evaluation",
                        "targetId" to docId,
                        "targetName" to "تقييم القرآن والكراس (${evaluation.activityId})",
                        "details" to changes.joinToString(" | "),
                        "meta" to meta
                    )
                    if (!evaluation.unitId.isNullOrBlank()) {
                        logEntry["unitId"] = evaluation.unitId
                    }
                    firestore.collection("activity_log").add(logEntry).await()
                } catch (e: Exception) {
                    Log.w("EvaluationDataSource", "Failed to write Quran/Notebook audit log: ${e.message}")
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteAllActivityEvaluations(): Result<Unit> {
        return try {
            val snapshot = evaluationsCollection.get().await()
            val docsToDelete = snapshot.documents.filter { doc ->
                val activityId = doc.getString("activityId") ?: ""
                !activityId.startsWith("TERM_")
            }
            docsToDelete.chunked(400).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { doc -> batch.delete(doc.reference) }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteEvaluation(id: String): Result<Unit> {
        return try {
            evaluationsCollection.document(id).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getEvaluationsForActivity(activityId: String): Flow<List<ScoutEvaluation>> = callbackFlow {
        val listenerRegistration = evaluationsCollection
            .whereEqualTo("activityId", activityId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { mapDocumentToEvaluation(it) } ?: emptyList()
                trySend(list)
            }
        awaitClose { listenerRegistration.remove() }
    }

    fun getEvaluationsForActivityByLeader(activityId: String, leaderId: String): Flow<List<ScoutEvaluation>> = callbackFlow {
        val listenerRegistration = evaluationsCollection
            .whereEqualTo("leaderId", leaderId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents
                    ?.mapNotNull { mapDocumentToEvaluation(it) }
                    ?.filter { it.activityId == activityId }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { listenerRegistration.remove() }
    }

    fun getEvaluationsForScout(scoutId: String): Flow<List<ScoutEvaluation>> = callbackFlow {
        val listenerRegistration = evaluationsCollection
            .whereEqualTo("scoutId", scoutId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { mapDocumentToEvaluation(it) } ?: emptyList()
                trySend(list)
            }
        awaitClose { listenerRegistration.remove() }
    }

    fun observeAllEvaluations(@Suppress("UNUSED_PARAMETER") unitId: String? = null): Flow<List<ScoutEvaluation>> = callbackFlow {
        val listenerRegistration = evaluationsCollection
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { mapDocumentToEvaluation(it) } ?: emptyList()
                trySend(list)
            }
        awaitClose { listenerRegistration.remove() }
    }

    private fun mapDocumentToCriteria(doc: DocumentSnapshot): EvaluationCriteria? {
        return try {
            val rawName = doc.getString("name") ?: ""
            val name = if (rawName == "تقييم الحفظ") "تقييم حفظ القرآن" else rawName
            EvaluationCriteria(
                id = doc.id,
                name = name,
                maxScore = doc.getLong("maxScore")?.toInt() ?: 10,
                type = doc.getString("type") ?: EvaluationCriteria.TYPE_LEADER_CRITERIA,
                order = doc.getLong("order")?.toInt() ?: 0,
                archived = doc.getBoolean("archived") ?: false
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun mapCriteriaToMap(criteria: EvaluationCriteria): Map<String, Any?> {
        return hashMapOf(
            "id" to criteria.id,
            "name" to criteria.name,
            "maxScore" to criteria.maxScore,
            "type" to criteria.type,
            "order" to criteria.order,
            "archived" to criteria.archived
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapDocumentToEvaluation(doc: DocumentSnapshot): ScoutEvaluation? {
        return try {
            val rawScores = doc.get("criteriaScores") as? Map<String, Any> ?: emptyMap()
            val criteriaScores = rawScores.mapNotNull { (k, v) ->
                val num = (v as? Number)?.toInt()
                if (num != null) k to num else null
            }.toMap()

            ScoutEvaluation(
                id = doc.id,
                scoutId = doc.getString("scoutId") ?: "",
                activityId = doc.getString("activityId") ?: "",
                leaderId = doc.getString("leaderId") ?: "",
                leaderName = doc.getString("leaderName") ?: "",
                criteriaScores = criteriaScores,
                penaltyAmount = doc.getLong("penaltyAmount")?.toInt() ?: 0,
                penaltyReason = doc.getString("penaltyReason"),
                testedAmount = doc.getString("testedAmount"),
                unitId = doc.getString("unitId"),
                createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun mapEvaluationToMap(evaluation: ScoutEvaluation): Map<String, Any?> {
        return hashMapOf(
            "id" to evaluation.id,
            "scoutId" to evaluation.scoutId,
            "activityId" to evaluation.activityId,
            "leaderId" to evaluation.leaderId,
            "leaderName" to evaluation.leaderName,
            "criteriaScores" to evaluation.criteriaScores,
            "penaltyAmount" to evaluation.penaltyAmount,
            "penaltyReason" to evaluation.penaltyReason,
            "testedAmount" to evaluation.testedAmount,
            "unitId" to evaluation.unitId,
            "createdAt" to evaluation.createdAt
        )
    }
}

internal fun pickQuranCriterion(criteria: List<EvaluationCriteria>): EvaluationCriteria? {
    val exactTypeMatches = criteria.filter {
        it.type == EvaluationCriteria.TYPE_QURAN ||
            it.type == CriteriaType.QURAN.name ||
            it.type == CriteriaType.QURAN.value
    }
    val candidates = exactTypeMatches.ifEmpty {
        criteria.filter { it.name.contains("قرآن") || it.name.contains("حفظ") }
    }
    return candidates.minWithOrNull(compareBy<EvaluationCriteria>({ it.order }, { it.id }))
}

internal fun pickNotebookCriterion(criteria: List<EvaluationCriteria>): EvaluationCriteria? {
    val exactTypeMatches = criteria.filter {
        it.type == EvaluationCriteria.TYPE_NOTEBOOK ||
            it.type == CriteriaType.NOTEBOOK.name ||
            it.type == CriteriaType.NOTEBOOK.value
    }
    val candidates = exactTypeMatches.ifEmpty {
        criteria.filter { it.name.contains("كراس") }
    }
    return candidates.minWithOrNull(compareBy<EvaluationCriteria>({ it.order }, { it.id }))
}
