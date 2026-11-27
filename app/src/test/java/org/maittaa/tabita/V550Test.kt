package org.maittaa.tabita

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 5.50: вкладка «Здоровье» (тренировки + самочувствие), шрифт значения не вылезает за плитку. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1400dp-xxhdpi")
class V550Test {
    init { Prefs(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()).lang = "ru" }
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun healthTab() {
        rule.onAllNodesWithText("Здоровье").let { it[it.fetchSemanticsNodes().size - 1] }.performClick()
        rule.waitForIdle()
        // 5.52: сначала главная с плитками разделов
        listOf("hub_strength", "hub_cardio", "hub_yoga", "hub_wb").forEach { rule.onNode(androidx.compose.ui.test.hasTestTag(it)).assertExists() }
        rule.onNode(androidx.compose.ui.test.hasTestTag("hub_strength")).performClick()
        rule.waitForIdle()
        listOf("Тренировка", "Прогресс").forEach { rule.onAllNodesWithText(it)[0].assertExists() }
        rule.onNodeWithContentDescription("Назад").performClick()
        rule.waitForIdle()
        rule.onNode(androidx.compose.ui.test.hasTestTag("health_hub")).assertExists()
        // «Программа» — без самочувствия
        rule.onAllNodesWithText("Программа").let { it[it.fetchSemanticsNodes().size - 1] }.performClick()
        rule.waitForIdle()
        assertTrue(rule.onAllNodes(androidx.compose.ui.test.hasText("Самочувствие") and androidx.compose.ui.test.hasAnySibling(androidx.compose.ui.test.hasText("Итоги")))
            .fetchSemanticsNodes().isEmpty())
        // плитка чекапа / напоминание ведут в «Здоровье → Самочувствие»
        rule.runOnUiThread { rule.activity.wbStart.value = true }
        rule.waitForIdle()
        assertTrue(rule.activity.train.section == org.maittaa.tabita.train.SEC_WB)
        assertTrue(Changelog.all.any { it.version == "5.50" })
    }

    @Test fun longValueFits() {
        val s = TileSummary("Советы дня", "Пора на силовую", "Последняя силовая 4 дн. назад. Для роста мышц — 2–3 тренировки в неделю",
            Icons.Filled.FitnessCenter)
        rule.runOnUiThread { rule.activity.setContent { MaterialTheme { Box(Modifier.size(380.dp, 112.dp)) { CompactTileView(s, 4, 2) } } } }
        rule.waitForIdle()
        val v = rule.onNodeWithText("Пора на силовую", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val sub = rule.onNodeWithText("Последняя силовая", substring = true, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("значение ${v.bottom} заходит на подпись ${sub.top}", v.bottom <= sub.top + 1f)
    }
}
