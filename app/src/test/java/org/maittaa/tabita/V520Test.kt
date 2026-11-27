package org.maittaa.tabita

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalTime

/** 5.20: плитка «Советы дня». */
@RunWith(RobolectricTestRunner::class)
class V520Test {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test fun migrationPutsTipsOnTop() {
        val repo = Repo(ctx)
        val o = JSONObject(repo.toJson())
        o.put("version", 4)
        val tm = o.getJSONArray("template")
        val rest = JSONArray(); for (i in 0 until tm.length()) if (tm.getJSONObject(i).getString("type") != "TIPS") rest.put(tm.getJSONObject(i))
        o.put("template", rest)
        o.put("layout", JSONArray().put(JSONObject().put("id", "sleep")))
        repo.loadJson(o.toString())
        assertEquals("tips", repo.template.value.first().id)
        assertEquals("tips", repo.layout.value.first().id)
        assertEquals(TileSize.WIDE, repo.layout.value.first().size)
    }

    @Test fun tipsFromData() {
        val repo = Repo(ctx)
        val prefs = Prefs(ctx)
        val today = LocalDate.now()
        repo.setProfile(Profile(183.0, 1996, "m"))
        repo.setValue(today.toString(), "weight", "72")
        val meals = repo.template.value.first { it.type == ItemType.MEALS }
        repo.setValue(today.toString(), meals.id, mealsToString(listOf(Meal(kind = "Завтрак", kcal = 400.0, protein = 15.0, fat = 10.0, carbs = 60.0))))
        val tips = Tips.build(repo, prefs, today, LocalTime.of(19, 0))
        tips.forEach { println("${it.prio} ${it.title} — ${it.text}") }
        assertTrue(tips.any { it.id == "food_protein" })
        assertTrue(tips.first().prio >= tips.last().prio)
        // категория отключена — советов про еду нет
        prefs.tipsOff = setOf("food")
        assertTrue(Tips.build(repo, prefs, today, LocalTime.of(19, 0)).none { it.cat == "food" })
        prefs.tipsOff = emptySet()
        // скрыт до завтра
        Tips.hide(prefs, today, "food_protein")
        assertTrue(Tips.build(repo, prefs, today, LocalTime.of(19, 0)).none { it.id == "food_protein" })
        // не сегодня — пусто
        assertTrue(Tips.build(repo, prefs, today.minusDays(1)).isEmpty())
    }
}
