package org.maittaa.tabita

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Сводка дневника по дням для тренировок (вкладка «Тренировки», train.TabitaClient):
 * {"v":1,"days":[{"d","sleep","rhr","steps","ex","w","fat","muscle","bmi","rate","rmin","rmax","ffmi","kcal","tdee","p","f","c"}],
 *  "profile":{"h","age","sex"},"checkup":{"d","overall","blocks":[{"id","t","g"}]}}
 * 5.25: раньше отдавалась отдельному приложению через ContentProvider; после слияния — обычная функция.
 */
object TrainingProvider {
    fun build(text: String, days: Int, today: LocalDate): String {
        val o = JSONObject(text)
        val t = o.getJSONArray("template")
        val tmpl = (0 until t.length()).map { TemplateItem.fromJson(t.getJSONObject(it)) }
        fun bySource(s: Source) = tmpl.filter { it.source == s }.map { it.id }
        val sleepIds = bySource(Source.HC_SLEEP)
        val rhrIds = bySource(Source.HC_RESTING_HR)
        val stepIds = bySource(Source.HC_STEPS)
        val exIds = bySource(Source.HC_EXERCISE)
        val bodyIds = tmpl.filter { it.type == ItemType.BODY }.map { it.id }
        val weightIds = tmpl.filter { it.type == ItemType.NUMBER && (it.source == Source.HC_WEIGHT || it.id == "weight") }.map { it.id }
        val rate = tmpl.firstOrNull { it.id == "rate" && it.type == ItemType.SCALE } ?: tmpl.firstOrNull { it.type == ItemType.SCALE }

        val entries = o.optJSONObject("entries") ?: JSONObject()
        val prof = Profile.fromJson(o.optJSONObject("profile"))
        // для расчёта расхода нужен вес на дату — берём записи как карту
        val all: Map<String, Map<String, String>> by lazy {
            entries.keys().asSequence().associateWith { d -> entries.getJSONObject(d).let { j -> j.keys().asSequence().associateWith { j.getString(it) } } }
        }
        val from = today.minusDays(days.toLong() - 1)
        val out = JSONArray()
        for (k in entries.keys()) {
            val date = runCatching { LocalDate.parse(k) }.getOrNull() ?: continue
            if (date.isBefore(from) || date.isAfter(today)) continue
            val day = entries.getJSONObject(k)
            fun num(ids: List<String>) = ids.firstNotNullOfOrNull { id -> parseNumber(day.optString(id).ifEmpty { null }) }
            val body = bodyIds.firstNotNullOfOrNull { id -> BodyDay.parse(day.optString(id).ifEmpty { null }) }
            fun bm(key: String) = body?.metrics?.firstOrNull { BodyCatalog.key(it.id) == key }?.value
            val weight = bm("weight") ?: num(weightIds)
            val r = JSONObject().put("d", k)
            num(sleepIds)?.let { r.put("sleep", it) }
            num(rhrIds)?.let { r.put("rhr", it) }
            num(stepIds)?.let { r.put("steps", it) }
            // 5.25: тренировочные минуты с учётом тяжести (ходьба не считается); без разбивки по видам — как записано
            val sess = exIds.flatMap { id -> Workouts.sessions(all[k] ?: emptyMap(), id) }
            (if (sess.isNotEmpty()) Workouts.loadMin(sess) else num(exIds))?.let { r.put("ex", it) }
            weight?.let { r.put("w", it) }
            bm("body_fat")?.let { r.put("fat", it) }
            bm("muscle")?.let { r.put("muscle", it) }
            (weight?.let { w -> prof.h?.let { Anthro.bmi(w, it) } } ?: bm("bmi"))?.let { r.put("bmi", Math.round(it * 10) / 10.0) }
            val dm = all[k] ?: emptyMap()
            // съедено и БЖУ — без профиля тоже (5.20.1)
            Anthro.eatenOf(tmpl, dm)?.let { r.put("kcal", Math.round(it)) }
            tmpl.filter { it.type == ItemType.MEALS }.flatMap { parseMeals(dm[it.id]) }.let(::mealMacros)?.let { m ->
                r.put("p", Math.round(m.protein)).put("f", Math.round(m.fat)).put("c", Math.round(m.carbs))
            }
            prof.h?.let { h ->
                Anthro.leanOf(tmpl, dm)?.let { r.put("ffmi", Math.round(Anthro.ffmi(it, h) * 10) / 10.0) }
                Anthro.energyOn(prof, tmpl, all, date)?.let { r.put("tdee", Math.round(it.total)) }
            }
            rate?.let { ri -> parseNumber(day.optString(ri.id).ifEmpty { null })?.let { r.put("rate", it).put("rmin", ri.min).put("rmax", ri.max) } }
            if (r.length() > 1) out.put(r)
        }
        val res = JSONObject().put("v", 1).put("days", out)
        if (!prof.empty) res.put("profile", JSONObject().apply {
            prof.heightCm?.let { put("h", it) }
            prof.age(today)?.let { put("age", it) }
            prof.sex?.let { put("sex", it) }
        })
        val ch = o.optJSONArray("checkups")
        if (ch != null && ch.length() > 0) {
            val last = (0 until ch.length()).mapNotNull { runCatching { Checkup.fromJson(ch.getJSONObject(it)) }.getOrNull() }
                .maxByOrNull { it.at }
            if (last != null) {
                val blocks = JSONArray()
                last.results.forEach { (id, br) ->
                    Wellbeing.block(id)?.let { b -> blocks.put(JSONObject().put("id", id).put("t", b.title.ru).put("g", Wellbeing.goodness(b, br.score))) }
                }
                val c = JSONObject().put("d", last.date).put("blocks", blocks)
                Wellbeing.overall(last.results)?.let { c.put("overall", it) }
                res.put("checkup", c)
            }
        }
        return res.toString()
    }
}
