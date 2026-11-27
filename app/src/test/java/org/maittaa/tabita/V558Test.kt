package org.maittaa.tabita

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertTextEquals
import androidx.activity.compose.setContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.maittaa.tabita.train.SEC_BODY
import org.maittaa.tabita.train.SEC_HUB
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** 5.58: «Тело» — замеры лентой, фото прогресса, состав тела с весов. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1600dp-xxhdpi")
class V558Test {
    init { Prefs(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()).lang = "ru" }
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun save(name: String) {
        rule.waitForIdle()
        val v = rule.activity.window.decorView
        val b = android.graphics.Bitmap.createBitmap(v.width, v.height, android.graphics.Bitmap.Config.ARGB_8888).also { v.draw(android.graphics.Canvas(it)) }
        val out = File(System.getProperty("user.dir"), "../shots").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun tapeLogic() {
        val m = mapOf("waist" to 82.5, "biceps" to 31.0)
        assertEquals(m, BodyLog.parseTape(BodyLog.tapeToString(m)))
        assertNull(BodyLog.tapeToString(emptyMap()))
        assertTrue(BodyLog.parseTape("bad").isEmpty())
        val d0 = LocalDate.of(2026, 10, 1)
        val s = listOf(d0 to 84.0, d0.plusDays(14) to 83.0, d0.plusDays(30) to 82.0)
        val c = BodyLog.change(s)!!
        assertEquals(82.0, c.value, 0.0); assertEquals(-2.0, c.delta!!, 1e-9); assertEquals(d0, c.since)
        assertNull(BodyLog.change(listOf(d0 to 80.0))!!.delta)
        // NICE: талия меньше половины роста
        assertEquals(0.45, BodyLog.whtr(81.0, 1.80), 1e-9)
        assertEquals(0, BodyLog.whtrBand(0.49)); assertEquals(1, BodyLog.whtrBand(0.5)); assertEquals(2, BodyLog.whtrBand(0.6))
        val sh = BodyLog.Shot("a.jpg", null)
        assertEquals(sh, BodyLog.parseShot(sh.toJson()))
        assertNull(BodyLog.Shot(null, null).toJson())
    }

    @Test fun bodySection() {
        val act = rule.activity; val repo = act.repo
        val today = LocalDate.now()
        File(repo.photosDir, "f1.jpg").writeBytes(byteArrayOf(1)); File(repo.photosDir, "f2.jpg").writeBytes(byteArrayOf(1))
        rule.runOnUiThread {
            act.train.discard(); act.train.saveProgram(null); act.train.section = SEC_HUB
            repo.setProfile(Profile(180.0, 1995, "m"))
            repo.setValues(mapOf(
                today.minusDays(30).key() to mapOf("weight" to "74.0", BodyLog.TAPE to BodyLog.tapeToString(mapOf("waist" to 80.0, "biceps" to 30.0))!!,
                    BodyLog.PHOTO to BodyLog.Shot("f1.jpg", null).toJson()!!),
                today.key() to mapOf("weight" to "75.5", BodyLog.TAPE to BodyLog.tapeToString(mapOf("waist" to 80.5, "biceps" to 31.5))!!),
            ))
        }
        assertEquals(1.5, BodyLog.change(BodyLog.series(repo, "biceps"))!!.delta!!, 1e-9)
        rule.onAllNodesWithText("Здоровье").let { it[it.fetchSemanticsNodes().size - 1] }.performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("hub_body").performScrollTo()
        save("58_hub")
        rule.onNodeWithTag("hub_body").performClick()
        rule.waitForIdle()
        assertEquals(SEC_BODY, act.train.section)
        rule.onNodeWithTag("body_scale").assertExists()
        rule.onNodeWithTag("tape_biceps").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Талия к росту").performScrollTo()
        rule.onNodeWithText("0,45").assertExists()     // 80,5 / 180
        save("58_measures")
        rule.onAllNodes(hasText("Фото") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))[0].performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("body_today").assertExists()
        // второе фото — сегодня; старое при замене удаляется
        rule.runOnUiThread { BodyLog.setShot(repo, today, false, "f2.jpg") }
        rule.waitForIdle()
        rule.onNodeWithTag("body_compare").assertExists()
        save("58_photos")
        File(repo.photosDir, "f3.jpg").writeBytes(byteArrayOf(1))
        rule.runOnUiThread { BodyLog.setShot(repo, today, false, "f3.jpg") }
        assertFalse(File(repo.photosDir, "f2.jpg").exists())
        assertEquals("f3.jpg", BodyLog.shots(repo).last().second.front)
        assertTrue(Changelog.all.any { it.version == "5.58" })
    }

    /** 5.58.1: ГИ и ГН — строкой, ГН из ГИ и углеводов; поля — по «Изменить». */
    @Test fun glycemicAuto() {
        val act = rule.activity
        val m = Meal(kind = "Ужин", text = "Сэндвич", kcal = 299.0, protein = 10.0, fat = 19.0, carbs = 17.0, gi = 50.0)
        assertEquals(8.5, m.glEff!!, 1e-9)
        rule.activity.runOnUiThread {
            act.setContent { AppTheme { androidx.compose.foundation.layout.Column { MealCard(act, "", m, {}, {}, {}, {}) } } }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("gi_line").assertTextEquals("ГИ 50 · ГН 9")
        save("581_meal_card")
        rule.onNodeWithTag("gi_edit").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("ГИ").assertExists()
    }
}
