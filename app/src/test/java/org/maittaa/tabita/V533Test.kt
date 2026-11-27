package org.maittaa.tabita

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.LocalDate
import java.time.LocalDateTime

/** 5.33: уведомления об отбое — время из «Ритма дня», ночь считается по вечеру. */
@RunWith(RobolectricTestRunner::class)
class V533Test {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test fun bedtimeTonight() {
        val repo = Repo(ctx); val prefs = Prefs(ctx)
        prefs.rhythmWake = 7 * 60; prefs.rhythmBed = 23 * 60   // вручную: подъём 7:00, отбой 23:00
        Alarms.next = null
        val d = LocalDate.of(2026, 11, 18)
        val (b1, _) = Reminders.bedtimeAt(repo, prefs, d.atTime(19, 0))
        assertEquals(d.atTime(23, 0), b1)
        // после отбоя — уже следующая ночь
        val (b2, _) = Reminders.bedtimeAt(repo, prefs, d.atTime(23, 30))
        assertEquals(d.plusDays(1).atTime(23, 0), b2)
        // отбой после полуночи: в 0:05 ещё «эта» ночь
        prefs.rhythmBed = 30
        val (b3, _) = Reminders.bedtimeAt(repo, prefs, d.plusDays(1).atTime(0, 5))
        assertEquals(d.plusDays(1).atTime(0, 30), b3)
        prefs.rhythmWake = -1; prefs.rhythmBed = -1
    }

    @Test fun schedulesWhenOn() {
        val prefs = Prefs(ctx)
        prefs.bedOn = true; prefs.bedLead = 60; prefs.reminderOn = false; prefs.motivOn = false; prefs.weeklyOn = false
        Reminders.scheduleAll(ctx)
        val am = shadowOf(ctx.getSystemService(AlarmManager::class.java))
        val n = am.scheduledAlarms.size
        assertTrue("$n", n >= 2)   // отбой (+ «скоро отбой», если ещё не прошло) и пересчёт на завтра
        prefs.bedOn = false
        Reminders.scheduleAll(ctx)
        assertEquals(0, am.scheduledAlarms.size)
    }
}
