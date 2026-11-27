package org.maittaa.tabita.train

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/**
 * 5.42: что за веса есть на самом деле — чтобы подсказывать рабочий вес из доступного.
 * Гантели: фиксированные (список весов одной гантели, парами) или разборные (гриф + блины).
 * Рюкзак: шаг (одна книга/бутылка) и максимум. Зал: шаг гантельного ряда, штанги, тренажёров.
 */
data class Inventory(
    val dbAdjustable: Boolean = false,
    /** Фиксированные гантели: вес одной, кг (есть парами). */
    val dbFixed: List<Double> = emptyList(),
    /** Разборные: вес грифа одной гантели. */
    val dbHandle: Double = 2.0,
    /** Разборные: блины — вес → сколько штук всего (на обе гантели). */
    val dbPlates: List<Pair<Double, Int>> = emptyList(),
    val bpStep: Double = 1.0,
    val bpMax: Double = 10.0,
    val gymDbStep: Double = 2.0,
    val gymBarStep: Double = 2.5,
    val gymMachineStep: Double = 5.0,
) {
    fun toJson(): String = JSONObject()
        .put("adj", dbAdjustable)
        .put("fixed", JSONArray(dbFixed))
        .put("handle", dbHandle)
        .put("plates", JSONArray(dbPlates.map { JSONObject().put("w", it.first).put("n", it.second) }))
        .put("bpStep", bpStep).put("bpMax", bpMax)
        .put("gDb", gymDbStep).put("gBar", gymBarStep).put("gM", gymMachineStep)
        .toString()

    /** Веса одной гантели, которые можно собрать (обе гантели одинаковые, блины симметрично). */
    fun dumbbells(): List<Double> {
        if (!dbAdjustable) return dbFixed.filter { it > 0 }.distinct().sorted()
        var sums = setOf(0.0)
        // на одну гантель — четверть блинов (2 гантели × 2 стороны), с каждой стороны одинаково
        dbPlates.forEach { (w, n) ->
            val perSide = n / 4
            sums = sums.flatMap { s -> (0..perSide).map { k -> r2(s + 2 * k * w) } }.toSet()
        }
        return sums.map { r2(dbHandle + it) }.distinct().sorted()
    }

    fun backpack(): List<Double> {
        if (bpStep <= 0 || bpMax <= 0) return emptyList()
        val n = (bpMax / bpStep + 1e-6).toInt()
        return (0..n).map { r2(it * bpStep) }
    }

    companion object {
        fun r2(v: Double) = Math.round(v * 100) / 100.0
        fun fromJson(s: String?): Inventory = runCatching {
            val o = JSONObject(s ?: return Inventory())
            val f = o.optJSONArray("fixed") ?: JSONArray()
            val p = o.optJSONArray("plates") ?: JSONArray()
            Inventory(
                o.optBoolean("adj"),
                (0 until f.length()).map { f.getDouble(it) },
                o.optDouble("handle", 2.0),
                (0 until p.length()).map { p.getJSONObject(it).let { j -> j.getDouble("w") to j.getInt("n") } },
                o.optDouble("bpStep", 1.0), o.optDouble("bpMax", 10.0),
                o.optDouble("gDb", 2.0), o.optDouble("gBar", 2.5), o.optDouble("gM", 5.0),
            )
        }.getOrDefault(Inventory())
    }
}

/**
 * Подбор рабочего веса. Двойная прогрессия: сначала повторы до верхней границы во всех подходах,
 * потом следующий доступный вес и снова от нижней границы. Облегчённая тренировка — ~10 % легче,
 * восстановительная — на шаг легче. Не добрал до нижней границы — вес пересчитывается по формуле Эпли
 * (1ПМ ≈ вес × (1 + повторы / 30)) на нижнюю границу.
 */
object LoadPlan {
    data class Pick(val kg: Double?, val text: String?, val note: String?, val resetReps: Boolean = false)

    fun options(kind: LoadKind, place: Place, inv: Inventory): List<Double> = when (kind) {
        LoadKind.DUMBBELLS -> if (place == Place.GYM) steps(inv.gymDbStep, inv.gymDbStep, 50.0) else inv.dumbbells()
        LoadKind.BACKPACK -> inv.backpack()
        LoadKind.BARBELL -> if (place == Place.GYM) steps(20.0, inv.gymBarStep, 200.0) else emptyList()
        LoadKind.MACHINE -> if (place == Place.GYM) steps(inv.gymMachineStep, inv.gymMachineStep, 200.0) else emptyList()
        LoadKind.NONE -> emptyList()
    }

    private fun steps(from: Double, step: Double, to: Double): List<Double> {
        if (step <= 0) return emptyList()
        val out = mutableListOf<Double>(); var v = from
        while (v <= to + 1e-6) { out += Inventory.r2(v); v += step }
        return out
    }

    fun kg(v: Double): String = (if (v % 1.0 == 0.0) v.toInt().toString() else Inventory.r2(v).toString().replace('.', ',')) + " кг"

    fun format(kind: LoadKind, v: Double): String = when (kind) {
        LoadKind.DUMBBELLS -> "2 × " + kg(v)
        LoadKind.BACKPACK -> if (v == 0.0) "пустой рюкзак" else "рюкзак " + kg(v)
        else -> kg(v)
    }

    /** Килограммы из записи нагрузки: «2 × 4,2 кг» → 4,2; «рюкзак 6 кг» → 6. Старые «2 книги» → null. */
    fun parseKg(s: String?): Double? {
        if (s == null || !s.contains("кг")) return if (s?.contains("пустой") == true) 0.0 else null
        val nums = Regex("""\d+(?:[.,]\d+)?""").findAll(s).map { it.value.replace(',', '.').toDouble() }.toList()
        if (nums.isEmpty()) return null
        // «2 × 4,2 кг» — берём число перед «кг»
        return nums.last()
    }

    fun snapDown(opts: List<Double>, v: Double) = opts.lastOrNull { it <= v + 1e-6 } ?: opts.firstOrNull()
    fun nearest(opts: List<Double>, v: Double) = opts.minByOrNull { abs(it - v) }

    fun epley1rm(w: Double, reps: Int) = w * (1 + reps / 30.0)
    fun epleyFor(oneRm: Double, reps: Int) = oneRm / (1 + reps / 30.0)

    fun pick(
        kind: LoadKind, opts: List<Double>, lastLoad: String?, lastReps: Int?, allTop: Boolean,
        repMin: Int, seconds: Boolean, intensity: Intensity, hold: Boolean, startKg: Double?,
        /** 5.54: верхняя граница — чтобы объяснить, почему вес не меняется, когда до верха дошёл только первый подход. */
        repMax: Int = Int.MAX_VALUE,
    ): Pick {
        if (kind == LoadKind.NONE || opts.isEmpty()) return Pick(null, null, null)
        val last = parseKg(lastLoad)
        fun p(v: Double, note: String?, reset: Boolean = false) = Pick(v, format(kind, v), note, reset)
        if (last == null) {
            val v = startKg?.let { snapDown(opts, it) } ?: opts.first { kind != LoadKind.BACKPACK || it > 0 || opts.size == 1 }
            return p(v, if (startKg != null) "Старт ~${kg(startKg)} от веса тела — оставь в запасе 3–4 повтора" else "Начни с самого лёгкого и посмотри, как пойдёт")
        }
        val cur = nearest(opts, last) ?: last
        val lighter = opts.lastOrNull { it < cur - 1e-6 }
        val heavier = opts.firstOrNull { it > cur + 1e-6 }
        return when {
            intensity == Intensity.RECOVERY -> p(lighter ?: cur, "Восстановительная — легче обычного")
            intensity == Intensity.LIGHT -> p(snapDown(opts, cur * 0.9)?.takeIf { it < cur } ?: lighter ?: cur, "Облегчённая — примерно на 10 % легче")
            !seconds && lastReps != null && lastReps < repMin -> {
                val target = epleyFor(epley1rm(cur, lastReps), repMin)
                val v = snapDown(opts, target) ?: cur
                p(v, if (v < cur) "В прошлый раз $lastReps повт. — тяжело. ${kg(v)} даст ~$repMin повт." else "В прошлый раз $lastReps повт. — легче вариантов нет, делай сколько выходит")
            }
            allTop && !hold && heavier != null -> {
                val pct = ((heavier / cur - 1) * 100).toInt()
                p(heavier, "Все подходы на верхней границе → ${kg(heavier)}, повторы снова с нижней" +
                    if (pct >= 20) ". Прибавка большая (+$pct %) — если не идёт, первые подходы на ${kg(cur)}" else "", reset = true)
            }
            allTop && !hold -> p(cur, "Тяжелее нет — усложняй: опускай 3 с, пауза внизу, или вариант потяжелее")
            allTop -> p(cur, "Прибавку придерживаем — сегодня без утяжеления")
            // 5.54: до верха дошёл первый подход, но не все — вес прежний, и это нужно объяснить
            !seconds && lastReps != null && lastReps >= repMax && heavier != null && !hold ->
                p(cur, "Первый подход дошёл до верхней границы, остальные ещё нет — вес тот же. Когда все подходы будут на $repMax, предложу ${kg(heavier)}")
            else -> p(cur, null)
        }
    }
}
