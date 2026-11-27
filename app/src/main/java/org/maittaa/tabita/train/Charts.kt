package org.maittaa.tabita.train

import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.background
import org.maittaa.tabita.chartScrub
import org.maittaa.tabita.drawScrub
import org.maittaa.tabita.nearestIndex
import org.maittaa.tabita.hScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

private val shortDate = DateTimeFormatter.ofPattern("d.MM")
fun num1(v: Double): String = if (v == Math.floor(v) && kotlin.math.abs(v) < 1e7) v.toLong().toString() else "%.1f".format(v).replace('.', ',')

/** Линейный график по датам: подписи мин/макс слева, первая/последняя дата снизу. */
@Composable
fun LineChart(
    points: List<Pair<LocalDate, Double>>,
    modifier: Modifier = Modifier,
    format: (Double) -> String = ::num1,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    if (points.isEmpty()) { Text("Нет данных", style = MaterialTheme.typography.bodySmall); return }
    val tm = rememberTextMeasurer()
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outlineVariant
    val style = TextStyle(fontSize = 10.sp, color = label)
    val touch = androidx.compose.runtime.remember(points) { androidx.compose.runtime.mutableStateOf<Float?>(null) }
    val tipStyle = TextStyle(fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
    val tipBg = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(modifier.fillMaxWidth().height(140.dp).then(Modifier.chartScrub(touch))) {
        val pts = points.sortedBy { it.first }
        val x0 = pts.first().first.toEpochDay().toDouble()
        val x1 = pts.last().first.toEpochDay().toDouble().let { if (it == x0) x0 + 1 else it }
        var y0 = pts.minOf { it.second }; var y1 = pts.maxOf { it.second }
        if (y1 - y0 < 1e-9) { y0 -= 1; y1 += 1 }
        val pad = (y1 - y0) * 0.08; y0 -= pad; y1 += pad
        val left = 44.dp.toPx(); val bottom = size.height - 16.dp.toPx(); val top = 4.dp.toPx(); val right = size.width - 4.dp.toPx()
        fun px(d: LocalDate) = (left + (d.toEpochDay() - x0) / (x1 - x0) * (right - left)).toFloat()
        fun py(v: Double) = (bottom - (v - y0) / (y1 - y0) * (bottom - top)).toFloat()
        for (k in 0..2) {
            val v = y0 + (y1 - y0) * k / 2
            val y = py(v)
            drawLine(grid, Offset(left, y), Offset(right, y), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            val t = tm.measure(format(v), style)
            drawText(t, topLeft = Offset(left - t.size.width - 4.dp.toPx(), y - t.size.height / 2))
        }
        val path = Path()
        pts.forEachIndexed { i, (d, v) -> if (i == 0) path.moveTo(px(d), py(v)) else path.lineTo(px(d), py(v)) }
        drawPath(path, color, style = Stroke(width = 2.5.dp.toPx()))
        if (pts.size <= 45) pts.forEach { (d, v) -> drawCircle(color, 3.dp.toPx(), Offset(px(d), py(v))) }
        val a = tm.measure(pts.first().first.format(shortDate), style)
        drawText(a, topLeft = Offset(left, bottom + 2.dp.toPx()))
        val b = tm.measure(pts.last().first.format(shortDate), style)
        drawText(b, topLeft = Offset(right - b.size.width, bottom + 2.dp.toPx()))
        touch.value?.let { tx ->
            val i = nearestIndex(tx, pts.map { px(it.first) }) ?: return@let
            val (d, v) = pts[i]
            drawScrub(px(d), top, bottom, listOf(py(v) to color), listOf(d.format(shortDate), format(v)), tm, tipStyle, label, tipBg, grid, left)
        }
    }
}

/** Столбики с подписями снизу. */
@Composable
fun BarChart(values: List<Pair<String, Double>>, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    if (values.isEmpty()) { Text("Нет данных", style = MaterialTheme.typography.bodySmall); return }
    val tm = rememberTextMeasurer()
    val style = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val valueStyle = TextStyle(fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
    Canvas(modifier.fillMaxWidth().height(130.dp)) {
        val max = values.maxOf { it.second }.coerceAtLeast(1.0)
        val bottom = size.height - 16.dp.toPx(); val top = 16.dp.toPx()
        val slot = size.width / values.size
        values.forEachIndexed { i, (l, v) ->
            val h = ((v / max) * (bottom - top)).toFloat()
            val x = i * slot + slot * 0.2f
            if (v > 0) drawRoundRect(color, Offset(x, bottom - h), Size(slot * 0.6f, h), CornerRadius(4.dp.toPx()))
            if (values.size <= 8 || (values.size - 1 - i) % 2 == 0) {
                val t = tm.measure(l, style)
                drawText(t, topLeft = Offset(i * slot + (slot - t.size.width) / 2, bottom + 2.dp.toPx()))
            }
            if (v > 0) {
                val vt = tm.measure(num1(v), valueStyle)
                drawText(vt, topLeft = Offset(i * slot + (slot - vt.size.width) / 2, bottom - h - vt.size.height))
            }
        }
    }
}

@Composable
fun ChartCard(title: String, subtitle: String? = null, action: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                action?.invoke()
            }
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            content()
        }
    }
}

// ——— данные Tabita ———

private data class Metric(val title: String, val get: (TDay) -> Double?, val fmt: (Double) -> String, val sum: Boolean = false)

private val metrics = listOf(
    Metric("Сон", { it.sleepMin }, { fmtMin(it) }),
    Metric("Пульс покоя", { it.rhr }, { "${it.roundToInt()} уд/мин" }),
    Metric("Шаги", { it.steps }, { it.roundToInt().toString() }, sum = true),
    Metric("Тренировки и активность", { it.exMin }, { "${it.roundToInt()} мин" }, sum = true),
    Metric("Вес", { it.weight }, { num1((it * 10).roundToInt() / 10.0) + " кг" }),
    Metric("Жир", { it.fat }, { num1((it * 10).roundToInt() / 10.0) + " %" }),
    Metric("Мышцы", { it.muscle }, { num1((it * 10).roundToInt() / 10.0) }),
    Metric("ИМТ", { it.bmi }, { num1((it * 10).roundToInt() / 10.0) }),
    Metric("Оценка дня", { it.rate }, { num1(it) }),
    Metric("FFMI (безжировая масса / рост²)", { it.ffmi }, { num1((it * 10).roundToInt() / 10.0) }),
    Metric("Калории", { it.kcal }, { "${it.roundToInt()} ккал" }),
    Metric("Энергобаланс (съедено − расход)", { d -> if (d.kcal != null && d.tdee != null) d.kcal - d.tdee else null }, { "${it.roundToInt()} ккал" }),
    Metric("Белок", { it.protein }, { "${it.roundToInt()} г" }),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabitaDataScreen(m: AppModel) {
    var period by remember { mutableIntStateOf(30) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Данные дневника") },
                navigationIcon = { IconButton({ m.closeTabitaData() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
                actions = { IconButton({ m.refreshTabita() }) { Icon(Icons.Outlined.Refresh, "Обновить") } },
            )
        },
    ) { pad ->
        val data = m.tabitaData
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text("Статус: ${m.tabitaStatus?.title ?: "проверяю…"}", style = MaterialTheme.typography.bodySmall) }
            m.readiness?.let { r -> item { ReadinessCard(r) } }
            if (data == null) { item { Text(tabitaHelp(m.tabitaStatus) ?: "Данных пока нет — заполняйте дневник: сон, вес, питание.") }; return@LazyColumn }
            item {
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(7 to "7 дней", 30 to "30 дней", 90 to "90 дней").forEach { (d, t) -> FilterChip(period == d, { period = d }, label = { Text(t) }) }
                }
            }
            val today = LocalDate.now()
            val days = data.days.filter { ChronoUnit.DAYS.between(it.date, today) < period }
            metrics.forEach { mt ->
                val pts = days.mapNotNull { d -> mt.get(d)?.let { d.date to it } }
                if (pts.isNotEmpty()) item(key = mt.title) {
                    val last = pts.last()
                    val avg = pts.map { it.second }.average()
                    val ck = "data_" + mt.title
                    var tp by remember(ck) { mutableStateOf(m.storage.chartType(ck, if (mt.sum) SChart.BARS else SChart.LINE)) }
                    ChartCard(
                        mt.title,
                        "Последнее: ${mt.fmt(last.second)} · ${prettyDate(last.first.toString())}\n" +
                            "Среднее за период: ${mt.fmt(avg)}" + (if (mt.sum) " · всего: ${mt.fmt(pts.sumOf { it.second })}" else "") +
                            " · дней с данными: ${pts.size}",
                        action = { ChartTypeMenu(m, ck, if (mt.sum) SChart.BARS else SChart.LINE) { tp = it } },
                    ) { SeriesChart(pts, tp, { v -> mt.fmt(v).substringBefore(" ") }) }
                }
            }
            data.checkup?.let { c ->
                item {
                    ChartCard("Чекап самочувствия", "Пройден ${prettyDate(c.date.toString())}" + (c.overall?.let { " · общая оценка ${it.roundToInt()}/100" } ?: "")) {
                        Text("Чем выше, тем лучше", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        c.blocks.forEach { b ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(b.title, Modifier.width(120.dp), style = MaterialTheme.typography.bodyMedium)
                                LinearProgressIndicator(progress = { (b.goodness / 100).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.weight(1f).height(8.dp))
                                Text(" ${b.goodness.roundToInt()}", Modifier.width(36.dp), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ——— прогресс ———

private fun sessionVolume(s: Session): Int = s.exercises.filter { !it.seconds }.sumOf { r ->
    r.values.sumOf { v -> v.split("/").sumOf { leadingInt(it) ?: 0 } }
}

private fun bestSet(r: ExResult): Int? = r.values.mapNotNull { v -> v.split("/").mapNotNull { leadingInt(it) }.maxOrNull() }.maxOrNull()

/** Карточки «Прогресса» (1.5): ключ → название. Порядок и видимость — в Storage.progOrder / progHidden. */
val progressCards = listOf(
    "muscles" to "Подходы на мышцы за неделю",
    "weeks" to "Тренировки по неделям",
    "sessions" to "Тренировки: объём и время",
    "exercises" to "Упражнения",
    "body" to "Тело и восстановление (дневник)",
    "food" to "Питание (дневник)",
)

fun progressOrder(m: AppModel): List<String> {
    val saved = m.storage.progOrder.filter { k -> progressCards.any { it.first == k } }
    // 5.55: новая карточка «Подходы на мышцы» — сверху, даже если порядок уже настроен
    return listOf("muscles").filter { it !in saved } + saved + progressCards.map { it.first }.filter { it !in saved && it != "muscles" }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProgressTab(m: AppModel, pad: PaddingValues) {
    var weeks by remember { mutableIntStateOf(12) }
    var cardsDialog by remember { mutableStateOf(false) }
    var ver by remember { mutableIntStateOf(0) }
    val today = LocalDate.now()
    val from = today.minusWeeks(weeks.toLong())
    val sessions = m.history.mapNotNull { s -> runCatching { LocalDate.parse(s.date) }.getOrNull()?.let { it to s } }
        .filter { !it.first.isBefore(from) }.sortedBy { it.first }
    val order = remember(ver) { progressOrder(m) }
    val hidden = remember(ver) { m.storage.progHidden }
    LazyColumn(
        Modifier.fillMaxSize().padding(pad),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).hScroll(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(4 to "4 недели", 12 to "12 недель", 52 to "год").forEach { (w, t) -> FilterChip(weeks == w, { weeks = w }, label = { Text(t) }) }
                }
                IconButton({ cardsDialog = true }) { Icon(Icons.Outlined.Tune, "Карточки: порядок и видимость") }
            }
        }
        order.filter { it !in hidden }.forEach { key ->
            when (key) {
                "weeks" -> item(key = key) {
                    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    val n = minOf(weeks, 12)
                    val bars = (n - 1 downTo 0).map { k ->
                        val start = monday.minusWeeks(k.toLong())
                        start.format(shortDate) to sessions.count { (d, _) -> !d.isBefore(start) && d.isBefore(start.plusWeeks(1)) }.toDouble()
                    }
                    ChartCard(
                        "Тренировки по неделям",
                        "Всего за период: ${sessions.size}" + (m.program?.days?.size?.let { " · план: $it в неделю" } ?: ""),
                    ) { BarChart(bars) }
                }
                "muscles" -> item(key = key) { MuscleSetsCard(m, today) }
                "sessions" -> if (sessions.isEmpty()) item(key = key) {
                    Text("Заверши тренировку — здесь появятся графики объёма и прогресса по упражнениям.", style = MaterialTheme.typography.bodyMedium)
                } else item(key = key) {
                    DetailCard(m, "sessions", "Тренировки", listOf(
                        DSeries("volume", "Объём", sessions.map { (d, s) -> d to sessionVolume(s).toDouble() }, { "${it.roundToInt()} повт." }, 1),
                        DSeries("sets", "Подходы", sessions.map { (d, s) -> d to s.exercises.sumOf { it.values.size }.toDouble() }, { "${it.roundToInt()}" }, 1),
                        DSeries("time", "Длительность", sessions.map { (d, s) -> d to s.durationSec / 60.0 }, { "${it.roundToInt()} мин" }, 0),
                    ), note = "Объём — сумма повторов за тренировку (без упражнений на время).")
                }
                "exercises" -> {
                    val names = sessions.flatMap { it.second.exercises }.filter { it.values.isNotEmpty() }.groupingBy { it.name }.eachCount()
                        .entries.sortedByDescending { it.value }.map { it.key }.take(15)
                    if (names.isNotEmpty()) item(key = key) {
                        DetailCard(m, "exercises", "Упражнения", names.map { n ->
                            val res = sessions.mapNotNull { (d, s) -> s.exercises.firstOrNull { it.name == n }?.let { d to it } }
                            val sec = res.firstOrNull()?.second?.seconds == true
                            DSeries(n, n, res.mapNotNull { (d, r) -> bestSet(r)?.let { d to it.toDouble() } }, { "${it.roundToInt()}" + if (sec) " с" else "" }, 1)
                        }, subtitle = "Лучший подход в каждой тренировке; в таблице — последний и изменение за период")
                    }
                }
                "body" -> m.tabitaData?.let { data ->
                    val days = data.days.filter { !it.date.isBefore(from) }
                    fun s(id: String, t: String, f: (TDay) -> Double?, fmt: (Double) -> String, good: Int = 0, sum: Boolean = false) =
                        DSeries(id, t, days.mapNotNull { d -> f(d)?.let { d.date to it } }, fmt, good, sum = sum)
                    val h = m.body.heightCm?.let { it / 100.0 }
                    val list = listOf(
                        s("w", "Вес", { it.weight }, { num1((it * 10).roundToInt() / 10.0) + " кг" }),
                        s("ffmi", "FFMI", { d -> d.ffmi ?: h?.let { hh -> if (d.weight != null && d.fat != null) d.weight * (1 - d.fat / 100) / (hh * hh) else null } },
                            { num1((it * 10).roundToInt() / 10.0) }, 1),
                        s("muscle", "Мышцы", { it.muscle }, { num1((it * 10).roundToInt() / 10.0) }, 1),
                        s("fat", "Жир", { it.fat }, { num1((it * 10).roundToInt() / 10.0) + " %" }, -1),
                        s("rhr", "Пульс покоя", { it.rhr }, { "${it.roundToInt()} уд/мин" }, -1),
                        s("sleep", "Сон", { it.sleepMin?.div(60) }, { num1((it * 10).roundToInt() / 10.0) + " ч" }, 1),
                        s("steps", "Шаги", { it.steps }, { "${it.roundToInt()}" }, 1, sum = true),
                    ).filter { it.points.size >= 2 }
                    if (list.isNotEmpty()) item(key = key) {
                        DetailCard(m, "body", "Тело и восстановление", list, subtitle = "Из дневника",
                            note = ReadinessCalc.weightTrend(data.days, today)?.let { t ->
                                "Тренд веса за 3 недели: " + (if (t >= 0) "+" else "−") + num1((kotlin.math.abs(t) * 10).roundToInt() / 10.0) + " кг/нед. " +
                                    "FFMI: около 18 — средне, 20–22 — хорошо тренирован."
                            })
                    }
                }
                "food" -> m.tabitaData?.let { data ->
                    val days = data.days.filter { !it.date.isBefore(from) }
                    val w = m.body.weightKg
                    val list = listOf(
                        DSeries("kcal", "Калории", days.mapNotNull { d -> d.kcal?.let { d.date to it } }, { "${it.roundToInt()} ккал" },
                            target = days.mapNotNull { it.tdee }.takeIf { it.isNotEmpty() }?.average()),
                        DSeries("bal", "Энергобаланс", days.mapNotNull { d -> if (d.kcal != null && d.tdee != null) d.date to d.kcal - d.tdee else null },
                            { "${it.roundToInt()} ккал" }, target = if (m.program?.goal == Goal.MASS) 300.0 else 0.0),
                        DSeries("p", "Белок", days.mapNotNull { d -> d.protein?.let { d.date to it } }, { "${it.roundToInt()} г" }, 1,
                            target = w?.let { it * ReadinessCalc.PROTEIN_PER_KG }),
                        DSeries("pkg", "Белок на кг", if (w == null) emptyList() else days.mapNotNull { d -> d.protein?.let { d.date to it / w } },
                            { num1((it * 10).roundToInt() / 10.0) + " г" }, 1, target = ReadinessCalc.PROTEIN_PER_KG),
                    ).filter { it.points.size >= 2 }
                    if (list.isNotEmpty()) item(key = key) {
                        DetailCard(m, "food", "Питание", list, subtitle = "Из дневника · зелёная линия — цель",
                            note = "Калории — цель: средний расход. Энергобаланс для набора массы: +200–400 ккал. Белок: от 1,6 г на кг веса.")
                    }
                }
            }
        }
        if (m.tabitaData == null && m.useTabita) item {
            Text(
                "Включи «Ещё → Тренировки → Что из дневника учитывать», чтобы видеть вес, мышцы, пульс покоя, сон и питание рядом с тренировками.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { Spacer(Modifier.size(8.dp)) }
    }
    if (cardsDialog) ProgressCardsDialog(m) { cardsDialog = false; ver++ }
}

/** Порядок и видимость карточек «Прогресса» (как фильтр графиков в Tabita). */
@Composable
private fun ProgressCardsDialog(m: AppModel, onClose: () -> Unit) {
    var order by remember { mutableStateOf(progressOrder(m)) }
    var hidden by remember { mutableStateOf(m.storage.progHidden) }
    fun save() { m.storage.progOrder = order; m.storage.progHidden = hidden }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Карточки") },
        text = {
            Column {
                order.forEachIndexed { i, k ->
                    val t = progressCards.first { it.first == k }.second
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(k !in hidden, { on -> hidden = if (on) hidden - k else hidden + k; save() })
                        Text(t, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        IconButton({ if (i > 0) { order = order.toMutableList().apply { add(i - 1, removeAt(i)) }; save() } }, enabled = i > 0) {
                            Icon(Icons.Outlined.KeyboardArrowUp, "Выше")
                        }
                        IconButton({ if (i < order.lastIndex) { order = order.toMutableList().apply { add(i + 1, removeAt(i)) }; save() } }, enabled = i < order.lastIndex) {
                            Icon(Icons.Outlined.KeyboardArrowDown, "Ниже")
                        }
                    }
                }
                androidx.compose.material3.TextButton({ order = progressCards.map { it.first }; hidden = emptySet(); save() }) { Text("Сбросить") }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClose) { Text("Готово") } },
    )
}

/** 5.55: рабочие подходы на группу мышц за эту неделю против ориентира 12–20 и средние за 4 недели. */
@Composable
fun MuscleSetsCard(m: AppModel, today: LocalDate) {
    val week = MuscleVolume.week(m.history, MuscleVolume.monday(today))
    val avg = MuscleVolume.average(m.history, today)
    val groups = MuscleVolume.order.filter { (week[it] ?: 0) > 0 || (avg[it] ?: 0.0) > 0 }
    Card(Modifier.fillMaxWidth().testTag("muscle_sets")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Подходы на мышцы за неделю", style = MaterialTheme.typography.titleMedium)
            if (groups.isEmpty()) Text("Появятся после первой тренировки.", style = MaterialTheme.typography.bodySmall)
            groups.forEach { g ->
                val n = week[g] ?: 0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(g.replaceFirstChar { it.uppercase() }, Modifier.width(76.dp), style = MaterialTheme.typography.bodyMedium)
                    Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth((n / MuscleVolume.HIGH.toFloat()).coerceIn(0f, 1f)).clip(RoundedCornerShape(6.dp))
                            .background(if (n >= MuscleVolume.LOW) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary))
                    }
                    Text("$n" + (avg[g]?.let { " · ~${"%.0f".format(it)}" } ?: ""), Modifier.width(64.dp), textAlign = TextAlign.End,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("Число — подходов на этой неделе, «~» — в среднем за 4 прошлые. Ориентир для роста у тренированных — 12–20 подходов на группу в неделю; новичку хватает меньше, наращивай постепенно — приложение само добавит подход, когда вес прибавить будет нечем.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
