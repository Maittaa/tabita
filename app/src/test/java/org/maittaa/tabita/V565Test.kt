package org.maittaa.tabita

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/** 5.65: быстрая отдача — строки «что значат данные» и воскресный обзор недели. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1600dp-xxhdpi")
class V565Test {
    init { Prefs(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()).lang = "ru" }
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun save(name: String) {
        rule.waitForIdle()
        val v = rule.activity.window.decorView
        val b = android.graphics.Bitmap.createBitmap(v.width, v.height, android.graphics.Bitmap.Config.ARGB_8888).also { v.draw(android.graphics.Canvas(it)) }
        val out = File(System.getProperty("user.dir"), "../shots").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun insightsAndWeek() {
        val act = rule.activity; val repo = act.repo; val today = LocalDate.now()
        val tmpl = repo.template.value
        val sleep = tmpl.first { it.source == Source.HC_SLEEP }
        val rhr = tmpl.first { it.source == Source.HC_RESTING_HR }
        val steps = tmpl.first { it.source == Source.HC_STEPS }
        val data = (1..20).associate { i ->
            today.minusDays(i.toLong()).key() to mapOf(sleep.id to "450", rhr.id to "58", steps.id to "8000")
        } + (today.key() to mapOf(sleep.id to "360", rhr.id to "65", steps.id to "3000"))
        rule.runOnUiThread { repo.setValues(data) }
        rule.waitForIdle()
        val ent = repo.entries.value
        // утро: пульс и сон, шагов ещё нет
        val morning = DayInsight.lines(repo.template.value, ent, today, today.atTime(9, 0)).map { it.replace('\u00A0', ' ') }
        assertEquals(2, morning.size)
        assertTrue(morning[0], morning[0].contains("Пульс покоя 65") && morning[0].contains("на 7 выше"))
        assertTrue(morning[1], morning[1].contains("Сон 6 ч") && morning[1].contains("на 1 ч 30 мин меньше"))
        // без сигналов пульса и сна — вечером шаги, плюс находка
        val calm = ent + (today.key() to mapOf(sleep.id to "440", rhr.id to "59", steps.id to "3000"))
        val eve = DayInsight.lines(repo.template.value, calm, today, today.atTime(20, 0), finding = "Находка: тест")
        assertTrue(eve[0], eve[0].contains("Шагов пока 3") && eve[0].contains("8") )
        assertEquals("Находка: тест", eve[1])
        assertTrue(DayInsight.lines(repo.template.value, calm, today, today.atTime(9, 0)).isEmpty())
        // мало истории — молчим
        assertTrue(DayInsight.lines(repo.template.value, mapOf(today.key() to mapOf(rhr.id to "80")), today, today.atTime(9, 0)).isEmpty())

        // находка: ненадёжная или слабая не выводится
        assertNull(DayInsight.factorLine(MonthData.Factor("Прогулка", 12.0, 2, 10)))
        assertNull(DayInsight.factorLine(MonthData.Factor("Прогулка", 3.0, 6, 6)))
        assertTrue(DayInsight.factorLine(MonthData.Factor("Прогулка", 8.4, 6, 7))!!.contains("на 8 выше"))

        // обзор недели
        val r = WeekReview.of(repo, act.prefs, today.with(java.time.DayOfWeek.MONDAY), emptyList(), today)
        val txt = DayInsight.weekText(r.copy(sleep = 400.0, sleepNights = 5, weight = 70.4, weightPrev = 70.0, active = 90, activeGoal = 150,
            fixes = listOf("Сон в среднем 6 ч 40 мин — ложитесь на 30 мин раньше")), "Находка: тест")!!.replace("\u00A0", " ")
        assertTrue(txt, txt.startsWith("Сон в среднем 6 ч 40 мин, активных минут 90 из 150, вес +0,4 кг за неделю."))
        assertTrue(txt, txt.contains("На неделю: сон в среднем"))
        assertTrue(txt.endsWith("Находка: тест"))
        assertNull(DayInsight.weekText(r.copy(sleep = null, sleepNights = 0, weight = null, active = null, protein = null, fixes = emptyList()), null))
        assertNotNull(Reminders.weekText(repo, act.prefs, today))

        // воскресенье 19:00
        val sat = LocalDateTime.of(2026, 11, 28, 10, 0)
        assertEquals(LocalDateTime.of(2026, 11, 29, 19, 0), DayInsight.nextSunday(sat))
        assertEquals(LocalDateTime.of(2026, 12, 6, 19, 0), DayInsight.nextSunday(LocalDateTime.of(2026, 11, 29, 19, 30)))
        assertTrue(act.prefs.weeklyOn)

        // то, что уже говорят «Советы дня», не повторяем; похвала остаётся
        assertTrue(DayInsight.lines(repo.template.value, ent, today, today.atTime(9, 0), covered = setOf("sleep", "move")).isEmpty())
        val good = ent + (today.key() to mapOf(sleep.id to "540", rhr.id to "65"))
        assertTrue(DayInsight.lines(repo.template.value, good, today, today.atTime(9, 0), covered = setOf("sleep"))[0].contains("больше обычного"))
        assertEquals(setOf("sleep", "move"), DayInsight.coveredByTips(repo.template.value, act.prefs))

        // на экране: сон дольше обычного
        rule.runOnUiThread { repo.setValues(mapOf(today.key() to mapOf(sleep.id to "540", rhr.id to "58", steps.id to "3000"))) }
        rule.waitForIdle()
        rule.onNodeWithTag("insight_0").assertExists()
        save("65_insights")
        assertTrue(Changelog.all.any { it.version == "5.65" })
    }
}
