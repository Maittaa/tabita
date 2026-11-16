package org.maittaa.tabita

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class GroupsTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test fun defaultGroupsAndTiles() {
        val r = Repo(ctx)
        val t = r.template.value
        fun g(id: String) = r.groupOf(t.first { it.id == id }).id
        assertEquals("checks", g("work")); assertEquals("values", g("sleep"))
        assertEquals("lists", g("used")); assertEquals("blocks", g("water"))
        assertTrue(r.tiles(t, emptyList()).any { it.id == "checks" })
    }

    @Test fun newCheckGroupIsOwnTile() {
        val r = Repo(ctx)
        val g = r.addGroup("Привычки", GroupKind.CHECKS)
        r.upsertItem(r.template.value.first { it.id == "nc" }.copy(group = g.id))
        r.upsertItem(r.template.value.first { it.id == "np" }.copy(group = g.id))
        val tiles = r.tiles(r.template.value, r.layout.value).map { it.id }
        assertTrue(g.id in tiles); assertTrue("checks" in tiles)
        assertEquals(listOf("nc", "np"), groupChecks(r, r.template.value, g.id).map { it.id })
        // число в группу отметок не попадает
        r.upsertItem(r.template.value.first { it.id == "steps" }.copy(group = g.id))
        assertEquals("values", r.groupOf(r.template.value.first { it.id == "steps" }).id)
        assertFalse(r.canDeleteGroup(g.id))
        val empty = r.addGroup("Пустая", GroupKind.CHECKS)
        assertTrue(r.canDeleteGroup(empty.id)); r.deleteGroup(empty.id)
        assertTrue(r.groups.value.none { it.id == empty.id })
        assertFalse(r.canDeleteGroup("blocks").and(r.template.value.isEmpty()))
    }

    @Test fun arrowsMoveTilesAndDragReordersList() {
        val r = Repo(ctx)
        fun tiles() = r.tiles(r.template.value, r.layout.value).map { it.id }
        val before = tiles()
        val values = r.groupsInOrder().first { it.first.id == "values" }.second.filter { !it.hidden }
        val a = values[1].id; val b = values[0].id
        r.moveItem(a, -1)
        val after = tiles()
        assertEquals(before.indexOf(a), after.indexOf(b)); assertEquals(before.indexOf(b), after.indexOf(a))
        // список «Все пункты» следует за экраном
        assertEquals(a, r.groupsInOrder().first { it.first.id == "values" }.second.first().id)
        // перетаскивание плитки на экране меняет порядок в списке
        val l = r.tiles(r.template.value, r.layout.value).toMutableList()
        val money = l.first { it.id == "money" }; l.remove(money); l.add(0, money)
        r.setLayout(l)
        assertEquals("money", r.template.value.first().id)
        assertEquals("blocks", r.groupsInOrder().first().first.id)
        // отметки двигаются внутри плитки
        r.moveItem("tr", -1)
        val checks = groupChecks(r, r.template.value, "checks").map { it.id }
        assertTrue(checks.indexOf("tr") < checks.indexOf("work"))
        // плитка группы отметок — на одну позицию
        val i = tiles().indexOf("checks"); r.moveTile("checks", 1)
        assertEquals(i + 1, tiles().indexOf("checks"))
    }

    @Test fun groupsSurviveJson() {
        val r = Repo(ctx)
        val g = r.addGroup("Привычки", GroupKind.CHECKS)
        r.upsertItem(r.template.value.first { it.id == "nc" }.copy(group = g.id))
        val r2 = Repo(ctx); assertTrue(r2.loadJson(r.toJson()))
        assertEquals(g, r2.groups.value.first { it.id == g.id })
        assertEquals(g.id, r2.template.value.first { it.id == "nc" }.group)
    }

    @Test fun reminderNamesWhatIsMissing() {
        I18n.lang = "ru"
        val r = Repo(ctx)
        val d = LocalDate.of(2026, 11, 14)
        assertTrue(Reminders.dailyText(r, MonthCfg(), d)!!.startsWith("Пара минут"))
        r.setValue(d.key(), "rate", "7")
        val txt = Reminders.dailyText(r, MonthCfg(), d)!!
        assertTrue(txt, txt.contains("вода") && txt.contains("сон"))
        r.setValue(d.key(), "water", "2"); r.setValue(d.key(), "sleep", "420")
        assertNull(Reminders.dailyText(r, MonthCfg(), d))
    }

    @Test fun moveWholeGroup() {
        val r = Repo(ctx)
        fun order() = r.groupsInOrder().filter { (_, l) -> l.any { !it.hidden } }.map { it.first.id }
        val before = order()
        val second = before[1]
        r.moveGroup(second, -1)
        assertEquals(second, order()[0])
        // плитки группы идут подряд, в прежнем порядке внутри группы
        val tiles = r.tiles(r.template.value, r.layout.value).map { it.id }
        val valuesTiles = r.template.value.filter { !it.hidden && r.groupOf(it).id == "values" }.map { it.id }
        val pos = valuesTiles.map { tiles.indexOf(it) }
        assertEquals(pos.sorted(), pos); assertEquals(pos.size - 1, pos.last() - pos.first())
        // край — без изменений
        r.moveGroup(order().first(), -1); assertEquals(second, order()[0])
        r.moveGroup(second, 1); assertEquals(before.take(2), order().take(2))
    }
}
