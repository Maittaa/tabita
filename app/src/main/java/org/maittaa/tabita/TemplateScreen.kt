package org.maittaa.tabita

import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun TemplateScreen(
    repo: Repo, modifier: Modifier,
    effectOf: ((String) -> Boolean?)? = null, onEffects: ((Map<String, Boolean?>) -> Unit)? = null,
    prefs: Prefs? = null,
) {
    val template = collect(repo.template)
    val groups = collect(repo.groups)
    collect(repo.layout)
    val trash = collect(repo.trash)
    var purge by remember { mutableStateOf<TemplateItem?>(null) }
    var editing by remember { mutableStateOf<TemplateItem?>(null) }
    var isNew by remember { mutableStateOf(false) }
    var lockKind by remember { mutableStateOf<GroupKind?>(null) }
    var groupEdit by remember { mutableStateOf<Group?>(null) }
    var newGroup by remember { mutableStateOf(false) }
    val sections = remember(template, groups) { repo.groupsInOrder(template) }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item("hint") {
                Text(
                    t("Порядок здесь совпадает с экраном «День». Стрелки двигают пункт внутри группы — на экране плитки меняются местами. Двойные стрелки двигают группу целиком. " +
                        "В группу попадают только пункты её вида. Скрытые пункты не показываются в дне, но их данные остаются в графиках."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            val movable = sections.filter { (_, items) -> items.any { !it.hidden } }.map { it.first.id }
            sections.forEach { (g, items) ->
                val vis = items.filter { !it.hidden }
                val gi = movable.indexOf(g.id)
                item("g_" + g.id) {
                    GroupHeader(
                        g, vis.size, items.size - vis.size,
                        onTileMove = if (gi >= 0 && movable.size > 1) { d -> repo.moveGroup(g.id, d) } else null,
                        canUp = gi > 0, canDown = gi in 0 until movable.size - 1,
                        onAdd = { editing = TemplateItem(name = "", type = g.kind.types.first(), group = g.id); isNew = true; lockKind = g.kind },
                        onEdit = { groupEdit = g },
                    )
                }
                if (items.isEmpty()) item("e_" + g.id) {
                    Text(t("Пусто — добавьте пункт кнопкой «+» или удалите группу."), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 12.dp, bottom = 4.dp))
                }
                (vis + items.filter { it.hidden }).forEach { item ->
                    val k = vis.indexOf(item)
                    item(item.id) {
                        ItemRow(
                            item, canUp = k > 0, canDown = k >= 0 && k < vis.size - 1,
                            onClick = { editing = item; isNew = false; lockKind = null },
                            onHide = { repo.upsertItem(item.copy(hidden = !item.hidden)) },
                            onMove = { d -> repo.moveItem(item.id, d) },
                        )
                    }
                }
            }
            item("newGroup") {
                OutlinedButton(onClick = { newGroup = true }, Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Icon(Icons.Filled.CreateNewFolder, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text(t("Новая группа"))
                }
            }
            if (trash.isNotEmpty()) {
                item("trashHead") {
                    Column(Modifier.padding(top = 20.dp, start = 4.dp)) {
                        Text(t("Удалённые пункты"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Text(t("Данные удалённых пунктов сохраняются. Верните пункт — история снова появится в днях и графиках."),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                trash.forEach { d ->
                    item("trash_" + d.id) {
                        val days = remember(d.id) { repo.daysWithData(d.id) }
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                            Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(d.displayName, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(d.type.title + " · " + if (days > 0) t("записей: %d дн.", days) else t("без записей"),
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                                TextButton(onClick = { repo.restoreItem(d.id) }) {
                                    Icon(Icons.Filled.Restore, null, Modifier.size(18.dp)); Spacer(Modifier.size(4.dp)); Text(t("Вернуть"))
                                }
                                IconButton(onClick = { purge = d }) { Icon(Icons.Filled.DeleteForever, t("Удалить навсегда"), tint = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { editing = TemplateItem(name = "", type = ItemType.CHECK); isNew = true; lockKind = null },
            icon = { Icon(Icons.Filled.Add, null) }, text = { Text(t("Пункт")) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    editing?.let { item ->
        ItemDialog(
            item, isNew, groups = groups, lockKind = lockKind, canAddWellbeing = template.none { it.type == ItemType.WELLBEING },
            canAddTips = template.none { it.type == ItemType.TIPS },
            canAddTraining = template.none { it.type == ItemType.TRAINING },
            canAddGoal = template.none { it.type == ItemType.GOAL },
            canAddRhythm = template.none { it.type == ItemType.RHYTHM },
            onNewGroup = if (lockKind == null) { n, k -> repo.addGroup(n, k) } else null, effectOf = effectOf, onEffects = onEffects,
            sections = checkSections(template), sectionsOn = prefs?.checkSections ?: true, onSectionsOn = prefs?.let { p -> { v: Boolean -> p.checkSections = v } },
            onSave = { repo.upsertItem(it); editing = null },
            onDelete = { repo.deleteItem(item.id); editing = null },
            onDismiss = { editing = null },
        )
    }
    purge?.let { d ->
        AlertDialog(
            onDismissRequest = { purge = null },
            title = { Text(t("Удалить «%s» навсегда?", d.displayName)) },
            text = { Text(t("Пункт и все его записи (%d дн.) будут стёрты без возможности вернуть. Резервная копия, сделанная раньше, их ещё содержит.", repo.daysWithData(d.id))) },
            confirmButton = { TextButton(onClick = { repo.purgeItem(d.id); purge = null }) { Text(t("Удалить навсегда"), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { purge = null }) { Text(t("Отмена")) } },
        )
    }
    if (newGroup) GroupDialog(null, canDelete = false,
        onSave = { name, kind -> repo.addGroup(name, kind); newGroup = false },
        onDelete = {}, onDismiss = { newGroup = false })
    groupEdit?.let { g ->
        GroupDialog(g, canDelete = repo.canDeleteGroup(g.id),
            onSave = { name, _ -> repo.renameGroup(g.id, name); groupEdit = null },
            onDelete = { repo.deleteGroup(g.id); groupEdit = null },
            onDismiss = { groupEdit = null })
    }
}

@Composable
private fun GroupHeader(
    g: Group, count: Int, hidden: Int, onTileMove: ((Int) -> Unit)?, onAdd: () -> Unit, onEdit: () -> Unit,
    canUp: Boolean = true, canDown: Boolean = true,
) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).clickable(onClick = onEdit)) {
            Text(g.displayName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                g.kind.title + " · " + t("%d пунктов", count) + (if (hidden > 0) t(", скрыто %d", hidden) else "") +
                    (if (g.kind == GroupKind.CHECKS) " · " + t("одна плитка") else ""),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onTileMove != null) {
            IconButton(onClick = { onTileMove(-1) }, enabled = canUp) { Icon(Icons.Filled.KeyboardDoubleArrowUp, t("Группу выше")) }
            IconButton(onClick = { onTileMove(1) }, enabled = canDown) { Icon(Icons.Filled.KeyboardDoubleArrowDown, t("Группу ниже")) }
        }
        IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, t("Добавить в группу")) }
        IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, t("Изменить группу")) }
    }
}

@Composable
private fun ItemRow(item: TemplateItem, canUp: Boolean, canDown: Boolean, onClick: () -> Unit, onHide: () -> Unit, onMove: (Int) -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.displayName, style = MaterialTheme.typography.titleSmall,
                    color = if (item.hidden) MaterialTheme.colorScheme.outline else Color.Unspecified,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.source != Source.NONE) {
                        Icon(Icons.Filled.Watch, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.size(4.dp))
                    }
                    Text(
                        item.type.title + (if (item.plusAllowed) t(", с «+»") else "") + (if (item.section.isNotBlank()) " · " + item.section else "") +
                            (when (item.train) { "easy" -> " · " + t("совет: легче"); "rest" -> " · " + t("совет: отдых"); else -> "" }) +
                            (if (item.source != Source.NONE) " · ${item.source.title}" else ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onHide) {
                Icon(if (item.hidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, t("Скрыть/показать"))
            }
            IconButton(onClick = { onMove(-1) }, enabled = canUp) { Icon(Icons.Filled.ArrowUpward, t("Выше")) }
            IconButton(onClick = { onMove(1) }, enabled = canDown) { Icon(Icons.Filled.ArrowDownward, t("Ниже")) }
        }
    }
}

/** 5.61: вариант списка в окне пункта: [orig] — прежнее название (для переименования). */
data class OptRow(val orig: String, val name: String, val meta: OptMeta)

@Composable
private fun OptionRow(
    o: OptRow, open: Boolean, sectionsOn: Boolean, sections: List<String>, effect: Boolean?, showEffect: Boolean,
    onEffect: (Boolean?) -> Unit, onToggle: () -> Unit, onChange: (OptRow) -> Unit, onDelete: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var newSec by remember { mutableStateOf<String?>(null) }
    var eff by remember { mutableStateOf(effect) }
    Surface(color = cs.surfaceContainerHigh, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).testTag("opt_${o.name}"), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(o.name.ifBlank { t("Новый вариант") }, style = MaterialTheme.typography.bodyMedium)
                    val sub = listOfNotNull(
                        o.meta.section.takeIf { sectionsOn && it.isNotBlank() },
                        if (o.meta.daily) t("каждый день") else null,
                        when (eff) { true -> t("индекс +"); false -> t("индекс −"); null -> null },
                    )
                    if (sub.isNotEmpty()) Text(sub.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
                Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
            }
            if (open) {
                OutlinedTextField(o.name, { onChange(o.copy(name = it.take(40))) }, Modifier.fillMaxWidth().testTag("opt_name"),
                    label = { Text(t("Название")) }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t("Каждый день"))
                        Text(t("Добавки, витамины: в плитке видно, что сегодня ещё не отмечено"),
                            style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    }
                    Switch(o.meta.daily, { onChange(o.copy(meta = o.meta.copy(daily = it))) }, Modifier.testTag("opt_daily"))
                }
                if (sectionsOn) {
                    Text(t("Раздел в плитке"), style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.hScroll(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = o.meta.section.isBlank(), onClick = { onChange(o.copy(meta = o.meta.copy(section = ""))) }, label = { Text(t("Без раздела")) })
                        sections.forEach { sc -> FilterChip(selected = o.meta.section == sc, onClick = { onChange(o.copy(meta = o.meta.copy(section = sc))) }, label = { Text(sc) }) }
                        if (newSec == null) androidx.compose.material3.AssistChip(onClick = { newSec = "" }, label = { Text(t("Новый раздел")) },
                            leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)) })
                    }
                    newSec?.let { ns ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(ns, { newSec = it.take(30) }, Modifier.weight(1f), placeholder = { Text(t("Например, Добавки")) }, singleLine = true)
                            TextButton(enabled = ns.isNotBlank(), onClick = { onChange(o.copy(meta = o.meta.copy(section = ns.trim()))); newSec = null }) { Text(t("Готово")) }
                        }
                    }
                }
                if (showEffect) {
                    Text(t("Влияние на индекс дня"), style = MaterialTheme.typography.labelLarge)
                    Effect3(eff) { eff = it; onEffect(it) }
                }
                TextButton(onDelete) { Text(t("Удалить вариант"), color = cs.error) }
            }
        }
    }
}

/** Влияние на индекс: не влияет / плюс / минус. */
@Composable
private fun Effect3(v: Boolean?, short: Boolean = false, onChange: (Boolean?) -> Unit) {
    Row(if (short) Modifier else Modifier.hScroll(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        FilterChip(selected = v == null, onClick = { onChange(null) }, label = { Text(if (short) "0" else t("не влияет"), maxLines = 1, softWrap = false) })
        FilterChip(selected = v == true, onClick = { onChange(true) }, label = { Text(if (short) "＋" else t("＋ плюс"), maxLines = 1, softWrap = false) })
        FilterChip(selected = v == false, onClick = { onChange(false) }, label = { Text(if (short) "−" else t("− минус"), maxLines = 1, softWrap = false) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.errorContainer, selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer))
    }
}

/** Новая группа (g = null) или переименование/удаление существующей. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupDialog(g: Group?, canDelete: Boolean, onSave: (String, GroupKind) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(g?.displayName ?: "") }
    var kind by remember { mutableStateOf(g?.kind ?: GroupKind.CHECKS) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (g == null) t("Новая группа") else t("Группа")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, placeholder = { Text(t("Название группы")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (g == null) {
                    Text(t("Какие пункты в ней будут"), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GroupKind.entries.forEach { k -> FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.title) }) }
                    }
                    Text(
                        if (kind == GroupKind.CHECKS) t("Отметки группы показываются в дне одной плиткой.")
                        else t("Пункты группы остаются отдельными плитками, группа собирает их в списке."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(t("Вид: %s", g.kind.title), style = MaterialTheme.typography.bodySmall)
                    if (canDelete) TextButton(onClick = onDelete) { Text(t("Удалить группу"), color = MaterialTheme.colorScheme.error) }
                    else Text(t("Удалить можно пустую группу, если есть другая группа того же вида."),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name, kind) }) { Text(t("Сохранить")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Отмена")) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ItemDialog(
    item: TemplateItem, isNew: Boolean,
    onSave: (TemplateItem) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit,
    groups: List<Group> = emptyList(), lockKind: GroupKind? = null,
    /** Создать группу прямо из окна; null — без кнопки. */
    onNewGroup: ((String, GroupKind) -> Group)? = null,
    /** Текущее влияние на индекс по ключу метрики: null — не влияет, true — плюс, false — минус. */
    effectOf: ((String) -> Boolean?)? = null,
    onEffects: ((Map<String, Boolean?>) -> Unit)? = null,
    /** Чекапа нет в шаблоне — можно добавить заново. */
    canAddWellbeing: Boolean = false,
    canAddTips: Boolean = false,
    canAddTraining: Boolean = false,
    canAddGoal: Boolean = false,
    canAddRhythm: Boolean = false,
    /** 5.59.1: разделы отметок, которые уже есть (для выбора). */
    sections: List<String> = emptyList(),
    /** 5.59.2: разделы в плитке включены; [onSectionsOn] — переключить (null — без переключателя). */
    sectionsOn: Boolean = true,
    onSectionsOn: ((Boolean) -> Unit)? = null,
) {
    var train by remember { mutableStateOf(item.train) }
    // 5.61: варианты списка — строками со своими настройками (как отметки дня)
    var opts by remember { mutableStateOf(item.options.map { OptRow(it, it, item.optMeta[it] ?: OptMeta()) }) }
    var openOpt by remember { mutableStateOf<Int?>(null) }
    var secOn by remember { mutableStateOf(sectionsOn) }
    var group by remember { mutableStateOf(item.group) }
    var section by remember { mutableStateOf(item.section) }
    var newSection by remember { mutableStateOf<String?>(null) }
    var eff by remember { mutableStateOf(mapOf<String, Boolean?>()) }
    fun effect(key: String): Boolean? = if (key in eff) eff[key] else effectOf?.invoke(key)
    var newGroupName by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf(item.displayName) }
    var type by remember { mutableStateOf(item.type) }
    var plus by remember { mutableStateOf(item.plusAllowed) }
    var unit by remember { mutableStateOf(item.unit) }
    var min by remember { mutableStateOf(formatNumber(item.min)) }
    var max by remember { mutableStateOf(formatNumber(item.max)) }
    var options by remember { mutableStateOf(item.options.joinToString("\n")) }
    var source by remember { mutableStateOf(item.source) }
    var counter by remember { mutableStateOf(item.counter) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) t("Новый пункт") else t("Пункт")) },
        text = {
            val scroll = rememberScrollState()
            Box {
            // плотные чипы, как в «Отметках дня»: без 48dp зоны касания вокруг каждого
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalMinimumInteractiveComponentSize provides 0.dp) {
            Column(Modifier.verticalScroll(scroll).padding(end = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(t("Название")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                // тип — выпадающий список
                val types = ItemType.entries.filter { (lockKind == null || it in lockKind.types) && (it != ItemType.WELLBEING || item.type == ItemType.WELLBEING || canAddWellbeing) &&
                    (it != ItemType.TIPS || item.type == ItemType.TIPS || canAddTips) &&
                    (it != ItemType.TRAINING || item.type == ItemType.TRAINING || canAddTraining) &&
                    (it != ItemType.GOAL || item.type == ItemType.GOAL || canAddGoal) &&
                    (it != ItemType.RHYTHM || item.type == ItemType.RHYTHM || canAddRhythm) }
                var typeMenu by remember { mutableStateOf(false) }
                Text(t("Тип"), style = MaterialTheme.typography.labelLarge)
                Box {
                    androidx.compose.material3.OutlinedButton(
                        onClick = { typeMenu = true }, enabled = types.size > 1, modifier = Modifier.fillMaxWidth(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    ) {
                        Text(type.title, Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Icon(Icons.Filled.ArrowDropDown, null)
                    }
                    androidx.compose.material3.DropdownMenu(typeMenu, { typeMenu = false }) {
                        types.forEach { tp ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(tp.title, fontWeight = if (tp == type) androidx.compose.ui.text.font.FontWeight.SemiBold else null) },
                                onClick = { type = tp; typeMenu = false },
                                trailingIcon = if (tp == type) { { Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) } } else null,
                            )
                        }
                    }
                }
                val fit = groups.filter { it.kind == type.kind }
                val shown = groups.filter { lockKind == null || it.kind == lockKind }
                if (shown.isNotEmpty()) {
                    if (fit.isNotEmpty() && fit.none { it.id == group }) group = fit.first().id
                    Text(t("Группа"), style = MaterialTheme.typography.labelLarge)
                    // группы — одной строкой с горизонтальной прокруткой
                    Row(Modifier.hScroll(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        shown.forEach { g ->
                            FilterChip(selected = group == g.id, onClick = {
                                group = g.id
                                // группа другого вида — подбираем подходящий тип
                                if (g.kind != type.kind) type = g.kind.types.first { it != ItemType.WELLBEING && it != ItemType.TIPS && it != ItemType.TRAINING && it != ItemType.GOAL && it != ItemType.RHYTHM }
                            }, label = { Text(g.displayName) })
                        }
                        if (onNewGroup != null && newGroupName == null)
                            androidx.compose.material3.AssistChip(onClick = { newGroupName = "" }, label = { Text(t("Новая группа")) },
                                leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)) })
                    }
                    newGroupName?.let { ng ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(ng, { newGroupName = it }, Modifier.weight(1f), placeholder = { Text(t("Название группы")) }, singleLine = true)
                            TextButton(enabled = ng.isNotBlank(), onClick = {
                                val g = onNewGroup!!(ng.trim(), type.kind); group = g.id; newGroupName = null
                            }) { Text(t("Создать")) }
                        }
                        Text(t("Вид новой группы: %s", type.kind.title), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                when (type) {
                    ItemType.CHECK -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t("Можно ставить «+»"), Modifier.weight(1f))
                        Switch(plus, { plus = it })
                    }
                    // 5.59.1: раздел внутри плитки — отметки одного раздела идут вместе, под своим заголовком
                    if (onSectionsOn != null) Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t("Разделы в плитке"))
                            Text(if (secOn) t("Отметки с разделом идут под подзаголовком") else t("Все отметки одной компактной кучей"),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(secOn, { secOn = it; onSectionsOn(it) }, Modifier.testTag("sections_on"))
                    }
                    if (secOn) {
                    Text(t("Раздел в плитке"), style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.hScroll().testTag("sections"), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = section.isBlank(), onClick = { section = "" }, label = { Text(t("Без раздела")) })
                        (sections + listOfNotNull(section.takeIf { it.isNotBlank() && it !in sections })).forEach { sc ->
                            FilterChip(selected = section == sc, onClick = { section = sc }, label = { Text(sc) })
                        }
                        if (newSection == null) androidx.compose.material3.AssistChip(onClick = { newSection = "" }, label = { Text(t("Новый раздел")) },
                            leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)) })
                    }
                    newSection?.let { ns ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(ns, { newSection = it.take(30) }, Modifier.weight(1f).testTag("section_new"),
                                placeholder = { Text(t("Например, Привычки")) }, singleLine = true)
                            TextButton(enabled = ns.isNotBlank(), onClick = { section = ns.trim(); newSection = null }) { Text(t("Готово")) }
                        }
                    }
                    }
                    }
                    ItemType.SCALE -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(min, { min = it }, Modifier.weight(1f), label = { Text(t("от")) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        OutlinedTextField(max, { max = it }, Modifier.weight(1f), label = { Text(t("до")) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    }
                    ItemType.NUMBER -> OutlinedTextField(unit, { unit = it }, label = { Text(t("Единица (кг, см, мин…)")) }, singleLine = true)
                    ItemType.MULTI, ItemType.EXPENSES -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (type == ItemType.MULTI) Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t("Считать количество"))
                            Text(t("Нажатие — +1, удержание — сброс. Выключено — просто отметка."),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(counter, { counter = it })
                    }
                    if (type == ItemType.EXPENSES) OutlinedTextField(
                        options, { options = it },
                        label = { Text(t("Категории, по одной в строке")) },
                        minLines = 3, modifier = Modifier.fillMaxWidth(),
                    ) else {
                        if (onSectionsOn != null) Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(t("Разделы в плитке"))
                                Text(if (secOn) t("Варианты с разделом идут под подзаголовком") else t("Все варианты одной компактной кучей"),
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(secOn, { secOn = it; onSectionsOn(it) })
                        }
                        Text(t("Варианты"), style = MaterialTheme.typography.labelLarge)
                        val optSections = (sections + opts.map { it.meta.section.trim() }).filter { it.isNotEmpty() }.distinct()
                        opts.forEachIndexed { i, o ->
                            OptionRow(o, open = openOpt == i, sectionsOn = secOn, sections = optSections, showEffect = onEffects != null,
                                effect = if (onEffects != null) effect(item.id + ":" + o.orig.ifEmpty { o.name }) else null,
                                onEffect = { v -> eff = eff + ((item.id + ":" + o.name.trim()) to v) },
                                onToggle = { openOpt = if (openOpt == i) null else i },
                                onChange = { n -> opts = opts.mapIndexed { j, x -> if (j == i) n else x } },
                                onDelete = { opts = opts.filterIndexed { j, _ -> j != i }; openOpt = null })
                        }
                        TextButton({ opts = opts + OptRow("", "", OptMeta()); openOpt = opts.size - 1 }, Modifier.testTag("opt_add")) {
                            Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(t("Вариант"))
                        }
                    }
                    }
                    ItemType.WATER -> OutlinedTextField(max, { max = it }, Modifier.fillMaxWidth(), label = { Text(t("Норма в день, л")) },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    ItemType.BODY -> Text(
                        t("Все показатели последнего взвешивания из openScale: вес, жир, вода, мышцы и др. Подключение — во вкладке «Ещё»."),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    ItemType.MEALS -> Text(
                        t("Приёмы пищи с фото, описанием и калориями. Калории можно посчитать по фото (настройка во вкладке «Ещё»)."),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    else -> {}
                }
                if (type == ItemType.NUMBER || type == ItemType.DURATION) {
                    Text(t("Заполнять автоматически"), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val allowed = if (type == ItemType.DURATION) listOf(Source.NONE, Source.HC_SLEEP)
                        else listOf(Source.NONE, Source.HC_STEPS, Source.HC_EXERCISE, Source.HC_RESTING_HR, Source.HC_WEIGHT)
                        allowed.forEach { s ->
                            FilterChip(selected = source == s, onClick = { source = s }, label = { Text(s.title) })
                        }
                    }
                }
                if (type == ItemType.CHECK) Text(t("Учитывать в расчётах"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
                if (onEffects != null && (type == ItemType.CHECK || type == ItemType.MULTI)) {
                    Text(t("Влияние на индекс дня"), style = MaterialTheme.typography.labelLarge)
                    Text(t("Плюс — полезное: отметили — индекс выше. Минус — вредное (кофе, сигареты): отметили — индекс ниже.") +
                        if (type == ItemType.MULTI) " " + t("0 — не влияет.") else "",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (type == ItemType.CHECK) Effect3(effect(item.id)) { eff = eff + (item.id to it) }
                    else if (type == ItemType.MULTI) Text(t("Для каждого варианта — в его строке выше."), style = MaterialTheme.typography.bodySmall)
                    else {
                        val opts = options.lines().map { it.trim() }.filter { it.isNotEmpty() }
                        if (opts.isEmpty()) Text(t("Сначала впишите варианты."), style = MaterialTheme.typography.bodySmall)
                        opts.forEach { o ->
                            val k = item.id + ":" + o
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(o, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Effect3(effect(k), short = true) { eff = eff + (k to it) }
                            }
                        }
                    }
                }
                // 5.59.2: отметка в совете на тренировку («Состояние сегодня», вариант силовой)
                if (type == ItemType.CHECK) {
                    Text(t("Совет на тренировку"), style = MaterialTheme.typography.labelLarge)
                    Text(t("Отметили — совет в «Состоянии сегодня» и силовая подстроятся: «легче» — облегчённая тренировка, «отдых» — день восстановления."),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.hScroll().testTag("train_effect"), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("" to t("не влияет"), "easy" to t("легче"), "rest" to t("отдых")).forEach { (k, l) ->
                            FilterChip(selected = train == k, onClick = { train = k }, label = { Text(l) }, modifier = Modifier.testTag("trn_${k.ifEmpty { "no" }}"))
                        }
                    }
                }
                if (!isNew) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { confirmDelete = true }) {
                        Text(t("Удалить пункт"), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            }
            // высота полосы = высота содержимого: окно растёт по содержимому, а не на весь экран
            VerticalScrollbar(scroll, Modifier.matchParentSize().wrapContentWidth(Alignment.End))
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                val src = when {
                    type == ItemType.DURATION && source == Source.HC_SLEEP -> source
                    type == ItemType.NUMBER && source in listOf(Source.HC_STEPS, Source.HC_EXERCISE, Source.HC_RESTING_HR, Source.HC_WEIGHT) -> source
                    else -> Source.NONE
                }
                val valid = when (type) {
                    ItemType.CHECK -> setOf(item.id)
                    ItemType.MULTI -> opts.map { it.name.trim() }.filter { it.isNotEmpty() }.map { item.id + ":" + it }.toSet()
                    else -> emptySet()
                }
                eff.filterKeys { it in valid }.takeIf { it.isNotEmpty() }?.let { onEffects?.invoke(it) }
                onSave(
                    item.copy(
                        // вернувшийся чекап получает прежний id — отметки «пройден» в днях снова на месте
                        id = if (isNew && type == ItemType.WELLBEING) "wb" else if (isNew && type == ItemType.TIPS && canAddTips) "tips"
                            else if (isNew && type == ItemType.TRAINING && canAddTraining) "training"
                            else if (isNew && type == ItemType.GOAL && canAddGoal) "goal"
                            else if (isNew && type == ItemType.RHYTHM && canAddRhythm) "rhythm" else item.id,
                        name = if (name.trim() == item.displayName) item.name else name.trim(), type = type,
                        plusAllowed = type == ItemType.CHECK && plus,
                        min = if (type == ItemType.WATER) 0.0 else parseNumber(min) ?: 1.0,
                        max = parseNumber(max) ?: if (type == ItemType.WATER) 2.0 else 10.0,
                        unit = if (type == ItemType.WATER) "л" else if (type == ItemType.NUMBER) unit.trim() else "",
                        options = if (type == ItemType.MULTI) opts.map { it.name.trim() }.filter { it.isNotEmpty() }.distinct()
                            else if (type == ItemType.EXPENSES) options.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct() else emptyList(),
                        optMeta = if (type == ItemType.MULTI) opts.filter { it.name.isNotBlank() }.associate { it.name.trim() to it.meta.copy(section = it.meta.section.trim()) }.filterValues { !it.isEmpty } else emptyMap(),
                        source = if (type == ItemType.BODY) Source.OPENSCALE else src,
                        counter = counter,
                        group = group,
                        section = if (type == ItemType.CHECK) section.trim() else "",
                        train = if (type == ItemType.CHECK) train else "",
                    )
                )
            }) { Text(t("Сохранить")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Отмена")) } },
    )

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(t("Удалить «%s»?", item.displayName)) },
            text = {
                Text(if (item.type == ItemType.RHYTHM)
                    t("Плитка ритма исчезнет из дня. Показывать ритм можно и тонкой полосой над плитками: «Ещё → Ритм дня». Вернуть плитку: «+ Пункт» → тип «Ритм дня».")
                else if (item.type == ItemType.GOAL)
                    t("Плитка цели исчезнет из дня. Сама цель и программа — во вкладке «Программа». Вернуть: «+ Пункт» → тип «Цель».")
                else if (item.type == ItemType.TRAINING)
                    t("Плитка тренировки исчезнет из дня. Сами тренировки — на вкладке «Тренировки». Вернуть: «+ Пункт» → тип «Тренировка».")
                else if (item.type == ItemType.TIPS)
                    t("Плитка советов исчезнет из дня. Вернуть: «+ Пункт» → тип «Советы дня». Скрыть временно можно глазом в «Все пункты».")
                else if (item.type == ItemType.WELLBEING)
                    t("Плитка чекапа исчезнет из дня. Результаты прохождений сохранятся на вкладке «Самочувствие» и в графиках. Вернуть пункт: «+ Пункт» → тип «Чекап самочувствия».")
                else t("Пункт пропадёт из чек-листа и графиков, но его записи сохранятся: он попадёт в «Удалённые пункты» внизу списка «Все пункты», откуда его можно вернуть вместе с историей."))
            },
            confirmButton = { TextButton(onClick = onDelete) { Text(t("Удалить"), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(t("Отмена")) } },
        )
    }
}
