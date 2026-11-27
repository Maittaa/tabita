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

/** 5.64: меньше ручного ввода — «Как вчера» и «само заполнилось». */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1600dp-xxhdpi")
class V564Test {
    init { Prefs(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()).lang = "ru" }
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun save(name: String) {
        rule.waitForIdle()
        val v = rule.activity.window.decorView
        val b = android.graphics.Bitmap.createBitmap(v.width, v.height, android.graphics.Bitmap.Config.ARGB_8888).also { v.draw(android.graphics.Canvas(it)) }
        val out = File(System.getProperty("user.dir"), "../shots").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun copyYesterday() {
        val act = rule.activity; val repo = act.repo; val today = LocalDate.now()
        val tmpl = repo.template.value
        val check = tmpl.first { it.type == ItemType.CHECK && it.source == Source.NONE && !it.hidden }
        val multi = tmpl.first { it.type == ItemType.MULTI && !it.hidden }
        val steps = tmpl.first { it.source == Source.HC_STEPS }
        val opt = multi.options.first()
        rule.runOnUiThread {
            repo.setValues(mapOf(
                today.minusDays(1).key() to mapOf(check.id to "1", multi.id to countsToString(mapOf(opt to 2))!!),
                today.key() to mapOf(steps.id to "8000"),
            ))
        }
        val c = DayCopy.fromYesterday(repo.template.value, repo.entries.value, today)
        assertEquals(setOf(check.id, multi.id), c.map { it.item.id }.toSet())
        assertTrue(c.first { it.item.id == multi.id }.text.contains("×2"))
        assertEquals(1, DayCopy.automatic(repo.template.value, repo.entries.value[today.key()]!!))
        rule.waitForIdle()
        rule.onNodeWithTag("auto_filled").assertExists()
        save("64_progress")
        rule.onNodeWithTag("copy_yday").performClick()
        rule.waitForIdle()
        // снять список — перенесётся только отметка
        rule.onNodeWithTag("copy_${multi.id}").performClick()
        rule.onNodeWithTag("copy_ok").performClick()
        rule.waitForIdle()
        assertEquals("1", repo.entries.value[today.key()]!![check.id])
        assertNull(repo.entries.value[today.key()]!![multi.id])
        // уже заполненное — не предлагается
        assertTrue(DayCopy.fromYesterday(repo.template.value, repo.entries.value, today).none { it.item.id == check.id })
        assertTrue(Changelog.all.any { it.version == "5.64" })
    }
}
