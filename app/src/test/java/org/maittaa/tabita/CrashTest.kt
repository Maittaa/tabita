package org.maittaa.tabita

import android.Manifest
import android.app.Application
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1400dp-xxhdpi")
class CrashTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test fun notificationPermissionFlow() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Prefs(app).lang = "ru"
        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            rule.onNodeWithText("Ещё").performScrollTo().performClick(); rule.onNodeWithText("Уведомления").performClick()
            rule.onNodeWithText("Напоминать заполнить день").performScrollTo().performClick()
            rule.waitForIdle()
            sc.onActivity { a ->
                val req = shadowOf(a).lastRequestedPermission
                println("REQ=" + req?.requestedPermissions?.toList() + " code=" + req?.requestCode)
                shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
                a.onRequestPermissionsResult(req!!.requestCode, req.requestedPermissions, intArrayOf(0))
            }
            rule.waitForIdle()
            println("PERMFLOW_OK reminderOn=" + Prefs(app).reminderOn)
        }
    }

    @Test fun notificationsAndLifecycle() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Prefs(app).lang = "ru"
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            rule.onNodeWithText("Ещё").performScrollTo().performClick(); rule.onNodeWithText("Уведомления").performClick()
            rule.onNodeWithText("Напоминать заполнить день").performScrollTo().performClick()
            rule.waitForIdle(); println("STEP reminder on")
            rule.onNodeWithText("Мотивирующие уведомления в случайное время").performScrollTo().performClick()
            rule.waitForIdle(); println("STEP motiv on")
            rule.onNodeWithText("Показать случайную сейчас").performScrollTo().performClick()
            rule.waitForIdle(); println("STEP show")
            sc.moveToState(Lifecycle.State.CREATED); sc.moveToState(Lifecycle.State.RESUMED)
            rule.waitForIdle(); println("STEP stop/resume")
            sc.recreate(); rule.waitForIdle(); println("STEP recreate")
            println("CRASHTEST_OK")
        }
    }
}
