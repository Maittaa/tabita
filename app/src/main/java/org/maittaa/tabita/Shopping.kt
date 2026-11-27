package org.maittaa.tabita

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

/**
 * 5.57: шаблоны приёмов пищи — сохранённый приём (одна карточка или сцепленные позиции) без фото и времени.
 * Добавляется в день одним нажатием. Хранится в Prefs.mealTpls (входит в резервную копию настроек).
 */
data class MealTpl(val id: String, val name: String, val kind: String, val items: List<Meal>) {
    val kcal get() = mealKcal(items)
    val protein get() = mealMacros(items)?.protein

    fun toJson(): JSONObject = JSONObject().put("id", id).put("n", name).put("kind", kind)
        .put("m", JSONArray(items.map { it.toJson() }))

    /** Новые карточки для дня: новые id, общая сцепка, если позиций несколько; [at] — время еды; фото копируются в [dir]. */
    fun instantiate(at: Long?, dir: java.io.File? = null): List<Meal> {
        val fresh = items.map { it.copy(id = UUID.randomUUID().toString().take(8), link = "", photos = MealTpls.copyPhotos(dir, it.photos), at = at, kind = kind) }
        if (fresh.size < 2) return fresh
        val head = fresh[0].id
        return fresh.map { it.copy(link = head) }
    }

    companion object {
        fun fromJson(o: JSONObject): MealTpl? = runCatching {
            val a = o.getJSONArray("m")
            val items = (0 until a.length()).map { Meal.fromJson(a.getJSONObject(it)) }
            if (items.isEmpty()) null else MealTpl(o.getString("id"), o.getString("n"), o.optString("kind", items[0].kind), items)
        }.getOrNull()

        /** Шаблон из приёма дня: фото — копии (у шаблона свои файлы), без времени и сцепки; имя — заданное или из описания. */
        fun from(unit: List<Meal>, name: String, dir: java.io.File? = null): MealTpl = MealTpl(
            UUID.randomUUID().toString().take(8), name.trim().ifBlank { defaultName(unit) }, unit[0].kind,
            unit.map { it.copy(photos = MealTpls.copyPhotos(dir, it.photos), at = null, link = "") },
        )

        fun defaultName(unit: List<Meal>): String =
            unit.map { it.text.trim() }.filter { it.isNotBlank() }.joinToString(", ").take(40).ifBlank { t(unit[0].kind) }
    }
}

object MealTpls {
    fun parse(s: String?): List<MealTpl> = runCatching {
        val a = JSONArray(s ?: return emptyList())
        (0 until a.length()).mapNotNull { MealTpl.fromJson(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    fun toJson(list: List<MealTpl>): String = JSONArray(list.map { it.toJson() }).toString()

    fun all(prefs: Prefs) = parse(prefs.mealTpls)
    fun save(prefs: Prefs, list: List<MealTpl>) { prefs.mealTpls = toJson(list) }
    fun add(prefs: Prefs, t: MealTpl) { save(prefs, all(prefs) + t); Log.i("Food", "шаблон приёма: ${t.name}") }
    /** Удалить шаблон вместе с его фото. */
    fun remove(prefs: Prefs, id: String, dir: java.io.File? = null) {
        val l = all(prefs)
        l.firstOrNull { it.id == id }?.let { deletePhotos(dir, it.items.flatMap { m -> m.photos }) }
        save(prefs, l.filterNot { it.id == id })
    }

    /** Копии фото под новыми именами (у дня и у шаблона — свои файлы, удаление одного не трогает другое). Без [dir] — без фото. */
    fun copyPhotos(dir: java.io.File?, names: List<String>): List<String> {
        if (dir == null) return emptyList()
        return names.mapNotNull { n ->
            val src = java.io.File(dir, n).takeIf { it.exists() } ?: return@mapNotNull null
            val name = UUID.randomUUID().toString().take(12) + ".jpg"
            runCatching { src.copyTo(java.io.File(dir, name)); name }.getOrNull()
        }
    }

    fun deletePhotos(dir: java.io.File?, names: Collection<String>) { if (dir != null) names.forEach { java.io.File(dir, it).delete() } }

    /** Добавить шаблон в день: в конец списка приёмов; время — сейчас, если день сегодняшний. */
    fun applyTo(meals: List<Meal>, t: MealTpl, date: String, now: Long = System.currentTimeMillis(), dir: java.io.File? = null): List<Meal> =
        meals + t.instantiate(if (date == LocalDate.now().toString()) now else null, dir)
}

/**
 * 5.57: список покупок на неделю из меню программы. Меню на 7 дней — с разными вариантами по дням
 * (как кнопка «Другой вариант»), граммы суммируются по продукту. Крупы, для которых в справочнике есть
 * сухой вариант, пересчитываются в сухой вес по калорийности (столько же калорий — сухого меньше).
 */
object Shopping {
    data class Line(val food: Food, val grams: Double, val dry: Food? = null) {
        /** Вес для покупки: сухой, если есть пара в справочнике. */
        val buyGrams get() = dry?.let { grams * food.kcal / it.kcal } ?: grams
        val buyFood get() = dry ?: food
        fun amount(): String = Program.Item(buyFood, roundBuy(buyGrams)).amount()
    }

    data class Extra(val text: String, val done: Boolean)

    /** Состояние списка: неделя (понедельник), отмеченные продукты и свои позиции. */
    data class State(val week: String, val checked: Set<String>, val extra: List<Extra>) {
        fun toJson(): String = JSONObject().put("w", week).put("k", JSONArray(checked.toList()))
            .put("x", JSONArray(extra.map { JSONObject().put("t", it.text).put("d", it.done) })).toString()
    }

    fun state(prefs: Prefs, today: LocalDate = LocalDate.now()): State {
        val week = Cardio.weekStart(today).toString()
        val s = runCatching {
            val o = JSONObject(prefs.shopList ?: return State(week, emptySet(), emptyList()))
            val k = o.getJSONArray("k"); val x = o.optJSONArray("x") ?: JSONArray()
            State(o.getString("w"), (0 until k.length()).map { k.getString(it) }.toSet(),
                (0 until x.length()).map { x.getJSONObject(it).let { e -> Extra(e.getString("t"), e.optBoolean("d")) } })
        }.getOrNull() ?: return State(week, emptySet(), emptyList())
        // новая неделя: отметки сброшены, купленные свои позиции убраны, некупленные остаются
        return if (s.week == week) s else State(week, emptySet(), s.extra.filterNot { it.done })
    }

    fun save(prefs: Prefs, s: State) { prefs.shopList = s.toJson() }

    /** Сухой вариант крупы из справочника: «Гречка отварная» → «Гречка (сухая крупа)». */
    fun dryOf(f: Food): Food? {
        if (f.cat != "grain" || !f.ru.contains("отварн")) return null
        val stem = f.ru.substringBefore(" отварн").trim()
        return FoodDb.all.firstOrNull { it.cat == "grain" && it !== f && it.ru.startsWith(stem) && it.ru.contains("сух") }
    }

    private fun roundBuy(g: Double) = when {
        g >= 1000 -> Math.round(g / 50) * 50.0
        g >= 100 -> Math.round(g / 10) * 10.0
        else -> Math.round(g / 5).coerceAtLeast(1) * 5.0
    }

    /** Меню на 7 дней: день i — вариант i по каждому приёму. */
    fun weekMenu(g: Goal, tg: Program.Targets, r: DayRhythm?, prefs: Prefs): List<List<Program.Meal>> =
        (0 until 7).map { i -> Program.menu(g, tg, r, prefs, Program.Slot.entries.associateWith { i }) }

    fun lines(menus: List<List<Program.Meal>>): List<Line> =
        menus.flatten().flatMap { it.items }.groupBy { it.food.key }
            .map { (_, l) -> Line(l[0].food, l.sumOf { it.grams }, dryOf(l[0].food)) }
            .sortedWith(compareBy<Line> { groupOrder(it.food.cat) }.thenBy { it.buyFood.name })

    private val groups = listOf(
        setOf("meat") to "Мясо и птица", setOf("fish") to "Рыба", setOf("dairy", "egg") to "Молочное и яйца",
        setOf("grain", "bread", "legume") to "Крупы, хлеб, бобовые", setOf("veg") to "Овощи", setOf("fruit") to "Фрукты",
        setOf("nuts", "fat") to "Орехи и масло",
    )
    fun groupOrder(cat: String) = groups.indexOfFirst { cat in it.first }.let { if (it < 0) groups.size else it }
    fun groupName(cat: String) = groups.firstOrNull { cat in it.first }?.second?.let { t(it) } ?: t("Другое")

    /** Текст для отправки в мессенджер: некупленное по группам. */
    fun shareText(lines: List<Line>, s: State): String {
        val sb = StringBuilder(t("Список покупок")).append('\n')
        lines.filterNot { it.buyFood.key in s.checked }.groupBy { groupName(it.food.cat) }.forEach { (gname, l) ->
            sb.append('\n').append(gname).append('\n')
            l.forEach { sb.append("• ").append(it.buyFood.name).append(" — ").append(it.amount()).append('\n') }
        }
        val ex = s.extra.filterNot { it.done }
        if (ex.isNotEmpty()) { sb.append('\n').append(t("Ещё")).append('\n'); ex.forEach { sb.append("• ").append(it.text).append('\n') } }
        return sb.toString().trimEnd()
    }
}
