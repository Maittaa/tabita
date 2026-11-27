package org.maittaa.tabita

import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.util.UUID

/** Короткое описание шаблона: вид · N позиций · ккал · белок. */
fun MealTpl.summary(): String = listOfNotNull(
    t(kind),
    if (items.size > 1) t("%d позиций", items.size) else null,
    kcal?.let { t("%s ккал", formatNumber(Math.round(it).toDouble())) },
    protein?.takeIf { it > 0 }?.let { t("белок %s г", formatNumber(Math.round(it).toDouble())) },
).joinToString(" · ")

/**
 * 5.57.1: выбор шаблона для дня — список и подтверждение одной кнопкой «Добавить»,
 * чтобы случайное нажатие не записало приём из нескольких позиций.
 */
@Composable
fun MealTplPicker(tpls: List<MealTpl>, date: String, onPick: (MealTpl) -> Unit, onManage: () -> Unit, onDismiss: () -> Unit) {
    var sel by remember { mutableStateOf<String?>(null) }
    val chosen = tpls.firstOrNull { it.id == sel }
    val dayText = if (date == LocalDate.now().toString()) t("сегодня") else runCatching { dateText(LocalDate.parse(date)) }.getOrDefault(date)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Из шаблона")) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                tpls.forEach { tp ->
                    Row(Modifier.fillMaxWidth().clickable { sel = tp.id }.padding(vertical = 2.dp).testTag("pick_${tp.id}"),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(sel == tp.id, null)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(tp.name, style = MaterialTheme.typography.bodyMedium)
                            Text(tp.summary(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                chosen?.let { c ->
                    Spacer(Modifier.height(8.dp))
                    Text(if (c.items.size > 1) t("Добавится %d позиций одним приёмом — %s.", c.items.size, dayText) else t("Добавится в дневник — %s.", dayText),
                        style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onManage, Modifier.padding(top = 4.dp)) { Text(t("Изменить шаблоны")) }
            }
        },
        confirmButton = { Button({ chosen?.let(onPick) }, enabled = chosen != null, modifier = Modifier.testTag("pick_ok")) { Text(t("Добавить")) } },
        dismissButton = { TextButton(onDismiss) { Text(t("Отмена")) } },
    )
}

/** 5.57.1: «Ещё → Шаблоны еды» — список, создание, правка, удаление. */
@Composable
fun MealTplsBody(act: MainActivity, notify: (String) -> Unit = {}) {
    val prefs = act.prefs; val cs = MaterialTheme.colorScheme
    var tick by remember { mutableIntStateOf(0) }
    val list = remember(tick) { MealTpls.all(prefs) }
    var edit by remember { mutableStateOf<MealTpl?>(null) }
    var del by remember { mutableStateOf<MealTpl?>(null) }
    val e = edit
    if (e != null) {
        MealTplEditor(act, e, notify, onSave = { n ->
            // фото, которые убрали из шаблона, — удалить
            val keep = n.items.flatMap { it.photos }.toSet()
            MealTpls.deletePhotos(act.repo.photosDir, e.items.flatMap { it.photos }.filter { it !in keep })
            MealTpls.save(prefs, if (list.any { it.id == n.id }) list.map { if (it.id == n.id) n else it } else list + n)
            Log.i("Food", "шаблон сохранён: ${n.name}"); edit = null; tick++
        }, onCancel = { added ->
            MealTpls.deletePhotos(act.repo.photosDir, added); edit = null
        })
        return
    }
    Text(style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, text = t("Шаблон — приём пищи, который добавляется в дневник одним выбором: кнопка «Шаблон» в карточке питания. Сохранить приём как шаблон можно и из самого приёма."))
    if (list.isEmpty()) Text(t("Шаблонов нет."), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
    list.forEach { tp ->
        Surface(color = cs.surfaceContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            Row(Modifier.clickable { edit = tp }.padding(start = 14.dp, top = 8.dp, bottom = 8.dp).testTag("tpl_row_${tp.id}"),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tp.name, style = MaterialTheme.typography.bodyLarge)
                    Text(tp.summary(), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
                IconButton({ del = tp }) { Icon(Icons.Filled.Delete, t("Удалить")) }
            }
        }
    }
    OutlinedButton({ edit = MealTpl(UUID.randomUUID().toString().take(8), "", mealKindsRu[0], emptyList()) },
        Modifier.fillMaxWidth().padding(top = 6.dp).testTag("tpl_new")) {
        Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text(t("Новый шаблон"))
    }
    del?.let { d ->
        AlertDialog(
            onDismissRequest = { del = null },
            title = { Text(t("Удалить шаблон «%s»?", d.name)) },
            text = { Text(t("Записи в дневнике, добавленные из него, останутся.")) },
            confirmButton = { TextButton({ MealTpls.remove(prefs, d.id, act.repo.photosDir); del = null; tick++ }) { Text(t("Удалить"), color = cs.error) } },
            dismissButton = { TextButton({ del = null }) { Text(t("Отмена")) } },
        )
    }
}

@Composable
private fun MealTplEditor(act: MainActivity, src: MealTpl, notify: (String) -> Unit, onSave: (MealTpl) -> Unit, onCancel: (added: List<String>) -> Unit) {
    val cs = MaterialTheme.colorScheme; val dir = act.repo.photosDir
    var name by remember { mutableStateOf(src.name) }
    var kind by remember { mutableStateOf(src.kind) }
    var items by remember { mutableStateOf(src.items) }
    var open by remember { mutableStateOf<String?>(null) }
    val srcPhotos = remember { src.items.flatMap { it.photos }.toSet() }
    fun blank(m: Meal) = m.text.isBlank() && m.photos.isEmpty() && m.kcal == null
    Text(if (src.name.isBlank()) t("Новый шаблон") else t("Шаблон"), style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(name, { name = it.take(40) }, Modifier.fillMaxWidth().testTag("tpl_name"), label = { Text(t("Название")) }, singleLine = true)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        mealKindsRu.forEach { k -> FilterChip(kind == k, { kind = k }, label = { Text(t(k)) }) }
    }
    if (items.isEmpty()) Text(t("Добавьте позицию: фото, описание — калории и БЖУ посчитает ИИ или впишите сами."),
        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    items.forEachIndexed { i, m ->
        Surface(color = cs.surfaceContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            Row(Modifier.clickable { open = m.id }.padding(10.dp).testTag("tpl_item_$i"), verticalAlignment = Alignment.CenterVertically) {
                m.photos.firstOrNull()?.let { PhotoImage(java.io.File(dir, it), 160, Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))); Spacer(Modifier.width(10.dp)) }
                Column(Modifier.weight(1f)) {
                    Text(m.text.ifBlank { t("Позиция %d", i + 1) }, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                    Text(listOfNotNull(m.kcal?.let { t("%s ккал", formatNumber(Math.round(it).toDouble())) },
                        mealMacros(listOf(m))?.short()).joinToString(" · ").ifEmpty { t("калории не заданы") },
                        style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
            }
        }
    }
    TextButton({ val n = Meal(kind = kind); items = items + n; open = n.id }, Modifier.testTag("tpl_add_item")) {
        Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text(t("Добавить позицию"))
    }
    val ok = items.any { !blank(it) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton({ onCancel(items.flatMap { it.photos }.filter { it !in srcPhotos }) }) { Text(t("Отмена")) }
        Button({
            val clean = items.filterNot(::blank).map { it.copy(kind = kind, at = null, link = "") }
            onSave(src.copy(name = name.trim().ifBlank { MealTpl.defaultName(clean) }, kind = kind, items = clean))
        }, enabled = ok, modifier = Modifier.testTag("tpl_ok")) { Text(t("Сохранить")) }
    }
    // позиция — та же карточка, что в чек-листе: фото, расчёт ИИ, уточнение, ГИ/ГН, подсказки и подтверждение удаления
    val editing = items.firstOrNull { it.id == open }
    if (editing != null) {
        val idx = items.indexOf(editing)
        fun done() { if (blank(editing)) items = items.filterNot { it.id == editing.id }; open = null }
        androidx.compose.ui.window.Dialog(onDismissRequest = { done() },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Surface(Modifier.fillMaxWidth(0.95f).padding(vertical = 24.dp), shape = RoundedCornerShape(20.dp), tonalElevation = 6.dp) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(4.dp).imePadding()) {
                    MealCard(act, "", editing.copy(kind = kind), notify,
                        onChange = { m -> items = items.map { if (it.id == m.id) m.copy(kind = kind, at = null) else it } },
                        onDelete = {
                            MealTpls.deletePhotos(dir, editing.photos.filter { it !in srcPhotos })
                            items = items.filterNot { it.id == editing.id }; open = null
                        },
                        onDone = { done() }, title = t("Позиция %d", idx + 1), showKind = false)
                }
            }
        }
    }
}

