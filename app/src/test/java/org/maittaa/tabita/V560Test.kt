package org.maittaa.tabita

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.maittaa.tabita.train.SEC_HUB
import org.maittaa.tabita.train.SEC_SLEEP
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/** 5.60: «Сон» — индекс регулярности, карта ночей, фазы. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1600dp-xxhdpi")
class V560Test {
    init { Prefs(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()).lang = "ru" }
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val zone = ZoneId.systemDefault()

    private fun save(name: String) {
        rule.waitForIdle()
        val v = rule.activity.window.decorView
        val b = android.graphics.Bitmap.createBitmap(v.width, v.height, android.graphics.Bitmap.Config.ARGB_8888).also { v.draw(android.graphics.Canvas(it)) }
        val out = File(System.getProperty("user.dir"), "../shots").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Ночь, закончившаяся в день [d]: отбой накануне в [bed] (мин от полуночи, может быть > 1440), подъём в [wake]. */
    private fun night(d: LocalDate, bed: Int, wake: Int, stages: List<SleepStage> = emptyList()): SleepStats.Night {
        val s = d.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() + bed * 60_000L
        val e = d.atStartOfDay(zone).toInstant().toEpochMilli() + wake * 60_000L
        return SleepStats.Night(d, SleepDetail(s, e, stages))
    }

    @Test fun regularityIndex() {
        val d0 = LocalDate.of(2026, 11, 1)
        val same = (0 until 8).map { night(d0.plusDays(it.toLong()), 23 * 60, 7 * 60) }
        assertEquals(100, SleepStats.sri(same))
        // отбой и подъём гуляют на 2 часа через день — индекс ниже
        val shifted = (0 until 8).map { i -> if (i % 2 == 0) night(d0.plusDays(i.toLong()), 23 * 60, 7 * 60) else night(d0.plusDays(i.toLong()), 25 * 60, 9 * 60) }
        val v = SleepStats.sri(shifted)!!
        // 2 ч сна не совпадает дважды за сутки: 240 из 1440 минут → P = 0,833 → 67
        assertEquals(67, v)
        assertEquals(2, SleepStats.sriBand(v)); assertEquals(1, SleepStats.sriBand(80)); assertEquals(0, SleepStats.sriBand(90))
        // меньше 5 пар соседних ночей — нет индекса
        assertNull(SleepStats.sri(same.take(5)))
        assertNull(SleepStats.sri(same.filterIndexed { i, _ -> i % 2 == 0 }))
        // фазы: 100 мин глубокого из 400 сна
        val st = listOf(SleepStage(0, 100, 3), SleepStage(100, 200, 2), SleepStage(300, 30, 0), SleepStage(330, 100, 1))
        val s = SleepStats.stages(listOf(night(d0, 23 * 60, 30 + 7 * 60, st)))!!
        assertEquals(25, s.deep); assertEquals(50, s.light); assertEquals(25, s.rem); assertEquals(30, s.awakeMin)
    }

    @Test fun sleepSection() {
        val act = rule.activity; val repo = act.repo
        val item = repo.template.value.first { it.source == Source.HC_SLEEP }
        val today = LocalDate.now()
        rule.runOnUiThread {
            act.train.discard(); act.train.saveProgram(null); act.train.section = SEC_HUB
            repo.setValues((0 until 10).associate { i ->
                val d = today.minusDays(i.toLong())
                val bed = if (i % 3 == 0) 24 * 60 + 30 else 23 * 60
                val n = night(d, bed, 7 * 60 + 10, listOf(SleepStage(0, 90, 3), SleepStage(90, 200, 2), SleepStage(290, 20, 0), SleepStage(310, 100, 1)))
                d.key() to mapOf(item.id to "420", SleepDetail.key(item.id) to n.d.toJson())
            })
        }
        assertTrue(SleepStats.sri(SleepStats.nights(repo))!! in 60..99)
        rule.onAllNodesWithText("Здоровье").let { it[it.fetchSemanticsNodes().size - 1] }.performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("hub_sleep").performScrollTo()
        save("60_hub")
        rule.onNodeWithTag("hub_sleep").performClick()
        rule.waitForIdle()
        assertEquals(SEC_SLEEP, act.train.section)
        rule.onNodeWithTag("sleep_last").assertExists()
        rule.onNodeWithTag("sri").assertExists()
        save("60_sleep")
        rule.onNodeWithTag("sleep_map").performScrollTo()
        save("60_sleep_map")
        assertTrue(Changelog.all.any { it.version == "5.60" })
    }
}
