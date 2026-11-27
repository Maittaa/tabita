package org.maittaa.tabita

import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate

/*
 * Вкладка «Месяц»: всё считается локально из записанных дней.
 * Метрика — пункт дня (или вариант списка, например «Кофе»), у которой есть значение по дням и цель.
 */

enum class MKind { CHECK, SCALE, AMOUNT, WATER, OPTION }

data class Metric(
    val key: String,
    val item: TemplateItem,
    val kind: MKind,
    val option: String? = null,
) {
    val title: String get() = option?.let { t(it) } ?: item.displayName
    /** Группа для выбора строк: «Отметки дня», имя списка, «Показатели». */
    val group: String get() = when (kind) { MKind.CHECK -> t("Отметки дня"); MKind.OPTION -> item.displayName; else -> t("Показатели") }
    val isSleep get() = item.source == Source.HC_SLEEP
    /** Пункт заполняется руками (а не с часов) — пропуск значит «не отмечено». */
    val manual get() = item.source == Source.NONE

    /** Значение за день. CHECK/OPTION в заполненный день без отметки = 0, в незаполненный — null. */
    fun value(day: Map<String, String>, filled: Boolean): Double? = when (kind) {
        MKind.CHECK -> when (day[item.id]) { null -> if (filled) 0.0 else null; "2" -> 2.0; else -> 1.0 }
        MKind.OPTION -> parseCounts(day[item.id])[option]?.toDouble() ?: if (filled) 0.0 else null
        else -> parseNumber(day[item.id])
    }

    /** Цель по умолчанию; [values] — значения за месяц (для медианы). */
    fun defaultGoal(values: List<Double?>): Double = when (kind) {
        MKind.CHECK, MKind.OPTION -> 1.0
        MKind.WATER -> item.max.takeIf { it > 0 } ?: 2.0
        MKind.SCALE -> Math.round(item.min + 0.7 * (item.max - item.min)).toDouble()
        MKind.AMOUNT -> when (item.source) {
            Source.HC_SLEEP -> 420.0
            Source.HC_STEPS -> 8000.0
            Source.HC_EXERCISE -> 20.0
            else -> values.filterNotNull().filter { it > 0 }.sorted().let { s ->
                if (s.isEmpty()) 1.0 else niceRound(s[s.size / 2])
            }
        }
    }

    /** Шаг для кнопок −/+ при настройке цели. */
    fun goalStep(goal: Double): Double = when {
        kind == MKind.SCALE || kind == MKind.OPTION -> 1.0
        item.type == ItemType.DURATION -> 30.0
        goal >= 5000 -> 500.0
        goal >= 1000 -> 100.0
        goal >= 100 -> 10.0
        goal >= 20 -> 5.0
        else -> 1.0
    }

    fun format(v: Double): String = when {
        item.type == ItemType.DURATION -> formatMinutes(v)
        kind == MKind.WATER -> formatLiters(v)
        kind == MKind.CHECK -> if (v >= 1) "✓" else "—"
        else -> formatNumber(Math.round(v * 10) / 10.0) + if (item.unit.isNotEmpty() && kind == MKind.AMOUNT) " ${item.displayUnit}" else ""
    }

    /** Подпись цели: «≥ 7ч 00м», «≥ нормы», «отмечено». */
    fun goalText(goal: Double): String = when (kind) {
        MKind.CHECK -> t("отмечено")
        MKind.WATER -> t("≥ нормы")
        MKind.OPTION -> if (goal <= 1) t("было") else "≥ " + formatNumber(goal)
        else -> "≥ " + format(goal)
    }

    /** Как называть фактор: «Сон ≥ 7ч 00м», «Вода ≥ нормы», «Work», «Кофе ≥ 3». */
    fun factorTitle(goal: Double): String = when (kind) {
        MKind.CHECK -> title
        MKind.OPTION -> if (goal <= 1) title else "$title ≥ ${formatNumber(goal)}"
        else -> "$title ${goalText(goal)}"
    }
}

private fun niceRound(v: Double): Double = when {
    v >= 1000 -> Math.round(v / 100) * 100.0
    v >= 100 -> Math.round(v / 10) * 10.0
    v >= 10 -> Math.round(v).toDouble()
    else -> Math.round(v * 10) / 10.0
}

/** Настройки вкладки «Месяц»: состав индекса, цели, цель индекса, что считать итогом дня. */
data class MonthCfg(
    val index: Set<String>? = null,
    val goals: Map<String, Double> = emptyMap(),
    val target: Int = 70,
    val outcome: String = "rate",
    /** Строки карты по дням (null — все). */
    val heat: Set<String>? = null,
    /** Галочки «каждый день»: не отмечено = 0 в индексе. Остальные — бонус. */
    val daily: Set<String> = emptySet(),
    /** Цель «раз в неделю» для галочек. */
    val weekly: Map<String, Int> = emptyMap(),
    /** Отметки и варианты списков, которые идут в индекс со знаком «минус» (вредное: кофе, сигареты…). */
    val negative: Set<String> = emptySet(),
    /** 5.37: цели и режимы — автоматически по цели «Программы»; вручную задаётся только состав индекса. */
    val auto: Boolean = false,
) {
    /**
     * Автонастройка под цель: сон, шаги и тренировки — по цели, остальное — по умолчанию (оценка дня ≥ 70 % шкалы,
     * вода ≥ нормы, прочее — медиана); галочки — бонус, «минус» для вредного остаётся как отмечено раньше.
     */
    fun autoApply(template: List<TemplateItem>, goal: Goal?): MonthCfg {
        val g = mutableMapOf<String, Double>()
        template.forEach { it ->
            when (it.source) {
                Source.HC_SLEEP -> g[it.id] = if (goal == Goal.WELLBEING) 450.0 else 420.0
                Source.HC_STEPS -> g[it.id] = when (goal) { Goal.LOSE -> 9000.0; Goal.WELLBEING -> 8000.0; Goal.MUSCLE, Goal.GAIN, Goal.DEFICITS -> 7000.0; null -> 8000.0 }
                Source.HC_EXERCISE -> g[it.id] = when (goal) { Goal.LOSE, Goal.MUSCLE, Goal.WELLBEING -> 30.0; else -> 20.0 }
                else -> {}
            }
        }
        return copy(auto = true, goals = g, daily = emptySet(), weekly = emptyMap())
    }
    /** Задать участие пункта в индексе: null — не влияет, true — плюс, false — минус. [all] — текущий состав индекса. */
    fun withEffect(key: String, plus: Boolean?, all: Set<String>): MonthCfg = copy(
        index = if (plus == null) all - key else all + key,
        negative = if (plus == false) negative + key else negative - key,
        weekly = if (plus == false) weekly - key else weekly,
    )

    fun toJson(): String = JSONObject().apply {
        index?.let { put("index", org.json.JSONArray(it.toList())) }
        put("goals", JSONObject(goals as Map<*, *>)); put("target", target); put("outcome", outcome)
        heat?.let { put("heat", org.json.JSONArray(it.toList())) }
        put("daily", org.json.JSONArray(daily.toList()))
        put("weekly", JSONObject(weekly as Map<*, *>))
        put("neg", org.json.JSONArray(negative.toList()))
        put("auto", auto)
    }.toString()

    companion object {
        fun parse(s: String?): MonthCfg = runCatching {
            val o = JSONObject(s ?: return MonthCfg())
            val g = o.optJSONObject("goals") ?: JSONObject()
            MonthCfg(
                index = o.optJSONArray("index")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() },
                goals = g.keys().asSequence().associateWith { g.getDouble(it) },
                target = o.optInt("target", 70),
                outcome = o.optString("outcome", "rate"),
                heat = o.optJSONArray("heat")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() },
                daily = o.optJSONArray("daily")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet(),
                weekly = o.optJSONObject("weekly")?.let { w -> w.keys().asSequence().associateWith { w.getInt(it) } } ?: emptyMap(),
                negative = o.optJSONArray("neg")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet(),
                auto = o.optBoolean("auto", false),
            )
        }.getOrDefault(MonthCfg())
    }
}

/** Типы пунктов, которые заполняются руками: по ним понимаем, что день «открывали». */
private val manualTypes = setOf(ItemType.CHECK, ItemType.SCALE, ItemType.MULTI, ItemType.WATER, ItemType.TEXT, ItemType.MEALS, ItemType.EXPENSES)

fun isFilled(day: Map<String, String>, template: List<TemplateItem>): Boolean =
    template.any { it.source == Source.NONE && (it.type in manualTypes || it.type == ItemType.NUMBER) && !day[it.id].isNullOrEmpty() }

/** Все метрики шаблона, подходящие для анализа. Варианты списков — только встречавшиеся в [seen]. */
fun monthMetrics(template: List<TemplateItem>, seen: List<Map<String, String>>): List<Metric> {
    val out = mutableListOf<Metric>()
    template.filter { !it.hidden }.forEach { item ->
        when (item.type) {
            ItemType.CHECK -> out += Metric(item.id, item, MKind.CHECK)
            ItemType.SCALE -> out += Metric(item.id, item, MKind.SCALE)
            ItemType.WATER -> out += Metric(item.id, item, MKind.WATER)
            ItemType.NUMBER, ItemType.DURATION ->
                if (item.source != Source.HC_RESTING_HR && item.source != Source.HC_WEIGHT) out += Metric(item.id, item, MKind.AMOUNT)
            ItemType.MULTI -> {
                val opts = LinkedHashSet<String>()
                seen.forEach { d -> opts += parseCounts(d[item.id]).keys }
                // только текущие варианты: переименованные и удалённые в анализ не попадают
                item.options.filter { it in opts }.forEach { o ->
                    out += Metric(item.id + ":" + o, item, MKind.OPTION, o)
                }
            }
            else -> {}
        }
    }
    // оценка дня — первой строкой
    return out.sortedBy { if (it.kind == MKind.SCALE) 0 else 1 }
}

/** Данные месяца, посчитанные один раз для всех карточек. */
class MonthData(
    val template: List<TemplateItem>,
    val entries: Map<String, Map<String, String>>,
    val month: LocalDate,
    val cfg: MonthCfg,
    val today: LocalDate = LocalDate.now(),
) {
    val days: List<LocalDate> = month.withDayOfMonth(1).let { s -> (0 until s.lengthOfMonth()).map { s.plusDays(it.toLong()) } }
    private val prevDays: List<LocalDate> = month.withDayOfMonth(1).minusMonths(1).let { s -> (0 until s.lengthOfMonth()).map { s.plusDays(it.toLong()) } }

    private fun dayMap(d: LocalDate) = entries[d.key()] ?: emptyMap()
    fun filled(d: LocalDate) = isFilled(dayMap(d), template)

    val metrics: List<Metric> = monthMetrics(template, (prevDays + days).map { dayMap(it) }).let { seen ->
        // варианты списков, явно включённые в индекс, учитываем, даже если их ещё ни разу не отмечали
        val have = seen.map { it.key }.toSet()
        val extra = template.filter { it.type == ItemType.MULTI && !it.hidden }.flatMap { item ->
            item.options.map { o -> Metric(item.id + ":" + o, item, MKind.OPTION, o) }
        }.filter { it.key !in have && (it.key in (cfg.index ?: emptySet()) || it.key in cfg.negative) }
        seen + extra
    }
    val filledDays: Int = days.count { filled(it) }

    /** Значение метрики в любой день (в т.ч. вне месяца — для «накануне»). */
    fun value(m: Metric, d: LocalDate): Double? = if (d > today) null else m.value(dayMap(d), filled(d))

    fun series(m: Metric, ds: List<LocalDate> = days): List<Double?> = ds.map { value(m, it) }

    fun goal(m: Metric): Double = cfg.goals[m.key] ?: m.defaultGoal(series(m, prevDays + days))
    /** 5.42: у воды своя норма на каждый день (Hydration, ключ `<id>!n`), если цель не задана вручную. */
    fun goalOn(m: Metric, d: LocalDate?): Double =
        if (d != null && m.kind == MKind.WATER && cfg.goals[m.key] == null) Hydration.normOf(m.item, dayMap(d)) else goal(m)
    fun met(m: Metric, v: Double?, d: LocalDate? = null): Boolean = v != null && v >= goalOn(m, d) - 1e-9

    // ---------- 1. тепловая карта ----------

    /** Уровень клетки: null — пропуск, 0 — нет, 1 — чуть, 2 — норма, 3 — много. */
    fun level(m: Metric, v: Double?, d: LocalDate? = null): Int? {
        if (v == null) return null
        val g = goalOn(m, d)
        return when (m.kind) {
            MKind.CHECK -> when { v <= 0 -> 0; v >= 2 -> 3; else -> 2 }
            MKind.OPTION -> when { v <= 0 -> 0; v < 2 -> 1; v < 3 -> 2; else -> 3 }
            MKind.SCALE -> {
                val r = if (g > m.item.min) (v - m.item.min) / (g - m.item.min) else 1.0
                when { r < 1 -> 1; r < 1.3 -> 2; else -> 3 }
            }
            else -> {
                if (v <= 0) return 0
                val r = v / g
                when { r < 1 - 1e-9 -> 1; r < 1.5 -> 2; else -> 3 }
            }
        }
    }

    /** Строки карты: только метрики, у которых в месяце есть хоть одно ненулевое значение. */
    val heatRows: List<Pair<Metric, List<Int?>>> = metrics.filter { cfg.heat == null || it.key in cfg.heat }.mapNotNull { m ->
        val s = series(m)
        if (s.none { (it ?: 0.0) > 0 }) null else m to s.mapIndexed { i, v -> level(m, v, days[i]) }
    }

    // ---------- 3. индекс дня ----------

    private val defaultIndex: Set<String> by lazy {
        val keys = metrics.map { it.key }
        val s = LinkedHashSet<String>()
        metrics.firstOrNull { it.kind == MKind.SCALE }?.let { s += it.key }
        listOf("work", "tr").filter { it in keys }.ifEmpty { metrics.filter { it.kind == MKind.CHECK }.take(2).map { it.key } }.forEach { s += it }
        metrics.firstOrNull { it.kind == MKind.WATER }?.let { s += it.key }
        metrics.firstOrNull { it.isSleep }?.let { s += it.key }
        s
    }
    val indexKeys: Set<String> get() = cfg.index ?: defaultIndex
    val indexMetrics: List<Metric> get() = metrics.filter { it.key in indexKeys }

    /** Галочка «каждый день»: не отмечено = 0. Остальные галочки — бонус: отмечено = 1, не отмечено — не считается. */
    fun isDaily(m: Metric) = m.kind == MKind.CHECK && m.key in cfg.daily && (cfg.weekly[m.key] ?: 0) == 0
    fun isBonus(m: Metric) = m.kind == MKind.CHECK && !isDaily(m)
    /** Вредное: отметка или вариант со знаком «минус». */
    fun isNegative(m: Metric) = (m.kind == MKind.CHECK || m.kind == MKind.OPTION) && m.key in cfg.negative

    /** Вклад метрики в индекс, 0..1; null — не учитывается в этот день. */
    fun score(m: Metric, v: Double?, d: LocalDate? = null): Double? {
        if (v == null) return null
        if (isNegative(m)) return when (m.kind) {
            // минус: отмечено — ноль; «каждый день» — день без отметки идёт в плюс
            MKind.CHECK -> if (v >= 1) 0.0 else if (isDaily(m)) 1.0 else null
            // вариант списка: не было — 1, к цели-«лимиту» (по умолчанию 1 раз) — до нуля
            else -> 1.0 - (v / goal(m)).coerceIn(0.0, 1.0)
        }
        return when (m.kind) {
            MKind.CHECK -> if (v >= 1) 1.0 else if (isDaily(m)) 0.0 else null
            MKind.SCALE -> if (m.item.max > m.item.min) ((v - m.item.min) / (m.item.max - m.item.min)).coerceIn(0.0, 1.0) else null
            else -> (v / goalOn(m, d)).coerceIn(0.0, 1.0)
        }
    }

    /**
     * Индекс дня по записанному: пропуски его не снижают.
     * [partial] — записано меньше половины значений индекса (галочки не в счёт): точка рисуется пустой
     * и не идёт в средние и серии.
     */
    data class DayIndex(val value: Double?, val partial: Boolean, val recorded: Int, val total: Int, val missing: List<Metric>) {
        val counted get() = value != null && !partial
    }

    /** Тренировки (Health Connect или пункт «Тренировка» с минутами). */
    fun isWorkout(m: Metric) = m.item.source == Source.HC_EXERCISE || m.item.id == "tr" && m.kind != MKind.CHECK

    fun dayIndex(d: LocalDate): DayIndex {
        val ms = indexMetrics
        if (ms.isEmpty() || d > today) return DayIndex(null, false, 0, 0, emptyList())
        // тренировка — не обязательная запись: нет тренировки в этот день = день отдыха, а не пропуск (5.22.1)
        val core = ms.filter { it.kind != MKind.CHECK && !(isWorkout(it) && value(it, d) == null) }
        val missing = core.filter { value(it, d) == null }
        val sc = ms.mapNotNull { score(it, value(it, d), d) }
        val rec = core.size - missing.size
        return DayIndex(
            sc.takeIf { it.isNotEmpty() }?.average()?.times(100),
            partial = core.isNotEmpty() && rec * 2 < core.size, recorded = rec, total = core.size, missing = missing,
        )
    }

    /** Индекс для расчётов: только полные дни. */
    fun indexOn(d: LocalDate): Double? = dayIndex(d).takeIf { it.counted }?.value

    val dayIndexes: List<DayIndex> by lazy { days.map { dayIndex(it) } }
    /** Все значения (и неполные дни) — для графика. */
    val index: List<Double?> by lazy { dayIndexes.map { it.value } }
    val partial: List<Boolean> by lazy { dayIndexes.map { it.partial && it.value != null } }
    private val counted: List<Double?> by lazy { dayIndexes.map { if (it.counted) it.value else null } }
    val prevIndex: List<Double?> by lazy { prevDays.map { indexOn(it) } }

    data class IndexStats(
        val avg: Double?, val prevAvg: Double?, val bestStreak: Int, val streakEnd: LocalDate?,
        val bestDow: DayOfWeek?, val bestDowAvg: Double?, val bestDay: LocalDate?, val above: Int, val counted: Int, val partial: Int,
    )

    val indexStats: IndexStats by lazy {
        val target = cfg.target.toDouble()
        var best = 0; var cur = 0; var end: LocalDate? = null
        counted.forEachIndexed { i, v ->
            if (v == null) return@forEachIndexed // пропуск и неполный день серию не рвут
            if (v >= target) { cur++; if (cur > best) { best = cur; end = days[i] } } else cur = 0
        }
        val byDow = days.indices.filter { counted[it] != null }.groupBy { days[it].dayOfWeek }
            .filterValues { it.size >= 2 }.mapValues { (_, l) -> l.map { counted[it]!! }.average() }
        val bd = byDow.maxByOrNull { it.value }
        val p = counted.filterNotNull()
        IndexStats(
            avg = p.takeIf { it.isNotEmpty() }?.average(),
            prevAvg = prevIndex.filterNotNull().takeIf { it.isNotEmpty() }?.average(),
            bestStreak = best, streakEnd = end,
            bestDow = bd?.key, bestDowAvg = bd?.value,
            bestDay = days.indices.filter { counted[it] != null }.maxByOrNull { counted[it]!! }?.let { days[it] },
            above = p.count { it >= target }, counted = p.size, partial = partial.count { it },
        )
    }

    /** Серии полных дней не ниже цели (от 2 дней); пропуски внутри серии её не рвут. */
    val streaks: List<IntRange> by lazy {
        val out = mutableListOf<IntRange>()
        val target = cfg.target.toDouble()
        var start = -1; var last = -1; var len = 0
        fun close() { if (len >= 2) out += start..last; start = -1; len = 0 }
        counted.forEachIndexed { i, v ->
            if (v == null) return@forEachIndexed
            if (v >= target) { if (start < 0) start = i; last = i; len++ } else close()
        }
        close()
        out
    }

    // ---------- мягкая мотивация ----------

    /** Недели (с понедельника) месяца: сколько выполнено цель «N раз в неделю» и из скольких. Текущая неделя — только если уже выполнена. */
    fun weeksMet(m: Metric, ds: List<LocalDate>): Pair<Int, Int> {
        val w = cfg.weekly[m.key] ?: return 0 to 0
        var met = 0; var of = 0
        ds.filter { it <= today }.map { it.with(DayOfWeek.MONDAY) }.distinct().forEach { wk ->
            val wd = (0..6).map { wk.plusDays(it.toLong()) }.filter { it <= today }
            if (wd.none { filled(it) }) return@forEach
            val ok = weekCount(m, wk) >= w
            if (ok) met++
            if (ok || wk.plusDays(6) < today) of++
        }
        return met to of
    }

    /** Сколько раз отмечено за неделю, начинающуюся с [monday] (до сегодня). */
    fun weekCount(m: Metric, monday: LocalDate): Int =
        (0..6).map { monday.plusDays(it.toLong()) }.filter { it <= today }.count { (value(m, it) ?: 0.0) >= 1 }

    /**
     * Серия дней с записью (5.66): день засчитан, если записано хоть что-то вручную; сегодняшний
     * незаполненный день серию не рвёт. Заморозки: за каждые 7 заполненных дней — одна, запас не больше 3.
     * Пропуск сам тратит заморозки, если их хватает на весь пропуск; если пропуск длиннее запаса —
     * серия начинается заново, а заморозки не сгорают.
     */
    data class Streak(
        val days: Int, val freezes: Int,
        /** Заполненных дней к следующей заморозке, 0..6. */
        val toNext: Int,
        /** Дни, закрытые заморозкой в текущей серии. */
        val frozen: List<LocalDate>,
        /** Серия прервалась вчера или раньше, и после этого ещё ничего не записано. */
        val broken: Boolean,
    )

    fun streak(d: LocalDate): Streak {
        val start = entries.keys.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .filter { it <= d && filled(it) }.minOrNull() ?: return Streak(0, 0, 0, emptyList(), false)
        var n = 0; var bank = 0; var total = 0; var broken = false
        val frozen = mutableListOf<LocalDate>()
        var day = start
        while (day <= d) {
            if (filled(day)) {
                n++; total++; broken = false
                if (total % FREEZE_EVERY == 0) bank = minOf(MAX_FREEZES, bank + 1)
                day = day.plusDays(1); continue
            }
            if (day == d) break // сегодня ещё можно заполнить
            // длина пропуска — до следующего заполненного дня или до сегодня
            var end = day
            while (end.plusDays(1) < d && !filled(end.plusDays(1))) end = end.plusDays(1)
            val gap = (java.time.temporal.ChronoUnit.DAYS.between(day, end) + 1).toInt()
            if (n > 0 && gap <= bank) {
                bank -= gap
                var x = day; while (x <= end) { frozen += x; x = x.plusDays(1) }
            } else {
                if (n > 0) broken = true
                n = 0; frozen.clear()
            }
            day = end.plusDays(1)
        }
        return Streak(n, bank, total % FREEZE_EVERY, frozen.toList(), broken && n == 0)
    }

    /** Совместимость: длина серии и была ли заморозка за последние 7 дней. */
    fun recordStreak(d: LocalDate): Pair<Int, Boolean> = streak(d).let { s -> s.days to s.frozen.any { it > d.minusDays(7) } }

    companion object {
        const val MAX_FREEZES = 3
        const val FREEZE_EVERY = 7
    }

    // ---------- 2. что влияет на итог дня ----------

    val scaleOutcome: Metric? = metrics.firstOrNull { it.kind == MKind.SCALE && it.key == "rate" } ?: metrics.firstOrNull { it.kind == MKind.SCALE }
    val outcomeIsIndex: Boolean get() = cfg.outcome == "index" || scaleOutcome == null

    fun outcome(d: LocalDate): Double? = if (outcomeIsIndex) indexOn(d) else scaleOutcome?.let { value(it, d) }

    data class Factor(val title: String, val effect: Double, val nWith: Int, val nWithout: Int) {
        val reliable get() = minOf(nWith, nWithout) >= 3 && nWith + nWithout >= 7
    }

    /**
     * Средний итог дня в дни с фактором минус без него.
     * Факторы: каждая метрика с её целью и те же отметки «накануне».
     */
    val factors: List<Factor> by lazy {
        val out = mutableListOf<Factor>()
        val outc = days.map { outcome(it) }
        if (outc.count { it != null } < 4) return@lazy emptyList()
        val skip = if (outcomeIsIndex) indexKeys else setOfNotNull(scaleOutcome?.key)
        fun add(title: String, has: (LocalDate) -> Boolean?) {
            val w = mutableListOf<Double>(); val wo = mutableListOf<Double>()
            days.forEachIndexed { i, d ->
                val o = outc[i] ?: return@forEachIndexed
                when (has(d)) { true -> w += o; false -> wo += o; null -> {} }
            }
            if (w.isNotEmpty() && wo.isNotEmpty()) out += Factor(title, w.average() - wo.average(), w.size, wo.size)
        }
        metrics.filter { it.key !in skip }.forEach { m ->
            val g = factorGoal(m)
            add(if (m.kind == MKind.OPTION) m.factorTitle(g) else m.factorTitle(goal(m))) { d ->
                value(m, d)?.let { if (m.kind == MKind.OPTION) it >= g else met(m, it, d) }
            }
        }
        // «накануне» — для отметок, списков и тренировок
        metrics.filter { it.key !in skip && (it.kind == MKind.CHECK || it.kind == MKind.OPTION || it.item.source == Source.HC_EXERCISE) }.forEach { m ->
            val g = factorGoal(m)
            add(t("%s накануне", if (m.kind == MKind.OPTION) m.factorTitle(g) else m.factorTitle(goal(m)))) { d ->
                value(m, d.minusDays(1))?.let { if (m.kind == MKind.OPTION) it >= g else met(m, it, d.minusDays(1)) }
            }
        }
        // сначала надёжные по силе эффекта, потом ненадёжные
        out.sortedWith(compareByDescending<Factor> { it.reliable }.thenByDescending { Math.abs(it.effect) })
    }

    /** Порог для списка: если обычно пьют несколько чашек — «≥ медианы», иначе «было». */
    private fun factorGoal(m: Metric): Double {
        if (m.kind != MKind.OPTION) return goal(m)
        cfg.goals[m.key]?.let { return it }
        val pos = series(m).filterNotNull().filter { it > 0 }.sorted()
        if (pos.size < 4) return 1.0
        val med = pos[pos.size / 2]
        return if (med >= 2) med else 1.0
    }

    // ---------- 4. сравнение с прошлым месяцем ----------

    data class GoalRow(val metric: Metric, val goalText: String, val days: Int, val of: Int, val prevDays: Int, val prevOf: Int, val weeks: Boolean = false) {
        val pct get() = if (of > 0) days * 100 / of else 0
    }

    val goalRows: List<GoalRow> by lazy {
        metrics.mapNotNull { m ->
            val cur = series(m); val prev = series(m, prevDays)
            val of = cur.count { it != null }; val pOf = prev.count { it != null }
            if (of == 0 && pOf == 0) return@mapNotNull null
            if (cur.none { (it ?: 0.0) > 0 } && prev.none { (it ?: 0.0) > 0 }) return@mapNotNull null
            val w = cfg.weekly[m.key] ?: 0
            if (m.kind == MKind.CHECK && w > 0) {
                val (a, b) = weeksMet(m, days); val (pa, pb) = weeksMet(m, prevDays)
                return@mapNotNull GoalRow(m, t("%d р/нед", w), a, b, pa, pb, weeks = true)
            }
            GoalRow(m, m.goalText(goal(m)), cur.indices.count { met(m, cur[it], days[it]) }, of,
                prev.indices.count { met(m, prev[it], prevDays[it]) }, pOf)
        }
    }
}
