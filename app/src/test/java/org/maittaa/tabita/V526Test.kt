package org.maittaa.tabita

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/** 5.26: режим дня — будильник, рекомендуемый отбой, будни/выходные, разброс. */
@RunWith(RobolectricTestRunner::class)
class V526Test {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val z = ZoneId.systemDefault()

    private fun night(repo: Repo, d: LocalDate, bedH: Int, bedM: Int, wakeH: Int, wakeM: Int) {
        val start = (if (bedH >= 12) d.minusDays(1) else d).atTime(bedH, bedM).atZone(z).toInstant().toEpochMilli()
        val end = d.atTime(wakeH, wakeM).atZone(z).toInstant().toEpochMilli()
        val det = SleepDetail(start, end, listOf(SleepStage(0, ((end - start) / 60_000).toInt(), 2)))
        repo.setValue(d.toString(), "sleep", det.asleep.toString())
        repo.setValue(d.toString(), SleepDetail.key("sleep"), det.toJson())
    }

    @Test fun alarmAndRegime() {
        val repo = Repo(ctx); val prefs = Prefs(ctx)
        // среда, 18.11.2026; будни: отбой ~0:40, подъём 6:40; выходные: 2:00 → 10:30
        val wed = LocalDate.of(2026, 11, 18)
        for (i in 0..20) {
            val d = wed.minusDays(i.toLong())
            if (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY) night(repo, d, 2, 0, 10, 30)
            else night(repo, d, 0, 40, 6, 40)
        }
        val alarm = wed.plusDays(1).atTime(6, 30).atZone(z).toInstant().toEpochMilli()
        val r = DayRhythm.of(repo, wed, prefs, wed.atTime(20, 0), alarm)
        assertTrue(r.alarm)
        assertEquals(6 * 60 + 30, r.nextWake)
        assertEquals(23 * 60 + 15, r.bed)                       // 6:30 − 7 ч − 15 мин
        assertEquals(40, r.usualBed)                             // привычный — 0:40
        assertNotNull(r.jetlag); assertTrue("jetlag ${r.jetlag}", r.jetlag!! >= 100)
        assertEquals(6 * 60 + 40, r.wakeWeekday); assertEquals(10 * 60 + 30, r.wakeWeekend)
        // без будильника: подъём — привычный для четверга (будни)
        val r2 = DayRhythm.of(repo, wed, prefs, wed.atTime(20, 0), null)
        assertFalse(r2.alarm); assertEquals(6 * 60 + 40, r2.nextWake); assertEquals(23 * 60 + 25, r2.bed)
        // пятница → завтра суббота: подъём выходного дня
        val fri = LocalDate.of(2026, 11, 13)
        assertEquals(10 * 60 + 30, DayRhythm.of(repo, fri, prefs, fri.atTime(20, 0), null).nextWake)
        // советы (только для сегодняшнего дня): привычный отбой 0:40, а нужно ~23:25 → «ложитесь позже, чем нужно»
        val today = LocalDate.now()
        for (i in 0..10) night(repo, today.minusDays(i.toLong()), 0, 40, 6, 40)
        val tips = Tips.build(repo, prefs, today, java.time.LocalTime.of(20, 30))
        val ids = tips.map { it.id }.toString()
        if (!DayRhythm.weekend(today.plusDays(1))) {
            assertTrue(ids, tips.any { it.id == "regime_late" })
            assertTrue(ids, tips.any { it.id == "regime_alarm" })
        }
    }
}
