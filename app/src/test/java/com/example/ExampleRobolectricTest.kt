package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("سرب النُّجُب", appName)
  }

  @Test
  fun `app container initializes all startup repositories without circular dependency`() {
    val app = ApplicationProvider.getApplicationContext<ScoutApplication>()
    val container = app.container
    assertNotNull(container.authRepository)
    assertNotNull(container.scoutRepository)
    assertNotNull(container.financeRepository)
    assertNotNull(container.activityRepository)
    assertNotNull(container.brandingRepository)
    assertNotNull(container.termsRepository)
    assertNotNull(container.evaluationRepository)
  }

  @Test
  fun `main activity launches splash screen without crashing`() {
    val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    val activity = controller.get()
    assertNotNull(activity)
    controller.pause().stop().destroy()
  }
}
