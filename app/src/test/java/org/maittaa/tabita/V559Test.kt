package org.maittaa.tabita

import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.maittaa.tabita.train.Goal
import org.maittaa.tabita.train.Intensity
import org.maittaa.tabita.train.ReadinessCalc
import org.maittaa.tabita.train.SEC_HUB
import org.maittaa.tabita.train.TabitaData
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** 5.59: отметки самочувствия — набор, индекс дня, совет на тренировку. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1600dp-xxhdpi")
class V559Test {
    init { Prefs(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()).lang = "ru" }
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @After fun reset() { rule.activity.prefs.monthCfg = MonthCfg() }

    private fun save(name: String) {
        rule.waitForIdle()
        val v = rule.activity.window.decorView
        val b = android.graphics.Bitmap.createBitmap(v.width, v.height, android.graphics.Bitmap.Config.ARGB_8888).also { v.draw(android.graphics.Canvas(it)) }
        val out = File(System.getProperty("user.dir"), "../shots").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun readinessFromMarks() {
        val d = TabitaData(emptyList(), null)
        val today = LocalDate.now()
        fun of(vararg ids: String) = ReadinessCalc.evaluate(d, today, Goal.MASS, marks = ids.map { DayMarks.of(it)!! }).intensity
        assertEquals(Intensity.NORMAL, of())
        assertEquals(Intensity.NORMAL, of("fm_energy", "fm_stress"))
        assertEquals(Intensity.LIGHT, of("fm_runny"))          // выше шеи — легче
        assertEquals(Intensity.RECOVERY, of("fm_runny", "fm_fever"))
        assertEquals(Intensity.RECOVERY, of("fm_stomach"))     // ниже шеи — отдых
        assertEquals(DayMarks.Effect.REST, DayMarks.effect(listOf(DayMarks.of("fm_cough")!!, DayMarks.of("fm_head")!!)))
    }

    @Test fun installAndIndex() {
        val act = rule.activity; val repo = act.repo
        rule.runOnUiThread { DayMarks.install(repo, act.prefs); DayMarks.install(repo, act.prefs) }
        val tmpl = repo.template.value
        assertEquals(DayMarks.all.size, DayMarks.installed(tmpl).size)
        assertEquals(DayMarks.all.size, tmpl.count { DayMarks.of(it.id) != null })     // без повторов
        assertTrue(tmpl.filter { DayMarks.of(it.id) != null }.all { it.group == DayMarks.GROUP && it.type == ItemType.CHECK && it.section == "Самочувствие" })
        assertTrue(repo.groups.value.any { it.id == DayMarks.GROUP && it.kind == GroupKind.CHECKS })
        val cfg = act.prefs.monthCfg
        assertTrue(DayMarks.all.filter { !it.good }.all { it.id in cfg.negative })
        assertTrue(DayMarks.all.filter { it.good }.none { it.id in cfg.negative })
        val today = LocalDate.now()
        rule.runOnUiThread {
            repo.setValues(mapOf(today.key() to mapOf("fm_head" to "1", "fm_mood" to "1"), today.minusDays(1).key() to mapOf("fm_mood" to "1")))
        }
        assertEquals(listOf("fm_head", "fm_mood"), DayMarks.marked(repo, today).map { it.id })
        val md = MonthData(repo.template.value, repo.entries.value, today, act.prefs.monthCfg)
        val withHead = md.dayIndex(today).value!!; val without = md.dayIndex(today.minusDays(1)).value!!
        assertTrue("$withHead < $without", withHead < without)
    }

    @Test fun stateCard() {
        val act = rule.activity; val repo = act.repo
        rule.runOnUiThread { act.train.discard(); act.train.saveProgram(null); act.train.section = SEC_HUB }
        rule.onAllNodesWithText("Здоровье").let { it[it.fetchSemanticsNodes().size - 1] }.performClick()
        rule.waitForIdle()
        save("59_state_offer")
        rule.onNodeWithTag("marks_install").performClick()
        rule.waitForIdle()
        assertTrue(DayMarks.isInstalled(repo.template.value))
        rule.runOnUiThread { repo.setValue(LocalDate.now().key(), "fm_fever", "1") }
        rule.waitForIdle()
        rule.onNodeWithTag("state_marks").assertExists()
        rule.onNodeWithText("Организму нужно восстановление").assertExists()
        save("59_state_marks")
        assertTrue(Changelog.all.any { it.version == "5.59" })
    }

    /** 5.59.1: отметки самочувствия — разделом в «Отметках дня»; перенос из отдельной плитки 5.59. */
    @Test fun sectionsInDayChecks() {
        val act = rule.activity; val repo = act.repo
        val it0 = TemplateItem(id = "x1", name = "Зарядка", type = ItemType.CHECK, section = "Привычки")
        assertEquals("Привычки", TemplateItem.fromJson(it0.toJson()).section)
        assertEquals("", TemplateItem.fromJson(it0.copy(section = "").toJson()).section)
        // как было в 5.59: своя группа g_feel
        rule.runOnUiThread {
            repo.ensureGroup(DayMarks.OLD_GROUP, "Отметки самочувствия", GroupKind.CHECKS)
            repo.upsertItem(TemplateItem(id = "fm_head", name = "Болит голова", type = ItemType.CHECK, group = DayMarks.OLD_GROUP))
            repo.upsertItem(it0.copy(group = "checks"))
            DayMarks.merge(repo)
        }
        val head = repo.template.value.first { it.id == "fm_head" }
        assertEquals("checks", repo.groupOf(head).id); assertEquals("Самочувствие", head.section)
        assertTrue(repo.groups.value.none { it.id == DayMarks.OLD_GROUP })
        assertEquals(listOf("Привычки", "Самочувствие"), checkSections(repo.template.value).sorted())
        // в плитке — подзаголовки разделов
        val items = groupChecks(repo, repo.template.value, "checks")
        rule.activity.runOnUiThread {
            act.setContent { AppTheme { androidx.compose.foundation.layout.Column { ChecksRow(items, mapOf("fm_head" to "1")) { _, _ -> } } } }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("sec_Самочувствие").assertExists()
        rule.onNodeWithTag("sec_Привычки").assertExists()
        save("591_sections")
    }

    /** 5.59.2: влияние на совет — настройка самой отметки; разделы можно выключить. */
    @Test fun calcSettingsPerMark() {
        val act = rule.activity; val repo = act.repo; val today = LocalDate.now()
        val mine = TemplateItem(id = "my1", name = "Перелёт", type = ItemType.CHECK, train = "rest")
        assertEquals("rest", TemplateItem.fromJson(mine.toJson()).train)
        act.prefs.marksTrainSet = false
        rule.runOnUiThread {
            repo.upsertItem(mine)
            repo.upsertItem(TemplateItem(id = "fm_head", name = "Голова", type = ItemType.CHECK))   // из набора, без настройки
            repo.upsertItem(TemplateItem(id = "x2", name = "Кино", type = ItemType.CHECK))
            DayMarks.merge(repo, act.prefs)
            repo.setValues(mapOf(today.key() to mapOf("my1" to "1", "fm_head" to "1", "x2" to "1")))
        }
        // набор получил значение по умолчанию один раз; своя отметка — по своей настройке; «Кино» — только для учёта
        assertEquals("easy", repo.template.value.first { it.id == "fm_head" }.train)
        val m = DayMarks.marked(repo, today)
        assertEquals(setOf("my1", "fm_head"), m.map { it.id }.toSet())
        assertEquals(DayMarks.Effect.REST, DayMarks.effect(m))
        // выключили влияние у отметки из набора — повторный запуск его не возвращает
        rule.runOnUiThread { repo.upsertItem(repo.template.value.first { it.id == "fm_head" }.copy(train = "")); DayMarks.merge(repo, act.prefs) }
        assertEquals("", repo.template.value.first { it.id == "fm_head" }.train)
        assertEquals(listOf("my1"), DayMarks.marked(repo, today).map { it.id })
        // разделы выключены — одна куча без подзаголовков
        val items = listOf(mine.copy(section = "Поездки"), TemplateItem(id = "x3", name = "Зарядка", type = ItemType.CHECK, section = "Привычки"))
        rule.activity.runOnUiThread {
            act.setContent { AppTheme { androidx.compose.foundation.layout.Column { ChecksRow(items, emptyMap(), sections = false) { _, _ -> } } } }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("sec_Поездки").assertDoesNotExist()
        rule.onNodeWithText("Зарядка").assertExists()
    }
}
