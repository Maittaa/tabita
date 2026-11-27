package org.maittaa.tabita

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 5.62: эксперименты — гипотеза на срок («две недели ложусь до 23:00») и сравнение показателей
 * за срок эксперимента с таким же числом дней до него. Разница считается заметной, если 95 %
 * доверительный интервал разницы средних (Уэлч) не включает ноль. Это не доказательство причины:
 * на показатели влияют и нагрузка, и погода, и болезни — поэтому результат подписан честно.
 */
data class Experiment(
    val id: String, val title: String, val start: LocalDate, val days: Int,
    val metrics: List<String>, val stopped: LocalDate? = null,
) {
    val plannedEnd: LocalDate get() = start.plusDays(days - 1L)
    /** Последний день эксперимента: досрочная остановка или плановый конец. */
    val end: LocalDate get() = stopped?.takeIf { it < plannedEnd } ?: plannedEnd
    fun done(today: LocalDate = LocalDate.now()) = stopped != null || today > plannedEnd
    fun started(today: LocalDate = LocalDate.now()) = !today.isBefore(start)
    fun dayNo(today: LocalDate = LocalDate.now()) = (ChronoUnit.DAYS.between(start, today) + 1).toInt().coerceIn(0, days)

    fun toJson(): JSONObject = JSONObject().put("id", id).put("t", title).put("s", start.toString()).put("n", days)
        .put("m", JSONArray(metrics)).apply { stopped?.let { put("x", it.toString()) } }

    companion object {
        const val INDEX = "index"
        fun fromJson(o: JSONObject) = Experiment(
            o.getString("id"), o.getString("t"), LocalDate.parse(o.getString("s")), o.getInt("n"),
            o.getJSONArray("m").let { a -> (0 until a.length()).map { a.getString(it) } },
            o.optString("x").takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) },
        )
        fun new(title: String, start: LocalDate, days: Int, metrics: List<String>) =
            Experiment(UUID.randomUUID().toString().take(8), title.trim(), start, days, metrics)
    }
}

object Experiments {
    fun parse(s: String?): List<Experiment> = runCatching {
        val a = JSONArray(s ?: return emptyList())
        (0 until a.length()).mapNotNull { runCatching { Experiment.fromJson(a.getJSONObject(it)) }.getOrNull() }
    }.getOrDefault(emptyList())

    fun toJson(l: List<Experiment>) = JSONArray(l.map { it.toJson() }).toString()
    fun all(prefs: Prefs) = parse(prefs.experiments)
    fun save(prefs: Prefs, l: List<Experiment>) { prefs.experiments = toJson(l) }
    fun upsert(prefs: Prefs, e: Experiment) {
        val l = all(prefs); save(prefs, if (l.any { it.id == e.id }) l.map { if (it.id == e.id) e else it } else l + e)
        Log.i("Exp", "эксперимент «${e.title}»: ${e.start} +${e.days} дн., показатели ${e.metrics}, стоп ${e.stopped}")
    }
    fun remove(prefs: Prefs, id: String) = save(prefs, all(prefs).filterNot { it.id == id })

    /** Что можно сравнивать: индекс дня и числа — сон, оценка, шаги, вода, пульс покоя, вес и т. п. */
    data class Option(val key: String, val title: String, val format: (Double) -> String, val value: (LocalDate) -> Double?, val lowerBetter: Boolean = false)

    fun options(repo: Repo, prefs: Prefs, today: LocalDate = LocalDate.now()): List<Option> {
        val tmpl = repo.template.value; val ent = repo.entries.value
        val md = MonthData(tmpl, ent, today, prefs.monthCfg, today)
        val out = mutableListOf<Option>()
        if (md.indexMetrics.isNotEmpty()) out += Option(Experiment.INDEX, t("Индекс дня"), { formatNumber(Math.round(it).toDouble()) }, { md.indexOn(it) })
        md.metrics.filter { it.kind == MKind.SCALE || it.kind == MKind.AMOUNT || it.kind == MKind.WATER }.forEach { m ->
            out += Option(m.key, m.title, m::format, { md.value(m, it) })
        }
        // пульс покоя и вес в «Итогах» не участвуют, а для экспериментов полезны
        tmpl.filter { !it.hidden && (it.source == Source.HC_RESTING_HR || it.source == Source.HC_WEIGHT) && it.isNumeric }.forEach { item ->
            out += Option(item.id, item.displayName, { formatNumber(Math.round(it * 10) / 10.0) + if (item.unit.isNotEmpty()) " " + item.displayUnit else "" },
                { d -> parseNumber(ent[d.key()]?.get(item.id)) }, lowerBetter = item.source == Source.HC_RESTING_HR)
        }
        return out.distinctBy { it.key }
    }

    /** Значение t-распределения для 95 % (двусторонний) по числу степеней свободы. */
    fun t95(df: Double): Double {
        val table = listOf(12.71, 4.30, 3.18, 2.78, 2.57, 2.45, 2.36, 2.31, 2.26, 2.23, 2.20, 2.18, 2.16, 2.14, 2.13, 2.12, 2.11, 2.10, 2.09, 2.09,
            2.08, 2.07, 2.07, 2.06, 2.06, 2.06, 2.05, 2.05, 2.05, 2.04)
        val i = df.toInt().coerceAtLeast(1)
        return if (i <= table.size) table[i - 1] else 2.0
    }

    /** Результат по показателю: средние до и во время, разница и 95 % ДИ (Уэлч). */
    data class Row(val key: String, val title: String, val before: Double?, val during: Double?, val nBefore: Int, val nDuring: Int,
                   val diff: Double?, val lo: Double?, val hi: Double?, val lowerBetter: Boolean, val format: (Double) -> String) {
        /** 1 — заметно лучше, −1 — заметно хуже, 0 — в пределах колебаний, null — мало данных. */
        val verdict: Int? get() {
            if (diff == null || lo == null || hi == null) return null
            if (lo <= 0 && hi >= 0) return 0
            val up = lo > 0
            return if (up != lowerBetter) 1 else -1
        }
    }

    const val MIN_DAYS = 4

    fun compare(before: List<Double>, during: List<Double>): Triple<Double, Double, Double>? {
        if (before.size < MIN_DAYS || during.size < MIN_DAYS) return null
        val mb = before.average(); val md = during.average()
        fun v(l: List<Double>, m: Double) = l.sumOf { (it - m) * (it - m) } / (l.size - 1)
        val sb = v(before, mb) / before.size; val sd = v(during, md) / during.size
        val se = sqrt(sb + sd)
        val diff = md - mb
        if (se == 0.0) return Triple(diff, diff, diff)
        // степени свободы Уэлча — Саттертуэйта
        val df = (sb + sd) * (sb + sd) / ((sb * sb) / (before.size - 1) + (sd * sd) / (during.size - 1))
        val h = t95(df) * se
        return Triple(diff, diff - h, diff + h)
    }

    fun result(e: Experiment, opts: List<Option>, today: LocalDate = LocalDate.now()): List<Row> {
        val last = minOf(e.end, today.minusDays(if (e.done(today)) 0 else 1)) // сегодня ещё не закончился
        val during = generateSequence(e.start) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.toList()
        val before = (1..e.days).map { e.start.minusDays(it.toLong()) }
        return e.metrics.mapNotNull { k ->
            val o = opts.firstOrNull { it.key == k } ?: return@mapNotNull null
            val b = before.mapNotNull(o.value); val d = during.mapNotNull(o.value)
            val c = compare(b, d)
            Row(k, o.title, b.takeIf { it.isNotEmpty() }?.average(), d.takeIf { it.isNotEmpty() }?.average(), b.size, d.size,
                c?.first, c?.second, c?.third, o.lowerBetter, o.format)
        }
    }

    fun fmtDiff(r: Row): String {
        val d = r.diff ?: return "—"
        val s = r.format(abs(d))
        return (if (d > 0) "+" else if (d < 0) "−" else "") + s
    }
}
