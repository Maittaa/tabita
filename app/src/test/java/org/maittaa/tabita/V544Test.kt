package org.maittaa.tabita

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime

/** 5.44: работа/учёба, события дня на ритме, дневной сон, белок перед сном, предупреждение о сдвиге режима. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h2400dp-xxhdpi")
class V544Test {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun save(name: String) {
        rule.waitForIdle()
        val v = rule.activity.window.decorView
        val b = android.graphics.Bitmap.createBitmap(v.width, v.height, android.graphics.Bitmap.Config.ARGB_8888).also { v.draw(android.graphics.Canvas(it)) }
        val out = java.io.File(System.getProperty("user.dir"), "../shots").apply { mkdirs() }
        java.io.File(out, "$name.png").outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    private val r = DayRhythm(7 * 60, 23 * 60, 0, null, 0)   // подъём 7:00, отбой 23:00
    private val day = LocalDate.of(2026, 11, 23)              // понедельник

    private fun prefs() = rule.activity.prefs.apply { planGoal = ""; rhythmSrc = RhythmSrc.ALL }

    @Test fun napAfterShortNight() {
        val p = prefs()
        val repo = rule.activity.repo
        repo.setValue(day.key(), "sleep", "300")
        val nap = DayPlan.of(repo, p, day, r, null).events.firstOrNull { it.kind == DayPlan.Kind.NAP }
        assertNotNull(nap); assertEquals(14 * 60, nap!!.at)
        repo.setValue(day.key(), "sleep", "450")
        assertFalse(DayPlan.of(repo, p, day, r, null).events.any { it.kind == DayPlan.Kind.NAP })
        // сон не учитывается — дневной сон не предлагаем
        repo.setValue(day.key(), "sleep", "300"); p.rhythmSrc = RhythmSrc.ALL - RhythmSrc.SLEEP
        assertFalse(DayPlan.of(repo, p, day, r, null).events.any { it.kind == DayPlan.Kind.NAP })
        p.rhythmSrc = RhythmSrc.ALL
    }

    @Test fun proteinBeforeSleepForMuscle() {
        val p = prefs(); p.planGoal = "muscle"
        val e = DayPlan.of(rule.activity.repo, p, day, r, null).events.first { it.kind == DayPlan.Kind.BED_PROTEIN }
        assertEquals(22 * 60 + 15, e.at)
        assertTrue(e.note, e.note.contains("30–40"))
        assertEquals(25.0, DayPlan.proteinDose(83.0), 0.01)
        assertEquals(20.0, DayPlan.proteinDose(50.0), 0.01)
        p.planGoal = "lose"
        assertFalse(DayPlan.of(rule.activity.repo, p, day, r, null).events.any { it.kind == DayPlan.Kind.BED_PROTEIN })
        p.planGoal = ""
    }

    @Test fun shiftWarningAndPlanList() {
        val shifted = DayRhythm(7 * 60, 23 * 60, 7, null, 0, jetlag = 100, bedSpread = 30)
        val p = prefs()
        val plan = DayPlan.of(rule.activity.repo, p, day, shifted, DayPlan.TrainPlan("Верх", 45, gym = true))
        rule.runOnUiThread { rule.activity.setContent { MaterialTheme { androidx.compose.material3.Surface {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.padding(16.dp)) { RhythmFull(shifted, LocalTime.of(15, 30), true, null, plan) } } } } }
        rule.waitForIdle()
        save("44_day_plan")
        rule.onNodeWithText("Your sleep schedule is shifting").assertExists().performClick()
        rule.onNodeWithText("What to do", substring = true).assertExists()
        rule.onNodeWithText("Day plan").assertExists()
        rule.onNodeWithText("Lunch").assertExists()
        rule.onNodeWithText("Верх").assertExists()
        // нет сдвига — нет предупреждения
        rule.runOnUiThread { rule.activity.setContent { MaterialTheme { RhythmShiftWarning(r) } } }
        rule.waitForIdle()
        rule.onNodeWithText("Your sleep schedule is shifting").assertDoesNotExist()
        assertTrue(Changelog.all.any { it.version == "5.44" })
    }
}
