package org.maittaa.tabita

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalTime

/** 5.43: сетка 4–6 колонок, плитка «Ритм дня», круг 12 ч, полоса над плитками. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1600dp-xxhdpi")
class V543Test {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun save(name: String) {
        rule.waitForIdle()
        val v = rule.activity.window.decorView
        val b = android.graphics.Bitmap.createBitmap(v.width, v.height, android.graphics.Bitmap.Config.ARGB_8888).also { v.draw(android.graphics.Canvas(it)) }
        val out = java.io.File(System.getProperty("user.dir"), "../shots").apply { mkdirs() }
        java.io.File(out, "$name.png").outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun gridSizes() {
        assertEquals(listOf(4, 2, 1), listOf(TileSize.WIDE, TileSize.HALF, TileSize.MINI).map { it.cols(4) })
        // 5.46: размеры в клетках постоянные, клетка мельчает
        assertEquals(listOf(4, 2, 1), listOf(TileSize.WIDE, TileSize.HALF, TileSize.MINI).map { it.cols(5) })
        assertEquals(listOf(4, 2, 1), listOf(TileSize.WIDE, TileSize.HALF, TileSize.MINI).map { it.cols(6) })
        // ресайз: 1 — мини, до половины — половина, шире — широкая
        assertEquals(TileSize.MINI, sizeForWidth(1, 6))
        assertEquals(TileSize.HALF, sizeForWidth(2, 4))
        assertNull(sizeForWidth(3, 4))
        assertEquals(TileSize.HALF, sizeForWidth(3, 6))
        assertEquals(TileSize.HALF, sizeForWidth(3, 5))
        assertNull(sizeForWidth(4, 6))
        val p = rule.activity.prefs
        p.gridCols = 9; assertEquals(6, p.gridCols)
        p.gridCols = 2; assertEquals(4, p.gridCols)
    }

    @Test fun migrationAddsRhythmAfterGoal() {
        val repo = rule.activity.repo
        val o = org.json.JSONObject(repo.toJson())
        val t = o.getJSONArray("template"); val keep = org.json.JSONArray()
        for (i in 0 until t.length()) if (t.getJSONObject(i).optString("type") != "RHYTHM") keep.put(t.getJSONObject(i))
        o.put("template", keep).put("version", 7)
        o.put("layout", org.json.JSONArray()
            .put(org.json.JSONObject().put("id", "tips").put("w", 4).put("h", 2).put("small", true))
            .put(org.json.JSONObject().put("id", "goal").put("w", 4).put("h", 2).put("small", true)))
        assertTrue(repo.loadJson(o.toString()))
        val ids = repo.template.value.map { it.id }
        assertEquals(ids.indexOf("goal") + 1, ids.indexOf("rhythm"))
        val l = repo.layout.value
        assertEquals(l.indexOfFirst { it.id == "goal" } + 1, l.indexOfFirst { it.id == "rhythm" })
        assertEquals(TileSize.WIDE, l.first { it.id == "rhythm" }.size)
        assertEquals("rhythm", singletonTypes[ItemType.RHYTHM])
    }

    @Test fun nowInfo() {
        // подъём 7:00, отбой 23:00 → отбой через 16 ч после подъёма
        val r = DayRhythm(7 * 60, 23 * 60, 0, null, 0)
        val peak = RhythmNow.of(r, LocalTime.of(15, 30))
        assertTrue(peak.active.contains("peak"))
        assertFalse(peak.sleeping)
        assertEquals(7 * 60 + 30, peak.toBed)
        val dim = RhythmNow.of(r, LocalTime.of(22, 0))
        assertTrue(dim.active.contains("dim"))
        assertEquals(60, dim.toBed)
        val night = RhythmNow.of(r, LocalTime.of(0, 30))
        assertTrue(night.sleeping)
        assertEquals(0, night.toBed)
        val morning = RhythmNow.of(r, LocalTime.of(7, 20))
        assertTrue(morning.active.contains("light"))
        assertTrue(morning.next.isNotEmpty())   // кофе и ужин ещё впереди
    }

    @Test fun viewsRender() {
        val r = DayRhythm(7 * 60, 23 * 60, 0, 18 * 60, 3)
        rule.runOnUiThread {
            rule.activity.setContent {
                MaterialTheme {
                    androidx.compose.material3.Surface {
                    Column(androidx.compose.ui.Modifier.padding(16.dp)) {
                        RhythmStrip(r, LocalTime.of(15, 30)) {}
                        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxWidth().height(104.dp)) {
                            RhythmCompact("Rhythm", r, LocalTime.of(15, 30), 4, 2, dial = true)
                        }
                        androidx.compose.foundation.layout.Row {
                            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.width(180.dp).height(104.dp)) {
                                RhythmCompact("Rhythm", r, LocalTime.of(15, 30), 2, 2, dial = true)
                            }
                            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.width(80.dp).height(104.dp)) {
                                RhythmCompact("Rhythm", r, LocalTime.of(15, 30), 1, 2, dial = true)
                            }
                        }
                        RhythmFull(r, LocalTime.of(15, 30), dial = true, onMore = null)
                    }
                    }
                }
            }
        }
        rule.waitForIdle()
        save("43_rhythm_dial")
        assertEquals(3, rule.onAllNodesWithContentDescription("Daily rhythm: Peak strength", substring = true).fetchSemanticsNodes().size)
        rule.onNodeWithText("Peak strength · bedtime 23:00 · in 7 h 30 min").assertExists()
    }

    @Test fun settingsSection() {
        val p = rule.activity.prefs
        rule.runOnUiThread { rule.activity.setContent { MaterialTheme { RhythmSettings(rule.activity.repo, p) } } }
        rule.waitForIdle()
        rule.onNodeWithText("Tracks").performClick()
        assertEquals("chart", p.rhythmView)
        rule.onNodeWithText("12 h dial").performClick()
        assertEquals("dial", p.rhythmView)
        assertTrue(Changelog.all.any { it.version == "5.43" })
    }
}
