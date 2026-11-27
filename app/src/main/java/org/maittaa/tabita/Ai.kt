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
            (FoodDb.mineForAi().takeIf { it.isNotBlank() }?.let { "User's own products with exact label values — when the meal mentions one of them, use these numbers: $it. " } ?: "") +
            "Work ingredient by ingredient. Strict rules: " +
            "0) A measuring scoop (\"мерная ложка\", scoop) is NOT a serving: labels often define one serving as 2–3 scoops. " +
            "Convert by grams: 1 scoop × scoop weight, never 1 scoop = 1 serving. If the scoop weight is unknown, take ~8–9 g of powder per scoop " +
            "(~4–5 g for fiber/tea powders). A cap of liquid concentrate (\"колпачок\") ≈ 5 ml. " +
            "1) Never invent nutrients. Water, plain tea, black coffee, mineral water and ice have 0 kcal and 0 g of everything. " +
            "2) For branded or packaged products (Herbalife, protein powders, instant drinks, bars, yogurts) use the manufacturer's label per serving, " +
            "scaled to the stated amount. Instant tea, coffee and herbal drink powders give only a few kcal and practically no protein or fat " +
            "(e.g. 3 g of instant herbal drink powder in 250 ml water ≈ 5–10 kcal, protein 0 g, fat 0 g). " +
            "3) protein_g + fat_g + carbs_g of an ingredient can never exceed its weight in grams; for a dissolved powder count only the powder's weight. " +
            "4) kcal must match 4*protein + 4*carbs + 9*fat (± fiber and alcohol). " +
            "5) If a nutrient is absent or unknown, put 0 instead of guessing. " +
            "Answer ONLY with JSON, no markdown: " +
            "{\"items\": [{\"name\": string, \"g\": number (weight in grams; for drinks — of the dry/solid part only, water = its volume), " +
            "\"kcal\": number, \"protein_g\": number, \"fat_g\": number, \"carbs_g\": number}], " +
            "\"description\": \"short list of dishes with estimated grams, in ${if (I18n.isEn) "English" else "Russian"}\", " +
            "\"kcal\": number, \"protein_g\": number, \"fat_g\": number, \"carbs_g\": number (totals = sums of items), " +
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
        parse(text)
    }

    /** Ингредиент из ответа модели. */
    data class Part(val name: String, val g: Double?, val kcal: Double, val protein: Double, val fat: Double, val carbs: Double)

    /**
     * Проверка ингредиента (5.19): модели (особенно GigaChat) «досочиняют» белок и жир.
     * 1) Б+Ж+У не больше веса ингредиента — иначе пропорционально уменьшаем.
     * 2) Энергия из БЖУ не больше калорийности (с запасом 25 % + 3 ккал на клетчатку и округления) — иначе уменьшаем БЖУ.
     */
    fun sanitize(p: Part): Part {
        var (pr, f, c) = Triple(p.protein.coerceAtLeast(0.0), p.fat.coerceAtLeast(0.0), p.carbs.coerceAtLeast(0.0))
        val kcal = p.kcal.coerceAtLeast(0.0)
        val mass = pr + f + c
        if (p.g != null && p.g >= 0 && mass > p.g) {
            val k = if (mass > 0) p.g / mass else 0.0
            pr *= k; f *= k; c *= k
        }
        val e = 4 * pr + 4 * c + 9 * f
        if (e > kcal * 1.25 + 3) {
            val k = if (e > 0) kcal / e else 0.0
            pr *= k; f *= k; c *= k
        }
        if (pr != p.protein || f != p.fat || c != p.carbs)
            Log.i("AI", "поправка «${p.name}»: Б ${p.protein}→${"%.1f".format(pr)}, Ж ${p.fat}→${"%.1f".format(f)}, У ${p.carbs}→${"%.1f".format(c)}")
        return p.copy(kcal = kcal, protein = pr, fat = f, carbs = c)
    }

    /** Разбор ответа: если есть список ингредиентов — итоги считаем сами по проверенным позициям. */
    fun parse(text: String): Estimate {
        if (text.indexOf('{') < 0 || text.lastIndexOf('}') < text.indexOf('{')) error(t("Модель ответила не в нужном формате: %s", text.take(120)))
        val json = JSONObject(text.substring(text.indexOf('{'), text.lastIndexOf('}') + 1))
        fun JSONObject.num(k: String) = optDouble(k).takeIf { !it.isNaN() }
        val parts = json.optJSONArray("items")?.let { a ->
            (0 until a.length()).mapNotNull { i -> a.optJSONObject(i) }.mapNotNull { o ->
                val kcal = o.num("kcal") ?: return@mapNotNull null
                sanitize(Part(o.optString("name"), o.num("g"), kcal, o.num("protein_g") ?: 0.0, o.num("fat_g") ?: 0.0, o.num("carbs_g") ?: 0.0))
            }
        }.orEmpty()
        val gi = json.num("gi")?.coerceIn(0.0, 110.0)
        if (parts.isNotEmpty()) {
            val carbs = parts.sumOf { it.carbs }
            fun r(v: Double) = Math.round(v * 10) / 10.0
            return Estimate(
                kcal = Math.round(parts.sumOf { it.kcal }).toDouble(),
                protein = r(parts.sumOf { it.protein }), fat = r(parts.sumOf { it.fat }), carbs = r(carbs),
                description = cleanMealText(json.opt("description")?.toString().orEmpty()).ifBlank {
                    parts.joinToString(", ") { p -> p.name + (p.g?.let { " ~${Math.round(it)} г" } ?: "") }
                },
                gi = gi,
                // нагрузку пересчитываем по исправленным углеводам
                gl = gi?.let { r(it * carbs / 100) } ?: json.num("gl")?.coerceAtLeast(0.0),
            )
        }
        // старый формат — только итоги; проверяем их как одно целое
        val total = sanitize(Part("итого", null, json.num("kcal") ?: error(t("Модель не вернула калорийность")),
            json.num("protein_g") ?: 0.0, json.num("fat_g") ?: 0.0, json.num("carbs_g") ?: 0.0))
        return Estimate(
            kcal = total.kcal,
            protein = json.num("protein_g")?.let { total.protein }, fat = json.num("fat_g")?.let { total.fat },
            carbs = json.num("carbs_g")?.let { total.carbs },
            description = cleanMealText(json.opt("description")?.toString().orEmpty()),
            gi = gi, gl = json.num("gl")?.coerceAtLeast(0.0),
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
