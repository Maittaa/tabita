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

@Composable
fun MealsBlock(act: MainActivity, item: TemplateItem, date: String, value: String?, notify: (String) -> Unit) {
    val repo = act.repo
    val meals = parseMeals(value)
    fun save(list: List<Meal>) = repo.setValue(date, item.id, mealsToString(list))
    val total = mealKcal(meals)
    var open by remember { mutableStateOf<String?>(null) }
    var askLink by remember { mutableStateOf<Pair<String, Meal>?>(null) }

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
            if (meals.any { it.kcal != null || it.gi != null || it.carbs != null }) GlycemicDaySummary(meals)
            OutlinedButton(onClick = {
                val h = LocalTime.now().hour
                val kind = when { h < 11 -> mealKindsRu[0]; h < 16 -> mealKindsRu[1]; h < 21 -> mealKindsRu[2]; else -> mealKindsRu[3] }
                val same = meals.firstOrNull { it.kind == kind }
                if (same != null && kind != mealKindsRu[3]) askLink = kind to same
                else { val m = Meal(kind = kind); save(meals + m); open = m.id }
            }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text(t("Добавить приём пищи"))
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
                    MealCard(act, editing, notify,
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
                    Text(t(meal.kind), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
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
private fun MealCard(
    act: MainActivity, meal: Meal, notify: (String) -> Unit, onChange: (Meal) -> Unit, onDelete: () -> Unit, onDone: () -> Unit,
    others: List<Meal> = emptyList(), onLink: (String) -> Unit = {}, onUnlink: () -> Unit = {},
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

    fun addPhoto(uri: Uri?) {
        uri ?: return
        scope.launch {
            val name = Photos.import(ctx, repo.photosDir, uri)
            Log.i("Food", "фото добавлено: $name")
            if (name != null) onChange(current.copy(photos = current.photos + name)) else notify(t("Не удалось загрузить фото"))
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(4)) { uris ->
        scope.launch {
            var m = current
            uris.forEach { u -> Photos.import(ctx, repo.photosDir, u)?.let { m = m.copy(photos = m.photos + it) } }
            onChange(m)
        }
    }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) addPhoto(cameraUri)
    }

    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("Приём пищи"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onDone) { Text(t("Готово")) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    mealKindsRu.forEach { k ->
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
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
            if (meal.photos.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                meal.photos.forEach { p ->
                    PhotoImage(File(repo.photosDir, p), 300,
                        Modifier.size(96.dp).clip(RoundedCornerShape(10.dp)).clickable { viewPhoto = p })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            }
            TextField2(meal.text, t("Что ели (можно не заполнять, если есть фото)")) { onChange(current.copy(text = it)) }
            NumberField(meal.kcal?.let(::numStr), t("ккал"), Modifier.fillMaxWidth(), label = t("Калории")) { onChange(current.copy(kcal = it)) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NumberField(meal.protein?.let(::numStr), t("г"), Modifier.weight(1f), label = t("Белки")) { onChange(current.copy(protein = it)) }
                NumberField(meal.fat?.let(::numStr), t("г"), Modifier.weight(1f), label = t("Жиры")) { onChange(current.copy(fat = it)) }
                NumberField(meal.carbs?.let(::numStr), t("г"), Modifier.weight(1f), label = t("Углеводы")) { onChange(current.copy(carbs = it)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                NumberField(meal.gi?.let(::numStr), "", Modifier.weight(1f), label = t("ГИ")) { onChange(current.copy(gi = it)) }
                NumberField(meal.gl?.let(::numStr), "", Modifier.weight(1f), label = t("ГН"),
                    placeholder = meal.glEff?.takeIf { meal.gl == null }?.let { formatNumber(Math.round(it).toDouble()) }) { onChange(current.copy(gl = it)) }
                var giInfo by remember { mutableStateOf(false) }
                IconButton(onClick = { giInfo = true }) { Icon(Icons.AutoMirrored.Filled.HelpOutline, t("Что это")) }
                if (giInfo) GlycemicInfoDialog { giInfo = false }
            }
            if (meal.gi == null && meal.gl == null) Text(t("ГИ и ГН ИИ посчитает вместе с калориями; ГН без ввода считается из ГИ и углеводов."),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            run {
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
                                gl = e.gl?.let { Math.round(it).toDouble() } ?: current.gl,
                                text = current.text.ifBlank { e.description },
                            ))
                            notify(t("Оценка: %s ккал. Проверьте и поправьте при необходимости.", formatNumber(Math.round(e.kcal).toDouble())))
                        }.onFailure { Log.e("AI", "оценка калорий", it); notify(t("Не удалось оценить: %s", it.message)) }
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
