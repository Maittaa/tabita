package org.maittaa.tabita

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.floor

/**
 * 5.51: пульсовые зоны как в Google Health / Fitbit на Pixel Watch.
 * С пульсом покоя — по резерву пульса (максимум − покой): лёгкая < 40 %, умеренная 40–59 %, высокая 60–84 %, пиковая ≥ 85 %.
 * Без пульса покоя — от максимума, как у остальных устройств Fitbit: < 50 %, 50–69 %, 70–84 %, ≥ 85 %.
 * Максимум — 220 − возраст или свой (настройка). Источник: справка Fitbit «How do I use heart rate zones».
 */
object HrZones {
    val names get() = listOf(t("Лёгкая"), t("Умеренная"), t("Высокая"), t("Пиковая"))

    data class Zones(val max: Int, val rest: Int?, val restDate: LocalDate?, val maxAuto: Boolean) {
        val byReserve get() = rest != null
        private fun at(pReserve: Double, pMax: Double): Int =
            if (rest != null) floor(rest + pReserve * (max - rest)).toInt() else floor(max * pMax).toInt()
        /** Нижние границы умеренной, высокой и пиковой зон. */
        val moderate get() = at(0.40, 0.50)
        val vigorous get() = at(0.60, 0.70)
        val peak get() = at(0.85, 0.85)
        fun zone(bpm: Int): Int = when { bpm >= peak -> 3; bpm >= vigorous -> 2; bpm >= moderate -> 1; else -> 0 }
        /** Подписи диапазонов: «< 112», «112–137», «138–169», «170+». */
        fun ranges(): List<String> = listOf("< $moderate", "$moderate–${vigorous - 1}", "$vigorous–${peak - 1}", "$peak+")
    }

    /** Последний пульс покоя за [days] дней до [on] (с часов или записанный вручную). */
    fun latestRest(tmpl: List<TemplateItem>, entries: Map<String, Map<String, String>>, on: LocalDate, days: Int = 7): Pair<LocalDate, Int>? {
        val items = tmpl.filter { it.source == Source.HC_RESTING_HR }
        if (items.isEmpty()) return null
        for (i in 0..days) {
            val d = on.minusDays(i.toLong())
            val day = entries[d.key()] ?: continue
            items.firstNotNullOfOrNull { parseNumber(day[it.id]) }?.takeIf { it in 30.0..120.0 }?.let { return d to Math.round(it).toInt() }
        }
        return null
    }

    /** Зоны на дату; null — неизвестен ни возраст, ни свой максимум. */
    fun of(profile: Profile, customMax: Int, rest: Pair<LocalDate, Int>?, on: LocalDate = LocalDate.now()): Zones? {
        val max = customMax.takeIf { it in 120..230 } ?: profile.age(on)?.let { 220 - it } ?: return null
        val r = rest?.second?.takeIf { it < max - 30 }
        return Zones(max, r, if (r != null) rest.first else null, customMax !in 120..230)
    }

    fun of(repo: Repo, prefs: Prefs, on: LocalDate = LocalDate.now()): Zones? =
        of(repo.profile.value, prefs.hrMax, latestRest(repo.template.value, repo.entries.value, on), on)

    /** Минуты в каждой зоне (0..3) по времени на значениях пульса. */
    fun minutes(hist: Map<Int, Int>, z: Zones): IntArray {
        val sec = IntArray(4)
        hist.forEach { (bpm, s) -> sec[z.zone(bpm)] += s }
        return IntArray(4) { Math.round(sec[it] / 60.0).toInt() }
    }

    /**
     * Время на каждом значении пульса по замерам [samples] (мс → уд/мин) внутри [start]..[end]:
     * каждый замер «действует» до следующего, но не дольше 2 минут (провалы в записи не растягиваем).
     */
    fun histOf(samples: List<Pair<Long, Int>>, start: Long, end: Long): Map<Int, Int> {
        val s = samples.filter { it.first in start until end && it.second in 30..240 }.sortedBy { it.first }
        val out = mutableMapOf<Int, Int>()
        s.forEachIndexed { i, (t0, bpm) ->
            val t1 = if (i + 1 < s.size) s[i + 1].first else minOf(end, t0 + 5_000)
            val sec = ((minOf(t1, end, t0 + 120_000) - t0) / 1000).toInt()
            if (sec > 0) out[bpm] = (out[bpm] ?: 0) + sec
        }
        return out
    }

    fun avg(hist: Map<Int, Int>): Int? {
        val total = hist.values.sum()
        return if (total <= 0) null else Math.round(hist.entries.sumOf { it.key.toLong() * it.value } / total.toDouble()).toInt()
    }
}

/** 5.51: аэробная нагрузка — виды, активные минуты, неделя. */
object Cardio {
    /** Виды, которые считаются аэробной нагрузкой (на выносливость). */
    val kinds = listOf("aerobics", "walk", "run", "bike", "swim", "hiit", "cardio", "dance", "hike", "winter", "sport")
    fun isCardio(s: WorkoutSession) = s.kind in kinds

    /** Норма ВОЗ (2020): 150–300 мин умеренной нагрузки в неделю, интенсивная минута — за две. */
    const val WEEK_MIN = 150
    const val WEEK_MAX = 300

    val effortNames get() = listOf(t("Легко — могу петь"), t("Умеренно — могу говорить, но не петь"), t("Интенсивно — только пара слов"))

    /**
     * Активные минуты сессии: по пульсу — умеренная зона ×1, высокая и пиковая ×2 (как «минуты активности»
     * у Fitbit и счёт ВОЗ); без пульса — по тесту разговором: умеренно — все минуты, интенсивно — ×2, легко — 0.
     * null — ни пульса, ни ощущения.
     */
    fun active(s: WorkoutSession, z: HrZones.Zones?): Int? {
        if (s.hist.isNotEmpty() && z != null) {
            val m = HrZones.minutes(s.hist, z)
            return m[1] + 2 * (m[2] + m[3])
        }
        // средний пульс, записанный вручную: зона среднего пульса на всё занятие
        if (s.hr != null && z != null) return when (z.zone(s.hr)) { 0 -> 0; 1 -> s.min; else -> 2 * s.min }
        return when (s.effort) { 1 -> 0; 2 -> s.min; 3 -> 2 * s.min; else -> null }
    }

    data class Item(val date: LocalDate, val s: WorkoutSession, val manualIndex: Int?, val itemId: String)

    /** Аэробные сессии за дни [from]..[to], новые сверху. manualIndex — индекс в ручном списке (чтобы удалить). */
    fun sessions(repo: Repo, from: LocalDate, to: LocalDate, filter: (WorkoutSession) -> Boolean = ::isCardio): List<Item> {
        val items = repo.template.value.filter { it.source == Source.HC_EXERCISE }
        val out = mutableListOf<Item>()
        var d = to
        while (!d.isBefore(from)) {
            val day = repo.entries.value[d.key()]
            if (day != null) items.forEach { item ->
                val synced = Workouts.parse(day[Workouts.syncedKey(item.id)])
                val manual = Workouts.parse(day[Workouts.manualKey(item.id)])
                val (merged, absorbed) = Workouts.merge(synced, manual)
                val manualLeft = manual.indices.filter { it !in absorbed }
                merged.forEachIndexed { i, s ->
                    if (!filter(s)) return@forEachIndexed
                    val mi = if (i < synced.size) null else manualLeft[i - synced.size]
                    out += Item(d, s, mi, item.id)
                }
            }
            d = d.minusDays(1)
        }
        return out.sortedWith(compareByDescending<Item> { it.date }.thenByDescending { it.s.start ?: 0L })
    }

    data class Week(val start: LocalDate, val count: Int, val minutes: Int, val active: Int, val activeKnown: Boolean)

    fun weekStart(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun week(list: List<Item>, start: LocalDate, z: HrZones.Zones?): Week {
        val w = list.filter { !it.date.isBefore(start) && it.date.isBefore(start.plusDays(7)) }
        val act = w.mapNotNull { active(it.s, z) }
        return Week(start, w.size, w.sumOf { it.s.min }, act.sum(), act.isNotEmpty())
    }

    /** Темп, мин/км (бег, ходьба, поход), если есть дистанция. */
    fun pace(s: WorkoutSession): Double? = s.dist?.takeIf { it >= 300 && s.kind in setOf("run", "walk", "hike") }?.let { s.min / (it / 1000.0) }

    fun paceText(p: Double): String { val m = p.toInt(); val sec = Math.round((p - m) * 60).toInt().coerceAtMost(59); return "%d:%02d".format(m, sec) + " " + t("/км") }

    fun distText(m: Int): String = if (m >= 1000) formatNumber(Math.round(m / 100.0) / 10.0) + " " + t("км") else "$m " + t("м")

    fun clock(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime().let { "%02d:%02d".format(it.hour, it.minute) }
}

/**
 * 5.51: таймер аэробики дома. Разминка 5 мин → основная часть (движения по минуте, после каждых пяти — 30 с спокойного шага)
 * или интервалы (работа/отдых × раундов) → заминка 5 мин. «Тихо для соседей» заменяет прыжки безударными вариантами.
 */
object CardioPlans {
    enum class Mode { HOME, INTERVALS, VIDEO, YOGA }
    /** phase: 0 — разминка, 1 — работа, 2 — отдых/восстановление, 3 — заминка, 4 — поза (йога). */
    data class Block(val name: String, val sec: Int, val phase: Int, val hint: String = "")

    private data class Move(val loud: String, val quiet: String, val hint: String)

    private val moves get() = listOf(
        Move(t("Шаг в сторону с хлопком"), t("Шаг в сторону с хлопком"), t("Мягко в коленях, руки работают")),
        Move(t("Бег на месте с высоким коленом"), t("Марш с высоким коленом"), t("Корпус ровно, колени до пояса")),
        Move(t("Захлёст голени"), t("Захлёст голени шагом"), t("Пятка к ягодице, руки в ритм")),
        Move(t("Прыжки «звёздочка»"), t("«Звёздочка» шагом: шаг в сторону, руки вверх"), t("Дышите ровно, не задерживайте дыхание")),
        Move(t("Бой с тенью"), t("Бой с тенью"), t("Удары от плеча, корпус поворачивается")),
        Move(t("Выпады назад попеременно"), t("Выпады назад попеременно"), t("Колено над стопой, спина прямая")),
        Move(t("Конькобежец с прыжком"), t("Конькобежец шагом"), t("Широкий шаг в сторону, рука тянется к стопе")),
        Move(t("Приседания с подъёмом рук"), t("Приседания с подъёмом рук"), t("Таз назад, пятки на полу")),
        Move(t("Колено к локтю"), t("Колено к локтю"), t("Поочерёдно, корпус скручивается")),
        Move(t("Бег на месте"), t("Быстрый марш на месте"), t("Темп, при котором можно говорить, но не петь")),
    )

    fun warmup() = listOf(
        Block(t("Марш на месте"), 60, 0), Block(t("Круги плечами"), 30, 0), Block(t("Наклоны в стороны"), 30, 0),
        Block(t("Шаг в сторону с махами рук"), 60, 0), Block(t("Полуприседания"), 60, 0), Block(t("Подъёмы коленей, медленно"), 60, 0),
    )

    fun cooldown() = listOf(
        Block(t("Спокойный марш"), 60, 3, t("Пульс и дыхание успокаиваются")), Block(t("Растяжка икр у стены"), 60, 3, t("По 30 с на каждую ногу")),
        Block(t("Растяжка передней поверхности бедра"), 60, 3, t("По 30 с на каждую ногу, держитесь за опору")),
        Block(t("Растяжка груди: руки в замок за спиной"), 60, 3), Block(t("Глубокое дыхание"), 60, 3, t("Вдох на 4 счёта, выдох на 6")),
    )

    private fun move(i: Int, quiet: Boolean) = moves[i % moves.size].let { (if (quiet) it.quiet else it.loud) to it.hint }

    fun home(mainMin: Int, quiet: Boolean): List<Block> {
        val main = mutableListOf<Block>()
        val total = mainMin * 60
        var t0 = 0; var i = 0
        while (t0 < total) {
            if (i > 0 && i % 5 == 0 && main.lastOrNull()?.phase == 1 && total - t0 > 60) {
                main += Block(t("Спокойный шаг"), 30, 2, t("Восстановите дыхание")); t0 += 30
            }
            val (n, h) = move(i, quiet)
            val sec = minOf(60, total - t0)
            main += Block(n, sec, 1, h); t0 += sec; i++
        }
        return warmup() + main + cooldown()
    }

    fun intervals(work: Int, rest: Int, rounds: Int, quiet: Boolean): List<Block> {
        val main = (0 until rounds).flatMap { i ->
            val (n, h) = move(i, quiet)
            listOfNotNull(Block(n, work, 1, h), if (i < rounds - 1 && rest > 0) Block(t("Отдых — шагом"), rest, 2) else null)
        }
        return warmup() + main + cooldown()
    }

    fun totalSec(b: List<Block>) = b.sumOf { it.sec }
}

/** 5.51: идущее занятие с таймером — переживает уход с экрана и перезапуск. */
data class CardioDraft(
    val mode: CardioPlans.Mode, val startedAt: Long, val pausedMs: Long = 0, val pausedSince: Long = 0,
    val quiet: Boolean = true, val mainMin: Int = 15, val work: Int = 30, val rest: Int = 30, val rounds: Int = 10,
    /** Пропущенное кнопкой «Дальше» время: двигает план, но не идёт в длительность занятия. */
    val skipMs: Long = 0,
    /** 5.53: комплекс йоги (Mode.YOGA). */
    val complex: String = "",
) {
    val paused get() = pausedSince > 0
    fun elapsedMs(now: Long = System.currentTimeMillis()) = now - startedAt - pausedMs - (if (pausedSince > 0) now - pausedSince else 0)
    /** Где мы в плане, секунд (с учётом пропущенного). */
    fun planSec(now: Long = System.currentTimeMillis()) = (elapsedMs(now) + skipMs) / 1000
    val blocks: List<CardioPlans.Block>
        get() = when (mode) {
            CardioPlans.Mode.HOME -> CardioPlans.home(mainMin, quiet)
            CardioPlans.Mode.INTERVALS -> CardioPlans.intervals(work, rest, rounds, quiet)
            CardioPlans.Mode.VIDEO -> emptyList()
            CardioPlans.Mode.YOGA -> Yoga.complex(complex)?.let { Yoga.blocks(it) } ?: emptyList()
        }
    val kind get() = when (mode) { CardioPlans.Mode.INTERVALS -> "hiit"; CardioPlans.Mode.YOGA -> "yoga"; else -> "aerobics" }
    val yoga get() = mode == CardioPlans.Mode.YOGA
    val title get() = when (mode) {
        CardioPlans.Mode.HOME -> t("Аэробика дома")
        CardioPlans.Mode.INTERVALS -> t("Интервалы")
        CardioPlans.Mode.VIDEO -> t("Аэробика по видео")
        CardioPlans.Mode.YOGA -> Yoga.complex(complex)?.name ?: t("Йога")
    }

    /** Текущий блок, секунд от его начала, индекс; null — план пройден (или режим «по видео»). */
    fun current(planSec: Long): Triple<Int, CardioPlans.Block, Long>? {
        var acc = 0L
        blocks.forEachIndexed { i, b ->
            if (planSec < acc + b.sec) return Triple(i, b, planSec - acc)
            acc += b.sec
        }
        return null
    }

    fun pause(now: Long = System.currentTimeMillis()) = if (paused) this else copy(pausedSince = now)
    fun resume(now: Long = System.currentTimeMillis()) = if (!paused) this else copy(pausedMs = pausedMs + now - pausedSince, pausedSince = 0)
    /** Перейти к следующему блоку: сдвигаем начало так, будто текущий блок уже закончился. */
    fun skip(now: Long = System.currentTimeMillis()): CardioDraft {
        val (_, b, into) = current(planSec(now)) ?: return this
        return copy(skipMs = skipMs + (b.sec - into) * 1000)
    }

    fun toJson(): String = org.json.JSONObject().put("mode", mode.name).put("at", startedAt).put("pm", pausedMs).put("ps", pausedSince)
        .put("q", quiet).put("main", mainMin).put("w", work).put("r", rest).put("n", rounds).put("sk", skipMs).put("cx", complex).toString()

    companion object {
        fun parse(s: String?): CardioDraft? = runCatching {
            val o = org.json.JSONObject(s ?: return null)
            CardioDraft(CardioPlans.Mode.valueOf(o.getString("mode")), o.getLong("at"), o.optLong("pm"), o.optLong("ps"),
                o.optBoolean("q", true), o.optInt("main", 15), o.optInt("w", 30), o.optInt("r", 30), o.optInt("n", 10), o.optLong("sk"), o.optString("cx"))
        }.getOrNull()
    }
}

/**
 * 5.54: активные минуты за день по пульсу с часов (как «минуты активности» в Google Health): умеренная зона ×1,
 * высокая и пиковая ×2. Хранятся в служебном ключе дня [KEY] как «умеренная,высокая,пиковая» (минуты).
 */
object ActiveMin {
    const val KEY = "hc!az"

    data class Day(val mod: Int, val vig: Int, val peak: Int) {
        val active get() = mod + 2 * (vig + peak)
        override fun toString() = "$mod,$vig,$peak"
    }

    fun parse(s: String?): Day? = s?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.takeIf { it.size == 3 }?.let { Day(it[0], it[1], it[2]) }
    fun of(entries: Map<String, Map<String, String>>, d: LocalDate): Day? = parse(entries[d.key()]?.get(KEY))
    fun from(hist: Map<Int, Int>, z: HrZones.Zones): Day = HrZones.minutes(hist, z).let { Day(it[1], it[2], it[3]) }

    /** Неделя с понедельника: по дням (null — нет данных пульса). */
    fun week(entries: Map<String, Map<String, String>>, start: LocalDate): List<Pair<LocalDate, Day?>> =
        (0..6).map { start.plusDays(it.toLong()).let { d -> d to of(entries, d) } }

    fun weekTotal(entries: Map<String, Map<String, String>>, start: LocalDate): Int? =
        week(entries, start).mapNotNull { it.second?.active }.takeIf { it.isNotEmpty() }?.sum()
}
