package org.maittaa.tabita

import java.time.LocalDate
import java.time.ZoneId

/**
 * 5.60: подробный сон — регулярность и ночи за 2 недели.
 * Индекс регулярности сна (SRI, Phillips и др., 2017): вероятность быть в одном состоянии (сон / не сон)
 * в два момента с разницей 24 ч, в шкале −100…100 (200·P − 100); 100 — сон и подъём минута в минуту.
 * В UK Biobank (Windred и др., 2024, SLEEP) медиана 81 (IQR 74–86), и регулярность предсказывала
 * смертность сильнее длительности сна. Здесь — по основному сну с часов (без дневного), минуты «без сна»
 * внутри ночи считаются бодрствованием.
 */
object SleepStats {
    data class Night(val date: LocalDate, val d: SleepDetail)

    fun nights(repo: Repo, today: LocalDate = LocalDate.now(), days: Int = 14): List<Night> {
        val item = repo.template.value.firstOrNull { it.source == Source.HC_SLEEP } ?: return emptyList()
        return (0 until days).mapNotNull { i ->
            val d = today.minusDays(i.toLong())
            repo.entries.value[d.key()]?.let { Sleep.detail(it, item) }?.takeIf { it.end > it.start }?.let { Night(d, it) }
        }.sortedBy { it.date }
    }

    /** Спал ли в минуту [ms]: внутри ночи и не на участке «без сна». */
    private fun asleep(n: SleepDetail, ms: Long): Boolean {
        if (ms < n.start || ms >= n.end) return false
        if (n.stages.isEmpty()) return true
        val off = ((ms - n.start) / 60_000).toInt()
        val st = n.stages.firstOrNull { off >= it.offset && off < it.offset + it.dur } ?: return true
        return st.type != 0
    }

    /**
     * SRI по парам соседних суток: сутки «ночи» — от полудня накануне дня пробуждения до полудня этого дня.
     * Нужно хотя бы [minPairs] пар соседних ночей; иначе null.
     */
    fun sri(nights: List<Night>, zone: ZoneId = ZoneId.systemDefault(), minPairs: Int = 5): Int? {
        val by = nights.associateBy { it.date }
        var same = 0L; var total = 0L; var pairs = 0
        nights.forEach { a ->
            val b = by[a.date.plusDays(1)] ?: return@forEach
            pairs++
            val t0 = a.date.minusDays(1).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
            for (m in 0 until 1440) {
                val t = t0 + m * 60_000L
                if (asleep(a.d, t) == asleep(b.d, t + 86_400_000L)) same++
                total++
            }
        }
        if (pairs < minPairs || total == 0L) return null
        return Math.round(200.0 * same / total - 100).toInt()
    }

    /** Оценка индекса относительно UK Biobank: 0 — выше типичного, 1 — типичный, 2 — ниже. */
    fun sriBand(v: Int) = when { v >= 86 -> 0; v >= 74 -> 1; else -> 2 }

    /** Средние доли фаз за ночи с разбивкой, %; null — фаз нет. */
    data class Stages(val deep: Int, val light: Int, val rem: Int, val awakeMin: Int, val nights: Int)

    fun stages(nights: List<Night>): Stages? {
        val n = nights.map { it.d }.filter { it.hasStages }
        if (n.isEmpty()) return null
        val asl = n.sumOf { it.asleep }.toDouble().takeIf { it > 0 } ?: return null
        return Stages(Math.round(n.sumOf { it.deep } * 100 / asl).toInt(), Math.round(n.sumOf { it.light + it.unspecified } * 100 / asl).toInt(),
            Math.round(n.sumOf { it.rem } * 100 / asl).toInt(), Math.round(n.sumOf { it.awake }.toDouble() / n.size).toInt(), n.size)
    }
}
