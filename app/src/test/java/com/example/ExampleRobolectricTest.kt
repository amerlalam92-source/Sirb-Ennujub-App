package com.example

import android.content.Context
import android.content.SharedPreferences
import com.example.di.DefaultAppContainer
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Ignore
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.w3c.dom.Element

class ExampleRobolectricTest {

    private fun resolveStringsXml(): File {
        val candidates = listOf(
            File("app/src/main/res/values/strings.xml"),
            File("src/main/res/values/strings.xml")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Could not locate strings.xml from ${File(".").absolutePath}")
    }

    @Test
    fun `read string from context`() {
        val doc = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(resolveStringsXml())
        val stringNodes = doc.getElementsByTagName("string")
        var appName: String? = null
        for (i in 0 until stringNodes.length) {
            val element = stringNodes.item(i) as? Element ?: continue
            if (element.getAttribute("name") == "app_name") {
                appName = element.textContent
                break
            }
        }
        assertEquals("إدارة الوحدة الكشفية", appName)
    }

    @Test
    fun `app container initializes all startup repositories without circular dependency`() {
        val mockPrefs = mock(SharedPreferences::class.java, RETURNS_DEEP_STUBS)
        val mockContext = mock(Context::class.java, RETURNS_DEEP_STUBS)
        `when`(mockContext.applicationContext).thenReturn(mockContext)
        `when`(mockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mockPrefs)

        val mockAuth = mock(FirebaseAuth::class.java)
        val mockFirestore = mock(FirebaseFirestore::class.java, RETURNS_DEEP_STUBS)

        val container = DefaultAppContainer(
            context = mockContext,
            firebaseAuthProvider = { mockAuth },
            firestoreProvider = { mockFirestore }
        )

        assertNotNull(container.authRepository)
        assertNotNull(container.scoutRepository)
        assertNotNull(container.financeRepository)
        assertNotNull(container.activityRepository)
        assertNotNull(container.attendanceRepository)
        assertNotNull(container.progressRepository)
        assertNotNull(container.hobbyBadgeRepository)
        assertNotNull(container.brandingRepository)
        assertNotNull(container.termsRepository)
        assertNotNull(container.evaluationRepository)
        assertNotNull(container.updateRepository)
        assertNotNull(container.evaluationMatrixViewModelFactory)
    }

    @Ignore("Launches MainActivity with ScoutApplication, FirebaseAppCheck, and live Firestore listeners; disabled in offline CI")
    @Test
    fun `main activity launches splash screen without crashing`() {
        // Instrumented / Robolectric smoke test intentionally disabled in offline CI.
    }
}
