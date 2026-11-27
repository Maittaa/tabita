package org.maittaa.tabita

import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.ui.text.withStyle
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

private enum class Mode(val ru: String) {
    WEEK("7 дней"), D30("30 дней"), MONTH("Месяц"), D90("90 дней"), YEAR("Год"), CUSTOM("Период…");
    val title get() = t(ru)
}

// ---------- модели карточек ----------

/** Что показывать крупно в компактной карточке. */
enum class Headline { AVG, LAST, SUM }

data class BarData(val label: String, val frac: Float, val text: String, val secondary: Float = 0f)
data class BodySeries(val id: String, val title: String, val unit: String, val values: List<Double?>, val dec: Int = 1, val target: Double? = null) {
    val fmt: (Double) -> String get() = { v ->
        val k = Math.pow(10.0, dec.toDouble())
        formatNumber(Math.round(v * k) / k) + bodyUnit(unit).let { if (it.isEmpty()) "" else " $it" }
    }
}

sealed class StatCard(val key: String, val groupRu: String, val title: String) {
    val group get() = t(groupRu)
}

class SeriesCard(
    key: String, group: String, title: String,
    val values: List<Double?>, val fmt: (Double) -> String,
    val headline: Headline = Headline.AVG, val good: Int = 0,
    val defType: ChartType = ChartType.LINE, val watch: Boolean = false,
    val extraStats: List<Pair<String, String>> = emptyList(), val note: String? = null,
    val target: Double? = null,
) : StatCard(key, group, title) {
    val present get() = values.filterNotNull()
    val headValue: Double? get() = when (headline) {
        Headline.AVG -> present.takeIf { it.isNotEmpty() }?.average()
        Headline.LAST -> present.lastOrNull()
        Headline.SUM -> present.sum()
    }
    val headLabel get() = when (headline) { Headline.AVG -> t("среднее"); Headline.LAST -> t("последнее"); Headline.SUM -> t("итого") }
    /** Динамика: для «последнего» — изменение за период, иначе — тренд (вторая половина − первая). */
    val dyn: Double? get() = if (headline == Headline.LAST) change(values) else trend(values)
    val dynLabel get() = if (headline == Headline.LAST) t("за период") else t("тренд")
}

class BarsCard(key: String, group: String, title: String, val rows: List<BarData>) : StatCard(key, group, title)
/** Карточка «как весы». [food] (5.19.5) — питание: среднее вместо последнего, норма линией и % от нормы в таблице. */
class BodyCard(key: String, title: String, val metrics: List<BodySeries>, val note: String? = null, group: String = "Весы", val food: Boolean = false) :
    StatCard(key, group, title)
/** Шкалы самочувствия: значение — в дни прохождения. */
data class WbSeries(val block: WBlock, val values: List<Double?>, val lastAll: Double?, val prevAll: Double?)
class WellbeingCard(key: String, title: String, val metrics: List<WbSeries>) : StatCard(key, "Самочувствие", title)
class NoteCard(key: String, val month: String) : StatCard(key, "Заметки", t("Итог месяца / идеи / наблюдения"))

/** Одна запись сводки заметок: день, пункт (если текстовых пунктов несколько) и текст. */
data class DiaryEntry(val date: LocalDate, val item: String?, val text: String)

/** Сводка всех текстовых заметок за выбранный период — одной большой заметкой в конце графиков. */
class DiaryCard(key: String, val entries: List<DiaryEntry>, val from: LocalDate, val to: LocalDate) :
    StatCard(key, "Заметки", t("Все заметки за период"))

private val groupOrder = listOf("Отметки", "Показатели", "Часы", "Весы", "Самочувствие", "Питание", "Финансы", "Списки", "Заметки")

/** Применить сохранённый порядок: известные — по нему, новые — на своих местах в конце. */
fun <T : StatCard> ordered(cards: List<T>, order: List<String>): List<T> {
    val idx = order.withIndex().associate { it.value to it.index }
    val res = cards.filter { it.key in idx }.sortedBy { idx[it.key] }.toMutableList()
    // новая карточка (5.19) встаёт сразу после своего соседа по умолчанию, а не в самый конец
    cards.forEachIndexed { i, c ->
        if (c.key in idx) return@forEachIndexed
        val prev = if (i > 0) cards[i - 1] else null
        res.add(if (prev == null) 0 else res.indexOf(prev) + 1, c)
    }
    return res
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(repo: Repo, prefs: Prefs, modifier: Modifier, openDay: (LocalDate) -> Unit = {},
                /** 5.24: графики тренировок (прогресс) — показываются в группе «Тренировки». */
                trainCharts: (@Composable (Modifier) -> Unit)? = null) {
    val template = collect(repo.template)
    val entries = collect(repo.entries)
    val notes = collect(repo.monthNotes)
    collect(repo.balance)
    collect(repo.checkups)
    collect(repo.profile)
    var order by remember { mutableStateOf(prefs.statOrder) }
    var mode by rememberSaveable { mutableStateOf(Mode.MONTH) }
    var monthStart by rememberSaveable { mutableStateOf(LocalDate.now().withDayOfMonth(1).key()) }
    var custom by rememberSaveable { mutableStateOf(LocalDate.now().minusDays(13).key() + "|" + LocalDate.now().key()) }
    var pickRange by remember { mutableStateOf(false) }
    var cols by remember { mutableIntStateOf(prefs.statCols) }
    var hidden by remember { mutableStateOf(prefs.statHidden) }
    var showFilter by remember { mutableStateOf(false) }
    var popup by remember { mutableStateOf<String?>(null) }
    var prefTick by remember { mutableIntStateOf(0) }

    val today = LocalDate.now()
    val (from, to) = when (mode) {
        Mode.WEEK -> today.minusDays(6) to today
        Mode.D30 -> today.minusDays(29) to today
        Mode.D90 -> today.minusDays(89) to today
        Mode.YEAR -> today.minusDays(364) to today
        Mode.MONTH -> LocalDate.parse(monthStart).let { it to it.plusMonths(1).minusDays(1) }
        Mode.CUSTOM -> custom.split("|").let { LocalDate.parse(it[0]) to LocalDate.parse(it[1]) }
    }
    val days = (0..ChronoUnit.DAYS.between(from, to)).map { from.plusDays(it) }
    val dayVals = days.map { entries[it.key()] ?: emptyMap() }
    val filled = dayVals.count { it.isNotEmpty() }
    // предыдущий период той же длины — для сравнения трат
    val prevVals = days.map { entries[it.minusDays(days.size.toLong()).key()] ?: emptyMap() }
    val raw = buildCards(repo, prefs, template, days, dayVals, prevVals, to, if (mode == Mode.MONTH) monthStart.take(7) else null)
    val cards = ordered(raw, order)
    var ctxSel by remember { mutableStateOf(prefs.statCtx) }
    val present = remember(cards, template) { cards.map { StatContext.of(it, template) }.toSet() }
    val shownCtx = StatContext.all.filter { it.id in present || (it.id == StatContext.TRAIN && trainCharts != null) }
    if (ctxSel != StatContext.ALL && shownCtx.none { it.id == ctxSel }) ctxSel = StatContext.ALL
    val visible = cards.filter { it.key !in hidden && (ctxSel == StatContext.ALL || StatContext.of(it, template) == ctxSel) }
    val ctx = Ctx(repo, prefs, days, notes, { prefTick++ }, prefTick, openDay)

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    var view by remember { mutableIntStateOf(prefs.statView) }
    Column(modifier.fillMaxSize()) {
    // 5.30: один ряд вместо трёх — группа графиков; период и вид — в строке инструментов
    // группы графиков по смыслу — фильтр сверху, всегда под рукой
    if (view == 0) Row(Modifier.fillMaxWidth().hScroll().padding(start = 12.dp, end = 12.dp, top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(ctxSel == StatContext.ALL, { ctxSel = StatContext.ALL; prefs.statCtx = ctxSel }, label = { Text(t("Все")) })
        shownCtx.forEach { c ->
            FilterChip(ctxSel == c.id, { ctxSel = c.id; prefs.statCtx = c.id; Log.d("Stats", "группа: ${c.id}") },
                label = { Text(t(c.ru)) }, leadingIcon = { Icon(c.icon, null, Modifier.size(16.dp)) })
        }
    }
    if (view == 0 && ctxSel == StatContext.TRAIN && trainCharts != null) trainCharts(Modifier.weight(1f).fillMaxSize())
    else
    Box(Modifier.weight(1f).fillMaxSize()) {
    LazyColumn(
        Modifier.fillMaxSize(), state = listState,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item("tools") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // период — одной кнопкой с меню
                var modeMenu by remember { mutableStateOf(false) }
                Box {
                    androidx.compose.material3.FilledTonalButton({ modeMenu = true }, contentPadding = PaddingValues(horizontal = 12.dp)) {
                        Text(mode.title); Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(18.dp))
                    }
                    androidx.compose.material3.DropdownMenu(modeMenu, { modeMenu = false }) {
                        Mode.entries.forEach { m ->
                            androidx.compose.material3.DropdownMenuItem({ Text(m.title) }, {
                                mode = m; modeMenu = false
                                if (m == Mode.CUSTOM) pickRange = true
                            })
                        }
                    }
                }
                TextButton(onClick = { showFilter = true }) {
                    Icon(Icons.Filled.FilterList, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
                    Text(if (hidden.isEmpty()) t("Все графики") else t("Показано %d из %d", visible.size, cards.size))
                }
                Spacer(Modifier.weight(1f))
                val colIcon = when (cols) { 1 -> Icons.Filled.ViewStream; 2 -> Icons.Filled.GridView; else -> Icons.Filled.ViewModule }
                IconButton({ cols = cols % 3 + 1; prefs.statCols = cols; Log.d("Stats", "в ряд: $cols") }) {
                    Icon(colIcon, t("%d в ряд", cols), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item("period") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (mode == Mode.MONTH) IconButton(onClick = { monthStart = LocalDate.parse(monthStart).minusMonths(1).key() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, t("Назад"))
                }
                Column(Modifier.weight(1f), horizontalAlignment = if (mode == Mode.MONTH) Alignment.CenterHorizontally else Alignment.Start) {
                    Text(
                        if (mode == Mode.MONTH) monthTitle(from) else "${from.ruShort()}.${from.year} – ${to.ruShort()}.${to.year}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(t("заполнено дней: %d из %d", filled, days.size), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (mode == Mode.MONTH) IconButton(onClick = { monthStart = LocalDate.parse(monthStart).plusMonths(1).key() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, t("Вперёд"))
                }
            }
        }

        // 5.63: наложение двух показателей и прошлого периода
        item("compare") {
            val prevDays = days.map { it.minusDays(days.size.toLong()) }
            val prevCards = remember(entries, template, from, to) {
                buildCards(repo, prefs, template, prevDays, prevDays.map { entries[it.key()] ?: emptyMap() },
                    prevDays.map { entries[it.minusDays(days.size.toLong()).key()] ?: emptyMap() }, prevDays.last(), null)
            }
            CompareCard(prefs, Compare.series(cards), Compare.series(prevCards), "${prevDays.first().ruShort()} – ${prevDays.last().ruShort()}")
        }
        if (visible.isEmpty()) item("empty") {
            Text(t("Нет данных за период или все графики скрыты фильтром."), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val grid = visible.filter { it !is NoteCard && it !is DiaryCard }
        val wide = visible.filter { it is NoteCard || it is DiaryCard }
        if (cols == 1) {
            grid.forEach { c -> item(c.key) { FullCard(c, ctx) } }
        } else {
            grid.chunked(cols).forEachIndexed { i, row ->
                item("row$i" + row.joinToString { it.key }) {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { c -> CompactCard(c, ctx, cols, Modifier.weight(1f).fillMaxHeight()) { popup = c.key } }
                        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        wide.forEach { c -> item(c.key) { FullCard(c, ctx) } }
        item("pad") { Spacer(Modifier.height(32.dp)) }
    }
    LazyScrollbar(listState, Modifier.align(Alignment.CenterEnd))
    }
    }

    // всплывающее окно с полным графиком
    popup?.let { key ->
        val c = cards.firstOrNull { it.key == key }
        if (c == null) popup = null
        else Dialog(onDismissRequest = { popup = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxWidth(0.95f), shape = RoundedCornerShape(20.dp), tonalElevation = 6.dp) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(c.group, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f).padding(start = 8.dp))
                        IconButton(onClick = { popup = null }) { Icon(Icons.Filled.Close, t("Закрыть")) }
                    }
                    FullCard(c, ctx)
                }
            }
        }
    }

    if (showFilter) FilterDialog(cards, hidden, onChange = { hidden = it; prefs.statHidden = it },
        defaults = raw.map { it.key },
        onOrder = { keys ->
            // сохраняем порядок, не теряя карточки, которых нет в этом периоде; пустой список — сброс
            order = if (keys.isEmpty()) emptyList() else keys + order.filter { it !in keys }
            prefs.statOrder = order
        },
        onDismiss = { showFilter = false })

    if (pickRange) {
        val parts = custom.split("|")
        val st = rememberDateRangePickerState(
            initialSelectedStartDateMillis = LocalDate.parse(parts[0]).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            initialSelectedEndDateMillis = LocalDate.parse(parts[1]).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickRange = false },
            confirmButton = {
                TextButton(onClick = {
                    val s = st.selectedStartDateMillis; val e = st.selectedEndDateMillis
                    if (s != null) {
                        val a = Instant.ofEpochMilli(s).atZone(ZoneOffset.UTC).toLocalDate()
                        val b = e?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() } ?: a
                        custom = a.key() + "|" + b.key()
                    }
                    pickRange = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickRange = false }) { Text(t("Отмена")) } },
        ) { DateRangePicker(st, Modifier.weight(1f)) }
    }
}

/** Общий контекст отрисовки карточек. */
class Ctx(
    val repo: Repo, val prefs: Prefs, val days: List<LocalDate>, val notes: Map<String, String>,
    val changed: () -> Unit, @Suppress("unused") val tick: Int,
    val openDay: (LocalDate) -> Unit = {},
)

// ---------- построение карточек из данных ----------

private fun buildCards(
    repo: Repo, prefs: Prefs, template: List<TemplateItem>, days: List<LocalDate>,
    dayVals: List<Map<String, String>>, prevVals: List<Map<String, String>>, to: LocalDate, month: String?,
): List<StatCard> {
    val out = mutableListOf<StatCard>()
    val n = days.size
    val prof = repo.profile.value

    // Индекс дня (как в «Итогах месяца»): только полные дни
    run {
        val cfg = prefs.monthCfg
        val md = MonthData(template, repo.entries.value, to, cfg)
        val idx = days.map { md.indexOn(it) }
        if (idx.count { it != null } >= 1) out += SeriesCard(
            "index", "Оценка", t("Индекс дня"), idx, { formatNumber(Math.round(it).toDouble()) },
            good = 1, defType = ChartType.LINE, target = cfg.target.toDouble(),
            extraStats = listOf(t("дней с индексом") to t("%d из %d", idx.count { it != null }, days.size),
                t("не ниже цели") to "${idx.count { it != null && it >= cfg.target }}"),
            note = t("Индекс 0–100 по пунктам из «Итогов месяца». Неполные дни не показываются. Линия — цель %d.", cfg.target),
        )
    }

    // Отметки
    val checks = template.filter { it.type == ItemType.CHECK }
    if (checks.isNotEmpty() && dayVals.any { d -> checks.any { d[it.id] != null } }) {
        val counts = checks.map { c -> Triple(c, dayVals.count { it[c.id] != null }, dayVals.count { it[c.id] == "2" }) }
        val mx = (counts.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
        out += BarsCard("checks", "Отметки", t("Отметки за период"), counts.map { (c, all, plus) ->
            BarData(c.displayName, all.toFloat() / mx, "$all" + if (plus > 0) " ($plus+)" else "", plus.toFloat() / mx)
        })
    }

    // Числовые пункты
    template.filter { it.isNumeric }.forEach { item ->
        if (item.source == Source.HC_SLEEP) { buildSleepCard(item, dayVals, prefs)?.let { out += it }; return@forEach }
        val values = dayVals.map { parseNumber(it[item.id]) }
        if (values.all { it == null }) return@forEach
        val whole = item.source == Source.HC_STEPS || item.source == Source.HC_EXERCISE || item.source == Source.HC_RESTING_HR
        val fmt: (Double) -> String = { v ->
            if (item.type == ItemType.DURATION) formatMinutes(v)
            else formatNumber(if (whole) Math.round(v).toDouble() else Math.round(v * 10) / 10.0) +
                if (item.unit.isNotEmpty()) " ${item.displayUnit}" else ""
        }
        val isWeight = item.source == Source.HC_WEIGHT
        val group = when { isWeight -> "Весы"; item.source != Source.NONE -> "Часы"; else -> "Показатели" }
        // тренировки: сколько раз какой вид за период
        val kindStats = if (item.source != Source.HC_EXERCISE) emptyList() else
            dayVals.flatMap { Workouts.sessions(it, item.id) }.groupBy { it.kind }
                .map { (k, l) -> Workouts.kind(k).title to t("%d раз · %s", l.size, formatMinutes(l.sumOf { it.min }.toDouble())) }
                .sortedByDescending { it.second.substringBefore(" ").toIntOrNull() ?: 0 }.take(4)
        // шаги → километры по длине шага из профиля
        val kmStats = if (item.source != Source.HC_STEPS || prof.h == null) emptyList() else {
            val km = values.mapNotNull { v -> v?.let { Anthro.stepsKm(prof, it) } }
            if (km.isEmpty()) emptyList() else listOf(
                t("км в день") to formatNumber(Math.round(km.average() * 10) / 10.0),
                t("всего км") to formatNumber(Math.round(km.sum()).toDouble()),
            )
        }
        out += SeriesCard(
            "num_" + item.id, group, item.displayName, values, fmt,
            extraStats = kindStats + kmStats,
            note = if (kindStats.isNotEmpty() && !prefs.countWalking) t("Ходьба в минуты тренировок не засчитывается.") else null,
            headline = if (isWeight) Headline.LAST else Headline.AVG,
            good = when (item.source) { Source.HC_SLEEP, Source.HC_STEPS, Source.HC_EXERCISE -> 1; Source.HC_RESTING_HR -> -1; else -> if (item.type == ItemType.SCALE) 1 else 0 },
            defType = if (item.source == Source.HC_STEPS || item.source == Source.HC_EXERCISE) ChartType.BARS else ChartType.LINE,
            watch = item.source != Source.NONE,
        )
    }

    // Весы openScale — одна группа
    template.filter { it.type == ItemType.BODY }.forEach { item ->
        val parsed = dayVals.map { BodyDay.parse(it[item.id]) }
        val ids = LinkedHashMap<String, BodyMetric>()
        parsed.filterNotNull().forEach { d -> d.metrics.forEach { ids.putIfAbsent(it.id, it) } }
        if (ids.isEmpty()) return@forEach
        val series = ids.values.sortedBy { BodyCatalog.order(it.id) }.map { m ->
            BodySeries(m.id, BodyCatalog.title(m), m.unit, parsed.map { d -> d?.metrics?.firstOrNull { it.id == m.id }?.value })
        }
        // 5.25: точность — сколько взвешиваний не утром и самый неправдоподобный скачок за период
        val notAm = parsed.count { it != null && !it.morning() }
        val jump = days.mapNotNull { d -> BodyCheck.jump(repo.entries.value, item.id, d) }
            .maxByOrNull { kotlin.math.abs(it.muscleKg ?: 0.0) + kotlin.math.abs(it.fatPct ?: 0.0) }
        val bodyNote = listOfNotNull(
            jump?.let { BodyCheck.text(it) },
            if (notAm > 0) t("Взвешиваний не утром: %d — жир и мышцы в эти дни менее точны.", notAm) else null,
            t("Состав тела весы оценивают по сопротивлению току — он зависит от воды. Смотрите тренд за 2–4 недели, а не отдельные дни."),
        ).joinToString("\n")
        out += BodyCard("body_" + item.id, item.displayName, series, note = bodyNote)
    }

    // Тело и рост (5.19): ИМТ на каждый день взвешивания, FFMI, FMI, талия/рост
    prof.h?.let { h ->
        val lean = dayVals.map { Anthro.leanOf(template, it) }
        val weight = dayVals.map { Anthro.weightOf(template, it) }
        val waist = dayVals.map { Anthro.bodyMetric(template, it, "waist") }
        val series = listOf(
            // ИМТ — по росту из профиля (у openScale может быть свой рост), его ИМТ — только если веса нет
            BodySeries("calc.bmi", t("ИМТ"), "", dayVals.zip(weight).map { (d, w) -> w?.let { Anthro.bmi(it, h) } ?: Anthro.bodyMetric(template, d, "bmi") }),
            BodySeries("calc.ffmi", "FFMI", "", lean.map { it?.let { Anthro.ffmi(it, h) } }),
            BodySeries("calc.ffmi_norm", t("FFMI норм."), "", lean.map { it?.let { Anthro.ffmiNorm(it, h) } }),
            BodySeries("calc.fmi", "FMI", "", weight.zip(lean).map { (w, l) -> if (w != null && l != null) Anthro.fmi(w - l, h) else null }),
            BodySeries("calc.whtr", t("Талия/рост"), "", dayVals.zip(waist).map { (d, wst) -> Anthro.bodyMetric(template, d, "whtr") ?: wst?.let { it / (h * 100) } }, dec = 2),
        ).filter { s -> s.values.any { it != null } }
        if (series.isNotEmpty()) {
            val (lo, hi) = Anthro.normalWeight(h)
            out += BodyCard("anthro", t("Тело и рост"), series, note = listOf(
                t("ИМТ: норма 18,5–25 — при росте %s см это %s–%s кг.", formatNumber(prof.heightCm!!), formatNumber(Math.round(lo).toDouble()), formatNumber(Math.round(hi).toDouble())),
                t("FFMI — безжировая масса на рост²: около 18 — средне, 20–22 — хорошо тренирован, 22–25 — очень много мышц. «Норм.» — с поправкой на рост."),
                if (prof.female) t("FMI — жир на рост²: для женщин 5–9 — норма.") else t("FMI — жир на рост²: для мужчин 3–6 — норма."),
                t("Талия/рост: меньше 0,5 — хорошо."),
            ).joinToString("\n"))
        }
    }

    // Самочувствие: все шкалы чекапа в одной карточке, как весы
    // результаты чекапов показываем, даже если пункт чекапа удалён из шаблона
    if (template.any { it.type == ItemType.WELLBEING } || repo.checkups.value.isNotEmpty()) {
        val cks = repo.checkups.value
        val series = Wellbeing.blocks.mapNotNull { b ->
            val l = cks.filter { b.id in it.results }.sortedBy { it.at }
            if (l.isEmpty()) return@mapNotNull null
            val byDate = l.associate { it.date to it.results[b.id]!!.score }
            WbSeries(b, days.map { byDate[it.key()] }, l.last().results[b.id]!!.score, l.getOrNull(l.size - 2)?.results?.get(b.id)?.score)
        }
        val withTotal = cks.filter { it.overall != null }.sortedBy { it.at }
        val totalSeries = withTotal.takeIf { it.isNotEmpty() }?.let { l ->
            val byDate = l.associate { it.date to it.overall!! }
            WbSeries(Wellbeing.total, days.map { byDate[it.key()] }, l.last().overall, l.getOrNull(l.size - 2)?.overall)
        }
        if (series.isNotEmpty()) out += WellbeingCard("wellbeing", t("Самочувствие"), listOfNotNull(totalSeries) + series)
    }

    // Вода: среднее, всего выпито, сколько дней норма выполнена
    template.filter { it.type == ItemType.WATER }.forEach { item ->
        val values = dayVals.map { parseNumber(it[item.id]) }
        if (values.all { it == null }) return@forEach
        // 5.42: норма своя на каждый день; линия — средняя норма периода
        val norms = dayVals.map { Hydration.normOf(item, it) }
        val norm = Math.round(norms.average() * 10) / 10.0
        val p = values.filterNotNull()
        out += SeriesCard(
            "water_" + item.id, "Показатели", item.displayName, values, ::formatLiters,
            good = 1, defType = ChartType.BARS, target = norm,
            extraStats = listOf(
                t("выпито всего") to formatLiters(p.sum()),
                t("норма выполнена") to t("%d из %d дн.", values.indices.count { values[it] != null && values[it]!! >= norms[it] }, p.size),
            ),
        )
    }

    // Питание
    template.filter { it.type == ItemType.MEALS }.forEach { item ->
        val meals = dayVals.map { parseMeals(it[item.id]) }
        val kcal = meals.map { mealKcal(it) }
        if (kcal.all { it == null }) return@forEach
        val all = meals.flatten(); val k = kcal.count { it != null }.coerceAtLeast(1)
        fun avg(f: (Meal) -> Double?) = formatNumber(Math.round(all.mapNotNull(f).sum() / k).toDouble())
        out += SeriesCard(
            "kcal_" + item.id, "Питание", t("%s: калории", item.displayName), kcal,
            { formatNumber(Math.round(it).toDouble()) + " " + t("ккал") }, defType = ChartType.BARS,
            note = t("В среднем за день: Б %s г · Ж %s г · У %s г", avg { it.protein }, avg { it.fat }, avg { it.carbs }),
        )
        // БЖУ «как весы» (5.19.5): калории, белки, жиры, углеводы, белок на кг и доли — среднее, норма, % от нормы
        val macros = meals.map { mealMacros(it) }
        if (macros.any { it != null }) {
            val ents = repo.entries.value
            val ok = macros.indices.filter { macros[it] != null }
            val wts = days.map { Anthro.weightOn(template, ents, it) }
            val burn = if (prof.h != null && prof.age() != null) days.map { Anthro.energyOn(prof, template, ents, it)?.total } else days.map { null }
            fun avgOn(l: List<Double?>) = ok.mapNotNull { l[it] }.takeIf { it.isNotEmpty() }?.average()
            val avgBurn = avgOn(burn); val avgW = avgOn(wts)
            fun share(f: (Macros) -> Double, k: Double) = macros.map { m ->
                m?.let { val tot = 4 * it.protein + 9 * it.fat + 4 * it.carbs; if (tot > 0) f(it) * k / tot * 100 else null }
            }
            val g = t("г")
            val series = listOf(
                BodySeries("calc.kcal_in", t("Калории"), "kcal", kcal.mapIndexed { i, v -> if (macros[i] != null || v != null) v else null }, dec = 0, target = avgBurn),
                BodySeries("calc.protein_g", t("Белки"), g, macros.map { it?.protein }, dec = 0, target = avgW?.let { it * DayNorms.P_LO }),
                BodySeries("calc.fat_g", t("Жиры"), g, macros.map { it?.fat }, dec = 0, target = avgBurn?.let { it * 0.275 / 9 }),
                BodySeries("calc.carbs_g", t("Углеводы"), g, macros.map { it?.carbs }, dec = 0, target = avgBurn?.let { it * 0.55 / 4 }),
                BodySeries("calc.protein_kg", t("Белок на кг"), g, macros.zip(wts).map { (m, w) -> if (m != null && w != null && w > 0) m.protein / w else null }, dec = 1, target = DayNorms.P_LO),
                BodySeries("calc.pct_p", t("Доля белков"), "%", share({ it.protein }, 4.0), dec = 0),
                BodySeries("calc.pct_f", t("Доля жиров"), "%", share({ it.fat }, 9.0), dec = 0, target = 27.5),
                BodySeries("calc.pct_c", t("Доля углеводов"), "%", share({ it.carbs }, 4.0), dec = 0, target = 55.0),
            ).filter { s -> s.values.any { it != null } }
            out += BodyCard("food_" + item.id, t("%s: БЖУ", item.displayName), series, group = "Питание", food = true, note = listOf(
                t("Среднее — по дням, когда записана еда. В таблице справа — сколько это от нормы."),
                if (avgBurn != null) t("Норма: калории — расход по профилю, белок — от 1,6 г на кг веса, жиры ≈ 27 % и углеводы ≈ 55 % калорий.")
                else t("Заполните «Профиль» (рост и год рождения) — появятся нормы калорий, жиров и углеводов."),
            ).joinToString("\n"))
        }
        // энергобаланс (5.19): съедено − расход по профилю
        if (prof.h != null && prof.age() != null) {
            val en = days.map { Anthro.energyOn(prof, template, repo.entries.value, it) }
            val bal = kcal.zip(en).map { (e, x) -> if (e != null && x != null) e - x.total else null }
            val ok = bal.indices.filter { bal[it] != null }
            if (ok.isNotEmpty()) {
                val sum = ok.sumOf { bal[it]!! }
                out += SeriesCard(
                    "energy_" + item.id, "Питание", t("Энергобаланс: съедено − расход"), bal, Anthro::balKcal,
                    defType = ChartType.BARS,
                    extraStats = listOf(
                        t("съедено в день") to Anthro.kcal(ok.map { kcal[it]!! }.average()),
                        t("расход в день") to Anthro.kcal(ok.map { en[it]!!.total }.average()),
                        t("из него тренировки") to Anthro.kcal(ok.map { en[it]!!.workouts }.average()),
                        t("за период") to Anthro.signedKcal(sum),
                        t("≈ в весе") to (if (sum < 0) "−" else "+") + formatNumber(Math.round(Math.abs(sum) / 770.0) / 10.0) + " " + t("кг"),
                    ),
                    note = t("Плюс — профицит (для набора массы нужен небольшой, 200–400 ккал), минус — дефицит. Расход — оценка: базовый обмен по Миффлину – Сан Жеору × 1,2 + шаги сверх 3000 + тренировки. Дни без записей о еде не считаются; если что-то не записано, будет ложный дефицит."),
                )
            }
        }
        val gly = meals.map { Glycemic.day(it) }
        if (gly.any { it != null }) {
            val g = gly.filterNotNull()
            out += SeriesCard(
                "gly_" + item.id, "Питание", t("Гликемическая оценка"), gly.map { it?.score?.toDouble() },
                { formatNumber(Math.round(it).toDouble()) }, good = 1, defType = ChartType.BARS, target = 80.0,
                extraStats = listOfNotNull(
                    t("нагрузка в день") to formatNumber(Math.round(g.map { it.load }.average()).toDouble()),
                    g.mapNotNull { it.avgGi }.takeIf { it.isNotEmpty() }?.let { t("ср. ГИ") to formatNumber(Math.round(it.average()).toDouble()) },
                    t("ровных дней") to t("%d из %d", g.count { it.score >= 80 }, g.size),
                ),
                note = t("100 — ровный сахар: низкая нагрузка без пиков. Линия — 80 баллов."),
            )
        }
    }

    // Финансы
    template.filter { it.type == ItemType.EXPENSES }.forEach { item ->
        val per = dayVals.map { parsePurchases(it[item.id]) }
        val all = per.flatten()
        if (all.isEmpty()) return@forEach
        val spentDays = per.map { l -> l.filter { !it.income }.sumOf { it.total }.takeIf { it > 0 } }
        val spent = all.filter { !it.income }.sumOf { it.total }
        val income = all.filter { it.income }.sumOf { it.total }
        val bal = repo.balanceOn(to.coerceAtMost(LocalDate.now()).key())
        out += SeriesCard(
            "money_" + item.id, "Финансы", t("%s: расходы", item.displayName), spentDays, ::formatMoney,
            headline = Headline.SUM, good = -1, defType = ChartType.BARS,
            extraStats = listOfNotNull(
                t("в день") to formatMoney(spent / n),
                if (income > 0) t("доход") to formatMoney(income) else null,
                bal?.let { t("баланс") to formatMoney(it) },
            ),
        )
        val byCat = all.filter { !it.income }.groupBy { it.category.ifEmpty { "Другое" } }.mapValues { e -> e.value.sumOf { it.total } }
        if (byCat.isNotEmpty()) {
            val prev = prevVals.flatMap { parsePurchases(it[item.id]) }.filter { !it.income }
            out += DonutCard("cat_" + item.id, t("Траты по категориям"), spent, prev.takeIf { it.isNotEmpty() }?.sumOf { it.total },
                byCat.entries.sortedByDescending { it.value }.map { Slice(it.key, it.value) },
                items = days.zip(per).flatMap { (d, l) -> l.filter { !it.income }.map { d to it } },
                prevByCat = prev.groupBy { catOf(it) }.mapValues { e -> e.value.sumOf { it.total } })
        }
        if (income > 0 || spent > 0) {
            val (labels, inc, exp) = flowBuckets(days, per)
            out += FlowCard("flow_" + item.id, if (days.size > 14) t("Доходы и расходы по неделям") else t("Доходы и расходы по дням"), labels, inc, exp)
        }
        val byName = all.filter { !it.income && it.name.isNotBlank() }.groupBy { it.name.trim().lowercase() }
        if (byName.isNotEmpty()) {
            val top = byName.values.sortedByDescending { l -> l.sumOf { it.total } }.take(15)
            val mx = top.first().sumOf { it.total }
            out += BarsCard("top_" + item.id, "Финансы", t("Покупки за период"), top.map { l ->
                val q = l.sumOf { it.qty }; val sum = l.sumOf { it.total }
                BarData("${l.last().name} · ${formatMoney(sum / q)}/${t(l.last().unit)}", (sum / mx).toFloat(), formatMoney(sum))
            })
        }
    }

    // Списки (кофе, чай…)
    template.filter { it.type == ItemType.MULTI }.forEach { item ->
        val dCount = mutableMapOf<String, Int>(); val total = mutableMapOf<String, Int>()
        dayVals.forEach { d -> parseCounts(d[item.id]).forEach { (k, c) -> dCount[k] = (dCount[k] ?: 0) + 1; total[k] = (total[k] ?: 0) + c } }
        if (dCount.isEmpty()) return@forEach
        val src = if (item.counter) total else dCount
        val mx = src.values.max()
        out += BarsCard("multi_" + item.id, "Списки",
            if (item.counter) t("%s: сколько всего", item.displayName) else t("%s: сколько дней", item.displayName),
            src.entries.sortedByDescending { it.value }.map { (opt, c) ->
                BarData(t(opt), c.toFloat() / mx, if (item.counter) t("%d за %d дн.", c, dCount[opt] ?: 0) else c.toString())
            })
    }

    if (month != null) out += NoteCard("note", month)

    // сводка заметок: все текстовые пункты по дням периода
    val texts = template.filter { it.type == ItemType.TEXT }
    if (texts.isNotEmpty()) {
        val entries = days.flatMap { d ->
            val day = repo.entries.value[d.key()] ?: emptyMap()
            texts.mapNotNull { it0 -> day[it0.id]?.trim()?.takeIf { it.isNotEmpty() }?.let { DiaryEntry(d, if (texts.size > 1) it0.displayName else null, it) } }
        }
        out += DiaryCard("diary", entries, days.first(), days.last())
    }
    // заметки всегда в самом конце, сводка — последней
    return out.sortedBy { groupOrder.indexOf(it.groupRu) * 2 + if (it is DiaryCard) 1 else 0 }
}

// ---------- отрисовка ----------

@Composable
internal fun ChartTypeMenu(key: String, def: ChartType, ctx: Ctx) {
    var open by remember { mutableStateOf(false) }
    val cur = ctx.prefs.chartType(key, def)
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.AutoMirrored.Filled.ShowChart, t("Вид графика")) }
        DropdownMenu(open, { open = false }) {
            ChartType.entries.forEach { tp ->
                DropdownMenuItem(
                    text = { Text(tp.title) },
                    leadingIcon = { if (tp == cur) Icon(Icons.Filled.Check, null) else Spacer(Modifier.size(24.dp)) },
                    onClick = { ctx.prefs.setChartType(key, tp); open = false; ctx.changed(); Log.d("Stats", "вид $key: $tp") },
                )
            }
        }
    }
}

@Composable
fun FullCard(c: StatCard, ctx: Ctx) {
    when (c) {
        is SeriesCard -> SectionCard(c.title, watch = c.watch, action = { ChartTypeMenu(c.key, c.defType, ctx) }) {
            Row(Modifier.hScroll(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                c.headValue?.let { Stat(c.headLabel, c.fmt(it)) }
                if (c.headline != Headline.SUM && c.present.isNotEmpty()) {
                    Stat(t("мин."), c.fmt(c.present.min())); Stat(t("макс."), c.fmt(c.present.max()))
                }
                c.extraStats.forEach { (l, v) -> Stat(l, v) }
                c.dyn?.let { Stat(c.dynLabel, deltaText(it, c.fmt), deltaColor(it, c.good)) }
            }
            Spacer(Modifier.size(8.dp))
            Chart(c.values, ctx.days, ctx.prefs.chartType(c.key, c.defType), c.fmt, target = c.target)
            c.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
        }

        is BarsCard -> SectionCard(c.title) {
            c.rows.forEach { BarRow(it.label, it.frac, it.text, it.secondary, valueWidth = if (it.text.length > 7) 96 else 64) }
        }

        is BodyCard -> BodyFull(c, ctx)
        is SleepCard -> SleepFull(c, ctx)
        is WellbeingCard -> WbFull(c, ctx)
        is DonutCard -> DonutFull(c)
        is FlowCard -> FlowFull(c)

        is DiaryCard -> DiaryFull(c, ctx)
        is NoteCard -> SectionCard(c.title) {
            var text by remember(c.month) { mutableStateOf(ctx.notes[c.month] ?: "") }
            OutlinedTextField(
                text, { text = it; ctx.repo.setMonthNote(c.month, it) },
                Modifier.fillMaxWidth(), minLines = 4,
                placeholder = { Text(t("Что получилось, что изменить в следующем месяце…")) },
            )
        }
    }
}

/** Сводка заметок: даты по порядку, тап по дате — открыть день. */
@Composable
private fun DiaryFull(c: DiaryCard, ctx: Ctx) {
    var newestFirst by remember { mutableStateOf(ctx.prefs.diaryNewest) }
    var all by remember(c.from, c.to) { mutableStateOf(false) }
    val byDay = c.entries.groupBy { it.date }.toList().let { if (newestFirst) it.sortedByDescending { p -> p.first } else it.sortedBy { p -> p.first } }
    val limit = 30
    val shown = if (all) byDay else byDay.take(limit)
    var hiddenText by remember { mutableStateOf(ctx.prefs.diaryHidden) }
    SectionCard(c.title, action = {
        if (byDay.size > 1 && !hiddenText) IconButton(onClick = { newestFirst = !newestFirst; ctx.prefs.diaryNewest = newestFirst }) {
            Icon(Icons.Filled.SwapVert, if (newestFirst) t("сначала новые") else t("сначала старые"))
        }
        IconButton(onClick = { hiddenText = !hiddenText; ctx.prefs.diaryHidden = hiddenText }) {
            Icon(if (hiddenText) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (hiddenText) t("Показать") else t("Скрыть"))
        }
    }) {
        Text(
            t("%s — %s · записей: %d из %d дн.", I18n.dayTitle(c.from), I18n.dayTitle(c.to), byDay.size, ctx.days.size) +
                if (!hiddenText && byDay.size > 1) " · " + (if (newestFirst) t("сначала новые") else t("сначала старые")) else "",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (hiddenText) {
            Text(t("Текст заметок скрыт — нажмите на глаз, чтобы показать."), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 6.dp))
            return@SectionCard
        }
        if (byDay.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(t("За этот период заметок нет."), color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@SectionCard
        }
        var prevMonth = -1
        shown.forEach { (d, list) ->
            if (d.monthValue != prevMonth && (c.from.monthValue != c.to.monthValue || c.from.year != c.to.year)) {
                prevMonth = d.monthValue
                Text("${I18n.month(d.monthValue)} ${d.year}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp))
            }
            Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Text(
                    I18n.dayTitle(d), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { ctx.openDay(d) }.padding(vertical = 2.dp),
                )
                list.forEach { e ->
                    Text(
                        if (e.item != null) androidx.compose.ui.text.buildAnnotatedString {
                            withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.SemiBold)) { append(e.item + ": ") }
                            append(e.text)
                        } else androidx.compose.ui.text.AnnotatedString(e.text),
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
        if (byDay.size > limit && !all) TextButton(onClick = { all = true }, Modifier.padding(top = 6.dp)) {
            Text(t("Показать все (%d)", byDay.size))
        }
        Text(t("Нажмите на дату, чтобы открыть день."), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 8.dp))
    }
}

/** Группа «Весы»: выбор показателя, один график, таблица всех показателей с динамикой. */
@Composable
private fun BodyFull(c: BodyCard, ctx: Ctx) {
    var sel by remember { mutableStateOf(if (c.food) ctx.prefs.foodSelected else ctx.prefs.bodySelected) }
    fun select(id: String) { sel = id; if (c.food) ctx.prefs.foodSelected = id else ctx.prefs.bodySelected = id }
    val cur = c.metrics.firstOrNull { it.id == sel } ?: c.metrics.first()
    val key = c.key + "_" + BodyCatalog.key(cur.id)
    SectionCard(c.title, action = { ChartTypeMenu(key, if (c.food) ChartType.BARS else ChartType.SMOOTH, ctx) }) {
        Row(Modifier.hScroll(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            c.metrics.forEach { m ->
                FilterChip(selected = m.id == cur.id, onClick = { select(m.id) },
                    leadingIcon = { Icon(BodyCatalog.icon(m.id), null, Modifier.size(18.dp)) }, label = { Text(m.title) })
            }
        }
        val p = cur.values.filterNotNull()
        val ch = change(cur.values)
        Row(Modifier.padding(top = 6.dp).hScroll(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (c.food) {
                if (p.isNotEmpty()) Stat(t("в среднем"), cur.fmt(p.average()))
                cur.target?.let { tg -> Stat(t("норма"), "≈ " + cur.fmt(tg)) }
                cur.target?.let { tg -> if (p.isNotEmpty()) Stat(t("дней в норме"), t("%d из %d", p.count { it in tg * 0.9..tg * 1.15 }, p.size)) }
            } else {
                p.lastOrNull()?.let { Stat(t("последнее"), cur.fmt(it)) }
                ch?.let { Stat(t("за период"), deltaText(it, cur.fmt), deltaColor(it, BodyCatalog.good(cur.id))) }
            }
            if (p.isNotEmpty()) { Stat(t("мин."), cur.fmt(p.min())); Stat(t("макс."), cur.fmt(p.max())) }
        }
        Spacer(Modifier.size(8.dp))
        Chart(cur.values, ctx.days, ctx.prefs.chartType(key, if (c.food) ChartType.BARS else ChartType.SMOOTH), cur.fmt, target = cur.target)
        Spacer(Modifier.size(8.dp))
        HorizontalDivider()
        // таблица: все показатели, изменение за период и мини-график
        c.metrics.forEach { m ->
            val last = m.values.lastOrNull { it != null }
            val d = change(m.values)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(if (m.id == cur.id) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { select(m.id) }
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(BodyCatalog.icon(m.id), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text(m.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (c.food) {
                    // питание: среднее за период и сколько это от нормы
                    val a = m.values.filterNotNull().takeIf { it.isNotEmpty() }?.average()
                    Text(a?.let(m.fmt) ?: "—", Modifier.width(88.dp).padding(start = 4.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    val pc = if (a != null && m.target != null && m.target > 0) Math.round(a / m.target * 100).toInt() else null
                    Text(pc?.let { "$it%" } ?: "", Modifier.width(72.dp), style = MaterialTheme.typography.labelMedium, maxLines = 1,
                        color = when { pc == null -> MaterialTheme.colorScheme.onSurfaceVariant; pc in 90..115 -> Color(0xFF3FA66B); else -> MaterialTheme.colorScheme.onSurfaceVariant })
                } else {
                Text(last?.let(m.fmt) ?: "—", Modifier.width(88.dp).padding(start = 4.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(deltaText(d, m.fmt).replace("▲ ", "▲").replace("▼ ", "▼"), Modifier.width(72.dp),
                    style = MaterialTheme.typography.labelMedium, color = deltaColor(d, BodyCatalog.good(m.id)), maxLines = 1)
                }
                Sparkline(m.values, Modifier.width(48.dp).height(22.dp))
            }
        }
        c.note?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** Группа «Самочувствие»: выбор шкалы, график, таблица всех шкал с полосой и изменением. */
@Composable
private fun WbFull(c: WellbeingCard, ctx: Ctx) {
    var sel by remember { mutableStateOf(ctx.prefs.wbSelected) }
    val cur = c.metrics.firstOrNull { it.block.id == sel } ?: c.metrics.first()
    val b = cur.block
    val fmt: (Double) -> String = { b.format(it) }
    val good = if (b.higherIsWorse) -1 else 1
    SectionCard(c.title, action = { ChartTypeMenu("wb_" + b.id, ChartType.LINE, ctx) }) {
        Row(Modifier.hScroll(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            c.metrics.forEach { m ->
                FilterChip(selected = m.block.id == b.id, onClick = { sel = m.block.id; ctx.prefs.wbSelected = m.block.id },
                    label = { Text(m.block.title.s) })
            }
        }
        val p = cur.values.filterNotNull()
        val ch = change(cur.values)
        Row(Modifier.padding(top = 6.dp).hScroll(), horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            cur.lastAll?.let { Stat(t("последнее"), fmt(it) + " / " + fmt(b.max)) }
            ch?.let { Stat(t("за период"), deltaText(it, fmt), deltaColor(it, good)) }
            if (p.size > 1) { Stat(t("мин."), fmt(p.min())); Stat(t("макс."), fmt(p.max())) }
        }
        cur.lastAll?.let { Box(Modifier.padding(top = 4.dp)) { BandChip(b, it) } }
        Spacer(Modifier.size(8.dp))
        if (p.isEmpty()) Text(t("За этот период прохождений нет — выберите период длиннее."), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        else Chart(cur.values, ctx.days, ctx.prefs.chartType("wb_" + b.id, ChartType.LINE), fmt)
        Spacer(Modifier.size(8.dp))
        HorizontalDivider()
        c.metrics.forEach { m ->
            val d = m.lastAll?.let { l -> m.prevAll?.let { l - it } }
            val g = if (m.block.higherIsWorse) -1 else 1
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(if (m.block.id == b.id) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { sel = m.block.id; ctx.prefs.wbSelected = m.block.id }
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(m.block.title.s, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(m.lastAll?.let { m.block.format(it) } ?: "—", Modifier.width(40.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Box(Modifier.width(118.dp)) { m.lastAll?.let { BandChip(m.block, it) } }
                Text(deltaText(d, { m.block.format(it) }).replace("▲ ", "▲").replace("▼ ", "▼"), Modifier.width(48.dp),
                    style = MaterialTheme.typography.labelMedium, color = deltaColor(d, g), maxLines = 1)
            }
        }
        Text(t("Изменение — с прошлого прохождения. Это скрининг, а не диагноз."), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Компактная карточка для сетки 2–3 в ряд; по нажатию — всплывающее окно. */
@Composable
private fun CompactCard(c: StatCard, ctx: Ctx, cols: Int, modifier: Modifier, onOpen: () -> Unit) {
    Card(
        onClick = onOpen, modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = cardBorder(),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(c.title, style = MaterialTheme.typography.labelMedium, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            val big = if (cols == 2) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium
            when (c) {
                is SeriesCard -> {
                    Text(c.headValue?.let(c.fmt) ?: "—", style = big, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(
                        c.headLabel + " · " + deltaText(c.dyn, c.fmt), style = MaterialTheme.typography.labelSmall,
                        color = deltaColor(c.dyn, c.good), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.weight(1f).height(6.dp))
                    val tp = ctx.prefs.chartType(c.key, c.defType)
                    Sparkline(
                        if (tp == ChartType.SMOOTH) movingAvg(c.values) else c.values,
                        Modifier.fillMaxWidth().height(36.dp), bars = tp == ChartType.BARS || tp == ChartType.WEEKLY,
                    )
                }
                is WellbeingCard -> {
                    val m = c.metrics.firstOrNull { it.block.id == ctx.prefs.wbSelected } ?: c.metrics.first()
                    Text(m.block.title.s, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    Text(m.lastAll?.let { m.block.format(it) } ?: "—", style = big, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    m.lastAll?.let { BandChip(m.block, it) }
                    Spacer(Modifier.weight(1f).height(6.dp))
                    Sparkline(m.values, Modifier.fillMaxWidth().height(36.dp))
                    Text(t("+ ещё %d показателей", c.metrics.size - 1), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline, maxLines = 1)
                }
                is SleepCard -> SleepCompact(c, big)
                is BodyCard -> {
                    val m = c.metrics.firstOrNull { it.id == (if (c.food) ctx.prefs.foodSelected else ctx.prefs.bodySelected) } ?: c.metrics.first()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(BodyCatalog.icon(m.id), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text(m.title, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                    Text(m.values.lastOrNull { it != null }?.let(m.fmt) ?: "—", style = big, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    val d = change(m.values)
                    Text(deltaText(d, m.fmt), style = MaterialTheme.typography.labelSmall, color = deltaColor(d, BodyCatalog.good(m.id)), maxLines = 1)
                    Spacer(Modifier.weight(1f).height(6.dp))
                    Sparkline(movingAvg(m.values), Modifier.fillMaxWidth().height(36.dp))
                    Text(t("+ ещё %d показателей", c.metrics.size - 1), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline, maxLines = 1)
                }
                is BarsCard -> {
                    Spacer(Modifier.height(4.dp))
                    c.rows.take(if (cols == 2) 4 else 3).forEach { BarRow(it.label, it.frac, it.text.substringBefore(" за ").substringBefore(" (").removeSuffix(" ₽").removeSuffix(" ${I18n.currency}"), it.secondary, compact = true) }
                    if (c.rows.size > 4) Text(t("+ ещё %d", c.rows.size - 4), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
                is NoteCard -> Text(ctx.notes[c.month] ?: "—", maxLines = 4, style = MaterialTheme.typography.bodySmall)
                is DiaryCard -> Text(if (ctx.prefs.diaryHidden) t("скрыто") else c.entries.lastOrNull()?.text ?: "—", maxLines = 4, style = MaterialTheme.typography.bodySmall)
                is DonutCard -> DonutCompact(c, cols)
                is FlowCard -> {
                    val net = c.inc - c.exp
                    Text((if (net >= 0) "+" else "") + formatMoney(net), style = big, fontWeight = FontWeight.SemiBold, maxLines = 1,
                        color = if (net >= 0) androidx.compose.ui.graphics.Color(0xFF3FA66B) else MaterialTheme.colorScheme.error)
                    Text(t("итог периода"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f).height(6.dp))
                    FlowBars(c, Modifier.fillMaxWidth().height(44.dp), labels = false)
                }
            }
        }
    }
}

/** Графики: какие показывать и в каком порядке. */
@Composable
private fun FilterDialog(cards: List<StatCard>, hidden: Set<String>, onChange: (Set<String>) -> Unit,
                         defaults: List<String>, onOrder: (List<String>) -> Unit, onDismiss: () -> Unit) {
    var list by remember { mutableStateOf(cards.map { it.key }) }
    val byKey = cards.associateBy { it.key }
    fun move(i: Int, d: Int) {
        val j = i + d; if (j !in list.indices) return
        list = list.toMutableList().also { val x = it[i]; it[i] = it[j]; it[j] = x }
        onOrder(list)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Графики: порядок и видимость")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(t("Галочка — показывать, стрелки — место на экране."), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                list.forEachIndexed { i, key ->
                    val c = byKey[key] ?: return@forEachIndexed
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(key !in hidden, { onChange(if (it) hidden - key else hidden + key) })
                        Column(Modifier.weight(1f).clickable { onChange(if (key in hidden) hidden - key else hidden + key) }) {
                            Text(c.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = if (key in hidden) MaterialTheme.colorScheme.outline else androidx.compose.ui.graphics.Color.Unspecified)
                            Text(c.group, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { move(i, -1) }, enabled = i > 0, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.ArrowUpward, t("Выше"), Modifier.size(18.dp))
                        }
                        IconButton(onClick = { move(i, 1) }, enabled = i < list.size - 1, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.ArrowDownward, t("Ниже"), Modifier.size(18.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("Готово")) } },
        dismissButton = {
            Row {
                TextButton(onClick = { onChange(emptySet()) }) { Text(t("Показать все")) }
                TextButton(onClick = { onOrder(emptyList()); list = defaults }) { Text(t("Сбросить порядок")) }
            }
        },
    )
}


/**
 * 5.24: группы графиков по смыслу — для фильтра на экране «Графики».
 * Группа карточки определяется по ключу и источнику её пункта.
 */
data class StatContext(val id: String, val ru: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    companion object {
        const val ALL = "all"
        const val TRAIN = "train"
        val all = listOf(
            StatContext("body", "Тело", Icons.Filled.MonitorWeight),
            StatContext("sleep", "Сон и пульс", Icons.Filled.Bedtime),
            StatContext("activity", "Активность", Icons.AutoMirrored.Filled.DirectionsWalk),
            StatContext(TRAIN, "Тренировки", Icons.Filled.FitnessCenter),
            StatContext("food", "Питание", Icons.Filled.Restaurant),
            StatContext("mood", "Самочувствие", Icons.Filled.Psychology),
            StatContext("habits", "Привычки", Icons.Filled.CheckCircle),
            StatContext("money", "Финансы", Icons.Filled.AccountBalanceWallet),
            StatContext("notes", "Заметки", Icons.AutoMirrored.Filled.Notes),
            StatContext("other", "Другое", Icons.Filled.Tag),
        )

        fun of(c: StatCard, template: List<TemplateItem>): String {
            val k = c.key
            fun item(prefix: String) = template.firstOrNull { it.id == k.removePrefix(prefix) }
            return when {
                k == "anthro" || k.startsWith("body_") -> "body"
                k == "index" || k == "wellbeing" -> "mood"
                k == "checks" || k.startsWith("multi_") -> "habits"
                k == "note" || k == "diary" -> "notes"
                k.startsWith("water_") || k.startsWith("kcal_") || k.startsWith("food_") || k.startsWith("energy_") || k.startsWith("gly_") -> "food"
                k.startsWith("money_") || k.startsWith("cat_") || k.startsWith("flow_") || k.startsWith("top_") -> "money"
                k.startsWith("num_") -> {
                    val it = item("num_")
                    when {
                        it == null -> "other"
                        c.groupRu == "Весы" -> "body"
                        it.source == Source.HC_SLEEP || it.source == Source.HC_RESTING_HR -> "sleep"
                        it.source == Source.HC_STEPS || it.source == Source.HC_EXERCISE -> "activity"
                        it.source == Source.HC_WEIGHT -> "body"
                        it.type == ItemType.SCALE -> "mood"
                        else -> "other"
                    }
                }
                c.groupRu == "Самочувствие" -> "mood"
                c.groupRu == "Питание" -> "food"
                c.groupRu == "Финансы" -> "money"
                else -> "other"
            }
        }
    }
}
