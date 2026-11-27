package org.maittaa.tabita

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/** 5.34: переименование варианта списка — в днях и настройках итогов; старые названия не попадают в анализ. */
@RunWith(RobolectricTestRunner::class)
class V534Test {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test fun renameOption() {
        I18n.lang = "ru"
        val repo = Repo(ctx); val prefs = Prefs(ctx)
        val item = TemplateItem(id = "sup", name = "Добавки", type = ItemType.MULTI, options = listOf("Найтиод", "Омега"))
        repo.upsertItem(item)
        val d = LocalDate.of(2026, 11, 10)
        repo.setValue(d.toString(), "sup", countsToString(mapOf("Найтиод" to 1, "Омега" to 2))!!)
        prefs.monthCfg = MonthCfg(index = setOf("sup:Найтиод"), goals = mapOf("sup:Найтиод" to 1.0))
        repo.upsertItem(item.copy(options = listOf("Найтмод", "Омега")))
        assertEquals(mapOf("Найтмод" to 1, "Омега" to 2), parseCounts(repo.entries.value[d.toString()]!!["sup"]).toMap())
        assertEquals(setOf("sup:Найтмод"), Prefs(ctx).monthCfg.index)
        assertTrue(Prefs(ctx).monthCfg.goals.containsKey("sup:Найтмод"))
        // вариант, которого нет в списке (удалён), в анализ не попадает
        repo.setValue(d.plusDays(1).toString(), "sup", countsToString(mapOf("Старое" to 1))!!)
        val ms = monthMetrics(repo.template.value, repo.entries.value.values.toList())
        assertTrue(ms.none { it.option == "Старое" || it.option == "Найтиод" })
        assertTrue(ms.any { it.option == "Найтмод" })
    }
}
