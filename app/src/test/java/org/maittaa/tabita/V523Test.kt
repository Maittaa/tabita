package org.maittaa.tabita

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 5.23: вкладка «Тренировки» и раздел настроек. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
class V523Test {
    init {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        Prefs(ctx).lang = "ru"
    }
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun shot(name: String) {
        rule.waitForIdle()
        val g = Class.forName("android.view.WindowManagerGlobal")
        val inst = g.getMethod("getInstance").invoke(null)
        @Suppress("UNCHECKED_CAST") val views = g.getDeclaredField("mViews").apply { isAccessible = true }.get(inst) as List<android.view.View>
        val v = views.last()
        val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(b).apply { drawColor(android.graphics.Color.rgb(30, 30, 30)); v.draw(this) }
        val out = File(System.getProperty("user.dir"), "../shots").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun trainTabAndSettings() {
        val today = java.time.LocalDate.now()
        val wd = listOf(today.dayOfWeek, today.plusDays(2).dayOfWeek, today.plusDays(4).dayOfWeek)
        rule.runOnUiThread {
            rule.activity.train.saveProgram(org.maittaa.tabita.train.Generator.generate(
                org.maittaa.tabita.train.GenSettings(org.maittaa.tabita.train.Goal.MASS, wd, 30, 1, false),
                setOf(org.maittaa.tabita.train.Equipment.DUMBBELLS)))
        }
        rule.onAllNodesWithText("Здоровье").let { it[it.fetchSemanticsNodes().size - 1] }.performClick()
        rule.waitForIdle()
        // 5.52: главная «Здоровья» — плитки разделов; силовая открывается плиткой
        rule.onNode(androidx.compose.ui.test.hasTestTag("hub_strength")).performClick()
        rule.waitForIdle()
        shot("23_train_tab")
        assertTrue(rule.onAllNodesWithText("Начать тренировку").fetchSemanticsNodes().isNotEmpty())
        rule.onNodeWithText("Прогресс").performClick(); shot("23b_train_progress")
        rule.onNode(androidx.compose.ui.test.hasText("Тренировка") and androidx.compose.ui.test.hasAnySibling(androidx.compose.ui.test.hasText("Прогресс"))).performClick(); rule.waitForIdle()
        rule.onNodeWithText("Начать тренировку").performClick(); rule.waitForIdle()
        shot("23e_workout")
        // во время тренировки нижней панели нет
        assertTrue(rule.onAllNodesWithText("Самочувствие").fetchSemanticsNodes().isEmpty())
        rule.activity.train.screen = org.maittaa.tabita.train.Screen.Home
        rule.waitForIdle()
        rule.onAllNodesWithText("Ещё").let { it[it.fetchSemanticsNodes().size - 1] }.performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Настройки тренировок").performScrollTo()
        shot("23c_settings_train")
        rule.onNodeWithText("Инвентарь дома").performClick()
        shot("23d_settings_eq")
    }
}
