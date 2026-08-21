package dev.harrison.panellib.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IconsTest {
    @Test fun glyphRangesAreSortedDegeneratePairsZeroTerminated() {
        val r = Icons.glyphRanges()
        assertEquals(0, r.last().toInt())
        val cps = (0 until (r.size - 1) / 2).map { r[it * 2].toInt() and 0xFFFF }
        assertEquals(cps.sorted(), cps)
        (0 until (r.size - 1) / 2).forEach { assertEquals(r[it * 2], r[it * 2 + 1]) }
        assertTrue(Icons.SEARCH.codePointAt(0) in cps)
    }

    @Test fun registerExtras() {
        Icons.register(listOf(0xF0F3))
        assertTrue(0xF0F3 in Icons.allCodepoints())
        assertEquals(Icons.allCodepoints().size, Icons.allCodepoints().distinct().size)
    }
}
