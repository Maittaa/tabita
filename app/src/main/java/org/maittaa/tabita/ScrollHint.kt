package org.maittaa.tabita

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Подсказка, что ряд крутится вбок (5.28): край, за которым есть ещё содержимое, плавно гаснет,
 * и на нём — маленькая стрелка. Ставится до horizontalScroll — рисуется по видимой области.
 */
fun Modifier.scrollHint(s: ScrollState, arrow: Color, fade: Float = 28f): Modifier =
    scrollHint({ s.canScrollBackward }, { s.canScrollForward }, arrow, fade)

/** То же для списков (LazyRow): [back] / [fwd] — можно ли ещё крутить назад / вперёд. */
fun Modifier.scrollHint(back0: () -> Boolean, fwd0: () -> Boolean, arrow: Color, fade: Float = 28f): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val f = fade.dp.toPx().coerceAtMost(size.width / 3)
        val back = back0(); val fwd = fwd0()
        if (back) drawRect(Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = 0f, endX = f), blendMode = BlendMode.DstOut)
        if (fwd) drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = size.width - f, endX = size.width), blendMode = BlendMode.DstOut)
        val a = 4.dp.toPx(); val cy = size.height / 2; val st = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        if (back) {
            val x = 4.dp.toPx() + a
            drawPath(Path().apply { moveTo(x, cy - a * 1.6f); lineTo(x - a, cy); lineTo(x, cy + a * 1.6f) }, arrow, style = st)
        }
        if (fwd) {
            val x = size.width - 4.dp.toPx() - a
            drawPath(Path().apply { moveTo(x, cy - a * 1.6f); lineTo(x + a, cy); lineTo(x, cy + a * 1.6f) }, arrow, style = st)
        }
    }

/** Горизонтальная прокрутка с подсказкой по краям. */
@Composable
fun Modifier.hScroll(s: ScrollState = rememberScrollState()): Modifier =
    this.scrollHint(s, MaterialTheme.colorScheme.onSurfaceVariant).horizontalScroll(s)
