package org.maittaa.tabita

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class WellbeingTest {
    private fun b(id: String) = Wellbeing.block(id)!!
    private fun all(id: String, pick: Int) = List(b(id).questions.size) { pick }

    @Test fun bankIsComplete() {
        assertEquals(listOf(8, 7, 4, 6, 5, 4, 7), Wellbeing.blocks.map { it.questions.size })
        assertEquals(30, Wellbeing.blocks.filter { it.id in Wellbeing.defaultSelection }.sumOf { it.questions.size })
        Wellbeing.blocks.forEach { bl ->
            bl.questions.forEach { q ->
                assertTrue(q.text.ru.isNotBlank() && q.text.en.isNotBlank())
                q.answers.forEach { assertTrue(it.text.ru.isNotBlank() && it.text.en.isNotBlank()) }
            }
            assertTrue(bl.title.en.isNotBlank() && bl.prompt.en.isNotBlank())
        }
    }

    @Test fun scoring() {
        assertEquals(24.0, Wellbeing.score(b("phq8"), all("phq8", 3)), 0.0)
        assertEquals(3, b("phq8").band(24.0).level)
        assertEquals("лёгкие", b("phq8").band(Wellbeing.score(b("phq8"), listOf(1, 1, 1, 1, 1, 0, 0, 0))).name.ru)
        assertEquals(21.0, Wellbeing.score(b("gad7"), all("gad7", 3)), 0.0)
        assertEquals("умеренные", b("gad7").band(10.0).name.ru)
        // WHO-5: первый вариант «всё время» = 5 баллов → 100
        assertEquals(100.0, Wellbeing.score(b("who5"), all("who5", 0)), 0.0)
        assertEquals(0.0, Wellbeing.score(b("who5"), all("who5", 5)), 0.0)
        assertEquals(3, b("who5").band(20.0).level)
        // CBI: «Иногда» = 50 → умеренное
        assertEquals(50.0, Wellbeing.score(b("cbi"), all("cbi", 2)), 0.0)
        assertEquals("умеренное", b("cbi").band(50.0).name.ru)
        // PSS-4: 2-й и 3-й вопросы обратные
        assertEquals(8.0, Wellbeing.score(b("pss4"), all("pss4", 0)), 0.0)
        // паника: без первого «да» — ноль
        assertEquals(0.0, Wellbeing.score(b("panic"), listOf(0, 1, 1, 1)), 0.0)
        assertEquals(1, b("panic").band(Wellbeing.score(b("panic"), listOf(1, 0, 0, 0))).level)
        assertEquals(2, b("panic").band(Wellbeing.score(b("panic"), all("panic", 1))).level)
        assertEquals(28.0, Wellbeing.score(b("isi"), all("isi", 4)), 0.0)
    }

    @Test fun draftFlow() {
        val d = WDraft.start(listOf("gad7", "phq8"))
        assertEquals(listOf("phq8", "gad7"), d.blocks)
        assertEquals(15, d.picks.size)
        assertNull(d.results())
        val full = d.copy(picks = List(15) { 1 }, pos = 14)
        val r = full.results()!!
        assertEquals(8.0, r["phq8"]!!.score, 0.0); assertEquals(7.0, r["gad7"]!!.score, 0.0)
        assertEquals(full, WDraft.parse(full.toJson()))
    }

    @Test fun daysLeft() {
        val c = listOf(Checkup("2026-11-01", 1, emptyMap()))
        assertEquals(1, Wellbeing.daysLeft(c, 14, LocalDate.of(2026, 11, 14)))
        assertEquals(0, Wellbeing.daysLeft(c, 14, LocalDate.of(2026, 11, 15)))
        assertEquals(0, Wellbeing.daysLeft(emptyList(), 14))
    }

    @Test fun repoStoresAndMarksDay() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        val r = Repo(ctx)
        assertTrue(r.template.value.any { it.type == ItemType.WELLBEING })
        val res = WDraft.start(listOf("gad7")).copy(picks = List(7) { 2 }).results()!!
        r.addCheckup(Checkup("2026-11-14", 5, res))
        assertEquals("1", r.entries.value["2026-11-14"]!!["wb"])
        val r2 = Repo(ctx); assertTrue(r2.loadJson(r.toJson()))
        assertEquals(14.0, r2.checkups.value.single().results["gad7"]!!.score, 0.0)
        // старые данные (v3) получают плитку чекапа
        val old = JSONObject(r.toJson()).apply {
            put("version", 3)
            put("template", org.json.JSONArray(defaultTemplate().filter { it.type != ItemType.WELLBEING }.map { it.toJson() }))
        }
        val r3 = Repo(ctx); assertTrue(r3.loadJson(old.toString()))
        assertNotNull(r3.template.value.firstOrNull { it.id == "wb" })
        assertEquals("blocks", r3.groupOf(r3.template.value.first { it.id == "wb" }).id)
    }

    @Test fun tileStatus() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        val r = Repo(ctx); val p = Prefs(ctx)
        r.addCheckup(Checkup("2026-11-01", 1, mapOf("gad7" to BlockResult(11.0, List(7) { 1 }))))
        assertEquals(1, wbStatus(r, p, "2026-11-14").daysLeft)
        assertTrue(wbStatus(r, p, "2026-11-15").due)
        val st = wbStatus(r, p, "2026-11-01")
        assertTrue(st.accent); assertNotNull(st.takenToday)
        I18n.lang = "ru"
        assertEquals("тревога 11 · умеренные", wbBrief(st.takenToday!!))
    }

    @Test fun statOrder() {
        val cards = listOf("a", "b", "c", "d").map { NoteCard(it, "2026-11") }
        // 5.19: новая карточка встаёт после своего соседа по умолчанию (b — после a, d — после c)
        assertEquals(listOf("c", "d", "a", "b"), ordered(cards, listOf("c", "a")).map { it.key })
        assertEquals(listOf("a", "b", "c", "d"), ordered(cards, emptyList()).map { it.key })
        val p = Prefs(ApplicationProvider.getApplicationContext())
        p.statOrder = listOf("x", "y"); assertEquals(listOf("x", "y"), p.statOrder)
    }
}
