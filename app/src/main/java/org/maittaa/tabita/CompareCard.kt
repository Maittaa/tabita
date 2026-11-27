package org.maittaa.tabita

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import kotlin.math.abs
import kotlin.math.sqrt

/** 5.63: ряд для сравнения — из карточек «Графиков». */
data class CmpSeries(val key: String, val title: String, val values: List<Double?>, val fmt: (Double) -> String)

object Compare {
    fun series(cards: List<StatCard>): List<CmpSeries> = cards.flatMap { c ->
        when (c) {
            is SeriesCard -> listOf(CmpSeries(c.key, c.title, c.values, c.fmt))
            is BodyCard -> c.metrics.map { m -> CmpSeries(c.key + "_" + m.id, m.title, m.values, m.fmt) }
            is SleepCard -> c.metrics.filter { it.id in setOf("dur", "score", "deep", "rem", "bed", "wake") }.map { m ->
                CmpSeries(c.key + "_" + m.id, if (m.id == "dur") c.title else c.title + ": " + m.title, m.values, m.fmt)
            }
            else -> emptyList()
        }
    }.filter { s -> s.values.count { it != null } >= 2 }.distinctBy { it.key }

    /** Коэффициент корреляции Пирсона по дням, где есть оба значения; null — меньше [minN] пар. */
    fun pearson(a: List<Double?>, b: List<Double?>, minN: Int = 7): Pair<Double, Int>? {
        val p = a.indices.mapNotNull { i -> val x = a[i]; val y = b.getOrNull(i); if (x != null && y != null) x to y else null }
        if (p.size < minN) return null
        val mx = p.sumOf { it.first } / p.size; val my = p.sumOf { it.second } / p.size
        val sxy = p.sumOf { (it.first - mx) * (it.second - my) }
        val sx = sqrt(p.sumOf { (it.first - mx) * (it.first - mx) }); val sy = sqrt(p.sumOf { (it.second - my) * (it.second - my) })
        if (sx == 0.0 || sy == 0.0) return null
        return sxy / (sx * sy) to p.size
    }

    /** Сила связи по модулю r (Коэн, 1988): слабая от 0,1, средняя от 0,3, сильная от 0,5. */
    fun strength(r: Double): String = when {
        abs(r) >= 0.5 -> t("сильная")
        abs(r) >= 0.3 -> t("средняя")
        abs(r) >= 0.1 -> t("слабая")
        else -> t("нет связи")
    }
}

@Composable
private fun Picker(label: String, list: List<CmpSeries>, sel: String?, allowNone: Boolean, color: Color, tag: String, onPick: (String?) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({ menu = true }, Modifier.testTag(tag), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color)); Spacer(Modifier.width(6.dp))
            Text(list.firstOrNull { it.key == sel }?.title ?: label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(18.dp))
        }
        DropdownMenu(menu, { menu = false }) {
            if (allowNone) DropdownMenuItem({ Text(t("— без второго")) }, { onPick(null); menu = false })
            list.forEach { s -> DropdownMenuItem({ Text(s.title) }, { onPick(s.key); menu = false }) }
        }
    }
}

/** Наложение двух показателей (у каждого своя шкала) и прошлого периода; связь по дням. */
@Composable
fun CompareCard(prefs: Prefs, cur: List<CmpSeries>, prev: List<CmpSeries>, prevLabel: String) {
    val cs = MaterialTheme.colorScheme
    if (cur.isEmpty()) return
    var a by remember { mutableStateOf(prefs.cmpA?.takeIf { k -> cur.any { it.key == k } } ?: cur.first().key) }
    var b by remember { mutableStateOf(prefs.cmpB?.takeIf { k -> cur.any { it.key == k } }) }
    var showPrev by remember { mutableStateOf(prefs.cmpPrev) }
    val sa = cur.first { it.key == a }; val sb = cur.firstOrNull { it.key == b }
    val pa = prev.firstOrNull { it.key == a }
    val ca = cs.primary; val cb = cs.tertiary; val cp = cs.outline
    Card(Modifier.fillMaxWidth().testTag("cmp_card"), colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CompareArrows, null, Modifier.size(20.dp), tint = cs.primary); Spacer(Modifier.width(8.dp))
                Text(t("Сравнить"), style = MaterialTheme.typography.titleMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.weight(1f)) { Picker(t("Показатель"), cur, a, false, ca, "cmp_a") { k -> a = k!!; prefs.cmpA = k } }
                Box(Modifier.weight(1f)) { Picker(t("+ второй"), cur.filter { it.key != a }, b, true, cb, "cmp_b") { k -> b = k; prefs.cmpB = k } }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(cp)); Spacer(Modifier.width(6.dp))
                Text(t("%s: %s", t("Прошлый период"), prevLabel), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Switch(showPrev, { showPrev = it; prefs.cmpPrev = it }, Modifier.testTag("cmp_prev"))
            }
            DualChart(sa.values, sb?.values, if (showPrev) pa?.values else null, ca, cb, cp)
            // средние
            fun avg(v: List<Double?>?) = v?.filterNotNull()?.takeIf { it.isNotEmpty() }?.average()
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                avg(sa.values)?.let { Stat(sa.title, sa.fmt(it), ca) }
                sb?.let { s -> avg(s.values)?.let { Stat(s.title, s.fmt(it), cb) } }
                if (showPrev) avg(pa?.values)?.let { p -> avg(sa.values)?.let { c ->
                    Stat(t("к прошлому"), (if (c - p > 0) "+" else if (c - p < 0) "−" else "") + sa.fmt(abs(c - p)), cp)
                } }
            }
            if (sb != null) {
                val r = Compare.pearson(sa.values, sb.values)
                Text(if (r == null) t("Связь посчитается, когда наберётся 7 дней с обоими показателями.")
                    else t("Связь: %s (r = %s, %d дн.) — %s", Compare.strength(r.first), formatNumber(Math.round(r.first * 100) / 100.0), r.second,
                        if (abs(r.first) < 0.1) t("показатели меняются независимо") else if (r.first > 0) t("растут и падают вместе") else t("когда один выше, другой ниже")),
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("cmp_r"))
                Text(t("Связь не значит причину: оба показателя могут зависеть от третьего. У каждой линии своя шкала — смотрите на форму, а не на высоту."),
                    style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DualChart(a: List<Double?>, b: List<Double?>?, p: List<Double?>?, ca: Color, cb: Color, cp: Color) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(160.dp).testTag("cmp_chart")) {
        val n = a.size.coerceAtLeast(2)
        fun x(i: Int) = size.width * i / (n - 1).toFloat()
        for (k in 0..3) drawLine(grid, Offset(0f, size.height * k / 3f), Offset(size.width, size.height * k / 3f), 1f)
        fun path(v: List<Double?>, lo: Double, hi: Double, color: Color, dashed: Boolean) {
            val span = (hi - lo).takeIf { it > 0 } ?: 1.0
            fun y(d: Double) = (size.height - 8f - (size.height - 16f) * ((d - lo) / span)).toFloat()
            val path = Path(); var on = false
            v.forEachIndexed { i, d ->
                if (d == null) { on = false; return@forEachIndexed }
                if (!on) path.moveTo(x(i), y(d)) else path.lineTo(x(i), y(d)); on = true
            }
            drawPath(path, color, style = Stroke(width = if (dashed) 3f else 5f, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(12f, 10f)) else null))
            v.forEachIndexed { i, d -> if (d != null) drawCircle(color, if (dashed) 3f else 5f, Offset(x(i), y(d))) }
        }
        // прошлый период — на шкале первого показателя
        val va = (a + (p ?: emptyList())).filterNotNull()
        if (va.isNotEmpty()) {
            val lo = va.min(); val hi = va.max()
            p?.let { path(it.take(n), lo, hi, cp, true) }
            path(a, lo, hi, ca, false)
        }
        b?.filterNotNull()?.takeIf { it.isNotEmpty() }?.let { vb -> path(b, vb.min(), vb.max(), cb, false) }
    }
}
