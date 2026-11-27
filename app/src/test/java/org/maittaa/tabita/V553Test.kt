package org.maittaa.tabita

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.maittaa.tabita.train.SEC_HUB
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** 5.53: общая история на главной «Здоровья», йога — позы, комплексы, свой комплекс, таймер, самопроверки. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1100dp-xxhdpi")
class V553Test {
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
        rule.runOnUiThread { rule.activity.train.discard(); rule.activity.train.saveProgram(null); rule.activity.train.section = SEC_HUB }
        rule.onAllNodesWithText("Здоровье").let { it[it.fetchSemanticsNodes().size - 1] }.performClick()
        rule.waitForIdle()
    }

    @Test fun yogaLogic() {
        val after = Yoga.complex("after")!!
        val blocks = Yoga.blocks(after)
        // двусторонние позы — по блоку на сторону
        val sided = after.poses.count { Yoga.pose(it)!!.sides }
        assertEquals(after.poses.size + sided, blocks.size)
        assertEquals(after.totalSec, CardioPlans.totalSec(blocks))
        assertTrue(blocks.all { it.phase == 4 })
        // все позы комплексов есть в библиотеке
        assertTrue(Yoga.builtIn.all { c -> c.poses.all { Yoga.pose(it) != null } })
        // свой комплекс: JSON туда и обратно, неизвестные позы отбрасываются
        val c = Yoga.Complex("c1", "Спина", "Спина", listOf("catcow", "nope", "child"), true)
        val back = Yoga.parseCustom(Yoga.customToJson(listOf(c)))
        assertEquals(listOf("catcow", "child"), back.single().poses)
        // самопроверки: пора, если не было или прошло 28 дней
        val today = LocalDate.of(2026, 11, 24)
        assertTrue(Yoga.checkDue(emptyList(), today))
        assertFalse(Yoga.checkDue(listOf(Yoga.Check("2026-11-10", setOf("head"), 12, 1)), today))
        assertTrue(Yoga.checkDue(listOf(Yoga.Check("2026-10-27", setOf("head"), 12, 1)), today))
        val l = listOf(Yoga.Check("2026-10-01", setOf("blades"), 15, 0), Yoga.Check("2026-11-01", setOf("head", "blades"), 11, 1))
        assertEquals(l, Yoga.parseChecks(Yoga.checksToJson(l)))
        // таймер йоги: вид и название из комплекса
        val d = CardioDraft(CardioPlans.Mode.YOGA, 0, complex = "evening")
        assertEquals("yoga", d.kind); assertEquals("Перед сном", d.title)
        assertEquals(d, CardioDraft.parse(d.toJson()))
    }

    @Test fun hubHistory() {
        val now = System.currentTimeMillis()
        rule.runOnUiThread {
            saveSession(rule.activity, LocalDate.now(), WorkoutSession("aerobics", 25, now - 7_200_000, "Аэробика дома", effort = 2))
            saveSession(rule.activity, LocalDate.now(), WorkoutSession("yoga", 12, now - 3_600_000, "Утро"))
            saveSession(rule.activity, LocalDate.now().minusDays(40), WorkoutSession("walk", 40, now - 40L * 86_400_000, dist = 3000))
        }
        openHealth()
        rule.onNodeWithTag("health_hub").performScrollToNode(hasTestTag("hist_all"))
        rule.onNodeWithText("Все · 3").assertExists()
        save("53_hub_history")
        rule.onNodeWithTag("health_hub").performScrollToNode(hasTestTag("hist_yoga"))
        rule.onNodeWithTag("hist_yoga").performClick()
        rule.waitForIdle()
        assertTrue(rule.onAllNodes(hasText("Аэробика дома")).fetchSemanticsNodes().none { true } ||
            rule.onAllNodes(hasText("Аэробика дома") and hasTestTag("hub_cardio")).fetchSemanticsNodes().isEmpty())
        assertEquals("yoga", rule.activity.prefs.histFilter)
        val rows = HealthHistory.rows(rule.activity)
        assertEquals(listOf("yoga", "cardio", "cardio"), rows.map { it.group })
        // подробности занятия из истории
        rule.onNodeWithTag("health_hub").performScrollToNode(hasText("Утро"))
        rule.onAllNodesWithText("Утро").let { it[it.fetchSemanticsNodes().size - 1] }.performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Длительность").assertExists()
    }

    @Test fun yogaFlow() {
        openHealth()
        rule.onNodeWithTag("hub_yoga").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("yoga_home").assertExists()
        rule.onNodeWithText("Самопроверка осанки").assertExists()
        save("53_yoga_home")
        // свой комплекс
        rule.onNodeWithTag("yoga_home").performScrollToNode(hasText("Свой комплекс"))
        rule.onNodeWithText("Свой комплекс").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Название").performTextInput("Шея")
        rule.onNodeWithTag("yoga_home").performScrollToNode(hasText("Втягивание подбородка"))
        rule.onAllNodesWithText("Втягивание подбородка")[0].performClick()
        rule.onAllNodesWithText("Растяжка шеи")[0].performClick()
        rule.onNodeWithTag("yoga_home").performScrollToNode(hasTestTag("yoga_editor_save"))
        rule.onNodeWithTag("yoga_editor_save").performClick()
        rule.waitForIdle()
        val custom = Yoga.parseCustom(rule.activity.prefs.yogaCustom).single()
        assertEquals("Шея", custom.ru); assertEquals(listOf("chin", "trap"), custom.poses)
        // старт «Утро»
        rule.onNodeWithTag("yoga_home").performScrollToNode(hasTestTag("yoga_morning"))
        rule.onAllNodesWithText("Начать")[0].performClick()
        rule.mainClock.advanceTimeBy(800)
        rule.onNodeWithTag("cardio_timer").assertExists()
        rule.onNodeWithText("Кошка — корова").assertExists()
        rule.onNodeWithText("Поза", substring = true).assertExists()
        save("53_yoga_timer")
        rule.runOnUiThread {
            val d = CardioDraft.parse(rule.activity.prefs.cardioDraft)!!
            rule.activity.prefs.cardioDraft = d.copy(startedAt = System.currentTimeMillis() - 600_000).toJson()
        }
        rule.onNodeWithText("Прогресс").performClick(); rule.mainClock.advanceTimeBy(300)
        rule.onNodeWithText("Комплексы").performClick(); rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithTag("cardio_finish").performClick()
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithTag("cardio_finish_save").performClick()
        rule.mainClock.advanceTimeBy(500)
        val y = Cardio.sessions(rule.activity.repo, LocalDate.now(), LocalDate.now()) { it.kind in Yoga.kinds }
        assertEquals(1, y.size); assertEquals("Утро", y[0].s.title); assertEquals(10, y[0].s.min)
        assertNull(rule.activity.prefs.cardioDraft)
        // самопроверка
        rule.onNodeWithText("Прогресс").performClick(); rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithTag("yoga_progress").performScrollToNode(hasTestTag("yoga_check"))
        rule.onNodeWithTag("yoga_check").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Лопатки").performClick()
        rule.onNodeWithText("Сохранить").performClick()
        rule.waitForIdle()
        val ch = Yoga.parseChecks(rule.activity.prefs.yogaChecks).single()
        assertEquals(setOf("blades"), ch.wall); assertEquals(10, ch.bend)
        save("53_yoga_progress")
        assertTrue(Changelog.all.any { it.version == "5.53" })
    }
}
