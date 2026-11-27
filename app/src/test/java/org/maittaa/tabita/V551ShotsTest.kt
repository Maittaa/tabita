package org.maittaa.tabita

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneId

/** 5.51: снимки экрана аэробной нагрузки — с часами (зоны, пульс) и без Health Connect. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1250dp-xxhdpi")
class V551ShotsTest {
    init { Prefs(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()).lang = "ru" }
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @After fun reset() { CardioData.override = null }

    private fun save(name: String) {
        rule.waitForIdle()
        val v = rule.activity.window.decorView
        val b = android.graphics.Bitmap.createBitmap(v.width, v.height, android.graphics.Bitmap.Config.ARGB_8888).also { v.draw(android.graphics.Canvas(it)) }
        val out = java.io.File(System.getProperty("user.dir"), "../shots").apply { mkdirs() }
        java.io.File(out, "$name.png").outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun seed() {
        val repo = rule.activity.repo
        val today = LocalDate.now()
        val ex = repo.template.value.first { it.source == Source.HC_EXERCISE }.id
        val rhr = repo.template.value.first { it.source == Source.HC_RESTING_HR }.id
        fun at(d: LocalDate, h: Int, m: Int) = d.atTime(h, m).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        fun hist(vararg p: Pair<Int, Int>) = p.toMap()
        rule.runOnUiThread {
            repo.setProfile(Profile(178.0, 1996, "m"))
            val vals = mutableMapOf<String, Map<String, String>>()
            vals[today.key()] = mapOf(rhr to "60")
            val walk = WorkoutSession("walk", 38, at(today.minusDays(1), 8, 5), src = "com.google.android.apps.fitness", dist = 3100,
                hist = hist(105 to 600, 114 to 1200, 121 to 480), hr = 113, hrMax = 124)
            val run = WorkoutSession("run", 27, at(today.minusDays(3), 19, 30), src = "com.google.android.apps.fitness", dist = 4200,
                hist = hist(128 to 300, 142 to 900, 151 to 420, 172 to 60), hr = 143, hrMax = 175)
            vals[today.minusDays(1).key()] = mapOf(Workouts.syncedKey(ex) to Workouts.toJson(listOf(walk))!!)
            vals[today.minusDays(3).key()] = mapOf(Workouts.syncedKey(ex) to Workouts.toJson(listOf(run))!!)
            (1..8).forEach { w ->
                val d = today.minusWeeks(w.toLong())
                vals[d.key()] = mapOf(Workouts.syncedKey(ex) to Workouts.toJson(listOf(
                    WorkoutSession("walk", 30 + w * 3, at(d, 8, 0), src = "com.google.android.apps.fitness", dist = 2600 + w * 40,
                        hist = hist(112 to 900 + w * 60, 120 to 500), hr = 115 + w / 2)))!!)
            }
            repo.setValues(vals)
            val item = repo.template.value.first { it.id == ex }
            repo.setWorkouts(today.key(), item, listOf(WorkoutSession("aerobics", 25, at(today, 7, 10), "Аэробика дома", effort = 2,
                hist = hist(108 to 300, 118 to 480, 126 to 420, 140 to 300), hr = 122, hrMax = 146)))
            repo.setWorkouts(today.minusDays(5).key(), item, listOf(WorkoutSession("aerobics", 25, at(today.minusDays(5), 7, 0), "Аэробика дома", effort = 2,
                hist = hist(112 to 600, 125 to 600, 133 to 300), hr = 125)))
        }
        rule.waitForIdle()
    }

    private fun openAerobic() {
        // незаконченная силовая из других тестов (файлы train/ общие в JVM) закрыла бы вкладку экраном тренировки
        rule.runOnUiThread { rule.activity.train.discard(); rule.activity.train.screen = org.maittaa.tabita.train.Screen.Home }
        rule.onAllNodesWithText("Здоровье").let { it[it.fetchSemanticsNodes().size - 1] }.performClick()
        rule.waitForIdle()
        rule.runOnUiThread { rule.activity.train.tab = 0; rule.activity.train.section = org.maittaa.tabita.train.SEC_CARDIO }
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()
    }

    @Test fun withWatch() {
        CardioData.override = CardioData(true, true, true, true)
        seed(); openAerobic()
        save("51_cardio_watch")
        rule.onNodeWithTag("cardio_home").performScrollToNode(hasTestTag("cardio_zones"))
        save("51_cardio_zones")
        rule.onNodeWithTag("cardio_home").performScrollToNode(hasText("4,2 км", substring = true))
        rule.onNodeWithText("4,2 км", substring = true).performClick()
        rule.waitForIdle()
        // подробности: время в зонах (зоны по пульсу покоя 60 и максимуму 190)
        rule.onNodeWithText("Время в зонах").assertExists()
        rule.onNodeWithText("6:26 /км").assertExists()
    }

    @Test fun progress() {
        CardioData.override = CardioData(true, true, true, true)
        seed(); openAerobic()
        rule.onNodeWithText("Прогресс").performClick()
        rule.mainClock.advanceTimeBy(500)
        save("51_cardio_progress")
        rule.onNodeWithTag("cardio_progress").performScrollToNode(hasText("Темп ходьбы"))
        save("51_cardio_progress2")
    }

    @Test fun withoutHealthConnect() {
        openAerobic()
        save("51_cardio_nohc")
        rule.onNodeWithTag("cardio_home").performScrollToNode(hasText("Записать занятие вручную"))
        rule.onNodeWithText("Записать занятие вручную").performClick()
        rule.onNodeWithTag("cardio_home").performScrollToNode(hasTestTag("cardio_save"))
        save("51_cardio_manual")
    }

    @Test fun timer() {
        openAerobic()
        rule.onNodeWithTag("cardio_home").performScrollToNode(hasText("Аэробика дома"))
        rule.onAllNodesWithText("Аэробика дома")[0].performClick()
        rule.waitForIdle()
        save("51_timer_setup")
        rule.onNodeWithTag("cardio_go").performClick()
        rule.runOnUiThread {
            val d = CardioDraft.parse(rule.activity.prefs.cardioDraft)!!
            rule.activity.prefs.cardioDraft = d.copy(startedAt = System.currentTimeMillis() - 400_000).toJson()
        }
        rule.onNodeWithText("Прогресс").performClick(); rule.mainClock.advanceTimeBy(300)
        rule.onNodeWithText("Занятия").performClick(); rule.mainClock.advanceTimeBy(600)
        save("51_timer")
    }
}
