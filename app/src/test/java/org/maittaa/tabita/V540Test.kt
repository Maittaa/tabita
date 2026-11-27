package org.maittaa.tabita

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** 5.40: инструкции по подключению ИИ-сервисов и пометка про VPN. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1400dp-xxhdpi")
class V540Test {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun mapping() {
        assertEquals(AiGuide.OPENROUTER, AiGuide.idFor("openai", "https://openrouter.ai/api/v1"))
        assertEquals(AiGuide.OPENAI, AiGuide.idFor("openai", "https://api.openai.com/v1/"))
        assertEquals(AiGuide.CLAUDE, AiGuide.idFor("anthropic", "https://api.anthropic.com"))
        assertEquals(AiGuide.GIGACHAT, AiGuide.idFor("gigachat", GigaChat.BASE))
        assertEquals(AiGuide.OTHER, AiGuide.idFor("openai", "https://example.com/v1"))
        assertEquals(AiGuide.Vpn.NO, AiGuide.guide(AiGuide.GIGACHAT).vpn)
        assertEquals(AiGuide.Vpn.YES, AiGuide.guide(AiGuide.OPENAI).vpn)
        assertEquals(AiGuide.Vpn.YES, AiGuide.guide(AiGuide.CLAUDE).vpn)
        assertEquals(AiGuide.Vpn.MAYBE, AiGuide.guide(AiGuide.OPENROUTER).vpn)
        listOf(AiGuide.OPENROUTER, AiGuide.OPENAI, AiGuide.CLAUDE, AiGuide.GIGACHAT, AiGuide.OTHER).forEach {
            assertTrue(AiGuide.guide(it).steps.size >= 4)
        }
        assertNotNull(AiGuide.guide(AiGuide.CLAUDE).url)
    }

    @Test fun regionError() {
        assertTrue(AiGuide.looksRegionBlocked("HTTP 403: Country, region, or territory not supported"))
        assertTrue(AiGuide.looksRegionBlocked("HTTP 400: User location is not supported for the API use."))
        assertFalse(AiGuide.looksRegionBlocked("HTTP 401: Invalid API key"))
        assertTrue(AiGuide.errorText("HTTP 403: unsupported_country_region_territory").contains("VPN"))
        assertFalse(AiGuide.errorText("HTTP 401: bad key").contains("VPN"))
    }

    @Test fun card() {
        rule.runOnUiThread { rule.activity.setContent { MaterialTheme { AiGuideCard(AiGuide.CLAUDE, open0 = true) } } }
        rule.waitForIdle()
        // вне основного экрана t() отдаёт английский
        rule.onNodeWithText("VPN required").assertExists()
        rule.onNodeWithText("Copy the key (starts with sk-ant-) — it is shown only once.").assertExists()
        rule.onNodeWithText("Open keys page").performClick()
        val i = shadowOf(rule.activity).nextStartedActivity
        assertEquals("https://platform.claude.com/settings/keys", i.dataString)
    }
}
