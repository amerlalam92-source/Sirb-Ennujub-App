package com.example.data.evaluation

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
     * الاستماع لجميع معايير التقييم مع بذر المعايير الافتراضية إذا كانت المجموعة فارغة
     */
    fun observeCriteria(): Flow<List<EvaluationCriteria>> = callbackFlow {
        val listenerRegistration = criteriaCollection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }

            if (snapshot == null || snapshot.isEmpty) {
                // بذر المعايير الافتراضية تلقائياً
                seedDefaultCriteria()
                return@addSnapshotListener
            }

            // هجرة سريعة لتحديث "تقييم الحفظ" إلى "تقييم حفظ القرآن" في فايرستور
            snapshot.documents.forEach { doc ->
                if (doc.getString("name") == "تقييم الحفظ") {
                    criteriaCollection.document(doc.id).update("name", "تقييم حفظ القرآن")
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
            EvaluationCriteria(name = "الانضباط", maxScore = 10, type = EvaluationCriteria.TYPE_LEADER_CRITERIA, order = 1),
            EvaluationCriteria(name = "الأخلاق", maxScore = 10, type = EvaluationCriteria.TYPE_LEADER_CRITERIA, order = 2),
            EvaluationCriteria(name = "المشاركة", maxScore = 10, type = EvaluationCriteria.TYPE_LEADER_CRITERIA, order = 3),
            EvaluationCriteria(name = "الحيوية", maxScore = 10, type = EvaluationCriteria.TYPE_LEADER_CRITERIA, order = 4),
            EvaluationCriteria(name = "تقييم حفظ القرآن", maxScore = 20, type = EvaluationCriteria.TYPE_QURAN, order = 5),
            EvaluationCriteria(name = "تقييم الكراس", maxScore = 20, type = EvaluationCriteria.TYPE_NOTEBOOK, order = 6)
        )

        val batch = firestore.batch()
        defaultList.forEach { item ->
            val docRef = criteriaCollection.document()
            batch.set(docRef, mapCriteriaToMap(item.copy(id = docRef.id)))
        }
        batch.commit()
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
            val docId = evaluation.id.ifBlank {
                if (evaluation.activityId.startsWith("TERM_")) {
                    "${evaluation.scoutId}_${evaluation.activityId}"
                } else {
                    "${evaluation.scoutId}_${evaluation.activityId}_${evaluation.leaderId}"
                }
            }
            val toSave = evaluation.copy(id = docId)
            evaluationsCollection.document(docId).set(mapEvaluationToMap(toSave)).await()
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

    fun observeAllEvaluations(): Flow<List<ScoutEvaluation>> = callbackFlow {
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
            "createdAt" to evaluation.createdAt
        )
    }
}
