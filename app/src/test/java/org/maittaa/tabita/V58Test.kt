package org.maittaa.tabita

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 5.8: гликемическая оценка и оценка сна. */
@RunWith(RobolectricTestRunner::class)
class V58Test {
    @Test fun mealGlFromGiAndCarbs() {
        val m = Meal(carbs = 60.0, gi = 50.0)
        assertEquals(30.0, m.glEff!!, 1e-9)
        assertEquals(12.0, m.copy(gl = 12.0).glEff!!, 1e-9)
        assertNull(Meal(carbs = 60.0).glEff)
        val back = parseMeals(mealsToString(listOf(m.copy(gl = 31.0))))[0]
        assertEquals(50.0, back.gi!!, 1e-9); assertEquals(31.0, back.gl!!, 1e-9)
        assertNull(parseMeals(mealsToString(listOf(Meal(kcal = 100.0))))[0].gi)
    }

    @Test fun glycemicDay() {
        assertNull(Glycemic.day(listOf(Meal(kcal = 300.0, carbs = 40.0))))
        // ровный день: две лёгкие трапезы
        val good = Glycemic.day(listOf(Meal(carbs = 40.0, gi = 40.0), Meal(carbs = 30.0, gi = 35.0)))!!
        assertEquals(100, good.score); assertEquals(0, good.band)
        assertEquals(37.857, good.avgGi!!, 0.01)
        // тяжёлый: ГН 75 + 70 = 145, средний ГИ 75, два пика
        val bad = Glycemic.day(listOf(Meal(carbs = 100.0, gi = 75.0), Meal(carbs = 93.3, gi = 75.0), Meal(kcal = 400.0, carbs = 50.0)))!!
        assertTrue(bad.score < 40); assertEquals(3, bad.band); assertEquals(1, bad.unknown)
        assertEquals(2, Glycemic.dayGlLevel(bad.load))
    }

    private fun night(deep: Int, rem: Int, light: Int, wakes: Int): SleepDetail {
        val st = mutableListOf<SleepStage>(); var off = 0
        fun add(tp: Int, m: Int) { st += SleepStage(off, m, tp); off += m }
        add(2, light / 2); add(3, deep); repeat(wakes) { add(0, 5); add(2, 1) }; add(1, rem); add(2, light - light / 2 - wakes)
        return SleepDetail(0, off * 60_000L, st)
    }

    @Test fun sleepScore() {
        // только длительность
        assertEquals(100, Sleep.score(450.0, null, 420.0).total)
        assertEquals(80, Sleep.score(420.0, null, 480.0).total)
        assertEquals(0, Sleep.score(150.0, null, 420.0).total)
        assertTrue(Sleep.score(720.0, null, 420.0).total in 70..95)
        assertNull(Sleep.score(450.0, null, 420.0).quality)
        // хорошая ночь с фазами
        val n = night(deep = 80, rem = 100, light = 270, wakes = 2)
        assertEquals(2, n.wakes)
        val s = Sleep.score(n.asleep.toDouble(), n, 420.0)
        assertNotNull(s.quality); assertTrue(s.total >= 90)
        // мало глубокого и REM, много пробуждений
        val b = night(deep = 20, rem = 30, light = 370, wakes = 12)
        val sb = Sleep.score(b.asleep.toDouble(), b, 420.0)
        assertTrue(sb.total < s.total - 15)
    }

    @Test fun sleepCombineFromHealthConnect() {
        val t0 = 1_700_000_000_000L
        fun m(x: Int) = t0 + x * 60_000L
        val ses = Sleep.RawSession(m(0), m(480), listOf(
            Sleep.RawStage(m(0), m(10), 7), Sleep.RawStage(m(10), m(100), 4), Sleep.RawStage(m(100), m(190), 5),
            Sleep.RawStage(m(190), m(200), 1), Sleep.RawStage(m(200), m(300), 6), Sleep.RawStage(m(300), m(470), 4),
            Sleep.RawStage(m(470), m(480), 1),
        ))
        val (min, d) = Sleep.combine(listOf(ses))
        assertEquals(450L, min)
        assertEquals(90, d.deep); assertEquals(100, d.rem); assertEquals(260, d.light); assertEquals(30, d.awake)
        assertEquals(1, d.wakes) // начальное и последнее бодрствование не считаются пробуждениями
        val back = SleepDetail.parse(d.toJson())!!
        assertEquals(d, back)
        assertNull(SleepDetail.parse("oops"))
    }
}
