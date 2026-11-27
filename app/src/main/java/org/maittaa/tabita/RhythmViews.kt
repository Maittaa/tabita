package org.maittaa.tabita

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ViewTimeline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.cos
import kotlin.math.sin

/**
 * 5.43: «Ритм дня» отдельной плиткой. Два вида (Prefs.rhythmView): дорожки (RhythmChart) и круг —
 * циферблат на 12 часов вперёд от «сейчас»: каждое окно — своё кольцо с заливкой, стрелка — текущее время.
 */
object RhythmNow {
    data class Info(
        /** Что сейчас: «Пик сил», «Тусклый свет», «Пора спать», «День». */
        val title: String,
        /** Ближайшее: «кофе до 15:00 · ужин до 20:00». */
        val next: String,
        /** Минут до отбоя (0 — уже пора). */
        val toBed: Int,
        val active: Set<String>,
        val sleeping: Boolean,
    )

    fun rel(r: DayRhythm, now: LocalTime) = ((now.hour * 60 + now.minute) - r.wake + 1440) % 1440

    fun of(r: DayRhythm, now: LocalTime): Info {
        val n = rel(r, now)
        val b = r.bedRel
        val lanes = r.lanes
        val active = lanes.filter { n in it.from until it.to }.map { it.key }.toSet()
        val sleeping = n >= b
        val title = when {
            sleeping -> t("Пора спать")
            "dim" in active -> t("Тусклый свет")
            "peak" in active -> t("Пик сил")
            "light" in active -> t("Свет, бодрость")
            else -> t("День")
        }
        val caf = lanes.first { it.key == "caf" }; val food = lanes.first { it.key == "food" }
        val next = listOfNotNull(
            if (n < caf.to) t("кофе до %s", clockOf(r.abs(caf.to))) else null,
            if (n < food.to) t("ужин до %s", clockOf(r.abs(food.to))) else null,
        ).joinToString(" · ")
        return Info(title, next, (b - n).coerceAtLeast(0), active, sleeping)
    }

    fun inText(min: Int): String = if (min >= 60) t("%d ч %d мин", min / 60, min % 60) else t("%d мин", min)
}

/** Отрезки окна в пределах [от, до) относительно подъёма, с учётом завтрашнего повтора. */
private fun clip(l: DayRhythm.Lane, a: Int, b: Int, next: Int): List<Pair<Int, Int>> =
    listOf(0, next).mapNotNull { sh ->
        val f = maxOf(l.from + sh, a); val tt = minOf(l.to + sh, b)
        if (tt > f) f to tt else null
    }

/**
 * Круг на 12 часов: «12» сверху, кольца — окна дня (снаружи — свет, внутри — сон), показан отрезок от «сейчас» на 12 ч вперёд;
 * бледная дорожка — остаток кольца. В центре — время и что сейчас.
 */
@Composable
fun RhythmDial(r: DayRhythm, now: LocalTime, modifier: Modifier = Modifier, compact: Boolean = false, plan: DayPlan.Info? = null) {
    val cs = MaterialTheme.colorScheme
    val tm = rememberTextMeasurer()
    val info = RhythmNow.of(r, now)
    val nRel = RhythmNow.rel(r, now)
    val nowMin = now.hour * 60 + now.minute
    val lanes = r.lanes
    val trainRel = r.train?.let { (it - r.wake + 1440) % 1440 }
    Canvas(modifier.aspectRatio(1f).semantics { contentDescription = t("Ритм дня") + ": " + info.title }) {
        val d = size.minDimension
        val c = Offset(size.width / 2, size.height / 2)
        val outer = d / 2 - (if (compact) 1.dp.toPx() else 16.dp.toPx())
        val inner = outer * (if (compact) 0.30f else 0.42f)
        val step = (outer - inner) / lanes.size
        val sw = step * 0.72f
        fun ang(absMin: Int) = ((absMin % 720) / 720f) * 360f - 90f
        lanes.forEachIndexed { i, l ->
            val rad = outer - step * i - step / 2
            val tl = Offset(c.x - rad, c.y - rad); val sz = Size(rad * 2, rad * 2)
            val col = Color(l.color)
            drawArc(col.copy(alpha = 0.13f), 0f, 360f, false, tl, sz, style = Stroke(sw))
            clip(l, nRel, nRel + 720, r.nextRel).forEach { (f, tt) ->
                val sweep = (tt - f) / 720f * 360f
                drawArc(col.copy(alpha = if (l.key in info.active || (l.key == "sleep" && info.sleeping)) 1f else 0.8f),
                    ang(r.wake + f), sweep, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
            }
            // привычное время силовой — только если в плане нет тренировки (иначе её показывает маркер плана)
            if (l.key == "peak" && trainRel != null && plan?.events?.none { it.kind == DayPlan.Kind.TRAIN } != false && clip(l, nRel, nRel + 720, r.nextRel).any { trainRel in it.first..it.second || trainRel + 1440 in it.first..it.second }) {
                val a = Math.toRadians(ang(r.wake + trainRel).toDouble())
                val p = Offset(c.x + rad * cos(a).toFloat(), c.y + rad * sin(a).toFloat())
                drawCircle(cs.onSurface, sw * 0.42f, p); drawCircle(cs.surface, sw * 0.25f, p)
            }
        }
        // 5.44: события дня — маркеры на своих кольцах (те же, что в подписях и «Плане дня»)
        plan?.events?.forEach { e ->
            val key = eventLane(e.kind)
            val i = lanes.indexOfFirst { it.key == key }.takeIf { it >= 0 } ?: return@forEach
            val eRel = (e.at - r.wake + 1440) % 1440
            if (eRel !in nRel..nRel + 720 && eRel + 1440 !in nRel..nRel + 720) return@forEach
            val rad = outer - step * i - step / 2
            val a = Math.toRadians(ang(e.at).toDouble())
            drawPlanMarker(markerOf(e), Offset(c.x + rad * cos(a).toFloat(), c.y + rad * sin(a).toFloat()), sw * 0.55f, Color(lanes[i].color), cs.onSurface, cs.surface)
        }
        // часовые деления и цифры 12 / 3 / 6 / 9
        if (!compact) for (h in 0 until 12) {
            val a = Math.toRadians((h * 30 - 90).toDouble())
            val r1 = outer + 3.dp.toPx(); val r2 = outer + (if (h % 3 == 0) 7.dp else 5.dp).toPx()
            drawLine(cs.outline, Offset(c.x + r1 * cos(a).toFloat(), c.y + r1 * sin(a).toFloat()),
                Offset(c.x + r2 * cos(a).toFloat(), c.y + r2 * sin(a).toFloat()), 1.dp.toPx())
            if (h % 3 == 0) {
                val lbl = tm.measure(if (h == 0) "12" else "$h", TextStyle(fontSize = 10.sp, color = cs.onSurfaceVariant))
                val rl = outer + 12.dp.toPx()
                drawText(lbl, topLeft = Offset(c.x + rl * cos(a).toFloat() - lbl.size.width / 2, c.y + rl * sin(a).toFloat() - lbl.size.height / 2))
            }
        }
        // стрелка «сейчас»
        val a = Math.toRadians(ang(nowMin).toDouble())
        val tipR = if (compact) outer - 1.dp.toPx() else outer + 2.dp.toPx()
        val tip = Offset(c.x + tipR * cos(a).toFloat(), c.y + tipR * sin(a).toFloat())
        val base = Offset(c.x + inner * 0.92f * cos(a).toFloat(), c.y + inner * 0.92f * sin(a).toFloat())
        drawLine(cs.error, base, tip, (if (compact) 1.5.dp else 2.dp).toPx(), cap = StrokeCap.Round)
        drawCircle(cs.error, (if (compact) 2.dp else 3.5.dp).toPx(), tip)
        if (!compact) {
            val big = tm.measure(clockOf(nowMin), TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface))
            val st = tm.measure(info.title, TextStyle(fontSize = 11.sp, color = cs.primary, fontWeight = FontWeight.Medium),
                maxLines = 1, overflow = TextOverflow.Ellipsis, constraints = androidx.compose.ui.unit.Constraints(maxWidth = (inner * 1.8f).toInt().coerceAtLeast(1)))
            val toBed = if (info.sleeping) null else tm.measure(t("отбой через %s", RhythmNow.inText(info.toBed)),
                TextStyle(fontSize = 9.sp, color = cs.onSurfaceVariant), maxLines = 1, overflow = TextOverflow.Ellipsis,
                constraints = androidx.compose.ui.unit.Constraints(maxWidth = (inner * 1.8f).toInt().coerceAtLeast(1)))
            val total = big.size.height + st.size.height + (toBed?.size?.height ?: 0)
            var y = c.y - total / 2f
            drawText(big, topLeft = Offset(c.x - big.size.width / 2, y)); y += big.size.height
            drawText(st, topLeft = Offset(c.x - st.size.width / 2, y)); y += st.size.height
            toBed?.let { drawText(it, topLeft = Offset(c.x - it.size.width / 2, y)) }
        }
    }
}

/** 5.45.1: вид маркера события — один и тот же на круге, в подписях и в «Плане дня». */
enum class PlanMarker { PLANNED, DONE, WALK }

fun markerOf(e: DayPlan.Event) = when {
    e.done -> PlanMarker.DONE
    e.kind == DayPlan.Kind.WALK -> PlanMarker.WALK
    else -> PlanMarker.PLANNED
}

/** На каком кольце событие: еда/перекус/прогулка — «Еда», тренировка — «Пик сил», дневной сон — «Сон», белок перед сном — «Тусклый свет». */
fun eventLane(k: DayPlan.Kind) = when (k) {
    DayPlan.Kind.MEAL, DayPlan.Kind.PRE_SNACK, DayPlan.Kind.WALK -> "food"
    DayPlan.Kind.TRAIN, DayPlan.Kind.CARDIO -> "peak"; DayPlan.Kind.NAP -> "sleep"; DayPlan.Kind.BED_PROTEIN, DayPlan.Kind.YOGA -> "dim"
}

/**
 * Маркер: план — тёмная точка в светлом ободке; сделано — тёмная точка с белой галочкой;
 * прогулка — пустой кружок цвета кольца. [rr] — радиус ободка.
 */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPlanMarker(m: PlanMarker, p: Offset, rr: Float, lane: Color, ink: Color, paper: Color) {
    when (m) {
        PlanMarker.WALK -> { drawCircle(paper, rr * 0.8f, p); drawCircle(lane, rr * 0.8f, p, style = Stroke(rr * 0.28f)) }
        PlanMarker.PLANNED -> { drawCircle(paper, rr, p); drawCircle(ink, rr * 0.68f, p) }
        PlanMarker.DONE -> {
            drawCircle(paper, rr, p); drawCircle(ink, rr * 0.82f, p)
            val k = rr * 0.42f
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(p.x - k, p.y); lineTo(p.x - k * 0.25f, p.y + k * 0.7f); lineTo(p.x + k, p.y - k * 0.65f)
            }
            drawPath(path, paper, style = Stroke(rr * 0.24f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
    }
}

/** Маркер для подписей — тот же рисунок, что на круге, на фоне цвета кольца. */
@Composable
fun MarkerIcon(m: PlanMarker, laneKey: String, r: DayRhythm, size: androidx.compose.ui.unit.Dp = 18.dp) {
    val cs = MaterialTheme.colorScheme
    val lane = Color(r.lanes.firstOrNull { it.key == laneKey }?.color ?: 0xFF888888)
    Canvas(Modifier.size(size)) {
        val c = Offset(this.size.width / 2, this.size.height / 2)
        // кусочек кольца — чтобы маркер выглядел как на круге
        drawLine(lane, Offset(0f, c.y), Offset(this.size.width, c.y), this.size.height * 0.42f, cap = StrokeCap.Round)
        drawPlanMarker(m, c, this.size.minDimension * 0.36f, lane, cs.onSurface, cs.surface)
    }
}

/** 5.44: план дня списком — время, что и подсказка; прошедшее бледнее, ближайшее — акцентом. */
@Composable
fun RhythmPlanList(plan: DayPlan.Info, now: LocalTime, r: DayRhythm) {
    val wake = r.wake
    val cs = MaterialTheme.colorScheme
    val n = ((now.hour * 60 + now.minute) - wake + 1440) % 1440
    val next = plan.events.firstOrNull { !it.done && (it.at - wake + 1440) % 1440 >= n }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(t("План дня"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        plan.events.forEach { e ->
            val past = (e.at - wake + 1440) % 1440 + e.len < n
            val col = if (e == next) cs.primary else if (past) cs.outline else cs.onSurface
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 1.dp, end = 8.dp)) { MarkerIcon(markerOf(e), eventLane(e.kind), r) }
                Text(clockOf(e.at), Modifier.width(48.dp), style = MaterialTheme.typography.labelLarge, color = col,
                    fontWeight = if (e == next) FontWeight.Bold else FontWeight.Normal)
                Column(Modifier.weight(1f)) {
                    Text(e.title, style = MaterialTheme.typography.labelLarge, color = col, fontWeight = if (e == next) FontWeight.Bold else FontWeight.Medium)
                    if (e.note.isNotEmpty()) Text(e.note, style = MaterialTheme.typography.labelSmall, color = if (past) cs.outline else cs.onSurfaceVariant)
                }
            }
        }
        plan.notes.forEach { Text("⚠ $it", style = MaterialTheme.typography.labelSmall, color = cs.tertiary) }
    }
}

/**
 * 5.44: предупреждение о сдвиге режима — только когда сдвиг реальный: выходные смещают сон на ≥ 1 ч
 * или отбой за неделю гуляет на ≥ ±1 ч. Связи из исследований, без запугивания.
 */
@Composable
fun RhythmShiftWarning(r: DayRhythm) {
    val jet = r.jetlag?.takeIf { it >= 60 }
    val spread = r.bedSpread?.takeIf { it >= 60 }
    if (jet == null && spread == null) return
    var open by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.tertiaryContainer, contentColor = cs.onTertiaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.clickable { open = !open }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(t("Режим сна сдвигается"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(listOfNotNull(
                jet?.let { t("в выходные сон смещён на %s относительно будней", formatMinutes(it.toDouble())) },
                spread?.let { t("отбой за неделю меняется на ±%s", formatMinutes(it.toDouble())) },
            ).joinToString("; ").replaceFirstChar { it.uppercase() } + ".", style = MaterialTheme.typography.bodySmall)
            if (!open) Text(t("Чем это грозит и что делать"), style = MaterialTheme.typography.labelLarge, color = cs.primary)
            else {
                Text(t("Чем грозит. Сдвиг сна в выходные называют «социальным джетлагом»: в исследовании на 65 тыс. человек (Current Biology, 2012) он был связан с более высоким ИМТ независимо от длительности сна. Нерегулярный сон в UK Biobank (60 тыс. человек, Sleep, 2023) был связан с более высоким риском преждевременной смерти — сильнее, чем короткий сон. Это связи, а не доказанная причина. Сами исследователи отмечают, что при таком сдвиге люди хронически недосыпают: в понедельник тяжело встать, днём клонит в сон."),
                    style = MaterialTheme.typography.bodySmall)
                Text(t("Что делать. Подъём в выходные — не позже чем на час от будничного; утром — яркий свет; отбой в одно время ±30 мин. Не выспались — лучше короткий дневной сон до 15:00, чем долгий сон утром."),
                    style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** Подписи колец: цвет, окно и время. Активные сейчас — жирным. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RhythmLegend(r: DayRhythm, now: LocalTime, withMarkers: Boolean = false) {
    val info = RhythmNow.of(r, now)
    val en = I18n.isEn
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        r.lanes.forEach { l ->
            val on = l.key in info.active || (l.key == "sleep" && info.sleeping)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(Color(l.color), CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(if (en) l.en else l.ru, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
                Text(clockOf(r.abs(l.from)) + "–" + clockOf(r.abs(l.to)), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        Text(t("Круг — ближайшие 12 часов от стрелки; кольца от внешнего: свет, кофе, еда, пик сил, тусклый свет, сон."),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (withMarkers) {
            listOf(
                Triple(PlanMarker.PLANNED, "food", t("по плану: еда — на кольце «Еда», тренировка — «Пик сил», белок перед сном — «Тусклый свет»")),
                Triple(PlanMarker.DONE, "food", t("уже сделано сегодня")),
                Triple(PlanMarker.WALK, "food", t("прогулка после еды")),
            ).forEach { (m, k, txt) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MarkerIcon(m, k, r); Spacer(Modifier.width(8.dp))
                    Text(txt, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Тонкая полоса дня: окна на одной дорожке от подъёма до подъёма, отметка «сейчас». */
@Composable
fun RhythmBar(r: DayRhythm, now: LocalTime, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 8.dp) {
    val cs = MaterialTheme.colorScheme
    val n = RhythmNow.rel(r, now)
    val paint = listOf("light", "peak", "dim", "sleep")
    Canvas(modifier.fillMaxWidth().height(height + 6.dp)) {
        val w = size.width; val h = height.toPx(); val y = 3.dp.toPx()
        fun x(rel: Int) = w * rel.coerceIn(0, 1440) / 1440f
        drawRoundRect(cs.surfaceVariant, Offset(0f, y), Size(w, h), CornerRadius(h / 2))
        r.lanes.filter { it.key in paint }.forEach { l ->
            drawRoundRect(Color(l.color), Offset(x(l.from), y), Size((x(l.to) - x(l.from)).coerceAtLeast(h), h), CornerRadius(h / 2))
        }
        // конец кофе и ужина — короткие засечки
        r.lanes.filter { it.key == "caf" || it.key == "food" }.forEach { l ->
            drawLine(Color(l.color), Offset(x(l.to), y - 2.dp.toPx()), Offset(x(l.to), y + h + 2.dp.toPx()), 2.dp.toPx())
        }
        drawLine(cs.error, Offset(x(n), 0f), Offset(x(n), size.height), 2.dp.toPx(), cap = StrokeCap.Round)
    }
}

/** Полоса над плитками (Prefs.rhythmStrip): что сейчас, до отбоя, полоса; по нажатию — окно с ритмом. */
@Composable
fun RhythmStrip(r: DayRhythm, now: LocalTime, onOpen: () -> Unit) {
    val info = RhythmNow.of(r, now)
    Column(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Schedule, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text(
                listOfNotNull(info.title, if (info.sleeping) null else t("отбой %s · через %s", clockOf(r.bed), RhythmNow.inText(info.toBed)))
                    .joinToString(" · "),
                style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        RhythmBar(r, now, height = 5.dp)
    }
}

/** Уменьшенная плитка ритма (5.47): вид от ширины и высоты — круг/полоса, что сейчас, отбой, ближайшие события. */
@Composable
fun RhythmCompact(title: String, r: DayRhythm, now: LocalTime, w: Int, h: Int, dial: Boolean, plan: DayPlan.Info? = null) {
    val info = RhythmNow.of(r, now)
    val cs = MaterialTheme.colorScheme
    val ty = MaterialTheme.typography
    val bedLine = if (info.sleeping) t("сейчас время сна") else t("отбой %s · через %s", clockOf(r.bed), RhythmNow.inText(info.toBed))
    val next = nextEventText(plan, r, now)?.let { t("дальше: %s", it) }
    val n = RhythmNow.rel(r, now)
    val upcoming = plan?.events?.filter { !it.done && (it.at - r.wake + 1440) % 1440 >= n }?.take(3) ?: emptyList()
    @Composable fun small(text: String, max: Int = 1) = Text(text, style = ty.labelSmall, color = cs.onSurfaceVariant, maxLines = max, overflow = TextOverflow.Ellipsis)
    @Composable fun picture(m: Modifier) { if (dial) RhythmDial(r, now, m, compact = true, plan = plan) else Icon(Icons.Filled.Schedule, null, m, tint = cs.primary) }
    when {
        h <= 1 && w <= 2 -> Row(Modifier.fillMaxSize().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center) {
            Icon(Icons.Filled.Schedule, null, Modifier.size(16.dp), tint = cs.primary); Spacer(Modifier.width(4.dp))
            Column { Text(clockOf(r.bed), style = ty.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                if (w >= 2) small(t("отбой")) }
        }
        h <= 1 -> Row(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            picture(Modifier.fillMaxHeight().aspectRatio(1f)); Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(info.title, style = ty.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                small(bedLine)
            }
            if (w >= 5 && next != null) { Spacer(Modifier.width(8.dp)); Column(Modifier.weight(1f)) { small(next, 2) } }
        }
        h == 2 && w <= 1 -> Column(Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
            RhythmDial(r, now, Modifier.size(34.dp), compact = true)
            Text(clockOf(r.bed), style = ty.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
            small(t("отбой"))
        }
        h == 2 && w == 2 -> Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(title, style = ty.labelMedium, color = cs.onSurfaceVariant, maxLines = 1)
            Text(info.title, style = ty.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            small(next ?: bedLine)
            Spacer(Modifier.weight(1f))
            RhythmBar(r, now, height = 6.dp)
        }
        h == 2 -> Row(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (dial) { RhythmDial(r, now, Modifier.fillMaxHeight(), compact = true, plan = plan); Spacer(Modifier.width(12.dp)) }
            Column(Modifier.weight(1f)) {
                Text(title, style = ty.labelMedium, color = cs.onSurfaceVariant, maxLines = 1)
                Text(info.title, style = ty.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                small(listOfNotNull(next ?: info.next.ifEmpty { null }, bedLine).joinToString(" · "), if (w >= 4) 2 else 1)
                if (!dial) { Spacer(Modifier.height(4.dp)); RhythmBar(r, now, height = 6.dp) }
            }
        }
        w <= 1 -> Column(Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
            RhythmDial(r, now, Modifier.fillMaxWidth().aspectRatio(1f), compact = true, plan = plan)
            Text(clockOf(r.bed), style = ty.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
            small(t("отбой"))
        }
        w <= 3 -> Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (dial) RhythmDial(r, now, Modifier.weight(1f).aspectRatio(1f), compact = true, plan = plan)
            else Text(title, style = ty.labelMedium, color = cs.onSurfaceVariant, maxLines = 1)
            Text(info.title, style = ty.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            small(next ?: bedLine)
            if (w == 3) small(bedLine.takeIf { next != null } ?: "")
            if (!dial) { Spacer(Modifier.weight(1f)); RhythmBar(r, now, height = 6.dp) }
        }
        else -> Row(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (dial) { RhythmDial(r, now, Modifier.fillMaxHeight(), compact = true, plan = plan); Spacer(Modifier.width(12.dp)) }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Text(title, style = ty.labelMedium, color = cs.onSurfaceVariant, maxLines = 1)
                Text(info.title, style = ty.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                small(bedLine)
                if (!dial) { Spacer(Modifier.height(4.dp)); RhythmBar(r, now, height = 6.dp) }
                Spacer(Modifier.height(4.dp))
                upcoming.take(if (w >= 4) 3 else 2).forEach { e ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MarkerIcon(markerOf(e), eventLane(e.kind), r, 14.dp); Spacer(Modifier.width(4.dp))
                        Text(clockOf(e.at) + "  " + e.title, style = ty.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** Ритм целиком: круг с подписями или дорожки; план дня и предупреждение о сдвиге режима. */
@Composable
fun RhythmFull(r: DayRhythm, now: LocalTime, dial: Boolean, onMore: (() -> Unit)?, plan: DayPlan.Info? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (dial) {
            RhythmDial(r, now, Modifier.fillMaxWidth(0.82f).align(Alignment.CenterHorizontally), plan = plan)
            RhythmLegend(r, now, withMarkers = plan?.events?.isNotEmpty() == true)
        }
        RhythmShiftWarning(r)
        plan?.let { RhythmPlanList(it, now, r) }
        if (dial) RhythmNotes(r, onMore) else RhythmChart(r, now, showTitle = false, onMore = onMore)
    }
}

/** Ближайшее событие плана: «Обед 13:00». */
fun nextEventText(plan: DayPlan.Info?, r: DayRhythm, now: LocalTime): String? {
    val n = RhythmNow.rel(r, now)
    return plan?.events?.firstOrNull { !it.done && (it.at - r.wake + 1440) % 1440 >= n }
        ?.let { it.title + " " + clockOf(it.at) }
}

@Composable
fun RhythmBlock(repo: Repo, prefs: Prefs, item: TemplateItem, date: LocalDate, onMore: () -> Unit) {
    var dial by remember { mutableStateOf(prefs.rhythmView != "chart") }
    val minute = rememberTick(60_000)
    val now = remember(minute) { LocalTime.now() }
    val r = remember(repo.entries.value, date, minute) { DayRhythm.of(repo, date, prefs) }
    val plan = remember(repo.entries.value, date, minute) { runCatching { DayPlan.of(repo, prefs, date, r) }.getOrNull() }
    SectionCard(item.displayName, action = {
        IconButton({ dial = !dial; prefs.rhythmView = if (dial) "dial" else "chart"; Log.d("Rhythm", "вид: ${prefs.rhythmView}") }) {
            Icon(if (dial) Icons.Filled.ViewTimeline else Icons.Filled.DonutLarge, if (dial) t("Показать дорожками") else t("Показать кругом"))
        }
    }) {
        if (date != LocalDate.now()) Text(t("Ритм — для сегодняшнего дня."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else RhythmFull(r, now, dial, onMore, plan)
    }
}

/** Окно ритма — из полосы над плитками. */
@Composable
fun RhythmDialog(repo: Repo, prefs: Prefs, onMore: () -> Unit, onClose: () -> Unit) {
    val now = remember { LocalTime.now() }
    val r = remember { DayRhythm.of(repo, LocalDate.now(), prefs) }
    val plan = remember { runCatching { DayPlan.of(repo, prefs, LocalDate.now(), r) }.getOrNull() }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(t("Ритм дня")) },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                RhythmFull(r, now, prefs.rhythmView != "chart", { onClose(); onMore() }, plan)
            }
        },
        confirmButton = { TextButton(onClose) { Text(t("Закрыть")) } },
    )
}

/** 5.43: раздел «Ещё → Ритм дня». */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RhythmSettings(repo: Repo, prefs: Prefs) {
    var strip by remember { mutableStateOf(prefs.rhythmStrip) }
    var view by remember { mutableStateOf(prefs.rhythmView) }
    val tmpl by repo.template.collectAsState()
    val now = remember { LocalTime.now() }
    var ver by remember { mutableIntStateOf(0) }
    val r = remember(ver) { DayRhythm.of(repo, LocalDate.now(), prefs) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(t("Вид"), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(view != "chart", { view = "dial"; prefs.rhythmView = "dial" }, label = { Text(t("Круг 12 ч"), maxLines = 1, softWrap = false) },
                leadingIcon = { Icon(Icons.Filled.DonutLarge, null, Modifier.size(18.dp)) })
            FilterChip(view == "chart", { view = "chart"; prefs.rhythmView = "chart" }, label = { Text(t("Дорожки"), maxLines = 1, softWrap = false) },
                leadingIcon = { Icon(Icons.Filled.ViewTimeline, null, Modifier.size(18.dp)) })
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (view != "chart") RhythmDial(r, now, Modifier.size(150.dp))
            else RhythmBar(r, now, Modifier.padding(vertical = 8.dp), height = 10.dp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t("Полоса над плитками"), style = MaterialTheme.typography.bodyMedium)
                Text(t("Тонкая строка «что сейчас и когда отбой» над плитками на «Сегодня»; нажатие — ритм целиком."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(strip, { strip = it; prefs.rhythmStrip = it; Log.i("Rhythm", "полоса: $it") })
        }
        val item = tmpl.firstOrNull { it.type == ItemType.RHYTHM }
        if (item == null) {
            Text(t("Плитки «Ритм дня» нет на «Сегодня»."), style = MaterialTheme.typography.bodySmall)
            OutlinedButton({ repo.upsertItem(rhythmTemplateItem()); Log.i("Rhythm", "плитка возвращена") }) { Text(t("Вернуть плитку")) }
        } else Text(t("Плитка «Ритм дня» — на «Сегодня»: размер и место меняются кнопкой «Плитки»."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // 5.45: какие данные учитывать и что по ним известно
        Spacer(Modifier.height(6.dp))
        Text(t("Учитывать данные"), style = MaterialTheme.typography.titleSmall)
        Text(t("Ритм и план дня строятся по этим данным сами, без ручных настроек. Выключите то, что мешает."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        var src by remember { mutableStateOf(prefs.rhythmSrc) }
        listOf(
            RhythmSrc.SLEEP to (t("Сон с часов") to t("подъём, отбой, недосып — для окон дня и дневного сна")),
            RhythmSrc.ALARM to (t("Будильник") to t("подъём завтра → во сколько лечь сегодня")),
            RhythmSrc.HC_TRAIN to (t("Тренировки с часов") to t("привычное время силовой и сделанные сегодня")),
            RhythmSrc.APP_TRAIN to (t("Тренировки в приложении") to t("время начала тренировок на вкладке «Тренировки»")),
            RhythmSrc.MEAL_PHOTO to (t("Время еды по фото") to t("когда снято фото блюда — привычное время завтрака, обеда, ужина")),
        ).forEach { (k, v) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(v.first, style = MaterialTheme.typography.bodyMedium)
                    Text(v.second, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(k in src, { on -> src = if (on) src + k else src - k; prefs.rhythmSrc = src; ver++; Log.i("Rhythm", "данные: $src") })
            }
        }
        val learned = remember(ver, src) { RhythmLearned.lines(repo, prefs, r) }
        Text(t("Что известно о вашем дне"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
        learned.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        RhythmTimes(repo, prefs) { ver++ }
    }
}

/** 5.45: что ритм узнал из данных — для прозрачности. */
object RhythmLearned {
    fun lines(repo: Repo, prefs: Prefs, r: DayRhythm): List<String> {
        val today = LocalDate.now()
        val out = mutableListOf<String>()
        out += if (r.manual) t("Подъём %s, отбой %s — заданы вручную", clockOf(r.usualWake), clockOf(r.bed))
        else if (r.nights > 0) t("Подъём около %s, отбой около %s — по %s", clockOf(r.usualWake), clockOf(r.usualBed), nightsText(r.nights))
        else t("Сна в данных нет — подъём 7:00 по умолчанию")
        Habits.training(repo, prefs, today)?.let { out += t("Силовая — около %s, %s", clockOf(it.min), Habits.basisText(it, today)) }
            ?: run { out += t("Время силовой пока не известно — нужно 2 тренировки") }
        val m = Habits.meals(repo, prefs, today)
        if (m.isEmpty()) out += t("Время еды пока не известно — нужно хотя бы 3 приёма одного вида с фото")
        else Program.Slot.entries.forEach { s -> m[s]?.let { out += t("%s — около %s, %s", s.title, clockOf(it.min), Habits.basisText(it, today)) } }
        return out
    }
}
