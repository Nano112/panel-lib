package dev.harrison.panellib.api

import dev.harrison.panellib.theme.Theme
import net.minecraft.client.KeyMapping

/**
 * Implement this and declare it in `fabric.mod.json` under `"entrypoints": { "panellib": [...] }`.
 * Called once on the render thread during client init, after panel-lib is ready; register your
 * mod, panels, menu items, keybinds and extra icons here.
 */
fun interface PanelLibEntrypoint {
    fun init(api: PanelLibApi)
}

interface PanelLibApi {
    /** Register a contributing mod; returns the handle used to add panels/menus/keybinds. */
    fun registerMod(modId: String, displayName: String, icon: String? = null): ModHandle

    /** Replace the global theme (all mods share it). Takes effect next frame. */
    fun setTheme(theme: Theme)
    val theme: Theme

    /** Add Font Awesome 6 Solid codepoints to the icon atlas (entrypoint time only). */
    fun registerIcons(codepoints: Collection<Int>)

    fun openOverlay()
    fun closeOverlay()
    fun toggleOverlay()
    val isOverlayOpen: Boolean
    /** True while the overlay is visible and no vanilla screen covers it. */
    val isOverlayFocused: Boolean

    fun confirm(title: String, message: String, confirmLabel: String = "Confirm", danger: Boolean = false, onConfirm: () -> Unit)

    /** Close the most recently opened panel (what Escape does first). */
    fun closeTopPanel()
    val anyPanelOpen: Boolean

    /** Unicode code points typed since the last call (for custom text widgets that bypass ImGui InputText). */
    fun drainTypedChars(): List<Int>

    /** All registered mods in registration order. */
    val mods: List<ModHandle>
    /** Look up a panel by its full id `<modId>:<id>`. */
    fun panel(fullId: String): PanelHandle?
}

interface ModHandle {
    val modId: String
    val displayName: String
    val icon: String?
    val panels: List<PanelHandle>

    /**
     * Declare a dockable panel. panel-lib owns the window (`ImGui.begin/end`, close button,
     * docking); [render] draws only the content. [flags] are extra ImGuiWindowFlags.
     */
    fun panel(id: String, title: String, icon: String? = null, flags: Int = 0, listed: Boolean = true, render: () -> Unit): PanelHandle

    /**
     * A panel that draws its OWN window: [render] must call `ImGui.begin(...)`/`ImGui.end()` itself (useful for
     * dynamic titles or ports of existing code) and should call [PanelHandle.close] when its close box is hit.
     * panel-lib still tracks open state, lists it in the menu (unless [listed] is false — for panels that need
     * context, e.g. a detail view), docks it on first show and closes it on Escape.
     */
    fun rawPanel(id: String, title: String, icon: String? = null, listed: Boolean = true, render: () -> Unit): PanelHandle

    /** Extra entry in this mod's toolbar menu (below its panels). [visible]/[enabled] are evaluated every frame. */
    fun menuItem(label: String, icon: String? = null, visible: () -> Boolean = { true }, enabled: () -> Boolean = { true }, action: () -> Unit)
    fun menuSeparator()

    /**
     * Runs every overlay frame after the panels (global popups, modals). While [keepsOverlayOpen] returns true the
     * overlay stays active even with no panels open (e.g. a popup is showing).
     */
    fun frameHook(keepsOverlayOpen: () -> Boolean = { false }, render: () -> Unit)

    /**
     * Register a key binding under the "panel-lib" controls category. Translation key is
     * `key.<modId>.<name>` — add it to your lang file. [action] runs on the client tick thread.
     */
    fun keybind(name: String, defaultKey: Int, action: () -> Unit): KeyMapping
}

interface PanelHandle {
    /** `<modId>:<id>` — unique across mods; also the imgui.ini key, so keep it stable. */
    val id: String
    /** The ImGui window label to use in `ImGui.begin` for raw panels: `"<title>###<id>"` (the part after ### is the identity). */
    val windowLabel: String
    val title: String
    val icon: String?
    val owner: ModHandle
    val isOpen: Boolean
    /** Opens the panel and makes sure the overlay is visible. */
    fun open()
    fun close()
    fun toggle()
}
