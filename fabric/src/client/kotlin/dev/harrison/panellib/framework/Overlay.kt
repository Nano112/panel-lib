package dev.harrison.panellib.framework

import dev.harrison.panellib.compat.Compat
import dev.harrison.panellib.theme.ThemeApplier
import dev.harrison.panellib.widgets.ConfirmModal
import net.minecraft.client.Minecraft

/**
 * The overlay lifecycle: visible when [overlayOpen] or any panel/modal is open; suspended (input
 * and cursor go to MC) while a vanilla Screen is open. [render] runs every frame from the present
 * mixin; [reconcileCursor] runs first so the cursor is released/grabbed on the exact frame the
 * state changes.
 */
object Overlay {
    @Volatile private var overlayOpen = false
    @Volatile private var cursorReleased = false

    @JvmStatic fun toggle() { overlayOpen = !overlayOpen }
    @JvmStatic fun ensureOpen() { overlayOpen = true }
    @JvmStatic fun close() { overlayOpen = false }
    @JvmStatic fun isOpen(): Boolean = overlayOpen

    /** Escape: close the topmost panel, else hide the overlay. */
    @JvmStatic fun onEscape() { if (PanelManager.anyOpen()) PanelManager.closeTop() else overlayOpen = false }

    private fun active(): Boolean = overlayOpen || PanelManager.anyOpen() || ConfirmModal.isOpen()

    /** Single source of truth for the input mixins and the render gate. */
    @JvmStatic fun isFocused(): Boolean = Compat.screen() == null && active()

    private fun reconcileCursor() {
        val mc = Minecraft.getInstance()
        if (Compat.screen() != null) { cursorReleased = false; return }
        val shouldRelease = active()
        if (shouldRelease == cursorReleased) return
        if (shouldRelease) mc.mouseHandler.releaseMouse() else mc.mouseHandler.grabMouse()
        cursorReleased = shouldRelease
    }

    @JvmStatic
    fun render() {
        reconcileCursor()
        ImGuiManager.initIfNeeded()
        if (!ImGuiManager.initialized || !isFocused()) return
        ImGuiManager.startFrame(true)
        ThemeApplier.apply()
        try {
            DockHost.render()
            PanelManager.renderAll()
            ConfirmModal.render()
        } finally {
            ThemeApplier.unapply()
        }
        ImGuiManager.endFrame()
    }
}
