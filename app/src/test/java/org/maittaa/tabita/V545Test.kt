package org.maittaa.tabita

import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
import java.time.ZoneId

/** 5.45: режим по данным — время еды по фото, привычное время тренировок, выбор источников, факт за сегодня. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1600dp-xxhdpi")
class V545Test {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val z = ZoneId.systemDefault()
    private val r = DayRhythm(7 * 60, 23 * 60, 0, null, 0)
    private val today = LocalDate.of(2026, 11, 23)   // понедельник
    private fun ms(d: LocalDate, h: Int, m: Int = 0) = d.atTime(h, m).atZone(z).toInstant().toEpochMilli()
    private fun prefs() = rule.activity.prefs.apply { planGoal = ""; rhythmSrc = RhythmSrc.ALL }

    private fun meal(d: LocalDate, kind: String, h: Int, m: Int = 0) =
        rule.activity.repo.setValue(d.key(), "food", mealsToString(parseMeals(rule.activity.repo.entries.value[d.key()]?.get("food")) +
            Meal(kind = kind, text = "x", at = ms(d, h, m))))

    @Test fun exifParse() {
        assertEquals(ms(today, 13, 5), Photos.parseExif("2026:11:23 13:05:00"))
        assertNull(Photos.parseExif("bad"))
        val m = Meal(kind = "Обед", at = 123L)
        assertEquals(123L, Meal.fromJson(m.toJson()).at)
        assertNull(Meal.fromJson(Meal(kind = "Обед").toJson()).at)
    }

    @Test fun mealHabitsShapePlan() {
        val p = prefs(); val repo = rule.activity.repo
        // обед в 13:10–13:20 и ужин в 22:00 — несколько дней
        (1..4).forEach { i -> val d = today.minusDays(i.toLong()); meal(d, "Обед", 13, 10 + i * 2); meal(d, "Ужин", 22) }
        val h = Habits.meals(repo, p, today)
        assertNotNull(h[Program.Slot.LUNCH])
        assertEquals(13 * 60 + 15, h[Program.Slot.LUNCH]!!.min)       // медиана 13:12,14,16,18
        val info = DayPlan.of(repo, p, today, r, null)
        assertEquals(13 * 60 + 15, info.meals[Program.Slot.LUNCH])
        // поздний ужин по привычке: план — не позже отбой − 3 ч, плюс совет сдвигать
        assertEquals(20 * 60, info.meals[Program.Slot.DINNER])
        assertTrue(info.notes.joinToString(), info.notes.any { it.contains("22:00") })
        // источник выключен — рекомендованное время
        p.rhythmSrc = RhythmSrc.ALL - RhythmSrc.MEAL_PHOTO
        assertTrue(Habits.meals(repo, p, today).isEmpty())
        assertEquals(7 * 60 + 330, DayPlan.of(repo, p, today, r, null).meals[Program.Slot.LUNCH])
        p.rhythmSrc = RhythmSrc.ALL
    }

    @Test fun trainingHabitFromWatchAndApp() {
        val p = prefs(); val repo = rule.activity.repo
        // силовые с часов по понедельникам в 18:30
        listOf(7L, 14L).forEach { w ->
            val d = today.minusDays(w)
            repo.setValue(d.key(), Workouts.syncedKey("ex"), Workouts.toJson(listOf(WorkoutSession("strength", 50, start = ms(d, 18, 30))))!!)
        }
        val h = Habits.training(repo, p, today)
        assertNotNull(h); assertEquals(18 * 60 + 30, h!!.min); assertEquals("dow", h.basis)
        val info = DayPlan.of(repo, p, today, r, DayPlan.TrainPlan("Низ", 50, gym = false))
        assertEquals(18 * 60 + 30, info.events.first { it.kind == DayPlan.Kind.TRAIN }.at)
        // от обеда (12:30) до тренировки ≥ 4 ч — перекус за 1 ч 15 мин
        assertEquals(17 * 60 + 15, info.meals[Program.Slot.SNACK])
        // ужин после тренировки
        assertEquals(20 * 60, info.meals[Program.Slot.DINNER])   // 20:05 после тренировки, но не позже отбой − 3 ч
        // часы выключены, тренировки из приложения — в 7:30 по будням
        p.rhythmSrc = RhythmSrc.ALL - RhythmSrc.HC_TRAIN
        Habits.appTrainings = { listOf(ms(today.minusDays(3), 7, 30), ms(today.minusDays(4), 7, 30)) }   // пт и чт
        val a = Habits.training(repo, p, today)
        assertEquals(7 * 60 + 30, a!!.min); assertEquals("weekday", a.basis)
        Habits.appTrainings = null; p.rhythmSrc = RhythmSrc.ALL
    }

    @Test fun todaysFactMarksDone() {
        val p = prefs(); val repo = rule.activity.repo
        meal(today, "Завтрак", 8, 5)
        val e = DayPlan.of(repo, p, today, r, null).events.first { it.title.startsWith("Breakfast") }
        assertTrue(e.done); assertEquals(8 * 60 + 5, e.at)
        assertFalse(DayPlan.of(repo, p, today, r, null).events.first { it.title.startsWith("Lunch") }.done)
    }

    @Test fun sourcesPersist() {
        val p = prefs()
        p.rhythmSrc = setOf(RhythmSrc.SLEEP)
        assertEquals(setOf(RhythmSrc.SLEEP), p.rhythmSrc)
        p.rhythmSrc = emptySet(); assertTrue(p.rhythmSrc.isEmpty())
        p.rhythmSrc = RhythmSrc.ALL
        assertTrue(Changelog.all.any { it.version == "5.45" })
    }

    @Test fun sleepEndsAtTomorrowWake() {
        // сегодня встал в 8:03, отбой 22:15, завтра будильник 5:30
        val rr = DayRhythm(8 * 60 + 3, 22 * 60 + 15, 3, null, 0, nextWake = 5 * 60 + 30, alarm = true)
        val sleep = rr.lanes.first { it.key == "sleep" }
        assertEquals(22 * 60 + 15, rr.abs(sleep.from))
        assertEquals(5 * 60 + 30, rr.abs(sleep.to))
        assertEquals(21 * 60 + 27, rr.nextRel)
        // без будильника и тот же подъём — сутки
        assertEquals(1440, DayRhythm(7 * 60, 23 * 60, 0, null, 0).nextRel)
        assertTrue(Changelog.all.any { it.version == "5.45.1" })
    }
}
