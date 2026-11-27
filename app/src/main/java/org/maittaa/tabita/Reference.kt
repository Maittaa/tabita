package org.maittaa.tabita

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.withStyle

/**
 * Справочник (5.21): продукты (таблица с поиском), суточные ритмы, ГИ и ГН.
 * Открывается из настроек («Информация → Справочник») и кнопкой с книжкой в карточке приёма пищи —
 * там по нажатию на продукт его можно добавить с нужным весом.
 */
@Composable
fun ReferenceBody(start: Int = 0) {
    var tab by remember { mutableStateOf(start) }
    Row(Modifier.hScroll(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(t("Продукты"), t("Суточные ритмы"), t("ГИ и ГН")).forEachIndexed { i, s ->
            FilterChip(tab == i, { tab = i }, label = { Text(s) })
        }
    }
    Spacer(Modifier.height(8.dp))
    when (tab) {
        0 -> FoodTable(null)
        1 -> RhythmsBody()
        else -> GiBody()
    }
}

/** Таблица продуктов. [onPick] — режим добавления в приём пищи (продукт, граммы). */
@Composable
fun FoodTable(onPick: ((Food, Double) -> Unit)?) {
    var q by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf<String?>(null) }
    var sel by remember { mutableStateOf<Food?>(null) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val act = remember(ctx) { generateSequence(ctx) { (it as? android.content.ContextWrapper)?.baseContext }.firstNotNullOfOrNull { it as? MainActivity } }
    val prefs = remember { Prefs(ctx) }
    var favs by remember { mutableStateOf(prefs.foodFav) }
    var uses by remember { mutableStateOf(prefs.foodUse) }
    var sort by remember { mutableStateOf(prefs.foodSort) }
    val mine = act?.let { collect(it.repo.myFoods) } ?: FoodDb.mine
    val list = remember(q, cat, mine, sort, favs, uses) {
        val base = when (cat) {
            "fav" -> FoodDb.search(q, null).filter { it.key in favs }
            "top" -> FoodDb.search(q, null).filter { (uses[it.key] ?: 0) > 0 }.sortedByDescending { uses[it.key] ?: 0 }.take(30)
            else -> FoodDb.search(q, cat)
        }
        when (sort) {
            1 -> base.sortedByDescending { FoodScore.score(it) ?: -1 }
            2 -> base.sortedByDescending { it.p }
            3 -> base.sortedBy { it.kcal }
            4 -> base.sortedBy { it.gi ?: if (it.c < 5) 0 else 999 }
            else -> base
        }
    }
    var edit by remember { mutableStateOf<Food?>(null) }
    var creating by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    val pick: ((Food, Double) -> Unit)? = onPick?.let { cb ->
        { f: Food, g: Double ->
            uses = uses + (f.key to (uses[f.key] ?: 0) + 1); prefs.foodUse = uses
            cb(f, g)
        }
    }
    OutlinedTextField(
        q, { q = it; sel = null }, Modifier.fillMaxWidth(), singleLine = true,
        placeholder = { Text(t("Поиск: гречка, творог, банан…")) },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
    )
    Spacer(Modifier.height(6.dp))
    Row(Modifier.hScroll(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(cat == null, { cat = null }, label = { Text(t("Все")) })
        if (favs.isNotEmpty()) FilterChip(cat == "fav", { cat = if (cat == "fav") null else "fav" }, label = { Text("★ " + t("Избранное")) })
        if (uses.isNotEmpty()) FilterChip(cat == "top", { cat = if (cat == "top") null else "top" }, label = { Text(t("Частые")) })
        if (mine.isNotEmpty()) FilterChip(cat == "my", { cat = if (cat == "my") null else "my" }, label = { Text(t("Мои")) })
        FoodDb.cats.forEach { (k, _) -> FilterChip(cat == k, { cat = if (cat == k) null else k }, label = { Text(FoodDb.catName(k)) }) }
    }
    Row(Modifier.hScroll(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(t("Порядок:"), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        listOf(t("обычный"), t("полезные"), t("больше белка"), t("меньше ккал"), t("ниже ГИ")).forEachIndexed { i, s ->
            FilterChip(sort == i, { sort = i; prefs.foodSort = i }, label = { Text(s) })
        }
    }
    if (act != null) androidx.compose.material3.TextButton({ creating = true }) {
        Icon(Icons.Filled.Add, null, Modifier.padding(end = 4.dp))
        Text(t("Свой продукт"))
    }
    @Composable
    fun Cell(s: String, w: Float, bold: Boolean = false, head: Boolean = false, color: androidx.compose.ui.graphics.Color? = null) = Text(
        s, Modifier.width(w.dp), textAlign = TextAlign.End, maxLines = 1, softWrap = false,
        style = if (head) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
        fontWeight = if (bold) FontWeight.SemiBold else null,
        color = color ?: if (head) cs.onSurfaceVariant else cs.onSurface,
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.Bottom) {
        Text(t("на 100 г"), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        Cell(t("ккал"), 36f, head = true); Cell(t("Б"), 28f, head = true); Cell(t("Ж"), 28f, head = true)
        Cell(t("У"), 30f, head = true); Cell(t("ГИ"), 30f, head = true); Cell(t("Оц."), 26f, head = true)
    }
    HorizontalDivider()
    if (list.isEmpty()) Text(
        when (cat) {
            "fav" -> t("В избранном пусто. Откройте продукт и нажмите «★ В избранное».")
            else -> t("Ничего не нашлось. Если продукта нет — добавьте «Свой продукт» с цифрами с упаковки или впишите его в описание, калории посчитает ИИ.")
        },
        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
    fun n(v: Double) = if (v >= 100) formatNumber(Math.round(v).toDouble()) else formatNumber(Math.round(v * 10) / 10.0)
    list.forEach { f ->
        val on = sel == f
        val score = FoodScore.score(f)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                .background(if (on) cs.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                .clickable { sel = if (on) null else f }.padding(horizontal = 4.dp, vertical = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    androidx.compose.ui.text.buildAnnotatedString {
                        if (f.key in favs) withStyle(androidx.compose.ui.text.SpanStyle(color = cs.primary)) { append("★ ") }
                        append(f.name + (if (f.ml) " · " + t("100 мл") else "") + if (f.approx) " ≈" else "")
                        if (f.mine) withStyle(androidx.compose.ui.text.SpanStyle(color = cs.tertiary, fontSize = MaterialTheme.typography.labelSmall.fontSize)) { append("  " + t("мой")) }
                    },
                    Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                )
                Cell(formatNumber(Math.round(f.kcal).toDouble()), 36f, bold = true); Cell(n(f.p), 28f); Cell(n(f.f), 28f); Cell(n(f.c), 30f)
                Cell(f.gi?.let { (if (f.giEst) "≈" else "") + it } ?: "—", 30f)
                Cell(score?.toString() ?: "—", 26f, bold = true, color = score?.let { glyColor(FoodScore.level(it)) })
            }
            if (on) FoodPick(f, pick)
            if (on) ScoreDetails(f)
            if (on) Row {
                val fav = f.key in favs
                androidx.compose.material3.TextButton({
                    favs = if (fav) favs - f.key else favs + f.key; prefs.foodFav = favs
                }) { Text(if (fav) t("Убрать из избранного") else "★ " + t("В избранное")) }
                if (f.mine && act != null) {
                    androidx.compose.material3.TextButton({ edit = f }) { Text(t("Изменить")) }
                    androidx.compose.material3.TextButton({
                        act.repo.setMyFoods(act.repo.myFoods.value.filterNot { it.id == f.id }); sel = null
                    }) { Text(t("Удалить")) }
                }
            }
        }
    }
    if (act != null && (creating || edit != null)) MyFoodDialog(edit, onDismiss = { creating = false; edit = null }) { nf ->
        val l = act.repo.myFoods.value
        act.repo.setMyFoods(if (l.any { it.id == nf.id }) l.map { if (it.id == nf.id) nf else it } else l + nf)
        creating = false; edit = null; sel = null; cat = "my"
    }
    Spacer(Modifier.height(8.dp))
    Text(
        t("Калории и БЖУ — USDA FoodData Central и справочник «Химический состав российских пищевых продуктов» (Скурихин, Тутельян); ГИ — международные таблицы гликемического индекса (Atkinson и др., 2008, 2021), шкала глюкозы. «—» — ГИ не измерен или углеводов почти нет. «≈» — готовое блюдо, у разных рецептов цифры разные."),
        style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant,
    )
    Text(
        t("«Оц.» — оценка полезности 0–10: белок и гликемическая нагрузка на 100 ккал, калорийность, а у своих продуктов ещё клетчатка, сахара и насыщенные жиры с этикетки. Упрощённо, по мотивам Nutri-Score и рекомендаций ВОЗ; витамины и соль не учитываются."),
        style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp),
    )
}

/** Из чего сложилась оценка полезности. */
@Composable
private fun ScoreDetails(f: Food) {
    val parts = FoodScore.parts(f)
    val cs = MaterialTheme.colorScheme
    Column(Modifier.padding(top = 6.dp)) {
        if (parts == null) {
            Text(t("Почти без калорий — оценка не ставится."), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            return@Column
        }
        Text(t("Полезность: %s из 10", FoodScore.score(f)?.toString() ?: "—"), style = MaterialTheme.typography.labelMedium)
        parts.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.name, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                Text(p.value, style = MaterialTheme.typography.labelSmall, color = if (p.v != null) cs.onSurface else cs.onSurfaceVariant)
                if (p.v != null) Text(" ●", style = MaterialTheme.typography.labelSmall,
                    color = glyColor(if (p.v >= 0.67) 0 else if (p.v >= 0.34) 1 else 2))
            }
        }
    }
}

/** Раскрытая строка: на порцию и (в режиме добавления) поле граммов. */
@Composable
private fun FoodPick(f: Food, onPick: ((Food, Double) -> Unit)?) {
    var g by remember(f) { mutableStateOf(formatNumber(f.unitG ?: (f.portion ?: 100).toDouble())) }
    val grams = parseNumber(g)?.takeIf { it > 0 && it < 5000 }
    val unit = if (f.ml) t("мл") else t("г")
    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        f.portionName?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (f.unit != null && f.unitG != null) {
            Text((t("1 × %s = %s г", f.unit, formatNumber(f.unitG)) + (f.labelG?.let { " · " + t("порция на этикетке %s г", formatNumber(it)) } ?: ""))
                .let { if (f.ml) it.replace(" г", " " + t("мл")).replace(" g", " ml") else it },
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (onPick != null) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0.5, 1.0, 2.0, 3.0).forEach { u ->
                    androidx.compose.material3.AssistChip({ g = formatNumber(Math.round(u * f.unitG * 10) / 10.0) },
                        label = { Text(formatNumber(u) + " ×") })
                }
            }
        }
        if (onPick != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(g, { g = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' }.take(5) }, Modifier.width(110.dp),
                singleLine = true, suffix = { Text(unit) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            Button({ grams?.let { onPick(f, it) } }, enabled = grams != null) { Text(t("Добавить")) }
        }
        val k = (grams ?: f.unitG ?: (f.portion ?: 100).toDouble()) / 100
        Text(
            t("%s %s: %s ккал · Б %s · Ж %s · У %s", formatNumber(Math.round(k * 100).toDouble()), unit,
                formatNumber(Math.round(f.kcal * k).toDouble()), formatNumber(Math.round(f.p * k * 10) / 10.0),
                formatNumber(Math.round(f.f * k * 10) / 10.0), formatNumber(Math.round(f.c * k * 10) / 10.0)) +
                (f.gi?.let { " · " + t("ГН %s", formatNumber(Math.round(it * f.c * k / 100 * 10) / 10.0)) } ?: ""),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Пункт справки: заголовок и текст. */
private data class Note(val ru: String, val en: String, val textRu: String, val textEn: String)

@Composable
private fun Notes(list: List<Note>, footRu: String, footEn: String) {
    list.forEach { n ->
        Text(if (I18n.isEn) n.en else n.ru, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
        Text(if (I18n.isEn) n.textEn else n.textRu, style = MaterialTheme.typography.bodyMedium)
    }
    Text(if (I18n.isEn) footEn else footRu, style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
}

/** Суточные ритмы: что подтверждено исследованиями — и что из популярных «гормональных расписаний» нет. */
@Composable
fun RhythmsBody() {
    Text(
        t("Внутренние часы привязаны не к времени на циферблате, а к тому, когда вы встаёте, ложитесь и видите свет. Поэтому «расписание по часам» для всех не работает — ниже то, что известно достоверно."),
        style = MaterialTheme.typography.bodyMedium,
    )
    Notes(listOf(
        Note("Кортизол — утро", "Cortisol — morning",
            "Резко растёт в первые 30–45 минут после пробуждения (реакция пробуждения) и постепенно снижается к ночи. Это бодрость и мобилизация, а не «время решений». Помогает яркий свет в первый час после подъёма.",
            "Rises sharply in the first 30–45 minutes after waking (cortisol awakening response) and declines towards night. Bright light in the first hour after waking supports the rhythm."),
        Note("Мелатонин — вечер", "Melatonin — evening",
            "Начинает расти примерно за 2 часа до привычного времени сна, если вокруг темно. Яркий свет и экраны вечером его подавляют. К утру уровень падает сам — с ним «не борются» будильником.",
            "Starts rising about 2 hours before your usual bedtime in dim light. Bright light and screens in the evening suppress it; it falls by morning on its own."),
        Note("Гормон роста — первые часы сна", "Growth hormone — early sleep",
            "Главный выброс — в первом глубоком сне, через 1–3 часа после засыпания, когда бы вы ни легли. Поэтому важны достаточный сон и регулярность, а не отбой ровно в 23:00. Дневной сон по 20 минут его заметно не добавляет.",
            "The main pulse comes with the first deep sleep, 1–3 hours after falling asleep, whenever that is. Enough regular sleep matters, not a fixed 11 pm bedtime."),
        Note("Тестостерон", "Testosterone",
            "У мужчин утром выше, к вечеру ниже (разница в молодом возрасте — около 20–30 %). Неделя сна по 5 часов снижает его примерно на 10–15 %. Время тренировки на этот ритм почти не влияет.",
            "In men it is higher in the morning and lower in the evening (about 20–30 % difference in young men). A week of 5-hour nights lowers it by roughly 10–15 %."),
        Note("Тренировки", "Training",
            "Сила, мощность и выносливость у большинства максимальны во второй половине дня (примерно 15–20 ч): теплее тело, лучше реакция. Утром тоже можно — тело привыкает к регулярному времени. Тяжёлую тренировку лучше закончить за 1,5–2 часа до сна.",
            "Strength, power and endurance usually peak in the late afternoon (about 3–8 pm). Mornings work too — the body adapts to a regular time. Finish hard sessions 1.5–2 hours before bed."),
        Note("Еда и сахар крови", "Food and blood sugar",
            "Чувствительность к инсулину утром и днём выше, чем поздно вечером: та же еда вечером даёт больший подъём сахара. Удобно, когда основная еда — в первой половине дня, а ужин — за 2–3 часа до сна.",
            "Insulin sensitivity is higher in the morning and afternoon than late in the evening, so the same meal raises blood sugar more at night. Have dinner 2–3 hours before bed."),
        Note("Сон", "Sleep",
            "Взрослым нужно 7–9 часов. Регулярность (отбой и подъём в одно время ±30 минут, в выходные тоже) важна почти так же, как длительность. В первой половине ночи больше глубокого сна, во второй — быстрого (REM).",
            "Adults need 7–9 hours. Regular bed and wake times (±30 min, weekends too) matter almost as much as duration. Deep sleep dominates the first half of the night, REM the second."),
        Note("Не подтверждено", "Not supported by evidence",
            "«Серотонин вырабатывается, только если встать до 6 утра», «эндорфины в 22:00», «час сна до полуночи стоит двух», «спорт после 17:00 вреден», «стакан воды утром вымывает гормоны стресса», «пик ДГЭА в 10–14 для умственной работы» — таких данных нет.",
            "“Serotonin only if you wake before 6 am”, “endorphins at 10 pm”, “an hour before midnight is worth two”, “exercise after 5 pm is harmful”, “morning water flushes stress hormones” — none of these is supported by research."),
    ),
        "Источники: обзоры по циркадным ритмам (Nature Reviews Endocrinology, Sleep Medicine Reviews), рекомендации American Academy of Sleep Medicine по длительности сна, исследования реакции кортизола на пробуждение и суточной работоспособности. Это справка, не медицинская консультация.",
        "Sources: reviews on circadian rhythms (Nature Reviews Endocrinology, Sleep Medicine Reviews), American Academy of Sleep Medicine sleep duration recommendations, research on the cortisol awakening response and time-of-day performance. Reference only, not medical advice.",
    )
}

/** ГИ и ГН — что это и как читать. */
@Composable
fun GiBody() {
    Notes(listOf(
        Note("Гликемический индекс (ГИ)", "Glycemic index (GI)",
            "Насколько быстро углеводы продукта поднимают сахар крови по сравнению с глюкозой (глюкоза = 100). Низкий ≤ 55, средний 56–69, высокий ≥ 70. ГИ меряют только у продуктов с углеводами: у мяса, рыбы, яиц, масел его нет.",
            "How fast a food's carbohydrate raises blood sugar compared with glucose (= 100). Low ≤ 55, medium 56–69, high ≥ 70. Only foods with carbs have a GI."),
        Note("Гликемическая нагрузка (ГН)", "Glycemic load (GL)",
            "Учитывает и скорость, и количество: ГН = ГИ × углеводы в порции (г) / 100. Арбуз с высоким ГИ в обычной порции даёт небольшую ГН, а большая тарелка белого риса — высокую. Для приёма пищи: низкая ≤ 10, средняя 11–19, высокая ≥ 20; за день — меньше 80 хорошо, больше 120 много.",
            "Combines speed and amount: GL = GI × carbs per serving (g) / 100. Meal: low ≤ 10, medium 11–19, high ≥ 20; per day under 80 is good, over 120 is a lot."),
        Note("Что меняет ГИ", "What changes GI",
            "Белок, жир и клетчатка в том же приёме замедляют всасывание. Разваривание, пюре и измельчение повышают ГИ; остывшие картофель и рис (резистентный крахмал) — немного понижают. Спелый банан выше зелёного.",
            "Protein, fat and fibre in the same meal slow absorption. Overcooking, mashing and grinding raise GI; cooled potatoes and rice lower it slightly. Ripe bananas are higher than green ones."),
        Note("Осторожно с таблицами", "Beware of tables",
            "В части популярных таблиц ГИ дан по «шкале хлеба» (белый хлеб = 100) — отсюда цифры вроде «хлеб 136», «финики 146». По шкале глюкозы белый хлеб ≈ 75, финики ≈ 42. Сверяйте шкалу и единицу: «на 100 г» или «на порцию».",
            "Some popular tables use the bread scale (white bread = 100), hence “bread 136”. On the glucose scale white bread is ≈ 75. Check the scale and whether values are per 100 g or per serving."),
    ),
        "Источник: International Tables of Glycemic Index and Glycemic Load Values (Atkinson, Foster-Powell, Brand-Miller; Diabetes Care 2008; Am J Clin Nutr 2021).",
        "Source: International Tables of Glycemic Index and Glycemic Load Values (Atkinson, Foster-Powell, Brand-Miller; Diabetes Care 2008; Am J Clin Nutr 2021).",
    )
}

/** Окно выбора продукта из карточки приёма пищи. */
@Composable
fun FoodPickDialog(onDismiss: () -> Unit, onAll: () -> Unit, onPick: (Food, Double) -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        androidx.compose.material3.Surface(
            Modifier.fillMaxWidth(0.95f).androidx_heightCap(), shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.verticalScrollCompat().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t("Справочник продуктов"), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    androidx.compose.material3.IconButton(onClick = onDismiss) { Icon(androidx.compose.material.icons.Icons.Filled.Close, t("Закрыть")) }
                }
                Text(t("Нажмите на продукт, укажите вес и «Добавить» — калории, БЖУ и ГИ прибавятся к приёму пищи."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                FoodTable(onPick)
                androidx.compose.material3.TextButton(onClick = onAll) { Text(t("Весь справочник: суточные ритмы, ГИ и ГН")) }
            }
        }
    }
}

private fun Modifier.androidx_heightCap() = this.then(Modifier.fillMaxHeight(0.9f))
@Composable
private fun Modifier.verticalScrollCompat() = this.then(Modifier.verticalScroll(rememberScrollState()))


/** Свой продукт: цифры «на порцию» с этикетки (или на 100 г), единица (мерная ложка) — сколько их в порции. */
@Composable
fun MyFoodDialog(f: Food?, onDismiss: () -> Unit, onSave: (Food) -> Unit) {
    val lg = f?.labelG ?: 100.0
    fun per(v: Double?) = v?.let { formatNumber(Math.round(it * lg / 100 * 10) / 10.0) } ?: ""
    var name by remember { mutableStateOf(f?.ru ?: "") }
    var portion by remember { mutableStateOf(f?.labelG?.let { formatNumber(it) } ?: "100") }
    var kcal by remember { mutableStateOf(per(f?.kcal)) }
    var p by remember { mutableStateOf(per(f?.p)) }
    var fat by remember { mutableStateOf(per(f?.f)) }
    var c by remember { mutableStateOf(per(f?.c)) }
    var fib by remember { mutableStateOf(per(f?.fib)) }
    var sug by remember { mutableStateOf(per(f?.sug)) }
    var sat by remember { mutableStateOf(per(f?.sat)) }
    var gi by remember { mutableStateOf(f?.gi?.toString() ?: "") }
    var giEst by remember { mutableStateOf(f?.giEst ?: false) }
    var unit by remember { mutableStateOf(f?.unit ?: "мерная ложка") }
    var units by remember { mutableStateOf(f?.unitG?.let { formatNumber(Math.round(lg / it * 10) / 10.0) } ?: "") }
    val pg = parseNumber(portion)?.takeIf { it > 0 }
    val pp = parseNumber(p); val ff = parseNumber(fat); val cc = parseNumber(c)
    val autoKcal = if (pp != null || ff != null || cc != null) Food.kcalOf(pp ?: 0.0, ff ?: 0.0, cc ?: 0.0) else null
    val kk = parseNumber(kcal) ?: autoKcal
    val ok = name.isNotBlank() && pg != null && kk != null
    fun build() = Food.fromLabel(name.trim(), pg!!, kk!!, pp ?: 0.0, ff ?: 0.0, cc ?: 0.0,
        gi.toIntOrNull()?.takeIf { it in 1..110 }, unit.trim(), parseNumber(units),
        id = f?.id ?: java.util.UUID.randomUUID().toString(),
        fib = parseNumber(fib), sug = parseNumber(sug), sat = parseNumber(sat), giEst = giEst)
    val cs = MaterialTheme.colorScheme
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (f == null) t("Свой продукт") else t("Изменить продукт")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(t("Перепишите цифры с этикетки «в 1 порции» (или «на 100 г» — тогда порция 100). Калории можно не вводить — посчитаются по БЖУ."),
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(t("Название")) }, singleLine = true)
                @Composable
                fun Num(v: String, set: (String) -> Unit, label: String, suffix: String, mod: Modifier, ph: String? = null) = OutlinedTextField(
                    v, { set(it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' }.take(6)) }, mod, label = { Text(label, maxLines = 1) },
                    placeholder = ph?.let { { Text(it) } }, suffix = { Text(suffix) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Num(portion, { portion = it }, t("Порция"), t("г"), Modifier.weight(1f))
                    Num(kcal, { kcal = it }, t("Калории"), t("ккал"), Modifier.weight(1f),
                        ph = autoKcal?.let { "≈" + formatNumber(Math.round(it).toDouble()) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Num(p, { p = it }, t("Белки"), t("г"), Modifier.weight(1f))
                    Num(fat, { fat = it }, t("Жиры"), t("г"), Modifier.weight(1f))
                    Num(c, { c = it }, t("Углеводы"), t("г"), Modifier.weight(1f))
                }
                Text(t("Если есть на этикетке:"), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Num(fib, { fib = it }, t("Клетчатка"), t("г"), Modifier.weight(1f))
                    Num(sug, { sug = it }, t("Сахара"), t("г"), Modifier.weight(1f))
                    Num(sat, { sat = it }, t("Насыщ."), t("г"), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(unit, { unit = it.take(30) }, Modifier.weight(1.4f), label = { Text(t("Единица")) }, singleLine = true)
                    Num(units, { units = it }, t("В порции"), "×", Modifier.weight(1f))
                }
                if (pg != null && parseNumber(units)?.takeIf { it > 0 } != null && unit.isNotBlank())
                    Text(t("1 × %s = %s г", unit, formatNumber(Math.round(pg / parseNumber(units)!! * 10) / 10.0)),
                        style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                Num(gi, { gi = it.filter { ch -> ch.isDigit() }.take(3); giEst = false }, if (giEst) t("ГИ ≈ как у похожего") else t("ГИ, если известен"), "", Modifier.fillMaxWidth())
                val similar = remember(name) { FoodDb.similarGi(name) }
                if (gi.isBlank() && (cc ?: 0.0) > 0) {
                    Text(t("ГИ не вычисляется из БЖУ — его измеряют на людях. Возьмите с упаковки или как у похожего продукта:"),
                        style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    if (similar.isEmpty()) Text(t("Похожих по названию нет — найдите в справочнике и впишите вручную."),
                        style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    else Row(Modifier.hScroll(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        similar.forEach { s ->
                            androidx.compose.material3.AssistChip({ gi = s.gi.toString(); giEst = true }, label = { Text("${s.name} · ${s.gi}") })
                        }
                    }
                }
                if (ok) {
                    val nf = build()
                    val giV = nf.gi
                    Text(
                        listOfNotNull(
                            if (parseNumber(kcal) == null) t("Калории по БЖУ: %s на порцию", formatNumber(Math.round(kk!!).toDouble())) else null,
                            giV?.let { t("ГН: %s на порцию, %s на 100 г", formatNumber(Math.round(it * (cc ?: 0.0) / 10) / 10.0), formatNumber(Math.round(it * nf.c / 10) / 10.0)) },
                            FoodScore.score(nf)?.let { t("Полезность: %s из 10", it.toString()) },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton({ onSave(build()) }, enabled = ok) { Text(t("Сохранить")) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onDismiss) { Text(t("Отмена")) } },
    )
}
