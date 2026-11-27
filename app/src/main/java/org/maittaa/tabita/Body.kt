package org.maittaa.tabita

import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 5.58: «Тело» — замеры лентой, фото прогресса, состав тела с весов в одном месте.
 * Замеры — служебный ключ дня `body!tape` (JSON: ключ замера → см), фото — `body!ph` ({"f": спереди, "s": сбоку}).
 * Талия — по NICE NG246: посередине между нижним ребром и верхом тазовой кости, на спокойном выдохе;
 * отношение талии к росту: 0,4–0,49 — без повышенного риска, 0,5–0,59 — повышенный, от 0,6 — высокий.
 */
object BodyLog {
    const val TAPE = "body!tape"
    const val PHOTO = "body!ph"

    data class Measure(val key: String, val ru: String, val en: String, val howRu: String, val howEn: String) {
        val name get() = if (I18n.isEn) en else t(ru)
        val how get() = if (I18n.isEn) howEn else howRu
    }

    val measures = listOf(
        Measure("waist", "Талия", "Waist", "посередине между нижним ребром и верхом тазовой кости, на спокойном выдохе",
            "midway between the lowest rib and the top of the hip bone, after a normal breath out"),
        Measure("chest", "Грудь", "Chest", "по самым выступающим точкам, лента под мышками, на спокойном выдохе",
            "around the fullest part, tape under the armpits, after a normal breath out"),
        Measure("hips", "Ягодицы", "Hips", "по самой широкой части ягодиц, стопы вместе", "around the widest part of the buttocks, feet together"),
        Measure("biceps", "Плечо", "Arm", "по самой широкой части, рука согнута и напряжена — каждый раз одинаково",
            "around the widest part, arm bent and flexed — the same way every time"),
        Measure("thigh", "Бедро", "Thigh", "сразу под ягодичной складкой, стоя, вес на обеих ногах", "just below the gluteal fold, standing, weight on both feet"),
        Measure("neck", "Шея", "Neck", "сразу под кадыком", "just below the Adam's apple"),
    )
    fun measure(key: String) = measures.firstOrNull { it.key == key }

    fun parseTape(s: String?): Map<String, Double> = runCatching {
        val o = JSONObject(s ?: return emptyMap())
        o.keys().asSequence().mapNotNull { k -> o.optDouble(k).takeIf { !it.isNaN() && it > 0 }?.let { k to it } }.toMap()
    }.getOrDefault(emptyMap())

    fun tapeToString(m: Map<String, Double>): String? =
        m.filterValues { it > 0 }.takeIf { it.isNotEmpty() }?.let { JSONObject(it as Map<*, *>).toString() }

    /** Значение замера в день: записанное лентой, иначе — с весов (openScale передаёт и обхваты). */
    fun valueOn(tmpl: List<TemplateItem>, day: Map<String, String>, key: String): Double? =
        parseTape(day[TAPE])[key] ?: Anthro.bodyMetric(tmpl, day, key)?.takeIf { it > 0 }

    fun series(repo: Repo, key: String, days: Long = 365, today: LocalDate = LocalDate.now()): List<Pair<LocalDate, Double>> {
        val tmpl = repo.template.value
        val from = today.minusDays(days)
        return repo.entries.value.mapNotNull { (d, day) ->
            val date = runCatching { LocalDate.parse(d) }.getOrNull()?.takeIf { !it.isBefore(from) && !it.isAfter(today) } ?: return@mapNotNull null
            valueOn(tmpl, day, key)?.let { date to it }
        }.sortedBy { it.first }
    }

    /** Последнее значение и изменение к замеру не меньше [gapDays] дней назад (самому близкому к этой давности). */
    data class Change(val date: LocalDate, val value: Double, val delta: Double?, val since: LocalDate?)

    fun change(series: List<Pair<LocalDate, Double>>, gapDays: Long = 21): Change? {
        val last = series.lastOrNull() ?: return null
        val prev = series.lastOrNull { ChronoUnit.DAYS.between(it.first, last.first) >= gapDays }
        return Change(last.first, last.second, prev?.let { last.second - it.second }, prev?.first)
    }

    /** Отношение талии к росту и уровень по NICE: 0 — без повышенного риска, 1 — повышенный, 2 — высокий. */
    fun whtr(waistCm: Double, heightM: Double): Double = waistCm / (heightM * 100)
    fun whtrBand(r: Double) = when { r >= 0.6 -> 2; r >= 0.5 -> 1; else -> 0 }

    // ---------- фото прогресса ----------

    data class Shot(val front: String?, val side: String?) {
        val files get() = listOfNotNull(front, side)
        fun toJson(): String? = if (front == null && side == null) null else JSONObject().apply { front?.let { put("f", it) }; side?.let { put("s", it) } }.toString()
    }

    fun parseShot(s: String?): Shot? = runCatching {
        val o = JSONObject(s ?: return null)
        Shot(o.optString("f").ifBlank { null }, o.optString("s").ifBlank { null }).takeIf { it.front != null || it.side != null }
    }.getOrNull()

    fun shots(repo: Repo): List<Pair<LocalDate, Shot>> = repo.entries.value.mapNotNull { (d, day) ->
        parseShot(day[PHOTO])?.let { s -> runCatching { LocalDate.parse(d) }.getOrNull()?.let { it to s } }
    }.sortedBy { it.first }

    /** Поставить или убрать фото дня; старый файл удаляется. */
    fun setShot(repo: Repo, date: LocalDate, side: Boolean, file: String?) {
        val cur = parseShot(repo.entries.value[date.key()]?.get(PHOTO)) ?: Shot(null, null)
        val old = if (side) cur.side else cur.front
        if (old != null && old != file) File(repo.photosDir, old).delete()
        val n = if (side) cur.copy(side = file) else cur.copy(front = file)
        repo.setValue(date.key(), PHOTO, n.toJson())
        Log.i("Body", "фото ${if (side) "сбоку" else "спереди"} ${date}: ${file ?: "убрано"}")
    }

    // ---------- состав тела с весов ----------

    val scaleKeys = listOf("weight", "body_fat", "muscle", "lbm", "water", "visceral_fat")

    fun scaleSeries(repo: Repo, key: String, days: Long = 365, today: LocalDate = LocalDate.now()): List<Pair<LocalDate, Double>> {
        val tmpl = repo.template.value
        val from = today.minusDays(days)
        return repo.entries.value.mapNotNull { (d, day) ->
            val date = runCatching { LocalDate.parse(d) }.getOrNull()?.takeIf { !it.isBefore(from) && !it.isAfter(today) } ?: return@mapNotNull null
            (if (key == "weight") Anthro.weightOf(tmpl, day) else if (key == "lbm") Anthro.leanOf(tmpl, day) else Anthro.bodyMetric(tmpl, day, key))
                ?.takeIf { it > 0 }?.let { date to it }
        }.sortedBy { it.first }
    }
}
