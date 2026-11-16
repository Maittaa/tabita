package org.maittaa.tabita

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 5.13: корзина пунктов — данные не теряются при удалении. */
@RunWith(RobolectricTestRunner::class)
class TrashTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test fun deleteRestorePurge() {
        val r = Repo(ctx)
        r.setManual("2026-11-10", "nc", "1", true)
        r.setManual("2026-11-11", "nc", "2", true)
        r.setManual("2026-11-10", "used", countsToString(mapOf("Кофе" to 2))!!, true)
        val ncGroup = r.groupOf(r.template.value.first { it.id == "nc" }).id

        r.deleteItem("nc")
        assertTrue(r.template.value.none { it.id == "nc" })
        assertEquals(listOf("nc"), r.trash.value.map { it.id })
        assertEquals("1", r.entries.value["2026-11-10"]!!["nc"])
        assertEquals(2, r.daysWithData("nc"))
        assertTrue(r.tiles(r.template.value, r.layout.value).none { it.id == "nc" })

        // переживает сохранение и загрузку
        val r2 = Repo(ctx); assertTrue(r2.loadJson(r.toJson()))
        assertEquals(listOf("nc"), r2.trash.value.map { it.id })

        // возврат: прежний id, прежняя группа, история на месте
        assertTrue(r2.restoreItem("nc"))
        val back = r2.template.value.first { it.id == "nc" }
        assertEquals(ncGroup, r2.groupOf(back).id)
        assertTrue(r2.trash.value.isEmpty())
        assertEquals("2", r2.entries.value["2026-11-11"]!!["nc"])
        assertFalse(r2.restoreItem("nc"))

        // навсегда: стираются и записи, включая служебные ключи
        r2.deleteItem("used")
        r2.purgeItem("used")
        assertTrue(r2.trash.value.isEmpty())
        assertNull(r2.entries.value["2026-11-10"]!!["used"])
        assertNull(r2.entries.value["2026-11-10"]!![manualKey("used")])
        assertEquals("1", r2.entries.value["2026-11-10"]!!["nc"])
    }

    @Test fun wipeAll() {
        val r = Repo(ctx)
        r.setManual("2026-11-10", "nc", "1", true)
        r.setMonthNote("2026-11", "итог")
        r.deleteItem("work")
        java.io.File(r.photosDir, "x.jpg").writeText("x")
        r.wipeAll()
        assertTrue(r.entries.value.isEmpty()); assertTrue(r.monthNotes.value.isEmpty())
        assertTrue(r.trash.value.isEmpty()); assertTrue(r.checkups.value.isEmpty())
        assertTrue(r.template.value.any { it.id == "work" })
        assertTrue(r.photosDir.listFiles().isNullOrEmpty())
    }
}
