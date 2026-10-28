package com.example.data.quran

import android.util.Log
import com.example.domain.quran.QuranSchool
import com.example.domain.quran.ScoutQuranEnrollment
import com.example.domain.quran.SelectedPeriod
import com.example.domain.quran.StudyPeriod
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class QuranFirestoreDataSource(
    private val firestore: FirebaseFirestore
) {
    companion object {
        private const val TAG = "QuranFirestoreDS"
        const val COLLECTION_SCHOOLS = "quran_schools"
        const val COLLECTION_ENROLLMENTS = "scout_quran_enrollment"
    }

    private val schoolsCollection = firestore.collection(COLLECTION_SCHOOLS)
    private val enrollmentsCollection = firestore.collection(COLLECTION_ENROLLMENTS)

    // ==========================================
    // Quran Schools
    // ==========================================

    fun observeAllSchools(): Flow<List<QuranSchool>> = callbackFlow {
        val registration: ListenerRegistration = schoolsCollection
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Error listening to schools", error)
                    return@addSnapshotListener
                }
                val schools = snapshot?.documents?.mapNotNull { mapDocToSchool(it) } ?: emptyList()
                trySend(schools)
            }
        awaitClose { registration.remove() }
    }

    suspend fun getSchoolById(id: String): QuranSchool? {
        val doc = schoolsCollection.document(id).get().await()
        return if (doc.exists()) mapDocToSchool(doc) else null
    }

    suspend fun saveSchool(school: QuranSchool): String {
        val docRef = if (school.id.isNotBlank()) {
            schoolsCollection.document(school.id)
        } else {
            schoolsCollection.document()
        }
        val generatedId = docRef.id
        val schoolWithId = school.copy(id = generatedId)
        val data = mapSchoolToMap(schoolWithId)
        docRef.set(data).await()
        return generatedId
    }

    suspend fun updateSchool(school: QuranSchool) {
        val data = mapSchoolToMap(school)
        schoolsCollection.document(school.id).set(data).await()
    }

    suspend fun setSchoolArchived(id: String, archived: Boolean, updatedBy: String?): Boolean {
        val doc = schoolsCollection.document(id).get().await()
        if (!doc.exists()) return false
        val updates = mapOf(
            "archived" to archived,
            "updatedAt" to System.currentTimeMillis(),
            "updatedBy" to updatedBy
        )
        schoolsCollection.document(id).update(updates).await()
        return true
    }

    suspend fun deleteSchoolDoc(id: String): Boolean {
        val doc = schoolsCollection.document(id).get().await()
        if (!doc.exists()) return false
        schoolsCollection.document(id).delete().await()
        return true
    }

    suspend fun getEnrollmentsCountForSchool(schoolId: String): Int {
        val snapshot = enrollmentsCollection
            .whereEqualTo("schoolId", schoolId)
            .get()
            .await()
        return snapshot.size()
    }

    suspend fun updateDenormalizedSchoolInEnrollments(
        schoolId: String,
        schoolName: String,
        teacherName: String,
        teacherPhone: String,
        updatedBy: String?
    ): Int {
        val snapshot = enrollmentsCollection
            .whereEqualTo("schoolId", schoolId)
            .get()
            .await()
        if (snapshot.isEmpty) return 0

        val batch = firestore.batch()
        val now = System.currentTimeMillis()
        for (doc in snapshot.documents) {
            batch.update(
                doc.reference,
                mapOf(
                    "schoolName" to schoolName,
                    "teacherName" to teacherName,
                    "teacherPhone" to teacherPhone,
                    "updatedAt" to now,
                    "updatedBy" to updatedBy
                )
            )
        }
        batch.commit().await()
        return snapshot.size()
    }

    // ==========================================
    // Scout Enrollments
    // ==========================================

    fun observeEnrollmentForScout(scoutId: String): Flow<ScoutQuranEnrollment?> = callbackFlow {
        val registration = enrollmentsCollection.document(scoutId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Error listening to enrollment for scout $scoutId", error)
                    return@addSnapshotListener
                }
                val enrollment = if (snapshot != null && snapshot.exists()) {
                    mapDocToEnrollment(snapshot)
                } else null
                trySend(enrollment)
            }
        awaitClose { registration.remove() }
    }

    fun observeAllEnrollments(): Flow<List<ScoutQuranEnrollment>> = callbackFlow {
        val registration = enrollmentsCollection
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Error listening to all enrollments", error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { mapDocToEnrollment(it) } ?: emptyList()
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    fun observeEnrollmentsForSchool(schoolId: String): Flow<List<ScoutQuranEnrollment>> = callbackFlow {
        val registration = enrollmentsCollection
            .whereEqualTo("schoolId", schoolId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Error listening to enrollments for school $schoolId", error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { mapDocToEnrollment(it) } ?: emptyList()
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    suspend fun getEnrollmentForScout(scoutId: String): ScoutQuranEnrollment? {
        val doc = enrollmentsCollection.document(scoutId).get().await()
        return if (doc.exists()) mapDocToEnrollment(doc) else null
    }

    suspend fun saveEnrollment(enrollment: ScoutQuranEnrollment) {
        val data = mapEnrollmentToMap(enrollment)
        enrollmentsCollection.document(enrollment.scoutId).set(data).await()
    }

    suspend fun deleteEnrollment(scoutId: String): Boolean {
        val doc = enrollmentsCollection.document(scoutId).get().await()
        if (!doc.exists()) return false
        enrollmentsCollection.document(scoutId).delete().await()
        return true
    }

    // ==========================================
    // Mappers
    // ==========================================

    private fun mapDocToSchool(doc: DocumentSnapshot): QuranSchool? {
        val id = doc.getString("id") ?: doc.id
        val name = doc.getString("name") ?: return null
        val teacherName = doc.getString("teacherName") ?: ""
        val teacherPhone = doc.getString("teacherPhone") ?: ""
        val address = doc.getString("address")
        val notes = doc.getString("notes")
        val archived = doc.getBoolean("archived") ?: false
        val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
        val updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
        val createdBy = doc.getString("createdBy")
        val updatedBy = doc.getString("updatedBy")

        return QuranSchool(
            id = id,
            name = name,
            teacherName = teacherName,
            teacherPhone = teacherPhone,
            address = address,
            notes = notes,
            archived = archived,
            createdAt = createdAt,
            updatedAt = updatedAt,
            createdBy = createdBy,
            updatedBy = updatedBy
        )
    }

    private fun mapSchoolToMap(school: QuranSchool): Map<String, Any?> {
        return hashMapOf(
            "id" to school.id,
            "name" to school.name,
            "teacherName" to school.teacherName,
            "teacherPhone" to school.teacherPhone,
            "address" to school.address,
            "notes" to school.notes,
            "archived" to school.archived,
            "createdAt" to school.createdAt,
            "updatedAt" to school.updatedAt,
            "createdBy" to school.createdBy,
            "updatedBy" to school.updatedBy
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapDocToEnrollment(doc: DocumentSnapshot): ScoutQuranEnrollment? {
        val scoutId = doc.getString("scoutId") ?: doc.id
        val schoolId = doc.getString("schoolId") ?: return null
        val schoolName = doc.getString("schoolName") ?: ""
        val teacherName = doc.getString("teacherName") ?: ""
        val teacherPhone = doc.getString("teacherPhone") ?: ""
        val currentMemorizedParts = (doc.getLong("currentMemorizedParts") ?: 0L).toInt()
        val memorizationNote = doc.getString("memorizationNote")
        val studyDays = (doc.get("studyDays") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

        val rawPeriods = (doc.get("studyPeriods") as? List<*>)?.mapNotNull { it as? Map<String, Any?> } ?: emptyList()
        val studyPeriods = rawPeriods.mapNotNull { map ->
            val key = map["key"] as? String ?: return@mapNotNull null
            val period = StudyPeriod.fromKey(key) ?: return@mapNotNull null
            val startTime = map["startTime"] as? String
            SelectedPeriod(period = period, startTime = startTime)
        }

        val generalNotes = doc.getString("generalNotes")
        val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
        val updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
        val createdBy = doc.getString("createdBy")
        val updatedBy = doc.getString("updatedBy")

        return ScoutQuranEnrollment(
            scoutId = scoutId,
            schoolId = schoolId,
            schoolName = schoolName,
            teacherName = teacherName,
            teacherPhone = teacherPhone,
            currentMemorizedParts = currentMemorizedParts,
            memorizationNote = memorizationNote,
            studyDays = studyDays,
            studyPeriods = studyPeriods,
            generalNotes = generalNotes,
            createdAt = createdAt,
            updatedAt = updatedAt,
            createdBy = createdBy,
            updatedBy = updatedBy
        )
    }

    private fun mapEnrollmentToMap(enrollment: ScoutQuranEnrollment): Map<String, Any?> {
        val mappedPeriods = enrollment.studyPeriods.map { sp ->
            val periodMap = hashMapOf<String, Any?>(
                "key" to sp.period.key,
                "label" to sp.period.name
            )
            if (sp.startTime != null) {
                periodMap["startTime"] = sp.startTime
            }
            periodMap
        }

        return hashMapOf(
            "scoutId" to enrollment.scoutId,
            "schoolId" to enrollment.schoolId,
            "schoolName" to enrollment.schoolName,
            "teacherName" to enrollment.teacherName,
            "teacherPhone" to enrollment.teacherPhone,
            "currentMemorizedParts" to enrollment.currentMemorizedParts,
            "memorizationNote" to enrollment.memorizationNote,
            "studyDays" to enrollment.studyDays,
            "studyPeriods" to mappedPeriods,
            "generalNotes" to enrollment.generalNotes,
            "createdAt" to enrollment.createdAt,
            "updatedAt" to enrollment.updatedAt,
            "createdBy" to enrollment.createdBy,
            "updatedBy" to enrollment.updatedBy
        )
    }
}
