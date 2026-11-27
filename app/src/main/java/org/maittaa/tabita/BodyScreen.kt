package org.maittaa.tabita

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

private fun cm(v: Double) = formatNumber(Math.round(v * 10) / 10.0)
private fun signed1(v: Double) = (if (v > 0.04) "+" else if (v < -0.04) "−" else "") + formatNumber(Math.abs(Math.round(v * 10) / 10.0))

/** 5.58: «Тело» — вкладки Замеры · Фото. */
@Composable
fun BodyScreen(act: MainActivity, tab: Int, modifier: Modifier) {
    if (tab == 0) BodyMeasures(act, modifier) else BodyPhotos(act, modifier)
}

@Composable
private fun Section(title: String, tag: String? = null, content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().let { if (tag != null) it.testTag(tag) else it }) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

@Composable
private fun BodyMeasures(act: MainActivity, modifier: Modifier) {
    val repo = act.repo; val cs = MaterialTheme.colorScheme
    val entries = collect(repo.entries); val profile = collect(repo.profile)
    var sel by remember { mutableStateOf<String?>(null) }
    var edit by remember { mutableStateOf(false) }
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ---- с весов ----
        val scale = remember(entries) { BodyLog.scaleKeys.associateWith { BodyLog.change(BodyLog.scaleSeries(repo, it)) } }
        Section(t("С весов"), "body_scale") {
            if (scale.values.all { it == null }) Text(t("Взвешиваний пока нет. Подключите весы через openScale или Health Connect в «Ещё» — или впишите вес в дневник."),
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            val names = mapOf("weight" to t("Вес"), "body_fat" to t("Жир"), "muscle" to t("Мышцы"), "lbm" to t("Безжировая масса"),
                "water" to t("Вода"), "visceral_fat" to t("Висцеральный жир"))
            val units = mapOf("weight" to t("кг"), "body_fat" to "%", "muscle" to "%", "lbm" to t("кг"), "water" to "%", "visceral_fat" to "")
            scale.forEach { (k, c) ->
                if (c != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(names[k] ?: k, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Column(horizontalAlignment = Alignment.End) {
                        Text(cm(c.value) + (units[k]?.let { if (it.isEmpty()) "" else " $it" } ?: ""), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(listOfNotNull(dateText(c.date), c.delta?.let { d -> t("%s с %s", signed1(d), dateText(c.since!!)) }).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    }
                }
            }
            if (scale["muscle"] != null) Text(t("«Мышцы» и «жир» у бытовых весов — оценка по сопротивлению тела, она зависит от воды: сравнивайте взвешивания в одно время, утром натощак."),
                style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        }
        // ---- замеры лентой ----
        val tape = remember(entries) { BodyLog.measures.associate { it.key to BodyLog.series(repo, it.key) } }
        Section(t("Замеры лентой"), "body_tape") {
            if (tape.values.all { it.isEmpty() }) Text(t("Замеров пока нет. При наборе мышц замеры показывают, что именно растёт — один вес этого не покажет."),
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            BodyLog.measures.forEach { m ->
                val c = BodyLog.change(tape[m.key].orEmpty())
                if (c != null) Row(Modifier.fillMaxWidth().clickable { sel = if (sel == m.key) null else m.key }.padding(vertical = 2.dp).testTag("tape_${m.key}"),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(m.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (sel == m.key) FontWeight.SemiBold else FontWeight.Normal)
                    Column(horizontalAlignment = Alignment.End) {
                        Text(t("%s см", cm(c.value)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(listOfNotNull(dateText(c.date), c.delta?.let { d -> t("%s с %s", signed1(d), dateText(c.since!!)) }).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    }
                }
            }
            sel?.let { k ->
                val pts = tape[k].orEmpty()
                if (pts.size >= 2) org.maittaa.tabita.train.LineChart(pts, Modifier.padding(top = 4.dp), format = { cm(it) })
                else Text(t("График появится со вторым замером."), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
            Button({ edit = true }, Modifier.fillMaxWidth().testTag("tape_edit")) {
                Icon(Icons.Filled.Straighten, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Записать замеры"))
            }
            Text(t("Мерьте в одно время, лучше утром до еды, лента плотно, но не сдавливая. Раз в 2–4 недели — чаще изменения тонут в погрешности."),
                style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        }
        // ---- талия к росту ----
        val waist = BodyLog.change(tape["waist"].orEmpty())
        val h = profile.h
        if (waist != null && h != null) {
            val r = BodyLog.whtr(waist.value, h)
            val band = BodyLog.whtrBand(r)
            Section(t("Талия к росту")) {
                Text(String.format(java.util.Locale.US, "%.2f", r).let { if (I18n.isEn) it else it.replace('.', ',') }, Modifier.testTag("whtr"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
                    color = if (band > 0) cs.error else cs.primary)
                Text(when (band) {
                    0 -> t("Ниже 0,5 — без повышенного риска по жиру на животе.")
                    1 -> t("0,5–0,59 — повышенный риск: жир на животе связан с диабетом 2 типа и болезнями сердца.")
                    else -> t("От 0,6 — высокий риск. Стоит обсудить с врачом.")
                }, style = MaterialTheme.typography.bodySmall)
                Text(t("Ориентир NICE (Великобритания, 2022): талия меньше половины роста. Талия %s см, рост %s см.", cm(waist.value), cm(h * 100)),
                    style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
        }
        // ---- осанка ----
        val checks = remember(entries) { Yoga.parseChecks(act.prefs.yogaChecks) }
        Section(t("Осанка")) {
            val last = checks.lastOrNull()
            if (last == null) Text(t("Самопроверок ещё не было: стойка у стены, наклон вперёд, замок рук за спиной."), style = MaterialTheme.typography.bodySmall)
            else {
                Text(t("Последняя проверка — %s", dateText(LocalDate.parse(last.date))), style = MaterialTheme.typography.bodyMedium)
                Text(listOf(
                    t("у стены: %s", last.wall.joinToString(", ") { Yoga.wallName(it).lowercase() }.ifEmpty { t("ничего не касается") }),
                    t("наклон: %d см до пола", last.bend),
                    Yoga.claspNames[last.clasp.coerceIn(0, 2)].lowercase(),
                ).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            TextButton({ act.train.section = org.maittaa.tabita.train.SEC_YOGA; act.train.tab = 0 }) {
                Text(if (Yoga.checkDue(checks)) t("Проверить осанку") else t("Открыть йогу"))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    if (edit) TapeDialog(act) { edit = false }
}

/** Ввод замеров за сегодня: пустое поле — замер не записывается. */
@Composable
private fun TapeDialog(act: MainActivity, onClose: () -> Unit) {
    val repo = act.repo
    val today = LocalDate.now().key()
    val cur = remember { BodyLog.parseTape(repo.entries.value[today]?.get(BodyLog.TAPE)) }
    var vals by remember { mutableStateOf(BodyLog.measures.associate { m -> m.key to (cur[m.key]?.let { cm(it) } ?: "") }) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(t("Замеры за сегодня")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BodyLog.measures.forEach { m ->
                    OutlinedTextField(vals[m.key] ?: "", { v -> vals = vals + (m.key to v.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(5)) },
                        Modifier.fillMaxWidth().testTag("tape_in_${m.key}"), label = { Text(t("%s, см", m.name)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        supportingText = { Text(m.how, style = MaterialTheme.typography.labelSmall) })
                }
            }
        },
        confirmButton = { TextButton({
            val m = vals.mapNotNull { (k, v) -> parseNumber(v.replace(',', '.'))?.takeIf { it in 10.0..250.0 }?.let { k to it } }.toMap()
            repo.setValue(today, BodyLog.TAPE, BodyLog.tapeToString(m))
            Log.i("Body", "замеры: $m"); onClose()
        }, Modifier.testTag("tape_save")) { Text(t("Сохранить")) } },
        dismissButton = { TextButton(onClose) { Text(t("Отмена")) } },
    )
}

@Composable
private fun BodyPhotos(act: MainActivity, modifier: Modifier) {
    val repo = act.repo; val cs = MaterialTheme.colorScheme
    val entries = collect(repo.entries)
    val shots = remember(entries) { BodyLog.shots(repo) }
    val today = LocalDate.now()
    val todayShot = shots.firstOrNull { it.first == today }?.second
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Section(t("Фото сегодня"), "body_today") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PhotoSlot(act, t("Спереди"), todayShot?.front, Modifier.weight(1f)) { BodyLog.setShot(repo, today, false, it) }
                PhotoSlot(act, t("Сбоку"), todayShot?.side, Modifier.weight(1f)) { BodyLog.setShot(repo, today, true, it) }
            }
            Text(t("Для сравнения: одно место и свет, та же одежда, телефон на одной высоте и расстоянии, стоя расслабленно. Раз в 2–4 недели."),
                style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        }
        if (shots.size >= 2) {
            var a by remember(shots) { mutableStateOf(shots.first().first) }
            var b by remember(shots) { mutableStateOf(shots.last().first) }
            Section(t("Сравнение"), "body_compare") {
                Text(t("Было"), style = MaterialTheme.typography.labelMedium)
                Row(Modifier.hScroll(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    shots.forEach { (d, _) -> FilterChip(a == d, { a = d }, label = { Text(dateText(d)) }) }
                }
                Text(t("Стало"), style = MaterialTheme.typography.labelMedium)
                Row(Modifier.hScroll(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    shots.forEach { (d, _) -> FilterChip(b == d, { b = d }, label = { Text(dateText(d)) }) }
                }
                val sa = shots.first { it.first == a }.second; val sb = shots.first { it.first == b }.second
                listOf(t("Спереди") to (sa.front to sb.front), t("Сбоку") to (sa.side to sb.side)).forEach { (name, p) ->
                    if (p.first != null || p.second != null) {
                        Text(name, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(p.first to a, p.second to b).forEach { (f, d) ->
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (f != null) PhotoImage(File(repo.photosDir, f), 900, Modifier.fillMaxWidth().aspectRatio(0.75f).clip(RoundedCornerShape(12.dp)))
                                    else Box(Modifier.fillMaxWidth().aspectRatio(0.75f), contentAlignment = Alignment.Center) { Text(t("нет фото"), color = cs.onSurfaceVariant) }
                                    Text(dateText(d), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
                val days = java.time.temporal.ChronoUnit.DAYS.between(a, b)
                val w = remember(entries, a, b) {
                    val s = BodyLog.scaleSeries(repo, "weight", 3650)
                    val wa = s.lastOrNull { !it.first.isAfter(a) }?.second; val wb = s.lastOrNull { !it.first.isAfter(b) }?.second
                    if (wa != null && wb != null) wb - wa else null
                }
                Text(listOfNotNull(t("%d дн. между фото", Math.abs(days)), w?.let { t("вес %s кг", signed1(it)) }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        } else if (shots.size == 1 && todayShot == null) Text(t("Одно фото есть — сделайте второе, и появится сравнение."), style = MaterialTheme.typography.bodySmall)
        Text(t("Фото хранятся только в приложении и попадают в его резервную копию."), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
    }
}

/** Ячейка фото: снимок с кнопкой «убрать» или кнопки «Камера» / «Галерея». */
@Composable
private fun PhotoSlot(act: MainActivity, title: String, file: String?, modifier: Modifier, onSet: (String?) -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope(); val cs = MaterialTheme.colorScheme
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var ask by remember { mutableStateOf(false) }
    fun import(u: Uri?) { u ?: return; scope.launch { Photos.import(ctx, act.repo.photosDir, u)?.let(onSet) ?: android.widget.Toast.makeText(ctx, t("Не удалось загрузить фото"), android.widget.Toast.LENGTH_SHORT).show() } }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { import(it) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) import(cameraUri) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        if (file != null) Box {
            PhotoImage(File(act.repo.photosDir, file), 900, Modifier.fillMaxWidth().aspectRatio(0.75f).clip(RoundedCornerShape(12.dp)))
            IconButton({ ask = true }, Modifier.align(Alignment.TopEnd)) { Icon(Icons.Filled.Close, t("Убрать фото"), tint = cs.onPrimary) }
        } else Surface(color = cs.surfaceContainerHighest, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().aspectRatio(0.75f)) {
            Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                FilledTonalButton({
                    val dir = File(ctx.cacheDir, "camera").apply { mkdirs() }
                    val u = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", File(dir, "body_${System.currentTimeMillis()}.jpg"))
                    cameraUri = u
                    runCatching { camera.launch(u) }.onFailure { android.widget.Toast.makeText(ctx, t("Камера недоступна"), android.widget.Toast.LENGTH_SHORT).show() }
                }) { Icon(Icons.Filled.PhotoCamera, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(t("Камера")) }
                TextButton({ gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Icon(Icons.Filled.PhotoLibrary, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(t("Галерея"))
                }
            }
        }
    }
    if (ask) AlertDialog(
        onDismissRequest = { ask = false },
        title = { Text(t("Убрать фото?")) },
        text = { Text(t("Фото удалится из приложения.")) },
        confirmButton = { TextButton({ onSet(null); ask = false }) { Text(t("Убрать"), color = cs.error) } },
        dismissButton = { TextButton({ ask = false }) { Text(t("Отмена")) } },
    )
}
