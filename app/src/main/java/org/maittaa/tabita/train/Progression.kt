package org.maittaa.tabita.train

import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * 5.55: прогрессия силовой.
 * - «Запас» после подхода (сколько повторов ещё мог бы): 0, 1–2, 3+ — шаг прибавки повторов 0 / +1 / +2.
 * - Рабочие подходы на группу мышц за неделю (ориентир для роста у тренированных — 12–20, Baz-Valle 2022).
 * - Прибавка подхода в упражнении, когда вес прибавить нечем: две тренировки подряд все подходы на верхней
 *   границе, обычная интенсивность, не больше +2 к плану, 5 подходов на упражнение и 20 на группу в неделю.
 *   Откат: первый подход упал на 2+ повтора к прошлому разу.
 */
object Reserve {
    const val NONE = -1
    /** Значения: 0 — «0», 1 — «1–2», 3 — «3+». */
    val options = listOf(0 to "0", 1 to "1–2", 3 to "3+")
    fun label(r: Int) = options.firstOrNull { it.first == r }?.second
}

object MuscleVolume {
    val order = listOf("ноги", "ягодицы", "грудь", "спина", "плечи", "руки", "пресс")
    const val LOW = 12
    const val HIGH = 20

    fun group(exId: String): String? = Library.get(exId)?.pattern?.let { MuscleFocus.groupOf(it) }

    fun monday(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Рабочие подходы по группам за неделю с [start] (сделанные, без разминки). */
    fun week(history: List<Session>, start: LocalDate): Map<String, Int> {
        val end = start.plusDays(7)
        val out = mutableMapOf<String, Int>()
        history.forEach { s ->
            val d = runCatching { LocalDate.parse(s.date) }.getOrNull() ?: return@forEach
            if (d.isBefore(start) || !d.isBefore(end)) return@forEach
            s.exercises.forEach { e -> group(e.exId)?.let { g -> out[g] = (out[g] ?: 0) + e.values.size } }
        }
        return out
    }

    /** Среднее за [weeks] прошлых полных недель (без текущей). */
    fun average(history: List<Session>, today: LocalDate, weeks: Int = 4): Map<String, Double> {
        val mon = monday(today)
        val per = (1..weeks).map { week(history, mon.minusWeeks(it.toLong())) }
        return order.associateWith { g -> per.sumOf { it[g] ?: 0 } / weeks.toDouble() }.filterValues { it > 0 }
    }
}

object SetProgression {
    const val MAX_EXTRA = 2
    const val MAX_SETS = 5

    fun parse(s: String?): Map<String, Int> = runCatching {
        val o = JSONObject(s ?: return emptyMap())
        o.keys().asSequence().associateWith { o.getInt(it) }.filterValues { it != 0 }
    }.getOrDefault(emptyMap())

    fun toJson(m: Map<String, Int>) = JSONObject().apply { m.filterValues { it != 0 }.forEach { (k, v) -> put(k, v) } }.toString()

    data class Change(val exId: String, val name: String, val delta: Int, val why: String)

    /**
     * После тренировки [s]: что поменять в числе подходов. [history] — уже с [s] первой.
     * [heavierAvailable] — можно ли прибавить вес в упражнении (тогда прогрессия — весом, а не подходами).
     * [planned] — подходов по плану (без прибавки).
     */
    fun evaluate(
        s: Session, history: List<Session>, extra: Map<String, Int>, planned: (String) -> Int?,
        heavierAvailable: (ExResult) -> Boolean, today: LocalDate = LocalDate.now(),
    ): List<Change> {
        if (s.intensity.isNotBlank() && s.intensity != Intensity.NORMAL.title) return emptyList()
        val weekSets = MuscleVolume.week(history, MuscleVolume.monday(today))
        val out = mutableListOf<Change>()
        s.exercises.forEach { r ->
            if (r.exId.isBlank() || r.values.isEmpty()) return@forEach
            val prev = history.drop(1).asSequence().flatMap { it.exercises }.firstOrNull { it.exId == r.exId && it.values.isNotEmpty() }
            val cur = extra[r.exId] ?: 0
            val base = planned(r.exId) ?: return@forEach
            val first = leadingInt(r.values.first()) ?: return@forEach
            val prevFirst = prev?.values?.firstOrNull()?.let { leadingInt(it) }
            when {
                cur > 0 && prevFirst != null && first <= prevFirst - 2 ->
                    out += Change(r.exId, r.name, -1, "первый подход упал с $prevFirst до $first — подход снят, восстановись")
                r.allTop && prev?.allTop == true && !heavierAvailable(r) && cur < MAX_EXTRA && base + cur < MAX_SETS &&
                    (MuscleVolume.group(r.exId)?.let { (weekSets[it] ?: 0) + 1 <= MuscleVolume.HIGH } ?: true) ->
                    out += Change(r.exId, r.name, +1, "две тренировки подряд все подходы на верхней границе, а вес прибавить нечем — +1 подход")
            }
        }
        return out
    }

    fun apply(extra: Map<String, Int>, changes: List<Change>): Map<String, Int> {
        val m = extra.toMutableMap()
        changes.forEach { c -> m[c.exId] = ((m[c.exId] ?: 0) + c.delta).coerceIn(0, MAX_EXTRA) }
        return m.filterValues { it != 0 }
    }
}
