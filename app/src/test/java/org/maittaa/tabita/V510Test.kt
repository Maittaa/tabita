package org.maittaa.tabita

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/** 5.10: знак влияния на индекс. */
@RunWith(RobolectricTestRunner::class)
class V510Test {
    private val rate = TemplateItem("rate", "Оценка", ItemType.SCALE, min = 1.0, max = 10.0)
    private val nc = TemplateItem("nc", "NC", ItemType.CHECK)
    private val smoke = TemplateItem("smoke", "Сигарета", ItemType.CHECK)
    private val use = TemplateItem("use", "Что использовалось", ItemType.MULTI, options = listOf("Кофе", "Чай"))
    private val tpl = listOf(rate, nc, smoke, use)
    private val day = LocalDate.of(2026, 11, 10)

    private fun idx(day0: Map<String, String>, cfg: MonthCfg): Double? =
        MonthData(tpl, mapOf(day.key() to day0), day, cfg, today = day).dayIndex(day).value

    @Test fun negativeCheckAndOption() {
        val keys = setOf("rate", "nc", "smoke", "use:Кофе")
        val base = MonthCfg(index = keys)
        val neg = base.copy(negative = setOf("smoke", "use:Кофе"))
        // оценка 10 (=1.0); NC отмечен (+1); кофе 0 → не было
        val clean = mapOf("rate" to "10", "nc" to "1", "use" to countsToString(mapOf("Чай" to 1))!!)
        assertEquals(100.0, idx(clean, neg)!!, 1e-9)
        // кофе был: минус → 0 в индексе; без минуса вариант «было» = 1
        val coffee = clean + ("use" to countsToString(mapOf("Кофе" to 1))!!)
        assertEquals(200.0 / 3 * 1, idx(coffee, neg)!!, 1e-9)
        assertEquals(100.0, idx(coffee, base)!!, 1e-9)
        // сигарета отмечена: минус → 0; не отмечена — не считается (как бонус наоборот)
        assertEquals(75.0, idx(clean + ("smoke" to "1"), neg)!!, 1e-9)
        // «каждый день» для вредного: не курил — плюс
        assertEquals(100.0, idx(clean, neg.copy(daily = setOf("smoke")))!!, 1e-9)
        // лимит кофе 3: одна чашка → 2/3
        val lim = neg.copy(goals = mapOf("use:Кофе" to 3.0))
        assertEquals((1.0 + 1.0 + 2.0 / 3) / 3 * 100, idx(coffee, lim)!!, 1e-9)
    }

    @Test fun withEffectAndJson() {
        val all = setOf("rate", "nc")
        val c1 = MonthCfg(weekly = mapOf("smoke" to 3)).withEffect("smoke", false, all)
        assertEquals(setOf("rate", "nc", "smoke"), c1.index)
        assertTrue("smoke" in c1.negative); assertNull(c1.weekly["smoke"])
        val c2 = c1.withEffect("smoke", true, c1.index!!)
        assertTrue("smoke" !in c2.negative && "smoke" in c2.index!!)
        val c3 = c2.withEffect("smoke", null, c2.index!!)
        assertTrue("smoke" !in c3.index!!)
        assertEquals(setOf("use:Кофе"), MonthCfg.parse(MonthCfg(negative = setOf("use:Кофе")).toJson()).negative)
        assertEquals(emptySet<String>(), MonthCfg.parse("{}").negative)
    }
}
