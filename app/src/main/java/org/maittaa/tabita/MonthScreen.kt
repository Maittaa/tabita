package org.maittaa.tabita

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

private val Good = Color(0xFF2E7D32)
private val TargetGreen = Color(0xFF3FA66B)

/** Вкладка «Итоги месяца»: карта по дням, что влияет на итог дня, индекс дня, цели против прошлого месяца. */
@Composable
fun MonthView(
    repo: Repo, prefs: Prefs, month: LocalDate, onMonth: (LocalDate) -> Unit,
    openDay: (LocalDate) -> Unit, modifier: Modifier,
) {
    val template = collect(repo.template)
    val entries = collect(repo.entries)
    var cfg by remember { mutableStateOf(prefs.monthCfg) }
    val data = remember(template, entries, month, cfg, I18n.lang) { MonthData(template, entries, month, cfg) }
    var setup by remember { mutableStateOf(false) }
    var pickRows by remember { mutableStateOf(false) }
    val save: (MonthCfg) -> Unit = { cfg = it; prefs.monthCfg = it; Log.d("Month", "настройки: ${it.toJson()}") }
    val listState = rememberLazyListState()

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(), state = listState,
            contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item("month") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onMonth(month.minusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, t("Назад")) }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(monthTitle(month), style = MaterialTheme.typography.titleMedium)
                        Text(t("заполнено дней: %d из %d", data.filledDays, data.days.size), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onMonth(month.plusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, t("Вперёд")) }
                }
            }
            if (data.heatRows.isEmpty()) item("empty") {
                Text(t("За этот месяц пока нет записей."), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
            } else {
                item("heat") { HeatmapCard(data, openDay, onPick = { pickRows = true }) }
                item("factors") { FactorsCard(data) { save(cfg.copy(outcome = it)) } }
                item("index") { IndexCard(data, onSetup = { setup = true }, openDay = openDay) }
                item("goals") { GoalsCard(data) }
            }
            item("pad") { Spacer(Modifier.height(32.dp)) }
        }
        LazyScrollbar(listState, Modifier.align(Alignment.CenterEnd))
    }

    if (pickRows) HeatRowsDialog(data, onSave = { save(cfg.copy(heat = it)); pickRows = false }, onDismiss = { pickRows = false })
    if (setup) IndexSetupDialog(data, onSave = { save(it); setup = false }, onDismiss = { setup = false })
}

// ---------- 1. тепловая карта ----------

@Composable
private fun levelColors(): List<Color> {
    val p = MaterialTheme.colorScheme.primary
    return listOf(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), p.copy(alpha = 0.35f), p.copy(alpha = 0.7f), p)
}

@Composable
private fun HeatmapCard(d: MonthData, openDay: (LocalDate) -> Unit, onPick: () -> Unit) {
    val colors = levelColors()
    val empty = MaterialTheme.colorScheme.outlineVariant
    val todayC = MaterialTheme.colorScheme.onSurface
    val measurer = rememberTextMeasurer()
    val numStyle = TextStyle(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val wkStyle = numStyle.copy(color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
    val rowH = 18.dp; val headH = 14.dp
    val rows = d.heatRows
    SectionCard(t("Каждый день месяца"), action = {
        TextButton(onClick = onPick) {
            Icon(Icons.Filled.FilterList, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
            Text(if (d.cfg.heat == null) t("Все строки") else t("Строк: %d", rows.size), style = MaterialTheme.typography.labelMedium)
        }
    }) {
        if (rows.isEmpty()) {
            Text(t("Не выбрано ни одной строки с данными."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@SectionCard
        }
        Row {
            Column(Modifier.width(84.dp)) {
                Spacer(Modifier.height(headH))
                rows.forEach { (m, _) ->
                    Box(Modifier.height(rowH), contentAlignment = Alignment.CenterStart) {
                        Text(m.title, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Canvas(
                Modifier.weight(1f).height(headH + rowH * rows.size).pointerInput(d.days) {
                    detectTapGestures { o ->
                        val col = (o.x / (size.width.toFloat() / d.days.size)).toInt()
                        d.days.getOrNull(col)?.takeIf { it <= d.today }?.let { Log.d("Month", "открыть день $it"); openDay(it) }
                    }
                }
            ) {
                val n = d.days.size
                val cw = size.width / n; val gap = 1.6.dp.toPx(); val head = headH.toPx(); val rh = rowH.toPx()
                val r = CornerRadius(2.dp.toPx())
                d.days.forEachIndexed { i, day ->
                    val we = day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY
                    if (day.dayOfMonth == 1 || (day.dayOfMonth % 5 == 0 && n - day.dayOfMonth >= 2) || i == n - 1) {
                        val tl = measurer.measure(day.dayOfMonth.toString(), if (we) wkStyle else numStyle)
                        drawText(tl, topLeft = Offset((i * cw + cw / 2 - tl.size.width / 2).coerceIn(0f, size.width - tl.size.width), 0f))
                    }
                }
                rows.forEachIndexed { ri, (_, levels) ->
                    levels.forEachIndexed { i, lv ->
                        if (d.days[i] > d.today) return@forEachIndexed
                        val tl = Offset(i * cw + gap / 2, head + ri * rh + gap / 2)
                        val sz = Size(cw - gap, rh - gap)
                        if (lv == null) drawRoundRect(empty, tl, sz, r, style = Stroke(1f))
                        else drawRoundRect(colors[lv], tl, sz, r)
                    }
                }
                // сегодня — рамка вокруг столбца
                val ti = d.days.indexOf(d.today)
                if (ti >= 0) drawRoundRect(todayC, Offset(ti * cw, head - 1f), Size(cw, rh * rows.size + 2f), r, style = Stroke(1.5f))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(t("Тап по столбцу — открыть день"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Legend(null, empty, t("пропуск"))
            listOf(t("нет"), t("чуть"), t("норма"), t("много")).forEachIndexed { i, s -> Legend(colors[i], null, s) }
        }
    }
}

@Composable
private fun Legend(fill: Color?, stroke: Color?, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(10.dp)) {
            if (fill != null) drawRoundRect(fill, cornerRadius = CornerRadius(2.dp.toPx()))
            if (stroke != null) drawRoundRect(stroke, cornerRadius = CornerRadius(2.dp.toPx()), style = Stroke(1f))
        }
        Text(" $label", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------- 2. что влияет ----------

private fun signed(v: Double, decimals: Int): String {
    val s = if (decimals == 0) Math.round(Math.abs(v)).toString() else "%.${decimals}f".format(Locale.US, Math.abs(v))
    val loc = if (I18n.isEn) s else s.replace('.', ',')
    return (if (v > 0) "+" else if (v < 0) "−" else "") + loc
}

@Composable
private fun FactorsCard(d: MonthData, onOutcome: (String) -> Unit) {
    var all by remember { mutableStateOf(false) }
    val title = if (d.outcomeIsIndex) t("Что влияет на индекс дня") else t("Что влияет на оценку дня")
    SectionCard(title, action = {
        Text(t("в дни с / без"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }) {
        if (d.scaleOutcome != null) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = !d.outcomeIsIndex, onClick = { onOutcome("rate") }, label = { Text(d.scaleOutcome.title) })
            FilterChip(selected = d.outcomeIsIndex, onClick = { onOutcome("index") }, label = { Text(t("Индекс дня")) })
        }
        val list = d.factors
        if (list.isEmpty()) {
            Text(
                t("Пока мало данных: нужно хотя бы 4 дня с итогом дня и днями «с» и «без» фактора."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }
        val shown = if (all) list else list.take(8)
        val maxAbs = shown.maxOf { Math.abs(it.effect) }.coerceAtLeast(1e-6)
        val dec = if (d.outcomeIsIndex) 0 else 1
        val grey = MaterialTheme.colorScheme.outline
        val neg = MaterialTheme.colorScheme.error
        val axis = MaterialTheme.colorScheme.outlineVariant
        shown.forEach { f ->
            val c = if (!f.reliable) grey else if (f.effect >= 0) Good else neg
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1.25f), verticalAlignment = Alignment.CenterVertically) {
                    Text(f.title, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodySmall, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, color = if (f.reliable) Color.Unspecified else grey)
                    Text(" " + t("%dд", f.nWith), style = MaterialTheme.typography.labelSmall, color = grey, maxLines = 1)
                }
                Canvas(Modifier.weight(1f).height(12.dp).padding(horizontal = 4.dp)) {
                    val mid = size.width / 2
                    drawLine(axis, Offset(mid, 0f), Offset(mid, size.height), 1.5f)
                    val len = (Math.abs(f.effect) / maxAbs).toFloat() * mid
                    val x0 = if (f.effect >= 0) mid else mid - len
                    drawRoundRect(c, Offset(x0, 1f), Size(len.coerceAtLeast(2f), size.height - 2f), CornerRadius(3.dp.toPx()))
                }
                Text(signed(f.effect, dec), Modifier.width(44.dp), style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold, color = c, maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
        }
        if (list.size > 8) TextButton(onClick = { all = !all }) {
            Text(if (all) t("Свернуть") else t("Показать все (%d)", list.size))
        }
        Text(
            if (d.outcomeIsIndex) t("Разница среднего индекса в дни с фактором и без. Серым — мало дней, вывод ненадёжен. Это подсказка, на что обратить внимание, а не доказательство причины.")
            else t("Разница средней оценки в дни с фактором и без. Серым — мало дней, вывод ненадёжен. Это подсказка, на что обратить внимание, а не доказательство причины."),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------- 3. индекс дня ----------

private fun dowName(d: DayOfWeek): String =
    d.getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, if (I18n.isEn) Locale.ENGLISH else Locale.forLanguageTag("ru"))
        .replaceFirstChar { it.uppercase() }

@Composable
private fun IndexCard(d: MonthData, onSetup: () -> Unit, openDay: (LocalDate) -> Unit) {
    SectionCard(t("Индекс дня"), action = {
        IconButton(onClick = onSetup) { Icon(Icons.Filled.Tune, t("Настроить индекс")) }
    }) {
        if (d.indexMetrics.isEmpty()) {
            Text(t("Выберите, из чего складывается индекс."), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onSetup) { Text(t("Настроить")) }
            return@SectionCard
        }
        val s = d.indexStats
        val delta = if (s.avg != null && s.prevAvg != null) s.avg - s.prevAvg else null
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Stat(t("среднее"), s.avg?.let { Math.round(it).toString() } ?: "—")
            Stat(t("прошлый месяц"), s.prevAvg?.let { Math.round(it).toString() } ?: "—")
            Stat(t("изменение"), delta?.let { (if (it >= 0) "▲ " else "▼ ") + signed(it, 0) } ?: "—", deltaColor(delta, 1))
        }
        Spacer(Modifier.height(8.dp))
        IndexChart(d, openDay)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoTile(t("Лучшая серия"), if (s.bestStreak == 0) "—" else t("%d дн.", s.bestStreak),
                s.streakEnd?.takeIf { s.bestStreak > 1 }?.let { t("по %s", it.ruShort()) }, Modifier.weight(1f))
            InfoTile(t("Лучший день недели"), s.bestDow?.let(::dowName) ?: "—",
                s.bestDowAvg?.let { t("в среднем %d", Math.round(it)) }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoTile(t("Лучший день"), s.bestDay?.ruShort() ?: "—",
                s.bestDay?.let { b -> d.index[d.days.indexOf(b)]?.let { t("индекс %d", Math.round(it)) } }, Modifier.weight(1f))
            InfoTile(t("Дней не ниже цели"), if (s.counted == 0) "—" else t("%d из %d", s.above, s.counted),
                t("цель %d", d.cfg.target), Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        if (s.partial > 0) Text(
            t("Неполных дней: %d — записано меньше половины, в средние и серии не идут. Пропуски индекс не снижают.", s.partial),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            t("Состав: %s", d.indexMetrics.joinToString { it.title + if (d.isBonus(it)) "★" else "" }) +
                if (d.indexMetrics.any { d.isBonus(it) }) "  ·  ★ " + t("бонус") else "",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InfoTile(label: String, value: String, sub: String?, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun IndexChart(d: MonthData, openDay: (LocalDate) -> Unit) {
    val line = MaterialTheme.colorScheme.primary
    val dim = MaterialTheme.colorScheme.outline
    val band = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val measurer = rememberTextMeasurer()
    val style = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val v = d.index
    val part = d.partial
    val target = d.cfg.target.toDouble()
    val left = 26.dp; val bottom = 16.dp
    Canvas(
        Modifier.fillMaxWidth().height(150.dp).pointerInput(d.days) {
            detectTapGestures { o ->
                val l = left.toPx(); val slot = (size.width - l) / d.days.size
                val i = ((o.x - l) / slot).toInt()
                d.days.getOrNull(i)?.takeIf { it <= d.today }?.let { openDay(it) }
            }
        }
    ) {
        val l = left.toPx(); val b = bottom.toPx(); val top = 4.dp.toPx()
        val w = size.width - l; val h = size.height - b - top
        val n = v.size; val slot = w / n
        fun x(i: Int) = l + slot * (i + 0.5f)
        fun y(p: Double) = top + h - (p / 100.0 * h).toFloat()
        listOf(0.0, 50.0, 100.0).forEach { p ->
            drawLine(grid, Offset(l, y(p)), Offset(size.width, y(p)), 1f)
            val tl = measurer.measure(Math.round(p).toString(), style)
            drawText(tl, topLeft = Offset(0f, (y(p) - tl.size.height / 2).coerceIn(0f, size.height - tl.size.height)))
        }
        // серии дней выше цели — подсветка
        d.streaks.forEach { r ->
            drawRoundRect(band, Offset(x(r.first) - slot / 2, top), Size(slot * (r.last - r.first + 1), h), CornerRadius(4.dp.toPx()))
        }
        drawLine(TargetGreen, Offset(l, y(target)), Offset(size.width, y(target)), 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
        val p = Path(); var started = false
        v.forEachIndexed { i, s -> if (s != null && !part[i]) { if (!started) { p.moveTo(x(i), y(s)); started = true } else p.lineTo(x(i), y(s)) } }
        drawPath(p, line, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        v.forEachIndexed { i, s ->
            if (s == null) return@forEachIndexed
            if (part[i]) drawCircle(dim, 3.dp.toPx(), Offset(x(i), y(s)), style = Stroke(1.5.dp.toPx()))
            else drawCircle(if (s >= target) line else dim, 3.dp.toPx(), Offset(x(i), y(s)))
        }
        listOf(0, n / 2, n - 1).distinct().forEach { i ->
            val tl = measurer.measure(d.days[i].ruShort(), style)
            drawText(tl, topLeft = Offset((x(i) - tl.size.width / 2).coerceIn(l, size.width - tl.size.width), size.height - tl.size.height))
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 14.dp, height = 2.dp).background(TargetGreen))
        Text("  " + t("цель") + "   ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.size(width = 14.dp, height = 10.dp).clip(RoundedCornerShape(3.dp)).background(band))
        Text("  " + t("серии выше цели") + "   ○ " + t("неполный день"), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Выбор состава индекса, цели индекса и целей по пунктам. */
@Composable
private fun IndexSetupDialog(d: MonthData, onSave: (MonthCfg) -> Unit, onDismiss: () -> Unit) {
    var keys by remember { mutableStateOf(d.indexKeys) }
    var goals by remember { mutableStateOf(d.metrics.associate { it.key to d.goal(it) }) }
    var target by remember { mutableIntStateOf(d.cfg.target) }
    var daily by remember { mutableStateOf(d.cfg.daily) }
    var weekly by remember { mutableStateOf(d.cfg.weekly) }
    var negative by remember { mutableStateOf(d.cfg.negative) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Индекс дня и цели")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    t("Отметьте, из чего складывается индекс (0–100). Пропуски его не снижают. Галочка-бонус только добавляет, «каждый день» — без отметки считается нулём, «в неделю» — цель на неделю, день без отметки не минус. «Минус» — для вредного: отметка или вариант списка снижает индекс."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t("Цель индекса"), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Stepper(target.toString(), { target = (target - 5).coerceAtLeast(5) }, { target = (target + 5).coerceAtMost(100) })
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                d.metrics.forEach { m ->
                    val g = goals[m.key] ?: d.goal(m)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(m.key in keys, { on -> keys = if (on) keys + m.key else keys - m.key })
                        Column(Modifier.weight(1f)) {
                            Text(m.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val neg = m.key in negative
                            val sub = if (neg && m.kind == MKind.OPTION) t("минус: %s и больше — ноль", formatNumber(g))
                                else if (neg) (if (m.key in daily) t("минус: отмечено — ноль, без отметки — плюс") else t("минус: отмечено — ноль"))
                                else if (m.kind != MKind.CHECK) t("цель: %s", m.goalText(g))
                                else (weekly[m.key] ?: 0).let { w -> when {
                                    w > 0 -> t("цель %d раз в неделю, день без отметки не минус", w)
                                    m.key in daily -> t("без отметки — ноль")
                                    else -> t("бонус — только добавляет")
                                } }
                            Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (m.kind == MKind.SCALE || m.kind == MKind.AMOUNT || m.kind == MKind.OPTION) {
                            val step = m.goalStep(g)
                            val lo = if (m.kind == MKind.SCALE) m.item.min else step
                            val hi = if (m.kind == MKind.SCALE) m.item.max else Double.MAX_VALUE
                            Stepper(
                                if (m.kind == MKind.AMOUNT) m.format(g) else formatNumber(g),
                                { goals = goals + (m.key to (g - step).coerceAtLeast(lo)) },
                                { goals = goals + (m.key to (g + step).coerceAtMost(hi)) },
                            )
                        }
                    }
                    val setSign: (Boolean) -> Unit = { minus ->
                        negative = if (minus) negative + m.key else negative - m.key
                        if (minus) { weekly = weekly - m.key; keys = keys + m.key }
                    }
                    if (m.kind == MKind.OPTION) EffectChips(m.key in negative, Modifier.padding(start = 40.dp), onChange = setSign)
                    if (m.kind == MKind.CHECK) {
                        val w = weekly[m.key] ?: 0
                        Row(Modifier.padding(start = 40.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            EffectChips(m.key in negative, onChange = setSign)
                            Spacer(Modifier.width(4.dp))
                            FilterChip(selected = w == 0 && m.key !in daily, onClick = { daily = daily - m.key },
                                label = { Text(if (m.key in negative) t("иногда") else t("бонус")) })
                            FilterChip(selected = w == 0 && m.key in daily, onClick = { daily = daily + m.key; weekly = weekly - m.key },
                                label = { Text(t("каждый день")) })
                        }
                        if (m.key !in negative) Row(Modifier.padding(start = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(t("в неделю"), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Stepper(if (w == 0) "—" else t("%d раз", w),
                                { weekly = if (w <= 1) weekly - m.key else weekly + (m.key to w - 1) },
                                { weekly = weekly + (m.key to (w + 1).coerceAtMost(7)); daily = daily - m.key })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // сохраняем только цели, отличающиеся от стандартных
                val changed = d.metrics.mapNotNull { m ->
                    val g = goals[m.key] ?: return@mapNotNull null
                    val def = MonthData(d.template, d.entries, d.month, d.cfg.copy(goals = emptyMap())).goal(m)
                    if (Math.abs(g - def) > 1e-9) m.key to g else null
                }.toMap()
                onSave(d.cfg.copy(index = keys, goals = changed, target = target, daily = daily, weekly = weekly, negative = negative))
            }) { Text(t("Готово")) }
        },
        dismissButton = {
            TextButton(onClick = { onSave(MonthCfg(outcome = d.cfg.outcome, heat = d.cfg.heat)) }) { Text(t("Сбросить")) }
        },
    )
}

/** Знак влияния на индекс: плюс (полезное) или минус (вредное). */
@Composable
internal fun EffectChips(minus: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = !minus, onClick = { onChange(false) }, label = { Text("＋") })
        FilterChip(selected = minus, onClick = { onChange(true) }, label = { Text("−") },
            colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.errorContainer, selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer))
    }
}

@Composable
private fun Stepper(value: String, minus: () -> Unit, plus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = minus, Modifier.size(32.dp)) { Icon(Icons.Filled.Remove, t("Меньше"), Modifier.size(18.dp)) }
        Text(value, Modifier.width(64.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
            maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        IconButton(onClick = plus, Modifier.size(32.dp)) { Icon(Icons.Filled.Add, t("Больше"), Modifier.size(18.dp)) }
    }
}

// ---------- 4. цели: этот месяц против прошлого ----------

@Composable
private fun GoalsCard(d: MonthData) {
    val rows = d.goalRows
    if (rows.isEmpty()) return
    val cur = I18n.month(d.month.monthValue); val prev = I18n.month(d.month.minusMonths(1).monthValue)
    val grey = MaterialTheme.colorScheme.onSurfaceVariant
    SectionCard(t("Цели: дни и % выполнения")) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            Text(cur, Modifier.width(78.dp), style = MaterialTheme.typography.labelSmall, color = grey, maxLines = 1)
            Text(prev, Modifier.width(70.dp), style = MaterialTheme.typography.labelSmall, color = grey, maxLines = 1)
            Spacer(Modifier.width(40.dp))
        }
        rows.forEach { r ->
            val diff = r.days - r.prevDays
            val good = if (r.metric.kind == MKind.OPTION) 0 else 1
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.metric.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(r.goalText, style = MaterialTheme.typography.labelSmall, color = grey, maxLines = 1)
                    Box(Modifier.padding(top = 3.dp, end = 10.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)) {
                        Box(Modifier.fillMaxWidth(r.pct / 100f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary))
                    }
                }
                Column(Modifier.width(78.dp)) {
                    Text(if (r.weeks) t("%d нед.", r.days) else t("%d дн.", r.days), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(if (r.of > 0) "${r.pct}%" else "—", style = MaterialTheme.typography.labelSmall, color = grey)
                }
                Column(Modifier.width(70.dp)) {
                    Text(if (r.weeks) t("%d нед.", r.prevDays) else t("%d дн.", r.prevDays), style = MaterialTheme.typography.bodyMedium, color = grey, maxLines = 1)
                    Text(if (r.prevOf > 0) "${r.prevDays * 100 / r.prevOf}%" else "—", style = MaterialTheme.typography.labelSmall, color = grey)
                }
                Text(
                    if (diff == 0) "•" else (if (diff > 0) "▲" else "▼") + Math.abs(diff),
                    Modifier.width(40.dp), style = MaterialTheme.typography.labelLarge,
                    color = deltaColor(diff.toDouble(), good), maxLines = 1,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
        }
        if (d.month.withDayOfMonth(1) == d.today.withDayOfMonth(1)) Text(
            t("Месяц ещё идёт — сравнивайте проценты, а не дни."),
            style = MaterialTheme.typography.labelSmall, color = grey, modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Какие строки показывать на карте по дням: группами (отметки, списки, показатели). */
@Composable
private fun HeatRowsDialog(d: MonthData, onSave: (Set<String>?) -> Unit, onDismiss: () -> Unit) {
    val all = d.metrics.map { it.key }.toSet()
    var sel by remember { mutableStateOf(d.cfg.heat?.intersect(all) ?: all) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Строки карты")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                d.metrics.groupBy { it.group }.forEach { (group, ms) ->
                    val keys = ms.map { it.key }.toSet()
                    val allOn = sel.containsAll(keys)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(group, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { sel = if (allOn) sel - keys else sel + keys }) { Text(if (allOn) t("Снять все") else t("Выбрать все")) }
                    }
                    ms.forEach { m ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(m.key in sel, { on -> sel = if (on) sel + m.key else sel - m.key })
                            Text(m.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(if (sel.containsAll(all)) null else sel) }) { Text(t("Готово")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Отмена")) } },
    )
}
