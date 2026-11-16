package org.maittaa.tabita

import android.app.AlarmManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LogicTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test fun migrationFromV1KeepsDataAndAddsBlocks() {
        val v1 = """{"version":1,"template":[{"id":"sleep","name":"Сон","type":"DURATION","source":"HC_SLEEP"},
            {"id":"weight","name":"Вес","type":"NUMBER","unit":"кг","source":"NONE"},
            {"id":"why","name":"Почему / заметка","type":"TEXT"}],
            "entries":{"2026-11-10":{"sleep":"420","weight":"95.5"}},"monthNotes":{"2026-11":"итог"}}"""
        File(ctx.filesDir, "data.json").writeText(v1)
        val r = Repo(ctx)
        val ids = r.template.value.map { it.id }
        assertEquals(listOf("sleep", "weight", "food", "money", "why", "water", "wb"), ids)
        assertEquals(Source.HC_WEIGHT, r.template.value.first { it.id == "weight" }.source)
        assertEquals("420", r.entries.value["2026-11-10"]!!["sleep"])
        assertEquals("итог", r.monthNotes.value["2026-11"])
        println("MIGRATION_OK")
    }

    @Test fun sourceLabelsAndWaterMigration() {
        AppNames.init(ctx)
        val item = TemplateItem(id = "steps", name = "Шаги", type = ItemType.NUMBER, source = Source.HC_STEPS)
        assertEquals("из Fitbit + Mi Fitness", sourceCaption(item, mapOf("steps" to "5000", "steps!src" to "com.fitbit.FitbitMobile,com.xiaomi.wearable"), true))
        assertEquals("из Health Connect", sourceCaption(item, mapOf("steps" to "5000"), true))
        assertEquals("исправлено вручную", sourceCaption(item, mapOf("steps" to "5000", "steps!m" to "1"), true))
        val v2 = """{"version":2,"template":[{"id":"used","name":"Что использовалось","type":"MULTI"},{"id":"why","name":"Заметка","type":"TEXT"}],"entries":{}}"""
        File(ctx.filesDir, "data.json").writeText(v2)
        val r = Repo(ctx)
        assertEquals(listOf("used", "water", "why", "wb"), r.template.value.map { it.id })
        assertEquals(2.0, r.template.value.first { it.id == "water" }.max, 0.0)
        println("SOURCES_WATER_OK")
    }

    @Test fun manualValuesSurviveSync() {
        val r = Repo(ctx)
        r.setValues(mapOf("2026-11-11" to mapOf("steps" to "5000")))
        r.setManual("2026-11-11", "steps", "7000", true)
        r.setValues(mapOf("2026-11-11" to mapOf("steps" to "5100", "sleep" to "400")))
        assertEquals("7000", r.entries.value["2026-11-11"]!!["steps"])
        assertEquals("400", r.entries.value["2026-11-11"]!!["sleep"])
        r.setManual("2026-11-11", "steps", null, false)
        r.setValues(mapOf("2026-11-11" to mapOf("steps" to "5200")))
        assertEquals("5200", r.entries.value["2026-11-11"]!!["steps"])
        println("MANUAL_OK")
    }

    @Test fun balanceFollowsPurchases() {
        val r = Repo(ctx)
        val today = LocalDate.now().key()
        r.setValue(today, "money", purchasesToString(listOf(Purchase(name = "Хлеб", qty = 2.0, price = 50.0))))
        r.setBalance(10_000.0)
        assertEquals(10_000.0, r.balanceOn(today)!!, 0.001)
        // ещё покупка и доход после установки баланса
        r.setValue(today, "money", purchasesToString(listOf(
            Purchase(name = "Хлеб", qty = 2.0, price = 50.0),
            Purchase(name = "Молоко", qty = 1.0, price = 90.0),
            Purchase(name = "Зарплата", qty = 1.0, price = 1000.0, income = true),
        )))
        assertEquals(10_910.0, r.balanceOn(today)!!, 0.001)
        println("BALANCE_OK")
    }

    @Test fun balanceChainWithFixes() {
        val r = Repo(ctx)
        fun buy(d: String, sum: Double) = r.setValue(d, "money", purchasesToString(listOf(Purchase(name = "x", price = sum))))
        r.setBalance(1000.0, "2026-11-01")
        buy("2026-11-02", 200.0)
        assertEquals(800.0, r.balanceOn("2026-11-02")!!, 0.001)
        assertEquals(800.0, r.balanceOn("2026-11-05")!!, 0.001)       // дни без операций — тот же остаток
        r.setBalance(500.0, "2026-11-03")                               // правка задним числом
        buy("2026-11-04", 50.0)
        assertEquals(450.0, r.balanceOn("2026-11-04")!!, 0.001)
        assertEquals(800.0, r.balanceOn("2026-11-02")!!, 0.001)       // раньше правки — по старой цепочке
        r.setBalance(null, "2026-11-03")
        assertEquals(750.0, r.balanceOn("2026-11-04")!!, 0.001)
        // старый формат (одна опорная точка) читается
        File(ctx.filesDir, "data.json").writeText("""{"version":3,"template":[{"id":"money","name":"Финансы","type":"EXPENSES"}],"entries":{},"balance":{"date":"2026-11-10","amount":25618.7,"dayNet":0}}""")
        assertEquals(25618.7, Repo(ctx).balanceOn("2026-11-12")!!, 0.001)
        println("BALANCE_CHAIN_OK")
    }

    @Test fun iconSwitch() {
        assertEquals("diary", AppIcons.current(ctx))
        AppIcons.set(ctx, "woman")
        assertEquals("woman", AppIcons.current(ctx))
        println("ICON_OK")
    }

    @Test fun backupZipRoundTrip() {
        val r = Repo(ctx)
        File(r.photosDir, "a.jpg").writeBytes(byteArrayOf(1, 2, 3))
        r.setValue("2026-11-01", "food", mealsToString(listOf(Meal(text = "суп", photos = listOf("a.jpg"), kcal = 300.0))))
        val out = ByteArrayOutputStream(); r.writeBackup(out)
        File(r.photosDir, "a.jpg").delete()
        r.setValue("2026-11-01", "food", null)
        assertTrue(r.importBackup(out.toByteArray()))
        assertEquals(300.0, parseMeals(r.entries.value["2026-11-01"]!!["food"]).first().kcal!!, 0.0)
        assertTrue(File(r.photosDir, "a.jpg").exists())
        println("BACKUP_OK")
    }

    @Test fun remindersScheduled() {
        val p = Prefs(ctx)
        p.reminderOn = true; p.motivOn = true; p.motivPerDay = 3; p.motivFrom = 0; p.motivTo = 24 * 60 - 1
        Reminders.scheduleAll(ctx)
        val am = shadowOf(ctx.getSystemService(AlarmManager::class.java))
        val n = am.scheduledAlarms.size
        println("ALARMS=$n")
        assertTrue(n >= 2) // ежедневное + план на завтра (+ оставшиеся сегодня мотивации)
    }

    @Test fun pinHashing() {
        val p = Prefs(ctx)
        p.setPin("2580")
        assertTrue(p.checkPin("2580")); assertTrue(!p.checkPin("0000"))
        println("PIN_OK")
    }

    private fun photo(): File {
        val f = File(ctx.cacheDir, "meal.jpg")
        val b = Bitmap.createBitmap(200, 150, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(200, 120, 40)) }
        f.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return f
    }

    @Test fun aiOpenAiCompatible() = runBlocking {
        val content = """```json
{"description":"Гречка 200 г, котлета 120 г","kcal":612.4,"protein_g":38,"fat_g":22,"carbs_g":60}
```"""
        val reply = JSONObject().put("choices", org.json.JSONArray().put(JSONObject().put("message", JSONObject().put("content", content)))).toString()
        val srv = MockWebServer().apply { enqueue(MockResponse().setBody(reply)); start() }
        val p = Prefs(ctx).apply { aiProvider = "openai"; aiUrl = srv.url("/v1").toString(); aiKey = "k"; aiModel = "m" }
        val e = Ai.estimate(p, listOf(photo()), "обед")
        val req = srv.takeRequest()
        srv.shutdown()
        assertEquals("/v1/chat/completions", req.path)
        assertEquals("Bearer k", req.getHeader("Authorization"))
        val parts = JSONObject(req.body.readUtf8()).getJSONArray("messages").getJSONObject(0).getJSONArray("content")
        assertTrue((0 until parts.length()).any { parts.getJSONObject(it).optString("type") == "image_url" })
        assertEquals(612.4, e.kcal, 0.01); assertEquals(38.0, e.protein!!, 0.0)
        assertNotNull(e.description)
        println("AI_OPENAI_OK ${e.description}")
    }

    @Test fun aiGigaChat() = runBlocking {
        val srv = MockWebServer()
        val exp = System.currentTimeMillis() + 30 * 60_000
        srv.enqueue(MockResponse().setBody(JSONObject().put("access_token", "tok").put("expires_at", exp).toString()))
        srv.enqueue(MockResponse().setBody("""{"id":"f1","object":"file"}"""))
        srv.enqueue(MockResponse().setBody("""{"id":"f2","object":"file"}"""))
        val content = "Вот оценка: {\"description\":\"Рёбрышки 150 г, салат 100 г\",\"kcal\":540,\"protein_g\":32,\"fat_g\":38,\"carbs_g\":9}"
        srv.enqueue(MockResponse().setBody(JSONObject().put("choices", org.json.JSONArray().put(JSONObject().put("message", JSONObject().put("content", content)))).toString()))
        srv.enqueue(MockResponse().setBody("{}")); srv.enqueue(MockResponse().setBody("{}"))
        srv.start()
        GigaChat.oauthUrl = srv.url("/api/v2/oauth").toString(); GigaChat.base = srv.url("/v1").toString().trimEnd('/'); GigaChat.token = null
        val p = Prefs(ctx).apply { aiProvider = "gigachat"; aiKey = "QUJDOmRlZg=="; aiModel = "GigaChat-2-Pro" }
        val e = Ai.estimate(p, listOf(photo(), photo()), "")
        val oauth = srv.takeRequest()
        assertEquals("/api/v2/oauth", oauth.path)
        assertEquals("Basic QUJDOmRlZg==", oauth.getHeader("Authorization"))
        assertNotNull(oauth.getHeader("RqUID"))
        assertEquals("scope=GIGACHAT_API_PERS", oauth.body.readUtf8())
        val up = srv.takeRequest()
        assertEquals("/v1/files", up.path); assertEquals("Bearer tok", up.getHeader("Authorization"))
        assertTrue(up.getHeader("Content-Type")!!.startsWith("multipart/form-data; boundary="))
        val upBody = up.body.readByteArray().toString(Charsets.ISO_8859_1)
        assertTrue(upBody.contains("name=\"purpose\"") && upBody.contains("general") && upBody.contains("filename=\"meal_1.jpg\""))
        srv.takeRequest()
        val chat = srv.takeRequest()
        assertEquals("/v1/chat/completions", chat.path)
        val msgs = JSONObject(chat.body.readUtf8()).getJSONArray("messages")
        assertEquals(2, msgs.length())
        assertEquals("f1", msgs.getJSONObject(0).getJSONArray("attachments").getString(0))
        assertEquals(1, msgs.getJSONObject(1).getJSONArray("attachments").length())
        assertTrue(msgs.getJSONObject(1).getString("content").contains("JSON"))
        assertEquals("/v1/files/f1/delete", srv.takeRequest().path)
        assertEquals(540.0, e.kcal, 0.0); assertEquals(38.0, e.fat!!, 0.0)
        // токен кэшируется
        assertEquals("tok", GigaChat.token("QUJDOmRlZg=="))
        assertEquals(6, srv.requestCount)
        srv.shutdown()
        println("AI_GIGACHAT_OK ${e.description}")
    }

    @Test fun aiHintAndTextOnly() = runBlocking {
        val m = Meal(text = "смузи", hint = "400 мл — банан 40%, молоко 60%")
        assertEquals(m.hint, Meal.fromJson(m.toJson()).hint)
        val reply = JSONObject().put("choices", org.json.JSONArray().put(JSONObject().put("message",
            JSONObject().put("content", "{\"description\":\"Смузи 400 мл\",\"kcal\":290,\"protein_g\":10,\"fat_g\":7,\"carbs_g\":48}")))).toString()
        val srv = MockWebServer().apply { enqueue(MockResponse().setBody(reply)); start() }
        val p = Prefs(ctx).apply { aiProvider = "openai"; aiUrl = srv.url("/v1").toString(); aiKey = "k"; aiModel = "m" }
        val e = Ai.estimate(p, emptyList(), m.text, m.hint)
        val body = srv.takeRequest().body.readUtf8(); srv.shutdown()
        assertTrue(body.contains("банан 40%") && body.contains("clarification"))
        assertTrue(!body.contains("image_url"))
        assertEquals(290.0, e.kcal, 0.0)
    }
}
