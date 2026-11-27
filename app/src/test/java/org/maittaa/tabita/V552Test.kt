package org.maittaa.tabita

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performScrollToNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.maittaa.tabita.train.SEC_CARDIO
import org.maittaa.tabita.train.SEC_HUB
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** 5.52: главная «Здоровья» — плитки разделов, переход внутрь и назад. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h900dp-xxhdpi")
class V552Test {
    init { Prefs(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()).lang = "ru" }
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun save(name: String) {
        rule.waitForIdle()
        val v = rule.activity.window.decorView
        val b = android.graphics.Bitmap.createBitmap(v.width, v.height, android.graphics.Bitmap.Config.ARGB_8888).also { v.draw(android.graphics.Canvas(it)) }
        val out = java.io.File(System.getProperty("user.dir"), "../shots").apply { mkdirs() }
        java.io.File(out, "$name.png").outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun openHealth() {
        rule.runOnUiThread { rule.activity.train.discard(); rule.activity.train.section = SEC_HUB }
        rule.onAllNodesWithText("Здоровье").let { it[it.fetchSemanticsNodes().size - 1] }.performClick()
        rule.waitForIdle()
    }

    @Test fun hub() {
        // аэробное занятие на этой неделе — плитка аэробики показывает активные минуты
        rule.runOnUiThread {
            rule.activity.train.saveProgram(null)  // файлы train/ общие в JVM — программа могла остаться от других тестов
            saveSession(rule.activity, LocalDate.now(), WorkoutSession("aerobics", 30, System.currentTimeMillis() - 3_600_000, "Аэробика дома", effort = 2))
        }
        openHealth()
        rule.onNodeWithTag("health_hub").assertExists()
        rule.onNodeWithText("Программы пока нет").assertExists()
        rule.onNodeWithTag("health_hub").performScrollToNode(hasTestTag("hub_activity"))
        rule.onNodeWithText("активных минут за неделю", substring = true).assertExists()
        rule.onNodeWithTag("health_hub").performScrollToNode(hasTestTag("hub_wb"))
        rule.onNodeWithText("Пора пройти чекап").assertExists()
        rule.onNodeWithTag("health_hub").performScrollToNode(hasTestTag("hub_strength"))
        save("52_hub")
        // аэробика → вкладки раздела, назад → главная
        rule.onNodeWithTag("hub_cardio").performClick()
        rule.waitForIdle()
        assertEquals(SEC_CARDIO, rule.activity.train.section)
        rule.onNodeWithText("Занятия").assertExists()
        rule.onNodeWithTag("cardio_home").assertExists()
        rule.onNodeWithContentDescription("Назад").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("health_hub").assertExists()
        // системная кнопка «назад» из раздела тоже ведёт на главную
        rule.onNodeWithTag("health_hub").performScrollToNode(hasTestTag("hub_wb"))
        // 5.61: плитка в полширины — по центру кнопка «Пройти», раздел открывается нажатием на заголовок
        rule.onNodeWithTag("hub_wb").performTouchInput { click(androidx.compose.ui.geometry.Offset(width * 0.3f, 40f)) }
        rule.waitForIdle()
        rule.onNodeWithText("Чекап самочувствия").assertExists()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        assertEquals(SEC_HUB, rule.activity.train.section)
        assertTrue(Changelog.all.any { it.version == "5.52" })
    }

    @Test fun strengthWithProgram() {
        val today = LocalDate.now()
        rule.runOnUiThread {
            rule.activity.train.saveProgram(org.maittaa.tabita.train.Generator.generate(
                org.maittaa.tabita.train.GenSettings(org.maittaa.tabita.train.Goal.MASS, listOf(today.dayOfWeek, today.plusDays(2).dayOfWeek, today.plusDays(4).dayOfWeek), 30, 1, false),
                setOf(org.maittaa.tabita.train.Equipment.DUMBBELLS)))
        }
        openHealth()
        val planned = rule.activity.train.todayPlanned()!!
        rule.onNodeWithText(planned.name).assertExists()
        save("52_hub_program")
        rule.onNodeWithText("Начать").performClick()
        rule.waitForIdle()
        assertTrue(rule.activity.train.draft != null)
        rule.runOnUiThread { rule.activity.train.discard() }
        rule.waitForIdle()
        // незаконченная тренировка — «Продолжить» на плитке
        rule.runOnUiThread { rule.activity.train.start(planned); rule.activity.train.screen = org.maittaa.tabita.train.Screen.Home; rule.activity.train.section = SEC_HUB }
        rule.waitForIdle()
        rule.onNodeWithText("Продолжить").assertExists()
        rule.runOnUiThread { rule.activity.train.discard() }
    }
}
