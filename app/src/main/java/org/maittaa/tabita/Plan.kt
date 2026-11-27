package org.maittaa.tabita

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Долгосрочный план (5.28): цель, рекомендация цели по данным дневника, что делать и вехи.
 * Ориентиры: темп похудения 0,5–1 % веса в неделю; белок 1,6–2,2 г/кг (Morton и др., 2018);
 * силовые ≥ 10 подходов на группу в неделю (Schoenfeld и др., 2017); сон 7–9 ч (AASM);
 * 150–300 мин умеренной активности в неделю (ВОЗ, 2020); 7–10 тыс. шагов (Paluch и др., 2022);
 * клинически заметное снижение веса — от 5 % (рекомендации по ожирению).
 */
enum class Goal(val id: String, val ru: String, val about: String) {
    LOSE("lose", "Похудение", "Снизить жир, сохранив мышцы"),
    DEFICITS("deficits", "Закрыть дефициты", "Добрать белок, воду и калории до нормы"),
    GAIN("gain", "Набор массы", "Набрать вес до нормы"),
    MUSCLE("muscle", "Набор мышц", "Растить мышцы с минимумом жира"),
    WELLBEING("wellbeing", "Самочувствие", "Сон, движение, режим и настроение");

    val title get() = t(ru)
    companion object { fun of(id: String?) = entries.firstOrNull { it.id == id } }
}

/** Сводка данных для плана: средние за 7/14/28 дней. */
data class PlanStats(
    val weight: Double?, val h: Double?, val bmi: Double?, val lean: Double?, val fatPct: Double?,
    val proteinKg: Double?, val waterRatio: Double?, val eatenRatio: Double?, val sleepH: Double?, val steps: Double?,
    val strengthWeek: Double, val activeMinWeek: Double, val wellbeing: Double?, val burn: Double?,
    val weekProtein: Int, val weekWater: Int, val weekSleep: Int, val weekSteps: Int, val weekDays: Int,
) {
    companion object {
        fun of(repo: Repo, prefs: Prefs, today: LocalDate = LocalDate.now()): PlanStats {
            val tmpl = repo.template.value; val ent = repo.entries.value; val profile = repo.profile.value
            fun day(i: Int) = ent[today.minusDays(i.toLong()).toString()] ?: emptyMap()
            val w7 = (0 until 7).mapNotNull { Anthro.weightOf(tmpl, day(it)) }.takeIf { it.isNotEmpty() }?.average()
                ?: Anthro.weightOn(tmpl, ent, today)
            val h = profile.h
            val lean = (0 until 14).firstNotNullOfOrNull { Anthro.leanOf(tmpl, day(it)) }
            val fat = (0 until 14).firstNotNullOfOrNull { Anthro.bodyMetric(tmpl, day(it), "body_fat") }?.takeIf { it in 2.0..70.0 }
            val sleepItem = tmpl.firstOrNull { it.source == Source.HC_SLEEP }
            val waterItem = tmpl.firstOrNull { it.type == ItemType.WATER }
            val meals = tmpl.filter { it.type == ItemType.MEALS }
            fun protein(d: Map<String, String>) = meals.flatMap { parseMeals(d[it.id]) }.let { mealMacros(it)?.protein }
            fun eaten(d: Map<String, String>) = Anthro.eatenOf(tmpl, d)
            fun water(d: Map<String, String>, i: Int): Double? {
                val v = waterItem?.let { parseNumber(d[it.id]) } ?: return null
                val norm = Hydration.normOf(waterItem, d)
                return v / norm
            }
            fun sleep(d: Map<String, String>) = sleepItem?.let { parseNumber(d[it.id]) }?.takeIf { it > 60 }
            val pk = (0 until 14).mapNotNull { i -> protein(day(i))?.let { p -> w7?.let { p / it } } }.takeIf { it.size >= 3 }?.average()
            val wr = (0 until 14).mapNotNull { water(day(it), it) }.takeIf { it.size >= 3 }?.average()
            // 5.56: + поправка по реальному тренду веса (Adapt), если её приняли
            val burn = (Anthro.energyOn(profile, tmpl, ent, today.minusDays(1)) ?: Anthro.energyOn(profile, tmpl, ent, today))?.total?.plus(prefs.burnAdj)
            val er = (1 until 15).mapNotNull { i -> eaten(day(i))?.takeIf { it > 300 }?.let { e ->
                Anthro.energyOn(profile, tmpl, ent, today.minusDays(i.toLong()))?.total?.let { e / it } } }.takeIf { it.size >= 3 }?.average()
            val sl = (0 until 14).mapNotNull { sleep(day(it)) }.takeIf { it.size >= 3 }?.average()?.div(60)
            val st = (1 until 15).mapNotNull { Anthro.stepsOf(tmpl, day(it)) }.takeIf { it.size >= 3 }?.average()
            val sess = (0 until 28).flatMap { Anthro.sessionsOf(tmpl, day(it)) }
            val strength = sess.count { it.kind in setOf("strength", "hiit", "martial") } / 4.0
            val active = sess.sumOf { it.min * (if (Workouts.load(it.kind) > 0) 1 else 0) } / 4.0
            val wb = repo.checkups.value.maxByOrNull { it.at }?.takeIf { ChronoUnit.DAYS.between(LocalDate.parse(it.date), today) <= 45 }?.overall
            val pLow = (w7 ?: 0.0) * 1.6
            return PlanStats(
                w7, h, if (w7 != null && h != null) Anthro.bmi(w7, h) else null, lean, fat, pk, wr, er, sl, st, strength, active, wb, burn,
                weekProtein = (0 until 7).count { i -> (protein(day(i)) ?: 0.0) >= pLow && pLow > 0 },
                weekWater = (0 until 7).count { i -> (water(day(i), i) ?: 0.0) >= 0.9 },
                weekSleep = (0 until 7).count { i -> (sleep(day(i)) ?: 0.0) >= 420 },
                weekSteps = (0 until 7).count { i -> (Anthro.stepsOf(tmpl, day(i)) ?: 0.0) >= 7000 },
                weekDays = 7,
            )
        }
    }
}

object Plan {
    data class Pick(val goal: Goal, val score: Double, val reasons: List<String>)

    private fun n1(v: Double) = formatNumber(Math.round(v * 10) / 10.0)

    /** Рекомендуемая цель: у какой цели больше причин в данных. */
    fun recommend(s: PlanStats): Pick {
        val r = mutableMapOf<Goal, Pair<Double, MutableList<String>>>()
        fun add(g: Goal, w: Double, why: String) { val p = r[g] ?: (0.0 to mutableListOf()); p.second += why; r[g] = (p.first + w) to p.second }
        s.bmi?.let { b ->
            when {
                b >= 30 -> add(Goal.LOSE, 4.0, t("ИМТ %s — ожирение (≥ 30)", n1(b)))
                b >= 27 -> add(Goal.LOSE, 3.0, t("ИМТ %s — заметно выше нормы (18,5–25)", n1(b)))
                b >= 25 -> add(Goal.LOSE, if ((s.fatPct ?: 0.0) >= 25) 2.0 else 1.0, t("ИМТ %s — немного выше нормы", n1(b)))
                b < 18.5 -> add(Goal.GAIN, 4.0, t("ИМТ %s — ниже нормы (18,5)", n1(b)))
                else -> add(Goal.MUSCLE, 1.0, t("ИМТ %s — в норме", n1(b)))
            }
        }
        s.fatPct?.let { if (it >= 25) add(Goal.LOSE, 1.0, t("жир по весам %s%%", n1(it))) }
        if (s.strengthWeek >= 1.5) add(Goal.MUSCLE, 1.5, t("силовых ≈ %s в неделю", n1(s.strengthWeek)))
        s.proteinKg?.let { if (it < 1.2) add(Goal.DEFICITS, 1.5, t("белка %s г на кг — меньше 1,2", n1(it))) }
        s.waterRatio?.let { if (it < 0.8) add(Goal.DEFICITS, 1.0, t("воды %d%% от нормы", Math.round(it * 100).toInt())) }
        s.eatenRatio?.let { if (it < 0.7 && (s.bmi ?: 22.0) < 27) add(Goal.DEFICITS, 1.5, t("съедаете %d%% от расхода", Math.round(it * 100).toInt())) }
        s.sleepH?.let { if (it < 6.5) add(Goal.WELLBEING, 1.5, t("сон в среднем %s ч", n1(it))) }
        s.wellbeing?.let { if (it < 50) add(Goal.WELLBEING, 2.0, t("чекап самочувствия — %d из 100", Math.round(it).toInt())) }
        s.steps?.let { if (it < 5000) add(Goal.WELLBEING, 1.0, t("шагов в среднем %s", formatNumber(Math.round(it).toDouble()))) }
        val best = r.maxByOrNull { it.value.first }
            ?: return Pick(Goal.WELLBEING, 0.0, listOf(t("данных пока мало — начните с режима, сна и движения")))
        return Pick(best.key, best.value.first, best.value.second)
    }

    /** Совет: что делать, значение-ориентир и (необязательно) ваше текущее. */
    data class Step(val what: String, val target: String, val now: String? = null, val ok: Boolean? = null)

    fun steps(g: Goal, s: PlanStats): List<Step> {
        val w = s.weight
        fun kg(lo: Double, hi: Double) = w?.let { t("%s–%s г", formatNumber(Math.round(it * lo).toDouble()), formatNumber(Math.round(it * hi).toDouble())) }
            ?: t("%s–%s г на кг", n1(lo), n1(hi))
        val pNow = s.proteinKg?.let { t("сейчас %s г/кг", n1(it)) }
        fun kcal(lo: Double, hi: Double) = s.burn?.let { t("%s–%s ккал", formatNumber(Math.round(it * lo / 10) * 10.0), formatNumber(Math.round(it * hi / 10) * 10.0)) }
            ?: t("укажите профиль — посчитаю калории")
        val eatNow = s.eatenRatio?.let { e -> s.burn?.let { b -> t("сейчас ≈ %s ккал", formatNumber(Math.round(e * b / 10) * 10.0)) } }
        val sleep = Step(t("Сон"), t("7–9 ч"), s.sleepH?.let { t("сейчас %s ч", n1(it)) }, s.sleepH?.let { it >= 7 })
        val walk = Step(t("Шаги"), t("7–10 тыс. в день"), s.steps?.let { t("сейчас %s", formatNumber(Math.round(it / 100) * 100.0)) }, s.steps?.let { it >= 7000 })
        val protein = Step(t("Белок"), kg(1.6, 2.2), pNow, s.proteinKg?.let { it >= 1.6 })
        val strength = Step(t("Силовые"), t("3 раза в неделю, ≥ 10 подходов на группу"), t("сейчас ≈ %s в неделю", n1(s.strengthWeek)), s.strengthWeek >= 2.5)
        return when (g) {
            Goal.LOSE -> listOf(
                Step(t("Калории"), kcal(0.8, 0.85) + " · " + t("дефицит 15–20 %"), eatNow, s.eatenRatio?.let { it in 0.75..0.9 }),
                Step(t("Темп"), t("−0,5…1 % веса в неделю") + (w?.let { " · " + t("%s–%s кг", n1(it * 0.005), n1(it * 0.01)) } ?: "")),
                protein, Step(t("Силовые"), t("2–3 раза в неделю — чтобы уходил жир, а не мышцы"), t("сейчас ≈ %s в неделю", n1(s.strengthWeek)), s.strengthWeek >= 2),
                walk, sleep,
            )
            Goal.GAIN -> listOf(
                Step(t("Калории"), kcal(1.1, 1.15) + " · " + t("профицит 10–15 %"), eatNow, s.eatenRatio?.let { it >= 1.05 }),
                Step(t("Темп"), t("+0,25…0,5 % веса в неделю")),
                protein, strength, sleep,
            )
            Goal.MUSCLE -> listOf(
                Step(t("Калории"), kcal(1.05, 1.1) + " · " + t("профицит 5–10 %"), eatNow, s.eatenRatio?.let { it in 1.0..1.15 }),
                protein, strength,
                Step(t("Прогрессия"), t("каждые 1–2 недели — +1 повтор или +вес")),
                sleep,
                Step(t("Темп"), t("мышцы — до ≈ 0,5–1 кг в месяц у новичков; быстрее растёт уже жир")),
            )
            Goal.DEFICITS -> listOf(
                protein,
                Step(t("Вода"), t("≈ 30 мл на кг + 0,5 л на час тренировки"), s.waterRatio?.let { t("сейчас %d%% нормы", Math.round(it * 100).toInt()) }, s.waterRatio?.let { it >= 0.9 }),
                Step(t("Калории"), kcal(0.9, 1.05) + " · " + t("не ниже 90 % расхода"), eatNow, s.eatenRatio?.let { it >= 0.85 }),
                Step(t("Еда"), t("3–4 приёма, в каждом белок и овощи")),
                sleep,
            )
            Goal.WELLBEING -> listOf(
                sleep,
                Step(t("Режим"), t("отбой и подъём ± 30 мин каждый день, в выходные тоже")),
                walk,
                Step(t("Активность"), t("150–300 мин умеренной в неделю"), t("сейчас ≈ %s мин", formatNumber(Math.round(s.activeMinWeek).toDouble())), s.activeMinWeek >= 150),
                Step(t("Чекап самочувствия"), t("раз в 2 недели"), s.wellbeing?.let { t("последний — %d из 100", Math.round(it).toInt()) }),
            )
        }
    }

    /** Веха: название, достигнута ли, доля пути 0..1. */
    data class Milestone(val name: String, val done: Boolean, val frac: Float, val note: String? = null)

    /** 5.56: целевые веса вех (похудение и набор): название → кг; пусто для других целей. */
    fun weightTargets(g: Goal, w0: Double, h: Double?): List<Pair<String, Double>> {
        val (pcts, sign, last) = when (g) {
            Goal.LOSE -> Triple(listOf(2.0, 5.0, 10.0), -1, h?.let { t("Нормальный ИМТ — %s кг", n1(25 * it * it)) to 25 * it * it })
            Goal.GAIN -> Triple(listOf(2.0, 5.0), 1, h?.let { t("Нормальный ИМТ — %s кг", n1(18.5 * it * it)) to 18.5 * it * it })
            else -> return emptyList()
        }
        return pcts.map { p -> val tw = w0 * (1 + sign * p / 100); t("%s%s %% — %s кг", if (sign < 0) "−" else "+", n1(p), n1(tw)) to tw } +
            listOfNotNull(last?.takeIf { (it.second - w0) * sign > w0 * 0.01 })
    }

    fun milestones(g: Goal, s: PlanStats, prefs: Prefs, today: LocalDate = LocalDate.now()): List<Milestone> {
        val w0 = prefs.planW0.takeIf { it > 0 }; val w = s.weight
        val start = runCatching { LocalDate.parse(prefs.planStart) }.getOrNull()
        val weeks = start?.let { ChronoUnit.DAYS.between(it, today) / 7.0 }
        fun weightMs(sign: Int): List<Milestone> {
            if (w0 == null || w == null) return listOf(Milestone(t("Нужен вес — взвесьтесь, и вехи появятся"), false, 0f))
            val list = weightTargets(g, w0, s.h)
            return list.map { (name, tw) ->
                val f = ((w - w0) / (tw - w0)).toFloat().coerceIn(0f, 1f)
                Milestone(name, (w - tw) * sign >= 0, f)
            }
        }
        val h = s.h
        return when (g) {
            Goal.LOSE -> weightMs(-1)
            Goal.GAIN -> weightMs(1)
            Goal.MUSCLE -> {
                val l0 = prefs.planLean0.takeIf { it > 0 }; val l = s.lean
                if (l0 == null || l == null) listOf(
                    Milestone(t("Силовые 3 раза в неделю 4 недели подряд"), s.strengthWeek >= 2.5, (s.strengthWeek / 3).toFloat().coerceIn(0f, 1f)),
                    Milestone(t("Белок ≥ 1,6 г/кг"), (s.proteinKg ?: 0.0) >= 1.6, ((s.proteinKg ?: 0.0) / 1.6).toFloat().coerceIn(0f, 1f)),
                    Milestone(t("Безжировая масса — появится со взвешиваниями на весах с составом тела"), false, 0f),
                ) else listOf(1.0, 2.0, 4.0).map { d ->
                    Milestone(t("+%s кг безжировой массы", n1(d)), l - l0 >= d, ((l - l0) / d).toFloat().coerceIn(0f, 1f))
                } + Milestone(t("Силовые 3 раза в неделю"), s.strengthWeek >= 2.5, (s.strengthWeek / 3).toFloat().coerceIn(0f, 1f))
            }
            Goal.DEFICITS -> listOf(
                Milestone(t("Белок в норме — %d из 7 дней", s.weekProtein), s.weekProtein >= 5, s.weekProtein / 5f),
                Milestone(t("Вода в норме — %d из 7 дней", s.weekWater), s.weekWater >= 5, s.weekWater / 5f),
                Milestone(t("Сон ≥ 7 ч — %d из 7 ночей", s.weekSleep), s.weekSleep >= 5, s.weekSleep / 5f),
            )
            Goal.WELLBEING -> listOf(
                Milestone(t("Сон ≥ 7 ч — %d из 7 ночей", s.weekSleep), s.weekSleep >= 5, s.weekSleep / 5f),
                Milestone(t("7 тыс. шагов — %d из 7 дней", s.weekSteps), s.weekSteps >= 5, s.weekSteps / 5f),
                Milestone(t("150 мин активности в неделю"), s.activeMinWeek >= 150, (s.activeMinWeek / 150).toFloat().coerceIn(0f, 1f)),
                Milestone(t("Чекап самочувствия — 70 из 100"), (s.wellbeing ?: 0.0) >= 70, ((s.wellbeing ?: 0.0) / 70).toFloat().coerceIn(0f, 1f)),
            )
        }.let { ms ->
            // темп за время плана
            if (weeks != null && weeks >= 1 && w0 != null && w != null && g in setOf(Goal.LOSE, Goal.GAIN, Goal.MUSCLE))
                ms + Milestone(t("Темп: %s%% веса в неделю за %d нед.", (if (w >= w0) "+" else "−") + n1(Math.abs(w - w0) / w0 * 100 / weeks), weeks.toInt()), false, -1f)
            else ms
        }
    }
}

/** Сворачиваемый раздел: заголовок и краткая строка; подробности — по нажатию. */
@Composable
fun Fold(title: String, summary: String?, open0: Boolean = false, accent: Boolean = false, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(open0) }
    val cs = MaterialTheme.colorScheme
    Surface(color = if (accent) cs.secondaryContainer else cs.surfaceContainerLow, shape = MaterialTheme.shapes.large,
        border = if (accent) null else cardBorder()) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                        color = if (accent) cs.onSecondaryContainer else cs.onSurface)
                    if (summary != null) Text(summary, style = MaterialTheme.typography.bodySmall,
                        color = if (accent) cs.onSecondaryContainer else cs.onSurfaceVariant)
                }
                Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = cs.onSurfaceVariant)
            }
            // 5.37: раскрывается вниз от заголовка, плавно и без «вылета» из угла
            androidx.compose.animation.AnimatedVisibility(open,
                enter = androidx.compose.animation.expandVertically(androidx.compose.animation.core.tween(220), expandFrom = Alignment.Top) +
                    androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(180, delayMillis = 60)),
                exit = androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.tween(180), shrinkTowards = Alignment.Top) +
                    androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120))) {
                Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) { content() }
            }
        }
    }
}

private fun g0(v: Double) = formatNumber(Math.round(v).toDouble())

@Composable
private fun ItemLine(it: Program.Item, mark: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text((if (mark) "★ " else "") + it.food.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(it.amount(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
    Text(t("%s ккал · Б %s · Ж %s · У %s", g0(it.kcal), g0(it.p), g0(it.f), g0(it.c)),
        style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
}

@Composable
fun PlanScreen(act: MainActivity, modifier: Modifier) {
    val repo = act.repo; val prefs = act.prefs
    val entries = collect(repo.entries); val profile = collect(repo.profile); val checkups = collect(repo.checkups)
    val stats = remember(entries, profile, checkups) { PlanStats.of(repo, prefs) }
    var goal by remember { mutableStateOf(Goal.of(prefs.planGoal)) }
    var tick by remember { mutableStateOf(0) }
    val pick = remember(stats) { Plan.recommend(stats) }
    var confirm by remember { mutableStateOf<Goal?>(null) }
    var pickGoal by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    fun choose(g: Goal) {
        prefs.planGoal = g.id; prefs.planStart = LocalDate.now().toString()
        prefs.planW0 = stats.weight ?: 0.0; prefs.planLean0 = stats.lean ?: 0.0
        prefs.monthCfg.let { c -> if (c.auto) prefs.monthCfg = c.autoApply(repo.template.value, g) }
        goal = g; tick++; pickGoal = false; Log.i("Plan", "цель: ${g.id}")
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val g = goal
        // ---- цель: главное крупно ----
        Surface(color = cs.primaryContainer, shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(t("Цель"), style = MaterialTheme.typography.labelMedium, color = cs.onPrimaryContainer)
                Text(g?.title ?: t("не выбрана"), style = MaterialTheme.typography.headlineSmall, color = cs.onPrimaryContainer)
                if (g != null) {
                    val start = runCatching { LocalDate.parse(prefs.planStart) }.getOrNull()
                    Text(t(g.about) + (start?.let { " · " + t("с %s", it.ruShort()) } ?: ""), style = MaterialTheme.typography.bodySmall, color = cs.onPrimaryContainer)
                    val next = remember(stats, tick, g) { Plan.milestones(g, stats, prefs).firstOrNull { !it.done && it.frac >= 0 } }
                    next?.let { m ->
                        Spacer(Modifier.height(6.dp))
                        Text(t("Ближайшая веха: %s", m.name), style = MaterialTheme.typography.bodyMedium, color = cs.onPrimaryContainer)
                        if (m.frac >= 0.02f) LinearProgressIndicator({ m.frac }, Modifier.fillMaxWidth().padding(top = 4.dp).height(6.dp))
                    }
                }
                if (g != pick.goal) Text(t("Рекомендую: %s — %s.", pick.goal.title, pick.reasons.joinToString("; ")),
                    style = MaterialTheme.typography.bodySmall, color = cs.onPrimaryContainer, modifier = Modifier.padding(top = 6.dp))
                Row {
                    if (g != pick.goal) TextButton({ if (g == null) choose(pick.goal) else confirm = pick.goal }) { Text(t("Выбрать «%s»", pick.goal.title)) }
                    TextButton({ pickGoal = !pickGoal }) { Text(if (g == null) t("Другая цель") else t("Сменить цель")) }
                }
                if (pickGoal || g == null) Row(Modifier.hScroll(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Goal.entries.forEach { x -> FilterChip(g == x, { if (g == null) choose(x) else if (g != x) confirm = x }, label = { Text(x.title) }) }
                }
            }
        }
        if (g != null) ForecastFold(act, g, stats, entries)
        if (g == null) {
            Text(t("Выберите цель — соберу программу: нормы на день, распорядок, меню из ваших продуктов, тренировки и вехи."),
                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        } else {
            val tg = remember(stats, g) { Program.targets(g, stats) }
            if (tg == null) Text(t("Нужен вес — взвесьтесь или впишите вес в дневник, и программа посчитается."), color = cs.error)
            else {
                val todayKey = LocalDate.now().toString()
                val meals = remember(entries) { repo.template.value.filter { it.type == ItemType.MEALS }.flatMap { parseMeals(entries[todayKey]?.get(it.id)) } }
                val eaten = mealKcal(meals); val eatenP = mealMacros(meals)?.protein
                val left = remember(tg, eaten, eatenP) { Program.left(g, tg, eaten, eatenP, prefs) }
                // ---- сегодня: главное под цель ----
                val today = remember(entries, g) { Program.today(g, repo, prefs) }
                Fold(t("Сегодня"), today?.value, open0 = true, accent = true) {
                    today?.rows?.forEach { (k, v, ok) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                            Text(k, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(v + when (ok) { true -> " ✓"; false -> " ↓"; null -> "" }, style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium, color = if (ok == false) cs.error else cs.onSecondaryContainer)
                        }
                    }
                    if (Program.kcalMatters(g) && left.fix.isNotEmpty() && (Program.proteinMatters(g) || g == Goal.LOSE && left.protein > tg.protein * 0.3)) {
                        Text(if (g == Goal.LOSE) t("Чтобы не терять мышцы, на оставшиеся калории — белок:") else t("Закрыть остаток:"),
                            style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                        (if (g == Goal.LOSE) left.fix.take(1) else left.fix).forEach { ItemLine(it) }
                    }
                    if (g == Goal.LOSE && (left.kcal ?: 0.0) < 0) Text(t("На сегодня хватит: если голодно — овощи, кефир 1 %, чай."), style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp))
                    if (g == Goal.WELLBEING) Text(t("Калории и белок для этой цели не главное — важнее сон, режим и движение. Еда — по тарелке: половина овощи, четверть белок, четверть крупа."),
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                }
                // ---- распорядок и меню ----
                val r = remember(entries) { runCatching { DayRhythm.of(repo, LocalDate.now(), prefs) }.getOrNull() }
                var variant by remember { mutableStateOf(mapOf<Program.Slot, Int>()) }
                // 5.44: время приёмов — из плана дня (работа, тренировка)
                val dayMeals = remember(entries, r) { r?.let { runCatching { DayPlan.of(repo, prefs, LocalDate.now(), it).meals }.getOrNull() } }
                val menu = remember(tg, r, variant, g, dayMeals) { Program.menu(g, tg, r, prefs, variant, dayMeals) }
                val prefKeys = remember { Program.preferred(prefs).map { it.key }.toSet() }
                Fold(t("Распорядок и меню"), t("%s ккал · белок %s г — %d приёма", g0(menu.sumOf { it.kcal }), g0(menu.sumOf { it.p }), menu.size)) {
                    r?.let { Text(t("Подъём %s · отбой %s · сон 7–9 ч", clockOf(it.usualWake), clockOf(it.bed)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium) }
                    menu.forEach { m ->
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text((m.time?.let { clockOf(it) + "  " } ?: "") + m.slot.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            Text(t("%s ккал · Б %s", g0(m.kcal), g0(m.p)), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                        }
                        m.items.forEach { ItemLine(it, it.food.key in prefKeys) }
                        TextButton({ variant = variant + (m.slot to (variant[m.slot] ?: 0) + 1) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                            Text(t("Другой вариант"), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    Text(t("★ — ваши продукты (частые и избранное). Граммы — готового продукта, если не сказано «сухой». Вода: стакан после подъёма и к каждой еде; последняя еда — за 3 ч до сна."),
                        style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
                // ---- 5.57: список покупок на неделю ----
                if (g != Goal.WELLBEING) ShoppingFold(act, g, tg, r)
                // ---- белок: добирать — только когда цель от него зависит ----
                if (Program.proteinMatters(g) || g == Goal.LOSE) {
                    val po = remember(tick) { Program.proteinOptions(prefs) }
                    Fold(if (g == Goal.LOSE) t("Белок на похудении") else t("Как добрать белок"),
                        if (g == Goal.LOSE) t("не меньше %s г в день — держит мышцы и сытость", g0(tg.protein))
                        else t("норма %s г — %s г на кг; по 25–40 г в 3–4 приёма", g0(tg.protein), formatNumber(tg.pPerKg))) {
                        if (g == Goal.LOSE) Text(t("На дефиците организм берёт энергию и из мышц. Белок 1,6–2,2 г на кг это тормозит и дольше держит сытость — поэтому его не «добирают сверх», а не опускают ниже минимума внутри лимита калорий."),
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 6.dp))
                        Text(if (g == Goal.LOSE) t("25 г белка с минимумом калорий:") else t("25 г белка — это:"), style = MaterialTheme.typography.labelLarge)
                        po.forEach { ItemLine(it, it.food.key in prefKeys) }
                        if (g != Goal.LOSE) Text(t("Отсортировано по калориям: сверху — больше белка на меньше калорий. Порция протеина или Формулы 1 удобна на завтрак и перекус."),
                            style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                // ---- калории ----
                if (g == Goal.LOSE) Fold(t("Как срезать калории"), t("замены без голода")) {
                    Program.swaps().forEach { sw ->
                        Text(t("%s %s → %s %s", sw.from.food.name, sw.from.amount(), sw.to.food.name, sw.to.amount()), style = MaterialTheme.typography.bodyMedium)
                        Text(t("−%s ккал", g0(sw.save)), style = MaterialTheme.typography.labelSmall, color = cs.primary)
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(t("Плюс: полтарелки овощей, белок в каждый приём, без сладких напитков — это даёт сытость на дефиците."),
                        style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                } else if (g != Goal.WELLBEING) Fold(t("Как добрать калории"), t("плотно, но без сладкого")) {
                    Program.kcalOptions().forEach { ItemLine(it) }
                    Text(t("Проще всего: + горсть орехов, ложка масла в кашу или салат, стакан молока к еде."),
                        style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
                // ---- самочувствие: сон, режим, движение ----
                if (g == Goal.WELLBEING) {
                    Fold(t("Сон и режим"), r?.let { t("отбой %s · подъём %s", clockOf(it.bed), clockOf(it.nextWake)) }) {
                        listOf(
                            t("Подъём в одно время каждый день, в выходные тоже (± 30 мин)"),
                            t("10–20 мин дневного света в первый час после подъёма"),
                            t("Кофе — до обеда, последняя еда — за 3 ч до сна"),
                            t("Час до сна — тусклый свет, без работы и новостей"),
                            t("Спальня прохладная (17–20 °C) и тёмная"),
                        ).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp)) }
                        if (!prefs.bedOn) TextButton({ act.openSettings.value = "notif" }) { Text(t("Включить напоминание об отбое")) }
                        r?.jetlag?.takeIf { it >= 60 }?.let { Text(t("Выходные сдвигают режим на %s — это самое сильное, что стоит выровнять.", formatDuration(it.toDouble())),
                            style = MaterialTheme.typography.bodySmall, color = cs.error, modifier = Modifier.padding(top = 4.dp)) }
                    }
                    Fold(t("Движение"), stats.steps?.let { t("в среднем %s шагов", formatNumber(Math.round(it / 100) * 100.0)) }) {
                        listOf(
                            t("7–10 тыс. шагов в день — часть быстрым шагом"),
                            t("150–300 мин умеренной активности в неделю"),
                            t("10–15 мин прогулки после еды — мягче сахар"),
                            t("Каждые 30–60 мин сидения — встать на 2–3 минуты"),
                        ).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp)) }
                    }
                }
                // ---- 5.56: неделя тренировок (шаблон нагрузки) ----
                WeekTplFold(act, g, stats)
                // ---- ориентиры (как было) ----
                Fold(t("Ориентиры и как у вас сейчас"), null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Plan.steps(g, stats).forEach { st ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text(st.what, Modifier.width(96.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Column(Modifier.weight(1f)) {
                                    Text(st.target, style = MaterialTheme.typography.bodyMedium)
                                    st.now?.let { Text(it + when (st.ok) { true -> " ✓"; false -> " ↓"; null -> "" },
                                        style = MaterialTheme.typography.labelSmall, color = if (st.ok == false) cs.error else cs.onSurfaceVariant) }
                                }
                            }
                        }
                    }
                }
            }
            val ms = remember(stats, tick, g) { Plan.milestones(g, stats, prefs) }
            Fold(t("Вехи"), t("пройдено %d из %d", ms.count { it.done }, ms.count { it.frac >= 0 })) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ms.forEach { m ->
                        if (m.frac < 0) Text(m.name, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                        else Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (m.done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, null, Modifier.size(20.dp),
                                tint = if (m.done) cs.primary else cs.outline)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(m.name, style = MaterialTheme.typography.bodyMedium)
                                if (!m.done && m.frac >= 0.02f) LinearProgressIndicator({ m.frac }, Modifier.fillMaxWidth().padding(top = 4.dp).height(4.dp))
                            }
                        }
                    }
                }
            }
        }
        Text(
            t("Ориентиры: темп похудения 0,5–1 % веса в неделю, заметный результат — от 5 %; белок 1,6–2,2 г/кг (Morton и др., 2018); ≥ 10 подходов на группу мышц в неделю (Schoenfeld и др., 2017); сон 7–9 ч (AASM); 150–300 мин активности в неделю (ВОЗ, 2020); 7–10 тыс. шагов (Paluch и др., 2022). Это не медицинская программа — при болезнях согласуйте с врачом."),
            style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
    }
    confirm?.let { ng ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(t("Сменить цель на «%s»?", ng.title)) },
            text = { Text(t("Вехи начнут считаться заново — от сегодняшнего веса.")) },
            confirmButton = { Button({ choose(ng); confirm = null }) { Text(t("Сменить")) } },
            dismissButton = { TextButton({ confirm = null }) { Text(t("Отмена")) } },
        )
    }
}
