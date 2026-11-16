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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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

/** Ориентир нормы: 30 мл на кг веса + 0,5 л на каждый час тренировки. */
data class WaterAdvice(val weight: Double?, val exerciseMin: Double?, val base: Double?, val extra: Double, val total: Double?)

fun waterAdvice(repo: Repo, date: String, day: Map<String, String>): WaterAdvice {
    val w = repo.latestWeight(date)
    val ex = repo.template.value.filter { it.source == Source.HC_EXERCISE }.firstNotNullOfOrNull { parseNumber(day[it.id]) }
    val base = w?.let { it * 0.030 }
    val extra = (ex ?: 0.0) / 60.0 * 0.5
    val total = base?.let { Math.round((it + extra) * 10) / 10.0 }
    return WaterAdvice(w, ex, base, extra, total)
}

@Composable
fun WaterBlock(repo: Repo, item: TemplateItem, date: String, day: Map<String, String>) {
    val norm = item.max.takeIf { it > 0 } ?: 2.0
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
        TextButton(onClick = { editNorm = true }) { Text(t("Норма %s", formatLiters(norm))) }
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
        // подсказка по расчёту нормы
        Row(Modifier.fillMaxWidth().clickable { showHint = !showHint }.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.HelpOutline, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text(t("Как рассчитать норму?"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        AnimatedVisibility(showHint) {
            val a = waterAdvice(repo, date, day)
            Column(Modifier.padding(top = 6.dp).clip(RoundedCornerShape(10.dp))
                .background(WaterBlue.copy(alpha = 0.08f)).padding(10.dp)) {
                Text(t("Ориентир: около 30 мл воды на 1 кг веса в сутки, плюс примерно 0,5 л на каждый час тренировки. В жару и при сильной потливости нужно больше."),
                    style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp))
                if (a.weight != null && a.base != null) {
                    Text(t("Ваш вес %s кг × 30 мл = %s", formatNumber(Math.round(a.weight * 10) / 10.0), formatLiters(a.base)),
                        style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    if (a.extra > 0) Text(t("Тренировка %s мин → + %s", formatNumber(Math.round(a.exerciseMin ?: 0.0).toDouble()), formatLiters(a.extra)),
                        style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    Text(t("Итого на сегодня ≈ %s", formatLiters(a.total!!)), style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold, color = WaterBlue)
                    if (Math.abs(a.total - norm) >= 0.05) TextButton(onClick = { repo.upsertItem(item.copy(max = a.total)) }) {
                        Text(t("Сделать нормой %s", formatLiters(a.total)))
                    }
                } else Text(t("Добавьте вес (весы или пункт «Вес»), и норма посчитается автоматически."), style = MaterialTheme.typography.bodySmall)
                Text(t("Это общий ориентир, не медицинская рекомендация. При болезнях почек или сердца норму воды обсудите с врачом."),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp))
            }
        }
    }

    if (editNorm) {
        var s by remember { mutableStateOf(formatNumber(norm)) }
        AlertDialog(
            onDismissRequest = { editNorm = false },
            title = { Text(t("Норма воды в день")) },
            text = {
                OutlinedTextField(s, { s = it }, singleLine = true, suffix = { Text(t("л")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            },
            confirmButton = {
                TextButton(onClick = {
                    parseNumber(s)?.takeIf { it in 0.3..10.0 }?.let { repo.upsertItem(item.copy(max = it)); Log.i("Water", "норма: $it л") }
                    editNorm = false
                }) { Text(t("Сохранить")) }
            },
            dismissButton = { TextButton(onClick = { editNorm = false }) { Text(t("Отмена")) } },
        )
    }
}
