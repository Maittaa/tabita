package org.maittaa.tabita

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ---------- категории: иконка и цвет ----------

data class CatStyle(val icon: ImageVector, val color: Color)

private val palette = listOf(
    Color(0xFF6DB87C), Color(0xFFF5B014), Color(0xFFEF4B3B), Color(0xFF4FA3E0), Color(0xFFAB7AE0),
    Color(0xFFEC7AA8), Color(0xFF3FB6A8), Color(0xFFE08A3C), Color(0xFF8D9AA6),
)

fun catStyle(name: String): CatStyle = when (name) {
    "Продукты" -> CatStyle(Icons.Filled.ShoppingCart, Color(0xFF6DB87C))
    "Кафе" -> CatStyle(Icons.Filled.Restaurant, Color(0xFFE08A3C))
    "Транспорт" -> CatStyle(Icons.Filled.DirectionsBus, Color(0xFFF5B014))
    "Дом" -> CatStyle(Icons.Filled.Home, Color(0xFF3FB6A8))
    "Здоровье" -> CatStyle(Icons.Filled.LocalHospital, Color(0xFFEF4B3B))
    "Одежда" -> CatStyle(Icons.Filled.Checkroom, Color(0xFFAB7AE0))
    "Развлечения" -> CatStyle(Icons.Filled.Celebration, Color(0xFFEC7AA8))
    "Другое" -> CatStyle(Icons.Filled.MoreHoriz, Color(0xFF8D9AA6))
    else -> CatStyle(Icons.AutoMirrored.Filled.Label, palette[Math.floorMod(name.hashCode(), palette.size)])
}

// ---------- модели ----------

data class Slice(val name: String, val amount: Double) {
    val style get() = catStyle(name)
}

/** Расходы по категориям: кольцо как в банковских приложениях. */
class DonutCard(
    key: String, title: String, val total: Double, val prevTotal: Double?, val slices: List<Slice>,
    /** Расходы периода с датами — для подробностей по категории. */
    val items: List<Pair<java.time.LocalDate, Purchase>> = emptyList(),
    /** Траты по категориям за прошлый период. */
    val prevByCat: Map<String, Double> = emptyMap(),
) : StatCard(key, "Финансы", title)

/** Категория покупки так же, как в кольце. */
fun catOf(p: Purchase) = p.category.ifEmpty { "Другое" }

/** Подробности по категории: сумма, доля, к прошлому периоду, что покупали чаще всего и все покупки. */
@Composable
fun CategoryDialog(c: DonutCard, cat: String, onDismiss: () -> Unit) {
    val st = catStyle(cat)
    val list = c.items.filter { catOf(it.second) == cat }.sortedByDescending { it.first }
    val sum = list.sumOf { it.second.total }
    val prev = c.prevByCat[cat]
    val byName = list.groupBy { it.second.name.trim().ifEmpty { t("без названия") }.lowercase() }
        .map { (_, l) -> Triple(l.first().second.name.trim().ifEmpty { t("без названия") }, l.size, l.sumOf { it.second.total }) }
        .sortedByDescending { it.third }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(Modifier.size(44.dp).clip(CircleShape).background(st.color), contentAlignment = Alignment.Center) {
                Icon(st.icon, null, Modifier.size(24.dp), tint = Color.White)
            }
        },
        title = { Text(t(cat)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatMoney(sum), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    if (c.total > 0) Text(t("%d%% трат", Math.round(sum / c.total * 100).toInt()), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    listOfNotNull(
                        t("%d покупок", list.size),
                        list.takeIf { it.isNotEmpty() }?.let { t("в среднем %s", formatMoney(sum / it.size)) },
                        prev?.let { p -> (if (sum <= p) "↓ " else "↑ ") + formatMoney(Math.abs(sum - p)) + " " + t("к прошлому периоду") },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (byName.size > 1) {
                    Text(t("На что больше всего"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
                    val mx = byName.first().third.coerceAtLeast(1.0)
                    byName.take(5).forEach { (n, cnt, s) ->
                        Column {
                            Row {
                                Text(n + if (cnt > 1) " ×$cnt" else "", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(formatMoney(s), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                                Box(Modifier.fillMaxWidth((s / mx).toFloat()).height(6.dp).clip(RoundedCornerShape(3.dp)).background(st.color))
                            }
                        }
                    }
                }
                Text(t("Все покупки"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
                list.forEach { (d, p) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(d.ruShort(), Modifier.width(64.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(Modifier.weight(1f)) {
                            Text(p.name.ifBlank { t("без названия") }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (p.qty != 1.0) Text("${formatNumber(p.qty)} ${t(p.unit)} × ${formatMoney(p.price)}", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(formatMoney(p.total), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("Закрыть")) } },
    )
}

/** Доходы и расходы по неделям (или дням для коротких периодов). */
class FlowCard(key: String, title: String, val labels: List<String>, val income: List<Double>, val expense: List<Double>) :
    StatCard(key, "Финансы", title) {
    val inc get() = income.sum()
    val exp get() = expense.sum()
}

fun flowBuckets(days: List<LocalDate>, per: List<List<Purchase>>): Triple<List<String>, List<Double>, List<Double>> {
    val byWeek = days.size > 14
    val groups = days.indices.groupBy { if (byWeek) days[it].with(DayOfWeek.MONDAY) else days[it] }
    val labels = groups.keys.map { it.ruShort() }
    val inc = groups.values.map { idx -> idx.sumOf { i -> per[i].filter { it.income }.sumOf { it.total } } }
    val exp = groups.values.map { idx -> idx.sumOf { i -> per[i].filter { !it.income }.sumOf { it.total } } }
    return Triple(labels, inc, exp)
}

private val Green = Color(0xFF3FA66B)
private val Red = Color(0xFFE5533D)

// ---------- отрисовка ----------

/**
 * Видимые дуги сегментов: маленькая доля получает минимум (зазор со скруглёнными концами + 3°),
 * чтобы не превращаться в кружок, наезжающий на соседей; остальные пропорционально сжимаются.
 */
fun donutSweeps(fracs: List<Double>, gap: Float): List<Float> {
    val n = fracs.size
    if (n <= 1) return fracs.map { 360f }
    val minS = minOf(gap + 3f, 360f / n)
    val small = BooleanArray(n)
    // несколько проходов: после сжатия крупных кто-то из них тоже может стать «маленьким»
    repeat(n) {
        val rest = 360f - small.count { it } * minS
        val restFrac = fracs.indices.filter { !small[it] }.sumOf { fracs[it] }
        var changed = false
        fracs.indices.filter { !small[it] }.forEach { i ->
            if (restFrac <= 0 || (fracs[i] / restFrac * rest).toFloat() < minS) { small[i] = true; changed = true }
        }
        if (!changed) {
            return fracs.indices.map { i -> if (small[i]) minS else (fracs[i] / restFrac * rest).toFloat() }
        }
    }
    return List(n) { 360f / n }
}

/** Кольцо: сегменты с зазорами, иконка категории в начале сегмента, проценты снаружи. */
@Composable
fun Donut(slices: List<Slice>, modifier: Modifier, thick: Float = 0.17f, showLabels: Boolean = true, showIcons: Boolean = true) {
    val total = slices.sumOf { it.amount }.takeIf { it > 0 } ?: return
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val painters = slices.map { rememberVectorPainter(it.style.icon) }
    Canvas(modifier) {
        val d = minOf(size.width, size.height) * (if (showLabels) 0.72f else 0.92f)
        val sw = d * thick
        val topLeft = Offset((size.width - d) / 2 + sw / 2, (size.height - d) / 2 + sw / 2)
        val arcSize = Size(d - sw, d - sw)
        val r = (d - sw) / 2
        val c = Offset(size.width / 2, size.height / 2)
        var start = -90f
        // зазор между сегментами в градусах (с учётом скруглённых концов)
        val gap = if (slices.size > 1) (sw / r * 180f / PI.toFloat()) * 1.1f else 0f
        val sweeps = donutSweeps(slices.map { it.amount / total }, gap)
        slices.forEachIndexed { i, s ->
            val sweep = sweeps[i]
            val draw = (sweep - gap).coerceAtLeast(0f)
            drawArc(s.style.color, start + gap / 2, draw, false, topLeft, arcSize,
                style = Stroke(sw, cap = if (slices.size > 1) StrokeCap.Round else StrokeCap.Butt))
            val mid = Math.toRadians((start + sweep / 2).toDouble())
            if (showLabels && sweep > 12f) {
                val pct = Math.round(s.amount / total * 100).let { if (it < 1) "<1%" else "$it%" }
                val tl = measurer.measure(pct, labelStyle)
                val lr = r + sw / 2 + 6.dp.toPx() + tl.size.width / 2
                drawText(tl, topLeft = Offset(c.x + (lr * cos(mid)).toFloat() - tl.size.width / 2, c.y + (lr * sin(mid)).toFloat() - tl.size.height / 2))
            }
            if (showIcons && sweep > 18f) {
                // кружок с иконкой в начале сегмента, как на фото
                val a = Math.toRadians((start + gap / 2 + minOf(draw / 2, sw / r * 57.3f * 0.6f)).toDouble())
                val p = Offset(c.x + (r * cos(a)).toFloat(), c.y + (r * sin(a)).toFloat())
                val ir = sw * 0.4f
                drawCircle(Color.White.copy(alpha = 0.25f), ir, p)
                translate(p.x - ir * 0.65f, p.y - ir * 0.65f) {
                    with(painters[i]) { draw(Size(ir * 1.3f, ir * 1.3f), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White)) }
                }
            }
            start += sweep
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DonutFull(c: DonutCard) {
    var bars by remember { mutableStateOf(false) }
    var openCat by remember { mutableStateOf<String?>(null) }
    openCat?.let { CategoryDialog(c, it) { openCat = null } }
    SectionCard(c.title) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatMoney(c.total), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            c.prevTotal?.let { prev ->
                val d = c.total - prev
                Box(Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Text((if (d <= 0) "↓ " else "↑ ") + formatMoney(Math.abs(d)), style = MaterialTheme.typography.labelMedium,
                        color = if (d <= 0) Green else Red, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Text(
            if (c.prevTotal != null) t("траты · к прошлому периоду") else t("траты"),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        if (!bars) {
            Donut(c.slices, Modifier.fillMaxWidth().aspectRatio(1.25f))
        } else {
            val mx = c.slices.maxOf { it.amount }
            c.slices.forEach { s ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { openCat = s.name }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(s.style.color), contentAlignment = Alignment.Center) {
                        Icon(s.style.icon, null, Modifier.size(16.dp), tint = Color.White)
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Row { Text(t(s.name), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(formatMoney(s.amount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) }
                        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                            Box(Modifier.fillMaxWidth((s.amount / mx).toFloat()).height(8.dp).clip(RoundedCornerShape(4.dp)).background(s.style.color))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            SingleChoiceSegmentedButtonRow {
                SegmentedButton(selected = !bars, onClick = { bars = false }, shape = SegmentedButtonDefaults.itemShape(0, 2),
                    icon = {}) { Icon(Icons.Filled.DonutLarge, t("Кольцо"), Modifier.size(18.dp)) }
                SegmentedButton(selected = bars, onClick = { bars = true }, shape = SegmentedButtonDefaults.itemShape(1, 2),
                    icon = {}) { Icon(Icons.Filled.BarChart, t("Столбцы"), Modifier.size(18.dp)) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(t("Нажмите на категорию — покажу, из чего она сложилась."), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        // чипы категорий: иконка, название, сумма
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            c.slices.forEach { s ->
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(s.style.color.copy(alpha = 0.15f)).clickable { openCat = s.name }.padding(end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(s.style.color), contentAlignment = Alignment.Center) {
                        Icon(s.style.icon, null, Modifier.size(18.dp), tint = Color.White)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(t(s.name), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = 6.dp))
                    Text(formatMoney(s.amount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun DonutCompact(c: DonutCard, cols: Int) {
    Text(formatMoney(c.total), style = if (cols == 2) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold, maxLines = 1)
    c.prevTotal?.let { prev ->
        val d = c.total - prev
        Text((if (d <= 0) "↓ " else "↑ ") + formatMoney(Math.abs(d)), style = MaterialTheme.typography.labelSmall, color = if (d <= 0) Green else Red)
    }
    Donut(c.slices, Modifier.fillMaxWidth().aspectRatio(1.4f).padding(top = 4.dp), thick = 0.24f, showLabels = false, showIcons = cols == 2)
}

/** Доходы (вверх, зелёные) и расходы (вниз, красные) по неделям/дням + итог и доля сбережений. */
@Composable
fun FlowFull(c: FlowCard) {
    SectionCard(c.title) {
        val net = c.inc - c.exp
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Stat(t("доходы"), formatMoney(c.inc), Green)
            Stat(t("расходы"), formatMoney(c.exp), Red)
            Stat(t("итог"), (if (net >= 0) "+" else "") + formatMoney(net), if (net >= 0) Green else Red)
        }
        if (c.inc > 0) {
            val rate = net / c.inc
            Spacer(Modifier.height(8.dp))
            Text(t("Отложено %d%% дохода", Math.round(rate * 100).toInt()), style = MaterialTheme.typography.bodySmall)
            Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Red.copy(alpha = 0.8f))) {
                Box(Modifier.fillMaxWidth(rate.coerceIn(0.0, 1.0).toFloat()).height(10.dp).background(Green))
            }
        }
        Spacer(Modifier.height(10.dp))
        FlowBars(c, Modifier.fillMaxWidth().height(170.dp), labels = true)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(Green)); Text(" " + t("доходы") + "   ", style = MaterialTheme.typography.labelSmall)
            Box(Modifier.size(10.dp).clip(CircleShape).background(Red)); Text(" " + t("расходы"), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun FlowBars(c: FlowCard, modifier: Modifier, labels: Boolean) {
    val measurer = rememberTextMeasurer()
    val style = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val axis = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier) {
        val bottom = if (labels) 16.dp.toPx() else 0f
        val h = size.height - bottom
        val mx = maxOf(c.income.maxOrNull() ?: 0.0, c.expense.maxOrNull() ?: 0.0).takeIf { it > 0 } ?: 1.0
        val mi = c.income.maxOrNull() ?: 0.0; val me = c.expense.maxOrNull() ?: 0.0
        val mid = h * when { mi <= 0 -> 0.04; me <= 0 -> 0.96; else -> (mi / (mi + me)).coerceIn(0.25, 0.75) }.toFloat()
        val n = c.labels.size
        val slot = size.width / maxOf(n, 1)
        val bw = (slot * 0.55f).coerceAtMost(26.dp.toPx())
        val upH = mid; val downH = h - mid
        val maxInc = c.income.maxOrNull() ?: 0.0
        val maxExp = c.expense.maxOrNull() ?: 0.0
        val scale = maxOf(if (maxInc > 0) maxInc / upH else 0.0, if (maxExp > 0) maxExp / downH else 0.0).takeIf { it > 0 } ?: 1.0
        drawLine(axis, Offset(0f, mid), Offset(size.width, mid), 2f)
        for (i in 0 until n) {
            val x = slot * i + (slot - bw) / 2
            val iu = (c.income[i] / scale).toFloat(); val ed = (c.expense[i] / scale).toFloat()
            if (iu > 0) drawRoundRect(Green, Offset(x, mid - iu), Size(bw, iu), androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
            if (ed > 0) drawRoundRect(Red, Offset(x, mid + 1f), Size(bw, ed), androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
        }
        if (labels) listOf(0, n / 2, n - 1).distinct().filter { it in 0 until n }.forEach { i ->
            val tl = measurer.measure(c.labels[i], style)
            drawText(tl, topLeft = Offset((slot * i + slot / 2 - tl.size.width / 2).coerceIn(0f, size.width - tl.size.width), size.height - tl.size.height))
        }
    }
}
