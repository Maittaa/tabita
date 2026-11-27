package org.maittaa.tabita

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 5.42: динамическая норма воды.
 *
 * Норма дня = база (30 мл на кг веса) + тренировки (длительность × тяжесть вида × скорость потери пота)
 * + шаги сверх обычного дня (1 мл на 1 ккал расхода — правило NRC 1989).
 * Скорость потери пота по умолчанию 0,5 л/ч (прежнее правило; ACSM: обычно 0,5–2 л/ч),
 * личная — по взвешиваниям в openScale прямо до и после тренировки (метод ACSM).
 * Посчитанная норма хранится в служебном ключе `<id>!n` дня — её видят «Итоги», графики, советы.
 */
object Hydration {
    const val BASE_ML_PER_KG = 30.0
    const val DEFAULT_SWEAT = 0.5
    const val STEPS_USUAL = 8000.0
    /** Окно взвешивания до начала и после конца тренировки. */
    const val WINDOW_MIN = 60L
    const val MIN_SESSION = 20
    val RATE_RANGE = 0.2..3.0

    fun normKey(id: String) = "$id!n"

    /** Норма дня: посчитанная (если есть) или заданная в пункте. */
    fun normOf(item: TemplateItem, day: Map<String, String>?): Double =
        parseNumber(day?.get(normKey(item.id)))?.takeIf { it > 0 } ?: item.max.takeIf { it > 0 } ?: 2.0

    data class WorkoutPart(val session: WorkoutSession, val liters: Double)

    data class Calc(
        val weight: Double, val base: Double,
        val workouts: List<WorkoutPart>, val stepsOver: Double, val stepsLiters: Double,
        val sweat: Double, val personal: Boolean, val total: Double,
    ) {
        val workoutLiters get() = workouts.sumOf { it.liters }
    }

    fun calc(repo: Repo, prefs: Prefs, date: String, day: Map<String, String>): Calc? {
        val tmpl = repo.template.value
        val w = Anthro.weightOf(tmpl, day) ?: repo.latestWeight(date) ?: return null
        val (sweat, personal) = sweatRate(prefs)
        val sessions = tmpl.filter { it.source == Source.HC_EXERCISE }.flatMap { Workouts.sessions(day, it.id) }
        val parts = sessions.mapNotNull { s ->
            val l = s.min / 60.0 * sweat * Workouts.load(s.kind)
            if (l < 0.01) null else WorkoutPart(s, l)
        }
        val steps = Anthro.stepsOf(tmpl, day) ?: 0.0
        val over = (steps - STEPS_USUAL).coerceAtLeast(0.0)
        // ходьба: ~0,5 ккал на кг на км (как в расходе энергии), 1 мл на ккал
        val stepsL = Anthro.stepsKm(repo.profile.value, over)?.let { 0.5 * w * it / 1000.0 } ?: 0.0
        val base = w * BASE_ML_PER_KG / 1000.0
        val total = Math.round((base + parts.sumOf { it.liters } + stepsL) * 10) / 10.0
        return Calc(w, base, parts, over, stepsL, sweat, personal, total)
    }

    /** Записать посчитанную норму в дни (или убрать, если норма задаётся вручную). Только если изменилась. */
    fun updateNorms(repo: Repo, prefs: Prefs, dates: Collection<String>) {
        val item = repo.template.value.firstOrNull { it.type == ItemType.WATER } ?: return
        val today = LocalDate.now().key()
        dates.filter { it <= today }.forEach { d ->
            // пустые дни не создаём: норма нужна только там, где что-то записано
            val day = repo.entries.value[d] ?: return@forEach
            val old = day[normKey(item.id)]
            val new = if (prefs.waterAuto) calc(repo, prefs, d, day)?.total?.let { formatPlain(it) } else if (d == today) null else old
            if (new != old) repo.setValue(d, normKey(item.id), new)
        }
    }

    private fun formatPlain(v: Double) = (Math.round(v * 10) / 10.0).toString()

    // ---------- личная потеря пота ----------

    data class Sample(val date: String, val start: Long, val kind: String, val min: Int, val lossKg: Double, val rate: Double) {
        fun toJson(): JSONObject = JSONObject().put("d", date).put("s", start).put("k", kind).put("m", min).put("l", lossKg).put("r", rate)
        companion object {
            fun fromJson(o: JSONObject) = Sample(o.getString("d"), o.getLong("s"), o.optString("k"), o.optInt("m"), o.getDouble("l"), o.getDouble("r"))
        }
    }

    fun samples(prefs: Prefs): List<Sample> = runCatching {
        val a = JSONArray(prefs.sweatLog.ifBlank { return emptyList() })
        (0 until a.length()).map { Sample.fromJson(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    /** Медиана последних 5 замеров; без замеров — 0,5 л/ч. */
    fun sweatRate(prefs: Prefs): Pair<Double, Boolean> {
        val r = samples(prefs).sortedBy { it.start }.takeLast(5).map { it.rate }.sorted()
        if (r.isEmpty()) return DEFAULT_SWEAT to false
        val med = if (r.size % 2 == 1) r[r.size / 2] else (r[r.size / 2 - 1] + r[r.size / 2]) / 2
        return Math.round(med * 100) / 100.0 to true
    }

    /**
     * Найти пары «взвешивание до (≤ 60 мин до начала) — после (≤ 60 мин после конца)» вокруг тренировок.
     * Потеря на час, приведённая к тяжести 1 (силовая/бег). Пьёте во время тренировки — замер занизится.
     */
    fun findSamples(weighIns: List<Pair<Long, Double>>, sessions: List<WorkoutSession>, zone: ZoneId = ZoneId.systemDefault()): List<Sample> {
        val ws = weighIns.sortedBy { it.first }
        return sessions.mapNotNull { s ->
            val start = s.start ?: return@mapNotNull null
            val load = Workouts.load(s.kind)
            if (s.min < MIN_SESSION || load <= 0) return@mapNotNull null
            val end = start + s.min * 60_000L
            val pre = ws.lastOrNull { it.first in (start - WINDOW_MIN * 60_000)..start } ?: return@mapNotNull null
            val post = ws.firstOrNull { it.first in end..(end + WINDOW_MIN * 60_000) } ?: return@mapNotNull null
            val loss = pre.second - post.second
            if (loss <= 0) return@mapNotNull null
            val rate = loss / (s.min / 60.0) / load
            if (rate !in RATE_RANGE) return@mapNotNull null
            Sample(Instant.ofEpochMilli(start).atZone(zone).toLocalDate().key(), start, s.kind, s.min,
                Math.round(loss * 100) / 100.0, Math.round(rate * 100) / 100.0)
        }
    }

    /** Добавить новые замеры (без дублей по началу тренировки), хранить последние 20. */
    fun addSamples(prefs: Prefs, new: List<Sample>): Int {
        if (new.isEmpty()) return 0
        val old = samples(prefs)
        val known = old.map { it.start }.toSet()
        val add = new.filter { it.start !in known }
        if (add.isEmpty()) return 0
        val all = (old + add).sortedBy { it.start }.takeLast(20)
        prefs.sweatLog = JSONArray(all.map { it.toJson() }).toString()
        return add.size
    }

    fun clearSamples(prefs: Prefs) { prefs.sweatLog = "" }
}
