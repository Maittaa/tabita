package org.maittaa.tabita

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val WaterBlue = Color(0xFF3D8BD9)

/** Литры красиво: 1,25 л */
fun formatLiters(l: Double): String = formatNumber(Math.round(l * 100) / 100.0) + " " + t("л")

@Composable
fun WaterBlock(repo: Repo, prefs: Prefs, item: TemplateItem, date: String, day: Map<String, String>) {
    var auto by remember { mutableStateOf(prefs.waterAuto) }
    var tick by remember { mutableStateOf(0) }
    // 5.42: норма дня считается сама (вес, тренировки, шаги) и сохраняется в день
    val calc = remember(date, day, tick) { runCatching { Hydration.calc(repo, prefs, date, day) }.getOrNull() }
    LaunchedEffect(date, calc?.total, auto) { Hydration.updateNorms(repo, prefs, listOf(date)) }
    val norm = if (auto && calc != null) calc.total else Hydration.normOf(item, if (auto) day else null)
    val stored = parseNumber(day[item.id]) ?: 0.0
    var cur by remember(date) { mutableFloatStateOf(stored.toFloat()) }
    var pushed by remember(date) { mutableStateOf(stored) }
    if (stored != pushed) { pushed = stored; cur = stored.toFloat() }
    var showHint by remember { mutableStateOf(false) }
    var editNorm by remember { mutableStateOf(false) }
    fun save(l: Double) {
        val v = (Math.round(l * 20) / 20.0).coerceAtLeast(0.0)  // шаг 50 мл
        pushed = v; cur = v.toFloat()
        repo.setValue(date, item.id, if (v == 0.0) null else v.toString())
    }
    val top = maxOf(norm, cur.toDouble()).toFloat()
    val pct = (cur / norm * 100).roundToInt()
    val fill by animateFloatAsState((cur / norm).toFloat().coerceIn(0f, 1f), label = "water")

    SectionCard(item.displayName, action = {
        TextButton(onClick = { editNorm = true }) { Text((if (auto && calc != null) t("Норма %s · авто", formatLiters(norm)) else t("Норма %s", formatLiters(norm)))) }
    }) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(formatLiters(cur.toDouble()), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = WaterBlue)
            Spacer(Modifier.width(8.dp))
            Text(t("из %s · %d%%", formatLiters(norm), pct), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
            Spacer(Modifier.weight(1f))
            if (cur >= norm) Text("✓ " + t("норма"), color = Color(0xFF3FA66B), fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 4.dp))
        }
        if (auto && calc != null && calc.workoutLiters >= 0.05) Text(
            t("С тренировками: +%s к обычной норме", formatLiters(Math.round(calc.workoutLiters * 10) / 10.0)),
            style = MaterialTheme.typography.bodySmall, color = WaterBlue)
        Spacer(Modifier.height(6.dp))
        // «стакан»: заполнение к норме
        Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(WaterBlue.copy(alpha = 0.15f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fill).clip(RoundedCornerShape(7.dp)).background(WaterBlue))
        }
        Slider(
            value = cur, onValueChange = { cur = ((it * 20).roundToInt() / 20f) },
            onValueChangeFinished = { save(cur.toDouble()) },
            valueRange = 0f..top,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { save(cur - 0.25) }, enabled = cur > 0f,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)) { Text("−250") }
            FilledTonalButton(onClick = { save(cur + 0.25) }, Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)) {
                Icon(Icons.Filled.WaterDrop, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(t("+ стакан 250"))
            }
            FilledTonalButton(onClick = { save(cur + 0.5) }, Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)) { Text(t("+ 500 мл")) }
        }
        Row(Modifier.fillMaxWidth().clickable { showHint = !showHint }.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.HelpOutline, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text(t("Из чего норма"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        AnimatedVisibility(showHint) {
            WaterBreakdown(prefs, calc, auto) { tick++ }
        }
    }

    if (editNorm) {
        var s by remember { mutableStateOf(formatNumber(item.max.takeIf { it > 0 } ?: 2.0)) }
        var a by remember { mutableStateOf(auto) }
        AlertDialog(
            onDismissRequest = { editNorm = false },
            title = { Text(t("Норма воды в день")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t("Считать автоматически"), Modifier.weight(1f))
                        Switch(a, { a = it })
                    }
                    Text(
                        if (a) t("Каждый день: вес × 30 мл + тренировки + шаги сверх обычного. Нужен вес — с весов или пункта «Вес».")
                        else t("Одна норма на все дни."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!a) OutlinedTextField(s, { s = it }, singleLine = true, suffix = { Text(t("л")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    prefs.waterAuto = a; auto = a
                    if (!a) parseNumber(s)?.takeIf { it in 0.3..10.0 }?.let { repo.upsertItem(item.copy(max = it)); Log.i("Water", "норма: $it л") }
                    Hydration.updateNorms(repo, prefs, listOf(date))
                    Log.i("Water", "норма автоматически: $a")
                    editNorm = false
                }) { Text(t("Сохранить")) }
            },
            dismissButton = { TextButton(onClick = { editNorm = false }) { Text(t("Отмена")) } },
        )
    }
}

/** Расклад нормы и личная потеря пота. */
@Composable
private fun WaterBreakdown(prefs: Prefs, a: Hydration.Calc?, auto: Boolean, changed: () -> Unit) {
    val small = MaterialTheme.typography.bodySmall
    Column(Modifier.padding(top = 6.dp).clip(RoundedCornerShape(10.dp))
        .background(WaterBlue.copy(alpha = 0.08f)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        if (a == null) {
            Text(t("Добавьте вес (весы или пункт «Вес»), и норма посчитается автоматически."), style = small)
        } else {
            Text(t("Вес %s кг × 30 мл = %s", formatNumber(Math.round(a.weight * 10) / 10.0), formatLiters(Math.round(a.base * 10) / 10.0)),
                style = small, fontWeight = FontWeight.SemiBold)
            a.workouts.forEach { p ->
                Text(t("%s %d мин → + %s", Workouts.kind(p.session.kind).title, p.session.min, formatLiters(Math.round(p.liters * 10) / 10.0)),
                    style = small, fontWeight = FontWeight.SemiBold)
            }
            if (a.stepsLiters >= 0.05) Text(t("Шаги сверх %s: + %s → + %s", formatNumber(Hydration.STEPS_USUAL),
                formatNumber(Math.round(a.stepsOver).toDouble()), formatLiters(Math.round(a.stepsLiters * 10) / 10.0)), style = small, fontWeight = FontWeight.SemiBold)
            Text(t("Итого на день ≈ %s", formatLiters(a.total)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = WaterBlue)
            if (!auto) Text(t("Сейчас норма задана вручную — включите «Считать автоматически» в окне нормы."), style = small)
        }
        Spacer(Modifier.height(4.dp))
        val (rate, personal) = Hydration.sweatRate(prefs)
        Text(
            if (personal) t("Ваша потеря с потом: %s л/ч (по замерам до и после тренировки)", formatNumber(rate))
            else t("Потеря с потом взята средняя: %s л/ч. У разных людей — от 0,5 до 2 л/ч.", formatNumber(rate)),
            style = small, fontWeight = FontWeight.SemiBold,
        )
        Text(t("Как узнать свою: встаньте на весы прямо перед тренировкой и сразу после, до того как попьёте. Tabita сама найдёт эти взвешивания в openScale и посчитает."),
            style = small)
        val samples = Hydration.samples(prefs)
        if (samples.isNotEmpty()) {
            samples.takeLast(3).reversed().forEach { s ->
                Text("• " + t("%s: %s %d мин, −%s кг → %s л/ч", s.date, Workouts.kind(s.kind).title.lowercase(), s.min,
                    formatNumber(s.lossKg), formatNumber(s.rate)), style = small)
            }
            TextButton(onClick = { Hydration.clearSamples(prefs); changed() }) { Text(t("Сбросить замеры")) }
        }
        Text(t("Это общий ориентир, не медицинская рекомендация. В жару нужно больше. При болезнях почек или сердца норму воды обсудите с врачом."),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
