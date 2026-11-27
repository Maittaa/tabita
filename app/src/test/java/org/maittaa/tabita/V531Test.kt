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

/** 5.31: советы и «Сегодня» по логике цели; плитка «Цель». */
@RunWith(RobolectricTestRunner::class)
class V531Test {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    @Before fun ru() { I18n.lang = "ru" }

    private fun setup(): Pair<Repo, Prefs> {
        val repo = Repo(ctx); val prefs = Prefs(ctx)
        repo.setProfile(Profile(183.0, 1996, "m"))
        val today = LocalDate.now()
        repo.setValue(today.toString(), "weight", "95")
        // белка мало (60 г при норме 170), калорий немного
        repo.setValue(today.toString(), "food", mealsToString(listOf(Meal(kind = "Обед", kcal = 1200.0, protein = 60.0, fat = 40.0, carbs = 150.0))))
        repo.setValue(today.toString(), "sleep", "400")
        return repo to prefs
    }

    @Test fun proteinTipsFollowGoal() {
        val (repo, prefs) = setup()
        val today = LocalDate.now()
        fun ids(goal: String, h: Int) = run { prefs.planGoal = goal; Tips.build(repo, prefs, today, LocalTime.of(h, 0)).map { it.id } }
        assertTrue(ids("muscle", 14).contains("food_protein"))
        // самочувствие: 60 г при весе 95 — меньше 0,8 г/кг (76 г) только вечером
        assertTrue(!ids("wellbeing", 14).contains("food_protein"))
        assertTrue(ids("wellbeing", 20).contains("food_protein"))
        assertTrue(!ids("wellbeing", 18).contains("food_left"))
        // похудение: днём не «добирать», вечером — если меньше 60 % нормы
        assertTrue(!ids("lose", 13).contains("food_protein"))
        val ev = run { prefs.planGoal = "lose"; Tips.build(repo, prefs, today, LocalTime.of(18, 0)) }
        assertTrue(ev.first { it.id == "food_protein" }.title.contains("мышцы"))
        prefs.planGoal = ""
    }

    @Test fun todayByGoal() {
        val (repo, prefs) = setup()
        val wb = Program.today(Goal.WELLBEING, repo, prefs)!!
        assertTrue(wb.value, wb.value.contains("сон"))
        assertTrue(wb.rows.none { it.first.contains("Калори") || it.first.contains("Белок") })
        val lose = Program.today(Goal.LOSE, repo, prefs)!!
        assertTrue(lose.value.startsWith("осталось"))
        assertTrue(lose.rows.any { it.first == "Белок — не меньше" })
        val mus = Program.today(Goal.MUSCLE, repo, prefs)!!
        assertTrue(mus.value.contains("белок ещё"))
        prefs.planGoal = "lose"
        val s = tileSummary(repo, repo.template.value, "goal", repo.entries.value[LocalDate.now().toString()]!!, LocalDate.now().toString(), prefs)
        assertTrue(s.value.startsWith("осталось")); assertTrue(s.sub.contains("Похудение"))
        prefs.planGoal = ""
    }

    @Test fun migrationAddsGoalTile() {
        val repo = Repo(ctx)
        val o = org.json.JSONObject(repo.toJson())
        val t = o.getJSONArray("template"); val keep = org.json.JSONArray()
        for (i in 0 until t.length()) if (t.getJSONObject(i).optString("type") != "GOAL") keep.put(t.getJSONObject(i))
        o.put("template", keep).put("version", 6)
        o.put("layout", org.json.JSONArray().put(org.json.JSONObject().put("id", "tips").put("w", 2).put("h", 1)))
        assertTrue(repo.loadJson(o.toString()))
        val ids = repo.template.value.map { it.id }
        assertEquals(ids.indexOf("tips") + 1, ids.indexOf("goal"))
        assertTrue(repo.layout.value.map { it.id }.let { it.indexOf("goal") == it.indexOf("tips") + 1 })
    }
}
