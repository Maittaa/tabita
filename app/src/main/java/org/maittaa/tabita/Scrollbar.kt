package org.maittaa.tabita

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Полоса прокрутки справа, как в меню приложений Pixel Launcher:
 * появляется при прокрутке, за неё можно взять и быстро пролистать.
 * [fraction] — положение 0..1, [visibleShare] — доля видимой части, [onSeek] — перейти к доле 0..1.
 */
@Composable
private fun ScrollbarCore(
    fraction: Float, visibleShare: Float, scrolling: Boolean, onSeek: (Float) -> Unit, modifier: Modifier,
) {
    if (visibleShare >= 0.999f) return
    var dragging by remember { mutableStateOf(false) }
    var recently by remember { mutableStateOf(false) }
    LaunchedEffect(scrolling, dragging) {
        if (scrolling || dragging) recently = true else { delay(1500); recently = false }
    }
    val show = scrolling || dragging || recently
    val a by animateFloatAsState(if (show) 1f else 0f, label = "sb")
    val w by animateDpAsState(if (dragging) 8.dp else 5.dp, label = "sbw")
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxHeight().width(28.dp).alpha(a)) {
        val trackPx = with(density) { maxHeight.toPx() }
        val thumbPx = (trackPx * visibleShare).coerceIn(with(density) { 48.dp.toPx() }, trackPx)
        val y = (trackPx - thumbPx) * fraction.coerceIn(0f, 1f)
        Box(
            Modifier.fillMaxHeight().width(28.dp)
                .then(if (show) Modifier.pointerInput(trackPx, thumbPx) {
                    detectVerticalDragGestures(
                        onDragStart = { o -> dragging = true; onSeek(((o.y - thumbPx / 2) / (trackPx - thumbPx)).coerceIn(0f, 1f)) },
                        onDragEnd = { dragging = false }, onDragCancel = { dragging = false },
                    ) { ch, _ ->
                        ch.consume()
                        onSeek(((ch.position.y - thumbPx / 2) / (trackPx - thumbPx)).coerceIn(0f, 1f))
                    }
                } else Modifier)
        ) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(end = 3.dp)
                    .offset(y = with(density) { y.toDp() })
                    .width(w).height(with(density) { thumbPx.toDp() })
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = if (dragging) 0.9f else 0.55f))
            )
        }
    }
}

@Composable
fun VerticalScrollbar(state: ScrollState, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    BoxWithConstraints(modifier.fillMaxHeight()) {
        val vp = with(LocalDensity.current) { maxHeight.toPx() }
        val max = state.maxValue.toFloat()
        if (max <= 0f || max == Float.POSITIVE_INFINITY) return@BoxWithConstraints
        ScrollbarCore(
            fraction = state.value / max, visibleShare = vp / (vp + max), scrolling = state.isScrollInProgress,
            onSeek = { f -> scope.launch { state.scrollTo((f * max).toInt()) } },
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}

/**
 * Полоса для LazyColumn (5.20): положение и размер считаются в пикселях. Высоты карточек запоминаются,
 * пока они видны; для ещё не показанных берётся средняя. Раньше считалось по числу видимых карточек —
 * при смене высоких карточек на низкие ползунок «дёргался» и менял размер.
 */
@Composable
fun LazyScrollbar(state: LazyListState, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val sizes = remember(state) { HashMap<Int, Int>() }
    val info = state.layoutInfo
    val total = info.totalItemsCount
    if (total == 0) return
    val visible = info.visibleItemsInfo
    val first = visible.firstOrNull() ?: return
    // число элементов поменялось (фильтр, другой период) — старые высоты могут не подходить
    if (sizes.keys.any { it >= total }) sizes.clear()
    visible.forEach { sizes[it.index] = it.size }
    val gap = info.mainAxisItemSpacing
    val avg = sizes.values.average().toFloat()
    fun sizeOf(i: Int) = (sizes[i]?.toFloat() ?: avg) + gap
    val content = (0 until total).sumOf { sizeOf(it).toDouble() }.toFloat() - gap + info.beforeContentPadding + info.afterContentPadding
    val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
    val maxScroll = (content - viewport).coerceAtLeast(1f)
    val pos = (0 until first.index).sumOf { sizeOf(it).toDouble() }.toFloat() - first.offset
    val fraction = when {
        !state.canScrollBackward -> 0f
        !state.canScrollForward -> 1f
        else -> (pos / maxScroll).coerceIn(0f, 1f)
    }
    Box(modifier.fillMaxHeight()) {
        ScrollbarCore(
            fraction = fraction,
            visibleShare = if (!state.canScrollForward && !state.canScrollBackward) 1f else (viewport / content).coerceIn(0.05f, 1f),
            scrolling = state.isScrollInProgress,
            onSeek = { f ->
                // доля → пиксель → элемент и смещение внутри него
                var rest = f * maxScroll
                var i = 0
                while (i < total - 1 && rest >= sizeOf(i)) { rest -= sizeOf(i); i++ }
                scope.launch { state.scrollToItem(i, rest.toInt()) }
            },
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}
