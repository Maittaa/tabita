package org.maittaa.tabita

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneId

/** 5.25: нагрузка по тяжести, дневной сон, точность весов. */
@RunWith(RobolectricTestRunner::class)
class V525Test {
    private val z = ZoneId.systemDefault()
    private fun ms(d: LocalDate, h: Int, m: Int = 0) = d.atTime(h, m).atZone(z).toInstant().toEpochMilli()

    @Test fun load() {
        val l = listOf(WorkoutSession("walk", 90), WorkoutSession("strength", 40), WorkoutSession("stretch", 15))
        assertEquals(40.0, Workouts.loadMin(l), 0.01)
        assertEquals(0.0, Workouts.loadMin(listOf(WorkoutSession("walk", 120))), 0.01)
    }

    @Test fun naps() {
        val d = LocalDate.of(2026, 11, 18)
        assertTrue(Sleep.isNap(ms(d, 18, 10), ms(d, 21, 30), z))          // вечерний сон
        assertTrue(Sleep.isNap(ms(d, 13), ms(d, 14), z))                    // дневной
        assertFalse(Sleep.isNap(ms(d, 1, 46), ms(d, 5, 46), z))             // ночной после полуночи
        assertFalse(Sleep.isNap(ms(d.minusDays(1), 23), ms(d, 7), z))       // ночной с вечера
    }

    @Test fun bodyMorningAndJump() {
        val d = LocalDate.of(2026, 11, 18)
        fun day(t: Long, w: Double, fat: Double, mus: Double) = BodyDay(t, listOf(
            BodyMetric("builtin.weight", "Вес", "kg", w), BodyMetric("builtin.body_fat", "Жир", "%", fat), BodyMetric("builtin.muscle", "Мышцы", "kg", mus)))
        assertTrue(day(ms(d, 7, 30), 78.0, 20.0, 35.0).morning(z))
        assertFalse(day(ms(d, 20), 78.0, 20.0, 35.0).morning(z))
        val e = mapOf(
            d.minusDays(7).toString() to mapOf("body" to day(ms(d.minusDays(7), 7), 78.0, 22.0, 33.0).toJson()),
            d.toString() to mapOf("body" to day(ms(d, 7), 78.4, 18.4, 36.8).toJson()),
        )
        val j = BodyCheck.jump(e, "body", d)
        assertNotNull(j)
        assertEquals(3.8, j!!.muscleKg!!, 0.01); assertEquals(-3.6, j.fatPct!!, 0.01)
        I18n.lang = "ru"
        assertTrue(BodyCheck.text(j), BodyCheck.text(j).contains("скорее вода"))
        val ok = mapOf(
            d.minusDays(7).toString() to mapOf("body" to day(ms(d.minusDays(7), 7), 78.0, 20.0, 35.0).toJson()),
            d.toString() to mapOf("body" to day(ms(d, 7), 78.3, 19.8, 35.3).toJson()),
        )
        assertNull(BodyCheck.jump(ok, "body", d))
    }
}
