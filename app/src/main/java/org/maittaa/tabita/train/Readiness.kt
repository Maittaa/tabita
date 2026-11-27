package org.maittaa.tabita.train

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

// ——— данные из Tabita ———

data class TDay(
    val date: LocalDate,
    val sleepMin: Double? = null,
    val rhr: Double? = null,
    val steps: Double? = null,
    val exMin: Double? = null,
    val weight: Double? = null,
    val rate: Double? = null,
    val fat: Double? = null,
    val muscle: Double? = null,
    val bmi: Double? = null,
    val rateMin: Double = 1.0,
    val rateMax: Double = 10.0,
    /** 1.3: питание из Tabita — съедено, оценка расхода (ккал), белок (г); FFMI. */
    val kcal: Double? = null,
    val tdee: Double? = null,
    val protein: Double? = null,
    val ffmi: Double? = null,
)

/**
 * Какие данные брать из Tabita (1.3). Выключенное Sink не читает: поля обнуляются до любых расчётов.
 */
object TabitaUse {
    val all = listOf(
        "sleep" to "Сон и пульс покоя",
        "activity" to "Шаги и активность",
        "body" to "Вес и состав тела (жир, мышцы, FFMI)",
        "food" to "Питание: калории, белок, расход",
        "rate" to "Оценка дня",
        "checkup" to "Чекап самочувствия",
        "profile" to "Профиль: рост, возраст, пол",
    )
    val default = all.map { it.first }.toSet()

    fun apply(d: TabitaData, on: Set<String>): TabitaData = d.copy(
        days = d.days.map { x ->
            x.copy(
                sleepMin = x.sleepMin.takeIf { "sleep" in on }, rhr = x.rhr.takeIf { "sleep" in on },
                steps = x.steps.takeIf { "activity" in on }, exMin = x.exMin.takeIf { "activity" in on },
                weight = x.weight.takeIf { "body" in on }, fat = x.fat.takeIf { "body" in on }, muscle = x.muscle.takeIf { "body" in on },
                bmi = x.bmi.takeIf { "body" in on }, ffmi = x.ffmi.takeIf { "body" in on },
                kcal = x.kcal.takeIf { "food" in on }, tdee = x.tdee.takeIf { "food" in on }, protein = x.protein.takeIf { "food" in on },
                rate = x.rate.takeIf { "rate" in on },
            )
        },
        checkup = d.checkup.takeIf { "checkup" in on },
        profile = d.profile.takeIf { "profile" in on },
    )
}

data class TBlock(val id: String, val title: String, val goodness: Double)
data class TCheckup(val date: LocalDate, val overall: Double?, val blocks: List<TBlock>)
/** Профиль из Tabita 5.19+: рост, возраст, пол ("m"/"f"). Поля пустые, если в Tabita не заполнены. */
data class TProfile(val heightCm: Double?, val age: Int?, val sex: String?)
data class TabitaData(val days: List<TDay>, val checkup: TCheckup?, val loadedAt: Long = System.currentTimeMillis(), val profile: TProfile? = null)

enum class TabitaStatus(val title: String) {
    OK("Подключено"),
    NOT_INSTALLED("Tabita не установлена или старой версии"),
    NO_PERMISSION("Нет доступа к данным Tabita"),
    ERROR("Ошибка чтения"),
}

/** Что делать, если Tabita недоступна (1.2.1): показывается под статусом. */
fun tabitaHelp(st: TabitaStatus?): String? = when (st) {
    TabitaStatus.ERROR -> "Не удалось прочитать дневник. Обновится при следующем открытии вкладки."
    else -> null
}

object TabitaClient {
    const val PACKAGE = "org.maittaa.tabita"
    val URI: Uri by lazy { Uri.parse("content://org.maittaa.tabita.training/summary") }

    /** 5.23: тренировки внутри Tabita — данные дневника читаются напрямую, без межприложенческого запроса. */
    fun load(ctx: Context, days: Int = 120): Pair<TabitaStatus, TabitaData?> = try {
        val f = java.io.File(ctx.filesDir, "data.json")
        if (!f.exists()) TabitaStatus.OK to TabitaData(emptyList(), null)
        else TabitaStatus.OK to parse(org.maittaa.tabita.TrainingProvider.build(f.readText(), days, LocalDate.now()))
    } catch (e: Exception) {
        SLog.e("Tabita", "чтение дневника", e)
        TabitaStatus.ERROR to null
    }

    fun parse(json: String): TabitaData {
        val o = JSONObject(json)
        val a = o.getJSONArray("days")
        fun JSONObject.num(k: String): Double? = if (has(k) && !isNull(k)) optDouble(k).takeIf { !it.isNaN() } else null
        val days = List(a.length()) { i ->
            val d = a.getJSONObject(i)
            TDay(
                LocalDate.parse(d.getString("d")), d.num("sleep"), d.num("rhr"), d.num("steps"), d.num("ex"),
                d.num("w"), d.num("rate"), rateMin = d.num("rmin") ?: 1.0, rateMax = d.num("rmax") ?: 10.0,
                fat = d.num("fat"), muscle = d.num("muscle"), bmi = d.num("bmi"),
                kcal = d.num("kcal"), tdee = d.num("tdee"), protein = d.num("p"), ffmi = d.num("ffmi"),
            )
        }.sortedBy { it.date }
        val ch = o.optJSONObject("checkup")?.let { c ->
            val b = c.optJSONArray("blocks")
            TCheckup(
                LocalDate.parse(c.getString("d")), c.num("overall"),
                if (b == null) emptyList() else List(b.length()) { i ->
                    b.getJSONObject(i).let { TBlock(it.getString("id"), it.optString("t"), it.optDouble("g")) }
                },
            )
        }
        val prof = o.optJSONObject("profile")?.let { p ->
            TProfile(p.num("h"), p.num("age")?.toInt(), p.optString("sex").takeIf { it == "m" || it == "f" })
        }
        return TabitaData(days, ch, profile = prof)
    }
}

// ——— оценка готовности ———

enum class Tone { GOOD, NEUTRAL, BAD }
/** [short] — коротко для сводки (1.8.1): «Сон 3:49», «Пульс 60 ↑». */
data class Reason(val text: String, val tone: Tone, val short: String? = null)

data class Readiness(
    val intensity: Intensity,
    val reasons: List<Reason>,
    val legsLight: Boolean,
    val holdProgression: Boolean,
    /** 1.3: совет по белку после тренировки (несколько дней подряд меньше нормы). */
    val proteinTip: String? = null,
    /** 1.3: пульс покоя повышен 3 дня подряд при тренировках — предложить разгрузочную неделю. */
    val deloadSuggested: Boolean = false,
) {
    val headline: String
        get() = when (intensity) {
            Intensity.NORMAL -> "Готов к обычной тренировке"
            Intensity.LIGHT -> "Лучше облегчённая тренировка"
            Intensity.RECOVERY -> "Организму нужно восстановление"
        }
}

fun hm(m: Double): String { val x = m.roundToInt(); return "${x / 60}:${"%02d".format(x % 60)}" }
fun fmtMin(m: Double): String { val x = m.roundToInt(); return "${x / 60} ч ${"%02d".format(x % 60)} мин" }
private fun fmt1(v: Double) = "%.1f".format(v).replace('.', ',')
private fun median(l: List<Double>): Double? = l.sorted().let { if (it.isEmpty()) null else if (it.size % 2 == 1) it[it.size / 2] else (it[it.size / 2 - 1] + it[it.size / 2]) / 2 }

object ReadinessCalc {
    /** Вес: наклон линейной регрессии, кг в неделю, по последним 21 дню (нужно ≥ 4 точки на ≥ 10 дней). */
    fun weightTrend(days: List<TDay>, today: LocalDate): Double? {
        val pts = days.filter { it.weight != null && !it.date.isAfter(today) && ChronoUnit.DAYS.between(it.date, today) <= 21 }
            .map { ChronoUnit.DAYS.between(today, it.date).toDouble() to it.weight!! }
        if (pts.size < 4 || pts.maxOf { it.first } - pts.minOf { it.first } < 10) return null
        val mx = pts.sumOf { it.first } / pts.size
        val my = pts.sumOf { it.second } / pts.size
        val num = pts.sumOf { (x, y) -> (x - mx) * (y - my) }
        val den = pts.sumOf { (x, _) -> (x - mx) * (x - mx) }
        return if (den == 0.0) null else num / den * 7
    }

    /** Норма белка для набора и силы, г на кг веса. */
    const val PROTEIN_PER_KG = 1.6

    /**
     * [sessions7] — тренировок за последние 7 дней (для разгрузки), [hour] — час сейчас (для перекуса),
     * [bodyWeight] — вес, если в данных Tabita его нет (введён вручную).
     */
    fun evaluate(
        data: TabitaData, today: LocalDate, goal: Goal,
        sessions7: Int = 0, hour: Int = java.time.LocalTime.now().hour, bodyWeight: Double? = null,
        /** 5.59: отметки самочувствия за сегодня. */
        marks: List<org.maittaa.tabita.DayMarks.Mark> = emptyList(),
    ): Readiness {
        val reasons = mutableListOf<Reason>()
        var score = 0
        val byDate = data.days.associateBy { it.date }
        val yesterday = today.minusDays(1)
        fun prev(days: Int, f: (TDay) -> Double?, before: LocalDate) =
            data.days.filter { it.date.isBefore(before) && ChronoUnit.DAYS.between(it.date, before) <= days }.mapNotNull(f)

        // сон этой ночи (в Tabita он записан на день пробуждения)
        val sleep = byDate[today]?.sleepMin
        if (sleep != null) {
            val base = median(prev(14, { it.sleepMin }, today))
            when {
                // 1.8: меньше 5 ч — сила, реакция и риск травмы заметно хуже: тренировку лучше перенести
                sleep < 300 -> { score -= 3; reasons += Reason("Сон ${fmtMin(sleep)} — меньше 5 часов", Tone.BAD, "Сон ${hm(sleep)}") }
                sleep < 360 -> { score -= 2; reasons += Reason("Сон ${fmtMin(sleep)} — меньше 6 часов", Tone.BAD, "Сон ${hm(sleep)}") }
                base != null && sleep < base - 60 -> { score -= 1; reasons += Reason("Сон ${fmtMin(sleep)} — на час меньше обычного (${fmtMin(base)})", Tone.BAD, "Сон ${hm(sleep)}") }
                else -> reasons += Reason("Сон ${fmtMin(sleep)}", Tone.GOOD, "Сон ${hm(sleep)}")
            }
        } else reasons += Reason("Сон за эту ночь ещё не записан", Tone.NEUTRAL, "Сон —")

        // пульс покоя: сегодня или вчера против медианы 14 дней
        val rhrDay = listOf(today, yesterday).firstNotNullOfOrNull { d -> byDate[d]?.rhr?.let { d to it } }
        if (rhrDay != null) {
            val (d, rhr) = rhrDay
            val base = prev(14, { it.rhr }, d).takeIf { it.size >= 4 }?.let(::median)
            val diff = base?.let { rhr - it }
            when {
                diff == null -> reasons += Reason("Пульс покоя ${rhr.roundToInt()} — мало данных для сравнения", Tone.NEUTRAL, "Пульс ${rhr.roundToInt()}")
                diff >= 7 -> { score -= 2; reasons += Reason("Пульс покоя ${rhr.roundToInt()} — на ${diff.roundToInt()} выше обычного (${base.roundToInt()})", Tone.BAD, "Пульс ${rhr.roundToInt()} ↑") }
                diff >= 4 -> { score -= 1; reasons += Reason("Пульс покоя ${rhr.roundToInt()} — немного выше обычного (${base.roundToInt()})", Tone.BAD, "Пульс ${rhr.roundToInt()} ↑") }
                else -> reasons += Reason("Пульс покоя ${rhr.roundToInt()} — как обычно", Tone.GOOD, "Пульс ${rhr.roundToInt()}")
            }
        }

        // оценка дня: сегодня, если уже поставлена, иначе вчера
        val rateDay = listOf(today, yesterday).firstNotNullOfOrNull { d -> byDate[d]?.takeIf { it.rate != null } }
        if (rateDay != null) {
            val r = rateDay.rate!!
            val frac = if (rateDay.rateMax > rateDay.rateMin) (r - rateDay.rateMin) / (rateDay.rateMax - rateDay.rateMin) else 0.5
            val label = "Оценка дня${if (rateDay.date == yesterday) " вчера" else ""} ${r.roundToInt()}/${rateDay.rateMax.roundToInt()}"
            val sh = "Оценка ${r.roundToInt()}/${rateDay.rateMax.roundToInt()}"
            if (frac <= 0.3) { score -= 1; reasons += Reason(label, Tone.BAD, sh) } else reasons += Reason(label, if (frac >= 0.6) Tone.GOOD else Tone.NEUTRAL, sh)
        }

        // чекап самочувствия за 14 дней
        data.checkup?.takeIf { ChronoUnit.DAYS.between(it.date, today) in 0..14 }?.let { c ->
            val o = c.overall
            val worst = c.blocks.minByOrNull { it.goodness }
            if (o != null && o < 40) {
                score -= 1
                reasons += Reason("Чекап: общая оценка ${o.roundToInt()}/100" + (worst?.let { ", хуже всего — ${it.title.lowercase()}" } ?: ""), Tone.BAD, "Чекап ${o.roundToInt()}")
            } else if (o != null) reasons += Reason("Чекап: общая оценка ${o.roundToInt()}/100", if (o >= 60) Tone.GOOD else Tone.NEUTRAL, "Чекап ${o.roundToInt()}")
        }

        // активность накануне — бережём ноги
        val yd = byDate[yesterday]
        val steps = yd?.steps
        val ex = yd?.exMin
        val legsLight = (steps != null && steps >= 15000) || (ex != null && ex >= 60)
        if (legsLight) reasons += Reason(
            "Вчера " + listOfNotNull(steps?.takeIf { it >= 15000 }?.let { "${it.roundToInt()} шагов" }, ex?.takeIf { it >= 60 }?.let { "${it.roundToInt()} мин активности" }).joinToString(" и ") + " — ногам полегче",
            Tone.NEUTRAL, "Ногам полегче",
        ) else if (steps != null) reasons += Reason("Вчера ${steps.roundToInt()} шагов", Tone.NEUTRAL, "Шаги ${fmt1(steps / 1000)} тыс.")

        // вес
        var hold = false
        weightTrend(data.days, today)?.let { t ->
            val s = (if (t >= 0) "+" else "−") + fmt1(kotlin.math.abs(t)) + " кг/нед"
            when {
                goal == Goal.MASS && t < -0.2 -> { hold = true; reasons += Reason("Вес снижается ($s) — для набора нужно больше еды; прибавку повторов пока придержим", Tone.BAD, "Вес $s") }
                goal == Goal.MASS && t > 0.75 -> reasons += Reason("Вес растёт быстро ($s) — проверь, не слишком ли много калорий", Tone.NEUTRAL, "Вес $s")
                goal == Goal.MASS && t >= 0.1 -> reasons += Reason("Вес растёт ($s) — хорошо для набора", Tone.GOOD, "Вес $s")
                goal == Goal.MASS -> reasons += Reason("Вес стоит на месте ($s) — для набора массы добавь еды", Tone.NEUTRAL, "Вес $s")
                else -> reasons += Reason("Вес: $s", Tone.NEUTRAL, "Вес $s")
            }
        }

        // ——— питание (1.3) ———
        // энергобаланс за неделю: съедено − расход по дням, где есть оба числа
        val bal = (1..7).mapNotNull { i -> byDate[today.minusDays(i.toLong())]?.let { d -> if (d.kcal != null && d.tdee != null) d.kcal - d.tdee else null } }
        if (bal.size >= 3) {
            val avg = bal.average()
            val s = (if (avg >= 0) "+" else "−") + kotlin.math.abs(avg).roundToInt() + " ккал/день"
            when {
                goal == Goal.MASS && avg < -300 -> { hold = true; reasons += Reason("Неделя в дефиците ($s) — мышцам не из чего расти: добавь ~300 ккал в день. Прибавку пока придержим", Tone.BAD, "Баланс $s") }
                avg > 500 -> reasons += Reason("Неделя с профицитом ($s) — для мышц хватает +200–400, лишнее уйдёт в жир", Tone.NEUTRAL, "Баланс $s")
                goal == Goal.MASS && avg >= 100 -> reasons += Reason("Энергобаланс за неделю $s — хорошо для набора", Tone.GOOD, "Баланс $s")
                goal == Goal.MASS -> reasons += Reason("Энергобаланс за неделю $s — для набора нужно +200–400", Tone.NEUTRAL, "Баланс $s")
                else -> reasons += Reason("Энергобаланс за неделю $s", Tone.NEUTRAL, "Баланс $s")
            }
        }
        // белок: последние 3 дня (без сегодняшнего), норма от веса
        val w = data.days.lastOrNull { it.weight != null && !it.date.isAfter(today) }?.weight ?: bodyWeight
        var proteinTip: String? = null
        if (w != null) {
            val p = (1..3).mapNotNull { i -> byDate[today.minusDays(i.toLong())]?.protein }
            val norm = w * PROTEIN_PER_KG
            if (p.size >= 2 && p.all { it < norm }) {
                val avg = p.average()
                reasons += Reason("Белок последние дни ~${avg.roundToInt()} г при норме от ${norm.roundToInt()} г (${fmt1(PROTEIN_PER_KG)} г/кг)", Tone.BAD, "Белок ${avg.roundToInt()}/${norm.roundToInt()} г")
                proteinTip = "Белка последние дни ~${avg.roundToInt()} г в день, а для роста мышц нужно от ${norm.roundToInt()} г. " +
                    "В ближайшие 1–2 часа съешь 25–40 г белка: творог 200 г — 34 г, куриная грудка 150 г — 45 г, 3 яйца — 19 г, " +
                    "банка тунца — 25 г, порция протеина — 20–25 г."
            }
        }
        // перекус перед тренировкой во второй половине дня
        val logs = (1..7).count { byDate[today.minusDays(it.toLong())]?.kcal != null }
        val eaten = byDate[today]?.kcal ?: if (logs >= 3) 0.0 else null
        if (hour >= 13 && eaten != null && eaten < 500) reasons += Reason(
            (if (eaten > 0) "Сегодня съедено ${eaten.roundToInt()} ккал" else "Сегодня еда ещё не записана") +
                " — перекуси за 1–1,5 ч до тренировки: банан и йогурт, бутерброд с сыром или творог с фруктами",
            Tone.NEUTRAL, "Перекус",
        )

        // ——— разгрузка (1.3): пульс покоя повышен 3 дня подряд при регулярных тренировках ———
        val last3 = (0..2).map { byDate[today.minusDays(it.toLong())]?.rhr }
        val base28 = data.days.filter { it.date.isBefore(today.minusDays(2)) && ChronoUnit.DAYS.between(it.date, today) <= 30 }.mapNotNull { it.rhr }
            .takeIf { it.size >= 7 }?.let(::median)
        val deload = base28 != null && sessions7 >= 2 && last3.all { it != null && it >= base28 + 4 }
        if (deload) reasons += Reason("Пульс покоя выше обычного три дня подряд при ${sessions7} тренировках за неделю — похоже на накопленную усталость. Стоит взять разгрузочную неделю", Tone.BAD, "Усталость")

        var intensity = when {
            score <= -3 -> Intensity.RECOVERY
            score <= -2 -> Intensity.LIGHT
            else -> Intensity.NORMAL
        }
        // 5.59: отметки самочувствия — «правило шеи» (Mayo Clinic): ниже шеи, температура, ломота — отдых; выше шеи — легче
        val rest = marks.filter { it.effect == org.maittaa.tabita.DayMarks.Effect.REST }
        val easy = marks.filter { it.effect == org.maittaa.tabita.DayMarks.Effect.EASY }
        if (rest.isNotEmpty()) {
            intensity = Intensity.RECOVERY
            reasons.add(0, Reason("Отмечено: ${rest.joinToString(", ") { it.ru.lowercase() }} — сегодня день восстановления, тренировку лучше отложить", Tone.BAD, rest.first().ru))
        } else if (easy.isNotEmpty()) {
            if (intensity == Intensity.NORMAL) intensity = Intensity.LIGHT
            reasons.add(0, Reason("Отмечено: ${easy.joinToString(", ") { it.ru.lowercase() }} — сегодня легче, по самочувствию", Tone.BAD, easy.first().ru))
        }
        marks.filter { it.good }.takeIf { it.isNotEmpty() }?.let { g ->
            reasons += Reason("Отмечено: ${g.joinToString(", ") { it.ru.lowercase() }}", Tone.GOOD, g.first().ru)
        }
        return Readiness(intensity, reasons, legsLight, hold, proteinTip, deload)
    }
}
