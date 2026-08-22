package dev.harrison.panellib.core

import dev.harrison.panellib.api.ModHandle
import dev.harrison.panellib.api.PanelHandle
import dev.harrison.panellib.api.PanelLibApi
import dev.harrison.panellib.framework.Overlay
import dev.harrison.panellib.theme.Icons
import dev.harrison.panellib.theme.Theme
import dev.harrison.panellib.widgets.ConfirmModal

class PanelLibApiImpl(private val registry: Registry) : PanelLibApi {
    override fun registerMod(modId: String, displayName: String, icon: String?): ModHandle = registry.registerMod(modId, displayName, icon)
    override fun setTheme(theme: Theme) { Theme.current = theme }
    override val theme: Theme get() = Theme.current
    override fun registerIcons(codepoints: Collection<Int>) = Icons.register(codepoints)
    override fun openOverlay() = Overlay.ensureOpen()
    override fun closeOverlay() = Overlay.close()
    override fun toggleOverlay() = Overlay.toggle()
    override val isOverlayOpen: Boolean get() = Overlay.isOpen()
    override val isOverlayFocused: Boolean get() = Overlay.isFocused()
    override fun confirm(title: String, message: String, confirmLabel: String, danger: Boolean, onConfirm: () -> Unit) =
        ConfirmModal.show(title, message, confirmLabel, danger, onConfirm)
    override fun closeTopPanel() = dev.harrison.panellib.framework.PanelManager.closeTop()
    override val anyPanelOpen: Boolean get() = dev.harrison.panellib.framework.PanelManager.anyOpen()
    override fun drainTypedChars(): List<Int> = dev.harrison.panellib.framework.ImGuiManager.drainTypedChars()
    override fun markSyntheticInput() { dev.harrison.panellib.framework.ImGuiManager.lastSyntheticInputAt = System.currentTimeMillis() }
    override val mods: List<ModHandle> get() = registry.mods()
    override fun panel(fullId: String): PanelHandle? = registry.panel(fullId)
}
