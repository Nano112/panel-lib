package dev.harrison.panellib.widgets

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnimTest {
    @Test fun fadesInAndEvicts() {
        Anim.clear()
        assertEquals(0f, Anim.peek(1))
        val v = Anim.advance(1, true, 0.05f)
        assertTrue(v in 0.39f..0.41f)
        Anim.advance(1, true, 1f); assertEquals(1f, Anim.peek(1))
        Anim.advance(1, false, 1f); assertEquals(0f, Anim.peek(1)); assertEquals(0, Anim.size())
    }
}
