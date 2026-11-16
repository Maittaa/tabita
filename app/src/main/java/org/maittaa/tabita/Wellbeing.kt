package org.maittaa.tabita

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/*
 * Чекап самочувствия: короткие скрининговые опросники, собираемые из блоков.
 * Это скрининг, а не диагноз. Тексты: официальные русские версии PHQ-9 (без 9-го вопроса), GAD-7,
 * WHO-5, личная шкала CBI; паника — вопросы панического модуля PHQ; PSS-4 и ISI — перевод.
 */

/** Текст на двух языках. */
data class Tx(val ru: String, val en: String) {
    val s: String get() = if (I18n.isEn) en else ru
    /** Текст с подстановкой периода чекапа: {p} — «последние 2 недели», {half} — «более половины дней». */
    fun s(period: Int): String = Wellbeing.period(s, period)
}

data class WAnswer(val text: Tx, val score: Int)

data class WQuestion(val text: Tx, val answers: List<WAnswer>, val reverse: Boolean = false)

/** Полоса результата: от [from] включительно; level 0 — норма … 3 — высокий. */
data class WBand(val from: Double, val name: Tx, val level: Int)

data class WBlock(
    val id: String,
    val title: Tx,
    val scale: String,
    val about: Tx,
    val prompt: Tx,
    val questions: List<WQuestion>,
    val max: Double,
    /** true — чем больше балл, тем хуже (кроме «Ресурса»). */
    val higherIsWorse: Boolean,
    val bands: List<WBand>,
    val defaultOn: Boolean = true,
    /** Порог уровня, с которого показываем мягкую подсказку обратиться к специалисту. */
    val adviseLevel: Int = 2,
    val approximate: Boolean = false,
    val score: (List<Int>) -> Double,
) {
    fun band(v: Double): WBand = bands.last { v >= it.from - 1e-9 }
    fun format(v: Double): String = if (max == 100.0) Math.round(v).toString() else formatNumber(Math.round(v * 10) / 10.0)
    /** Доля 0..1 «плохого» направления для полоски. */
    fun frac(v: Double): Float = (v / max).toFloat().coerceIn(0f, 1f)
}

private fun a(vararg p: Pair<String, String>) = p.mapIndexed { i, (ru, en) -> WAnswer(Tx(ru, en), i) }
private fun q(ru: String, en: String, ans: List<WAnswer>, reverse: Boolean = false) = WQuestion(Tx(ru, en), ans, reverse)

object Wellbeing {
    private val phqAns = a("Ни разу" to "Not at all", "Несколько дней" to "Several days",
        "{half}" to "More than half the days", "Почти каждый день" to "Nearly every day")
    private val gadAns = a("Ни разу" to "Not at all", "Несколько дней" to "Several days",
        "Более половины всех дней" to "More than half the days", "Почти каждый день" to "Nearly every day")
    private val yesNo = a("Нет" to "No", "Да" to "Yes")
    private val cbiAns = listOf(
        WAnswer(Tx("Всегда", "Always"), 100), WAnswer(Tx("Часто", "Often"), 75), WAnswer(Tx("Иногда", "Sometimes"), 50),
        WAnswer(Tx("Редко", "Seldom"), 25), WAnswer(Tx("Никогда или почти никогда", "Never or almost never"), 0),
    )
    private val whoAns = listOf(
        WAnswer(Tx("Всё время", "All of the time"), 5), WAnswer(Tx("Большую часть времени", "Most of the time"), 4),
        WAnswer(Tx("Более половины времени", "More than half of the time"), 3), WAnswer(Tx("Менее половины времени", "Less than half of the time"), 2),
        WAnswer(Tx("Иногда", "Some of the time"), 1), WAnswer(Tx("Ни разу", "At no time"), 0),
    )
    private val pssAns = a("Никогда" to "Never", "Почти никогда" to "Almost never", "Иногда" to "Sometimes",
        "Довольно часто" to "Fairly often", "Очень часто" to "Very often")
    private val isiSev = a("Нет" to "None", "Слабо" to "Mild", "Умеренно" to "Moderate", "Сильно" to "Severe", "Очень сильно" to "Very severe")
    private val isiSat = a("Очень доволен(-льна)" to "Very satisfied", "Доволен(-льна)" to "Satisfied", "Более-менее" to "Moderately satisfied",
        "Недоволен(-льна)" to "Dissatisfied", "Очень недоволен(-льна)" to "Very dissatisfied")
    private val isiMuch = a("Совсем нет" to "Not at all", "Немного" to "A little", "Отчасти" to "Somewhat", "Сильно" to "Much", "Очень сильно" to "Very much")

    private val twoWeeks = Tx("Как часто за {p} Вас беспокоили следующие проблемы?",
        "Over {p}, how often have you been bothered by the following problem?")

    /** Интервал чекапа в днях → фраза периода. Опросники рассчитаны на 2 недели (PSS — месяц), здесь — под ваш интервал. */
    fun periodPhrase(period: Int, en: Boolean = I18n.isEn): String = when {
        period <= 7 -> if (en) "the last week" else "последнюю неделю"
        period <= 14 -> if (en) "the last 2 weeks" else "последние 2 недели"
        else -> if (en) "the last month" else "последний месяц"
    }

    fun period(text: String, period: Int, en: Boolean = I18n.isEn): String = text
        .replace("{p}", periodPhrase(period, en))
        .replace("{half}", if (period in 8..14) "Более недели" else "Более половины дней")

    val blocks: List<WBlock> = listOf(
        WBlock(
            "phq8", Tx("Настроение", "Mood"), "PHQ-8", Tx("признаки депрессии", "signs of depression"), twoWeeks,
            listOf(
                q("Вам не хотелось ничего делать", "Little interest or pleasure in doing things", phqAns),
                q("У Вас было плохое настроение, Вы были подавлены или испытывали чувство безысходности", "Feeling down, depressed, or hopeless", phqAns),
                q("Вам было трудно заснуть, у Вас был прерывистый сон, или Вы слишком много спали", "Trouble falling or staying asleep, or sleeping too much", phqAns),
                q("Вы были утомлены, или у Вас было мало сил", "Feeling tired or having little energy", phqAns),
                q("У Вас был плохой аппетит, или Вы переедали", "Poor appetite or overeating", phqAns),
                q("Вы плохо о себе думали: считали себя неудачником (неудачницей), были в себе разочарованы или считали, что подвели свою семью",
                    "Feeling bad about yourself — or that you are a failure or have let yourself or your family down", phqAns),
                q("Вам было трудно сосредоточиться (например, на чтении или на просмотре фильма)",
                    "Trouble concentrating on things, such as reading or watching a film", phqAns),
                q("Вы двигались или говорили настолько медленно, что окружающие это замечали? Или, наоборот, были настолько суетливы, что двигались гораздо больше обычного",
                    "Moving or speaking so slowly that other people could have noticed? Or the opposite — being so fidgety or restless that you have been moving around a lot more than usual", phqAns),
            ),
            24.0, true,
            listOf(WBand(0.0, Tx("минимальные", "minimal"), 0), WBand(5.0, Tx("лёгкие", "mild"), 1), WBand(10.0, Tx("умеренные", "moderate"), 2),
                WBand(15.0, Tx("выраженные", "moderately severe"), 3), WBand(20.0, Tx("тяжёлые", "severe"), 3)),
        ) { it.sum().toDouble() },
        WBlock(
            "gad7", Tx("Тревога", "Anxiety"), "GAD-7", Tx("признаки тревоги", "signs of anxiety"), twoWeeks,
            listOf(
                q("Вы нервничали, тревожились или были очень напряжены", "Feeling nervous, anxious, or on edge", gadAns),
                q("Вы были неспособны успокоить или контролировать свои волнения", "Not being able to stop or control worrying", gadAns),
                q("Вы слишком сильно волновались по различным поводам", "Worrying too much about different things", gadAns),
                q("Вам было трудно расслабиться", "Trouble relaxing", gadAns),
                q("Вы были настолько взбудоражены, что Вам было тяжело сидеть спокойно", "Being so restless that it is hard to sit still", gadAns),
                q("Вы легко сердились или раздражались", "Becoming easily annoyed or irritable", gadAns),
                q("Вы испытывали страх, словно должно произойти нечто ужасное", "Feeling afraid, as if something awful might happen", gadAns),
            ),
            21.0, true,
            listOf(WBand(0.0, Tx("минимальные", "minimal"), 0), WBand(5.0, Tx("лёгкие", "mild"), 1),
                WBand(10.0, Tx("умеренные", "moderate"), 2), WBand(15.0, Tx("выраженные", "severe"), 3)),
        ) { it.sum().toDouble() },
        WBlock(
            "panic", Tx("Паника", "Panic"), "PHQ-PD", Tx("приступы паники", "panic attacks"),
            Tx("Вопросы о приступах тревоги за {p}", "Questions about anxiety attacks over {p}"),
            listOf(
                q("За {p} у Вас был приступ тревоги — внезапный страх или паника?",
                    "Over {p}, have you had an anxiety attack — suddenly feeling fear or panic?", yesNo),
                q("Такое случалось с Вами и раньше?", "Has this ever happened before?", yesNo),
                q("Бывает ли, что приступы начинаются внезапно, «на ровном месте» — когда Вы не ожидаете волнения или дискомфорта?",
                    "Do some of these attacks come suddenly out of the blue — that is, in situations where you don't expect to be nervous or uncomfortable?", yesNo),
                q("Эти приступы Вас сильно беспокоят, или Вы боитесь, что приступ повторится?",
                    "Do these attacks bother you a lot or are you worried about having another attack?", yesNo),
            ),
            4.0, true,
            listOf(WBand(0.0, Tx("нет признаков", "no signs"), 0), WBand(1.0, Tx("отдельные приступы", "occasional attacks"), 1),
                WBand(4.0, Tx("признаки панических атак", "signs of panic attacks"), 2)),
        ) { ans -> if (ans.firstOrNull() != 1) 0.0 else ans.sum().toDouble() },
        WBlock(
            "cbi", Tx("Выгорание", "Burnout"), "CBI", Tx("личное выгорание", "personal burnout"),
            Tx("Как обычно бывает в последнее время?", "How are things usually lately?"),
            listOf(
                q("Как часто Вы чувствуете усталость?", "How often do you feel tired?", cbiAns),
                q("Как часто Вы чувствуете физическое истощение?", "How often are you physically exhausted?", cbiAns),
                q("Как часто Вы чувствуете эмоциональное истощение?", "How often are you emotionally exhausted?", cbiAns),
                q("Как часто Вы думаете: «Я больше не могу это выносить!»?", "How often do you think: \"I can't take it anymore\"?", cbiAns),
                q("Как часто Вы чувствуете себя совершенно без сил?", "How often do you feel worn out?", cbiAns),
                q("Как часто Вы чувствуете слабость и уязвимость к болезням?", "How often do you feel weak and susceptible to illness?", cbiAns),
            ),
            100.0, true,
            listOf(WBand(0.0, Tx("низкое", "low"), 0), WBand(50.0, Tx("умеренное", "moderate"), 1),
                WBand(75.0, Tx("высокое", "high"), 2), WBand(100.0, Tx("очень высокое", "very high"), 3)),
        ) { it.average() },
        WBlock(
            "who5", Tx("Ресурс", "Wellbeing"), "WHO-5", Tx("общее благополучие", "overall well-being"),
            Tx("Как часто за {p} было так:", "Over {p}, how often:"),
            listOf(
                q("Я чувствовал(а) себя бодро, настроение было хорошим", "I have felt cheerful and in good spirits", whoAns),
                q("Я чувствовал(а) себя спокойно и расслабленно", "I have felt calm and relaxed", whoAns),
                q("Я чувствовал(а) себя активно и энергично", "I have felt active and vigorous", whoAns),
                q("Я просыпался(-лась) свежим(-ей) и отдохнувшим(-ей)", "I woke up feeling fresh and rested", whoAns),
                q("Моя повседневная жизнь была наполнена тем, что мне интересно", "My daily life has been filled with things that interest me", whoAns),
            ),
            100.0, false,
            listOf(WBand(0.0, Tx("низкое", "low"), 3), WBand(29.0, Tx("сниженное", "reduced"), 1), WBand(51.0, Tx("хорошее", "good"), 0)),
            adviseLevel = 3,
        ) { it.sum() * 4.0 },
        WBlock(
            "pss4", Tx("Стресс", "Stress"), "PSS-4", Tx("воспринимаемый стресс", "perceived stress"),
            Tx("Как часто за {p}:", "Over {p}, how often have you:"),
            listOf(
                q("Вам казалось, что Вы не можете контролировать важные вещи в своей жизни", "Felt that you were unable to control the important things in your life", pssAns),
                q("Вы были уверены, что справитесь со своими личными проблемами", "Felt confident about your ability to handle your personal problems", pssAns, reverse = true),
                q("Вам казалось, что всё идёт так, как Вы хотите", "Felt that things were going your way", pssAns, reverse = true),
                q("Вам казалось, что трудностей накопилось так много, что их не преодолеть", "Felt difficulties were piling up so high that you could not overcome them", pssAns),
            ),
            16.0, true,
            listOf(WBand(0.0, Tx("низкий", "low"), 0), WBand(5.0, Tx("средний", "medium"), 1),
                WBand(9.0, Tx("повышенный", "elevated"), 2), WBand(13.0, Tx("высокий", "high"), 3)),
            defaultOn = false, adviseLevel = 4, approximate = true,
        ) { it.sum().toDouble() },
        WBlock(
            "isi", Tx("Сон", "Sleep"), "ISI", Tx("признаки бессонницы", "signs of insomnia"),
            Tx("Оцените свой сон за {p}", "Rate your sleep over {p}"),
            listOf(
                q("Насколько Вам трудно заснуть?", "Difficulty falling asleep", isiSev),
                q("Насколько трудно спать, не просыпаясь ночью?", "Difficulty staying asleep", isiSev),
                q("Насколько мешает слишком раннее пробуждение?", "Problems waking up too early", isiSev),
                q("Насколько Вы довольны своим сном сейчас?", "How satisfied or dissatisfied are you with your current sleep pattern?", isiSat),
                q("Насколько заметно окружающим, что проблемы со сном ухудшают Вашу жизнь?", "How noticeable to others is your sleep problem in terms of impairing the quality of your life?", isiMuch),
                q("Насколько Вас тревожат нынешние проблемы со сном?", "How worried or distressed are you about your current sleep problem?", isiMuch),
                q("Насколько проблемы со сном мешают Вам днём (усталость, настроение, работа, внимание, память)?",
                    "To what extent does your sleep problem interfere with your daily functioning (fatigue, mood, work, concentration, memory)?", isiMuch),
            ),
            28.0, true,
            listOf(WBand(0.0, Tx("нет", "none"), 0), WBand(8.0, Tx("подпороговые", "subthreshold"), 1),
                WBand(15.0, Tx("умеренные", "moderate"), 2), WBand(22.0, Tx("тяжёлые", "severe"), 3)),
            defaultOn = false,
        ) { it.sum().toDouble() },
    )

    /**
     * Общая оценка самочувствия 0–100 (больше — лучше): каждая шкала переводится в «насколько хорошо»
     * (для шкал «чем больше, тем хуже» — 100 × (1 − балл / максимум)), затем среднее по пройденным блокам.
     */
    val total = WBlock(
        "total", Tx("Общая оценка", "Overall"), "0–100", Tx("среднее по всем шкалам", "average of all scales"),
        Tx("", ""), emptyList(), 100.0, false,
        listOf(WBand(0.0, Tx("низкая", "low"), 3), WBand(25.0, Tx("сниженная", "reduced"), 2),
            WBand(50.0, Tx("средняя", "fair"), 1), WBand(75.0, Tx("хорошая", "good"), 0)),
        adviseLevel = 3,
    ) { 0.0 }

    fun goodness(b: WBlock, v: Double): Double = (if (b.higherIsWorse) 100 * (1 - v / b.max) else 100 * v / b.max).coerceIn(0.0, 100.0)

    fun overall(results: Map<String, BlockResult>): Double? =
        results.mapNotNull { (id, r) -> block(id)?.let { goodness(it, r.score) } }.takeIf { it.isNotEmpty() }?.average()

    fun block(id: String) = if (id == "total") total else blocks.firstOrNull { it.id == id }
    val defaultSelection: Set<String> = blocks.filter { it.defaultOn }.map { it.id }.toSet()

    /** Балл блока по индексам выбранных ответов (обратные вопросы уже учтены в значениях). */
    fun score(b: WBlock, picks: List<Int>): Double = b.score(picks.mapIndexed { i, p ->
        val qq = b.questions[i]; val v = qq.answers[p].score
        if (qq.reverse) qq.answers.maxOf { it.score } - v else v
    })

    /** Дней до следующего чекапа: ≤ 0 — пора. Без прохождений — 0. */
    fun daysLeft(checkups: List<Checkup>, period: Int, today: LocalDate = LocalDate.now()): Int {
        val last = checkups.maxOfOrNull { it.date } ?: return 0
        return (period - ChronoUnit.DAYS.between(LocalDate.parse(last), today)).toInt()
    }

    /** Последний результат блока и предыдущий (для изменения). */
    fun lastTwo(checkups: List<Checkup>, blockId: String): Pair<Checkup?, Checkup?> {
        val l = checkups.filter { blockId in it.results }.sortedBy { it.at }
        return l.lastOrNull() to l.getOrNull(l.size - 2)
    }
}

/** Ответы и балл одного блока. */
data class BlockResult(val score: Double, val picks: List<Int>)

/** Одно прохождение чекапа. */
data class Checkup(val date: String, val at: Long, val results: Map<String, BlockResult>, val period: Int = 14) {
    val overall: Double? get() = Wellbeing.overall(results)

    fun toJson(): JSONObject = JSONObject().put("date", date).put("at", at).put("p", period).put("r", JSONObject().apply {
        results.forEach { (k, v) -> put(k, JSONObject().put("s", v.score).put("a", JSONArray(v.picks))) }
    })

    companion object {
        fun fromJson(o: JSONObject): Checkup {
            val r = o.getJSONObject("r")
            return Checkup(o.getString("date"), o.optLong("at"), r.keys().asSequence().associateWith { k ->
                val b = r.getJSONObject(k); val a = b.getJSONArray("a")
                BlockResult(b.getDouble("s"), (0 until a.length()).map { a.getInt(it) })
            }, o.optInt("p", 14))
        }
    }
}

/** Незаконченное прохождение: выбранные блоки и ответы (−1 — нет ответа), позиция. */
data class WDraft(val blocks: List<String>, val picks: List<Int>, val pos: Int) {
    val questions: List<Pair<WBlock, Int>> get() = blocks.mapNotNull { Wellbeing.block(it) }.flatMap { b -> b.questions.indices.map { b to it } }

    fun toJson(): String = JSONObject().put("b", JSONArray(blocks)).put("p", JSONArray(picks)).put("pos", pos).toString()

    companion object {
        fun start(blocks: List<String>): WDraft {
            val ordered = Wellbeing.blocks.map { it.id }.filter { it in blocks }
            val n = ordered.sumOf { Wellbeing.block(it)!!.questions.size }
            return WDraft(ordered, List(n) { -1 }, 0)
        }
        fun parse(s: String?): WDraft? = runCatching {
            val o = JSONObject(s ?: return null)
            val b = o.getJSONArray("b"); val p = o.getJSONArray("p")
            WDraft((0 until b.length()).map { b.getString(it) }, (0 until p.length()).map { p.getInt(it) }, o.optInt("pos"))
                .takeIf { d -> d.picks.size == d.questions.size }
        }.getOrNull()
    }

    /** Результаты по блокам (только если все ответы даны). */
    fun results(): Map<String, BlockResult>? {
        if (picks.any { it < 0 }) return null
        var i = 0
        return blocks.mapNotNull { Wellbeing.block(it) }.associate { b ->
            val p = picks.subList(i, i + b.questions.size); i += b.questions.size
            b.id to BlockResult(Wellbeing.score(b, p), p.toList())
        }
    }
}
