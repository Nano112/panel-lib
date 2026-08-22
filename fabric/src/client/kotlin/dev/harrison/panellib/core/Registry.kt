package dev.harrison.panellib.core

import dev.harrison.panellib.api.ModHandle
import dev.harrison.panellib.api.PanelHandle
import net.minecraft.client.KeyMapping

/** One registered panel: the handle consumers hold plus the render callback panel-lib invokes. */
class PanelSpec(
    override val id: String,
    override val title: String,
    override val icon: String?,
    override val owner: ModHandle,
    val flags: Int,
    val render: () -> Unit,
    private val manager: PanelController,
    /** false → the consumer calls ImGui.begin/end itself (see ModHandle.rawPanel). */
    val managed: Boolean = true,
    /** Shown in the mod's toolbar menu. */
    val listed: Boolean = true,
) : PanelHandle {
    override val isOpen: Boolean get() = manager.isOpen(id)
    override fun open() = manager.open(this)
    override fun close() = manager.close(id)
    override fun toggle() = manager.toggle(this)
    /** Window label: title shown, id used for identity/ini. */
    override val windowLabel: String get() = (if (icon != null) "$icon  $title" else title) + "###" + id
}

/** What the registry needs from whoever owns open-panel state (PanelManager at runtime, a fake in tests). */
interface PanelController {
    fun isOpen(id: String): Boolean
    fun open(panel: PanelSpec)
    fun close(id: String)
    fun toggle(panel: PanelSpec)
}

sealed class MenuEntry {
    data class Item(val label: String, val icon: String?, val visible: () -> Boolean, val enabled: () -> Boolean, val action: () -> Unit) : MenuEntry()
    object Separator : MenuEntry()
}

class FrameHook(val keepsOverlayOpen: () -> Boolean, val render: () -> Unit)

class ModSpec(
    override val modId: String,
    override val displayName: String,
    override val icon: String?,
    private val registry: Registry,
) : ModHandle {
    private val _panels = ArrayList<PanelSpec>()
    val menu = ArrayList<MenuEntry>()
    val frameHooks = ArrayList<FrameHook>()
    override val panels: List<PanelHandle> get() = _panels.toList()
    val panelSpecs: List<PanelSpec> get() = _panels.toList()

    override fun panel(id: String, title: String, icon: String?, flags: Int, listed: Boolean, render: () -> Unit): PanelHandle = add(id, title, icon, flags, render, true, listed)

    override fun rawPanel(id: String, title: String, icon: String?, listed: Boolean, render: () -> Unit): PanelHandle = add(id, title, icon, 0, render, false, listed)

    private fun add(id: String, title: String, icon: String?, flags: Int, render: () -> Unit, managed: Boolean, listed: Boolean): PanelHandle {
        require(id.isNotBlank() && !id.contains(':')) { "panel id must be non-blank and must not contain ':'" }
        val spec = PanelSpec("$modId:$id", title, icon, this, flags, render, registry.controller, managed, listed)
        registry.addPanel(spec)
        _panels += spec
        return spec
    }

    override fun menuItem(label: String, icon: String?, visible: () -> Boolean, enabled: () -> Boolean, action: () -> Unit) {
        menu += MenuEntry.Item(label, icon, visible, enabled, action)
    }

    override fun frameHook(keepsOverlayOpen: () -> Boolean, render: () -> Unit) { frameHooks += FrameHook(keepsOverlayOpen, render) }
    override fun menuSeparator() { menu += MenuEntry.Separator }
    override fun keybind(name: String, defaultKey: Int, action: () -> Unit): KeyMapping = registry.keybinds.register(modId, name, defaultKey, action)
}

/** Pure registry of contributors. No ImGui or MC calls (unit-tested). */
class Registry(val controller: PanelController, val keybinds: KeybindRegistrar) {
    private val mods = LinkedHashMap<String, ModSpec>()
    private val panels = LinkedHashMap<String, PanelSpec>()

    fun registerMod(modId: String, displayName: String, icon: String?): ModSpec {
        require(modId.isNotBlank()) { "modId must not be blank" }
        return mods.getOrPut(modId) { ModSpec(modId, displayName, icon, this) }
    }

    internal fun addPanel(spec: PanelSpec) {
        require(spec.id !in panels) { "duplicate panel id '${spec.id}'" }
        panels[spec.id] = spec
    }

    fun mods(): List<ModSpec> = mods.values.toList()
    fun frameHooks(): List<FrameHook> = mods.values.flatMap { it.frameHooks }
    fun panels(): List<PanelSpec> = panels.values.toList()
    fun panel(fullId: String): PanelSpec? = panels[fullId]
}

/** Abstracts KeyMapping registration so the registry is testable headless. */
fun interface KeybindRegistrar {
    fun register(modId: String, name: String, defaultKey: Int, action: () -> Unit): KeyMapping
}
