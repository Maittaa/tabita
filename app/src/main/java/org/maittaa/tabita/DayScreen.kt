package org.maittaa.tabita

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayScreen(act: MainActivity, modifier: Modifier, notify: (String) -> Unit) {
    val repo = act.repo
    val template = collect(repo.template)
    val entries = collect(repo.entries)
    val status = collect(act.syncStatus)
    collect(repo.balance)
    var dateStr by rememberSaveable { mutableStateOf(act.jumpDate.value ?: LocalDate.now().key()) }
    val jump = collect(act.jumpDate)
    androidx.compose.runtime.LaunchedEffect(jump) { jump?.let { dateStr = it; act.jumpDate.value = null } }
    val date = LocalDate.parse(dateStr)
    val day = entries[dateStr] ?: emptyMap()
    var pickDate by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val layout = collect(repo.layout)
    val groups = collect(repo.groups)
    val tiles = repo.tiles(template, layout, groups)
    var editing by remember { mutableStateOf(false) }
    var sel by remember { mutableStateOf<String?>(null) }
    var popup by remember { mutableStateOf<String?>(null) }
    var newItem by remember { mutableStateOf(false) }
    var allItems by remember { mutableStateOf(false) }
    var editItem by remember { mutableStateOf<TemplateItem?>(null) }

    /** Полное содержимое плитки. */
    @Composable
    fun FullTile(id: String) {
        groups.firstOrNull { it.id == id }?.let { g ->
            SectionCard(g.displayName) {
                ChecksRow(groupChecks(repo, template, g.id), day) { cid, v -> repo.setValue(dateStr, cid, v) }
            }
            return
        }
        val item = template.firstOrNull { it.id == id } ?: return
        when (item.type) {
            ItemType.MEALS -> MealsBlock(act, item, dateStr, day[item.id], notify)
            ItemType.EXPENSES -> FinanceBlock(repo, item, dateStr, day[item.id])
            ItemType.WATER -> WaterBlock(repo, item, dateStr, day)
            ItemType.WELLBEING -> WellbeingTileCard(item, wbStatus(repo, act.prefs, dateStr)) { act.wbStart.value = true }
            ItemType.BODY -> BodyBlock(repo, item, dateStr, day[item.id], manual = day[manualKey(item.id)] != null, srcValue = day[srcKey(item.id)],
                onRevert = { scope.launch { act.syncOpenScale(date.minusDays(1)) } })
            else -> if (item.source == Source.HC_EXERCISE) WorkoutBlock(item, day, act.prefs.countWalking,
                onManualSessions = { repo.setWorkouts(dateStr, item, it) },
                onOverride = { v -> repo.setManual(dateStr, item.id, v, true) },
                onRevert = { repo.setManual(dateStr, item.id, null, false); repo.setWorkouts(dateStr, item, Workouts.parse(day[Workouts.manualKey(item.id)])); scope.launch { act.syncHealth(date, date) } },
                onCountWalking = { act.prefs.countWalking = it; repo.recountAllWorkouts() },
            ) else if (item.source == Source.HC_SLEEP) SleepBlock(item, day, act.prefs,
                onManual = { v -> repo.setManual(dateStr, item.id, v, true) },
                onRevert = { repo.setManual(dateStr, item.id, null, false); scope.launch { act.syncHealth(date, date) } },
                onSync = { scope.launch { notify(act.syncHealth(date.minusDays(1), date)) } },
            ) else SectionCard(item.displayName, watch = item.source != Source.NONE) {
                if (item.source != Source.NONE) {
                    WatchValue(item, day[item.id], manual = day[manualKey(item.id)] != null, day = day,
                        onManual = { v -> repo.setManual(dateStr, item.id, v, true) },
                        onRevert = {
                            repo.setManual(dateStr, item.id, null, false)
                            scope.launch { act.syncHealth(date, date) }
                        })
                    // нет данных с часов — подсказка, как замерить пульс покоя вручную
                    if (item.source == Source.HC_RESTING_HR && (day[srcKey(item.id)] == null || day[manualKey(item.id)] != null)) RestingHrHint()
                } else {
                    key(item.id + dateStr) {
                        ItemEditor(item, day[item.id], onAddOption = { repo.addOption(item.id, it) }) { repo.setValue(dateStr, item.id, it) }
                    }
                }
            }
        }
    }

    /** Краткое содержимое уменьшенной плитки. */
    @Composable
    fun CompactTile(id: String, w: Int, h: Int) {
        val s = tileSummary(repo, template, id, day, dateStr, act.prefs)
        CompactTileView(s, w, h)
    }

    Column(modifier.fillMaxSize().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { dateStr = date.minusDays(1).key() }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, t("Назад"))
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                TextButton(onClick = { pickDate = true }) {
                    Text(date.ruTitle(), style = MaterialTheme.typography.titleLarge)
                }
                if (date != LocalDate.now()) {
                    Text(
                        t("вернуться к сегодня"), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 2.dp).clickable { dateStr = LocalDate.now().key() },
                    )
                }
            }
            IconButton(onClick = { dateStr = date.plusDays(1).key() }, enabled = date < LocalDate.now()) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, t("Вперёд"))
            }
        }

        val monthCfg = remember { act.prefs.monthCfg }
        if (!editing) DayProgressLine(template, entries, monthCfg, date) { popup = it }

        // подсказка режима правки закреплена над плитками
        androidx.compose.animation.AnimatedVisibility(editing) {
            Box(Modifier.padding(horizontal = 12.dp)) {
            EditBanner(
                selected = sel?.let { id -> id to (template.firstOrNull { it.id == id }) },
                onEditItem = { editItem = it },
                onHide = { id ->
                    template.firstOrNull { it.id == id }?.let { repo.upsertItem(it.copy(hidden = true)) }
                    if (groups.any { it.id == id }) groupChecks(repo, template, id).forEach { repo.upsertItem(it.copy(hidden = true)) }
                    sel = null
                },
                onReset = { id -> repo.setLayout(tiles.map { if (it.id == id) Repo.Tile(id) else it }) },
                onDone = { editing = false; sel = null; Log.d("Day", "раскладка: $tiles") },
                size = sel?.let { id -> tiles.firstOrNull { it.id == id }?.size },
                onSize = { ts -> sel?.let { id -> repo.setLayout(tiles.map { if (it.id == id) ts.tile(id) else it }); Log.d("Day", "плитка $id → $ts") } },
            )
            }
        }
        val scroll = rememberScrollState()
        Box(Modifier.weight(1f)) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(vertical = 0.dp)) {
            TileGrid(
                tiles = tiles, editing = editing, selected = sel,
                onSelect = { sel = it },
                onStartEdit = { editing = true; sel = it },
                onMove = { id, to ->
                    val l = tiles.toMutableList(); val from = l.indexOfFirst { it.id == id }
                    if (from >= 0 && from != to) { val t0 = l.removeAt(from); l.add(to.coerceIn(0, l.size), t0); repo.setLayout(l) }
                },
                onResize = { id, w, h, small -> repo.setLayout(tiles.map { if (it.id == id) Repo.Tile(id, w, h, small) else it }) },
                onOpen = { id ->
                    // плитка чекапа — сразу к тесту
                    if (template.any { it.id == id && it.type == ItemType.WELLBEING }) act.wbStart.value = true
                    else popup = id
                    Log.d("Day", "открыта плитка $id")
                },
                full = { id -> FullTile(id) },
                compact = { id, w, h -> CompactTile(id, w, h) },
            )
            Column(Modifier.padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    status.ifEmpty { t("Данные с часов подтягиваются при открытии приложения") },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { scope.launch { act.syncHealth(date.minusDays(1), date) } }) { Text(t("Обновить")) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                val pad = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                androidx.compose.material3.Button(onClick = { newItem = true }, Modifier.weight(1f), contentPadding = pad) {
                    Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(t("Пункт"), maxLines = 1)
                }
                OutlinedButton(onClick = { allItems = true }, Modifier.weight(1f), contentPadding = pad) { Text(t("Все пункты"), maxLines = 1) }
                OutlinedButton(onClick = { editing = !editing; sel = null }, Modifier.weight(1f), contentPadding = pad) {
                    Text(if (editing) t("Готово") else t("Плитки"), maxLines = 1)
                }
            }
            Spacer(Modifier.size(40.dp))
            }
        }
        VerticalScrollbar(scroll, Modifier.align(Alignment.CenterEnd))
        }
    }

    // подробное окно уменьшенной плитки
    popup?.let { id ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { popup = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            androidx.compose.material3.Surface(Modifier.fillMaxWidth(0.95f), shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp), tonalElevation = 6.dp) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(8.dp).imePadding()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(date.ruTitle(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f).padding(start = 8.dp))
                        IconButton(onClick = { popup = null }) { Icon(Icons.Filled.Close, t("Закрыть")) }
                    }
                    FullTile(id)
                }
            }
        }
    }
    val effectOf: (String) -> Boolean? = { k ->
        val cfg = act.prefs.monthCfg
        if (k !in MonthData(template, repo.entries.value, LocalDate.now(), cfg).indexKeys) null else k !in cfg.negative
    }
    val onEffects: (Map<String, Boolean?>) -> Unit = { m ->
        var cfg = act.prefs.monthCfg
        m.forEach { (k, v) -> cfg = cfg.withEffect(k, v, MonthData(template, repo.entries.value, LocalDate.now(), cfg).indexKeys) }
        act.prefs.monthCfg = cfg; Log.i("Day", "влияние на индекс: $m")
    }
    val onNewGroup: (String, GroupKind) -> Group = { n, k -> repo.addGroup(n, k) }
    if (newItem) ItemDialog(TemplateItem(name = "", type = ItemType.CHECK), true, groups = groups,
        canAddWellbeing = template.none { it.type == ItemType.WELLBEING },
        onNewGroup = onNewGroup, effectOf = effectOf, onEffects = onEffects,
        onSave = { repo.upsertItem(it); newItem = false; Log.i("Day", "новый пункт ${it.name} (${it.type})") },
        onDelete = {}, onDismiss = { newItem = false })
    editItem?.let { item ->
        ItemDialog(item, false, groups = groups, onNewGroup = onNewGroup, effectOf = effectOf, onEffects = onEffects,
            onSave = { repo.upsertItem(it); editItem = null },
            onDelete = { repo.deleteItem(item.id); editItem = null; sel = null },
            onDismiss = { editItem = null })
    }
    if (allItems) androidx.compose.ui.window.Dialog(onDismissRequest = { allItems = false },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        androidx.compose.material3.Surface(Modifier.fillMaxSize()) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                    Text(t("Все пункты"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 8.dp))
                    IconButton(onClick = { allItems = false }) { Icon(Icons.Filled.Close, t("Закрыть")) }
                }
                TemplateScreen(repo, Modifier.weight(1f), effectOf, onEffects)
            }
        }
    }

    if (pickDate) {
        val st = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let {
                        val d = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        dateStr = minOf(d, LocalDate.now()).key()
                    }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(t("Отмена")) } },
        ) { DatePicker(st) }
    }
}

/** Значение с часов: показ + кнопка «Изменить» для ручной корректировки. */
@Composable
fun WatchValue(item: TemplateItem, value: String?, manual: Boolean, day: Map<String, String> = emptyMap(), onManual: (String?) -> Unit, onRevert: () -> Unit) {
    var editing by remember { mutableStateOf(false) }
    if (editing) {
        var draft by remember { mutableStateOf(value) }
        ItemEditor(item, draft, onAddOption = {}) { draft = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            OutlinedButton(onClick = { editing = false }) { Text(t("Отмена")) }
            androidx.compose.material3.Button(onClick = {
                Log.i("Day", "ручная правка «${item.name}»: $value → $draft")
                onManual(draft); editing = false
            }) { Text(t("Сохранить")) }
        }
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                formatValue(item, value) ?: "—",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (manual) t("исправлено вручную") else sourceCaption(item, day, value != null),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (manual) TextButton(onClick = onRevert) { Text(t("Вернуть")) }
        OutlinedButton(onClick = { editing = true }) {
            Icon(Icons.Filled.Edit, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(t("Изменить"))
        }
    }
}

fun formatValue(item: TemplateItem, value: String?): String? {
    value ?: return null
    return when (item.type) {
        ItemType.DURATION -> value.toDoubleOrNull()?.let(::formatMinutes)
        ItemType.NUMBER, ItemType.SCALE -> parseNumber(value)?.let { formatNumber(it) + if (item.unit.isNotEmpty()) " " + item.displayUnit else "" }
        ItemType.WATER -> parseNumber(value)?.let(::formatLiters)
        else -> value
    }
}

@Composable
fun SectionCard(title: String, watch: Boolean = false, action: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    val inTile = LocalInTile.current
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (inTile) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLow),
        border = if (inTile) null else cardBorder(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (watch) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.Watch, t("из другого приложения"), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                }
                Spacer(Modifier.weight(1f))
                action?.invoke()
            }
            Spacer(Modifier.size(8.dp))
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChecksRow(items: List<TemplateItem>, day: Map<String, String>, onSet: (String, String?) -> Unit) {
    // плотнее: без 48dp «зоны касания» вокруг каждого чипа
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalMinimumInteractiveComponentSize provides 0.dp) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            val v = day[item.id]
            val label = if (v == "2") item.displayName + "+" else item.displayName
            FilterChip(
                selected = v != null,
                onClick = {
                    // выкл → вкл → «+» (если разрешено) → выкл
                    val next = when (v) {
                        null -> "1"
                        "1" -> if (item.plusAllowed) "2" else null
                        else -> null
                    }
                    onSet(item.id, next)
                },
                label = { Text(label, fontWeight = if (v == "2") FontWeight.Bold else FontWeight.Normal) },
                leadingIcon = if (v != null) {
                    { Icon(Icons.Filled.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
                } else null,
            )
        }
    }
    }
    if (items.any { it.plusAllowed }) {
        Text(
            t("Повторное нажатие ставит «+»"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ItemEditor(item: TemplateItem, value: String?, onAddOption: (String) -> Unit, onChange: (String?) -> Unit) {
    when (item.type) {
        ItemType.WELLBEING -> {}
        ItemType.CHECK -> ChecksRow(listOf(item), if (value != null) mapOf(item.id to value) else emptyMap()) { _, v -> onChange(v) }

        ItemType.SCALE -> {
            var v by remember { mutableStateOf(parseNumber(value)) }
            var pushed by remember { mutableStateOf(value) }
            if (value != pushed) { pushed = value; v = parseNumber(value) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = (v ?: item.min).toFloat(),
                    onValueChange = {
                        val r = (it * 10).roundToInt() / 10.0
                        v = r; pushed = r.toString(); onChange(pushed)
                    },
                    valueRange = item.min.toFloat()..item.max.toFloat(),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    v?.let(::formatNumber) ?: "—",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.width(56.dp).padding(start = 8.dp),
                )
                if (v != null) IconButton(onClick = { v = null; pushed = null; onChange(null) }) { Icon(Icons.Filled.Close, t("Очистить")) }
            }
        }

        ItemType.NUMBER -> NumberField(value, item.displayUnit, Modifier.fillMaxWidth()) { onChange(it?.let { n -> n.toString().removeSuffix(".0") }) }

        ItemType.DURATION -> {
            val total = value?.toIntOrNull()
            var h by remember { mutableStateOf(total?.let { (it / 60).toString() } ?: "") }
            var m by remember { mutableStateOf(total?.let { (it % 60).toString() } ?: "") }
            var pushed by remember { mutableStateOf(value) }
            if (value != pushed) {
                pushed = value
                h = total?.let { (it / 60).toString() } ?: ""
                m = total?.let { (it % 60).toString() } ?: ""
            }
            fun push() {
                val hh = h.toIntOrNull(); val mm = m.toIntOrNull()
                val out = if (hh == null && mm == null) null else ((hh ?: 0) * 60 + (mm ?: 0)).toString()
                pushed = out; onChange(out)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    h, { h = it.filter(Char::isDigit).take(2); push() }, Modifier.weight(1f),
                    singleLine = true, suffix = { Text(t("ч")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    m, { m = it.filter(Char::isDigit).take(2); push() }, Modifier.weight(1f),
                    singleLine = true, suffix = { Text(t("мин")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        }

        ItemType.MULTI -> {
            val counts = parseCounts(value)
            var adding by remember { mutableStateOf(false) }
            val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                (item.options + (counts.keys - item.options.toSet())).forEach { opt ->
                    val n = counts[opt] ?: 0
                    CounterChip(
                        label = t(opt), count = n, showCount = item.counter,
                        onClick = {
                            val m = LinkedHashMap(counts)
                            if (item.counter) m[opt] = n + 1 else if (n > 0) m.remove(opt) else m[opt] = 1
                            Log.d("Day", "«$opt»: ${m[opt] ?: 0}")
                            onChange(countsToString(item.options.filter { it in m }.associateWith { m[it]!! } + (m - item.options.toSet())))
                        },
                        onLongClick = {
                            if (n > 0) {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                Log.d("Day", "«$opt»: сброс")
                                onChange(countsToString(counts - opt))
                            }
                        },
                    )
                }
                AssistChip(onClick = { adding = true }, label = { Text(t("Добавить")) }, leadingIcon = { Icon(Icons.Filled.Add, null) })
            }
            if (item.counter) Text(
                t("Нажатие — +1, удержание — сброс"),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (adding) {
                var s by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { adding = false },
                    title = { Text(t("Новый вариант")) },
                    text = { OutlinedTextField(s, { s = it }, singleLine = true, placeholder = { Text(t("например: Магний")) }) },
                    confirmButton = {
                        TextButton(onClick = {
                            val o = s.trim()
                            if (o.isNotEmpty()) {
                                onAddOption(o)
                                onChange(countsToString(counts + (o to (counts[o] ?: 0) + 1)))
                            }
                            adding = false
                        }) { Text(t("Добавить")) }
                    },
                    dismissButton = { TextButton(onClick = { adding = false }) { Text(t("Отмена")) } },
                )
            }
        }

        ItemType.TEXT -> {
            var text by remember { mutableStateOf(value ?: "") }
            var pushed by remember { mutableStateOf(value) }
            if (value != pushed) { pushed = value; text = value ?: "" }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; pushed = it.ifEmpty { null }; onChange(pushed) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
        }

        ItemType.MEALS, ItemType.EXPENSES, ItemType.BODY, ItemType.WATER -> {}
    }
}

/** Поле для числа: допускает запятую, сообщает null при пустом вводе. */
@Composable
fun NumberField(
    value: String?, suffix: String, modifier: Modifier,
    placeholder: String? = null, label: String? = null, onChange: (Double?) -> Unit,
) {
    var text by remember { mutableStateOf(value?.let { v -> parseNumber(v)?.let(::formatNumber) } ?: "") }
    var pushed by remember { mutableStateOf(value) }
    if (value != pushed) { pushed = value; text = value?.let { v -> parseNumber(v)?.let(::formatNumber) } ?: "" }
    OutlinedTextField(
        value = text,
        onValueChange = { s ->
            text = s
            val n = parseNumber(s)
            if (s.isBlank()) { pushed = null; onChange(null) }
            else if (n != null) { pushed = n.toString().removeSuffix(".0"); onChange(n) }
        },
        singleLine = true,
        placeholder = placeholder?.let { { Text(it) } },
        label = label?.let { { Text(it, maxLines = 1) } },
        suffix = { if (suffix.isNotEmpty()) Text(suffix) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

/** Чип-счётчик: нажатие — +1, долгое нажатие — сброс. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CounterChip(label: String, count: Int, showCount: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val on = count > 0
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        color = if (on) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent,
        border = if (on) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.height(32.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            if (on && showCount) {
                Spacer(Modifier.width(6.dp))
                androidx.compose.material3.Badge(containerColor = MaterialTheme.colorScheme.primary) { Text("$count") }
            }
        }
    }
}
