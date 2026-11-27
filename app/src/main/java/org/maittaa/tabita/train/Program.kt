package org.maittaa.tabita.train

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

enum class Goal(val title: String, val repMin: Int, val repMax: Int, val secMin: Int, val secMax: Int, val rest: Int) {
    MASS("Набор массы", 10, 15, 20, 40, 90),
    STRENGTH("Сила", 6, 10, 20, 30, 120),
    TONE("Общая форма", 12, 20, 30, 45, 60),
}

/**
 * Профиль тела — для ИМТ, поправок программы, стартовых весов и подсказок по питанию.
 * Рост, возраст и пол (1.2) берутся из профиля Tabita, если он там заполнен, иначе — из настроек тренировок.
 */
data class Body(
    val heightCm: Int?, val weightKg: Double?, val weightFromTabita: Boolean = false,
    val age: Int? = null, val sex: String? = null,
    val heightFromTabita: Boolean = false, val ageFromTabita: Boolean = false, val sexFromTabita: Boolean = false,
) {
    val anyFromTabita get() = weightFromTabita || heightFromTabita || ageFromTabita || sexFromTabita

    /** Базовый обмен по Миффлину – Сан Жеору (как в Tabita), ккал. Пол не задан — среднее. */
    val bmr: Double? get() {
        val h = heightCm?.takeIf { it > 0 } ?: return null
        val w = weightKg ?: return null
        val a = age ?: return null
        return 10 * w + 6.25 * h - 5 * a + when (sex) { "m" -> 5.0; "f" -> -161.0; else -> -78.0 }
    }

    val bmi: Double? get() = if (heightCm != null && heightCm > 0 && weightKg != null) weightKg / (heightCm / 100.0).let { it * it } else null
    val heavy: Boolean get() = (bmi ?: 0.0) >= 30
    val light: Boolean get() = bmi?.let { it < 18.5 } ?: false

    fun bmiLabel(): String? = bmi?.let { b ->
        val v = "%.1f".format(b).replace('.', ',')
        when {
            b < 18.5 -> "ИМТ $v — ниже нормы"
            b < 25 -> "ИМТ $v — норма"
            b < 30 -> "ИМТ $v — выше нормы"
            else -> "ИМТ $v — ожирение"
        }
    }

    /** Поправки, которые учитываются в программе и тренировке. */
    fun notes(goal: Goal): List<String> = buildList {
        val b = bmi ?: return@buildList
        val v = "%.1f".format(b).replace('.', ',')
        when {
            b >= 30 -> {
                add("ИМТ $v: отжимания и упражнения на одной ноге — в облегчённых вариантах, прыжки в разминке выключены")
            }
            b < 18.5 && goal == Goal.MASS -> add("ИМТ $v — ниже нормы: для набора массы главное — питание с запасом калорий и белка")
            b >= 25 && goal == Goal.MASS -> add("ИМТ $v — выше нормы: вместо набора массы можно выбрать цель «Общая форма»")
        }
    }

    /** Ориентиры питания под цель: белок по весу, калории от базового обмена (× 1,4 — 3 тренировки в неделю). */
    fun nutrition(goal: Goal): List<String> = buildList {
        val w = weightKg ?: return@buildList
        val (pLo, pHi) = when (goal) { Goal.MASS -> 1.6 to 2.0; Goal.STRENGTH -> 1.6 to 2.0; Goal.TONE -> 1.2 to 1.6 }
        add("Белок: ${(w * pLo).roundToInt()}–${(w * pHi).roundToInt()} г в день")
        bmr?.let { b ->
            val tdee = b * 1.4
            val r = { x: Double -> ((x / 50).roundToInt() * 50).toString() }
            add(when (goal) {
                Goal.MASS -> "Калории для набора: ≈ ${r(tdee + 300)} ккал (расход ≈ ${r(tdee)} + 300)"
                Goal.STRENGTH -> "Калории: ≈ ${r(tdee)}–${r(tdee + 200)} ккал"
                Goal.TONE -> "Калории: ≈ ${r(tdee)} ккал — без дефицита, чтобы мышцы росли"
            })
        }
        age?.let { if (it >= 40) add("После 40 восстановление дольше: держи день отдыха между силовыми и не сокращай отдых между подходами") }
    }
}

/** Упражнение в программе. */
data class PlanEx(val exId: String, val sets: Int, val repMin: Int, val repMax: Int) {
    val def: ExerciseDef? get() = Library.get(exId)
    fun toJson() = JSONObject().put("ex", exId).put("sets", sets).put("min", repMin).put("max", repMax)

    companion object {
        fun fromJson(o: JSONObject) = PlanEx(o.getString("ex"), o.optInt("sets", 2), o.optInt("min", 10), o.optInt("max", 15))
    }
}

data class ProgramDay(
    val id: String = UUID.randomUUID().toString().take(6),
    val name: String,
    val weekday: DayOfWeek?,
    val exercises: List<PlanEx>,
) {
    fun toJson() = JSONObject().put("id", id).put("name", name).put("wd", weekday?.value ?: 0)
        .put("ex", JSONArray(exercises.map { it.toJson() }))

    companion object {
        fun fromJson(o: JSONObject) = ProgramDay(
            o.getString("id"), o.getString("name"),
            o.optInt("wd").takeIf { it in 1..7 }?.let { DayOfWeek.of(it) },
            o.getJSONArray("ex").let { a -> List(a.length()) { PlanEx.fromJson(a.getJSONObject(it)) } },
        )
    }
}

data class Program(
    val name: String,
    val goal: Goal,
    val restSec: Int,
    val noEquipment: Boolean,
    val days: List<ProgramDay>,
    val place: Place = Place.HOME,
    val notes: List<String> = emptyList(),
) {
    fun toJson() = JSONObject().put("name", name).put("goal", goal.name).put("rest", restSec)
        .put("noEq", noEquipment).put("place", place.name).put("notes", JSONArray(notes))
        .put("days", JSONArray(days.map { it.toJson() }))

    companion object {
        fun fromJson(o: JSONObject) = Program(
            o.getString("name"),
            runCatching { Goal.valueOf(o.getString("goal")) }.getOrDefault(Goal.MASS),
            o.optInt("rest", 90), o.optBoolean("noEq"),
            o.getJSONArray("days").let { a -> List(a.length()) { ProgramDay.fromJson(a.getJSONObject(it)) } },
            runCatching { Place.valueOf(o.optString("place")) }.getOrDefault(Place.HOME),
            o.optJSONArray("notes")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList(),
        )
    }
}

fun DayOfWeek.ru(): String = listOf("понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье")[value - 1]
fun DayOfWeek.ruShort(): String = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")[value - 1]

/** Диапазон повторов упражнения для цели: секунды — по цели, тяжёлые упражнения — свой. */
fun rangeFor(def: ExerciseDef, goal: Goal): IntRange = when {
    def.seconds -> goal.secMin..goal.secMax
    def.reps != null -> def.reps
    def.pattern == Pattern.CALF || def.pattern == Pattern.CORE -> maxOf(goal.repMin, 12)..maxOf(goal.repMax, 20)
    // изолирующие и тренажёры с лёгким весом — больше повторов
    def.pattern in setOf(Pattern.SHOULDER, Pattern.REAR, Pattern.BICEPS) || def.id in setOf("db_ext", "pushdown", "leg_ext", "leg_curl") ->
        maxOf(goal.repMin, 10)..maxOf(goal.repMax, 15)
    // в зале со штангой для массы — 8–12
    def.load == LoadKind.BARBELL && goal == Goal.MASS -> 8..12
    else -> goal.repMin..goal.repMax
}

/** Разбивка (1.5): всё тело каждую тренировку или разные дни на разные группы мышц. */
enum class Split(val title: String, val hint: String) {
    FULL("Всё тело", "Каждая тренировка — на все группы мышц. Лучше всего при 2–3 днях в неделю и на старте."),
    UPPER_LOWER("Верх / низ", "Дни чередуются: верх тела (грудь, спина, плечи, руки) и низ (ноги, ягодицы, пресс). Подходит для 2–4 дней."),
    PPL("Жим / тяга / ноги", "Три типа дней: жим (грудь, плечи, трицепс), тяга (спина, бицепс, задняя дельта), ноги. Для 3–6 дней; при 3 днях каждая группа — раз в неделю."),
}

data class GenSettings(
    val goal: Goal = Goal.MASS,
    val days: List<DayOfWeek> = listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
    val minutes: Int = 30,
    val level: Int = 1,
    val noEquipment: Boolean = false,
    val place: Place = Place.HOME,
    val split: Split = Split.FULL,
)

object Generator {
    private val letters = listOf("А", "Б", "В", "Г", "Д", "Е", "Ж")

    /**
     * Full-body на каждый день: ноги (присед/выпад по очереди), отжимания/жим, тяга, наклон/ягодицы,
     * дальше — по времени: плечи, руки, пресс, икры. Варианты упражнений чередуются по дням.
     */
    fun generate(s: GenSettings, homeEq: Set<Equipment>, gymEq: Set<Equipment> = emptySet(), body: Body = Body(null, null)): Program {
        val gym = s.place == Place.GYM
        val eq = if (gym) gymEq else homeEq
        val noEq = !gym && s.noEquipment
        val days = s.days.sortedBy { it.value }.ifEmpty { listOf(DayOfWeek.MONDAY) }
        val count = when { s.minutes <= 20 -> 5; s.minutes <= 30 -> 7; else -> 8 }
        val sets = when { s.minutes >= 45 -> 3; s.level >= 2 -> 3; else -> 2 }
        val bar = !noEq && Equipment.BAR in eq
        // счётчик дней одного типа: «Верх А», «Верх Б»… и чередование вариантов
        val typeCount = mutableMapOf<String, Int>()
        val programDays = days.mapIndexed { i, wd ->
            if (s.split != Split.FULL) {
                val type = when (s.split) {
                    Split.UPPER_LOWER -> listOf("Верх", "Низ")[i % 2]
                    else -> listOf("Жим", "Тяга", "Ноги")[i % 3]
                }
                val v = typeCount.getOrDefault(type, 0); typeCount[type] = v + 1
                val pull2 = if (bar || (gym && Equipment.CABLES in eq)) Pattern.VPULL else Pattern.ROW
                val order = when (type) {
                    "Верх" -> listOf(Pattern.PUSH, Pattern.ROW, Pattern.VPUSH, pull2, if (v % 2 == 0) Pattern.SHOULDER else Pattern.REAR,
                        Pattern.BICEPS, Pattern.TRICEPS, Pattern.REAR)
                    "Низ" -> listOf(if (v % 2 == 0) Pattern.SQUAT else Pattern.LUNGE, Pattern.HINGE, if (v % 2 == 0) Pattern.LUNGE else Pattern.SQUAT,
                        Pattern.GLUTE, Pattern.CALF, Pattern.CORE, Pattern.CORE, Pattern.HINGE)
                    "Жим" -> listOf(Pattern.PUSH, Pattern.VPUSH, Pattern.PUSH, Pattern.SHOULDER, Pattern.TRICEPS, Pattern.TRICEPS, Pattern.CORE, Pattern.SHOULDER)
                    "Тяга" -> listOf(Pattern.ROW, pull2, Pattern.ROW, Pattern.REAR, Pattern.BICEPS, Pattern.BICEPS, Pattern.CORE, Pattern.REAR)
                    else -> listOf(Pattern.SQUAT, Pattern.HINGE, Pattern.LUNGE, Pattern.GLUTE, Pattern.CALF, Pattern.CORE, Pattern.GLUTE, Pattern.CALF)
                }
                val used = mutableSetOf<String>()
                val ex = order.mapNotNull { p ->
                    if (used.size >= count) return@mapNotNull null
                    pick(p, s.level, v, eq, noEq, used, gym, body)?.let { d ->
                        used += d.id
                        val r = rangeFor(d, s.goal)
                        PlanEx(d.id, sets, r.first, r.last)
                    }
                }
                return@mapIndexed ProgramDay(name = "$type ${letters[v % letters.size]}", weekday = wd, exercises = ex)
            }
            val slots = mutableListOf(
                if (i % 2 == 0) Pattern.SQUAT else Pattern.LUNGE,
                Pattern.PUSH,
                if ((bar || (gym && Equipment.CABLES in eq)) && i % 2 == 1) Pattern.VPULL else Pattern.ROW,
                if (i % 2 == 0) Pattern.HINGE else Pattern.GLUTE,
            )
            val extra = listOf(
                listOf(Pattern.VPUSH, Pattern.SHOULDER, Pattern.REAR)[i % 3],
                if (i % 2 == 0) Pattern.BICEPS else Pattern.TRICEPS,
                Pattern.CORE,
                if (i % 2 == 0) Pattern.CALF else Pattern.REAR,
            )
            slots += if (count <= 5) listOf(Pattern.CORE) else extra.take(count - 4)
            val used = mutableSetOf<String>()
            val ex = slots.mapNotNull { p ->
                pick(p, s.level, i, eq, noEq, used, gym, body)?.let { d ->
                    used += d.id
                    val r = rangeFor(d, s.goal)
                    PlanEx(d.id, sets, r.first, r.last)
                }
            }
            ProgramDay(name = "Тренировка ${letters[i % letters.size]}", weekday = wd, exercises = ex)
        }
        return Program(
            name = "${s.goal.title} · " + (if (s.split != Split.FULL) s.split.title.lowercase() + " · " else "") +
                "${if (gym) "зал" else if (noEq) "без инвентаря" else "дома"} · ${days.size}×в неделю · ${s.minutes} мин",
            goal = s.goal, restSec = s.goal.rest, noEquipment = noEq, days = programDays, place = s.place,
            notes = body.notes(s.goal),
        )
    }

    /** Упражнения, которые при большом весе тела делаем в облегчённом варианте. */
    private fun bodyweightHard(d: ExerciseDef) =
        d.pattern == Pattern.LUNGE || (d.load == LoadKind.NONE && d.pattern in setOf(Pattern.PUSH, Pattern.VPULL, Pattern.TRICEPS))

    fun pick(
        p: Pattern, level: Int, dayIdx: Int, eq: Set<Equipment>, noEq: Boolean, used: Set<String>,
        gym: Boolean = false, body: Body = Body(null, null),
    ): ExerciseDef? {
        val all = Library.all.filter { it.pattern == p && it.id !in used && it.available(eq, noEq) }
        if (all.isEmpty()) return null
        // при ИМТ ≥ 30 облегчённые варианты разрешены и уровень на ступень ниже
        val cands = if (body.heavy) all else all.filterNot { it.easy }.ifEmpty { all }
        fun lvl(d: ExerciseDef) = if (body.heavy && bodyweightHard(d)) d.level + 1 else d.level
        fun score(d: ExerciseDef) = abs(lvl(d) - level) + if (gym && !d.gymOnly && d.load in setOf(LoadKind.NONE, LoadKind.BACKPACK)) 2 else 0
        val best = cands.minOf { score(it) }
        val pool = cands.filter { score(it) <= if (best == 0) 1 else best }
            .sortedWith(compareBy({ score(it) }, { if (it.needs.isEmpty()) 1 else 0 }, { it.id }))
        return pool[dayIdx % pool.size]
    }
}

// ——— план конкретной тренировки (после всех поправок) ———

enum class Intensity(val title: String, val hint: String) {
    NORMAL("Обычная", "по программе, с прогрессией"),
    LIGHT("Облегчённая", "на подход меньше, повторы без прибавки, отдых длиннее"),
    RECOVERY("Восстановительная", "по одному лёгкому подходу — размяться и не перегрузиться"),
}

data class PlannedEx(
    val exId: String,
    val name: String,
    val sets: Int,
    val repMin: Int,
    val repMax: Int,
    val seconds: Boolean,
    val perSide: Boolean,
    val cue: String,
    val load: LoadKind,
    val note: String? = null,
) {
    val def: ExerciseDef? get() = Library.get(exId)
    val scheme: String
        get() = "$sets × " + (if (repMin == repMax) "$repMin" else "$repMin–$repMax") +
            (if (seconds) " с" else "") + (if (perSide) " на сторону" else "")

    fun toJson() = JSONObject().put("ex", exId).put("name", name).put("sets", sets).put("min", repMin).put("max", repMax)
        .put("sec", seconds).put("side", perSide).put("cue", cue).put("load", load.name).put("note", note ?: "")

    companion object {
        fun of(def: ExerciseDef, sets: Int, range: IntRange, note: String? = null) =
            PlannedEx(def.id, def.name, sets, range.first, range.last, def.seconds, def.perSide, def.cue, def.load, note)

        fun fromJson(o: JSONObject) = PlannedEx(
            o.getString("ex"), o.getString("name"), o.getInt("sets"), o.getInt("min"), o.getInt("max"),
            o.optBoolean("sec"), o.optBoolean("side"), o.optString("cue"),
            runCatching { LoadKind.valueOf(o.optString("load")) }.getOrDefault(LoadKind.NONE),
            o.optString("note").ifBlank { null },
        )
    }
}

data class WorkoutPlan(
    val dayId: String,
    val title: String,
    val restSec: Int,
    val intensity: Intensity,
    val noEquipment: Boolean,
    val exercises: List<PlannedEx>,
    val notes: List<String>,
    val place: Place = Place.HOME,
    val warmups: List<String> = emptyList(),
) {
    fun toJson() = JSONObject().put("day", dayId).put("title", title).put("rest", restSec).put("int", intensity.name)
        .put("noEq", noEquipment).put("ex", JSONArray(exercises.map { it.toJson() })).put("notes", JSONArray(notes))
        .put("place", place.name).put("warm", JSONArray(warmups))

    companion object {
        fun fromJson(o: JSONObject) = WorkoutPlan(
            o.getString("day"), o.getString("title"), o.getInt("rest"),
            runCatching { Intensity.valueOf(o.getString("int")) }.getOrDefault(Intensity.NORMAL),
            o.optBoolean("noEq"),
            o.getJSONArray("ex").let { a -> List(a.length()) { PlannedEx.fromJson(a.getJSONObject(it)) } },
            o.optJSONArray("notes")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList(),
            runCatching { Place.valueOf(o.optString("place")) }.getOrDefault(Place.HOME),
            o.optJSONArray("warm")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList(),
        )
    }
}

/** Стартовый вес для упражнения без истории — по весу тела. */
fun startLoad(def: ExerciseDef?, body: Body): String? {
    val pct = def?.startPct ?: return null
    val w = body.weightKg ?: return null
    val kg = (w * pct / 2.5).roundToInt() * 2.5
    return "≈ ${if (kg % 1.0 == 0.0) kg.toInt().toString() else kg.toString().replace('.', ',')} кг"
}

object Planner {
    /**
     * Собирает тренировку дня: место (дом/зал), замены по инвентарю, интенсивность,
     * поправки по весу тела. [legsLight] — накануне было много ходьбы: ногам на подход меньше.
     */
    fun build(
        p: Program, day: ProgramDay, homeEq: Set<Equipment>, gymEq: Set<Equipment>, place: Place, noEquipment: Boolean,
        intensity: Intensity, legsLight: Boolean, restOverride: Int, body: Body = Body(null, null),
        warmups: Set<String> = Library.defaultWarmups,
        /** Разгрузочная неделя (1.3): до этой даты подходов на 40 % меньше. */
        deloadUntil: String? = null,
        /** 5.55: прибавка подходов по упражнениям (SetProgression). */
        extra: Map<String, Int> = emptyMap(),
    ): WorkoutPlan {
        val notes = mutableListOf<String>()
        val used = mutableSetOf<String>()
        val gym = place == Place.GYM
        val eq = if (gym) gymEq else homeEq
        val noEq = !gym && (noEquipment || p.noEquipment)
        val dayIds = day.exercises.map { it.exId }.toSet()
        val list = day.exercises.mapNotNull { pe ->
            val orig = pe.def ?: return@mapNotNull null
            var def = orig
            var note: String? = null
            if (gym && !orig.gymOnly && orig.load in setOf(LoadKind.NONE, LoadKind.BACKPACK)) {
                // в зале — вариант с весом из того же паттерна
                Library.all.filter { it.pattern == orig.pattern && it.gymOnly && it.id !in used && it.id !in dayIds && it.available(eq, false) }
                    .minByOrNull { abs(it.level - orig.level) }
                    ?.let { def = it; note = "в зале вместо «${orig.name}»" }
            }
            if (!def.available(eq, noEq)) {
                val sub = Library.substitute(orig, eq, noEq, used + dayIds - orig.id) ?: Library.substitute(orig, eq, noEq, used)
                if (sub == null) { notes += "«${orig.name}» пропущено — нет подходящего инвентаря"; return@mapNotNull null }
                def = sub
                note = "вместо «${orig.name}»"
                if (!gym) notes += "«${orig.name}» → «${sub.name}»" + orig.needs.filter { noEq || it !in Equipment.effective(eq) }
                    .takeIf { it.isNotEmpty() }?.let { n -> " (нет: ${n.joinToString { it.title.lowercase() }})" }.orEmpty()
            }
            used += def.id
            val range = if (def.id == orig.id) pe.repMin..pe.repMax else rangeFor(def, p.goal)
            var sets = (pe.sets + (extra[def.id] ?: extra[orig.id] ?: 0)).coerceAtMost(maxOf(pe.sets, SetProgression.MAX_SETS))
            when (intensity) {
                Intensity.NORMAL -> if (legsLight && def.pattern.legs && sets > 1) sets--
                Intensity.LIGHT -> if (sets > 1) sets--
                Intensity.RECOVERY -> sets = 1
            }
            if (deloadUntil != null && sets > 1) sets = maxOf(1, Math.round(sets * 0.6).toInt())
            PlannedEx.of(def, sets, range, note)
        }
        if (gym) list.count { it.note?.startsWith("в зале") == true }.takeIf { it > 0 }?.let {
            notes += "В зале $it упр. заменено вариантами со штангой, гантелями или тренажёрами"
        }
        if (deloadUntil != null) notes += "Разгрузочная неделя до ${prettyDate(deloadUntil)}: подходов на 40 % меньше, веса те же"
        if (legsLight && intensity == Intensity.NORMAL && list.any { it.def?.pattern?.legs == true }) {
            notes += "Накануне много шагов — упражнениям на ноги на подход меньше"
        }
        val warm = Library.warmups.filter { w ->
            w.id in warmups && (w.needs == null || (gym && w.needs in eq)) && !(w.impact && body.heavy)
        }.map { it.id }
        if (body.heavy && "wu_jumps" in warmups) notes += "Прыжки в разминке выключены — меньше ударной нагрузки на колени"
        val baseRest = if (restOverride > 0) restOverride else p.restSec
        val rest = if (intensity == Intensity.LIGHT) baseRest + 30 else baseRest
        return WorkoutPlan(day.id, day.name, rest, intensity, noEq, list, notes, place, warm)
    }
}
