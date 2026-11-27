package org.maittaa.tabita

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalTime

/** 5.29: программа под цель — нормы, меню, добор белка; советы под цель. */
@RunWith(RobolectricTestRunner::class)
class V529Test {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    @Before fun ru() { I18n.lang = "ru" }

    private fun stats(w: Double = 95.0, burn: Double? = 2800.0) = PlanStats(w, 1.83, w / (1.83 * 1.83), null, null, null, null, null, null, null,
        0.0, 0.0, null, burn, 0, 0, 0, 0, 7)

    @Test fun targets() {
        val tl = Program.targets(Goal.LOSE, stats())!!
        assertEquals(2310.0, tl.kcal!!, 0.1)          // 2800 × 0,825
        assertEquals(170.0, tl.protein, 0.1)           // 95 × 1,8
        val tm = Program.targets(Goal.MUSCLE, stats(75.0))!!
        assertEquals(135.0, tm.protein, 0.1)           // 75 × 1,8
        // ИМТ ≥ 30 — белок от веса при ИМТ 27
        val to = Program.targets(Goal.LOSE, stats(110.0))!!
        assertEquals(Math.round(27 * 1.83 * 1.83 * 1.8 / 5) * 5.0, to.protein, 0.1)
    }

    @Test fun menu() {
        val p = Prefs(ctx); p.foodUse = emptyMap(); p.foodFav = emptySet()
        for (g in Goal.entries) {
            val tg = Program.targets(g, stats())!!
            val m = Program.menu(g, tg, null, p)
            assertEquals(4, m.size)
            val k = m.sumOf { it.kcal }; val pr = m.sumOf { it.p }
            assertTrue("$g kcal $k vs ${tg.kcal}", k in tg.kcal!! * 0.8..tg.kcal * 1.1)
            assertTrue("$g protein $pr vs ${tg.protein}", pr >= tg.protein * 0.75 && (g == Goal.WELLBEING || pr <= tg.protein * 1.4))
            assertTrue(m.all { it.items.isNotEmpty() })
        }
        // свои продукты — первыми: «Формула 1» на завтрак
        Repo(ctx); p.foodUse = mapOf("seed_f1" to 10)
        val tg = Program.targets(Goal.MUSCLE, stats())!!
        val br = Program.menu(Goal.MUSCLE, tg, null, p).first()
        assertEquals("seed_f1", br.items.first().food.id)
        assertTrue(br.items.first().amount().contains("мерная ложка"))
        // другой вариант — другой продукт
        val v1 = Program.menu(Goal.MUSCLE, tg, null, p, mapOf(Program.Slot.LUNCH to 1))[1].items.first().food
        val v0 = Program.menu(Goal.MUSCLE, tg, null, p)[1].items.first().food
        assertTrue(v0 != v1)
    }

    @Test fun options() {
        val p = Prefs(ctx); p.foodUse = emptyMap()
        val o = Program.proteinOptions(p)
        assertTrue(o.size >= 4)
        assertTrue(o.all { it.p in 18.0..40.0 })
        assertTrue(o.zipWithNext().all { (a, b) -> a.kcal <= b.kcal })
        assertTrue(Program.swaps().all { it.save > 0 })
        val l = Program.left(Goal.MUSCLE, Program.targets(Goal.MUSCLE, stats())!!, 1000.0, 50.0, p)
        assertTrue(l.fix.size == 2 && l.protein > 100)
    }

    @Test fun tipsFollowGoal() {
        val repo = Repo(ctx); val prefs = Prefs(ctx)
        repo.setProfile(Profile(183.0, 1996, "m"))
        val today = LocalDate.now()
        repo.setValue(today.toString(), "weight", "95")
        val big = Meal(kind = "Обед", kcal = 3600.0, protein = 120.0, fat = 150.0, carbs = 400.0)
        repo.setValue(today.toString(), "food", mealsToString(listOf(big)))
        prefs.planGoal = "lose"
        val lose = Tips.build(repo, prefs, today, LocalTime.of(18, 0))
        assertTrue(lose.map { it.id }.toString(), lose.any { it.id == "food_over" && it.title.startsWith("Лимит дня") })
        prefs.planGoal = "muscle"
        val mus = Tips.build(repo, prefs, today, LocalTime.of(18, 0))
        assertTrue(mus.none { it.id == "food_over" })
        prefs.planGoal = ""
    }
}
