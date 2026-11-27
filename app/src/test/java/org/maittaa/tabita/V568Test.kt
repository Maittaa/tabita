package org.maittaa.tabita

import android.app.NotificationManager
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 5.68: колокольчики окончания таймеров. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1100dp-xxhdpi")
class V568Test {
    init { Prefs(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()).lang = "ru" }
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun save(name: String) {
        rule.waitForIdle()
        val v = rule.activity.window.decorView
        val b = android.graphics.Bitmap.createBitmap(v.width, v.height, android.graphics.Bitmap.Config.ARGB_8888).also { v.draw(android.graphics.Canvas(it)) }
        val out = File(System.getProperty("user.dir"), "../shots").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun bells() {
        val act = rule.activity
        assertEquals(5, Bells.all.size)
        assertEquals(Bells.DEFAULT, act.prefs.bell)
        // у каждого звука есть ресурс, и он не пустой
        Bells.all.forEach { b -> act.resources.openRawResourceFd(b.res).use { assertTrue(b.id, it.length > 5_000) } }
        Bells.all.forEach { assertTrue(EN.containsKey(it.name)) }
        // канал «Отдых окончен» без звука, старый со звуком удалён
        org.maittaa.tabita.train.TimerService.createChannels(act)
        val nm = act.getSystemService(NotificationManager::class.java)
        assertNull(nm.getNotificationChannel(org.maittaa.tabita.train.TimerService.CH_DONE).sound)
        assertNull(nm.getNotificationChannel("rest_done_v1"))
        // воспроизведение не падает
        Bells.play(act); Bells.playSystem(act); Bells.stop()

        rule.runOnUiThread { act.openSettings.value = "bells" }
        rule.waitForIdle()
        rule.onNodeWithTag("bell_bell_hand").assertExists()
        rule.onNodeWithTag("bell_bell_bowl").performClick()
        rule.waitForIdle()
        assertEquals("bell_bowl", act.prefs.bell)
        rule.onNodeWithTag("bell_beep").performClick()
        assertEquals(Bells.SIMPLE, act.prefs.bell)
        rule.onNodeWithTag("bell_bell_chimes").performClick()
        save("68_bells")
        assertTrue(Changelog.all.any { it.version == "5.68" })
    }
}
