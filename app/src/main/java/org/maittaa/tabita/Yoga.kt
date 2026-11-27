package org.maittaa.tabita

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * 5.53: йога и растяжка для осанки и восстановления. Позы со временем удержания (статическая растяжка —
 * 30 с: ACSM рекомендует 10–30 с до ощущения натяжения, 2–4 повтора, всего ~60 с на мышцу), упражнения
 * на мышцы-стабилизаторы — по времени на серию. Готовые комплексы и свои (Prefs.yogaCustom), самопроверки
 * осанки и гибкости раз в месяц (Prefs.yogaChecks).
 */
object Yoga {
    /** Поза: [sec] — на сторону, если [sides]; [reps] — подпись вместо секунд («10 раз»). */
    data class Pose(val id: String, val ru: String, val en: String, val sec: Int, val sides: Boolean, val cueRu: String, val cueEn: String, val reps: String = "") {
        val name get() = if (I18n.isEn) en else ru
        val cue get() = if (I18n.isEn) cueEn else cueRu
        val totalSec get() = if (sides) sec * 2 else sec
    }

    val poses = listOf(
        Pose("mountain", "Гора у стены", "Wall mountain pose", 60, false,
            "Затылок, лопатки и ягодицы касаются стены, подбородок чуть к себе, дышите спокойно",
            "Back of the head, shoulder blades and glutes touch the wall, chin slightly in, breathe calmly"),
        Pose("catcow", "Кошка — корова", "Cat–cow", 60, false,
            "На четвереньках: на вдохе прогиб, на выдохе спина круглая. Медленно, по дыханию",
            "On all fours: arch on the inhale, round the back on the exhale. Slowly, with the breath"),
        Pose("child", "Поза ребёнка", "Child's pose", 60, false,
            "Таз к пяткам, руки вперёд, лоб на пол. Дышите в спину",
            "Hips to heels, arms forward, forehead down. Breathe into the back"),
        Pose("sphinx", "Сфинкс", "Sphinx", 45, false,
            "Лёжа на животе, опора на предплечья, плечи от ушей, грудь вперёд",
            "Lying on your belly, on your forearms, shoulders away from the ears, chest forward"),
        Pose("cobra", "Низкая кобра", "Low cobra", 30, false,
            "Лёжа на животе, ладони под плечами, приподнимите грудь без опоры на руки",
            "Lying on your belly, palms under the shoulders, lift the chest without pushing into the hands"),
        Pose("chin", "Втягивание подбородка", "Chin tucks", 45, false,
            "Сидя или стоя: подбородок назад, как будто делаете двойной подбородок, 3 с — и расслабить",
            "Sitting or standing: draw the chin straight back into a double chin, hold 3 s, relax", "10 раз"),
        Pose("angels", "Уголки у стены", "Wall angels", 60, false,
            "Спина и руки у стены, локти под 90°. Медленно вверх и вниз, не отрывая рук",
            "Back and arms against the wall, elbows at 90°. Slide up and down without lifting the arms", "10 раз"),
        Pose("doorway", "Растяжка груди в дверном проёме", "Doorway chest stretch", 30, true,
            "Предплечье на косяке, локоть на уровне плеча, шаг вперёд до натяжения в груди",
            "Forearm on the door frame, elbow at shoulder height, step forward until you feel the chest stretch"),
        Pose("trap", "Растяжка шеи", "Neck side stretch", 30, true,
            "Ухо к плечу, противоположное плечо опущено. Без рывков",
            "Ear toward the shoulder, the other shoulder down. No jerking"),
        Pose("thread", "Нить в иголку", "Thread the needle", 30, true,
            "На четвереньках: рука под корпус, плечо и висок на пол, скручивание в груди",
            "On all fours: slide one arm under the body, shoulder and temple down, twist through the chest"),
        Pose("bridge", "Ягодичный мост", "Glute bridge", 60, false,
            "Лёжа, стопы у таза: поднять таз, сжать ягодицы, 2 с вверху. Поясница не прогибается",
            "Lying down, feet near the hips: lift the hips, squeeze the glutes, 2 s at the top. Don't arch the lower back", "12 раз"),
        Pose("birddog", "Птица — собака", "Bird dog", 60, false,
            "На четвереньках: противоположные рука и нога вытягиваются, спина ровная, по очереди",
            "On all fours: extend the opposite arm and leg, back level, alternate sides", "6 на сторону"),
        Pose("deadbug", "Мёртвый жук", "Dead bug", 60, false,
            "Лёжа, руки и колени вверх: опускать противоположные руку и ногу, поясница прижата",
            "Lying down, arms and knees up: lower the opposite arm and leg, lower back pressed down", "8 на сторону"),
        Pose("superman", "Лодочка на животе", "Prone Y raise", 45, false,
            "Лёжа на животе, руки буквой Y: поднять руки и грудь, лопатки вниз и вместе",
            "Lying on your belly, arms in a Y: lift arms and chest, shoulder blades down and together", "10 раз"),
        Pose("hipflexor", "Растяжка сгибателей бедра", "Kneeling hip flexor stretch", 30, true,
            "Выпад на колене, таз подкручен вперёд, ягодица задней ноги напряжена",
            "Kneeling lunge, tuck the pelvis, squeeze the glute of the back leg"),
        Pose("hamstring", "Растяжка задней поверхности бедра", "Hamstring stretch", 30, true,
            "Лёжа на спине, нога вверх, ремень или полотенце на стопе, колено чуть согнуто",
            "Lying on your back, one leg up, a strap or towel around the foot, knee slightly bent"),
        Pose("downdog", "Собака мордой вниз", "Downward dog", 45, false,
            "Таз вверх и назад, спина длинная, колени можно согнуть, пятки к полу",
            "Hips up and back, long spine, knees may bend, heels toward the floor"),
        Pose("twist", "Скручивание лёжа", "Supine twist", 30, true,
            "Лёжа, колени в сторону, плечи на полу, взгляд в другую сторону",
            "Lying down, knees to one side, shoulders on the floor, look the other way"),
        Pose("figure4", "Растяжка ягодицы лёжа", "Figure-four stretch", 30, true,
            "Лёжа, лодыжка на колене другой ноги, подтяните ногу к себе",
            "Lying down, ankle over the other knee, draw the leg toward you"),
        Pose("legswall", "Ноги на стене", "Legs up the wall", 120, false,
            "Таз у стены, ноги вверх по стене, руки в стороны, спокойное дыхание",
            "Hips near the wall, legs up the wall, arms out, calm breathing"),
        Pose("breath", "Диафрагмальное дыхание", "Belly breathing", 90, false,
            "Ладонь на животе: вдох на 4 счёта животом, выдох на 6",
            "Hand on the belly: breathe in for 4 counts into the belly, out for 6"),
    )

    fun pose(id: String) = poses.firstOrNull { it.id == id }

    /** Комплекс: позы по порядку (одна поза может стоять дважды — второй подход). */
    data class Complex(val id: String, val ru: String, val en: String, val poses: List<String>, val custom: Boolean = false) {
        val name get() = if (custom || !I18n.isEn) ru else en
        val totalSec get() = poses.mapNotNull { pose(it)?.totalSec }.sum()
    }

    val builtIn = listOf(
        Complex("morning", "Утро", "Morning", listOf("catcow", "chin", "angels", "doorway", "hipflexor", "downdog", "mountain")),
        Complex("posture", "Осанка", "Posture", listOf("mountain", "chin", "catcow", "angels", "doorway", "thread", "sphinx", "superman",
            "birddog", "deadbug", "bridge", "hipflexor", "doorway", "hipflexor", "hamstring", "child", "breath")),
        Complex("after", "После силовой", "After strength", listOf("child", "doorway", "hipflexor", "hamstring", "figure4", "twist", "breath")),
        Complex("evening", "Перед сном", "Before sleep", listOf("catcow", "child", "thread", "figure4", "twist", "hamstring", "legswall", "breath")),
    )

    /** Свои комплексы (JSON из Prefs, ставит MainActivity и экран йоги). */
    @Volatile var customJson: String = ""
    val custom: List<Complex> get() = parseCustom(customJson)
    fun all() = builtIn + custom
    fun complex(id: String) = all().firstOrNull { it.id == id }

    fun parseCustom(s: String): List<Complex> = runCatching {
        val a = JSONArray(s.ifBlank { return emptyList() })
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val p = o.getJSONArray("p")
            Complex(o.getString("id"), o.getString("n"), o.getString("n"), (0 until p.length()).map { p.getString(it) }.filter { pose(it) != null }, true)
        }
    }.getOrDefault(emptyList())

    fun customToJson(list: List<Complex>): String = JSONArray(list.map { c ->
        JSONObject().put("id", c.id).put("n", c.ru).put("p", JSONArray(c.poses))
    }).toString()

    /** Блоки таймера: поза на две стороны — два блока. */
    fun blocks(c: Complex): List<CardioPlans.Block> = c.poses.mapNotNull { pose(it) }.flatMap { p ->
        val hint = listOf(p.cue, p.reps.let { r -> if (r.isBlank()) "" else t(r) }).filter { it.isNotBlank() }.joinToString(" · ")
        if (p.sides) listOf(CardioPlans.Block(p.name + " · " + t("левая"), p.sec, 4, hint), CardioPlans.Block(p.name + " · " + t("правая"), p.sec, 4, hint))
        else listOf(CardioPlans.Block(p.name, p.sec, 4, hint))
    }

    // ---------- самопроверки ----------

    /**
     * Самопроверка: [wall] — что касается стены в стойке (затылок, лопатки, ягодицы), [bend] — от кончиков пальцев
     * до пола в наклоне, см (0 — касаетесь, меньше нуля — ладони ниже уровня пола на ступеньке/книге не меряем: 0),
     * [clasp] — руки за спиной: 2 — сцепляются, 1 — пальцы касаются, 0 — нет.
     */
    data class Check(val date: String, val wall: Set<String>, val bend: Int, val clasp: Int) {
        fun toJson() = JSONObject().put("d", date).put("w", JSONArray(wall.toList())).put("b", bend).put("c", clasp)
        companion object {
            fun fromJson(o: JSONObject) = Check(o.getString("d"),
                o.optJSONArray("w")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet(), o.optInt("b"), o.optInt("c"))
        }
    }

    val wallPoints = listOf("head", "blades", "glutes")
    fun wallName(k: String) = when (k) { "head" -> t("Затылок"); "blades" -> t("Лопатки"); else -> t("Ягодицы") }
    val claspNames get() = listOf(t("Не сходятся"), t("Пальцы касаются"), t("Сцепляются"))

    fun parseChecks(s: String): List<Check> = runCatching {
        val a = JSONArray(s.ifBlank { return emptyList() })
        (0 until a.length()).map { Check.fromJson(a.getJSONObject(it)) }.sortedBy { it.date }
    }.getOrDefault(emptyList())

    fun checksToJson(l: List<Check>) = JSONArray(l.map { it.toJson() }).toString()

    /** Пора проверить: проверок не было или последней 28+ дней. */
    fun checkDue(l: List<Check>, today: LocalDate = LocalDate.now()) =
        l.lastOrNull()?.let { !LocalDate.parse(it.date).plusDays(28).isAfter(today) } ?: true

    val kinds = setOf("yoga", "stretch")
}
