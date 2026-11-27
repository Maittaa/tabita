package org.maittaa.tabita

/**
 * Оценка полезности продукта (5.27), 0–10. Упрощённый профиль по мотивам Nutri-Score и рекомендаций ВОЗ:
 * белок на 100 ккал, гликемическая нагрузка на 100 ккал, калорийность на 100 г, а если известны (свои продукты
 * с этикетки) — клетчатка, сахара и насыщенные жиры. Неизвестные показатели не учитываются, а не считаются плохими.
 */
object FoodScore {
    /** Показатель: название, значение для показа, доля от лучшего 0..1 (null — нет данных), вес. */
    data class Part(val name: String, val value: String, val v: Double?, val w: Double)

    private fun lin(x: Double, good: Double, bad: Double) =
        if (good > bad) ((x - bad) / (good - bad)).coerceIn(0.0, 1.0) else ((bad - x) / (bad - good)).coerceIn(0.0, 1.0)

    private fun n1(v: Double) = formatNumber(Math.round(v * 10) / 10.0)

    fun parts(f: Food): List<Part>? {
        if (f.kcal < 15) return null
        val per = 100 / f.kcal
        val p100 = f.p * per
        val carbShare = f.c * per
        val gl100 = when {
            f.gi != null -> f.gi * f.c / 100 * per
            carbShare < 5 -> 0.0
            else -> null
        }
        val u = if (f.ml) t("мл") else t("г")
        return listOf(
            Part(t("Белок на 100 ккал"), n1(p100) + " " + t("г"), lin(p100, 20.0, 0.0), 3.0),
            Part(t("ГН на 100 ккал"), gl100?.let { n1(it) } ?: t("ГИ неизвестен"), gl100?.let { lin(it, 3.0, 15.0) }, 2.0),
            Part(t("Калорийность"), t("%s ккал на 100 %s", formatNumber(Math.round(f.kcal).toDouble()), u), lin(f.kcal, 100.0, 400.0), 1.0),
            Part(t("Клетчатка на 100 ккал"), f.fib?.let { n1(it * per) + " " + t("г") } ?: t("нет данных"), f.fib?.let { lin(it * per, 3.0, 0.0) }, 2.0),
            Part(t("Сахара"), f.sug?.let { t("%d%% калорий", Math.round(it * 4 * per).toInt()) } ?: t("нет данных"), f.sug?.let { lin(it * 4 * per, 5.0, 25.0) }, 1.5),
            Part(t("Насыщенные жиры"), f.sat?.let { t("%d%% калорий", Math.round(it * 9 * per).toInt()) } ?: t("нет данных"), f.sat?.let { lin(it * 9 * per, 5.0, 12.0) }, 1.5),
        )
    }

    /** Оценка 0–10; null — почти без калорий (вода, чай, специи). */
    fun score(f: Food): Int? {
        val ps = parts(f)?.filter { it.v != null } ?: return null
        val w = ps.sumOf { it.w }
        return if (w <= 0) null else Math.round(10 * ps.sumOf { it.v!! * it.w } / w).toInt()
    }

    /** Уровень для цвета: 0 — хорошо (7–10), 1 — средне (4–6), 2 — слабо. */
    fun level(s: Int) = when { s >= 7 -> 0; s >= 4 -> 1; else -> 2 }
}
