package org.maittaa.tabita

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 5.56: недельный шаблон нагрузки. Дни силовой берутся из программы силовой (один источник),
 * в шаблоне — аэробная ('C') и йога ('Y') по дням недели, цель активных минут и длительность аэробной.
 * Ориентиры: ВОЗ 2020 — 150–300 мин умеренной активности и силовые на все группы мышц ≥ 2 дней в неделю;
 * в один день — сначала силовая, потом аэробная (Eddens и др., 2018, Sports Med) или через ≥ 3 ч
 * (Schumann и др., 2022, Sports Med: в разных сессиях помех силе и росту мышц не найдено).
 */
data class WeekTpl(val days: List<Set<Char>>, val activeGoal: Int = 150, val cardioMin: Int = 30) {
    fun on(d: DayOfWeek): Set<Char> = days[d.value - 1]
    fun count(k: Char) = days.count { k in it }
    fun toggle(d: DayOfWeek, k: Char) = copy(days = days.mapIndexed { i, s -> if (i == d.value - 1) (if (k in s) s - k else s + k) else s })

    fun toJson(): String = JSONObject()
        .put("d", JSONArray(days.map { it.sorted().joinToString("") }))
        .put("am", activeGoal).put("cm", cardioMin).toString()

    companion object {
        const val STRENGTH = 'S'; const val CARDIO = 'C'; const val YOGA = 'Y'

        /** По умолчанию: аэробная вт и сб, йога чт и вс (силовая — из программы, обычно пн/ср/пт). */
        val DEFAULT = WeekTpl(List(7) { i -> when (i) { 1, 5 -> setOf(CARDIO); 3, 6 -> setOf(YOGA); else -> emptySet() } })

        fun parse(s: String?): WeekTpl? = runCatching {
            val o = JSONObject(s ?: return null)
            val a = o.getJSONArray("d")
            if (a.length() != 7) return null
            WeekTpl(List(7) { i -> a.getString(i).filter { it == CARDIO || it == YOGA }.toSet() },
                o.optInt("am", 150).coerceIn(30, 600), o.optInt("cm", 30).coerceIn(10, 120))
        }.getOrNull()

        fun of(prefs: Prefs): WeekTpl = parse(prefs.weekTpl) ?: DEFAULT
    }
}

object WeekPlan {
    /** Дни силовой по программе (задаётся в MainActivity; в тестах можно подменить). */
    @Volatile var strengthDays: (() -> List<DayOfWeek>)? = null

    fun strength(): List<DayOfWeek> = runCatching { strengthDays?.invoke() }.getOrNull().orEmpty().distinct().sortedBy { it.value }

    /** Что запланировано на день недели: силовая из программы + шаблон. */
    fun kinds(tpl: WeekTpl, d: DayOfWeek, sDays: List<DayOfWeek> = strength()): Set<Char> =
        tpl.on(d) + if (d in sDays) setOf(WeekTpl.STRENGTH) else emptySet()

    fun planned(tpl: WeekTpl, k: Char, sDays: List<DayOfWeek> = strength()): Int =
        if (k == WeekTpl.STRENGTH) sDays.size else tpl.count(k)

    private fun dayName(d: DayOfWeek) = t(listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")[d.value - 1])

    /** Замечания к шаблону. */
    fun warnings(tpl: WeekTpl, sDays: List<DayOfWeek> = strength()): List<String> {
        val w = mutableListOf<String>()
        val both = DayOfWeek.entries.filter { it in sDays && WeekTpl.CARDIO in tpl.on(it) }
        if (both.isNotEmpty()) w += t("%s: силовая и аэробная в один день — сначала силовая, аэробная после неё или через 3+ часа", both.joinToString(", ") { dayName(it) })
        if (sDays.size in 1..1) w += t("Одна силовая в неделю — ВОЗ советует силовые на все группы мышц не реже 2 дней в неделю")
        if (sDays.isEmpty() && tpl.count(WeekTpl.CARDIO) + tpl.count(WeekTpl.YOGA) > 0)
            w += t("Силовых в плане нет — соберите программу в «Здоровье → Силовая»")
        return w
    }

    /** Пояснение: сколько активных минут даёт аэробная по плану. */
    fun note(tpl: WeekTpl): String? {
        val perWeek = tpl.count(WeekTpl.CARDIO) * tpl.cardioMin
        return if (perWeek in 1 until tpl.activeGoal)
            t("Аэробная по плану — %d мин в неделю; остальное до %d наберётся ходьбой и делами, если пульс в умеренной зоне", perWeek, tpl.activeGoal)
        else null
    }
}

/** Линейный тренд по точкам (день, значение): наклон в единицах за день и значение на последний день. */
data class Trend(val now: Double, val perDay: Double, val n: Int, val spanDays: Int) {
    val perWeek get() = perDay * 7

    companion object {
        fun fit(points: List<Pair<Int, Double>>): Trend? {
            if (points.size < 2) return null
            val mx = points.sumOf { it.first.toDouble() } / points.size
            val my = points.sumOf { it.second } / points.size
            val sxx = points.sumOf { (it.first - mx) * (it.first - mx) }
            if (sxx <= 0) return null
            val slope = points.sumOf { (it.first - mx) * (it.second - my) } / sxx
            val last = points.maxOf { it.first }
            return Trend(my + slope * (last - mx), slope, points.size, last - points.minOf { it.first })
        }
    }
}

/**
 * 5.56: реальный расход по съеденному и тренду веса (метод баланса энергии):
 * расход = среднее съеденное − изменение веса × 7700 ккал/кг. Hall и Chow (2011, AJCN): для точности
 * ±300 ккал/сут нужны ежедневные взвешивания дольше 28 дней — поэтому окно 28 дней и требования к данным.
 * 7700 ккал/кг — средняя энергия жировой ткани; при наборе мышц часть прироста — безжировая масса
 * с меньшей энергией, поэтому оценка грубее.
 */
object Adapt {
    const val KCAL_PER_KG = 7700.0
    const val WINDOW = 28
    const val MIN_INTAKE_DAYS = 20
    const val MIN_WEIGHINS = 8
    const val MIN_SPAN = 21
    const val STEP = 100

    data class Result(
        val intakeDays: Int, val weighIns: Int, val span: Int,
        val intake: Double?, val trend: Trend?, val tdee: Double?, val model: Double?,
        val missing: String?,
    ) {
        val ok get() = missing == null && tdee != null && model != null
        /** Поправка к расчётному расходу, ккал (с шагом 10, не больше ±800). */
        val offset: Int? get() = if (ok) (Math.round((tdee!! - model!!) / 10) * 10).toInt().coerceIn(-800, 800) else null
    }

    fun estimate(repo: Repo, today: LocalDate = LocalDate.now()): Result {
        val tmpl = repo.template.value; val ent = repo.entries.value; val profile = repo.profile.value
        val intake = mutableListOf<Double>(); val model = mutableListOf<Double>(); val weights = mutableListOf<Pair<Int, Double>>()
        for (i in 1..WINDOW) {
            val d = today.minusDays(i.toLong())
            val day = ent[d.key()] ?: continue
            Anthro.weightOf(tmpl, day)?.let { weights += (WINDOW - i) to it }
            val e = Anthro.eatenOf(tmpl, day)?.takeIf { it > 300 } ?: continue
            intake += e
            Anthro.energyOn(profile, tmpl, ent, d)?.total?.let { model += it }
        }
        val tr = Trend.fit(weights)
        val span = tr?.spanDays ?: 0
        val missing = when {
            intake.size < MIN_INTAKE_DAYS -> t("еда записана %d из %d дней — нужно хотя бы %d", intake.size, WINDOW, MIN_INTAKE_DAYS)
            weights.size < MIN_WEIGHINS -> t("взвешиваний %d за 4 недели — нужно хотя бы %d, лучше каждое утро", weights.size, MIN_WEIGHINS)
            span < MIN_SPAN -> t("взвешивания охватывают %d дн. — нужно хотя бы %d", span, MIN_SPAN)
            model.isEmpty() -> t("укажите в профиле рост, год рождения и пол — нужен расчётный расход для сравнения")
            else -> null
        }
        val avgIn = intake.takeIf { it.isNotEmpty() }?.average()
        val tdee = if (avgIn != null && tr != null) avgIn - tr.perDay * KCAL_PER_KG else null
        return Result(intake.size, weights.size, span, avgIn, tr, tdee, model.takeIf { it.isNotEmpty() }?.average(), missing)
    }

    /** Предложить поправку, если она отличается от принятой хотя бы на [STEP] ккал. */
    fun suggestion(r: Result, current: Int): Int? = r.offset?.takeIf { abs(it - current) >= STEP }

    fun accept(prefs: Prefs, offset: Int, today: LocalDate = LocalDate.now()) {
        prefs.burnAdj = offset; prefs.burnAdjAt = if (offset == 0) null else today.toString()
        Log.i("Plan", "поправка расхода: $offset ккал")
    }
}

/** 5.56: прогноз к вехам по тренду веса (или безжировой массы) за 4 недели. */
object Forecast {
    /** Ориентир темпа, % веса в неделю: похудение −1…−0,5 %; набор массы и мышц +0,25…0,5 % (Iraki и др., 2019). */
    fun band(g: Goal): ClosedFloatingPointRange<Double>? = when (g) {
        Goal.LOSE -> -1.0..-0.5
        Goal.GAIN, Goal.MUSCLE -> 0.25..0.5
        else -> null
    }

    fun weightTrend(repo: Repo, today: LocalDate = LocalDate.now(), days: Int = 28): Trend? = trendOf(repo, today, days) { tmpl, d -> Anthro.weightOf(tmpl, d) }
    fun leanTrend(repo: Repo, today: LocalDate = LocalDate.now(), days: Int = 28): Trend? = trendOf(repo, today, days) { tmpl, d -> Anthro.leanOf(tmpl, d) }

    private fun trendOf(repo: Repo, today: LocalDate, days: Int, f: (List<TemplateItem>, Map<String, String>) -> Double?): Trend? {
        val tmpl = repo.template.value; val ent = repo.entries.value
        val pts = (0 until days).mapNotNull { i -> ent[today.minusDays(i.toLong()).key()]?.let { f(tmpl, it) }?.let { (days - 1 - i) to it } }
        return Trend.fit(pts)?.takeIf { it.n >= 6 && it.spanDays >= 14 }
    }

    data class Eta(val name: String, val target: Double, val date: LocalDate?)

    /** Когда дойдём до каждой непройденной вехи при нынешнем темпе; null-дата — темп не туда или дольше 5 лет. */
    fun etas(targets: List<Pair<String, Double>>, tr: Trend, today: LocalDate = LocalDate.now()): List<Eta> =
        targets.filter { (_, v) -> abs(v - tr.now) > 0.05 }.map { (name, v) ->
            val days = (v - tr.now) / tr.perDay
            Eta(name, v, if (tr.perDay != 0.0 && days > 0 && days < 5 * 365) today.plusDays(days.roundToInt().toLong()) else null)
        }

    /** Нужный темп (кг/нед) к своей дате и сколько ккал в день это меняет. */
    data class Need(val perWeek: Double, val kcalDelta: Int)

    fun need(target: Double, due: LocalDate, tr: Trend, today: LocalDate = LocalDate.now()): Need? {
        val weeks = ChronoUnit.DAYS.between(today, due) / 7.0
        if (weeks < 1) return null
        val per = (target - tr.now) / weeks
        val delta = (per - tr.perWeek) * Adapt.KCAL_PER_KG / 7
        return Need(per, (Math.round(delta / 10) * 10).toInt())
    }
}

/** 5.56: обзор недели (понедельник–воскресенье) и 1–2 правки на следующую. */
object WeekReview {
    data class Count(val done: Int, val plan: Int)

    data class Review(
        val start: LocalDate, val days: Int,
        val weight: Double?, val weightPrev: Double?, val trend: Trend?,
        val proteinOk: Int, val proteinLogged: Int, val protein: Double?,
        val kcal: Double?, val kcalTarget: Double?, val kcalDays: Int,
        val strength: Count, val cardio: Count, val yoga: Count,
        val active: Int?, val activeHr: Boolean, val activeGoal: Int,
        val sleep: Double?, val sleepNights: Int, val bedSpread: Int?,
        val fixes: List<String>,
    )

    /** Разброс отбоя (стандартное отклонение, мин) по началу ночного сна. */
    fun bedSpread(starts: List<Long>): Int? {
        if (starts.size < 3) return null
        val zone = java.time.ZoneId.systemDefault()
        // минуты от полудня: отбой в 23:30 и 0:30 — рядом
        val m = starts.map { java.time.Instant.ofEpochMilli(it).atZone(zone).let { z -> ((z.hour * 60 + z.minute) - 720 + 1440) % 1440 }.toDouble() }
        val avg = m.average()
        return sqrt(m.sumOf { (it - avg) * (it - avg) } / m.size).roundToInt()
    }

    fun of(repo: Repo, prefs: Prefs, start: LocalDate, rows: List<HealthHistory.Row>, today: LocalDate = LocalDate.now()): Review {
        val tmpl = repo.template.value; val ent = repo.entries.value
        val end = start.plusDays(6)
        val last = minOf(end, today)
        val days = (ChronoUnit.DAYS.between(start, last) + 1).toInt().coerceIn(0, 7)
        val dates = (0 until days).map { start.plusDays(it.toLong()) }
        fun day(d: LocalDate) = ent[d.key()] ?: emptyMap()
        fun avgW(ds: List<LocalDate>) = ds.mapNotNull { Anthro.weightOf(tmpl, day(it)) }.takeIf { it.isNotEmpty() }?.average()
        val goal = Goal.of(prefs.planGoal)
        val st = PlanStats.of(repo, prefs, last)
        val tg = goal?.let { Program.targets(it, st) }
        val meals = tmpl.filter { it.type == ItemType.MEALS }
        val proteins = dates.mapNotNull { d -> meals.flatMap { parseMeals(day(d)[it.id]) }.let { mealMacros(it)?.protein }?.takeIf { it > 0 } }
        val kcals = dates.mapNotNull { Anthro.eatenOf(tmpl, day(it))?.takeIf { k -> k > 300 } }
        val pTarget = tg?.protein
        val proteinOk = if (pTarget != null) proteins.count { it >= pTarget * 0.9 } else 0

        val tpl = WeekTpl.of(prefs); val sDays = WeekPlan.strength()
        val inWeek = rows.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }
        fun done(group: String, minMin: Int) = inWeek.filter { it.group == group && it.min >= minMin }.map { it.date }.distinct().size
        val strength = Count(done("strength", 0), WeekPlan.planned(tpl, WeekTpl.STRENGTH, sDays))
        val cardio = Count(done("cardio", 10), WeekPlan.planned(tpl, WeekTpl.CARDIO, sDays))
        val yoga = Count(done("yoga", 5), WeekPlan.planned(tpl, WeekTpl.YOGA, sDays))

        val hrActive = ActiveMin.weekTotal(ent, start)
        val active = hrActive ?: inWeek.filter { it.group == "cardio" }.sumOf { it.min }.takeIf { inWeek.any { r -> r.group == "cardio" } }

        val sleepItem = tmpl.firstOrNull { it.source == Source.HC_SLEEP }
        val sleeps = sleepItem?.let { si -> dates.mapNotNull { parseNumber(day(it)[si.id])?.takeIf { v -> v > 60 } } }.orEmpty()
        val starts = sleepItem?.let { si -> dates.mapNotNull { SleepDetail.parse(day(it)[SleepDetail.key(si.id)])?.start } }.orEmpty()
        val spread = bedSpread(starts)

        val trend = Forecast.weightTrend(repo, last)
        val r = Review(
            start, days, avgW(dates), avgW((1..7).map { start.minusDays(it.toLong()) }), trend,
            proteinOk, proteins.size, pTarget, kcals.takeIf { it.isNotEmpty() }?.average(), tg?.kcal, kcals.size,
            strength, cardio, yoga, active, hrActive != null, tpl.activeGoal,
            sleeps.takeIf { it.isNotEmpty() }?.average(), sleeps.size, spread, emptyList(),
        )
        return r.copy(fixes = fixes(r, goal, st.weight))
    }

    private fun n1(v: Double) = formatNumber(Math.round(v * 10) / 10.0)

    /** Правки на следующую неделю: по порядку важности, не больше двух. */
    fun fixes(r: Review, goal: Goal?, weight: Double?): List<String> {
        val out = mutableListOf<String>()
        val band = goal?.let { Forecast.band(it) }
        if (band != null && r.trend != null && weight != null && weight > 0) {
            val pct = r.trend.perWeek / weight * 100
            if (goal == Goal.LOSE && pct >= 0) out += t("Вес не снижается (+%s%% в неделю) — срежьте 100–200 ккал в день", n1(pct))
            else if (goal != Goal.LOSE && pct <= 0) out += t("Вес не растёт (%s%% в неделю) — добавьте 100–200 ккал в день", n1(pct))
            else if (pct < band.start) out += if (goal == Goal.LOSE) t("Вес уходит на %s%% в неделю — быстрее ориентира 0,5–1 %%. Добавьте 100–200 ккал, чтобы не терять мышцы", n1(-pct))
                else t("Вес растёт на %s%% в неделю — медленнее ориентира 0,25–0,5 %%. Добавьте 100–200 ккал в день", n1(pct))
            else if (pct > band.endInclusive) out += if (goal == Goal.LOSE) t("Вес уходит на %s%% в неделю — медленнее ориентира 0,5–1 %%. Срежьте 100–200 ккал в день", n1(-pct))
                else t("Вес растёт на %s%% в неделю — быстрее ориентира 0,25–0,5 %%: лишнее уходит в жир. Срежьте 100–200 ккал в день", n1(pct))
        }
        if (goal != null && Program.proteinMatters(goal) && r.protein != null && r.proteinLogged >= 3 && r.proteinOk < r.proteinLogged * 0.7)
            out += t("Белок в норме %d из %d дней — добавьте белок в завтрак или перекус (≈ %s г)", r.proteinOk, r.proteinLogged, formatNumber(DayPlan.proteinDose(weight)))
        if (r.days == 7 && r.strength.plan > 0 && r.strength.done < r.strength.plan)
            out += t("Силовых %d из %d по плану — если дни неудобные, поменяйте их в программе силовой", r.strength.done, r.strength.plan)
        if (r.days == 7 && r.active != null && r.active < r.activeGoal) {
            val miss = r.activeGoal - r.active
            out += t("Активных минут %d из %d — добавьте %d прогулки быстрым шагом по 25–30 мин", r.active, r.activeGoal, ((miss + 24) / 25).coerceIn(1, 6))
        }
        if (r.sleep != null && r.sleepNights >= 3 && r.sleep < 420)
            out += t("Сон в среднем %s — ложитесь на 30 мин раньше", formatDuration(r.sleep))
        if (r.bedSpread != null && r.bedSpread > 60)
            out += t("Отбой гуляет на ± %d мин — держите одно время ± 30 мин, в выходные тоже", r.bedSpread)
        return out.take(2)
    }
}
