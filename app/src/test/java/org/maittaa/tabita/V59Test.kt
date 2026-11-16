package org.maittaa.tabita

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 5.9: общая оценка самочувствия, период в вопросах. */
@RunWith(RobolectricTestRunner::class)
class V59Test {
    @Test fun periodPhrases() {
        val phq = Wellbeing.block("phq8")!!
        assertEquals("Как часто за последнюю неделю Вас беспокоили следующие проблемы?", Wellbeing.period(phq.prompt.ru, 7, false))
        assertTrue(Wellbeing.period(phq.prompt.ru, 14, false).contains("последние 2 недели"))
        assertTrue(Wellbeing.period(phq.prompt.en, 30, true).startsWith("Over the last month"))
        // «Более недели» имеет смысл только для 2 недель
        assertEquals("Более недели", Wellbeing.period(phq.questions[0].answers[2].text.ru, 14, false))
        assertEquals("Более половины дней", Wellbeing.period(phq.questions[0].answers[2].text.ru, 7, false))
        // нигде не осталось жёстко заданных сроков и незаменённых меток
        Wellbeing.blocks.forEach { b ->
            (listOf(b.prompt) + b.questions.map { it.text } + b.questions.flatMap { q -> q.answers.map { it.text } }).forEach { tx ->
                listOf(7, 14, 30).forEach { p ->
                    listOf(Wellbeing.period(tx.ru, p, false), Wellbeing.period(tx.en, p, true)).forEach { s ->
                        assertFalse(s, s.contains("{"))
                        assertFalse(s, s.contains("4 недели") || s.contains("4 weeks"))
                    }
                }
            }
        }
    }

    @Test fun overall() {
        assertNull(Wellbeing.overall(emptyMap()))
        // PHQ-8 0 из 24 → 100; WHO-5 50 → 50; CBI 75 (хуже) → 25
        val r = mapOf("phq8" to BlockResult(0.0, emptyList()), "who5" to BlockResult(50.0, emptyList()), "cbi" to BlockResult(75.0, emptyList()))
        assertEquals(175.0 / 3, Wellbeing.overall(r)!!, 1e-9)
        assertEquals(1, Wellbeing.total.band(58.3).level)
        assertEquals(0, Wellbeing.total.band(80.0).level)
        assertEquals(3, Wellbeing.total.band(10.0).level)
    }

    @Test fun checkupKeepsPeriod() {
        val c = Checkup("2026-11-15", 1L, mapOf("gad7" to BlockResult(3.0, listOf(1, 1, 1, 0, 0, 0, 0))), 7)
        assertEquals(7, Checkup.fromJson(c.toJson()).period)
        // старые прохождения без периода — 2 недели
        val old = JSONObject(c.toJson().toString()).apply { remove("p") }
        assertEquals(14, Checkup.fromJson(old).period)
    }
}
