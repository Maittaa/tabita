package org.maittaa.tabita

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Расчёты вкладки «Итоги месяца». */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
class MonthTest {
    private val template = defaultTemplate()
    private val oct = LocalDate.of(2026, 10, 1)
    private val today = LocalDate.of(2026, 11, 14)

    /** Октябрь: в чётные дни тренировка и оценка 8, в нечётные — без тренировки и 5. 5-го не заполнено. */
    private fun data(): Map<String, Map<String, String>> {
        val m = mutableMapOf<String, Map<String, String>>()
        for (i in 1..31) {
            if (i == 5) continue
            val tr = i % 2 == 0
            m[oct.withDayOfMonth(i).key()] = buildMap {
                put("rate", if (tr) "8" else "5")
                if (tr) put("tr", "1")
                if (i <= 10) put("work", "2")
                put("sleep", if (tr) "450" else "360")
                put("water", if (i % 3 == 0) "2.5" else "1")
                put("used", countsToString(mapOf("Кофе" to if (tr) 1 else 3))!!)
            }
        }
        // сентябрь: тренировка 5 раз
        for (i in 1..5) m[LocalDate.of(2026, 9, i * 2).key()] = mapOf("rate" to "6", "tr" to "1")
        return m
    }

    @Test fun heatmapLevels() {
        val d = MonthData(template, data(), oct, MonthCfg(), today)
        assertEquals(30, d.filledDays)
        val tr = d.heatRows.first { it.first.key == "tr" }.second
        assertEquals(2, tr[1])          // 2 октября — отмечено
        assertEquals(0, tr[0])          // 1 октября — не было
        assertNull(tr[4])               // 5 октября — пропуск
        val work = d.heatRows.first { it.first.key == "work" }.second
        assertEquals(3, work[0])        // «+» — много
        val water = d.heatRows.first { it.first.key == "water" }.second
        assertEquals(1, water[0])       // 1 л из 2 — чуть
        assertEquals(2, water[2])       // 2,5 л — норма
        assertEquals("rate", d.heatRows.first().first.key)
        assertTrue(d.heatRows.any { it.first.key == "used:Кофе" })
    }

    @Test fun factorsFindTraining() {
        val d = MonthData(template, data(), oct, MonthCfg(), today)
        val f = d.factors.first()
        assertTrue(f.reliable)
        assertEquals(3.0, Math.abs(f.effect), 1e-9)
        val tr = d.factors.first { it.title == "Tr" }
        assertEquals(3.0, tr.effect, 1e-9)
        assertEquals(15, tr.nWith)
        // кофе: обычно 3 чашки в дни без тренировки → фактор «Кофе ≥ 3» с отрицательным эффектом
        val coffee = d.factors.first { it.title.startsWith("Кофе ≥") }
        assertTrue(coffee.effect < 0)
        // оценка сама себя не объясняет
        assertTrue(d.factors.none { it.title.startsWith("Оценка") })
    }

    @Test fun indexAndStreaks() {
        val cfg = MonthCfg(index = setOf("rate", "tr"), target = 70)
        val d = MonthData(template, data(), oct, cfg, today)
        // 2 октября: оценка 8 → (8−1)/9 = 0,78; тренировка-бонус отмечена → индекс 89
        assertEquals(88.9, d.index[1]!!, 0.1)
        // 1 октября: тренировка не отмечена — бонус не снижает, считается только оценка
        assertEquals(44.4, d.index[0]!!, 0.1)
        assertNull(d.index[4])
        val s = d.indexStats
        // 4 и 6 октября подряд: незаполненное 5-е серию не рвёт
        assertEquals(2, s.bestStreak)
        assertEquals(15, s.above)
        assertNotNull(s.prevAvg)
        assertEquals(listOf(3..5), d.streaks)
        // «каждый день» — без отметки ноль
        val daily = MonthData(template, data(), oct, cfg.copy(daily = setOf("tr")), today)
        assertEquals(22.2, daily.index[0]!!, 0.1)
    }

    @Test fun partialDayDoesNotCount() {
        val nov = LocalDate.of(2026, 11, 1)
        val cfg = MonthCfg(index = setOf("rate", "water", "sleep"))
        val e = data() + (today.key() to mapOf("sleep" to "400"))
        val d = MonthData(template, e, nov, cfg, today)
        val di = d.dayIndex(today)
        assertTrue(di.partial); assertTrue(!di.counted)
        assertEquals(listOf("rate", "water"), di.missing.map { it.key })
        assertNull(d.indexOn(today))
        assertEquals(1, d.indexStats.partial)
    }

    @Test fun weeklyGoal() {
        val d = MonthData(template, data(), oct, MonthCfg(weekly = mapOf("tr" to 3)), today)
        val r = d.goalRows.first { it.metric.key == "tr" }
        assertTrue(r.weeks)
        assertEquals(4, r.days); assertEquals(5, r.of)
        assertTrue(d.isBonus(d.metrics.first { it.key == "tr" }))
    }

    @Test fun recordStreakUsesFreezes() {
        // 3–9 заполнены (7 дней → 1 заморозка), 10-е пропущено, 11–14 заполнены
        val e = (3..14).filter { it != 10 }.associate { LocalDate.of(2026, 11, it).key() to mapOf("rate" to "6") }
        val d = MonthData(template, e, LocalDate.of(2026, 11, 1), MonthCfg(), today)
        assertEquals(11 to true, d.recordStreak(today))
        assertEquals(0, d.streak(today).freezes)
        // сегодня ещё не заполнено — серия не рвётся
        val d2 = MonthData(template, e - today.key(), LocalDate.of(2026, 11, 1), MonthCfg(), today)
        assertEquals(10, d2.recordStreak(today).first)
    }

    @Test fun goalsCompareMonths() {
        val d = MonthData(template, data(), oct, MonthCfg(), today)
        val tr = d.goalRows.first { it.metric.key == "tr" }
        assertEquals(15, tr.days); assertEquals(30, tr.of); assertEquals(5, tr.prevDays); assertEquals(50, tr.pct)
        val sleep = d.goalRows.first { it.metric.key == "sleep" }
        assertEquals(15, sleep.days)    // ≥ 7 ч только в дни с тренировкой
    }

    @Test fun customGoalAndCfgRoundtrip() {
        val cfg = MonthCfg(index = setOf("rate"), goals = mapOf("sleep" to 360.0), target = 60, outcome = "index")
        val back = MonthCfg.parse(cfg.toJson())
        assertEquals(cfg, back)
        val d = MonthData(template, data(), oct, back, today)
        assertEquals(30, d.goalRows.first { it.metric.key == "sleep" }.days)
        assertTrue(d.outcomeIsIndex)
        assertEquals(MonthCfg(), MonthCfg.parse("garbage"))
    }

    @Test fun futureDaysAreEmpty() {
        val nov = LocalDate.of(2026, 11, 1)
        val d = MonthData(template, data() + (today.key() to mapOf("rate" to "7")), nov, MonthCfg(), today)
        assertEquals(1, d.filledDays)
        assertNull(d.value(d.metrics.first(), today.plusDays(1)))
    }

    @Test fun heatRowsFilter() {
        val cfg = MonthCfg(heat = setOf("tr", "used:Кофе", "nc"))
        val d = MonthData(template, data(), oct, MonthCfg.parse(cfg.toJson()), today)
        assertEquals(listOf("tr", "used:Кофе"), d.heatRows.map { it.first.key }) // NC без данных — не показывается
        assertEquals(setOf("Отметки дня", "Что использовалось", "Показатели"), d.metrics.map { it.group }.toSet())
    }
}
