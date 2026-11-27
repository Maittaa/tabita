package org.maittaa.tabita

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.compose.setContent
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h2400dp-xxhdpi")
class V521Test {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun save(name: String) {
        rule.waitForIdle()
        val v = rule.activity.window.decorView
        val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(android.graphics.Canvas(it)) }
        val out = File(System.getProperty("user.dir"), "../shots").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun dbSane() {
        assertTrue(FoodDb.all.size >= 150)
        FoodDb.all.forEach { f ->
            // энергия по Этуотеру не должна сильно расходиться с заявленной (алкоголь/клетчатка — допуск)
            val atw = f.p * 4 + f.c * 4 + f.f * 9
            assertTrue("${f.ru}: ${f.kcal} vs $atw", f.kcal < 15 || f.cat == "drink" || Math.abs(atw - f.kcal) / f.kcal < 0.3)
            assertTrue(f.ru, f.gi == null || f.gi in 1..110)
            assertTrue(f.ru, FoodDb.cats.any { it.first == f.cat })
        }
        assertEquals(FoodDb.all.size, FoodDb.all.map { it.ru }.toSet().size)
    }

    @Test fun search() {
        assertTrue(FoodDb.search("гречк", null).isNotEmpty())
        assertTrue(FoodDb.search("ГРЕЧКА", null).isNotEmpty())
        assertTrue(FoodDb.search("buckwheat", null).isNotEmpty())
        assertTrue(FoodDb.search("", "fruit").all { it.cat == "fruit" })
        assertTrue(FoodDb.search("абвгд", null).isEmpty())
    }

    @Test fun addTo() {
        val rice = Food("Рис", "Rice", "grain", 130.0, 2.7, 0.3, 28.0, gi = 73)
        val chicken = Food("Курица", "Chicken", "meat", 165.0, 31.0, 3.6, 0.0)
        var m = Meal(id = "1", kind = "lunch")
        m = FoodDb.addTo(m, rice, 200.0)
        assertEquals(260.0, m.kcal!!, 0.01)
        assertEquals(56.0, m.carbs!!, 0.01)
        assertEquals(73.0, m.gi!!, 0.01)
        assertEquals(40.9, m.gl!!, 0.1)
        m = FoodDb.addTo(m, chicken, 150.0)
        assertEquals(508.0, m.kcal!!, 1.0)
        assertEquals(51.9, m.protein!!, 0.1)
        assertEquals(73.0, m.gi!!, 0.01)   // курица без углеводов ГИ не меняет
        assertEquals(40.9, m.gl!!, 0.1)
        assertTrue(m.text, m.text.contains(rice.name + " 200") && m.text.contains(chicken.name + " 150"))
        val empty = FoodDb.addTo(Meal(id = "2", kind = "lunch"), chicken, 100.0)
        assertNull(empty.gi); assertNotNull(empty.kcal)
    }

    @Test fun screens() {
        var picked: Pair<Food, Double>? = null
        I18n.lang = "ru"
        rule.activity.setContent {
            MaterialTheme { Surface { Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                FoodTable { f, g -> picked = f to g }
            } } }
        }
        rule.waitForIdle()
        save("ref_foods")
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("греч")
        rule.waitForIdle()
        rule.onNodeWithText("Гречка отварная").performClick()
        rule.waitForIdle()
        save("ref_pick")
        rule.onNodeWithText("Добавить").performClick()
        assertNotNull(picked)
    }

    @Test fun rhythmChart() {
        val r = DayRhythm(wake = 7 * 60 + 10, bed = 23 * 60 + 40, nights = 12, train = 19 * 60, trainN = 6)
        assertEquals(990, r.bedRel)
        I18n.lang = "ru"
        rule.activity.setContent {
            MaterialTheme { Surface { Column(Modifier.padding(16.dp)) { RhythmChart(r, java.time.LocalTime.of(14, 30)) {} } } }
        }
        rule.waitForIdle()
        save("rhythm_chart")
        // отбой после полуночи
        assertEquals(17 * 60, DayRhythm(8 * 60, 60, 5, null, 0).bedRel)
    }

    @Test fun rhythms() {
        I18n.lang = "ru"
        rule.activity.setContent {
            MaterialTheme { Surface { Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) { RhythmsBody() } } }
        }
        rule.waitForIdle()
        save("ref_rhythms")
    }
}
