package org.maittaa.tabita.train

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

const val TRAIN_PREFS = "train"
fun trainDir(ctx: Context) = File(ctx.filesDir, "train").apply { mkdirs() }

class Storage(private val ctx: Context) {
    val context: Context get() = ctx
    // данные тренировок — в своей папке и своих настройках
    private val dir = trainDir(ctx)
    private val historyFile = File(dir, "history.json")
    private val draftFile = File(dir, "draft.json")
    private val programFile = File(dir, "program.json")
    private val prefs = ctx.getSharedPreferences(TRAIN_PREFS, Context.MODE_PRIVATE)

    fun loadProgram(): Program? = runCatching { Program.fromJson(JSONObject(programFile.readText())) }.getOrNull()
    fun saveProgram(p: Program?) { if (p == null) programFile.delete() else programFile.writeText(p.toJson().toString()) }

    var equipment: Set<Equipment>
        get() = prefs.getStringSet("equipment", null)?.mapNotNull { runCatching { Equipment.valueOf(it) }.getOrNull() }?.toSet()
            ?: setOf(Equipment.DUMBBELLS, Equipment.BACKPACK, Equipment.CHAIR, Equipment.SOFA, Equipment.STEP)
        set(v) = prefs.edit().putStringSet("equipment", v.map { it.name }.toSet()).apply()

    /** 5.42: веса инвентаря (гантели, рюкзак, шаги в зале). */
    var inventory: Inventory
        get() = Inventory.fromJson(prefs.getString("inventory", null))
        set(v) = prefs.edit().putString("inventory", v.toJson()).apply()

    var gymEquipment: Set<Equipment>
        get() = prefs.getStringSet("gymEq", null)?.mapNotNull { runCatching { Equipment.valueOf(it) }.getOrNull() }?.toSet()
            ?: Equipment.gymList.toSet()
        set(v) = prefs.edit().putStringSet("gymEq", v.map { it.name }.toSet()).apply()

    var gymEnabled: Boolean
        get() = prefs.getBoolean("gym", false)
        set(v) = prefs.edit().putBoolean("gym", v).apply()

    var heightCm: Int
        get() = prefs.getInt("height", 0)
        set(v) = prefs.edit().putInt("height", v).apply()

    /** Год рождения (0 — не указан) и пол ("m"/"f"/"") — когда в Tabita профиля нет. */
    var birthYear: Int
        get() = prefs.getInt("birthYear", 0)
        set(v) = prefs.edit().putInt("birthYear", v).apply()

    var sex: String
        get() = prefs.getString("sex", "") ?: ""
        set(v) = prefs.edit().putString("sex", v).apply()

    /** Тренировки, которые ещё не удалось записать в Health Connect (id сессий) — дописываются при входе. */
    var hcQueue: Set<String>
        get() = prefs.getStringSet("hcQueue", emptySet())!!.toSet()
        set(v) = prefs.edit().putStringSet("hcQueue", v).apply()

    var manualWeight: Float
        get() = prefs.getFloat("weight", 0f)
        set(v) = prefs.edit().putFloat("weight", v).apply()

    var warmups: Set<String>
        get() = prefs.getStringSet("warmups", null) ?: Library.defaultWarmups
        set(v) = prefs.edit().putStringSet("warmups", v).apply()

    var useHealth: Boolean
        get() = prefs.getBoolean("hc", true)
        set(v) = prefs.edit().putBoolean("hc", v).apply()

    var useTabita: Boolean
        get() = prefs.getBoolean("tabita", true)
        set(v) = prefs.edit().putBoolean("tabita", v).apply()

    /** Какие данные брать из Tabita (1.3), см. [TabitaUse]. */
    var tabitaUse: Set<String>
        get() = prefs.getStringSet("tabitaUse", null)?.toSet() ?: TabitaUse.default
        set(v) = prefs.edit().putStringSet("tabitaUse", v).apply()

    /** Переносы тренировок (1.8): дата → id дня программы ("" — отдых), см. [Schedule]. */
    var schedOverrides: Map<String, String>
        get() = runCatching {
            val o = org.json.JSONObject(prefs.getString("schedOverrides", "{}") ?: "{}")
            o.keys().asSequence().associateWith { o.getString(it) }
        }.getOrDefault(emptyMap())
        set(v) = prefs.edit().putString("schedOverrides", org.json.JSONObject(v as Map<*, *>).toString()).apply()

    /** Разгрузочная неделя (1.3): до какой даты включительно; от какой даты не предлагать снова. */
    /** 5.55: прибавка подходов к плану по упражнениям (JSON exId → +1/+2). */
    var extraSets: String
        get() = prefs.getString("extraSets", "") ?: ""
        set(v) = prefs.edit().putString("extraSets", v).apply()
    var deloadUntil: String
        get() = prefs.getString("deloadUntil", "") ?: ""
        set(v) = prefs.edit().putString("deloadUntil", v).apply()
    var deloadSnooze: String
        get() = prefs.getString("deloadSnooze", "") ?: ""
        set(v) = prefs.edit().putString("deloadSnooze", v).apply()

    /** 1.7: интенсивность по данным дневника выбирается сама («умный режим»); smartDraft — режим текущей тренировки. */
    var autoIntensity: Boolean
        get() = prefs.getBoolean("autoIntensity", true)
        set(v) = prefs.edit().putBoolean("autoIntensity", v).apply()
    var smartDraft: Boolean
        get() = prefs.getBoolean("smartDraft", false)
        set(v) = prefs.edit().putBoolean("smartDraft", v).apply()

    // ——— оформление и графики (1.5) ———
    var themeMode: String
        get() = prefs.getString("themeMode", "system") ?: "system"
        set(v) = prefs.edit().putString("themeMode", v).apply()
    var accent: String
        get() = prefs.getString("accent", "dynamic") ?: "dynamic"
        set(v) = prefs.edit().putString("accent", v).apply()
    /** Тип графика по ключу карточки/показателя. */
    fun chartType(key: String, def: SChart): SChart = runCatching { SChart.valueOf(prefs.getString("chart_$key", null)!!) }.getOrDefault(def)
    fun setChartType(key: String, t: SChart) = prefs.edit().putString("chart_$key", t.name).apply()
    /** Выбранный показатель в подробной карточке. */
    fun selected(key: String): String = prefs.getString("sel_$key", "") ?: ""
    fun setSelected(key: String, id: String) = prefs.edit().putString("sel_$key", id).apply()
    /** Порядок и скрытые карточки «Прогресса». */
    var progOrder: List<String>
        get() = prefs.getString("progOrder", "")!!.split(',').filter { it.isNotBlank() }
        set(v) = prefs.edit().putString("progOrder", v.joinToString(",")).apply()
    var progHidden: Set<String>
        get() = prefs.getStringSet("progHidden", emptySet())!!.toSet()
        set(v) = prefs.edit().putStringSet("progHidden", v).apply()

    /**
     * Автоподбор программы (5.37): день первой автосборки (epochDay, 0 — не было), сколько пересборок уже сделано
     * после неё (0 → ждём пересборку через 7 дней, 1 → через 14, 2 — готово) и «напомнить позже» (epochDay).
     */
    var autoStart: Long
        get() = prefs.getLong("autoStart", 0)
        set(v) = prefs.edit().putLong("autoStart", v).apply()
    var autoStage: Int
        get() = prefs.getInt("autoStage", 0)
        set(v) = prefs.edit().putInt("autoStage", v).apply()
    var autoSnooze: Long
        get() = prefs.getLong("autoSnooze", 0)
        set(v) = prefs.edit().putLong("autoSnooze", v).apply()

    var genSettings: GenSettings
        get() = runCatching {
            val o = JSONObject(prefs.getString("gen", null)!!)
            GenSettings(
                Goal.valueOf(o.getString("goal")),
                o.getJSONArray("days").let { a -> List(a.length()) { java.time.DayOfWeek.of(a.getInt(it)) } },
                o.getInt("min"), o.getInt("lvl"), o.getBoolean("noEq"),
                runCatching { Place.valueOf(o.getString("place")) }.getOrDefault(Place.HOME),
                runCatching { Split.valueOf(o.getString("split")) }.getOrDefault(Split.FULL),
            )
        }.getOrDefault(GenSettings())
        set(g) = prefs.edit().putString(
            "gen",
            JSONObject().put("goal", g.goal.name).put("days", JSONArray(g.days.map { it.value }))
                .put("min", g.minutes).put("lvl", g.level).put("noEq", g.noEquipment).put("place", g.place.name).put("split", g.split.name).toString(),
        ).apply()

    fun loadHistory(): List<Session> = runCatching {
        val a = JSONArray(historyFile.readText())
        List(a.length()) { Session.fromJson(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    fun saveHistory(list: List<Session>) {
        historyFile.writeText(JSONArray().apply { list.forEach { put(it.toJson()) } }.toString())
    }

    fun loadDraft(): Draft? = runCatching { Draft.fromJson(JSONObject(draftFile.readText())) }.getOrNull()
    fun saveDraft(d: Draft?) {
        if (d == null) draftFile.delete() else draftFile.writeText(d.toJson().toString())
    }

    var vaultUri: Uri?
        get() = prefs.getString("vault", null)?.let(Uri::parse)
        set(v) = prefs.edit().putString("vault", v?.toString()).apply()

    var autoVault: Boolean
        get() = prefs.getBoolean("autoVault", true)
        set(v) = prefs.edit().putBoolean("autoVault", v).apply()

    var restOverride: Int
        get() = prefs.getInt("rest", 0)
        set(v) = prefs.edit().putInt("rest", v).apply()

    /** Пишет заметку в выбранную папку хранилища Obsidian. Возвращает имя файла. */
    fun writeToVault(fileName: String, text: String): String {
        val tree = vaultUri ?: error("Папка не выбрана")
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        var name = fileName
        var n = 2
        while (exists(tree, name)) { name = fileName.removeSuffix(".md") + " ($n).md"; n++ }
        val doc = DocumentsContract.createDocument(ctx.contentResolver, parent, "application/octet-stream", name)
            ?: error("Не удалось создать файл")
        ctx.contentResolver.openOutputStream(doc, "wt")!!.use { it.write(text.toByteArray()) }
        return name
    }

    private fun exists(tree: Uri, name: String): Boolean {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        ctx.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
            while (it.moveToNext()) if (it.getString(0) == name || it.getString(0) == name.removeSuffix(".md")) return true
        }
        return false
    }

    fun writeToUri(uri: Uri, text: String) {
        ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
    }

    fun shareFile(fileName: String, text: String): File {
        val d = File(ctx.cacheDir, "share").apply { mkdirs() }
        return File(d, fileName).apply { writeText(text) }
    }
}
