package org.maittaa.tabita

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Тихая строка над плитками: что ещё не записано для индекса (тап — открыть пункт),
 * цели «раз в неделю» и серия дней с записью. Без красного и без упрёков.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DayProgressLine(
    template: List<TemplateItem>, entries: Map<String, Map<String, String>>, cfg: MonthCfg,
    date: LocalDate, onOpen: (String) -> Unit,
) {
    if (date > LocalDate.now()) return
    val d = remember(template, entries, cfg, date) { MonthData(template, entries, date, cfg) }
    val di = remember(d) { d.dayIndex(date) }
    val (streak, frozen) = remember(d) { d.recordStreak(date) }
    val weekly = remember(d) {
        d.metrics.filter { it.kind == MKind.CHECK && (cfg.weekly[it.key] ?: 0) > 0 }
            .map { Triple(it, d.weekCount(it, date.with(DayOfWeek.MONDAY)), cfg.weekly[it.key]!!) }
    }
    if (di.total == 0 && weekly.isEmpty() && streak < 2) return
    val grey = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
        shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (di.total > 0) {
                if (di.missing.isEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        t("День записан") + (di.value?.let { " · " + t("индекс %d", Math.round(it)) } ?: ""),
                        style = MaterialTheme.typography.labelLarge,
                    )
                } else {
                    Text(
                        t("Для индекса записано %d из %d", di.recorded, di.total) +
                            (di.value?.takeIf { !di.partial }?.let { " · " + t("индекс %d", Math.round(it)) } ?: "") +
                            " · " + t("ещё:"),
                        style = MaterialTheme.typography.labelMedium, color = grey,
                    )
                    // одна строка с горизонтальной прокруткой; варианты одного списка — одним чипом с названием списка
                    val chips = di.missing.groupBy { it.item.id }.map { (_, l) -> l.first().item to if (l.size > 1 || l.first().kind == MKind.OPTION) l.first().item.displayName else l.first().title }
                    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalMinimumInteractiveComponentSize provides 0.dp) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            chips.forEach { (item, title) ->
                                SuggestionChip(onClick = { Log.d("Day", "дописать ${item.id}"); onOpen(item.id) }, label = { Text(title, maxLines = 1) })
                            }
                        }
                    }
                }
            }
            if (weekly.isNotEmpty() || streak >= 2) FlowRow(
                Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                weekly.forEach { (m, n, w) ->
                    Text(
                        (if (n >= w) "✓ " else "") + t("%s: %d из %d за неделю", m.title, minOf(n, w), w),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (n >= w) MaterialTheme.colorScheme.primary else grey,
                    )
                }
                if (streak >= 2) {
                    var info by remember { mutableStateOf(false) }
                    Row(Modifier.clickable { info = true }, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.LocalFireDepartment, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.tertiary)
                        Text(
                            " " + t("записи %d дн. подряд", streak) + if (frozen) " · " + t("1 пропуск на неделе прощён") else "",
                            style = MaterialTheme.typography.labelMedium, color = grey,
                        )
                    }
                    if (info) AlertDialog(
                        onDismissRequest = { info = false },
                        title = { Text(t("Серия записей")) },
                        text = {
                            Text(
                                t("Серия — сколько дней подряд вы что-то записывали вручную. Один незаполненный день в неделю (пн–вс) серию не обрывает — он прощается. Сегодняшний день, пока он не заполнен, тоже не обрывает серию.") +
                                    if (frozen) "\n\n" + t("На этой неделе пропуск уже прощён: если пропустить ещё один день до воскресенья, серия начнётся заново.")
                                    else "\n\n" + t("На этой неделе пропусков ещё не было."),
                            )
                        },
                        confirmButton = { TextButton(onClick = { info = false }) { Text(t("Понятно")) } },
                    )
                }
            }
        }
    }
}
