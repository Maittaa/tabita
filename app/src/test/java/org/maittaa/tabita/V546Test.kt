package org.maittaa.tabita

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 5.46: при 5–6 колонках плитки уменьшаются и по ширине, и по высоте. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1400dp-xxhdpi")
class V546Test {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun save(name: String) {
        rule.waitForIdle()
        val v = rule.activity.window.decorView
        val b = android.graphics.Bitmap.createBitmap(v.width, v.height, android.graphics.Bitmap.Config.ARGB_8888).also { v.draw(android.graphics.Canvas(it)) }
        val out = java.io.File(System.getProperty("user.dir"), "../shots").apply { mkdirs() }
        java.io.File(out, "$name.png").outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun scale() {
        assertEquals(1f, gridScale(4), 0.001f)
        assertEquals(0.8f, gridScale(5), 0.001f)
        assertEquals(0.6667f, gridScale(6), 0.001f)
        assertEquals(TileSize.HALF, sizeForWidth(3, 6))
        assertNull(sizeForWidth(3, 4))
        assertEquals(TileSize.MINI, sizeForWidth(1, 5))
    }

    @Test fun tilesShrink() {
        val act = rule.activity
        rule.runOnUiThread { act.repo.setLayout(act.repo.ergonomicLayout()) }
        fun widthOf(text: String) = rule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot.let { it.width to it.height }
        rule.waitForIdle()
        save("46_grid4")
        val (w4, h4) = widthOf("Ритм дня")
        act.prefs.gridCols = 6
        rule.runOnUiThread { act.recreate() }
        rule.waitForIdle()
        save("46_grid6")
        val (w6, h6) = widthOf("Ритм дня")
        assertTrue("ширина текста $w4 → $w6", w6 < w4 * 0.8f)
        assertTrue("высота текста $h4 → $h6", h6 < h4 * 0.8f)
        act.prefs.gridCols = 4
        assertTrue(Changelog.all.any { it.version == "5.46" })
    }
}
