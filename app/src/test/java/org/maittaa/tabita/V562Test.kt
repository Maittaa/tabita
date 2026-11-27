package org.maittaa.tabita

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** 5.62: эксперименты «до и после». */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1600dp-xxhdpi")
class V562Test {
    init { Prefs(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()).lang = "ru" }
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @After fun reset() { rule.activity.prefs.experiments = null }

    private fun save(name: String) {
        rule.waitForIdle()
        val v = rule.activity.window.decorView
        val b = android.graphics.Bitmap.createBitmap(v.width, v.height, android.graphics.Bitmap.Config.ARGB_8888).also { v.draw(android.graphics.Canvas(it)) }
        val out = File(System.getProperty("user.dir"), "../shots").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun statistics() {
        assertEquals(12.71, Experiments.t95(1.0), 0.0); assertEquals(2.0, Experiments.t95(100.0), 0.0)
        val before = listOf(400.0, 410.0, 395.0, 405.0, 400.0, 390.0, 410.0)
        val clear = Experiments.compare(before, listOf(450.0, 445.0, 455.0, 460.0, 440.0, 450.0, 452.0))!!
        assertTrue(clear.second > 0)                 // интервал выше нуля — заметно
        val noise = Experiments.compare(before, listOf(405.0, 395.0, 402.0, 398.0, 408.0, 392.0, 401.0))!!
        assertTrue(noise.second < 0 && noise.third > 0)
        assertNull(Experiments.compare(before, listOf(450.0, 460.0, 455.0)))    // меньше 4 дней
        val e = Experiment.new("Ложусь до 23:00", LocalDate.of(2026, 11, 1), 14, listOf("index", "sleep")).copy(stopped = LocalDate.of(2026, 11, 10))
        val back = Experiments.parse(Experiments.toJson(listOf(e))).single()
        assertEquals(e, back); assertEquals(LocalDate.of(2026, 11, 10), back.end)
        assertTrue(back.done(LocalDate.of(2026, 11, 11)))
    }

    @Test fun resultAndCard() {
        val act = rule.activity; val repo = act.repo; val today = LocalDate.now()
        val sleep = repo.template.value.first { it.source == Source.HC_SLEEP }
        val rhr = repo.template.value.first { it.source == Source.HC_RESTING_HR }
        val start = today.minusDays(14)
        rule.runOnUiThread {
            repo.setValues((1..28).associate { i ->
                val d = today.minusDays(i.toLong())
                val inExp = !d.isBefore(start)
                d.key() to mapOf(sleep.id to (if (inExp) 450 + i % 5 else 400 + i % 7).toString(), rhr.id to (if (inExp) 58 + i % 2 else 62 + i % 2).toString())
            })
        }
        val e = Experiment.new("Ложусь до 23:00", start, 14, listOf(sleep.id, rhr.id))
        Experiments.upsert(act.prefs, e)
        val rows = Experiments.result(e, Experiments.options(repo, act.prefs))
        assertEquals(1, rows.first { it.key == sleep.id }.verdict)           // сон длиннее — лучше
        assertEquals(1, rows.first { it.key == rhr.id }.verdict)              // пульс покоя ниже — лучше
        assertEquals(14, rows.first { it.key == sleep.id }.nDuring)
        rule.onAllNodesWithText("Программа").let { it[it.fetchSemanticsNodes().size - 1] }.performClick()
        rule.onAllNodes(hasText("Итоги") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))[0].performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("exp_card").assertExists()
        rule.onNodeWithTag("exp_${e.id}").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("заметно лучше", substring = false).let { }
        save("62_experiment")
        assertTrue(Changelog.all.any { it.version == "5.62" })
    }
}
