package org.maittaa.tabita

import org.json.JSONObject
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.round

/**
 * Профиль (5.19): рост, год рождения, пол. Статичные данные — не пункт чек-листа, а раздел настроек.
 * Хранится в data.json (ключ "profile"), чтобы попадать в резервную копию и в расчёты тренировок.
 */
data class Profile(val heightCm: Double? = null, val birthYear: Int? = null, val sex: String? = null) {
    /** Рост в метрах, если задан и правдоподобен. */
    val h: Double? get() = heightCm?.takeIf { it in 100.0..250.0 }?.div(100)
    val male get() = sex == "m"
    val female get() = sex == "f"
    val empty get() = heightCm == null && birthYear == null && sex == null

    fun age(on: LocalDate = LocalDate.now()): Int? = birthYear?.let { on.year - it }?.takeIf { it in 10..110 }

    fun toJson(): JSONObject = JSONObject().apply {
        heightCm?.let { put("h", it) }
        birthYear?.let { put("by", it) }
        sex?.let { put("sex", it) }
    }

    companion object {
        fun fromJson(o: JSONObject?): Profile = if (o == null) Profile() else Profile(
            o.optDouble("h").takeIf { !it.isNaN() },
            o.optInt("by", 0).takeIf { it > 0 },
            o.optString("sex").takeIf { it == "m" || it == "f" },
        )
    }
}

/** Расчёты, которым нужен рост. Всё — оценки по общепринятым формулам. */
object Anthro {
    fun bmi(w: Double, h: Double) = w / (h * h)
    /** Индекс безжировой массы: безжировая масса / рост². */
    fun ffmi(lbm: Double, h: Double) = lbm / (h * h)
    /** FFMI с поправкой на рост (Kouri, 1995): сравним у высоких и низких. */
    fun ffmiNorm(lbm: Double, h: Double) = ffmi(lbm, h) + 6.1 * (1.8 - h)
    /** Индекс жировой массы: жировая масса / рост². */
    fun fmi(fatKg: Double, h: Double) = fatKg / (h * h)

    /** Границы нормального ИМТ (18,5–25) в килограммах для роста. */
    fun normalWeight(h: Double) = 18.5 * h * h to 25.0 * h * h

    /** Базовый обмен по Миффлину – Сан Жеору, ккал/день. Пол не задан — среднее между формулами. */
    fun bmr(p: Profile, w: Double, on: LocalDate = LocalDate.now()): Double? {
        val h = p.h ?: return null
        val age = p.age(on) ?: return null
        val s = when { p.male -> 5.0; p.female -> -161.0; else -> -78.0 }
        return 10 * w + 625 * h - 5 * age + s
    }

    /** Длина шага ≈ 0,415 роста у мужчин, 0,413 у женщин. */
    fun strideM(p: Profile): Double? = p.h?.let { it * if (p.female) 0.413 else 0.415 }
    fun stepsKm(p: Profile, steps: Double): Double? = strideM(p)?.let { steps * it / 1000 }

    /** Нетто-затраты на нагрузку, MET. Ходьба, бег и походы — 0: их считают шаги. */
    private val met = mapOf(
        "strength" to 5.0, "hiit" to 8.0, "bike" to 7.0, "swim" to 7.0, "cardio" to 6.0, "yoga" to 2.5,
        "stretch" to 2.3, "dance" to 5.0, "sport" to 7.0, "martial" to 8.0, "winter" to 7.0, "other" to 4.0,
        // 5.51: Compendium 2024 — аэробика низкой ударности, умеренно 4,8 (02005); высокой ударности, интенсивно 8,0 (02006)
        "aerobics" to 4.8,
    )

    /** MET сессии: у аэробики, отмеченной «интенсивно», — как у высокоударной. */
    fun metOf(s: WorkoutSession): Double = if (s.kind == "aerobics" && s.effort >= 3) 8.0 else met[s.kind] ?: 0.0

    /** Шаги, которые уже входят в «быт» (коэффициент 1,2 к базовому обмену). */
    const val BASE_STEPS = 3000.0

    data class Energy(val bmr: Double, val base: Double, val steps: Double, val workouts: Double) {
        val total get() = base + steps + workouts
    }

    /**
     * Расход за день: базовый обмен × 1,2 (быт и переваривание пищи) + ходьба сверх 3000 шагов
     * (0,5 ккал на кг на км) + тренировки ((MET − 1) × вес × часы). null — нет роста, возраста или веса.
     */
    fun energy(p: Profile, w: Double, on: LocalDate, steps: Double?, sessions: List<WorkoutSession>): Energy? {
        val b = bmr(p, w, on) ?: return null
        val km = stepsKm(p, ((steps ?: 0.0) - BASE_STEPS).coerceAtLeast(0.0)) ?: 0.0
        val ex = sessions.sumOf { s -> (metOf(s) - 1).coerceAtLeast(0.0) * w * s.min / 60 }
        return Energy(b, b * 1.2, 0.5 * w * km, ex)
    }

    // ---------- данные дня ----------

    fun bodyMetric(tmpl: List<TemplateItem>, day: Map<String, String>, key: String): Double? =
        tmpl.filter { it.type == ItemType.BODY }.firstNotNullOfOrNull { b ->
            BodyDay.parse(day[b.id])?.metrics?.firstOrNull { BodyCatalog.key(it.id) == key }?.value
        }

    /** Вес, измеренный в этот день (весы, Health Connect или вручную). */
    fun weightOf(tmpl: List<TemplateItem>, day: Map<String, String>): Double? =
        bodyMetric(tmpl, day, "weight") ?: tmpl.filter { it.type == ItemType.NUMBER && (it.source == Source.HC_WEIGHT || it.id == "weight") }
            .firstNotNullOfOrNull { parseNumber(day[it.id]) }

    /** Безжировая масса: из весов (lbm) или вес × (1 − жир %). */
    fun leanOf(tmpl: List<TemplateItem>, day: Map<String, String>): Double? {
        bodyMetric(tmpl, day, "lbm")?.let { return it }
        val w = weightOf(tmpl, day) ?: return null
        val fat = bodyMetric(tmpl, day, "body_fat")?.takeIf { it in 2.0..70.0 } ?: return null
        return w * (1 - fat / 100)
    }

    /** Последний известный вес на дату (до 60 дней назад). */
    fun weightOn(tmpl: List<TemplateItem>, entries: Map<String, Map<String, String>>, date: LocalDate): Double? {
        for (i in 0..60) {
            val d = entries[date.minusDays(i.toLong()).toString()] ?: continue
            weightOf(tmpl, d)?.let { return it }
        }
        return null
    }

    fun stepsOf(tmpl: List<TemplateItem>, day: Map<String, String>): Double? =
        tmpl.filter { it.source == Source.HC_STEPS }.firstNotNullOfOrNull { parseNumber(day[it.id]) }

    fun sessionsOf(tmpl: List<TemplateItem>, day: Map<String, String>): List<WorkoutSession> =
        tmpl.filter { it.source == Source.HC_EXERCISE }.flatMap { Workouts.sessions(day, it.id) }

    fun eatenOf(tmpl: List<TemplateItem>, day: Map<String, String>): Double? =
        tmpl.filter { it.type == ItemType.MEALS }.mapNotNull { mealKcal(parseMeals(day[it.id])) }.takeIf { it.isNotEmpty() }?.sum()

    /** Расход за день по записям дневника. */
    fun energyOn(p: Profile, tmpl: List<TemplateItem>, entries: Map<String, Map<String, String>>, date: LocalDate): Energy? {
        val w = weightOn(tmpl, entries, date) ?: return null
        val day = entries[date.toString()] ?: emptyMap()
        return energy(p, w, date, stepsOf(tmpl, day), sessionsOf(tmpl, day))
    }

    fun kcal(v: Double) = formatNumber(round(v)) + " " + t("ккал")
    /** Для графика: без «+», минус — знаком «−» (оси и «тренд» сами добавляют знаки). */
    fun balKcal(v: Double) = (if (v < 0) "−" else "") + formatNumber(round(abs(v))) + " " + t("ккал")
    fun signedKcal(v: Double) = (if (v < 0) "−" else if (v > 0) "+" else "") + formatNumber(round(abs(v))) + " " + t("ккал")
}
