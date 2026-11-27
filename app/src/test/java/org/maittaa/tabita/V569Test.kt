package org.maittaa.tabita

import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 5.69: звук таймеров — простой сигнал или звук Android из системного окна выбора. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1100dp-xxhdpi")
class V569Test {
    init { Prefs(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()).lang = "ru" }
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun systemSound() {
        val act = rule.activity
        assertEquals(Bells.SIMPLE, act.prefs.bell)
        // значения синтезированных колокольчиков 5.68 считаются простым сигналом
        assertTrue(Bells.isSimple("bell_bowl")); assertTrue(Bells.isSimple(""))
        assertFalse(Bells.isSimple("content://media/internal/audio/media/12"))
        assertEquals("Простой сигнал", Bells.title(act, "bell_hand"))
        // окно выбора: уведомления и будильники, без «Без звука»
        val i = Bells.pickerIntent(act, Bells.SIMPLE)
        assertEquals(RingtoneManager.ACTION_RINGTONE_PICKER, i.action)
        assertEquals(RingtoneManager.TYPE_NOTIFICATION or RingtoneManager.TYPE_ALARM, i.getIntExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, 0))
        assertFalse(i.getBooleanExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true))
        assertNull(Bells.fromPicker(null))
        val u = "content://media/internal/audio/media/12"
        assertEquals(u, Bells.fromPicker(Intent().putExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri.parse(u))))
        // канал отдыха без собственного звука
        org.maittaa.tabita.train.TimerService.createChannels(act)
        assertNull(act.getSystemService(NotificationManager::class.java).getNotificationChannel(org.maittaa.tabita.train.TimerService.CH_DONE).sound)
        Bells.play(act); Bells.play(act, u); Bells.stop()

        rule.runOnUiThread { act.openSettings.value = "bells" }
        rule.waitForIdle()
        rule.onNodeWithTag("bell_system").performClick()
        rule.waitForIdle()
        val started = shadowOf(act).nextStartedActivityForResult
        assertEquals(RingtoneManager.ACTION_RINGTONE_PICKER, started.intent.action)
        shadowOf(act).receiveResult(started.intent, Activity.RESULT_OK, Intent().putExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri.parse(u)))
        rule.waitForIdle()
        assertEquals(u, act.prefs.bell)
        rule.onNodeWithTag("bell_play").assertExists()
        rule.onNodeWithTag("bell_simple").performClick()
        rule.waitForIdle()
        assertEquals(Bells.SIMPLE, act.prefs.bell)
        assertTrue(Changelog.all.any { it.version == "5.69" })
    }
}
