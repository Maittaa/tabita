package org.maittaa.tabita

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

/** 5.51: аэробная нагрузка — зоны пульса, активные минуты, слияние с часами, таймер, экран без Health Connect. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1400dp-xxhdpi")
class V551Test {
    init { Prefs(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()).lang = "ru" }
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun zonesLikeGoogleHealth() {
        // зоны пользователя в Google Health: 112 / 138 / 170 — максимум 190, пульс покоя 60
        val z = HrZones.Zones(190, 60, LocalDate.of(2026, 11, 24), true)
        assertEquals(listOf(112, 138, 170), listOf(z.moderate, z.vigorous, z.peak))
        assertEquals(listOf(0, 1, 1, 2, 3), listOf(111, 112, 137, 138, 170).map { z.zone(it) })
        // без пульса покоя — от максимума: 50 / 70 / 85 %
        val m = HrZones.Zones(190, null, null, true)
        assertEquals(listOf(95, 133, 161), listOf(m.moderate, m.vigorous, m.peak))
        // возраст из профиля, свой максимум приоритетнее
        val p = Profile(180.0, 1996, "m")
        assertEquals(190, HrZones.of(p, 0, LocalDate.of(2026, 11, 20) to 60, LocalDate.of(2026, 11, 24))!!.max)
        assertEquals(185, HrZones.of(p, 185, null, LocalDate.of(2026, 11, 24))!!.max)
        assertNull(HrZones.of(Profile(), 0, null))
    }

    @Test fun histAndActiveMinutes() {
        val t0 = 1_000_000_000L
        // 10 мин по 120 (умеренная), 5 мин по 150 (высокая); замеры каждые 5 с
        val samples = (0 until 120).map { t0 + it * 5_000L to 120 } + (0 until 60).map { t0 + 600_000 + it * 5_000L to 150 }
        val h = HrZones.histOf(samples, t0, t0 + 900_000)
        val z = HrZones.Zones(190, 60, null, true)
        val mins = HrZones.minutes(h, z)
        assertEquals(10, mins[1]); assertEquals(5, mins[2])
        assertEquals(130, HrZones.avg(h))
        val s = WorkoutSession("aerobics", 15, t0, hist = h)
        assertEquals(10 + 2 * 5, Cardio.active(s, z))
        // без пульса — по тесту разговором
        assertEquals(30, Cardio.active(WorkoutSession("aerobics", 30, effort = 2), null))
        assertEquals(60, Cardio.active(WorkoutSession("run", 30, effort = 3), null))
        assertEquals(0, Cardio.active(WorkoutSession("walk", 30, effort = 1), null))
        assertNull(Cardio.active(WorkoutSession("walk", 30), null))
        // средний пульс, записанный вручную
        assertEquals(40, Cardio.active(WorkoutSession("bike", 20, hr = 145), z))
        // провал в записи не растягивается дольше 2 минут
        assertEquals(125, HrZones.histOf(listOf(t0 to 100, t0 + 600_000 to 100), t0, t0 + 605_000)[100])
    }

    @Test fun jsonAndMerge() {
        val s = WorkoutSession("aerobics", 25, 1000L, "Аэробика дома", hr = 128, hrMax = 151, hist = mapOf(120 to 300, 140 to 600), dist = 1500, effort = 2)
        assertEquals(s, WorkoutSession.fromJson(s.toJson()))
        // таймер в приложении и тренировка на часах в то же время → одна запись: пульс с часов, вид и название — из приложения
        val start = 1_700_000_000_000L
        val watch = WorkoutSession("other", 26, start - 30_000, src = "com.google.android.apps.fitness", hist = mapOf(130 to 1500), hr = 130)
        val app = WorkoutSession("aerobics", 25, start, "Аэробика дома", effort = 2)
        val walk = WorkoutSession("walk", 15, start + 3_600_000, src = "x")
        val (merged, absorbed) = Workouts.merge(listOf(watch, walk), listOf(app))
        assertEquals(2, merged.size); assertEquals(setOf(0), absorbed)
        assertEquals("aerobics", merged[0].kind); assertEquals("Аэробика дома", merged[0].title); assertEquals(130, merged[0].hr)
        // не пересекается — остаются обе
        assertEquals(3, Workouts.merge(listOf(watch), listOf(app.copy(start = start + 7_200_000), walk.copy(start = null))).first.size)
        // MET: аэробика 4,8, «интенсивно» — 8,0
        assertEquals(4.8, Anthro.metOf(app), 1e-9); assertEquals(8.0, Anthro.metOf(app.copy(effort = 3)), 1e-9)
    }

    @Test fun timerPlan() {
        val home = CardioDraft(CardioPlans.Mode.HOME, 0, quiet = true, mainMin = 15)
        assertEquals(25 * 60, CardioPlans.totalSec(home.blocks))
        assertTrue(home.blocks.none { it.name.contains("Прыжки") || it.name.contains("с прыжком") })
        assertTrue(CardioDraft(CardioPlans.Mode.HOME, 0, quiet = false).blocks.any { it.name.contains("Прыжки") })
        assertEquals("Марш на месте", home.current(0)!!.second.name)
        // после разминки (5 мин) — работа
        assertEquals(1, home.current(300)!!.second.phase)
        val iv = CardioDraft(CardioPlans.Mode.INTERVALS, 0, work = 30, rest = 30, rounds = 10)
        assertEquals(300 + 10 * 30 + 9 * 30 + 300, CardioPlans.totalSec(iv.blocks))
        assertEquals("hiit", iv.kind)
        // «Дальше» двигает план, но не длительность; пауза не идёт в зачёт
        val d = CardioDraft(CardioPlans.Mode.HOME, 1_000_000, quiet = true)
        val now = 1_000_000 + 10_000L
        val sk = d.skip(now)
        assertEquals(60, sk.planSec(now)); assertEquals(10_000, sk.elapsedMs(now))
        val p = d.pause(now).resume(now + 5_000)
        assertEquals(10_000, p.elapsedMs(now + 5_000))
        assertEquals(sk, CardioDraft.parse(sk.toJson()))
    }

    @Test fun cardioScreenWithoutHealthConnect() {
        rule.onAllNodesWithText("Здоровье").let { it[it.fetchSemanticsNodes().size - 1] }.performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("hub_cardio").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("cardio_home").assertExists()
        // без Health Connect — пояснение и ручная запись, зон пульса нет
        rule.onNodeWithText("Без часов").assertExists()
        assertTrue(rule.onAllNodes(hasTestTag("cardio_zones")).fetchSemanticsNodes().isEmpty())
        rule.onNodeWithTag("cardio_home").performScrollToNode(hasText("Записать занятие вручную"))
        rule.onNodeWithText("Записать занятие вручную").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("cardio_home").performScrollToNode(hasText("Умеренно — могу говорить, но не петь"))
        rule.onNodeWithText("Умеренно — могу говорить, но не петь").performClick()
        rule.onNodeWithTag("cardio_home").performScrollToNode(hasTestTag("cardio_save"))
        rule.onNodeWithTag("cardio_save").performClick()
        rule.waitForIdle()
        val repo = rule.activity.repo
        val today = Cardio.sessions(repo, LocalDate.now(), LocalDate.now())
        assertEquals(1, today.size)
        assertEquals("aerobics", today[0].s.kind); assertEquals(30, today[0].s.min); assertEquals(2, today[0].s.effort)
        // минуты дня в пункте «Тренировки» пересчитались
        val ex = repo.template.value.first { it.source == Source.HC_EXERCISE }
        assertEquals("30", repo.entries.value[LocalDate.now().key()]!![ex.id])
        // активные минуты недели — по ощущению
        rule.onNodeWithTag("cardio_home").performScrollToNode(hasTestTag("cardio_week"))
        rule.onNodeWithText("активных мин из 150").assertExists()
    }

    @Test fun timerFlow() {
        rule.onAllNodesWithText("Здоровье").let { it[it.fetchSemanticsNodes().size - 1] }.performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("hub_cardio").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("cardio_home").performScrollToNode(hasText("Аэробика дома"))
        rule.onAllNodesWithText("Аэробика дома")[0].performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("cardio_go").performClick()
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithTag("cardio_timer").assertExists()
        rule.onNodeWithText("Марш на месте").assertExists()
        rule.onNodeWithText("Разминка", substring = true).assertExists()
        assertTrue(CardioDraft.parse(rule.activity.prefs.cardioDraft) != null)
        // закончить через 3 минуты — сохраняется с началом из таймера
        rule.runOnUiThread {
            val d = CardioDraft.parse(rule.activity.prefs.cardioDraft)!!
            rule.activity.prefs.cardioDraft = d.copy(startedAt = System.currentTimeMillis() - 180_000).toJson()
        }
        // уйти и вернуться: таймер восстанавливается из сохранённого
        rule.onNodeWithText("Прогресс").performClick(); rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithText("Занятия").performClick(); rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithTag("cardio_finish").performClick()
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithText("Занятие: 3 мин").assertExists()
        rule.onNodeWithTag("cardio_finish_save").performClick()
        rule.mainClock.advanceTimeBy(500)
        val today = Cardio.sessions(rule.activity.repo, LocalDate.now(), LocalDate.now())
        assertEquals(1, today.size); assertEquals("Аэробика дома", today[0].s.title); assertEquals(3, today[0].s.min)
        assertNull(rule.activity.prefs.cardioDraft)
    }
}
