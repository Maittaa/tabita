package org.maittaa.tabita

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZoneId

private fun hm(min: Int) = "%d:%02d".format(((min % 1440) + 1440) % 1440 / 60, ((min % 1440) + 1440) % 1440 % 60)

@Composable
private fun Card(title: String, tag: String? = null, content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().let { if (tag != null) it.testTag(tag) else it }) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

@Composable
private fun KV(k: String, v: String, sub: String? = null, bad: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.Top) {
        Text(k, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Column(horizontalAlignment = Alignment.End) {
            Text(v, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = if (bad) cs.error else cs.onSurface)
            sub?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant) }
        }
    }
}

/** 5.60: «Сон» — последняя ночь, регулярность, ночи за 2 недели, фазы в среднем. */
@Composable
fun SleepScreen(act: MainActivity, modifier: Modifier) {
    val repo = act.repo; val cs = MaterialTheme.colorScheme
    val entries = collect(repo.entries)
    val nights = remember(entries) { SleepStats.nights(repo) }
    val r = remember(entries) { runCatching { DayRhythm.of(repo, LocalDate.now(), act.prefs) }.getOrNull() }
    val item = repo.template.value.firstOrNull { it.source == Source.HC_SLEEP }
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (item == null || nights.isEmpty()) {
            Text(t("Ночей с часов пока нет. Подключите Health Connect в «Ещё» и разрешите сон — время засыпания, подъёма и фазы подтянутся сами."),
                style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        // ---- последняя ночь ----
        val last = nights.last()
        Card(t("Последняя ночь — %s", dateText(last.date)), "sleep_last") {
            val mins = parseNumber(entries[last.date.key()]?.get(item.id)) ?: last.d.asleep.toDouble().takeIf { it > 0 } ?: last.d.inBed.toDouble()
            val sc = Sleep.score(mins, last.d, Sleep.goal(act.prefs, item))
            KV(t("Сон"), formatMinutes(mins), t("%s – %s", Sleep.clock(last.d.start), Sleep.clock(last.d.end)))
            KV(t("Оценка"), "${sc.total} · ${Sleep.bandName(Sleep.band(sc.total))}")
            if (last.d.hasStages) {
                Hypnogram(last.d, Modifier.fillMaxWidth().height(110.dp))
                KV(t("Пробуждения"), last.d.wakes.toString(), last.d.efficiency?.let { t("эффективность %d%%", Math.round(it * 100).toInt()) })
            }
        }
        // ---- регулярность ----
        val sri = remember(nights) { SleepStats.sri(nights) }
        Card(t("Регулярность"), "sleep_regular") {
            if (sri != null) {
                val band = SleepStats.sriBand(sri)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(sri.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold,
                        color = if (band == 2) cs.error else cs.primary, modifier = Modifier.testTag("sri"))
                    Spacer(Modifier.padding(4.dp))
                    Text(t("индекс регулярности за 2 недели"), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                }
                Text(when (band) {
                    0 -> t("Регулярнее, чем у трёх четвертей людей в UK Biobank.")
                    1 -> t("Как у большинства: в UK Biobank у половины людей 74–86.")
                    else -> t("Ниже, чем у большинства: в UK Biobank у половины людей 74–86. Сон и подъём в одно время поднимут индекс.")
                }, style = MaterialTheme.typography.bodySmall)
            } else Text(t("Индекс регулярности появится, когда наберётся 5 пар ночей подряд с часов."), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            r?.bedSpread?.let { KV(t("Отбой гуляет"), t("± %d мин", it), t("за последние ночи"), bad = it > 60) }
            if (r?.wakeWeekday != null && r.wakeWeekend != null) KV(t("Подъём"), t("будни %s · выходные %s", hm(r.wakeWeekday), hm(r.wakeWeekend)))
            r?.jetlag?.let { j ->
                KV(t("Социальный джетлаг"), formatMinutes(j.toDouble()), t("сдвиг середины сна в выходные"), bad = j >= 60)
                if (j >= 60) Text(t("Больше часа: по утрам понедельника организм живёт будто в другом часовом поясе. Держите подъём в выходные не позже чем на час от будней."),
                    style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
            Text(t("Индекс регулярности (Phillips, 2017): насколько совпадает сон в одно и то же время суток день ко дню, 100 — минута в минуту. В UK Biobank (Windred, 2024) регулярность сильнее связана со смертностью, чем длительность сна."),
                style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        }
        // ---- ночи за 2 недели ----
        Card(t("Ночи за 2 недели"), "sleep_map") {
            NightMap(nights)
            Text(t("Полоса — от засыпания до подъёма, светлее — без сна внутри ночи. Чем ровнее полосы, тем регулярнее режим."),
                style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        }
        // ---- фазы ----
        SleepStats.stages(nights)?.let { s ->
            Card(t("Фазы в среднем")) {
                KV(Sleep.stageName(3), "${s.deep}%", t("обычно %s", Sleep.stageNorm(3)), bad = s.deep < 13)
                KV(Sleep.stageName(2), "${s.light}%", t("обычно %s", Sleep.stageNorm(2)))
                KV(Sleep.stageName(1), "${s.rem}%", t("обычно %s", Sleep.stageNorm(1)), bad = s.rem < 20)
                KV(Sleep.stageName(0), formatMinutes(s.awakeMin.toDouble()), t("за ночь"))
                Text(t("По %d ночам с разбивкой. Фазы с часов — оценка по пульсу и движению, а не замер мозговой активности.", s.nights),
                    style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Карта ночей: строка — ночь, ось 20:00 → 12:00. */
@Composable
private fun NightMap(nights: List<SleepStats.Night>) {
    val cs = MaterialTheme.colorScheme
    val zone = ZoneId.systemDefault()
    val sleepC = cs.primary; val wakeC = cs.primary.copy(alpha = 0.3f); val grid = cs.outlineVariant; val bg = cs.surfaceContainer
    Column {
        Row {
            Spacer(Modifier.width(44.dp))
            listOf("20", "24", "4", "8").forEach { l ->
                Text(l, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
        }
        nights.forEach { n ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(18.dp)) {
                Text(n.date.ruShort(), Modifier.width(44.dp), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                Canvas(Modifier.weight(1f).height(12.dp)) {
                    val t0 = n.date.minusDays(1).atTime(20, 0).atZone(zone).toInstant().toEpochMilli()
                    val span = 16 * 3600_000f
                    fun x(ms: Long) = ((ms - t0) / span).coerceIn(0f, 1f) * size.width
                    for (h in 0..4) drawLine(grid, Offset(size.width * h / 4f, 0f), Offset(size.width * h / 4f, size.height), 1f)
                    drawRoundRect(sleepC, Offset(x(n.d.start), 0f), Size((x(n.d.end) - x(n.d.start)).coerceAtLeast(2f), size.height), CornerRadius(4f, 4f))
                    n.d.stages.filter { it.type == 0 }.forEach { st ->
                        val s = n.d.start + st.offset * 60_000L
                        val w = (x(s + st.dur * 60_000L) - x(s)).coerceAtLeast(1f)
                        drawRect(bg, Offset(x(s), 0f), Size(w, size.height))
                        drawRect(wakeC, Offset(x(s), 0f), Size(w, size.height))
                    }
                }
            }
        }
    }
}
