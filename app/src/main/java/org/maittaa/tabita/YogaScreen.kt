package org.maittaa.tabita

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.maittaa.tabita.train.SChart
import org.maittaa.tabita.train.SeriesChart
import java.time.LocalDate

/** 5.53: раздел «Йога и растяжка»: [tab] 0 — комплексы (старт, свои, позы), 1 — прогресс (недели, самопроверки). */
@Composable
fun YogaScreen(act: MainActivity, tab: Int, modifier: Modifier) {
    val prefs = act.prefs
    var custom by remember { mutableStateOf(Yoga.parseCustom(prefs.yogaCustom)) }
    val saveCustom: (List<Yoga.Complex>) -> Unit = { l -> custom = l; prefs.yogaCustom = Yoga.customToJson(l); Yoga.customJson = prefs.yogaCustom }
    var checks by remember { mutableStateOf(Yoga.parseChecks(prefs.yogaChecks)) }
    val saveChecks: (List<Yoga.Check>) -> Unit = { l -> checks = l; prefs.yogaChecks = Yoga.checksToJson(l) }
    var draft by remember(tab) { mutableStateOf(CardioDraft.parse(prefs.cardioDraft)) }
    val saveDraft: (CardioDraft?) -> Unit = { draft = it; prefs.cardioDraft = it?.toJson() }
    var checking by remember { mutableStateOf(false) }
    val entries = collect(act.repo.entries)
    val today = LocalDate.now()
    val list = remember(entries) { Cardio.sessions(act.repo, today.minusDays(120), today) { it.kind in Yoga.kinds } }

    Box(modifier.fillMaxSize()) {
        val dr = draft?.takeIf { it.yoga }
        when {
            tab == 0 && dr != null -> CardioTimer(act, dr, CardioData.NONE, saveDraft)
            tab == 0 -> YogaHome(custom, checks, onStart = { c ->
                saveDraft(CardioDraft(CardioPlans.Mode.YOGA, System.currentTimeMillis(), complex = c.id))
                Log.i("Yoga", "старт: ${c.id}, ${c.poses.size} поз")
            }, onCustom = saveCustom, onCheck = { checking = true })
            else -> YogaProgress(list, checks) { checking = true }
        }
    }
    if (checking) CheckDialog(onDismiss = { checking = false }) { c ->
        saveChecks(checks.filter { it.date != c.date } + c)
        Log.i("Yoga", "самопроверка: стена ${c.wall}, наклон ${c.bend} см, руки ${c.clasp}")
        checking = false
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun YogaHome(
    custom: List<Yoga.Complex>, checks: List<Yoga.Check>, onStart: (Yoga.Complex) -> Unit,
    onCustom: (List<Yoga.Complex>) -> Unit, onCheck: () -> Unit,
) {
    var editing by remember { mutableStateOf<Yoga.Complex?>(null) }
    var library by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    LazyColumn(Modifier.fillMaxSize().testTag("yoga_home"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (Yoga.checkDue(checks)) item {
            Card(colors = CardDefaults.cardColors(containerColor = cs.tertiaryContainer, contentColor = cs.onTertiaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(t("Самопроверка осанки"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(t("Раз в месяц: стойка у стены, наклон вперёд, руки за спиной. Видно, что меняется."), style = MaterialTheme.typography.bodySmall)
                    TextButton(onCheck) { Text(t("Пройти"), fontWeight = FontWeight.SemiBold, color = cs.onTertiaryContainer) }
                }
            }
        }
        val ed = editing
        if (ed != null) item {
            ComplexEditor(ed, onCancel = { editing = null }) { c ->
                onCustom(if (custom.any { it.id == c.id }) custom.map { if (it.id == c.id) c else it } else custom + c)
                Log.i("Yoga", "свой комплекс «${c.ru}»: ${c.poses.size} поз")
                editing = null
            }
        } else {
            items(Yoga.builtIn + custom, key = { it.id }) { c ->
                ComplexCard(c, onStart = { onStart(c) },
                    onEdit = if (c.custom) ({ editing = c }) else null,
                    onDelete = if (c.custom) ({ onCustom(custom.filter { it.id != c.id }) }) else null)
            }
            item {
                OutlinedButton({ editing = Yoga.Complex("c" + System.currentTimeMillis(), "", "", emptyList(), true) }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Свой комплекс"))
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().clickable { library = !library }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(t("Все позы (%d)", Yoga.poses.size), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Icon(if (library) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
            }
        }
        if (library) items(Yoga.poses, key = { "p_" + it.id }) { p -> PoseRow(p) }
        item {
            Text(t("Тянитесь до лёгкого натяжения, не до боли: ACSM советует держать растяжку 10–30 с. Если поза вызывает боль, пропустите её и обсудите с врачом."),
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}

private fun mins(sec: Int) = (sec + 59) / 60

@Composable
private fun ComplexCard(c: Yoga.Complex, onStart: () -> Unit, onEdit: (() -> Unit)?, onDelete: (() -> Unit)?) {
    var open by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().testTag("yoga_" + c.id)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { open = !open }) {
                    Text(c.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(t("%d мин · поз: %d", mins(c.totalSec), c.poses.size), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                onEdit?.let { IconButton(it) { Icon(Icons.Filled.Edit, t("Изменить")) } }
                onDelete?.let { IconButton({ confirm = true }) { Icon(Icons.Filled.Delete, t("Удалить")) } }
                Button(onStart, enabled = c.poses.isNotEmpty()) { Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(t("Начать")) }
            }
            Text(c.poses.mapNotNull { Yoga.pose(it)?.name }.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                maxLines = if (open) 20 else 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { open = !open })
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text(t("Удалить комплекс «%s»?", c.name)) },
        confirmButton = { TextButton({ confirm = false; onDelete?.invoke() }) { Text(t("Удалить")) } },
        dismissButton = { TextButton({ confirm = false }) { Text(t("Отмена")) } },
    )
}

@Composable
private fun PoseRow(p: Yoga.Pose) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(12.dp)) {
        Row {
            Text(p.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(poseTime(p), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(p.cue, style = MaterialTheme.typography.bodySmall)
    }
}

private fun poseTime(p: Yoga.Pose): String =
    (if (p.reps.isNotBlank()) t(p.reps) + " · " else "") + t("%d с", p.sec) + (if (p.sides) " × 2" else "")

/** Свой комплекс: название, позы по порядку (стрелки — переставить), добавить из библиотеки. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ComplexEditor(start: Yoga.Complex, onCancel: () -> Unit, onSave: (Yoga.Complex) -> Unit) {
    var name by remember { mutableStateOf(start.ru) }
    var poses by remember { mutableStateOf(start.poses) }
    Card(Modifier.fillMaxWidth().testTag("yoga_editor")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(t("Свой комплекс"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(t("Название")) })
            Text(t("%d мин · поз: %d", mins(poses.mapNotNull { Yoga.pose(it)?.totalSec }.sum()), poses.size),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                poses.forEachIndexed { i, id ->
                    val p = Yoga.pose(id) ?: return@forEachIndexed
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}. " + p.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton({ if (i > 0) poses = poses.toMutableList().also { l -> l.add(i - 1, l.removeAt(i)) } }, enabled = i > 0) {
                            Icon(Icons.Filled.ArrowUpward, t("Выше"), Modifier.size(18.dp)) }
                        IconButton({ if (i < poses.lastIndex) poses = poses.toMutableList().also { l -> l.add(i + 1, l.removeAt(i)) } }, enabled = i < poses.lastIndex) {
                            Icon(Icons.Filled.ArrowDownward, t("Ниже"), Modifier.size(18.dp)) }
                        IconButton({ poses = poses.filterIndexed { j, _ -> j != i } }) { Icon(Icons.Filled.Close, t("Убрать"), Modifier.size(18.dp)) }
                    }
                }
                Text(t("Добавить позу"), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Yoga.poses.forEach { p -> FilterChip(false, { poses = poses + p.id }, label = { Text(p.name) }, leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(16.dp)) }) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onCancel) { Text(t("Отмена")) }
                Button({ onSave(start.copy(ru = name.trim().ifBlank { t("Мой комплекс") }, en = name.trim(), poses = poses)) },
                    enabled = poses.isNotEmpty(), modifier = Modifier.testTag("yoga_editor_save")) { Text(t("Сохранить")) }
            }
        }
    }
}

// =====================================================================
// Прогресс и самопроверки
// =====================================================================

@Composable
private fun YogaProgress(list: List<Cardio.Item>, checks: List<Yoga.Check>, onCheck: () -> Unit) {
    val start = Cardio.weekStart(LocalDate.now())
    val weeks = (11 downTo 0).map { Cardio.week(list, start.minusWeeks(it.toLong()), null) }
    LazyColumn(Modifier.fillMaxSize().testTag("yoga_progress"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ChartCard(t("Йога и растяжка по неделям, мин"), t("ACSM советует растяжку не реже 2–3 раз в неделю, лучше каждый день.")) {
                SeriesChart(weeks.map { it.start to it.minutes.toDouble() }, SChart.BARS, { formatNumber(it) })
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(t("Самопроверки"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    val last = checks.lastOrNull()
                    if (last == null) Text(t("Пока не было. Раз в месяц — видно, что меняется."), style = MaterialTheme.typography.bodySmall)
                    else {
                        Text(t("Последняя: %s", LocalDate.parse(last.date).ruShort()), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        CheckLines(last, checks.getOrNull(checks.size - 2))
                    }
                    if (checks.size >= 2) SeriesChart(checks.map { LocalDate.parse(it.date) to it.bend.toDouble() }, SChart.LINE, { t("%s см", formatNumber(it)) })
                    if (checks.size >= 2) Text(t("Наклон: сантиметров до пола — меньше лучше."), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onCheck, Modifier.testTag("yoga_check")) { Text(t("Пройти самопроверку")) }
                }
            }
        }
    }
}

@Composable
private fun CheckLines(c: Yoga.Check, prev: Yoga.Check?) {
    @Composable fun line(label: String, v: String, d: String?) = Row {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(v + (d?.let { " ($it)" } ?: ""), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
    line(t("Стена"), if (c.wall.size == 3) t("всё касается") else Yoga.wallPoints.filter { it in c.wall }.joinToString(", ") { Yoga.wallName(it).lowercase() }.ifBlank { "—" },
        prev?.let { p -> if (c.wall.size != p.wall.size) "%+d".format(c.wall.size - p.wall.size) else null })
    line(t("Наклон"), if (c.bend == 0) t("касаюсь пола") else t("%d см до пола", c.bend),
        prev?.let { p -> if (c.bend != p.bend) "%+d".format(c.bend - p.bend) else null })
    line(t("Руки за спиной"), Yoga.claspNames[c.clasp], null)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CheckDialog(onDismiss: () -> Unit, onSave: (Yoga.Check) -> Unit) {
    var wall by remember { mutableStateOf(setOf<String>()) }
    var bend by remember { mutableIntStateOf(10) }
    var clasp by remember { mutableIntStateOf(1) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Самопроверка")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t("1. Встаньте спиной к стене, пятки в 5 см от неё, стойте как обычно. Что касается стены?"), style = MaterialTheme.typography.bodySmall)
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Yoga.wallPoints.forEach { k -> FilterChip(k in wall, { wall = if (k in wall) wall - k else wall + k }, label = { Text(Yoga.wallName(k)) }) }
                    }
                }
                Text(t("2. Наклон вперёд с прямыми ногами: сколько сантиметров от пальцев до пола?"), style = MaterialTheme.typography.bodySmall)
                Stepper(bend, { bend = it.coerceIn(0, 60) }, step = 1, big = 5, unit = "", text = if (bend == 0) t("касаюсь") else t("%d см", bend))
                Text(t("3. Одна рука сверху через плечо, другая снизу за спину: сходятся ли пальцы?"), style = MaterialTheme.typography.bodySmall)
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Yoga.claspNames.forEachIndexed { i, n -> FilterChip(clasp == i, { clasp = i }, label = { Text(n) }) }
                    }
                }
            }
        },
        confirmButton = { TextButton({ onSave(Yoga.Check(LocalDate.now().key(), wall, bend, clasp)) }) { Text(t("Сохранить")) } },
        dismissButton = { TextButton(onDismiss) { Text(t("Отмена")) } },
    )
}
