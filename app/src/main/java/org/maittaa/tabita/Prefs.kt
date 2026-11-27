package org.maittaa.tabita

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import java.security.MessageDigest
import java.security.SecureRandom

/** Настройки приложения (не путать с данными дневника). */
class Prefs(context: Context) {
    companion object {
        /** Ключи, которые не попадают в резервную копию. */
        val NO_BACKUP = setOf("pinHash", "pinSalt", "lock", "bio", "secure", "aiKey")
    }
    val sp: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /**
     * Настройки в резервную копию (5.32). Не переносятся: PIN, блокировка и биометрия (защита — у каждого телефона своя)
     * и ключ ИИ (секрет; провайдер, модель и адрес — переносятся).
     */
    fun backupJson(): String {
        val o = org.json.JSONObject().put("v", 1)
        val p = org.json.JSONObject()
        sp.all.forEach { (k, v) ->
            if (k in NO_BACKUP || v == null) return@forEach
            p.put(k, org.json.JSONObject().put("t", when (v) {
                is Boolean -> "b"; is Int -> "i"; is Long -> "l"; is Float -> "f"; is Set<*> -> "s"; else -> "str"
            }).put("v", if (v is Set<*>) JSONArray(v.toList()) else v))
        }
        return o.put("prefs", p).toString()
    }

    fun restoreJson(text: String) {
        val p = org.json.JSONObject(text).getJSONObject("prefs")
        val e = sp.edit()
        // всё, что переносится, заменяем; защиту и ключ ИИ оставляем как есть
        sp.all.keys.filter { it !in NO_BACKUP }.forEach { e.remove(it) }
        p.keys().forEach { k ->
            if (k in NO_BACKUP) return@forEach
            val x = p.getJSONObject(k)
            when (x.getString("t")) {
                "b" -> e.putBoolean(k, x.getBoolean("v"))
                "i" -> e.putInt(k, x.getInt("v"))
                "l" -> e.putLong(k, x.getLong("v"))
                "f" -> e.putFloat(k, x.getDouble("v").toFloat())
                "s" -> e.putStringSet(k, x.getJSONArray("v").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() })
                else -> e.putString(k, x.getString("v"))
            }
        }
        e.commit()
    }

    // язык: "" — как в системе, "ru", "en"
    var lang: String
        get() = sp.getString("lang", "") ?: ""
        set(v) = sp.edit().putString("lang", v).apply()
    var currency: String
        get() = sp.getString("currency", "₽") ?: "₽"
        set(v) = sp.edit().putString("currency", v).apply()

    /** Журнал: 0 — выкл, 1 — краткий, 2 — полный */
    var logLevel: Int
        get() = sp.getInt("log", 1)
        set(v) = sp.edit().putInt("log", v).apply()

    // ---------- оформление ----------
    var themeMode: String
        get() = sp.getString("theme", "system") ?: "system"
        set(v) = sp.edit().putString("theme", v).apply()
    var accent: String
        get() = sp.getString("accent", if (android.os.Build.VERSION.SDK_INT >= 31) "dynamic" else "green") ?: "green"
        set(v) = sp.edit().putString("accent", v).apply()

    // ---------- графики ----------
    /** Карточек в ряд: 1, 2 или 3 */
    var statCols: Int
        get() = sp.getInt("statCols", 1)
        set(v) = sp.edit().putInt("statCols", v).apply()
    /** Скрытые карточки графиков (ключи) */
    var statHidden: Set<String>
        get() = sp.getStringSet("statHidden", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("statHidden", v).apply()
    /** Вкладка «Месяц»: состав индекса, цели, цель индекса. */
    var monthCfg: MonthCfg
        get() = MonthCfg.parse(sp.getString("monthCfg", null))
        set(v) = sp.edit().putString("monthCfg", v.toJson()).apply()
    /** Чекап: период в днях, выбранные блоки, незаконченное прохождение. */
    var wbPeriod: Int
        get() = sp.getInt("wbPeriod", 14)
        set(v) = sp.edit().putInt("wbPeriod", v).apply()
    var wbBlocks: Set<String>
        get() = sp.getStringSet("wbBlocks", null)?.toSet() ?: Wellbeing.defaultSelection
        set(v) = sp.edit().putStringSet("wbBlocks", v).apply()
    var wbDraft: String?
        get() = sp.getString("wbDraft", null)
        set(v) = sp.edit().putString("wbDraft", v).apply()
    /** Порядок карточек на экране графиков (ключи); новые — в конце. */
    var statOrder: List<String>
        get() = sp.getString("statOrder", "")!!.split("\u001F").filter { it.isNotEmpty() }
        set(v) = sp.edit().putString("statOrder", v.joinToString("\u001F")).apply()
    /** Выбранная шкала в карточке «Самочувствие». */
    var wbSelected: String
        get() = sp.getString("wbSelected", "phq8")!!
        set(v) = sp.edit().putString("wbSelected", v).apply()
    /** Графики (5.24): выбранная группа по смыслу ("all" — все). */
    /** Справочник продуктов (5.27): избранное (ключи Food.key), сколько раз добавлен продукт, сортировка. */
    var foodFav: Set<String>
        get() = sp.getStringSet("foodFav", emptySet())?.toSet() ?: emptySet()
        set(v) = sp.edit().putStringSet("foodFav", v).apply()
    var foodUse: Map<String, Int>
        get() = runCatching { org.json.JSONObject(sp.getString("foodUse", "{}") ?: "{}").let { o -> o.keys().asSequence().associateWith { o.getInt(it) } } }.getOrDefault(emptyMap())
        set(v) = sp.edit().putString("foodUse", org.json.JSONObject(v as Map<*, *>).toString()).apply()
    var foodSort: Int
        get() = sp.getInt("foodSort", 0)
        set(v) = sp.edit().putInt("foodSort", v).apply()
    /** Подсказки на «Сегодня» после знакомства (5.36): про жесты плиток и про кнопки внизу. */
    var tileHint: Boolean
        get() = sp.getBoolean("tileHint", false)
        set(v) = sp.edit().putBoolean("tileHint", v).apply()
    var tileBtnHint: Boolean
        get() = sp.getBoolean("tileBtnHint", false)
        set(v) = sp.edit().putBoolean("tileBtnHint", v).apply()
    /** Знакомство при первом запуске пройдено (5.35). */
    var introDone: Boolean
        get() = sp.getBoolean("introDone", false)
        set(v) = sp.edit().putBoolean("introDone", v).apply()
    /** Раздел «Программы» (5.30): 0 — план, 1 — графики, 2 — самочувствие. */
    var programSub: Int
        get() = sp.getInt("programSub", 0)
        set(v) = sp.edit().putInt("programSub", v).apply()
    /** План (5.28): цель, дата начала, вес и безжировая масса на старте. */
    var planGoal: String
        get() = sp.getString("planGoal", "") ?: ""
        set(v) = sp.edit().putString("planGoal", v).apply()
    var planStart: String
        get() = sp.getString("planStart", "") ?: ""
        set(v) = sp.edit().putString("planStart", v).apply()
    var planW0: Double
        get() = sp.getFloat("planW0", 0f).toDouble()
        set(v) = sp.edit().putFloat("planW0", v.toFloat()).apply()
    var planLean0: Double
        get() = sp.getFloat("planLean0", 0f).toDouble()
        set(v) = sp.edit().putFloat("planLean0", v.toFloat()).apply()
    var statCtx: String
        get() = sp.getString("statCtx", "all") ?: "all"
        set(v) = sp.edit().putString("statCtx", v).apply()
    /** Экран графиков: 0 — графики, 1 — месяц. */
    var statView: Int
        get() = sp.getInt("statView", 0)
        set(v) = sp.edit().putInt("statView", v).apply()
    fun chartType(key: String, def: ChartType): ChartType =
        runCatching { ChartType.valueOf(sp.getString("ct_$key", null)!!) }.getOrDefault(def)
    fun setChartType(key: String, t: ChartType) = sp.edit().putString("ct_$key", t.name).apply()
    /** Засчитывать ходьбу как тренировку (по умолчанию нет: ходьба и так видна в шагах) */
    /** 5.42: норма воды считается сама каждый день (вес, тренировки, шаги). */
    var waterAuto: Boolean
        get() = sp.getBoolean("waterAuto", true)
        set(v) = sp.edit().putBoolean("waterAuto", v).apply()
    /** 5.42: замеры потери пота — JSON-массив Hydration.Sample. */
    var sweatLog: String
        get() = sp.getString("sweatLog", "") ?: ""
        set(v) = sp.edit().putString("sweatLog", v).apply()
    var countWalking: Boolean
        get() = sp.getBoolean("countWalking", false)
        set(v) = sp.edit().putBoolean("countWalking", v).apply()
    /** Сводка заметок: текст скрыт */
    var diaryHidden: Boolean
        get() = sp.getBoolean("diaryHidden", false)
        set(v) = sp.edit().putBoolean("diaryHidden", v).apply()
    /** Сводка заметок: сначала новые */
    var diaryNewest: Boolean
        get() = sp.getBoolean("diaryNewest", false)
        set(v) = sp.edit().putBoolean("diaryNewest", v).apply()
    /** Выбранный показатель в карточке «Сон» */
    var sleepSelected: String
        get() = sp.getString("sleepSel", "score") ?: "score"
        set(v) = sp.edit().putString("sleepSel", v).apply()
    /** Советы дня (5.20): отключённые категории и скрытые сегодня советы ("дата|id"). */
    var tipsOff: Set<String>
        get() = sp.getStringSet("tipsOff", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("tipsOff", v).apply()
    /** Как часто менять совет в плитке, секунды (0 — не менять); по умолчанию 5 минут (5.22.1). */
    /** 5.45: какие данные учитывает ритм дня (Rhythm.SOURCES); по умолчанию все. */
    var rhythmSrc: Set<String>
        get() = sp.getString("rhythmSrc", null)?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: RhythmSrc.ALL
        set(v) = sp.edit().putString("rhythmSrc", v.joinToString(",")).apply()

    /** 5.51: свой максимальный пульс (0 — по формуле 220 − возраст). */
    var hrMax: Int
        get() = sp.getInt("hrMax", 0)
        set(v) = sp.edit().putInt("hrMax", v).apply()
    /** 5.51: незаконченное занятие с таймером (JSON CardioDraft). */
    var cardioDraft: String?
        get() = sp.getString("cardioDraft", null)
        set(v) = sp.edit().putString("cardioDraft", v).apply()
    /** 5.51: настройки таймера: тихо для соседей, минут основной части, интервалы. */
    var cardioQuiet: Boolean
        get() = sp.getBoolean("cardioQuiet", true)
        set(v) = sp.edit().putBoolean("cardioQuiet", v).apply()
    var cardioMain: Int
        get() = sp.getInt("cardioMain", 15)
        set(v) = sp.edit().putInt("cardioMain", v).apply()
    var cardioWork: Int
        get() = sp.getInt("cardioWork", 30)
        set(v) = sp.edit().putInt("cardioWork", v).apply()
    var cardioRest: Int
        get() = sp.getInt("cardioRest", 30)
        set(v) = sp.edit().putInt("cardioRest", v).apply()
    var cardioRounds: Int
        get() = sp.getInt("cardioRounds", 10)
        set(v) = sp.edit().putInt("cardioRounds", v).apply()

    /** 5.53: свои комплексы йоги (JSON), самопроверки осанки (JSON), фильтр общей истории «Здоровья». */
    var yogaCustom: String
        get() = sp.getString("yogaCustom", "") ?: ""
        set(v) = sp.edit().putString("yogaCustom", v).apply()
    var yogaChecks: String
        get() = sp.getString("yogaChecks", "") ?: ""
        set(v) = sp.edit().putString("yogaChecks", v).apply()
    var histFilter: String
        get() = sp.getString("histFilter", "all") ?: "all"
        set(v) = sp.edit().putString("histFilter", v).apply()

    /** 5.56: недельный шаблон нагрузки (JSON WeekTpl: аэробная и йога по дням, цель активных минут). */
    var weekTpl: String?
        get() = sp.getString("weekTpl", null)
        set(v) = sp.edit().putString("weekTpl", v).apply()
    /** 5.56: поправка расхода по реальному тренду веса, ккал в день (0 — нет). */
    var burnAdj: Int
        get() = sp.getInt("burnAdj", 0)
        set(v) = sp.edit().putInt("burnAdj", v).apply()
    /** 5.56: когда принята поправка (дата ISO). */
    var burnAdjAt: String?
        get() = sp.getString("burnAdjAt", null)
        set(v) = sp.edit().putString("burnAdjAt", v).apply()
    /** 5.59.2: разделы в плитке «Отметки дня» подзаголовками (выкл — все отметки одной кучей). */
    var checkSections: Boolean
        get() = sp.getBoolean("checkSections", true)
        set(v) = sp.edit().putBoolean("checkSections", v).apply()
    /** 5.59.2: отметкам из набора самочувствия проставлено влияние на совет (однократно). */
    var marksTrainSet: Boolean
        get() = sp.getBoolean("marksTrainSet", false)
        set(v) = sp.edit().putBoolean("marksTrainSet", v).apply()
    /** 5.63: «Сравнить» в «Графиках»: первый и второй показатель, прошлый период. */
    var cmpA: String?
        get() = sp.getString("cmpA", null)
        set(v) = sp.edit().putString("cmpA", v).apply()
    var cmpB: String?
        get() = sp.getString("cmpB", null)
        set(v) = sp.edit().putString("cmpB", v).apply()
    var cmpPrev: Boolean
        get() = sp.getBoolean("cmpPrev", false)
        set(v) = sp.edit().putBoolean("cmpPrev", v).apply()
    /** 5.62: эксперименты (JSON Experiments). */
    var experiments: String?
        get() = sp.getString("experiments", null)
        set(v) = sp.edit().putString("experiments", v).apply()
    /** 5.57: шаблоны приёмов пищи (JSON MealTpls). */
    var mealTpls: String?
        get() = sp.getString("mealTpls", null)
        set(v) = sp.edit().putString("mealTpls", v).apply()
    /** 5.57: список покупок — неделя, отметки, свои позиции (JSON Shopping.State). */
    var shopList: String?
        get() = sp.getString("shopList", null)
        set(v) = sp.edit().putString("shopList", v).apply()
    /** 5.56: своя дата, к которой хочется дойти до цели (ISO), null — нет. */
    var planDue: String?
        get() = sp.getString("planDue", null)
        set(v) = sp.edit().putString("planDue", v).apply()

    /** 5.43: колонок в сетке плиток (4–6). */
    var gridCols: Int
        get() = sp.getInt("gridCols", 4).coerceIn(4, 6)
        set(v) = sp.edit().putInt("gridCols", v.coerceIn(4, 6)).apply()
    /** 5.43: вид ритма дня — "chart" (дорожки) или "dial" (круг 12 ч). */
    var rhythmView: String
        get() = sp.getString("rhythmView", "dial") ?: "dial"
        set(v) = sp.edit().putString("rhythmView", v).apply()
    /** 5.43: тонкая полоса ритма над плитками. */
    var rhythmStrip: Boolean
        get() = sp.getBoolean("rhythmStrip", false)
        set(v) = sp.edit().putBoolean("rhythmStrip", v).apply()

    var tipsEvery: Int
        get() = sp.getInt("tipsEvery", 300)
        set(v) = sp.edit().putInt("tipsEvery", v).apply()
    /** Подъём и отбой вручную, минуты от полуночи; −1 — по данным сна (5.22.2). */
    var rhythmWake: Int
        get() = sp.getInt("rhWake", -1)
        set(v) = sp.edit().putInt("rhWake", v).apply()
    var rhythmBed: Int
        get() = sp.getInt("rhBed", -1)
        set(v) = sp.edit().putInt("rhBed", v).apply()
    var tipsHidden: Set<String>
        get() = sp.getStringSet("tipsHidden", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("tipsHidden", v).apply()
    /** Выбранный показатель в карточке «БЖУ» (5.19.5) */
    var foodSelected: String
        get() = sp.getString("foodSel", "") ?: ""
        set(v) = sp.edit().putString("foodSel", v).apply()
    /** Выбранный показатель в группе «Весы» */
    var bodySelected: String
        get() = sp.getString("bodySel", "") ?: ""
        set(v) = sp.edit().putString("bodySel", v).apply()

    // ---------- openScale ----------
    var osPackage: String
        get() = sp.getString("osPkg", "") ?: ""
        set(v) = sp.edit().putString("osPkg", v).apply()
    var osUserId: Long
        get() = sp.getLong("osUser", -1)
        set(v) = sp.edit().putLong("osUser", v).apply()
    val osReady get() = osPackage.isNotEmpty() && osUserId >= 0

    // ---------- блокировка ----------
    var lockEnabled: Boolean
        get() = sp.getBoolean("lock", false)
        set(v) = sp.edit().putBoolean("lock", v).apply()
    /** Разрешить вход отпечатком / блокировкой телефона (PIN, графический ключ, пароль телефона). */
    var biometric: Boolean
        get() = sp.getBoolean("bio", true)
        set(v) = sp.edit().putBoolean("bio", v).apply()
    var hideInRecents: Boolean
        get() = sp.getBoolean("secure", false)
        set(v) = sp.edit().putBoolean("secure", v).apply()
    val hasPin get() = sp.getString("pinHash", null) != null

    fun setPin(pin: String?) {
        if (pin == null) { sp.edit().remove("pinHash").remove("pinSalt").apply(); return }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        sp.edit().putString("pinSalt", salt).putString("pinHash", hash(salt, pin)).apply()
    }

    fun checkPin(pin: String): Boolean {
        val salt = sp.getString("pinSalt", null) ?: return false
        return hash(salt, pin) == sp.getString("pinHash", null)
    }

    private fun hash(salt: String, pin: String): String {
        var b = (salt + pin).toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        repeat(10_000) { b = md.digest(b) }
        return b.joinToString("") { "%02x".format(it) }
    }

    // ---------- напоминания ----------
    var reminderOn: Boolean
        get() = sp.getBoolean("remOn", false)
        set(v) = sp.edit().putBoolean("remOn", v).apply()
    /** минуты от полуночи */
    var reminderTime: Int
        get() = sp.getInt("remTime", 21 * 60)
        set(v) = sp.edit().putInt("remTime", v).apply()
    var reminderOnlyIfEmpty: Boolean
        get() = sp.getBoolean("remEmpty", true)
        set(v) = sp.edit().putBoolean("remEmpty", v).apply()

    /** Уведомления об отбое (5.33) и за сколько минут предупреждать (0 — только «пора спать»). */
    var bedOn: Boolean
        get() = sp.getBoolean("bedOn", false)
        set(v) = sp.edit().putBoolean("bedOn", v).apply()
    var bedLead: Int
        get() = sp.getInt("bedLead", 60)
        set(v) = sp.edit().putInt("bedLead", v).apply()

    /** 5.65: воскресный обзор недели уведомлением (19:00). */
    var weeklyOn: Boolean
        get() = sp.getBoolean("weeklyOn", true)
        set(v) = sp.edit().putBoolean("weeklyOn", v).apply()
    /** 5.65: последняя показанная на «Сегодня» находка: «название|дата». Новая находка видна один день. */
    var findingSeen: String
        get() = sp.getString("findSeen", "") ?: ""
        set(v) = sp.edit().putString("findSeen", v).apply()

    /** 5.67: последняя просмотренная веха (14/30/66 дней), 0 — ни одной. */
    var milestoneSeen: Int
        get() = sp.getInt("msSeen", 0)
        set(v) = sp.edit().putInt("msSeen", v).apply()

    /** Звук окончания таймеров: Bells.SIMPLE или URI системного звука (5.69; значения bell_* от 5.68 = простой сигнал). */
    var bell: String
        get() = sp.getString("bell", Bells.SIMPLE) ?: Bells.SIMPLE
        set(v) = sp.edit().putString("bell", v).apply()

    var motivOn: Boolean
        get() = sp.getBoolean("motOn", false)
        set(v) = sp.edit().putBoolean("motOn", v).apply()
    var motivPerDay: Int
        get() = sp.getInt("motN", 2)
        set(v) = sp.edit().putInt("motN", v).apply()
    var motivFrom: Int
        get() = sp.getInt("motFrom", 9 * 60)
        set(v) = sp.edit().putInt("motFrom", v).apply()
    var motivTo: Int
        get() = sp.getInt("motTo", 20 * 60)
        set(v) = sp.edit().putInt("motTo", v).apply()
    var motivations: List<String>
        get() = runCatching {
            val a = JSONArray(sp.getString("motList", null) ?: return defaultMotivations())
            (0 until a.length()).map { a.getString(it) }
        }.getOrDefault(defaultMotivations())
        set(v) = sp.edit().putString("motList", JSONArray(v).toString()).apply()

    // ---------- распознавание еды ----------
    /** "anthropic" или "openai" (любой OpenAI-совместимый сервис) */
    var aiProvider: String
        get() = sp.getString("aiProv", "openai") ?: "openai"
        set(v) = sp.edit().putString("aiProv", v).apply()
    var aiUrl: String
        get() = sp.getString("aiUrl", "https://openrouter.ai/api/v1") ?: ""
        set(v) = sp.edit().putString("aiUrl", v).apply()
    var aiKey: String
        get() = sp.getString("aiKey", "") ?: ""
        set(v) = sp.edit().putString("aiKey", v).apply()
    var aiModel: String
        get() = sp.getString("aiModel", "google/gemini-2.5-flash") ?: ""
        set(v) = sp.edit().putString("aiModel", v).apply()
    val aiReady get() = aiKey.isNotBlank() && aiModel.isNotBlank()
}

fun defaultMotivations(): List<String> = listOf(
    t("Маленький шаг сегодня — большой результат через месяц."),
    t("Ты уже сделал больше, чем вчера. Продолжай!"),
    t("Как ты себя чувствуешь? Отметь день в дневнике."),
)
