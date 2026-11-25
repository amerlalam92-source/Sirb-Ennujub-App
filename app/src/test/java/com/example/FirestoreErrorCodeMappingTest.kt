package com.example

import com.example.data.common.DataLoadErrorKind
import com.example.data.common.mapFirestoreErrorCodeToKind
import com.example.data.common.mapThrowableToDataLoadError
import com.google.firebase.firestore.FirebaseFirestoreException
import org.junit.Assert.assertEquals
import org.junit.Test

class FirestoreErrorCodeMappingTest {

    @Test
    fun `mapFirestoreErrorCodeToKind maps all Firestore Code enum values`() {
        assertEquals(
            DataLoadErrorKind.NO_PERMISSION,
            mapFirestoreErrorCodeToKind(FirebaseFirestoreException.Code.PERMISSION_DENIED)
        )
        assertEquals(
            DataLoadErrorKind.QUOTA,
            mapFirestoreErrorCodeToKind(FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED)
        )
        assertEquals(
            DataLoadErrorKind.MISSING_INDEX,
            mapFirestoreErrorCodeToKind(FirebaseFirestoreException.Code.FAILED_PRECONDITION)
        )
        assertEquals(
            DataLoadErrorKind.SIGNED_OUT,
            mapFirestoreErrorCodeToKind(FirebaseFirestoreException.Code.UNAUTHENTICATED)
        )
        assertEquals(
            DataLoadErrorKind.OTHER,
            mapFirestoreErrorCodeToKind(FirebaseFirestoreException.Code.UNAVAILABLE)
        )
        assertEquals(
            DataLoadErrorKind.OTHER,
            mapFirestoreErrorCodeToKind(null)
        )
    }

    @Test
    fun `mapThrowableToDataLoadError preserves FirebaseFirestoreException message and kind`() {
        val indexError = FirebaseFirestoreException(
            "The query requires an index. You can create it here: https://console.firebase.google.com/v1/r/project/test/firestore/indexes",
            FirebaseFirestoreException.Code.FAILED_PRECONDITION
        )
        val mapped = mapThrowableToDataLoadError(indexError)
        assertEquals(DataLoadErrorKind.MISSING_INDEX, mapped.kind)
        assertEquals(indexError.message, mapped.message)
    }
}
