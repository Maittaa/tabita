package org.maittaa.tabita

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 5.15: тренировки по видам, ходьба, ручные записи. */
@RunWith(RobolectricTestRunner::class)
class WorkoutsTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val t0 = 1_763_300_000_000L

    @Test fun kindsAndCounting() {
        assertEquals("strength", Workouts.fromHc(70).key)
        assertEquals("walk", Workouts.fromHc(79).key)
        assertEquals("run", Workouts.fromHc(57).key)
        assertEquals("other", Workouts.fromHc(0).key)
        val l = listOf(WorkoutSession("strength", 40), WorkoutSession("walk", 25))
        assertEquals(40, Workouts.counted(l, false)); assertEquals(65, Workouts.counted(l, true))
        val json = Workouts.toJson(l + WorkoutSession("run", 30, t0, "Утренний бег", "com.fitbit", "5 км"))
        assertEquals(WorkoutSession("run", 30, t0, "Утренний бег", "com.fitbit", "5 км"), Workouts.parse(json)[2])
        assertEquals("Силовая, ходьба", Workouts.summary(l))
    }

    @Test fun dedupeAcrossApps() {
        val a = WorkoutSession("run", 30, t0, src = "com.fitbit")
        val b = WorkoutSession("run", 28, t0 + 60_000, src = "com.google.android.apps.fitness")
        val c = WorkoutSession("strength", 40, t0 + 3_600_000, src = "com.google.android.apps.fitness")
        assertEquals(listOf(a, c), Workouts.dedupe(listOf(b, c, a)))
        // одно приложение, две подряд — не дубли
        assertEquals(2, Workouts.dedupe(listOf(a, a.copy(start = t0 + 10 * 60_000))).size)
    }

    @Test fun repoSyncedPlusManualAndWalkingToggle() {
        val r = Repo(ctx)
        var walk = false; r.countWalking = { walk }
        val ex = r.template.value.first { it.id == "ex" }
        val synced = listOf(WorkoutSession("strength", 45, t0, src = "com.fitbit"), WorkoutSession("walk", 30, t0 + 7_200_000, src = "com.fitbit"))
        r.setValues(mapOf("2026-11-16" to mapOf("ex" to "45", Workouts.syncedKey("ex") to Workouts.toJson(synced)!!)))
        assertEquals("45", r.entries.value["2026-11-16"]!!["ex"])
        // вручную + бег 20
        r.setWorkouts("2026-11-16", ex, listOf(WorkoutSession("run", 20)))
        assertEquals("65", r.entries.value["2026-11-16"]!!["ex"])
        // повторная синхронизация не теряет ручное
        r.setValues(mapOf("2026-11-16" to mapOf("ex" to "45", Workouts.syncedKey("ex") to Workouts.toJson(synced)!!)))
        assertEquals("65", r.entries.value["2026-11-16"]!!["ex"])
        // ходьба в зачёт
        walk = true; r.recountAllWorkouts()
        assertEquals("95", r.entries.value["2026-11-16"]!!["ex"])
        // старый день (до 5.15): только минуты, без списка — минуты остаются основой
        r.setValues(mapOf("2026-11-01" to mapOf("ex" to "50")))
        r.setWorkouts("2026-11-01", ex, listOf(WorkoutSession("yoga", 15)))
        assertEquals("65", r.entries.value["2026-11-01"]!!["ex"])
        r.setWorkouts("2026-11-01", ex, emptyList())
        assertEquals("50", r.entries.value["2026-11-01"]!!["ex"])
    }
}
