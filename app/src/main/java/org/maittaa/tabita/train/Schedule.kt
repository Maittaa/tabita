package org.maittaa.tabita.train

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 1.8: расписание с переносами и решение «тренироваться ли сегодня».
 *
 * Переносы хранятся как поправки к недельному расписанию: дата → id дня программы ("" — отдых).
 * Дата без поправки — по дню недели из программы.
 */
object Schedule {
    /** Какой день программы стоит на [date] с учётом поправок (null — отдых). */
    fun dayOn(p: Program, overrides: Map<String, String>, date: LocalDate): ProgramDay? {
        val days = p.days.filter { it.exercises.isNotEmpty() }
        overrides[date.toString()]?.let { id ->
            if (id.isEmpty()) return null
            days.firstOrNull { it.id == id }?.let { return it }  // дня уже нет в программе — по дню недели
        }
        return days.firstOrNull { it.weekday == date.dayOfWeek }
    }

    /**
     * Минимальный отдых между тренировками по исходному расписанию: если в неделе есть дни подряд
     * (сплит «верх/низ», 4–5 дней), достаточно 1 дня; иначе (всё тело через день) — 2.
     */
    fun minGap(p: Program): Int {
        val wd = p.days.filter { it.exercises.isNotEmpty() }.mapNotNull { it.weekday?.value }.distinct().sorted()
        if (wd.size < 2) return 2
        val diffs = wd.zipWithNext { a, b -> b - a } + (wd.first() + 7 - wd.last())
        return if (diffs.min() <= 1) 1 else 2
    }

    /**
     * Сдвинуть сегодняшнюю тренировку на следующий подходящий день, и дальше по цепочке — только пока нужно,
     * чтобы между тренировками оставался отдых ([minGap]). Например, Пн/Ср/Пт и пропуск в Пн:
     * Пн→Вт, Ср→Чт, Пт→Сб, а следующий Пн остаётся на месте.
     * Возвращает новые поправки и список переносов (откуда → куда) для показа.
     */
    fun postpone(p: Program, overrides: Map<String, String>, today: LocalDate): Pair<Map<String, String>, List<Triple<ProgramDay, LocalDate, LocalDate>>> {
        val gap = minGap(p)
        // ближайшие тренировки на 3 недели вперёд, начиная с сегодняшней
        val upcoming = (0L..20L).mapNotNull { k -> val d = today.plusDays(k); dayOn(p, overrides, d)?.let { d to it } }
        if (upcoming.isEmpty() || upcoming.first().first != today) return overrides to emptyList()
        val out = overrides.filterKeys { it >= today.toString() }.toMutableMap()
        val moves = mutableListOf<Triple<ProgramDay, LocalDate, LocalDate>>()
        var prevNew = today  // сегодняшняя уезжает минимум на завтра
        for ((i, pair) in upcoming.withIndex()) {
            val (date, day) = pair
            val newDate = if (i == 0) today.plusDays(1) else maxOf(date, prevNew.plusDays(gap.toLong()))
            if (i > 0 && newDate == date) break
            moves += Triple(day, date, newDate)
            prevNew = newDate
        }
        // сначала снимаем со старых дат, потом ставим на новые
        moves.forEach { (_, from, _) -> out[from.toString()] = "" }
        moves.forEach { (d, _, to) -> out[to.toString()] = d.id }
        return out to moves
    }

    /** Убрать устаревшие поправки (до сегодня). */
    fun prune(overrides: Map<String, String>, today: LocalDate) = overrides.filterKeys { it >= today.toString() }

    /** Поправка совпадает с исходным расписанием — её можно не хранить. */
    fun simplify(p: Program, overrides: Map<String, String>): Map<String, String> = overrides.filter { (date, id) ->
        val orig = p.days.filter { it.exercises.isNotEmpty() }.firstOrNull { it.weekday == LocalDate.parse(date).dayOfWeek }?.id ?: ""
        orig != id
    }
}

/** Решение на сегодня по готовности. */
enum class Verdict(val title: String) {
    GO("Тренировка сегодня — рекомендуется"),
    EASY("Можно, но облегчённо"),
    SKIP("Сегодня тренировку лучше перенести"),
}

/** Совет на сегодня для восстановления. */
data class RecoveryTip(val title: String, val text: String)

object TodayAdvice {
    fun verdict(r: Readiness): Verdict = when (r.intensity) {
        Intensity.NORMAL -> Verdict.GO
        Intensity.LIGHT -> Verdict.EASY
        Intensity.RECOVERY -> Verdict.SKIP
    }

    /** Почему так — коротко, по самым весомым причинам. */
    fun why(r: Readiness): String? = r.reasons.filter { it.tone == Tone.BAD }.take(2).joinToString("; ") { it.text }.ifBlank { null }

    /**
     * Советы по восстановлению на сегодня — по тем же данным Tabita, что и готовность.
     * Основа: рекомендации по сну (AASM), активное восстановление (лёгкая аэробная нагрузка 20–30 мин),
     * белок 1,6 г/кг (ISSN), гидратация.
     */
    fun recovery(data: TabitaData?, r: Readiness, today: LocalDate, bodyWeight: Double?, hour: Int = java.time.LocalTime.now().hour): List<RecoveryTip> {
        val out = mutableListOf<RecoveryTip>()
        val byDate = data?.days?.associateBy { it.date } ?: emptyMap()
        val sleep = byDate[today]?.sleepMin
        val rhrHigh = r.reasons.any { it.tone == Tone.BAD && it.text.startsWith("Пульс покоя") }
        val sleepShort = sleep != null && sleep < 360
        if (sleepShort) {
            if (hour < 15) out += RecoveryTip("Короткий дневной сон",
                "20–30 минут до 15:00 частично возвращают бодрость и реакцию после короткой ночи. Дольше — проснёшься разбитым.")
            out += RecoveryTip("Лечь пораньше",
                "Сегодня — на 30–60 минут раньше обычного. Без кофе после 14:00 и яркого экрана за час до сна: недосып отдаётся за 1–2 ночи, а не за одну.")
        }
        if (rhrHigh) out += RecoveryTip("Пульс выше обычного",
            "Это часто недовосстановление, начало простуды или мало воды. Пей больше, без алкоголя; если есть температура или ломота — никаких тренировок, даже лёгких.")
        if (r.intensity != Intensity.NORMAL) out += RecoveryTip("Активное восстановление",
            "Вместо тренировки — прогулка 20–30 минут в спокойном темпе (можно говорить, не задыхаясь) и 10 минут растяжки или мобильности. Кровоток ускоряет восстановление лучше полного покоя.")
        bodyWeight?.let { w ->
            out += RecoveryTip("Белок и еда",
                "Восстановление идёт и в день отдыха: белок ~${(w * ReadinessCalc.PROTEIN_PER_KG).toInt()} г за день, по 25–40 г в 3–4 приёма. Калории не урезай — дефицит тормозит восстановление.")
        }
        val legs = r.legsLight
        if (legs) out += RecoveryTip("Ногам — отдых",
            "Вчера было много шагов или активности — для ног сегодня хватит прогулки, без прыжков и выпадов.")
        if (out.isEmpty()) out += RecoveryTip("Всё в норме", "Восстановление в порядке — хороший день для обычной тренировки.")
        return out
    }

    /** Сколько дней прошло после последней силовой (для подписи). */
    fun daysSince(history: List<Session>, today: LocalDate): Int? =
        history.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }.maxOrNull()?.let { ChronoUnit.DAYS.between(it, today).toInt() }
}
