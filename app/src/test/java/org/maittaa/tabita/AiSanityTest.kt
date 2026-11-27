package org.maittaa.tabita

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 5.19: проверка ответа ИИ — белок и жир не «досочиняются». */
@RunWith(RobolectricTestRunner::class)
class AiSanityTest {
    @Test fun herbalDrink() {
        // как ответил GigaChat: 3 г травяного напитка, а белка 4 г
        val e = Ai.parse("""{"items":[{"name":"Травяной напиток Herbalife","g":3,"kcal":12,"protein_g":4,"fat_g":0.5,"carbs_g":1},
            {"name":"Вода","g":250,"kcal":0,"protein_g":0,"fat_g":0,"carbs_g":0}],
            "description":"Травяной напиток 3 г, вода 250 мл","kcal":12,"protein_g":4,"fat_g":0.5,"carbs_g":1,"gi":0,"gl":0}""")
        assertEquals(12.0, e.kcal, 0.01)
        // проверка не знает состав — она только не даёт превысить вес порошка; основное исправление — в промпте
        assertTrue("белок ${e.protein}", e.protein!! < 3.0)
        assertTrue("масса БЖУ ≤ 3 г", e.protein!! + e.fat!! + e.carbs!! <= 3.01)
    }

    @Test fun normalMealUntouched() {
        val e = Ai.parse("""{"items":[{"name":"Гречка","g":200,"kcal":220,"protein_g":8,"fat_g":2,"carbs_g":42},
            {"name":"Курица","g":150,"kcal":250,"protein_g":45,"fat_g":7,"carbs_g":0}],"description":"гречка, курица","gi":50}""")
        assertEquals(470.0, e.kcal, 0.01)
        assertEquals(53.0, e.protein!!, 0.01); assertEquals(9.0, e.fat!!, 0.01); assertEquals(42.0, e.carbs!!, 0.01)
        assertEquals(21.0, e.gl!!, 0.01)
    }

    @Test fun oldFormatTotalsChecked() {
        // нет списка: 10 ккал при 4 г белка и 2 г жира — БЖУ уменьшаются под калории
        val e = Ai.parse("""{"description":"чай","kcal":10,"protein_g":4,"fat_g":2,"carbs_g":0}""")
        assertTrue(4 * e.protein!! + 9 * e.fat!! <= 10 * 1.25 + 3 + 0.01)
    }
}
