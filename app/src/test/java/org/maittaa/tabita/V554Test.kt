package org.maittaa.tabita

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.maittaa.tabita.train.Intensity
import org.maittaa.tabita.train.LoadKind
import org.maittaa.tabita.train.LoadPlan
import org.maittaa.tabita.train.SEC_ACTIVITY
import org.maittaa.tabita.train.SEC_HUB
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** 5.54: активные минуты за день по пульсу, «Активность», «Состояние сегодня», объяснение веса. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1300dp-xxhdpi")
class V554Test {
    init { Prefs(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()).lang = "ru" }
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @After fun reset() { CardioData.override = null }

    private fun save(name: String) {
        rule.waitForIdle()
        val v = rule.activity.window.decorView
        val b = android.graphics.Bitmap.createBitmap(v.width, v.height, android.graphics.Bitmap.Config.ARGB_8888).also { v.draw(android.graphics.Canvas(it)) }
        val out = java.io.File(System.getProperty("user.dir"), "../shots").apply { mkdirs() }
        java.io.File(out, "$name.png").outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun activeMinutesModel() {
        val z = HrZones.Zones(190, 60, null, true)
        // 20 мин на 115 (умеренная), 10 на 145 (высокая), 2 на 175 (пиковая), 60 мин на 80 (лёгкая)
        val hist = mapOf(115 to 1200, 145 to 600, 175 to 120, 80 to 3600)
        val d = ActiveMin.from(hist, z)
        assertEquals(ActiveMin.Day(20, 10, 2), d)
        assertEquals(20 + 2 * 12, d.active)
        assertEquals(d, ActiveMin.parse(d.toString()))
        assertNull(ActiveMin.parse("1,2"))
        val mon = LocalDate.of(2026, 11, 23)
        val e = mapOf(mon.key() to mapOf(ActiveMin.KEY to "20,10,2"), mon.plusDays(1).key() to mapOf(ActiveMin.KEY to "30,0,0"))
        assertEquals(44 + 30, ActiveMin.weekTotal(e, mon))
        assertNull(ActiveMin.weekTotal(e, mon.minusWeeks(1)))
    }

    @Test fun loadNoteWhenOnlyFirstSetAtTop() {
        val p = LoadPlan.pick(LoadKind.DUMBBELLS, listOf(4.2, 6.0), "2 × 4,2 кг", 15, false, 10, false, Intensity.NORMAL, false, null, 15)
        assertEquals(4.2, p.kg!!, 0.001)
        assertTrue(p.note!!.contains("Первый подход"))
        assertTrue(p.note!!.contains("6 кг"))
        // все подходы на верхней — следующий вес, как раньше
        assertEquals(6.0, LoadPlan.pick(LoadKind.DUMBBELLS, listOf(4.2, 6.0), "2 × 4,2 кг", 15, true, 10, false, Intensity.NORMAL, false, null, 15).kg!!, 0.001)
    }

    @Test fun hubStateAndActivity() {
        val repo = rule.activity.repo
        val today = LocalDate.now()
        val start = Cardio.weekStart(today)
        rule.runOnUiThread {
            rule.activity.train.discard(); rule.activity.train.saveProgram(null); rule.activity.train.section = SEC_HUB
            val tmpl = repo.template.value
            val rhr = tmpl.first { it.source == Source.HC_RESTING_HR }.id
            val sleep = tmpl.first { it.source == Source.HC_SLEEP }.id
            val steps = tmpl.first { it.source == Source.HC_STEPS }.id
            val vals = mutableMapOf<String, Map<String, String>>()
            (1..20).forEach { vals[today.minusDays(it.toLong()).key()] = mapOf(rhr to "58", steps to "${8000 + it * 300}") }
            vals[today.key()] = mapOf(rhr to "63", sleep to "395", steps to "9100", ActiveMin.KEY to "25,6,0")
            var d = start
            while (d.isBefore(today)) { vals[d.key()] = (vals[d.key()] ?: emptyMap()) + (ActiveMin.KEY to "18,4,1"); d = d.plusDays(1) }
            repo.setValues(vals)
        }
        CardioData.override = CardioData(true, true, true, true)
        rule.onAllNodesWithText("Здоровье").let { it[it.fetchSemanticsNodes().size - 1] }.performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("hub_state").assertExists()
        rule.onNodeWithText("Сон: 6 ч 35 мин — меньше 7 часов").assertExists()
        rule.onNodeWithText("Пульс покоя: 63 — на 5 выше обычного").assertExists()
        rule.onNodeWithTag("hub_activity").assertExists()
        save("54_hub")
        rule.onNodeWithTag("hub_activity").performClick()
        rule.waitForIdle()
        assertEquals(SEC_ACTIVITY, rule.activity.train.section)
        rule.onNodeWithTag("activity_week").assertExists()
        val days = java.time.temporal.ChronoUnit.DAYS.between(start, today).toInt()
        val expected = days * (18 + 2 * 5) + (25 + 12)
        rule.onNodeWithText("$expected").assertExists()
        save("54_activity")
        assertTrue(Changelog.all.any { it.version == "5.54" })
    }
}
