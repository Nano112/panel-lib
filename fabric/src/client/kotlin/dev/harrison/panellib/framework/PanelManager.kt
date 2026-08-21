package dev.harrison.panellib.framework

import dev.harrison.panellib.core.PanelController
import dev.harrison.panellib.core.PanelSpec
import imgui.ImGui
import imgui.type.ImBoolean

/**
 * Open-panel state in z/insertion order, plus the per-frame begin/end wrapper around each
 * panel's content. Pure state operations are unit-tested; [renderAll] needs ImGui.
 */
object PanelManager : PanelController {
    private val open = LinkedHashMap<String, PanelSpec>()
    private val openFlag = ImBoolean(true)

    override fun isOpen(id: String): Boolean = open.containsKey(id)
    override fun open(panel: PanelSpec) { open.putIfAbsent(panel.id, panel); Overlay.ensureOpen() }
    override fun close(id: String) { open.remove(id) }
    override fun toggle(panel: PanelSpec) { if (isOpen(panel.id)) close(panel.id) else open(panel) }
    fun anyOpen(): Boolean = open.isNotEmpty()
    fun openPanels(): List<PanelSpec> = open.values.toList()
    fun closeAll() = open.clear()
    fun closeTop() { open.keys.lastOrNull()?.let { open.remove(it) } }

    /** Visible for tests: reset all state. */
    internal fun reset() = open.clear()

    fun renderAll() {
        for (panel in open.values.toList()) {
            openFlag.set(true)
            val shown = ImGui.begin(panel.windowLabel, openFlag, panel.flags)
            try {
                if (shown) {
                    try { panel.render() } catch (t: Throwable) { PanelErrors.render(panel, t) }
                }
            } finally {
                ImGui.end()
            }
            if (!openFlag.get()) close(panel.id)
        }
    }
}

/** A panel whose render threw shows the error instead of taking the whole overlay down. */
internal object PanelErrors {
    private val seen = HashMap<String, Throwable>()
    fun render(panel: PanelSpec, t: Throwable) {
        if (seen.put(panel.id, t) == null) PanelLibLog.LOGGER.error("[panel-lib] panel '{}' threw", panel.id, t)
        val th = dev.harrison.panellib.theme.Theme.current
        ImGui.textColored(th.danger.x, th.danger.y, th.danger.z, th.danger.w, "Panel error: ${t::class.java.simpleName}")
        ImGui.textWrapped(t.message ?: "")
    }
}

internal object PanelLibLog { val LOGGER = org.slf4j.LoggerFactory.getLogger("panellib") }
