package org.maittaa.tabita

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.MatrixCursor
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime
import java.time.ZoneId

/** Поддельный openScale: те же пути и столбцы, что у настоящего провайдера (API v3). */
class FakeOpenScale : ContentProvider() {
    override fun onCreate() = true
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?) = when {
        uri.path == "/meta" -> MatrixCursor(arrayOf("apiVersion", "versionCode")).apply { addRow(arrayOf(3, 313)) }
        uri.path == "/users" -> MatrixCursor(arrayOf("_ID", "username")).apply { addRow(arrayOf(1L, "Я")) }
        uri.path!!.startsWith("/measurements/") -> MatrixCursor(arrayOf("_ID", "datetime", "values_json")).apply {
            fun ms(d: LocalDateTime) = d.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val j = { w: Double, f: Double -> """[{"identity":"os:weight","name":"Вес","unit":"kg","inputType":"FLOAT","value":$w},
                {"identity":"os:fat","name":"Жир","unit":"%","inputType":"FLOAT","value":$f},
                {"identity":"os:bmi","name":"ИМТ","unit":"kg/m2","inputType":"FLOAT","isDerived":true,"value":29.1},
                {"identity":"os:comment","name":"Комментарий","unit":"","inputType":"TEXT","text":"утро"}]""" }
            addRow(arrayOf(1, ms(LocalDateTime.of(2026, 11, 10, 7, 30)), j(95.2, 24.0)))
            addRow(arrayOf(2, ms(LocalDateTime.of(2026, 11, 10, 21, 0)), j(95.9, 24.3))) // позже в тот же день — берём его
            addRow(arrayOf(3, ms(LocalDateTime.of(2026, 11, 11, 7, 10)), j(94.8, 23.9)))
        }
        else -> null
    }
    override fun getType(uri: Uri) = null
    override fun insert(uri: Uri, v: ContentValues?) = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
}

@RunWith(RobolectricTestRunner::class)
class V3Test {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test fun countersCompatible() {
        assertEquals(mapOf("Кофе" to 1, "Чай" to 1), parseCounts("Кофе${MULTI_SEP}Чай")) // старый формат
        val m = parseCounts(countsToString(linkedMapOf("Кофе" to 3, "Чай" to 1, "Сок" to 0)))
        assertEquals(mapOf("Кофе" to 3, "Чай" to 1), m)
        println("COUNTERS_OK")
    }

    @Test fun openScaleReadsAllMetrics() = runBlocking {
        Robolectric.buildContentProvider(FakeOpenScale::class.java)
            .create(ProviderInfo().apply { authority = "com.health.openscale.provider" })
        val os = OpenScale(ctx)
        assertEquals(3, os.apiVersion("com.health.openscale"))
        assertEquals("Я", os.users("com.health.openscale").single().name)
        val days = os.readDays("com.health.openscale", 1)
        assertEquals(2, days.size)
        val d10 = days[java.time.LocalDate.of(2026, 11, 10)]!!
        assertEquals(95.9, d10.metrics.first { it.id == "os:weight" }.value, 0.001)
        assertEquals(listOf("Вес", "Жир", "ИМТ"), d10.metrics.map { it.name })
        // в дневник: блок «Весы» добавляется, отдельный «Вес» скрывается
        val repo = Repo(ctx)
        assertTrue(repo.ensureBodyItem())
        repo.setBodyDays(days.mapKeys { it.key.key() })
        assertTrue(repo.template.value.first { it.id == "weight" }.hidden)
        assertEquals(94.8, BodyDay.parse(repo.entries.value["2026-11-11"]!!["body"])!!.metrics[0].value, 0.001)
        // ручная правка не перезаписывается
        repo.setManual("2026-11-11", "body", BodyDay(0, listOf(BodyMetric("os:weight", "Вес", "kg", 94.0))).toJson(), true)
        repo.setBodyDays(days.mapKeys { it.key.key() })
        assertEquals(94.0, BodyDay.parse(repo.entries.value["2026-11-11"]!!["body"])!!.metrics[0].value, 0.001)
        println("OPENSCALE_OK")
    }

    @Test fun logWritesFile() {
        Prefs(ctx).logLevel = Log.FULL
        Log.init(ctx)
        Log.i("Test", "привет"); Log.e("Test", "ошибка", RuntimeException("boom"))
        Log.crash(IllegalStateException("crash"))
        val u = Log.shareUri()
        println("LOG_URI=$u")
        val dirs = listOfNotNull(ctx.getExternalFilesDir(null), ctx.filesDir).map { java.io.File(it, "logging") } + ctx.filesDir
        val text = dirs.flatMap { it.listFiles()?.toList() ?: emptyList() }.filter { it.name.endsWith(".log") }
            .joinToString("\n") { it.readText() } +
            (u?.let { runCatching { ctx.contentResolver.openInputStream(it)!!.readBytes().toString(Charsets.UTF_8) }.getOrNull() } ?: "")
        assertTrue(text, text.contains("I/Test: привет") && text.contains("boom") && text.contains("CRASH"))
        println("LOG_OK\n" + text.lines().take(3).joinToString("\n"))
    }
}
