package org.maittaa.tabita

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/** 5.18: выдача данных приложению «Подход». */
@RunWith(RobolectricTestRunner::class)
class TrainingProviderTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test fun summary() {
        val repo = Repo(ctx)
        val today = LocalDate.of(2026, 11, 17)
        repo.setValue("2026-11-17", "sleep", "412")
        repo.setValue("2026-11-17", "rhr", "61")
        repo.setValue("2026-11-16", "steps", "16200")
        repo.setValue("2026-11-16", "rate", "3")
        repo.setValue("2026-11-15", "weight", "71.8")
        repo.setValue("2026-09-01", "weight", "70")
        repo.addCheckup(Checkup("2026-11-10", 1L, mapOf("pss4" to BlockResult(10.0, listOf(2, 2, 3, 3)))))
        val out = JSONObject(TrainingProvider.build(repo.toJson(), 35, today))
        println(out.toString(2))
        val days = out.getJSONArray("days")
        val byDate = (0 until days.length()).associate { days.getJSONObject(it).let { d -> d.getString("d") to d } }
        assertEquals(412.0, byDate["2026-11-17"]!!.getDouble("sleep"), 0.01)
        assertEquals(61.0, byDate["2026-11-17"]!!.getDouble("rhr"), 0.01)
        assertEquals(16200.0, byDate["2026-11-16"]!!.getDouble("steps"), 0.01)
        assertEquals(10.0, byDate["2026-11-16"]!!.getDouble("rmax"), 0.01)
        assertEquals(71.8, byDate["2026-11-15"]!!.getDouble("w"), 0.01)
        assertTrue("за пределами периода", "2026-09-01" !in byDate)
        assertNotNull(out.getJSONObject("checkup").optDouble("overall"))
    }
}
