package org.maittaa.tabita.train

import org.json.JSONArray
import org.json.JSONObject

/** 5.55: [rir] — запас повторов после подхода (Reserve: −1 не указан, 0, 1 — «1–2», 3 — «3+»). */
data class SetResult(val left: String = "", val right: String = "", val done: Boolean = false, val rir: Int = -1)

fun setKey(ex: Int, set: Int) = "$ex:$set"

/** Незаконченная тренировка — пишется на диск после каждого изменения. План хранится снимком. */
data class Draft(
    val plan: WorkoutPlan,
    val startedAt: Long,
    val step: Int = 0,
    /** Сделанные упражнения разминки (id). */
    val warmDone: Set<String> = emptySet(),
    val after: Set<Int> = emptySet(),
    val sets: Map<String, SetResult> = emptyMap(),
    val loads: Map<Int, String> = emptyMap(),
    val restSec: Int = plan.restSec,
    /** 1.7: пауза общего таймера — сколько уже простояло (мс) и с какого момента стоит сейчас (0 — идёт). */
    val pausedMs: Long = 0,
    val pausedSince: Long = 0,
    /** Пауза поставлена кнопкой (а не уходом из приложения) — при возвращении не снимается. */
    val pausedManual: Boolean = false,
) {
    val paused: Boolean get() = pausedSince > 0
    /** Чистое время тренировки без пауз, мс. */
    fun elapsedMs(now: Long = System.currentTimeMillis()): Long =
        if (startedAt <= 0) 0 else now - startedAt - pausedMs - (if (pausedSince > 0) now - pausedSince else 0)

    /** Хотя бы один подход сделан — план уже не пересобираем. */
    val started: Boolean get() = sets.values.any { it.done }
    /** Время тренировки идёт после «Вперёд» на подготовке. */
    val running: Boolean get() = startedAt > 0

    fun toJson(): JSONObject = JSONObject().apply {
        put("plan", plan.toJson()); put("startedAt", startedAt); put("step", step); put("rest", restSec)
        put("pausedMs", pausedMs); put("pausedSince", pausedSince); put("pausedManual", pausedManual)
        put("warmDone", JSONArray(warmDone.toList())); put("after", JSONArray(after.toList()))
        put("loads", JSONObject().apply { loads.forEach { (k, v) -> put(k.toString(), v) } })
        put("sets", JSONObject().apply {
            sets.forEach { (k, v) -> put(k, JSONObject().put("l", v.left).put("r", v.right).put("d", v.done).put("q", v.rir)) }
        })
    }

    companion object {
        fun fromJson(o: JSONObject): Draft {
            fun ints(k: String) = o.optJSONArray(k)?.let { a -> List(a.length()) { a.getInt(it) }.toSet() } ?: emptySet()
            val warm = o.optJSONArray("warmDone")?.let { a -> List(a.length()) { a.getString(it) }.toSet() } ?: emptySet()
            val loads = mutableMapOf<Int, String>()
            o.optJSONObject("loads")?.let { c -> c.keys().forEach { loads[it.toInt()] = c.getString(it) } }
            val sets = mutableMapOf<String, SetResult>()
            o.optJSONObject("sets")?.let { c ->
                c.keys().forEach { val s = c.getJSONObject(it); sets[it] = SetResult(s.optString("l"), s.optString("r"), s.optBoolean("d"), s.optInt("q", -1)) }
            }
            val plan = WorkoutPlan.fromJson(o.getJSONObject("plan"))
            return Draft(plan, o.getLong("startedAt"), o.optInt("step"), warm, ints("after"), sets, loads, o.optInt("rest", plan.restSec),
                o.optLong("pausedMs"), o.optLong("pausedSince"), o.optBoolean("pausedManual"))
        }
    }
}

data class ExResult(
    val exId: String,
    val name: String,
    val seconds: Boolean,
    val load: String?,
    val values: List<String>,
    val allTop: Boolean,
    /** 5.55: запас по подходам (как в [values]; −1 — не указан). */
    val rirs: List<Int> = emptyList(),
) {
    fun toJson() = JSONObject().put("id", exId).put("name", name).put("sec", seconds).put("load", load ?: "")
        .put("values", JSONArray(values)).put("allTop", allTop).apply { if (rirs.any { it >= 0 }) put("rir", JSONArray(rirs)) }

    companion object {
        fun fromJson(o: JSONObject) = ExResult(
            o.optString("id"), o.getString("name"), o.optBoolean("sec"), o.optString("load").ifBlank { null },
            o.getJSONArray("values").let { a -> List(a.length()) { a.getString(it) } }, o.optBoolean("allTop"),
            o.optJSONArray("rir")?.let { a -> List(a.length()) { a.getInt(it) } } ?: emptyList(),
        )
    }
}

data class Session(
    val id: String,
    val dayId: String,
    val title: String,
    val date: String,
    val startedAt: Long,
    val durationSec: Long,
    val week: String,
    val wellbeing: String,
    val notes: String,
    val exercises: List<ExResult>,
    val markdown: String,
    val fileName: String,
    val savedTo: String?,
    val intensity: String = "",
    val place: String = "",
    val hcSaved: Boolean = false,
) {
    fun toJson() = JSONObject().apply {
        put("id", id); put("day", dayId); put("title", title); put("date", date)
        put("startedAt", startedAt); put("durationSec", durationSec); put("week", week)
        put("wellbeing", wellbeing); put("notes", notes); put("markdown", markdown)
        put("fileName", fileName); put("savedTo", savedTo ?: ""); put("int", intensity); put("place", place); put("hc", hcSaved)
        put("exercises", JSONArray().apply { exercises.forEach { put(it.toJson()) } })
    }

    companion object {
        fun fromJson(o: JSONObject) = Session(
            o.getString("id"), o.optString("day", o.optString("templateId")), o.getString("title"), o.getString("date"),
            o.getLong("startedAt"), o.getLong("durationSec"), o.optString("week"), o.optString("wellbeing"),
            o.optString("notes"),
            o.getJSONArray("exercises").let { a -> List(a.length()) { ExResult.fromJson(a.getJSONObject(it)) } },
            o.optString("markdown"), o.optString("fileName"), o.optString("savedTo").ifBlank { null }, o.optString("int"), o.optString("place"), o.optBoolean("hc"),
        )
    }
}

fun leadingInt(s: String): Int? = Regex("""\d+""").find(s)?.value?.toIntOrNull()

object Results {
    fun value(r: SetResult, perSide: Boolean): String? =
        if (r.left.isBlank() && r.right.isBlank()) null
        else if (perSide) "${r.left.ifBlank { "–" }}/${r.right.ifBlank { "–" }}" else r.left

    fun summarize(d: Draft): List<ExResult> = d.plan.exercises.mapIndexed { i, ex ->
        val res = (0 until ex.sets).map { d.sets[setKey(i, it)] }
        val values = res.mapNotNull { r -> r?.let { value(it, ex.perSide) } }
        val allTop = res.all { r ->
            r != null && r.done && (leadingInt(r.left) ?: 0) >= ex.repMax && (!ex.perSide || (leadingInt(r.right) ?: 0) >= ex.repMax)
        }
        val rirs = res.mapNotNull { r -> r?.takeIf { value(it, ex.perSide) != null }?.rir }
        ExResult(ex.exId, ex.name, ex.seconds, d.loads[i]?.ifBlank { null }, values, allTop, rirs)
    }
}

/** Заметка (Markdown) в формате прежних шаблонов: frontmatter, правила, разминка, подходы галочками, заметки. */
object MarkdownExport {
    val afterItems = listOf("Записал результаты", "Отметил, что усложнить в следующий раз")

    fun build(
        d: Draft, date: String, weekday: String?, week: String, wellbeing: String, notes: String,
        readiness: Readiness?,
    ): String = buildString {
        val p = d.plan
        appendLine("---")
        appendLine("тип: тренировка")
        appendLine("день: ${weekday ?: ""}")
        appendLine("дата: $date")
        appendLine("неделя: $week")
        appendLine("самочувствие: $wellbeing")
        appendLine("интенсивность: ${p.intensity.title.lowercase()}")
        appendLine("место: ${if (p.place == Place.GYM) "зал" else "дом"}")
        appendLine("tags:")
        appendLine("  - тренировка")
        appendLine("---")
        appendLine()
        appendLine("# ${p.title}" + (weekday?.let { " — $it" } ?: ""))
        appendLine()
        appendLine("> [!info] Правила")
        appendLine("> Отдых между подходами ${d.restSec} с. Заканчивай подход, когда в запасе 3–4 повторения. Опускай вес за 2–3 секунды.")
        if (readiness != null && readiness.reasons.isNotEmpty()) {
            appendLine()
            appendLine("> [!note] Готовность по дневнику: ${readiness.headline.lowercase()}")
            readiness.reasons.forEach { appendLine("> - ${it.text}") }
        }
        if (p.notes.isNotEmpty()) {
            appendLine()
            appendLine("> [!tip] Поправки")
            p.notes.forEach { appendLine("> - $it") }
        }
        appendLine()
        if (p.warmups.isNotEmpty()) {
            appendLine("## Разминка")
            p.warmups.mapNotNull { Library.warmup(it) }.forEach { w ->
                appendLine("- [${if (w.id in d.warmDone) "x" else " "}] ${w.name} — ${w.amountLabel}")
            }
        }
        p.exercises.forEachIndexed { i, ex ->
            appendLine()
            appendLine("## ${ex.name} — ${ex.scheme}")
            val load = d.loads[i]?.takeIf { it.isNotBlank() }
            appendLine(ex.cue + (load?.let { " Нагрузка: $it." } ?: "") + (ex.note?.let { " ($it)" } ?: ""))
            val unit = if (ex.seconds) "с" else "повт."
            for (s in 0 until ex.sets) {
                val r = d.sets[setKey(i, s)] ?: SetResult()
                val mark = if (r.done) "x" else " "
                val v = if (ex.perSide) "${r.left.ifBlank { "___" }} / ${r.right.ifBlank { "___" }} $unit (левая / правая)"
                else "${r.left.ifBlank { "___" }} $unit"
                appendLine("- [$mark] Подход ${s + 1}: $v")
            }
        }
        appendLine()
        appendLine("## После тренировки")
        afterItems.forEachIndexed { i, t -> appendLine("- [${if (i in d.after) "x" else " "}] $t") }
        appendLine()
        appendLine("Заметки:")
        if (notes.isNotBlank()) appendLine(notes.trim())
    }
}
