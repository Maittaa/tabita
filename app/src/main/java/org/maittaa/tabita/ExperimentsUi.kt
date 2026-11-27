package org.maittaa.tabita

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/** 5.62: «Эксперименты» в «Итогах»: идущие и законченные, новый — гипотеза, срок, показатели. */
@Composable
fun ExperimentsCard(repo: Repo, prefs: Prefs) {
    val cs = MaterialTheme.colorScheme
    val entries = collect(repo.entries); val template = collect(repo.template)
    var tick by remember { mutableIntStateOf(0) }
    val list = remember(tick) { Experiments.all(prefs).sortedWith(compareBy<Experiment> { it.done() }.thenByDescending { it.start }) }
    val opts = remember(entries, template, tick) { Experiments.options(repo, prefs) }
    var creating by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<String?>(null) }
    Card(Modifier.fillMaxWidth().testTag("exp_card"), colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Science, null, Modifier.size(20.dp), tint = cs.primary); Spacer(Modifier.width(8.dp))
                Text(t("Эксперименты"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            }
            if (list.isEmpty()) Text(t("Проверьте гипотезу на себе: «две недели ложусь до 23:00», «йога перед сном», «без кофе после обеда». Приложение сравнит показатели за срок эксперимента с таким же сроком до него."),
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            list.forEach { e ->
                ExperimentRow(e, opts, expanded = open == e.id, onToggle = { open = if (open == e.id) null else e.id },
                    onStop = { Experiments.upsert(prefs, e.copy(stopped = LocalDate.now())); tick++ },
                    onDelete = { Experiments.remove(prefs, e.id); tick++ })
            }
            OutlinedButton({ creating = true }, Modifier.fillMaxWidth().testTag("exp_new")) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Новый эксперимент"))
            }
        }
    }
    if (creating) NewExperimentDialog(opts, onSave = { Experiments.upsert(prefs, it); tick++; creating = false; open = it.id }, onDismiss = { creating = false })
}

@Composable
private fun ExperimentRow(e: Experiment, opts: List<Experiments.Option>, expanded: Boolean, onToggle: () -> Unit, onStop: () -> Unit, onDelete: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val today = LocalDate.now()
    val rows = remember(e, opts) { Experiments.result(e, opts) }
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 4.dp).testTag("exp_${e.id}"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(e.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        val status = when {
            !e.started(today) -> t("начнётся %s", dateText(e.start))
            !e.done(today) -> t("день %d из %d · до %s", e.dayNo(today), e.days, dateText(e.plannedEnd))
            else -> t("закончен %s · %d дн.", dateText(e.end), java.time.temporal.ChronoUnit.DAYS.between(e.start, e.end).toInt() + 1)
        }
        Text(status, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        if (e.started(today) && !e.done(today)) LinearProgressIndicator(progress = { e.dayNo(today).toFloat() / e.days }, modifier = Modifier.fillMaxWidth())
        // кратко: заметные изменения
        val notable = rows.filter { it.verdict == 1 || it.verdict == -1 }
        if (!expanded && e.started(today)) Text(
            if (notable.isEmpty()) (if (rows.any { it.verdict == 0 }) t("заметных изменений пока нет") else t("данных пока мало"))
            else notable.joinToString(" · ") { r -> "${r.title} ${Experiments.fmtDiff(r)}" },
            style = MaterialTheme.typography.bodySmall, color = if (notable.any { it.verdict == -1 }) cs.error else if (notable.isNotEmpty()) cs.primary else cs.onSurfaceVariant)
        if (expanded) {
            rows.forEach { r ->
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(r.title, style = MaterialTheme.typography.bodyMedium)
                        Text(t("до: %s (%d дн.) → во время: %s (%d дн.)", r.before?.let(r.format) ?: "—", r.nBefore, r.during?.let(r.format) ?: "—", r.nDuring),
                            style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(Experiments.fmtDiff(r), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                            color = when (r.verdict) { 1 -> cs.primary; -1 -> cs.error; else -> cs.onSurface })
                        Text(when (r.verdict) { 1 -> t("заметно лучше"); -1 -> t("заметно хуже"); 0 -> t("в пределах колебаний"); else -> t("мало данных") },
                            style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    }
                }
            }
            Text(t("«Заметно» — разница больше обычных колебаний от дня ко дню (95 %% интервал не включает ноль), нужно хотя бы %d дней до и во время. Это не доказательство причины: в те же дни могли измениться нагрузка, погода, здоровье.", Experiments.MIN_DAYS),
                style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            Row {
                if (!e.done(today)) TextButton(onStop) { Text(t("Закончить сейчас")) }
                TextButton({ confirm = true }) { Text(t("Удалить"), color = cs.error) }
            }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text(t("Удалить эксперимент?")) },
        text = { Text(t("Записи в днях останутся, пропадёт только сравнение.")) },
        confirmButton = { TextButton({ confirm = false; onDelete() }) { Text(t("Удалить"), color = cs.error) } },
        dismissButton = { TextButton({ confirm = false }) { Text(t("Отмена")) } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewExperimentDialog(opts: List<Experiments.Option>, onSave: (Experiment) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var days by remember { mutableIntStateOf(14) }
    var tomorrow by remember { mutableStateOf(false) }
    val defaults = opts.map { it.key }.filter { it == Experiment.INDEX || opts.firstOrNull { o -> o.key == it }?.title in setOf(t("Сон"), t("Оценка дня"), t("Пульс покоя")) }
    var sel by remember { mutableStateOf(defaults.toSet().ifEmpty { opts.take(3).map { it.key }.toSet() }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Новый эксперимент")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it.take(60) }, Modifier.fillMaxWidth().testTag("exp_title"), label = { Text(t("Что пробую")) },
                    placeholder = { Text(t("Ложусь до 23:00")) }, singleLine = true)
                Text(t("Начало"), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(!tomorrow, { tomorrow = false }, label = { Text(t("Сегодня")) })
                    FilterChip(tomorrow, { tomorrow = true }, label = { Text(t("Завтра")) })
                }
                Text(t("Срок"), style = MaterialTheme.typography.labelLarge)
                // 5.62.1: на узком экране чипы переносятся, подпись не ломается по буквам
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(7, 14, 21, 28).forEach { d -> FilterChip(days == d, { days = d }, label = { Text(t("%d дн.", d), maxLines = 1, softWrap = false) },
                        modifier = Modifier.testTag("exp_days_$d")) }
                }
                Text(t("Сравнение — с теми же %d днями до начала. Две недели и дольше дают более надёжный результат.", days),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(t("Что смотреть"), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    opts.forEach { o -> FilterChip(o.key in sel, { sel = if (o.key in sel) sel - o.key else sel + o.key }, label = { Text(o.title) }) }
                }
            }
        },
        confirmButton = { TextButton({
            onSave(Experiment.new(title, LocalDate.now().plusDays(if (tomorrow) 1 else 0), days, opts.map { it.key }.filter { it in sel }))
        }, enabled = title.isNotBlank() && sel.isNotEmpty(), modifier = Modifier.testTag("exp_save")) { Text(t("Начать")) } },
        dismissButton = { TextButton(onDismiss) { Text(t("Отмена")) } },
    )
}
