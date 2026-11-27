package org.maittaa.tabita

import androidx.compose.material.icons.filled.Link
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalTime

// =====================================================================
// Питание
// =====================================================================

fun numStr(d: Double) = d.toString().removeSuffix(".0")

fun mealKcal(list: List<Meal>) = list.mapNotNull { it.kcal }.takeIf { it.isNotEmpty() }?.sum()

/** Сумма БЖУ за день (5.19.2); null — ни у одного приёма не указаны. */
data class Macros(val protein: Double, val fat: Double, val carbs: Double) {
    fun short() = "${t("Б")} ${formatNumber(Math.round(protein).toDouble())} · ${t("Ж")} ${formatNumber(Math.round(fat).toDouble())} · ${t("У")} ${formatNumber(Math.round(carbs).toDouble())}"
}
fun mealMacros(list: List<Meal>): Macros? =
    if (list.none { it.protein != null || it.fat != null || it.carbs != null }) null
    else Macros(list.sumOf { it.protein ?: 0.0 }, list.sumOf { it.fat ?: 0.0 }, list.sumOf { it.carbs ?: 0.0 })

/**
 * Нормы на день (5.19.3). Калории — оценка расхода за день по профилю (Anthro.energyOn).
 * Белок — 1,6–2 г на кг веса (рост мышц; общая норма для взрослых — от 0,8). Жиры 20–35 %, углеводы 45–65 % калорий.
 * Без веса белок — 10–35 % калорий; без профиля граммы жиров и углеводов не считаются, только доли.
 */
data class DayNorms(val kcal: Double?, val protein: ClosedFloatingPointRange<Double>?, val fat: ClosedFloatingPointRange<Double>?, val carbs: ClosedFloatingPointRange<Double>?) {
    companion object {
        const val P_LO = 1.6; const val P_HI = 2.0
        val FAT_PCT = 20.0..35.0; val CARB_PCT = 45.0..65.0; val PROT_PCT = 10.0..35.0
        fun of(kcal: Double?, weight: Double?) = DayNorms(
            kcal,
            weight?.let { (it * P_LO)..(it * P_HI) } ?: kcal?.let { (it * PROT_PCT.start / 400)..(it * PROT_PCT.endInclusive / 400) },
            kcal?.let { (it * FAT_PCT.start / 900)..(it * FAT_PCT.endInclusive / 900) },
            kcal?.let { (it * CARB_PCT.start / 400)..(it * CARB_PCT.endInclusive / 400) },
        )
    }
}

/** Итог БЖУ за день в блоке «Питание»: граммы, доля калорий, нормы и белок на кг веса. */
@Composable
fun DayMacros(repo: Repo, date: String, meals: List<Meal>) {
    val m = mealMacros(meals) ?: return
    val macroKcal = 4 * m.protein + 9 * m.fat + 4 * m.carbs
    val eaten = mealKcal(meals) ?: macroKcal
    val profile = collect(repo.profile)
    val day = java.time.LocalDate.parse(date)
    val w = remember(date, repo.entries.value) { Anthro.weightOn(repo.template.value, repo.entries.value, day) }
    val burn = remember(date, repo.entries.value, profile) { Anthro.energyOn(profile, repo.template.value, repo.entries.value, day)?.total }
    val norms = DayNorms.of(burn, w)
    val on = MaterialTheme.colorScheme.onSecondaryContainer
    fun g(v: Double) = formatNumber(Math.round(v).toDouble())
    /** Пометка к норме: ✓ в норме, ↓ мало, ↑ много (с запасом 10 %). */
    fun mark(v: Double, r: ClosedFloatingPointRange<Double>?) = when {
        r == null -> ""
        v < r.start -> " ↓"
        v > r.endInclusive * 1.1 -> " ↑"
        else -> " ✓"
    }
    var open by remember { mutableStateOf(false) }
    val sum = listOf(t("Б") to (m.protein to norms.protein), t("Ж") to (m.fat to norms.fat), t("У") to (m.carbs to norms.carbs))
    // компактно: две строки, подробности — по нажатию
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clickable { open = true }) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    t("БЖУ за день") + if (norms.kcal != null) " · " + t("%s из ≈ %s ккал", g(eaten), g(norms.kcal)) +
                        mark(eaten, (norms.kcal * 0.9)..(norms.kcal * 1.05)) else "",
                    style = MaterialTheme.typography.labelMedium, color = on,
                )
                Text(
                    sum.joinToString("   ") { (k, v) -> "$k ${g(v.first)}" + (v.second?.let { r -> mark(v.first, r) } ?: "") } + "  " + t("г"),
                    style = MaterialTheme.typography.titleSmall, color = on, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Filled.ChevronRight, t("Подробнее"), tint = on)
        }
    }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        confirmButton = { TextButton({ open = false }) { Text(t("Закрыть")) } },
        title = { Text(t("БЖУ за день")) },
        text = {
            val cs = MaterialTheme.colorScheme
            /** Строка таблицы: показатель (и подпись под ним) | съедено | норма | пометка — каждое в своей колонке, без переносов. */
            @Composable
            fun TRow(name: String, sub: String?, value: String, norm: String, mk: String, head: Boolean = false) {
                val st = if (head) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium
                val col = if (head) cs.onSurfaceVariant else cs.onSurface
                Row(Modifier.fillMaxWidth().padding(vertical = if (head) 0.dp else 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1.15f)) {
                        Text(name, style = st, color = col, maxLines = 1, softWrap = false)
                        if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, maxLines = 1, softWrap = false)
                    }
                    Text(value, Modifier.weight(0.9f), style = if (head) st else MaterialTheme.typography.titleSmall, color = col,
                        maxLines = 1, softWrap = false, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    Text(norm, Modifier.weight(1.1f), style = if (head) st else MaterialTheme.typography.bodySmall, color = if (head) col else cs.onSurfaceVariant,
                        maxLines = 1, softWrap = false, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    Text(mk.trim(), Modifier.width(22.dp), style = st, color = col, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
            }
            fun share(grams: Double, k: Double) = if (macroKcal > 0) t("%d%% калорий", Math.round(grams * k / macroKcal * 100).toInt()) else null
            fun range(r: ClosedFloatingPointRange<Double>, unit: String) = "${g(r.start)}–${g(r.endInclusive)}$unit"
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TRow("", null, t("съедено"), t("норма"), "", head = true)
                if (norms.kcal != null) TRow(t("Калории"), t("расход за день"), g(eaten), "≈ " + g(norms.kcal), mark(eaten, (norms.kcal * 0.9)..(norms.kcal * 1.05)))
                else TRow(t("Калории"), null, g(eaten), "—", "")
                listOf(
                    Triple(t("Белки"), m.protein, 4.0) to (norms.protein to null),
                    Triple(t("Жиры"), m.fat, 9.0) to (norms.fat to DayNorms.FAT_PCT),
                    Triple(t("Углеводы"), m.carbs, 4.0) to (norms.carbs to DayNorms.CARB_PCT),
                ).forEach { (v, nr) ->
                    val (name, grams, k) = v
                    val (r, pct) = nr
                    val sh = if (macroKcal > 0) grams * k / macroKcal * 100 else 0.0
                    when {
                        r != null -> TRow(name, share(grams, k), t("%s г", g(grams)), range(r, " " + t("г")), mark(grams, r))
                        pct != null -> TRow(name, null, "${Math.round(sh)}%", range(pct, "%"), mark(sh, pct.start..(pct.endInclusive / 1.1)))
                        else -> TRow(name, share(grams, k), t("%s г", g(grams)), "—", "")
                    }
                }
                if (w != null && m.protein > 0) TRow(
                    t("Белок на кг"), null, formatNumber(Math.round(m.protein / w * 10) / 10.0) + " " + t("г"),
                    "${formatNumber(DayNorms.P_LO)}–${formatNumber(DayNorms.P_HI)} " + t("г"), mark(m.protein / w, DayNorms.P_LO..DayNorms.P_HI),
                )
                MealAbsorption(meals, w)
                Text(
                    (if (norms.kcal != null) t("Нормы: белок 1,6–2 г на кг веса (рост мышц), жиры 20–35 % и углеводы 45–65 % от расхода за день.")
                    else t("Укажите рост и год рождения в «Профиле» — появятся нормы калорий, жиров и углеводов в граммах.")) +
                        " " + t("✓ в норме, ↓ мало, ↑ много."),
                    style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
    )
}

/**
 * Усвоение БЖУ по приёмам пищи (5.27). Белок: мышцы лучше всего используют ≈ 0,4 г/кг за приём при 4 приёмах в день
 * (Schoenfeld, Aragon, JISSN 2018); ниже ≈ 0,24 г/кг синтез мышечного белка заметно слабее (Moore и др., 2015);
 * без веса — порог 20 г (Moore и др., 2009). Углеводы — гликемическая нагрузка приёма (≤10 низкая, ≥20 высокая).
 */
object Absorption {
    const val LOW_KG = 0.24; const val GOOD_KG = 0.4; const val LOW_G = 20.0
    data class Row(val kind: String, val kcal: Double?, val p: Double?, val f: Double?, val c: Double?, val gl: Double?) {
        fun proteinOk(w: Double?) = p != null && p >= (w?.let { it * LOW_KG } ?: LOW_G)
    }
    fun rows(meals: List<Meal>): List<Row> = mealUnits(meals).map { combineMeals(it) }
        .filter { it.protein != null || it.fat != null || it.carbs != null }
        .map { Row(it.kind, it.kcal, it.protein, it.fat, it.carbs, it.glEff) }
}

@Composable
private fun MealAbsorption(meals: List<Meal>, w: Double?) {
    val rows = Absorption.rows(meals)
    if (rows.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    fun g(v: Double?) = v?.let { formatNumber(Math.round(it).toDouble()) } ?: "—"
    val low = w?.let { it * Absorption.LOW_KG } ?: Absorption.LOW_G
    val good = w?.let { it * Absorption.GOOD_KG }
    Text(t("По приёмам пищи"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
    rows.forEach { r ->
        Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t(r.kind), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text((r.kcal?.let { t("%s ккал", g(it)) + " · " } ?: "") + t("Б %s · Ж %s · У %s", g(r.p), g(r.f), g(r.c)),
                    style = MaterialTheme.typography.bodySmall)
            }
            val notes = listOfNotNull(
                r.p?.let { p ->
                    when {
                        p < low -> t("белка мало для мышц (меньше %s г)", g(low))
                        good != null && p >= good -> t("белка на максимум для мышц ✓")
                        else -> t("белка достаточно ✓")
                    }
                },
                r.gl?.let { gl -> t("ГН %s — %s", formatNumber(Math.round(gl * 10) / 10.0), Glycemic.loadName(Glycemic.mealGlLevel(gl))) },
            )
            if (notes.isNotEmpty()) Text(notes.joinToString(" · "), style = MaterialTheme.typography.labelSmall,
                color = if (r.p != null && r.p < low) cs.error else cs.onSurfaceVariant)
        }
    }
    val okN = rows.count { it.proteinOk(w) }
    val pTotal = rows.sumOf { it.p ?: 0.0 }
    val top = rows.maxOfOrNull { it.p ?: 0.0 } ?: 0.0
    val tips = buildList {
        add(t("Белок с порогом для мышц — в %d из %d приёмов.", okN, rows.size))
        if (pTotal > 0 && top / pTotal > 0.6 && rows.size > 1) add(t("Больше половины белка — за один приём: лучше распределить ровнее."))
        if (good != null) add(t("Для роста мышц — 3–4 приёма по ≈ %s г белка (0,4 г на кг).", g(good)))
        val hi = rows.count { (it.gl ?: 0.0) >= 20 }
        if (hi > 0) add(t("Приёмов с высокой ГН: %d — добавьте к ним белок, клетчатку или овощи, сахар поднимется мягче.", hi))
    }
    Text(tips.joinToString(" "), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    Text(t("Источники: Schoenfeld, Aragon (JISSN, 2018); Moore и др. (2009, 2015)."), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
}

@Composable
fun MealsBlock(act: MainActivity, item: TemplateItem, date: String, value: String?, notify: (String) -> Unit) {
    val repo = act.repo
    val meals = parseMeals(value)
    fun save(list: List<Meal>) = repo.setValue(date, item.id, mealsToString(list))
    val total = mealKcal(meals)
    var open by remember { mutableStateOf<String?>(null) }
    var askLink by remember { mutableStateOf<Pair<String, Meal>?>(null) }
    // 5.57: шаблоны приёмов
    var tplTick by remember { mutableStateOf(0) }
    val tpls = remember(tplTick) { MealTpls.all(act.prefs) }
    var saveTpl by remember { mutableStateOf<List<Meal>?>(null) }
    var editTpls by remember { mutableStateOf(false) }

    /** Сцепить карточку с приёмом [unit]: один приём пищи, вид — как у приёма. */
    fun linked(list: List<Meal>, m: Meal, unit: String): List<Meal> {
        val head = list.firstOrNull { it.unit == unit } ?: return list
        return list.map {
            when {
                it.id == m.id -> it.copy(link = head.unit, kind = head.kind)
                it.id == head.id && head.link.isEmpty() -> it.copy(link = head.id)
                else -> it
            }
        }
    }
    /** Расцепить: карточка снова отдельный приём; одинокий остаток группы тоже. */
    fun unlinked(list: List<Meal>, m: Meal): List<Meal> {
        val l = list.map { if (it.id == m.id) it.copy(link = "") else it }
        val rest = l.filter { it.link == m.link && m.link.isNotEmpty() }
        return if (rest.size == 1) l.map { if (it.id == rest[0].id) it.copy(link = "") else it } else l
    }

    SectionCard(item.displayName, action = {
        if (total != null) Text(t("%s ккал", formatNumber(Math.round(total).toDouble())), fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary)
    }) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            mealUnits(meals).forEach { unit ->
                if (unit.size == 1) MealSummary(repo, unit[0]) { open = unit[0].id; Log.d("Food", "открыт приём пищи ${unit[0].kind}") }
                else LinkedMealSummary(repo, unit) { open = it.id; Log.d("Food", "открыта позиция ${it.kind}") }
            }
            DayMacros(repo, date, meals)
            if (meals.any { it.kcal != null || it.gi != null || it.carbs != null }) GlycemicDaySummary(meals)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val h = LocalTime.now().hour
                val kind = when { h < 11 -> mealKindsRu[0]; h < 16 -> mealKindsRu[1]; h < 21 -> mealKindsRu[2]; else -> mealKindsRu[3] }
                val same = meals.firstOrNull { it.kind == kind }
                if (same != null && kind != mealKindsRu[3]) askLink = kind to same
                else { val m = Meal(kind = kind); save(meals + m); open = m.id }
            }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text(t("Добавить приём пищи"), maxLines = 1)
            }
            // 5.57.1: шаблоны — одной кнопкой, выбор и подтверждение во всплывающем окне
            if (tpls.isNotEmpty()) OutlinedButton({ editTpls = true }, Modifier.testTag("tpl_open"),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp)) {
                Icon(Icons.Filled.BookmarkAdded, null); Spacer(Modifier.width(4.dp)); Text(t("Шаблон"), maxLines = 1)
            }
            }
        }
    }

    askLink?.let { (kind, same) ->
        val unit = meals.filter { it.unit == same.unit }
        AlertDialog(
            onDismissRequest = { askLink = null },
            icon = { Icon(Icons.Filled.Link, null) },
            title = { Text(t("%s уже записан", t(kind))) },
            text = {
                Text(t("Сейчас: %s. Это тот же приём пищи? Тогда карточки сцепятся: на экране — одна общая (калории, БЖУ, ГИ и ГН вместе), а открываться будут по отдельности.",
                    unit.joinToString(", ") { it.text.ifBlank { t(it.kind) }.take(40) } + (mealKcal(unit)?.let { " · " + t("%s ккал", formatNumber(Math.round(it).toDouble())) } ?: "")))
            },
            confirmButton = {
                TextButton(onClick = {
                    val m = Meal(kind = kind); save(linked(meals + m, m, same.unit)); open = m.id; askLink = null
                    Log.i("Food", "новая позиция сцеплена с $kind")
                }) { Text(t("Тот же, сцепить")) }
            },
            dismissButton = {
                TextButton(onClick = { val m = Meal(kind = mealKindsRu[3]); save(meals + m); open = m.id; askLink = null }) { Text(t("Отдельный перекус")) }
            },
        )
    }

    saveTpl?.let { unit ->
        var name by remember(unit) { mutableStateOf(MealTpl.defaultName(unit)) }
        AlertDialog(
            onDismissRequest = { saveTpl = null },
            title = { Text(t("Шаблон приёма")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(t("Сохранятся фото, описание, калории и БЖУ%s. Добавлять — кнопкой «Шаблон» рядом с «Добавить приём пищи».",
                        if (unit.size > 1) t(" всех %d позиций", unit.size) else ""), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(name, { name = it.take(40) }, label = { Text(t("Название")) }, singleLine = true)
                }
            },
            confirmButton = { TextButton({
                MealTpls.add(act.prefs, MealTpl.from(unit, name, repo.photosDir)); tplTick++; saveTpl = null; notify(t("Шаблон сохранён"))
            }) { Text(t("Сохранить")) } },
            dismissButton = { TextButton({ saveTpl = null }) { Text(t("Отмена")) } },
        )
    }
    if (editTpls) MealTplPicker(tpls, date, onPick = { tp ->
        save(MealTpls.applyTo(parseMeals(repo.entries.value[date]?.get(item.id)), tp, date, dir = repo.photosDir)); editTpls = false
        notify(t("Добавлено: %s", tp.name)); Log.i("Food", "шаблон в день: ${tp.name}")
    }, onManage = { editTpls = false; act.openSettings.value = "meal_tpl" }, onDismiss = { editTpls = false })

    val editing = meals.firstOrNull { it.id == open }
    if (editing != null) {
        Dialog(onDismissRequest = {
            // пустой приём пищи не сохраняем
            if (editing.text.isBlank() && editing.photos.isEmpty() && editing.kcal == null) save(meals.filterNot { it.id == editing.id })
            open = null
        }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            androidx.compose.material3.Surface(
                Modifier.fillMaxWidth(0.95f).padding(vertical = 24.dp),
                shape = RoundedCornerShape(20.dp), tonalElevation = 6.dp,
            ) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(4.dp).imePadding()) {
                    MealCard(act, date, editing, notify,
                        others = meals.filter { it.unit != editing.unit || (editing.link.isNotEmpty() && it.id != editing.id) },
                        onLink = { unit -> save(linked(parseMeals(repo.entries.value[date]?.get(item.id)), editing, unit)) },
                        onUnlink = { save(unlinked(parseMeals(repo.entries.value[date]?.get(item.id)), editing)) },
                        onChange = { m ->
                            val cur = parseMeals(repo.entries.value[date]?.get(item.id))
                            // сменили вид у сцепленной позиции — это уже другой приём
                            val l = if (m.kind != editing.kind && m.link.isNotEmpty()) unlinked(cur, m).map { if (it.id == m.id) m.copy(link = "") else it }
                                else cur.map { if (it.id == m.id) m else it }
                            save(l)
                        },
                        onDelete = {
                            editing.photos.forEach { File(repo.photosDir, it).delete() }
                            save(meals.filterNot { it.id == editing.id }); open = null
                        },
                        onDone = {
                            if (editing.text.isBlank() && editing.photos.isEmpty() && editing.kcal == null) save(meals.filterNot { it.id == editing.id })
                            open = null
                        })
                    if (editing.text.isNotBlank() || editing.kcal != null) TextButton({
                        saveTpl = parseMeals(repo.entries.value[date]?.get(item.id)).filter { it.unit == editing.unit }
                    }, Modifier.padding(start = 8.dp).testTag("tpl_save")) {
                        Icon(Icons.Filled.BookmarkAdd, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Сохранить как шаблон"))
                    }
                }
            }
        }
    }
}

/** Сцепленный приём: общая шапка с суммами, ниже позиции — каждая открывается отдельно. */
@Composable
private fun LinkedMealSummary(repo: Repo, unit: List<Meal>, onOpen: (Meal) -> Unit) {
    val c = combineMeals(unit)
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Link, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text(t(c.kind), style = MaterialTheme.typography.titleSmall)
                Text("  " + t("%d позиции", unit.size), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(c.kcal?.let { t("%s ккал", formatNumber(Math.round(it).toDouble())) } ?: "—", fontWeight = FontWeight.SemiBold)
            }
            if (c.protein != null || c.fat != null || c.carbs != null) Text(
                "${t("Б")} ${c.protein?.let { formatNumber(Math.round(it).toDouble()) } ?: "—"} · ${t("Ж")} ${c.fat?.let { formatNumber(Math.round(it).toDouble()) } ?: "—"} · ${t("У")} ${c.carbs?.let { formatNumber(Math.round(it).toDouble()) } ?: "—"}",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, top = 2.dp),
            )
            Box(Modifier.padding(start = 24.dp)) { GlyBadge(c) }
            Spacer(Modifier.height(6.dp))
            unit.forEach { m ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onOpen(m) }
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)).padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val first = m.photos.firstOrNull()
                    if (first != null) PhotoImage(File(repo.photosDir, first), 160, Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)))
                    else Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center) { Icon(Icons.Filled.Restaurant, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.text.ifBlank { t("Нажмите, чтобы заполнить") }, style = MaterialTheme.typography.bodySmall, maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        GlyBadge(m)
                    }
                    Text(m.kcal?.let { t("%s ккал", formatNumber(it)) } ?: "—", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

/** Краткая сводка приёма пищи: фото, название, калории, БЖУ. */
@Composable
private fun MealSummary(repo: Repo, meal: Meal, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            val first = meal.photos.firstOrNull()
            if (first != null) {
                PhotoImage(File(repo.photosDir, first), 200, Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)))
            } else {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Restaurant, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 5.45: время приёма по фото
                    val tm = meal.at?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).let { z -> clockOf(z.hour * 60 + z.minute) } }
                    Text(t(meal.kind) + (tm?.let { " · $it" } ?: ""), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    if (meal.photos.size > 1) Text("📷 ${meal.photos.size}  ", style = MaterialTheme.typography.labelSmall)
                    Text(meal.kcal?.let { t("%s ккал", formatNumber(it)) } ?: "—", fontWeight = FontWeight.SemiBold)
                }
                if (meal.text.isNotBlank()) Text(
                    meal.text, style = MaterialTheme.typography.bodySmall, maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (meal.protein != null || meal.fat != null || meal.carbs != null) Text(
                    "${t("Б")} ${meal.protein?.let(::formatNumber) ?: "—"} · ${t("Ж")} ${meal.fat?.let(::formatNumber) ?: "—"} · ${t("У")} ${meal.carbs?.let(::formatNumber) ?: "—"}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                GlyBadge(meal)
                if (meal.text.isBlank() && meal.photos.isEmpty() && meal.kcal == null) Text(
                    t("Нажмите, чтобы заполнить"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MealCard(
    act: MainActivity, date: String, meal: Meal, notify: (String) -> Unit, onChange: (Meal) -> Unit, onDelete: () -> Unit, onDone: () -> Unit,
    others: List<Meal> = emptyList(), onLink: (String) -> Unit = {}, onUnlink: () -> Unit = {},
    /** 5.57.2: для позиции шаблона — свой заголовок и без выбора вида (он общий у шаблона). */
    title: String? = null, showKind: Boolean = true,
) {
    val ctx = LocalContext.current
    val repo = act.repo
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var viewPhoto by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    // актуальная версия блюда для асинхронных колбэков
    var current by remember { mutableStateOf(meal) }
    current = meal

    // 5.45: время приёма — по первому фото: с камеры — сейчас, из галереи — время съёмки (EXIF), если оно в тот же день
    fun withTime(m: Meal, taken: Long?): Meal {
        if (m.at != null || taken == null) return m
        val d = java.time.Instant.ofEpochMilli(taken).atZone(java.time.ZoneId.systemDefault()).toLocalDate().key()
        return if (d == date) m.copy(at = taken).also { Log.i("Food", "время приёма по фото: ${clockOf(java.time.Instant.ofEpochMilli(taken).atZone(java.time.ZoneId.systemDefault()).let { z -> z.hour * 60 + z.minute })}") } else m
    }
    fun addPhoto(uri: Uri?, camera: Boolean = false) {
        uri ?: return
        scope.launch {
            val taken = if (camera) System.currentTimeMillis() else Photos.takenAt(ctx, uri)
            val name = Photos.import(ctx, repo.photosDir, uri)
            Log.i("Food", "фото добавлено: $name")
            if (name != null) onChange(withTime(current.copy(photos = current.photos + name), taken)) else notify(t("Не удалось загрузить фото"))
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(4)) { uris ->
        scope.launch {
            var m = current
            uris.forEach { u ->
                val taken = Photos.takenAt(ctx, u)
                Photos.import(ctx, repo.photosDir, u)?.let { m = withTime(m.copy(photos = m.photos + it), taken) }
            }
            onChange(m)
        }
    }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) addPhoto(cameraUri, camera = true)
    }

    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title ?: t("Приём пищи"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onDone, modifier = Modifier.testTag("meal_done")) { Text(t("Готово")) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).hScroll(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (showKind) mealKindsRu.forEach { k ->
                        FilterChip(selected = meal.kind == k, onClick = { onChange(meal.copy(kind = k)) }, label = { Text(t(k)) })
                    }
                }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, t("Удалить")) }
            }
            // сцепка с другим приёмом за этот день
            val mates = others.filter { meal.link.isNotEmpty() && it.unit == meal.link }
            if (mates.isNotEmpty()) Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)).padding(start = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Link, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(t("Один приём с: %s", mates.joinToString(", ") { it.text.ifBlank { t(it.kind) }.take(30) }),
                    Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                TextButton(onClick = onUnlink) { Text(t("Расцепить")) }
            } else {
                val units = mealUnits(others.filter { it.unit != meal.unit }).sortedByDescending { it[0].kind == meal.kind }
                if (units.isNotEmpty()) {
                    val same = units.firstOrNull { it[0].kind == meal.kind && meal.kind != mealKindsRu[3] }
                    Text(
                        if (same != null) t("%s уже есть — если это тот же приём, сцепите карточки:", t(meal.kind))
                        else t("Съели вместе с другим приёмом? Сцепите карточки:"),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (same != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalMinimumInteractiveComponentSize provides 0.dp) {
                        Row(Modifier.hScroll(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            units.forEach { u ->
                                androidx.compose.material3.AssistChip(onClick = { onLink(u[0].unit) },
                                    leadingIcon = { Icon(Icons.Filled.Link, null, Modifier.size(16.dp)) },
                                    label = { Text(t(u[0].kind) + (u[0].text.takeIf { it.isNotBlank() }?.let { ": " + it.take(18) + if (it.length > 18) "…" else "" } ?: ""), maxLines = 1) })
                            }
                        }
                    }
                }
            }
            // фото
            if (meal.photos.isNotEmpty()) Row(Modifier.hScroll(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                meal.photos.forEach { p ->
                    PhotoImage(File(repo.photosDir, p), 300,
                        Modifier.size(96.dp).clip(RoundedCornerShape(10.dp)).clickable { viewPhoto = p })
                }
            }
            var refOpen by remember { mutableStateOf(false) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = {
                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.PhotoLibrary, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Галерея"))
                }
                FilledTonalButton(onClick = {
                    val dir = File(ctx.cacheDir, "camera").apply { mkdirs() }
                    val f = File(dir, "shot_${System.currentTimeMillis()}.jpg")
                    val u = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                    cameraUri = u
                    runCatching { camera.launch(u) }.onFailure { notify(t("Камера недоступна")) }
                }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.PhotoCamera, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Камера"))
                }
                androidx.compose.material3.FilledTonalIconButton(onClick = { refOpen = true }) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, t("Справочник продуктов"))
                }
            }
            if (refOpen) FoodPickDialog(onDismiss = { refOpen = false }, onAll = {
                refOpen = false; act.openSettings.value = "ref"
            }) { f, g -> onChange(FoodDb.addTo(current, f, g)); refOpen = false; notify(t("Добавлено: %s", f.name)) }
            TextField2(meal.text, t("Что ели (можно не заполнять, если есть фото)")) { onChange(current.copy(text = it)) }
            NumberField(meal.kcal?.let(::numStr), t("ккал"), Modifier.fillMaxWidth(), label = t("Калории")) { onChange(current.copy(kcal = it)) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NumberField(meal.protein?.let(::numStr), t("г"), Modifier.weight(1f), label = t("Белки")) { onChange(current.copy(protein = it)) }
                NumberField(meal.fat?.let(::numStr), t("г"), Modifier.weight(1f), label = t("Жиры")) { onChange(current.copy(fat = it)) }
                // 5.58.1: ГН считается из ГИ и углеводов — правка углеводов пересчитывает её
                NumberField(meal.carbs?.let(::numStr), t("г"), Modifier.weight(1f), label = t("Углеводы")) { onChange(current.copy(carbs = it, gl = null)) }
            }
            // 5.58.1: ГИ и ГН — строкой «посчитано автоматически»; поля — по «Изменить»
            var giEdit by remember { mutableStateOf(false) }
            var giInfo by remember { mutableStateOf(false) }
            if (!giEdit) Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (meal.gi != null || meal.glEff != null) listOfNotNull(meal.gi?.let { t("ГИ %s", formatNumber(Math.round(it).toDouble())) },
                        meal.glEff?.let { t("ГН %s", formatNumber(Math.round(it).toDouble())) }).joinToString(" · ") else t("ГИ и ГН — не посчитаны"),
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("gi_line"))
                    Text(if (meal.gi == null) (if (act.prefs.aiReady) t("ИИ посчитает вместе с калориями") else t("считаются по ГИ — его даёт ИИ"))
                        else t("посчитано автоматически: ГН = ГИ × углеводы / 100"),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton({ giEdit = true }, Modifier.testTag("gi_edit")) { Text(t("Изменить")) }
                IconButton(onClick = { giInfo = true }) { Icon(Icons.AutoMirrored.Filled.HelpOutline, t("Что это")) }
            } else Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                NumberField(meal.gi?.let(::numStr), "", Modifier.weight(1f), label = t("ГИ")) { onChange(current.copy(gi = it)) }
                NumberField(meal.gl?.let(::numStr), "", Modifier.weight(1f), label = t("ГН"),
                    placeholder = meal.glEff?.takeIf { meal.gl == null }?.let { formatNumber(Math.round(it).toDouble()) }) { onChange(current.copy(gl = it)) }
                IconButton(onClick = { giInfo = true }) { Icon(Icons.AutoMirrored.Filled.HelpOutline, t("Что это")) }
            }
            if (giInfo) GlycemicInfoDialog { giInfo = false }
            // 5.39: без настроенного ИИ — без его полей; вместо них ссылка на настройку
            if (!act.prefs.aiReady) Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    Spacer(Modifier.width(8.dp))
                    Text(t("Калории, БЖУ и ГИ по фото или описанию может считать ИИ"), Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    TextButton({ act.openSettings.value = "ai" }) { Text(t("Подключить"), color = MaterialTheme.colorScheme.onTertiaryContainer, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) }
                }
            }
            if (act.prefs.aiReady) run {
                TextField2(meal.hint, t("Уточнение для расчёта: тарелка 24 см; смузи 400 мл — банан 40%, молоко 40%, овсянка 20%")) {
                    onChange(current.copy(hint = it))
                }
                Button(enabled = !busy, onClick = {
                    if (!act.prefs.aiReady) { notify(t("Настройте распознавание еды во вкладке «Ещё»")); return@Button }
                    if (current.photos.isEmpty() && current.text.isBlank() && current.hint.isBlank()) { notify(t("Добавьте фото или опишите блюдо")); return@Button }
                    busy = true
                    scope.launch {
                        runCatching {
                            Ai.estimate(act.prefs, current.photos.map { File(repo.photosDir, it) }, current.text, current.hint)
                        }.onSuccess { e ->
                            onChange(current.copy(
                                kcal = Math.round(e.kcal).toDouble(),
                                protein = e.protein?.let { Math.round(it).toDouble() },
                                fat = e.fat?.let { Math.round(it).toDouble() },
                                carbs = e.carbs?.let { Math.round(it).toDouble() },
                                gi = e.gi?.let { Math.round(it).toDouble() } ?: current.gi,
                                // 5.58.1: ГН — из ГИ и углеводов (glEff), чтобы совпадала с полями после правок
                                gl = if (e.gi != null || current.gi != null) null else e.gl?.let { Math.round(it).toDouble() } ?: current.gl,
                                text = current.text.ifBlank { e.description },
                            ))
                            notify(t("Оценка: %s ккал. Проверьте и поправьте при необходимости.", formatNumber(Math.round(e.kcal).toDouble())))
                        }.onFailure { Log.e("AI", "оценка калорий", it); notify(AiGuide.errorText(it.message)) }
                        busy = false
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (busy) t("Считаю…") else if (meal.photos.isNotEmpty()) t("Посчитать калории по фото") else t("Посчитать калории по описанию"))
                }
            }
        }
    }

    viewPhoto?.let { p ->
        Dialog(onDismissRequest = { viewPhoto = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().clickable { viewPhoto = null }) {
                PhotoImage(File(repo.photosDir, p), 2000, Modifier.fillMaxSize(), crop = false)
                FilledTonalButton(onClick = {
                    File(repo.photosDir, p).delete()
                    onChange(current.copy(photos = current.photos - p))
                    viewPhoto = null
                }, modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp)) {
                    Icon(Icons.Filled.Delete, null); Spacer(Modifier.width(6.dp)); Text(t("Удалить фото"))
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(t("Удалить приём пищи?")) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(t("Удалить"), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(t("Отмена")) } },
        )
    }
}

@Composable
fun TextField2(value: String, placeholder: String, onChange: (String) -> Unit) {
    var text by remember { mutableStateOf(value) }
    var pushed by remember { mutableStateOf(value) }
    if (value != pushed) { pushed = value; text = value }
    OutlinedTextField(
        text, { text = it; pushed = it; onChange(it) }, Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) }, minLines = 1,
    )
}

// =====================================================================
// Финансы
// =====================================================================

@Composable
fun FinanceBlock(repo: Repo, item: TemplateItem, date: String, value: String?) {
    val list = parsePurchases(value)
    fun save(l: List<Purchase>) = repo.setValue(date, item.id, purchasesToString(l))
    var editing by remember { mutableStateOf<Purchase?>(null) }
    var editBalance by remember { mutableStateOf(false) }
    val spent = list.filter { !it.income }.sumOf { it.total }
    val income = list.filter { it.income }.sumOf { it.total }
    val balance = repo.balanceOn(date)
    val startBalance = repo.balanceOn(java.time.LocalDate.parse(date).minusDays(1).key())

    SectionCard(item.displayName, action = {
        TextButton(onClick = { editBalance = true }) {
            Icon(Icons.Filled.AccountBalanceWallet, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
            Text(balance?.let { formatMoney(it) } ?: t("Баланс"))
        }
    }) {
        Column {
            if (list.isEmpty()) Text(t("Покупок нет"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            list.forEach { p ->
                Row(
                    Modifier.fillMaxWidth().clickable { editing = p }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name.ifBlank { t("Без названия") }, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${formatNumber(p.qty)} ${t(p.unit)} × ${formatMoney(p.price)}" +
                                if (p.category.isNotEmpty()) " · ${t(p.category)}" else "",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        (if (p.income) "+" else "−") + formatMoney(p.total),
                        fontWeight = FontWeight.SemiBold,
                        color = if (p.income) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
                HorizontalDivider()
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t("Потрачено: %s", formatMoney(spent)), fontWeight = FontWeight.SemiBold)
                    if (income > 0) Text(t("Доход: %s", formatMoney(income)), color = MaterialTheme.colorScheme.primary)
                    if (balance != null) Text(
                        (startBalance?.let { t("Баланс: %s → %s", formatMoney(it), formatMoney(balance)) } ?: t("Баланс на конец дня: %s", formatMoney(balance))) +
                            if (repo.hasBalanceFix(date)) " · " + t("правка") else "",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(onClick = { editing = Purchase(category = "") }) {
                    Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text(t("Покупка"))
                }
            }
        }
    }

    editing?.let { p ->
        PurchaseDialog(
            p, item.options, suggestions(repo, item.id), isNew = list.none { it.id == p.id },
            onSave = { np -> save(if (list.any { it.id == np.id }) list.map { if (it.id == np.id) np else it } else list + np); editing = null },
            onDelete = { save(list.filterNot { it.id == p.id }); editing = null },
            onDismiss = { editing = null },
        )
    }
    if (editBalance) {
        var s by remember { mutableStateOf(balance?.let { formatNumber(Math.round(it * 100) / 100.0) } ?: "") }
        AlertDialog(
            onDismissRequest = { editBalance = false },
            title = { Text(t("Баланс на конец дня %s", java.time.LocalDate.parse(date).ruShort())) },
            text = {
                Column {
                    Text(
                        t("Баланс считается сам: остаток предыдущего дня + доходы − расходы. Если он разошёлся с реальным — впишите, сколько денег у вас на конец этого дня (все счета и наличные); следующие дни пересчитаются от этой суммы."),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(s, { s = it }, singleLine = true, suffix = { Text(I18n.currency) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    if (repo.hasBalanceFix(date)) TextButton(onClick = { repo.setBalance(null, date); editBalance = false }) {
                        Text(t("Убрать правку этого дня"))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { parseNumber(s)?.let { repo.setBalance(it, date) }; editBalance = false }) { Text(t("Сохранить")) }
            },
            dismissButton = { TextButton(onClick = { editBalance = false }) { Text(t("Отмена")) } },
        )
    }
}

/** Частые покупки для быстрого ввода: название → последняя покупка. */
fun suggestions(repo: Repo, itemId: String): List<Purchase> {
    val all = repo.entries.value.toSortedMap().values.flatMap { parsePurchases(it[itemId]) }.filter { it.name.isNotBlank() }
    val last = all.associateBy { it.name.lowercase() }
    val freq = all.groupingBy { it.name.lowercase() }.eachCount()
    return freq.entries.sortedByDescending { it.value }.take(30).mapNotNull { last[it.key] }
}

private val unitsRu = listOf("шт", "кг", "г", "л", "уп")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PurchaseDialog(
    p: Purchase, categories: List<String>, sugg: List<Purchase>, isNew: Boolean,
    onSave: (Purchase) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(p.name) }
    var qty by remember { mutableStateOf(formatNumber(p.qty)) }
    var unit by remember { mutableStateOf(p.unit) }
    var price by remember { mutableStateOf(if (p.price == 0.0) "" else formatNumber(p.price)) }
    var cat by remember { mutableStateOf(p.category) }
    var income by remember { mutableStateOf(p.income) }
    val q = parseNumber(qty) ?: 1.0
    val pr = parseNumber(price) ?: 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) t("Новая покупка") else t("Покупка")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(t("Что купили")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                val matches = sugg.filter { name.isBlank() || (it.name.contains(name, true) && !it.name.equals(name, true)) }.take(6)
                if (isNew && matches.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    matches.forEach { s ->
                        FilterChip(selected = false, onClick = {
                            name = s.name; unit = s.unit; price = formatNumber(s.price); cat = s.category; income = s.income
                        }, label = { Text("${s.name} · ${formatMoney(s.price)}") })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(qty, { qty = it }, Modifier.weight(1f), label = { Text(t("Кол-во")) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(price, { price = it }, Modifier.weight(1.4f), label = { Text(t("Цена за ед.")) }, singleLine = true,
                        suffix = { Text(I18n.currency) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    unitsRu.forEach { u -> FilterChip(selected = unit == u, onClick = { unit = u }, label = { Text(t(u)) }) }
                }
                Text(t("Итого: %s", formatMoney(q * pr)), fontWeight = FontWeight.SemiBold)
                if (categories.isNotEmpty()) {
                    Text(t("Категория"), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        categories.forEach { c ->
                            FilterChip(selected = cat == c, onClick = { cat = if (cat == c) "" else c }, label = { Text(t(c)) })
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t("Это доход (зарплата, возврат…)"), Modifier.weight(1f))
                    Switch(income, { income = it })
                }
                if (!isNew) TextButton(onClick = onDelete) { Text(t("Удалить"), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = pr > 0, onClick = {
                onSave(p.copy(name = name.trim(), qty = q, unit = unit, price = pr, category = cat, income = income))
            }) { Text(t("Сохранить")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Отмена")) } },
    )
}


// =====================================================================
// Весы openScale
// =====================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BodyBlock(repo: Repo, item: TemplateItem, date: String, value: String?, manual: Boolean, srcValue: String? = null, onRevert: () -> Unit) {
    val day = BodyDay.parse(value)
    var edit by remember { mutableStateOf(false) }
    SectionCard(item.displayName, watch = false, action = {
        if (manual) TextButton(onClick = { repo.setManual(date, item.id, null, false); onRevert() }) { Text(t("Вернуть")) }
        TextButton(onClick = { edit = true }) {
            Icon(Icons.Filled.Edit, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(t("Изменить"))
        }
    }) {
        if (day == null || day.metrics.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.MonitorWeight, null, tint = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.width(8.dp))
                Text(t("Нет взвешивания за этот день"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val time = java.time.Instant.ofEpochMilli(day.time).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
            Text(
                (if (manual) t("исправлено вручную") else t("взвешивание в %s", "%02d:%02d".format(time.hour, time.minute)) +
                    " · " + (AppNames.from(srcValue) ?: "openScale")),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // 5.25: точность состава тела — утро натощак и правдоподобность скачков
            if (!manual && !day.morning()) Text(
                t("Взвешивание не утром — жир, мышцы и вода менее точны. Для сравнения между днями — утром натощак, после туалета."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            if (!manual) BodyCheck.jump(repo.entries.value, item.id, java.time.LocalDate.parse(date))?.let {
                Text(BodyCheck.text(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 3) {
                day.metrics.sortedBy { BodyCatalog.order(it.id) }.forEach { m ->
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        // тематическая иконка фоном: правый нижний угол, прозрачность 80 %
                        Icon(
                            BodyCatalog.icon(m.id), null,
                            Modifier.align(Alignment.BottomEnd).padding(4.dp).size(36.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                        )
                        Column(Modifier.padding(8.dp)) {
                            Text(BodyCatalog.title(m), style = MaterialTheme.typography.labelSmall, maxLines = 2, minLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                formatNumber(Math.round(m.value * 10) / 10.0) + bodyUnit(m.unit).let { if (it.isEmpty()) "" else " $it" },
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
    if (edit) {
        val base = day?.metrics?.takeIf { it.isNotEmpty() }
            ?: listOf(BodyMetric("manual:weight", t("Вес"), "kg", 0.0))
        val values = remember { base.map { mutableStateOf(if (it.value == 0.0) "" else formatNumber(Math.round(it.value * 10) / 10.0)) } }
        AlertDialog(
            onDismissRequest = { edit = false },
            title = { Text(t("Показатели за день")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    base.forEachIndexed { i, m ->
                        OutlinedTextField(values[i].value, { values[i].value = it }, Modifier.fillMaxWidth(),
                            label = { Text(BodyCatalog.title(m)) }, singleLine = true, suffix = { Text(bodyUnit(m.unit)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val ms = base.mapIndexedNotNull { i, m -> parseNumber(values[i].value)?.let { m.copy(value = it) } }
                    Log.i("Body", "ручная правка $date: $ms")
                    repo.setManual(date, item.id, if (ms.isEmpty()) null else BodyDay(day?.time ?: System.currentTimeMillis(), ms).toJson(), true)
                    edit = false
                }) { Text(t("Сохранить")) }
            },
            dismissButton = { TextButton(onClick = { edit = false }) { Text(t("Отмена")) } },
        )
    }
}
