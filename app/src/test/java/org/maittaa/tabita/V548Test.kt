package org.maittaa.tabita

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** 5.48: быстрые действия в уменьшенных плитках. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1600dp-xxhdpi")
class V548Test {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun save(name: String) {
        rule.waitForIdle()
        val v = rule.activity.window.decorView
        val b = android.graphics.Bitmap.createBitmap(v.width, v.height, android.graphics.Bitmap.Config.ARGB_8888).also { v.draw(android.graphics.Canvas(it)) }
        val out = java.io.File(System.getProperty("user.dir"), "../shots").apply { mkdirs() }
        java.io.File(out, "$name.png").outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun actionsInSmallTiles() {
        val act = rule.activity; val repo = act.repo
        val tmpl = repo.template.value
        val water = tmpl.first { it.type == ItemType.WATER }.id
        val scale = tmpl.first { it.type == ItemType.SCALE }.id
        val group = repo.groups.value.first { g -> tmpl.any { it.type == ItemType.CHECK && repo.groupOf(it).id == g.id } }.id
        val check = groupChecks(repo, tmpl, group).first()
        rule.runOnUiThread {
            val l = repo.tiles(tmpl, repo.layout.value).filter { it.id in setOf(water, scale, group) }.map {
                when (it.id) { water -> Repo.Tile(water, 3, 2, true); scale -> Repo.Tile(scale, 4, 3, true); else -> Repo.Tile(group, 4, 3, true) }
            }
            repo.setLayout(l + repo.tiles(tmpl, repo.layout.value).filter { it.id !in setOf(water, scale, group) })
        }
        rule.waitForIdle()
        save("48_actions")
        val d = LocalDate.now().key()
        rule.onAllNodesWithText("+250", useUnmergedTree = true).onFirst().performClick()
        rule.waitForIdle()
        assertEquals("0.25", repo.entries.value[d]!![water])
        rule.onAllNodesWithText("+250", useUnmergedTree = true).onFirst().performClick()
        rule.waitForIdle()
        assertEquals("0.5", repo.entries.value[d]!![water])
        rule.onAllNodesWithText(check.displayName, useUnmergedTree = true).onFirst().performClick()
        rule.waitForIdle()
        assertEquals("1", repo.entries.value[d]!![check.id])
        // после отметки название есть и в подписи плитки — нажимаем именно кнопку (она последняя)
        rule.onAllNodesWithText(check.displayName, useUnmergedTree = true).onLast().performClick()
        rule.waitForIdle()
        assertTrue(repo.entries.value[d]!![check.id] in setOf("2", null))
        rule.onAllNodesWithText("7", useUnmergedTree = true).onFirst().performClick()
        rule.waitForIdle()
        assertEquals("7.0", repo.entries.value[d]!![scale])
        // нажатие мимо кнопок — как раньше открывает плитку
        rule.onAllNodesWithText(tmpl.first { it.id == water }.displayName, useUnmergedTree = true).onFirst().performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText("What the target is made of").onFirst().assertExists()
        assertTrue(Changelog.all.any { it.version == "5.48" })
        assertTrue(Changelog.all.any { it.version == "5.49" })
    }

    @Test fun hitsRegistry() {
        val h = TileHits(); var n = 0
        h.put("a", androidx.compose.ui.geometry.Rect(0f, 0f, 10f, 10f)) { n++ }
        h.at(androidx.compose.ui.geometry.Offset(5f, 5f))!!.invoke(); assertEquals(1, n)
        assertNull(h.at(androidx.compose.ui.geometry.Offset(50f, 50f)))
        h.remove("a"); assertNull(h.at(androidx.compose.ui.geometry.Offset(5f, 5f)))
    }
}
