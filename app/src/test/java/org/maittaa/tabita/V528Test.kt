package org.maittaa.tabita

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
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

/** 5.28: план (рекомендация цели, вехи) и касание графика. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h800dp-xxhdpi")
class V528Test {
    @get:Rule val rule = createEmptyComposeRule()
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    private fun stats(bmiW: Double?, h: Double? = 1.83, protein: Double? = null, sleep: Double? = null, strength: Double = 0.0, wb: Double? = null, lean: Double? = null) =
        PlanStats(bmiW, h, if (bmiW != null && h != null) bmiW / (h * h) else null, lean, null, protein, null, null, sleep, null,
            strength, 0.0, wb, 2800.0, 0, 0, 0, 0, 7)

    @Test fun recommend() {
        I18n.lang = "ru"
        assertEquals(Goal.LOSE, Plan.recommend(stats(95.0)).goal)            // ИМТ 28,4
        assertEquals(Goal.GAIN, Plan.recommend(stats(58.0)).goal)            // ИМТ 17,3
        assertEquals(Goal.MUSCLE, Plan.recommend(stats(75.0, strength = 3.0)).goal)
        assertEquals(Goal.DEFICITS, Plan.recommend(stats(75.0, protein = 0.8)).goal)
        assertEquals(Goal.WELLBEING, Plan.recommend(stats(75.0, sleep = 5.5, wb = 40.0)).goal)
        assertEquals(Goal.WELLBEING, Plan.recommend(stats(null, null)).goal)
        assertTrue(Plan.recommend(stats(95.0)).reasons.first().contains("ИМТ 28,4"))
    }

    @Test fun milestones() {
        I18n.lang = "ru"
        val p = Prefs(ctx)
        p.planW0 = 100.0; p.planStart = LocalDate.now().minusWeeks(4).toString()
        val ms = Plan.milestones(Goal.LOSE, stats(96.0), p)
        // −2 % (98) пройдено, −5 % (95) — 80 % пути, −10 % (90) нет
        assertTrue(ms[0].done); assertTrue(!ms[1].done); assertEquals(0.8f, ms[1].frac, 0.01f)
        assertTrue(ms.any { it.name.startsWith("Нормальный ИМТ") })
        assertTrue(ms.last().name.contains("Темп"))
        val steps = Plan.steps(Goal.LOSE, stats(96.0))
        assertTrue(steps.first().target.contains("2240–2380 ккал"))
        p.planLean0 = 70.0
        val mm = Plan.milestones(Goal.MUSCLE, stats(80.0, lean = 71.5, strength = 3.0), p)
        assertTrue(mm[0].done); assertTrue(!mm[1].done)
    }

    @Test fun scrub() {
        ActivityScenario.launch(MainActivity::class.java).use { s ->
            rule.waitForIdle()
            val days = (0 until 14).map { LocalDate.of(2026, 11, 1).plusDays(it.toLong()) }
            val v = days.mapIndexed { i, _ -> if (i == 5) null else 90.0 - i * 0.3 }
            s.onActivity { it.prefs.lang = "ru"; I18n.lang = "ru"; it.setContent {
                androidx.compose.material3.MaterialTheme { androidx.compose.material3.Surface {
                    Column(Modifier.padding(16.dp)) {
                        Chart(v, days, ChartType.LINE, { formatNumber(Math.round(it * 10) / 10.0) + " кг" })
                        Chart(v, days, ChartType.SMOOTH, { formatNumber(Math.round(it * 10) / 10.0) + " кг" })
                    }
                } }
            } }
            rule.waitForIdle()
            rule.onRoot().performTouchInput { click(androidx.compose.ui.geometry.Offset(width * 0.62f, 100.dp.toPx())) }
            rule.onRoot().performTouchInput { click(androidx.compose.ui.geometry.Offset(width * 0.3f, 300.dp.toPx())) }
            rule.waitForIdle()
            s.onActivity { a ->
                val vw = a.window.decorView
                val b = Bitmap.createBitmap(vw.width, vw.height, Bitmap.Config.ARGB_8888).also { vw.draw(android.graphics.Canvas(it)) }
                val out = File(System.getProperty("user.dir"), "../shots").apply { mkdirs() }
                File(out, "14d_chart_scrub.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }
}
