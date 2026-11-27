package org.maittaa.tabita

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * 5.48: быстрые действия в уменьшенных плитках — отметить, добавить стакан, оценить день, +1 к счётчику.
 * Поверх плитки лежит слой жестов (нажатие — открыть, удержание — правка и перетаскивание), поэтому кнопки
 * внутри не ловят нажатия сами: они регистрируют свои границы в [TileHits], а слой жестов по точке нажатия
 * решает — действие или открыть плитку.
 */
data class QuickAction(val key: String, val label: String, val on: Boolean = false, val icon: ImageVector? = null, val run: () -> Unit)

class TileHits {
    private val map = LinkedHashMap<String, Pair<Rect, () -> Unit>>()
    fun put(key: String, r: Rect, run: () -> Unit) { map[key] = r to run }
    fun remove(key: String) { map.remove(key) }
    /** Действие под точкой (координаты корня окна); с запасом 4 px — по маленьким кнопкам легче попасть. */
    fun at(p: Offset): (() -> Unit)? = map.values.lastOrNull { it.first.inflate(4f).contains(p) }?.second
}

val LocalTileHits = staticCompositionLocalOf<TileHits?> { null }

/** Кнопка внутри уменьшенной плитки: её нажатие обработает слой жестов плитки. */
fun Modifier.tileAction(key: String, run: () -> Unit): Modifier = composed {
    val hits = LocalTileHits.current
    val r by rememberUpdatedState(run)
    DisposableEffect(hits, key) { onDispose { hits?.remove(key) } }
    onGloballyPositioned { c -> hits?.put(key, c.boundsInRoot()) { r() } }
}

/** Действия для плитки по её виду; в разумных пределах — только то, что делается одним нажатием. */
fun quickActions(act: MainActivity, repo: Repo, template: List<TemplateItem>, id: String, day: Map<String, String>, date: String): List<QuickAction> {
    // отметки группы: нажатие — отметить, ещё раз — «+» (если можно), ещё раз — снять
    repo.groups.value.firstOrNull { it.id == id }?.let {
        return groupChecks(repo, template, id).map { c ->
            val v = day[c.id]
            QuickAction(c.id, if (v == "2") c.displayName + "+" else c.displayName, on = v != null) {
                val nv = when (v) { null -> "1"; "1" -> if (c.plusAllowed) "2" else null; else -> null }
                repo.setValue(date, c.id, nv); Log.d("Tile", "отметка ${c.id}: $nv")
            }
        }
    }
    val item = template.firstOrNull { it.id == id } ?: return emptyList()
    val v = day[item.id]
    return when {
        item.type == ItemType.WATER -> {
            val l = parseNumber(v) ?: 0.0
            fun add(d: Double) = repo.setValue(date, item.id, (Math.round((l + d) * 20) / 20.0).coerceAtLeast(0.0).let { if (it == 0.0) null else it.toString() })
                .also { Log.d("Tile", "вода ${l + d} л") }
            listOf(QuickAction("w250", "+250") { add(0.25) }, QuickAction("w500", "+500") { add(0.5) }, QuickAction("wm", "−250") { add(-0.25) })
        }
        item.type == ItemType.SCALE && item.max - item.min in 1.0..10.0 -> {
            val cur = parseNumber(v)
            (item.min.toInt()..item.max.toInt()).map { n ->
                QuickAction("s$n", n.toString(), on = cur != null && Math.round(cur).toInt() == n) {
                    repo.setValue(date, item.id, if (cur != null && Math.round(cur).toInt() == n) null else n.toDouble().toString())
                    Log.d("Tile", "оценка $n")
                }
            }
        }
        item.type == ItemType.MULTI -> {
            val counts = parseCounts(v)
            (item.options + (counts.keys - item.options.toSet())).map { opt ->
                val n = counts[opt] ?: 0
                QuickAction("m:$opt", if (item.counter && n > 0) "${t(opt)} $n" else t(opt), on = n > 0) {
                    val m = LinkedHashMap(counts)
                    if (item.counter) m[opt] = n + 1 else if (n > 0) m.remove(opt) else m[opt] = 1
                    repo.setValue(date, item.id, countsToString(m)); Log.d("Tile", "«$opt»: ${m[opt] ?: 0}")
                }
            }
        }
        item.type == ItemType.WELLBEING -> {
            val st = wbStatus(repo, act.prefs, date)
            if (st.due) listOf(QuickAction("wb", t("Пройти")) { act.wbStart.value = true }) else emptyList()
        }
        item.type == ItemType.TRAINING -> listOf(QuickAction("tr", t("Открыть")) { act.openTrain.value = true })
        else -> emptyList()
    }
}

@Composable
internal fun rememberHits(): TileHits = androidx.compose.runtime.remember { TileHits() }
