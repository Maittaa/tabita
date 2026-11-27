package org.maittaa.tabita

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.maittaa.tabita.train.Draft
import org.maittaa.tabita.train.ExResult
import org.maittaa.tabita.train.Intensity
import org.maittaa.tabita.train.LoadKind
import org.maittaa.tabita.train.MuscleVolume
import org.maittaa.tabita.train.PlannedEx
import org.maittaa.tabita.train.Session
import org.maittaa.tabita.train.SetProgression
import org.maittaa.tabita.train.SetResult
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/** 5.55: запас повторов, подходы на мышцы, прибавка и откат подходов. */
@RunWith(RobolectricTestRunner::class)
class V555Test {
    init { Prefs(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()).lang = "ru" }
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun sess(id: String, date: LocalDate, vararg ex: ExResult, intensity: String = Intensity.NORMAL.title) =
        Session(id, "d1", "Тренировка A", date.toString(), date.toEpochDay() * 86_400_000, 1800, "1", "", "", ex.toList(), "", "", null, intensity)

    @Test fun reserveJson() {
        val r = ExResult("pushup", "Отжимания", false, null, listOf("12", "11"), false, listOf(3, 0))
        assertEquals(r, ExResult.fromJson(r.toJson()))
        // старые записи без запаса читаются как раньше
        val old = JSONObject(r.toJson().toString()).apply { remove("rir") }
        assertEquals(emptyList<Int>(), ExResult.fromJson(old).rirs)
        val sr = SetResult("10", "", true, 1)
        val d = Draft(org.maittaa.tabita.train.WorkoutPlan.fromJson(JSONObject().put("day", "d").put("title", "t").put("ex", org.json.JSONArray()).put("int", "NORMAL")
            .put("warmups", org.json.JSONArray()).put("rest", 60)), 0, sets = mapOf("0:0" to sr))
        assertEquals(sr, Draft.fromJson(d.toJson()).sets["0:0"])
    }

    @Test fun recommendByReserve() {
        val m = rule.activity.train
        val ex = PlannedEx("pushup", "Отжимания", 2, 8, 20, false, false, "", LoadKind.NONE)
        val today = LocalDate.now()
        fun withRir(vararg r: Int) = rule.runOnUiThread {
            m.replaceHistory(listOf(sess("1", today.minusDays(2), ExResult("pushup", "Отжимания", false, null, listOf("12", "12"), false, r.toList()))))
        }
        withRir(3, 0)
        assertEquals(14, m.recommend(ex, 0, Intensity.NORMAL).left)
        assertTrue(m.recommend(ex, 0, Intensity.NORMAL).reason.contains("запасом 3+"))
        assertEquals(12, m.recommend(ex, 1, Intensity.NORMAL).left)
        withRir(1, -1)
        assertEquals(13, m.recommend(ex, 0, Intensity.NORMAL).left)
        assertEquals(13, m.recommend(ex, 1, Intensity.NORMAL).left)
        rule.runOnUiThread { m.replaceHistory(emptyList()) }
    }

    @Test fun muscleVolumeAndSetProgression() {
        val mon = LocalDate.of(2026, 11, 23)
        val push = { v: List<String>, top: Boolean, load: String? -> ExResult("pushup", "Отжимания", false, load, v, top) }
        val squat = ExResult("squat", "Приседания", false, null, listOf("20", "20", "20"), true)
        val s1 = sess("1", mon, push(listOf("20", "20"), true, null), squat)
        val s2 = sess("2", mon.plusDays(2), push(listOf("20", "20"), true, null), squat)
        val history = listOf(s2, s1)
        val wk = MuscleVolume.week(history, mon)
        assertEquals(4, wk["грудь"]); assertEquals(6, wk["ноги"])
        // две тренировки подряд всё на верхней границе, веса нет — +1 подход
        val ch = SetProgression.evaluate(s2, history, emptyMap(), planned = { 2 }, heavierAvailable = { false }, today = mon.plusDays(2))
        assertEquals(setOf("pushup", "squat"), ch.filter { it.delta == 1 }.map { it.exId }.toSet())
        // есть тяжелее — прогрессия весом, подходы не трогаем
        assertTrue(SetProgression.evaluate(s2, history, emptyMap(), { 2 }, { true }, mon.plusDays(2)).isEmpty())
        // облегчённая тренировка — без изменений
        val light = sess("3", mon.plusDays(2), push(listOf("20", "20"), true, null), intensity = Intensity.LIGHT.title)
        assertTrue(SetProgression.evaluate(light, listOf(light, s1), emptyMap(), { 2 }, { false }, mon.plusDays(2)).isEmpty())
        // уже +2 — больше не прибавляем
        assertTrue(SetProgression.evaluate(s2, history, mapOf("pushup" to 2, "squat" to 2), { 2 }, { false }, mon.plusDays(2)).none { it.delta > 0 })
        // откат: первый подход упал на 2+
        val drop = sess("4", mon.plusDays(4), push(listOf("17", "15"), false, null))
        val back = SetProgression.evaluate(drop, listOf(drop, s2, s1), mapOf("pushup" to 1), { 2 }, { false }, mon.plusDays(4))
        assertEquals(-1, back.single().delta)
        assertEquals(emptyMap<String, Int>(), SetProgression.apply(mapOf("pushup" to 1), back))
        assertEquals(mapOf("pushup" to 1), SetProgression.parse(SetProgression.toJson(mapOf("pushup" to 1))))
        assertTrue(Changelog.all.any { it.version == "5.55" })
    }
}
