package org.maittaa.tabita

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding

/**
 * Интерактивные графики (5.28), как в Google Health: ведёшь пальцем по графику — вертикальная линия
 * и подпись с датой и значением. Горизонтальное ведение не мешает прокрутке страницы; касание ставит
 * линию, повторное касание рядом — убирает. [x] — координата касания в пикселях холста.
 */
fun Modifier.chartScrub(x: MutableState<Float?>): Modifier = this
    // ключ — само состояние: после смены показателя в карточке создаётся новое, и жесты должны писать в него (5.38)
    .pointerInput(x) {
        detectTapGestures { p ->
            val cur = x.value
            x.value = if (cur != null && kotlin.math.abs(cur - p.x) < 24.dp.toPx()) null else p.x
        }
    }
    .pointerInput(x) {
        detectHorizontalDragGestures(onDragStart = { x.value = it.x }) { change, _ ->
            change.consume(); x.value = change.position.x
        }
    }

/** Индекс точки, ближайшей к [tx] по горизонтали; [xs] — координаты точек (null — пропуск). */
fun nearestIndex(tx: Float, xs: List<Float?>): Int? =
    xs.indices.filter { xs[it] != null }.minByOrNull { kotlin.math.abs(xs[it]!! - tx) }

/** Рисует линию в [x], кружки в точках [ys] и подпись [lines] сверху (внутри области [left]..ширина). */
fun DrawScope.drawScrub(
    x: Float, top: Float, bottom: Float, ys: List<Pair<Float, Color>>, lines: List<String>,
    measurer: TextMeasurer, style: TextStyle, lineColor: Color, bg: Color, border: Color, left: Float = 0f,
) {
    drawLine(lineColor, Offset(x, top), Offset(x, bottom), 1.5.dp.toPx())
    ys.forEach { (y, c) ->
        drawCircle(bg, 5.5.dp.toPx(), Offset(x, y))
        drawCircle(c, 4.dp.toPx(), Offset(x, y))
    }
    if (lines.isEmpty()) return
    val tl = measurer.measure(lines.joinToString("\n"), style)
    val padH = 6.dp.toPx(); val padV = 3.dp.toPx()
    val w = tl.size.width + padH * 2; val h = tl.size.height + padV * 2
    // подпись — сбоку от линии, чтобы не закрывать точку; у края — с другой стороны
    val gap = 8.dp.toPx()
    val bx = if (x + gap + w <= size.width) x + gap else (x - gap - w).coerceAtLeast(left)
    val by = top
    drawRoundRect(bg, Offset(bx, by), Size(w, h), CornerRadius(6.dp.toPx()))
    drawRoundRect(border, Offset(bx, by), Size(w, h), CornerRadius(6.dp.toPx()), style = Stroke(1.dp.toPx()))
    drawText(tl, topLeft = Offset(bx + padH, by + padV))
}


/**
 * Вкладки-разделы со значком и короткой подписью (5.38): одинаковой ширины, подпись не обрезается и не вылезает —
 * для «Программы» и «Тренировок», чтобы шапки были в одном стиле.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@androidx.compose.runtime.Composable
fun IconTabRow(selected: Int, items: List<Pair<String, androidx.compose.ui.graphics.vector.ImageVector>>, onSelect: (Int) -> Unit) {
    val cs = androidx.compose.material3.MaterialTheme.colorScheme
    androidx.compose.material3.PrimaryTabRow(selected) {
        items.forEachIndexed { i, (label, icon) ->
            val on = i == selected
            androidx.compose.material3.Tab(on, { onSelect(i) },
                selectedContentColor = cs.primary, unselectedContentColor = cs.onSurfaceVariant) {
                androidx.compose.foundation.layout.Column(
                    Modifier.padding(top = 8.dp, bottom = 8.dp),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                ) {
                    androidx.compose.material3.Icon(icon, null, Modifier.padding(bottom = 2.dp))
                    // подпись ужимается только если не влезает (крупный шрифт в системе, «Самочувствие»)
                    androidx.compose.foundation.text.BasicText(label, Modifier.padding(horizontal = 4.dp), maxLines = 1, softWrap = false,
                        color = { if (on) cs.primary else cs.onSurfaceVariant },
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium.copy(
                            letterSpacing = androidx.compose.ui.unit.TextUnit(0f, androidx.compose.ui.unit.TextUnitType.Sp),
                            fontWeight = if (on) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Medium),
                        autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(
                            minFontSize = androidx.compose.ui.unit.TextUnit(8f, androidx.compose.ui.unit.TextUnitType.Sp),
                            maxFontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp)))
                }
            }
        }
    }
}
