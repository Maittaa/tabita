package org.maittaa.tabita

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate

/** 5.32: резервная копия переносит всё — данные, тренировки, фото и настройки (кроме защиты и ключа ИИ); CSV полнее. */
@RunWith(RobolectricTestRunner::class)
class V532Test {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    @Before fun ru() { I18n.lang = "ru" }

    @Test fun backupRoundTrip() {
        val repo = Repo(ctx); val prefs = Prefs(ctx)
        val d = LocalDate.of(2026, 11, 18).toString()
        repo.setValue(d, "weight", "95")
        repo.setProfile(Profile(183.0, 1996, "m"))
        File(repo.photosDir, "p1.jpg").writeBytes(byteArrayOf(1, 2, 3))
        prefs.planGoal = "lose"; prefs.planW0 = 95.0; prefs.foodFav = setOf("seed_f1"); prefs.foodUse = mapOf("seed_f1" to 4)
        prefs.tipsEvery = 900; prefs.rhythmWake = 400; prefs.aiKey = "SECRET-A"; prefs.sp.edit().putString("pinHash", "OLDPIN").commit()
        val ts = org.maittaa.tabita.train.Storage(ctx)
        val prog = org.maittaa.tabita.train.Generator.generate(
            org.maittaa.tabita.train.GenSettings(org.maittaa.tabita.train.Goal.MASS, listOf(java.time.DayOfWeek.MONDAY), 30, 1, false),
            setOf(org.maittaa.tabita.train.Equipment.DUMBBELLS))
        ts.saveProgram(prog)
        val out = ByteArrayOutputStream(); repo.writeBackup(out)
        val bytes = out.toByteArray()
        val names = java.util.zip.ZipInputStream(bytes.inputStream()).use { z -> generateSequence { z.nextEntry?.name }.toList() }
        assertTrue(names.toString(), names.containsAll(listOf("data.json", "train.json", "settings.json", "photos/p1.jpg")))
        val st = String(java.util.zip.ZipInputStream(bytes.inputStream()).use { z -> while (z.nextEntry!!.name != "settings.json") {}; z.readBytes() })
        assertFalse(st.contains("SECRET-A")); assertFalse(st.contains("OLDPIN"))

        // «другой телефон»: всё стёрто, свой PIN и ключ ИИ
        repo.wipeAll(); repo.setProfile(Profile())
        File(repo.photosDir, "p1.jpg").delete(); ts.saveProgram(null)
        prefs.sp.edit().clear().putString("pinHash", "NEWPIN").putString("aiKey", "SECRET-B").commit()
        assertTrue(repo.importBackup(bytes))
        assertEquals("95", repo.entries.value[d]!!["weight"])
        assertEquals(183.0, repo.profile.value.heightCm!!, 0.01)
        assertTrue(File(repo.photosDir, "p1.jpg").exists())
        assertNotNull(org.maittaa.tabita.train.Storage(ctx).loadProgram())
        val p2 = Prefs(ctx)
        assertEquals("lose", p2.planGoal); assertEquals(95.0, p2.planW0, 0.01)
        assertEquals(setOf("seed_f1"), p2.foodFav); assertEquals(4, p2.foodUse["seed_f1"])
        assertEquals(900, p2.tipsEvery); assertEquals(400, p2.rhythmWake)
        assertEquals("NEWPIN", p2.sp.getString("pinHash", null)); assertEquals("SECRET-B", p2.aiKey)
    }

    @Test fun csvHasMore() {
        val repo = Repo(ctx)
        val d = LocalDate.of(2026, 11, 18)
        repo.setValue(d.toString(), "food", mealsToString(listOf(Meal(kind = "Обед", kcal = 600.0, protein = 40.0, fat = 20.0, carbs = 60.0))))
        val z = java.time.ZoneId.systemDefault()
        val start = d.minusDays(1).atTime(23, 10).atZone(z).toInstant().toEpochMilli(); val end = d.atTime(6, 25).atZone(z).toInstant().toEpochMilli()
        repo.setValue(d.toString(), "sleep", "405")
        repo.setValue(d.toString(), SleepDetail.key("sleep"), SleepDetail(start, end, listOf(SleepStage(0, 435, 2))).toJson())
        val csv = buildCsv(repo)
        val lines = csv.trim().lines()
        val head = lines[0].split(";")
        assertFalse(head.any { it.contains("Советы") || it == "Цель" || it == "Тренировка" })
        val row = lines.first { it.startsWith("18.11.2026") }.split(";")
        assertEquals("40", row[head.indexOfFirst { it.contains("белки") }])
        assertEquals("23:10", row[head.indexOfFirst { it.endsWith("отбой") }])
        assertEquals("6:25", row[head.indexOfFirst { it.endsWith("подъём") }])
    }
}
