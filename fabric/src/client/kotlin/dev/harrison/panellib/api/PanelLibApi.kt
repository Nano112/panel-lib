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
    fun panel(id: String, title: String, icon: String? = null, flags: Int = 0, render: () -> Unit): PanelHandle

    /** Extra entry in this mod's toolbar menu (below its panels). */
    fun menuItem(label: String, icon: String? = null, action: () -> Unit)
    fun menuSeparator()

    /**
     * Register a key binding under the "panel-lib" controls category. Translation key is
     * `key.<modId>.<name>` — add it to your lang file. [action] runs on the client tick thread.
     */
    fun keybind(name: String, defaultKey: Int, action: () -> Unit): KeyMapping
}

interface PanelHandle {
    /** `<modId>:<id>` — unique across mods; also the imgui.ini key, so keep it stable. */
    val id: String
    val title: String
    val icon: String?
    val owner: ModHandle
    val isOpen: Boolean
    /** Opens the panel and makes sure the overlay is visible. */
    fun open()
    fun close()
    fun toggle()
}
