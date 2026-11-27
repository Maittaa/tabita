package org.maittaa.tabita

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/** 5.19: профиль (рост, год рождения, пол) и расчёты по нему. */
@RunWith(RobolectricTestRunner::class)
class V519Test {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val day = LocalDate.of(2026, 11, 17)
    private val me = Profile(183.0, 1996, "m")

    @Test fun formulas() {
        assertEquals(30, me.age(day))
        assertEquals(20.9, Anthro.bmi(70.0, 1.83), 0.05)
        // Миффлин: 10·70 + 6,25·183 − 5·30 + 5
        assertEquals(1698.75, Anthro.bmr(me, 70.0, day)!!, 0.01)
        assertEquals(1698.75 - 166, Anthro.bmr(me.copy(sex = "f"), 70.0, day)!!, 0.01)
        assertEquals(0.759, Anthro.strideM(me)!!, 0.001)
        // 60 кг безжировой массы при 183 см
        assertEquals(17.9, Anthro.ffmi(60.0, 1.83), 0.05)
        assertEquals(17.9 - 0.18, Anthro.ffmiNorm(60.0, 1.83), 0.05)
        val (lo, hi) = Anthro.normalWeight(1.83)
        assertEquals(62.0, lo, 0.1); assertEquals(83.7, hi, 0.1)
        // без роста или возраста — не считаем
        assertNull(Anthro.bmr(Profile(null, 1996, "m"), 70.0, day))
        assertNull(Anthro.bmr(Profile(183.0, null, "m"), 70.0, day))
        assertNull(Profile(80.0).h)
        // расход: 3000 шагов уже в коэффициенте; 30 мин силовой = (5−1)·70·0,5
        val e = Anthro.energy(me, 70.0, day, 13000.0, listOf(WorkoutSession("strength", 30)))!!
        assertEquals(1698.75 * 1.2, e.base, 0.01)
        assertEquals(0.5 * 70 * 10000 * 0.75945 / 1000, e.steps, 0.5)
        assertEquals(140.0, e.workouts, 0.01)
        // ходьба тренировкой не считается — её дают шаги
        assertEquals(0.0, Anthro.energy(me, 70.0, day, null, listOf(WorkoutSession("walk", 60)))!!.workouts, 0.01)
    }

    @Test fun storedAndExported() {
        val repo = Repo(ctx)
        repo.setProfile(me)
        repo.setValue("2026-11-15", "weight", "70")
        repo.setValue("2026-11-17", "steps", "8000")
        val json = repo.toJson()
        val r2 = Repo(ctx); r2.loadJson(json)
        assertEquals(me, r2.profile.value)
        // старая копия без профиля не стирает текущий
        val old = JSONObject(json).apply { remove("profile") }.toString()
        r2.loadJson(old)
        assertEquals(me, r2.profile.value)
        // «Удалить все данные» профиль не трогает
        r2.wipeAll(); assertEquals(me, r2.profile.value)

        val out = JSONObject(TrainingProvider.build(json, 35, day))
        println(out.toString(2))
        val p = out.getJSONObject("profile")
        assertEquals(183.0, p.getDouble("h"), 0.01); assertEquals(30, p.getInt("age")); assertEquals("m", p.getString("sex"))
        val days = out.getJSONArray("days")
        val byDate = (0 until days.length()).associate { days.getJSONObject(it).let { d -> d.getString("d") to d } }
        assertEquals(20.9, byDate["2026-11-15"]!!.getDouble("bmi"), 0.05)
        // 5.20.1: БЖУ по дням
        val m = repo.template.value.first { it.type == ItemType.MEALS }
        repo.setValue("2026-11-16", m.id, mealsToString(listOf(Meal(kind = "Обед", kcal = 500.0, protein = 30.0, fat = 15.0, carbs = 60.0))))
        val out2 = JSONObject(TrainingProvider.build(repo.toJson(), 35, day))
        val d16 = (0 until out2.getJSONArray("days").length()).map { out2.getJSONArray("days").getJSONObject(it) }.first { it.getString("d") == "2026-11-16" }
        assertEquals(30, d16.getInt("p")); assertEquals(15, d16.getInt("f")); assertEquals(60, d16.getInt("c")); assertEquals(500, d16.getInt("kcal"))
        // вес 15-го переносится на 17-е для расхода
        assertNotNull(byDate["2026-11-17"]!!.opt("tdee"))
    }
}
