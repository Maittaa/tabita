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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
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
/** 5.43: число колонок сетки — от 4 до 6 (Prefs.gridCols). */
val GRID_COLS_RANGE = 4..6
/** Сколько колонок сейчас у сетки — для плиток, которым надо ужаться. */
val LocalGridCols = androidx.compose.runtime.compositionLocalOf { GRID_COLS }

/**
 * Ширина положения в клетках. 5.46: как в лаунчере — размеры в клетках постоянные (широкая 4×2, половина 2×2,
 * мини 1×2), а клетка уменьшается с ростом числа колонок; полная — во всю ширину.
 */
fun TileSize.cols(cols: Int): Int = when (this) { TileSize.FULL -> cols; TileSize.WIDE -> minOf(4, cols); TileSize.HALF -> 2; TileSize.MINI -> 1 }

/** Положение по ширине в клетках при ресайзе: 1 — мини, 2 (и 3 при 5–6 колонках) — половина, шире — широкая/полная. */
fun sizeForWidth(nw: Int, cols: Int): TileSize? = when {
    nw <= 1 -> TileSize.MINI
    nw <= 2 || (nw == 3 && cols > 4) -> TileSize.HALF
    else -> null
}

/** 5.46: масштаб клетки относительно сетки в 4 колонки — высота строки и содержимое плиток. */
fun gridScale(cols: Int) = 4f / cols.coerceIn(GRID_COLS_RANGE)
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
    cols: Int = GRID_COLS,
    editing: Boolean,
    selected: String?,
    onSelect: (String?) -> Unit,
    onStartEdit: (String) -> Unit,
    /** Перенести плитку на место другой (id перетаскиваемой, id той, на которую навели). */
    onMove: (String, String) -> Unit,
    onResize: (id: String, w: Int, h: Int, small: Boolean) -> Unit,
    onOpen: (String) -> Unit,
    full: @Composable (String) -> Unit,
    compact: @Composable (id: String, w: Int, h: Int) -> Unit,
    modifier: Modifier = Modifier,
    /** Плитка, которая время от времени «покачивается» — подсказка, что плитки можно двигать (5.36). */
    hintId: String? = null,
) {
    val wiggle = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(hintId) {
        if (hintId == null) { wiggle.snapTo(0f); return@LaunchedEffect }
        kotlinx.coroutines.delay(1200)
        while (true) {
            // как значок на рабочем столе Pixel, который зажали: лёгкое покачивание и снова покой
            for (a in listOf(-1.6f, 1.6f, -1.2f, 1.2f, -0.6f, 0f)) wiggle.animateTo(a, androidx.compose.animation.core.tween(90))
            kotlinx.coroutines.delay(3200)
        }
    }
    val haptic = LocalHapticFeedback.current
    val rects = remember { HashMap<String, Pair<IntOffset, IntSize>>() }
    val autoRows = remember { HashMap<String, Int>() }
    // 5.48: кнопки внутри уменьшенных плиток — по плитке
    val hitsMap = remember { HashMap<String, TileHits>() }
    fun hitsOf(id: String) = hitsMap.getOrPut(id) { TileHits() }
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
    // плитка, с которой только что поменялись местами: пока центр над ней, повторно не меняем —
    // иначе плитки разного размера меняются туда-обратно на каждом кадре (5.22.4)
    var swapped by remember { mutableStateOf<String?>(null) }
    val moveNow by rememberUpdatedState(onMove)
    fun dragBy(d: Offset) {
        val id = dragId ?: return
        val r = rects[id] ?: return
        dragPos += d
        val cx = dragPos.x + r.second.width / 2f; val cy = dragPos.y + r.second.height / 2f
        fun over(tid: String) = rects[tid]?.let { (o, s) -> cx >= o.x && cx < o.x + s.width && cy >= o.y && cy < o.y + s.height } == true
        swapped?.let { if (!over(it)) swapped = null }
        val target = tilesNow.firstOrNull { t -> t.id != id && t.id != swapped && over(t.id) } ?: return
        swapped = target.id
        moveNow(id, target.id)
    }
    fun endDrag() { dragId = null; swapped = null }

    SubcomposeLayout(modifier.fillMaxWidth()) { cs ->
        val gap = 8.dp.roundToPx()
        val margin = OVERLAY_MARGIN.roundToPx()  // слой жестов шире плитки — чтобы ручки за краем ловили касания
        // 5.46: высота строки уменьшается вместе с шириной колонки
        val unit = (52.dp * gridScale(cols)).roundToPx()
        val inset = GRID_INSET.roundToPx()   // поле вокруг плиток: в нём ручки крайних плиток ловят касания
        val outerW = cs.maxWidth
        val width = outerW - 2 * inset
        val cell = (width - gap * (cols - 1)) / cols
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
                Spec(t.id, cols, maxOf(rows, t.h), true, ps)
            } else {
                // в хранилище положение записано по 4 колонкам; на экране ширина — от текущего числа колонок
                // 5.47: размер в клетках — как задан (ширина не больше числа колонок, высота 1–3)
                val w = t.w.coerceIn(1, cols); val h = t.h.coerceIn(1, Repo.Tile.MAX_H)
                val ps = subcompose("s_" + t.id) {
                    // 5.46: содержимое уменьшенных плиток масштабируется целиком (отступы, иконки, текст) — как клетка
                    val d = androidx.compose.ui.platform.LocalDensity.current
                    val k = gridScale(cols)
                    androidx.compose.runtime.CompositionLocalProvider(
                        LocalGridCols provides cols,
                        LocalTileHits provides hitsOf(t.id),
                        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density * k, d.fontScale),
                    ) { compact(t.id, w, h) }
                }.map { it.measure(Constraints.fixed(wPx(w), hPx(h))) }
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
                if (c + w > cols) return false
                for (rr in r until r + h) { if (rr >= occ.size) continue; for (cc in c until c + w) if (occ[rr][cc]) return false }
                return true
            }
            while (i < specs.size && !specs[i].isFull) {
                val s = specs[i]
                var r = 0
                while (true) {
                    val c = (0..cols - s.w).firstOrNull { free(r, it, s.w, s.h) }
                    if (c != null) {
                        while (occ.size < r + s.h) occ.add(BooleanArray(cols))
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
                    onTap = { p ->
                        // 5.48: нажатие на кнопку внутри плитки — действие, иначе открыть плитку
                        val hit = if (!editing && !s.isFull) hitsOf(s.id).at(p) else null
                        when {
                            editing -> onSelect(s.id)
                            hit != null -> { hit(); haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove) }
                            !s.isFull -> onOpen(s.id)
                        }
                    },
                    onLongPress = { onStartEdit(s.id); startDrag(s.id) },
                    onDragStart = { onSelect(s.id); startDrag(s.id) },
                    onDrag = ::dragBy, onDragEnd = ::endDrag,
                    onResizeStart = { resizeStart[s.id] = s.w to s.h },
                    onResize = { dx, dy, horiz, vert ->
                        val (w0, h0) = resizeStart[s.id] ?: (s.w to s.h)
                        // horiz/vert: +1 — правый/нижний край, −1 — левый/верхний (тянем наружу — больше)
                        val nw = if (horiz != 0) ((wPx(w0) + dx * horiz + gap) / (cell + gap)).roundToInt().coerceIn(1, cols) else w0
                        val nh = if (vert != 0) ((hPx(h0) + dy * vert + gap) / (unit + gap)).roundToInt().coerceIn(1, 40) else h0
                        // 5.47: размер меняется по одной клетке; выше 3 строк — развернуть, развёрнутую тянуть вверх/внутрь — свернуть
                        if (s.isFull) {
                            if ((vert != 0 && nh < h0 - 1) || (horiz != 0 && nw < cols)) onResize(s.id, if (horiz != 0) nw else cols, Repo.Tile.MAX_H, true)
                        } else if (vert != 0 && nh > Repo.Tile.MAX_H) {
                            onResize(s.id, 0, 0, false)
                        } else if (nw != s.w || nh.coerceAtMost(Repo.Tile.MAX_H) != s.h) {
                            onResize(s.id, nw, nh.coerceIn(1, Repo.Tile.MAX_H), true)
                        }
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
                    val w = if (id == hintId) wiggle.value else 0f
                    val sc = (if (id == pulseId) pulse.value else 1f) * (if (id == dragId) lift else 1f) * (1f + Math.abs(w) * 0.012f)
                    scaleX = sc; scaleY = sc; rotationZ = w
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
    onTap: (Offset) -> Unit, onLongPress: () -> Unit,
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

    // точка нажатия — в координатах окна, чтобы сравнить с границами кнопок внутри плитки
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxSize()) {
      Box(Modifier.fillMaxSize().padding(OVERLAY_MARGIN).onGloballyPositioned { origin = it.positionInRoot() }) {
        when {
            // режим правки: выбранную тащим сразу, остальные — после долгого нажатия (страница прокручивается)
            editing -> Box(
                Modifier.fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures(onTap = { tap(origin + it) }) }
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
                    .pointerInput(Unit) { detectTapGestures(onTap = { tap(origin + it) }) }
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
