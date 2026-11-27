package org.maittaa.tabita

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** 5.41: инструкция подключения весов через openScale, короче окно «Калории по фото». */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1400dp-xxhdpi")
class V541Test {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun steps() {
        assertEquals(5, OsGuide.steps().size)
        assertTrue(OsGuide.FDROID.contains("com.health.openscale"))
        assertTrue(OsGuide.PLAY.endsWith("com.health.openscale.oss"))
    }

    @Test fun cardOpenAndLinks() {
        rule.runOnUiThread { rule.activity.setContent { MaterialTheme { OpenScaleGuideCard(open0 = true) } } }
        rule.waitForIdle()
        // вне основного экрана t() отдаёт английский
        rule.onNodeWithText("How to connect the scale").assertExists()
        rule.onNodeWithText("Weigh yourself — the measurement should appear in openScale.").assertExists()
        rule.onNodeWithText("F-Droid").performClick()
        assertEquals(OsGuide.FDROID, shadowOf(rule.activity).nextStartedActivity.dataString)
        rule.onNodeWithText("Google Play").performClick()
        assertEquals(OsGuide.PLAY, shadowOf(rule.activity).nextStartedActivity.dataString)
    }

    @Test fun cardClosed() {
        rule.runOnUiThread { rule.activity.setContent { MaterialTheme { OpenScaleGuideCard(open0 = false) } } }
        rule.waitForIdle()
        rule.onNodeWithText("via openScale · steps: 5").assertExists()
        rule.onNodeWithText("F-Droid").assertDoesNotExist()
    }

    @Test fun changelogFirst() {
        assertTrue(Changelog.all.any { it.version == "5.41" })
    }
}
