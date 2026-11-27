package org.maittaa.tabita

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import org.maittaa.tabita.train.*

/** 5.67: обход основных экранов на английском — какие строки остались без перевода (список в shots/missing_en.txt). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1600dp-xxhdpi")
class V567EnTest {
    init {
        Prefs(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()).lang = "en"
        I18n.track = true; I18n.missing.clear()
    }
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun tab(name: String) {
        rule.onAllNodes(hasText(name) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))[0].performClick()
        rule.waitForIdle()
    }

    @Test fun walk() {
        val act = rule.activity; val repo = act.repo; val today = LocalDate.now()
        rule.runOnUiThread {
            act.prefs.lang = "en"; I18n.lang = "en"
            repo.setProfile(Profile(180.0, 1995, "m"))
            val tmpl = repo.template.value
            fun id(s: Source) = tmpl.firstOrNull { it.source == s }?.id
            val vals = (0..35).associate { i ->
                val d = today.minusDays(i.toLong())
                d.key() to buildMap {
                    put("weight", (75.0 + i * 0.03).toString())
                    id(Source.HC_SLEEP)?.let { put(it, (420 + (i % 5) * 20).toString()) }
                    id(Source.HC_RESTING_HR)?.let { put(it, (58 + i % 4).toString()) }
                    id(Source.HC_STEPS)?.let { put(it, (6000 + i * 100).toString()) }
                    tmpl.filter { it.type == ItemType.SCALE && it.source == Source.NONE }.forEach { put(it.id, (5 + i % 4).toString()) }
                    tmpl.filter { it.type == ItemType.CHECK && it.source == Source.NONE }.take(4).forEachIndexed { k, it -> if ((i + k) % 2 == 0) put(it.id, "1") }
                }
            }
            repo.setValues(vals)
        }
        rule.waitForIdle()
        val steps = mutableListOf<() -> Unit>()
        steps += { tab("Today") }
        for (s in 0..3) steps += { tab("Programme"); rule.runOnUiThread { act.openProgram.value = s } }
        steps += { tab("Health") }
        for (sec in listOf(SEC_CARDIO, SEC_YOGA, SEC_ACTIVITY, SEC_BODY, SEC_SLEEP, SEC_WB, SEC_HUB)) steps += { rule.runOnUiThread { act.train.section = sec } }
        steps += { tab("More") }
        val failed = mutableListOf<String>()
        steps.forEachIndexed { i, st -> runCatching { st(); rule.waitForIdle() }.onFailure { failed += "шаг $i: ${it.message?.take(80)}" } }
        val out = File(System.getProperty("user.dir"), "../shots").apply { mkdirs() }
        File(out, "missing_en.txt").writeText((I18n.missing.toList() + failed.map { "!! $it" }).joinToString("\n"))
        println("MISSING_EN " + I18n.missing.size)
        org.junit.Assert.assertTrue(failed.joinToString(), failed.isEmpty())
        org.junit.Assert.assertTrue(I18n.missing.joinToString("\n"), I18n.missing.isEmpty())
        // контроль: незнакомая русская строка ловится
        t("Проверочная строка без перевода")
        org.junit.Assert.assertTrue("Проверочная строка без перевода" in I18n.missing)
        I18n.track = false; I18n.missing.clear()
    }
}
