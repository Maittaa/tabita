package org.maittaa.tabita.train

enum class Place(val title: String) { HOME("Дома"), GYM("В зале") }

/** Инвентарь: [home] — можно отметить дома, [gym] — бывает в зале. */
enum class Equipment(val title: String, val hint: String, val home: Boolean = true, val gym: Boolean = false) {
    DUMBBELLS("Гантели", "дома — свои, в зале — гантельный ряд", gym = true),
    BACKPACK("Рюкзак с книгами", "вес меняешь количеством книг или бутылок"),
    CHAIR("Устойчивый стул", "сплит-приседания, зашагивания, обратные отжимания, тяга одной рукой"),
    SOFA("Диван или кровать", "ягодичный мост с опорой"),
    STEP("Ступенька или порог", "подъёмы на носки с полной амплитудой", gym = true),
    TABLE("Прочный стол", "тяга лёжа под столом"),
    TOWEL("Полотенце и дверь", "тяга полотенцем за ручки двери"),
    BAR("Турник", "подтягивания, подъёмы ног в висе", gym = true),
    BANDS("Резиновые эспандеры", "тяги и разведения", gym = true),
    BARBELL("Штанга и стойка", "приседания, жимы, тяги со штангой", home = false, gym = true),
    BENCH("Скамья", "жимы лёжа, тяга с опорой; заменяет стул и диван", home = false, gym = true),
    LEG_PRESS("Жим ногами", "тренажёр", home = false, gym = true),
    LEG_MACHINES("Тренажёры для ног", "сгибание и разгибание ног", home = false, gym = true),
    CABLES("Блоки", "верхний и нижний блок, кроссовер", home = false, gym = true),
    HYPER("Гиперэкстензия", "тренажёр для спины", home = false, gym = true),
    DIP("Брусья", "отжимания на брусьях", home = false, gym = true),
    CARDIO("Кардиотренажёр", "дорожка или велосипед для разминки", home = false, gym = true);

    companion object {
        val homeList get() = entries.filter { it.home }
        val gymList get() = entries.filter { it.gym }

        /** Что фактически доступно: скамья в зале заменяет стул и диван. */
        fun effective(set: Set<Equipment>): Set<Equipment> =
            if (BENCH in set) set + CHAIR + SOFA else set
    }
}

/** Двигательный паттерн — по нему генератор раскладывает тренировку и подбирает замены. */
enum class Pattern(val title: String, val legs: Boolean = false) {
    SQUAT("Приседания", true),
    LUNGE("Выпады и одна нога", true),
    HINGE("Наклоны (задняя поверхность)", true),
    GLUTE("Ягодицы", true),
    PUSH("Отжимания (грудь)"),
    VPUSH("Жимы над головой (плечи)"),
    ROW("Тяги (спина)"),
    VPULL("Подтягивания"),
    SHOULDER("Средняя дельта"),
    REAR("Задняя дельта и осанка"),
    BICEPS("Бицепс"),
    TRICEPS("Трицепс"),
    CORE("Пресс и корпус"),
    CALF("Икры", true),
}

enum class LoadKind { NONE, BACKPACK, DUMBBELLS, BARBELL, MACHINE }

data class ExerciseDef(
    val id: String,
    val name: String,
    val pattern: Pattern,
    val needs: Set<Equipment> = emptySet(),
    /** 1 — самый простой вариант, 4 — самый сложный. */
    val level: Int = 1,
    val seconds: Boolean = false,
    val perSide: Boolean = false,
    val load: LoadKind = LoadKind.NONE,
    /** Короткая подсказка на экране подхода. */
    val cue: String = "",
    /** Свой диапазон повторов для тяжёлых упражнений (подтягивания). */
    val reps: IntRange? = null,
    /** Облегчённый вариант: генератор его не ставит, он нужен для замен и ручного выбора. */
    val easy: Boolean = false,
    /** Стартовый рабочий вес, доля от веса тела (для штанги и тренажёров, на всю нагрузку). */
    val startPct: Double? = null,
) {
    /** Упражнение только для зала. */
    val gymOnly: Boolean get() = needs.any { !it.home }
    val info: ExerciseInfo? get() = Guide.find(name)
    fun available(eq: Set<Equipment>, noEquipment: Boolean) = if (noEquipment) needs.isEmpty() else Equipment.effective(eq).containsAll(needs)
    val needsLabel: String get() = if (needs.isEmpty()) "без инвентаря" else needs.joinToString(", ") { it.title.lowercase() }
}

object Library {
    private fun e(vararg x: Equipment) = x.toSet()

    val all: List<ExerciseDef> = listOf(
        // приседания
        ExerciseDef("squat_chair", "Приседания на стул", Pattern.SQUAT, e(Equipment.CHAIR), 1, cue = "Коснулся стула — встал, не садись полностью.", easy = true),
        ExerciseDef("squat", "Приседания", Pattern.SQUAT, level = 1, cue = "Таз назад, как на стул; пятки на полу; спина прямая."),
        ExerciseDef("wall_sit", "Стульчик у стены", Pattern.SQUAT, level = 1, seconds = true, cue = "Спина к стене, бёдра почти параллельно полу.", easy = true),
        ExerciseDef("goblet_squat", "Приседания с рюкзаком у груди", Pattern.SQUAT, e(Equipment.BACKPACK), 2, load = LoadKind.BACKPACK, cue = "Рюкзак у груди, локти вниз, грудь раскрыта."),
        ExerciseDef("db_squat", "Приседания с гантелями", Pattern.SQUAT, e(Equipment.DUMBBELLS), 2, load = LoadKind.DUMBBELLS, cue = "Гантели по бокам, спина прямая."),
        // выпады
        ExerciseDef("reverse_lunge", "Выпады назад", Pattern.LUNGE, level = 2, perSide = true, cue = "Шаг назад, оба колена под прямым углом, корпус прямо."),
        ExerciseDef("db_lunge", "Выпады назад с гантелями", Pattern.LUNGE, e(Equipment.DUMBBELLS), 3, perSide = true, load = LoadKind.DUMBBELLS, cue = "Гантели в руках, шаг назад, корпус прямо."),
        ExerciseDef("step_up", "Зашагивания на стул", Pattern.LUNGE, e(Equipment.CHAIR), 2, perSide = true, cue = "Толкайся ногой на стуле, нижней не помогай."),
        ExerciseDef("split_squat", "Сплит-приседания", Pattern.LUNGE, e(Equipment.CHAIR), 3, perSide = true, load = LoadKind.BACKPACK, cue = "Задняя нога на стуле, опускаешься прямо вниз, держишься за стену."),
        // наклоны
        ExerciseDef("superman", "Лодочка", Pattern.HINGE, level = 1, cue = "Лёжа на животе: руки, грудь и ноги вверх, пауза 1–2 с."),
        ExerciseDef("sl_rdl", "Румынская тяга на одной ноге", Pattern.HINGE, level = 2, perSide = true, cue = "«Ласточка», рука на стене, спина ровная."),
        ExerciseDef("bp_rdl", "Румынская тяга с рюкзаком", Pattern.HINGE, e(Equipment.BACKPACK), 2, load = LoadKind.BACKPACK, cue = "Таз назад, спина ровная, колени почти не сгибаются."),
        ExerciseDef("db_rdl", "Румынская тяга с гантелями", Pattern.HINGE, e(Equipment.DUMBBELLS), 2, load = LoadKind.DUMBBELLS, cue = "Гантели скользят вдоль ног, спина ровная."),
        // ягодицы
        ExerciseDef("glute_bridge", "Ягодичный мост", Pattern.GLUTE, level = 1, cue = "Лёжа, наверху сжать ягодицы на 1 с."),
        ExerciseDef("sl_bridge", "Ягодичный мост на одной ноге", Pattern.GLUTE, level = 2, perSide = true, cue = "Толкайся пяткой одной ноги, таз ровно."),
        ExerciseDef("hip_thrust", "Ягодичный мост с опорой на диван", Pattern.GLUTE, e(Equipment.SOFA), 3, load = LoadKind.BACKPACK, cue = "Лопатки на диване, рюкзак на тазу."),
        // отжимания
        ExerciseDef("knee_pushup", "Отжимания с колен", Pattern.PUSH, level = 1, cue = "Тело от коленей до головы прямое, локти назад.", easy = true),
        ExerciseDef("pushup", "Отжимания", Pattern.PUSH, level = 2, cue = "Тело — доска, локти назад под углом."),
        ExerciseDef("decline_pushup", "Отжимания с ногами на стуле", Pattern.PUSH, e(Equipment.CHAIR), 3, cue = "Стопы на стуле, тело прямое."),
        // жимы
        ExerciseDef("db_press", "Жим гантелей сидя", Pattern.VPUSH, e(Equipment.DUMBBELLS), 1, load = LoadKind.DUMBBELLS, cue = "От плеч вверх, поясница не прогибается."),
        ExerciseDef("bp_press", "Жим рюкзака над головой", Pattern.VPUSH, e(Equipment.BACKPACK), 2, load = LoadKind.BACKPACK, cue = "Рюкзак от груди вверх, пресс напряжён."),
        ExerciseDef("pike_pushup", "Отжимания пайк", Pattern.VPUSH, level = 2, cue = "Таз вверх «уголком», голова к полу между ладоней."),
        // тяги
        ExerciseDef("prone_pull", "Лодочка с тягой локтей", Pattern.ROW, level = 1, cue = "Лёжа на животе, тяни локти к рёбрам, сведи лопатки."),
        ExerciseDef("towel_row", "Тяга полотенца у двери", Pattern.ROW, e(Equipment.TOWEL), 1, cue = "Полотенце за ручки, отклонись и подтяни себя к двери."),
        ExerciseDef("band_row", "Тяга резины к поясу", Pattern.ROW, e(Equipment.BANDS), 1, cue = "Локти вдоль тела, сведи лопатки."),
        ExerciseDef("bp_row", "Тяга рюкзака в наклоне", Pattern.ROW, e(Equipment.BACKPACK), 1, load = LoadKind.BACKPACK, cue = "Спина ровная, тянешь к животу, сводишь лопатки."),
        ExerciseDef("bp_row1", "Тяга рюкзака одной рукой", Pattern.ROW, e(Equipment.BACKPACK, Equipment.CHAIR), 1, perSide = true, load = LoadKind.BACKPACK, cue = "Опора на стул, локоть скользит назад вдоль тела."),
        ExerciseDef("db_row", "Тяга гантели одной рукой", Pattern.ROW, e(Equipment.DUMBBELLS, Equipment.CHAIR), 1, perSide = true, load = LoadKind.DUMBBELLS, cue = "Опора на стул, локоть к поясу."),
        ExerciseDef("pullover", "Пуловер с рюкзаком", Pattern.ROW, e(Equipment.BACKPACK), 2, load = LoadKind.BACKPACK, cue = "Лёжа, рюкзак за голову и обратно, поясница прижата."),
        ExerciseDef("table_row", "Тяга лёжа под столом", Pattern.ROW, e(Equipment.TABLE), 2, cue = "Тело прямое, грудь к краю стола."),
        // подтягивания
        ExerciseDef("neg_pullup", "Негативные подтягивания", Pattern.VPULL, e(Equipment.BAR, Equipment.CHAIR), 2, cue = "Со стула наверх, опускайся 3–5 с.", reps = 3..6),
        ExerciseDef("pullup", "Подтягивания", Pattern.VPULL, e(Equipment.BAR), 4, cue = "Лопатки вниз, грудь к перекладине.", reps = 3..8),
        // плечи
        ExerciseDef("db_lateral", "Махи гантелями в стороны", Pattern.SHOULDER, e(Equipment.DUMBBELLS), 1, load = LoadKind.DUMBBELLS, cue = "Руки как крылья, не выше плеч."),
        ExerciseDef("band_lateral", "Разведения резины в стороны", Pattern.SHOULDER, e(Equipment.BANDS), 1, cue = "Встань на резину, руки до уровня плеч."),
        ExerciseDef("ytw", "Y-T-W лёжа", Pattern.REAR, level = 1, cue = "Лёжа на животе: Y, T, W — медленно, с паузой."),
        ExerciseDef("db_rear", "Махи в наклоне", Pattern.REAR, e(Equipment.DUMBBELLS), 1, load = LoadKind.DUMBBELLS, cue = "Сидя, грудь к бёдрам, руки в стороны, свести лопатки."),
        ExerciseDef("band_pullapart", "Разведения резины перед грудью", Pattern.REAR, e(Equipment.BANDS), 1, cue = "Прямые руки, резина к груди."),
        // руки
        ExerciseDef("db_curl", "Сгибания на бицепс", Pattern.BICEPS, e(Equipment.DUMBBELLS), 1, load = LoadKind.DUMBBELLS, cue = "Локти прижаты, корпус не качается."),
        ExerciseDef("hammer", "Молотковые сгибания", Pattern.BICEPS, e(Equipment.DUMBBELLS), 1, load = LoadKind.DUMBBELLS, cue = "Ладони смотрят друг на друга."),
        ExerciseDef("bp_curl", "Сгибания с рюкзаком", Pattern.BICEPS, e(Equipment.BACKPACK), 1, load = LoadKind.BACKPACK, cue = "Рюкзак за ручку, локти прижаты."),
        ExerciseDef("narrow_pushup", "Узкие отжимания", Pattern.TRICEPS, level = 2, cue = "Ладони на ширине плеч, локти вдоль тела. Можно с колен."),
        ExerciseDef("chair_dip", "Обратные отжимания от стула", Pattern.TRICEPS, e(Equipment.CHAIR), 2, cue = "Стул у стены, локти назад, неглубоко."),
        ExerciseDef("db_ext", "Французский жим гантели", Pattern.TRICEPS, e(Equipment.DUMBBELLS), 1, load = LoadKind.DUMBBELLS, cue = "Гантель за голову, локти смотрят вперёд."),
        // корпус
        ExerciseDef("plank", "Планка", Pattern.CORE, level = 1, seconds = true, cue = "На предплечьях, тело прямое, живот напряжён."),
        ExerciseDef("dead_bug", "Мёртвый жук", Pattern.CORE, level = 1, cue = "Поясница прижата, противоположные рука и нога вниз."),
        ExerciseDef("leg_raise", "Подъёмы ног лёжа", Pattern.CORE, level = 2, cue = "Колени согнуты, поясница прижата к полу."),
        ExerciseDef("side_plank", "Боковая планка", Pattern.CORE, level = 2, seconds = true, perSide = true, cue = "Локоть под плечом, таз не провисает."),
        // икры
        ExerciseDef("calf_floor", "Подъёмы на носки на полу", Pattern.CALF, level = 1, cue = "Держись за стену, пауза наверху."),
        ExerciseDef("calf_raise", "Подъёмы на носки", Pattern.CALF, e(Equipment.STEP), 1, cue = "На ступеньке, на двух ногах, пауза наверху."),
        ExerciseDef("sl_calf", "Подъёмы на носки на одной ноге", Pattern.CALF, e(Equipment.STEP), 2, perSide = true, cue = "На ступеньке, пятка ниже края."),
        // ——— зал ———
        ExerciseDef("leg_press", "Жим ногами", Pattern.SQUAT, e(Equipment.LEG_PRESS), 1, load = LoadKind.MACHINE, cue = "Поясница прижата, колени не сводить, не разгибать до щелчка.", startPct = 0.9),
        ExerciseDef("bb_squat", "Приседания со штангой", Pattern.SQUAT, e(Equipment.BARBELL), 2, load = LoadKind.BARBELL, cue = "Штанга на трапециях, таз назад, колени по носкам.", startPct = 0.45),
        ExerciseDef("leg_ext", "Разгибание ног в тренажёре", Pattern.LUNGE, e(Equipment.LEG_MACHINES), 1, load = LoadKind.MACHINE, cue = "Вверху пауза 1 с, вниз медленно.", startPct = 0.3),
        ExerciseDef("db_split_squat", "Болгарские выпады с гантелями", Pattern.LUNGE, e(Equipment.DUMBBELLS, Equipment.BENCH), 3, perSide = true, load = LoadKind.DUMBBELLS, cue = "Задняя нога на скамье, гантели в руках, вниз прямо."),
        ExerciseDef("bb_rdl", "Румынская тяга со штангой", Pattern.HINGE, e(Equipment.BARBELL), 2, load = LoadKind.BARBELL, cue = "Штанга скользит по бёдрам, спина ровная.", startPct = 0.5),
        ExerciseDef("leg_curl", "Сгибание ног в тренажёре", Pattern.HINGE, e(Equipment.LEG_MACHINES), 1, load = LoadKind.MACHINE, cue = "Таз прижат, вниз медленно.", startPct = 0.25),
        ExerciseDef("hyperext", "Гиперэкстензия", Pattern.HINGE, e(Equipment.HYPER), 1, cue = "Наклон от таза, вверх до прямой линии, не прогибаться."),
        ExerciseDef("bb_hip_thrust", "Ягодичный мост со штангой", Pattern.GLUTE, e(Equipment.BARBELL, Equipment.BENCH), 2, load = LoadKind.BARBELL, cue = "Лопатки на скамье, штанга на тазу через накладку.", startPct = 0.5),
        ExerciseDef("db_bench", "Жим гантелей лёжа", Pattern.PUSH, e(Equipment.DUMBBELLS, Equipment.BENCH), 1, load = LoadKind.DUMBBELLS, cue = "Лопатки сведены, локти под 45°, гантели к груди."),
        ExerciseDef("bench_press", "Жим штанги лёжа", Pattern.PUSH, e(Equipment.BARBELL, Equipment.BENCH), 2, load = LoadKind.BARBELL, cue = "Лопатки сведены, штанга к низу груди, стопы в пол.", startPct = 0.4),
        ExerciseDef("incline_db", "Жим гантелей на наклонной", Pattern.PUSH, e(Equipment.DUMBBELLS, Equipment.BENCH), 2, load = LoadKind.DUMBBELLS, cue = "Скамья 30°, гантели к верху груди."),
        ExerciseDef("ohp", "Жим штанги стоя", Pattern.VPUSH, e(Equipment.BARBELL), 3, load = LoadKind.BARBELL, cue = "Ягодицы и пресс напряжены, штанга по прямой над головой.", startPct = 0.3),
        ExerciseDef("db_press_gym", "Жим гантелей сидя на скамье", Pattern.VPUSH, e(Equipment.DUMBBELLS, Equipment.BENCH), 1, load = LoadKind.DUMBBELLS, cue = "Спинка 80–90°, от плеч вверх."),
        ExerciseDef("lat_pulldown", "Тяга верхнего блока", Pattern.VPULL, e(Equipment.CABLES), 1, load = LoadKind.MACHINE, cue = "Грудь вперёд, тяни к верху груди локтями вниз.", startPct = 0.45),
        ExerciseDef("seated_row", "Тяга нижнего блока", Pattern.ROW, e(Equipment.CABLES), 1, load = LoadKind.MACHINE, cue = "Спина ровная, тяни к поясу, сведи лопатки.", startPct = 0.4),
        ExerciseDef("bb_row", "Тяга штанги в наклоне", Pattern.ROW, e(Equipment.BARBELL), 3, load = LoadKind.BARBELL, cue = "Наклон 45°, штанга к поясу, спина ровная.", startPct = 0.4),
        ExerciseDef("chest_row", "Тяга гантелей лёжа на наклонной", Pattern.ROW, e(Equipment.DUMBBELLS, Equipment.BENCH), 1, load = LoadKind.DUMBBELLS, cue = "Грудью на скамье, локти к поясу."),
        ExerciseDef("face_pull", "Тяга каната к лицу", Pattern.REAR, e(Equipment.CABLES), 1, load = LoadKind.MACHINE, cue = "Канат к глазам, локти в стороны и вверх."),
        ExerciseDef("cable_lateral", "Махи в кроссовере", Pattern.SHOULDER, e(Equipment.CABLES), 2, perSide = true, load = LoadKind.MACHINE, cue = "Рука в сторону до уровня плеча, медленно вниз."),
        ExerciseDef("bb_curl", "Подъём штанги на бицепс", Pattern.BICEPS, e(Equipment.BARBELL), 1, load = LoadKind.BARBELL, cue = "Локти прижаты, без раскачки.", startPct = 0.25),
        ExerciseDef("pushdown", "Разгибания на блоке", Pattern.TRICEPS, e(Equipment.CABLES), 1, load = LoadKind.MACHINE, cue = "Локти прижаты, разгибай до конца.", startPct = 0.25),
        ExerciseDef("dips", "Отжимания на брусьях", Pattern.TRICEPS, e(Equipment.DIP), 3, cue = "Корпус чуть вперёд, вниз до 90° в локтях.", reps = 5..12),
        ExerciseDef("hanging_knee", "Подъёмы коленей в висе", Pattern.CORE, e(Equipment.BAR), 2, cue = "Без раскачки, колени к груди, таз подкручиваешь."),
        ExerciseDef("calf_press", "Подъёмы на носки в жиме ногами", Pattern.CALF, e(Equipment.LEG_PRESS), 1, load = LoadKind.MACHINE, cue = "Носки на краю платформы, полная амплитуда.", startPct = 0.7),
    )

    private val byId = all.associateBy { it.id }
    fun get(id: String): ExerciseDef? = byId[id]

    /** Разминка: отдельные упражнения, включаются на экране подготовки. */
    val warmups: List<WarmupDef> = listOf(
        WarmupDef("wu_cardio", "Кардио на тренажёре", 300, true, "Дорожка или велосипед в лёгком темпе, 5 минут.", needs = Equipment.CARDIO),
        WarmupDef("wu_jumps", "Прыжки на месте", 45, true, "Лёгкие прыжки или ходьба с высоким подниманием коленей.", impact = true),
        WarmupDef("wu_arms", "Вращения руками и плечами", 40, true, "10 кругов вперёд, 10 назад, потом плечами."),
        WarmupDef("wu_bends", "Наклоны в стороны и вперёд", 10, false, "Плавно, без пружинистых движений."),
        WarmupDef("wu_hips", "Вращения тазом", 10, false, "Руки на поясе, круги в каждую сторону."),
        WarmupDef("wu_catcow", "Кошка-корова", 8, false, "На четвереньках: прогиб — выгиб спины."),
        WarmupDef("wu_squats", "10 лёгких приседаний", 10, false, "Первые неглубоко, потом глубже."),
    )
    fun warmup(id: String) = warmups.firstOrNull { it.id == id }
    val defaultWarmups = setOf("wu_jumps", "wu_arms", "wu_bends", "wu_squats")

    /** Лучшая замена упражнению из того же паттерна, доступная с этим инвентарём. */
    fun substitute(ex: ExerciseDef, eq: Set<Equipment>, noEquipment: Boolean, exclude: Set<String> = emptySet()): ExerciseDef? =
        all.filter { it.pattern == ex.pattern && it.id !in exclude && it.available(eq, noEquipment) }
            .sortedWith(compareBy({ kotlin.math.abs(it.level - ex.level) }, { it.seconds != ex.seconds }, { it.perSide != ex.perSide }))
            .firstOrNull()
}

data class WarmupDef(
    val id: String,
    val name: String,
    /** Секунды или повторы. */
    val amount: Int,
    val seconds: Boolean,
    val cue: String,
    val needs: Equipment? = null,
    /** Ударная нагрузка на суставы (прыжки). */
    val impact: Boolean = false,
) {
    val amountLabel: String get() = if (seconds) (if (amount >= 60) "${amount / 60} мин" + (if (amount % 60 > 0) " ${amount % 60} с" else "") else "$amount с") else "$amount раз"
}
