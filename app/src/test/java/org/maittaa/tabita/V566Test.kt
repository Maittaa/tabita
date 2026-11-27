package org.maittaa.tabita

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** 5.66: заморозки серии (1 за 7 заполненных дней, максимум 3) и строка прогресса в прокрутке. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h900dp-xxhdpi")
class V566Test {
    init { Prefs(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()).lang = "ru" }
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun save(name: String) {
        rule.waitForIdle()
        val v = rule.activity.window.decorView
        val b = android.graphics.Bitmap.createBitmap(v.width, v.height, android.graphics.Bitmap.Config.ARGB_8888).also { v.draw(android.graphics.Canvas(it)) }
        val out = File(System.getProperty("user.dir"), "../shots").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun freezes() {
        val act = rule.activity; val repo = act.repo; val today = LocalDate.now()
        val tmpl = repo.template.value
        val item = tmpl.first { it.type == ItemType.SCALE && it.source == Source.NONE && !it.hidden }
        fun ent(vararg back: IntRange) = back.flatMap { it.toList() }.associate { today.minusDays(it.toLong()).key() to mapOf(item.id to "6") }
        fun st(e: Map<String, Map<String, String>>) = MonthData(tmpl, e, today, act.prefs.monthCfg, today).streak(today)

        // 21 день подряд → 3 заморозки; сегодня пусто — серия жива
        st(ent(1..21)).let { assertEquals(21, it.days); assertEquals(3, it.freezes); assertEquals(0, it.toNext) }
        // 35 дней — запас не больше 3
        assertEquals(3, st(ent(1..35)).freezes)
        // 14 дней (2 заморозки), пропуск 2 дня, 5 дней → серия 19, заморозки потрачены
        st(ent(1..5, 8..21)).let { assertEquals(19, it.days); assertEquals(0, it.freezes); assertEquals(listOf(today.minusDays(7), today.minusDays(6)), it.frozen) }
        // 7 дней (1 заморозка), пропуск 2 дня — серия заново, заморозка не сгорает
        st(ent(1..3, 6..12)).let { assertEquals(3, it.days); assertEquals(1, it.freezes); assertTrue(it.frozen.isEmpty()) }
        // пропуск сейчас длиннее запаса — серия прервалась
        st(ent(3..9)).let { assertEquals(0, it.days); assertTrue(it.broken); assertEquals(1, it.freezes) }
        // вчера пропущено, заморозка есть — закрыто
        st(ent(2..8)).let { assertEquals(7, it.days); assertEquals(0, it.freezes); assertEquals(listOf(today.minusDays(1)), it.frozen); assertFalse(it.broken) }
        // до следующей заморозки
        assertEquals(3, st(ent(1..10)).toNext)

        rule.runOnUiThread { repo.setValues(ent(1..5, 8..30)) }
        rule.waitForIdle()
        rule.onNodeWithTag("freezes", useUnmergedTree = true).assertExists()
        save("66_streak")
        rule.onNodeWithTag("streak").performClick()
        rule.onNodeWithText("Серия и заморозки").assertIsDisplayed()
        save("66_dialog")
        assertTrue(Changelog.all.any { it.version == "5.66" })
    }
}
