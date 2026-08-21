package dev.harrison.panellib.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PanelLibConfigTest {
    @Test fun parse() {
        val c = PanelLibConfig.parse("""{"accent":"#ff00aa","font_size":19}""")
        assertEquals("#ff00aa", c.accent); assertEquals(19f, c.fontSize)
        assertNull(PanelLibConfig.parse("""{"accent":""}""").accent)
    }
}
