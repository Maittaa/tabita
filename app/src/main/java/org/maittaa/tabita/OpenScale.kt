package org.maittaa.tabita

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Показатель тела из openScale (вес, жир, вода, мышцы, ИМТ, кости, висцеральный жир…). */
data class BodyMetric(val id: String, val name: String, val unit: String, val value: Double)

/** Значение блока «Весы» за день: последнее взвешивание дня. */
data class BodyDay(val time: Long, val metrics: List<BodyMetric>) {
    fun toJson(): String = JSONObject().put("t", time).put("m", JSONArray(metrics.map {
        JSONObject().put("id", it.id).put("n", it.name).put("u", it.unit).put("v", it.value)
    })).toString()

    companion object {
        fun parse(s: String?): BodyDay? = runCatching {
            val o = JSONObject(s ?: return null)
            val a = o.getJSONArray("m")
            BodyDay(o.optLong("t"), (0 until a.length()).map {
                val m = a.getJSONObject(it)
                BodyMetric(m.getString("id"), m.optString("n"), m.optString("u"), m.getDouble("v"))
            })
        }.getOrNull()
    }
}

/** Единицы openScale (UCUM) → как показывать. */
fun bodyUnit(u: String): String = when (u) {
    "kg" -> t("кг"); "cm" -> t("см"); "%" -> "%"; "kcal", "kcal/d" -> t("ккал")
    "kg/m2", "1", "" -> ""; "a" -> t("лет"); else -> u
}

/**
 * Чтение данных openScale через его открытый ContentProvider
 * (content://<пакет>.provider/users и /measurements/<id>), разрешение <пакет>.READ_WRITE_DATA.
 */
class OpenScale(private val ctx: Context) {
    companion object {
        val packages = listOf(
            "com.health.openscale", "com.health.openscale.oss", "com.health.openscale.pro",
            "com.health.openscale.beta", "com.health.openscale.light", "com.health.openscale.debug",
        )
        private const val TAG = "openScale"
    }

    data class User(val id: Long, val name: String)

    fun installed(): List<String> = packages.filter {
        runCatching { ctx.packageManager.getPackageInfo(it, 0); true }.getOrDefault(false)
    }

    fun permission(pkg: String) = "$pkg.READ_WRITE_DATA"

    fun granted(pkg: String) =
        ContextCompat.checkSelfPermission(ctx, permission(pkg)) == PackageManager.PERMISSION_GRANTED

    private fun uri(pkg: String, path: String) = Uri.parse("content://$pkg.provider/$path")

    suspend fun apiVersion(pkg: String): Int? = withContext(Dispatchers.IO) {
        runCatching {
            ctx.contentResolver.query(uri(pkg, "meta"), null, null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getInt(c.getColumnIndexOrThrow("apiVersion")) else null
            }
        }.onFailure { Log.e(TAG, "meta", it) }.getOrNull()
    }

    suspend fun users(pkg: String): List<User> = withContext(Dispatchers.IO) {
        ctx.contentResolver.query(uri(pkg, "users"), null, null, null, null)?.use { c ->
            val idI = c.getColumnIndex("_ID"); val nI = c.getColumnIndex("username")
            buildList { while (c.moveToNext()) add(User(c.getLong(idI), if (nI >= 0) c.getString(nI) ?: "" else "")) }
        } ?: emptyList()
    }

    /** Все измерения пользователя, сгруппированные по дням (берётся последнее за день). */
    suspend fun readDays(pkg: String, userId: Long, from: LocalDate? = null): Map<LocalDate, BodyDay> = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val res = mutableMapOf<LocalDate, BodyDay>()
        ctx.contentResolver.query(uri(pkg, "measurements/$userId"), null, null, null, null)?.use { c ->
            val cols = c.columnNames.toList()
            Log.d(TAG, "столбцы: $cols, строк: ${c.count}")
            val dtI = c.getColumnIndex("datetime")
            val jsonI = c.getColumnIndex("values_json")
            while (c.moveToNext()) {
                val time = c.getLong(dtI)
                val day = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
                if (from != null && day < from) continue
                val metrics = if (jsonI >= 0 && !c.isNull(jsonI)) parseValues(c.getString(jsonI))
                else legacy(c, cols)
                if (metrics.isEmpty()) continue
                val prev = res[day]
                if (prev == null || time >= prev.time) res[day] = BodyDay(time, metrics)
            }
        }
        res
    }

    /** API v3: values_json = [{identity, name, unit, value|text, ...}] */
    private fun parseValues(json: String): List<BodyMetric> = runCatching {
        val a = JSONArray(json)
        (0 until a.length()).mapNotNull { i ->
            val o = a.getJSONObject(i)
            if (!o.has("value")) return@mapNotNull null
            val v = o.getDouble("value"); if (v.isNaN()) return@mapNotNull null
            val id = o.optString("identity").ifEmpty { o.optString("name") }
            BodyMetric(id, o.optString("name").ifEmpty { id.substringAfterLast(':').substringAfterLast('.') }, o.optString("unit"), v)
        }
    }.onFailure { Log.e(TAG, "values_json: $json", it) }.getOrDefault(emptyList())

    /** Старые версии openScale: отдельные столбцы weight/fat/water/muscle. */
    private fun legacy(c: android.database.Cursor, cols: List<String>): List<BodyMetric> {
        val map = listOf("weight" to (t("Вес") to "kg"), "fat" to (t("Жир") to "%"), "water" to (t("Вода") to "%"), "muscle" to (t("Мышцы") to "%"))
        return map.mapNotNull { (col, nu) ->
            val i = cols.indexOf(col)
            if (i < 0 || c.isNull(i)) null else BodyMetric("legacy:$col", nu.first, nu.second, c.getDouble(i)).takeIf { it.value > 0 }
        }
    }
}
