package org.maittaa.tabita

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

/** Тип пункта чек-листа. */
enum class ItemType(val ru: String) {
    CHECK("Галочка"),
    SCALE("Оценка (шкала)"),
    NUMBER("Число"),
    DURATION("Время (ч:мин)"),
    MULTI("Список вариантов"),
    TEXT("Текст"),
    MEALS("Питание (фото, калории)"),
    EXPENSES("Финансы (покупки)"),
    BODY("Весы openScale (все показатели)"),
    WATER("Вода (норма, ползунок)"),
    WELLBEING("Чекап самочувствия (тесты)"),
    /** Советы дня (5.20): плитка с подсказками по питанию, воде, шагам, тренировкам и сну. Значений не хранит. */
    TIPS("Советы дня"),
    /** Тренировка (5.24): решение на сегодня по дневнику, план и советы по восстановлению. Значений не хранит. */
    TRAINING("Тренировка (план и советы)"),
    /** Цель (5.31): цель из «Программы» и что осталось на сегодня. Значений не хранит. */
    GOAL("Цель (программа)"),
    RHYTHM("Ритм дня");

    val title get() = t(ru)
}

/** Вид пунктов: в одну группу кладутся только пункты одного вида. */
enum class GroupKind(val ru: String, val types: List<ItemType>) {
    CHECKS("Отметки", listOf(ItemType.CHECK)),
    VALUES("Числа и оценки", listOf(ItemType.SCALE, ItemType.NUMBER, ItemType.DURATION)),
    LISTS("Списки вариантов", listOf(ItemType.MULTI)),
    BLOCKS("Блоки", listOf(ItemType.WATER, ItemType.MEALS, ItemType.EXPENSES, ItemType.BODY, ItemType.TEXT, ItemType.WELLBEING, ItemType.TIPS, ItemType.TRAINING, ItemType.GOAL, ItemType.RHYTHM));

    val title get() = t(ru)

    companion object {
        fun of(type: ItemType) = entries.first { type in it.types }
    }
}

val ItemType.kind get() = GroupKind.of(this)

/**
 * Группа пунктов. Отметки одной группы показываются одной плиткой (id плитки = id группы),
 * остальные пункты — своими плитками; группа упорядочивает их в списке «Все пункты».
 */
data class Group(val id: String, val name: String, val kind: GroupKind) {
    val displayName: String
        get() = defaultGroupNames[id]?.let { (ru, en) -> if (name == ru && I18n.isEn) en else null } ?: name

    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("kind", kind.name)

    companion object {
        fun fromJson(o: JSONObject) = Group(
            o.getString("id"), o.optString("name"),
            runCatching { GroupKind.valueOf(o.getString("kind")) }.getOrDefault(GroupKind.BLOCKS),
        )
        fun newId() = "g_" + UUID.randomUUID().toString().take(6)
    }
}

private val defaultGroupNames = mapOf(
    "checks" to ("Отметки дня" to "Day checks"),
    "values" to ("Показатели" to "Metrics"),
    "lists" to ("Списки" to "Lists"),
    "blocks" to ("Блоки" to "Blocks"),
)

fun defaultGroups(): List<Group> = listOf(
    Group("checks", "Отметки дня", GroupKind.CHECKS),
    Group("values", "Показатели", GroupKind.VALUES),
    Group("lists", "Списки", GroupKind.LISTS),
    Group("blocks", "Блоки", GroupKind.BLOCKS),
)

/** Откуда пункт может заполняться автоматически. */
enum class Source(val ru: String) {
    NONE("Вручную"),
    HC_SLEEP("Часы: сон"),
    HC_STEPS("Часы: шаги"),
    HC_EXERCISE("Часы: тренировки, мин"),
    HC_RESTING_HR("Часы: пульс покоя"),
    HC_WEIGHT("Весы: вес"),
    OPENSCALE("openScale");

    val title get() = t(ru)
}

data class TemplateItem(
    val id: String = UUID.randomUUID().toString().take(8),
    val name: String,
    val type: ItemType,
    val plusAllowed: Boolean = false,
    val options: List<String> = emptyList(),
    val unit: String = "",
    val min: Double = 1.0,
    val max: Double = 10.0,
    val source: Source = Source.NONE,
    val hidden: Boolean = false,
    /** Для списка: нажатие прибавляет количество, удержание — сброс (иначе — просто отметка). */
    val counter: Boolean = true,
    /** Группа (id из [Group]); пусто — группа по умолчанию для вида пункта. */
    val group: String = "",
    /** 5.59.1: раздел внутри плитки отметок («Самочувствие», «Привычки»…); пусто — без раздела. */
    val section: String = "",
    /** 5.59.2: влияние отметки на совет на тренировку: "" — нет, "easy" — легче, "rest" — отдых. */
    val train: String = "",
    /** 5.61: настройки вариантов списка: раздел и «каждый день» (по названию варианта). */
    val optMeta: Map<String, OptMeta> = emptyMap(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("type", type.name)
        put("plus", plusAllowed); put("options", JSONArray(options))
        put("unit", unit); put("min", min); put("max", max)
        put("source", source.name); put("hidden", hidden); put("counter", counter)
        if (group.isNotEmpty()) put("group", group)
        if (section.isNotEmpty()) put("sec", section)
        if (train.isNotEmpty()) put("trn", train)
        optMeta.filterKeys { it in options }.filterValues { !it.isEmpty }.takeIf { it.isNotEmpty() }?.let { m ->
            put("om", JSONObject().apply { m.forEach { (k, v) -> put(k, v.toJson()) } })
        }
    }

    /** Пункт можно усреднять и рисовать линейным графиком. */
    val isNumeric get() = type == ItemType.SCALE || type == ItemType.NUMBER || type == ItemType.DURATION

    /** Название для показа: стандартные пункты переводятся, свои — как есть. */
    val displayName: String
        get() = defaultNames[id]?.let { (ru, en) -> if (name == ru && I18n.isEn) en else null } ?: name

    val displayUnit: String
        get() = defaultUnits[unit]?.takeIf { I18n.isEn } ?: unit

    companion object {
        fun fromJson(o: JSONObject): TemplateItem {
            val opts = o.optJSONArray("options") ?: JSONArray()
            return TemplateItem(
                id = o.getString("id"),
                name = o.getString("name"),
                type = runCatching { ItemType.valueOf(o.getString("type")) }.getOrDefault(ItemType.TEXT),
                plusAllowed = o.optBoolean("plus"),
                options = (0 until opts.length()).map { opts.getString(it) },
                unit = o.optString("unit"),
                min = o.optDouble("min", 1.0),
                max = o.optDouble("max", 10.0),
                source = runCatching { Source.valueOf(o.optString("source")) }.getOrDefault(Source.NONE),
                hidden = o.optBoolean("hidden"),
                counter = o.optBoolean("counter", true),
                group = o.optString("group"),
                section = o.optString("sec"),
                train = o.optString("trn"),
                optMeta = o.optJSONObject("om")?.let { om -> om.keys().asSequence().associateWith { k -> OptMeta.fromJson(om.getJSONObject(k)) } } ?: emptyMap(),
            )
        }
    }
}

private val defaultNames = mapOf(
    "sleep" to ("Сон" to "Sleep"),
    "rate" to ("Оценка дня" to "Day rating"),
    "used" to ("Что использовалось" to "What I took"),
    "steps" to ("Шаги" to "Steps"),
    "ex" to ("Тренировки" to "Workouts"),
    "rhr" to ("Пульс покоя" to "Resting heart rate"),
    "weight" to ("Вес" to "Weight"),
    "food" to ("Питание" to "Food"),
    "money" to ("Финансы" to "Finance"),
    "why" to ("Почему / заметка" to "Why / note"),
    "body" to ("Весы" to "Scale"),
    "water" to ("Вода" to "Water"),
    "wb" to ("Чекап самочувствия" to "Wellbeing check-up"),
)
private val defaultUnits = mapOf("мин" to "min", "уд/мин" to "bpm", "кг" to "kg", "см" to "cm", "л" to "L")

/**
 * Значения хранятся строками:
 * CHECK: "1" — отмечено, "2" — с плюсом; SCALE/NUMBER: число; DURATION: минуты;
 * MULTI: варианты через [MULTI_SEP]; TEXT: текст; MEALS/EXPENSES: JSON-массив.
 * Ключ "<id>!m" = "1" — значение с часов исправлено вручную (синхронизация его не трогает).
 */
const val MULTI_SEP = "\u001F"
const val COUNT_SEP = "\u001E"

/** Список с количеством: "Кофе␞2␟Чай" → {Кофе=2, Чай=1}. Старые значения без количества = 1. */
/** 5.61: вариант списка — раздел в плитке и «каждый день» (добавки, витамины: видно, что ещё не отмечено). */
data class OptMeta(val section: String = "", val daily: Boolean = false) {
    val isEmpty get() = section.isBlank() && !daily
    fun toJson(): JSONObject = JSONObject().apply { if (section.isNotBlank()) put("s", section); if (daily) put("d", true) }
    companion object { fun fromJson(o: JSONObject) = OptMeta(o.optString("s"), o.optBoolean("d")) }
}

/** Варианты «каждый день», которые в день [v] ещё не отмечены. */
fun TemplateItem.dailyMissing(v: String?): List<String> {
    val c = parseCounts(v)
    return options.filter { optMeta[it]?.daily == true && (c[it] ?: 0) == 0 }
}

fun parseCounts(v: String?): LinkedHashMap<String, Int> {
    val m = LinkedHashMap<String, Int>()
    v?.split(MULTI_SEP)?.filter { it.isNotEmpty() }?.forEach { part ->
        val name = part.substringBefore(COUNT_SEP)
        val n = part.substringAfter(COUNT_SEP, "1").toIntOrNull() ?: 1
        if (n > 0) m[name] = (m[name] ?: 0) + n
    }
    return m
}

fun countsToString(m: Map<String, Int>): String? =
    m.filterValues { it > 0 }.entries.joinToString(MULTI_SEP) { (k, n) -> if (n == 1) k else "$k$COUNT_SEP$n" }.ifEmpty { null }

/** Вода: норма в литрах хранится в [TemplateItem.max]. */
fun waterTemplateItem() = TemplateItem(id = "water", name = "Вода", type = ItemType.WATER, min = 0.0, max = 2.0, unit = "л")

fun tipsTemplateItem() = TemplateItem(id = "tips", name = "Советы дня", type = ItemType.TIPS)

fun trainingTemplateItem() = TemplateItem(id = "training", name = "Тренировка", type = ItemType.TRAINING)

fun goalTemplateItem() = TemplateItem(id = "goal", name = "Цель", type = ItemType.GOAL)

/** 5.43: «Ритм дня» отдельной плиткой. */
fun rhythmTemplateItem() = TemplateItem(id = "rhythm", name = "Ритм дня", type = ItemType.RHYTHM)

/** Плитки-«одиночки»: в шаблоне не больше одной, id фиксирован. */
val singletonTypes = mapOf(ItemType.WELLBEING to "wb", ItemType.TIPS to "tips", ItemType.TRAINING to "training", ItemType.GOAL to "goal", ItemType.RHYTHM to "rhythm")

fun wellbeingTemplateItem() = TemplateItem(id = "wb", name = "Чекап самочувствия", type = ItemType.WELLBEING)

fun bodyTemplateItem() = TemplateItem(id = "body", name = "Весы", type = ItemType.BODY, source = Source.OPENSCALE)
fun manualKey(id: String) = "$id!m"
/** Ключ «откуда пришли данные»: пакеты приложений через запятую. */
fun srcKey(id: String) = "$id!src"

val expenseCategoriesRu = listOf("Продукты", "Кафе", "Транспорт", "Дом", "Здоровье", "Одежда", "Развлечения", "Другое")

fun defaultTemplate(): List<TemplateItem> = listOf(
    tipsTemplateItem(),
    goalTemplateItem(),
    rhythmTemplateItem(),
    trainingTemplateItem(),
    TemplateItem(id = "sleep", name = "Сон", type = ItemType.DURATION, source = Source.HC_SLEEP),
    TemplateItem(id = "rate", name = "Оценка дня", type = ItemType.SCALE, min = 1.0, max = 10.0),
    TemplateItem(id = "work", name = "Work", type = ItemType.CHECK, plusAllowed = true),
    TemplateItem(id = "tr", name = "Tr", type = ItemType.CHECK, plusAllowed = true),
    TemplateItem(id = "dr", name = "Dr", type = ItemType.CHECK, plusAllowed = true),
    TemplateItem(id = "soc", name = "Soc", type = ItemType.CHECK, plusAllowed = true),
    TemplateItem(id = "nc", name = "NC", type = ItemType.CHECK),
    TemplateItem(id = "np", name = "NP", type = ItemType.CHECK),
    TemplateItem(
        id = "used", name = "Что использовалось", type = ItemType.MULTI,
        options = listOf("Кофе", "Зелёный чай", "Элеутерококк"),
    ),
    waterTemplateItem(),
    TemplateItem(id = "steps", name = "Шаги", type = ItemType.NUMBER, source = Source.HC_STEPS),
    TemplateItem(id = "ex", name = "Тренировки", type = ItemType.NUMBER, unit = "мин", source = Source.HC_EXERCISE),
    TemplateItem(id = "rhr", name = "Пульс покоя", type = ItemType.NUMBER, unit = "уд/мин", source = Source.HC_RESTING_HR),
    TemplateItem(id = "weight", name = "Вес", type = ItemType.NUMBER, unit = "кг", source = Source.HC_WEIGHT),
    TemplateItem(id = "food", name = "Питание", type = ItemType.MEALS),
    TemplateItem(id = "money", name = "Финансы", type = ItemType.EXPENSES, options = expenseCategoriesRu),
    TemplateItem(id = "why", name = "Почему / заметка", type = ItemType.TEXT),
    wellbeingTemplateItem(),
)

// ---------- Питание ----------

val mealKindsRu = listOf("Завтрак", "Обед", "Ужин", "Перекус")

data class Meal(
    val id: String = UUID.randomUUID().toString().take(8),
    val kind: String = mealKindsRu[0],
    val text: String = "",
    val photos: List<String> = emptyList(),
    val kcal: Double? = null,
    val protein: Double? = null,
    val fat: Double? = null,
    val carbs: Double? = null,
    /** Уточнение для ИИ: объём тарелки, вес, состав смузи в процентах и т. п. */
    val hint: String = "",
    /** Гликемический индекс приёма пищи (взвешенный по углеводам), 0–100. */
    val gi: Double? = null,
    /** Гликемическая нагрузка; если не задана — считается из ГИ и углеводов. */
    val gl: Double? = null,
    /** Сцепка: карточки с одинаковым link — один приём пищи (позиции открываются по отдельности). */
    val link: String = "",
    /** 5.45: когда ели (мс) — время съёмки фото блюда; null — неизвестно. */
    val at: Long? = null,
) {
    /** Ключ приёма: у сцепленных карточек общий. */
    val unit: String get() = link.ifEmpty { id }

    /** Нагрузка для расчётов: заданная вручную/ИИ или ГИ × углеводы / 100. */
    val glEff: Double? get() = gl ?: gi?.let { g -> carbs?.let { c -> g * c / 100 } }

    fun toJson() = JSONObject().apply {
        put("id", id); put("kind", kind); put("text", text); put("photos", JSONArray(photos))
        if (hint.isNotBlank()) put("hint", hint)
        gi?.let { put("gi", it) }; gl?.let { put("gl", it) }
        if (link.isNotEmpty()) put("link", link)
        at?.let { put("at", it) }
        kcal?.let { put("kcal", it) }; protein?.let { put("p", it) }
        fat?.let { put("f", it) }; carbs?.let { put("c", it) }
    }

    companion object {
        fun fromJson(o: JSONObject) = Meal(
            id = o.optString("id", UUID.randomUUID().toString().take(8)),
            kind = o.optString("kind", mealKindsRu[0]),
            text = cleanMealText(o.optString("text")),
            photos = o.optJSONArray("photos")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
            kcal = o.optNum("kcal"), protein = o.optNum("p"), fat = o.optNum("f"), carbs = o.optNum("c"),
            hint = o.optString("hint"),
            gi = o.optNum("gi"), gl = o.optNum("gl"),
            link = o.optString("link"),
            at = if (o.has("at")) o.optLong("at") else null,
        )
    }
}

/** ИИ иногда возвращает описание списком ["…","…"] — показываем позиции через запятую. */
fun cleanMealText(s: String): String {
    val t = s.trim()
    if (!t.startsWith("[") || !t.endsWith("]")) return s
    return runCatching { JSONArray(t).let { a -> (0 until a.length()).joinToString(", ") { a.get(it).toString().trim() } } }.getOrDefault(s)
}

/** Карточки, сгруппированные по приёмам (сцепленные — вместе), в порядке первой карточки. */
fun mealUnits(meals: List<Meal>): List<List<Meal>> = meals.groupBy { it.unit }.values.toList()

/** Сцепленный приём как одна карточка: суммы калорий, БЖУ и ГН; ГИ — среднее, взвешенное по углеводам. */
fun combineMeals(list: List<Meal>): Meal {
    if (list.size == 1) return list[0]
    fun sum(f: (Meal) -> Double?) = list.mapNotNull(f).takeIf { it.isNotEmpty() }?.sum()
    val withGi = list.filter { it.gi != null && (it.carbs ?: 0.0) > 0 }
    val gi = when {
        withGi.isNotEmpty() -> withGi.sumOf { it.gi!! * it.carbs!! } / withGi.sumOf { it.carbs!! }
        list.any { it.gi != null } -> list.mapNotNull { it.gi }.average()
        else -> null
    }
    return Meal(
        id = list[0].unit, kind = list[0].kind, text = list.map { it.text }.filter { it.isNotBlank() }.joinToString("; "),
        photos = list.flatMap { it.photos }, kcal = sum { it.kcal }, protein = sum { it.protein }, fat = sum { it.fat },
        carbs = sum { it.carbs }, gi = gi, gl = sum { it.glEff }, link = list[0].unit,
    )
}

fun parseMeals(v: String?): List<Meal> = runCatching {
    val a = JSONArray(v ?: return emptyList())
    (0 until a.length()).map { Meal.fromJson(a.getJSONObject(it)) }
}.getOrDefault(emptyList())

fun mealsToString(list: List<Meal>): String? =
    if (list.isEmpty()) null else JSONArray(list.map { it.toJson() }).toString()

// ---------- Финансы ----------

data class Purchase(
    val id: String = UUID.randomUUID().toString().take(8),
    val name: String = "",
    val qty: Double = 1.0,
    val unit: String = "шт",
    val price: Double = 0.0, // цена за единицу
    val category: String = "",
    val income: Boolean = false,
) {
    val total get() = qty * price
    /** + для дохода, − для расхода */
    val signed get() = if (income) total else -total

    fun toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("qty", qty); put("unit", unit)
        put("price", price); put("cat", category); put("inc", income)
    }

    companion object {
        fun fromJson(o: JSONObject) = Purchase(
            id = o.optString("id", UUID.randomUUID().toString().take(8)),
            name = o.optString("name"), qty = o.optDouble("qty", 1.0), unit = o.optString("unit", "шт"),
            price = o.optDouble("price", 0.0), category = o.optString("cat"), income = o.optBoolean("inc"),
        )
    }
}

fun parsePurchases(v: String?): List<Purchase> = runCatching {
    val a = JSONArray(v ?: return emptyList())
    (0 until a.length()).map { Purchase.fromJson(a.getJSONObject(it)) }
}.getOrDefault(emptyList())

fun purchasesToString(list: List<Purchase>): String? =
    if (list.isEmpty()) null else JSONArray(list.map { it.toJson() }).toString()

private fun JSONObject.optNum(k: String): Double? = if (has(k) && !isNull(k)) optDouble(k).takeIf { !it.isNaN() } else null

// ---------- форматирование ----------

fun LocalDate.key(): String = toString()

fun formatMinutes(min: Double): String {
    val m = Math.round(min).toInt()
    return if (I18n.isEn) "${m / 60}h ${"%02d".format(m % 60)}m" else "${m / 60}ч ${"%02d".format(m % 60)}м"
}

fun formatNumber(v: Double): String =
    if (v == Math.floor(v) && Math.abs(v) < 1e9) v.toLong().toString()
    else "%.1f".format(java.util.Locale.US, v).let { if (I18n.isEn) it else it.replace('.', ',') }

/** Деньги: 1 234,50 ₽ */
fun formatMoney(v: Double): String {
    val sym = I18n.currency
    val abs = Math.abs(v)
    val s = if (abs == Math.floor(abs)) "%,d".format(java.util.Locale.US, abs.toLong())
    else "%,.2f".format(java.util.Locale.US, abs)
    val loc = if (I18n.isEn) s else s.replace(',', ' ').replace('.', ',')
    return (if (v < 0) "−" else "") + loc + " " + sym
}

fun parseNumber(s: String?): Double? = s?.trim()?.replace(" ", "")?.replace(',', '.')?.toDoubleOrNull()

fun LocalDate.ruTitle(): String = I18n.dayTitle(this)
fun LocalDate.ruShort(): String = "%02d.%02d".format(dayOfMonth, monthValue)
fun monthTitle(d: LocalDate) = I18n.month(d.monthValue) + " " + d.year
