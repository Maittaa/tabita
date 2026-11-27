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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
    /** 5.64: «Как вчера» — открыть выбор переноса. */
    onCopy: ((List<DayCopy.Candidate>) -> Unit)? = null,
    /** 5.65: свежая находка «Что влияет» — одной строкой, если других сигналов меньше двух. */
    finding: String? = null,
    covered: Set<String> = emptySet(),
) {
    if (date > LocalDate.now()) return
    val d = remember(template, entries, cfg, date) { MonthData(template, entries, date, cfg) }
    val di = remember(d) { d.dayIndex(date) }
    val st = remember(d) { d.streak(date) }
    val streak = st.days
    val weekly = remember(d) {
        d.metrics.filter { it.kind == MKind.CHECK && (cfg.weekly[it.key] ?: 0) > 0 }
            .map { Triple(it, d.weekCount(it, date.with(DayOfWeek.MONDAY)), cfg.weekly[it.key]!!) }
    }
    // 5.64: «как вчера» и «заполнилось само»
    val copy = remember(template, entries, date) { DayCopy.fromYesterday(template, entries, date) }
    val auto = remember(template, entries, date) { DayCopy.automatic(template, entries[date.key()] ?: emptyMap()) }
    // 5.65: что значат сегодняшние данные — сравнение с вашей обычной нормой
    val insights = remember(template, entries, date, finding, covered) { DayInsight.lines(template, entries, date, finding = finding, covered = covered) }
    if (di.total == 0 && weekly.isEmpty() && streak < 2 && st.freezes == 0 && copy.isEmpty() && auto == 0 && insights.isEmpty()) return
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
                        Row(Modifier.fillMaxWidth().hScroll().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            chips.forEach { (item, title) ->
                                SuggestionChip(onClick = { Log.d("Day", "дописать ${item.id}"); onOpen(item.id) }, label = { Text(title, maxLines = 1) })
                            }
                        }
                    }
                }
            }
            insights.forEachIndexed { i, line ->
                Row(Modifier.padding(top = if (i == 0 && di.total > 0) 6.dp else 2.dp).testTag("insight_$i")) {
                    Icon(Icons.Filled.Lightbulb, null, Modifier.size(15.dp).padding(top = 1.dp), tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.width(6.dp))
                    Text(line, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (copy.isNotEmpty() || auto > 0) Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (auto > 0) Text(t("само заполнилось: %d", auto), style = MaterialTheme.typography.labelMedium, color = grey,
                    modifier = Modifier.weight(1f).testTag("auto_filled"))
                else Spacer(Modifier.weight(1f))
                if (copy.isNotEmpty() && onCopy != null) androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalMinimumInteractiveComponentSize provides 0.dp) {
                    SuggestionChip(onClick = { onCopy(copy) }, label = { Text(t("Как вчера"), maxLines = 1) },
                        icon = { Icon(Icons.Filled.ContentCopy, null, Modifier.size(14.dp)) }, modifier = Modifier.testTag("copy_yday"))
                }
            }
            if (weekly.isNotEmpty() || streak >= 2 || st.freezes > 0) FlowRow(
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
                if (streak >= 2 || st.freezes > 0) {
                    var info by remember { mutableStateOf(false) }
                    Row(Modifier.clickable { info = true }.testTag("streak"), verticalAlignment = Alignment.CenterVertically) {
                        if (streak >= 2) {
                            Icon(Icons.Filled.LocalFireDepartment, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.tertiary)
                            Text(" " + t("записи %d дн. подряд", streak) + "   ", style = MaterialTheme.typography.labelMedium, color = grey)
                        }
                        Icon(Icons.Filled.AcUnit, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(" " + t("заморозки %d из %d", st.freezes, MonthData.MAX_FREEZES) +
                            (if (st.frozen.any { it >= date.minusDays(2) }) " · " + t("пропуск сохранён") else ""), style = MaterialTheme.typography.labelMedium, color = grey,
                            modifier = Modifier.testTag("freezes"))
                    }
                    if (info) AlertDialog(
                        onDismissRequest = { info = false },
                        title = { Text(t("Серия и заморозки")) },
                        text = {
                            val recent = st.frozen.filter { it > date.minusDays(14) }
                            Text(
                                t("Серия — сколько дней подряд вы что-то записывали вручную. Сегодняшний день, пока он не заполнен, серию не обрывает.") + "\n\n" +
                                    t("Заморозки берегут серию, когда день пропущен — болезнь, поездка, просто не до того. За каждые 7 заполненных дней даётся одна, запас — не больше 3. Пропуск тратит их сам; если пропуск длиннее запаса, серия начнётся заново, а заморозки останутся.") + "\n\n" +
                                    (if (st.freezes >= MonthData.MAX_FREEZES) t("Сейчас запас полный: %d из %d.", st.freezes, MonthData.MAX_FREEZES)
                                    else t("Сейчас: %d из %d, до следующей — %d дн. с записью.", st.freezes, MonthData.MAX_FREEZES, MonthData.FREEZE_EVERY - st.toNext)) +
                                    (if (recent.isNotEmpty()) "\n" + t("Заморожено за 2 недели: %s.", recent.joinToString { dateText(it) }) else ""),
                            )
                        },
                        confirmButton = { TextButton(onClick = { info = false }) { Text(t("Понятно")) } },
                    )
                }
            }
        }
    }
}
