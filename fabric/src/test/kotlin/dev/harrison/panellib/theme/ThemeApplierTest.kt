package dev.harrison.panellib.theme

import imgui.flag.ImGuiCol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThemeApplierTest {
    @Test fun everyImGuiColSlotIsMapped() {
        val slots = ImGuiCol::class.java.fields
            .filter { java.lang.reflect.Modifier.isStatic(it.modifiers) && it.type == Int::class.javaPrimitiveType && it.name != "COUNT" }
            .map { it.name to it.getInt(null) }
        val mapped = ThemeApplier.colorMap(Themes.DEFAULT).keys
        val missing = slots.filter { it.second !in mapped }.map { it.first }
        assertTrue(missing.isEmpty(), "unmapped ImGuiCol slots: $missing")
        assertEquals(slots.size, mapped.size)
    }

    @Test fun parseArgb() {
        assertEquals(0xFF5B8DEF.toInt(), dev.harrison.panellib.theme.parseArgb("#5B8DEF"))
        assertEquals(0x405B8DEF, dev.harrison.panellib.theme.parseArgb("#405B8DEF"))
        val v = rgba("#FF0000"); assertEquals(1f, v.x); assertEquals(0f, v.y); assertEquals(1f, v.w)
    }

    @Test fun withAccentDerives() {
        val t = Themes.withAccent(Themes.DEFAULT, "#FF00AA")
        assertEquals(1f, t.accent.x); assertTrue(t.accentHover.y > t.accent.y); assertEquals(0.25f, t.accentMuted.w)
        assertEquals(Themes.DEFAULT.bg, t.bg)
    }
}
