package org.maittaa.tabita

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * «Ритм дня» (5.21): суточная диаграмма по вашему сну и тренировкам.
 * Окна считаются от подъёма и отбоя, а не от часов на циферблате. Отбой и подъём — средние за последние 7 ночей с данными
 * (сколько есть, если меньше), либо заданные вручную в настройках советов (5.22.2). Если сегодня вы уже проснулись,
 * отсчёт дня идёт от сегодняшнего подъёма.
 * свет и пик кортизола — первый час после подъёма; кофеин — не позже чем за 8 ч до сна;
 * еда — до 3 ч до сна; пик сил — 8–12 ч после подъёма (максимум температуры тела);
 * тусклый свет — 2 ч до сна (начало выработки мелатонина); гормон роста — первые 30–180 мин сна.
 */
data class DayRhythm(
    /** Минуты от полуночи. */
    val wake: Int, val bed: Int,
    /** Сколько ночей учтено (0 — значения по умолчанию). */
    val nights: Int,
    /** Привычное начало силовой, минуты от полуночи. */
    val train: Int?, val trainN: Int,
    /** Обычный подъём (средний); [wake] — сегодняшний, если уже проснулись. */
    val usualWake: Int = wake,
    /** Сегодняшний подъём известен по данным сна. */
    val wokeToday: Boolean = false,
    /** Время задано вручную. */
    val manual: Boolean = false,
    /** Учтённые ночи: начало и конец, мс. */
    val used: List<Pair<Long, Long>> = emptyList(),
    /** 5.26: привычный отбой (среднее по ночам того же типа — будни/выходные). */
    val usualBed: Int = bed,
    /** Подъём завтра: по будильнику или привычный для завтрашнего дня. */
    val nextWake: Int = usualWake,
    /** [nextWake] взят из будильника. */
    val alarm: Boolean = false,
    /** Нужно сна (норма + 15 мин на засыпание), мин. */
    val need: Int = 435,
    /** Разница середины сна в выходные и будни, мин («социальный джетлаг»); null — мало данных. */
    val jetlag: Int? = null,
    /** Разброс времени отбоя за неделю (стандартное отклонение), мин. */
    val bedSpread: Int? = null,
    /** Средний подъём в будни и в выходные (для подсказок). */
    val wakeWeekday: Int? = null, val wakeWeekend: Int? = null,
) {
    /** Отбой в минутах от подъёма (14–20 ч). */
    val bedRel get() = ((bed - wake + 1440) % 1440).coerceIn(14 * 60, 20 * 60)
    fun abs(rel: Int) = (wake + rel) % 1440
    /**
     * 5.45.1: завтрашний подъём в минутах от сегодняшнего (будильник или привычный) — ночной сон идёт до него,
     * а не до сегодняшнего времени подъёма; с него же начинается «завтра» на круге.
     */
    val nextRel: Int get() {
        val d = ((nextWake - wake) % 1440 + 1440) % 1440
        return (if (d <= bedRel) d + 1440 else d).coerceIn(bedRel + 60, bedRel + 16 * 60)
    }

    data class Lane(val key: String, val ru: String, val en: String, val from: Int, val to: Int, val color: Long, val noteRu: String, val noteEn: String)

    val lanes: List<Lane> get() {
        val b = bedRel
        val peakTo = minOf(12 * 60, b - 120)
        return listOf(
            Lane("light", "Свет, бодрость", "Light, alertness", 0, 60, 0xFFF2B33D,
                "яркий свет, пик кортизола", "bright light, cortisol peak"),
            Lane("caf", "Кофе и чай", "Coffee and tea", 0, b - 480, 0xFF9C6B4E,
                "до %s", "until %s"),
            Lane("food", "Еда", "Meals", 30, b - 180, 0xFF4CAF7A,
                "ужин до %s", "dinner by %s"),
            Lane("peak", "Пик сил", "Peak strength", minOf(8 * 60, peakTo - 60), peakTo, 0xFF4E7FD9,
                "лучшее время силовой", "best time for strength"),
            Lane("dim", "Тусклый свет", "Dim light", b - 120, b, 0xFF9575CD,
                "без ярких экранов, мелатонин", "no bright screens, melatonin"),
            Lane("sleep", "Ночной сон", "Night sleep", b, nextRel, 0xFF3F51B5,
                "гормон роста — первые 3 ч", "growth hormone — first 3 h"),
        )
    }

    companion object {
        /** Выходной для режима — суббота и воскресенье (день пробуждения). */
        fun weekend(d: LocalDate) = d.dayOfWeek == java.time.DayOfWeek.SATURDAY || d.dayOfWeek == java.time.DayOfWeek.SUNDAY

        /**
         * [alarmMs] — ближайший будильник (AlarmManager.nextAlarmClock), по умолчанию из [Alarms].
         * Отбой на сегодня = подъём завтра (будильник или привычный для завтрашнего дня) − норма сна − 15 мин на засыпание.
         */
        fun of(repo: Repo, date: LocalDate, prefs: Prefs? = null, now: java.time.LocalDateTime = java.time.LocalDateTime.now(),
               alarmMs: Long? = Alarms.next): DayRhythm {
            val tmpl = repo.template.value
            val ents = repo.entries.value
            val zone = ZoneId.systemDefault()
            // 5.45: какие данные учитывать (Ещё → Ритм дня → Учитывать данные)
            val src = prefs?.rhythmSrc ?: RhythmSrc.ALL
            val alarmMs = if (RhythmSrc.ALARM in src) alarmMs else null
            fun tod(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalTime().let { it.hour * 60 + it.minute }
            fun endDay(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
            val sleepItem = if (RhythmSrc.SLEEP in src) tmpl.firstOrNull { it.source == Source.HC_SLEEP } else null
            // ночной сон за 4 недели: 3–13 ч и начало не днём (дневной сон не в счёт); одна ночь — один раз
            val all = if (sleepItem == null) emptyList() else (0..27).mapNotNull { i ->
                Sleep.detail(ents[date.minusDays(i.toLong()).toString()] ?: emptyMap(), sleepItem)
            }.filter { it.end - it.start in 3 * 3600_000L..13 * 3600_000L && tod(it.start).let { m -> m >= 18 * 60 || m < 8 * 60 } }
                .distinctBy { it.start / 600_000 }.sortedByDescending { it.end }
            fun bedOf(ms: Long) = (tod(ms) - 720 + 1440) % 1440   // от полудня: 23:30 и 00:30 рядом
            fun wakeMean(l: List<SleepDetail>) = l.map { tod(it.end) }.meanOrNull()
            fun bedMean(l: List<SleepDetail>) = l.map { bedOf(it.start) }.meanOrNull()?.let { (it + 720) % 1440 }
            val wd = all.filter { !weekend(endDay(it.end)) }.take(7)
            val we = all.filter { weekend(endDay(it.end)) }.take(4)
            // для дня — ночи того же типа (будни/выходные), если их хватает; иначе все последние 7
            fun ofType(d: LocalDate) = (if (weekend(d)) we else wd).takeIf { it.size >= 2 } ?: all.take(7)
            val det = ofType(date)
            val mw = prefs?.rhythmWake?.takeIf { it >= 0 }
            val mb = prefs?.rhythmBed?.takeIf { it >= 0 }
            val usualWake = mw ?: wakeMean(det) ?: 7 * 60
            val usualBed = bedMean(ofType(date.plusDays(1))) ?: bedMean(det) ?: 23 * 60
            // сегодня уже проснулись: сон закончился сегодня и не позже «сейчас»
            val nowMs = now.atZone(zone).toInstant().toEpochMilli()
            val todayEnd = all.firstOrNull { d -> endDay(d.end) == date && d.end <= nowMs }?.end
            val wake = todayEnd?.let(::tod) ?: usualWake
            // подъём завтра: будильник на завтрашнее утро (до 14:00) — иначе привычный для завтрашнего дня
            val tomorrow = date.plusDays(1)
            val alarmMin = alarmMs?.let { a ->
                val z = Instant.ofEpochMilli(a).atZone(zone)
                if (z.toLocalDate() == tomorrow && z.hour < 14 && a > nowMs) z.hour * 60 + z.minute else null
            }
            val tomorrowWake = alarmMin ?: mw ?: wakeMean(ofType(tomorrow)) ?: usualWake
            val goal = tmpl.firstOrNull { it.source == Source.HC_SLEEP }?.let { Sleep.goal(prefs, it).toInt() } ?: 420
            val need = goal + 15
            val recBed = ((tomorrowWake - need) % 1440 + 1440) % 1440
            val bed = mb ?: recBed
            // режим: середина сна в будни и выходные, разброс отбоя за неделю
            fun mid(l: List<SleepDetail>) = l.map { ((bedOf(it.start) + ((it.end - it.start) / 120_000).toInt())) }.meanOrNull()
            val jet = if (wd.size >= 3 && we.size >= 2) kotlin.math.abs(mid(we)!! - mid(wd)!!) else null
            val beds7 = all.take(7).map { bedOf(it.start) }
            val spread = if (beds7.size >= 4) beds7.average().let { m -> Math.sqrt(beds7.sumOf { (it - m) * (it - m) } / beds7.size) }.toInt() else null
            val exItem = if (RhythmSrc.HC_TRAIN in src) tmpl.firstOrNull { it.source == Source.HC_EXERCISE } else null
            val tr = if (exItem == null) emptyList() else (0..27).flatMap { i ->
                Workouts.sessions(ents[date.minusDays(i.toLong()).toString()] ?: emptyMap(), exItem.id)
                    .filter { it.kind == "strength" }.mapNotNull { it.start?.let(::tod) }
            }
            return DayRhythm(wake, bed, det.size, tr.median(), tr.size, usualWake = usualWake, wokeToday = todayEnd != null,
                manual = mw != null || mb != null, used = all.take(7).map { it.start to it.end },
                usualBed = usualBed, nextWake = tomorrowWake, alarm = alarmMin != null, need = need,
                jetlag = jet, bedSpread = spread, wakeWeekday = wakeMean(wd), wakeWeekend = wakeMean(we))
        }

        private fun List<Int>.meanOrNull(): Int? = if (isEmpty()) null else Math.round(average()).toInt()
        private fun List<Int>.median(): Int? = if (isEmpty()) null else sorted()[size / 2]
    }
}

fun clockOf(min: Int): String = "%d:%02d".format(((min % 1440) + 1440) % 1440 / 60, ((min % 1440) + 1440) % 1440 % 60)

/** Диаграмма «Ритм дня» для плитки советов. */
@Composable
fun RhythmChart(r: DayRhythm, now: LocalTime?, showTitle: Boolean = true, onMore: (() -> Unit)?) {
    val cs = MaterialTheme.colorScheme
    val tm = rememberTextMeasurer()
    val en = I18n.isEn
    val nowRel = now?.let { ((it.hour * 60 + it.minute) - r.wake + 1440) % 1440 }
    val trainRel = r.train?.let { (it - r.wake + 1440) % 1440 }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (showTitle) Text(t("Ритм дня"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        r.lanes.forEach { l ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(112.dp)) {
                    Text(if (en) l.en else l.ru, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    val range = clockOf(r.wake + l.from) + "–" + clockOf(r.wake + l.to)
                    Text(range, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, maxLines = 1)
                }
                Box(Modifier.weight(1f).height(30.dp)) {
                    Canvas(Modifier.fillMaxWidth().height(30.dp)) {
                        val w = size.width
                        fun x(rel: Int) = w * rel.coerceIn(0, 1440) / 1440f
                        val c = Color(l.color)
                        val y = 6.dp.toPx(); val hgt = 12.dp.toPx()
                        drawRoundRect(cs.surfaceVariant, Offset(0f, y + hgt / 2 - 1.dp.toPx()), Size(w, 2.dp.toPx()))
                        nowRel?.let { n ->
                            drawLine(cs.error, Offset(x(n), 0f), Offset(x(n), y + hgt + 2.dp.toPx()), 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))
                        }
                        drawRoundRect(c.copy(alpha = 0.85f), Offset(x(l.from), y), Size((x(l.to) - x(l.from)).coerceAtLeast(hgt * 1.6f), hgt), CornerRadius(hgt / 2))
                        if (l.key == "sleep") {
                            // гормон роста: первые 30–180 мин сна
                            drawRoundRect(Color(0xFF1A237E), Offset(x(l.from + 30), y + 2.dp.toPx()),
                                Size(x(l.from + 180) - x(l.from + 30), hgt - 4.dp.toPx()), CornerRadius(hgt / 2))
                        }
                        if (l.key == "peak" && trainRel != null) {
                            drawCircle(cs.onSurface, 5.dp.toPx(), Offset(x(trainRel), y + hgt / 2))
                            drawCircle(cs.surface, 3.dp.toPx(), Offset(x(trainRel), y + hgt / 2))
                        }
                        val note = (if (en) l.noteEn else l.noteRu).let {
                            if (l.key == "caf" || l.key == "food") it.format(clockOf(r.wake + l.to)) else it
                        }
                        val tl = tm.measure(note, TextStyle(fontSize = 9.sp, color = cs.onSurfaceVariant))
                        val tx = if (x(l.from) + tl.size.width <= w) x(l.from) else (w - tl.size.width).coerceAtLeast(0f)
                        drawText(tl, topLeft = Offset(tx, y + hgt + 1.dp.toPx()))
                    }
                }
            }
        }
        // ось времени
        Row {
            Box(Modifier.width(112.dp))
            Canvas(Modifier.weight(1f).height(14.dp)) {
                val w = size.width
                val first = ((r.wake + 59) / 60) * 60 + 0
                var m = first
                while (m - r.wake < 1440) {
                    val rel = m - r.wake
                    if ((m / 60) % 3 == 0) {
                        val tl = tm.measure("%d".format((m / 60) % 24), TextStyle(fontSize = 9.sp, color = cs.onSurfaceVariant))
                        val px = w * rel / 1440f - tl.size.width / 2
                        if (px >= 0 && px + tl.size.width <= w) drawText(tl, topLeft = Offset(px, 0f))
                    }
                    m += 60
                }
            }
        }
        RhythmNotes(r, onMore)
    }
}

/** Пояснения под ритмом: откуда время, будильник, привычная тренировка, ссылка на справочник. */
@Composable
fun RhythmNotes(r: DayRhythm, onMore: (() -> Unit)?) {
    val cs = MaterialTheme.colorScheme
    val trainRel = r.train?.let { (it - r.wake + 1440) % 1440 }
    Column {
        val src = (if (r.wokeToday) t("Сегодня подъём в %s.", clockOf(r.wake)) + " " else "") + when {
            r.manual -> t("Обычно: подъём %s, отбой %s (задано вручную в «Ещё → Ритм дня»).", clockOf(r.usualWake), clockOf(r.bed))
            r.nights >= 1 -> t("Обычно %s: подъём %s, отбой %s — по %s.", if (DayRhythm.weekend(LocalDate.now())) t("в выходные") else t("в будни"),
                clockOf(r.usualWake), clockOf(r.usualBed), nightsText(r.nights))
            else -> t("Данных сна нет — взят подъём %s. С часами и Health Connect окна подстроятся под вас, или задайте время в «Ещё → Ритм дня».", clockOf(r.usualWake))
        } + " " + if (r.manual) "" else if (r.alarm)
            t("Завтра будильник на %s — чтобы спать %s, лечь в %s.", clockOf(r.nextWake), formatMinutes((r.need - 15).toDouble()), clockOf(r.bed))
        else t("Будильника на завтра нет — рекомендую подъём в %s и отбой в %s.", clockOf(r.nextWake), clockOf(r.bed))
        val tr = trainRel?.takeIf { r.trainN >= 2 }?.let { rel ->
            val b = r.bedRel
            val l = r.lanes.first { it.key == "peak" }
            " " + when {
                rel in l.from..l.to -> t("Силовые обычно в %s — как раз в окне пика сил.", clockOf(r.train!!))
                rel > b - 120 -> t("Силовые обычно в %s — поздновато: тяжёлую лучше закончить за 2 ч до сна.", clockOf(r.train!!))
                else -> t("Силовые обычно в %s — тоже хорошо: тело привыкает к регулярному времени.", clockOf(r.train!!))
            }
        } ?: ""
        var showNights by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
        Text(src + tr, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp).then(if (r.used.isNotEmpty() && !r.manual) Modifier.clickable { showNights = !showNights } else Modifier))
        if (showNights) {
            val z = ZoneId.systemDefault()
            val fmt = java.time.format.DateTimeFormatter.ofPattern("d.MM")
            Text(r.used.joinToString("\n") { (a, b) ->
                val sa = Instant.ofEpochMilli(a).atZone(z); val sb = Instant.ofEpochMilli(b).atZone(z)
                sb.format(fmt) + ": " + clockOf(sa.hour * 60 + sa.minute) + " → " + clockOf(sb.hour * 60 + sb.minute)
            }, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        } else if (r.used.isNotEmpty() && !r.manual) Text(t("нажмите, чтобы увидеть ночи"), style = MaterialTheme.typography.labelSmall,
            color = cs.primary, modifier = Modifier.clickable { showNights = true })
        if (!r.alarm && !r.manual) {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            TextButton({ runCatching { ctx.startActivity(Alarms.setIntent(r.nextWake)) } }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp)) {
                Text(t("Поставить будильник на %s", clockOf(r.nextWake)))
            }
        }
        if (onMore != null) TextButton(onMore, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp)) {
            Text(t("Почему так — справочник «Суточные ритмы»"))
        }
    }
}

/** «1 ночь», «4 ночи», «7 ночей». */
fun nightsText(n: Int): String = if (I18n.isEn) "$n night" + (if (n == 1) "" else "s") else "$n " + when {
    n % 10 == 1 && n % 100 != 11 -> "ночь"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "ночи"
    else -> "ночей"
}

/** Ближайший будильник (5.26): AlarmManager.nextAlarmClock — будильник любого приложения часов. */
object Alarms {
    @Volatile var next: Long? = null
    fun refresh(ctx: android.content.Context) {
        next = runCatching { (ctx.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager).nextAlarmClock?.triggerTime }.getOrNull()
    }
    /** Открыть установку будильника в приложении часов. */
    fun setIntent(min: Int) = android.content.Intent(android.provider.AlarmClock.ACTION_SET_ALARM)
        .putExtra(android.provider.AlarmClock.EXTRA_HOUR, min / 60).putExtra(android.provider.AlarmClock.EXTRA_MINUTES, min % 60)
        .putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, "Tabita: подъём")
}
