package org.maittaa.tabita

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.SportsGymnastics
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.health.connect.client.PermissionController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maittaa.tabita.train.SChart
import org.maittaa.tabita.train.SeriesChart
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 5.51: аэробная нагрузка во вкладке «Здоровье». [tab]: 0 — занятия (старт, неделя, зоны, последние),
 * 1 — прогресс, 2 — история. Что видно, зависит от Health Connect (см. [CardioData]).
 */

/**
 * Что известно о данных: подключён ли Health Connect, есть ли доступ к тренировкам и к пульсу.
 * Без часов — ручная запись и таймер, интенсивность по тесту разговором; зоны пульса скрыты.
 * С тренировками без пульса — занятия подтягиваются, но зон и активных минут по пульсу нет: просим доступ.
 */
data class CardioData(val sdk: Boolean, val exercise: Boolean, val heart: Boolean, val distance: Boolean) {
    val watch get() = sdk && exercise
    companion object {
        val NONE = CardioData(false, false, false, false)
        /** Только для тестов: подставить состояние Health Connect. */
        @androidx.annotation.VisibleForTesting var override: CardioData? = null
    }
}

@Composable
fun CardioScreen(act: MainActivity, tab: Int, modifier: Modifier) {
    val repo = act.repo; val prefs = act.prefs
    val entries = collect(repo.entries)
    val profile = collect(repo.profile)
    var refresh by remember { mutableIntStateOf(0) }
    var data by remember { mutableStateOf<CardioData?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(refresh) {
        data = CardioData.override ?: runCatching {
            val g = act.health.granted()
            CardioData(act.health.available, act.health.exercisePermission in g, act.health.hrPermission in g, act.health.distPermission in g)
        }.getOrDefault(CardioData.NONE)
    }
    val perm = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { res ->
        Log.i("Cardio", "доступ к пульсу и тренировкам: $res")
        refresh++
        if (res.isNotEmpty()) scope.launch { act.syncHealth(LocalDate.now().minusDays(29), LocalDate.now()); refresh++ }
    }
    val askAccess: () -> Unit = {
        runCatching { perm.launch(setOf(act.health.exercisePermission, act.health.hrPermission, act.health.distPermission)) }
            .onFailure { Log.e("Cardio", "запрос доступа", it) }
    }
    var hrMax by remember { mutableIntStateOf(prefs.hrMax) }
    val today = LocalDate.now()
    val zones = remember(entries, profile, hrMax) { HrZones.of(profile, hrMax, HrZones.latestRest(repo.template.value, entries, today), today) }
    val d = data ?: CardioData.NONE
    var draft by remember(tab) { mutableStateOf(CardioDraft.parse(prefs.cardioDraft)) }
    val saveDraft: (CardioDraft?) -> Unit = { draft = it; prefs.cardioDraft = it?.toJson() }
    var detail by remember { mutableStateOf<Cardio.Item?>(null) }

    val list = remember(entries) { Cardio.sessions(repo, today.minusDays(90), today) }

    Box(modifier.fillMaxSize()) {
        val dr = draft?.takeIf { !it.yoga }
        when {
            tab == 0 && dr != null -> CardioTimer(act, dr, d, saveDraft)
            tab == 0 -> CardioHome(act, d, zones, list, askAccess, onHrMax = { hrMax = it; prefs.hrMax = it }, onStart = saveDraft) { detail = it }
            else -> CardioProgress(list, zones)
        }
    }
    detail?.let { item -> SessionDetail(act, item, zones) { detail = null } }
}

/** 5.53: подробности занятия (аэробика, йога) с удалением записанных вручную — для разделов и общей истории. */
@Composable
fun SessionDetail(act: MainActivity, item: Cardio.Item, zones: HrZones.Zones?, onDismiss: () -> Unit) {
    val repo = act.repo
    CardioDetail(item, zones, onDismiss = onDismiss, onDelete = item.manualIndex?.let { mi -> {
        val tItem = repo.template.value.firstOrNull { it.id == item.itemId }
        if (tItem != null) {
            val manual = Workouts.parse(repo.entries.value[item.date.key()]?.get(Workouts.manualKey(tItem.id)))
            repo.setWorkouts(item.date.key(), tItem, manual.filterIndexed { i, _ -> i != mi })
            Log.i("Cardio", "удалено занятие ${item.date} #$mi")
        }
        onDismiss()
    } })
}

// =====================================================================
// Главная: старт, неделя, зоны, последние занятия
// =====================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardioHome(
    act: MainActivity, d: CardioData, zones: HrZones.Zones?, list: List<Cardio.Item>, askAccess: () -> Unit,
    onHrMax: (Int) -> Unit, onStart: (CardioDraft) -> Unit, onOpen: (Cardio.Item) -> Unit,
) {
    val prefs = act.prefs
    var manual by remember { mutableStateOf(false) }
    var setup by remember { mutableStateOf<CardioPlans.Mode?>(null) }
    LazyColumn(Modifier.fillMaxSize().testTag("cardio_home"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!(d.sdk && d.exercise && d.heart)) item { SourceCard(act, d, askAccess) }
        item { WeekCard(list, zones, d) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(t("Начать занятие"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    StartRow(Icons.Filled.SportsGymnastics, t("Аэробика дома"), t("Разминка, движения по минуте, заминка — таймер ведёт сам")) { setup = CardioPlans.Mode.HOME }
                    StartRow(Icons.Filled.Whatshot, t("Интервалы"), t("Работа и отдых по кругу, звук на смене")) { setup = CardioPlans.Mode.INTERVALS }
                    StartRow(Icons.Filled.OndemandVideo, t("По видео"), t("Только секундомер — занимаетесь по своему видео")) { setup = CardioPlans.Mode.VIDEO }
                    if (d.watch) Text(t("Бег, ходьбу, велосипед и другие занятия, записанные часами, приложение подхватит само."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton({ manual = !manual }, Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Записать занятие вручную"))
                    }
                    if (manual) CardioEditor(act, d, zones, onDone = { manual = false })
                }
            }
        }
        if (d.heart && zones != null) item { ZonesCard(zones, onHrMax) }
        item {
            Text(t("Последние занятия"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        if (list.isEmpty()) item {
            Text(if (d.watch) t("Пока пусто. Начните занятие здесь или запишите тренировку часами — она появится после синхронизации.")
                else t("Пока пусто. Начните занятие с таймером или запишите вручную."),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(list.take(10)) { CardioRow(it, zones) { onOpen(it) } }
    }
    setup?.let { mode -> TimerSetup(prefs, mode, onDismiss = { setup = null }) { dr -> setup = null; onStart(dr); Log.i("Cardio", "старт: ${dr.mode}") } }
}

@Composable
private fun StartRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.PlayArrow, t("Начать"))
    }
}

/** Откуда данные и чего не хватает. Нет карточки — когда всё подключено. */
@Composable
private fun SourceCard(act: MainActivity, d: CardioData, askAccess: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val (title, text, button) = when {
        !d.sdk -> Triple(t("Без часов"), t("Health Connect на телефоне нет. Занятия записываются таймером или вручную, интенсивность — по тесту разговором: после занятия отметьте, могли ли вы говорить. По нему считаются активные минуты."), null)
        !d.exercise -> Triple(t("Без часов"), t("Health Connect не подключён. Занятия записываются таймером или вручную, интенсивность — по тесту разговором. Подключите — и тренировки с часов будут приходить сами, с пульсом и дистанцией."), t("Подключить"))
        !d.heart -> Triple(t("Нет доступа к пульсу"), t("Тренировки с часов приходят, но без пульса: нет зон и активных минут по пульсу. Дайте доступ к пульсу и дистанции."), t("Дать доступ"))
        else -> return
    }
    Card(colors = CardDefaults.cardColors(containerColor = cs.tertiaryContainer, contentColor = cs.onTertiaryContainer), modifier = Modifier.testTag("cardio_source")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Info, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Text(text, style = MaterialTheme.typography.bodySmall)
            if (button != null) TextButton(onClick = askAccess) {
                Text(button, fontWeight = FontWeight.SemiBold, color = cs.onTertiaryContainer)
            }
        }
    }
}

/** Неделя: минуты аэробной нагрузки, активные минуты против нормы ВОЗ. */
@Composable
private fun WeekCard(list: List<Cardio.Item>, zones: HrZones.Zones?, d: CardioData) {
    val wk = Cardio.week(list, Cardio.weekStart(LocalDate.now()), zones)
    Card(Modifier.fillMaxWidth().testTag("cardio_week")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(t("Эта неделя"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(if (wk.activeKnown) "${wk.active}" else "${wk.minutes}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Text(if (wk.activeKnown) t("активных мин из %d", Cardio.WEEK_MIN) else t("мин аэробной нагрузки"),
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 4.dp))
            }
            if (wk.activeKnown) LinearProgressIndicator({ (wk.active / Cardio.WEEK_MIN.toFloat()).coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
            Text(listOf(t("занятий: %d", wk.count), t("всего %d мин", wk.minutes)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(when {
                wk.activeKnown && d.heart -> t("Активные минуты — по пульсу с часов: умеренная зона ×1, высокая и пиковая ×2. Норма ВОЗ — 150–300 в неделю.")
                wk.activeKnown -> t("Активные минуты — по ощущению после занятия: умеренно ×1, интенсивно ×2. Норма ВОЗ — 150–300 в неделю.")
                else -> t("Отмечайте после занятия, могли ли вы говорить, — тогда посчитаются активные минуты (норма ВОЗ — 150–300 в неделю).")
            }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Пульсовые зоны — как в Google Health; максимум можно задать свой. */
@Composable
private fun ZonesCard(z: HrZones.Zones, onHrMax: (Int) -> Unit) {
    var edit by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().testTag("cardio_zones")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("Пульсовые зоны"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                TextButton({ edit = true }) { Text(t("Максимум")) }
            }
            HrZones.names.zip(z.ranges()).forEachIndexed { i, (n, r) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(zoneColor(i)))
                    Spacer(Modifier.width(8.dp))
                    Text(n, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(r + " " + t("уд/мин"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            Text(
                (if (z.maxAuto) t("Максимум %d = 220 − возраст", z.max) else t("Максимум %d — задан вами", z.max)) + ". " +
                    (if (z.byReserve) t("По резерву пульса, как в Google Health: пульс покоя %d (%s).", z.rest!!, z.restDate!!.ruShort())
                    else t("Пульса покоя за неделю нет — зоны от максимума: 50, 70 и 85 %.")),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (edit) {
        var v by remember { mutableIntStateOf(z.max) }
        AlertDialog(
            onDismissRequest = { edit = false },
            title = { Text(t("Максимальный пульс")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(t("Если знаете свой максимум (например, по тесту или по часам), задайте его — зоны пересчитаются. Иначе — 220 − возраст."),
                        style = MaterialTheme.typography.bodySmall)
                    Stepper(v, { v = it.coerceIn(120, 230) }, step = 1, big = 5, unit = t("уд/мин"))
                }
            },
            confirmButton = { TextButton({ onHrMax(v); edit = false }) { Text(t("Сохранить")) } },
            dismissButton = { TextButton({ onHrMax(0); edit = false }) { Text(t("По возрасту")) } },
        )
    }
}

@Composable
fun zoneColor(i: Int): Color = when (i) {
    0 -> Color(0xFF29B6F6); 1 -> Color(0xFF66BB6A); 2 -> Color(0xFFFFB300); else -> Color(0xFFEF5350)
}

/** Счётчик без поля ввода (поле в диалоге тормозит тесты и неудобно на ходу). */
@Composable
fun Stepper(v: Int, onChange: (Int) -> Unit, step: Int, big: Int, unit: String, text: String = "$v") {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton({ onChange(v - big) }) { Text("−$big") }
            TextButton({ onChange(v - step) }) { Text("−$step") }
            Text("$text $unit", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            TextButton({ onChange(v + step) }) { Text("+$step") }
            TextButton({ onChange(v + big) }) { Text("+$big") }
        }
    }
}

// =====================================================================
// Строка занятия и подробности
// =====================================================================

@Composable
fun CardioRow(it: Cardio.Item, zones: HrZones.Zones?, onClick: () -> Unit) {
    val s = it.s
    val k = Workouts.kind(s.kind)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Icon(k.icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(s.title.ifBlank { k.title }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(
                it.date.ruShort() + (s.start?.let { st -> " " + Cardio.clock(st) } ?: ""),
                s.dist?.takeIf { s.kind in distKinds }?.let { m -> Cardio.distText(m) },
                s.hr?.let { h -> t("%d уд/мин", h) },
                Cardio.active(s, zones)?.takeIf { a -> a > 0 }?.let { a -> t("%d акт. мин", a) },
                sourceText(s, it.manualIndex != null),
            ).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(t("%d мин", s.min), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

private val distKinds = setOf("run", "walk", "hike", "bike", "swim", "winter")

private fun sourceText(s: WorkoutSession, manual: Boolean): String = when {
    manual -> t("вручную")
    else -> AppNames.from(s.src) ?: t("из Health Connect")
}

@Composable
private fun CardioDetail(item: Cardio.Item, zones: HrZones.Zones?, onDismiss: () -> Unit, onDelete: (() -> Unit)?) {
    val s = item.s
    val k = Workouts.kind(s.kind)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.title.ifBlank { k.title }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.date.ruTitle() + (s.start?.let { " · " + Cardio.clock(it) + "–" + Cardio.clock(it + s.min * 60_000L) } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Line(t("Длительность"), t("%d мин", s.min))
                s.dist?.takeIf { s.kind in distKinds }?.let { Line(t("Дистанция"), Cardio.distText(it)) }
                Cardio.pace(s)?.let { Line(t("Темп"), Cardio.paceText(it)) }
                s.hr?.let { Line(t("Средний пульс"), t("%d уд/мин", it)) }
                s.hrMax?.let { Line(t("Максимум"), t("%d уд/мин", it)) }
                if (s.effort > 0) Line(t("Ощущение"), Cardio.effortNames[s.effort - 1])
                Cardio.active(s, zones)?.let { Line(t("Активные минуты"), "$it") }
                if (s.hist.isNotEmpty() && zones != null) {
                    val m = HrZones.minutes(s.hist, zones)
                    val total = m.sum().coerceAtLeast(1)
                    Text(t("Время в зонах"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                    HrZones.names.forEachIndexed { i, n ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(n, Modifier.width(92.dp), style = MaterialTheme.typography.bodySmall)
                            Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                                Box(Modifier.fillMaxHeight().fillMaxWidth(m[i] / total.toFloat()).background(zoneColor(i)))
                            }
                            Text(t("%d мин", m[i]), Modifier.width(56.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else if (s.hr == null && s.start != null && item.manualIndex != null && Cardio.isCardio(s))
                    Text(t("Пульс подтянется с часов после синхронизации, если они его записали."), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (s.note.isNotBlank()) Text(s.note, style = MaterialTheme.typography.bodySmall)
                Text(sourceText(s, item.manualIndex != null), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onDismiss) { Text(t("Закрыть")) } },
        dismissButton = onDelete?.let { del -> { TextButton(del) { Text(t("Удалить"), color = MaterialTheme.colorScheme.error) } } },
    )
}

@Composable
private fun Line(label: String, value: String) = Row {
    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
}

// =====================================================================
// Запись вручную
// =====================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardioEditor(act: MainActivity, d: CardioData, zones: HrZones.Zones?, onDone: () -> Unit) {
    val now = java.time.LocalTime.now()
    var kind by remember { mutableStateOf("aerobics") }
    var dayBack by remember { mutableIntStateOf(0) }
    var min by remember { mutableIntStateOf(30) }
    var startMin by remember { mutableIntStateOf(((now.hour * 60 + now.minute - 30) / 5 * 5).coerceAtLeast(0)) }
    var distOn by remember { mutableStateOf(false) }
    var dist by remember { mutableIntStateOf(3000) }
    var hrOn by remember { mutableStateOf(false) }
    var hr by remember { mutableIntStateOf(120) }
    var effort by remember { mutableIntStateOf(0) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(10.dp)
            .testTag("cardio_editor"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Cardio.kinds.forEach { key ->
                    val k = Workouts.kind(key)
                    FilterChip(kind == key, { kind = key }, label = { Text(k.title) }, leadingIcon = { Icon(k.icon, null, Modifier.size(16.dp)) })
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(t("Сегодня"), t("Вчера"), t("Позавчера")).forEachIndexed { i, n -> FilterChip(dayBack == i, { dayBack = i }, label = { Text(n) }) }
            }
        }
        Text(t("Начало"), style = MaterialTheme.typography.labelLarge)
        Stepper(startMin, { startMin = ((it % 1440) + 1440) % 1440 }, step = 5, big = 60, unit = "", text = clockOf(startMin))
        Text(t("Длительность"), style = MaterialTheme.typography.labelLarge)
        Stepper(min, { min = it.coerceIn(5, 600) }, step = 5, big = 15, unit = t("мин"))
        if (kind in distKinds) {
            ToggleRow(t("Дистанция"), distOn) { distOn = it }
            if (distOn) Stepper(dist, { dist = it.coerceIn(100, 200_000) }, step = 100, big = 1000, unit = "", text = Cardio.distText(dist))
        }
        // без часов можно записать средний пульс, если мерили; с часами он подтянется сам
        if (!d.heart) {
            ToggleRow(t("Средний пульс (если мерили)"), hrOn) { hrOn = it }
            if (hrOn) {
                Stepper(hr, { hr = it.coerceIn(50, 220) }, step = 1, big = 10, unit = t("уд/мин"))
                zones?.let { z -> Text(t("Зона: %s", HrZones.names[z.zone(hr)].lowercase()), style = MaterialTheme.typography.bodySmall) }
            }
        }
        EffortChips(effort, optional = d.heart) { effort = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onDone) { Text(t("Отмена")) }
            Button({
                val date = LocalDate.now().minusDays(dayBack.toLong())
                val start = date.atStartOfDay(java.time.ZoneId.systemDefault()).plusMinutes(startMin.toLong()).toInstant().toEpochMilli()
                val s = WorkoutSession(kind, min, start, effort = effort, hr = if (hrOn && !d.heart) hr else null,
                    dist = if (distOn && kind in distKinds) dist else null)
                saveSession(act, date, s)
                onDone()
            }, Modifier.testTag("cardio_save")) { Text(t("Сохранить")) }
        }
    }
}

@Composable
fun ToggleRow(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!on) }, verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        Switch(on, onChange)
    }
}

/** Тест разговором (CDC): петь — легко, говорить, но не петь — умеренно, пара слов — интенсивно. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EffortChips(effort: Int, optional: Boolean, onChange: (Int) -> Unit) {
    Text(if (optional) t("Как ощущалось (по желанию — пульс подтянется с часов)") else t("Как ощущалось — по нему посчитаются активные минуты"),
        style = MaterialTheme.typography.labelLarge)
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Cardio.effortNames.forEachIndexed { i, n -> FilterChip(effort == i + 1, { onChange(if (effort == i + 1) 0 else i + 1) }, label = { Text(n) }) }
        }
    }
}

/** Сохранить занятие в пункт «Тренировки» дня и попросить пульс с часов. */
fun saveSession(act: MainActivity, date: LocalDate, s: WorkoutSession) {
    val item = act.repo.exerciseItem()
    val manual = Workouts.parse(act.repo.entries.value[date.key()]?.get(Workouts.manualKey(item.id)))
    act.repo.setWorkouts(date.key(), item, manual + s)
    Log.i("Cardio", "занятие ${s.kind} ${s.min} мин, ощущение ${s.effort}, $date")
    Hydration.updateNorms(act.repo, act.prefs, listOf(date.key()))
    act.lifecycleScope.launch { runCatching { act.syncHealth(date, date) } }
}

// =====================================================================
// Таймер
// =====================================================================

@Composable
private fun TimerSetup(prefs: Prefs, mode: CardioPlans.Mode, onDismiss: () -> Unit, onStart: (CardioDraft) -> Unit) {
    var quiet by remember { mutableStateOf(prefs.cardioQuiet) }
    var main by remember { mutableIntStateOf(prefs.cardioMain) }
    var work by remember { mutableIntStateOf(prefs.cardioWork) }
    var rest by remember { mutableIntStateOf(prefs.cardioRest) }
    var rounds by remember { mutableIntStateOf(prefs.cardioRounds) }
    val probe = CardioDraft(mode, 0, quiet = quiet, mainMin = main, work = work, rest = rest, rounds = rounds)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(probe.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (mode) {
                    CardioPlans.Mode.HOME -> {
                        Text(t("Основная часть"), style = MaterialTheme.typography.labelLarge)
                        Stepper(main, { main = it.coerceIn(5, 45) }, step = 5, big = 10, unit = t("мин"))
                    }
                    CardioPlans.Mode.INTERVALS -> {
                        Text(t("Работа"), style = MaterialTheme.typography.labelLarge)
                        Stepper(work, { work = it.coerceIn(10, 120) }, step = 5, big = 15, unit = t("с"))
                        Text(t("Отдых"), style = MaterialTheme.typography.labelLarge)
                        Stepper(rest, { rest = it.coerceIn(0, 120) }, step = 5, big = 15, unit = t("с"))
                        Text(t("Раундов"), style = MaterialTheme.typography.labelLarge)
                        Stepper(rounds, { rounds = it.coerceIn(2, 30) }, step = 1, big = 5, unit = "")
                    }
                    CardioPlans.Mode.VIDEO -> Text(t("Секундомер с паузой. Если на часах запущена тренировка, пульс и время совпадут — запись не задвоится."),
                        style = MaterialTheme.typography.bodySmall)
                    CardioPlans.Mode.YOGA -> {}
                }
                if (mode != CardioPlans.Mode.VIDEO) {
                    ToggleRow(t("Тихо для соседей — без прыжков"), quiet) { quiet = it }
                    Text(t("Всего %d мин: разминка 5, заминка 5.", (CardioPlans.totalSec(probe.blocks) + 59) / 60),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton({
                prefs.cardioQuiet = quiet; prefs.cardioMain = main; prefs.cardioWork = work; prefs.cardioRest = rest; prefs.cardioRounds = rounds
                onStart(probe.copy(startedAt = System.currentTimeMillis()))
            }, Modifier.testTag("cardio_go")) { Text(t("Начать")) }
        },
        dismissButton = { TextButton(onDismiss) { Text(t("Отмена")) } },
    )
}

private fun mmss(sec: Long): String = "%d:%02d".format(sec / 60, sec % 60)

@Composable
fun CardioTimer(act: MainActivity, dr: CardioDraft, d: CardioData, save: (CardioDraft?) -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var finishing by remember { mutableStateOf(false) }
    val view = LocalView.current
    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80) }.getOrNull() }
    DisposableEffect(Unit) { onDispose { tone?.release() } }
    val vib = remember { act.getSystemService(Vibrator::class.java) }
    var lastIdx by remember { mutableIntStateOf(-1) }
    var lastBeep by remember { mutableLongStateOf(-1) }
    LaunchedEffect(dr, finishing) {
        while (!finishing) { now = System.currentTimeMillis(); delay(250) }
    }
    val planSec = dr.planSec(now)
    val cur = dr.current(planSec)
    val blocks = remember(dr.mode, dr.quiet, dr.mainMin, dr.work, dr.rest, dr.rounds) { dr.blocks }
    // звук: длинный сигнал на смене блока, короткие — за 3 секунды до конца
    LaunchedEffect(cur?.first, dr.paused) {
        val idx = cur?.first ?: blocks.size
        if (!dr.paused && dr.mode != CardioPlans.Mode.VIDEO && lastIdx >= 0 && idx != lastIdx) {
            // смена блока — звук из настроек («Простой сигнал» — писк, как раньше)
            if (Bells.isSimple(act.prefs.bell)) runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 400) } else Bells.play(act)
            runCatching { vib?.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE)) }
        }
        lastIdx = idx
    }
    LaunchedEffect(planSec) {
        val left = cur?.let { it.second.sec - it.third } ?: return@LaunchedEffect
        if (!dr.paused && left in 1..3 && planSec != lastBeep) { lastBeep = planSec; runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 120) } }
    }
    val done = dr.mode != CardioPlans.Mode.VIDEO && cur == null
    LaunchedEffect(done) { if (done) finishing = true }
    val elapsed = dr.elapsedMs(now) / 1000
    Column(Modifier.fillMaxSize().padding(20.dp).testTag("cardio_timer"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(dr.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (dr.mode == CardioPlans.Mode.VIDEO || cur == null) {
            Spacer(Modifier.height(24.dp))
            Text(mmss(elapsed), fontSize = 72.sp, fontWeight = FontWeight.Bold)
            Text(if (dr.paused) t("Пауза") else t("Идёт занятие"), style = MaterialTheme.typography.bodyMedium)
        } else {
            val (idx, b, into) = cur
            val phase = when (b.phase) { 0 -> t("Разминка"); 1 -> t("Работа"); 2 -> t("Отдых"); 3 -> t("Заминка"); else -> t("Поза") }
            val total = CardioPlans.totalSec(blocks)
            LinearProgressIndicator({ (planSec / total.toFloat()).coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
            Text(phase + " · " + t("%d из %d", idx + 1, blocks.size), style = MaterialTheme.typography.labelLarge,
                color = if (b.phase == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(b.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            if (b.hint.isNotBlank()) Text(b.hint, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(mmss(b.sec - into), fontSize = 80.sp, fontWeight = FontWeight.Bold)
            blocks.getOrNull(idx + 1)?.let { n -> Text(t("Дальше: %s", n.name), style = MaterialTheme.typography.bodyMedium) }
            Text(t("Прошло %s · осталось %s", mmss(elapsed), mmss((total - planSec).coerceAtLeast(0))), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (dr.yoga) Text(t("Тянитесь до лёгкого натяжения, не до боли. Больно — нажмите «Дальше»."), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        else if (!d.heart) Text(t("Пульс без часов: можно замерить в конце — 15 секунд × 4."), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        else Text(t("Пульс за это время возьмём с часов после синхронизации."), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ save(if (dr.paused) dr.resume() else dr.pause()) }, Modifier.weight(1f)) {
                Icon(if (dr.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, null); Spacer(Modifier.width(6.dp))
                Text(if (dr.paused) t("Продолжить") else t("Пауза"))
            }
            if (dr.mode != CardioPlans.Mode.VIDEO && cur != null) OutlinedButton({ save(dr.skip()) }) { Icon(Icons.Filled.SkipNext, t("Дальше")) }
        }
        OutlinedButton({ save(dr.pause()); finishing = true }, Modifier.fillMaxWidth().testTag("cardio_finish")) { Text(t("Закончить")) }
    }
    if (finishing) FinishDialog(act, dr, d, onCancel = { finishing = false; save(dr.resume()) }, onDiscard = { save(null); finishing = false }) { save(null); finishing = false }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FinishDialog(act: MainActivity, dr: CardioDraft, d: CardioData, onCancel: () -> Unit, onDiscard: () -> Unit, onSaved: () -> Unit) {
    val sec = dr.elapsedMs() / 1000
    val min = Math.round(sec / 60.0).toInt()
    var effort by remember { mutableIntStateOf(0) }
    var kind by remember { mutableStateOf(dr.kind) }
    var hrOn by remember { mutableStateOf(false) }
    var hr by remember { mutableIntStateOf(120) }
    AlertDialog(
        onDismissRequest = {},
        title = { Text(if (min < 1) t("Меньше минуты") else t("Занятие: %d мин", min)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (min < 1) Text(t("Такое короткое занятие не сохранится."))
                else {
                    if (dr.mode == CardioPlans.Mode.VIDEO) CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("aerobics", "dance", "hiit", "cardio").forEach { key ->
                                FilterChip(kind == key, { kind = key }, label = { Text(Workouts.kind(key).title) })
                            }
                        }
                    }
                    if (!dr.yoga) EffortChips(effort, optional = d.heart) { effort = it }
                    if (!d.heart && !dr.yoga) {
                        ToggleRow(t("Пульс в конце (если мерили)"), hrOn) { hrOn = it }
                        if (hrOn) Stepper(hr, { hr = it.coerceIn(50, 220) }, step = 1, big = 10, unit = t("уд/мин"))
                    }
                }
            }
        },
        confirmButton = {
            if (min >= 1) TextButton({
                val s = WorkoutSession(kind, min, dr.startedAt, title = dr.title, effort = effort, hr = if (hrOn && !d.heart) hr else null)
                val date = java.time.Instant.ofEpochMilli(dr.startedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                saveSession(act, date, s)
                onSaved()
            }, Modifier.testTag("cardio_finish_save")) { Text(t("Сохранить")) }
            else TextButton(onDiscard) { Text(t("Выйти")) }
        },
        dismissButton = {
            Row {
                if (min >= 1) TextButton(onDiscard) { Text(t("Не сохранять"), color = MaterialTheme.colorScheme.error) }
                TextButton(onCancel) { Text(t("Продолжить")) }
            }
        },
    )
}

// =====================================================================
// Прогресс и история
// =====================================================================

@Composable
private fun CardioProgress(list: List<Cardio.Item>, zones: HrZones.Zones?) {
    val start = Cardio.weekStart(LocalDate.now())
    val weeks = (11 downTo 0).map { Cardio.week(list, start.minusWeeks(it.toLong()), zones) }
    val home = list.filter { it.s.kind == "aerobics" && it.s.hr != null }.reversed()
    val walk = list.filter { it.s.kind == "walk" && Cardio.pace(it.s) != null }.reversed()
    val run = list.filter { it.s.kind == "run" && Cardio.pace(it.s) != null }.reversed()
    LazyColumn(Modifier.fillMaxSize().testTag("cardio_progress"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ChartCard(t("Аэробная нагрузка по неделям, мин"), t("Все занятия на выносливость за 12 недель.")) {
                SeriesChart(weeks.map { it.start to it.minutes.toDouble() }, SChart.BARS, { formatNumber(it) })
            }
        }
        if (weeks.any { it.activeKnown }) item {
            ChartCard(t("Активные минуты по неделям"), t("Линия — норма ВОЗ 150 в неделю. Интенсивная минута — за две.")) {
                SeriesChart(weeks.map { it.start to it.active.toDouble() }, SChart.BARS, { formatNumber(it) }, target = Cardio.WEEK_MIN.toDouble())
            }
        }
        if (home.size >= 2) item {
            ChartCard(t("Пульс на аэробике дома"), t("Средний пульс за занятие. На той же нагрузке он со временем ниже — выносливость растёт.")) {
                SeriesChart(home.map { it.date to it.s.hr!!.toDouble() }, SChart.LINE, { formatNumber(it) })
            }
        }
        listOf(t("Темп ходьбы") to walk, t("Темп бега") to run).forEach { (title, l) ->
            if (l.size >= 2) item {
                ChartCard(title, t("Минут на километр: ниже — быстрее.")) {
                    SeriesChart(l.map { it.date to Cardio.pace(it.s)!! }, SChart.LINE, { Cardio.paceText(it) })
                }
            }
        }
        if (list.isEmpty()) item { Text(t("Графики появятся после первых занятий."), color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun ChartCard(title: String, note: String, chart: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            chart()
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
