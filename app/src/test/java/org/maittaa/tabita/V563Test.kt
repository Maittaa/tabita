package org.maittaa.tabita

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.After
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

/** 5.63: «Сравнить» — два показателя на одном графике, прошлый период, связь. 5.62.1 — срок эксперимента переносится. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1600dp-xxhdpi")
class V563Test {
    init { Prefs(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()).lang = "ru" }
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @After fun reset() { rule.activity.prefs.apply { cmpA = null; cmpB = null; cmpPrev = false } }

    private fun save(name: String) {
        rule.waitForIdle()
        val v = rule.activity.window.decorView
        val b = android.graphics.Bitmap.createBitmap(v.width, v.height, android.graphics.Bitmap.Config.ARGB_8888).also { v.draw(android.graphics.Canvas(it)) }
        val out = File(System.getProperty("user.dir"), "../shots").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun pearson() {
        val a = (1..10).map { it.toDouble() }
        assertEquals(1.0, Compare.pearson(a, a.map { 2 * it + 1 })!!.first, 1e-9)
        assertEquals(-1.0, Compare.pearson(a, a.map { -it })!!.first, 1e-9)
        assertNull(Compare.pearson(a.take(6), a.take(6)))                         // меньше 7 пар
        assertNull(Compare.pearson(a, a.map { 5.0 }))                             // нет разброса
        assertEquals(9, Compare.pearson(a + null, a.map<Double, Double?> { it } + 3.0)!!.second.let { it - 1 })
        assertEquals("сильная", Compare.strength(-0.6)); assertEquals("средняя", Compare.strength(0.35))
        assertEquals("слабая", Compare.strength(0.15)); assertEquals("нет связи", Compare.strength(0.05))
    }

    @Test fun compareCard() {
        val act = rule.activity; val repo = act.repo; val today = LocalDate.now()
        val sleep = repo.template.value.first { it.source == Source.HC_SLEEP }
        val rhr = repo.template.value.first { it.source == Source.HC_RESTING_HR }
        rule.runOnUiThread {
            repo.setValues((0 until 60).associate { i ->
                val d = today.minusDays(i.toLong()); val s = 380 + (i * 37) % 90
                d.key() to mapOf(sleep.id to s.toString(), rhr.id to (75 - s / 10).toString())
            })
        }
        rule.onAllNodesWithText("Программа").let { it[it.fetchSemanticsNodes().size - 1] }.performClick()
        rule.onAllNodes(hasText("Графики") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))[0].performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("cmp_card").assertExists()
        rule.onNodeWithTag("cmp_chart").assertExists()
        rule.onNodeWithTag("cmp_prev").performClick()
        assertTrue(act.prefs.cmpPrev)
        save("63_compare")
        assertTrue(Changelog.all.any { it.version == "5.63" })
    }
}
