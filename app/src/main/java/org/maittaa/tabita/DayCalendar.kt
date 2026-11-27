package org.maittaa.tabita

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.time.LocalDate

/** Заполненность дня для календаря (5.22). */
enum class DayFill { NONE, PARTIAL, FULL }

object DayFillCalc {
    /** Есть ли в дне хоть одна запись (служебные ключи вида "id!..." не считаются). */
    fun hasAny(day: Map<String, String>?) = day?.any { (k, v) -> '!' !in k && v.isNotBlank() } == true

    /**
     * Полный — записано всё, что входит в индекс дня; неполный — что-то записано, но не всё;
     * пусто — ничего. Без настроенного индекса любая запись считается полным днём.
     */
    fun month(template: List<TemplateItem>, entries: Map<String, Map<String, String>>, month: LocalDate, cfg: MonthCfg,
              today: LocalDate = LocalDate.now()): Map<LocalDate, DayFill> {
        val md = MonthData(template, entries, month, cfg, today)
        return md.days.filter { it <= today }.associateWith { d ->
            val di = md.dayIndex(d)
            val any = hasAny(entries[d.key()]) || di.recorded > 0
            when {
                !any -> DayFill.NONE
                di.total == 0 || di.missing.isEmpty() -> DayFill.FULL
                else -> DayFill.PARTIAL
            }
        }
    }
}

/** Календарь выбора дня с отметками: ● заполнен, ○ не до конца, без точки — пропуск. */
@Composable
fun DayCalendarDialog(
    selected: LocalDate, template: List<TemplateItem>, entries: Map<String, Map<String, String>>, cfg: MonthCfg,
    onDismiss: () -> Unit, onPick: (LocalDate) -> Unit,
) {
    val today = LocalDate.now()
    var month by remember { mutableStateOf(selected.withDayOfMonth(1)) }
    val fill = remember(month, template, entries, cfg) { DayFillCalc.month(template, entries, month, cfg, today) }
    val cs = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), tonalElevation = 6.dp) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                Text(t("Выберите дату"), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text(monthTitle(month), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 8.dp))
                    IconButton({ month = month.minusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, t("Назад")) }
                    IconButton({ month = month.plusMonths(1) }, enabled = month < today.withDayOfMonth(1)) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, t("Вперёд"))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    (if (I18n.isEn) listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su") else listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")).forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant) }
                }
                var drag = 0f
                Column(Modifier.pointerInput(month) {
                    detectHorizontalDragGestures(onDragStart = { drag = 0f }, onDragEnd = {
                        if (drag > 80) month = month.minusMonths(1)
                        else if (drag < -80 && month < today.withDayOfMonth(1)) month = month.plusMonths(1)
                    }) { _, dx -> drag += dx }
                }) {
                    val lead = month.dayOfWeek.value - 1
                    val n = month.lengthOfMonth()
                    val rows = (lead + n + 6) / 7
                    for (r in 0 until rows) Row(Modifier.fillMaxWidth()) {
                        for (c in 0 until 7) {
                            val i = r * 7 + c - lead + 1
                            Box(Modifier.weight(1f).aspectRatio(0.9f), contentAlignment = Alignment.Center) {
                                if (i in 1..n) {
                                    val d = month.withDayOfMonth(i)
                                    DayCell(d, d == selected, d == today, d <= today, fill[d] ?: DayFill.NONE) { onPick(d) }
                                }
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, top = 4.dp)) {
                    Dot(DayFill.FULL, cs.primary); Text(" " + t("заполнен"), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Dot(DayFill.PARTIAL, cs.primary); Text(" " + t("не до конца"), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    TextButton({ onPick(today) }) { Text(t("Сегодня")) }
                }
            }
        }
    }
}

@Composable
private fun Dot(f: DayFill, c: Color) {
    val m = Modifier.size(7.dp).clip(CircleShape)
    when (f) {
        DayFill.FULL -> Box(m.background(c))
        DayFill.PARTIAL -> Box(m.border(BorderStroke(1.5.dp, c), CircleShape))
        DayFill.NONE -> Box(m)
    }
}

@Composable
private fun DayCell(d: LocalDate, sel: Boolean, today: Boolean, enabled: Boolean, fill: DayFill, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.size(44.dp).clip(CircleShape)
            .then(if (sel) Modifier.background(cs.primary) else if (today) Modifier.border(1.dp, cs.primary, CircleShape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text("${d.dayOfMonth}", style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (today) FontWeight.SemiBold else null,
            color = when { sel -> cs.onPrimary; !enabled -> cs.onSurface.copy(alpha = 0.38f); today -> cs.primary; else -> cs.onSurface })
        Spacer(Modifier.height(2.dp))
        Dot(fill, if (sel) cs.onPrimary else cs.primary)
    }
}
