package org.maittaa.tabita

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** 5.24: плитка «Тренировка», группы графиков. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1600dp-night-xxhdpi")
class V524Test {
    init { Prefs(ApplicationProvider.getApplicationContext()).lang = "ru" }
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun shot(name: String) {
        rule.waitForIdle()
        val g = Class.forName("android.view.WindowManagerGlobal")
        val inst = g.getMethod("getInstance").invoke(null)
        @Suppress("UNCHECKED_CAST") val views = g.getDeclaredField("mViews").apply { isAccessible = true }.get(inst) as List<android.view.View>
        val v = views.last()
        val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(b).apply { drawColor(android.graphics.Color.rgb(30, 30, 30)); v.draw(this) }
        File(File(System.getProperty("user.dir"), "../shots").apply { mkdirs() }, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun migrationAddsTrainingTile() {
        val repo = rule.activity.repo
        val o = org.json.JSONObject(repo.toJson())
        // данные 5.23: версия 5, плитки «Тренировка» нет
        val t = o.getJSONArray("template"); val keep = org.json.JSONArray()
        for (i in 0 until t.length()) if (t.getJSONObject(i).optString("type") != "TRAINING") keep.put(t.getJSONObject(i))
        o.put("template", keep).put("version", 5)
        o.put("layout", org.json.JSONArray().put(org.json.JSONObject().put("id", "tips").put("w", 2).put("h", 1)))
        assertTrue(repo.loadJson(o.toString()))
        val ids = repo.template.value.map { it.id }
        assertEquals(ids.indexOf("tips") + 1, ids.indexOf("training"))
        assertTrue(repo.layout.value.map { it.id }.let { it.indexOf("training") == it.indexOf("tips") + 1 })
    }

    @Test fun contexts() {
        val tm = defaultTemplate()
        fun card(k: String, g: String = "Показатели") = BarsCard(k, g, "x", emptyList())
        assertEquals("sleep", StatContext.of(card("num_sleep", "Часы"), tm))
        assertEquals("activity", StatContext.of(card("num_steps", "Часы"), tm))
        assertEquals("mood", StatContext.of(card("num_rate"), tm))
        assertEquals("food", StatContext.of(card("kcal_meals"), tm))
        assertEquals("money", StatContext.of(card("cat_x"), tm))
        assertEquals("body", StatContext.of(card("anthro"), tm))
    }

    @Test fun screens() {
        val today = LocalDate.now()
        rule.runOnUiThread {
            rule.activity.train.saveProgram(org.maittaa.tabita.train.Generator.generate(
                org.maittaa.tabita.train.GenSettings(org.maittaa.tabita.train.Goal.MASS,
                    listOf(today.dayOfWeek, today.plusDays(2).dayOfWeek, today.plusDays(4).dayOfWeek), 30, 1, false),
                setOf(org.maittaa.tabita.train.Equipment.DUMBBELLS)))
        }
        rule.waitForIdle()
        rule.onNodeWithText("Открыть").performScrollTo()
        rule.onAllNodesWithText("Тренировка Б").let { if (it.fetchSemanticsNodes().isNotEmpty()) it[0].performScrollTo() }
        shot("24_day_training")
        // уменьшенные версии плитки
        rule.runOnUiThread {
            val r = rule.activity.repo
            r.setLayout(listOf(TileSize.HALF.tile("training"), TileSize.MINI.tile("tips")) + r.tiles(r.template.value, r.layout.value).filter { it.id != "training" && it.id != "tips" })
        }
        rule.waitForIdle()
        rule.onAllNodesWithText("Тренировка")[0].performScrollTo()
        shot("24a_day_training_small")
        rule.onAllNodesWithText("Программа").let { it[it.fetchSemanticsNodes().size - 1] }.performClick()
        rule.onAllNodesWithText("Графики").let { it[it.fetchSemanticsNodes().size - 1] }.performClick()
        rule.waitForIdle()
        shot("24b_stats_ctx")
        rule.onAllNodesWithText("Тренировки").let { it[0] }.performClick()
        shot("24c_stats_train")
    }
}
