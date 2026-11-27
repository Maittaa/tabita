package org.maittaa.tabita.train

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * «Подобрать автоматически» (1.4): настройки программы по данным дневника (сон, пульс покоя, состав тела),
 * профилю и истории тренировок. Место и инвентарь не меняются — их выбирает пользователь.
 */
object Recommender {
    data class Rec(val s: GenSettings, val why: List<String>)

    private fun fmt1(v: Double) = "%.1f".format(v).replace('.', ',')

    /** Удобные дни с отдыхом между тренировками. */
    fun daysFor(n: Int): List<DayOfWeek> = when (n) {
        1 -> listOf(DayOfWeek.WEDNESDAY)
        2 -> listOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)
        3 -> listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        else -> listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    }

    fun recommend(cur: GenSettings, data: TabitaData?, body: Body, history: List<Session>, today: LocalDate = LocalDate.now()): Rec {
        val why = mutableListOf<String>()
        val days = data?.days.orEmpty().filter { !it.date.isAfter(today) && ChronoUnit.DAYS.between(it.date, today) <= 30 }

        // ——— цель: по составу тела ———
        val fat = days.lastOrNull { it.fat != null }?.fat
        val h = body.heightCm?.takeIf { it > 0 }?.div(100.0)
        val ffmi = days.lastOrNull { it.ffmi != null }?.ffmi
            ?: if (h != null && body.weightKg != null && fat != null) body.weightKg * (1 - fat / 100) / (h * h) else null
        val bmi = body.bmi
        val female = body.sex == "f"
        val fatHigh = fat != null && fat >= (if (female) 33.0 else 25.0)
        val goal = when {
            bmi != null && bmi < 18.5 -> Goal.MASS.also { why += "ИМТ ${fmt1(bmi)} — ниже нормы: цель «набор массы»" }
            fatHigh -> Goal.TONE.also { why += "Жира ${fat!!.roundToInt()} % — сначала «общая форма»: больше повторов и меньше отдыха, жир уходит быстрее. Набор массы — когда жир станет ниже ${if (female) 30 else 22} %" }
            fat == null && bmi != null && bmi >= 30 -> Goal.TONE.also { why += "ИМТ ${fmt1(bmi)} — начнём с «общей формы», это бережнее для суставов" }
            ffmi != null && ffmi < 20 -> Goal.MASS.also { why += "FFMI ${fmt1(ffmi)} — мышцам есть куда расти: цель «набор массы»" }
            ffmi != null -> Goal.STRENGTH.also { why += "FFMI ${fmt1(ffmi)} — мышц уже хорошо: можно делать упор на силу" }
            else -> cur.goal.also { why += "Цель оставлена прежней — для подбора нужны вес и процент жира в дневнике" }
        }

        // ——— уровень: по истории тренировок за 3 месяца ———
        val done90 = history.count { s -> runCatching { LocalDate.parse(s.date) }.getOrNull()?.let { ChronoUnit.DAYS.between(it, today) in 0..90 } == true }
        val level = when { done90 < 12 -> 1; done90 < 36 -> 2; else -> 3 }
        why += "Уровень «${listOf("новичок", "средний", "продвинутый")[level - 1]}»: за 3 месяца $done90 тренировок"

        // ——— восстановление: сон и пульс покоя за 2 недели ———
        val sleep = days.filter { ChronoUnit.DAYS.between(it.date, today) <= 14 }.mapNotNull { it.sleepMin }.takeIf { it.size >= 5 }?.average()
        val rhr = days.mapNotNull { d -> d.rhr?.let { d.date to it } }
        val rhrUp = rhr.size >= 10 && rhr.takeLast(5).map { it.second }.average() >= rhr.dropLast(5).map { it.second }.average() + 3
        val poorRecovery = (sleep != null && sleep < 390) || rhrUp
        sleep?.let { why += "Сон в среднем ${fmtMin(it)}" + if (it < 390) " — мало для тяжёлых тренировок" else "" }
        if (rhrUp) why += "Пульс покоя последние дни выше обычного"

        // ——— сколько дней: что реально получается + восстановление ———
        val weeks4 = history.count { s -> runCatching { LocalDate.parse(s.date) }.getOrNull()?.let { ChronoUnit.DAYS.between(it, today) in 0..27 } == true } / 4.0
        var n = cur.days.size.coerceIn(2, 4)
        if (done90 >= 4 && weeks4 < n - 1) { n = maxOf(2, weeks4.roundToInt() + 1); why += "За последний месяц ~${fmt1(weeks4)} тренировки в неделю — план на $n дня, чтобы его выполнять" }
        if (poorRecovery && n > 3) { n = 3; why += "При таком восстановлении хватит 3 тренировок в неделю" }
        if (level == 1 && n > 3) { n = 3; why += "Новичку достаточно 3 тренировок всего тела в неделю" }
        val dayList = if (n == cur.days.size) cur.days else daysFor(n)

        // ——— длительность ———
        val minutes = when {
            poorRecovery -> 30.also { why += "30 минут — пока сон и пульс не придут в норму" }
            level >= 2 && goal != Goal.TONE -> 45.also { why += "45 минут, 3 подхода — опыт уже позволяет" }
            else -> 30.also { why += "30 минут, 7 упражнений — оптимально на старте" }
        }
        // ——— разбивка ———
        val split = when {
            n >= 5 -> Split.PPL.also { why += "$n дней — «жим / тяга / ноги»: каждая группа дважды в неделю" }
            n == 4 && level >= 2 -> Split.UPPER_LOWER.also { why += "4 дня — «верх / низ»: каждая группа дважды в неделю, больше объёма на мышцу" }
            else -> Split.FULL.also { why += "«Всё тело»: при $n днях так каждая мышца получает нагрузку ${if (n >= 2) "$n раза" else "раз"} в неделю" }
        }
        return Rec(cur.copy(goal = goal, days = dayList, minutes = minutes, level = level, split = split), why)
    }
}

/**
 * На какие мышцы тренировка (1.4): группы по паттернам упражнений, упор — где больше всего подходов.
 * Пример: «Всё тело · упор: ноги и ягодицы, спина».
 */
object MuscleFocus {
    private val groups = mapOf(
        Pattern.SQUAT to "ноги", Pattern.LUNGE to "ноги", Pattern.CALF to "ноги",
        Pattern.HINGE to "ягодицы", Pattern.GLUTE to "ягодицы",
        Pattern.PUSH to "грудь", Pattern.VPUSH to "плечи", Pattern.SHOULDER to "плечи", Pattern.REAR to "плечи",
        Pattern.ROW to "спина", Pattern.VPULL to "спина",
        Pattern.BICEPS to "руки", Pattern.TRICEPS to "руки", Pattern.CORE to "пресс",
    )

    /** 5.55: группа мышц упражнения по его паттерну. */
    fun groupOf(p: Pattern): String? = groups[p]

    /** Группы мышц с числом подходов, от большего к меньшему. */
    fun groups(day: ProgramDay): List<Pair<String, Int>> =
        day.exercises.mapNotNull { pe -> pe.def?.let { groups[it.pattern] to pe.sets } }
            .filter { it.first != null }.groupBy({ it.first!! }, { it.second })
            .map { (g, s) -> g to s.sum() }.sortedByDescending { it.second }

    fun describe(day: ProgramDay): String? {
        val g = groups(day)
        if (g.isEmpty()) return null
        val names = g.map { it.first }
        val upper = setOf("грудь", "спина", "плечи", "руки")
        val lower = setOf("ноги", "ягодицы")
        val head = when {
            names.any { it in upper } && names.any { it in lower } && names.size >= 4 -> "Всё тело"
            names.all { it in lower || it == "пресс" } -> "Низ тела"
            names.all { it in upper || it == "пресс" } -> "Верх тела"
            else -> null
        }
        // упор — только если подходы распределены неравномерно
        val top = g.first().second
        val even = g.all { it.second == top }
        val focus = g.filter { it.second == top }.map { it.first }
        val order = listOf("ноги", "ягодицы", "грудь", "спина", "плечи", "руки", "пресс")
        val list = names.sortedBy { order.indexOf(it) }.joinToString(", ")
        return when {
            head != null && !even -> "$head · упор: ${focus.joinToString(", ")}"
            head != null -> "$head: $list"
            else -> "Мышцы: $list"
        }
    }
}
