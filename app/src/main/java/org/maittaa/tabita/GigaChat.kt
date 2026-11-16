package org.maittaa.tabita

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

/**
 * GigaChat API (Сбер), бесплатный пакет для физлиц.
 * Ключ авторизации из Studio → временный токен (30 мин) → загрузка фото → ответ.
 * Одно фото на сообщение; сертификаты НУЦ Минцифры подключены в network_security_config только для доменов Сбера.
 */
object GigaChat {
    const val BASE = "https://api.giga.chat/v1"
    /** Адреса можно подменить в тестах. */
    internal var oauthUrl = "https://ngw.devices.sberbank.ru:9443/api/v2/oauth"
    internal var base = BASE
    private const val UA = "Tabita/Android"

    internal var token: String? = null
    private var tokenKey: String? = null
    private var expires = 0L

    /** Временный токен по ключу авторизации; кэшируется до истечения. */
    @Synchronized
    fun token(authKey: String, scope: String = "GIGACHAT_API_PERS"): String {
        val now = System.currentTimeMillis()
        token?.let { if (tokenKey == authKey && now < expires - 60_000) return it }
        val c = URL(oauthUrl).openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.connectTimeout = 20_000; c.readTimeout = 30_000; c.doOutput = true
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        c.setRequestProperty("Accept", "application/json")
        c.setRequestProperty("RqUID", UUID.randomUUID().toString())
        c.setRequestProperty("Authorization", "Basic " + authKey.removePrefix("Basic ").trim())
        c.setRequestProperty("User-Agent", UA)
        c.outputStream.use { it.write(("scope=" + URLEncoder.encode(scope, "UTF-8")).toByteArray()) }
        val o = read(c, "токен")
        val t = o.getString("access_token")
        // expires_at — Unix-время; бывает в миллисекундах
        val exp = o.optLong("expires_at", 0L).let { if (it in 1..9_999_999_999L) it * 1000 else it }
        token = t; tokenKey = authKey; expires = if (exp > 0) exp else now + 25 * 60_000
        Log.i("GigaChat", "получен токен до ${java.util.Date(expires)}")
        return t
    }

    /** Загрузка изображения; возвращает id файла. */
    fun upload(bearer: String, jpeg: ByteArray, name: String): String {
        val b = "----tabita" + UUID.randomUUID().toString().replace("-", "")
        val c = URL("$base/files").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.connectTimeout = 20_000; c.readTimeout = 60_000; c.doOutput = true
        c.setRequestProperty("Content-Type", "multipart/form-data; boundary=$b")
        c.setRequestProperty("Authorization", "Bearer $bearer")
        c.setRequestProperty("Accept", "application/json")
        c.setRequestProperty("User-Agent", UA)
        c.outputStream.use { out ->
            fun w(s: String) = out.write(s.toByteArray())
            w("--$b\r\nContent-Disposition: form-data; name=\"purpose\"\r\n\r\ngeneral\r\n")
            w("--$b\r\nContent-Disposition: form-data; name=\"file\"; filename=\"$name\"\r\nContent-Type: image/jpeg\r\n\r\n")
            out.write(jpeg)
            w("\r\n--$b--\r\n")
        }
        return read(c, "загрузка фото").getString("id")
    }

    private fun delete(bearer: String, id: String) = runCatching {
        val c = URL("$base/files/$id/delete").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.connectTimeout = 10_000; c.readTimeout = 10_000
        c.setRequestProperty("Authorization", "Bearer $bearer"); c.setRequestProperty("User-Agent", UA)
        c.responseCode
    }

    /** Тело запроса: каждое фото — отдельным сообщением (не больше одного на сообщение), вопрос — последним. */
    fun body(model: String, fileIds: List<String>, prompt: String): JSONObject {
        val msgs = JSONArray()
        fileIds.forEachIndexed { i, id ->
            val last = i == fileIds.size - 1
            msgs.put(JSONObject().put("role", "user")
                .put("content", if (last) prompt else t("Фото %d из %d", i + 1, fileIds.size))
                .put("attachments", JSONArray().put(id)))
        }
        if (fileIds.isEmpty()) msgs.put(JSONObject().put("role", "user").put("content", prompt))
        return JSONObject().put("model", model).put("temperature", 0.2).put("messages", msgs)
    }

    fun complete(prefs: Prefs, jpegs: List<ByteArray>, prompt: String): String {
        val bearer = try { token(prefs.aiKey) } catch (e: Exception) {
            Log.e("GigaChat", "токен: ${e.message}")
            error(t("GigaChat: не удалось войти по ключу авторизации — проверьте ключ (%s)", e.message ?: ""))
        }
        val ids = jpegs.mapIndexed { i, j -> upload(bearer, j, "meal_${i + 1}.jpg") }
        Log.i("GigaChat", "фото загружены: ${ids.size}, модель ${prefs.aiModel}")
        try {
            val r = Ai.post("$base/chat/completions", body(prefs.aiModel, ids, prompt),
                mapOf("Authorization" to "Bearer $bearer", "Accept" to "application/json", "User-Agent" to UA))
            return r.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        } finally {
            ids.forEach { delete(bearer, it) }
        }
    }

    private fun read(c: HttpURLConnection, what: String): JSONObject {
        val code = c.responseCode
        val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: ""
        if (code !in 200..299) {
            Log.e("GigaChat", "$what HTTP $code: $txt")
            val msg = runCatching { JSONObject(txt).getString("message") }.getOrDefault(txt.take(200))
            error("$what: HTTP $code $msg")
        }
        return JSONObject(txt)
    }
}
