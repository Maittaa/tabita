package org.maittaa.tabita

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
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
import kotlin.math.abs

/** 5.70: цветные плитки — гармонизация, контраст, вид. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1500dp-xxhdpi")
class V570Test {
    init {
        Prefs(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()).apply { lang = "ru"; themeMode = "dark" }
    }
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun save(name: String) {
        rule.waitForIdle()
        val v = rule.activity.window.decorView
        val b = android.graphics.Bitmap.createBitmap(v.width, v.height, android.graphics.Bitmap.Config.ARGB_8888).also { v.draw(android.graphics.Canvas(it)) }
        val out = File(System.getProperty("user.dir"), "../shots").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun contrast(a: Color, b: Color): Double {
        val x = a.luminance() + 0.05; val y = b.luminance() + 0.05
        return (maxOf(x, y) / minOf(x, y)).toDouble()
    }

    @Test fun tones() {
        // гармонизация: сдвиг к основному цвету не больше 15°
        assertEquals(15.0, abs(Tones.harmonize(240.0, 0.0) - 240.0).let { minOf(it, 360 - it) }, 1e-9)
        assertEquals(255.0, Tones.harmonize(240.0, 270.0), 1e-9)
        assertEquals(245.0, Tones.harmonize(240.0, 250.0), 1e-9)
        // OKLCH туда и обратно
        val c = Color(0xFF2F5DA8); val l = Tones.toLch(c); val back = Tones.fromLch(l[0], l[1], l[2])
        assertTrue(abs(back.red - c.red) < 0.01 && abs(back.blue - c.blue) < 0.01)
        // текст на плитке читается (WCAG ≥ 4.5:1) при любом акценте, в тёмной и светлой теме
        val primaries = Look.accents.map { it.second.second } + Color(0xFFD0BCFF) + Color(0xFFA8C7FA)
        for (p in primaries) for (dark in listOf(true, false)) for (t in Tone.entries.filter { it.hue != null }) {
            val k = Tones.colors(t, p, dark)
            val r = contrast(k.container, k.onContainer)
            assertTrue("$t dark=$dark ${"%.2f".format(r)}", r >= 4.5)
        }

        // экран
        val act = rule.activity; val repo = act.repo; val today = LocalDate.now()
        assertTrue(act.prefs.colorTiles); assertTrue(Look.colorful)
        rule.runOnUiThread {
            val tmpl = repo.template.value
            fun id(s: Source) = tmpl.firstOrNull { it.source == s }?.id
            repo.setValues(mapOf(today.key() to buildMap {
                id(Source.HC_SLEEP)?.let { put(it, "493") }; id(Source.HC_STEPS)?.let { put(it, "9523") }
                id(Source.HC_RESTING_HR)?.let { put(it, "59") }; put("weight", "79.9")
            }))
        }
        rule.waitForIdle()
        save("70_today_dark")
        rule.onAllNodes(hasText("Здоровье") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))[0].performClick()
        save("70_health_dark")
        rule.runOnUiThread { Look.mode = "light" }
        save("70_health_light")
        rule.onAllNodes(hasText("Сегодня") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))[0].performClick()
        save("70_today_light")
        rule.runOnUiThread { Look.colorful = false }
        save("70_today_plain")
        // фиксированный акцент — палитра известна, вид как на телефоне
        rule.runOnUiThread { Look.colorful = true; Look.accent = "teal"; Look.mode = "light" }
        save("70_today_teal_light")
        rule.runOnUiThread { Look.mode = "dark" }
        save("70_today_teal_dark")
        // маленькие плитки: сон, шаги, пульс, вес, вода, тренировка — как на телефоне
        rule.runOnUiThread {
            Look.accent = "dynamic"
            val tmpl = repo.template.value
            val ids = listOfNotNull(
                tmpl.firstOrNull { it.source == Source.HC_SLEEP }?.id, tmpl.firstOrNull { it.source == Source.HC_STEPS }?.id,
                tmpl.firstOrNull { it.source == Source.HC_RESTING_HR }?.id, "weight",
                tmpl.firstOrNull { it.type == ItemType.WATER }?.id, tmpl.firstOrNull { it.type == ItemType.TRAINING }?.id,
                tmpl.firstOrNull { it.type == ItemType.MEALS }?.id, tmpl.firstOrNull { it.type == ItemType.EXPENSES }?.id,
            )
            repo.setLayout(ids.map { Repo.Tile(it, 3, 1, true) } + repo.layout.value.filter { it.id !in ids })
        }
        save("70_small_dark")
        rule.runOnUiThread { Look.mode = "light"; Look.accent = "blue" }
        save("70_small_light")
        rule.runOnUiThread { Look.accent = "dynamic"; Look.mode = "system" }
    }
}
