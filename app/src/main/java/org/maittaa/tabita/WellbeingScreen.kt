package org.maittaa.tabita

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

/** Цвет уровня полосы: 0 — норма … 3 — высокий. */
@Composable
fun levelColor(level: Int): Color = when (level) {
    0 -> Color(0xFF2E7D32)
    1 -> Color(0xFFB26A00)
    2 -> Color(0xFFD84315)
    else -> MaterialTheme.colorScheme.error
}

private sealed interface WView {
    data object Home : WView
    data object Select : WView
    data object Quiz : WView
    data class Done(val c: Checkup) : WView
    data class Detail(val blockId: String) : WView
}

@Composable
fun WellbeingScreen(act: MainActivity, modifier: Modifier) {
    val repo = act.repo; val prefs = act.prefs
    val checkups = collect(repo.checkups)
    var view by remember { mutableStateOf<WView>(WView.Home) }
    var draft by remember { mutableStateOf(WDraft.parse(prefs.wbDraft)) }
    var period by remember { mutableIntStateOf(prefs.wbPeriod) }
    val start = collect(act.wbStart)
    LaunchedEffect(start) {
        if (start) { view = if (draft != null) WView.Quiz else WView.Select; act.wbStart.value = false }
    }
    val saveDraft: (WDraft?) -> Unit = { d -> draft = d; prefs.wbDraft = d?.toJson() }

    Box(modifier.fillMaxSize()) {
        when (val v = view) {
            WView.Home -> WHome(
                checkups, draft, period, prefs.wbBlocks,
                onPeriod = { period = it; prefs.wbPeriod = it },
                onStart = { view = WView.Select },
                onContinue = { view = WView.Quiz },
                onRestart = { saveDraft(null); view = WView.Select },
                onDetail = { view = WView.Detail(it) },
                onDelete = { repo.deleteCheckup(it) },
            )
            WView.Select -> WSelect(checkups, prefs.wbBlocks, onBack = { view = WView.Home }) { sel ->
                prefs.wbBlocks = sel
                saveDraft(WDraft.start(sel.toList()))
                Log.i("Wellbeing", "чекап: блоки $sel")
                view = WView.Quiz
            }
            WView.Quiz -> draft?.let { d ->
                WQuiz(d, period, onChange = saveDraft, onClose = { view = WView.Home }) { res ->
                    val c = Checkup(LocalDate.now().key(), System.currentTimeMillis(), res, period)
                    repo.addCheckup(c); saveDraft(null)
                    view = WView.Done(c)
                }
            } ?: run { view = WView.Home }
            is WView.Done -> WDone(v.c, checkups) { view = WView.Home }
            is WView.Detail -> WDetail(repo, checkups, v.blockId) { view = WView.Home }
        }
    }
}

// ---------- главная ----------

@Composable
private fun WHome(
    checkups: List<Checkup>, draft: WDraft?, period: Int, selected: Set<String>,
    onPeriod: (Int) -> Unit, onStart: () -> Unit, onContinue: () -> Unit, onRestart: () -> Unit,
    onDetail: (String) -> Unit, onDelete: (Long) -> Unit,
) {
    val left = Wellbeing.daysLeft(checkups, period)
    val takenToday = checkups.any { it.date == LocalDate.now().key() }
    val due = !takenToday && left <= 0
    val sel = Wellbeing.blocks.filter { it.id in selected }
    val n = sel.sumOf { it.questions.size }
    var deleting by remember { mutableStateOf<Checkup?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item("head") {
            Column(Modifier.padding(horizontal = 4.dp)) {
                Text(t("Самочувствие"), style = MaterialTheme.typography.headlineSmall)
                Text(t("Короткие проверенные опросники. Это скрининг, а не диагноз; правильных ответов нет."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item("start") {
            val acc = due || takenToday
            Card(colors = CardDefaults.cardColors(
                containerColor = if (acc) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Psychology, null, Modifier.size(28.dp),
                            tint = if (acc) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t("Чекап самочувствия"), style = MaterialTheme.typography.titleMedium)
                            Text(
                                when { takenToday -> t("Пройден сегодня"); due -> t("Пора пройти"); else -> t("Следующий через %d дн.", left) },
                                style = MaterialTheme.typography.labelLarge,
                                color = if (acc) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(sel.joinToString(" · ") { it.title.s } + "\n" + t("%d вопросов · ~%d мин", n, maxOf(1, (n + 5) / 6)),
                        style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(10.dp))
                    if (draft != null) {
                        val done = draft.picks.count { it >= 0 }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onContinue) { Text(t("Продолжить (%d из %d)", done, draft.picks.size)) }
                            TextButton(onClick = onRestart) { Text(t("Заново")) }
                        }
                    } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onStart) { Text(if (takenToday) t("Пройти ещё раз") else t("Начать")) }
                    }
                }
            }
        }
        item("period") {
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(t("Повторять каждые"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                listOf(7, 14, 30).forEach { d -> FilterChip(selected = period == d, onClick = { onPeriod(d) }, label = { Text(t("%d дн.", d)) }) }
            }
            Text(t("Вопросы будут о том, как было за %s.", Wellbeing.periodPhrase(period)), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        }
        val withData = Wellbeing.blocks.filter { b -> checkups.any { b.id in it.results } }
        item("resHead") {
            Text(
                t("Результаты") + (checkups.maxOfOrNull { it.date }?.let { " · " + LocalDate.parse(it).ruShort() } ?: ""),
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp, top = 8.dp),
            )
        }
        if (withData.isEmpty()) item("noRes") {
            Text(t("Результаты появятся после первого чекапа."), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(4.dp))
        }
        checkups.maxByOrNull { it.at }?.let { last -> item("total") { TotalCard(last, checkups) } }
        withData.forEach { b -> item("r_" + b.id) { ResultRow(b, checkups) { onDetail(b.id) } } }
        if (checkups.isNotEmpty()) {
            item("histHead") {
                Text(t("История"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
            }
            checkups.sortedByDescending { it.at }.forEach { c ->
                item("h_" + c.at) {
                    Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(LocalDate.parse(c.date).ruShort(), Modifier.width(80.dp), style = MaterialTheme.typography.bodyMedium)
                        Text(c.results.keys.mapNotNull { Wellbeing.block(it)?.title?.s }.joinToString(", "), Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { deleting = c }) { Icon(Icons.Filled.Delete, t("Удалить"), tint = MaterialTheme.colorScheme.outline) }
                    }
                }
            }
        }
        item("pad") { Spacer(Modifier.height(24.dp)) }
    }
    deleting?.let { c ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(t("Удалить прохождение?")) },
            text = { Text(LocalDate.parse(c.date).ruShort()) },
            confirmButton = { TextButton(onClick = { onDelete(c.at); deleting = null }) { Text(t("Удалить"), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(t("Отмена")) } },
        )
    }
}

@Composable
fun BandChip(b: WBlock, v: Double) {
    val band = b.band(v); val c = levelColor(band.level)
    Surface(shape = RoundedCornerShape(8.dp), color = c.copy(alpha = 0.14f)) {
        Text(band.name.s, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelMedium, color = c, maxLines = 1)
    }
}

/** Строка результата блока: балл, полоса, изменение, полоска. */
@Composable
private fun ResultRow(b: WBlock, checkups: List<Checkup>, onClick: () -> Unit) {
    val (last, prev) = Wellbeing.lastTwo(checkups, b.id)
    val v = last?.results?.get(b.id)?.score ?: return
    val p = prev?.results?.get(b.id)?.score
    val level = b.band(v).level
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(b.title.s, style = MaterialTheme.typography.titleSmall)
                    Text(
                        t("%s из %s", b.format(v), b.format(b.max)) + (p?.let { " · " + t("было %s", b.format(it)) } ?: "") +
                            " · " + LocalDate.parse(last.date).ruShort(),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BandChip(b, v)
                if (p != null && Math.abs(v - p) > 1e-9) {
                    val better = if (b.higherIsWorse) v < p else v > p
                    Text((if (v > p) " ▲" else " ▼") + b.format(Math.abs(v - p)), style = MaterialTheme.typography.labelLarge,
                        color = if (better) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
            }
            Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                Box(Modifier.fillMaxWidth(b.frac(v).coerceAtLeast(0.02f)).height(5.dp).clip(RoundedCornerShape(3.dp)).background(levelColor(level)))
            }
        }
    }
}

/** Общая оценка 0–100 по последнему прохождению и изменение с предыдущего. */
@Composable
private fun TotalCard(last: Checkup, checkups: List<Checkup>) {
    val v = last.overall ?: return
    val prev = checkups.filter { it.at < last.at && it.overall != null }.maxByOrNull { it.at }?.overall
    val b = Wellbeing.total
    val band = b.band(v)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ScoreRing(Math.round(v).toInt(), levelColor(band.level), size = 64)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(t("Общая оценка самочувствия"), style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BandChip(b, v)
                    prev?.let { p ->
                        val d = v - p
                        if (Math.abs(d) >= 0.5) Text((if (d > 0) "  ▲" else "  ▼") + Math.round(Math.abs(d)), style = MaterialTheme.typography.labelLarge,
                            color = if (d > 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error)
                    }
                }
                Text(t("100 — всё хорошо; среднее по %d шкалам", last.results.size), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---------- выбор блоков ----------

@Composable
private fun WSelect(checkups: List<Checkup>, initial: Set<String>, onBack: () -> Unit, onStart: (Set<String>) -> Unit) {
    var sel by remember { mutableStateOf(initial) }
    val n = Wellbeing.blocks.filter { it.id in sel }.sumOf { it.questions.size }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Назад")) }
            Text(t("Что проверить"), style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            Wellbeing.blocks.forEach { b ->
                val last = Wellbeing.lastTwo(checkups, b.id).first
                val stale = last != null && java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(last.date), LocalDate.now()) > 30
                Row(Modifier.fillMaxWidth().clickable { sel = if (b.id in sel) sel - b.id else sel + b.id }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(b.title.s, style = MaterialTheme.typography.titleMedium)
                        Text(b.about.s + " · " + b.scale + " · " + t("%d вопр.", b.questions.size),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (stale && b.id !in sel) Text(t("давно не проходили"), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary)
                    }
                    Switch(b.id in sel, { on -> sel = if (on) sel + b.id else sel - b.id })
                }
                HorizontalDivider()
            }
        }
        Surface(tonalElevation = 3.dp) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(t("Блоков: %d · %d вопросов · ~%d мин", sel.size, n, maxOf(1, (n + 5) / 6)), Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge)
                Button(onClick = { onStart(sel) }, enabled = sel.isNotEmpty()) { Text(t("Начать")) }
            }
        }
    }
}

// ---------- прохождение ----------

@Composable
private fun WQuiz(d0: WDraft, period: Int, onChange: (WDraft) -> Unit, onClose: () -> Unit, onFinish: (Map<String, BlockResult>) -> Unit) {
    var d by remember { mutableStateOf(d0) }
    val qs = remember(d.blocks) { d.questions }
    val pos = d.pos.coerceIn(0, qs.size - 1)
    val (b, qi) = qs[pos]
    val q = b.questions[qi]
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, t("Закрыть, ответы сохранятся")) }
            Text(t("%s · %d из %d", b.title.s, qi + 1, b.questions.size), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text("${pos + 1}/${qs.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(progress = { (pos + 1f) / qs.size }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(b.prompt.s(period), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Text(q.text.s(period), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(20.dp))
            q.answers.forEachIndexed { i, ans ->
                val chosen = d.picks[pos] == i
                val pick = {
                    val p = d.picks.toMutableList().also { it[pos] = i }
                    val next = d.copy(picks = p, pos = if (pos < qs.size - 1) pos + 1 else pos)
                    d = next; onChange(next)
                    if (pos == qs.size - 1) next.results()?.let(onFinish)
                }
                if (chosen) Button(onClick = pick, Modifier.fillMaxWidth().padding(vertical = 4.dp).height(52.dp)) { Text(ans.text.s(period)) }
                else OutlinedButton(onClick = pick, Modifier.fillMaxWidth().padding(vertical = 4.dp).height(52.dp)) { Text(ans.text.s(period)) }
            }
        }
        Row {
            if (pos > 0) TextButton(onClick = { val n = d.copy(pos = pos - 1); d = n; onChange(n) }) { Text(t("Назад")) }
            Spacer(Modifier.weight(1f))
            // вернулся назад и поменял ответ — можно идти дальше к первому пропуску
            val gap = d.picks.indexOfFirst { it < 0 }
            if (gap > pos) TextButton(onClick = { val n = d.copy(pos = gap); d = n; onChange(n) }) { Text(t("Дальше")) }
            else if (gap < 0 && pos < qs.size - 1) TextButton(onClick = { d.results()?.let(onFinish) }) { Text(t("Завершить")) }
        }
    }
}

// ---------- итог ----------

@Composable
private fun WDone(c: Checkup, checkups: List<Checkup>, onHome: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(t("Готово"), style = MaterialTheme.typography.headlineSmall)
        Text(t("Спасибо, что нашли время. Вот что получилось:"), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        TotalCard(c, checkups.ifEmpty { listOf(c) })
        Wellbeing.blocks.filter { it.id in c.results }.forEach { b -> ResultRow(b, checkups.ifEmpty { listOf(c) }) {} }
        if (c.results.any { (k, r) -> Wellbeing.block(k)?.let { it.band(r.score).level >= it.adviseLevel } == true }) AdviceNote()
        Button(onClick = onHome, Modifier.fillMaxWidth()) { Text(t("К результатам")) }
    }
}

@Composable
private fun AdviceNote() {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            t("Если так держится неделями, стоит обсудить это с врачом или психологом — с этим работают, и обычно успешно. Это скрининг, а не диагноз."),
            Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

// ---------- подробно ----------

@Composable
private fun WDetail(repo: Repo, checkups: List<Checkup>, blockId: String, onBack: () -> Unit) {
    val b = Wellbeing.block(blockId) ?: return onBack()
    val list = checkups.filter { b.id in it.results }.sortedBy { it.at }
    val last = list.lastOrNull() ?: return onBack()
    val r = last.results[b.id]!!
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Назад")) }
            Text(b.title.s + " · " + b.scale, style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(b.about.s.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("%s из %s", b.format(r.score), b.format(b.max)), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(10.dp)); BandChip(b, r.score)
                Spacer(Modifier.weight(1f))
                Text(LocalDate.parse(last.date).ruShort(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            WChart(b, list)
            Text(b.bands.joinToString(" · ") { "${b.format(it.from)}+ ${it.name.s}" }, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (b.approximate) Text(t("У этой шкалы нет официальных порогов — полосы ориентировочные, важнее сравнение с собой."),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Answers(b, r, last.period)
            DiaryContext(repo, list)
            if (b.band(r.score).level >= b.adviseLevel) AdviceNote()
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun WChart(b: WBlock, list: List<Checkup>) {
    val line = MaterialTheme.colorScheme.primary
    val measurer = rememberTextMeasurer()
    val style = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val bandColors = b.bands.map { levelColor(it.level).copy(alpha = 0.10f) }
    val vals = list.map { it.results[b.id]!!.score }
    Canvas(Modifier.fillMaxWidth().height(160.dp)) {
        val l = 28.dp.toPx(); val bot = 16.dp.toPx(); val top = 4.dp.toPx()
        val w = size.width - l; val h = size.height - bot - top
        fun y(v: Double) = top + h - (v / b.max * h).toFloat()
        b.bands.forEachIndexed { i, band ->
            val hi = b.bands.getOrNull(i + 1)?.from ?: b.max
            if (hi > band.from) drawRect(bandColors[i], Offset(l, y(hi)), Size(w, y(band.from) - y(hi)))
            val tl = measurer.measure(b.format(band.from), style)
            drawText(tl, topLeft = Offset(0f, (y(band.from) - tl.size.height).coerceIn(0f, size.height - tl.size.height)))
        }
        val n = vals.size
        fun x(i: Int) = if (n == 1) l + w / 2 else l + 10.dp.toPx() + (w - 20.dp.toPx()) * i / (n - 1)
        val p = Path()
        vals.forEachIndexed { i, v -> if (i == 0) p.moveTo(x(i), y(v)) else p.lineTo(x(i), y(v)) }
        drawPath(p, line, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        vals.forEachIndexed { i, v -> drawCircle(line, 4.dp.toPx(), Offset(x(i), y(v))) }
        listOf(0, n - 1).distinct().forEach { i ->
            val tl = measurer.measure(LocalDate.parse(list[i].date).ruShort(), style)
            drawText(tl, topLeft = Offset((x(i) - tl.size.width / 2).coerceIn(l, size.width - tl.size.width), size.height - tl.size.height))
        }
    }
}

/** Вопросы с самыми «тяжёлыми» ответами (для «Ресурса» — с самыми низкими). */
@Composable
private fun Answers(b: WBlock, r: BlockResult, period: Int) {
    val items = b.questions.mapIndexed { i, q ->
        val raw = q.answers[r.picks[i]].score; val mx = q.answers.maxOf { it.score }
        val sev = (if (q.reverse) mx - raw else raw).toFloat() / mx
        Triple(q, if (b.higherIsWorse) sev else 1f - sev, q.answers[r.picks[i]].text.s(period))
    }.filter { it.second > 0f }.sortedByDescending { it.second }.take(4)
    if (items.isEmpty()) return
    Text(if (b.higherIsWorse) t("Что сильнее всего") else t("Что проседает"), style = MaterialTheme.typography.titleSmall)
    items.forEach { (q, f, ans) ->
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(q.text.s(period), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(8.dp))
                Canvas(Modifier.width(72.dp).height(6.dp)) {
                    drawRoundRect(Color.Gray.copy(alpha = 0.2f), cornerRadius = CornerRadius(3.dp.toPx()))
                    drawRoundRect(if (f >= 0.67f) Color(0xFFD84315) else Color(0xFFB26A00), size = Size(size.width * f, size.height), cornerRadius = CornerRadius(3.dp.toPx()))
                }
            }
            Text(ans, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Сон, тренировки и оценка дня за 2 недели до прохождения — против двух недель до прошлого. */
@Composable
private fun DiaryContext(repo: Repo, list: List<Checkup>) {
    val tmpl = repo.template.value; val entries = repo.entries.value
    val sleep = tmpl.firstOrNull { it.source == Source.HC_SLEEP }
    val ex = tmpl.firstOrNull { it.source == Source.HC_EXERCISE }
    val tr = tmpl.firstOrNull { it.id == "tr" && it.type == ItemType.CHECK }
    val rate = tmpl.firstOrNull { it.id == "rate" && it.type == ItemType.SCALE } ?: tmpl.firstOrNull { it.type == ItemType.SCALE }
    fun window(c: Checkup) = (1..14).map { LocalDate.parse(c.date).minusDays(it.toLong()).key() }.map { entries[it] ?: emptyMap() }
    fun stats(c: Checkup): Triple<Double?, Int, Double?> {
        val w = window(c)
        val s = sleep?.let { i -> w.mapNotNull { parseNumber(it[i.id]) } }?.takeIf { it.isNotEmpty() }?.average()
        val wk = w.count { d -> (ex?.let { parseNumber(d[it.id]) } ?: 0.0) > 0 || (tr != null && d[tr.id] != null) }
        val rt = rate?.let { i -> w.mapNotNull { parseNumber(it[i.id]) } }?.takeIf { it.isNotEmpty() }?.average()
        return Triple(s, wk, rt)
    }
    val cur = stats(list.last()); val prev = list.getOrNull(list.size - 2)?.let { stats(it) }
    if (cur.first == null && cur.second == 0 && cur.third == null) return
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(t("2 недели до прохождения по дневнику"), style = MaterialTheme.typography.titleSmall)
            fun line(label: String, v: String?, p: String?) = if (v != null)
                label + ": " + v + (p?.let { "  (" + t("было %s", it) + ")" } ?: "") else null
            listOfNotNull(
                line(t("Сон в среднем"), cur.first?.let { formatMinutes(it) }, prev?.first?.let { formatMinutes(it) }),
                line(t("Дней с тренировкой"), cur.second.toString(), prev?.second?.toString()),
                line(t("Оценка дня"), cur.third?.let { formatNumber(Math.round(it * 10) / 10.0) }, prev?.third?.let { formatNumber(Math.round(it * 10) / 10.0) }),
            ).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/** Плитка чекапа в развёрнутом виде (на экране «День»). */
@Composable
fun WellbeingTileCard(item: TemplateItem, st: WbStatus, onStart: () -> Unit) {
    val acc = st.accent
    Card(colors = CardDefaults.cardColors(
        containerColor = if (acc) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Psychology, null, Modifier.size(28.dp),
                tint = if (acc) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        st.takenToday != null -> t("Пройден сегодня") + " · " + wbBrief(st.takenToday)
                        st.due -> t("Пора пройти") + " · " + t("%d вопросов · ~%d мин", st.questions, maxOf(1, (st.questions + 5) / 6))
                        else -> t("через %d дн.", st.daysLeft)
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (st.takenToday == null) Button(onClick = onStart) { Text(t("Пройти")) }
        }
    }
}
