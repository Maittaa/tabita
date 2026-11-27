package org.maittaa.tabita

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import kotlinx.coroutines.launch
import org.maittaa.tabita.train.SChart
import org.maittaa.tabita.train.SeriesChart
import java.time.LocalDate

/**
 * 5.54: «Активность» — активные минуты за весь день по пульсу с часов против нормы ВОЗ (150–300 в неделю),
 * по дням недели, по неделям и шаги. Без доступа к пульсу — минуты только по записанным занятиям.
 */
@Composable
fun ActivityScreen(act: MainActivity, modifier: Modifier) {
    val entries = collect(act.repo.entries)
    val today = LocalDate.now()
    val start = Cardio.weekStart(today)
    var heart by remember { mutableStateOf<Boolean?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(refresh) {
        heart = CardioData.override?.heart ?: runCatching { act.health.hrPermission in act.health.granted() }.getOrDefault(false)
    }
    val perm = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { res ->
        refresh++
        if (res.isNotEmpty()) scope.launch { act.syncHealth(today.minusDays(29), today); refresh++ }
    }
    val week = ActiveMin.week(entries, start)
    val hasDays = week.any { it.second != null }
    val sessions = remember(entries) { Cardio.week(Cardio.sessions(act.repo, start, today), start, HrZones.of(act.repo, act.prefs)) }
    val total = ActiveMin.weekTotal(entries, start) ?: sessions.active.takeIf { sessions.activeKnown }
    LazyColumn(modifier.fillMaxSize().testTag("activity"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (heart == false) item {
            val cs = MaterialTheme.colorScheme
            Card(colors = CardDefaults.cardColors(containerColor = cs.tertiaryContainer, contentColor = cs.onTertiaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(t("Нет пульса за день"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(t("Активные минуты за весь день считаются по пульсу с часов. Сейчас доступа к пульсу нет — считаются только записанные занятия."),
                        style = MaterialTheme.typography.bodySmall)
                    if (act.health.available) TextButton({
                        runCatching { perm.launch(setOf(act.health.hrPermission, act.health.exercisePermission, act.health.distPermission)) }
                    }) { Text(t("Дать доступ"), fontWeight = FontWeight.SemiBold, color = cs.onTertiaryContainer) }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth().testTag("activity_week")) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(t("Эта неделя"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${total ?: 0}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(6.dp))
                        Text(t("активных мин из %d", Cardio.WEEK_MIN), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    LinearProgressIndicator({ ((total ?: 0) / Cardio.WEEK_MIN.toFloat()).coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
                    if (hasDays) WeekBars(week, today)
                    Text(if (hasDays) t("По пульсу за весь день: умеренная зона ×1, высокая и пиковая ×2 — как «минуты активности» в Google Health. Норма ВОЗ — 150–300 в неделю.")
                        else t("По записанным занятиям: умеренно ×1, интенсивно ×2. Норма ВОЗ — 150–300 в неделю."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (hasDays) item {
            val weeks = (11 downTo 0).map { start.minusWeeks(it.toLong()) }.map { w -> w to (ActiveMin.weekTotal(entries, w) ?: 0).toDouble() }
            ChartCard(t("Активные минуты по неделям"), t("Линия — норма ВОЗ 150 в неделю. Интенсивная минута — за две.")) {
                SeriesChart(weeks, SChart.BARS, { formatNumber(it) }, target = Cardio.WEEK_MIN.toDouble())
            }
        }
        val stepsItem = act.repo.template.value.firstOrNull { it.source == Source.HC_STEPS }
        if (stepsItem != null) {
            val pts = (13 downTo 0).map { today.minusDays(it.toLong()) }.mapNotNull { d -> parseNumber(entries[d.key()]?.get(stepsItem.id))?.let { d to it } }
            if (pts.size >= 2) item {
                ChartCard(t("Шаги за 2 недели"), t("Шаги — сколько вы двигались; активные минуты — сколько из этого было с повышенным пульсом.")) {
                    SeriesChart(pts, SChart.BARS, { formatNumber(it) })
                }
            }
        }
    }
}

/** Столбики по дням недели: умеренная зона снизу, высокая и пиковая (×2) сверху. */
@Composable
private fun WeekBars(week: List<Pair<LocalDate, ActiveMin.Day?>>, today: LocalDate) {
    val max = (week.maxOfOrNull { it.second?.active ?: 0 } ?: 0).coerceAtLeast(30)
    val mod = zoneColor(1); val vig = zoneColor(2); val empty = MaterialTheme.colorScheme.surfaceVariant
    Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        week.forEach { (d, day) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(day?.active?.toString() ?: "", style = MaterialTheme.typography.labelSmall)
                Canvas(Modifier.weight(1f).fillMaxWidth()) {
                    val h = size.height
                    val r = CornerRadius(6f, 6f)
                    if (day == null || d.isAfter(today)) {
                        drawRoundRect(empty, Offset(0f, h - 6f), Size(size.width, 6f), r)
                    } else {
                        val hm = h * day.mod / max
                        val hv = h * 2 * (day.vig + day.peak) / max
                        drawRoundRect(mod, Offset(0f, h - hm), Size(size.width, hm), r)
                        if (hv > 0) drawRoundRect(vig, Offset(0f, h - hm - hv), Size(size.width, hv), r)
                    }
                }
                Text((if (I18n.isEn) listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su") else listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"))[d.dayOfWeek.value - 1], style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (d == today) FontWeight.Bold else null, textAlign = TextAlign.Center)
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(mod)); Spacer(Modifier.width(4.dp))
        Text(t("умеренная"), style = MaterialTheme.typography.labelSmall); Spacer(Modifier.width(12.dp))
        Box(Modifier.size(10.dp).clip(CircleShape).background(vig)); Spacer(Modifier.width(4.dp))
        Text(t("высокая и пиковая ×2"), style = MaterialTheme.typography.labelSmall)
    }
}
