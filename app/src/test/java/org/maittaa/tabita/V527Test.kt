package org.maittaa.tabita

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 5.27: свой продукт (калории по БЖУ, клетчатка/сахара), оценка полезности, похожий ГИ, разбор БЖУ по приёмам. */
@RunWith(RobolectricTestRunner::class)
class V527Test {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    @Before fun ru() { I18n.lang = "ru" }

    private fun food(ru: String) = FoodDb.all.first { it.ru.startsWith(ru) }

    @Test fun score() {
        val chicken = FoodDb.all.first { it.ru.contains("грудка", true) }
        val sugar = FoodDb.all.first { it.ru == "Сахар" || it.ru.startsWith("Сахар") }
        val butter = FoodDb.all.first { it.ru.startsWith("Масло сливочное") }
        val sc = FoodScore.score(chicken)!!; val ss = FoodScore.score(sugar)!!; val sb = FoodScore.score(butter)!!
        assertTrue("$sc", sc >= 8); assertTrue("$ss", ss <= 2); assertTrue("$sb", sb <= 4)
        // вода — без оценки
        assertNull(FoodScore.score(Food("Вода", "Water", "drink", 0.0, 0.0, 0.0, 0.0, ml = true)))
        // клетчатка с этикетки повышает оценку
        val a = Food.fromLabel("Хлопья", 40.0, 150.0, 4.0, 2.0, 28.0, 55, null, null)
        val b = Food.fromLabel("Хлопья", 40.0, 150.0, 4.0, 2.0, 28.0, 55, null, null, fib = 4.0)
        assertTrue(FoodScore.score(b)!! > FoodScore.score(a)!!)
        // сахара снижают
        val c = Food.fromLabel("Хлопья", 40.0, 150.0, 4.0, 2.0, 28.0, 55, null, null, sug = 12.0)
        assertTrue(FoodScore.score(c)!! <= FoodScore.score(a)!!)
        assertEquals(0.0, FoodScore.parts(c)!!.first { it.name == "Сахара" }.v!!, 0.001)
    }

    @Test fun myFood() {
        assertEquals(4 * 12 + 9 * 6 + 4 * 60.0, Food.kcalOf(12.0, 6.0, 60.0), 0.01)
        val f = Food.fromLabel("Овсянка", 50.0, Food.kcalOf(6.0, 3.0, 30.0), 6.0, 3.0, 30.0, 55, "", null, fib = 5.0, giEst = true)
        assertEquals(10.0, f.fib!!, 0.01)
        val back = Food.fromJson(f.toJson())
        assertEquals(10.0, back.fib!!, 0.01); assertTrue(back.giEst); assertEquals(55, back.gi)
        assertNull(back.sug)
        // похожий ГИ: «гречневая крупа» → гречка
        val sim = FoodDb.similarGi("Гречка ядрица")
        assertTrue(sim.toString(), sim.isNotEmpty() && sim.all { it.gi != null })
        assertTrue(FoodDb.similarGi("zz").isEmpty())
    }

    @Test fun seedsGetFibre() {
        val f1 = FoodDb.seed.first { it.id == "seed_f1" }
        assertEquals(3.0 * 100 / 26, f1.fib!!, 0.05)
        // старые копии без клетчатки получают её из этикетки
        val repo = Repo(ctx)
        val old = org.json.JSONObject(repo.toJson())
        val arr = old.getJSONArray("myFoods")
        for (i in 0 until arr.length()) { arr.getJSONObject(i).remove("fib"); arr.getJSONObject(i).remove("sug") }
        assertTrue(repo.loadJson(old.toString()))
        assertNotNull(repo.myFoods.value.first { it.id == "seed_f1" }.fib)
    }

    @Test fun prefs() {
        val p = Prefs(ctx)
        p.foodFav = setOf("seed_f1", "Гречка"); p.foodUse = mapOf("seed_f1" to 3)
        assertEquals(setOf("seed_f1", "Гречка"), p.foodFav); assertEquals(3, p.foodUse["seed_f1"])
    }

    @Test fun absorption() {
        val meals = listOf(
            Meal(kind = "Завтрак", kcal = 400.0, protein = 30.0, fat = 10.0, carbs = 40.0, gi = 50.0),
            Meal(kind = "Обед", kcal = 700.0, protein = 10.0, fat = 25.0, carbs = 90.0, gi = 70.0),
        )
        val r = Absorption.rows(meals)
        assertEquals(2, r.size)
        assertTrue(r[0].proteinOk(80.0)); assertTrue(!r[1].proteinOk(80.0))
        assertEquals(63.0, r[1].gl!!, 0.1)
        assertTrue(!r[1].proteinOk(null)); assertTrue(r[0].proteinOk(null))
    }
}
