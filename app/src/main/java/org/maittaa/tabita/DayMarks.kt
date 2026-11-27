package org.maittaa.tabita

import java.time.LocalDate

/**
 * 5.59: отметки самочувствия — готовый набор галочек в группе «Отметки самочувствия» на «Сегодня».
 * Симптомы идут в «Индекс дня» со знаком «минус», хорошее — бонусом; «Что влияет» ищет связи со сном и нагрузкой.
 * Совет на тренировку — по «правилу шеи» Mayo Clinic: симптомы выше шеи (насморк, заложенность) — можно,
 * но легче; ниже шеи (кашель, грудь, живот), температура, ломота и сильная усталость — без тренировки.
 */
object DayMarks {
    /** 5.59.1: отметки самочувствия — в общей плитке «Отметки дня», разделом «Самочувствие». */
    const val GROUP = "checks"
    const val OLD_GROUP = "g_feel"
    val SECTION get() = if (I18n.isEn) "Wellbeing" else "Самочувствие"

    /** Что отметка значит для тренировки: ничего, «лучше легче», «лучше отдых». */
    enum class Effect { NONE, EASY, REST }

    data class Mark(val id: String, val ru: String, val en: String, val good: Boolean, val effect: Effect) {
        val name get() = if (I18n.isEn) en else ru
    }

    val all = listOf(
        Mark("fm_runny", "Насморк", "Runny nose", false, Effect.EASY),
        Mark("fm_cough", "Кашель", "Cough", false, Effect.REST),
        Mark("fm_fever", "Температура", "Fever", false, Effect.REST),
        Mark("fm_aches", "Ломота в теле", "Body aches", false, Effect.REST),
        Mark("fm_stomach", "Живот", "Upset stomach", false, Effect.REST),
        Mark("fm_head", "Болит голова", "Headache", false, Effect.EASY),
        Mark("fm_back", "Болит спина", "Back pain", false, Effect.EASY),
        Mark("fm_sore", "Крепатура", "Sore muscles", false, Effect.EASY),
        Mark("fm_tired", "Усталость", "Tired", false, Effect.EASY),
        Mark("fm_stress", "Стресс", "Stress", false, Effect.NONE),
        Mark("fm_energy", "Бодрость", "Energetic", true, Effect.NONE),
        Mark("fm_mood", "Хорошее настроение", "Good mood", true, Effect.NONE),
    )
    fun of(id: String) = all.firstOrNull { it.id == id }

    /** 5.59.1: из отдельной плитки «Отметки самочувствия» (5.59) — в «Отметки дня», раздел «Самочувствие». */
    fun merge(repo: Repo, prefs: Prefs? = null) {
        // 5.59.2: однократно — отметкам набора проставить влияние на совет (раньше оно было зашито по id)
        if (prefs != null && !prefs.marksTrainSet) {
            repo.template.value.forEach { it -> of(it.id)?.takeIf { m -> m.effect != Effect.NONE && it.train.isEmpty() }?.let { m -> repo.upsertItem(it.copy(train = trainOf(m.effect))) } }
            prefs.marksTrainSet = true
        }
        val old = repo.template.value.filter { it.group == OLD_GROUP }
        if (old.isEmpty() && repo.groups.value.none { it.id == OLD_GROUP }) return
        repo.ensureGroup(GROUP, if (I18n.isEn) "Day checks" else "Отметки дня", GroupKind.CHECKS)
        old.forEach { repo.upsertItem(it.copy(group = GROUP, section = it.section.ifBlank { SECTION })) }
        if (repo.canDeleteGroup(OLD_GROUP)) repo.deleteGroup(OLD_GROUP)
        Log.i("Marks", "отметки самочувствия перенесены в «Отметки дня»: ${old.size}")
    }

    fun installed(tmpl: List<TemplateItem>) = all.filter { m -> tmpl.any { it.id == m.id } }
    fun isInstalled(tmpl: List<TemplateItem>) = installed(tmpl).isNotEmpty()

    /**
     * Отмеченные в день и влияющие на совет: любая видимая отметка с «Совет на тренировку» = легче/отдых
     * (5.59.2 — своя тоже), плюс «Бодрость» и «Хорошее настроение» из набора — как хорошее.
     */
    fun marked(repo: Repo, date: LocalDate): List<Mark> {
        val day = repo.entries.value[date.key()] ?: return emptyList()
        return repo.template.value.filter { it.type == ItemType.CHECK && !it.hidden && (parseNumber(day[it.id]) ?: 0.0) >= 1 }.mapNotNull { it ->
            val eff = when (it.train) { "easy" -> Effect.EASY; "rest" -> Effect.REST; else -> Effect.NONE }
            val base = of(it.id)
            when {
                eff != Effect.NONE -> Mark(it.id, it.displayName, it.displayName, false, eff)
                base?.good == true -> Mark(it.id, it.displayName, it.displayName, true, Effect.NONE)
                else -> null
            }
        }
    }

    fun trainOf(e: Effect) = when (e) { Effect.EASY -> "easy"; Effect.REST -> "rest"; Effect.NONE -> "" }

    /** Самая строгая отметка дня. */
    fun effect(marks: List<Mark>): Effect = marks.maxOfOrNull { it.effect } ?: Effect.NONE

    /**
     * Добавить набор: группа «Самочувствие» (одна плитка), галочки, которых ещё нет,
     * и участие в индексе дня — симптомы с минусом, хорошее — бонусом.
     */
    fun install(repo: Repo, prefs: Prefs) {
        repo.ensureGroup(GROUP, if (I18n.isEn) "Day checks" else "Отметки дня", GroupKind.CHECKS)
        val tmpl = repo.template.value
        all.forEach { m ->
            val cur = tmpl.firstOrNull { it.id == m.id }
            if (cur == null) repo.upsertItem(TemplateItem(id = m.id, name = m.name, type = ItemType.CHECK, group = GROUP, section = SECTION, train = trainOf(m.effect)))
            else if (cur.hidden) repo.upsertItem(cur.copy(hidden = false))
        }
        var c = prefs.monthCfg
        all.forEach { m -> c = c.withEffect(m.id, plus = m.good, all = MonthData(repo.template.value, repo.entries.value, LocalDate.now(), c).indexKeys) }
        prefs.monthCfg = c
        Log.i("Marks", "набор самочувствия добавлен, в индексе ${all.size} отметок")
    }
}
