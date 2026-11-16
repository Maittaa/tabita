package org.maittaa.tabita

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Оценка калорийности блюда по фото через внешнюю модель с распознаванием изображений. */
object Ai {
    data class Estimate(val kcal: Double, val protein: Double?, val fat: Double?, val carbs: Double?, val description: String,
                        val gi: Double? = null, val gl: Double? = null)

    private fun prompt(note: String, hint: String) = (
        "You are a nutritionist. Estimate the food on the photo(s) or from the description: identify dishes and portion sizes, " +
            "then estimate total calories and macronutrients for everything shown. " +
            (if (note.isNotBlank()) "User's description of the meal: \"$note\". " else "") +
            (if (hint.isNotBlank()) "User's clarification — treat it as reliable and prefer it over visual guessing " +
                "(plate or glass size, volume, weight, ingredient proportions in percent, cooking method): \"$hint\". " +
                "If proportions or volume are given, compute the amount of each ingredient from them. " else "") +
            "Answer ONLY with JSON, no markdown: " +
            "{\"description\": \"short list of dishes with estimated grams, in ${if (I18n.isEn) "English" else "Russian"}\", " +
            "\"kcal\": number, \"protein_g\": number, \"fat_g\": number, \"carbs_g\": number, " +
            "\"gi\": number (glycemic index of the whole meal, 0-100, each food's GI weighted by its share of carbohydrates; " +
            "account for cooking, fat, protein and fiber), \"gl\": number (glycemic load = gi * carbs_g / 100)}"
        )

    private fun jpeg(f: File): ByteArray {
        val bmp = Photos.decode(f, 1024) ?: error("photo")
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
        return out.toByteArray()
    }

    private fun b64(f: File): String = Base64.encodeToString(jpeg(f), Base64.NO_WRAP)

    suspend fun estimate(prefs: Prefs, photos: List<File>, note: String, hint: String = ""): Estimate = withContext(Dispatchers.IO) {
        require(photos.isNotEmpty() || note.isNotBlank() || hint.isNotBlank()) { t("Добавьте фото или опишите блюдо") }
        val images = if (prefs.aiProvider == "gigachat") emptyList() else photos.take(4).map { b64(it) }
        Log.i("AI", "запрос: ${prefs.aiProvider} ${prefs.aiUrl} модель ${prefs.aiModel}, фото ${photos.size}, уточнение: ${hint.isNotBlank()}")
        val text = if (prefs.aiProvider == "gigachat") {
            GigaChat.complete(prefs, photos.take(4).map { jpeg(it) }, prompt(note, hint))
        } else if (prefs.aiProvider == "anthropic") {
            val content = JSONArray()
            images.forEach {
                content.put(JSONObject().put("type", "image").put("source",
                    JSONObject().put("type", "base64").put("media_type", "image/jpeg").put("data", it)))
            }
            content.put(JSONObject().put("type", "text").put("text", prompt(note, hint)))
            val body = JSONObject().put("model", prefs.aiModel).put("max_tokens", 1000)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
            val resp = post(
                "https://api.anthropic.com/v1/messages", body,
                mapOf("x-api-key" to prefs.aiKey, "anthropic-version" to "2023-06-01"),
            )
            resp.getJSONArray("content").getJSONObject(0).getString("text")
        } else {
            val content = JSONArray().put(JSONObject().put("type", "text").put("text", prompt(note, hint)))
            images.forEach {
                content.put(JSONObject().put("type", "image_url")
                    .put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$it")))
            }
            val body = JSONObject().put("model", prefs.aiModel).put("temperature", 0.2)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
            val resp = post(prefs.aiUrl.trimEnd('/') + "/chat/completions", body, mapOf("Authorization" to "Bearer ${prefs.aiKey}"))
            resp.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        }
        Log.d("AI", "ответ: $text")
        if (text.indexOf('{') < 0 || text.lastIndexOf('}') < text.indexOf('{')) error(t("Модель ответила не в нужном формате: %s", text.take(120)))
        val json = JSONObject(text.substring(text.indexOf('{'), text.lastIndexOf('}') + 1))
        fun num(k: String) = json.optDouble(k).takeIf { !it.isNaN() }
        Estimate(
            kcal = num("kcal") ?: error(t("Модель не вернула калорийность")),
            protein = num("protein_g"), fat = num("fat_g"), carbs = num("carbs_g"),
            description = cleanMealText(json.opt("description")?.toString().orEmpty()),
            gi = num("gi")?.coerceIn(0.0, 110.0), gl = num("gl")?.coerceAtLeast(0.0),
        )
    }

    internal fun post(url: String, body: JSONObject, headers: Map<String, String>): JSONObject {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 20_000; c.readTimeout = 90_000
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = c.responseCode
        val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: ""
        if (code !in 200..299) {
            val msg = runCatching { JSONObject(txt).getJSONObject("error").getString("message") }
                .recoverCatching { JSONObject(txt).getString("message") }.getOrDefault(txt.take(200))
            Log.e("AI", "HTTP $code: $txt")
            error("HTTP $code: $msg")
        }
        return JSONObject(txt)
    }
}
