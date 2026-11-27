package org.maittaa.tabita

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DownhillSkiing
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.Rowing
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.SportsGymnastics
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

// =====================================================================
// Виды тренировок
// =====================================================================

/** Вид тренировки: ключ, подпись, иконка и коды Health Connect, которые к нему сводятся. */
data class WorkoutKind(val key: String, val ru: String, val en: String, val icon: ImageVector, val hc: Set<Int>) {
    val title: String get() = if (I18n.isEn) en else ru
}

object Workouts {
    val kinds = listOf(
        WorkoutKind("strength", "Силовая", "Strength", Icons.Filled.FitnessCenter, setOf(70, 81, 13)),
        WorkoutKind("run", "Бег", "Running", Icons.AutoMirrored.Filled.DirectionsRun, setOf(56, 57)),
        WorkoutKind("walk", "Ходьба", "Walking", Icons.AutoMirrored.Filled.DirectionsWalk, setOf(79, 82)),
        WorkoutKind("hike", "Поход", "Hiking", Icons.Filled.Hiking, setOf(37, 51, 63)),
        WorkoutKind("bike", "Велосипед", "Cycling", Icons.AutoMirrored.Filled.DirectionsBike, setOf(8, 9)),
        WorkoutKind("swim", "Плавание", "Swimming", Icons.Filled.Pool, setOf(73, 74, 80, 59)),
        WorkoutKind("hiit", "ВИИТ", "HIIT", Icons.Filled.Whatshot, setOf(36, 10, 26)),
        // 5.51: аэробика дома (таймер в приложении); в Health Connect своего вида нет — приходит как «тренировка»
        WorkoutKind("aerobics", "Аэробика", "Aerobics", Icons.Filled.SportsGymnastics, emptySet()),
        WorkoutKind("cardio", "Кардиотренажёр", "Cardio machine", Icons.Filled.Rowing, setOf(25, 54, 69, 68, 53, 46)),
        WorkoutKind("yoga", "Йога, пилатес", "Yoga, Pilates", Icons.Filled.SelfImprovement, setOf(83, 48)),
        WorkoutKind("stretch", "Растяжка", "Stretching", Icons.Filled.AccessibilityNew, setOf(71, 33, 34)),
        WorkoutKind("dance", "Танцы", "Dancing", Icons.Filled.MusicNote, setOf(16)),
        WorkoutKind("sport", "Игровой спорт", "Team & racket sports",  Icons.Filled.SportsSoccer,
            setOf(2, 4, 5, 14, 28, 29, 31, 32, 35, 38, 50, 52, 55, 64, 65, 66, 75, 76, 78)),
        WorkoutKind("martial", "Единоборства", "Martial arts", Icons.Filled.SportsMartialArts, setOf(11, 27, 44)),
        WorkoutKind("winter", "Зимний спорт", "Winter sports", Icons.Filled.DownhillSkiing, setOf(39, 60, 61, 62)),
        WorkoutKind("other", "Другое", "Other", Icons.Filled.Timer, emptySet()),
    )

    fun kind(key: String) = kinds.firstOrNull { it.key == key } ?: kinds.last()
    fun fromHc(type: Int) = kinds.firstOrNull { type in it.hc } ?: kinds.last()

    fun syncedKey(id: String) = "$id!ex"
    fun manualKey(id: String) = "$id!exm"

    /**
     * Тяжесть нагрузки (5.25): доля минут, которая идёт в «тренировочную нагрузку» для советов и готовности.
     * Ходьба и растяжка — активность, а не тренировка (0); силовая, бег, ВИИТ, единоборства — полная нагрузка.
     */
    fun load(kind: String): Double = when (kind) {
        "strength", "run", "martial", "sport" -> 1.0
        "hiit" -> 1.2
        "swim", "winter", "aerobics" -> 0.8
        "bike", "cardio" -> 0.7
        "hike" -> 0.6
        "dance", "other" -> 0.5
        "yoga" -> 0.2
        else -> 0.0   // walk, stretch
    }
    /** Тренировочные минуты с учётом тяжести (ходьба не считается). */
    fun loadMin(list: List<WorkoutSession>): Double = list.sumOf { it.min * load(it.kind) }

    /** Засчитывается ли сессия: ходьба — только если так задано в настройках. */
    fun counts(s: WorkoutSession, countWalking: Boolean) = s.kind != "walk" || countWalking
    fun counted(list: List<WorkoutSession>, countWalking: Boolean) = list.filter { counts(it, countWalking) }.sumOf { it.min }

    fun parse(v: String?): List<WorkoutSession> = runCatching {
        val a = JSONArray(v ?: return emptyList())
        (0 until a.length()).map { WorkoutSession.fromJson(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    fun toJson(list: List<WorkoutSession>): String? = if (list.isEmpty()) null else JSONArray(list.map { it.toJson() }).toString()

    /** Все сессии дня: с часов и вручную; ручная, совпавшая по времени с записью часов, сливается с ней (5.51). */
    fun sessions(day: Map<String, String>, id: String) = merge(parse(day[syncedKey(id)]), parse(day[manualKey(id)])).first

    /** Виды, которые часы пишут общим словом: если в это время шёл таймер аэробики, берём вид из приложения. */
    private val generic = setOf("other", "hiit", "dance", "cardio")

    /**
     * 5.51: ручные сессии (таймер аэробики, запись вручную), которые совпали по времени с записью часов
     * (пересечение больше половины короткой), не дублируются: остаётся запись часов (пульс, дистанция),
     * а вид, название, заметка и ощущение — из ручной. Возвращает список и индексы поглощённых ручных.
     */
    fun merge(synced: List<WorkoutSession>, manual: List<WorkoutSession>): Pair<List<WorkoutSession>, Set<Int>> {
        if (manual.isEmpty() || synced.isEmpty()) return (synced + manual) to emptySet()
        val out = synced.toMutableList()
        val absorbed = mutableSetOf<Int>()
        manual.forEachIndexed { mi, m ->
            val a0 = m.start ?: return@forEachIndexed
            val a1 = a0 + m.min * 60_000L
            val si = out.indices.firstOrNull { i ->
                val s = out[i]; val b0 = s.start ?: return@firstOrNull false; val b1 = b0 + s.min * 60_000L
                minOf(a1, b1) - maxOf(a0, b0) > minOf(m.min, s.min) * 60_000L / 2
            } ?: return@forEachIndexed
            val s = out[si]
            out[si] = s.copy(
                kind = if (s.kind in generic || s.kind == m.kind) m.kind else s.kind,
                title = m.title.ifBlank { s.title }, note = m.note.ifBlank { s.note },
                effort = if (m.effort > 0) m.effort else s.effort,
                hr = s.hr ?: m.hr, hrMax = s.hrMax ?: m.hrMax, hist = s.hist.ifEmpty { m.hist }, dist = s.dist ?: m.dist,
            )
            absorbed += mi
        }
        return (out + manual.filterIndexed { i, _ -> i !in absorbed }) to absorbed
    }

    /**
     * Сессии из Health Connect за день → без дублей: если два приложения записали одну тренировку
     * (пересечение больше половины), берём ту, что началась раньше.
     */
    fun dedupe(list: List<WorkoutSession>): List<WorkoutSession> {
        val out = mutableListOf<WorkoutSession>()
        list.sortedBy { it.start ?: 0 }.forEach { s ->
            val dup = out.any { o ->
                val a0 = s.start ?: return@any false; val b0 = o.start ?: return@any false
                val a1 = a0 + s.min * 60_000L; val b1 = b0 + o.min * 60_000L
                val overlap = minOf(a1, b1) - maxOf(a0, b0)
                o.src != s.src && overlap > minOf(s.min, o.min) * 60_000L / 2
            }
            if (!dup) out += s
        }
        return out
    }

    fun clock(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime().let { "%02d:%02d".format(it.hour, it.minute) }

    /** Подпись видов за день для плитки: «Силовая, бег». */
    fun summary(list: List<WorkoutSession>): String =
        list.map { kind(it.kind).title }.distinct().joinToString(", ").lowercase().replaceFirstChar { it.uppercase() }
}

/**
 * Одна тренировка. start — эпоха в мс (для синхронизированных), src — пакет приложения-источника.
 * 5.51: пульс (средний, максимум), время на каждом значении пульса (hist: уд/мин → секунды — зоны считаются
 * по нему при показе, поэтому меняются вместе с пульсом покоя и максимумом), дистанция в метрах и
 * ощущение по тесту разговором (effort: 0 — не указано, 1 — легко, 2 — умеренно, 3 — интенсивно).
 */
data class WorkoutSession(
    val kind: String, val min: Int, val start: Long? = null, val title: String = "", val src: String = "", val note: String = "",
    val hr: Int? = null, val hrMax: Int? = null, val hist: Map<Int, Int> = emptyMap(), val dist: Int? = null, val effort: Int = 0,
) {
    fun toJson(): JSONObject = JSONObject().put("k", kind).put("m", min).apply {
        start?.let { put("s", it) }
        if (title.isNotBlank()) put("t", title)
        if (src.isNotBlank()) put("src", src)
        if (note.isNotBlank()) put("n", note)
        hr?.let { put("hr", it) }
        hrMax?.let { put("hx", it) }
        if (hist.isNotEmpty()) put("h", JSONObject().apply { hist.forEach { (b, s) -> put(b.toString(), s) } })
        dist?.let { put("d", it) }
        if (effort > 0) put("e", effort)
    }

    companion object {
        fun fromJson(o: JSONObject) = WorkoutSession(
            o.optString("k", "other"), o.optInt("m"), if (o.has("s")) o.getLong("s") else null,
            o.optString("t"), o.optString("src"), o.optString("n"),
            if (o.has("hr")) o.getInt("hr") else null, if (o.has("hx")) o.getInt("hx") else null,
            o.optJSONObject("h")?.let { h -> h.keys().asSequence().mapNotNull { k -> k.toIntOrNull()?.let { it to h.getInt(k) } }.toMap() } ?: emptyMap(),
            if (o.has("d")) o.getInt("d") else null, o.optInt("e"),
        )
    }
}

// =====================================================================
// Блок «Тренировки» на экране дня
// =====================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkoutBlock(
    item: TemplateItem, day: Map<String, String>, countWalking: Boolean,
    onManualSessions: (List<WorkoutSession>) -> Unit,
    onOverride: (String?) -> Unit, onRevert: () -> Unit,
    onCountWalking: (Boolean) -> Unit,
) {
    val synced = Workouts.parse(day[Workouts.syncedKey(item.id)])
    val manualList = Workouts.parse(day[Workouts.manualKey(item.id)])
    val overridden = day[manualKey(item.id)] != null
    val value = parseNumber(day[item.id])
    var adding by remember { mutableStateOf(false) }
    var editTotal by remember { mutableStateOf(false) }

    SectionCard(item.displayName, watch = item.source != Source.NONE, action = {
        if (overridden) TextButton(onClick = onRevert) { Text(t("Вернуть")) }
        if (!adding && !editTotal) IconButton(onClick = { editTotal = true }) { Icon(Icons.Filled.Edit, t("Исправить минуты")) }
    }) {
        if (editTotal) {
            var draft by remember { mutableStateOf(day[item.id]) }
            Text(t("Сколько минут засчитать за день"), style = MaterialTheme.typography.labelLarge)
            ItemEditor(item.copy(type = ItemType.NUMBER), draft, onAddOption = {}) { draft = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                OutlinedButton(onClick = { editTotal = false }) { Text(t("Отмена")) }
                Button(onClick = { onOverride(draft); editTotal = false }) { Text(t("Сохранить")) }
            }
            return@SectionCard
        }
        // итог
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value?.let { t("%s мин", formatNumber(Math.round(it).toDouble())) } ?: "—",
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Text(if (overridden) t("исправлено вручную") else t("в зачёт за день"), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
        }
        // список тренировок
        val (merged, absorbed) = Workouts.merge(synced, manualList)
        val all = merged.take(synced.size).map { it to false } + manualList.filterIndexed { i, _ -> i !in absorbed }.map { it to true }
        if (all.isNotEmpty()) Spacer(Modifier.height(6.dp))
        all.forEach { (s, isManual) ->
            val k = Workouts.kind(s.kind)
            val counts = Workouts.counts(s, countWalking)
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(
                    if (counts) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center) {
                    Icon(k.icon, null, Modifier.size(20.dp),
                        tint = if (counts) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.outline)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.title.ifBlank { k.title } + if (s.note.isNotBlank()) " · " + s.note else "",
                        style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (counts) Color.Unspecified else MaterialTheme.colorScheme.outline)
                    Text(
                        listOfNotNull(
                            s.start?.let { Workouts.clock(it) + "–" + Workouts.clock(it + s.min * 60_000L) },
                            if (isManual) t("вручную") else AppNames.from(s.src) ?: t("из Health Connect"),
                            if (!counts) t("не в зачёт") else null,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(t("%d мин", s.min), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                    color = if (counts) Color.Unspecified else MaterialTheme.colorScheme.outline)
                if (isManual) IconButton(onClick = { onManualSessions(manualList - s) }) { Icon(Icons.Filled.Close, t("Удалить")) }
            }
        }
        if (all.any { it.first.kind == "walk" }) Row(
            Modifier.fillMaxWidth().clickable { onCountWalking(!countWalking) }.padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(t("Засчитывать ходьбу"), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(countWalking, onCountWalking)
        }
        if (synced.isEmpty() && value == null && !adding) Text(
            t("С часами тренировки подтянутся сами — с видом и временем. Без синхронизации добавьте вручную."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        if (adding) WorkoutEditor(onCancel = { adding = false }) { s -> onManualSessions(manualList + s); adding = false }
        else OutlinedButton(onClick = { adding = true }, Modifier.padding(top = 4.dp)) {
            Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Добавить тренировку"))
        }
    }
}

/** Ручная тренировка: вид (плотные чипы), минуты, что делали. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkoutEditor(onCancel: () -> Unit, onSave: (WorkoutSession) -> Unit) {
    var kind by remember { mutableStateOf("strength") }
    var min by remember { mutableIntStateOf(30) }
    var note by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(t("Вид тренировки"), style = MaterialTheme.typography.labelLarge)
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Workouts.kinds.forEach { k ->
                    FilterChip(selected = kind == k.key, onClick = { kind = k.key }, label = { Text(k.title) },
                        leadingIcon = { Icon(k.icon, null, Modifier.size(16.dp)) })
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t("Минут"), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                listOf(-10, -5).forEach { d -> TextButton(onClick = { min = (min + d).coerceAtLeast(5) }) { Text("$d") } }
                Text("$min", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp))
                listOf(5, 10).forEach { d -> TextButton(onClick = { min = (min + d).coerceAtMost(600) }) { Text("+$d") } }
            }
        }
        OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text(t("Что делали: ноги, 5 км…")) })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel) { Text(t("Отмена")) }
            Button(onClick = { Log.i("Workout", "вручную: $kind $min мин"); onSave(WorkoutSession(kind, min, note = note.trim())) }) { Text(t("Добавить")) }
        }
    }
}

// =====================================================================
// Пульс покоя: как замерить вручную
// =====================================================================

@Composable
fun RestingHrHint() {
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)).clickable { open = !open }.padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.FavoriteBorder, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(8.dp))
            Text(t("Как замерить пульс покоя без часов"), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer)
            Icon(Icons.Outlined.Info, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        if (open) Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(
                t("Утром, сразу после пробуждения: до кофе, душа и телефона."),
                t("Полежите или посидите спокойно 5 минут, не разговаривайте."),
                t("Найдите пульс: двумя пальцами (не большим) на запястье под большим пальцем или на шее сбоку от гортани."),
                t("Считайте удары 30 секунд по секундомеру и умножьте на 2 (или считайте полную минуту)."),
                t("Повторите ещё раз через минуту и запишите меньшее значение."),
            ).forEachIndexed { i, s -> Text("${i + 1}. $s", style = MaterialTheme.typography.bodySmall) }
            Text(t("У взрослых обычно 60–80 уд/мин, у тренированных людей — 40–60. Важнее не одно число, а как оно меняется: рост на 5–10 уд/мин несколько дней подряд часто говорит о недосыпе, перегрузке или начале болезни."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
