package org.maittaa.tabita

import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EggAlt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Советы дня (5.20): короткие подсказки по данным дневника — питание, вода, шаги, тренировки, сон, вес.
 * Считаются только для сегодняшнего дня (зависят от времени суток). Категории отключаются в настройке плитки,
 * отдельный совет можно скрыть до конца дня.
 */
data class Tip(val id: String, val cat: String, val icon: ImageVector, val title: String, val text: String, val prio: Int)

object Tips {
    /** Категории: ключ → название. */
    val cats = listOf("food" to "Питание", "water" to "Вода", "move" to "Шаги", "train" to "Тренировки", "sleep" to "Сон и восстановление", "weight" to "Вес", "rhythm" to "Ритм дня (диаграмма)")

    private fun n(v: Double) = formatNumber(v.roundToInt().toDouble())
    private fun n1(v: Double) = formatNumber((v * 10).roundToInt() / 10.0)

    fun build(repo: Repo, prefs: Prefs, date: LocalDate, now: LocalTime = LocalTime.now()): List<Tip> {
        if (date != LocalDate.now()) return emptyList()
        val tmpl = repo.template.value.filter { !it.hidden }
        val ents = repo.entries.value
        val day = ents[date.toString()] ?: emptyMap()
        val h = now.hour + now.minute / 60.0
        val prof = repo.profile.value
        val w = Anthro.weightOn(tmpl, ents, date)
        val burn = Anthro.energyOn(prof, tmpl, ents, date)?.total
        val out = mutableListOf<Tip>()
        fun dayOf(d: LocalDate) = ents[d.toString()] ?: emptyMap()
        // 5.29: цель из «Плана» — нормы и тон советов под неё
        val goal = Goal.of(prefs.planGoal)
        val bmi = w?.let { ww -> prof.h?.let { Anthro.bmi(ww, it) } }
        val goalProtein = goal?.let { g -> w?.let { ww -> (if ((bmi ?: 0.0) >= 30) 27 * prof.h!! * prof.h!! else ww) * Program.proteinPerKg(g) } }
        val goalKcal = goal?.let { g -> burn?.let { it * Program.kcalFactor(g) } }

        // ---------- питание ----------
        tmpl.firstOrNull { it.type == ItemType.MEALS }?.let { item ->
            val meals = parseMeals(day[item.id])
            val kcal = mealKcal(meals)
            val mac = mealMacros(meals)
            val norms = DayNorms.of(burn, w)
            if (meals.isEmpty() && h >= 11) out += Tip("food_log", "food", Icons.Filled.Restaurant, t("Запишите еду"),
                t("Без записей не посчитать БЖУ и энергобаланс. Можно просто сфотографировать тарелку — калории оценит ИИ."), 55)
            // белок по ходу дня: к вечеру — вся норма
            val frac = ((h - 7) / 13).coerceIn(0.25, 1.0)
            (goalProtein ?: norms.protein?.start)?.let { target ->
                val have = mac?.protein ?: 0.0
                val need = target * frac
                // 5.31: добирать белок — только когда цель от него зависит; на похудении — лишь если сильно ниже минимума,
                // для самочувствия — если вечером меньше 0,8 г/кг (минимум взрослого, ВОЗ)
                val wants = when {
                    goal == null || Program.proteinMatters(goal) -> have < need - 15
                    goal == Goal.LOSE -> h >= 16 && have < need * 0.6
                    else -> h >= 19 && w != null && have < w * 0.8
                }
                if (meals.isNotEmpty() && wants) {
                    val opts = Program.proteinOptions(prefs, minOf(target - have, 30.0), 3)
                    out += Tip("food_protein", "food", Icons.Filled.EggAlt,
                        when (goal) {
                            Goal.LOSE -> t("Мало белка — на дефиците уходят мышцы")
                            Goal.WELLBEING -> t("Белка сегодня мало")
                            else -> t("Доберите белок: ещё ~%s г", n(target - have))
                        },
                        t("Сейчас %s г из %s.", n(have), n(target)) + " " +
                            (if (opts.isNotEmpty()) t("Например: %s.", opts.joinToString("; ") { it.food.name.lowercase() + " " + it.amount() + " — " + n(it.p) + " " + t("г") })
                            else t("Например: творог 5 % 200 г — 34 г, куриная грудка 150 г — 45 г, 3 яйца — 19 г.")), 80)
                }
            }
            if (goalKcal != null && kcal != null) {
                // под цель: на похудении — «лимит», на наборе — «добрать»
                val left = goalKcal - kcal
                if (goal == Goal.LOSE) {
                    if (left < -150) out += Tip("food_over", "food", Icons.Filled.LocalFireDepartment, t("Лимит дня превышен на ~%s ккал", n(-left)),
                        t("Цель — %s ккал. Вечером — овощи и белок (рыба, курица, творог 0–1 %%), без сладкого; завтра без «отработки» — просто по плану.", n(goalKcal)), 70)
                    else if (h >= 15 && left in 0.0..400.0 && left < goalKcal * 0.25) out += Tip("food_left", "food", Icons.Filled.LocalFireDepartment,
                        t("На ужин ~%s ккал", n(left)), t("Чтобы уложиться: белок 150 г (курица, рыба, творог) и тарелка овощей — без гарнира или с ложкой крупы."), 55)
                } else if (goal != Goal.WELLBEING && h >= 16 && left > 400) out += Tip("food_left", "food", Icons.Filled.LocalFireDepartment,
                    t("До цели ещё ~%s ккал", n(left)),
                    t("Закроет ужин с белком и гарниром: крупа 200 г + мясо или рыба 150 г + овощи ≈ 600–700 ккал. Перекус: орехи 30 г — 180 ккал, банан — 100 ккал, кефир 250 мл — 130 ккал."), 65)
            } else if (burn != null && kcal != null) {
                val left = burn - kcal
                if (h >= 17 && left > 500) out += Tip("food_left", "food", Icons.Filled.LocalFireDepartment,
                    t("До расхода ещё ~%s ккал", n(left)),
                    t("Закроет ужин с белком и гарниром: крупа 200 г + мясо или рыба 150 г + овощи ≈ 600–700 ккал. Перекус: орехи 30 г — 180 ккал, банан — 100 ккал, кефир 250 мл — 130 ккал."), 65)
                if (kcal > burn * 1.2) out += Tip("food_over", "food", Icons.Filled.LocalFireDepartment,
                    t("Съедено больше расхода на ~%s ккал", n(kcal - burn)),
                    t("Если цель — не набор массы, вечером лёгкий ужин: овощи и белок (рыба, курица, творог), без сладкого и выпечки."), 60)
            }
            if (mac != null && h >= 16) norms.fat?.let { r ->
                if (mac.fat > r.endInclusive * 1.15) out += Tip("food_fat", "food", Icons.Filled.Restaurant, t("Жиров больше нормы"),
                    t("~%s г при норме до %s г. Меньше жареного, сыра, колбасы и выпечки; масло отмеряйте ложкой.", n(mac.fat), n(r.endInclusive)), 45)
            }
            Glycemic.day(meals)?.peak?.let { p ->
                val gl = p.glEff ?: 0.0
                if (gl > 20) out += Tip("food_gl", "food", Icons.Filled.Restaurant, t("Высокий пик сахара: ГН %s", n(gl)),
                    t("Добавляйте к углеводам белок и овощи, берите цельные крупы вместо белого риса и выпечки, фрукты — целиком, а не соком."), 40)
            }
            // энергобаланс за 7 дней
            val bal = (1..7).mapNotNull { i ->
                val d = date.minusDays(i.toLong())
                val e = mealKcal(parseMeals(dayOf(d)[item.id])) ?: return@mapNotNull null
                Anthro.energyOn(prof, tmpl, ents, d)?.let { e - it.total }
            }
            if (bal.size >= 3) {
                val avg = bal.average()
                val kgWeek = avg * 7 / 7700
                when (goal) {
                    Goal.LOSE -> {
                        if (avg > -150) out += Tip("food_surplus", "food", Icons.Filled.MonitorWeight, t("Дефицита за неделю нет"),
                            t("В среднем %s ккал к расходу — вес стоит. Для похудения нужен минус 15–20 %%: ~%s ккал в день.", (if (avg >= 0) "+" else "−") + n(abs(avg)), burn?.let { n(it * 0.18) } ?: "400–500"), 55)
                        else if (burn != null && avg < -burn * 0.3) out += Tip("food_deficit", "food", Icons.Filled.MonitorWeight, t("Слишком большой дефицит: ~%s ккал", n(-avg)),
                            t("Больше 25–30 %% от расхода — уходят мышцы, падают силы и сон. Добавьте ~%s ккал в день, лучше белком.", n(-avg - burn * 0.2)), 55)
                    }
                    Goal.GAIN, Goal.MUSCLE -> {
                        if (avg < 0) out += Tip("food_deficit", "food", Icons.Filled.MonitorWeight, t("Неделя в дефиците ~%s ккал в день", n(-avg)),
                            t("Для набора нужен профицит: +200–400 ккал к расходу. Без него мышцы почти не растут."), 60)
                        if (avg > 600) out += Tip("food_surplus", "food", Icons.Filled.MonitorWeight, t("Неделя с профицитом ~%s ккал в день", n(avg)),
                            t("Вес будет расти примерно на %s кг в неделю. Для набора мышц хватает +200–400 ккал — остальное уйдёт в жир.", n1(kgWeek)), 45)
                    }
                    else -> {}
                }
                if (goal == null && avg < -400) out += Tip("food_deficit", "food", Icons.Filled.MonitorWeight, t("Неделя в дефиците ~%s ккал в день", n(-avg)),
                    t("Вес будет снижаться примерно на %s кг в неделю. Для набора мышц нужен небольшой профицит: +200–400 ккал к расходу.", n1(-kgWeek)), 50)
                if (goal == null && avg > 500) out += Tip("food_surplus", "food", Icons.Filled.MonitorWeight, t("Неделя с профицитом ~%s ккал в день", n(avg)),
                    t("Вес будет расти примерно на %s кг в неделю. Для набора мышц хватает +200–400 ккал — остальное уйдёт в жир.", n1(kgWeek)), 45)
            }
        }

        // ---------- вода ----------
        tmpl.firstOrNull { it.type == ItemType.WATER }?.let { item ->
            val l = parseNumber(day[item.id]) ?: 0.0
            val norm = Hydration.normOf(item, day)
            val expect = norm * ((h - 8) / 13).coerceIn(0.0, 1.0)
            // 5.42: после тренировки — сколько добрать за неё
            val exL = runCatching { Hydration.calc(repo, prefs, date.key(), day)?.workoutLiters }.getOrNull() ?: 0.0
            if (l < expect - 0.4) out += Tip("water", "water", Icons.Filled.WaterDrop, t("Выпейте воды"),
                t("%s из %s л — к этому часу лучше бы ~%s л. Стакан сейчас и по стакану к каждому приёму пищи.", n1(l), n1(norm), n1(expect)) +
                    (if (exL >= 0.2) " " + t("Из нормы ~%s л — на тренировку: их лучше выпить в ближайшие пару часов после неё.", n1(exL)) else ""), 50)
        }

        // ---------- шаги ----------
        val steps = Anthro.stepsOf(tmpl, day)
        if (tmpl.any { it.source == Source.HC_STEPS }) {
            val s = steps ?: 0.0
            if (h >= 17 && s < 6000) {
                val kcal = w?.let { ww -> Anthro.stepsKm(prof, 3000.0)?.let { 0.5 * ww * it } }
                out += Tip("move_walk", "move", Icons.AutoMirrored.Filled.DirectionsWalk, t("Прогулка 20–30 минут"),
                    t("Сегодня %s шагов. Прогулка добавит ~3000", n(s)) + (kcal?.let { t(" и ~%s ккал", n(it)) } ?: "") + ".", 55)
            } else if (h in 11.0..17.0 && s < (h - 8) * 500) out += Tip("move_sit", "move", Icons.AutoMirrored.Filled.DirectionsWalk,
                t("Пора размяться"), t("С утра %s шагов. Вставайте и проходитесь по 3–5 минут каждый час — спине и сосудам это важнее, чем одна долгая прогулка.", n(s)), 35)
            val prev = (1..7).mapNotNull { Anthro.stepsOf(tmpl, dayOf(date.minusDays(it.toLong()))) }
            if (prev.size >= 4 && prev.average() < 5000) out += Tip("move_week", "move", Icons.AutoMirrored.Filled.DirectionsWalk,
                t("Мало шагов за неделю: ~%s в день", n(prev.average())),
                t("Цель на ближайшую неделю — +1500 шагов в день: выходите на остановку раньше, ходите по лестнице, звонки — на ходу."), 40)
        }

        // ---------- тренировки ----------
        val exItem = tmpl.firstOrNull { it.source == Source.HC_EXERCISE }
        if (exItem != null) {
            val last = (0..21).firstOrNull { i -> Workouts.sessions(dayOf(date.minusDays(i.toLong())), exItem.id).any { it.kind == "strength" } }
            // 5.25: по тяжести — прогулки не тренировка
            val yMin = Workouts.loadMin(Workouts.sessions(dayOf(date.minusDays(1)), exItem.id)).toInt()
            val sleepMin = tmpl.firstOrNull { it.source == Source.HC_SLEEP }?.let { parseNumber(day[it.id]) }
            when {
                yMin >= 45 && sleepMin != null && sleepMin < 390 -> out += Tip("train_rest", "train", Icons.Filled.FitnessCenter, t("День восстановления"),
                    t("Вчера тренировка %s мин, а сон короткий. Сегодня лучше прогулка или растяжка — мышцы растут во время отдыха.", n(yMin.toDouble())), 60)
                last == null || last >= 3 -> out += Tip("train_go", "train", Icons.Filled.FitnessCenter, t("Пора на силовую"),
                    (if (last == null) t("За три недели силовых не было.") else t("Последняя силовая %d дн. назад.", last)) + " " +
                        t("Для роста мышц — 2–3 тренировки в неделю: каждая группа мышц хотя бы дважды."), 50)
            }
        }

        // ---------- сон и восстановление ----------
        var sleepAvg: Double? = null
        tmpl.firstOrNull { it.source == Source.HC_SLEEP }?.let { item ->
            val m = parseNumber(day[item.id])
            if (m != null && m < 390) out += Tip("sleep_short", "sleep", Icons.Filled.Bedtime, t("Короткий сон: %s", formatDuration(m)),
                t("Сегодня без тяжёлой тренировки и кофе после 14:00. Лягте на 30–40 минут раньше обычного."), 70)
            val avg = (1..7).mapNotNull { parseNumber(dayOf(date.minusDays(it.toLong()))[item.id]) }.takeIf { it.size >= 4 }?.average()
            sleepAvg = avg
        }
        tmpl.firstOrNull { it.source == Source.HC_RESTING_HR }?.let { item ->
            val r = parseNumber(day[item.id])
            val base = (1..14).mapNotNull { parseNumber(dayOf(date.minusDays(it.toLong()))[item.id]) }.takeIf { it.size >= 5 }?.average()
            if (r != null && base != null && r >= base + 5) out += Tip("sleep_rhr", "sleep", Icons.Filled.Favorite, t("Пульс покоя выше обычного"),
                t("%s против обычных %s — так бывает при недосыпе, стрессе или начале болезни. Снизьте сегодня нагрузку.", n(r), n(base)), 75)
        }

        // ---------- вес ----------
        val pts = (0..20).mapNotNull { i -> Anthro.weightOf(tmpl, dayOf(date.minusDays(i.toLong())))?.let { i to it } }
        if (pts.size >= 4 && pts.maxOf { it.first } - pts.minOf { it.first } >= 7) {
            // наклон по МНК, кг в неделю (i — дни назад, поэтому знак меняем)
            val mx = pts.map { it.first.toDouble() }.average(); val my = pts.map { it.second }.average()
            val k = -pts.sumOf { (it.first - mx) * (it.second - my) } / pts.sumOf { (it.first - mx) * (it.first - mx) } * 7
            val title = t("Вес: %s%s кг в неделю", if (k >= 0) "+" else "−", n1(abs(k)))
            val ww = w ?: 80.0
            if (goal == Goal.LOSE) when {
                k < -ww * 0.012 -> out += Tip("weight_fast", "weight", Icons.Filled.MonitorWeight, title,
                    t("Быстрее 1 % веса в неделю — вместе с жиром уходят мышцы. Добавьте ~200 ккал и держите белок."), 50)
                k <= -ww * 0.0025 -> out += Tip("weight_ok", "weight", Icons.Filled.MonitorWeight, title,
                    t("Хороший темп похудения (0,5–1 % веса в неделю). Так держать."), 25)
                else -> out += Tip("weight_down", "weight", Icons.Filled.MonitorWeight, title,
                    t("Вес не снижается. Проверьте, все ли приёмы записаны, и уберите ~200 ккал в день или добавьте 2–3 тыс. шагов."), 50)
            } else if (goal == null || goal == Goal.MUSCLE || goal == Goal.GAIN) when {
                k > 0.6 -> out += Tip("weight_fast", "weight", Icons.Filled.MonitorWeight, title,
                    t("Быстрее, чем растут мышцы (0,25–0,5 кг в неделю) — часть уйдёт в жир. Уберите ~200 ккал в день."), 50)
                k >= 0.15 -> out += Tip("weight_ok", "weight", Icons.Filled.MonitorWeight, title,
                    t("Хороший темп для набора мышц. Держите белок и тренировки."), 25)
                k < -0.3 -> out += Tip("weight_down", "weight", Icons.Filled.MonitorWeight, title,
                    t("Вес снижается. Если цель — набор массы, добавьте ~300 ккал в день: крупы, хлеб, орехи, молочное."), 45)
            }
        }

        // ---------- программа (5.29): следующий приём по меню ----------
        if (goal != null && goal != Goal.WELLBEING && w != null) runCatching {
            val st = PlanStats.of(repo, prefs, date)
            val tg = Program.targets(goal, st) ?: return@runCatching
            val nowMin = now.hour * 60 + now.minute
            val meals = tmpl.filter { it.type == ItemType.MEALS }.flatMap { parseMeals(day[it.id]) }
            val rr = DayRhythm.of(repo, date, prefs)
            val m = Program.menu(goal, tg, rr, prefs, at = runCatching { DayPlan.of(repo, prefs, date, rr).meals }.getOrNull())
                .firstOrNull { mm -> mm.time != null && mm.time - nowMin in -30..90 && meals.none { it.kind == mm.slot.ru } }
            if (m != null && m.items.isNotEmpty()) out += Tip("plan_meal", "food", Icons.Filled.Restaurant,
                t("%s около %s", m.slot.title, clockOf(m.time!!)),
                m.items.joinToString(", ") { it.food.name.lowercase() + " " + it.amount() } + " — " + t("%s ккал, белок %s г", n(m.kcal), n(m.p)) + ".", 58)
        }

        // ---------- по времени суток (5.22.1): от привычного подъёма и отбоя ----------
        val r = DayRhythm.of(repo, date, prefs, java.time.LocalDateTime.of(date, now))
        val b = r.bedRel
        val rel = ((now.hour * 60 + now.minute) - r.wake + 1440) % 1440
        val night = rel >= b
        when {
            night -> {
                // 5.26: до подъёма — по будильнику, если он стоит, иначе до рекомендуемого
                val left = ((r.nextWake - (now.hour * 60 + now.minute)) % 1440 + 1440) % 1440
                out += Tip("now_sleep", "sleep", Icons.Filled.Bedtime, t("Пора спать"),
                    (if (r.alarm) t("Будильник на %s — до него %s.", clockOf(r.nextWake), formatDuration(left.toDouble()))
                    else t("Для сна до %s осталось %s.", clockOf(r.nextWake), formatDuration(left.toDouble()))) + " " +
                        t("Отложите телефон: яркий экран сдвигает засыпание."), 95)
            }
            rel >= b - 120 -> out += Tip("now_wind", "sleep", Icons.Filled.Bedtime, t("Скоро сон — в %s", clockOf(r.bed)),
                (if (r.alarm) t("Будильник на %s: чтобы выспаться, лечь в %s. ", clockOf(r.nextWake), clockOf(r.bed)) else "") +
                (sleepAvg?.takeIf { it < 420 }?.let { t("За неделю в среднем %s сна. ", formatDuration(it)) } ?: "") +
                    t("Приглушите свет и экраны, тяжёлая еда и тренировка — уже не сейчас."), 60)
            rel >= b - 180 -> out += Tip("now_dinner", "food", Icons.Filled.Restaurant, t("Последняя еда — до %s", clockOf(r.abs(b - 120))),
                t("За 2–3 часа до сна лучше закончить с едой: сахар вечером поднимается сильнее, а сон с полным желудком хуже."), 35)
            rel < 60 -> out += Tip("now_light", "sleep", Icons.Filled.Lightbulb, t("Утро: свет"),
                t("10–15 минут дневного света или яркая лампа после подъёма — проще проснуться и вовремя уснуть вечером."), 30)
        }
        // ---------- режим дня (5.26) ----------
        val dow = date.dayOfWeek
        val usualBedGap = ((r.usualBed - r.bed + 720 + 1440) % 1440) - 720   // насколько привычный отбой позже рекомендуемого
        if (!night && rel >= b - 240 && !r.alarm && !r.manual && !DayRhythm.weekend(date.plusDays(1)))
            out += Tip("regime_alarm", "sleep", Icons.Filled.Alarm, t("Будильник на завтра не стоит"),
                t("Рекомендую подъём в %s и отбой в %s — так будет %s сна. Будильник можно поставить в «Ритме дня».",
                    clockOf(r.nextWake), clockOf(r.bed), formatDuration((r.need - 15).toDouble())), 40)
        if (!night && rel >= b - 240 && usualBedGap >= 60)
            out += Tip("regime_late", "sleep", Icons.Filled.Bedtime, t("Обычно ложитесь позже, чем нужно"),
                t("Привычный отбой — %s, а чтобы выспаться к %s, нужно лечь в %s. Сдвигайте отбой на 15–20 минут раньше каждые пару дней.",
                    clockOf(r.usualBed), clockOf(r.nextWake), clockOf(r.bed)), 45)
        r.jetlag?.takeIf { it >= 60 && (dow == java.time.DayOfWeek.FRIDAY || dow == java.time.DayOfWeek.SATURDAY || dow == java.time.DayOfWeek.SUNDAY || dow == java.time.DayOfWeek.MONDAY) }?.let { j ->
            out += Tip("regime_weekend", "sleep", Icons.Filled.Bedtime, t("Выходные сдвигают режим на %s", formatDuration(j.toDouble())),
                listOfNotNull(r.wakeWeekday?.let { t("В будни подъём около %s", clockOf(it)) }, r.wakeWeekend?.let { t("в выходные — около %s", clockOf(it)) }).joinToString(", ") +
                    ". " + t("Это как перелёт через часовые пояса каждую неделю — в понедельник тяжело встать. Старайтесь не сдвигать подъём больше чем на час."), 45)
        }
        r.bedSpread?.takeIf { it >= 60 }?.let { sp ->
            out += Tip("regime_spread", "sleep", Icons.Filled.Bedtime, t("Отбой «гуляет»"),
                t("За неделю время отбоя менялось в среднем на ±%s. Регулярность почти так же важна, как длительность: ложитесь в одно время ±30 минут.",
                    formatDuration(sp.toDouble())), 35)
        }
        if (!night && rel in (b - 480)..(b - 180)) out += Tip("now_coffee", "sleep", Icons.Filled.LocalCafe, t("Кофе на сегодня всё"),
            t("После %s кофеин успеет повлиять на сон: выводится 5–6 часов. Лучше вода, травяной или некрепкий чай без кофеина.", clockOf(r.abs(b - 480))), 20)
        val peak = r.lanes.first { it.key == "peak" }
        if (exItem != null && rel in peak.from..peak.to) {
            val today = Workouts.sessions(day, exItem.id).any { it.kind == "strength" }
            val last = (1..21).firstOrNull { i -> Workouts.sessions(dayOf(date.minusDays(i.toLong())), exItem.id).any { it.kind == "strength" } }
            if (!today && (last == null || last >= 2) && out.none { it.id == "train_rest" })
                out += Tip("now_train", "train", Icons.Filled.FitnessCenter, t("Сейчас пик сил — до %s", clockOf(r.abs(peak.to))),
                    t("Хорошее время для силовой: тело разогрето, сила и реакция на дневном максимуме."), 55)
        }

        val off = prefs.tipsOff
        val hidden = prefs.tipsHidden.filter { it.startsWith("$date|") }.map { it.substringAfter('|') }.toSet()
        // ночью важен только сон: остальное подождёт до утра
        return out.filter { it.cat !in off && it.id !in hidden && (!night || it.cat == "sleep") }
            .distinctBy { it.id }.sortedByDescending { it.prio }
    }


    /** Общие советы (5.22): крутятся в плитке, когда срочных мало. Источники — обзоры ВОЗ, ACSM, ISSN, AASM. */
    fun general(prefs: Prefs, date: LocalDate, r: DayRhythm? = null, now: LocalTime? = null): List<Tip> {
        val off = prefs.tipsOff
        val hidden = prefs.tipsHidden.filter { it.startsWith("$date|") }.map { it.substringAfter('|') }.toSet()
        val all = listOf(
            Tip("g_food1", "food", Icons.Filled.Restaurant, t("Белок в каждый приём"), t("20–40 г белка за раз (ладонь мяса, рыбы или творога) лучше запускает рост мышц, чем вся норма за ужином."), 0),
            Tip("g_food2", "food", Icons.Filled.Restaurant, t("Половина тарелки — овощи"), t("Клетчатка замедляет подъём сахара и дольше держит сытость. Цель — 25–35 г клетчатки в день."), 0),
            Tip("g_food3", "food", Icons.Filled.Restaurant, t("Сначала овощи и белок"), t("Если начать приём пищи с салата и белка, а крупу или хлеб съесть в конце, сахар после еды поднимается заметно меньше."), 0),
            Tip("g_food4", "food", Icons.Filled.Restaurant, t("Фрукт целиком, а не сок"), t("В соке нет клетчатки: стакан апельсинового сока — это 4–5 чайных ложек сахара, которые всасываются почти сразу."), 0),
            Tip("g_food5", "food", Icons.Filled.Restaurant, t("Цельные крупы"), t("Гречка, овсянка-хлопья долгой варки, бурый рис и цельнозерновой хлеб дают меньше скачков сахара, чем белый рис и батон."), 0),
            Tip("g_food6", "food", Icons.Filled.Restaurant, t("Остывший рис и картофель"), t("Охлаждение после варки превращает часть крахмала в резистентный — ГИ становится немного ниже, даже если потом разогреть."), 0),
            Tip("g_food7", "food", Icons.Filled.Restaurant, t("Взвешивайте пару дней"), t("Глазомер ошибается на 20–40 %. Пара дней с кухонными весами хорошо калибрует «на глаз» на месяцы вперёд."), 0),
            Tip("g_water1", "water", Icons.Filled.WaterDrop, t("Пить по жажде"), t("Ориентир — светло-жёлтая моча. Чай, кофе, суп и фрукты тоже дают воду; в жару и после тренировки нужно больше."), 0),
            Tip("g_water2", "water", Icons.Filled.WaterDrop, t("Стакан воды к каждой еде"), t("Простая привычка: к завтраку, обеду и ужину по стакану — и половина дневной нормы уже есть."), 0),
            Tip("g_move1", "move", Icons.AutoMirrored.Filled.DirectionsWalk, t("Прогулка после еды"), t("10–15 минут ходьбы после еды заметно снижают пик сахара: мышцы забирают глюкозу без инсулина."), 0),
            Tip("g_move2", "move", Icons.AutoMirrored.Filled.DirectionsWalk, t("Вставать каждые 30–60 минут"), t("Короткие перерывы в сидении (пара минут ходьбы) улучшают сахар и давление, даже если шагов за день столько же."), 0),
            Tip("g_move3", "move", Icons.AutoMirrored.Filled.DirectionsWalk, t("7–10 тысяч шагов"), t("Польза от шагов растёт примерно до 7–10 тысяч в день, дальше прирост меньше. Темп тоже важен: часть шагов — быстрым шагом."), 0),
            Tip("g_train1", "train", Icons.Filled.FitnessCenter, t("Прогрессия нагрузки"), t("Мышцы растут, когда нагрузка понемногу растёт: +1 повтор или +1–2 кг, пока техника держится."), 0),
            Tip("g_train2", "train", Icons.Filled.FitnessCenter, t("До 1–3 повторов до отказа"), t("Для роста мышц подходы стоит заканчивать, когда в запасе 1–3 повтора. Вес может быть и лёгким — тогда повторов больше."), 0),
            Tip("g_train3", "train", Icons.Filled.FitnessCenter, t("10–20 подходов в неделю"), t("Примерный объём на группу мышц для роста. Лучше разбить на 2–3 тренировки, чем делать всё за раз."), 0),
            Tip("g_train4", "train", Icons.Filled.FitnessCenter, t("Отдых между подходами"), t("Для базовых упражнений 2–3 минуты отдыха дают больше прогресса, чем 1 минута."), 0),
            Tip("g_sleep1", "sleep", Icons.Filled.Bedtime, t("Один и тот же подъём"), t("Вставайте в одно время и в выходные: регулярный подъём сильнее всего держит внутренние часы."), 0),
            Tip("g_sleep2", "sleep", Icons.Filled.Bedtime, t("Прохладная тёмная спальня"), t("Лучше всего спится при 17–20 °C и в темноте. Яркий свет и экраны за час до сна сдвигают засыпание."), 0),
            Tip("g_sleep3", "sleep", Icons.Filled.Bedtime, t("Кофе — до обеда"), t("Кофеин выводится 5–6 часов, и даже чашка за 6 часов до сна ухудшает его глубину."), 0),
            Tip("g_sleep4", "sleep", Icons.Filled.Bedtime, t("Алкоголь и сон"), t("Алкоголь помогает уснуть, но разрушает вторую половину ночи: меньше REM-сна, больше пробуждений."), 0),
            Tip("g_weight1", "weight", Icons.Filled.MonitorWeight, t("Смотрите на тренд"), t("Вес за день колеблется на 1–2 кг из-за воды и соли. Важна средняя за неделю, а не отдельное утро."), 0),
            Tip("g_weight2", "weight", Icons.Filled.MonitorWeight, t("Взвешивание в одних условиях"), t("Утром, после туалета, до еды и питья — так цифры между днями сравнимы."), 0),
            Tip("g_weight3", "weight", Icons.Filled.MonitorWeight, t("Набор без лишнего жира"), t("Для набора мышц хватит +0,25–0,5 % веса в месяц у новичка до ~1 %. Быстрее — в основном жир."), 0),
        )
        val goal = Goal.of(prefs.planGoal)
        val byGoal = when (goal) {
            Goal.LOSE -> listOf(
                Tip("g_lose1", "food", Icons.Filled.Restaurant, t("Не пейте калории"), t("Сок, сладкий кофе, газировка и алкоголь почти не насыщают — 300–500 ккал в день уходят незаметно."), 0),
                Tip("g_lose2", "food", Icons.Filled.Restaurant, t("Белок и овощи — первыми"), t("Начинайте приём с белка и овощей: сытость наступает раньше, порция гарнира сама становится меньше."), 0),
                Tip("g_lose3", "weight", Icons.Filled.MonitorWeight, t("Плато — это нормально"), t("Вес может стоять 1–2 недели из-за воды. Смотрите на среднее за 2–3 недели и объёмы, прежде чем урезать калории."), 0),
            )
            Goal.MUSCLE, Goal.GAIN -> listOf(
                Tip("g_gain1", "food", Icons.Filled.Restaurant, t("Калории без объёма"), t("Если не лезет: орехи, ореховая паста, масло в кашу, молоко вместо воды — +300–500 ккал без лишнего объёма еды."), 0),
            )
            Goal.WELLBEING -> listOf(
                Tip("g_wb1", "sleep", Icons.Filled.Bedtime, t("Свет утром"), t("10–20 минут дневного света в первый час после подъёма помогают уснуть вечером и держат настроение."), 0),
            )
            else -> emptyList()
        }
        return (all.filter { goal == null || it.id != "g_weight3" || goal == Goal.MUSCLE || goal == Goal.GAIN } + byGoal)
            .filter { it.cat !in off && it.id !in hidden && (r == null || now == null || fits(it, r, now)) }
            .shuffled(kotlin.random.Random(date.toEpochDay()))
    }

    /** Общий совет к месту по времени: еда — днём, тренировки — ближе к пику сил, сон — вечером, вес — утром, ночью — ничего. */
    fun fits(tip: Tip, r: DayRhythm, now: LocalTime): Boolean {
        val rel = ((now.hour * 60 + now.minute) - r.wake + 1440) % 1440
        val b = r.bedRel
        if (rel >= b) return false
        val peak = r.lanes.first { it.key == "peak" }
        return when (tip.cat) {
            "food" -> rel in 30..(b - 180)
            "water" -> rel < b - 120
            "move" -> rel in 60..(b - 120)
            "train" -> rel in (peak.from - 180)..peak.to
            "sleep" -> rel >= b - 240 || (tip.id == "g_sleep1" && rel < 90)
            "weight" -> rel < 240
            else -> true
        }
    }

    /** Очередь для смены советов: сначала срочные, затем общие. */
    fun rotation(prefs: Prefs, date: LocalDate, urgent: List<Tip>, now: LocalTime = LocalTime.now(), r: DayRhythm? = null): List<Tip> =
        urgent + general(prefs, date, r, now)

    /** Важные — то, что стоит показать в плитке в первую очередь. */
    const val IMPORTANT = 40

    /** Варианты интервала смены совета, секунды; 0 — не менять сам. */
    val everyOptions = listOf(0, 30, 60, 300, 900, 1800, 3600)
    fun everyName(sec: Int) = when {
        sec == 0 -> t("не менять (только по нажатию)")
        sec < 60 -> t("каждые %d с", sec)
        sec < 3600 -> t("каждые %d мин", sec / 60)
        else -> t("каждый час")
    }

    /** Когда советов нет. */
    fun allGood() = Tip("ok", "", Icons.Filled.ThumbUp, t("Всё по плану"), t("Сегодня ничего не просит внимания. Так держать!"), 0)

    fun hide(prefs: Prefs, date: LocalDate, id: String) {
        // храним только сегодняшние скрытия
        prefs.tipsHidden = prefs.tipsHidden.filter { it.startsWith("$date|") }.toSet() + "$date|$id"
    }
}

internal fun formatDuration(min: Double): String {
    val m = min.roundToInt()
    return t("%d ч %02d мин", m / 60, m % 60)
}

/** Полная плитка «Советы дня»: список советов, скрытие на сегодня, выбор категорий. */
@Composable
fun TipsBlock(repo: Repo, prefs: Prefs, item: TemplateItem, date: LocalDate, onMore: (() -> Unit)? = null) {
    var ver by remember { mutableIntStateOf(0) }
    var settings by remember { mutableStateOf(false) }
    var every by remember { mutableIntStateOf(prefs.tipsEvery) }
    collect(repo.entries); collect(repo.profile)
    // пересчёт раз в 5 минут: часть советов зависит от времени суток
    val slow = rememberTick(300_000)
    val ctx0 = androidx.compose.ui.platform.LocalContext.current
    remember(slow) { Alarms.refresh(ctx0) }
    val tips = remember(ver, slow, repo.entries.value, repo.profile.value, date) { Tips.build(repo, prefs, date) }
    SectionCard(item.displayName, action = {
        IconButton({ settings = true }, Modifier.size(32.dp)) { Icon(Icons.Filled.Settings, t("Настроить советы"), Modifier.size(20.dp)) }
    }) {
        if (date != LocalDate.now()) {
            Text(t("Советы — для сегодняшнего дня."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@SectionCard
        }
        val r = remember(repo.entries.value, date, ver, slow) { DayRhythm.of(repo, date, prefs) }
        // 5.43: если ритм есть отдельной плиткой или полосой — в советах не повторяем
        val rhythmElsewhere = prefs.rhythmStrip || repo.template.value.any { it.type == ItemType.RHYTHM && !it.hidden }
        if ("rhythm" !in prefs.tipsOff && !rhythmElsewhere) {
            // 5.30: ритм дня — одной строкой, график по нажатию
            var showR by remember { mutableStateOf(false) }
            val peak = r.lanes.firstOrNull { it.key == "peak" }
            Row(Modifier.fillMaxWidth().clickable { showR = !showR }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Schedule, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(listOfNotNull(
                    t("подъём %s", clockOf(r.wake)),
                    peak?.let { t("пик сил %s–%s", clockOf(r.abs(it.from)), clockOf(r.abs(it.to))) },
                    t("отбой %s", clockOf(r.bed)),
                ).joinToString(" · "), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Icon(if (showR) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, t("Ритм дня"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.animation.AnimatedVisibility(showR,
                enter = androidx.compose.animation.expandVertically(expandFrom = Alignment.Top) + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.shrinkVertically(shrinkTowards = Alignment.Top) + androidx.compose.animation.fadeOut()) { RhythmChart(r, LocalTime.now(), onMore = onMore) }
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
        }
        val tick = rememberTick(every * 1000L)
        var shift by remember { mutableIntStateOf(0) }
        // 5.30: два главных совета; остальные — по очереди одной строкой
        val shown = tips.take(2)
        val rot = remember(ver, slow, tips, date) { Tips.rotation(prefs, date, tips.drop(2), LocalTime.now(), r) }
        @Composable
        fun TipRow(tip: Tip, mod: Modifier = Modifier) = Row(mod, verticalAlignment = Alignment.Top) {
            Icon(tip.icon, null, Modifier.padding(top = 2.dp).size(20.dp),
                tint = if (tip.prio >= 70) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(tip.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(tip.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (tip.id != "ok") IconButton({ Tips.hide(prefs, date, tip.id); ver++ }, Modifier.size(28.dp)) {
                Icon(Icons.Filled.Close, t("Скрыть до завтра"), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (shown.isEmpty() && rot.isEmpty()) TipRow(Tips.allGood())
            shown.forEachIndexed { i, tip ->
                if (i > 0) HorizontalDivider()
                TipRow(tip)
            }
            if (rot.isNotEmpty()) {
                if (shown.isNotEmpty()) HorizontalDivider()
                val k = (tick + shift) % rot.size
                androidx.compose.animation.AnimatedContent(rot[k], label = "tip", transitionSpec = {
                    (androidx.compose.animation.slideInHorizontally { it / 3 } + androidx.compose.animation.fadeIn()) togetherWith
                        (androidx.compose.animation.slideOutHorizontally { -it / 3 } + androidx.compose.animation.fadeOut())
                }) { tip ->
                    TipRow(tip, Modifier.fillMaxWidth().clickable { shift++ })
                }
                Text(t("совет %d из %d · нажмите — следующий", k + 1, rot.size) + " · " + Tips.everyName(every),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (settings) {
        var off by remember { mutableStateOf(prefs.tipsOff) }
        AlertDialog(
            onDismissRequest = { settings = false },
            title = { Text(t("Советы дня")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(t("О чём подсказывать:"), style = MaterialTheme.typography.bodyMedium)
                    Tips.cats.forEach { (k, name) ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(k !in off, { on -> off = if (on) off - k else off + k; prefs.tipsOff = off; ver++ })
                            Text(t(name))
                        }
                    }
                    val idx = Tips.everyOptions.indexOf(every).coerceAtLeast(0)
                    Text(t("Смена совета: %s", Tips.everyName(every)), style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp))
                    androidx.compose.material3.Slider(idx.toFloat(), { v ->
                        every = Tips.everyOptions[Math.round(v).coerceIn(0, Tips.everyOptions.size - 1)]; prefs.tipsEvery = every
                    }, valueRange = 0f..(Tips.everyOptions.size - 1).toFloat(), steps = Tips.everyOptions.size - 2)
                    RhythmTimes(repo, prefs) { ver++ }
                    Text(t("Сверху всегда самые важные советы на текущее время: ночью — только про сон, вечером — про отбой, в окне пика сил — про тренировку. Общие советы подбираются ко времени суток и сменяются с этим интервалом."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(t("Советы считаются по вашим записям, профилю и данным часов. Крестик скрывает совет до завтра. Размер и место плитки меняются кнопкой «Плитки», скрыть её можно в «Все пункты»."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                    if (prefs.tipsHidden.any { it.startsWith("${LocalDate.now()}|") }) TextButton({ prefs.tipsHidden = emptySet(); ver++ }) {
                        Text(t("Вернуть скрытые сегодня"))
                    }
                }
            },
            confirmButton = { TextButton({ settings = false }) { Text(t("Готово")) } },
        )
    }
}


/** Счётчик, растущий раз в [periodMs] — для смены советов. В тестах (Robolectric) стоит на месте. */
@Composable
fun rememberTick(periodMs: Long): Int {
    var n by remember { mutableIntStateOf(0) }
    if (periodMs > 0 && android.os.Build.FINGERPRINT != "robolectric") androidx.compose.runtime.LaunchedEffect(periodMs) {
        while (true) { kotlinx.coroutines.delay(periodMs); n++ }
    }
    return n
}


/** Подъём и отбой для советов и «Ритма дня»: по данным сна или вручную (5.22.2). */
@Composable
internal fun RhythmTimes(repo: Repo, prefs: Prefs, onChange: () -> Unit) {
    var manual by remember { mutableStateOf(prefs.rhythmWake >= 0 || prefs.rhythmBed >= 0) }
    val auto = remember(manual) { DayRhythm.of(repo, LocalDate.now()) }
    var wake by remember { mutableIntStateOf(prefs.rhythmWake.takeIf { it >= 0 } ?: auto.usualWake) }
    var bed by remember { mutableIntStateOf(prefs.rhythmBed.takeIf { it >= 0 } ?: auto.bed) }
    fun save() { if (manual) { prefs.rhythmWake = wake; prefs.rhythmBed = bed } else { prefs.rhythmWake = -1; prefs.rhythmBed = -1 }; onChange() }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(t("Подъём и отбой"), style = MaterialTheme.typography.bodyMedium)
            Text(if (manual) t("заданы вручную") else if (auto.nights > 0) t("средние за %s", nightsText(auto.nights)) else t("данных сна нет"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(t("вручную"), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.width(6.dp))
        androidx.compose.material3.Switch(manual, { manual = it; save() })
    }
    @Composable
    fun Stepper(label: String, v: Int, set: (Int) -> Unit) = Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        IconButton({ set((v - 15 + 1440) % 1440) }, enabled = manual) { Text("−15", style = MaterialTheme.typography.labelMedium) }
        Text(clockOf(v), style = MaterialTheme.typography.titleMedium, color = if (manual) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        IconButton({ set((v + 15) % 1440) }, enabled = manual) { Text("+15", style = MaterialTheme.typography.labelMedium) }
    }
    Stepper(t("Подъём"), if (manual) wake else auto.usualWake) { wake = it; save() }
    Stepper(t("Отбой"), if (manual) bed else auto.bed) { bed = it; save() }
}
