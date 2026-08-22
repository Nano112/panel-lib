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

    /**
     * Game-focus sub-mode: the overlay stays visible but the game owns mouse + keyboard (cursor grabbed,
     * mouse-look works). Entered by clicking the game area, left with Escape. Lets you play with panels open.
     */
    @Volatile var gameFocus: Boolean = false
        private set

    /** Java-friendly accessor for other mods (e.g. to decide whether a mouse grab is human-initiated). */
    @JvmStatic fun isGameFocus(): Boolean = gameFocus

    @JvmStatic fun toggle() { overlayOpen = !overlayOpen; if (!overlayOpen) gameFocus = false }
    @JvmStatic fun ensureOpen() { overlayOpen = true }
    @JvmStatic fun close() { overlayOpen = false; gameFocus = false }
    @JvmStatic fun isOpen(): Boolean = overlayOpen

    /** Escape: leave game focus, else close the topmost panel, else hide the overlay. */
    @JvmStatic fun onEscape() {
        when {
            gameFocus -> exitGameFocus()
            PanelManager.anyOpen() -> PanelManager.closeTop()
            else -> { overlayOpen = false; gameFocus = false }
        }
    }

    @JvmStatic fun enterGameFocus() {
        if (gameFocus) return
        gameFocus = true
        cursorReleased = false
        Minecraft.getInstance().mouseHandler.grabMouse()
    }

    @JvmStatic fun exitGameFocus() {
        if (!gameFocus) return
        gameFocus = false
        Minecraft.getInstance().mouseHandler.releaseMouse()
        cursorReleased = true
    }

    private fun active(): Boolean = overlayOpen || PanelManager.anyOpen() || ConfirmModal.isOpen() ||
        runCatching { Toolbar.registry().frameHooks().any { it.keepsOverlayOpen() } }.getOrDefault(false)

    /** The overlay is drawn (no vanilla screen covers it). */
    @JvmStatic fun isVisible(): Boolean = Compat.screen() == null && active()

    /** The overlay owns input: visible AND not in game focus. Single source of truth for the input mixins. */
    @JvmStatic fun isFocused(): Boolean = isVisible() && !gameFocus

    private fun reconcileCursor() {
        val mc = Minecraft.getInstance()
        if (Compat.screen() != null) { cursorReleased = false; gameFocus = false; return }
        val shouldRelease = active() && !gameFocus
        if (shouldRelease == cursorReleased) return
        if (shouldRelease) mc.mouseHandler.releaseMouse() else mc.mouseHandler.grabMouse()
        cursorReleased = shouldRelease
    }

    @JvmStatic
    fun render() {
        reconcileCursor()
        ImGuiManager.initIfNeeded()
        if (!ImGuiManager.initialized || !isVisible()) { GameViewport.restore(); return }
        ImGuiManager.startFrame(focused = !gameFocus)
        ThemeApplier.apply()
        try {
            DockHost.render()
            PanelManager.renderAll()
            for (hook in Toolbar.registry().frameHooks()) {
                try { hook.render() } catch (t: Throwable) { PanelLibLog.LOGGER.error("[panel-lib] frame hook threw", t) }
            }
            ConfirmModal.render()
        } finally {
            ThemeApplier.unapply()
        }
        GameViewport.composite()   // move the game frame into the central rect, under the panels
        ImGuiManager.endFrame()
        GameViewport.applySizing() // size Minecraft for the NEXT frame
    }
}
