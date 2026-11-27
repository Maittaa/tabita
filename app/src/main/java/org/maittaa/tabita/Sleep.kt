package org.maittaa.tabita

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

// =====================================================================
// Данные и расчёт
// =====================================================================

/** Фаза сна: 0 — без сна, 1 — REM, 2 — лёгкий, 3 — глубокий, 4 — сон без разбивки на фазы. */
data class SleepStage(val offset: Int, val dur: Int, val type: Int)

/** Подробности сна из Health Connect: ключ `<id>!sd`. Время — эпоха в мс, фазы — минуты от начала. */
data class SleepDetail(val start: Long, val end: Long, val stages: List<SleepStage>) {
    private fun sum(type: Int) = stages.filter { it.type == type }.sumOf { it.dur }
    val awake get() = sum(0)
    val rem get() = sum(1)
    val light get() = sum(2)
    val deep get() = sum(3)
    val unspecified get() = sum(4)
    val asleep get() = rem + light + deep + unspecified
    /** Есть разбивка на фазы (а не просто «спал»). */
    val hasStages get() = rem + light + deep > 0
    val inBed get() = ((end - start) / 60000).toInt()
    /** Эффективность: доля сна во времени в кровати. */
    val efficiency: Double? get() = (if (stages.isEmpty()) null else asleep + awake).takeIf { it != null && it > 0 }?.let { asleep.toDouble() / it }
    /** Пробуждения: участки без сна между участками сна (подряд идущие считаются одним). */
    val wakes: Int get() {
        val s = stages.sortedBy { it.offset }
        var n = 0; var seenSleep = false; var inWake = false
        s.forEach { st ->
            if (st.type == 0) { if (seenSleep && !inWake) { n++; inWake = true } }
            else { seenSleep = true; inWake = false }
        }
        // бодрствование в самом конце — это подъём, а не пробуждение
        if (inWake && s.lastOrNull()?.type == 0) n--
        return n.coerceAtLeast(0)
    }

    fun toJson(): String = JSONObject().put("s", start).put("e", end)
        .put("st", JSONArray(stages.map { JSONArray().put(it.offset).put(it.dur).put(it.type) })).toString()

    companion object {
        fun key(id: String) = "$id!sd"
        fun parse(v: String?): SleepDetail? = runCatching {
            val o = JSONObject(v ?: return null)
            val a = o.optJSONArray("st") ?: JSONArray()
            SleepDetail(o.getLong("s"), o.getLong("e"), (0 until a.length()).map { i ->
                val x = a.getJSONArray(i); SleepStage(x.getInt(0), x.getInt(1), x.getInt(2))
            })
        }.getOrNull()
    }
}

object Sleep {
    data class RawStage(val start: Long, val end: Long, val hcType: Int)
    data class RawSession(val start: Long, val end: Long, val stages: List<RawStage>)

    /** Тип фазы Health Connect → свой (0 без сна, 1 REM, 2 лёгкий, 3 глубокий, 4 сон). */
    fun mapType(hc: Int) = when (hc) { 1, 3, 7 -> 0; 6 -> 1; 4 -> 2; 5 -> 3; else -> 4 }

    /**
     * Сессии одного источника за ночь → (минуты сна, подробности).
     * Минуты сна = время в сессиях минус бодрствование (как раньше); фазы — от начала первой сессии.
     */
    /**
     * Дневной/вечерний сон (5.25): начался после 11:00 и закончился в тот же день. Такой сон — отдельная строка дня
     * («дневной сон»), а не основной сон: не складывается с ночным и не сдвигает средние подъёма и отбоя.
     * Ночной сон по-прежнему записывается в день пробуждения.
     */
    fun isNap(start: Long, end: Long, zone: java.time.ZoneId): Boolean {
        val s = java.time.Instant.ofEpochMilli(start).atZone(zone); val e = java.time.Instant.ofEpochMilli(end).atZone(zone)
        return s.toLocalDate() == e.toLocalDate() && s.hour >= 11
    }
    fun napKey(id: String) = "$id!nap"
    fun napTimeKey(id: String) = "$id!napt"
    fun hhmm(ms: Long, zone: java.time.ZoneId) = java.time.Instant.ofEpochMilli(ms).atZone(zone).let { "%d:%02d".format(it.hour, it.minute) }

    fun combine(sessions: List<RawSession>): Pair<Long, SleepDetail> {
        val s0 = sessions.minOf { it.start }; val e0 = sessions.maxOf { it.end }
        var minutes = 0L
        val stages = mutableListOf<SleepStage>()
        sessions.sortedBy { it.start }.forEach { ss ->
            val total = (ss.end - ss.start) / 60000
            val awake = ss.stages.filter { mapType(it.hcType) == 0 }.sumOf { (it.end - it.start) / 60000 }
            minutes += (total - awake).coerceAtLeast(0)
            ss.stages.sortedBy { it.start }.forEach { st ->
                val dur = ((st.end - st.start) / 60000).toInt()
                if (dur > 0) stages += SleepStage(((st.start - s0) / 60000).toInt(), dur, mapType(st.hcType))
            }
        }
        return minutes to SleepDetail(s0, e0, stages)
    }

    /** Норма сна: цель из «Итогов месяца», по умолчанию 7 ч. */
    fun goal(prefs: Prefs?, item: TemplateItem): Double = prefs?.monthCfg?.goals?.get(item.id) ?: 420.0

    /** Подробности, если день не исправлен вручную (вручную — только часы). */
    fun detail(day: Map<String, String>, item: TemplateItem): SleepDetail? =
        if (day[manualKey(item.id)] != null) null else SleepDetail.parse(day[SleepDetail.key(item.id)])

    data class Score(val total: Int, val duration: Int, val quality: Int?, val restoration: Int?) {
        val band get() = band(total)
        val detailed get() = quality != null
    }

    /** Доля «длительности»: полная — от нормы до нормы + 2 ч, к 3 ч сна падает до нуля, пересып снижает до 0,7. */
    fun durationPart(min: Double, goal: Double): Double = when {
        min >= goal && min <= goal + 120 -> 1.0
        min < goal -> ((min - 180) / (goal - 180).coerceAtLeast(60.0)).coerceIn(0.0, 1.0)
        else -> (1 - (min - goal - 120) / 240 * 0.3).coerceIn(0.7, 1.0)
    }

    /**
     * Оценка сна 0–100 (по мотивам Fitbit / Google Health).
     * С фазами: длительность 50 + качество (глубокий ≥15 %, REM ≥20 %) 25 + восстановление (эффективность, пробуждения) 25.
     * Без фаз: только длительность, приведённая к 100.
     */
    fun score(min: Double, d: SleepDetail?, goal: Double): Score {
        val dp = durationPart(min, goal)
        if (d == null || !d.hasStages || d.asleep <= 0) {
            val v = Math.round(dp * 100).toInt()
            return Score(v, v, null, null)
        }
        val a = d.asleep.toDouble()
        val q = 0.5 * (d.deep / a / 0.15).coerceAtMost(1.0) + 0.5 * (d.rem / a / 0.20).coerceAtMost(1.0)
        val eff = ((d.efficiency ?: 0.85) - 0.75) / 0.17
        val r = 0.7 * eff.coerceIn(0.0, 1.0) + 0.3 * (1 - ((d.wakes - 3) / 9.0).coerceIn(0.0, 1.0))
        val dur = Math.round(dp * 50).toInt(); val qq = Math.round(q * 25).toInt(); val rr = Math.round(r * 25).toInt()
        return Score((dur + qq + rr).coerceIn(0, 100), dur, qq, rr)
    }

    /** 0 — отлично (90+), 1 — хорошо (80–89), 2 — удовлетворительно (60–79), 3 — плохо. */
    fun band(s: Int) = when { s >= 90 -> 0; s >= 80 -> 1; s >= 60 -> 2; else -> 3 }
    fun bandName(b: Int) = when (b) { 0 -> t("отлично"); 1 -> t("хорошо"); 2 -> t("удовлетворительно"); else -> t("плохо") }

    fun stageName(type: Int) = when (type) { 0 -> t("Без сна"); 1 -> t("REM"); 2 -> t("Лёгкий"); 3 -> t("Глубокий"); else -> t("Сон") }
    fun stageColor(type: Int) = when (type) {
        0 -> Color(0xFFF2A65A); 1 -> Color(0xFF7CC4F5); 2 -> Color(0xFF4A86E8); 3 -> Color(0xFF3D3A9E); else -> Color(0xFF6E8FD8)
    }
    /** Типичные доли фаз у взрослых. */
    fun stageNorm(type: Int) = when (type) { 1 -> "20–25%"; 2 -> "45–55%"; 3 -> "13–23%"; else -> "" }

    fun clock(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime().let { "%02d:%02d".format(it.hour, it.minute) }
}

/** Цвет оценки сна. */
fun sleepColor(band: Int): Color = glyColor(band)

// =====================================================================
// Интерфейс: блок сна на экране дня
// =====================================================================

/** Кольцо оценки. */
@Composable
fun ScoreRing(score: Int, color: Color, size: Int = 76) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size.dp)) {
            val w = 7.dp.toPx()
            val inset = w / 2
            drawArc(track, 135f, 270f, false, Offset(inset, inset), Size(this.size.width - w, this.size.height - w), style = Stroke(w, cap = StrokeCap.Round))
            drawArc(color, 135f, 270f * score / 100f, false, Offset(inset, inset), Size(this.size.width - w, this.size.height - w), style = Stroke(w, cap = StrokeCap.Round))
        }
        Text("$score", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SleepBlock(
    item: TemplateItem, day: Map<String, String>, prefs: Prefs,
    onManual: (String?) -> Unit, onRevert: () -> Unit, onSync: () -> Unit,
) {
    val value = day[item.id]
    val manual = day[manualKey(item.id)] != null
    val min = parseNumber(value)
    val detail = Sleep.detail(day, item)
    var editing by remember { mutableStateOf(false) }

    SectionCard(item.displayName, watch = item.source != Source.NONE, action = {
        if (!editing) {
            if (manual) TextButton(onClick = onRevert) { Text(t("Вернуть")) }
            TextButton(onClick = { editing = true }) {
                Icon(Icons.Filled.Edit, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(t("Изменить"))
            }
        }
    }) {
        if (editing) {
            var draft by remember { mutableStateOf(value) }
            Text(t("Сколько спали"), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            ItemEditor(item.copy(type = ItemType.DURATION), draft, onAddOption = {}) { draft = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                OutlinedButton(onClick = { editing = false }) { Text(t("Отмена")) }
                Button(onClick = { Log.i("Sleep", "ручная правка: $value → $draft"); onManual(draft); editing = false }) { Text(t("Сохранить")) }
            }
            return@SectionCard
        }
        parseNumber(day[Sleep.napKey(item.id)])?.takeIf { it > 0 }?.let { nap ->
            Text(t("Дневной сон: %s", formatMinutes(nap)) + (day[Sleep.napTimeKey(item.id)]?.let { " ($it)" } ?: "") +
                " · " + t("не входит в ночной"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp))
        }
        if (min == null) {
            Text(t("Сон за эту ночь не записан"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            SyncHint(onSync)
            return@SectionCard
        }
        val sc = Sleep.score(min, detail, Sleep.goal(prefs, item))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoreRing(sc.total, sleepColor(sc.band))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(t("Оценка сна: %s", Sleep.bandName(sc.band)), style = MaterialTheme.typography.labelLarge, color = sleepColor(sc.band))
                Text(formatMinutes(min), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        manual -> t("исправлено вручную")
                        detail != null -> t("%s → %s", Sleep.clock(detail.start), Sleep.clock(detail.end)) +
                            (AppNames.from(day[srcKey(item.id)])?.let { " · $it" } ?: "")
                        else -> sourceCaption(item, day, true)
                    },
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (detail != null && detail.hasStages) {
            Spacer(Modifier.height(12.dp))
            Hypnogram(detail, Modifier.fillMaxWidth().height(120.dp))
            Spacer(Modifier.height(10.dp))
            listOf(0, 1, 2, 3).forEach { tp ->
                val m = when (tp) { 0 -> detail.awake; 1 -> detail.rem; 2 -> detail.light; else -> detail.deep }
                val pct = if (tp == 0) null else Math.round(m * 100.0 / detail.asleep.coerceAtLeast(1)).toInt()
                StageRow(tp, m, pct)
            }
            Spacer(Modifier.height(6.dp))
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat(t("в кровати"), formatMinutes(detail.inBed.toDouble()))
                detail.efficiency?.let { Stat(t("эффективность"), "${Math.round(it * 100)}%") }
                Stat(t("пробуждения"), "${detail.wakes}")
            }
            Spacer(Modifier.height(8.dp))
            Text(t("Из чего оценка"), style = MaterialTheme.typography.labelLarge)
            Text(t("Качество — доля глубокого сна и REM; восстановление — эффективность сна и пробуждения."),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PartRow(t("Длительность"), sc.duration, 50)
            PartRow(t("Качество фаз"), sc.quality ?: 0, 25)
            PartRow(t("Восстановление"), sc.restoration ?: 0, 25)
        } else {
            Spacer(Modifier.height(10.dp))
            Text(
                if (manual) t("Записано вручную — оценка только по длительности сна.")
                else t("Оценка только по длительности: источник не передал фазы сна."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            SyncHint(onSync)
        }
    }
}

@Composable
private fun SyncHint(onSync: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Sync, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        Spacer(Modifier.width(8.dp))
        Text(
            t("Для более точной оценки нужна синхронизация с часами через Health Connect: фазы сна, пробуждения и эффективность."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onSync) { Text(t("Обновить")) }
    }
}

@Composable
private fun StageRow(type: Int, min: Int, pct: Int?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(Sleep.stageColor(type)))
        Spacer(Modifier.width(8.dp))
        Text(Sleep.stageName(type), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(formatMinutes(min.toDouble()), Modifier.width(76.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(pct?.let { "$it%" } ?: "", Modifier.width(40.dp), style = MaterialTheme.typography.bodyMedium)
        Text(Sleep.stageNorm(type).let { if (it.isEmpty()) "" else t("норма %s", it) }, Modifier.width(92.dp),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun PartRow(title: String, v: Int, max: Int) {
    BarRow(title, v.toFloat() / max, "$v / $max", valueWidth = 64, compact = true)
}

/** Гипнограмма: ряды «без сна / REM / лёгкий / глубокий», как в Google Health. */
@Composable
fun Hypnogram(d: SleepDetail, modifier: Modifier) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outlineVariant
    val rows = listOf(0, 1, 2, 3)
    Column(modifier) {
        Row(Modifier.weight(1f)) {
            Column(Modifier.width(64.dp).fillMaxHeight()) {
                rows.forEach {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        Text(Sleep.stageName(it), style = MaterialTheme.typography.labelSmall, color = labelColor, maxLines = 1)
                    }
                }
            }
            Canvas(Modifier.weight(1f).fillMaxHeight()) {
                val total = d.stages.maxOfOrNull { it.offset + it.dur }?.coerceAtLeast(1) ?: 1
                val rowH = size.height / rows.size
                rows.forEachIndexed { i, _ -> drawLine(grid, Offset(0f, rowH * (i + 0.5f)), Offset(size.width, rowH * (i + 0.5f)), 1f) }
                var prev: Pair<Float, Int>? = null
                d.stages.sortedBy { it.offset }.forEach { s ->
                    val row = if (s.type == 4) 2 else s.type
                    val x = size.width * s.offset / total
                    val w = (size.width * s.dur / total).coerceAtLeast(1.5f)
                    val y = rowH * row + rowH * 0.18f
                    val c = Sleep.stageColor(s.type)
                    prev?.let { (px, pr) -> if (pr != row) drawLine(c.copy(alpha = 0.5f), Offset(x, rowH * (minOf(pr, row) + 0.5f)), Offset(x, rowH * (maxOf(pr, row) + 0.5f)), 1.5f) }
                    drawRoundRect(c, Offset(x, y), Size(w, rowH * 0.64f), androidx.compose.ui.geometry.CornerRadius(3f, 3f))
                    prev = x + w to row
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 64.dp)) {
            Text(Sleep.clock(d.start), style = MaterialTheme.typography.labelSmall, color = labelColor)
            Spacer(Modifier.weight(1f))
            Text(Sleep.clock(d.end), style = MaterialTheme.typography.labelSmall, color = labelColor)
        }
    }
}

/** Столбики фаз по дням (для графиков за период). */
@Composable
fun StageBars(details: List<SleepDetail?>, modifier: Modifier) {
    val p = details.filterNotNull().filter { it.hasStages }
    if (p.isEmpty()) { Box(modifier); return }
    val mx = p.maxOf { it.asleep + it.awake }.coerceAtLeast(1)
    Canvas(modifier) {
        val n = details.size
        val bw = (size.width / n * 0.7f).coerceAtLeast(1f)
        details.forEachIndexed { i, d ->
            if (d == null || !d.hasStages) return@forEachIndexed
            var y = size.height
            listOf(3 to d.deep, 2 to d.light + d.unspecified, 1 to d.rem, 0 to d.awake).forEach { (tp, m) ->
                val h = size.height * m / mx
                drawRect(Sleep.stageColor(tp), Offset(size.width * (i + 0.15f) / n, y - h), Size(bw, h))
                y -= h
            }
        }
    }
}

// =====================================================================
// Графики: одна карточка «Сон», как весы
// =====================================================================

data class SleepSeries(val id: String, val title: String, val values: List<Double?>, val fmt: (Double) -> String, val good: Int)

class SleepCard(key: String, title: String, val metrics: List<SleepSeries>, val details: List<SleepDetail?>, val goal: Double) : StatCard(key, "Часы", title)

private fun clockMin(v: Double): String { val m = ((Math.round(v).toInt() % 1440) + 1440) % 1440; return "%02d:%02d".format(m / 60, m % 60) }

fun buildSleepCard(item: TemplateItem, dayVals: List<Map<String, String>>, prefs: Prefs?): SleepCard? {
    val mins = dayVals.map { parseNumber(it[item.id]) }
    if (mins.all { it == null }) return null
    val det = dayVals.map { Sleep.detail(it, item) }
    val goal = Sleep.goal(prefs, item)
    val zone = ZoneId.systemDefault()
    fun stage(f: (SleepDetail) -> Int) = det.map { d -> d?.takeIf { it.hasStages }?.let { f(it).toDouble() } }
    // отбой — в минутах от полудня, чтобы 23:30 и 00:30 шли подряд
    val bed = det.map { d -> d?.let { Instant.ofEpochMilli(it.start).atZone(zone).toLocalTime().let { t -> ((t.hour * 60 + t.minute - 720 + 1440) % 1440).toDouble() } } }
    val wake = det.map { d -> d?.let { Instant.ofEpochMilli(it.end).atZone(zone).toLocalTime().let { t -> (t.hour * 60 + t.minute).toDouble() } } }
    val list = listOfNotNull(
        SleepSeries("score", t("Оценка"), mins.indices.map { i -> mins[i]?.let { Sleep.score(it, det[i], goal).total.toDouble() } },
            { formatNumber(Math.round(it).toDouble()) }, 1),
        SleepSeries("dur", t("Длительность"), mins, ::formatMinutes, 1),
        SleepSeries("stages", t("Фазы"), stage { it.asleep }, ::formatMinutes, 1).takeIf { s -> s.values.any { it != null } },
        SleepSeries("deep", t("Глубокий"), stage { it.deep }, ::formatMinutes, 1),
        SleepSeries("rem", t("REM"), stage { it.rem }, ::formatMinutes, 1),
        SleepSeries("light", t("Лёгкий"), stage { it.light + it.unspecified }, ::formatMinutes, 0),
        SleepSeries("awake", t("Без сна"), stage { it.awake }, ::formatMinutes, -1),
        SleepSeries("wakes", t("Пробуждения"), stage { it.wakes }, { formatNumber(Math.round(it).toDouble()) }, -1),
        SleepSeries("eff", t("Эффективность"), det.map { d -> d?.takeIf { it.hasStages }?.efficiency?.let { it * 100 } }, { "${Math.round(it)}%" }, 1),
        SleepSeries("bed", t("Отбой"), bed, { clockMin(it + 720) }, 0),
        SleepSeries("wake", t("Подъём"), wake, ::clockMin, 0),
    ).filter { s -> s.values.any { it != null } }
    return SleepCard("num_" + item.id, item.displayName, list, det, goal)
}

@Composable
fun SleepFull(c: SleepCard, ctx: Ctx) {
    var sel by remember { mutableStateOf(ctx.prefs.sleepSelected) }
    val cur = c.metrics.firstOrNull { it.id == sel } ?: c.metrics.first()
    val key = c.key + "_" + cur.id
    val def = if (cur.id == "score" || cur.id == "dur") ChartType.BARS else ChartType.LINE
    SectionCard(c.title, watch = true, action = { if (cur.id != "stages") ChartTypeMenu(key, def, ctx) }) {
        Row(Modifier.hScroll(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            c.metrics.forEach { m ->
                androidx.compose.material3.FilterChip(selected = m.id == cur.id, onClick = { sel = m.id; ctx.prefs.sleepSelected = m.id },
                    label = { Text(m.title) })
            }
        }
        val p = cur.values.filterNotNull()
        Row(Modifier.padding(top = 6.dp).hScroll(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (p.isNotEmpty()) {
                Stat(t("среднее"), cur.fmt(p.average()))
                Stat(t("мин."), cur.fmt(p.min())); Stat(t("макс."), cur.fmt(p.max()))
            }
            trend(cur.values)?.takeIf { cur.id != "bed" && cur.id != "wake" }?.let { Stat(t("тренд"), deltaText(it, cur.fmt), deltaColor(it, cur.good)) }
        }
        Spacer(Modifier.size(8.dp))
        if (cur.id == "stages") {
            StageBars(c.details, Modifier.fillMaxWidth().height(150.dp))
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(3, 2, 1, 0).forEach { tp ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(Sleep.stageColor(tp)))
                        Spacer(Modifier.width(3.dp)); Text(Sleep.stageName(tp), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        } else Chart(cur.values, ctx.days, ctx.prefs.chartType(key, def), cur.fmt,
            target = if (cur.id == "score") 80.0 else if (cur.id == "dur") c.goal else null)
        Spacer(Modifier.size(8.dp))
        HorizontalDivider()
        c.metrics.filter { it.id != "stages" }.forEach { m ->
            val pm = m.values.filterNotNull()
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(if (m.id == cur.id) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) else Color.Transparent)
                    .clickable { sel = m.id; ctx.prefs.sleepSelected = m.id }
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(m.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Text(if (pm.isEmpty()) "—" else m.fmt(pm.average()), Modifier.width(80.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Sparkline(m.values, Modifier.width(56.dp).height(22.dp))
            }
        }
        Text(t("В таблице — средние за период. Фазы и эффективность — только по ночам с данными часов."),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun SleepCompact(c: SleepCard, big: androidx.compose.ui.text.TextStyle) {
    val score = c.metrics.first { it.id == "score" }
    val dur = c.metrics.first { it.id == "dur" }
    val avg = score.values.filterNotNull().takeIf { it.isNotEmpty() }?.average()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(avg?.let { "${Math.round(it)}" } ?: "—", style = big, fontWeight = FontWeight.SemiBold, maxLines = 1,
            color = avg?.let { sleepColor(Sleep.band(Math.round(it).toInt())) } ?: Color.Unspecified)
        Spacer(Modifier.width(6.dp))
        Text(dur.values.filterNotNull().takeIf { it.isNotEmpty() }?.average()?.let(::formatMinutes) ?: "", style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
    Text(t("средняя оценка"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    Spacer(Modifier.height(6.dp))
    Sparkline(score.values, Modifier.fillMaxWidth().height(36.dp), bars = true)
    Text(t("+ ещё %d показателей", c.metrics.size - 1), style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline, maxLines = 1)
}

