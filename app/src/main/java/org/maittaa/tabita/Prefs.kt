package org.maittaa.tabita

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import java.security.MessageDigest
import java.security.SecureRandom

/** Настройки приложения (не путать с данными дневника). */
class Prefs(context: Context) {
    val sp: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

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
    /** Экран графиков: 0 — графики, 1 — месяц. */
    var statView: Int
        get() = sp.getInt("statView", 0)
        set(v) = sp.edit().putInt("statView", v).apply()
    fun chartType(key: String, def: ChartType): ChartType =
        runCatching { ChartType.valueOf(sp.getString("ct_$key", null)!!) }.getOrDefault(def)
    fun setChartType(key: String, t: ChartType) = sp.edit().putString("ct_$key", t.name).apply()
    /** Засчитывать ходьбу как тренировку (по умолчанию нет: ходьба и так видна в шагах) */
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
