package org.maittaa.tabita

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 5.37: индекс «по цели»; напоминание пересобрать программу тренировок через 7 и 14 дней. */
@RunWith(RobolectricTestRunner::class)
class V537Test {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test fun autoIndex() {
        val repo = Repo(ctx)
        val base = MonthCfg(index = setOf("rate", "steps"), goals = mapOf("steps" to 12000.0), daily = setOf("work"), weekly = mapOf("tr" to 3), negative = setOf("used:Кофе"))
        val lose = base.autoApply(repo.template.value, Goal.LOSE)
        assertTrue(lose.auto)
        assertEquals(9000.0, lose.goals["steps"]!!, 0.1)
        assertEquals(420.0, lose.goals["sleep"]!!, 0.1)
        assertTrue(lose.daily.isEmpty() && lose.weekly.isEmpty())
        assertEquals(setOf("used:Кофе"), lose.negative)
        assertEquals(setOf("rate", "steps"), lose.index)
        val wb = base.autoApply(repo.template.value, Goal.WELLBEING)
        assertEquals(450.0, wb.goals["sleep"]!!, 0.1)
        assertTrue(MonthCfg.parse(wb.toJson()).auto)
    }

    @Test fun rebuildReminder() {
        val m = org.maittaa.tabita.train.AppModel(ctx.applicationContext as android.app.Application)
        val s = org.maittaa.tabita.train.GenSettings()
        m.generate(s, auto = true)
        val today = java.time.LocalDate.now()
        assertNull(m.rebuildDue(today))
        assertEquals(7, m.rebuildDue(today.plusDays(7)))
        assertEquals(14, m.rebuildDue(today.plusDays(15)))
        // пересобрал на 8-й день — следующее напоминание на 14-й
        val st = org.maittaa.tabita.train.Storage(ctx)
        st.autoStart = today.toEpochDay() - 8
        m.generate(s, auto = true)
        assertEquals(1, st.autoStage)
        assertNull(m.rebuildDue(today))
        st.autoStart = today.toEpochDay() - 14
        assertEquals(14, m.rebuildDue(today))
        m.generate(s, auto = true)
        assertEquals(2, st.autoStage); assertNull(m.rebuildDue(today.plusDays(30)))
    }
}
