package org.maittaa.tabita

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 5.65: быстрая отдача. Строки «что значат сегодняшние данные» на «Сегодня» — сравнение с вашей же
 * обычной нормой (медиана прошлых дней), а не с чужими нормами; и текст воскресного обзора недели.
 * Пороги: сон ±45 мин к медиане 14 ночей, пульс покоя +5 уд/мин к медиане 30 дней,
 * шаги вечером — на 30 % выше или на 40 % ниже медианы 14 дней.
 */
object DayInsight {
    private fun median(v: List<Double>): Double? = v.sorted().takeIf { it.isNotEmpty() }?.let { s ->
        if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    private fun dur(min: Double): String {
        val m = min.roundToInt()
        // неразрывные пробелы: «6 ч 34 мин» не переносится по частям
        return (if (m < 60) t("%d мин", m) else if (m % 60 == 0) t("%d ч", m / 60) else t("%d ч %02d мин", m / 60, m % 60)).replace(' ', '\u00A0')
    }

    /** Значения пункта за [n] дней до [date] (без самого дня). */
    private fun past(entries: Map<String, Map<String, String>>, id: String, date: LocalDate, n: Int, min: Double): List<Double> =
        (1..n).mapNotNull { parseNumber(entries[date.minusDays(it.toLong()).key()]?.get(id))?.takeIf { v -> v > min } }

    fun lines(
        template: List<TemplateItem>, entries: Map<String, Map<String, String>>, date: LocalDate,
        now: LocalDateTime = LocalDateTime.now(), finding: String? = null,
        /** Категории, которые уже говорят «Советы дня» (sleep, move): тревожные строки по ним не дублируем. */
        covered: Set<String> = emptySet(),
    ): List<String> {
        val day = entries[date.key()] ?: emptyMap()
        val out = mutableListOf<String>()
        fun item(s: Source) = template.firstOrNull { it.source == s && !it.hidden }
        // пульс покоя — первым: это самый ранний сигнал недосыпа, стресса или простуды
        if ("sleep" !in covered) item(Source.HC_RESTING_HR)?.let { it ->
            val v = parseNumber(day[it.id])?.takeIf { v -> v > 25 } ?: return@let
            val base = past(entries, it.id, date, 30, 25.0).takeIf { p -> p.size >= 7 }?.let(::median) ?: return@let
            val d = (v - base).roundToInt()
            if (d >= 5) out += t("Пульс покоя %d — на %d выше вашего обычного (%d). Так бывает при недосыпе, стрессе или начале простуды — нагрузку сегодня лучше облегчить.",
                v.roundToInt(), d, base.roundToInt())
        }
        item(Source.HC_SLEEP)?.let { it ->
            val v = parseNumber(day[it.id])?.takeIf { v -> v > 60 } ?: return@let
            val base = past(entries, it.id, date, 14, 60.0).takeIf { p -> p.size >= 5 }?.let(::median) ?: return@let
            val d = v - base
            if (d <= -45 && "sleep" !in covered) out += t("Сон %s — на %s меньше обычного (%s). Вечером лучше лечь пораньше, а не отсыпаться завтра утром.", dur(v), dur(-d), dur(base))
            else if (d >= 45) out += t("Сон %s — на %s больше обычного (%s).", dur(v), dur(d), dur(base))
        }
        // шаги — только вечером, когда день почти прожит
        if (date == now.toLocalDate() && now.hour >= 18 || date < now.toLocalDate()) item(Source.HC_STEPS)?.let { it ->
            val v = parseNumber(day[it.id])?.takeIf { v -> v > 0 } ?: return@let
            val base = past(entries, it.id, date, 14, 300.0).takeIf { p -> p.size >= 5 }?.let(::median) ?: return@let
            if (v >= base * 1.3) out += t("Шагов %s — заметно больше обычного (≈ %s).", formatNumber(v.roundToInt().toDouble()), formatNumber((base / 100).roundToInt() * 100.0))
            else if (v <= base * 0.6 && "move" !in covered) out += t("Шагов пока %s — обычно за день ≈ %s. Короткая прогулка ещё успеет.", formatNumber(v.roundToInt().toDouble()), formatNumber((base / 100).roundToInt() * 100.0))
        }
        if (out.size < 2 && finding != null) out += finding
        return out.take(2)
    }

    /** Что из сигналов уже покрывают видимые «Советы дня» с включёнными категориями. */
    fun coveredByTips(template: List<TemplateItem>, prefs: Prefs): Set<String> =
        if (template.none { it.type == ItemType.TIPS && !it.hidden }) emptySet() else setOf("sleep", "move") - prefs.tipsOff

    /** Надёжная находка из «Что влияет» одной фразой (для строки и уведомления). */
    fun factorLine(f: MonthData.Factor, index: Boolean = true): String? {
        if (!f.reliable || abs(f.effect) < (if (index) 5.0 else 0.5)) return null
        val v = if (index) formatNumber(abs(f.effect).roundToInt().toDouble()) else formatNumber(Math.round(abs(f.effect) * 10) / 10.0)
        return if (index) {
            if (f.effect > 0) t("Находка: в дни «%s» индекс в среднем на %s выше (%d дн. с, %d без).", f.title, v, f.nWith, f.nWithout)
            else t("Находка: в дни «%s» индекс в среднем на %s ниже (%d дн. с, %d без).", f.title, v, f.nWith, f.nWithout)
        } else {
            if (f.effect > 0) t("Находка: в дни «%s» оценка дня в среднем на %s выше (%d дн. с, %d без).", f.title, v, f.nWith, f.nWithout)
            else t("Находка: в дни «%s» оценка дня в среднем на %s ниже (%d дн. с, %d без).", f.title, v, f.nWith, f.nWithout)
        }
    }

    /** Главная надёжная находка: этот месяц, а в его начале — прошлый. */
    fun topFactor(template: List<TemplateItem>, entries: Map<String, Map<String, String>>, cfg: MonthCfg, date: LocalDate): Pair<MonthData.Factor, Boolean>? {
        for (m in listOf(date, date.minusMonths(1))) {
            val d = MonthData(template, entries, m, cfg, today = date)
            val f = d.factors.firstOrNull { factorLine(it, d.outcomeIsIndex) != null }
            if (f != null) return f to d.outcomeIsIndex
        }
        return null
    }

    /**
     * Находка для строки на «Сегодня»: новая (другая, чем в прошлый раз) показывается весь день,
     * потом не повторяется, пока не сменится.
     */
    fun freshFinding(prefs: Prefs, template: List<TemplateItem>, entries: Map<String, Map<String, String>>, today: LocalDate): String? {
        val (f, idx) = topFactor(template, entries, prefs.monthCfg, today) ?: return null
        val seen = prefs.findingSeen
        if (seen.substringBeforeLast('|') == f.title && seen.substringAfterLast('|') != today.key()) return null
        prefs.findingSeen = f.title + "|" + today.key()
        return factorLine(f, idx)
    }

    // ---------- воскресный обзор недели ----------

    /** Текст уведомления; null — за неделю почти ничего не записано, молчим. */
    fun weekText(r: WeekReview.Review, finding: String?): String? {
        val parts = mutableListOf<String>()
        if (r.sleep != null && r.sleepNights >= 3) parts += t("сон в среднем %s", dur(r.sleep))
        if (r.active != null && r.active > 0) parts += t("активных минут %d из %d", r.active, r.activeGoal)
        if (r.weight != null && r.weightPrev != null) {
            val d = Math.round((r.weight - r.weightPrev) * 10) / 10.0
            parts += if (abs(d) < 0.05) t("вес без изменений") else t("вес %s кг за неделю", (if (d > 0) "+" else "−") + formatNumber(abs(d)))
        }
        if (r.protein != null && r.proteinLogged >= 3) parts += t("белок в норме %d из %d дн.", r.proteinOk, r.proteinLogged)
        if (parts.isEmpty() && finding == null) return null
        val sb = StringBuilder()
        if (parts.isNotEmpty()) sb.append(parts.joinToString(", ").replaceFirstChar { it.uppercase() }).append('.')
        r.fixes.firstOrNull()?.let { sb.append(if (sb.isEmpty()) "" else "\n").append(t("На неделю: %s.", it.replaceFirstChar { c -> c.lowercase() })) }
        finding?.let { sb.append(if (sb.isEmpty()) "" else "\n").append(it) }
        return sb.toString()
    }

    /** Ближайшее воскресенье 19:00 после [now]. */
    fun nextSunday(now: LocalDateTime): LocalDateTime {
        var d = now.toLocalDate()
        while (d.dayOfWeek != DayOfWeek.SUNDAY) d = d.plusDays(1)
        var at = d.atTime(19, 0)
        if (!at.isAfter(now)) at = at.plusWeeks(1)
        return at
    }
}
