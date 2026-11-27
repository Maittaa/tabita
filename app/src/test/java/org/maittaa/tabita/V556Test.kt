package org.maittaa.tabita

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.DayOfWeek
import java.time.LocalDate

/** 5.56: «Программа» — Цель · Неделя, шаблон нагрузки, реальный расход, прогноз, обзор недели. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1600dp-xxhdpi")
class V556Test {
    init { Prefs(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()).lang = "ru" }
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @After fun reset() { WeekPlan.strengthDays = null; rule.activity.prefs.apply { weekTpl = null; burnAdj = 0; burnAdjAt = null; planDue = null; planGoal = "" } }

    private fun save(name: String) {
        rule.waitForIdle()
        val v = rule.activity.window.decorView
        val b = android.graphics.Bitmap.createBitmap(v.width, v.height, android.graphics.Bitmap.Config.ARGB_8888).also { v.draw(android.graphics.Canvas(it)) }
        val out = java.io.File(System.getProperty("user.dir"), "../shots").apply { mkdirs() }
        java.io.File(out, "$name.png").outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun tab(name: String) {
        rule.onAllNodes(hasText(name) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))[0].performClick()
        rule.waitForIdle()
    }

    /** 28 дней: 2800 ккал в день, вес растёт на 0,25 кг в неделю от 75 кг. */
    private fun fill(today: LocalDate = LocalDate.now(), kcal: Double = 2800.0) {
        val repo = rule.activity.repo
        rule.runOnUiThread {
            repo.setProfile(Profile(180.0, 1995, "m"))
            val vals = mutableMapOf<String, Map<String, String>>()
            for (i in 0..28) {
                val d = today.minusDays(i.toLong())
                val w = 75.0 + 0.25 / 7 * (28 - i)
                vals[d.key()] = mapOf("weight" to String.format(java.util.Locale.US, "%.4f", w),
                    "food" to mealsToString(listOf(Meal(kind = "Обед", text = "x", kcal = kcal, protein = 150.0, fat = 80.0, carbs = 350.0)))!!)
            }
            repo.setValues(vals)
        }
    }

    @Test fun trendFit() {
        val tr = Trend.fit((0..20).map { it to 70.0 + 0.1 * it })!!
        assertEquals(0.1, tr.perDay, 1e-9)
        assertEquals(72.0, tr.now, 1e-9)
        assertEquals(20, tr.spanDays)
        assertNull(Trend.fit(listOf(1 to 70.0)))
        // прогноз: до 77 кг при +0,25 кг/нед от 75 — 8 недель
        val t2 = Trend(75.0, 0.25 / 7, 10, 28)
        val today = LocalDate.of(2026, 11, 25)
        val e = Forecast.etas(listOf("77" to 77.0, "74" to 74.0), t2, today)
        assertEquals(today.plusDays(56), e[0].date)
        assertNull(e[1].date)                       // в другую сторону — не приближается
        val need = Forecast.need(77.0, today.plusWeeks(4), t2, today)!!
        assertEquals(0.5, need.perWeek, 1e-9)
        assertEquals(280, need.kcalDelta)           // (0,5 − 0,25) × 7700 / 7 = 275 → 280
        assertNull(Forecast.need(77.0, today.plusDays(3), t2, today))
    }

    @Test fun templateAndWarnings() {
        val tpl = WeekTpl.DEFAULT.toggle(DayOfWeek.MONDAY, WeekTpl.CARDIO)
        val back = WeekTpl.parse(tpl.toJson())!!
        assertEquals(tpl, back)
        assertTrue(WeekTpl.CARDIO in back.on(DayOfWeek.MONDAY))
        assertNull(WeekTpl.parse("{\"d\":[\"C\"]}"))
        val s = listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        assertEquals(setOf(WeekTpl.STRENGTH, WeekTpl.CARDIO), WeekPlan.kinds(back, DayOfWeek.MONDAY, s))
        assertEquals(3, WeekPlan.planned(back, WeekTpl.STRENGTH, s))
        assertEquals(3, WeekPlan.planned(back, WeekTpl.CARDIO, s))
        assertTrue(WeekPlan.warnings(back, s).isNotEmpty())                // силовая и аэробная в понедельник
        assertTrue(WeekPlan.warnings(WeekTpl.DEFAULT, s).isEmpty() || WeekPlan.warnings(WeekTpl.DEFAULT, s).size == 1)
        assertTrue(WeekPlan.warnings(WeekTpl.DEFAULT, listOf(DayOfWeek.MONDAY)).size >= 1)  // одна силовая
    }

    @Test fun realExpenditure() {
        val repo = rule.activity.repo; val prefs = rule.activity.prefs
        fill()
        val r = Adapt.estimate(repo)
        assertTrue(r.missing, r.ok)
        assertEquals(28, r.intakeDays)
        // расход = 2800 − 0,25/7 × 7700 = 2525
        assertEquals(2525.0, r.tdee!!, 1.0)
        val off = r.offset!!
        assertEquals((Math.round((2525.0 - r.model!!) / 10) * 10).toInt().coerceIn(-800, 800), off)
        // норма программы учитывает принятую поправку
        val before = PlanStats.of(repo, prefs).burn!!
        Adapt.accept(prefs, off)
        assertEquals(before + off, PlanStats.of(repo, prefs).burn!!, 0.01)
        assertNull(Adapt.suggestion(r, off))
        Adapt.accept(prefs, 0)
        assertNull(prefs.burnAdjAt)
    }

    @Test fun notEnoughData() {
        val repo = rule.activity.repo
        rule.runOnUiThread {
            repo.setValues(mapOf(LocalDate.now().minusDays(1).key() to mapOf("weight" to "75")))
        }
        val r = Adapt.estimate(repo)
        assertFalse(r.ok); assertNotNull(r.missing); assertNull(r.offset)
    }

    @Test fun weekFixes() {
        val start = LocalDate.of(2026, 11, 16)
        val base = WeekReview.Review(start, 7, 75.0, 75.0, Trend(75.0, 0.0, 10, 28), 2, 6, 135.0, 2600.0, 2700.0, 6,
            WeekReview.Count(2, 3), WeekReview.Count(1, 2), WeekReview.Count(0, 2), 90, true, 150, 400.0, 6, 75, emptyList())
        val f = WeekReview.fixes(base, Goal.MUSCLE, 75.0)
        assertEquals(2, f.size)
        assertTrue(f[0], f[0].contains("0"))         // вес не растёт — первая правка
        assertTrue(f[1], f[1].contains("2") && f[1].contains("6"))     // белок
        assertTrue(WeekReview.fixes(base.copy(trend = Trend(75.0, 0.3 / 7, 10, 28), proteinOk = 6, strength = WeekReview.Count(3, 3),
            active = 200, sleep = 450.0, bedSpread = 20), Goal.MUSCLE, 75.0).isEmpty())
        // разброс отбоя
        val z = java.time.ZoneId.systemDefault()
        fun at(d: Int, h: Int, m: Int) = LocalDate.of(2026, 11, d).atTime(h, m).atZone(z).toInstant().toEpochMilli()
        assertEquals(0, WeekReview.bedSpread(listOf(at(1, 23, 0), at(2, 23, 0), at(3, 23, 0))))
        assertTrue(WeekReview.bedSpread(listOf(at(1, 23, 30), at(3, 0, 30), at(3, 23, 30), at(5, 0, 30)))!! in 25..35)
    }

    @Test fun dayPlanFromTemplate() {
        val repo = rule.activity.repo; val prefs = rule.activity.prefs.apply { planGoal = ""; rhythmSrc = RhythmSrc.ALL }
        val mon = LocalDate.of(2026, 11, 23)
        WeekPlan.strengthDays = { emptyList() }
        prefs.weekTpl = WeekTpl(List(7) { if (it == 0) setOf(WeekTpl.CARDIO, WeekTpl.YOGA) else emptySet() }).toJson()
        val r = DayRhythm(7 * 60, 23 * 60, 0, null, 0)
        val info = DayPlan.of(repo, prefs, mon, r, null)
        val cardio = info.events.single { it.kind == DayPlan.Kind.CARDIO }
        assertFalse(cardio.done); assertEquals(30, cardio.len)
        val yoga = info.events.single { it.kind == DayPlan.Kind.YOGA }
        assertTrue(yoga.at in 21 * 60..22 * 60)
        // с силовой в этот день — аэробная после неё
        val withS = DayPlan.of(repo, prefs, mon, r, DayPlan.TrainPlan("А", 40, gym = false))
        val s = withS.events.single { it.kind == DayPlan.Kind.TRAIN }
        assertTrue(withS.events.single { it.kind == DayPlan.Kind.CARDIO }.at >= s.at + 40)
        // вторник — ничего
        assertTrue(DayPlan.of(repo, prefs, mon.plusDays(1), r, null).events.none { it.kind == DayPlan.Kind.YOGA || it.kind == DayPlan.Kind.CARDIO })
    }

    @Test fun programTabs() {
        val prefs = rule.activity.prefs
        rule.runOnUiThread { rule.activity.train.discard() }
        prefs.planGoal = "muscle"; prefs.planW0 = 75.0
        WeekPlan.strengthDays = { listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY) }
        fill()
        rule.onAllNodesWithText("Программа").let { it[it.fetchSemanticsNodes().size - 1] }.performClick()
        rule.waitForIdle()
        tab("Цель")
        rule.onNodeWithText("Прогноз").performScrollTo()
        save("56_goal")
        rule.onNodeWithText("Неделя тренировок").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("wk_C_3").performScrollTo().performClick()
        rule.waitForIdle()
        assertTrue(WeekTpl.CARDIO in WeekTpl.of(prefs).on(DayOfWeek.WEDNESDAY))
        save("56_template")
        tab("Неделя")
        rule.onNodeWithText("Обзор").assertExists()
        rule.onNodeWithTag("kcal_accept").performScrollTo()
        save("56_week")
        val expected = Adapt.suggestion(Adapt.estimate(rule.activity.repo), 0)!!
        rule.onNodeWithTag("kcal_accept").performClick()
        rule.waitForIdle()
        assertEquals(expected, prefs.burnAdj)
        assertNotEquals(0, prefs.burnAdj)
        rule.onNodeWithTag("wk_prev").performClick()
        rule.onNodeWithText("Прошлая неделя").assertExists()
        assertTrue(Changelog.all.any { it.version == "5.56" })
    }
}
