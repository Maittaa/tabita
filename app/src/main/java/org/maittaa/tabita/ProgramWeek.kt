package org.maittaa.tabita

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private fun n1(v: Double) = formatNumber(Math.round(v * 10) / 10.0)
private fun signed(v: Double, digits: Int = 1): String {
    val r = if (digits == 0) Math.round(v).toDouble() else Math.round(v * 10) / 10.0
    return (if (r > 0) "+" else if (r < 0) "−" else "") + formatNumber(Math.abs(r))
}
internal fun dateText(d: LocalDate, today: LocalDate = LocalDate.now()) =
    if (d.year == today.year) d.ruShort() else "%02d.%02d.%d".format(d.dayOfMonth, d.monthValue, d.year)

@Composable
private fun Line(k: String, v: String, ok: Boolean? = null, sub: String? = null) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
        Text(k, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Column(horizontalAlignment = Alignment.End) {
            Text(v + when (ok) { true -> " ✓"; false -> " ↓"; null -> "" }, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium, color = if (ok == false) cs.error else cs.onSurface, textAlign = TextAlign.End)
            sub?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, textAlign = TextAlign.End) }
        }
    }
}

// ---------------- «Цель»: прогноз ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForecastFold(act: MainActivity, g: Goal, stats: PlanStats, entries: Any?) {
    val repo = act.repo; val prefs = act.prefs; val cs = MaterialTheme.colorScheme
    var tick by remember { mutableIntStateOf(0) }
    val muscle = g == Goal.MUSCLE
    val tr = remember(entries) { Forecast.weightTrend(repo) }
    val lean = remember(entries) { if (muscle) Forecast.leanTrend(repo) else null }
    val band = Forecast.band(g) ?: return
    val w = stats.weight
    val pct = if (tr != null && w != null && w > 0) tr.perWeek / w * 100 else null
    val summary = tr?.let { t("%s кг в неделю за 4 недели", signed(it.perWeek, 1).let { s -> if (Math.abs(it.perWeek) < 0.05) "0" else s }) }
        ?: t("нужно 6+ взвешиваний за 2 недели")
    Fold(t("Прогноз"), summary) {
        if (tr == null || pct == null) {
            Text(t("Прогноз строится по тренду веса: взвешивайтесь утром натощак хотя бы через день — через 2 недели появится темп и даты вех."),
                style = MaterialTheme.typography.bodySmall)
            return@Fold
        }
        val inBand = pct in band
        Line(t("Темп"), t("%s кг/нед · %s%%", signed(tr.perWeek, 1), signed(pct, 1)), inBand,
            if (g == Goal.LOSE) t("ориентир −0,5…1 % веса в неделю") else t("ориентир +0,25…0,5 % веса в неделю (Iraki и др., 2019)"))
        Line(t("Вес по тренду"), t("%s кг", n1(tr.now)), null, t("по %d взвешиваниям", tr.n))
        // вехи и даты
        val w0 = prefs.planW0.takeIf { it > 0 } ?: tr.now
        val targets: List<Pair<String, Double>>
        val base: Trend
        if (muscle && lean != null && prefs.planLean0 > 0) {
            targets = listOf(1.0, 2.0, 4.0).map { d -> t("+%s кг безжировой массы", n1(d)) to prefs.planLean0 + d }; base = lean
            Line(t("Безжировая масса"), t("%s кг · %s кг/нед", n1(lean.now), signed(lean.perWeek, 2)))
        } else { targets = Plan.weightTargets(g, w0, stats.h); base = tr }
        val sign = if (g == Goal.LOSE) -1 else 1
        val open = targets.filter { (it.second - base.now) * sign > 0 }
        if (open.isNotEmpty()) {
            Text(t("Когда дойдёте при нынешнем темпе"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
            Forecast.etas(open, base).forEach { e ->
                Line(e.name, e.date?.let { t("≈ %s", dateText(it)) } ?: t("не приближается"), if (e.date == null) false else null)
            }
        } else if (muscle) Text(t("Даты вех для мышц появятся со взвешиваниями на весах с составом тела. Пока ориентир — темп веса."),
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        // своя дата
        val due = remember(tick) { prefs.planDue?.let { runCatching { LocalDate.parse(it) }.getOrNull() } }
        val goalT = open.lastOrNull()
        var pick by remember { mutableStateOf(false) }
        if (goalT != null) {
            Spacer(Modifier.height(6.dp))
            if (due != null) {
                val need = Forecast.need(goalT.second, due, base)
                if (need == null) Text(t("Дата %s уже близко — выберите другую.", dateText(due)), style = MaterialTheme.typography.bodySmall, color = cs.error)
                else {
                    Text(t("Чтобы к %s дойти до «%s»: %s кг в неделю", dateText(due), goalT.first, signed(need.perWeek, 2)), style = MaterialTheme.typography.bodyMedium)
                    val needPct = if (base === tr) need.perWeek / tr.now * 100 else null
                    val msg = when {
                        needPct != null && needPct * sign > Math.abs(if (sign > 0) band.endInclusive else band.start) ->
                            t("Это быстрее ориентира — реальнее сдвинуть дату.")
                        Math.abs(need.kcalDelta) < 50 -> t("Нынешний темп как раз подходит.")
                        else -> t("Это ≈ %s ккал в день к тому, что сейчас.", signed(need.kcalDelta.toDouble(), 0))
                    }
                    Text(msg, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
            Row {
                TextButton({ pick = true }, Modifier.testTag("due_pick")) { Text(if (due == null) t("Задать свою дату") else t("Сменить дату")) }
                if (due != null) TextButton({ prefs.planDue = null; tick++ }) { Text(t("Убрать дату")) }
            }
        }
        if (pick) {
            val init = (due ?: LocalDate.now().plusMonths(3)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            val st = rememberDatePickerState(initialSelectedDateMillis = init)
            DatePickerDialog(onDismissRequest = { pick = false }, confirmButton = {
                TextButton({
                    st.selectedDateMillis?.let { ms -> prefs.planDue = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                    pick = false; tick++
                }) { Text(t("Готово")) }
            }, dismissButton = { TextButton({ pick = false }) { Text(t("Отмена")) } }) { DatePicker(st) }
        }
        Text(t("Прогноз — прямая по взвешиваниям за 4 недели; вес колеблется на 0,5–1 кг за день, поэтому даты уточняются каждую неделю."),
            style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
    }
}

// ---------------- «Цель»: недельный шаблон ----------------

private fun dayShort(d: DayOfWeek) = t(listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")[d.value - 1])

@Composable
fun WeekTplFold(act: MainActivity, g: Goal, stats: PlanStats) {
    val prefs = act.prefs; val cs = MaterialTheme.colorScheme
    var tpl by remember { mutableStateOf(WeekTpl.of(prefs)) }
    fun save(n: WeekTpl) { tpl = n; prefs.weekTpl = n.toJson(); Log.d("Plan", "шаблон недели: ${n.toJson()}") }
    val sDays = remember(act.train.program, act.train.genSettings) { WeekPlan.strength() }
    val summary = t("силовых %d · аэробных %d · йоги %d", sDays.size, tpl.count(WeekTpl.CARDIO), tpl.count(WeekTpl.YOGA))
    Fold(t("Неделя тренировок"), summary) {
        val rows = listOf(WeekTpl.STRENGTH to t("Силовая"), WeekTpl.CARDIO to t("Аэробная"), WeekTpl.YOGA to t("Йога"))
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(80.dp))
            DayOfWeek.entries.forEach { d -> Text(dayShort(d), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, color = cs.onSurfaceVariant) }
        }
        rows.forEach { (k, name) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.width(80.dp), style = MaterialTheme.typography.bodyMedium)
                DayOfWeek.entries.forEach { d ->
                    val on = if (k == WeekTpl.STRENGTH) d in sDays else k in tpl.on(d)
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        val m = Modifier.size(28.dp).testTag("wk_${k}_${d.value}")
                            .let { if (k == WeekTpl.STRENGTH) it else it.clickable { save(tpl.toggle(d, k)) } }
                            .background(if (on) cs.primary else cs.surface, CircleShape)
                            .border(1.dp, if (on) cs.primary else cs.outlineVariant, CircleShape)
                        Box(m)
                    }
                }
            }
        }
        Text(t("Силовая — по дням программы; поменять их можно в «Здоровье → Силовая». Аэробную и йогу отмечайте нажатием."),
            style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.height(6.dp))
        Text(t("Аэробная, мин"), style = MaterialTheme.typography.bodyMedium)
        Stepper(tpl.cardioMin, { save(tpl.copy(cardioMin = it.coerceIn(10, 120))) }, step = 5, big = 15, unit = t("мин"))
        Spacer(Modifier.height(4.dp))
        Text(t("Активных минут в неделю"), style = MaterialTheme.typography.bodyMedium)
        Stepper(tpl.activeGoal, { save(tpl.copy(activeGoal = it.coerceIn(30, 600))) }, step = 10, big = 30, unit = t("мин"))
        Text(t("ВОЗ: 150–300 мин умеренной нагрузки в неделю, минута высокой — за две."),
            style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        WeekPlan.note(tpl)?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)) }
        WeekPlan.warnings(tpl, sDays).forEach {
            Text("• $it", style = MaterialTheme.typography.bodySmall, color = cs.error, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(6.dp))
        Program.trainingPlan(g).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp)) }
        Text(t("Аэробная и йога из плана появляются в «Плане дня» на «Ритме дня»: аэробная — в привычное время или после силовой, йога — за час до сна."),
            style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        TextButton({ act.openTrain.value = true }) { Text(t("Открыть тренировки")) }
    }
}

// ---------------- «Неделя» ----------------

@Composable
fun WeekScreen(act: MainActivity, modifier: Modifier) {
    val repo = act.repo; val prefs = act.prefs; val cs = MaterialTheme.colorScheme
    val entries = collect(repo.entries); val profile = collect(repo.profile)
    val today = LocalDate.now()
    val cur = Cardio.weekStart(today)
    var start by remember { mutableStateOf(cur) }
    var tick by remember { mutableIntStateOf(0) }
    val hist = act.train.history
    val rows = remember(entries, hist) { HealthHistory.rows(act, 120) }
    val rv = remember(start, entries, rows, profile, tick) { WeekReview.of(repo, prefs, start, rows) }
    val goal = Goal.of(prefs.planGoal)
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton({ start = start.minusWeeks(1) }, Modifier.testTag("wk_prev")) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, t("Раньше")) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (start == cur) t("Эта неделя") else if (start == cur.minusWeeks(1)) t("Прошлая неделя") else t("Неделя"),
                    style = MaterialTheme.typography.titleMedium)
                Text("${dateText(start)} – ${dateText(start.plusDays(6))}", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            }
            IconButton({ if (start < cur) start = start.plusWeeks(1) }, enabled = start < cur) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, t("Позже")) }
        }
        // ---- обзор ----
        Surface(color = cs.surfaceContainer, shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Text(t("Обзор"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 4.dp))
                Line(t("Вес"), rv.weight?.let { t("%s кг", n1(it)) } ?: "—", null,
                    listOfNotNull(
                        rv.weight?.let { w -> rv.weightPrev?.let { p -> t("%s кг к прошлой неделе", signed(w - p, 1)) } },
                        rv.trend?.let { t("тренд %s кг/нед", signed(it.perWeek, 2)) },
                    ).joinToString(" · ").ifEmpty { null })
                if (goal != null && Program.kcalMatters(goal)) Line(t("Калории"), rv.kcal?.let { t("%s в среднем", formatNumber(Math.round(it).toDouble())) } ?: t("не записаны"),
                    null, listOfNotNull(rv.kcalTarget?.let { t("норма %s", formatNumber(Math.round(it).toDouble())) }, t("записано %d из %d дн.", rv.kcalDays, rv.days)).joinToString(" · "))
                if (rv.protein != null) Line(t("Белок в норме"), t("%d из %d дн.", rv.proteinOk, rv.proteinLogged),
                    if (rv.proteinLogged >= 3) rv.proteinOk >= rv.proteinLogged * 0.7 else null, t("норма %s г", formatNumber(Math.round(rv.protein).toDouble())))
                fun cnt(c: WeekReview.Count) = if (c.plan > 0) t("%d из %d", c.done, c.plan) else c.done.toString()
                fun ok(c: WeekReview.Count) = if (c.plan > 0 && rv.days == 7) c.done >= c.plan else if (c.plan > 0 && c.done >= c.plan) true else null
                Line(t("Силовая"), cnt(rv.strength), ok(rv.strength))
                Line(t("Аэробная"), cnt(rv.cardio), ok(rv.cardio))
                Line(t("Йога"), cnt(rv.yoga), ok(rv.yoga))
                Line(t("Активные минуты"), rv.active?.let { t("%d из %d", it, rv.activeGoal) } ?: "—",
                    rv.active?.let { if (it >= rv.activeGoal) true else if (rv.days == 7) false else null },
                    rv.active?.let { if (rv.activeHr) t("по пульсу с часов") else t("по занятиям, без пульса") })
                Line(t("Сон"), rv.sleep?.let { formatDuration(it) } ?: "—", rv.sleep?.let { it >= 420 },
                    listOfNotNull(rv.sleep?.let { t("в среднем за %d ноч.", rv.sleepNights) }, rv.bedSpread?.let { t("отбой ± %d мин", it) }).joinToString(" · ").ifEmpty { null })
            }
        }
        // ---- правки ----
        Surface(color = cs.secondaryContainer, shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (start == cur) t("Что поправить") else t("Правки на следующую неделю"), style = MaterialTheme.typography.titleSmall, color = cs.onSecondaryContainer)
                if (rv.fixes.isEmpty()) Text(if (goal == null) t("Выберите цель во вкладке «Цель» — правки будут под неё.") else t("Правок нет — неделя идёт по плану."),
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSecondaryContainer)
                rv.fixes.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, color = cs.onSecondaryContainer) }
            }
        }
        // ---- поправка калорий ----
        if (goal != null && Program.kcalMatters(goal)) KcalAdjustCard(act, entries) { tick++ }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun KcalAdjustCard(act: MainActivity, entries: Any?, changed: () -> Unit) {
    val prefs = act.prefs; val cs = MaterialTheme.colorScheme
    var tick by remember { mutableIntStateOf(0) }
    val r = remember(entries) { Adapt.estimate(act.repo) }
    val adj = remember(tick) { prefs.burnAdj }
    Surface(color = cs.surfaceContainer, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(t("Реальный расход"), style = MaterialTheme.typography.titleSmall)
            if (!r.ok) {
                Text(t("Пока не хватает данных: %s.", r.missing ?: ""), style = MaterialTheme.typography.bodyMedium)
                Text(t("Нужны 4 недели: еда записана почти каждый день и вес — лучше каждое утро. Тогда по съеденному и тренду веса посчитаю, сколько вы тратите на самом деле, и предложу поправку нормы."),
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            } else {
                val tdee = r.tdee!!; val model = r.model!!; val tr = r.trend!!
                Line(t("Съедали в среднем"), t("%s ккал", formatNumber(Math.round(r.intake!!).toDouble())), null, t("за %d дн. из 28", r.intakeDays))
                Line(t("Вес"), t("%s кг/нед", signed(tr.perWeek, 2)), null, t("по %d взвешиваниям", r.weighIns))
                Line(t("Реальный расход"), t("≈ %s ккал", formatNumber(Math.round(tdee / 10) * 10.0)), null,
                    t("расчёт по профилю и шагам — %s", formatNumber(Math.round(model / 10) * 10.0)))
                val sug = Adapt.suggestion(r, adj)
                if (sug != null) Button({ Adapt.accept(prefs, sug); tick++; changed() }, Modifier.testTag("kcal_accept")) {
                    Text(t("Принять поправку %s ккал", signed(sug.toDouble(), 0)))
                }
                else Text(t("Норма совпадает с реальным расходом — поправка не нужна."), style = MaterialTheme.typography.bodySmall)
            }
            if (adj != 0) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("Принята поправка %s ккал%s", signed(adj.toDouble(), 0), prefs.burnAdjAt?.let { d -> runCatching { " · " + t("с %s", dateText(LocalDate.parse(d))) }.getOrDefault("") } ?: ""),
                    Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton({ Adapt.accept(prefs, 0); tick++; changed() }) { Text(t("Сбросить")) }
            }
            Text(t("Метод: расход = съеденное − изменение веса × 7700 ккал/кг (Hall и Chow, 2011: нужно 4+ недели данных). Если записываете не всю еду, расход выйдет заниженным."),
                style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        }
    }
}

// ---------------- «Цель»: список покупок (5.57) ----------------

@Composable
fun ShoppingFold(act: MainActivity, g: Goal, tg: Program.Targets, r: DayRhythm?) {
    val prefs = act.prefs; val cs = MaterialTheme.colorScheme
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val lines = remember(tg, r, g) { Shopping.lines(Shopping.weekMenu(g, tg, r, prefs)) }
    var st by remember { mutableStateOf(Shopping.state(prefs)) }
    fun upd(n: Shopping.State) { st = n; Shopping.save(prefs, n) }
    val done = lines.count { it.buyFood.key in st.checked } + st.extra.count { it.done }
    val total = lines.size + st.extra.size
    Fold(t("Список покупок на неделю"), t("%d позиций · куплено %d", total, done)) {
        lines.groupBy { Shopping.groupName(it.food.cat) }.forEach { (gname, l) ->
            Text(gname, style = MaterialTheme.typography.labelLarge, color = cs.primary, modifier = Modifier.padding(top = 6.dp))
            l.forEach { ln ->
                val k = ln.buyFood.key; val on = k in st.checked
                Row(Modifier.fillMaxWidth().clickable { upd(st.copy(checked = if (on) st.checked - k else st.checked + k)) }.testTag("shop_$k"),
                    verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(on, null)
                    Text(ln.buyFood.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                        color = if (on) cs.onSurfaceVariant else cs.onSurface,
                        textDecoration = if (on) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
                    Text(ln.amount(), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                }
            }
        }
        if (st.extra.isNotEmpty()) Text(t("Своё"), style = MaterialTheme.typography.labelLarge, color = cs.primary, modifier = Modifier.padding(top = 6.dp))
        st.extra.forEachIndexed { i, e ->
            Row(Modifier.fillMaxWidth().clickable { upd(st.copy(extra = st.extra.mapIndexed { j, x -> if (j == i) x.copy(done = !x.done) else x })) },
                verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Checkbox(e.done, null)
                Text(e.text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                    textDecoration = if (e.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
                IconButton({ upd(st.copy(extra = st.extra.filterIndexed { j, _ -> j != i })) }) {
                    Icon(Icons.Filled.Close, t("Удалить"))
                }
            }
        }
        var add by remember { mutableStateOf("") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.OutlinedTextField(add, { add = it.take(60) }, Modifier.weight(1f).testTag("shop_add"),
                placeholder = { Text(t("Добавить своё: хлеб, кофе…")) }, singleLine = true)
            IconButton({ if (add.isNotBlank()) { upd(st.copy(extra = st.extra + Shopping.Extra(add.trim(), false))); add = "" } }) {
                Icon(Icons.Filled.Add, t("Добавить"))
            }
        }
        Row {
            TextButton({
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(android.content.Intent.EXTRA_TEXT, Shopping.shareText(lines, st))
                runCatching { ctx.startActivity(android.content.Intent.createChooser(send, t("Список покупок"))) }
                Log.i("Food", "список покупок отправлен")
            }) { Text(t("Поделиться")) }
            if (done > 0) TextButton({ upd(st.copy(checked = emptySet(), extra = st.extra.map { it.copy(done = false) })) }) { Text(t("Снять отметки")) }
        }
        Text(t("По меню программы на 7 дней — каждый день свой вариант приёмов. Гречка, рис и макароны — в сухом весе (пересчёт по калорийности из справочника), остальное — вес готового продукта: мясо и рыба сырыми весят больше. Отметки сбрасываются в понедельник."),
            style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
    }
}
