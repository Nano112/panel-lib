package dev.harrison.panellib.framework

import dev.harrison.panellib.core.MenuEntry
import dev.harrison.panellib.core.Registry
import dev.harrison.panellib.theme.Fonts
import dev.harrison.panellib.theme.Icons
import dev.harrison.panellib.theme.Theme
import dev.harrison.panellib.theme.withFont
import imgui.ImGui
import imgui.flag.ImGuiCol

/**
 * The shared top bar: wordmark · one `<Mod> ▾` menu per contributor (panels as checkable items,
 * then custom items) · right-aligned `Layout ▾`.
 */
object Toolbar {
    private var registryRef: Registry? = null
    fun bind(registry: Registry) { registryRef = registry }
    fun registry(): Registry = registryRef ?: error("panel-lib registry not bound")

    fun renderMenuBar() {
        val t = Theme.current
        ImGui.textColored(t.accent.x, t.accent.y, t.accent.z, t.accent.w, Icons.LAYER_GROUP)
        ImGui.sameLine(0f, 6f)
        withFont(Fonts.SEMIBOLD) { ImGui.text("Panels") }
        ImGui.textDisabled("|")

        val mods = registry().mods()
        if (mods.isEmpty()) ImGui.textDisabled("no mods registered")
        for (mod in mods) {
            val label = (mod.icon?.let { "$it  " } ?: "") + mod.displayName + "  " + Icons.CHEVRON_DOWN
            val anyOpen = mod.panelSpecs.any { it.isOpen }
            val dl = ImGui.getWindowDrawList()
            val open = ImGui.beginMenu(label + "##mod-" + mod.modId)
            if (anyOpen) {
                // 2px accent underline marks "this mod has an open panel" (rect of the menu item just submitted).
                val minX = ImGui.getItemRectMinX(); val maxX = ImGui.getItemRectMaxX(); val maxY = ImGui.getItemRectMaxY()
                dl.addRectFilled(minX, maxY - 2f, maxX, maxY, ImGui.getColorU32(t.accent.x, t.accent.y, t.accent.z, 1f))
            }
            if (open) {
                for (p in mod.panelSpecs) {
                    if (!p.listed) continue
                    val pl = (p.icon?.let { "$it  " } ?: "") + p.title + "##panel-" + p.id
                    if (ImGui.menuItem(pl, "", p.isOpen)) p.toggle()
                }
                if (mod.panelSpecs.isNotEmpty() && mod.menu.isNotEmpty()) ImGui.separator()
                for (e in mod.menu) when (e) {
                    is MenuEntry.Separator -> ImGui.separator()
                    is MenuEntry.Item -> if (e.visible()) {
                        val enabled = e.enabled()
                        if (ImGui.menuItem((e.icon?.let { "$it  " } ?: "") + e.label, "", false, enabled) && enabled) e.action()
                    }
                }
                ImGui.endMenu()
            }
        }

        // Hint + right-aligned Layout menu.
        val hint = if (Overlay.gameFocus) "playing · Esc returns to panels" else if (GameViewport.enabled) "click the game to play" else ""
        val layoutLabel = "${Icons.WINDOW}  Layout  ${Icons.CHEVRON_DOWN}"
        val hintW = if (hint.isEmpty()) 0f else ImGui.calcTextSize(hint).x + 16f
        val w = ImGui.calcTextSize(layoutLabel).x + ImGui.getStyle().framePaddingX * 2 + 8f + hintW
        val avail = ImGui.getContentRegionAvailX()
        if (avail > w) ImGui.setCursorPosX(ImGui.getCursorPosX() + avail - w)
        if (hint.isNotEmpty()) { ImGui.textDisabled(hint); ImGui.sameLine(0f, 16f) }
        if (ImGui.beginMenu(layoutLabel)) {
            if (ImGui.menuItem("${Icons.REFRESH}  Reset layout")) DockHost.resetLayout()
            if (ImGui.menuItem("${Icons.XMARK}  Close all panels")) PanelManager.closeAll()
            if (ImGui.menuItem("${Icons.WINDOW}  Embed game in layout", "", GameViewport.enabled)) GameViewport.enabled = !GameViewport.enabled
            ImGui.separator()
            if (ImGui.menuItem("${Icons.EYE}  Hide overlay", "Esc")) { PanelManager.closeAll(); Overlay.close() }
            ImGui.endMenu()
        }
    }

}
