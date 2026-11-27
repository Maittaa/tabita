package org.maittaa.tabita.train

import org.maittaa.tabita.chartScrub
import org.maittaa.tabita.drawScrub
import org.maittaa.tabita.nearestIndex
import org.maittaa.tabita.hScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/** Тип графика (1.5, как в Tabita): выбирается на каждой карточке и запоминается. */
enum class SChart(val title: String) {
    LINE("Линия"),
    SMOOTH("Сглаженная (среднее за 7 дней)"),
    BARS("Столбики"),
}

private val dm = DateTimeFormatter.ofPattern("d.MM")

/** Среднее за 7 дней назад от каждой точки — для «сглаженной». */
fun smooth7(pts: List<Pair<LocalDate, Double>>): List<Pair<LocalDate, Double>> = pts.map { (d, _) ->
    d to pts.filter { !it.first.isAfter(d) && it.first.isAfter(d.minusDays(7)) }.map { it.second }.average()
}

/** График точек по датам выбранного типа; [target] — линия цели. */
@Composable
fun SeriesChart(
    points: List<Pair<LocalDate, Double>>, type: SChart, format: (Double) -> String = ::num1,
    color: Color = MaterialTheme.colorScheme.primary, target: Double? = null,
) {
    if (points.isEmpty()) { Text("Нет данных", style = MaterialTheme.typography.bodySmall); return }
    val tm = rememberTextMeasurer()
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outlineVariant
    val faint = color.copy(alpha = 0.3f)
    val goalColor = Color(0xFF3FA66B)
    val style = TextStyle(fontSize = 10.sp, color = label)
    val pts = points.sortedBy { it.first }
    val line = if (type == SChart.SMOOTH) smooth7(pts) else pts
    val touch = androidx.compose.runtime.remember(points, type) { androidx.compose.runtime.mutableStateOf<Float?>(null) }
    val tipStyle = TextStyle(fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
    val tipBg = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(Modifier.fillMaxWidth().height(150.dp).then(Modifier.chartScrub(touch))) {
        val x0 = pts.first().first.toEpochDay().toDouble()
        val x1 = pts.last().first.toEpochDay().toDouble().let { if (it == x0) x0 + 1 else it }
        val all = pts.map { it.second } + listOfNotNull(target)
        var y0 = all.min(); var y1 = all.max()
        if (type == SChart.BARS) y0 = minOf(0.0, y0)
        if (y1 - y0 < 1e-9) { y0 -= 1; y1 += 1 }
        val pad = (y1 - y0) * 0.08; if (type != SChart.BARS) y0 -= pad; y1 += pad
        val left = 44.dp.toPx(); val bottom = size.height - 16.dp.toPx(); val top = 4.dp.toPx(); val right = size.width - 4.dp.toPx()
        val slot = if (type == SChart.BARS) ((right - left) / (x1 - x0 + 1)).toFloat() else 0f
        fun px(d: LocalDate) = (left + slot / 2 + (d.toEpochDay() - x0) / (x1 - x0) * (right - left - slot)).toFloat()
        fun py(v: Double) = (bottom - (v - y0) / (y1 - y0) * (bottom - top)).toFloat()
        for (k in 0..2) {
            val v = y0 + (y1 - y0) * k / 2
            val y = py(v)
            drawLine(grid, Offset(left, y), Offset(right, y), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            val t = tm.measure(format(v), style)
            drawText(t, topLeft = Offset(left - t.size.width - 4.dp.toPx(), y - t.size.height / 2))
        }
        if (type == SChart.BARS) {
            val w = (slot * 0.7f).coerceIn(2.dp.toPx(), 18.dp.toPx())
            pts.forEach { (d, v) ->
                val y = py(v); val b = py(maxOf(0.0, y0))
                drawRoundRect(color, Offset(px(d) - w / 2, minOf(y, b)), Size(w, abs(b - y)), CornerRadius(3.dp.toPx()))
            }
        } else {
            if (type == SChart.SMOOTH) pts.forEach { (d, v) -> drawCircle(faint, 2.5.dp.toPx(), Offset(px(d), py(v))) }
            val path = Path()
            line.forEachIndexed { i, (d, v) -> if (i == 0) path.moveTo(px(d), py(v)) else path.lineTo(px(d), py(v)) }
            drawPath(path, color, style = Stroke(width = 2.5.dp.toPx()))
            if (type == SChart.LINE && pts.size <= 45) pts.forEach { (d, v) -> drawCircle(color, 3.dp.toPx(), Offset(px(d), py(v))) }
        }
        target?.let { tg ->
            val y = py(tg)
            drawLine(goalColor, Offset(left, y), Offset(right, y), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f)))
        }
        val a = tm.measure(pts.first().first.format(dm), style)
        drawText(a, topLeft = Offset(left, bottom + 2.dp.toPx()))
        val b = tm.measure(pts.last().first.format(dm), style)
        drawText(b, topLeft = Offset(right - b.size.width, bottom + 2.dp.toPx()))
        touch.value?.let { tx ->
            val i = nearestIndex(tx, pts.map { px(it.first) }) ?: return@let
            val (d, v) = pts[i]
            val txt = mutableListOf(d.format(dm), format(v))
            val ys = mutableListOf(py(v) to color)
            if (type == SChart.SMOOTH) line.getOrNull(i)?.let { txt += "за 7 дн.: " + format(it.second); ys[0] = py(v) to faint.copy(alpha = 1f); ys += py(it.second) to color }
            drawScrub(px(d), top, bottom, ys, txt, tm, tipStyle, label, tipBg, grid, left)
        }
    }
    if (target != null) Text("— цель ${format(target)}", style = MaterialTheme.typography.labelSmall, color = goalColor)
}

/** Мини-график для строки таблицы. */
@Composable
fun Sparkline(values: List<Double>, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    if (values.size < 2) { Box(modifier); return }
    Canvas(modifier) {
        val lo = values.min(); val hi = values.max().let { if (it - lo < 1e-9) lo + 1 else it }
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * size.width / (values.size - 1)
            val y = (size.height - (v - lo) / (hi - lo) * size.height).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(1.5.dp.toPx()))
        val y = (size.height - (values.last() - lo) / (hi - lo) * size.height).toFloat()
        drawCircle(color, 2.dp.toPx(), Offset(size.width, y))
    }
}

/** Кнопка выбора типа графика на карточке. */
@Composable
fun ChartTypeMenu(m: AppModel, key: String, def: SChart, onChange: (SChart) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton({ open = true }, Modifier.size(36.dp)) { Icon(Icons.Outlined.ShowChart, "Тип графика", Modifier.size(20.dp)) }
        DropdownMenu(open, { open = false }) {
            SChart.entries.forEach { tp ->
                DropdownMenuItem(
                    text = { Text(tp.title + if (m.storage.chartType(key, def) == tp) "  ✓" else "") },
                    onClick = { m.storage.setChartType(key, tp); onChange(tp); open = false },
                )
            }
        }
    }
}

/** Показатель подробной карточки: [good] — +1 рост хорошо, −1 снижение хорошо, 0 — нейтрально. */
data class DSeries(
    val id: String, val title: String, val points: List<Pair<LocalDate, Double>>,
    val fmt: (Double) -> String = ::num1, val good: Int = 0, val target: Double? = null, val sum: Boolean = false,
)

@Composable
private fun Stat(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
    }
}

@Composable
fun deltaColor(d: Double?, good: Int): Color = when {
    d == null || abs(d) < 1e-9 || good == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
    (d > 0) == (good > 0) -> Color(0xFF3FA66B)
    else -> MaterialTheme.colorScheme.error
}

fun deltaText(d: Double?, fmt: (Double) -> String) = when {
    d == null -> "—"
    abs(d) < 1e-9 -> "без изм."
    d > 0 -> "▲ " + fmt(d)
    else -> "▼ " + fmt(-d)
}

/**
 * Подробная карточка «как весы в Tabita»: чипы показателей, сводка (последнее, за период, мин, макс, среднее),
 * график выбранного типа и таблица всех показателей с изменением и мини-графиком. Нажатие на строку — выбрать.
 */
@Composable
fun DetailCard(m: AppModel, key: String, title: String, series: List<DSeries>, subtitle: String? = null, note: String? = null) {
    val list = series.filter { it.points.isNotEmpty() }
    if (list.isEmpty()) return
    var sel by remember(key) { mutableStateOf(m.storage.selected(key)) }
    val cur = list.firstOrNull { it.id == sel } ?: list.first()
    val ck = key + "_" + cur.id
    var type by remember(ck) { mutableStateOf(m.storage.chartType(ck, SChart.LINE)) }
    fun pick(id: String) { sel = id; m.storage.setSelected(key, id) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                ChartTypeMenu(m, ck, SChart.LINE) { type = it }
            }
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (list.size > 1) Row(Modifier.hScroll(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                list.forEach { s -> FilterChip(s.id == cur.id, { pick(s.id) }, label = { Text(s.title, maxLines = 1) }) }
            }
            val v = cur.points.sortedBy { it.first }.map { it.second }
            val d = if (v.size >= 2) v.last() - v.first() else null
            Row(Modifier.hScroll(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Stat("последнее", cur.fmt(v.last()))
                if (d != null) Stat("за период", deltaText(d, cur.fmt), deltaColor(d, cur.good))
                Stat("среднее", cur.fmt(v.average()))
                if (cur.sum) Stat("всего", cur.fmt(v.sum()))
                Stat("мин.", cur.fmt(v.min())); Stat("макс.", cur.fmt(v.max()))
            }
            SeriesChart(cur.points, type, { cur.fmt(it).substringBefore(" ") }, target = cur.target)
            if (list.size > 1) {
                HorizontalDivider(Modifier.padding(top = 4.dp))
                list.forEach { s ->
                    val sv = s.points.sortedBy { it.first }.map { it.second }
                    val sd = if (sv.size >= 2) sv.last() - sv.first() else null
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(if (s.id == cur.id) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) else Color.Transparent)
                            .clickable { pick(s.id) }.padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(s.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(s.fmt(sv.last()), Modifier.width(84.dp).padding(start = 4.dp), style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(deltaText(sd, s.fmt).replace("▲ ", "▲").replace("▼ ", "▼"), Modifier.width(72.dp),
                            style = MaterialTheme.typography.labelMedium, color = deltaColor(sd, s.good), maxLines = 1)
                        Sparkline(sv.takeLast(30), Modifier.width(44.dp).height(20.dp))
                    }
                }
            }
            note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
