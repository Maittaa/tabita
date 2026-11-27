package org.maittaa.tabita

import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.maittaa.tabita.train.SEC_HUB
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 5.61: «Что использовалось» — настройки вариантов (раздел, каждый день); главная «Здоровья» — сетка. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1600dp-xxhdpi")
class V561Test {
    init { Prefs(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()).lang = "ru" }
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun save(name: String) {
        rule.waitForIdle()
        val v = rule.activity.window.decorView
        val b = android.graphics.Bitmap.createBitmap(v.width, v.height, android.graphics.Bitmap.Config.ARGB_8888).also { v.draw(android.graphics.Canvas(it)) }
        val out = File(System.getProperty("user.dir"), "../shots").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val used = TemplateItem(id = "used", name = "Что использовалось", type = ItemType.MULTI, options = listOf("Кофе", "Магний", "Витамин D", "Протеин"),
        optMeta = mapOf("Магний" to OptMeta("Добавки", true), "Витамин D" to OptMeta("Добавки", true), "Протеин" to OptMeta("Добавки", false)))

    @Test fun optionsMeta() {
        val back = TemplateItem.fromJson(used.toJson())
        assertEquals(used.optMeta, back.optMeta)
        // настройки удалённого варианта не сохраняются
        assertTrue(TemplateItem.fromJson(used.copy(options = listOf("Кофе")).toJson()).optMeta.isEmpty())
        assertEquals(listOf("Магний", "Витамин D"), used.dailyMissing(null))
        assertEquals(listOf("Витамин D"), used.dailyMissing(countsToString(mapOf("Магний" to 1, "Кофе" to 2))))
        assertTrue(used.dailyMissing(countsToString(mapOf("Магний" to 1, "Витамин D" to 1))).isEmpty())
    }

    @Test fun tileSections() {
        val act = rule.activity
        rule.activity.runOnUiThread {
            act.setContent { AppTheme { androidx.compose.foundation.layout.Column { ItemEditor(used, countsToString(mapOf("Кофе" to 2, "Магний" to 1)), {}, sections = true) {} } } }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("osec_Добавки").assertExists()
        rule.onNodeWithTag("daily_missing").assertExists()
        rule.onNodeWithText("Каждый день, ещё не отмечено: Витамин D").assertExists()
        save("61_used")
        rule.activity.runOnUiThread {
            act.setContent { AppTheme { androidx.compose.foundation.layout.Column { ItemEditor(used, null, {}, sections = false) {} } } }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("osec_Добавки").assertDoesNotExist()
    }

    @Test fun hubGrid() {
        val act = rule.activity
        rule.runOnUiThread { act.train.discard(); act.train.saveProgram(null); act.train.section = SEC_HUB }
        rule.onAllNodesWithText("Здоровье").let { it[it.fetchSemanticsNodes().size - 1] }.performClick()
        rule.waitForIdle()
        listOf("hub_strength", "hub_cardio", "hub_yoga", "hub_activity", "hub_sleep", "hub_body", "hub_wb").forEach { rule.onNodeWithTag(it).assertExists() }
        // плитки одной строки — одной высоты
        val a = rule.onNodeWithTag("hub_activity").fetchSemanticsNode().boundsInRoot
        val s = rule.onNodeWithTag("hub_sleep").fetchSemanticsNode().boundsInRoot
        assertEquals(a.top, s.top, 1f); assertEquals(a.height, s.height, 1f)
        save("61_hub")
        assertTrue(Changelog.all.any { it.version == "5.61" })
    }
}
