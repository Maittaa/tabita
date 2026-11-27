package org.maittaa.tabita

import androidx.activity.compose.setContent
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalTime

/** 5.47: плитки любого размера 1…6 × 1…3 с содержимым под размер. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h2600dp-xxhdpi")
class V547Test {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun save(name: String) {
        rule.waitForIdle()
        val v = rule.activity.window.decorView
        val b = android.graphics.Bitmap.createBitmap(v.width, v.height, android.graphics.Bitmap.Config.ARGB_8888).also { v.draw(android.graphics.Canvas(it)) }
        val out = java.io.File(System.getProperty("user.dir"), "../shots").apply { mkdirs() }
        java.io.File(out, "$name.png").outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun normalize() {
        assertEquals(Repo.Tile("a", 6, 3, true), Repo.Tile("a", 9, 7, true).normalized())
        assertEquals(Repo.Tile("a", 1, 1, true), Repo.Tile("a", 0, 0, true).normalized())
        assertEquals(Repo.Tile("a", 3, 1, true), Repo.Tile("a", 3, 1, true).normalized())
        assertEquals(Repo.Tile("a"), Repo.Tile("a", 4, 2, false).normalized())
        val repo = rule.activity.repo
        val id = repo.template.value.first { it.type == ItemType.WATER }.id
        rule.runOnUiThread { repo.setLayout(repo.tiles(repo.template.value, repo.layout.value).map { if (it.id == id) Repo.Tile(id, 5, 3, true) else it }) }
        assertEquals(Repo.Tile(id, 5, 3, true), repo.tiles(repo.template.value, repo.layout.value).first { it.id == id })
        assertTrue(Changelog.all.any { it.version == "5.47" })
    }

    /** Галерея всех 18 размеров при сетке 6 колонок — как на экране: клетка и содержимое в масштабе 4/6. */
    @Test fun gallery() {
        val s = TileSummary("Вода", "1,2 / 2,4 л", "50% нормы\nещё 1,2 л\nпоследний стакан 13:10", Icons.Filled.WaterDrop,
            spark = listOf(1.8, 2.2, 1.5, 2.6, 2.0, 2.4, 1.2), bars = true, progress = 0.5f)
        val r = DayRhythm(7 * 60, 23 * 60, 3, 18 * 60, 2)
        val cols = 6; val k = gridScale(cols)
        val cell = ((411 - 24 - 8 * (cols - 1)) / cols).dp; val unit = 52.dp * k
        rule.runOnUiThread {
            rule.activity.setContent {
                MaterialTheme { Surface {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (h in 3 downTo 1) for (w in 6 downTo 1) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(true, false).forEach { rhythm ->
                                    if (w > 3 && !rhythm) return@forEach
                                    val ww = if (w > 3) w else w
                                    Box(Modifier.size(cell * ww + 8.dp * (ww - 1), unit * h + 8.dp * (h - 1))
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))) {
                                        val d = LocalDensity.current
                                        CompositionLocalProvider(LocalDensity provides Density(d.density * k, d.fontScale)) {
                                            if (rhythm) CompactTileView(s, w, h) else RhythmCompact("Ритм дня", r, LocalTime.of(15, 30), w, h, true)
                                        }
                                    }
                                }
                                Text("${w}×$h", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                } }
            }
        }
        rule.waitForIdle()
        save("47_sizes")
        assertTrue(true)
    }
}
