package org.maittaa.tabita.train

import androidx.compose.material.icons.outlined.AutoAwesome
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(m: AppModel, a: Actions, showInfo: (String, String?) -> Unit) {
    val d = m.draft ?: return
    val plan = d.plan
    val ctx = LocalContext.current
    KeepScreenOn()
    val steps = remember(plan) { m.steps(plan) }
    val idx = d.step.coerceIn(0, steps.lastIndex)
    val step = steps[idx]
    val timer by RestTimer.state.collectAsState()
    val resting = timer != null && step is Step.Set

    val back: () -> Unit = {
        when {
            resting -> { RestTimer.stop(ctx); m.goTo(idx - 1) }
            idx > 0 -> m.goTo(idx - 1)
            else -> m.screen = Screen.Home
        }
    }
    BackHandler(onBack = back)

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(plan.title)
                            ElapsedText(d)
                        }
                    },
                    actions = {
                        if (d.running) IconButton({ if (d.paused) m.resumeClock(true) else m.pauseClock(true) }) {
                            Icon(if (d.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, if (d.paused) "Продолжить" else "Пауза")
                        }
                        IconButton({ m.screen = Screen.Home }) { Icon(Icons.Outlined.Close, "На главный экран") }
                    },
                )
                LinearProgressIndicator(
                    progress = { idx.toFloat() / steps.lastIndex },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) { pad ->
        AnimatedContent(
            targetState = resting to idx,
            transitionSpec = {
                // 1.7: как перелистывание страниц — следующий шаг (в том числе отдых → подход) всегда въезжает справа,
                // а текущий целиком уходит влево. Справа налево наоборот — только при явном шаге назад (номер шага меньше).
                val back = targetState.second < initialState.second
                val dir = if (back) -1 else 1
                slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { w -> dir * w } togetherWith
                    slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { w -> -dir * w } using null
            },
            modifier = Modifier.padding(pad).fillMaxSize(),
            label = "step",
        ) { (isRest, i) ->
            val st = steps[i]
            when {
                isRest -> RestView(m, steps, i, back)
                st is Step.Prep -> PrepView(m, d, showInfo)
                st is Step.Warm -> WarmView(m, st, showInfo, back)
                st is Step.Set -> SetView(m, d, st, showInfo, back)
                else -> ResultsView(m, d, back)
            }
        }
    }
}

/** Общая раскладка шага: прокручиваемая середина и закреплённый низ с кнопками. */
@Composable
private fun StepLayout(
    middle: @Composable ColumnScope.() -> Unit,
    bottom: @Composable ColumnScope.() -> Unit,
    centered: Boolean = false,
) {
    Column(Modifier.fillMaxSize().imePadding()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = if (centered) Arrangement.spacedBy(14.dp, Alignment.CenterVertically) else Arrangement.spacedBy(14.dp),
            horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
            content = middle,
        )
        Surface(tonalElevation = 2.dp) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = bottom,
            )
        }
    }
}

// ——— 1. Подготовка ———

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrepView(m: AppModel, d: Draft, showInfo: (String, String?) -> Unit) {
    val plan = d.plan
    val totalSets = plan.exercises.sumOf { it.sets }
    val warm = plan.warmups.mapNotNull { Library.warmup(it) }
    val warmMin = warm.sumOf { if (it.seconds) it.amount else it.amount * 3 } / 60
    val estMin = (totalSets * (d.restSec + 45) / 60 + warmMin + 1)
    val r = m.readiness
    var replace by remember { mutableStateOf<Int?>(null) }
    val gymEq = m.gymEquipment
    val body = m.body
    StepLayout(
        middle = {
            Text("Подготовка", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "${plan.exercises.size} упражнений · $totalSets подходов · отдых ${d.restSec} с · примерно $estMin мин",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (r != null) ReadinessCard(r, onClick = { m.openTabitaData() })
            DeloadCard(m) { m.replan(plan.intensity, plan.noEquipment, plan.place) }
            if (m.gymEnabled) {
                Text("Где тренируешься", style = MaterialTheme.typography.titleSmall)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Place.entries.forEach { pl ->
                        FilterChip(plan.place == pl, { m.replan(plan.intensity, plan.noEquipment, pl) }, label = { Text(pl.title) }, enabled = !d.started)
                    }
                }
            }
            Text("Интенсивность", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // 1.7: умный режим — только когда есть данные Tabita; иначе выбор вручную
                if (m.smartAvailable) FilterChip(m.smartMode, { m.replanSmart(plan.noEquipment, plan.place) },
                    label = { Text("Умный режим") }, enabled = !d.started,
                    leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp)) })
                Intensity.entries.forEach { i ->
                    FilterChip(!(m.smartMode && m.smartAvailable) && plan.intensity == i, { m.replan(i, plan.noEquipment, plan.place, smart = false) },
                        label = { Text(i.title) }, enabled = !d.started)
                }
            }
            Text(
                if (m.smartMode && m.smartAvailable) "Умный режим: ${plan.intensity.title.lowercase()} — ${plan.intensity.hint}. Подобрано по данным дневника (причины — в карточке выше)."
                else plan.intensity.hint + if (!m.smartAvailable) ". Умный режим появится, когда в дневнике будут сон или пульс покоя." else "",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (plan.place == Place.HOME && m.program?.noEquipment != true) Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Сегодня без инвентаря")
                    Text("Упражнения с инвентарём заменятся вариантами со своим весом", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(plan.noEquipment, { m.replan(plan.intensity, it, plan.place) }, enabled = !d.started)
            }
            val notes = plan.notes + (m.program?.notes ?: emptyList())
            if (notes.isNotEmpty()) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Поправки", style = MaterialTheme.typography.titleSmall)
                    notes.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text("Разминка", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    Text(
                        "Включённые упражнения пойдут отдельными шагами перед подходами.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Library.warmups.filter { w ->
                        (w.needs == null || (plan.place == Place.GYM && w.needs in gymEq)) && !(w.impact && body.heavy)
                    }.forEach { w ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 4.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(w.name)
                                Text(w.amountLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton({ showInfo(w.name, w.cue) }) {
                                Icon(Icons.AutoMirrored.Outlined.HelpOutline, "Как делать", tint = MaterialTheme.colorScheme.primary)
                            }
                            Switch(w.id in plan.warmups, { m.toggleWarmupOn(w.id) })
                        }
                    }
                }
            }
            Text("План", style = MaterialTheme.typography.titleMedium)
            Text("Нажми на упражнение, чтобы заменить его.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            plan.exercises.forEachIndexed { i, ex ->
                Row(Modifier.fillMaxWidth().clickable { replace = i }.padding(vertical = 4.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(ex.name)
                        ex.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    Text(ex.scheme, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        bottom = {
            Button({ m.begin() }, Modifier.fillMaxWidth().height(56.dp)) {
                Text(if (d.running) "Вперёд" else "Начать", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
            }
        },
    )
    replace?.let { i -> ReplaceDialog(m, i) { replace = null } }
}

/** Замена упражнения по ходу: варианты того же паттерна с инвентарём текущего места. */
@Composable
fun ReplaceDialog(m: AppModel, exIndex: Int, onClose: () -> Unit) {
    val cur = m.draft?.plan?.exercises?.getOrNull(exIndex) ?: run { onClose(); return }
    val alts = remember(exIndex, m.draft?.plan) { m.alternatives(exIndex) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Заменить «${cur.name}»") },
        text = {
            if (!m.canReplace(exIndex)) Text("По этому упражнению уже есть сделанные подходы — замена недоступна.")
            else if (alts.isEmpty()) Text("Других вариантов с инвентарём ${if (m.draft?.plan?.place == Place.GYM) "зала" else "дома"} нет.")
            else LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(alts, key = { it.id }) { e ->
                    ExerciseRow(e, if (m.draft?.plan?.place == Place.GYM) m.gymEquipment else m.equipment) {
                        m.replaceExercise(exIndex, e); onClose()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClose) { Text("Отмена") } },
    )
}

// ——— разминка ———

@Composable
private fun WarmView(m: AppModel, step: Step.Warm, showInfo: (String, String?) -> Unit, back: () -> Unit) {
    val w = step.w
    val ctx = LocalContext.current
    var left by rememberSaveable(w.id) { mutableIntStateOf(w.amount) }
    var running by rememberSaveable(w.id) { mutableStateOf(false) }
    // после нуля: сигнал, галочка и переход дальше через 3 с (можно остаться)
    var autoNext by rememberSaveable(w.id) { mutableIntStateOf(-1) }
    LaunchedEffect(running, w.id) {
        while (running && left > 0) {
            delay(1000); left--
            if (left in 1..3) beep(ctx, short = true)
            if (left == 0) {
                running = false
                beep(ctx, short = false)
                ctx.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1))
                autoNext = 3
            }
        }
    }
    LaunchedEffect(autoNext) {
        if (autoNext > 0) { delay(1000); autoNext-- } else if (autoNext == 0) { autoNext = -1; m.completeWarm(w.id) }
    }
    StepLayout(
        centered = true,
        middle = {
            Text(
                "Разминка · ${step.index + 1} из ${step.total}",
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(w.name, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(w.cue, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            AssistChip(
                onClick = { showInfo(w.name, w.cue) },
                label = { Text("Как делать") },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.HelpOutline, null, Modifier.size(18.dp)) },
            )
            if (w.seconds) {
                if (left == 0) {
                    Icon(Icons.Filled.CheckCircle, null, Modifier.size(96.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(
                        if (autoNext > 0) "Готово! Дальше через $autoNext…" else "Готово!",
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (autoNext > 0) OutlinedButton({ autoNext = -1 }) { Text("Остаться") }
                        OutlinedButton({ autoNext = -1; left = w.amount; running = true }) { Text("Ещё раз") }
                    }
                } else {
                    Text(
                        mmss(left.toLong()), fontSize = 72.sp, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ running = !running }) {
                            Icon(if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp)); Text(if (running) "Пауза" else if (left == w.amount) "Старт" else "Продолжить")
                        }
                        if (left != w.amount) OutlinedButton({ running = false; left = w.amount }) { Text("Сначала") }
                    }
                }
            } else {
                Text("${w.amount}", fontSize = 72.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("раз", style = MaterialTheme.typography.titleMedium)
            }
        },
        bottom = {
            ActionRow(back) {
                Button({ m.completeWarm(w.id) }, Modifier.weight(1f).height(56.dp)) {
                    Icon(Icons.Filled.Check, null); Spacer(Modifier.width(8.dp))
                    Text(if (step.index + 1 == step.total) "Готово — к подходам" else "Готово", style = MaterialTheme.typography.titleMedium)
                }
            }
        },
    )
}

// ——— 2. Подход ———

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetView(m: AppModel, d: Draft, step: Step.Set, showInfo: (String, String?) -> Unit, back: () -> Unit) {
    val ex = step.ex
    val key = setKey(step.exIndex, step.setIndex)
    val rec = remember(ex, step.setIndex, m.history, d.plan.intensity) { m.recommend(ex, step.setIndex, d.plan.intensity) }
    val saved = d.sets[key]
    var left by rememberSaveable(key) { mutableStateOf(saved?.left?.ifBlank { null } ?: rec.left?.toString() ?: "") }
    var right by rememberSaveable(key) { mutableStateOf(saved?.right?.ifBlank { null } ?: rec.right?.toString() ?: "") }
    var rir by rememberSaveable(key) { mutableStateOf(saved?.rir ?: Reserve.NONE) }
    var editLoad by remember { mutableStateOf(false) }
    var replace by remember { mutableStateOf(false) }
    val sec = ex.seconds
    val unit = if (sec) "с" else "повт."
    val harder = remember(ex.name) { Guide.find(ex.name)?.harder }

    StepLayout(
        centered = true,
        middle = {
            Text(
                "Упражнение ${step.exIndex + 1} из ${d.plan.exercises.size} · подход ${step.setIndex + 1} из ${ex.sets}",
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(ex.name, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(ex.scheme, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            if (ex.cue.isNotBlank()) Text(ex.cue, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            ex.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { showInfo(ex.name, ex.cue) },
                    label = { Text("Техника") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.HelpOutline, null, Modifier.size(18.dp)) },
                )
                if (m.canReplace(step.exIndex)) AssistChip(
                    onClick = { replace = true },
                    label = { Text("Заменить") },
                    leadingIcon = { Icon(Icons.Outlined.SwapHoriz, null, Modifier.size(18.dp)) },
                )
                if (ex.load != LoadKind.NONE) AssistChip(
                    onClick = { editLoad = true },
                    label = { Text(loadTitle(ex.load) + ": " + (d.loads[step.exIndex]?.ifBlank { null } ?: "—")) },
                    leadingIcon = { Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp)) },
                )
            }
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Рекомендую", style = MaterialTheme.typography.labelLarge)
                    val big = if (ex.perSide) "${rec.left ?: "—"} / ${rec.right ?: "—"}" else "${rec.left ?: "—"}"
                    Text(big, fontSize = 48.sp, fontWeight = FontWeight.Bold)
                    Text(if (ex.perSide) "$unit · левая / правая" else unit, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(rec.reason, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    // 5.42: рабочий вес из инвентаря
                    rec.load?.let { lv ->
                        Spacer(Modifier.height(6.dp))
                        Text("Вес: $lv", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        rec.loadNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center) }
                        val cur = d.loads[step.exIndex]
                        if (!cur.isNullOrBlank() && cur != lv) TextButton({ m.setLoad(step.exIndex, lv) }) { Text("Взять $lv (сейчас $cur)") }
                    }
                    rec.startLoad?.let {
                        Text("Начальный вес $it — подбери так, чтобы в запасе оставалось 3–4 повтора", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
                    }
                    if (rec.reason.contains("пора усложнить") && harder != null) Row(
                        Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.TrendingUp, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(harder, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        bottom = {
            if (sec) HoldStopwatch(ex) { left = it.toString() }
            Text("Сделано", style = MaterialTheme.typography.labelLarge)
            if (ex.perSide) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stepper(left, { left = it }, "Левая", if (sec) 5 else 1, Modifier.weight(1f))
                    Stepper(right, { right = it }, "Правая", if (sec) 5 else 1, Modifier.weight(1f))
                }
            } else Stepper(left, { left = it }, unit, if (sec) 5 else 1, Modifier.fillMaxWidth())
            // 5.55: запас — по нему следующая прибавка: 0 — держим, 1–2 — +1, 3+ — +2
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Запас:", style = MaterialTheme.typography.labelLarge)
                Reserve.options.forEach { (v, lbl) ->
                    FilterChip(rir == v, { rir = if (rir == v) Reserve.NONE else v }, label = { Text(lbl) },
                        modifier = Modifier.testTag("rir_$v"))
                }
            }
            Text("Сколько ещё ${if (sec) "секунд" else "повторов"} мог бы — по желанию", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            ActionRow(back) {
                Button(
                    onClick = { m.completeSet(step, SetResult(left, if (ex.perSide) right else "", rir = rir)) },
                    enabled = left.isNotBlank() && (!ex.perSide || right.isNotBlank()),
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Icon(Icons.Filled.Check, null); Spacer(Modifier.width(8.dp))
                    Text("Готово", style = MaterialTheme.typography.titleMedium)
                }
            }
        },
    )
    if (replace) ReplaceDialog(m, step.exIndex) { replace = false }
    if (editLoad) {
        var v by remember { mutableStateOf(d.loads[step.exIndex] ?: "") }
        // 5.42: варианты — из инвентаря (Ещё → Инвентарь дома / Зал); без весов — прежние примеры
        val inv = LoadPlan.options(ex.load, d.plan.place, m.inventory).map { LoadPlan.format(ex.load, it) }
        val options = inv.ifEmpty {
            when (ex.load) {
                LoadKind.BACKPACK -> listOf("0 книг", "1 книга", "2 книги", "3 книги", "4 книги", "5 книг", "6 книг")
                LoadKind.DUMBBELLS -> listOf("2 × 2,5 кг", "2 × 5 кг", "2 × 7,5 кг", "2 × 10 кг", "2 × 12,5 кг", "2 × 15 кг")
                else -> listOf("10 кг", "15 кг", "20 кг", "25 кг", "30 кг", "40 кг", "50 кг", "60 кг")
            }
        }
        AlertDialog(
            onDismissRequest = { editLoad = false },
            title = { Text(loadTitle(ex.load)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(v, { v = it }, singleLine = true, label = { Text("Нагрузка") })
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        options.forEach { o -> FilterChip(v == o, { v = o }, label = { Text(o, maxLines = 1, softWrap = false) }) }
                    }
                    if (inv.isEmpty() && ex.load in setOf(LoadKind.DUMBBELLS, LoadKind.BACKPACK)) Text(
                        "Укажи свои веса в «Ещё → Инвентарь дома» — тогда здесь будут именно они, а вес будет подбираться сам.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton({ m.setLoad(step.exIndex, v.trim()); editLoad = false }) { Text("Сохранить") } },
            dismissButton = { TextButton({ editLoad = false }) { Text("Отмена") } },
        )
    }
}

/** Нижняя строка шага: маленькая «назад» слева и большая основная кнопка. */
/** Короткий сигнал (последние секунды) или длинный (время вышло). */
private fun beep(ctx: android.content.Context, short: Boolean) {
    runCatching {
        val tg = android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 80)
        tg.startTone(if (short) android.media.ToneGenerator.TONE_PROP_BEEP else android.media.ToneGenerator.TONE_PROP_ACK, if (short) 120 else 400)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ tg.release() }, 600)
    }
}

@Composable
private fun ActionRow(onBack: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onBack, Modifier.size(56.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "К предыдущему шагу") }
        content()
    }
}

fun loadTitle(k: LoadKind) = when (k) {
    LoadKind.BACKPACK -> "Рюкзак"
    LoadKind.DUMBBELLS -> "Гантели"
    LoadKind.BARBELL -> "Штанга"
    else -> "Вес"
}

@Composable
private fun Stepper(value: String, onChange: (String) -> Unit, label: String, inc: Int, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton({ onChange(((leadingInt(value) ?: 0) - inc).coerceAtLeast(0).toString()) }) {
            Icon(Icons.Filled.Remove, "Меньше")
        }
        OutlinedTextField(
            value = value,
            onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(4)) },
            label = { Text(label, maxLines = 1) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold),
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
        )
        FilledTonalIconButton({ onChange(((leadingInt(value) ?: 0) + inc).toString()) }) {
            Icon(Icons.Filled.Add, "Больше")
        }
    }
}

/** Секундомер удержания (планка): вибрирует на нижней и верхней границе цели, по «Стоп» записывает секунды. */
@Composable
private fun HoldStopwatch(ex: PlannedEx, onStop: (Int) -> Unit) {
    val ctx = LocalContext.current
    var startedAt by rememberSaveable { mutableLongStateOf(0L) }
    var buzzed by remember { mutableStateOf(setOf<Int>()) }
    val running = startedAt > 0
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(running) { while (running) { now = SystemClock.elapsedRealtime(); delay(200) } }
    val sec = if (running) ((now - startedAt) / 1000).toInt() else 0
    LaunchedEffect(sec) {
        val marks = setOf(ex.repMin, ex.repMax)
        if (running && sec in marks && sec !in buzzed) {
            buzzed = buzzed + sec
            ctx.getSystemService(Vibrator::class.java)
                ?.vibrate(VibrationEffect.createOneShot(if (sec == ex.repMax) 700 else 250, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (!running) {
            OutlinedButton({ buzzed = emptySet(); startedAt = SystemClock.elapsedRealtime(); now = startedAt }) {
                Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Засечь удержание")
            }
        } else {
            Text(
                mmss(sec.toLong()), fontSize = 36.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                color = if (sec >= ex.repMin) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(16.dp))
            Button({ onStop(sec); startedAt = 0 }) {
                Icon(Icons.Filled.Stop, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Стоп")
            }
        }
    }
}

// ——— 3. Отдых ———

@Composable
private fun RestView(m: AppModel, steps: List<Step>, idx: Int, back: () -> Unit) {
    val ctx = LocalContext.current
    val st by RestTimer.state.collectAsState()
    val s = st ?: return
    val next = steps.getOrNull(idx) as? Step.Set
    StepLayout(
        centered = true,
        middle = {
            Text("Отдых", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            RestDial(s)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(-60 to "−1 мин", -10 to "−10 с", 10 to "+10 с", 60 to "+1 мин").forEach { (sec, label) ->
                    OutlinedButton(
                        { RestTimer.add(ctx, sec) },
                        Modifier.weight(1f).height(52.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp),
                    ) { Text(label, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelLarge) }
                }
            }
            if (next != null) {
                val rec = remember(next) { m.recommend(next.ex, next.setIndex, m.draft?.plan?.intensity ?: Intensity.NORMAL) }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Дальше", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(next.ex.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        val target = if (next.ex.perSide) "${rec.left ?: "—"} / ${rec.right ?: "—"}" else "${rec.left ?: "—"}"
                        Text(
                            "Подход ${next.setIndex + 1} из ${next.ex.sets} · рекомендую $target " +
                                (if (next.ex.seconds) "с" else "повт.") +
                                ((m.draft?.loads?.get(next.exIndex)?.ifBlank { null } ?: rec.load)?.let { " · $it" } ?: ""),
                        )
                    }
                }
            }
        },
        bottom = {
            ActionRow(back) {
                Button({ RestTimer.stop(ctx) }, Modifier.weight(1f).height(56.dp)) {
                    Text("Начать подход", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                }
            }
        },
    )
}

// ——— 4. Результаты ———

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultsView(m: AppModel, d: Draft, back: () -> Unit) {
    var week by rememberSaveable { mutableStateOf(m.suggestedWeek()) }
    var well by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    val summary = remember(d) { Results.summarize(d) }
    val prev = remember(d.plan) { d.plan.exercises.associate { it.exId to m.lastResult(it.exId, it.name) } }
    val doneSets = d.sets.values.count { it.done }
    val total = d.plan.exercises.sumOf { it.sets }
    StepLayout(
        middle = {
            Text("Тренировка завершена", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "${formatDuration(d.elapsedMs() / 1000)} · подходов $doneSets из $total" +
                    if (d.plan.intensity != Intensity.NORMAL) " · ${d.plan.intensity.title.lowercase()}" else "",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    summary.forEachIndexed { i, r ->
                        if (i > 0) HorizontalDivider()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.name, style = MaterialTheme.typography.bodyLarge)
                                prev[r.exId]?.let { p ->
                                    Text(
                                        "было: " + p.values.joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Text(
                                if (r.values.isEmpty()) "—" else r.values.joinToString(" · ") + if (r.seconds) " с" else "",
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                            )
                            if (r.allTop) Icon(Icons.Outlined.TrendingUp, "Пора усложнить", Modifier.padding(start = 6.dp).size(18.dp), tint = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
            }
            if (summary.any { it.allTop }) Text(
                "↗ — все подходы на верхней границе: в следующий раз можно усложнить.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary,
            )
            // 1.3: белок после тренировки — если последние дни его мало
            m.readiness?.proteinTip?.let { tip ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Питание после тренировки", style = MaterialTheme.typography.titleSmall)
                        Text(tip, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Text("Самочувствие", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("отлично", "хорошо", "нормально", "устал", "болят мышцы").forEach { v ->
                    FilterChip(well == v, { well = if (well == v) "" else v }, label = { Text(v) })
                }
            }
            OutlinedTextField(well, { well = it }, label = { Text("Своими словами") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                notes, { notes = it }, label = { Text("Заметки") }, minLines = 3, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Что было тяжело, что усложнить в следующий раз…") },
            )
            OutlinedTextField(
                week, { week = it.filter(Char::isDigit) }, label = { Text("Неделя программы") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(180.dp),
            )
            Text("После тренировки", style = MaterialTheme.typography.titleSmall)
            MarkdownExport.afterItems.forEachIndexed { i, item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(i in d.after, { m.toggleAfter(i) }); Text(item)
                }
            }
        },
        bottom = {
            ActionRow(back) {
                Button({ m.finish(week.trim(), well.trim(), notes) }, Modifier.weight(1f).height(56.dp)) {
                    Text("Сохранить и выйти", style = MaterialTheme.typography.titleMedium)
                }
            }
        },
    )
}

/** Время с начала тренировки: перерисовывается раз в секунду только эта строка. */
@Composable
private fun ElapsedText(d: Draft) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000 - now % 1000) } }
    Text(
        when {
            !d.running -> "ещё не начата"
            d.paused -> "пауза · " + mmss(d.elapsedMs(now) / 1000)
            else -> "идёт " + mmss(d.elapsedMs(now) / 1000)
        },
        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
    )
}

/**
 * Круг отдыха. Положение дуги читается каждый кадр прямо при отрисовке (без перекомпоновки экрана),
 * поэтому кольцо уменьшается плавно; цифры меняются раз в секунду.
 */
@Composable
private fun RestDial(s: TimerState) {
    val frame = remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(s) { while (true) withFrameMillis { frame.longValue = SystemClock.elapsedRealtime() } }
    val secondsLeft by remember(s) { derivedStateOf { ((s.endElapsed - frame.longValue).coerceAtLeast(0) + 999) / 1000 } }
    Box(Modifier.size(280.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { ((s.endElapsed - frame.longValue).coerceAtLeast(0) / 1000f / s.totalSec).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxSize(),
            strokeWidth = 14.dp,
            gapSize = 0.dp,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Text(
            mmss(secondsLeft),
            style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
            fontSize = 80.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
        )
    }
}
