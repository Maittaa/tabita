package org.maittaa.tabita

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
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

/** 5.67: мягкое возвращение, «N из 7», вехи 14/30/66. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h900dp-xxhdpi")
class V567Test {
    init { Prefs(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()).lang = "ru" }
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun save(name: String) {
        rule.waitForIdle()
        val v = rule.activity.window.decorView
        val b = android.graphics.Bitmap.createBitmap(v.width, v.height, android.graphics.Bitmap.Config.ARGB_8888).also { v.draw(android.graphics.Canvas(it)) }
        val out = File(System.getProperty("user.dir"), "../shots").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun returnAndMilestones() {
        val act = rule.activity; val repo = act.repo; val today = LocalDate.now()
        val tmpl = repo.template.value
        val item = tmpl.first { it.type == ItemType.SCALE && it.source == Source.NONE && !it.hidden }
        fun ent(vararg back: IntRange) = back.flatMap { it.toList() }.associate { today.minusDays(it.toLong()).key() to mapOf(item.id to "6") }
        fun st(e: Map<String, Map<String, String>>) = MonthData(tmpl, e, today, act.prefs.monthCfg, today).streak(today)

        // вехи
        assertNull(Milestones.due(13, 0))
        assertEquals(14, Milestones.due(16, 0))
        assertNull(Milestones.due(16, 14))
        assertEquals(30, Milestones.due(40, 0)) // пропущенная 14 не показывается после 30
        assertEquals(66, Milestones.due(66, 30))

        // перерыв 5 дней после 4 записей: серия прервалась, заморозок нет
        st(ent(6..9)).let { assertTrue(it.broken); assertEquals(5, it.gap(today)); assertEquals(4, it.total); assertEquals(1, it.week) }
        st(ent(1..3)).let { assertEquals(3, it.week); assertEquals(0, it.gap(today)) }

        rule.runOnUiThread { repo.setValues(ent(6..9)) }
        rule.waitForIdle()
        rule.onNodeWithTag("welcome_back", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("week7", useUnmergedTree = true).assertExists()
        save("67_back")

        // 14 заполненных дней → веха
        rule.runOnUiThread { repo.setValues(ent(1..14)) }
        rule.waitForIdle()
        rule.onNodeWithTag("milestone_14").assertExists()
        save("67_milestone")
        rule.onNodeWithTag("milestone_ok").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("milestone_14").assertDoesNotExist()
        assertEquals(14, act.prefs.milestoneSeen)
        assertTrue(Changelog.all.any { it.version == "5.67" })
    }
}
