package org.maittaa.tabita

import androidx.activity.compose.setContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** 5.57: шаблоны приёмов пищи и список покупок на неделю. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1600dp-xxhdpi")
class V557Test {
    init { Prefs(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()).lang = "ru" }
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @After fun reset() { rule.activity.prefs.apply { mealTpls = null; shopList = null; planGoal = "" } }

    private fun save(name: String) {
        rule.waitForIdle()
        val v = rule.activity.window.decorView
        val b = android.graphics.Bitmap.createBitmap(v.width, v.height, android.graphics.Bitmap.Config.ARGB_8888).also { v.draw(android.graphics.Canvas(it)) }
        val out = java.io.File(System.getProperty("user.dir"), "../shots").apply { mkdirs() }
        java.io.File(out, "$name.png").outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val smoothie = Meal(id = "a", kind = "Завтрак", text = "Формула 1, 2 ложки", photos = listOf("p.jpg"), kcal = 90.0, protein = 10.0, link = "a", at = 1L)
    private val aloe = Meal(id = "b", kind = "Завтрак", text = "Алоэ", kcal = 10.0, protein = 0.0, link = "a")

    @Test fun templates() {
        val tp = MealTpl.from(listOf(smoothie, aloe), "  ")
        assertEquals("Формула 1, 2 ложки, Алоэ", tp.name)
        assertTrue(tp.items.all { it.photos.isEmpty() && it.at == null && it.link.isEmpty() })
        assertEquals(100.0, tp.kcal!!, 0.001)
        val back = MealTpls.parse(MealTpls.toJson(listOf(tp))).single()
        assertEquals(tp.name, back.name); assertEquals(2, back.items.size); assertEquals("Завтрак", back.kind)
        // в день: новые id, общая сцепка, время — сейчас для сегодняшнего дня
        val day = MealTpls.applyTo(listOf(Meal(id = "x", kind = "Обед", text = "суп")), back, LocalDate.now().toString(), now = 123L)
        assertEquals(3, day.size)
        val added = day.drop(1)
        assertTrue(added.none { it.id == "a" || it.id == "b" })
        assertEquals(1, added.map { it.unit }.distinct().size)
        assertTrue(added.all { it.at == 123L })
        assertNull(MealTpls.applyTo(emptyList(), back, "2020-01-01").first().at)
        // одна позиция — без сцепки
        assertEquals("", MealTpl.from(listOf(smoothie), "Смузи").instantiate(null).single().link)
        assertTrue(MealTpls.parse("bad").isEmpty())
    }

    @Test fun dryWeights() {
        val buck = FoodDb.all.first { it.ru == "Гречка отварная" }
        val dry = Shopping.dryOf(buck)!!
        assertTrue(dry.ru.contains("сух"))
        val ln = Shopping.Line(buck, 1400.0, dry)
        assertEquals(1400.0 * buck.kcal / dry.kcal, ln.buyGrams, 0.001)
        assertNotNull(Shopping.dryOf(FoodDb.all.first { it.ru == "Макароны отварные" }))
        assertNull(Shopping.dryOf(FoodDb.all.first { it.ru == "Рис бурый отварной" }))
        assertNull(Shopping.dryOf(FoodDb.all.first { it.ru == "Брокколи отварная" }))
    }

    @Test fun weekListAndState() {
        val prefs = rule.activity.prefs
        val tg = Program.Targets(2600.0, 140.0, 80.0, 330.0, 1.8)
        val menus = Shopping.weekMenu(Goal.MUSCLE, tg, null, prefs)
        assertEquals(7, menus.size)
        val lines = Shopping.lines(menus)
        // всё меню недели попало в список, граммы совпадают
        assertEquals(menus.flatten().flatMap { it.items }.sumOf { it.grams }, lines.sumOf { it.grams }, 0.01)
        assertEquals(lines.size, lines.map { it.food.key }.distinct().size)
        // разные дни — разные варианты
        assertNotEquals(menus[0][1].items[0].food.key, menus[1][1].items[0].food.key)
        // состояние: на новой неделе отметки сброшены, некупленное своё осталось
        val mon = LocalDate.of(2026, 11, 23)
        Shopping.save(prefs, Shopping.State(mon.toString(), setOf("x"), listOf(Shopping.Extra("хлеб", true), Shopping.Extra("кофе", false))))
        assertEquals(setOf("x"), Shopping.state(prefs, mon.plusDays(3)).checked)
        val next = Shopping.state(prefs, mon.plusDays(7))
        assertTrue(next.checked.isEmpty()); assertEquals(listOf("кофе"), next.extra.map { it.text })
        val text = Shopping.shareText(lines, Shopping.State(mon.toString(), setOf(lines[0].buyFood.key), listOf(Shopping.Extra("кофе", false))))
        assertFalse(text.contains(lines[0].buyFood.name + " —"))
        assertTrue(text.contains("кофе"))
    }

    @Test fun shoppingInProgram() {
        val prefs = rule.activity.prefs; val repo = rule.activity.repo
        prefs.planGoal = "muscle"
        rule.runOnUiThread {
            repo.setProfile(Profile(180.0, 1995, "m"))
            repo.setValues(mapOf(LocalDate.now().key() to mapOf("weight" to "75")))
        }
        rule.onAllNodesWithText("Программа").let { it[it.fetchSemanticsNodes().size - 1] }.performClick()
        rule.onAllNodes(hasText("Цель") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))[0].performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Список покупок на неделю").performScrollTo().performClick()
        rule.waitForIdle()
        val buck = FoodDb.all.first { it.ru.startsWith("Гречка (сух") }
        rule.onNodeWithTag("shop_${buck.key}").performScrollTo()
        save("57_shopping")
        rule.onNodeWithTag("shop_${buck.key}").performClick()
        rule.waitForIdle()
        assertTrue(buck.key in Shopping.state(prefs).checked)
        assertTrue(Changelog.all.any { it.version == "5.57" })
    }

    @Test fun templateChipAddsMeal() {
        val act = rule.activity; val repo = act.repo
        val tp = MealTpl.from(listOf(smoothie, aloe), "Смузи")
        MealTpls.add(act.prefs, tp)
        val date = LocalDate.now().toString()
        val item = repo.template.value.first { it.type == ItemType.MEALS }
        rule.runOnUiThread { repo.setValue(date, item.id, null) }
        rule.activity.runOnUiThread {
            act.setContent { AppTheme { androidx.compose.foundation.layout.Column { MealsBlock(act, item, date, null) {} } } }
        }
        rule.waitForIdle()
        save("57_template_chip")
        rule.onNodeWithTag("tpl_open").performClick()
        rule.waitForIdle()
        // без выбора «Добавить» не активна — случайное нажатие ничего не пишет
        rule.onNodeWithTag("pick_ok").assertIsNotEnabled()
        rule.onNodeWithTag("pick_${tp.id}").performClick()
        save("57_template_pick")
        rule.onNodeWithTag("pick_ok").performClick()
        rule.waitForIdle()
        val meals = parseMeals(repo.entries.value[date]?.get(item.id))
        assertEquals(2, meals.size)
        assertEquals(100.0, mealKcal(meals)!!, 0.001)
    }

    @Test fun templatesInSettings() {
        val act = rule.activity; val dir = act.repo.photosDir
        java.io.File(dir, "t1.jpg").writeBytes(byteArrayOf(1, 2, 3))
        MealTpls.add(act.prefs, MealTpl("t", "Смузи", "Завтрак", listOf(smoothie.copy(photos = listOf("t1.jpg"), link = "", at = null), aloe.copy(link = ""))))
        rule.activity.runOnUiThread {
            act.setContent { AppTheme { androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) { MealTplsBody(act) } } }
        }
        rule.waitForIdle()
        save("57_tpl_list")
        rule.onNodeWithTag("tpl_row_t").performClick()
        rule.onNodeWithTag("tpl_item_0").assertExists(); rule.onNodeWithTag("tpl_item_1").assertExists()
        save("57_tpl_editor")
        // новая позиция открывает ту же карточку, что в чек-листе; пустая при «Готово» убирается
        rule.onNodeWithTag("tpl_add_item").performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText("Позиция 3").assertCountEquals(2)
        rule.onNodeWithTag("meal_done").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("tpl_item_2").assertDoesNotExist()
        rule.onNodeWithTag("tpl_name").performTextClearance()
        rule.onNodeWithTag("tpl_name").performTextInput("Утро")
        rule.onNodeWithTag("tpl_ok").performClick()
        rule.waitForIdle()
        val tp = MealTpls.all(act.prefs).single()
        assertEquals("Утро", tp.name); assertEquals(2, tp.items.size); assertEquals(listOf("t1.jpg"), tp.items[0].photos)
        // в день — копия фото, у шаблона остаётся своё
        val day = MealTpls.applyTo(emptyList(), tp, "2020-01-01", dir = dir)
        val copy = day[0].photos.single()
        assertNotEquals("t1.jpg", copy); assertTrue(java.io.File(dir, copy).exists())
        // удаление с подтверждением — вместе с фото шаблона
        rule.onNodeWithContentDescription("Удалить").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Удалить").performClick()
        rule.waitForIdle()
        assertTrue(MealTpls.all(act.prefs).isEmpty())
        assertFalse(java.io.File(dir, "t1.jpg").exists()); assertTrue(java.io.File(dir, copy).exists())
    }
}
