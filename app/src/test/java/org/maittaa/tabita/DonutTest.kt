package org.maittaa.tabita

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 5.9.1: маленькие доли кольца не налезают друг на друга. */
@RunWith(RobolectricTestRunner::class)
class DonutTest {
    // траты со скриншота: 13 500 / 2 815 / 1 524 / 418 / 36
    private val amounts = listOf(13500.0, 2815.0, 1524.0, 418.0, 36.0)

    @Test fun sweepsHaveMinimumAndSumTo360() {
        val f = amounts.map { it / amounts.sum() }
        val gap = 12f
        val s = donutSweeps(f, gap)
        assertEquals(360f, s.sum(), 0.01f)
        s.forEach { assertTrue("$it", it >= gap + 3f - 1e-3f) }
        // порядок долей сохраняется
        assertTrue(s[0] > s[1] && s[1] > s[2] && s[2] >= s[3])
        // крупная доля почти не меняется
        assertEquals(f[0] * 360, s[0].toDouble(), 25.0)
        // одна доля — полный круг; много крошечных — поровну
        assertEquals(listOf(360f), donutSweeps(listOf(1.0), gap))
        assertEquals(360f, donutSweeps(List(40) { 1.0 / 40 }, gap).sum(), 0.01f)
    }
}
