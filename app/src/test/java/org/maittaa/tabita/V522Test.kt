package org.maittaa.tabita

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** 5.22: отметки заполненности в календаре, смена советов. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h900dp-xxhdpi")
class V522Test {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test fun fill() {
        val repo = rule.activity.repo
        val prefs = rule.activity.prefs
        val today = LocalDate.now()
        val d1 = today.minusDays(1); val d2 = today.minusDays(2)
        repo.setValue(d1.toString(), "weight", "72")
        val md = MonthData(repo.template.value, repo.entries.value, d2, prefs.monthCfg)
        val f = DayFillCalc.month(repo.template.value, repo.entries.value, d2, prefs.monthCfg)
        assertEquals(DayFill.NONE, f[d2])
        assertNotEquals(DayFill.NONE, f[d1])
        assertEquals(if (md.dayIndex(d1).missing.isEmpty()) DayFill.FULL else DayFill.PARTIAL, f[d1])
        // служебные ключи не считаются записью
        assertTrue(!DayFillCalc.hasAny(mapOf("sleep!sd" to "{}")))
        assertTrue(DayFillCalc.hasAny(mapOf("water" to "1.5")))
    }

    @Test fun rotation() {
        val prefs = rule.activity.prefs
        val today = LocalDate.now()
        val g = Tips.general(prefs, today)
        assertTrue(g.size >= 20)
        assertEquals(g.map { it.id }, Tips.general(prefs, today).map { it.id })  // порядок на день стабилен
        prefs.tipsOff = setOf("sleep")
        assertTrue(Tips.general(prefs, today).none { it.cat == "sleep" })
        prefs.tipsOff = emptySet()
        Tips.hide(prefs, today, g[0].id)
        assertTrue(Tips.general(prefs, today).none { it.id == g[0].id })
        prefs.tipsHidden = emptySet()
    }

    @Test fun calendarShot() {
        val repo = rule.activity.repo
        val today = LocalDate.now()
        for (i in 1..12) if (i % 4 != 0) repo.setValue(today.minusDays(i.toLong()).toString(), "weight", "72")
        I18n.lang = "ru"
        var picked: LocalDate? = null
        rule.activity.setContent {
            MaterialTheme {
                DayCalendarDialog(today, repo.template.value, repo.entries.value, rule.activity.prefs.monthCfg, {}) { picked = it }
            }
        }
        rule.waitForIdle()
        val g = Class.forName("android.view.WindowManagerGlobal")
        val inst = g.getMethod("getInstance").invoke(null)
        @Suppress("UNCHECKED_CAST") val views = g.getDeclaredField("mViews").apply { isAccessible = true }.get(inst) as List<android.view.View>
        val v = views.last()
        val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(b).apply { drawColor(android.graphics.Color.rgb(90, 90, 90)); v.draw(this) }
        val out = File(System.getProperty("user.dir"), "../shots").apply { mkdirs() }
        File(out, "calendar.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        rule.onNodeWithText("Сегодня").performClick()
        assertEquals(today, picked)
    }
}

/** 5.22.1: советы по времени суток. */
@RunWith(RobolectricTestRunner::class)
class V5221Test {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test fun nightOnlySleep() {
        val repo = Repo(ctx); val prefs = Prefs(ctx)
        val today = LocalDate.now()
        repo.setValue(today.toString(), "weight", "72")
        val night = Tips.build(repo, prefs, today, java.time.LocalTime.of(1, 23))
        assertEquals("now_sleep", night.first().id)
        assertTrue(night.all { it.cat == "sleep" })
        val r = DayRhythm.of(repo, today)
        assertTrue(Tips.general(prefs, today, r, java.time.LocalTime.of(1, 23)).isEmpty())
        // вечер: отбой скоро
        assertTrue(Tips.build(repo, prefs, today, java.time.LocalTime.of(22, 15)).any { it.id == "now_wind" })
        // днём общие советы про сон не лезут, про еду — да
        val day = Tips.general(prefs, today, r, java.time.LocalTime.of(13, 0))
        assertTrue(day.none { it.cat == "sleep" } && day.any { it.cat == "food" })
        assertEquals(300, prefs.tipsEvery)
    }

    @Test fun workoutNotMissing() {
        val repo = Repo(ctx)
        val today = LocalDate.now()
        val md0 = MonthData(repo.template.value, repo.entries.value, today, MonthCfg())
        val ex = md0.metrics.first { it.item.source == Source.HC_EXERCISE }
        val water = md0.metrics.first { it.kind == MKind.WATER }
        val cfg = MonthCfg(index = setOf(ex.key, water.key))
        val d = today.minusDays(1)
        repo.setValue(d.toString(), water.item.id, "2")
        val md = MonthData(repo.template.value, repo.entries.value, today, cfg)
        val di = md.dayIndex(d)
        assertTrue(di.missing.isEmpty())
        assertEquals(1, di.total)
        assertEquals(DayFill.FULL, DayFillCalc.month(repo.template.value, repo.entries.value, today, cfg)[d])
    }

    private fun night(repo: Repo, d: LocalDate, bedH: Int, bedM: Int, wakeH: Int, wakeM: Int) {
        val z = java.time.ZoneId.systemDefault()
        val start = (if (bedH >= 12) d.minusDays(1) else d).atTime(bedH, bedM).atZone(z).toInstant().toEpochMilli()
        val end = d.atTime(wakeH, wakeM).atZone(z).toInstant().toEpochMilli()
        val det = SleepDetail(start, end, listOf(SleepStage(0, ((end - start) / 60_000).toInt(), 2)))
        repo.setValue(d.toString(), "sleep", det.asleep.toString())
        repo.setValue(d.toString(), SleepDetail.key("sleep"), det.toJson())
    }

    @Test fun wokeTodayNotNight() {
        val repo = Repo(ctx); val prefs = Prefs(ctx)
        val today = LocalDate.now()
        for (i in 1..6) night(repo, today.minusDays(i.toLong()), 23, 30, 7, 0)
        night(repo, today, 1, 46, 5, 46)
        val r = DayRhythm.of(repo, today, prefs, today.atTime(6, 4))
        assertTrue(r.wokeToday)
        assertEquals(5 * 60 + 46, r.wake)
        // средний отбой — около 23:30–0:00, а не позднее
        assertTrue(clockOf(r.bed), r.bed >= 23 * 60 || r.bed < 30)
        val tips = Tips.build(repo, prefs, today, java.time.LocalTime.of(6, 4))
        assertTrue(tips.none { it.id == "now_sleep" })
        assertTrue(tips.any { it.id == "now_light" })
        // до пробуждения — ночь
        val r2 = DayRhythm.of(repo, today, prefs, today.atTime(3, 0))
        assertTrue(!r2.wokeToday)
        // вручную
        prefs.rhythmWake = 8 * 60; prefs.rhythmBed = 0
        val r3 = DayRhythm.of(repo, today.plusDays(1), prefs, today.plusDays(1).atTime(12, 0))
        assertEquals(8 * 60, r3.usualWake); assertEquals(0, r3.bed); assertTrue(r3.manual)
        prefs.rhythmWake = -1; prefs.rhythmBed = -1
        assertEquals("4 ночи", nightsText(4)); assertEquals("1 ночь", nightsText(1)); assertEquals("7 ночей", nightsText(7))
    }

    @Test fun scoops() {
        val f1 = FoodDb.seed.first { it.id == "seed_f1" }
        assertEquals(8.67, f1.unitG!!, 0.01)
        val m = FoodDb.addTo(Meal(id = "1", kind = "breakfast"), f1, f1.unitG!!)
        assertEquals(30.0, m.kcal!!, 0.5)
        assertEquals(3.3, m.protein!!, 0.05)
        assertTrue(m.text, m.text.contains("1 × мерная ложка"))
        val g = FoodDb.seed.first { it.id == "seed_green" }
        val m2 = FoodDb.addTo(m, g, g.unitG!!)
        assertEquals(55.0, m2.kcal!!, 0.5)
        assertEquals(4.8, m2.protein!!, 0.05)
        // json туда-обратно
        val back = Food.fromJson(f1.toJson())
        assertEquals(f1.copy(en = back.en), back)
        assertTrue(FoodDb.search("формула", null).any { it.id == "seed_f1" })
    }

    @Test fun seedMigration() {
        val repo = Repo(ctx)
        // данные 5.22.2: только два продукта, свой добавлен, версии набора нет
        val my = Food.fromLabel("Мой", 100.0, 100.0, 10.0, 1.0, 10.0, null, null, null, id = "x")
        val o = org.json.JSONObject(repo.toJson())
        o.put("myFoods", org.json.JSONArray(listOf(FoodDb.seed[0].toJson(), my.toJson()))); o.remove("myFoodsSeed")
        assertTrue(repo.loadJson(o.toString()))
        val ids = repo.myFoods.value.map { it.id }
        assertTrue(ids.toString(), "x" in ids && "seed_cr7" in ids && "seed_bar" in ids && "seed_green" !in ids)
        val rebuild = FoodDb.seed.first { it.id == "seed_rebuild" }
        assertEquals(10.0, rebuild.unitG!!, 0.01)
        assertEquals(5.0, FoodDb.addTo(Meal(id = "1", kind = "breakfast"), rebuild, 10.0).protein!!, 0.05)
    }
}
