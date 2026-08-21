package dev.harrison.panellib.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegistryTest {
    private class FakeController : PanelController {
        val open = LinkedHashSet<String>()
        override fun isOpen(id: String) = id in open
        override fun open(panel: PanelSpec) { open += panel.id }
        override fun close(id: String) { open -= id }
        override fun toggle(panel: PanelSpec) { if (isOpen(panel.id)) close(panel.id) else open(panel) }
    }
    private val ctl = FakeController()
    private val reg = Registry(ctl) { _, _, _, _ -> throw UnsupportedOperationException("no KeyMapping in tests") }

    @Test fun panelsGetNamespacedIds() {
        val m = reg.registerMod("inspector", "Inspector", null)
        val p = m.panel("status", "MCP Status") {}
        assertEquals("inspector:status", p.id)
        assertEquals("MCP Status###inspector:status", (p as PanelSpec).windowLabel)
        assertEquals(listOf(p), reg.panels())
    }

    @Test fun duplicatePanelIdThrows() {
        val m = reg.registerMod("a", "A", null)
        m.panel("x", "X") {}
        assertFailsWith<IllegalArgumentException> { m.panel("x", "X2") {} }
        assertFailsWith<IllegalArgumentException> { m.panel("bad:id", "X3") {} }
    }

    @Test fun registerModIsIdempotentAndOrdered() {
        val a = reg.registerMod("a", "A", null); reg.registerMod("b", "B", null)
        assertTrue(reg.registerMod("a", "A again", null) === a)
        assertEquals(listOf("a", "b"), reg.mods().map { it.modId })
    }

    @Test fun handleDrivesController() {
        val p = reg.registerMod("a", "A", null).panel("p", "P") {}
        assertFalse(p.isOpen); p.open(); assertTrue(p.isOpen); p.toggle(); assertFalse(p.isOpen)
    }

    @Test fun menuEntriesKeepOrder() {
        val m = reg.registerMod("a", "A", null)
        m.menuItem("One") {}; m.menuSeparator(); m.menuItem("Two") {}
        assertEquals(3, m.menu.size); assertTrue(m.menu[1] is MenuEntry.Separator)
    }
}
