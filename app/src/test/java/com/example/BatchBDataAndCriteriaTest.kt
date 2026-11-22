package com.example

import com.example.data.common.DataLoadErrorKind
import com.example.data.common.DataLoadErrorReporter
import com.example.data.common.ListenerFallbackState
import com.example.data.common.mapFirestoreErrorCodeNameToKind
import com.example.data.evaluation.pickNotebookCriterion
import com.example.data.evaluation.pickQuranCriterion
import com.example.domain.evaluation.CriteriaType
import com.example.domain.evaluation.EvaluationCriteria
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchBDataAndCriteriaTest {

    @Test
    fun `mapFirestoreErrorCodeNameToKind maps expected codes and defaults to OTHER`() {
        assertEquals(DataLoadErrorKind.NO_PERMISSION, mapFirestoreErrorCodeNameToKind("PERMISSION_DENIED"))
        assertEquals(DataLoadErrorKind.QUOTA, mapFirestoreErrorCodeNameToKind("RESOURCE_EXHAUSTED"))
        assertEquals(DataLoadErrorKind.MISSING_INDEX, mapFirestoreErrorCodeNameToKind("FAILED_PRECONDITION"))
        assertEquals(DataLoadErrorKind.SIGNED_OUT, mapFirestoreErrorCodeNameToKind("UNAUTHENTICATED"))
        assertEquals(DataLoadErrorKind.OTHER, mapFirestoreErrorCodeNameToKind("UNAVAILABLE"))
        assertEquals(DataLoadErrorKind.OTHER, mapFirestoreErrorCodeNameToKind(null))
    }

    @Test
    fun `listenerFallbackState retains lastGood on error and clears source on snapshot`() {
        val reporter = DataLoadErrorReporter()
        val state = ListenerFallbackState<String>("scouts", reporter)

        // Error before any snapshot -> emptyList + reported error
        val initialErrorFallback = state.onError(IllegalStateException("offline"))
        assertEquals(emptyList<String>(), initialErrorFallback)
        assertTrue(reporter.errors.value.containsKey("scouts"))
        assertEquals(DataLoadErrorKind.OTHER, reporter.errors.value["scouts"]?.kind)

        // First good snapshot -> updates lastGood + clears source error
        val goodItems = listOf("scout-1", "scout-2")
        assertEquals(goodItems, state.onSnapshot(goodItems))
        assertTrue(reporter.errors.value.isEmpty())

        // Subsequent error -> preserves lastGood + reports error
        val subsequentErrorFallback = state.onError(RuntimeException("permission lost"))
        assertEquals(goodItems, subsequentErrorFallback)
        assertTrue(reporter.errors.value.containsKey("scouts"))

        // Subsequent snapshot clears again without touching other sources
        reporter.report("activities", RuntimeException("index missing"))
        assertEquals(listOf("scout-3"), state.onSnapshot(listOf("scout-3")))
        assertEquals(setOf("activities"), reporter.errors.value.keys)
    }

    @Test
    fun `pickQuranCriterion prefers exact type over lower-order name match and tie-breaks deterministically`() {
        val criteria = listOf(
            EvaluationCriteria(id = "a_name_only", name = "حفظ الأناشيد", type = EvaluationCriteria.TYPE_LEADER_CRITERIA, order = 1),
            EvaluationCriteria(id = "c_quran_later", name = "قرآن متقدم", type = EvaluationCriteria.TYPE_QURAN, order = 6),
            EvaluationCriteria(id = "b_quran_tie_2", name = "تقييم حفظ القرآن", type = CriteriaType.QURAN.value, order = 5),
            EvaluationCriteria(id = "a_quran_tie_1", name = "مراجعة القرآن", type = CriteriaType.QURAN.name, order = 5)
        )

        val picked = pickQuranCriterion(criteria)
        assertEquals("a_quran_tie_1", picked?.id)
    }

    @Test
    fun `pickQuranCriterion falls back to name-contains only when no typed criterion exists`() {
        val criteria = listOf(
            EvaluationCriteria(id = "b_hifz", name = "تقييم الحفظ", type = EvaluationCriteria.TYPE_LEADER_CRITERIA, order = 4),
            EvaluationCriteria(id = "a_quran", name = "قرآن كريم", type = EvaluationCriteria.TYPE_LEADER_CRITERIA, order = 3)
        )

        assertEquals("a_quran", pickQuranCriterion(criteria)?.id)
        assertNull(
            pickQuranCriterion(
                listOf(
                    EvaluationCriteria(id = "x", name = "الأخلاق", type = EvaluationCriteria.TYPE_LEADER_CRITERIA, order = 1)
                )
            )
        )
    }

    @Test
    fun `pickNotebookCriterion prefers exact type and falls back deterministically by order then id`() {
        val withExactType = listOf(
            EvaluationCriteria(id = "name_first", name = "ترتيب الكراس الإضافي", type = EvaluationCriteria.TYPE_LEADER_CRITERIA, order = 1),
            EvaluationCriteria(id = "nb_b", name = "الكراس 2", type = CriteriaType.NOTEBOOK.name, order = 6),
            EvaluationCriteria(id = "nb_a", name = "تقييم الكراس", type = EvaluationCriteria.TYPE_NOTEBOOK, order = 6)
        )
        assertEquals("nb_a", pickNotebookCriterion(withExactType)?.id)

        val fallbackOnly = listOf(
            EvaluationCriteria(id = "k_2", name = "مراقبة الكراس", type = EvaluationCriteria.TYPE_LEADER_CRITERIA, order = 5),
            EvaluationCriteria(id = "k_1", name = "نظافة الكراس", type = EvaluationCriteria.TYPE_LEADER_CRITERIA, order = 2)
        )
        assertEquals("k_1", pickNotebookCriterion(fallbackOnly)?.id)
        assertNull(pickNotebookCriterion(emptyList()))
    }

    @Test
    fun `dataLoadErrorReporter StateFlow emits reported and cleared errors deterministically`() = runTest {
        val reporter = DataLoadErrorReporter()
        assertTrue(reporter.errors.first().isEmpty())

        reporter.report("scouts", IllegalStateException("network error"))
        val afterReport = reporter.errors.first()
        assertEquals(setOf("scouts"), afterReport.keys)
        assertEquals(DataLoadErrorKind.OTHER, afterReport["scouts"]?.kind)
        assertEquals("network error", afterReport["scouts"]?.message)

        reporter.clear("scouts")
        assertTrue(reporter.errors.first().isEmpty())
    }
}
