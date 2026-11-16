package org.maittaa.tabita

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 5.17: сцепленные приёмы пищи — одна общая карточка и один пик сахара. */
@RunWith(RobolectricTestRunner::class)
class LinkedMealsTest {
    private val pasta = Meal(id = "a", kind = "Обед", kcal = 350.0, protein = 15.0, fat = 15.0, carbs = 40.0, gi = 55.0, gl = 22.0)
    private val coffee = Meal(id = "b", kind = "Обед", kcal = 310.0, protein = 5.0, fat = 15.0, carbs = 41.0, gi = 60.0, gl = 25.0)

    @Test fun combine() {
        val c = combineMeals(listOf(pasta.copy(link = "a"), coffee.copy(link = "a")))
        assertEquals(660.0, c.kcal!!, 1e-9); assertEquals(81.0, c.carbs!!, 1e-9); assertEquals(47.0, c.gl!!, 1e-9)
        assertEquals((55.0 * 40 + 60.0 * 41) / 81, c.gi!!, 1e-9)
        assertEquals(2, mealUnits(listOf(pasta, coffee)).size)
        assertEquals(1, mealUnits(listOf(pasta.copy(link = "a"), coffee.copy(link = "a"))).size)
    }

    @Test fun onePeakForLinkedLunch() {
        val sep = Glycemic.day(listOf(pasta, coffee))!!
        val lnk = Glycemic.day(listOf(pasta.copy(link = "a"), coffee.copy(link = "a")))!!
        assertEquals(sep.load, lnk.load, 1e-9)
        assertEquals(47.0, lnk.peak!!.glEff!!, 1e-9)
        // ср. ГИ 57,5 → −2,5; раздельно пики −1,5 −3,75; вместе один пик (47−20)·0,75 → −15 (потолок)
        assertEquals(92, sep.score); assertEquals(82, lnk.score)
    }

    @Test fun jsonAndTextCleanup() {
        val m = parseMeals(mealsToString(listOf(coffee.copy(link = "a", text = "[\"Кофе 3 в 1\",\"шоколад\"]"))))[0]
        assertEquals("a", m.link); assertEquals("Кофе 3 в 1, шоколад", m.text)
        assertEquals("", parseMeals(mealsToString(listOf(pasta)))[0].link)
        assertEquals("просто [текст]", cleanMealText("просто [текст]"))
    }
}
