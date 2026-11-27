package org.maittaa.tabita.train

import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Psychology
import org.maittaa.tabita.R

import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Save
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Backpack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun App(m: AppModel, a: Actions) {
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(m.message) {
        m.message?.let { snack.showSnackbar(it); m.message = null }
    }
    var info by remember { mutableStateOf<Pair<String, String?>?>(null) }
    val showInfo: (String, String?) -> Unit = { name, desc -> info = name to desc }

    when (val s = m.screen) {
        Screen.Home -> HomeScreen(m, a, snack)
        Screen.Workout -> if (m.draft != null) WorkoutScreen(m, a, showInfo) else LaunchedEffect(Unit) { m.screen = Screen.Home }
        Screen.Guide -> GuideScreen(m, showInfo)
        Screen.Program -> ProgramScreen(m, snack, showInfo)
        Screen.Generator -> GeneratorScreen(m)
        Screen.TabitaData -> TabitaDataScreen(m)
        is Screen.Detail -> m.history.firstOrNull { it.id == s.id }?.let { DetailScreen(m, a, it, snack) }
            ?: LaunchedEffect(Unit) { m.screen = Screen.Home }
    }
    info?.let { (name, desc) -> InfoSheet(name, desc) { info = null } }
    ImportConfirm(m, a)
}

/** 5.50: раздел «Самочувствие» во вкладке «Здоровье» — экран чекапа даёт MainActivity. */
val LocalWellbeing = androidx.compose.runtime.staticCompositionLocalOf<(@Composable (Modifier) -> Unit)?> { null }
/** 5.51: аэробная нагрузка — экран даёт MainActivity: (вкладка 0–2, модификатор). */
val LocalCardio = androidx.compose.runtime.staticCompositionLocalOf<(@Composable (Int, Modifier) -> Unit)?> { null }

/**
 * 5.52: «Здоровье» — главная с крупными плитками (силовая, аэробика, йога, самочувствие; её даёт MainActivity
 * через [LocalHealthHub]), нажатие открывает раздел со своими вкладками. Назад — на главную.
 */
const val SEC_HUB = -1
const val SEC_STRENGTH = 0
const val SEC_CARDIO = 1
const val SEC_YOGA = 2
const val SEC_WB = 3
/** 5.54: «Активность» — активные минуты за день по пульсу. */
const val SEC_ACTIVITY = 4
/** 5.58: «Тело» — замеры, фото, состав тела. */
const val SEC_BODY = 5
/** 5.60: «Сон» — последняя ночь, регулярность, фазы. */
const val SEC_SLEEP = 6
val LocalSleep = androidx.compose.runtime.staticCompositionLocalOf<(@Composable (Modifier) -> Unit)?> { null }
val LocalBody = androidx.compose.runtime.staticCompositionLocalOf<(@Composable (Int, Modifier) -> Unit)?> { null }
val LocalHealthHub = androidx.compose.runtime.staticCompositionLocalOf<(@Composable (Modifier) -> Unit)?> { null }
/** 5.53: раздел «Йога и растяжка» — экран даёт MainActivity: (вкладка 0–1, модификатор). */
val LocalActivity = androidx.compose.runtime.staticCompositionLocalOf<(@Composable (Modifier) -> Unit)?> { null }
val LocalYoga = androidx.compose.runtime.staticCompositionLocalOf<(@Composable (Int, Modifier) -> Unit)?> { null }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(m: AppModel, a: Actions, snack: SnackbarHostState) {
    val hub = LocalHealthHub.current
    val sec = if (hub == null && m.section == SEC_HUB) SEC_STRENGTH else m.section
    // 5.53: история — общая, на главной; в разделах две вкладки
    if (m.tab !in 0..1) m.tab = 0
    androidx.activity.compose.BackHandler(enabled = sec != SEC_HUB && hub != null) { m.section = SEC_HUB }
    val title = when (sec) {
        SEC_STRENGTH -> org.maittaa.tabita.t("Силовая")
        SEC_CARDIO -> org.maittaa.tabita.t("Аэробика")
        SEC_YOGA -> org.maittaa.tabita.t("Йога и растяжка")
        SEC_WB -> org.maittaa.tabita.t("Самочувствие")
        SEC_ACTIVITY -> org.maittaa.tabita.t("Активность")
        SEC_BODY -> org.maittaa.tabita.t("Тело")
        SEC_SLEEP -> org.maittaa.tabita.t("Сон")
        else -> org.maittaa.tabita.t("Здоровье")
    }
    Scaffold(
        topBar = {
            if (!(m.ownBar && sec == SEC_WB)) Column {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        if (sec != SEC_HUB && hub != null) IconButton({ m.section = SEC_HUB; org.maittaa.tabita.Log.d("UI", "здоровье: главная") }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, org.maittaa.tabita.t("Назад"))
                        }
                    },
                    actions = {
                        if (sec == SEC_STRENGTH) IconButton(onClick = { m.screen = Screen.Guide }) { Icon(Icons.AutoMirrored.Outlined.MenuBook, "Справочник") }
                    },
                )
                if (sec == SEC_STRENGTH || sec == SEC_CARDIO || sec == SEC_YOGA || sec == SEC_BODY) org.maittaa.tabita.IconTabRow(m.tab, listOf(
                    when (sec) {
                        SEC_CARDIO -> org.maittaa.tabita.t("Занятия") to Icons.Filled.Favorite
                        SEC_YOGA -> org.maittaa.tabita.t("Комплексы") to Icons.Filled.SelfImprovement
                        SEC_BODY -> org.maittaa.tabita.t("Замеры") to Icons.Filled.Straighten
                        else -> "Тренировка" to Icons.Filled.FitnessCenter
                    },
                    if (sec == SEC_BODY) org.maittaa.tabita.t("Фото") to Icons.Filled.PhotoCamera else "Прогресс" to Icons.AutoMirrored.Filled.TrendingUp,
                )) { i -> m.tab = i; org.maittaa.tabita.Log.d("UI", "здоровье: раздел $sec, вкладка $i") }
            }
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        val mod = Modifier.padding(pad).fillMaxSize()
        when (sec) {
            SEC_HUB -> hub?.invoke(mod)
            SEC_WB -> LocalWellbeing.current?.invoke(mod)
            SEC_CARDIO -> LocalCardio.current?.invoke(m.tab, mod)
            SEC_YOGA -> LocalYoga.current?.invoke(m.tab, mod)
            SEC_ACTIVITY -> LocalActivity.current?.invoke(mod)
            SEC_BODY -> LocalBody.current?.invoke(m.tab, mod)
            SEC_SLEEP -> LocalSleep.current?.invoke(mod)
            else -> if (m.tab == 0) TrainingsTab(m, a, pad) else ProgressTab(m, pad)
        }
    }
}

@Composable
fun ReadinessCard(r: Readiness, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val container = when (r.intensity) {
        Intensity.NORMAL -> MaterialTheme.colorScheme.secondaryContainer
        Intensity.LIGHT -> MaterialTheme.colorScheme.tertiaryContainer
        Intensity.RECOVERY -> MaterialTheme.colorScheme.errorContainer
    }
    Card(
        modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.MonitorHeart, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(r.headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (onClick != null) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Все данные")
            }
            Text(if (onClick != null) "По данным дневника · нажми, чтобы увидеть все данные" else "По данным дневника", style = MaterialTheme.typography.labelSmall)
            r.reasons.forEach {
                Text(
                    "• ${it.text}", style = MaterialTheme.typography.bodySmall,
                    color = if (it.tone == Tone.BAD) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * 1.8: решение на сегодня по данным дневника — тренироваться ли, перенос с пересчётом расписания
 * и советы по восстановлению.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TodayCard(m: AppModel, r: Readiness, onStart: (ProgramDay) -> Unit) {
    val planned = m.todayPlanned()
    val verdict = TodayAdvice.verdict(r)
    val moves = m.upcomingMoves()
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when (verdict) {
        Verdict.GO -> cs.secondaryContainer to cs.onSecondaryContainer
        Verdict.EASY -> cs.tertiaryContainer to cs.onTertiaryContainer
        Verdict.SKIP -> cs.errorContainer to cs.onErrorContainer
    }
    var details by remember { mutableStateOf(false) }
    val title = if (planned != null) verdict.title else when (verdict) {
        Verdict.GO -> "Сегодня отдых · готовность в норме"
        else -> "Сегодня отдых — и он кстати"
    }
    Card(Modifier.fillMaxWidth().clickable { details = true }, colors = CardDefaults.cardColors(containerColor = bg, contentColor = fg)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.MonitorHeart, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text((planned?.let { "По плану: ${it.name} · " } ?: "") + "по данным дневника", style = MaterialTheme.typography.labelSmall)
                }
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Подробнее")
            }
            // сводка: коротко все показатели, плохие — выделены
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                r.reasons.mapNotNull { rs -> rs.short?.let { it to rs.tone } }.forEach { (t, tone) ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (tone == Tone.BAD) cs.error.copy(alpha = 0.22f) else fg.copy(alpha = 0.08f),
                        contentColor = fg,
                    ) {
                        Text((if (tone == Tone.BAD) "! " else "") + t.replace("ккал/день", "ккал/д"), Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelMedium, fontWeight = if (tone == Tone.BAD) FontWeight.SemiBold else null, maxLines = 1)
                    }
                }
            }
            if (moves.isNotEmpty()) Text("Перенесено: " + moves.joinToString(", ") { (d, date) -> "${d.name} → ${date.dayOfWeek.ruShort()} ${date.dayOfMonth}" },
                style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (planned != null && verdict != Verdict.GO) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (verdict == Verdict.SKIP) {
                    Button({ m.postponeToday() }, Modifier.weight(1f)) { Text("Перенести", maxLines = 1) }
                    OutlinedButton({ onStart(planned) }, Modifier.weight(1f)) { Text("Всё равно, легко", maxLines = 1) }
                } else {
                    Button({ onStart(planned) }, Modifier.weight(1f)) { Text("Облегчённая", maxLines = 1) }
                    OutlinedButton({ m.postponeToday() }, Modifier.weight(1f)) { Text("Перенести", maxLines = 1) }
                }
            }
        }
    }
    if (details) TodayDetails(m, r, planned, verdict, moves, onDismiss = { details = false })
}

/** Подробности «сегодня»: все причины, почему так, переносы, советы по восстановлению. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodayDetails(m: AppModel, r: Readiness, planned: ProgramDay?, verdict: Verdict,
                         moves: List<Pair<ProgramDay, java.time.LocalDate>>, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (planned != null) verdict.title else r.headline, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (planned != null) Text(r.headline + " · по плану ${planned.name}", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            if (planned != null) when (verdict) {
                Verdict.SKIP -> Text("Тяжёлая тренировка на таком восстановлении даст меньше пользы и больше риска травмы. " +
                    "«Перенести» сдвинет её и следующие ровно настолько, чтобы между тренировками остался отдых.", style = MaterialTheme.typography.bodySmall)
                Verdict.EASY -> Text("Можно с меньшим объёмом" + (if (m.autoIntensity) " — умный режим сам облегчит план." else " — выбери «Облегчённая» на подготовке."),
                    style = MaterialTheme.typography.bodySmall)
                Verdict.GO -> {}
            }
            Text("Показатели", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
            r.reasons.forEach {
                Text("• ${it.text}", style = MaterialTheme.typography.bodySmall, color = if (it.tone == Tone.BAD) cs.error else cs.onSurface)
            }
            if (moves.isNotEmpty()) {
                Text("Переносы", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
                moves.forEach { (d, date) -> Text("• ${d.name} — ${date.dayOfWeek.ru()}, ${date.dayOfMonth}", style = MaterialTheme.typography.bodySmall) }
                TextButton({ m.resetSchedule(); onDismiss() }, contentPadding = PaddingValues(0.dp)) { Text("Вернуть обычное расписание") }
            }
            Text("Восстановление сегодня", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
            TodayAdvice.recovery(m.tabitaData, r, java.time.LocalDate.now(), m.body.weightKg).forEach { t ->
                Column {
                    Text(t.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text(t.text, style = MaterialTheme.typography.bodySmall)
                }
            }
            OutlinedButton({ onDismiss(); m.openTabitaData() }, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Данные дневника с графиками") }
        }
    }
}

/** Разгрузочная неделя (1.3): предложение по данным дневника или уже идущая разгрузка. [onChange] — пересобрать план. */
@Composable
fun DeloadCard(m: AppModel, onChange: () -> Unit = {}) {
    if (!m.deloadActive && !m.deloadOffer) return
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (m.deloadActive) {
                Text("Разгрузочная неделя до ${prettyDate(m.deloadUntil)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Подходов на 40 % меньше, веса и упражнения те же. Так мышцы и нервная система восстанавливаются, а форма не теряется.",
                    style = MaterialTheme.typography.bodySmall)
                TextButton({ m.cancelDeload(); onChange() }) { Text("Закончить раньше") }
            } else {
                Text("Взять разгрузочную неделю?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Пульс покоя три дня подряд выше обычного при регулярных тренировках — признак накопленной усталости. " +
                    "Неделя с объёмом −40 % обычно возвращает пульс к норме, а силу — с запасом.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ m.startDeload(); onChange() }) { Text("Включить на неделю") }
                    OutlinedButton({ m.snoozeDeload() }) { Text("Не сейчас") }
                }
            }
        }
    }
}

@Composable
private fun TrainingsTab(m: AppModel, a: Actions, pad: PaddingValues) {
    val p = m.program
    val sug = m.suggestion()
    var confirmStart by remember { mutableStateOf<ProgramDay?>(null) }
    val start: (ProgramDay) -> Unit = { d ->
        if (m.draft != null) confirmStart = d else { a.askNotifications(); m.start(d) }
    }
    confirmStart?.let { d ->
        AlertDialog(
            onDismissRequest = { confirmStart = null },
            title = { Text("Начать заново?") },
            text = { Text("Незаконченная тренировка «${m.draft?.plan?.title ?: ""}» будет сброшена.") },
            confirmButton = { TextButton({ confirmStart = null; a.askNotifications(); m.start(d) }) { Text("Начать") } },
            dismissButton = { TextButton({ confirmStart = null }) { Text("Отмена") } },
        )
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(pad),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 5.37: после автосборки — напоминание пересобрать через 7 и 14 дней
        m.rebuildDue()?.let { d -> item {
            var hide by remember { mutableStateOf(false) }
            if (!hide) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (d == 7) "Неделя с программой — пора пересобрать" else "Две недели — пересоберите ещё раз",
                        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    Text("Подбор учтёт ваши тренировки: сколько повторов и с каким весом получилось и как шло восстановление. " +
                        if (d == 7) "Через неделю будет ещё одна пересборка." else "После неё план станет вашим.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ m.screen = Screen.Generator }) { Text("Пересобрать") }
                        TextButton({ m.snoozeRebuild(); hide = true }) { Text("Позже") }
                    }
                }
            }
        } }
        if (p == null) item {
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Программы пока нет", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Выбери цель, дни, длительность и уровень — приложение соберёт программу под твой инвентарь. Потом её можно править вручную.")
                    Button({ m.screen = Screen.Generator }, Modifier.fillMaxWidth()) { Text("Создать программу") }
                }
            }
        }
        m.readiness?.let { r -> item { TodayCard(m, r, onStart = start) } }
        if (m.deloadActive || m.deloadOffer) item { DeloadCard(m) }
        val d = m.draft
        if (d != null) item {
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Column(Modifier.padding(20.dp)) {
                    Text("Тренировка не закончена", style = MaterialTheme.typography.labelLarge)
                    Text(d.plan.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Шаг ${d.step + 1} из ${m.steps(d.plan).size}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.padding(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ m.screen = Screen.Workout }, Modifier.weight(1f)) { Text("Продолжить") }
                        OutlinedButton({ m.discard() }) { Text("Сбросить") }
                    }
                }
            }
        }
        if (sug != null && d == null) item {
            // 1.8.1: компактно — решение и показатели уже в карточке «сегодня» выше
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(sug.label, style = MaterialTheme.typography.labelLarge)
                    Text(sug.day.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    MuscleFocus.describe(sug.day)?.let { Text(it, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    Text(sug.day.exercises.mapNotNull { it.def?.name }.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.padding(2.dp))
                    Button(onClick = { start(sug.day) }, Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Начать тренировку")
                    }
                }
            }
        }
        if (p != null) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Программа", style = MaterialTheme.typography.titleMedium)
                        Text(p.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton({ m.screen = Screen.Program }) {
                        Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Изменить")
                    }
                }
            }
            items(p.days, key = { it.id }) { day ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(day.name + (day.weekday?.let { " · ${it.ru()}" } ?: ""), style = MaterialTheme.typography.titleMedium)
                            MuscleFocus.describe(day)?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            Text(
                                day.exercises.mapNotNull { it.def?.name }.joinToString(" · ").ifBlank { "нет упражнений" },
                                style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (day.exercises.isNotEmpty()) IconButton(onClick = { start(day) }) { Icon(Icons.Filled.PlayArrow, "Начать") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(m: AppModel, showInfo: (String, String?) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Справочник") },
                navigationIcon = { IconButton({ m.screen = Screen.Home }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Как прогрессировать", style = MaterialTheme.typography.titleMedium)
                        Guide.progression.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
            Pattern.entries.forEach { pat ->
                val list = Library.all.filter { it.pattern == pat }
                if (list.isNotEmpty()) {
                    item(key = pat.name) {
                        Text(pat.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                    }
                    items(list, key = { it.id }) { e ->
                        ExerciseRow(e, if (m.gymEnabled) m.equipment + m.gymEquipment else m.equipment) { showInfo(e.name, e.cue) }
                    }
                }
            }
        }
    }
}

@Composable
fun ExerciseRow(e: ExerciseDef, eq: Set<Equipment>, onClick: () -> Unit) {
    val ok = e.available(eq, false)
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(e.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "Уровень ${e.level} · ${e.needsLabel}" + if (!ok) " · нет инвентаря" else "",
                style = MaterialTheme.typography.bodySmall,
                color = if (ok) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
        }
    }
}

// ——— настройки ———

@Composable
internal fun SettingsHeader(title: String) = Text(
    title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(start = 8.dp, top = 8.dp),
)

fun yearsWord(n: Int): String = when {
    n % 100 in 11..14 -> "лет"
    n % 10 == 1 -> "год"
    n % 10 in 2..4 -> "года"
    else -> "лет"
}

@Composable
internal fun SettingsItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TrainingSettingsDialog(m: AppModel, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Настройки тренировок") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Отдых между подходами", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(m.defaultRest == 0, { m.changeDefaultRest(0) }, label = { Text("по программе" + (m.program?.let { " (${it.restSec} с)" } ?: "")) })
                    listOf(60, 90, 120, 150, 180).forEach { v ->
                        FilterChip(m.defaultRest == v, { m.changeDefaultRest(v) }, label = { Text("$v с") })
                    }
                }
                Text(
                    "Во время отдыха время меняется кнопками ±10 с и ±1 мин. В облегчённый день отдых на 30 с длиннее.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Интенсивность автоматически", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Умный режим: обычная, облегчённая или восстановительная тренировка выбирается сама по сну, пульсу покоя и другим данным дневника. " +
                                "На экране подготовки всегда можно выбрать вручную." + if (!m.smartAvailable) " Сейчас данные дневника не учитываются — интенсивность обычная." else "",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(m.autoIntensity, { m.changeAutoIntensity(it) })
                }
                HorizontalDivider()
                Text("Программа", style = MaterialTheme.typography.titleSmall)
                OutlinedButton({ onClose(); m.screen = Screen.Program }, enabled = m.program != null) { Text("Изменить программу") }
                OutlinedButton({ onClose(); m.screen = Screen.Generator }) { Text(if (m.program == null) "Создать программу" else "Собрать заново") }
            }
        },
        confirmButton = { TextButton(onClose) { Text("Готово") } },
    )
}

@Composable
internal fun EquipmentDialog(m: AppModel, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Инвентарь дома") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Отметь, что есть. Упражнения, для которых чего-то нет, в тренировке заменятся похожими.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Equipment.homeList.forEach { e ->
                    val on = e in m.equipment
                    val toggle = { m.changeEquipment(if (on) m.equipment - e else m.equipment + e) }
                    Row(
                        Modifier.fillMaxWidth().clickable(onClick = toggle).padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(on, { toggle() })
                        Column {
                            Text(e.title)
                            Text(e.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    // 5.42: веса — чтобы тренировка подбирала рабочий вес из того, что есть
                    if (on && e == Equipment.DUMBBELLS) DumbbellSetup(m)
                    if (on && e == Equipment.BACKPACK) BackpackSetup(m)
                }
            }
        },
        confirmButton = { TextButton(onClose) { Text("Готово") } },
    )
}

@Composable
internal fun HealthDialog(m: AppModel, a: Actions, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Health Connect") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Записывать тренировки", Modifier.weight(1f))
                    Switch(m.useHealth, { m.changeUseHealth(it) })
                }
                Text(
                    "Каждая завершённая тренировка записывается как «Силовая тренировка» с временем, названием и результатами. " +
                        "Она попадёт в пункт дня «Тренировки» и в Google Health / Fitbit. " +
                        "Если запись не удалась (нет доступа, ошибка), тренировка допишется сама при следующем входе.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (m.useHealth) {
                    Text("Статус: ${m.hcStatus?.title ?: "проверяю…"}", style = MaterialTheme.typography.titleSmall)
                    hcHelp(m.hcStatus)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    if (m.hcStatus == HcStatus.NO_PERMISSION) OutlinedButton(a.requestHealth) { Text("Разрешить запись") }
                    if ((m.hcStatus == HcStatus.UNAVAILABLE && android.os.Build.VERSION.SDK_INT < 34) || m.hcStatus == HcStatus.NEEDS_UPDATE) {
                        val ctx = androidx.compose.ui.platform.LocalContext.current
                        OutlinedButton({
                            runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(HealthSync.PLAY))) }
                                .onFailure { m.message = "Google Play не найден" }
                        }) { Text("Открыть Google Play") }
                    }
                    val notSaved = m.history.count { !it.hcSaved }
                    if (m.hcStatus == HcStatus.OK && notSaved > 0) OutlinedButton({ m.history.filter { !it.hcSaved }.forEach { m.saveToHealth(it, quiet = true) } }) {
                        Text("Записать прошлые тренировки ($notSaved)")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClose) { Text("Готово") } },
    )
}

@Composable
internal fun GymDialog(m: AppModel, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Зал") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Хожу в зал", Modifier.weight(1f))
                    Switch(m.gymEnabled, { m.changeGymEnabled(it) })
                }
                Text(
                    "Перед каждой тренировкой можно выбрать «Дома» или «В зале» — упражнения подстроятся под инвентарь места.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (m.gymEnabled) {
                    Text("Что есть в зале", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                    Equipment.gymList.forEach { e ->
                        val on = e in m.gymEquipment
                        val toggle = { m.changeGymEquipment(if (on) m.gymEquipment - e else m.gymEquipment + e) }
                        Row(
                            Modifier.fillMaxWidth().clickable(onClick = toggle).padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(on, { toggle() })
                            Column {
                                Text(e.title)
                                Text(e.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    GymSteps(m)
                }
            }
        },
        confirmButton = { TextButton(onClose) { Text("Готово") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)


/** Данные тренировок: резервная копия, восстановление, таблица, удаление. */
@Composable
internal fun DataDialog(m: AppModel, a: Actions, onClose: () -> Unit) {
    var wipe by remember { mutableStateOf(false) }
    var sure by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Данные тренировок") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Резервная копия", style = MaterialTheme.typography.titleSmall)
                Text("Один файл: история тренировок, программа, незаконченная тренировка и настройки. Подходит для переноса на другой телефон.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(a.exportBackup, Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Сохранить", maxLines = 1, softWrap = false) }
                    OutlinedButton(a.importBackup, Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Восстановить", maxLines = 1, softWrap = false) }
                }
                HorizontalDivider()
                Text("Таблица", style = MaterialTheme.typography.titleSmall)
                Text("CSV для Excel или Google Таблиц: строка — подход (дата, упражнение, результат, нагрузка).",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(a.exportCsv, enabled = m.history.isNotEmpty()) { Text("Выгрузить тренировки в CSV") }
                HorizontalDivider()
                Text("Удаление", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                Text("Стирает историю тренировок, программу и незаконченную тренировку. Настройки тренировок остаются. Дневник не трогается. " +
                    "Записи в Health Connect не трогаются.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton({ wipe = true; sure = false }, enabled = m.history.isNotEmpty() || m.program != null) {
                    Text("Удалить все данные", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClose) { Text("Готово") } },
    )
    if (wipe) AlertDialog(
        onDismissRequest = { wipe = false },
        title = { Text("Удалить все данные?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Будет удалено тренировок: ${m.history.size}" + if (m.program != null) " и программа «${m.program?.name}»." else ".")
                Text("Сначала можно сохранить резервную копию.", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.clickable { sure = !sure }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(sure, { sure = it }); Text("Понимаю, что это нельзя отменить")
                }
            }
        },
        confirmButton = {
            TextButton({ Backup.wipe(m.storage.context); a.restart() }, enabled = sure) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton({ wipe = false }) { Text("Отмена") } },
    )
    ImportConfirm(m, a)
}

/** Подтверждение восстановления копии тренировок (из файла). */
@Composable
fun ImportConfirm(m: AppModel, a: Actions) {
    m.importText?.let { text ->
        AlertDialog(
            onDismissRequest = { m.importText = null },
            title = { Text("Перенести тренировки?") },
            text = {
                Text((Backup.describe(text) ?: "") + ".\nТекущие история, программа и настройки тренировок будут заменены. Приложение перезапустится.")
            },
            confirmButton = {
                TextButton({
                    runCatching { Backup.restore(m.storage.context, text) }
                        .onSuccess { m.importText = null; a.restart() }
                        .onFailure { m.importText = null; m.message = "Не удалось восстановить: ${it.message}" }
                }) { Text("Перенести") }
            },
            dismissButton = { TextButton({ m.importText = null }) { Text("Отмена") } },
        )
    }
}

@Composable
internal fun TabitaDialog(m: AppModel, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Что из дневника учитывать") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Корректировать тренировку", Modifier.weight(1f))
                    Switch(m.useTabita, { m.changeUseTabita(it) })
                }
                Text(
                    "Плохой сон или пульс выше обычного → облегчённая или восстановительная тренировка; много шагов накануне → " +
                        "меньше подходов на ноги; вес падает или неделя в дефиците калорий при наборе массы → без прибавки повторов; " +
                        "мало белка → подсказка после тренировки; мало еды днём → совет перекусить; пульс повышен три дня подряд → " +
                        "предложение разгрузочной недели. Режим можно поменять на экране подготовки. Данные обновляются при каждом входе.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (m.useTabita) {
                    Text("Какие данные брать", style = MaterialTheme.typography.titleSmall)
                    TabitaUse.all.forEach { (k, title) ->
                        Row(Modifier.fillMaxWidth().clickable { m.changeTabitaUse(if (k in m.tabitaUse) m.tabitaUse - k else m.tabitaUse + k) },
                            verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(k in m.tabitaUse, { on -> m.changeTabitaUse(if (on) m.tabitaUse + k else m.tabitaUse - k) })
                            Text(title, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Text("Выключенное тренировки не используют ни в расчётах, ни в графиках.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (m.useTabita) {
                    if (m.tabitaStatus != TabitaStatus.OK) Text("Статус: ${m.tabitaStatus?.title ?: "проверяю…"}", style = MaterialTheme.typography.titleSmall)
                    tabitaHelp(m.tabitaStatus)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    m.tabitaData?.let { d ->
                        val last = d.days.lastOrNull()
                        Text(
                            "Дней с данными: ${d.days.size}" + (last?.let { " · последний ${prettyDate(it.date.toString())}" } ?: "") +
                                (d.checkup?.let { " · чекап ${prettyDate(it.date.toString())}" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    m.readiness?.let { ReadinessCard(it, onClick = { onClose(); m.openTabitaData(); m.wantTab = true }) }
                    OutlinedButton({ m.refreshTabita() }) {
                        Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Обновить")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClose) { Text("Готово") } },
    )
}


fun formatDuration(sec: Long): String {
    val m = sec / 60
    return if (m >= 60) "${m / 60} ч ${m % 60} мин" else "$m мин"
}

fun prettyDate(iso: String): String = runCatching {
    val d = java.time.LocalDate.parse(iso)
    d.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM, EEE", java.util.Locale("ru")))
}.getOrDefault(iso)
