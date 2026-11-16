package org.maittaa.tabita

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate

/** Вид графика для числового ряда. */
enum class ChartType(val ru: String) {
    LINE("Линия"),
    BARS("Столбцы"),
    SMOOTH("Сглаженная (7 дн.)"),
    WEEKLY("По неделям"),
    DELTA("Изменение от начала");

    val title get() = t(ru)
}

// ---------- расчёты динамики ----------

/** Скользящее среднее по окну ±3 дня (только по заполненным дням). */
fun movingAvg(v: List<Double?>, half: Int = 3): List<Double?> = v.indices.map { i ->
    if (v[i] == null) null
    else v.subList(maxOf(0, i - half), minOf(v.size, i + half + 1)).filterNotNull().average()
}

/** Средние по неделям (неделя с понедельника): (начало недели, среднее). */
fun weekly(v: List<Double?>, days: List<LocalDate>): List<Pair<LocalDate, Double?>> =
    days.indices.groupBy { days[it].with(DayOfWeek.MONDAY) }.map { (wk, idx) ->
        wk to idx.mapNotNull { v[it] }.takeIf { it.isNotEmpty() }?.average()
    }

/** Тренд за период: среднее второй половины минус среднее первой (по заполненным дням). */
fun trend(v: List<Double?>): Double? {
    val p = v.filterNotNull()
    if (p.size < 2) return null
    val h = p.size / 2
    return p.drop(p.size - h).average() - p.take(h).average()
}

/** Изменение «последнее − первое» (для веса и состава тела). */
fun change(v: List<Double?>): Double? {
    val p = v.filterNotNull()
    return if (p.size < 2) null else p.last() - p.first()
}

/** Цвет изменения: зелёный — в «хорошую» сторону, красный — в плохую, серый — нейтрально. */
@Composable
fun deltaColor(d: Double?, good: Int): Color {
    if (d == null || d == 0.0 || good == 0) return MaterialTheme.colorScheme.onSurfaceVariant
    return if (d * good > 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
}

fun deltaText(d: Double?, fmt: (Double) -> String): String =
    if (d == null) "—" else (if (d > 0) "▲ +" else if (d < 0) "▼ −" else "• ") + fmt(Math.abs(d))

// ---------- рисование ----------

/** Большой график с осями. */
@Composable
fun Chart(values: List<Double?>, days: List<LocalDate>, type: ChartType, fmt: (Double) -> String, height: Int = 170, target: Double? = null) {
    val line = MaterialTheme.colorScheme.primary
    val accent = MaterialTheme.colorScheme.secondary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val faint = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    val neg = MaterialTheme.colorScheme.error
    val measurer = rememberTextMeasurer()
    val style = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

    // ряд для рисования в зависимости от вида
    val weeks = if (type == ChartType.WEEKLY) weekly(values, days) else emptyList()
    val first = values.firstOrNull { it != null }
    val series: List<Double?> = when (type) {
        ChartType.WEEKLY -> weeks.map { it.second }
        ChartType.DELTA -> values.map { v -> v?.let { it - first!! } }
        else -> values
    }
    val present = series.filterNotNull()
    if (present.isEmpty()) return
    val smooth = if (type == ChartType.SMOOTH) movingAvg(values) else emptyList()
    val bars = type == ChartType.BARS || type == ChartType.WEEKLY
    var lo = present.min(); var hi = present.max()
    if (target != null && type != ChartType.DELTA) { hi = maxOf(hi, target); lo = minOf(lo, target) }
    if (bars || type == ChartType.DELTA) { lo = minOf(lo, 0.0); hi = maxOf(hi, 0.0) }
    if (hi == lo) { hi += 1; lo -= if (bars) 0.0 else 1.0 }
    val pad = (hi - lo) * 0.08
    if (!bars) lo -= pad
    hi += pad
    val avg = values.filterNotNull().average()
    val labels: List<String> = if (type == ChartType.WEEKLY) weeks.map { it.first.ruShort() } else days.map { it.ruShort() }
    val axisFmt: (Double) -> String = { v -> if (type == ChartType.DELTA && v > 0) "+" + fmt(v) else if (v < 0) "−" + fmt(-v) else fmt(v) }

    Canvas(Modifier.fillMaxWidth().height(height.dp)) {
        val left = 46.dp.toPx(); val bottom = 18.dp.toPx(); val top = 6.dp.toPx()
        val w = size.width - left; val h = size.height - bottom - top
        val n = series.size
        val slot = w / maxOf(n, 1)
        fun x(i: Int) = if (bars) left + slot * (i + 0.5f) else left + if (n <= 1) w / 2 else w * i / (n - 1)
        fun y(v: Double) = top + h - ((v - lo) / (hi - lo) * h).toFloat()

        // сетка и подписи оси Y
        listOf(present.min(), (present.min() + present.max()) / 2, present.max()).distinct().forEach { v ->
            drawLine(grid, Offset(left, y(v)), Offset(size.width, y(v)), 1f)
            val tl = measurer.measure(axisFmt(v), style)
            drawText(tl, topLeft = Offset(0f, (y(v) - tl.size.height / 2).coerceIn(0f, size.height - tl.size.height)))
        }
        if (type == ChartType.DELTA || (bars && lo < 0)) drawLine(grid, Offset(left, y(0.0)), Offset(size.width, y(0.0)), 2f)
        // цель (например, норма воды) — зелёная пунктирная линия
        if (target != null && type != ChartType.DELTA) drawLine(
            Color(0xFF3FA66B), Offset(left, y(target)), Offset(size.width, y(target)), 3f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
        )

        when {
            bars -> series.forEachIndexed { i, v ->
                v ?: return@forEachIndexed
                val bw = (slot * 0.7f).coerceAtMost(28.dp.toPx())
                val y0 = y(maxOf(lo, 0.0)); val y1 = y(v)
                drawRoundRect(
                    if (v < 0) neg else line,
                    topLeft = Offset(x(i) - bw / 2, minOf(y0, y1)), size = Size(bw, Math.abs(y1 - y0).coerceAtLeast(2f)),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
            }
            else -> {
                if (type != ChartType.DELTA) drawLine(
                    accent, Offset(left, y(avg)), Offset(size.width, y(avg)), 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
                )
                fun path(s: List<Double?>): Path {
                    val p = Path(); var started = false
                    s.forEachIndexed { i, v ->
                        if (v == null) return@forEachIndexed
                        if (!started) { p.moveTo(x(i), y(v)); started = true } else p.lineTo(x(i), y(v))
                    }
                    return p
                }
                if (type == ChartType.SMOOTH) {
                    series.forEachIndexed { i, v -> if (v != null) drawCircle(faint, 3.dp.toPx(), Offset(x(i), y(v))) }
                    drawPath(path(smooth), line, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                } else {
                    drawPath(path(series), line, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
                    if (n <= 62) series.forEachIndexed { i, v ->
                        if (v != null) drawCircle(if (type == ChartType.DELTA && v < 0) neg else line, 3.dp.toPx(), Offset(x(i), y(v)))
                    }
                }
            }
        }
        // подписи оси X: начало, середина, конец
        listOf(0, n / 2, n - 1).distinct().filter { it in labels.indices }.forEach { i ->
            val tl = measurer.measure(labels[i], style)
            val px = (x(i) - tl.size.width / 2).coerceIn(left, size.width - tl.size.width)
            drawText(tl, topLeft = Offset(px, size.height - tl.size.height))
        }
    }
    if (target != null) Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 14.dp, height = 2.dp).background(Color(0xFF3FA66B)))
        Text("  " + t("норма") + " " + fmt(target), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (type != ChartType.DELTA && !bars) Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 14.dp, height = 2.dp).background(accent))
        Text("  " + t("среднее"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (type == ChartType.SMOOTH) Text(
            "   ● " + t("дни") + "   ━ " + t("среднее за 7 дн."),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Мини-график без осей (для компактных карточек и таблиц). */
@Composable
fun Sparkline(values: List<Double?>, modifier: Modifier, color: Color = MaterialTheme.colorScheme.primary, bars: Boolean = false) {
    val p = values.filterNotNull()
    if (p.isEmpty()) { Box(modifier); return }
    var lo = if (bars) minOf(0.0, p.min()) else p.min()
    var hi = p.max()
    if (hi == lo) { hi += 1; if (!bars) lo -= 1 }  // ровная линия — посередине
    Canvas(modifier) {
        val n = values.size
        fun x(i: Int) = if (n <= 1) size.width / 2 else size.width * i / (n - 1)
        fun y(v: Double) = size.height - ((v - lo) / (hi - lo) * size.height * 0.9f).toFloat() - size.height * 0.05f
        if (bars) {
            val bw = (size.width / maxOf(n, 1) * 0.7f).coerceAtLeast(1f)
            values.forEachIndexed { i, v ->
                v ?: return@forEachIndexed
                drawRect(color, Offset(size.width * (i + 0.15f) / n, y(v)), Size(bw, size.height - y(v)))
            }
        } else {
            val path = Path(); var started = false
            values.forEachIndexed { i, v ->
                v ?: return@forEachIndexed
                if (!started) { path.moveTo(x(i), y(v)); started = true } else path.lineTo(x(i), y(v))
            }
            drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
            values.indexOfLast { it != null }.takeIf { it >= 0 }?.let { drawCircle(color, 3.dp.toPx(), Offset(x(it), y(values[it]!!))) }
        }
    }
}

@Composable
fun Stat(label: String, value: String, color: Color = Color.Unspecified) {
    androidx.compose.foundation.layout.Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
fun BarRow(label: String, value: Float, valueText: String, secondary: Float = 0f, valueWidth: Int = 64, compact: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = if (compact) 1.dp else 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, if (compact) Modifier.weight(1.2f) else Modifier.width(110.dp), style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium, maxLines = 1)
        Box(
            Modifier.weight(if (compact) 0.8f else 1f).height(if (compact) 8.dp else 14.dp).clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(value.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.primary)
            )
            if (secondary > 0f) Box(
                Modifier.fillMaxHeight().fillMaxWidth(secondary.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.secondary)
            )
        }
        Text(
            valueText, Modifier.width(if (compact) 50.dp else valueWidth.dp).padding(start = 6.dp),
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold, maxLines = 1,
        )
    }
}
