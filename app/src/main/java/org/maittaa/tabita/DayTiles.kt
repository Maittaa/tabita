package org.maittaa.tabita

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.IconButton
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
import androidx.compose.material.icons.filled.Flag
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
import androidx.compose.material.icons.filled.Lightbulb
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
import androidx.compose.ui.unit.sp

/** Краткая сводка плитки: заголовок, главное значение, подпись, иконка. */
data class TileSummary(val title: String, val value: String, val sub: String, val icon: ImageVector,
                       val spark: List<Double?> = emptyList(), val bars: Boolean = false, val accent: Boolean = false,
                       /** 5.47: доля к норме/цели (0…1) — полоса в плитках побольше; null — нет нормы. */
                       val progress: Float? = null,
                       /** 5.48: быстрые действия в уменьшенной плитке. */
                       val actions: List<QuickAction> = emptyList())

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
    item.type == ItemType.TIPS -> Icons.Filled.Lightbulb
    item.type == ItemType.TRAINING -> Icons.Filled.FitnessCenter
    item.type == ItemType.GOAL -> Icons.Filled.Flag
    item.type == ItemType.RHYTHM -> Icons.Filled.Schedule
    else -> Icons.Filled.Tag
}

fun tileSummary(repo: Repo, template: List<TemplateItem>, id: String, day: Map<String, String>, date: String, prefs: Prefs? = null, rot: Int = 0,
                train: org.maittaa.tabita.train.AppModel? = null): TileSummary {
    template.firstOrNull { it.id == id && it.type == ItemType.TRAINING }?.let { item ->
        val s = train?.let { org.maittaa.tabita.train.TrainTileInfo.of(it, java.time.LocalDate.parse(date)) }
            ?: return TileSummary(item.displayName, "—", "", tileIcon(item))
        return TileSummary(item.displayName, s.value, s.sub, tileIcon(item), accent = s.accent)
    }
    template.firstOrNull { it.id == id && it.type == ItemType.GOAL }?.let { item ->
        val g = prefs?.let { Goal.of(it.planGoal) } ?: return TileSummary(item.displayName, t("не выбрана"), t("Выберите цель в «Программе»"), tileIcon(item))
        val d = java.time.LocalDate.parse(date)
        val tl = Program.today(g, repo, prefs, d)
        return TileSummary(item.displayName, tl?.value ?: g.title, listOfNotNull(g.title, tl?.sub).joinToString("\n"), tileIcon(item), accent = tl?.alert == true)
    }
    template.firstOrNull { it.id == id && it.type == ItemType.RHYTHM }?.let { item ->
        // 5.43: текстовая сводка (для доступности и запасной вид); на экране дня рисуется RhythmCompact
        val r = DayRhythm.of(repo, java.time.LocalDate.parse(date), prefs)
        val info = RhythmNow.of(r, java.time.LocalTime.now())
        return TileSummary(item.displayName, info.title, t("отбой %s", clockOf(r.bed)), tileIcon(item))
    }
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
    template.firstOrNull { it.id == id && it.type == ItemType.TIPS }?.let { item ->
        val d = java.time.LocalDate.parse(date)
        if (prefs == null || d != java.time.LocalDate.now()) return TileSummary(item.displayName, "—", t("Советы — для сегодняшнего дня."), tileIcon(item))
        // в плитке — только важное: если есть срочные советы, крутятся они; иначе — общие, подходящие ко времени
        val urgent = Tips.build(repo, prefs, d)
        val imp = urgent.filter { it.prio >= Tips.IMPORTANT }
        val q = imp.ifEmpty { Tips.rotation(prefs, d, urgent, java.time.LocalTime.now(), DayRhythm.of(repo, d, prefs)) }.ifEmpty { listOf(Tips.allGood()) }
        val top = q[rot % q.size]
        return TileSummary(item.displayName, top.title, top.text, top.icon, accent = top.prio >= 70)
    }
    repo.groups.value.firstOrNull { it.id == id }?.let { g ->
        val checks = groupChecks(repo, template, id)
        val on = checks.filter { day[it.id] != null }
        return TileSummary(
            g.displayName, "${on.size} / ${checks.size}",
            on.joinToString(", ") { if (day[it.id] == "2") it.displayName + "+" else it.displayName }.ifEmpty { t("ничего не отмечено") },
            Icons.Filled.CheckCircle, week(repo, template, id, date), bars = true,
            progress = if (checks.isEmpty()) null else on.size.toFloat() / checks.size,
        )
    }
    val item = template.firstOrNull { it.id == id } ?: return TileSummary(id, "—", "", Icons.Filled.Tag)
    val v = day[item.id]
    val (value, sub) = when (item.type) {
        ItemType.MULTI -> {
            val c = parseCounts(v)
            (if (c.isEmpty()) "—" else c.values.sum().toString()) to
                (c.entries.joinToString(" · ") { (k, n) -> if (item.counter && n > 1) "${t(k)} $n" else t(k) } +
                    // 5.61: «каждый день», ещё не отмеченные
                    (item.dailyMissing(v).takeIf { it.isNotEmpty() }?.let { m -> (if (c.isEmpty()) "" else "\n") + t("ещё: %s", m.joinToString(", ") { t(it) }) } ?: ""))
        }
        ItemType.TEXT -> (v?.lineSequence()?.firstOrNull()?.take(60) ?: "—") to ""
        ItemType.MEALS -> {
            val m = parseMeals(v)
            val g = Glycemic.day(m)
            (mealKcal(m)?.let { t("%s ккал", formatNumber(Math.round(it).toDouble())) } ?: "—") to
                if (m.isEmpty()) t("нет записей")
                else (mealMacros(m)?.let { it.short() + "\n" } ?: "") +
                    (g?.let { t("ГН %s · оценка %d", formatNumber(Math.round(it.load).toDouble()), it.score) + "\n" } ?: "") +
                    mealUnits(m).joinToString(", ") { t(it[0].kind) }
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
            val l = parseNumber(v) ?: 0.0; val norm = Hydration.normOf(item, day)
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
    // 5.47: доля к норме и детали для плиток побольше
    var sub2 = sub
    val progress: Float? = when {
        item.type == ItemType.WATER -> (parseNumber(v) ?: 0.0).div(Hydration.normOf(item, day)).toFloat()
        item.source == Source.HC_SLEEP && v != null -> {
            val det = Sleep.detail(day, item)
            if (det != null) {
                val z = java.time.ZoneId.systemDefault()
                fun hm(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(z).let { clockOf(it.hour * 60 + it.minute) }
                sub2 = listOfNotNull(sub.ifEmpty { null }, hm(det.start) + " → " + hm(det.end),
                    if (det.hasStages) t("глубокий %s · REM %s", formatMinutes(det.deep.toDouble()), formatMinutes(det.rem.toDouble())) else null).joinToString("\n")
            }
            parseNumber(v)?.let { (it / Sleep.goal(prefs, item)).toFloat() }
        }
        item.source == Source.HC_STEPS && v != null -> {
            val st = parseNumber(v) ?: 0.0
            val km = Anthro.stepsKm(repo.profile.value ?: Profile(), st)
            sub2 = listOfNotNull(sub.ifEmpty { null }, km?.let { t("%s км", formatNumber(Math.round(it * 10) / 10.0)) },
                t("цель %s", formatNumber(Hydration.STEPS_USUAL))).joinToString("\n")
            (st / Hydration.STEPS_USUAL).toFloat()
        }
        else -> null
    }
    return TileSummary(item.displayName, value, sub2, tileIcon(item), week(repo, template, id, date),
        bars = item.type == ItemType.EXPENSES || item.type == ItemType.WATER || item.type == ItemType.MEALS || item.type == ItemType.MULTI || item.source == Source.HC_STEPS,
        progress = progress)
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
    // 5.47: вид — от ширины (1…6 клеток) и высоты (1…3 строки): чем больше плитка, тем больше деталей
    val acc = s.accent
    val cs = MaterialTheme.colorScheme
    val fg = if (acc) cs.onTertiaryContainer else cs.onSurfaceVariant
    val valueColor = if (acc) cs.onTertiaryContainer else androidx.compose.ui.graphics.Color.Unspecified
    val bg = if (acc) Modifier.background(cs.tertiaryContainer, androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) else Modifier
    val ty = MaterialTheme.typography
    val lines = s.sub.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    val hasSpark = s.spark.count { it != null } >= 2
    @Composable fun title(max: Int = 1) = Text(s.title, style = ty.labelMedium, maxLines = max, overflow = TextOverflow.Ellipsis, color = fg)
    @Composable fun value(style: androidx.compose.ui.text.TextStyle, max: Int = 1, center: Boolean = false) =
        Text(s.value, style = style, fontWeight = FontWeight.SemiBold, maxLines = max, overflow = TextOverflow.Ellipsis, color = valueColor,
            textAlign = if (center) androidx.compose.ui.text.style.TextAlign.Center else null)
    @Composable fun sub(max: Int, joined: Boolean = false, modifier: Modifier = Modifier, roomy: Boolean = false) {
        if (lines.isEmpty()) return
        // 5.49: если строк мало, а места много — подпись крупнее
        Text(if (joined) lines.joinToString(" · ") else lines.joinToString("\n"), style = if (roomy) ty.bodyMedium else ty.labelSmall, maxLines = max,
            overflow = TextOverflow.Ellipsis, color = fg, modifier = modifier)
    }
    /**
     * 5.49: значение заполняет свободное место — шрифт подбирается от обычного до [max], чтобы в плитке
     * с небольшим количеством данных не оставалось пустоты; прочерк «—» крупнее 32 sp не делаем.
     */
    @Composable fun bigValue(modifier: Modifier, max: androidx.compose.ui.unit.TextUnit, lines: Int = 1, center: Boolean = false) =
        androidx.compose.foundation.layout.BoxWithConstraints(modifier, contentAlignment = if (center) Alignment.Center else Alignment.CenterStart) {
            val base = ty.titleLarge
            val dens = androidx.compose.ui.platform.LocalDensity.current
            // 5.50: потолок шрифта — по высоте места (строка ≈ 1,3 размера шрифта) и по длине текста:
            // короткие числа могут быть крупными, длинные фразы — нет
            val byHeight = if (constraints.hasBoundedHeight) constraints.maxHeight / lines / 1.3f / (dens.density * dens.fontScale) else max.value
            val len = s.value.length
            val byLength = when { s.value == "—" -> 32f; len > 24 -> 24f; len > 14 -> 30f; len > 9 -> 40f; else -> max.value }
            val topV = minOf(max.value, byHeight, byLength)
            val minV = minOf(base.fontSize.value, topV).coerceAtLeast(10f)
            androidx.compose.foundation.text.BasicText(
                s.value,
                style = base.copy(fontWeight = FontWeight.SemiBold, color = if (acc) fg else cs.onSurface,
                    textAlign = if (center) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start),
                maxLines = lines, overflow = TextOverflow.Ellipsis,
                autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = minV.sp, maxFontSize = maxOf(minV, topV).sp, stepSize = 1.sp),
            )
        }
    @Composable fun progress(modifier: Modifier = Modifier) {
        val p = s.progress ?: return
        LinearProgressIndicator(progress = { p.coerceIn(0f, 1f) }, modifier = modifier.fillMaxWidth().height(4.dp),
            color = if (acc) fg else cs.primary, trackColor = (if (acc) fg else cs.primary).copy(alpha = 0.15f), drawStopIndicator = {})
    }
    @Composable fun iconCircle(d: androidx.compose.ui.unit.Dp) = Box(Modifier.size(d).background(
        if (acc) fg.copy(alpha = 0.15f) else cs.secondaryContainer, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
        Icon(s.icon, null, Modifier.size(d * 0.55f), tint = if (acc) fg else cs.onSecondaryContainer)
    }
    @Composable fun spark(modifier: Modifier, label: Boolean = true) {
        if (!hasSpark) return
        Column(modifier, horizontalAlignment = Alignment.End) {
            Sparkline(s.spark, Modifier.fillMaxWidth().weight(1f, fill = true), bars = s.bars)
            if (label) Text(t("7 дней"), style = ty.labelSmall, color = cs.outline, maxLines = 1)
        }
    }
    val acts = s.actions
    val hasActs = acts.isNotEmpty()
    // в высоких плитках кнопки крупнее — заполняют место и по ним легче попасть
    @Composable fun actions(lines: Int, modifier: Modifier = Modifier) { if (hasActs) ActionRow(acts, lines, modifier, big = h >= 3) }
    val pad = Modifier.fillMaxSize().then(bg)
    when {
        // ---------- одна строка ----------
        h <= 1 && w <= 1 -> Column(pad.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(s.icon, null, Modifier.size(16.dp), tint = if (acc) fg else cs.primary)
            value(ty.labelMedium, center = true)
        }
        h <= 1 && w == 2 -> Row(pad.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(s.icon, null, Modifier.size(18.dp), tint = if (acc) fg else cs.primary)
            Spacer(Modifier.size(6.dp))
            Column(Modifier.weight(1f)) { value(ty.titleSmall); Text(s.title, style = ty.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = fg) }
            if (hasActs) ActionChip(acts.first())
        }
        h <= 1 -> Row(pad.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            iconCircle(30.dp); Spacer(Modifier.size(8.dp))
            Column(Modifier.weight(1f)) {
                title(); value(ty.titleMedium)
            }
            if (hasActs) {
                Spacer(Modifier.size(8.dp))
                if (w == 3) ActionChip(acts.first()) else actions(1, Modifier.weight(if (w >= 5) 2f else 1.3f))
            } else {
                if (w >= 4) {
                    Spacer(Modifier.size(8.dp))
                    Column(Modifier.weight(if (w >= 5) 1.3f else 1f)) { sub(2); if (lines.isEmpty()) progress() }
                }
                if (w >= 5 && hasSpark) { Spacer(Modifier.size(8.dp)); spark(Modifier.width(64.dp).fillMaxHeight().padding(vertical = 8.dp), label = false) }
            }
        }
        // ---------- две строки ----------
        h == 2 && w <= 1 -> Column(pad.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
            Icon(s.icon, null, Modifier.size(22.dp), tint = if (acc) fg else cs.primary)
            value(ty.titleSmall, max = 2, center = true)
            Text(s.title, style = ty.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = fg)
        }
        h == 2 && w == 2 -> Box(pad.padding(horizontal = 12.dp, vertical = 10.dp)) {
            if (!hasActs) Icon(s.icon, null, Modifier.align(Alignment.BottomEnd).size(36.dp), tint = fg.copy(alpha = if (acc) 0.35f else 0.2f))
            Column(Modifier.fillMaxSize()) {
                title()
                if (hasActs) { value(ty.titleLarge); Spacer(Modifier.weight(1f)); actions(1) }
                else bigValue(Modifier.weight(1f).fillMaxWidth(0.85f), 40.sp)
                if (hasActs) Unit
                else { sub(2, modifier = Modifier.fillMaxWidth(0.8f), roomy = lines.size <= 1); progress(Modifier.fillMaxWidth(0.7f).padding(top = 4.dp)) }
            }
        }
        h == 2 && w == 3 -> Column(pad.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(s.icon, null, Modifier.size(18.dp), tint = if (acc) fg else cs.primary); Spacer(Modifier.size(6.dp)); title()
            }
            when {
                hasActs -> { value(ty.titleLarge); Spacer(Modifier.weight(1f)); actions(1) }
                hasSpark -> { value(ty.titleLarge); sub(1, joined = true); spark(Modifier.fillMaxWidth().weight(1f), label = false) }
                else -> { bigValue(Modifier.weight(1f).fillMaxWidth(), 44.sp); sub(2, roomy = lines.size <= 1); progress(Modifier.padding(top = 4.dp)) }
            }
        }
        h == 2 -> Row(pad.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            iconCircle(40.dp); Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                title()
                if (hasActs) { value(ty.titleLarge); Spacer(Modifier.weight(1f)); actions(1) }
                else bigValue(Modifier.weight(1f).fillMaxWidth(), if (hasSpark) 32.sp else 44.sp)
                if (hasActs) Unit
                else {
                    sub(if (w >= 5) 2 else 1, joined = w < 5, roomy = !hasSpark && lines.size <= 1)
                    if (s.progress != null) { Spacer(Modifier.size(4.dp)); progress() }
                }
            }
            if (hasSpark && !hasActs) { Spacer(Modifier.size(10.dp)); spark(Modifier.fillMaxWidth(if (w >= 5) 0.42f else 0.4f).fillMaxHeight()) }
        }
        // ---------- три строки ----------
        w <= 1 -> Column(pad.padding(horizontal = 6.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(s.icon, null, Modifier.size(24.dp), tint = if (acc) fg else cs.primary)
            bigValue(Modifier.weight(1f).fillMaxWidth(), 28.sp, lines = 3, center = true)
            if (hasActs) ActionChip(acts.first())
            else if (s.progress != null) progress() else if (hasSpark) spark(Modifier.fillMaxWidth().height(22.dp), label = false)
            Spacer(Modifier.size(4.dp))
            Text(s.title, style = ty.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = fg,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        w == 2 -> Column(pad.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(s.icon, null, Modifier.size(18.dp), tint = if (acc) fg else cs.primary); Spacer(Modifier.size(6.dp)); title()
            }
            when {
                hasActs -> { value(ty.headlineSmall); progress(Modifier.padding(vertical = 4.dp)); Spacer(Modifier.weight(1f)); actions(2) }
                hasSpark -> {
                    value(ty.headlineSmall); progress(Modifier.padding(vertical = 4.dp)); sub(2)
                    spark(Modifier.fillMaxWidth().weight(1f).padding(top = 4.dp), label = false)
                }
                else -> { bigValue(Modifier.weight(1f).fillMaxWidth(), 52.sp, lines = 2); progress(Modifier.padding(vertical = 4.dp)); sub(4, roomy = lines.size <= 2) }
            }
        }
        w == 3 -> Column(pad.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                iconCircle(32.dp); Spacer(Modifier.size(8.dp)); title()
            }
            when {
                hasActs -> { value(ty.headlineSmall); progress(Modifier.padding(vertical = 4.dp)); sub(1, joined = true); Spacer(Modifier.weight(1f)); actions(2) }
                hasSpark -> {
                    value(ty.headlineSmall); progress(Modifier.padding(vertical = 4.dp)); sub(2)
                    spark(Modifier.fillMaxWidth().weight(1f).padding(top = 4.dp))
                }
                else -> { bigValue(Modifier.weight(1f).fillMaxWidth(), 56.sp, lines = 2); progress(Modifier.padding(vertical = 4.dp)); sub(4, roomy = lines.size <= 2) }
            }
        }
        else -> Row(pad.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (hasActs) {
                    // с кнопками: значение рядом с заголовком, кнопки — внизу
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        iconCircle(40.dp); Spacer(Modifier.size(10.dp))
                        Column(Modifier.weight(1f)) { title(); value(ty.headlineSmall) }
                    }
                    progress(Modifier.padding(vertical = 6.dp))
                    sub(1, joined = true); Spacer(Modifier.weight(1f)); actions(2)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) { iconCircle(40.dp); Spacer(Modifier.size(10.dp)); title() }
                    bigValue(Modifier.weight(1f).fillMaxWidth(), if (hasSpark) 44.sp else 60.sp, lines = 2)
                    progress(Modifier.padding(vertical = 6.dp))
                    sub(if (w >= 5) 4 else 3, roomy = lines.size <= 2)
                }
            }
            if (hasSpark && !hasActs) { Spacer(Modifier.size(12.dp)); spark(Modifier.fillMaxWidth(if (w >= 5) 0.45f else 0.4f).fillMaxHeight()) }
        }
    }
}

/** 5.48: кнопка быстрого действия в уменьшенной плитке; нажатие ловит слой жестов плитки (TileHits). */
@Composable
fun ActionChip(a: QuickAction, big: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.tileAction(a.key, a.run).height(if (big) 40.dp else 30.dp).widthIn(min = if (big) 40.dp else 30.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(if (big) 10.dp else 8.dp))
            .background(if (a.on) cs.primary else cs.secondaryContainer).padding(horizontal = if (big) 12.dp else 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(a.label, style = if (big) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false,
            color = if (a.on) cs.onPrimary else cs.onSecondaryContainer)
    }
}

/** Ряд кнопок; что не влезло в [lines] строк — не показывается (и не нажимается). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ActionRow(actions: List<QuickAction>, lines: Int, modifier: Modifier = Modifier, big: Boolean = false) {
    androidx.compose.foundation.layout.FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(if (big) 8.dp else 6.dp),
        verticalArrangement = Arrangement.spacedBy(if (big) 8.dp else 6.dp), maxLines = lines) {
        actions.forEach { ActionChip(it, big) }
    }
}

/** Панель режима правки плиток. */
@Composable
fun EditBanner(
    selected: Pair<String, TemplateItem?>?,
    onEditItem: (TemplateItem) -> Unit, onHide: (String) -> Unit, onReset: (String) -> Unit, onDone: () -> Unit,
    tile: Repo.Tile? = null, cols: Int = GRID_COLS, onTile: (Repo.Tile) -> Unit = {},
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
                t("Нажмите на плитку, чтобы выбрать; выбранную — тащите. Размер — по одной клетке кнопками ниже или точками на рамке."),
                style = MaterialTheme.typography.bodySmall,
            )
            if (selected != null) {
                // 5.47: размер по одной клетке — ширина 1…колонок, высота 1…3; «развернуть» — всё содержимое пункта
                tile?.let { tl ->
                    val full = !tl.small
                    val w = if (full) cols else tl.w.coerceIn(1, cols); val h = if (full) 0 else tl.h.coerceIn(1, Repo.Tile.MAX_H)
                    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalMinimumInteractiveComponentSize provides 0.dp) {
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            @Composable fun stepper(label: String, v: Int, min: Int, max: Int, set: (Int) -> Unit) {
                                Row(Modifier.weight(1f).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)), verticalAlignment = Alignment.CenterVertically) {
                                    IconButton({ set(v - 1) }, Modifier.size(32.dp), enabled = !full && v > min) { Text("−", style = MaterialTheme.typography.titleMedium) }
                                    Text(if (full) label else "$label $v", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1, softWrap = false)
                                    IconButton({ set(v + 1) }, Modifier.size(32.dp), enabled = !full && v < max) { Text("+", style = MaterialTheme.typography.titleMedium) }
                                }
                            }
                            stepper(t("Ширина"), w, 1, cols) { onTile(Repo.Tile(tl.id, it, h, true)) }
                            stepper(t("Высота"), h, 1, Repo.Tile.MAX_H) { onTile(Repo.Tile(tl.id, w, it, true)) }
                            Box(
                                Modifier.height(32.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                    .background(if (full) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                                    .clickable { onTile(if (full) Repo.Tile(tl.id, minOf(4, cols), 2, true) else Repo.Tile(tl.id)) }
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(t("Полная"), style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false,
                                    color = if (full) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
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
