package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.di.DefaultAppContainer
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import org.mockito.Mockito.mock
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("إدارة الوحدة الكشفية", appName)
    }

    @Test
    fun `app container initializes all startup repositories without circular dependency`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mockAuth = mock(FirebaseAuth::class.java)
        val mockFirestore = mock(FirebaseFirestore::class.java, RETURNS_DEEP_STUBS)

        val container = DefaultAppContainer(
            context = context,
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
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        assertNotNull(activity)
        controller.pause().stop().destroy()
    }
}
