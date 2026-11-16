package org.maittaa.tabita

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Краткая сводка плитки: заголовок, главное значение, подпись, иконка. */
data class TileSummary(val title: String, val value: String, val sub: String, val icon: ImageVector,
                       val spark: List<Double?> = emptyList(), val bars: Boolean = false, val accent: Boolean = false)

/** Состояние чекапа на дату: пройден в этот день, пора или через сколько дней. */
data class WbStatus(val takenToday: Checkup?, val daysLeft: Int, val questions: Int, val last: String?) {
    val due get() = takenToday == null && daysLeft <= 0
    val accent get() = takenToday != null || due
}

fun wbStatus(repo: Repo, prefs: Prefs, date: String): WbStatus {
    val all = repo.checkups.value.filter { it.date <= date }
    val today = all.filter { it.date == date }.maxByOrNull { it.at }
    val n = prefs.wbBlocks.mapNotNull { Wellbeing.block(it) }.sumOf { it.questions.size }
    return WbStatus(today, Wellbeing.daysLeft(all, prefs.wbPeriod, java.time.LocalDate.parse(date)), n, all.maxOfOrNull { it.date })
}

/** Короткая сводка прохождения: самый заметный блок. */
fun wbBrief(c: Checkup): String {
    val worst = c.results.mapNotNull { (k, r) -> Wellbeing.block(k)?.let { it to r } }
        .maxByOrNull { (b, r) -> b.band(r.score).level * 1000 + if (b.higherIsWorse) b.frac(r.score) * 100 else 0f }
        ?: return ""
    val (b, r) = worst
    return b.title.s.lowercase() + " " + b.format(r.score) + " · " + b.band(r.score).name.s
}

/** Видимые отметки группы [groupId] в порядке показа. */
fun groupChecks(repo: Repo, template: List<TemplateItem>, groupId: String): List<TemplateItem> =
    template.filter { !it.hidden && it.type == ItemType.CHECK && repo.groupOf(it).id == groupId }

/** Числовое значение пункта за день — для мини-графика за 7 дней. */
private fun dayNumber(repo: Repo, template: List<TemplateItem>, id: String, day: Map<String, String>): Double? {
    if (repo.groups.value.any { it.id == id }) return groupChecks(repo, template, id).count { day[it.id] != null }.toDouble()
    val item = template.firstOrNull { it.id == id } ?: return null
    val v = day[item.id]
    return when (item.type) {
        ItemType.SCALE, ItemType.NUMBER, ItemType.DURATION -> parseNumber(v)
        ItemType.MULTI -> parseCounts(v).values.sum().toDouble().takeIf { v != null }
        ItemType.MEALS -> mealKcal(parseMeals(v))
        ItemType.WATER -> parseNumber(v)
        ItemType.EXPENSES -> parsePurchases(v).filter { !it.income }.sumOf { it.total }.takeIf { v != null }
        ItemType.BODY -> BodyDay.parse(v)?.metrics?.let { m -> (m.firstOrNull { BodyCatalog.key(it.id) == "weight" } ?: m.firstOrNull())?.value }
        else -> null
    }
}

fun tileIcon(item: TemplateItem): ImageVector = when {
    item.source == Source.HC_SLEEP -> Icons.Filled.Bedtime
    item.source == Source.HC_STEPS -> Icons.AutoMirrored.Filled.DirectionsWalk
    item.source == Source.HC_EXERCISE -> Icons.Filled.FitnessCenter
    item.source == Source.HC_RESTING_HR -> Icons.Filled.Favorite
    item.source == Source.HC_WEIGHT || item.type == ItemType.BODY -> Icons.Filled.MonitorWeight
    item.type == ItemType.SCALE -> Icons.Filled.Star
    item.type == ItemType.MULTI -> Icons.Filled.LocalCafe
    item.type == ItemType.WATER -> Icons.Filled.WaterDrop
    item.type == ItemType.TEXT -> Icons.AutoMirrored.Filled.Notes
    item.type == ItemType.MEALS -> Icons.Filled.Restaurant
    item.type == ItemType.EXPENSES -> Icons.Filled.AccountBalanceWallet
    item.type == ItemType.DURATION -> Icons.Filled.Schedule
    item.type == ItemType.CHECK -> Icons.Filled.CheckCircle
    item.type == ItemType.WELLBEING -> Icons.Filled.Psychology
    else -> Icons.Filled.Tag
}

fun tileSummary(repo: Repo, template: List<TemplateItem>, id: String, day: Map<String, String>, date: String, prefs: Prefs? = null): TileSummary {
    template.firstOrNull { it.id == id && it.type == ItemType.WELLBEING }?.let { item ->
        if (prefs == null) return TileSummary(item.displayName, "—", "", tileIcon(item))
        val st = wbStatus(repo, prefs, date)
        return when {
            st.takenToday != null -> TileSummary(item.displayName, t("Пройден ✓"), wbBrief(st.takenToday), tileIcon(item), accent = true)
            st.due -> TileSummary(item.displayName, t("Пора пройти"), t("%d вопросов · ~%d мин", st.questions, maxOf(1, (st.questions + 5) / 6)), tileIcon(item), accent = true)
            else -> TileSummary(item.displayName, t("через %d дн.", st.daysLeft),
                st.last?.let { t("последний: %s", java.time.LocalDate.parse(it).ruShort()) } ?: "", tileIcon(item))
        }
    }
    repo.groups.value.firstOrNull { it.id == id }?.let { g ->
        val checks = groupChecks(repo, template, id)
        val on = checks.filter { day[it.id] != null }
        return TileSummary(
            g.displayName, "${on.size} / ${checks.size}",
            on.joinToString(", ") { if (day[it.id] == "2") it.displayName + "+" else it.displayName }.ifEmpty { t("ничего не отмечено") },
            Icons.Filled.CheckCircle, week(repo, template, id, date), bars = true,
        )
    }
    val item = template.firstOrNull { it.id == id } ?: return TileSummary(id, "—", "", Icons.Filled.Tag)
    val v = day[item.id]
    val (value, sub) = when (item.type) {
        ItemType.MULTI -> {
            val c = parseCounts(v)
            (if (c.isEmpty()) "—" else c.values.sum().toString()) to
                c.entries.joinToString(" · ") { (k, n) -> if (item.counter && n > 1) "${t(k)} $n" else t(k) }
        }
        ItemType.TEXT -> (v?.lineSequence()?.firstOrNull()?.take(60) ?: "—") to ""
        ItemType.MEALS -> {
            val m = parseMeals(v)
            val g = Glycemic.day(m)
            (mealKcal(m)?.let { t("%s ккал", formatNumber(Math.round(it).toDouble())) } ?: "—") to
                if (m.isEmpty()) t("нет записей")
                else (g?.let { t("ГН %s · оценка %d", formatNumber(Math.round(it.load).toDouble()), it.score) + "\n" } ?: "") + mealUnits(m).joinToString(", ") { t(it[0].kind) }
        }
        ItemType.EXPENSES -> {
            val l = parsePurchases(v)
            val spent = l.filter { !it.income }.sumOf { it.total }
            (if (l.isEmpty()) "—" else "−" + formatMoney(spent)) to
                (repo.balanceOn(date)?.let { t("баланс %s", formatMoney(it)) } ?: t("%d покупок", l.size))
        }
        ItemType.BODY -> {
            val d = BodyDay.parse(v)
            val wgt = d?.metrics?.firstOrNull { BodyCatalog.key(it.id) == "weight" } ?: d?.metrics?.firstOrNull()
            (wgt?.let { formatNumber(Math.round(it.value * 10) / 10.0) + " " + bodyUnit(it.unit) } ?: "—") to
                (d?.metrics?.filter { it != wgt }?.take(2)?.joinToString(" · ") {
                    BodyCatalog.title(it) + " " + formatNumber(Math.round(it.value * 10) / 10.0) + bodyUnit(it.unit).let { u -> if (u.isEmpty()) "" else " $u" }
                } ?: t("нет взвешивания"))
        }
        ItemType.WATER -> {
            val l = parseNumber(v) ?: 0.0; val norm = item.max.takeIf { it > 0 } ?: 2.0
            (formatNumber(Math.round(l * 100) / 100.0) + " / " + formatLiters(norm)) to t("%d%% нормы", Math.round(l / norm * 100).toInt())
        }
        ItemType.CHECK -> (if (v == "2") item.displayName + "+" else if (v != null) "✓" else "—") to ""
        else -> if (item.source == Source.HC_SLEEP && v != null) {
            val sc = parseNumber(v)?.let { Sleep.score(it, Sleep.detail(day, item), Sleep.goal(prefs, item)) }
            (formatValue(item, v) ?: "—") to (sc?.let { t("оценка %d · %s", it.total, Sleep.bandName(it.band)) } ?: "")
        } else if (item.source == Source.HC_EXERCISE && Workouts.sessions(day, item.id).isNotEmpty()) {
            (formatValue(item, v) ?: "0") to Workouts.summary(Workouts.sessions(day, item.id))
        } else (formatValue(item, v) ?: "—") to
            if (item.source != Source.NONE) sourceCaption(item, day, v != null) else ""
    }
    return TileSummary(item.displayName, value, sub, tileIcon(item), week(repo, template, id, date),
        bars = item.type == ItemType.EXPENSES || item.type == ItemType.WATER || item.type == ItemType.MEALS || item.type == ItemType.MULTI || item.source == Source.HC_STEPS)
}

private fun week(repo: Repo, template: List<TemplateItem>, id: String, date: String): List<Double?> {
    val d = java.time.LocalDate.parse(date)
    return (6 downTo 0).map { i -> dayNumber(repo, template, id, repo.entries.value[d.minusDays(i.toLong()).key()] ?: emptyMap()) }
}

/**
 * Вид уменьшенной плитки — у каждого из трёх положений своё содержание:
 * «Широкая» (4×2) — иконка, название, значение, подпись и график за 7 дней;
 * «Половина» (2×2) — название, значение, подпись, иконка фоном;
 * «Мини» (1×2) — иконка, значение и короткое название.
 */
@Composable
fun CompactTileView(s: TileSummary, w: Int, h: Int) {
    val size = Repo.Tile("", w, h, true).size
    val acc = s.accent
    val fg = if (acc) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val valueColor = if (acc) MaterialTheme.colorScheme.onTertiaryContainer else androidx.compose.ui.graphics.Color.Unspecified
    val bg = if (acc) Modifier.background(MaterialTheme.colorScheme.tertiaryContainer, androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) else Modifier
    when (size) {
        TileSize.MINI -> Column(
            Modifier.fillMaxSize().then(bg).padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(s.icon, null, Modifier.size(22.dp), tint = if (acc) fg else MaterialTheme.colorScheme.primary)
            Text(s.value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2,
                overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = valueColor)
            Text(s.title, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = fg)
        }
        TileSize.WIDE -> Row(
            Modifier.fillMaxSize().then(bg).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).background(
                if (acc) fg.copy(alpha = 0.15f) else MaterialTheme.colorScheme.secondaryContainer, androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center) {
                Icon(s.icon, null, Modifier.size(22.dp), tint = if (acc) fg else MaterialTheme.colorScheme.onSecondaryContainer)
            }
            androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.title, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = fg)
                Text(s.value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, color = valueColor)
                if (s.sub.isNotEmpty()) Text(s.sub.replace("\n", " · "), style = MaterialTheme.typography.labelSmall, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, color = fg)
            }
            if (s.spark.count { it != null } >= 2) {
                androidx.compose.foundation.layout.Spacer(Modifier.size(10.dp))
                Column(Modifier.fillMaxWidth(0.4f), horizontalAlignment = Alignment.End) {
                    Sparkline(s.spark, Modifier.fillMaxWidth().height(34.dp), bars = s.bars)
                    Text(t("7 дней"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
        else -> Box(Modifier.fillMaxSize().then(bg).padding(horizontal = 12.dp, vertical = 10.dp)) {
            Icon(s.icon, null, Modifier.align(Alignment.BottomEnd).size(36.dp), tint = fg.copy(alpha = if (acc) 0.35f else 0.2f))
            Column(Modifier.fillMaxSize()) {
                Text(s.title, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = fg)
                Text(s.value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, color = valueColor)
                if (s.sub.isNotEmpty()) Text(s.sub, style = MaterialTheme.typography.labelSmall, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, color = fg, modifier = Modifier.fillMaxWidth(0.8f))
            }
        }
    }
}

/** Панель режима правки плиток. */
@Composable
fun EditBanner(
    selected: Pair<String, TemplateItem?>?,
    onEditItem: (TemplateItem) -> Unit, onHide: (String) -> Unit, onReset: (String) -> Unit, onDone: () -> Unit,
    size: TileSize? = null, onSize: (TileSize) -> Unit = {},
) {
    Card(
        Modifier.fillMaxWidth().padding(bottom = 10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("Расположение плиток"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onDone) { Text(t("Готово")) }
            }
            Text(
                t("Нажмите на плитку, чтобы выбрать; выбранную — тащите. Размер — кнопками ниже или точками на рамке."),
                style = MaterialTheme.typography.bodySmall,
            )
            if (selected != null) {
                // четыре положения плитки
                androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalMinimumInteractiveComponentSize provides 0.dp) {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TileSize.entries.forEach { ts ->
                            val on = size == ts
                            Box(
                                Modifier.weight(1f).height(32.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                                    .clickable { onSize(ts) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(ts.title, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false,
                                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    selected.second?.let { item -> TextButton(onClick = { onEditItem(item) }) { Text(t("Изменить пункт")) } }
                    TextButton(onClick = { onHide(selected.first) }) { Text(t("Скрыть")) }
                }
            }
        }
    }
}
