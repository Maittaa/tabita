package org.maittaa.tabita.train

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek

// ——— генератор ———

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GeneratorScreen(m: AppModel) {
    var s by remember { mutableStateOf(m.genSettings) }
    var confirm by remember { mutableStateOf(false) }
    var why by remember { mutableStateOf<List<String>>(emptyList()) }
    val back = { m.screen = if (m.program != null) Screen.Program else Screen.Home }
    BackHandler(onBack = back)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Собрать программу") },
                navigationIcon = { IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 1.4: подбор по данным дневника, профилю и истории
                OutlinedButton({
                    val r = Recommender.recommend(s, m.tabitaData, m.body, m.history)
                    s = r.s; why = r.why
                }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text(if (m.tabitaData != null) "Подобрать по данным дневника" else "Подобрать автоматически")
                }
                if (why.isNotEmpty()) Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Почему так", style = MaterialTheme.typography.titleSmall)
                        why.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                        Text("Через 7 и 14 дней пересоберите программу этой же кнопкой — по первым тренировкам (сколько повторов и с каким весом получилось, как шло восстановление) план подстроится под вас. Приложение напомнит.",
                            style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp))
                        Text("Можно поправить вручную ниже, затем «Собрать программу».", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                Text("Цель", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Goal.entries.forEach { g -> FilterChip(s.goal == g, { s = s.copy(goal = g) }, label = { Text(g.title) }) }
                }
                Text(
                    when (s.goal) {
                        Goal.MASS -> "10–15 повторов, отдых 90 с — объём для роста мышц."
                        Goal.STRENGTH -> "6–10 повторов в сложных вариантах, отдых 2 мин."
                        Goal.TONE -> "12–20 повторов, отдых 60 с — выносливость и тонус."
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider()
                Text("Дни", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DayOfWeek.entries.forEach { d ->
                        val on = d in s.days
                        FilterChip(on, { s = s.copy(days = if (on) s.days - d else (s.days + d).sortedBy { it.value }) }, label = { Text(d.ruShort()) })
                    }
                }
                if (s.days.size > 4) Text(
                    "Больше 4 тренировок всего тела в неделю новичку многовато — мышцам нужен день отдыха между ними.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                )
                HorizontalDivider()
                Text("Разбивка", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Split.entries.forEach { sp -> FilterChip(s.split == sp, { s = s.copy(split = sp) }, label = { Text(sp.title) }) }
                }
                Text(s.split.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (s.split == Split.PPL && s.days.size < 3) Text(
                    "Для «жим / тяга / ноги» нужно хотя бы 3 дня — иначе часть мышц останется без нагрузки.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                )
                if (s.split == Split.UPPER_LOWER && s.days.size % 2 == 1) Text(
                    "При нечётном числе дней верх и низ будут чередоваться между неделями неравномерно — лучше 2 или 4 дня.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary,
                )
                HorizontalDivider()
                Text("Длительность", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(20 to "20 мин · 5 упр.", 30 to "30 мин · 7 упр.", 45 to "45 мин · 8 упр., 3 подхода").forEach { (v, t) ->
                        FilterChip(s.minutes == v, { s = s.copy(minutes = v) }, label = { Text(t) })
                    }
                }
                HorizontalDivider()
                Text("Уровень", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1 to "Новичок", 2 to "Средний", 3 to "Продвинутый").forEach { (v, t) ->
                        FilterChip(s.level == v, { s = s.copy(level = v) }, label = { Text(t) })
                    }
                }
                HorizontalDivider()
                Text("Где", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(s.place == Place.HOME && !s.noEquipment, { s = s.copy(place = Place.HOME, noEquipment = false) }, label = { Text("Дома с подручными средствами") })
                    FilterChip(s.place == Place.HOME && s.noEquipment, { s = s.copy(place = Place.HOME, noEquipment = true) }, label = { Text("Дома без инвентаря") })
                    if (m.gymEnabled) FilterChip(s.place == Place.GYM, { s = s.copy(place = Place.GYM, noEquipment = false) }, label = { Text("В зале") })
                }
                Text(
                    when {
                        s.place == Place.GYM -> "Инвентарь зала: " + m.gymEquipment.joinToString(", ") { it.title.lowercase() } + ". В дни дома упражнения заменятся домашними."
                        s.noEquipment -> "Только своё тело, пол и стена."
                        else -> "Из настроек: " + (if (m.equipment.isEmpty()) "ничего не отмечено" else m.equipment.joinToString(", ") { it.title.lowercase() }) +
                            ". Поменять — Настройки → Инвентарь дома." + if (m.gymEnabled) " В дни в зале упражнения заменятся вариантами с весом." else ""
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!m.gymEnabled) Text(
                    "Тренировки в зале включаются в Настройки → Зал.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                m.body.bmiLabel()?.let {
                    Text("$it — учтено при подборе упражнений", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                } ?: Text(
                    "Укажи рост в Настройки → Профиль — программа учтёт ИМТ.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Button(
                    { if (m.program != null) confirm = true else m.generate(s, auto = why.isNotEmpty()) },
                    Modifier.fillMaxWidth().height(56.dp), enabled = s.days.isNotEmpty(),
                ) {
                    Icon(Icons.Outlined.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text("Собрать программу")
                }
            }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Заменить программу?") },
        text = { Text("Текущая программа и ручные правки будут заменены. История тренировок останется.") },
        confirmButton = { TextButton({ confirm = false; m.generate(s, auto = why.isNotEmpty()) }) { Text("Заменить") } },
        dismissButton = { TextButton({ confirm = false }) { Text("Отмена") } },
    )
}

// ——— редактор ———

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProgramScreen(m: AppModel, snack: SnackbarHostState, showInfo: (String, String?) -> Unit) {
    val p = m.program
    if (p == null) { GeneratorScreen(m); return }
    var editEx by remember { mutableStateOf<Pair<ProgramDay, Int>?>(null) }
    var pick by remember { mutableStateOf<Triple<ProgramDay, Int?, Pattern?>?>(null) }
    var editDay by remember { mutableStateOf<ProgramDay?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Программа") },
                navigationIcon = { IconButton({ m.screen = Screen.Home }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
                actions = {
                    TextButton({ m.screen = Screen.Generator }) {
                        Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Собрать заново")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(p.name, style = MaterialTheme.typography.titleMedium)
                    Text("Отдых между подходами", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(60, 75, 90, 120, 150).forEach { v -> FilterChip(p.restSec == v, { m.changeProgramRest(v) }, label = { Text("$v с") }) }
                    }
                    if (p.noEquipment) Text("Программа без инвентаря", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    (p.notes + m.body.notes(p.goal)).distinct().forEach {
                        Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
            items(p.days, key = { it.id }) { day ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(day.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                Text(day.weekday?.ru() ?: "день недели не выбран", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                MuscleFocus.describe(day)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                            }
                            IconButton({ editDay = day }) { Icon(Icons.Outlined.Settings, "Настройки дня") }
                        }
                        day.exercises.forEachIndexed { i, pe ->
                            val def = pe.def
                            val available = def?.available(if (p.place == Place.GYM) m.gymEquipment else m.equipment, p.noEquipment) ?: false
                            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                                Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        // не TextButton: кнопка центрирует перенесённый текст и обрезает первые буквы строк
                                        Text(
                                            def?.name ?: pe.exId, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { def?.let { showInfo(it.name, it.cue) } }.padding(vertical = 4.dp),
                                        )
                                        Text(
                                            "${pe.sets} × ${if (pe.repMin == pe.repMax) "${pe.repMin}" else "${pe.repMin}–${pe.repMax}"}" +
                                                (if (def?.seconds == true) " с" else "") + (if (def?.perSide == true) " на сторону" else "") +
                                                if (!available) " · нет инвентаря — будет замена" else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (available) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                        )
                                    }
                                    IconButton({ editEx = day to i }, Modifier.size(40.dp)) { Text("±", style = MaterialTheme.typography.titleMedium) }
                                    IconButton({ pick = Triple(day, i, def?.pattern) }, Modifier.size(40.dp)) { Icon(Icons.Outlined.SwapHoriz, "Заменить") }
                                    Column {
                                        IconButton({ if (i > 0) m.updateDay(day.copy(exercises = day.exercises.swap(i, i - 1))) }, Modifier.size(28.dp), enabled = i > 0) {
                                            Icon(Icons.Outlined.KeyboardArrowUp, "Выше")
                                        }
                                        IconButton({ if (i < day.exercises.lastIndex) m.updateDay(day.copy(exercises = day.exercises.swap(i, i + 1))) }, Modifier.size(28.dp), enabled = i < day.exercises.lastIndex) {
                                            Icon(Icons.Outlined.KeyboardArrowDown, "Ниже")
                                        }
                                    }
                                    IconButton({ m.updateDay(day.copy(exercises = day.exercises.filterIndexed { j, _ -> j != i })) }, Modifier.size(40.dp)) {
                                        Icon(Icons.Outlined.Delete, "Удалить")
                                    }
                                }
                            }
                        }
                        TextButton({ pick = Triple(day, null, null) }, Modifier.padding(start = 8.dp)) {
                            Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Добавить упражнение")
                        }
                    }
                }
            }
            item {
                OutlinedButton({ m.addDay() }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, null); Spacer(Modifier.width(8.dp)); Text("Добавить день")
                }
            }
        }
    }

    editEx?.let { (day, i) ->
        val pe = day.exercises.getOrNull(i)
        if (pe == null) editEx = null else PlanExDialog(pe, onDismiss = { editEx = null }) { np ->
            m.updateDay(day.copy(exercises = day.exercises.mapIndexed { j, x -> if (j == i) np else x }))
            editEx = null
        }
    }
    pick?.let { (day, idx, pattern) ->
        ExercisePicker(m, pattern, onDismiss = { pick = null }) { def ->
            val fresh = m.program?.days?.firstOrNull { it.id == day.id } ?: day
            val r = rangeFor(def, p.goal)
            val sets = fresh.exercises.firstOrNull()?.sets ?: 2
            val list = if (idx == null) fresh.exercises + PlanEx(def.id, sets, r.first, r.last)
            else fresh.exercises.mapIndexed { j, x -> if (j == idx) PlanEx(def.id, x.sets, r.first, r.last) else x }
            m.updateDay(fresh.copy(exercises = list))
            pick = null
        }
    }
    editDay?.let { day ->
        DayDialog(day, onDismiss = { editDay = null }, onDelete = { m.deleteDay(day.id); editDay = null }) { nd ->
            m.updateDay(nd); editDay = null
        }
    }
}

private fun <T> List<T>.swap(a: Int, b: Int): List<T> = toMutableList().also { val t = it[a]; it[a] = it[b]; it[b] = t }

@Composable
private fun PlanExDialog(pe: PlanEx, onDismiss: () -> Unit, onSave: (PlanEx) -> Unit) {
    var sets by remember { mutableIntStateOf(pe.sets) }
    var min by remember { mutableIntStateOf(pe.repMin) }
    var max by remember { mutableIntStateOf(pe.repMax) }
    val unit = if (pe.def?.seconds == true) "с" else "повт."
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pe.def?.name ?: pe.exId) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Counter("Подходов", sets, 1..6) { sets = it }
                Counter("Минимум, $unit", min, 1..300) { min = it; if (max < it) max = it }
                Counter("Максимум, $unit", max, 1..300) { max = it; if (min > it) min = it }
                Text(
                    "Рекомендация начинает с минимума и прибавляет по ${if (unit == "с") "5 с" else "повтору"}, пока не дойдёшь до максимума.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton({ onSave(pe.copy(sets = sets, repMin = min, repMax = max)) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun Counter(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        FilledTonalIconButton({ onChange((value - 1).coerceIn(range)) }, Modifier.size(36.dp)) { Icon(Icons.Filled.Remove, "Меньше") }
        Text("$value", Modifier.width(44.dp), style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        FilledTonalIconButton({ onChange((value + 1).coerceIn(range)) }, Modifier.size(36.dp)) { Icon(Icons.Filled.Add, "Больше") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayDialog(day: ProgramDay, onDismiss: () -> Unit, onDelete: () -> Unit, onSave: (ProgramDay) -> Unit) {
    var name by remember { mutableStateOf(day.name) }
    var wd by remember { mutableStateOf(day.weekday) }
    var confirm by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("День программы") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true)
                Text("День недели", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(wd == null, { wd = null }, label = { Text("—") })
                    DayOfWeek.entries.forEach { d -> FilterChip(wd == d, { wd = d }, label = { Text(d.ruShort()) }) }
                }
                TextButton({ confirm = true }) { Text("Удалить день", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton({ onSave(day.copy(name = name.trim().ifBlank { day.name }, weekday = wd)) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onDismiss) { Text("Отмена") } },
    )
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Удалить «${day.name}»?") },
        confirmButton = { TextButton({ confirm = false; onDelete() }) { Text("Удалить") } },
        dismissButton = { TextButton({ confirm = false }) { Text("Отмена") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExercisePicker(m: AppModel, pattern: Pattern?, onDismiss: () -> Unit, onPick: (ExerciseDef) -> Unit) {
    var pat by remember { mutableStateOf(pattern) }
    var onlyAvailable by remember { mutableStateOf(true) }
    val eq = if (m.program?.place == Place.GYM) m.gymEquipment else m.equipment
    val list = Library.all.filter { (pat == null || it.pattern == pat) && (!onlyAvailable || it.available(eq, m.program?.noEquipment == true)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (pattern != null) "Заменить упражнение" else "Добавить упражнение") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                        FilterChip(pat == null, { pat = null }, label = { Text("Все") })
                        Pattern.entries.forEach { p -> FilterChip(pat == p, { pat = p }, label = { Text(p.title) }) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Только с моим инвентарём", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(onlyAvailable, { onlyAvailable = it })
                }
                LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(list, key = { it.id }) { e -> ExerciseRow(e, eq) { onPick(e) } }
                    if (list.isEmpty()) item { Text("Нет подходящих упражнений — сними фильтр или отметь инвентарь.") }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Отмена") } },
    )
}
