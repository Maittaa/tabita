package org.maittaa.tabita

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1400dp-xxhdpi")
class ScreensTest {
    @get:Rule val rule = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun shot(name: String) {
        rule.waitForIdle()
        var b: Bitmap? = null
        scenario!!.onActivity { a ->
            val v = a.window.decorView
            b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(android.graphics.Canvas(it)) }
        }
        val bmp = b!!
        val out = File(System.getProperty("user.dir"), "../shots").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Снимок верхнего окна (диалога). */
    private fun shotTop(name: String) {
        rule.waitForIdle()
        val g = Class.forName("android.view.WindowManagerGlobal")
        val inst = g.getMethod("getInstance").invoke(null)
        val f = g.getDeclaredField("mViews").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST") val views = f.get(inst) as List<android.view.View>
        val v = views.last()
        val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        // фон под диалог
        android.graphics.Canvas(b).apply { drawColor(android.graphics.Color.rgb(90, 90, 90)); v.draw(this) }
        val out = File(System.getProperty("user.dir"), "../shots").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun scrollTo(text: String, scroll: Boolean = true) {
        val n = rule.onNode(androidx.compose.ui.test.hasScrollToIndexAction())
        if (scroll) n.performScrollToNode(androidx.compose.ui.test.hasText(text))
        else rule.onNodeWithText(text).performScrollTo()
    }

    @Test
    fun screens() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        // тестовые данные за 40 дней
        Prefs(ctx).lang = "ru"
        val repo = Repo(ctx)
        val rnd = Random(1)
        val today = LocalDate.now()
        val data = (0..40).associate { i ->
            val d = today.minusDays(i.toLong())
            d.key() to buildMap {
                put("sleep", (300 + rnd.nextInt(240)).toString())
                put("rate", "%.1f".format(java.util.Locale.US, 4.5 + rnd.nextDouble() * 3))
                if (rnd.nextBoolean()) put("work", if (rnd.nextInt(5) == 0) "2" else "1")
                if (rnd.nextBoolean()) put("tr", "1")
                if (rnd.nextInt(3) == 0) put("dr", "1")
                if (rnd.nextInt(4) == 0) put("soc", "2")
                put("steps", (3000 + rnd.nextInt(9000)).toString())
                put("used", listOf("Кофе", "Зелёный чай", "Элеутерококк").filter { rnd.nextBoolean() }.joinToString(MULTI_SEP))
                if (i % 7 == 0) put("weight", (95 - i * 0.05).toString())
            }.filterValues { it.isNotEmpty() }
        }
        repo.setValues(data)
        // фото блюда
        val bmp = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(bmp).apply {
            drawColor(android.graphics.Color.rgb(238, 226, 205))
            drawCircle(200f, 150f, 120f, android.graphics.Paint().apply { color = android.graphics.Color.WHITE })
            drawCircle(170f, 140f, 55f, android.graphics.Paint().apply { color = android.graphics.Color.rgb(150, 90, 40) })
            drawCircle(240f, 170f, 45f, android.graphics.Paint().apply { color = android.graphics.Color.rgb(90, 160, 70) })
        }
        File(repo.photosDir, "p1.jpg").outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        (0..30).forEach { i ->
            val d = today.minusDays(i.toLong()).key()
            repo.setValue(d, "food", mealsToString(listOf(
                Meal(kind = "Завтрак", text = "Овсянка с бананом", kcal = 350.0 + rnd.nextInt(100), protein = 12.0, fat = 8.0, carbs = 60.0, gi = 55.0 + rnd.nextInt(15)),
                Meal(id = "lunch$i", kind = "Обед", text = "Гречка, котлета, салат", photos = if (i == 0) listOf("p1.jpg") else emptyList(),
                    kcal = 600.0 + rnd.nextInt(200), protein = 38.0, fat = 22.0, carbs = 60.0 + rnd.nextInt(40), gi = 50.0 + rnd.nextInt(25),
                    link = if (i == 0) "lunch0" else ""),
            ) + if (i == 0) listOf(Meal(kind = "Обед", text = "[\"Кофе 3 в 1\",\"молочный шоколад, 30 г\"]", kcal = 310.0, protein = 5.0, fat = 15.0, carbs = 41.0, gi = 60.0, gl = 25.0, link = "lunch0")) else emptyList()))
            val buys = mutableListOf(
                Purchase(name = "Хлеб", qty = 1.0, price = 55.0, category = "Продукты"),
                Purchase(name = "Кофе", qty = 1.0, unit = "шт", price = 180.0, category = "Кафе"),
            )
            if (i % 3 == 0) buys += Purchase(name = "Яблоки", qty = 1.5, unit = "кг", price = 140.0, category = "Продукты")
            if (i % 10 == 0) buys += Purchase(name = "Коммуналка", qty = 1.0, price = 4177.0, category = "Дом")
            if (i % 9 == 0) buys += Purchase(name = "Аптека", qty = 1.0, price = 650.0, category = "Здоровье")
            if (i % 7 == 0) buys += Purchase(name = "Такси", qty = 1.0, price = 350.0 + rnd.nextInt(200), category = "Транспорт")
            if (i == 14) buys += Purchase(name = "Зарплата", qty = 1.0, price = 85000.0, income = true)
            repo.setValue(d, "money", purchasesToString(buys))
        }
        repo.setValues((0..30).associate { i ->
            today.minusDays(i.toLong()).key() to mapOf(
                "water" to (1.2 + (i % 5) * 0.25).toString(),
                "sleep!src" to "com.fitbit.FitbitMobile", "steps!src" to "com.fitbit.FitbitMobile",
                "ex" to (if (i % 2 == 0) "45" else "0"), "ex!src" to "com.fitbit.FitbitMobile",
            )
        })
        // фазы сна за 30 ночей (как из Health Connect)
        repo.setValues((0..30).associate { i ->
            val d = today.minusDays(i.toLong())
            val st = mutableListOf<SleepStage>(); var off = 0
            val cycle = listOf(2 to 20, 3 to 35 - i % 4 * 5, 2 to 25, 1 to 15 + i % 3 * 5, 0 to if (i % 3 == 0) 6 else 2)
            repeat(5) { c -> cycle.forEach { (tp, m) -> val mm = if (tp == 3) (m - c * 7).coerceAtLeast(5) else m; st += SleepStage(off, mm, tp); off += mm } }
            val start = d.atTime(23, 10 + i % 40).minusDays(1).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            val det = SleepDetail(start, start + off * 60_000L, st)
            d.key() to mapOf("sleep" to det.asleep.toString(), SleepDetail.key("sleep") to det.toJson())
        })
        repo.setManual(today.minusDays(2).key(), "sleep", "400", true)
        listOf(1 to "Плохо спал, много кофе после обеда.", 3 to "Хорошая тренировка, весь день бодрый.", 6 to "Встреча с друзьями, поздно легли.",
            12 to "Начал новый проект на работе.", 20 to "Голова болела с утра, прошло к вечеру.")
            .forEach { (i, txt) -> repo.setManual(today.minusDays(i.toLong()).key(), "why", txt, true) }
        repo.setManual(today.key(), "steps", "9120", true)
        run {
            val st = today.atTime(18, 5).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            val ws = listOf(WorkoutSession("strength", 45, st, "Силовая", "com.fitbit"), WorkoutSession("walk", 32, st - 3 * 3_600_000L, src = "com.fitbit"))
            repo.setValues(mapOf(today.key() to mapOf("ex" to "45", Workouts.syncedKey("ex") to Workouts.toJson(ws)!!)))
            repo.setWorkouts(today.key(), repo.template.value.first { it.id == "ex" }, listOf(WorkoutSession("stretch", 15, note = "после силовой")))
        }
        repo.setValue(today.key(), "used", countsToString(linkedMapOf("Кофе" to 3, "Элеутерококк" to 1)))
        repo.ensureBodyItem()
        repo.setBodyDays(srcPackage = "com.health.openscale", days = (0..30).associate { i ->
            today.minusDays(i.toLong()).key() to BodyDay(System.currentTimeMillis() - i * 86_400_000L, listOf(
                BodyMetric("builtin.weight", "Вес", "kg", 95.5 - i * 0.03 + rnd.nextDouble() * 0.4),
                BodyMetric("builtin.body_fat", "Жир", "%", 24.0 + i * 0.03),
                BodyMetric("builtin.water", "Вода", "%", 55.1), BodyMetric("builtin.muscle", "Мышцы", "%", 38.2 - i * 0.02),
                BodyMetric("builtin.bone", "Кости", "kg", 3.4), BodyMetric("builtin.bmi", "ИМТ", "kg/m2", 29.3),
                BodyMetric("builtin.visceral_fat", "Висцеральный жир", "", 11.0), BodyMetric("builtin.bmr", "Обмен веществ", "kcal", 1980.0),
            ))
        })
        repo.setBalance(48250.0)
        val habits = repo.addGroup("Привычки", GroupKind.CHECKS)
        listOf("nc", "np").forEach { id -> repo.upsertItem(repo.template.value.first { it.id == id }.copy(group = habits.id)) }
        Prefs(ctx).monthCfg = MonthCfg(weekly = mapOf("tr" to 3))
        // прошлый чекап 15 дней назад — сегодня «пора»
        val before = java.time.LocalDate.now().minusDays(15).key()
        repo.addCheckup(Checkup(before, 1, mapOf(
            "phq8" to BlockResult(9.0, listOf(2, 1, 2, 1, 1, 1, 1, 0)), "gad7" to BlockResult(8.0, listOf(1, 2, 1, 1, 1, 1, 1)),
            "panic" to BlockResult(0.0, listOf(0, 0, 0, 0)), "cbi" to BlockResult(61.0, listOf(1, 1, 2, 3, 2, 3)),
            "who5" to BlockResult(48.0, listOf(3, 2, 2, 2, 3)))))
        File(ctx.filesDir, "data.json").writeText(repo.toJson())

        ActivityScenario.launch(MainActivity::class.java).use {
            scenario = it
            shot("1_day")
            rule.onAllNodesWithText("Tr")[0].performClick(); rule.waitForIdle(); rule.onAllNodesWithText("Tr")[0].performClick()
            rule.onNodeWithText("Добавить тренировку").performScrollTo(); shot("1w_day_workouts")
            rule.onNodeWithText("Добавить тренировку").performClick()
            rule.onNodeWithText("Что делали", substring = true).performScrollTo(); shot("1x_day_workout_add")
            rule.onAllNodesWithText("Отмена")[0].performClick()
            rule.onNodeWithText("Как замерить пульс покоя без часов").performScrollTo().performClick(); shot("1h_rhr_hint")
            rule.onNodeWithText("Из чего оценка").performScrollTo(); shot("1s_day_sleep")
            rule.onNodeWithText("Как рассчитать норму?").performScrollTo().performClick()
            rule.onNodeWithText("Сделать нормой", substring = true).performScrollTo(); shot("1w_water")
            rule.onNodeWithText("Весы").performScrollTo(); shot("1a_day_body")
            it.onActivity { a -> a.prefs.lang = "en"; I18n.lang = "en" }; shot("1a_day_body_en")
            it.onActivity { a -> a.prefs.lang = "ru"; I18n.lang = "ru" }
            rule.onNodeWithText("Гликемическая оценка:", substring = true).performScrollTo(); shot("1l_day_linked_lunch")
            rule.onNodeWithText("Кофе 3 в 1, молочный шоколад", substring = true).performScrollTo().performClick()
            rule.onNodeWithText("Расцепить").performScrollTo(); shotTop("1m_linked_card")
            rule.onAllNodesWithText("Готово").let { it[it.fetchSemanticsNodes().size - 1] }.performClick()
            rule.onNodeWithText("Гликемическая оценка", substring = true).performScrollTo(); shot("1g_day_food_gly")
            rule.onNodeWithText("Финансы").performScrollTo(); shot("1b_day_food_money")
            rule.onNodeWithText("Гречка, котлета, салат").performScrollTo().performClick(); shotTop("1c_meal_dialog")
            rule.onNodeWithText("Готово").performClick()
            // плитки: часть уменьшена
            it.onActivity { a -> a.repo.setLayout(listOf(
                Repo.Tile("checks"), TileSize.WIDE.tile("sleep"), TileSize.HALF.tile("steps"),
                TileSize.HALF.tile("rate"), TileSize.MINI.tile("rhr"), TileSize.MINI.tile("ex"), TileSize.MINI.tile("water"), TileSize.MINI.tile("weight"),
                TileSize.WIDE.tile("food"), TileSize.HALF.tile("used"), TileSize.HALF.tile("money"), TileSize.WIDE.tile("body"),
                TileSize.HALF.tile("why"),
            )) }
            rule.onNodeWithText("Отметки дня").performScrollTo(); shot("1d_tiles")
            rule.onNodeWithText("Шаги").performScrollTo(); shot("1d2_tiles")
            rule.onNodeWithText("Сон").performClick(); shotTop("1e_tile_popup")
            rule.onNodeWithContentDescription("Закрыть").performClick()
            rule.onNodeWithText("Плитки").performScrollTo().performClick()
            rule.onNodeWithText("Отметки дня").performScrollTo()
            rule.onNodeWithText("Шаги").performClick(); shot("1f_tiles_edit")
            it.onActivity { a -> Look.mode = "dark"; Look.accent = "blue" }
            shot("8_dark_edit")
            rule.onAllNodesWithText("Готово").let { n ->
                val i = n.fetchSemanticsNodes().withIndex().minBy { it.value.boundsInRoot.top }.index; n[i].performClick() }; shot("8b_dark_tiles")
            rule.onNodeWithText("Графики").performClick(); shot("8c_dark_stats")
            rule.onNodeWithText("День").performClick()
            it.onActivity { a -> Look.accent = "dynamic" }; shot("8d_dark_dynamic")
            it.onActivity { a -> Look.mode = "light"; Look.accent = "green" }
            rule.onNodeWithText("Плитки").performScrollTo().performClick()
            rule.onAllNodesWithText("Готово").let { n ->
                val i = n.fetchSemanticsNodes().withIndex().minBy { it.value.boundsInRoot.top }.index; n[i].performClick() }
            rule.onNodeWithText("Все пункты").performScrollTo().performClick(); shotTop("3_all_items")
            rule.onNode(androidx.compose.ui.test.hasScrollToIndexAction()).performScrollToNode(androidx.compose.ui.test.hasText("Список вариантов"))
            rule.onNodeWithText("Список вариантов").performClick(); shotTop("3c_item_dialog")
            rule.onAllNodesWithText("Список вариантов")[1].performClick(); shotTop("3d_type_menu")
            rule.onAllNodesWithText("Список вариантов").let { it[it.fetchSemanticsNodes().size - 1] }.performClick()
            rule.onNodeWithText("Отмена").performClick()
            rule.onNode(androidx.compose.ui.test.hasScrollToIndexAction()).performScrollToIndex(0)
            rule.onAllNodesWithText("Work").let { it[it.fetchSemanticsNodes().size - 1] }.performClick(); shotTop("3e_item_check")
            rule.onNodeWithText("Отмена").performClick()
            rule.onNodeWithText("Привычки").performScrollTo(); shotTop("3b_all_items_groups")
            rule.onNodeWithContentDescription("Закрыть").performClick()
            rule.onNodeWithText("Графики").performClick()
            scrollTo("Весы"); shot("2a_stats_body")
            scrollTo("норма выполнена"); shot("2w_stats_water")
            scrollTo("Траты по категориям"); shot("2f_stats_donut")
            rule.onNodeWithText("Нажмите на категорию", substring = true).performScrollTo()
            rule.onAllNodesWithText("Продукты")[0].performClick(); shotTop("2h_category")
            rule.onNodeWithText("Закрыть").performClick()
            scrollTo("Доходы и расходы по неделям"); shot("2g_stats_flow")
            rule.onNode(androidx.compose.ui.test.hasScrollToIndexAction()).performScrollToIndex(0)
            rule.onNodeWithContentDescription("2 в ряд").performClick(); shot("2b_stats_grid2")
            rule.onNodeWithContentDescription("3 в ряд").performClick(); shot("2c_stats_grid3")
            rule.onNodeWithText("Весы").performClick(); shotTop("2d_popup_body")
            rule.onNodeWithContentDescription("Закрыть").performClick()
            rule.onNodeWithText("Все графики").performClick(); shotTop("2e_filter")
            rule.onNodeWithText("Готово").performClick()
            rule.onNodeWithContentDescription("1 в ряд").performClick()
            // итоги месяца
            rule.onNodeWithText("Итоги месяца").performClick(); shot("9a_month_now")
            rule.onNodeWithContentDescription("Назад").performClick(); shot("9b_month_prev")
            scrollTo("Лучшая серия"); shot("9c_month_index")
            scrollTo("Цели: дни и % выполнения"); shot("9d_month_goals")
            rule.onNode(androidx.compose.ui.test.hasScrollToIndexAction()).performScrollToIndex(0)
            rule.onNodeWithText("Все строки").performClick(); shotTop("9i_heat_rows")
            rule.onAllNodesWithText("Снять все")[1].performClick(); rule.onNodeWithText("Готово").performClick(); shot("9j_heat_filtered")
            rule.onNodeWithText("Каждый день месяца").performTouchInput { click(androidx.compose.ui.geometry.Offset(centerX + 60f, centerY + 120f)) }
            shot("9h_jump_to_day")
            rule.onNodeWithText("Графики").performClick()
            scrollTo("Лучшая серия")
            rule.onNodeWithContentDescription("Настроить индекс").performClick(); shotTop("9e_index_setup")
            rule.onNodeWithText("Готово").performClick()
            it.onActivity { a -> a.prefs.lang = "en"; I18n.lang = "en" }; scrollTo("Day index"); shot("9f_month_en")
            it.onActivity { a -> a.prefs.lang = "ru"; I18n.lang = "ru" }
            it.onActivity { a -> Look.mode = "dark" }; rule.onNode(androidx.compose.ui.test.hasScrollToIndexAction()).performScrollToIndex(0); shot("9g_month_dark")
            it.onActivity { a -> Look.mode = "light" }
            rule.onNodeWithText("Динамика").performClick()
            // самочувствие: плитка в дне «пора», вкладка, выбор блоков, прохождение, итог, подробно
            rule.onNodeWithText("День").performClick()
            rule.onAllNodesWithText("Чекап самочувствия")[0].performScrollTo()
            shot("10a_day_wb_due")
            rule.onNodeWithText("Самочувствие").performClick(); shot("10b_wb_home")
            rule.onNodeWithText("Начать").performClick(); shot("10c_wb_select")
            rule.onNodeWithText("Стресс").performClick(); rule.onNodeWithText("Стресс").performClick()
            rule.onAllNodesWithText("Начать")[0].performClick(); shot("10d_wb_quiz")
            val qs = WDraft.start(Wellbeing.defaultSelection.toList()).questions
            qs.forEachIndexed { i, (b, qi) ->
                val pick = if (b.id == "panic") 0 else if (b.id == "who5") 2 else 1
                rule.onAllNodesWithText(b.questions[qi].answers[pick].text.ru)[0].performClick()
                if (i == 10) { it.onActivity { _ -> I18n.lang = "en" }; shot("10e_wb_quiz_en"); it.onActivity { _ -> I18n.lang = "ru" } }
            }
            shot("10f_wb_done")
            rule.onNodeWithText("К результатам").performClick(); shot("10g_wb_results")
            rule.onAllNodesWithText("Тревога")[0].performClick(); shot("10h_wb_detail")
            rule.onNodeWithContentDescription("Назад").performClick()
            rule.onNodeWithText("День").performClick()
            rule.onAllNodesWithText("Чекап самочувствия")[0].performScrollTo()
            shot("10i_day_wb_done")
            // графики: карточка «Самочувствие» и порядок карточек
            rule.onNodeWithText("Графики").performClick()
            rule.onNode(androidx.compose.ui.test.hasScrollToIndexAction()).performScrollToNode(androidx.compose.ui.test.hasText("Изменение — с прошлого прохождения. Это скрининг, а не диагноз."))
            shot("10j_stats_wellbeing")
            rule.onNode(androidx.compose.ui.test.hasScrollToIndexAction()).performScrollToIndex(0)
            rule.onNodeWithText("Все графики").performClick()
            rule.onAllNodesWithContentDescription("Выше")[3].performClick()
            shotTop("10k_stats_order")
            rule.onNodeWithText("Готово").performClick()
            rule.onNodeWithText("Ещё").performClick(); shot("5_settings")
            rule.onNodeWithText("Оформление").performClick(); shotTop("5c_icons")
            rule.onNodeWithContentDescription("Закрыть").performClick()
            rule.onNodeWithText("Часы и весы (Health Connect)").performClick(); shotTop("5b_settings_hc")
            rule.onNodeWithContentDescription("Закрыть").performClick()
            rule.onNodeWithText("Данные: экспорт и копия").performClick()
            rule.onNodeWithText("Удалить все данные").performScrollTo(); shotTop("5e_data")
            rule.onNodeWithText("Удалить все данные").performClick(); shotTop("5f_wipe")
            rule.onNodeWithText("Отмена").performClick()
            rule.onNodeWithContentDescription("Закрыть").performClick()
            rule.onNodeWithText("О приложении").performClick(); shotTop("5d_about")
            rule.onNodeWithContentDescription("Закрыть").performClick()
            // сон: блок дня и карточка графиков
            rule.onNodeWithText("Графики").performClick()
            rule.onNode(androidx.compose.ui.test.hasScrollToIndexAction()).performScrollToNode(androidx.compose.ui.test.hasText("Гликемическая оценка"))
            shot("11c_stats_gly")
            rule.onNode(androidx.compose.ui.test.hasScrollToIndexAction()).performScrollToNode(androidx.compose.ui.test.hasText("Индекс дня"))
            shot("11d_stats_index")
            rule.onNode(androidx.compose.ui.test.hasScrollToIndexAction()).performScrollToNode(androidx.compose.ui.test.hasText("Все заметки за период"))
            rule.onNode(androidx.compose.ui.test.hasScrollToIndexAction()).performScrollToNode(androidx.compose.ui.test.hasText("Нажмите на дату, чтобы открыть день."))
            shot("11e_stats_diary")
            rule.onNodeWithContentDescription("Скрыть").performClick(); shot("11f_stats_diary_hidden")
            rule.onNodeWithContentDescription("Показать").performClick()
            rule.onNode(androidx.compose.ui.test.hasScrollToIndexAction()).performScrollToNode(androidx.compose.ui.test.hasText("Подъём"))
            shot("11a_stats_sleep")
            rule.onNodeWithText("Фазы").performScrollTo().performClick(); shot("11b_stats_sleep_stages")
            rule.onNode(androidx.compose.ui.test.hasScrollToIndexAction()).performScrollToIndex(0)
            rule.onNodeWithText("День").performClick()
            it.onActivity { a -> a.prefs.lang = "en"; I18n.lang = "en" }
            rule.onNodeWithText("Day").performClick(); shot("6_day_en")
            it.onActivity { a -> a.prefs.setPin("1234"); a.prefs.lockEnabled = true; a.locked = true }
            shot("7_lock")
            it.onActivity { a -> a.prefs.lang = "ru"; I18n.lang = "ru"; a.prefs.lockEnabled = false; a.locked = false }
            println("CSV:\n" + buildCsv(it.let { s -> var r: Repo? = null; s.onActivity { a -> r = a.repo }; r!! }).lines().take(4).joinToString("\n"))
        }
    }
}
