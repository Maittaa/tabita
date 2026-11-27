package org.maittaa.tabita

import java.time.LocalDate

/**
 * 5.44–5.45: события дня поверх ритма — приёмы пищи, тренировка, перекус перед ней, прогулки после еды,
 * короткий дневной сон при недосыпе, белок перед сном. Без ручных настроек: время — от сна (подъём/отбой),
 * привычек (Habits: фото еды, тренировки с часов и в приложении) и сегодняшнего факта.
 * Время — минуты от полуночи. Источники: ISSN (белок 20–40 г каждые 3–4 ч, 30–40 г казеина перед сном),
 * Sports Med 2023 (ходьба сразу после еды сглаживает сахар), Brooks & Lack 2006 (10-минутный дневной сон).
 */
object DayPlan {
    enum class Kind { MEAL, TRAIN, PRE_SNACK, WALK, NAP, BED_PROTEIN, CARDIO, YOGA }

    data class Event(val kind: Kind, val at: Int, val len: Int, val title: String, val note: String = "", val done: Boolean = false)

    /** Тренировка на день — из модуля тренировок (ставит MainActivity). */
    data class TrainPlan(val name: String, val min: Int, val gym: Boolean, val doneAt: Int? = null)
    @Volatile var trainSource: ((LocalDate) -> TrainPlan?)? = null

    data class Info(val events: List<Event>, val meals: Map<Program.Slot, Int>, val notes: List<String>)

    /** Белка на приём: 0,3 г/кг (ISSN: 0,25–0,40 г/кг), 20–40 г. */
    fun proteinDose(weight: Double?) = ((weight ?: 75.0) * 0.3).coerceIn(20.0, 40.0).let { Math.round(it / 5) * 5.0 }

    private fun tod(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).let { it.hour * 60 + it.minute }

    fun of(repo: Repo, prefs: Prefs, date: LocalDate, r: DayRhythm, train: TrainPlan? = trainSource?.invoke(date)): Info {
        val goal = Goal.of(prefs.planGoal)
        val src = prefs.rhythmSrc
        val wake = r.wake
        val bed = wake + r.bedRel          // отбой в минутах от полуночи дня подъёма (может быть > 1440)
        fun rel(m: Int) = ((m - wake) % 1440 + 1440) % 1440 + wake   // время дня → шкала от подъёма
        val ev = mutableListOf<Event>()
        val notes = mutableListOf<String>()
        val tmpl = repo.template.value
        val day = repo.entries.value[date.key()] ?: emptyMap()
        val weight = Anthro.weightOf(tmpl, day) ?: repo.latestWeight(date.key())

        // ---- тренировка: сделанная сегодня (часы) или плановая — во время по привычке ----
        val exItem = if (RhythmSrc.HC_TRAIN in src) tmpl.firstOrNull { it.source == Source.HC_EXERCISE } else null
        val doneS = exItem?.let { Workouts.sessions(day, it.id) }?.firstOrNull { it.kind == "strength" && it.start != null }
        val peak = r.lanes.first { it.key == "peak" }
        val pFrom = wake + peak.from; val pTo = wake + peak.to
        var trainAt: Int? = null; var trainLen = 0
        if (doneS != null) {
            trainAt = rel(tod(doneS.start!!)); trainLen = doneS.min
            ev += Event(Kind.TRAIN, trainAt, trainLen, t("Тренировка ✓"), done = true)
        } else if (train != null) {
            val habit = Habits.training(repo, prefs, date)
            val at = habit?.let { rel(it.min) } ?: pFrom
            trainAt = at; trainLen = train.min
            val why = when {
                habit != null && at in pFrom..pTo -> Habits.basisText(habit, date) + " · " + t("в окне пика сил")
                habit != null -> Habits.basisText(habit, date)
                else -> t("в окне пика сил")
            }
            ev += Event(Kind.TRAIN, at, train.min, train.name, why)
            if (at + train.min > bed - 120)
                notes += t("Тренировка заканчивается позже чем за 2 ч до сна (%s) — сон может стать поверхностнее. Сделайте её легче или раньше.", clockOf(bed))
        }

        // ---- 5.51: аэробные занятия, сделанные сегодня (таймер, вручную, часы) ----
        tmpl.filter { it.source == Source.HC_EXERCISE }.flatMap { Workouts.sessions(day, it.id) }
            .filter { (Cardio.isCardio(it) || it.kind in Yoga.kinds) && it.start != null && it.min >= 10 }
            .forEach { s -> ev += Event(Kind.CARDIO, rel(tod(s.start!!)), s.min, Workouts.kind(s.kind).title + " ✓", done = true) }

        // ---- 5.56: плановые аэробная и йога по недельному шаблону ----
        val tpl = WeekTpl.of(prefs)
        val planned = tpl.on(date.dayOfWeek)
        val doneAll = tmpl.filter { it.source == Source.HC_EXERCISE }.flatMap { Workouts.sessions(day, it.id) }
        if (WeekTpl.CARDIO in planned && doneAll.none { Cardio.isCardio(it) && it.min >= 10 }) {
            val habit = Habits.cardio(repo, prefs, date)
            var at = habit?.let { rel(it.min) } ?: (trainAt?.let { it + trainLen + 15 } ?: pFrom)
            var why = habit?.let { Habits.basisText(it, date) } ?: if (trainAt != null) t("после силовой") else t("в окне пика сил")
            if (trainAt != null && at < trainAt + trainLen + 15 && at + tpl.cardioMin > trainAt - 180) {
                // в один день с силовой: после неё (Eddens и др., 2018) или за 3+ ч до
                at = trainAt + trainLen + 15; why = t("после силовой — так меньше мешает силе")
            }
            ev += Event(Kind.CARDIO, at, tpl.cardioMin, t("Аэробная"), why)
        }
        if (WeekTpl.YOGA in planned && doneAll.none { it.kind in Yoga.kinds && it.min >= 5 }) {
            val c = Yoga.complex("evening")
            val len = c?.let { (it.totalSec + 59) / 60 } ?: 10
            ev += Event(Kind.YOGA, bed - 60 - len, len, t("Йога перед сном"), c?.let { t("комплекс «%s»", it.name) } ?: "")
        }

        // ---- приёмы пищи: рекомендованное время, поправленное привычками (фото еды) ----
        val habits = Habits.meals(repo, prefs, date)
        val recBreakfast = wake + 45; val recLunch = wake + 330; val recDinner = bed - 210
        fun habitOr(slot: Program.Slot, rec: Int) = habits[slot]?.let { rel(it.min) } ?: rec
        val breakfast = habitOr(Program.Slot.BREAKFAST, recBreakfast).coerceAtLeast(wake + 10)
        val lunch = habitOr(Program.Slot.LUNCH, recLunch)
        var dinner = habitOr(Program.Slot.DINNER, recDinner)
        val trainEnd = trainAt?.let { it + trainLen }
        if (trainEnd != null && trainAt!! > lunch && dinner < trainEnd + 30) dinner = trainEnd + 45
        if (dinner > bed - 180) {
            habits[Program.Slot.DINNER]?.let { h ->
                notes += t("Обычно ужинаете в %s — за %s до сна, а окно еды заканчивается за 3 ч до отбоя. Сдвигайте ужин на 15–20 мин раньше каждые несколько дней.",
                    clockOf(h.min), formatMinutes((bed - rel(h.min)).coerceAtLeast(0).toDouble()))
            }
            dinner = maxOf(bed - 180, lunch + 120)
        }
        val preSnack = trainAt != null && doneS == null && trainAt - lunch >= 240
        val snack = if (preSnack) trainAt!! - 75 else habitOr(Program.Slot.SNACK, (lunch + dinner) / 2)
        val meals = mapOf(Program.Slot.BREAKFAST to breakfast, Program.Slot.LUNCH to lunch, Program.Slot.SNACK to snack, Program.Slot.DINNER to dinner)
            .mapValues { it.value % 1440 }

        // меню программы — что есть в каждый приём
        val menu = runCatching {
            if (goal == null || goal == Goal.WELLBEING) null
            else Program.targets(goal, PlanStats.of(repo, prefs, date))?.let { tg -> Program.menu(goal, tg, r, prefs, at = meals) }
        }.getOrNull()
        // сегодня уже записано: вид приёма → время (по фото) или просто «был»
        val logged = tmpl.filter { it.type == ItemType.MEALS }.flatMap { parseMeals(day[it.id]) }.groupBy { it.unit }.values
            .mapNotNull { u -> Program.Slot.entries.firstOrNull { it.ru == u.first().kind }?.let { it to u.mapNotNull { m -> Habits.mealTime(m, date, repo.photosDir) }.minOrNull() } }
            .toMap()
        val proteinGoal = goal == Goal.MUSCLE || goal == Goal.GAIN || goal == Goal.DEFICITS
        val dose = proteinDose(weight)
        meals.forEach { (slot, at) ->
            val m = menu?.firstOrNull { it.slot == slot }
            val isPre = slot == Program.Slot.SNACK && preSnack
            val title = if (isPre) t("Перекус перед тренировкой") else slot.title
            if (slot in logged) {
                val ms = logged[slot]
                ev += Event(if (isPre) Kind.PRE_SNACK else Kind.MEAL, ms?.let { rel(tod(it)) } ?: rel(at), 20, "$title ✓",
                    if (ms == null) t("записан") else t("по фото"), done = true)
                return@forEach
            }
            val what = m?.items?.take(3)?.joinToString(", ") { it.food.name.lowercase() + " " + it.amount() }
            val prot = if (proteinGoal) t("белок ~%s г", formatNumber(m?.p?.let { Math.round(it).toDouble() } ?: dose)) else null
            val hab = habits[slot]?.let { if (!isPre) Habits.basisText(it, date) else null }
            ev += Event(if (isPre) Kind.PRE_SNACK else Kind.MEAL, rel(at), 20, title, listOfNotNull(hab, prot, what).joinToString(" · "))
        }

        // ---- прогулки после обеда и ужина ----
        listOf(Program.Slot.LUNCH, Program.Slot.DINNER).forEach { s ->
            val m = ev.firstOrNull { it.kind == Kind.MEAL && it.title.startsWith(s.title) }?.at ?: rel(meals[s]!!)
            ev += Event(Kind.WALK, m + 20, 10, t("Прогулка после еды"), t("сразу после еды; даже 2–5 мин пешком сглаживают сахар"))
        }

        // ---- короткий дневной сон, если ночью не доспали больше часа ----
        val sleepItem = if (RhythmSrc.SLEEP in src) tmpl.firstOrNull { it.source == Source.HC_SLEEP } else null
        val slept = sleepItem?.let { parseNumber(day[it.id]) }
        if (slept != null && slept < r.need - 15 - 60) {
            val at = (wake + 7 * 60).coerceIn(13 * 60, 15 * 60 - 20)
            ev += Event(Kind.NAP, rel(at), 20, t("Короткий дневной сон"),
                t("ночью %s — 10–20 мин, не позже 15:00, с будильником; если есть возможность", formatMinutes(slept)))
        }

        // ---- белок перед сном (набор мышц и массы) ----
        if (goal == Goal.MUSCLE || goal == Goal.GAIN) {
            val f = FoodDb.all.firstOrNull { it.ru.startsWith("Творог 0") } ?: FoodDb.all.firstOrNull { it.ru.startsWith("Творог") }
            val what = f?.let { it.name.lowercase() + " " + formatNumber(Program.gramsFor(it, 35.0, 100.0, 400.0)) + " " + t("г") }
            ev += Event(Kind.BED_PROTEIN, bed - 45, 10, t("Белок перед сном"),
                listOfNotNull(t("30–40 г медленного белка"), what).joinToString(": "))
        }
        return Info(ev.sortedBy { it.at }.map { it.copy(at = ((it.at % 1440) + 1440) % 1440) }, meals, notes)
    }
}
