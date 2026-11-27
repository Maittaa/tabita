package org.maittaa.tabita.train

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 1.6: резервная копия, восстановление, CSV, удаление. */
@RunWith(RobolectricTestRunner::class)
class BackupTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    private fun session(i: Int) = Session(
        id = "s$i", dayId = "a", title = "Тренировка А", date = "2026-11-1$i", startedAt = 0, durationSec = 1800,
        week = "1", wellbeing = "хорошо", notes = "", markdown = "", fileName = "", savedTo = null,
        exercises = listOf(ExResult("squat", "Приседания", false, "рюкзак 8 кг", listOf("12", "11"), false)),
    )

    @Test fun roundTrip() {
        val st = Storage(ctx)
        st.saveHistory(listOf(session(1), session(2)))
        st.saveProgram(Generator.generate(GenSettings(split = Split.UPPER_LOWER, days = Recommender.daysFor(4)), setOf(Equipment.DUMBBELLS)))
        st.themeMode = "dark"; st.heightCm = 183; st.equipment = setOf(Equipment.DUMBBELLS, Equipment.CHAIR)
        st.genSettings = GenSettings(split = Split.PPL)
        val text = Backup.build(ctx)
        assertTrue(Backup.describe(text)!!.contains("тренировок 2"))
        assertNull(Backup.describe("""{"a":1}"""))

        Backup.wipe(ctx)
        assertEquals(0, st.loadHistory().size); assertNull(st.loadProgram())
        assertEquals("dark", st.themeMode)   // настройки при удалении остаются

        st.themeMode = "light"; st.heightCm = 170
        Backup.restore(ctx, text)
        val st2 = Storage(ctx)
        assertEquals(2, st2.loadHistory().size)
        assertEquals(4, st2.loadProgram()!!.days.size)
        assertEquals("dark", st2.themeMode); assertEquals(183, st2.heightCm)
        assertEquals(setOf(Equipment.DUMBBELLS, Equipment.CHAIR), st2.equipment)
        assertEquals(Split.PPL, st2.genSettings.split)

        // журнал: запись не падает, уровень сохраняется
        SLog.i("Test", "проверка журнала"); SLog.e("Test", "ошибка", RuntimeException("x"))
        val csv = Backup.csv(st2.loadHistory())
        println(csv)
        assertEquals(1 + 4, csv.trim().lines().size)
        assertTrue(csv.contains("2026-11-11;Тренировка А;;;30;Приседания;1;12;повт.;рюкзак 8 кг;хорошо"))
    }
}

/** 5.23: копия из приложения Sink (app = "sink") переносится в тренировки Tabita, в общую копию Tabita тренировки входят. */
@RunWith(RobolectricTestRunner::class)
class MergeTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test fun sinkBackupIntoTabitaAndZip() {
        val sinkCopy = org.json.JSONObject().put("app", "sink").put("v", 1).put("date", "2026-11-19")
            .put("history", org.json.JSONArray().put(org.json.JSONObject(Storage(ctx).let { st ->
                st.saveHistory(listOf(Session(id = "x", dayId = "a", title = "Т", date = "2026-11-18", startedAt = 0, durationSec = 60,
                    week = "1", wellbeing = "", notes = "", markdown = "", fileName = "", savedTo = null, exercises = emptyList())))
                java.io.File(trainDir(ctx), "history.json").readText().let { org.json.JSONArray(it).getJSONObject(0).toString() }
            })))
            .put("prefs", org.json.JSONObject().put("rest", org.json.JSONObject().put("t", "i").put("v", 75)))
        Backup.wipe(ctx)
        Backup.restore(ctx, sinkCopy.toString())
        assertEquals(1, Storage(ctx).loadHistory().size)
        assertEquals(75, Storage(ctx).restOverride)
        // общая копия Tabita несёт train.json и восстанавливает его
        val repo = org.maittaa.tabita.Repo(ctx)
        val out = java.io.ByteArrayOutputStream(); repo.writeBackup(out)
        Backup.wipe(ctx)
        assertEquals(0, Storage(ctx).loadHistory().size)
        assertTrue(repo.importBackup(out.toByteArray()))
        assertEquals(1, Storage(ctx).loadHistory().size)
    }
}
