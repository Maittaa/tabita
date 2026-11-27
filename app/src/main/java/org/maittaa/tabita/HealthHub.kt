package org.maittaa.tabita

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.maittaa.tabita.train.Intensity
import org.maittaa.tabita.train.LocalTrainActions
import org.maittaa.tabita.train.SEC_CARDIO
import org.maittaa.tabita.train.SEC_STRENGTH
import org.maittaa.tabita.train.SEC_WB
import org.maittaa.tabita.train.SEC_YOGA
import org.maittaa.tabita.train.Screen
import org.maittaa.tabita.train.TodayAdvice
import org.maittaa.tabita.train.Verdict
import java.time.LocalDate

/**
 * 5.52: главная «Здоровья» — крупные плитки разделов с главным на сегодня. Нажатие открывает раздел
 * (силовая: тренировка, прогресс, история; аэробика: занятия, прогресс, история; самочувствие — чекап).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HealthHub(act: MainActivity, modifier: Modifier) {
    val m = act.train
    val open: (Int) -> Unit = { s -> m.section = s; m.tab = 0; Log.d("UI", "здоровье: раздел $s") }
    val entries = collect(act.repo.entries)
    val rows = remember(entries, m.history) { HealthHistory.rows(act) }
    var filter by remember { mutableStateOf(act.prefs.histFilter) }
    var shown by remember { mutableIntStateOf(30) }
    var detail by remember { mutableStateOf<Cardio.Item?>(null) }
    val list = rows.filter { filter == "all" || it.group == filter }
    LazyColumn(modifier.testTag("health_hub"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { StateCard(act) }
        item { StrengthTile(act) { open(SEC_STRENGTH) } }
        // 5.61: разделы — сеткой 2 × 3 одинаковых компактных плиток: одна цифра и строка сводки
        val pairs: List<Pair<@Composable (Modifier) -> Unit, @Composable (Modifier) -> Unit>> = listOf(
            Pair({ md -> CardioTile(act, md) { open(SEC_CARDIO) } }, { md -> YogaTile(act, md) { open(SEC_YOGA) } }),
            Pair({ md -> ActivityTile(act, md) { open(org.maittaa.tabita.train.SEC_ACTIVITY) } }, { md -> SleepTile(act, md) { open(org.maittaa.tabita.train.SEC_SLEEP) } }),
            Pair({ md -> BodyTile(act, md) { open(org.maittaa.tabita.train.SEC_BODY) } }, { md -> WellbeingTile(act, md) { open(SEC_WB) } }),
        )
        pairs.forEach { (a, b) ->
            item {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    a(Modifier.weight(1f).fillMaxHeight()); b(Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
        // 5.53: общая история всех тренировок — вместо вкладок «История» в разделах
        item {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t("История"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("all" to t("Все"), "strength" to t("Силовая"), "cardio" to t("Аэробика"), "yoga" to t("Йога")).forEach { (k, n) ->
                            val c = if (k == "all") rows.size else rows.count { it.group == k }
                            FilterChip(filter == k, { filter = k; act.prefs.histFilter = k; shown = 30 }, label = { Text("$n · $c") },
                                modifier = Modifier.testTag("hist_$k"))
                        }
                    }
                }
            }
        }
        if (list.isEmpty()) item {
            Text(t("Пока пусто. Тренировки, занятия с таймером и записанные часами появятся здесь."),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val fmt = java.time.format.DateTimeFormatter.ofPattern("LLLL yyyy", if (I18n.isEn) java.util.Locale.ENGLISH else java.util.Locale.forLanguageTag("ru"))
        list.take(shown).groupBy { it.date.withDayOfMonth(1) }.forEach { (month, l) ->
            item(key = "m_$month") {
                Text(month.format(fmt).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(l, key = { it.key }) { r -> HistRowView(r) {
                if (r.strengthId != null) m.screen = Screen.Detail(r.strengthId) else detail = r.item
            } }
        }
        if (list.size > shown) item {
            TextButton({ shown += 30 }, Modifier.fillMaxWidth()) { Text(t("Показать ещё")) }
        }
    }
    detail?.let { item ->
        val profile = collect(act.repo.profile)
        val z = remember(entries, profile) { HrZones.of(act.repo, act.prefs) }
        SessionDetail(act, item, z) { detail = null }
    }
}

/** 5.53: строки общей истории — силовые из приложения и с часов, аэробика, йога. */
object HealthHistory {
    data class Row(val key: String, val date: LocalDate, val start: Long, val group: String, val title: String, val sub: String,
                   val min: Int, val kind: String, val strengthId: String? = null, val item: Cardio.Item? = null)

    fun rows(act: MainActivity, days: Long = 365): List<Row> {
        val today = LocalDate.now()
        val from = today.minusDays(days)
        val app = act.train.history.filter { runCatching { !LocalDate.parse(it.date).isBefore(from) }.getOrDefault(false) }
        val out = mutableListOf<Row>()
        app.forEach { s ->
            out += Row("s_" + s.id, LocalDate.parse(s.date), s.startedAt, "strength", s.title.substringBefore(" — "),
                listOfNotNull(t("упражнений: %d", s.exercises.size), s.intensity.takeIf { it.isNotBlank() && it != Intensity.NORMAL.title }?.lowercase())
                    .joinToString(" · "), (s.durationSec / 60).toInt(), "strength", strengthId = s.id)
        }
        // силовые с часов (другие приложения): без тех, что записало само приложение
        Cardio.sessions(act.repo, from, today) { it.kind == "strength" }.forEach { it ->
            val st = it.s.start
            val dup = st != null && app.any { a -> st < a.startedAt + a.durationSec * 1000 + 600_000 && st + it.s.min * 60_000L > a.startedAt - 600_000 }
            if (!dup) out += Row("h_${it.date}_${st}_${it.manualIndex}", it.date, st ?: 0, "strength", it.s.title.ifBlank { Workouts.kind("strength").title },
                sourceOf(it), it.s.min, "strength", item = it)
        }
        Cardio.sessions(act.repo, from, today).forEach {
            out += Row("c_${it.date}_${it.s.start}_${it.manualIndex}_${it.s.kind}", it.date, it.s.start ?: 0, "cardio",
                it.s.title.ifBlank { Workouts.kind(it.s.kind).title }, sourceOf(it), it.s.min, it.s.kind, item = it)
        }
        Cardio.sessions(act.repo, from, today) { it.kind in Yoga.kinds }.forEach {
            out += Row("y_${it.date}_${it.s.start}_${it.manualIndex}", it.date, it.s.start ?: 0, "yoga",
                it.s.title.ifBlank { Workouts.kind(it.s.kind).title }, sourceOf(it), it.s.min, it.s.kind, item = it)
        }
        return out.sortedWith(compareByDescending<Row> { it.date }.thenByDescending { it.start })
    }

    private fun sourceOf(it: Cardio.Item): String = listOfNotNull(
        it.s.start?.let { st -> Cardio.clock(st) },
        it.s.dist?.takeIf { d -> d >= 300 && it.s.kind in setOf("run", "walk", "hike", "bike", "swim") }?.let { d -> Cardio.distText(d) },
        it.s.hr?.let { h -> t("%d уд/мин", h) },
        if (it.manualIndex != null) t("вручную") else AppNames.from(it.s.src) ?: t("из Health Connect"),
    ).joinToString(" · ")
}

@Composable
private fun HistRowView(r: HealthHistory.Row, onClick: () -> Unit) {
    val k = Workouts.kind(r.kind)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Icon(k.icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(r.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(r.date.ruShort() + (if (r.sub.isNotBlank()) " · " + r.sub else ""), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(t("%d мин", r.min), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

/** Общий вид плитки: значок, название, крупное значение, подписи, кнопка. */
@Composable
private fun HubTile(
    title: String, icon: ImageVector, container: Color, content: Color, tag: String, modifier: Modifier = Modifier,
    onClick: () -> Unit, body: @Composable () -> Unit,
) {
    Card(
        modifier.heightIn(min = 112.dp).clickable(onClick = onClick).testTag(tag),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp))
            }
            body()
        }
    }
}

@Composable
private fun Big(text: String, unit: String? = null) = Row(verticalAlignment = Alignment.Bottom) {
    Text(text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    if (unit != null) { Spacer(Modifier.width(4.dp)); Text(unit, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 3.dp)) }
}

@Composable
private fun Small(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)

/** Силовая: незаконченная тренировка, иначе план на сегодня и готовность по дневнику. */
@Composable
private fun StrengthTile(act: MainActivity, onOpen: () -> Unit) {
    val m = act.train
    val a = LocalTrainActions.current
    val cs = MaterialTheme.colorScheme
    val r = m.readiness
    val draft = m.draft
    val planned = m.todayPlanned()
    val (bg, fg) = when {
        draft != null -> cs.tertiaryContainer to cs.onTertiaryContainer
        r?.intensity == Intensity.RECOVERY -> cs.errorContainer to cs.onErrorContainer
        r?.intensity == Intensity.LIGHT -> cs.tertiaryContainer to cs.onTertiaryContainer
        else -> (toneColors(Tone.TRAIN)?.let { it.container to it.onContainer } ?: (cs.secondaryContainer to cs.onSecondaryContainer))
    }
    HubTile(t("Силовая"), Icons.Filled.FitnessCenter, bg, fg, "hub_strength", Modifier.fillMaxWidth(), onOpen) {
        when {
            draft != null -> {
                Big(draft.plan.title)
                Small(t("Тренировка не закончена · шаг %d из %d", draft.step + 1, m.steps(draft.plan).size))
                Button({ m.screen = Screen.Workout }, colors = ButtonDefaults.buttonColors(containerColor = fg, contentColor = bg)) { Text(t("Продолжить")) }
            }
            m.program == null -> {
                Big(t("Программы пока нет"))
                Small(t("Соберите программу под цель, дни и инвентарь"))
                Button({ m.section = SEC_STRENGTH; m.screen = Screen.Generator }, colors = ButtonDefaults.buttonColors(containerColor = fg, contentColor = bg)) {
                    Text(t("Создать программу"))
                }
            }
            else -> {
                Big(planned?.name ?: t("Сегодня отдых"))
                r?.let { Small(if (planned != null) TodayAdvice.verdict(it).title else it.headline) }
                m.history.firstOrNull()?.let { s ->
                    Small(t("Последняя: %s · %d мин", LocalDate.parse(s.date).ruShort(), (s.durationSec / 60).toInt()))
                }
                if (planned != null && r?.let { TodayAdvice.verdict(it) } != Verdict.SKIP) Button(
                    { a?.askNotifications?.invoke(); m.section = SEC_STRENGTH; m.tab = 0; m.start(planned) },
                    colors = ButtonDefaults.buttonColors(containerColor = fg, contentColor = bg),
                ) { Text(t("Начать")) }
            }
        }
    }
}

/** Аэробика: активные минуты недели против нормы ВОЗ (или минуты), идущее занятие. */
@Composable
private fun CardioTile(act: MainActivity, modifier: Modifier, onOpen: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val entries = collect(act.repo.entries)
    val profile = collect(act.repo.profile)
    val today = LocalDate.now()
    val wk = remember(entries, profile) {
        val z = HrZones.of(profile, act.prefs.hrMax, HrZones.latestRest(act.repo.template.value, entries, today), today)
        Cardio.week(Cardio.sessions(act.repo, Cardio.weekStart(today), today), Cardio.weekStart(today), z)
    }
    val running = CardioDraft.parse(act.prefs.cardioDraft)?.takeIf { !it.yoga }
    HubTile(t("Аэробика"), Icons.Filled.Favorite, hubC(Tone.HEART), hubF(Tone.HEART), "hub_cardio", modifier, onOpen) {
        if (running != null) {
            Big(t("Идёт"))
            Small(running.title)
        } else {
            Big("${wk.minutes}", t("мин"))
            Small(if (wk.count == 0) t("за неделю занятий не было") else t("за неделю · занятий: %d", wk.count))
        }
    }
}

/** Йога: минуты за неделю, идущий комплекс, пора ли самопроверка. */
@Composable
private fun YogaTile(act: MainActivity, modifier: Modifier, onOpen: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val entries = collect(act.repo.entries)
    val today = LocalDate.now()
    val wk = remember(entries) { Cardio.week(Cardio.sessions(act.repo, Cardio.weekStart(today), today) { it.kind in Yoga.kinds }, Cardio.weekStart(today), null) }
    val running = CardioDraft.parse(act.prefs.cardioDraft)?.takeIf { it.yoga }
    val due = Yoga.checkDue(Yoga.parseChecks(act.prefs.yogaChecks))
    HubTile(t("Йога"), Icons.Filled.SelfImprovement, hubC(Tone.CALM), hubF(Tone.CALM), "hub_yoga", modifier, onOpen) {
        if (running != null) { Big(t("Идёт")); Small(running.title) }
        else {
            Big("${wk.minutes}", t("мин"))
            Small(if (wk.count == 0) t("за неделю занятий не было") else t("за неделю · занятий: %d", wk.count))
            if (due) Small(t("Пора проверить осанку"))
        }
    }
}

/** Самочувствие: пора ли чекап, общая оценка последнего. */
@Composable
private fun WellbeingTile(act: MainActivity, modifier: Modifier, onOpen: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val checkups = collect(act.repo.checkups)
    val left = Wellbeing.daysLeft(checkups, act.prefs.wbPeriod)
    val takenToday = checkups.any { it.date == LocalDate.now().key() }
    val due = !takenToday && left <= 0
    val draft = act.prefs.wbDraft != null
    val last = checkups.maxByOrNull { it.at }
    val (bg, fg) = if (due || draft) cs.tertiaryContainer to cs.onTertiaryContainer else hubC(Tone.MIND) to hubF(Tone.MIND)
    HubTile(t("Самочувствие"), Icons.Filled.Psychology, bg, fg, "hub_wb", modifier, onOpen) {
        val overall = last?.overall
        if (overall != null) Big("${Math.round(overall)}", t("из 100 · общая оценка"))
        Small(when {
            draft -> t("Чекап не закончен")
            takenToday -> t("Чекап пройден сегодня")
            due -> t("Пора пройти чекап")
            else -> t("Следующий чекап через %d дн.", left)
        })
        if (due || draft) Button({ act.wbStart.value = true }, colors = ButtonDefaults.buttonColors(containerColor = fg, contentColor = bg)) {
            Text(if (draft) t("Продолжить") else t("Пройти"))
        }
    }
}

/**
 * 5.54: «Состояние сегодня» — сон, пульс покоя против обычного, нагрузка за 3 дня и вывод готовности
 * (той же, что подбирает интенсивность силовой). Нет данных — карточки нет.
 */
@Composable
private fun StateCard(act: MainActivity) {
    val entries = collect(act.repo.entries)
    val tmpl = collect(act.repo.template)
    val today = LocalDate.now()
    val day = entries[today.key()].orEmpty()
    val lines = mutableListOf<String>()
    tmpl.firstOrNull { it.source == Source.HC_SLEEP }?.let { parseNumber(day[it.id]) }?.takeIf { it > 0 }?.let { m ->
        val mm = m.toInt()
        lines += t("Сон: %d ч %02d мин", mm / 60, mm % 60) + if (mm < 420) " — " + t("меньше 7 часов") else ""
    }
    val rhrItem = tmpl.firstOrNull { it.source == Source.HC_RESTING_HR }
    rhrItem?.let { parseNumber(day[it.id]) }?.let { r ->
        val past = (1..30).mapNotNull { parseNumber(entries[today.minusDays(it.toLong()).key()]?.get(rhrItem.id)) }.filter { it in 30.0..120.0 }
        val base = past.takeIf { it.size >= 7 }?.sorted()?.let { it[it.size / 2] }
        val ri = Math.round(r).toInt()
        lines += when {
            base == null -> t("Пульс покоя: %d", ri)
            r >= base + 3 -> t("Пульс покоя: %d — на %d выше обычного", ri, Math.round(r - base).toInt())
            r <= base - 3 -> t("Пульс покоя: %d — ниже обычного", ri)
            else -> t("Пульс покоя: %d — как обычно", ri)
        }
    }
    val load = (0..2).mapNotNull { ActiveMin.of(entries, today.minusDays(it.toLong()))?.active }
    if (load.isNotEmpty()) lines += t("Нагрузка за 3 дня: %d акт. мин", load.sum())
    val r = act.train.readiness
    // 5.59: отметки самочувствия сегодня
    val marksOn = DayMarks.isInstalled(tmpl)
    val marks = remember(entries, tmpl) { DayMarks.marked(act.repo, today) }
    val markEff = DayMarks.effect(marks)
    val cs = MaterialTheme.colorScheme
    Card(Modifier.fillMaxWidth().testTag("hub_state"), colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow),
        shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(t("Состояние сегодня"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            r?.let { Text(t(it.headline), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                color = when (it.intensity) { Intensity.RECOVERY -> cs.error; Intensity.LIGHT -> cs.tertiary; else -> cs.primary }) }
                ?: if (markEff != DayMarks.Effect.NONE) Text(if (markEff == DayMarks.Effect.REST) t("Организму нужно восстановление") else t("Лучше облегчённая тренировка"),
                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                    color = if (markEff == DayMarks.Effect.REST) cs.error else cs.tertiary) else Unit
            if (marks.isNotEmpty()) Text(t("Отмечено: %s", marks.joinToString(", ") { it.name.lowercase() }), style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("state_marks"))
            if (marks.any { it.effect == DayMarks.Effect.REST && DayMarks.of(it.id) != null }) Text(t("Температура, кашель, ломота или живот — тренировку лучше отложить; насморк без температуры — можно легко (Mayo Clinic)."),
                style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (!marksOn) {
                Text(t("Отмечайте самочувствие галочками — насморк, голова, усталость, бодрость: совет на тренировку учтёт их, а индекс дня покажет, что на него влияет."),
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                androidx.compose.material3.TextButton({ DayMarks.install(act.repo, act.prefs) }, Modifier.testTag("marks_install")) { Text(t("Добавить отметки самочувствия")) }
            } else if (marks.isEmpty()) Text(t("Самочувствие сегодня не отмечено — плитка «Отметки дня» на «Сегодня», раздел «Самочувствие»."),
                style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        }
    }
}

/** 5.60: сон — последняя ночь и индекс регулярности за 2 недели. */
@Composable
private fun SleepTile(act: MainActivity, modifier: Modifier, onOpen: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val entries = collect(act.repo.entries)
    val nights = remember(entries) { SleepStats.nights(act.repo) }
    val sri = remember(nights) { SleepStats.sri(nights) }
    HubTile(t("Сон"), Icons.Filled.Bedtime, hubC(Tone.SLEEP), hubF(Tone.SLEEP), "hub_sleep", modifier, onOpen) {
        val last = nights.lastOrNull()
        if (last == null) { Big(t("Нет данных")); Small(t("Ночи с часов: время, фазы и регулярность сна")) }
        else {
            val m = last.d.asleep.takeIf { it > 0 } ?: last.d.inBed
            Big("%d:%02d".format(m / 60, m % 60), t("ч сна"))
            Small(Sleep.clock(last.d.start) + "–" + Sleep.clock(last.d.end) + (sri?.let { " · " + t("регулярность %d", it) } ?: ""))
        }
    }
}

/** 5.58: тело — вес с весов, талия, последнее фото. */
@Composable
private fun BodyTile(act: MainActivity, modifier: Modifier, onOpen: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val entries = collect(act.repo.entries)
    val w = remember(entries) { BodyLog.change(BodyLog.scaleSeries(act.repo, "weight")) }
    val waist = remember(entries) { BodyLog.change(BodyLog.series(act.repo, "waist")) }
    val shot = remember(entries) { BodyLog.shots(act.repo).lastOrNull()?.first }
    HubTile(t("Тело"), Icons.Filled.Straighten, hubC(Tone.BODY), hubF(Tone.BODY), "hub_body", modifier, onOpen) {
        if (w == null && waist == null && shot == null) {
            Big(t("Нет данных"))
            Small(t("Вес и состав с весов, замеры лентой и фото прогресса"))
        } else {
            w?.let { Big(formatNumber(Math.round(it.value * 10) / 10.0), t("кг")) }
            Small(listOfNotNull(w?.delta?.let { d -> t("%s за %d дн.", (if (d > 0) "+" else if (d < 0) "−" else "") + formatNumber(Math.abs(Math.round(d * 10) / 10.0)), java.time.temporal.ChronoUnit.DAYS.between(w.since, w.date)) },
                waist?.let { t("талия %s см", formatNumber(Math.round(it.value * 10) / 10.0)) },
                shot?.let { t("фото %s", dateText(it)) } ?: t("фото прогресса нет")).joinToString(" · "))
        }
    }
}

/** 5.54: активность — активные минуты за неделю по пульсу за весь день (без пульса — по занятиям). */
@Composable
private fun ActivityTile(act: MainActivity, modifier: Modifier, onOpen: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val entries = collect(act.repo.entries)
    val today = LocalDate.now()
    val start = Cardio.weekStart(today)
    val byHeart = ActiveMin.weekTotal(entries, start)
    val sessions = remember(entries) { Cardio.week(Cardio.sessions(act.repo, start, today), start, HrZones.of(act.repo, act.prefs)) }
    val total = byHeart ?: sessions.active.takeIf { sessions.activeKnown }
    HubTile(t("Активность"), Icons.AutoMirrored.Filled.DirectionsWalk, hubC(Tone.STEPS), hubF(Tone.STEPS), "hub_activity", modifier, onOpen) {
        if (total == null) {
            Big(t("Нет данных"))
            Small(t("Активные минуты считаются по пульсу с часов или по отметкам после занятий"))
        } else {
            Big("$total", t("из %d активных минут за неделю", Cardio.WEEK_MIN))
            LinearProgressIndicator({ (total / Cardio.WEEK_MIN.toFloat()).coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
            Small(if (byHeart != null) t("по пульсу за весь день · норма ВОЗ 150–300") else t("по записанным занятиям · норма ВОЗ 150–300"))
        }
    }
}

/** 5.70: цвет плитки раздела — категория в цветах Material You или нейтральный, если цветные плитки выключены. */
@Composable
private fun hubC(t: Tone): Color = toneColors(t)?.container ?: MaterialTheme.colorScheme.surfaceContainerHigh

@Composable
private fun hubF(t: Tone): Color = toneColors(t)?.onContainer ?: MaterialTheme.colorScheme.onSurface
