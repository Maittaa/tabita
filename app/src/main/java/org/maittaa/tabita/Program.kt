package org.maittaa.tabita

import java.time.LocalDate

/**
 * Программа жизни (5.29): под выбранную цель — нормы на день, распорядок, меню с граммовками
 * из ваших продуктов (частые, избранное, свои) и справочника, как добрать белок и калории, остаток на сегодня.
 * Нормы: белок 1,6–2,2 г/кг (Morton и др., 2018; при похудении — ближе к верхней границе, Helms и др., 2014);
 * жиры 25–30 % калорий; при ИМТ ≥ 30 белок считается от веса при ИМТ 27 (иначе норма завышена жиром).
 */
object Program {
    data class Targets(val kcal: Double?, val protein: Double, val fatG: Double?, val carbG: Double?, val pPerKg: Double)

    fun kcalFactor(g: Goal) = when (g) { Goal.LOSE -> 0.825; Goal.GAIN -> 1.125; Goal.MUSCLE -> 1.075; else -> 1.0 }
    /** Белок г/кг: на дефиците — выше (сохранить мышцы и сытость), для самочувствия — обычная норма взрослого (≥ 0,8; ВОЗ). */
    fun proteinPerKg(g: Goal) = when (g) { Goal.LOSE -> 1.8; Goal.MUSCLE -> 1.8; Goal.GAIN, Goal.DEFICITS -> 1.6; Goal.WELLBEING -> 1.0 }

    /** Что для цели главное в еде: добирать белок (рост мышц, набор, дефициты) — или он лишь «не ниже минимума». */
    fun proteinMatters(g: Goal) = g == Goal.MUSCLE || g == Goal.GAIN || g == Goal.DEFICITS
    /** Калории: лимит (похудение), добор (набор, мышцы, дефициты) или не считаем (самочувствие). */
    fun kcalMatters(g: Goal) = g != Goal.WELLBEING

    fun targets(g: Goal, s: PlanStats): Targets? {
        val w = s.weight ?: return null
        val refW = if ((s.bmi ?: 0.0) >= 30 && s.h != null) 27 * s.h * s.h else w
        val p = Math.round(refW * proteinPerKg(g) / 5) * 5.0
        val kcal = s.burn?.let { Math.round(it * kcalFactor(g) / 10) * 10.0 }
        val fat = kcal?.let { Math.round(it * 0.28 / 9).toDouble() }
        val carb = kcal?.let { k -> Math.round(((k - p * 4 - fat!! * 9) / 4).coerceAtLeast(50.0)).toDouble() }
        return Targets(kcal, p, fat, carb, proteinPerKg(g))
    }

    // ---------- продукты по ролям ----------

    private fun byName(prefix: String) = FoodDb.all.firstOrNull { it.ru.startsWith(prefix) }

    /** Ваши продукты: частые и избранные, по убыванию использования. */
    fun preferred(prefs: Prefs): List<Food> {
        val use = prefs.foodUse; val fav = prefs.foodFav
        return (FoodDb.mine + FoodDb.all).filter { (use[it.key] ?: 0) > 0 || it.key in fav }
            .sortedByDescending { (use[it.key] ?: 0) + if (it.key in fav) 3 else 0 }
    }

    fun proteinShare(f: Food) = if (f.kcal > 0) f.p * 4 / f.kcal else 0.0
    fun isProtein(f: Food) = f.kcal >= 40 && proteinShare(f) >= 0.35 && f.cat != "fat"
    fun isCarb(f: Food) = f.cat in setOf("grain", "bread") && f.kcal > 60 && f.c * 4 / f.kcal >= 0.6 && f.gi?.let { it <= 75 } != false
    fun isVeg(f: Food) = f.cat == "veg" && f.kcal < 60
    fun isFruit(f: Food) = f.cat == "fruit" && f.kcal < 100
    fun isFat(f: Food) = (f.cat == "nuts" || (f.cat == "fat" && f.f > 90))

    enum class Slot(val ru: String, val kcal: Double, val protein: Double) {
        BREAKFAST("Завтрак", 0.25, 0.25), LUNCH("Обед", 0.35, 0.3), SNACK("Перекус", 0.10, 0.15), DINNER("Ужин", 0.30, 0.3);
        val title get() = t(ru)
    }

    private val defProtein = mapOf(
        Slot.BREAKFAST to listOf("Творог 5%", "Яйцо куриное", "Йогурт греческий", "Омлет", "Творог 0–1%"),
        Slot.LUNCH to listOf("Куриная грудка", "Индейка, грудка", "Говядина нежирная", "Минтай отварной", "Горбуша запечённая"),
        Slot.SNACK to listOf("Йогурт греческий", "Творог 5%", "Кефир 1%", "Творог 0–1%"),
        Slot.DINNER to listOf("Треска отварная", "Куриная грудка", "Творог 5%", "Индейка, грудка", "Горбуша запечённая", "Яйцо куриное"),
    )
    private val defCarb = mapOf(
        Slot.BREAKFAST to listOf("Овсяные хлопья (сухие)", "Хлеб цельнозерновой", "Гречка отварная"),
        Slot.LUNCH to listOf("Гречка отварная", "Рис бурый отварной", "Булгур отварной", "Макароны отварные", "Киноа отварная"),
        Slot.SNACK to emptyList(),
        Slot.DINNER to listOf("Гречка отварная", "Булгур отварной", "Рис бурый отварной"),
    )
    private val defVeg = listOf("Огурцы", "Помидоры", "Брокколи отварная", "Капуста белокочанная", "Перец сладкий красный", "Кабачок")
    private val defFruit = listOf("Яблоко", "Банан", "Апельсин", "Груша", "Киви")
    private val defFat = listOf("Миндаль", "Грецкий орех", "Масло оливковое")

    private fun pool(slot: Slot, pref: List<Food>, def: List<String>, ok: (Food) -> Boolean, slotOk: (Food) -> Boolean = { true }): List<Food> =
        (pref.filter { ok(it) && slotOk(it) } + def.mapNotNull { byName(it) }).distinctBy { it.key }

    /** Подходит ли белковый продукт к приёму: утром и в перекус — молочное, яйца, свои смеси; в обед — мясо, рыба, бобовые. */
    private fun proteinFits(slot: Slot, f: Food) = when (slot) {
        Slot.BREAKFAST, Slot.SNACK -> f.cat in setOf("dairy", "egg", "my")
        Slot.LUNCH -> f.cat in setOf("meat", "fish", "legume")
        Slot.DINNER -> f.cat in setOf("meat", "fish", "egg", "dairy", "legume")
    }

    data class Item(val food: Food, val grams: Double) {
        val k get() = grams / 100
        val kcal get() = food.kcal * k
        val p get() = food.p * k
        val f get() = food.f * k
        val c get() = food.c * k
        fun amount(): String {
            val u = if (food.ml) t("мл") else t("г")
            val g = formatNumber(grams) + " " + u
            val units = food.unitG?.let { grams / it }?.takeIf { food.unit != null && Math.abs(it - Math.round(it)) < 0.05 }
            if (units != null) return formatNumber(Math.round(units).toDouble()) + " × " + food.unit + " (" + g + ")"
            val pcs = food.portion?.takeIf { food.portionRu?.contains("шт") == true }?.let { grams / it }?.takeIf { Math.abs(it - Math.round(it)) < 0.05 && it >= 1 }
            return if (pcs != null) t("%s шт.", formatNumber(Math.round(pcs).toDouble())) + " (" + g + ")" else g
        }
    }
    data class Meal(val slot: Slot, val time: Int?, val items: List<Item>, val kcalT: Double?, val proteinT: Double) {
        val kcal get() = items.sumOf { it.kcal }
        val p get() = items.sumOf { it.p }
        val f get() = items.sumOf { it.f }
        val c get() = items.sumOf { it.c }
    }

    private fun round10(v: Double) = Math.round(v / 10) * 10.0

    /** Граммы, чтобы набрать [protein] г белка: шаг — мерная ложка/штука/10 г. */
    fun gramsFor(f: Food, protein: Double, min: Double = 30.0, max: Double = 300.0): Double {
        val raw = (protein / (f.p / 100)).coerceIn(min, max)
        f.unitG?.let { u -> return (Math.round(raw / u).coerceIn(1, 6) * u) }
        val pcs = f.portion?.takeIf { f.portionRu?.contains("шт") == true }
        if (pcs != null) return (Math.round(raw / pcs).coerceIn(1, 5) * pcs).toDouble()
        return round10(raw)
    }

    /** Меню на день; [variant] — сдвиг выбора по каждому приёму (кнопка «Другой вариант»). */
    fun menu(g: Goal, tg: Targets, r: DayRhythm?, prefs: Prefs, variant: Map<Slot, Int> = emptyMap(), at: Map<Slot, Int>? = null): List<Meal> {
        val pref = preferred(prefs)
        val wake = r?.usualWake ?: 420; val bed = r?.bed ?: 1380
        // 5.44: время приёмов — из плана дня (обед по работе, перекус до тренировки, ужин после неё)
        val times = at ?: mapOf(Slot.BREAKFAST to wake + 45, Slot.LUNCH to wake + 330, Slot.SNACK to wake + 510, Slot.DINNER to bed - 210)
        return Slot.entries.map { slot ->
            val v = variant[slot] ?: 0
            val mk = tg.kcal?.let { it * slot.kcal }
            val mp = tg.protein * slot.protein
            val items = mutableListOf<Item>()
            val prot = pool(slot, pref, defProtein[slot]!!, ::isProtein) { proteinFits(slot, it) }
            prot.getOrNull(v % prot.size.coerceAtLeast(1))?.let { items += Item(it, gramsFor(it, mp * 0.85)) }
            if (slot == Slot.LUNCH || slot == Slot.DINNER) {
                val veg = pool(slot, pref, defVeg, ::isVeg)
                veg.getOrNull(v % veg.size.coerceAtLeast(1))?.let { items += Item(it, 200.0) }
            }
            if (slot == Slot.SNACK) {
                val fr = pool(slot, pref, defFruit, ::isFruit)
                fr.getOrNull(v % fr.size.coerceAtLeast(1))?.let { items += Item(it, (it.portion ?: 150).toDouble()) }
            }
            val carbs = pool(slot, pref, defCarb[slot]!!, ::isCarb) { f -> slot != Slot.BREAKFAST || f.cat in setOf("grain", "bread") }
            if (mk != null) {
                // углеводы — до ~85 % калорий приёма (на похудении ужин легче)
                val share = if (g == Goal.LOSE && slot == Slot.DINNER) 0.6 else 0.85
                val left = mk * share - items.sumOf { it.kcal }
                carbs.getOrNull(v % carbs.size.coerceAtLeast(1))?.let { c ->
                    if (left > 60) items += Item(c, round10((left / (c.kcal / 100)).coerceIn(30.0, 400.0)))
                }
                val rest = mk - items.sumOf { it.kcal }
                if (rest > 70 && (slot != Slot.DINNER || g != Goal.LOSE)) {
                    val fats = pool(slot, pref, defFat, ::isFat)
                    fats.getOrNull(v % fats.size.coerceAtLeast(1))?.let { fa ->
                        val gr = Math.round((rest / (fa.kcal / 100)).coerceIn(5.0, 40.0) / 5) * 5.0
                        items += Item(fa, gr)
                    }
                }
            }
            Meal(slot, times[slot], items, mk, mp)
        }
    }

    /** Чем добрать [protein] г белка: самые «чистые» по калориям, ваши — первыми. */
    fun proteinOptions(prefs: Prefs, protein: Double = 25.0, n: Int = 6): List<Item> {
        val pref = preferred(prefs).filter(::isProtein)
        val def = listOf("Творог 0–1%", "Куриная грудка", "Тунец консервированный", "Йогурт греческий", "Минтай отварной", "Яйцо куриное", "Креветки варёные", "Индейка, грудка")
            .mapNotNull(::byName)
        return (pref + def).distinctBy { it.key }.take(n + 2)
            .map { Item(it, gramsFor(it, protein, 20.0, 400.0)) }.sortedBy { it.kcal }.take(n)
    }

    /** Чем добрать калории без сладкого: плотные, но полезные продукты. */
    fun kcalOptions(): List<Item> = listOf(
        "Миндаль" to 30.0, "Арахисовая паста" to 20.0, "Масло оливковое" to 10.0, "Банан" to 120.0,
        "Овсяные хлопья (сухие)" to 50.0, "Молоко 3,2%" to 250.0, "Сыр твёрдый" to 30.0, "Курага" to 40.0,
    ).mapNotNull { (n, g) -> byName(n)?.let { Item(it, g) } }

    /** Как срезать калории без голода: замены (было → стало, сколько экономится на порцию). */
    data class Swap(val from: Item, val to: Item) { val save get() = from.kcal - to.kcal }
    fun swaps(): List<Swap> = listOf(
        ("Сметана 20%" to 30.0) to ("Йогурт греческий" to 30.0),
        ("Рис белый отварной" to 200.0) to ("Гречка отварная" to 120.0),
        ("Творог 9%" to 200.0) to ("Творог 0–1%" to 200.0),
        ("Колбаса варёная" to 60.0) to ("Куриная грудка" to 60.0),
        ("Майонез" to 20.0) to ("Йогурт натуральный" to 30.0),
        ("Батон нарезной" to 60.0) to ("Хлеб цельнозерновой" to 40.0),
    ).mapNotNull { (a, b) -> val fa = byName(a.first); val fb = byName(b.first)
        if (fa != null && fb != null) Swap(Item(fa, a.second), Item(fb, b.second)) else null }

    /** Остаток на сегодня и чем его закрыть. */
    data class Left(val kcal: Double?, val protein: Double, val fix: List<Item>)

    fun left(g: Goal, tg: Targets, eatenKcal: Double?, eatenP: Double?, prefs: Prefs): Left {
        val lp = (tg.protein - (eatenP ?: 0.0)).coerceAtLeast(0.0)
        val lk = tg.kcal?.let { it - (eatenKcal ?: 0.0) }
        val fix = mutableListOf<Item>()
        if (lp >= 10) proteinOptions(prefs, minOf(lp, 45.0), 1).firstOrNull()?.let { fix += it }
        val rest = (lk ?: 0.0) - fix.sumOf { it.kcal }
        if (lk != null && rest > 150 && g != Goal.LOSE) {
            val c = byName("Гречка отварная")!!
            fix += Item(c, round10((rest * 0.7 / (c.kcal / 100)).coerceIn(50.0, 350.0)))
        }
        return Left(lk, lp, fix)
    }

    /** Тренировки на неделю под цель. */
    fun trainingPlan(g: Goal): List<String> = when (g) {
        Goal.LOSE -> listOf(t("Силовые 2–3 раза в неделю, всё тело — сохраняют мышцы на дефиците"), t("Шаги 8–10 тыс. каждый день — основной расход сверх тренировок"), t("Кардио по желанию: 1–2 раза по 20–30 мин"))
        Goal.MUSCLE -> listOf(t("Силовые 3 раза в неделю (пн/ср/пт), всё тело или верх/низ"), t("10–20 подходов на группу в неделю, 6–15 повторов, 1–3 в запасе"), t("Прогрессия: +1 повтор или +вес каждые 1–2 недели"), t("Шаги 7–8 тыс. — не больше, чтобы не съедать профицит"))
        Goal.GAIN -> listOf(t("Силовые 2–3 раза в неделю — чтобы набор шёл в мышцы"), t("Кардио — минимум, шаги 6–8 тыс."))
        Goal.DEFICITS -> listOf(t("Силовые 2 раза в неделю"), t("Шаги 7–10 тыс."))
        Goal.WELLBEING -> listOf(t("150–300 мин умеренной активности в неделю (быстрая ходьба, велосипед)"), t("Силовые 2 раза в неделю"), t("Прогулка 10–15 мин после еды"))
    }

    /** Главное на сегодня под цель: крупная строка, строки «показатель — значение», подпись; [alert] — выбились из плана. */
    data class Today(val value: String, val rows: List<Triple<String, String, Boolean?>>, val sub: String?, val alert: Boolean)

    fun today(g: Goal, repo: Repo, prefs: Prefs, date: LocalDate = LocalDate.now()): Today? {
        fun n(v: Double) = formatNumber(Math.round(v).toDouble())
        val tmpl = repo.template.value; val day = repo.entries.value[date.toString()] ?: emptyMap()
        val st = PlanStats.of(repo, prefs, date)
        val tg = targets(g, st) ?: return null
        val meals = tmpl.filter { it.type == ItemType.MEALS }.flatMap { parseMeals(day[it.id]) }
        val kcal = mealKcal(meals) ?: 0.0; val prot = mealMacros(meals)?.protein ?: 0.0
        val sleep = tmpl.firstOrNull { it.source == Source.HC_SLEEP }?.let { parseNumber(day[it.id]) }
        val steps = Anthro.stepsOf(tmpl, day)
        val water = tmpl.firstOrNull { it.type == ItemType.WATER }?.let { parseNumber(day[it.id]) }
        val waterNorm = tmpl.firstOrNull { it.type == ItemType.WATER }?.let { Hydration.normOf(it, day) }
        val r = runCatching { DayRhythm.of(repo, date, prefs) }.getOrNull()
        val next = Plan.milestones(g, st, prefs).firstOrNull { !it.done && it.frac >= 0 }?.let { t("Ближайшая веха: %s", it.name) }
        val rows = mutableListOf<Triple<String, String, Boolean?>>()
        val value: String; var alert = false
        when (g) {
            Goal.LOSE -> {
                val left = tg.kcal?.let { it - kcal }
                value = when {
                    left == null -> t("белок не меньше %s г", n(tg.protein))
                    left >= 0 -> t("осталось %s ккал", n(left))
                    else -> t("перебор %s ккал", n(-left)).also { alert = true }
                }
                tg.kcal?.let { rows += Triple(t("Лимит дня"), t("%s ккал", n(it)), null) }
                rows += Triple(t("Белок — не меньше"), t("%s из %s г", n(prot), n(tg.protein)), if (meals.isEmpty()) null else prot >= tg.protein * 0.8)
                steps?.let { rows += Triple(t("Шаги"), n(it), it >= 8000) }
            }
            Goal.MUSCLE, Goal.GAIN -> {
                val lk = (tg.kcal ?: 0.0) - kcal; val lp = tg.protein - prot
                value = listOfNotNull(if (lk > 0) t("ещё %s ккал", n(lk)) else t("калории ✓"), if (lp > 5) t("белок ещё %s г", n(lp)) else t("белок ✓")).joinToString(" · ")
                rows += Triple(t("Калории"), t("%s из %s", n(kcal), n(tg.kcal ?: 0.0)), if (meals.isEmpty()) null else lk <= 150)
                rows += Triple(t("Белок"), t("%s из %s г", n(prot), n(tg.protein)), if (meals.isEmpty()) null else lp <= 5)
                sleep?.let { rows += Triple(t("Сон"), formatDuration(it), it >= 420) }
            }
            Goal.DEFICITS -> {
                val lp = tg.protein - prot
                value = if (lp > 5) t("белок ещё %s г", n(lp)) else t("белок ✓")
                rows += Triple(t("Белок"), t("%s из %s г", n(prot), n(tg.protein)), if (meals.isEmpty()) null else lp <= 5)
                rows += Triple(t("Калории"), t("%s из %s", n(kcal), n(tg.kcal ?: 0.0)), if (meals.isEmpty()) null else kcal >= (tg.kcal ?: 0.0) * 0.85)
                if (water != null || waterNorm != null) rows += Triple(t("Вода"), t("%s из %s л", formatNumber(Math.round((water ?: 0.0) * 10) / 10.0), formatNumber(waterNorm ?: 2.0)), (water ?: 0.0) >= (waterNorm ?: 2.0) * 0.9)
            }
            Goal.WELLBEING -> {
                value = listOfNotNull(sleep?.let { t("сон %s", formatDuration(it)) }, steps?.let { t("%s шагов", n(it)) }).joinToString(" · ").ifEmpty { t("сон, шаги и режим") }
                sleep?.let { rows += Triple(t("Сон ночью"), formatDuration(it), it >= 420) }
                steps?.let { rows += Triple(t("Шаги"), t("%s из 7000", n(it)), it >= 7000) }
                if (water != null) rows += Triple(t("Вода"), formatNumber(Math.round(water * 10) / 10.0) + " " + t("л"), waterNorm?.let { water >= it * 0.9 })
                r?.let { rows += Triple(t("Отбой сегодня"), clockOf(it.bed), null) }
            }
        }
        return Today(value, rows, next, alert)
    }
}
