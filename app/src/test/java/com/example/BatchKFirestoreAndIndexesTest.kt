package com.example

import com.example.data.activity.ActivityFirestoreDataSource
import com.example.data.activity.ActivityRepositoryImpl
import com.example.data.attendance.AttendanceFirestoreDataSource
import com.example.data.log.ActivityLogDataSource
import com.example.domain.activity.Activity
import com.example.domain.activity.ActivityStatus
import com.example.domain.activity.ActivityType
import com.example.domain.auth.AuthRepository
import com.example.domain.finance.FinanceRepository
import com.example.domain.scout.ScoutRepository
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.WriteBatch
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class BatchKFirestoreAndIndexesTest {

    private fun resolveIndexesFile(): File {
        val candidates = listOf(
            File("firestore.indexes.json"),
            File("../firestore.indexes.json")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Could not locate firestore.indexes.json from ${File(".").absolutePath}")
    }

    private fun mockDocSnapshot(
        id: String,
        fields: Map<String, Any?>,
        reference: DocumentReference = mock(DocumentReference::class.java),
        exists: Boolean = true
    ): DocumentSnapshot {
        val doc = mock(DocumentSnapshot::class.java)
        `when`(doc.id).thenReturn(id)
        `when`(doc.exists()).thenReturn(exists)
        `when`(doc.reference).thenReturn(reference)
        `when`(doc.get(anyString())).thenAnswer { invocation ->
            fields[invocation.getArgument<String>(0)]
        }
        return doc
    }

    private class SimpleJsonParser(private val text: String) {
        private var pos = 0

        fun parse(): Any? {
            skipWhitespace()
            val value = parseValue()
            skipWhitespace()
            require(pos == text.length) { "Unexpected trailing JSON character at index $pos" }
            return value
        }

        private fun parseValue(): Any? {
            skipWhitespace()
            require(pos < text.length) { "Unexpected end of JSON input" }
            return when (val ch = text[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> parseLiteral("true", true)
                'f' -> parseLiteral("false", false)
                'n' -> parseLiteral("null", null)
                '-', in '0'..'9' -> parseNumber()
                else -> error("Unexpected JSON token '$ch' at index $pos")
            }
        }

        private fun parseObject(): Map<String, Any?> {
            pos++ // '{'
            val map = linkedMapOf<String, Any?>()
            skipWhitespace()
            if (pos < text.length && text[pos] == '}') {
                pos++
                return map
            }
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                require(pos < text.length && text[pos] == ':') { "Expected ':' at index $pos" }
                pos++
                map[key] = parseValue()
                skipWhitespace()
                require(pos < text.length) { "Unterminated JSON object" }
                when (text[pos]) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return map
                    }
                    else -> error("Expected ',' or '}' at index $pos")
                }
            }
        }

        private fun parseArray(): List<Any?> {
            pos++ // '['
            val list = mutableListOf<Any?>()
            skipWhitespace()
            if (pos < text.length && text[pos] == ']') {
                pos++
                return list
            }
            while (true) {
                list.add(parseValue())
                skipWhitespace()
                require(pos < text.length) { "Unterminated JSON array" }
                when (text[pos]) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return list
                    }
                    else -> error("Expected ',' or ']' at index $pos")
                }
            }
        }

        private fun parseString(): String {
            require(pos < text.length && text[pos] == '"') { "Expected '\"' at index $pos" }
            pos++
            val sb = StringBuilder()
            while (pos < text.length) {
                val ch = text[pos++]
                when (ch) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        require(pos < text.length) { "Unterminated escape in JSON string" }
                        when (val esc = text[pos++]) {
                            '"', '\\', '/' -> sb.append(esc)
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                require(pos + 4 <= text.length) { "Invalid unicode escape" }
                                sb.append(text.substring(pos, pos + 4).toInt(16).toChar())
                                pos += 4
                            }
                            else -> error("Invalid escape '\\$esc'")
                        }
                    }
                    else -> sb.append(ch)
                }
            }
            error("Unterminated JSON string")
        }

        private fun parseLiteral(literal: String, value: Any?): Any? {
            require(text.startsWith(literal, pos)) { "Invalid JSON literal at index $pos" }
            pos += literal.length
            return value
        }

        private fun parseNumber(): Number {
            val start = pos
            while (pos < text.length && text[pos] in "-0123456789.eE+") {
                pos++
            }
            val raw = text.substring(start, pos)
            return if ('.' in raw || 'e' in raw || 'E' in raw) raw.toDouble() else raw.toLong()
        }

        private fun skipWhitespace() {
            while (pos < text.length && text[pos].isWhitespace()) {
                pos++
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `firestore indexes json is valid and contains activity_log composite and full attendance overrides`() {
        val raw = resolveIndexesFile().readText()
        val root = SimpleJsonParser(raw).parse() as Map<String, Any?>

        val indexes = root["indexes"] as List<Map<String, Any?>>
        var foundActivityLogIndex = false
        for (idx in indexes) {
            if (idx["collectionGroup"] == "activity_log" &&
                idx["queryScope"] == "COLLECTION"
            ) {
                val fields = idx["fields"] as List<Map<String, Any?>>
                if (fields.size == 2) {
                    val f0 = fields[0]
                    val f1 = fields[1]
                    if (f0["fieldPath"] == "unitId" &&
                        f0["order"] == "ASCENDING" &&
                        f1["fieldPath"] == "timestamp" &&
                        f1["order"] == "DESCENDING"
                    ) {
                        foundActivityLogIndex = true
                    }
                }
            }
        }
        assertTrue("Missing activity_log (unitId ASC, timestamp DESC) composite index", foundActivityLogIndex)

        val overrides = root["fieldOverrides"] as List<Map<String, Any?>>
        val requiredFields = setOf("activityDate", "scoutId")
        val seenFields = mutableSetOf<String>()

        for (override in overrides) {
            if (override["collectionGroup"] == "attendance") {
                val fieldPath = override["fieldPath"] as? String
                if (fieldPath != null && fieldPath in requiredFields) {
                    seenFields.add(fieldPath)
                    val idxArray = override["indexes"] as List<Map<String, Any?>>
                    val pairs = mutableSetOf<Pair<String?, String?>>()
                    for (entry in idxArray) {
                        assertFalse("arrayConfig must not be present", entry.containsKey("arrayConfig"))
                        pairs.add((entry["order"] as? String) to (entry["queryScope"] as? String))
                    }
                    assertTrue(pairs.contains("ASCENDING" to "COLLECTION"))
                    assertTrue(pairs.contains("DESCENDING" to "COLLECTION"))
                    assertTrue(pairs.contains("ASCENDING" to "COLLECTION_GROUP"))
                    assertTrue(pairs.contains("DESCENDING" to "COLLECTION_GROUP"))
                }
            }
        }
        assertEquals(requiredFields, seenFields)
    }

    @Test
    fun `hasAnyAttendanceForActivity queries Source SERVER and rethrows exceptions instead of returning true`(): Unit = runBlocking {
        val mockFirestore = mock(FirebaseFirestore::class.java)
        val mockActivitiesCol = mock(CollectionReference::class.java)
        val mockActivityDoc = mock(DocumentReference::class.java)
        val mockAttendanceCol = mock(CollectionReference::class.java)
        val mockLimitQuery = mock(Query::class.java)

        `when`(mockFirestore.collection(AttendanceFirestoreDataSource.COLLECTION_ACTIVITIES)).thenReturn(mockActivitiesCol)
        `when`(mockActivitiesCol.document("act_1")).thenReturn(mockActivityDoc)
        `when`(mockActivityDoc.collection(AttendanceFirestoreDataSource.SUBCOLLECTION_ATTENDANCE)).thenReturn(mockAttendanceCol)
        `when`(mockAttendanceCol.limit(1)).thenReturn(mockLimitQuery)
        `when`(mockLimitQuery.get(Source.SERVER)).thenReturn(Tasks.forException(RuntimeException("Offline")))

        val dataSource = AttendanceFirestoreDataSource(mockFirestore)
        try {
            dataSource.hasAnyAttendanceForActivity("act_1")
            fail("Expected hasAnyAttendanceForActivity to rethrow server exception")
        } catch (e: RuntimeException) {
            assertEquals("Offline", e.message)
        }
        verify(mockLimitQuery).get(Source.SERVER)
    }

    @Test
    fun `recalculateAllAttendanceFlags fails immediately when server read fails without overwriting flags`(): Unit = runBlocking {
        val firestore = mock(FirebaseFirestore::class.java)
        val activitiesCol = mock(CollectionReference::class.java)
        val act1Ref = mock(DocumentReference::class.java)
        val act1AttendanceCol = mock(CollectionReference::class.java)
        val act1LimitQuery = mock(Query::class.java)
        val activitiesSnapshot = mock(QuerySnapshot::class.java)

        val act1Doc = mockDocSnapshot(
            id = "act_1",
            fields = mapOf(
                "title" to "نشاط",
                "type" to ActivityType.TRAINING_MEETING.key,
                "status" to ActivityStatus.PLANNED.key,
                "startDate" to 1_757_000_000_000L,
                "endDate" to 1_757_003_600_000L,
                "hasAttendanceRecorded" to false
            ),
            reference = act1Ref
        )
        `when`(activitiesSnapshot.documents).thenReturn(listOf(act1Doc))
        `when`(firestore.collection(ActivityFirestoreDataSource.COLLECTION_ACTIVITIES)).thenReturn(activitiesCol)
        `when`(activitiesCol.get(Source.SERVER)).thenReturn(Tasks.forResult(activitiesSnapshot))
        `when`(activitiesCol.document("act_1")).thenReturn(act1Ref)
        `when`(act1Ref.collection(AttendanceFirestoreDataSource.SUBCOLLECTION_ATTENDANCE)).thenReturn(act1AttendanceCol)
        `when`(act1AttendanceCol.limit(1)).thenReturn(act1LimitQuery)
        `when`(act1LimitQuery.get(Source.SERVER)).thenReturn(Tasks.forException(RuntimeException("Network failure")))

        val repo = ActivityRepositoryImpl(
            dataSource = ActivityFirestoreDataSource(firestore),
            attendanceFirestoreDataSource = AttendanceFirestoreDataSource(firestore),
            activityLogDataSource = mock(ActivityLogDataSource::class.java),
            authRepository = mock(AuthRepository::class.java),
            financeRepository = mock(FinanceRepository::class.java),
            scoutRepository = mock(ScoutRepository::class.java),
            firestore = firestore
        )

        val result = repo.recalculateAllAttendanceFlags()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("اتصال") == true)
        verify(activitiesCol).get(Source.SERVER)
        verify(act1LimitQuery).get(Source.SERVER)
        verify(act1Ref, never()).update(anyMap())
    }

    @Test
    fun `recalculateAllAttendanceFlags fixes flags and only missing or mismatched attendance dates`(): Unit = runBlocking {
        val firestore = mock(FirebaseFirestore::class.java)
        val activitiesCol = mock(CollectionReference::class.java)
        val act1Ref = mock(DocumentReference::class.java)
        val actNoDateRef = mock(DocumentReference::class.java)
        val act1AttendanceCol = mock(CollectionReference::class.java)
        val actNoDateAttendanceCol = mock(CollectionReference::class.java)
        val act1LimitQuery = mock(Query::class.java)
        val actNoDateLimitQuery = mock(Query::class.java)
        val activitiesSnapshot = mock(QuerySnapshot::class.java)
        val nonEmptyCheckSnapshot = mock(QuerySnapshot::class.java)
        val emptyCheckSnapshot = mock(QuerySnapshot::class.java)
        val act1AttendanceDocsSnapshot = mock(QuerySnapshot::class.java)
        val batch = mock(WriteBatch::class.java)

        val startDate = 1_757_000_000_000L
        val act1Doc = mockDocSnapshot(
            id = "act_1",
            fields = mapOf(
                "title" to "نشاط 1",
                "type" to ActivityType.TRAINING_MEETING.key,
                "status" to ActivityStatus.DONE.key,
                "startDate" to startDate,
                "endDate" to startDate + 3600_000L,
                "hasAttendanceRecorded" to false
            ),
            reference = act1Ref
        )
        val actNoDateDoc = mockDocSnapshot(
            id = "act_no_date",
            fields = mapOf(
                "title" to "نشاط بلا تاريخ",
                "type" to ActivityType.TRAINING_MEETING.key,
                "status" to ActivityStatus.PLANNED.key,
                "startDate" to 0L,
                "endDate" to 0L,
                "hasAttendanceRecorded" to false
            ),
            reference = actNoDateRef
        )

        val missingRef = mock(DocumentReference::class.java)
        val sameRef = mock(DocumentReference::class.java)
        val staleRef = mock(DocumentReference::class.java)
        val docMissingDate = mockDocSnapshot("s1", mapOf("activityDate" to null), missingRef)
        val docSameDate = mockDocSnapshot("s2", mapOf("activityDate" to startDate), sameRef)
        val docStaleDate = mockDocSnapshot("s3", mapOf("activityDate" to startDate - 10_000L), staleRef)

        `when`(activitiesSnapshot.documents).thenReturn(listOf(act1Doc, actNoDateDoc))
        `when`(nonEmptyCheckSnapshot.isEmpty).thenReturn(false)
        `when`(emptyCheckSnapshot.isEmpty).thenReturn(true)
        `when`(act1AttendanceDocsSnapshot.documents).thenReturn(listOf(docMissingDate, docSameDate, docStaleDate))

        `when`(firestore.collection(ActivityFirestoreDataSource.COLLECTION_ACTIVITIES)).thenReturn(activitiesCol)
        `when`(activitiesCol.get(Source.SERVER)).thenReturn(Tasks.forResult(activitiesSnapshot))
        `when`(activitiesCol.document("act_1")).thenReturn(act1Ref)
        `when`(activitiesCol.document("act_no_date")).thenReturn(actNoDateRef)

        `when`(act1Ref.collection(AttendanceFirestoreDataSource.SUBCOLLECTION_ATTENDANCE)).thenReturn(act1AttendanceCol)
        `when`(actNoDateRef.collection(AttendanceFirestoreDataSource.SUBCOLLECTION_ATTENDANCE)).thenReturn(actNoDateAttendanceCol)

        `when`(act1AttendanceCol.limit(1)).thenReturn(act1LimitQuery)
        `when`(act1LimitQuery.get(Source.SERVER)).thenReturn(Tasks.forResult(nonEmptyCheckSnapshot))
        `when`(actNoDateAttendanceCol.limit(1)).thenReturn(actNoDateLimitQuery)
        `when`(actNoDateLimitQuery.get(Source.SERVER)).thenReturn(Tasks.forResult(emptyCheckSnapshot))

        `when`(act1Ref.update(anyMap())).thenReturn(Tasks.forResult(null))
        `when`(act1AttendanceCol.get(Source.SERVER)).thenReturn(Tasks.forResult(act1AttendanceDocsSnapshot))
        `when`(firestore.batch()).thenReturn(batch)
        `when`(batch.commit()).thenReturn(Tasks.forResult(null))

        val repo = ActivityRepositoryImpl(
            dataSource = ActivityFirestoreDataSource(firestore),
            attendanceFirestoreDataSource = AttendanceFirestoreDataSource(firestore),
            activityLogDataSource = mock(ActivityLogDataSource::class.java),
            authRepository = mock(AuthRepository::class.java),
            financeRepository = mock(FinanceRepository::class.java),
            scoutRepository = mock(ScoutRepository::class.java),
            firestore = firestore
        )

        val result = repo.recalculateAllAttendanceFlags()
        assertTrue(result.isSuccess)
        val report = result.getOrThrow()
        assertEquals(2, report.activitiesChecked)
        assertEquals(1, report.flagsFixed)
        assertEquals(2, report.attendanceDatesFixed)

        verify(act1Ref, times(1)).update(anyMap())
        verify(actNoDateRef, never()).update(anyMap())
        verify(actNoDateAttendanceCol, never()).get(Source.SERVER)
        verify(batch, times(1)).update(eq(missingRef), anyMap())
        verify(batch, times(1)).update(eq(staleRef), anyMap())
        verify(batch, never()).update(eq(sameRef), anyMap())
    }

    @Test
    fun `updateActivity emits Arabic warning when attendance date sync fails while still returning success`(): Unit = runBlocking {
        val firestore = mock(FirebaseFirestore::class.java)
        val activitiesCol = mock(CollectionReference::class.java)
        val act1Ref = mock(DocumentReference::class.java)
        val attendanceCol = mock(CollectionReference::class.java)

        val oldStartDate = 1_757_000_000_000L
        val oldEndDate = 1_757_003_600_000L
        val oldDoc = mockDocSnapshot(
            id = "act_1",
            fields = mapOf(
                "title" to "نشاط",
                "type" to ActivityType.TRAINING_MEETING.key,
                "status" to ActivityStatus.PLANNED.key,
                "startDate" to oldStartDate,
                "endDate" to oldEndDate,
                "hasAttendanceRecorded" to false
            ),
            reference = act1Ref
        )
        val updatedActivity = Activity(
            id = "act_1",
            title = "نشاط",
            type = ActivityType.TRAINING_MEETING.key,
            status = ActivityStatus.PLANNED.key,
            startDate = 1_758_000_000_000L,
            endDate = 1_758_003_600_000L
        )

        `when`(firestore.collection(ActivityFirestoreDataSource.COLLECTION_ACTIVITIES)).thenReturn(activitiesCol)
        `when`(activitiesCol.document("act_1")).thenReturn(act1Ref)
        `when`(act1Ref.get()).thenReturn(Tasks.forResult(oldDoc))
        `when`(act1Ref.set(any(), eq(SetOptions.merge()))).thenReturn(Tasks.forResult(null))
        `when`(act1Ref.collection(AttendanceFirestoreDataSource.SUBCOLLECTION_ATTENDANCE)).thenReturn(attendanceCol)
        `when`(attendanceCol.get(Source.SERVER)).thenReturn(Tasks.forException(RuntimeException("Offline")))

        val repo = ActivityRepositoryImpl(
            dataSource = ActivityFirestoreDataSource(firestore),
            attendanceFirestoreDataSource = AttendanceFirestoreDataSource(firestore),
            activityLogDataSource = mock(ActivityLogDataSource::class.java),
            authRepository = mock(AuthRepository::class.java),
            financeRepository = mock(FinanceRepository::class.java),
            scoutRepository = mock(ScoutRepository::class.java),
            firestore = firestore
        )

        val result = repo.updateActivity(updatedActivity)
        assertTrue(result.isSuccess)
        val warning = repo.dateSyncWarnings.value
        assertNotNull(warning)
        assertTrue(warning!!.contains("إصلاح حالة الحضور للأنشطة"))
        verify(attendanceCol).get(Source.SERVER)
        verify(firestore, never()).batch()
    }
}
