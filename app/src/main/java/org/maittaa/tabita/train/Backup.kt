package org.maittaa.tabita.train

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/**
 * Данные (1.6, как в Tabita): резервная копия (JSON — история, программа, незаконченная тренировка, настройки),
 * восстановление, таблица CSV для Excel и удаление всех данных. Раньше была и выгрузка в Obsidian — выгрузка заметок.
 */
object Backup {
    const val VERSION = 1
    private const val PREFS = TRAIN_PREFS
    /** Не переносим: ссылка на папку Obsidian действует только на этом телефоне. */
    private val skipPrefs = setOf("vault")

    private fun files(ctx: Context) = listOf("history.json", "program.json", "draft.json").map { File(trainDir(ctx), it) }

    fun fileName(ext: String) = "Tabita-тренировки-${if (ext == "csv") "таблица" else "копия"}-${LocalDate.now()}.$ext"

    fun build(ctx: Context): String {
        val o = JSONObject().put("app", "sink").put("v", VERSION).put("date", LocalDate.now().toString())
        files(ctx).forEach { f -> if (f.exists()) o.put(f.nameWithoutExtension, f.readText().let { t -> if (t.trimStart().startsWith("[")) JSONArray(t) else JSONObject(t) }) }
        val p = JSONObject()
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all.forEach { (k, v) ->
            if (k in skipPrefs || v == null) return@forEach
            p.put(k, JSONObject().put("t", when (v) {
                is Boolean -> "b"; is Int -> "i"; is Long -> "l"; is Float -> "f"; is Set<*> -> "s"; else -> "str"
            }).put("v", if (v is Set<*>) JSONArray(v.toList()) else v))
        }
        o.put("prefs", p)
        return o.toString(1)
    }

    /** Сводка копии перед восстановлением: дата и число тренировок; null — это не копия тренировок. */
    fun describe(text: String): String? = runCatching {
        val o = JSONObject(text)
        if (o.optString("app") != "sink") return null
        val n = o.optJSONArray("history")?.length() ?: 0
        "Копия от ${prettyDate(o.optString("date"))}: тренировок $n" + if (o.has("program")) ", программа" else ""
    }.getOrNull()

    /** Заменяет историю, программу и настройки содержимым копии. */
    fun restore(ctx: Context, text: String) {
        SLog.i("Data", "восстановление копии: ${describe(text)}")
        val o = JSONObject(text)
        require(o.optString("app") == "sink") { "Это не копия тренировок" }
        files(ctx).forEach { f ->
            val k = f.nameWithoutExtension
            if (o.has(k)) f.writeText(o.get(k).toString()) else f.delete()
        }
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val keep = sp.all.filterKeys { it in skipPrefs }
        val e = sp.edit().clear()
        keep.forEach { (k, v) -> if (v is String) e.putString(k, v) }
        o.optJSONObject("prefs")?.let { p ->
            p.keys().forEach { k ->
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
        }
        e.commit()
    }

    /** Таблица для Excel: строка — подход. Разделитель «;», дробные с запятой. */
    fun csv(history: List<Session>): String = buildString {
        append('﻿')
        appendLine("Дата;Тренировка;Место;Интенсивность;Длительность, мин;Упражнение;Подход;Результат;Единица;Нагрузка;Самочувствие")
        history.sortedBy { it.date }.forEach { s ->
            s.exercises.forEach { r ->
                r.values.forEachIndexed { i, v ->
                    appendLine(listOf(
                        s.date, s.title, s.place, s.intensity, (s.durationSec / 60).toString(), r.name, (i + 1).toString(),
                        v, if (r.seconds) "с" else "повт.", r.load.orEmpty(), s.wellbeing,
                    ).joinToString(";") { c -> c.replace(';', ',').replace('\n', ' ') })
                }
            }
        }
    }

    /** Удаляет историю, программу и незаконченную тренировку; настройки (инвентарь, оформление, Tabita) остаются. */
    fun wipe(ctx: Context) {
        SLog.i("Data", "удаление всех данных")
        files(ctx).forEach { it.delete() }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove("hcQueue").remove("deloadUntil").remove("deloadSnooze").commit()
    }

    fun write(ctx: Context, uri: Uri, text: String) {
        ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
    }

    fun read(ctx: Context, uri: Uri): String = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
}
