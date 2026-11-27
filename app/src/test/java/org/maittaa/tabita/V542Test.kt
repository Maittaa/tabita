package org.maittaa.tabita

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.maittaa.tabita.train.Intensity
import org.maittaa.tabita.train.Inventory
import org.maittaa.tabita.train.LoadKind
import org.maittaa.tabita.train.LoadPlan
import org.maittaa.tabita.train.Place
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneId

/** 5.42: динамическая норма воды, личная потеря пота, инвентарь и подбор рабочего веса. */
@RunWith(RobolectricTestRunner::class)
class V542Test {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val z = ZoneId.systemDefault()
    private fun ms(d: LocalDate, h: Int, m: Int = 0) = d.atTime(h, m).atZone(z).toInstant().toEpochMilli()

    @Test fun waterNormFromWeightWorkoutSteps() {
        val repo = Repo(ctx); val prefs = Prefs(ctx); prefs.sweatLog = ""; prefs.waterAuto = true
        repo.setProfile(Profile(183.0, 1996, "m"))
        val d = LocalDate.now().toString()
        repo.setValue(d, "weight", "80")
        repo.setValue(d, Workouts.syncedKey("ex"), Workouts.toJson(listOf(WorkoutSession("strength", 60), WorkoutSession("walk", 30)))!!)
        repo.setValue(d, "steps", "12000")
        val c = Hydration.calc(repo, prefs, d, repo.entries.value[d]!!)!!
        assertEquals(2.4, c.base, 0.001)
        assertEquals(0.5, c.workoutLiters, 0.001)          // ходьба не в счёт — её дают шаги
        assertEquals(4000.0, c.stepsOver, 0.1)
        assertEquals(0.12, c.stepsLiters, 0.01)              // 0,5 ккал/кг/км × 80 × 3,04 км ≈ 122 ккал ≈ 0,12 л
        assertEquals(3.0, c.total, 0.001)
        Hydration.updateNorms(repo, prefs, listOf(d))
        assertEquals("3.0", repo.entries.value[d]!![Hydration.normKey("water")])
        val item = repo.template.value.first { it.type == ItemType.WATER }
        assertEquals(3.0, Hydration.normOf(item, repo.entries.value[d]), 0.001)
        // ручная норма — посчитанная сегодня убирается
        prefs.waterAuto = false
        Hydration.updateNorms(repo, prefs, listOf(d))
        assertNull(repo.entries.value[d]!![Hydration.normKey("water")])
        prefs.waterAuto = true
    }

    @Test fun monthUsesDayNorm() {
        val tmpl = listOf(waterTemplateItem())
        val d1 = LocalDate.of(2026, 10, 5); val d2 = LocalDate.of(2026, 10, 6)
        val e = mapOf(
            d1.toString() to mapOf("water" to "2.5", "water!n" to "3.0"),   // тренировка — норма выше, не выполнена
            d2.toString() to mapOf("water" to "2.5", "water!n" to "2.4"),
        )
        val m = MonthData(tmpl, e, LocalDate.of(2026, 10, 1), MonthCfg(), LocalDate.of(2026, 10, 31))
        val w = m.metrics.first { it.kind == MKind.WATER }
        assertFalse(m.met(w, 2.5, d1))
        assertTrue(m.met(w, 2.5, d2))
        assertEquals(2.5 / 3.0, m.score(w, 2.5, d1)!!, 0.001)
    }

    @Test fun sweatFromWeighIns() {
        val d = LocalDate.of(2026, 11, 20)
        val s = WorkoutSession("strength", 60, start = ms(d, 18))
        val ws = listOf(ms(d, 7) to 80.6, ms(d, 17, 40) to 80.0, ms(d, 19, 10) to 79.4)
        val r = Hydration.findSamples(ws, listOf(s, WorkoutSession("strength", 10, start = ms(d, 12))), z)
        assertEquals(1, r.size)
        assertEquals(0.6, r[0].lossKg, 0.001)
        assertEquals(0.6, r[0].rate, 0.001)
        // попил больше, чем потерял, — замера нет; взвешивание через 2 часа — тоже нет
        assertTrue(Hydration.findSamples(listOf(ms(d, 17, 40) to 80.0, ms(d, 19, 10) to 80.2), listOf(s), z).isEmpty())
        assertTrue(Hydration.findSamples(listOf(ms(d, 15) to 80.0, ms(d, 19, 10) to 79.4), listOf(s), z).isEmpty())
        val prefs = Prefs(ctx); prefs.sweatLog = ""
        assertEquals(Hydration.DEFAULT_SWEAT to false, Hydration.sweatRate(prefs))
        assertEquals(1, Hydration.addSamples(prefs, r))
        assertEquals(0, Hydration.addSamples(prefs, r))       // без дублей
        assertEquals(0.6 to true, Hydration.sweatRate(prefs))
        prefs.sweatLog = ""
    }

    @Test fun inventory() {
        val adj = Inventory(dbAdjustable = true, dbHandle = 2.0, dbPlates = listOf(2.5 to 4, 1.25 to 4))
        assertEquals(listOf(2.0, 4.5, 7.0, 9.5), adj.dumbbells())
        val fixed = Inventory(dbFixed = listOf(4.2))
        assertEquals(listOf(4.2), fixed.dumbbells())
        assertEquals(fixed, Inventory.fromJson(fixed.toJson()))
        assertEquals(adj, Inventory.fromJson(adj.toJson()))
        assertEquals(listOf(0.0, 1.5, 3.0, 4.5, 6.0), Inventory(bpStep = 1.5, bpMax = 6.0).backpack())
        assertEquals(listOf(20.0, 22.5, 25.0), LoadPlan.options(LoadKind.BARBELL, Place.GYM, Inventory()).take(3))
        assertTrue(LoadPlan.options(LoadKind.BARBELL, Place.HOME, Inventory()).isEmpty())
    }

    @Test fun loadPick() {
        assertEquals(4.2, LoadPlan.parseKg("2 × 4,2 кг")!!, 0.001)
        assertEquals(6.0, LoadPlan.parseKg("рюкзак 6 кг")!!, 0.001)
        assertNull(LoadPlan.parseKg("2 книги"))
        assertEquals("2 × 4,2 кг", LoadPlan.format(LoadKind.DUMBBELLS, 4.2))
        val db = LoadKind.DUMBBELLS
        fun pick(opts: List<Double>, last: String?, reps: Int?, top: Boolean, i: Intensity = Intensity.NORMAL, hold: Boolean = false) =
            LoadPlan.pick(db, opts, last, reps, top, 8, false, i, hold, null)
        // одна пара 4,2 кг: всё на верхней границе — тяжелее нет, подсказка усложнить
        pick(listOf(4.2), "2 × 4,2 кг", 12, true).let { assertEquals(4.2, it.kg!!, 0.001); assertFalse(it.resetReps); assertTrue(it.note!!.contains("Тяжелее нет")) }
        // есть тяжелее — следующий вес и повторы с нижней границы
        pick(listOf(4.2, 6.0, 8.0), "2 × 4,2 кг", 12, true).let { assertEquals(6.0, it.kg!!, 0.001); assertTrue(it.resetReps) }
        // придерживаем прибавку
        assertEquals(4.2, pick(listOf(4.2, 6.0), "2 × 4,2 кг", 12, true, hold = true).kg!!, 0.001)
        // тяжело (5 повт. при нижней 8) — легче по Эпли: 8 × (1 + 5/30) / (1 + 8/30) ≈ 7,4 → 6
        assertEquals(6.0, pick(listOf(4.2, 6.0, 8.0), "2 × 8 кг", 5, false).kg!!, 0.001)
        // облегчённая — на ~10 % легче, восстановительная — на шаг легче
        assertEquals(6.0, pick(listOf(4.2, 6.0, 8.0), "2 × 8 кг", 10, false, Intensity.LIGHT).kg!!, 0.001)
        assertEquals(6.0, pick(listOf(4.2, 6.0, 8.0), "2 × 8 кг", 10, false, Intensity.RECOVERY).kg!!, 0.001)
        // первый раз — самый лёгкий; от веса тела — ближайший не тяжелее
        assertEquals(4.2, pick(listOf(4.2, 6.0), null, null, false).kg!!, 0.001)
        assertEquals(30.0, LoadPlan.pick(LoadKind.BARBELL, LoadPlan.options(LoadKind.BARBELL, Place.GYM, Inventory()), null, null, false,
            8, false, Intensity.NORMAL, false, 32.4).kg!!, 0.001)
        // без весов — ничего не подсказываем
        assertNull(pick(emptyList(), null, null, false).kg)
        assertNotNull(Changelog.all.first().items.firstOrNull())
        assertTrue(Changelog.all.any { it.version == "5.42" })
    }
}
