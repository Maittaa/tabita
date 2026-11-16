package org.maittaa.tabita

import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.roundToInt

const val GRID_COLS = 4
private val OVERLAY_MARGIN = 24.dp
val GRID_INSET = 12.dp

/**
 * Сетка плиток как на рабочем столе: 4 колонки, высота кратна клетке.
 * Плитки укладываются плотно по порядку. Полноразмерная плитка (во всю ширину и не уменьшенная)
 * показывает полное содержимое, уменьшенная — краткое; по нажатию на краткую — [onOpen].
 *
 * Режим правки: перетаскивание меняет порядок, ручки справа/снизу/в углу — размер.
 */
@Composable
fun TileGrid(
    tiles: List<Repo.Tile>,
    editing: Boolean,
    selected: String?,
    onSelect: (String?) -> Unit,
    onStartEdit: (String) -> Unit,
    onMove: (String, Int) -> Unit,
    onResize: (id: String, w: Int, h: Int, small: Boolean) -> Unit,
    onOpen: (String) -> Unit,
    full: @Composable (String) -> Unit,
    compact: @Composable (id: String, w: Int, h: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val rects = remember { HashMap<String, Pair<IntOffset, IntSize>>() }
    val autoRows = remember { HashMap<String, Int>() }
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    val resizeStart = remember { mutableStateMapOf<String, Pair<Int, Int>>() }
    val tilesNow by rememberUpdatedState(tiles)
    // анимация «подхвата» плитки при долгом нажатии и при перетаскивании
    val pulse = remember { androidx.compose.animation.core.Animatable(1f) }
    var pulseId by remember { mutableStateOf<String?>(null) }
    var pulseKey by remember { mutableStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(pulseKey) {
        if (pulseKey == 0) return@LaunchedEffect
        pulse.snapTo(1f)
        pulse.animateTo(0.94f, androidx.compose.animation.core.tween(90))
        pulse.animateTo(1.04f, androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 600f))
        pulse.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.6f))
    }
    val lift by androidx.compose.animation.core.animateFloatAsState(if (dragId != null) 1.04f else 1f, label = "lift")

    fun startDrag(id: String) {
        val r = rects[id] ?: return
        dragId = id; dragPos = Offset(r.first.x.toFloat(), r.first.y.toFloat())
        pulseId = id; pulseKey++
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    fun dragBy(d: Offset) {
        val id = dragId ?: return
        val r = rects[id] ?: return
        dragPos += d
        val cx = dragPos.x + r.second.width / 2f; val cy = dragPos.y + r.second.height / 2f
        val target = tilesNow.indexOfFirst { t ->
            t.id != id && rects[t.id]?.let { (o, s) -> cx >= o.x && cx < o.x + s.width && cy >= o.y && cy < o.y + s.height } == true
        }
        if (target >= 0) onMove(id, target)
    }
    fun endDrag() { dragId = null }

    SubcomposeLayout(modifier.fillMaxWidth()) { cs ->
        val gap = 8.dp.roundToPx()
        val margin = OVERLAY_MARGIN.roundToPx()  // слой жестов шире плитки — чтобы ручки за краем ловили касания
        val unit = 52.dp.roundToPx()
        val inset = GRID_INSET.roundToPx()   // поле вокруг плиток: в нём ручки крайних плиток ловят касания
        val outerW = cs.maxWidth
        val width = outerW - 2 * inset
        val cell = (width - gap * (GRID_COLS - 1)) / GRID_COLS
        fun wPx(w: Int) = cell * w + gap * (w - 1)
        fun hPx(h: Int) = unit * h + gap * (h - 1)
        fun rowsFor(px: Int) = maxOf(1, ceil((px + gap).toDouble() / (unit + gap)).toInt())

        // 1) содержимое и размеры плиток в клетках.
        // Полная плитка измеряется по содержимому (во всю ширину), уменьшенная — по заданному размеру.
        data class Spec(val id: String, val w: Int, val h: Int, val isFull: Boolean, val content: List<androidx.compose.ui.layout.Placeable>)
        val specs = tiles.map { t ->
            if (!t.small) {
                val ps = subcompose("c_" + t.id) { androidx.compose.runtime.CompositionLocalProvider(LocalInTile provides true) { full(t.id) } }
                    .map { it.measure(Constraints(minWidth = width, maxWidth = width)) }
                val rows = rowsFor(ps.maxOfOrNull { it.height } ?: unit)
                autoRows[t.id] = rows
                Spec(t.id, GRID_COLS, maxOf(rows, t.h), true, ps)
            } else {
                val w = t.w.coerceIn(1, GRID_COLS); val h = t.h.coerceIn(1, 12)
                val ps = subcompose("s_" + t.id) { compact(t.id, w, h) }.map { it.measure(Constraints.fixed(wPx(w), hPx(h))) }
                Spec(t.id, w, h, false, ps)
            }
        }

        // 2) укладка полосами: полная плитка — полоса ровно по высоте содержимого;
        //    подряд идущие уменьшенные плитки плотно укладываются в клетки (сверху вниз, слева направо)
        val offsets = HashMap<String, IntOffset>()
        val heights = HashMap<String, Int>()
        var y = 0
        var i = 0
        while (i < specs.size) {
            val s0 = specs[i]
            if (s0.isFull) {
                val contentH = s0.content.maxOfOrNull { it.height } ?: unit
                val h = maxOf(contentH, if (tiles[i].h > 0) hPx(tiles[i].h) else 0)
                offsets[s0.id] = IntOffset(0, y); heights[s0.id] = h
                y += h + gap; i++
                continue
            }
            val occ = ArrayList<BooleanArray>()
            fun free(r: Int, c: Int, w: Int, h: Int): Boolean {
                if (c + w > GRID_COLS) return false
                for (rr in r until r + h) { if (rr >= occ.size) continue; for (cc in c until c + w) if (occ[rr][cc]) return false }
                return true
            }
            while (i < specs.size && !specs[i].isFull) {
                val s = specs[i]
                var r = 0
                while (true) {
                    val c = (0..GRID_COLS - s.w).firstOrNull { free(r, it, s.w, s.h) }
                    if (c != null) {
                        while (occ.size < r + s.h) occ.add(BooleanArray(GRID_COLS))
                        for (rr in r until r + s.h) for (cc in c until c + s.w) occ[rr][cc] = true
                        offsets[s.id] = IntOffset(c * (cell + gap), y + r * (unit + gap)); heights[s.id] = hPx(s.h)
                        break
                    }
                    r++
                }
                i++
            }
            y += hPx(occ.size) + gap
        }
        val totalH = maxOf(0, y - gap) + 2 * inset

        // 3) фон и слой жестов — отдельными слоями точного размера
        val placed = specs.map { s ->
            val size = IntSize(wPx(s.w), heights[s.id]!!)
            val off = offsets[s.id]!!
            rects[s.id] = off to size
            val sel = selected == s.id
            val bg = subcompose("bg_" + s.id) { TileBg(editing, sel, dragId == s.id) }.map { it.measure(Constraints.fixed(size.width, size.height)) }
            val ov = subcompose("ov_" + s.id + "_" + s.isFull) {
                TileOverlay(
                    full = s.isFull, editing = editing, selected = sel,
                    onTap = { if (editing) onSelect(s.id) else if (!s.isFull) onOpen(s.id) },
                    onLongPress = { onStartEdit(s.id); startDrag(s.id) },
                    onDragStart = { onSelect(s.id); startDrag(s.id) },
                    onDrag = ::dragBy, onDragEnd = ::endDrag,
                    onResizeStart = { resizeStart[s.id] = s.w to s.h },
                    onResize = { dx, dy, horiz, vert ->
                        val (w0, h0) = resizeStart[s.id] ?: (s.w to s.h)
                        // horiz/vert: +1 — правый/нижний край, −1 — левый/верхний (тянем наружу — больше)
                        val nw = if (horiz != 0) ((wPx(w0) + dx * horiz + gap) / (cell + gap)).roundToInt().coerceIn(1, GRID_COLS) else w0
                        val nh = if (vert != 0) ((hPx(h0) + dy * vert + gap) / (unit + gap)).roundToInt().coerceIn(1, 40) else h0
                        // размер «перескакивает» только между четырьмя положениями
                        val cur = if (s.isFull) TileSize.FULL else Repo.Tile(s.id, s.w, s.h, true).size
                        val target = when {
                            nw <= 1 -> TileSize.MINI
                            nw == 2 -> TileSize.HALF
                            cur == TileSize.FULL -> if (vert != 0 && nh < h0 - 1) TileSize.WIDE else TileSize.FULL
                            else -> if (vert != 0 && nh >= 4) TileSize.FULL else TileSize.WIDE
                        }
                        if (target != cur) onResize(s.id, target.w, target.h, target.small)
                    },
                )
            }.map { it.measure(Constraints.fixed(size.width + 2 * margin, size.height + 2 * margin)) }
            Triple(s.id, off, Triple(bg, s.content, ov))
        }
        layout(outerW, totalH) {
            placed.forEach { (id, off, layers) ->
                val o = (if (id == dragId) IntOffset(dragPos.x.roundToInt(), dragPos.y.roundToInt()) else off) + IntOffset(inset, inset)
                val z = when (id) { dragId -> 3f; selected -> 2f; else -> 0f }
                val layer: androidx.compose.ui.graphics.GraphicsLayerScope.() -> Unit = {
                    val sc = (if (id == pulseId) pulse.value else 1f) * (if (id == dragId) lift else 1f)
                    scaleX = sc; scaleY = sc
                }
                layers.first.forEach { it.placeWithLayer(o, z, layer) }
                layers.second.forEach { it.placeWithLayer(o, z, layer) }
                layers.third.forEach { it.placeWithLayer(o.x - margin, o.y - margin, z, layer) }
            }
        }
    }
}

@Composable
private fun TileBg(editing: Boolean, selected: Boolean, dragging: Boolean) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.fillMaxSize()
            .then(if (dragging) Modifier.shadow(12.dp, shape) else Modifier)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(cardBorder(), shape)
    )
}

/** Расширить область рисования за пределы плитки (для рамки выделения и ручек). */
private fun Modifier.outset(d: androidx.compose.ui.unit.Dp) = this.then(
    Modifier.layout { m, c ->
        val p = d.roundToPx()
        val pl = m.measure(Constraints.fixed(c.maxWidth + 2 * p, c.maxHeight + 2 * p))
        layout(c.maxWidth, c.maxHeight) { pl.place(-p, -p) }
    }
)

@Composable
private fun TileOverlay(
    full: Boolean, editing: Boolean, selected: Boolean,
    onTap: () -> Unit, onLongPress: () -> Unit,
    onDragStart: () -> Unit, onDrag: (Offset) -> Unit, onDragEnd: () -> Unit,
    onResizeStart: () -> Unit, onResize: (dx: Float, dy: Float, horiz: Int, vert: Int) -> Unit,
) {
    // жесты живут дольше одной перерисовки — берём всегда свежие обработчики
    val tap by rememberUpdatedState(onTap)
    val long by rememberUpdatedState(onLongPress)
    val dStart by rememberUpdatedState(onDragStart)
    val drag by rememberUpdatedState(onDrag)
    val dEnd by rememberUpdatedState(onDragEnd)
    val rStart by rememberUpdatedState(onResizeStart)
    val resize by rememberUpdatedState(onResize)
    val frameAlpha by androidx.compose.animation.core.animateFloatAsState(if (editing && selected) 1f else 0f, label = "frame")

    Box(Modifier.fillMaxSize()) {
      Box(Modifier.fillMaxSize().padding(OVERLAY_MARGIN)) {
        when {
            // режим правки: выбранную тащим сразу, остальные — после долгого нажатия (страница прокручивается)
            editing -> Box(
                Modifier.fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures(onTap = { tap() }) }
                    .pointerInput(selected) {
                        if (selected) detectDragGestures(onDragStart = { dStart() }, onDragEnd = { dEnd() }, onDragCancel = { dEnd() }) { ch, d ->
                            ch.consume(); drag(d)
                        } else detectDragGesturesAfterLongPress(onDragStart = { dStart() }, onDragEnd = { dEnd() }, onDragCancel = { dEnd() }) { ch, d ->
                            ch.consume(); drag(d)
                        }
                    }
            )
            // краткая плитка: нажатие — подробно, долгое нажатие — правка
            !full -> Box(
                Modifier.fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures(onTap = { tap() }) }
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(onDragStart = { long() }, onDragEnd = { dEnd() }, onDragCancel = { dEnd() }) { ch, d ->
                            ch.consume(); drag(d)
                        }
                    }
            )
            // полная плитка: долгое нажатие на заголовок — правка
            else -> Box(
                Modifier.fillMaxWidth(0.55f).height(44.dp)
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(onDragStart = { long() }, onDragEnd = { dEnd() }, onDragCancel = { dEnd() }) { ch, d ->
                            ch.consume(); drag(d)
                        }
                    }
            )
        }
      }
        // рамка выделения как в Pixel Launcher: толстая, чуть шире плитки, с ручками на серединах сторон.
        // Ручки лежат во внешнем слое (он на OVERLAY_MARGIN шире плитки), поэтому ловят касания и за краем.
        if (frameAlpha > 0f) {
            val accent = MaterialTheme.colorScheme.primary.copy(alpha = frameAlpha)
            val handleFill = MaterialTheme.colorScheme.primaryContainer.copy(alpha = frameAlpha)
            Box(Modifier.fillMaxSize().padding(OVERLAY_MARGIN - 6.dp).border(3.dp, accent, RoundedCornerShape(18.dp)))
            val extraPx = with(androidx.compose.ui.platform.LocalDensity.current) { 48.dp.toPx() }
            @Composable
            fun Handle(align: Alignment, horiz: Int, vert: Int) = Box(
                Modifier.align(align).size((OVERLAY_MARGIN - 6.dp) * 2)
                    // боковые ручки у края экрана: просим систему не забирать касание под жест «назад»
                    // (Android позволяет исключить до 200 dp по высоте у каждого края)
                    .then(if (horiz != 0) Modifier.systemGestureExclusion { c ->
                        androidx.compose.ui.geometry.Rect(-c.size.width.toFloat(), -extraPx, c.size.width * 2f, c.size.height + extraPx)
                    } else Modifier)
                    .pointerInput(Unit) {
                        var acc = Offset.Zero
                        detectDragGestures(onDragStart = { acc = Offset.Zero; rStart() }) { ch, d ->
                            ch.consume(); acc += d; resize(acc.x, acc.y, horiz, vert)
                        }
                    }
            ) {
                Box(Modifier.align(Alignment.Center).size(18.dp).clip(CircleShape).background(handleFill)
                    .border(3.dp, accent, CircleShape))
            }
            Handle(Alignment.CenterEnd, 1, 0)
            Handle(Alignment.CenterStart, -1, 0)
            Handle(Alignment.BottomCenter, 0, 1)
            Handle(Alignment.TopCenter, 0, -1)
        }
    }
}
