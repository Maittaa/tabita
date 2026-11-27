package org.maittaa.tabita

import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 5.45: какие данные учитывает ритм дня. */
object RhythmSrc {
    const val SLEEP = "sleep"           // сон с часов: подъём, отбой, недосып
    const val ALARM = "alarm"           // будильник на завтра
    const val HC_TRAIN = "hc_train"     // тренировки с часов (Health Connect)
    const val APP_TRAIN = "app_train"   // тренировки, проведённые в приложении
    const val MEAL_PHOTO = "meal_photo" // время приёмов пищи по фото
    val ALL = setOf(SLEEP, ALARM, HC_TRAIN, APP_TRAIN, MEAL_PHOTO)
}

/**
 * 5.45: привычки по накопленным данным — без ручных настроек.
 * Время еды — по фото приёмов (время съёмки, у старых записей — когда фото добавлено), время силовой —
 * по тренировкам с часов и в приложении; сначала тот же день недели, потом будни/выходные, потом все дни.
 */
object Habits {
    data class Habit(val min: Int, val n: Int, val basis: String)

    const val MIN_SAMPLES = 3
    private val zone get() = ZoneId.systemDefault()
    private fun tod(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).let { it.hour * 60 + it.minute }
    private fun dayOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    private fun median(l: List<Int>) = l.sorted().let { if (it.size % 2 == 1) it[it.size / 2] else (it[it.size / 2 - 1] + it[it.size / 2]) / 2 }

    /** Тренировки в приложении (время начала, мс) — ставит MainActivity. */
    @Volatile var appTrainings: (() -> List<Long>)? = null

    /** Время приёма: съёмка фото; для записей до 5.45 — когда фото сохранено в приложение (если в тот же день). */
    fun mealTime(m: Meal, date: LocalDate, photosDir: File?): Long? {
        m.at?.let { return it }
        val f = m.photos.firstOrNull()?.let { File(photosDir ?: return null, it) }?.takeIf { it.exists() } ?: return null
        return f.lastModified().takeIf { it > 0 && dayOf(it) == date }
    }

    /** Отбор по дню: тот же день недели → тот же тип (будни/выходные) → все; нужно не меньше [need] значений. */
    private fun pick(samples: List<Pair<LocalDate, Int>>, date: LocalDate, need: Int): Habit? {
        val dow = date.dayOfWeek
        val we = DayRhythm.weekend(date)
        val same = samples.filter { it.first.dayOfWeek == dow }
        if (same.size >= need) return Habit(median(same.map { it.second }), same.size, "dow")
        val type = samples.filter { DayRhythm.weekend(it.first) == we }
        if (type.size >= need) return Habit(median(type.map { it.second }), type.size, if (we) "weekend" else "weekday")
        if (samples.size >= need) return Habit(median(samples.map { it.second }), samples.size, "all")
        return null
    }

    /** Привычное время приёмов пищи по видам (Завтрак/Обед/Перекус/Ужин) за 28 дней до [date]. */
    fun meals(repo: Repo, prefs: Prefs, date: LocalDate): Map<Program.Slot, Habit> {
        if (RhythmSrc.MEAL_PHOTO !in prefs.rhythmSrc) return emptyMap()
        val items = repo.template.value.filter { it.type == ItemType.MEALS }
        val by = HashMap<Program.Slot, MutableList<Pair<LocalDate, Int>>>()
        for (i in 1..28) {
            val d = date.minusDays(i.toLong())
            val day = repo.entries.value[d.key()] ?: continue
            items.flatMap { parseMeals(day[it.id]) }.groupBy { it.unit }.values.forEach { unit ->
                val slot = Program.Slot.entries.firstOrNull { it.ru == unit.first().kind } ?: return@forEach
                val ms = unit.mapNotNull { mealTime(it, d, repo.photosDir) }.minOrNull() ?: return@forEach
                by.getOrPut(slot) { mutableListOf() } += d to tod(ms)
            }
        }
        return by.mapNotNull { (s, l) -> pick(l, date, MIN_SAMPLES)?.let { s to it } }.toMap()
    }

    /** Привычное начало силовой за 6 недель: часы (strength) и тренировки в приложении; дубли в пределах 30 мин — одна. */
    fun training(repo: Repo, prefs: Prefs, date: LocalDate): Habit? {
        val src = prefs.rhythmSrc
        val starts = mutableListOf<Long>()
        if (RhythmSrc.HC_TRAIN in src) repo.template.value.firstOrNull { it.source == Source.HC_EXERCISE }?.let { ex ->
            for (i in 1..42) {
                val day = repo.entries.value[date.minusDays(i.toLong()).key()] ?: continue
                Workouts.sessions(day, ex.id).filter { it.kind == "strength" }.mapNotNull { it.start }.forEach { starts += it }
            }
        }
        if (RhythmSrc.APP_TRAIN in src) {
            val from = date.minusDays(42).atStartOfDay(zone).toInstant().toEpochMilli()
            val to = date.atStartOfDay(zone).toInstant().toEpochMilli()
            appTrainings?.invoke()?.filter { it in from until to }?.forEach { a -> if (starts.none { kotlin.math.abs(it - a) < 30 * 60_000 }) starts += a }
        }
        return pick(starts.map { dayOf(it) to tod(it) }, date, 2)
    }

    /** 5.56: привычное начало аэробной (≥ 10 мин) за 6 недель — с часов, таймера и ручных записей. */
    fun cardio(repo: Repo, prefs: Prefs, date: LocalDate): Habit? {
        if (RhythmSrc.HC_TRAIN !in prefs.rhythmSrc) return null
        val ex = repo.template.value.firstOrNull { it.source == Source.HC_EXERCISE } ?: return null
        val starts = mutableListOf<Long>()
        for (i in 1..42) {
            val day = repo.entries.value[date.minusDays(i.toLong()).key()] ?: continue
            Workouts.sessions(day, ex.id).filter { Cardio.isCardio(it) && it.min >= 10 }.mapNotNull { it.start }.forEach { starts += it }
        }
        return pick(starts.map { dayOf(it) to tod(it) }, date, 2)
    }

    fun basisText(h: Habit, date: LocalDate): String = when (h.basis) {
        "dow" -> t("обычно по %s (%d раз)", dowText(date.dayOfWeek), h.n)
        "weekday" -> t("обычно в будни (%d раз)", h.n)
        "weekend" -> t("обычно в выходные (%d раз)", h.n)
        else -> t("обычно (%d раз)", h.n)
    }

    private fun dowText(d: DayOfWeek) = t(listOf("понедельникам", "вторникам", "средам", "четвергам", "пятницам", "субботам", "воскресеньям")[d.value - 1])
}
