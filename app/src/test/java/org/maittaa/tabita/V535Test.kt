package org.maittaa.tabita

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** 5.35: знакомство при первом запуске — шаги, профиль, вес, цель. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h900dp-xxhdpi")
class V535Test {
    @get:Rule val rule = createEmptyComposeRule()
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    private fun shot(s: ActivityScenario<MainActivity>, name: String) {
        rule.waitForIdle()
        s.onActivity { a ->
            val v = a.window.decorView
            val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(android.graphics.Canvas(it)) }
            val out = File(System.getProperty("user.dir"), "../shots").apply { mkdirs() }
            File(out, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun intro() {
        Prefs(ctx).apply { introDone = false; lang = "ru"; planGoal = "" }
        I18n.lang = "ru"
        ActivityScenario.launch(MainActivity::class.java).use { s ->
            shot(s, "15a_intro_welcome")
            rule.onNodeWithText("Начать").performClick(); shot(s, "15b_intro_features")
            rule.onNodeWithText("Далее").performClick()
            rule.onNodeWithText("Мужской").performClick()
            rule.onNodeWithText("Год рождения").performTextInput("1996")
            rule.onNodeWithText("Рост").performTextInput("183")
            rule.onNodeWithText("Вес").performTextInput("95"); shot(s, "15c_intro_about")
            rule.onNodeWithText("Далее").performClick(); shot(s, "15d_intro_goal")
            rule.onNodeWithText("Набор мышц").performScrollTo().performClick()
            rule.onNodeWithText("Далее").performClick(); shot(s, "15e_intro_health")
            rule.onNodeWithText("Далее").performClick(); shot(s, "15f_intro_notif")
            rule.onNodeWithText("Далее").performClick(); shot(s, "15g_intro_done")
            // дальше — подсказки с бесконечной анимацией: время двигаем вручную
            rule.mainClock.autoAdvance = false
            rule.onNodeWithText("Готово").performClick(); rule.mainClock.advanceTimeBy(2500)
            s.onActivity { a ->
                assertTrue(a.prefs.introDone)
                assertEquals("muscle", a.prefs.planGoal)
                assertEquals(183.0, a.repo.profile.value.heightCm!!, 0.01)
                assertEquals(1996, a.repo.profile.value.birthYear)
                assertEquals("95", a.repo.entries.value[LocalDate.now().toString()]!!["weight"])
                // удобная раскладка: цель сверху, оценка и сон — половинками
                val l = a.repo.layout.value
                assertEquals("goal", l.first().id)
                assertEquals(TileSize.HALF, l.first { it.id == "rate" }.size)
                assertEquals(TileSize.FULL, l.first { it.id == "food" }.size)
                assertTrue(a.prefs.tileHint && a.prefs.tileBtnHint)
            }
            rule.onNodeWithText("Плитки можно двигать").assertExists()
            shot(s, "15h_after_intro")
        }
    }
}
