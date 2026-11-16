package org.maittaa.tabita

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Гликемический индекс (ГИ) и нагрузка (ГН).
 * Пороги: ГИ ≤55 низкий, 56–69 средний, ≥70 высокий; ГН приёма ≤10 низкая, 11–19 средняя, ≥20 высокая;
 * ГН за день <80 низкая, 80–120 средняя, >120 высокая.
 */
object Glycemic {
    /** Уровень: 0 — низкий, 1 — средний, 2 — высокий. */
    fun giLevel(gi: Double) = when { gi <= 55 -> 0; gi < 70 -> 1; else -> 2 }
    fun mealGlLevel(gl: Double) = when { gl <= 10 -> 0; gl < 20 -> 1; else -> 2 }
    fun dayGlLevel(gl: Double) = when { gl < 80 -> 0; gl <= 120 -> 1; else -> 2 }

    fun levelName(l: Int) = when (l) { 0 -> t("низкий"); 1 -> t("средний"); else -> t("высокий") }
    fun loadName(l: Int) = when (l) { 0 -> t("низкая"); 1 -> t("средняя"); else -> t("высокая") }

    data class Day(
        /** Суммарная нагрузка за день по приёмам с известной ГН. */
        val load: Double,
        /** Средний ГИ, взвешенный по углеводам (или простое среднее, если углеводы не указаны). */
        val avgGi: Double?,
        /** Самый «тяжёлый» приём пищи. */
        val peak: Meal?,
        val score: Int,
        /** Приёмов с углеводами, но без ГИ — оценка по ним неполная. */
        val unknown: Int,
        val counted: Int,
    ) {
        val band get() = scoreBand(score)
    }

    /** Итоговая гликемическая оценка дня (0–100, больше — ровнее сахар) или null, если ГИ нигде не указан. */
    fun day(all: List<Meal>): Day? {
        // сцепленные карточки — один приём: общий пик
        val meals = mealUnits(all).map { combineMeals(it) }
        val known = meals.filter { it.glEff != null || it.gi != null }
        if (known.isEmpty()) return null
        val load = known.sumOf { it.glEff ?: 0.0 }
        val withCarbs = known.filter { it.gi != null && (it.carbs ?: 0.0) > 0 }
        val avgGi = when {
            withCarbs.isNotEmpty() -> withCarbs.sumOf { it.gi!! * it.carbs!! } / withCarbs.sumOf { it.carbs!! }
            known.any { it.gi != null } -> known.mapNotNull { it.gi }.average()
            else -> null
        }
        val peak = known.filter { it.glEff != null }.maxByOrNull { it.glEff!! }
        val unknown = all.count { it.glEff == null && it.gi == null && ((it.carbs ?: 0.0) > 5 || (it.carbs == null && it.kcal != null)) }
        return Day(load, avgGi, peak, score(load, avgGi, known.mapNotNull { it.glEff }), unknown, known.size)
    }

    /**
     * Штрафы от 100: дневная нагрузка выше 80 (до −40 при 160), средний ГИ выше 55 (до −30 при 85),
     * «пики» — приёмы с ГН больше 20 (до −15 за каждый, всего до −30).
     */
    fun score(load: Double, avgGi: Double?, mealLoads: List<Double>): Int {
        val pLoad = ((load - 80) / 80 * 40).coerceIn(0.0, 40.0)
        val pGi = avgGi?.let { ((it - 55) / 30 * 30).coerceIn(0.0, 30.0) } ?: 0.0
        val pPeaks = mealLoads.sumOf { ((it - 20) * 0.75).coerceIn(0.0, 15.0) }.coerceAtMost(30.0)
        return Math.round(100 - pLoad - pGi - pPeaks).toInt().coerceIn(0, 100)
    }

    /** 0 — отлично, 1 — хорошо, 2 — средне, 3 — высокая нагрузка. */
    fun scoreBand(s: Int) = when { s >= 80 -> 0; s >= 60 -> 1; s >= 40 -> 2; else -> 3 }
    fun bandName(b: Int) = when (b) { 0 -> t("ровный сахар"); 1 -> t("хорошо"); 2 -> t("средне"); else -> t("высокая нагрузка") }
}

/** Цвет уровня: зелёный / жёлтый / оранжевый / красный. */
fun glyColor(level: Int): Color = when (level) {
    0 -> Color(0xFF3FA66B); 1 -> Color(0xFFD9A400); 2 -> Color(0xFFE07B39); else -> Color(0xFFD64545)
}

/** Плашка «ГИ 55 · ГН 12» для приёма пищи. */
@Composable
fun GlyBadge(meal: Meal) {
    val gl = meal.glEff
    if (meal.gi == null && gl == null) return
    val lvl = gl?.let { Glycemic.mealGlLevel(it) } ?: Glycemic.giLevel(meal.gi!!)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(glyColor(lvl)))
        Spacer(Modifier.width(4.dp))
        Text(
            listOfNotNull(meal.gi?.let { t("ГИ %s", formatNumber(Math.round(it).toDouble())) },
                gl?.let { t("ГН %s", formatNumber(Math.round(it).toDouble())) }).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Сводка дня в блоке «Питание». */
@Composable
fun GlycemicDaySummary(meals: List<Meal>) {
    val d = Glycemic.day(meals)
    var info by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .clickable { info = true }.padding(10.dp),
    ) {
        if (d == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("Гликемическая оценка"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.HelpOutline, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
            }
            Text(
                t("Появится, когда у приёмов пищи будет указан ГИ: посчитайте ИИ или впишите вручную."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).background(glyColor(d.band)), contentAlignment = Alignment.Center) {
                    Text("${d.score}", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(t("Гликемическая оценка: %s", Glycemic.bandName(d.band)), style = MaterialTheme.typography.labelLarge)
                    Text(
                        t("нагрузка %s (%s)", formatNumber(Math.round(d.load).toDouble()), Glycemic.loadName(Glycemic.dayGlLevel(d.load))) +
                            (d.avgGi?.let { " · " + t("ср. ГИ %s", formatNumber(Math.round(it).toDouble())) } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    d.peak?.glEff?.takeIf { it >= 20 }?.let {
                        Text(t("пик: %s, ГН %s", t(d.peak.kind).lowercase(), formatNumber(Math.round(it).toDouble())),
                            style = MaterialTheme.typography.bodySmall, color = glyColor(2))
                    }
                    if (d.unknown > 0) Text(t("без ГИ: %d приём(а) — оценка неполная", d.unknown),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
                Icon(Icons.AutoMirrored.Filled.HelpOutline, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
    if (info) GlycemicInfoDialog { info = false }
}

@Composable
fun GlycemicInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Гликемический индекс и нагрузка")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t("ГИ — насколько быстро продукт поднимает сахар в крови (глюкоза = 100). Низкий — до 55, средний — 56–69, высокий — от 70."),
                    style = MaterialTheme.typography.bodyMedium)
                Text(t("ГН учитывает и количество углеводов: ГН = ГИ × углеводы (г) / 100. За приём: до 10 — низкая, 11–19 — средняя, от 20 — высокая. За день: до 80 — низкая, 80–120 — средняя, больше 120 — высокая."),
                    style = MaterialTheme.typography.bodyMedium)
                Text(t("Оценка дня — 100 баллов минус штрафы: за дневную нагрузку выше 80, за средний ГИ выше 55 и за приёмы с ГН больше 20. 80+ — ровный сахар, 60–79 — хорошо, 40–59 — средне, ниже 40 — высокая нагрузка."),
                    style = MaterialTheme.typography.bodyMedium)
                Text(t("ГИ блюда приблизительный: его меняют способ готовки, жир, белок и клетчатка. Это ориентир для питания, а не медицинский показатель."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("Понятно")) } },
    )
}
