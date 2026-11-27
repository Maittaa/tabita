package org.maittaa.tabita

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.toSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 5.22.4: перетаскивание плиток не прыгает обратно и не мигает между двумя местами. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h900dp-xxhdpi")
class V5224Test {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun dragDoesNotFlip() {
        // половинки и широкая плитка разного размера — самый «прыгучий» случай
        val state = mutableStateOf(listOf(TileSize.HALF.tile("A"), TileSize.WIDE.tile("B"), TileSize.HALF.tile("C"), TileSize.HALF.tile("D")))
        var moves = 0
        rule.activity.setContent {
            MaterialTheme {
                TileGrid(
                    tiles = state.value, editing = true, selected = "A",
                    onSelect = {}, onStartEdit = {},
                    onMove = { id, target ->
                        val l = state.value.toMutableList()
                        val from = l.indexOfFirst { it.id == id }; val to = l.indexOfFirst { it.id == target }
                        if (from >= 0 && to >= 0 && from != to) { val t = l.removeAt(from); l.add(to, t); state.value = l; moves++ }
                    },
                    onResize = { _, _, _, _ -> }, onOpen = {},
                    full = { Text("full $it") }, compact = { id, _, _ -> Text("tile $id") },
                )
            }
        }
        rule.waitForIdle()
        val a = rule.onNodeWithText("tile A").getBoundsInRoot()
        val c = rule.onNodeWithText("tile C").getBoundsInRoot()
        val d = rule.activity.resources.displayMetrics.density
        val start = Offset((a.left.value + a.right.value) / 2 * d, (a.top.value + a.bottom.value) / 2 * d)
        val end = Offset((c.left.value + c.right.value) / 2 * d, (c.top.value + c.bottom.value) / 2 * d)
        rule.onRoot().performTouchInput {
            down(start)
            val steps = 60
            for (i in 1..steps) { moveTo(start + (end - start) * (i / steps.toFloat())); advanceEventTime(16) }
            // держим палец на месте и чуть шевелим — порядок не должен меняться
            for (i in 1..30) { moveTo(end + Offset(if (i % 2 == 0) 3f else -3f, 0f)); advanceEventTime(16) }
            up()
        }
        rule.waitForIdle()
        val order = state.value.map { it.id }
        assertTrue("order=$order moves=$moves", order.indexOf("A") > order.indexOf("C") || order.indexOf("A") > order.indexOf("B"))
        assertTrue("слишком много перестановок: $moves, $order", moves <= 3)
        // после отпускания ничего не откатывается
        val after = state.value.map { it.id }
        rule.waitForIdle()
        assertEquals(after, state.value.map { it.id })
    }
}
